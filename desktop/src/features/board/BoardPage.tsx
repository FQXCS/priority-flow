import { memo, useCallback, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  DndContext,
  DragEndEvent,
  DragOverlay,
  DragStartEvent,
  PointerSensor,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import clsx from 'clsx';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { projectApi, taskApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { TaskCard } from '@/components/TaskCard';
import { TaskFormDialog } from '@/components/TaskFormDialog';
import { TaskDrawer } from '@/components/TaskDrawer';
import { canDropWithinBucket } from '@/utils/dragBucket';
import { resolveSortRef } from '@/utils/dragSortRef';
import { applyBoardSort } from '@/utils/boardOptimisticSort';
import type { BoardVo, TaskVo } from '@/api/types';

interface DragPayload {
  task: TaskVo;
  fromScope: string;
}

/** 看板页：默认聚合全部可见项目，项目为筛选条件；六列结构化数据 + dnd-kit 拖拽 + WS 实时对账 */
export function BoardPage() {
  const { user, projectList, hasPermission } = useAuthStore();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<{
    projectId?: string;
    assigneeId?: string;
    keyword?: string;
    priority?: string;
    taskType?: string;
    overdueOnly?: boolean;
  }>({});
  const [active, setActive] = useState<DragPayload | null>(null);
  const [detailTask, setDetailTask] = useState<TaskVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [distribute, setDistribute] = useState<{ task: TaskVo; assigneeId: string } | null>(null);
  const [switchConfirm, setSwitchConfirm] = useState<TaskVo | null>(null);
  const [executorOptions, setExecutorOptions] = useState<{ value: string; label: string }[]>([]);

  const { data: board, isLoading } = useQuery({
    queryKey: ['board', filter],
    queryFn: () => taskApi.board(filter),
  });

  // 人员筛选项：指定项目取该项目成员；全部项目取所有可见项目成员并集（同人去重）
  const { data: memberOptions } = useQuery({
    queryKey: ['boardMembers', filter.projectId, projectList],
    queryFn: async () => {
      const projectIds = filter.projectId ? [filter.projectId] : projectList.map((p) => p.projectId);
      const memberLists = await Promise.all(projectIds.map((id) => projectApi.listMembers(id)));
      const seen = new Set<string>();
      const options: { value: string; label: string }[] = [];
      for (const m of memberLists.flat()) {
        if (seen.has(m.userId)) {
          continue;
        }
        seen.add(m.userId);
        options.push({ value: m.userId, label: m.user?.displayName ?? m.userId });
      }
      return options;
    },
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['board'] });
  };

  async function openDistributeOptions(projectId: string) {
    try {
      const members = await projectApi.listMembers(projectId);
      setExecutorOptions(
        members
          .filter((m) => m.roleList.some((r) => r.permissionList?.includes('pf:task:start')))
          .map((m) => ({ value: m.userId, label: m.user?.displayName ?? m.userId })),
      );
    } catch {
      setExecutorOptions([]);
    }
  }

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));

  function onDragStart(event: DragStartEvent) {
    const task = event.active.data.current?.task as TaskVo | undefined;
    if (!task) {
      return;
    }
    setActive({ task, fromScope: event.active.data.current?.scope as string });
  }

  async function onDragEnd(event: DragEndEvent) {
    setActive(null);
    const { active: a, over } = event;
    if (!over) {
      return;
    }
    const payload = a.data.current as { task: TaskVo; scope: string } | undefined;
    if (!payload) {
      return;
    }
    const overData = over.data.current as { scope?: string; assigneeId?: string; task?: TaskVo } | undefined;
    const overScope = overData?.scope ?? '';

    // ---- 跨列：待分发 → 等待中（触发分发弹窗选执行人） ----
    if (payload.scope === 'pendingDist' && overScope === 'waiting') {
      setDistribute({ task: payload.task, assigneeId: '' });
      void openDistributeOptions(payload.task.projectId);
      return;
    }
    // ---- 跨列：等待中 → 进行中（拖到第 1 位确认切换 / 直接开始） ----
    if (payload.scope === 'waiting' && overScope === 'inProgress') {
      if (payload.task.assigneeId !== user?.id) {
        toast('仅任务执行人本人可开始任务', 'error');
        return;
      }
      setSwitchConfirm(payload.task);
      return;
    }
    // ---- 同列排序 ----
    if (overScope !== payload.scope) {
      return;
    }
    // 待分发队列作用域是单个项目：跨项目视图下列内混排多项目任务，需先筛选到具体项目才能排序
    if (payload.scope === 'pendingDist' && !filter.projectId) {
      toast('请在项目筛选中选择具体项目后再排序待分发任务', 'error');
      return;
    }
    // 解析落点引用（beforeId=插到它前面 / afterId=插到它后面，均空=本优先级桶尾）；落回原位则无需请求
    const scopeList =
      payload.scope === 'pendingDist'
        ? board?.pendingDistList ?? []
        : payload.scope === 'waiting'
          ? board?.waitingList ?? []
          : [];
    const ref = resolveSortRef(scopeList, payload.task.id, overData?.task?.id);
    if (!ref) {
      return;
    }
    // 等待中队列按执行人隔离：落点邻居须与被拖任务同一执行人（桶尾插无邻居不受限，服务端仍兜底）
    if (
      payload.scope === 'waiting' &&
      (ref.beforeId || ref.afterId) &&
      overData?.task &&
      overData.task.assigneeId !== payload.task.assigneeId
    ) {
      toast('仅可在同一执行人的任务之间调整顺序', 'error');
      return;
    }
    const beforeId = ref.beforeId;
    const afterId = ref.afterId;
    // 同优先级桶约束：仅允许桶内调整顺序（前端先行拦截，服务端仍兜底）；桶尾插（均空）恒合法
    if (beforeId || afterId) {
      const remaining = scopeList.filter((t) => t.id !== payload.task.id);
      const priorities = remaining.map((t) => t.priority ?? 'LOW');
      const index = beforeId
        ? remaining.findIndex((t) => t.id === beforeId)
        : remaining.findIndex((t) => t.id === afterId) + 1;
      if (!canDropWithinBucket(priorities, payload.task.priority ?? 'LOW', index)) {
        toast('不允许跨优先级拖拽，仅可在同优先级内调整顺序', 'error');
        return;
      }
    }
    // 乐观更新：在首个 await 前的同步段写缓存，与上方 setActive(null) 同批提交，卡片放下立即到位（不弹回）
    void queryClient.cancelQueries({ queryKey: ['board', filter] });
    const previousBoard = queryClient.getQueryData<BoardVo>(['board', filter]);
    queryClient.setQueryData<BoardVo>(['board', filter], (old) =>
      old ? applyBoardSort(old, payload.scope, payload.task.id, ref) : old,
    );
    try {
      await taskApi.sortRank(payload.task.id, beforeId, afterId);
      refresh();
    } catch (e) {
      if (previousBoard) {
        queryClient.setQueryData(['board', filter], previousBoard);
      }
      toast((e as Error).message, 'error');
      refresh();
    }
  }

  if (projectList.length === 0) {
    return <EmptyHint text="你还没有参与任何项目，请联系管理员将你加入项目。" />;
  }

  return (
    <div className="flex h-full flex-col">
      {/* 筛选栏 */}
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-zinc-200 bg-white px-4 py-2 dark:border-zinc-800 dark:bg-zinc-900">
        {/* 定宽包裹：Input/Select 基础样式自带 w-full，直接传宽度类会被覆盖 */}
        <div className="w-44">
          <Select
            value={filter.projectId ?? ''}
            onChange={(e) => setFilter((f) => ({ ...f, projectId: e.target.value || undefined }))}
            options={[
              { value: '', label: '全部项目' },
              ...projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` })),
            ]}
          />
        </div>
        <div className="w-52">
          <Input
            placeholder="搜索标题/描述/任务编号"
            value={filter.keyword ?? ''}
            onChange={(e) => setFilter((f) => ({ ...f, keyword: e.target.value || undefined }))}
          />
        </div>
        <div className="w-28">
          <Select
            value={filter.priority ?? ''}
            onChange={(e) => setFilter((f) => ({ ...f, priority: e.target.value || undefined }))}
            options={[
              { value: '', label: '全部优先级' },
              { value: 'URGENT', label: '紧急' },
              { value: 'HIGH', label: '高' },
              { value: 'MEDIUM', label: '中' },
              { value: 'LOW', label: '低' },
            ]}
          />
        </div>
        <div className="w-28">
          <Select
            value={filter.taskType ?? ''}
            onChange={(e) => setFilter((f) => ({ ...f, taskType: e.target.value || undefined }))}
            options={[
              { value: '', label: '全部类型' },
              { value: 'REQUIREMENT', label: '需求' },
              { value: 'DEVELOP', label: '开发' },
              { value: 'TEST', label: '测试' },
              { value: 'BUG', label: '缺陷' },
              { value: 'DESIGN', label: '设计' },
              { value: 'OPS', label: '运维' },
            ]}
          />
        </div>
        <div className="w-28">
          <Select
            value={filter.assigneeId ?? ''}
            onChange={(e) => setFilter((f) => ({ ...f, assigneeId: e.target.value || undefined }))}
            options={[{ value: '', label: '全部人员' }, ...(memberOptions ?? [])]}
          />
        </div>
        <label className="flex items-center gap-1 text-xs text-zinc-500">
          <input
            type="checkbox"
            checked={!!filter.overdueOnly}
            onChange={(e) => setFilter((f) => ({ ...f, overdueOnly: e.target.checked || undefined }))}
          />
          仅看逾期
        </label>
        <span className="flex-1" />
        {hasPermission('pf:task:createDraft') && (
          <Button variant="primary" size="sm" onClick={() => setFormOpen(true)}>
            ＋ 新建任务
          </Button>
        )}
      </div>

      {/* 六列看板 */}
      <div className="min-h-0 flex-1 overflow-x-auto p-4">
        {isLoading || !board ? (
          <div className="grid h-full place-items-center text-sm text-zinc-400">加载中…</div>
        ) : (
          <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={(e) => void onDragEnd(e)}>
            <div className="flex h-full min-w-max gap-3">
              <DraftColumn list={board.draftList} onCardClick={setDetailTask} />
              <PlainColumn title="待分发" count={board.pendingDistList.length} scope="pendingDist">
                <SortableColumnTasks
                  scope="pendingDist"
                  tasks={board.pendingDistList}
                  onCardClick={setDetailTask}
                  draggable={hasPermission('pf:task:dragSort')}
                />
              </PlainColumn>
              <PlainColumn title="等待中" count={board.waitingList.length} scope="waiting">
                <SortableColumnTasks scope="waiting" tasks={board.waitingList} onCardClick={setDetailTask} />
              </PlainColumn>
              <PlainColumn title="进行中" count={board.inProgressList.length} scope="inProgress">
                <StaticTasks tasks={board.inProgressList} onCardClick={setDetailTask} />
              </PlainColumn>
              <PlainColumn title="已完成" count={board.completedList.length} scope="done">
                <StaticTasks tasks={board.completedList} onCardClick={setDetailTask} />
              </PlainColumn>
              <PlainColumn title="已关闭" count={board.closedList.length} scope="closed">
                <StaticTasks tasks={board.closedList} onCardClick={setDetailTask} />
              </PlainColumn>
            </div>
            <DragOverlay>
              {active && (
                <div className="w-64 rotate-2 opacity-90">
                  <TaskCard task={active.task} compact />
                </div>
              )}
            </DragOverlay>
          </DndContext>
        )}
      </div>

      <TaskFormDialog open={formOpen} onClose={() => setFormOpen(false)} projectId={filter.projectId} onSaved={refresh} />
      <TaskDrawer task={detailTask} onClose={() => setDetailTask(null)} onChanged={refresh} />

      {/* 分发弹窗（跨列拖入等待中触发） */}
      <Dialog
        open={!!distribute}
        title={`分发任务 ${distribute?.task.taskNo ?? ''}`}
        onClose={() => setDistribute(null)}
        footer={
          <>
            <Button variant="outline" onClick={() => setDistribute(null)}>
              取消
            </Button>
            <Button
              variant="primary"
              disabled={!distribute?.assigneeId}
              onClick={async () => {
                if (!distribute) {
                  return;
                }
                try {
                  await taskApi.distribute(distribute.task.id, distribute.assigneeId);
                  toast('已分发，执行人已收到通知', 'success');
                  setDistribute(null);
                  refresh();
                } catch (e) {
                  toast((e as Error).message, 'error');
                }
              }}
            >
              确认分发
            </Button>
          </>
        }
      >
        <Select
          value={distribute?.assigneeId ?? ''}
          onChange={(e) => setDistribute((d) => (d ? { ...d, assigneeId: e.target.value } : d))}
          options={executorOptions}
          placeholder="选择执行人（须具备开始任务能力）"
        />
      </Dialog>

      {/* 拖到第 1 位确认切换 */}
      <Dialog
        open={!!switchConfirm}
        title="开始新任务"
        onClose={() => setSwitchConfirm(null)}
        footer={
          <>
            <Button variant="outline" onClick={() => setSwitchConfirm(null)}>
              取消
            </Button>
            <Button
              variant="primary"
              onClick={async () => {
                const task = switchConfirm;
                setSwitchConfirm(null);
                if (!task) {
                  return;
                }
                try {
                  // 当前进行中任务切回等待中（保留原排序位）+ 新任务开始
                  const inProgress = board?.inProgressList.filter((t) => t.assigneeId === user?.id) ?? [];
                  if (inProgress.length > 0) {
                    await taskApi.switchBack(inProgress[0].id);
                  }
                  await taskApi.start(task.id);
                  toast('已切换到新任务', 'success');
                  refresh();
                } catch (e) {
                  toast((e as Error).message, 'error');
                  refresh();
                }
              }}
            >
              确认开始
            </Button>
          </>
        }
      >
        <p className="text-sm text-zinc-600 dark:text-zinc-300">
          确认开始「{switchConfirm?.title}」？
          {board?.inProgressList.some((t) => t.assigneeId === user?.id) && (
            <span className="mt-1 block text-xs text-amber-600">当前进行中的任务将切换回等待中（保留原排序位置）。</span>
          )}
        </p>
      </Dialog>
    </div>
  );
}

// ---------------- 列组件 ----------------

function ColumnShell({ title, count, children }: { title: string; count: number; children: ReactNode }) {
  return (
    <div className="flex h-full w-72 shrink-0 flex-col rounded-xl bg-zinc-100 dark:bg-zinc-800/60">
      <div className="flex items-center justify-between px-3 py-2.5">
        <span className="text-xs font-semibold text-zinc-600 dark:text-zinc-300">{title}</span>
        <span className="rounded-full bg-zinc-200 px-1.5 text-[11px] text-zinc-500 dark:bg-zinc-700 dark:text-zinc-400">
          {count}
        </span>
      </div>
      <div className="min-h-0 flex-1 space-y-2 overflow-y-auto px-2 pb-2">{children}</div>
    </div>
  );
}

function PlainColumn({
  title,
  count,
  scope,
  children,
}: {
  title: string;
  count: number;
  scope: string;
  children: ReactNode;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: scope, data: { scope } });
  return (
    <div ref={setNodeRef} className={isOver ? 'ring-2 ring-blue-300 rounded-xl' : undefined}>
      <ColumnShell title={title} count={count}>
        {children}
      </ColumnShell>
    </div>
  );
}

/** 可拖拽排序列表（落点引用由 onDragEnd 按拖拽方向解析；draggable=false 时渲染为静态列表） */
const SortableColumnTasks = memo(function SortableColumnTasks({
  scope,
  tasks,
  onCardClick,
  draggable = true,
}: {
  scope: string;
  tasks: TaskVo[];
  onCardClick: (task: TaskVo) => void;
  draggable?: boolean;
}) {
  const items = useMemo(() => tasks.map((t) => t.id), [tasks]);
  if (!draggable) {
    return <StaticTasks tasks={tasks} onCardClick={onCardClick} />;
  }
  return (
    <SortableContext items={items} strategy={verticalListSortingStrategy}>
      {tasks.map((task) => (
        <SortableTaskCard key={task.id} task={task} scope={scope} onCardClick={onCardClick} />
      ))}
    </SortableContext>
  );
});

const SortableTaskCard = memo(function SortableTaskCard({
  task,
  scope,
  onCardClick,
}: {
  task: TaskVo;
  scope: string;
  onCardClick: (task: TaskVo) => void;
}) {
  const sortable = useSortable({
    id: task.id,
    data: { task, scope },
  });
  const handleCardClick = useCallback(() => onCardClick(task), [onCardClick, task]);
  return (
    <div
      ref={sortable.setNodeRef}
      style={{ transform: CSS.Transform.toString(sortable.transform), transition: sortable.transition }}
      className={clsx(sortable.isDragging && 'opacity-40')}
      {...sortable.attributes}
      {...sortable.listeners}
    >
      <TaskCard task={task} onClick={handleCardClick} />
    </div>
  );
});

/** 静态列表（进行中按开始时间倒序 / 已完成 / 已关闭，不支持拖拽） */
const StaticTasks = memo(function StaticTasks({ tasks, onCardClick }: { tasks: TaskVo[]; onCardClick: (task: TaskVo) => void }) {
  if (tasks.length === 0) {
    return <div className="py-6 text-center text-xs text-zinc-400">暂无任务</div>;
  }
  return (
    <>
      {tasks.map((task) => (
        <TaskCard key={task.id} task={task} onClick={() => onCardClick(task)} />
      ))}
    </>
  );
});

const DraftColumn = memo(function DraftColumn({ list, onCardClick }: { list: TaskVo[]; onCardClick: (task: TaskVo) => void }) {
  return (
    <ColumnShell title="草稿（仅自己可见）" count={list.length}>
      {list.length === 0 ? (
        <div className="py-6 text-center text-xs text-zinc-400">暂无草稿</div>
      ) : (
        list.map((task) => <TaskCard key={task.id} task={task} onClick={() => onCardClick(task)} />)
      )}
    </ColumnShell>
  );
});

function EmptyHint({ text }: { text: string }) {
  return <div className="grid h-full place-items-center text-sm text-zinc-400">{text}</div>;
}
