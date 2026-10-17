import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { milestoneApi, projectApi } from '@/api';
import type { MilestoneSaveBody } from '@/api';
import { PERMISSIONS, type MilestoneVo, type ProjectMemberVo } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';

/** 里程碑页：里程碑时间轴（按预计完成日期分组、上下交替），未规划日期收纳在面板底部 */
export function PlanPage() {
  const { projectList, hasPermission } = useAuthStore();
  const canManage = hasPermission(PERMISSIONS.MILESTONE_MANAGE);
  const queryClient = useQueryClient();

  // 项目上下文页内自选，默认第一个项目
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const projectId = selectedProjectId || projectList[0]?.projectId || '';

  const [dialog, setDialog] = useState<{ open: boolean; milestone: MilestoneVo | null; prefillDate?: string }>({
    open: false,
    milestone: null,
  });

  const { data: milestones = [], isLoading } = useQuery({
    queryKey: ['milestones', projectId],
    queryFn: () => milestoneApi.list(projectId),
    enabled: !!projectId,
  });

  // 成员列表仅弹窗使用（负责人下拉）
  const { data: members = [] } = useQuery({
    queryKey: ['plan-members', projectId],
    queryFn: () => projectApi.listMembers(projectId),
    enabled: dialog.open && !!projectId,
  });

  // 有日期的按日期升序分组（后端已排序，Map 保序）；无日期进未规划区
  const dateGroups = useMemo(() => {
    const map = new Map<string, MilestoneVo[]>();
    for (const item of milestones) {
      if (!item.expectedDate) {
        continue;
      }
      const list = map.get(item.expectedDate) ?? [];
      list.push(item);
      map.set(item.expectedDate, list);
    }
    return Array.from(map.entries());
  }, [milestones]);
  const unplanned = useMemo(() => milestones.filter((item) => !item.expectedDate), [milestones]);

  const closeDialog = () => setDialog({ open: false, milestone: null });
  const openCreate = () => setDialog({ open: true, milestone: null });
  const openCreateAt = (date: string) => setDialog({ open: true, milestone: null, prefillDate: date });
  const openEdit = (milestone: MilestoneVo) => setDialog({ open: true, milestone });

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['milestones'] });

  const saveMutation = useMutation({
    mutationFn: ({ milestoneId, body }: { milestoneId?: string; body: MilestoneSaveBody }) =>
      milestoneId ? milestoneApi.update(projectId, milestoneId, body) : milestoneApi.create(projectId, body),
    onSuccess: (_data, variables) => {
      invalidate();
      toast(variables.milestoneId ? '已保存' : '已创建', 'success');
      closeDialog();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  const removeMutation = useMutation({
    mutationFn: (milestoneId: string) => milestoneApi.remove(projectId, milestoneId),
    onSuccess: () => {
      invalidate();
      toast('已删除', 'success');
      closeDialog();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  const confirmDelete = (milestone: MilestoneVo) => {
    if (window.confirm(`确定删除里程碑「${milestone.name}」吗？`)) {
      removeMutation.mutate(milestone.id);
    }
  };

  if (projectList.length === 0) {
    return <div className="grid h-full place-items-center text-sm text-zinc-400">暂无项目</div>;
  }

  return (
    <div className="h-full overflow-y-auto p-6">
      <div className="mx-auto max-w-7xl space-y-5">
        {/* 页头：标题 + 计数 + 副标题；右侧项目筛选与新增入口 */}
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-2xl font-bold text-zinc-800 dark:text-zinc-100">里程碑</h1>
              <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
                {milestones.length} 项
              </span>
            </div>
            <p className="mt-1.5 text-sm text-zinc-400">先确定阶段目标，安排工作时再关联任务。</p>
          </div>
          <div className="flex items-center gap-2">
            {/* 定宽包裹：Select 基础样式自带 w-full */}
            <div className="w-60">
              <Select
                value={projectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                options={projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` }))}
              />
            </div>
            {canManage && (
              <Button variant="primary" onClick={openCreate}>
                ＋ 新增里程碑
              </Button>
            )}
          </div>
        </header>

        {/* 时间轴面板：上半部横向时间轴，下半部未规划日期收纳区 */}
        <section className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
          {isLoading ? (
            <div className="py-20 text-center text-sm text-zinc-400">加载中…</div>
          ) : milestones.length === 0 ? (
            <EmptyState canManage={canManage} onCreate={openCreate} />
          ) : (
            <>
              {dateGroups.length > 0 ? (
                <div className="overflow-x-auto">
                  <div className="relative flex min-w-max items-stretch gap-8 px-6 py-8">
                    {/* 贯穿时间轴的水平线（上下卡片区等高，轴行恰在内容垂直居中） */}
                    <div className="absolute left-6 right-6 top-1/2 h-px bg-zinc-200 dark:bg-zinc-700" />
                    {dateGroups.map(([date, items], index) => (
                      <DateGroupColumn
                        key={date}
                        date={date}
                        items={items}
                        alternate={index % 2 === 1}
                        canManage={canManage}
                        onAdd={() => openCreateAt(date)}
                        onEdit={openEdit}
                        onDelete={confirmDelete}
                      />
                    ))}
                  </div>
                </div>
              ) : (
                <div className="px-6 py-10 text-center text-sm text-zinc-400">暂无已规划日期的里程碑</div>
              )}

              {/* 未规划日期 */}
              <div className="border-t border-zinc-100 px-6 py-5 dark:border-zinc-800">
                <div className="flex items-center gap-2">
                  <h2 className="text-sm font-semibold text-zinc-700 dark:text-zinc-200">未规划日期</h2>
                  <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] font-medium text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
                    {unplanned.length}
                  </span>
                </div>
                {unplanned.length > 0 ? (
                  <div className="mt-3 flex flex-wrap items-start gap-3">
                    {unplanned.map((item) => (
                      <div key={item.id} className="w-60">
                        <MilestoneCard
                          milestone={item}
                          canManage={canManage}
                          onEdit={openEdit}
                          onDelete={confirmDelete}
                        />
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="mt-3 text-xs text-zinc-400">暂无未规划日期的里程碑</p>
                )}
              </div>
            </>
          )}
        </section>
      </div>

      <MilestoneFormDialog
        open={dialog.open}
        milestone={dialog.milestone}
        prefillDate={dialog.prefillDate}
        members={members}
        saving={saveMutation.isPending}
        deleting={removeMutation.isPending}
        onSave={(body) => saveMutation.mutate({ milestoneId: dialog.milestone?.id, body })}
        onDelete={() => dialog.milestone && confirmDelete(dialog.milestone)}
        onClose={closeDialog}
      />
    </div>
  );
}

/** 无里程碑时的空状态面板 */
function EmptyState({ canManage, onCreate }: { canManage: boolean; onCreate: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-20 text-center">
      <div className="grid h-12 w-12 place-items-center rounded-full bg-blue-50 text-xl text-blue-500 dark:bg-blue-900/30 dark:text-blue-300">
        ⚑
      </div>
      <p className="mt-1 text-sm font-medium text-zinc-700 dark:text-zinc-200">还没有里程碑</p>
      <p className="text-xs text-zinc-400">
        {canManage ? '先确定阶段目标，安排团队工作时再关联任务' : '暂无可见的里程碑'}
      </p>
      {canManage && (
        <Button variant="primary" className="mt-2" onClick={onCreate}>
          创建第一个里程碑
        </Button>
      )}
    </div>
  );
}

/** 单个日期分组列：卡片区（上下交替）+ 日期节点（日期/年份/数量徽章/快捷新增） */
function DateGroupColumn(props: {
  date: string;
  items: MilestoneVo[];
  alternate: boolean;
  canManage: boolean;
  onAdd: () => void;
  onEdit: (milestone: MilestoneVo) => void;
  onDelete: (milestone: MilestoneVo) => void;
}) {
  const { date, items, alternate, canManage, onAdd, onEdit, onDelete } = props;
  const [year, month, day] = date.split('-');
  const cards = items.map((item) => (
    <MilestoneCard key={item.id} milestone={item} canManage={canManage} onEdit={onEdit} onDelete={onDelete} />
  ));
  return (
    <div className="relative flex w-72 shrink-0 flex-col">
      {/* 上卡片区（偶数组）：贴轴底对齐，超出可滚动 */}
      <div className="h-64 overflow-y-auto pb-5">
        {!alternate && (
          <div className="relative ml-5 flex min-h-full flex-col justify-end gap-3 pl-5">
            <span className="absolute bottom-0 left-0 top-0 w-px bg-zinc-200 dark:bg-zinc-700" />
            {cards}
          </div>
        )}
      </div>
      {/* 轴行：日期节点 */}
      <div className="flex h-16 items-center">
        <div className="flex items-center gap-3 rounded-full border border-zinc-200 bg-white py-2 pl-4 pr-2 shadow-sm dark:border-zinc-700 dark:bg-zinc-900">
          <div className="leading-tight">
            <div className="text-lg font-bold text-zinc-800 dark:text-zinc-100">
              {month}/{day}
            </div>
            <div className="text-[10px] tracking-wide text-zinc-400">{year}</div>
          </div>
          <span className="grid h-6 min-w-6 place-items-center rounded-full bg-blue-50 px-1.5 text-xs font-semibold text-blue-600 dark:bg-blue-900/40 dark:text-blue-300">
            {items.length}
          </span>
          {canManage && (
            <button
              className="grid h-7 w-7 place-items-center rounded-full text-zinc-400 transition-colors hover:bg-blue-50 hover:text-blue-600 dark:hover:bg-blue-900/40 dark:hover:text-blue-300"
              title="在该日期新增里程碑"
              onClick={onAdd}
            >
              ＋
            </button>
          )}
        </div>
      </div>
      {/* 下卡片区（奇数组）：贴轴顶对齐 */}
      <div className="h-64 overflow-y-auto pt-5">
        {alternate && (
          <div className="relative ml-5 flex flex-col gap-3 pl-5">
            <span className="absolute bottom-0 left-0 top-0 w-px bg-zinc-200 dark:bg-zinc-700" />
            {cards}
          </div>
        )}
      </div>
    </div>
  );
}

/** 里程碑卡片：仅展示名称；管理权限下悬停显示编辑/删除，点击整卡编辑 */
function MilestoneCard(props: {
  milestone: MilestoneVo;
  canManage: boolean;
  onEdit: (milestone: MilestoneVo) => void;
  onDelete: (milestone: MilestoneVo) => void;
}) {
  const { milestone, canManage, onEdit, onDelete } = props;
  return (
    <div
      className={`group relative w-full rounded-xl border border-zinc-200 bg-white p-4 shadow-sm transition-all dark:border-zinc-700 dark:bg-zinc-900 ${
        canManage ? 'cursor-pointer hover:border-blue-300 hover:shadow-md dark:hover:border-blue-800' : ''
      }`}
      onClick={canManage ? () => onEdit(milestone) : undefined}
    >
      {canManage && (
        <div className="absolute right-2 top-2 hidden items-center gap-0.5 rounded-lg bg-white/90 p-0.5 shadow-sm backdrop-blur group-hover:flex dark:bg-zinc-800/90">
          <button
            className="grid h-6 w-6 place-items-center rounded-md text-xs text-zinc-400 hover:bg-zinc-100 hover:text-blue-600 dark:hover:bg-zinc-700 dark:hover:text-blue-300"
            title="编辑"
            onClick={(e) => {
              e.stopPropagation();
              onEdit(milestone);
            }}
          >
            ✎
          </button>
          <button
            className="grid h-6 w-6 place-items-center rounded-md text-xs text-zinc-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400"
            title="删除"
            onClick={(e) => {
              e.stopPropagation();
              onDelete(milestone);
            }}
          >
            ✕
          </button>
        </div>
      )}
      <div className="break-words pr-8 text-sm font-semibold leading-5 text-zinc-800 dark:text-zinc-100">
        {milestone.name}
      </div>
    </div>
  );
}

/** 新增/编辑共用弹窗：编辑模式额外提供删除入口 */
function MilestoneFormDialog(props: {
  open: boolean;
  milestone: MilestoneVo | null;
  prefillDate?: string;
  members: ProjectMemberVo[];
  saving: boolean;
  deleting: boolean;
  onSave: (body: MilestoneSaveBody) => void;
  onDelete: () => void;
  onClose: () => void;
}) {
  const { open, milestone, prefillDate, members, saving, deleting, onSave, onDelete, onClose } = props;
  const editing = !!milestone;

  const [name, setName] = useState('');
  const [ownerId, setOwnerId] = useState('');
  const [expectedDate, setExpectedDate] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    setName(milestone?.name ?? '');
    setOwnerId(milestone?.ownerId ?? '');
    setExpectedDate(milestone?.expectedDate ?? prefillDate ?? '');
  }, [open, milestone, prefillDate]);

  const handleSave = () => {
    if (!name.trim()) {
      toast('目标名称不能为空', 'error');
      return;
    }
    onSave({
      name: name.trim(),
      ownerId: ownerId || undefined,
      expectedDate: expectedDate || undefined,
    });
  };

  return (
    <Dialog
      open={open}
      title={editing ? '编辑里程碑' : '新增里程碑'}
      onClose={onClose}
      footer={
        <>
          {editing && (
            <Button variant="danger" className="mr-auto" disabled={deleting} onClick={onDelete}>
              删除
            </Button>
          )}
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button variant="primary" disabled={saving} onClick={handleSave}>
            {editing ? '保存修改' : '创建里程碑'}
          </Button>
        </>
      }
    >
      {editing && <div className="mb-3 text-sm text-zinc-400">{milestone?.name}</div>}
      <label className="mb-1 block text-xs text-zinc-500">目标名称 *</label>
      <Input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="例如：完成项目联调验收"
        maxLength={255}
      />
      <label className="mb-1 mt-3 block text-xs text-zinc-500">负责人</label>
      <Select
        value={ownerId}
        onChange={(e) => setOwnerId(e.target.value)}
        options={[
          { value: '', label: '未指定' },
          ...members.map((m) => ({ value: m.userId, label: m.user?.displayName ?? m.userId })),
        ]}
      />
      <label className="mb-1 mt-3 block text-xs text-zinc-500">预计完成日期</label>
      <Input type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} />
      <div className="mt-1 text-xs text-zinc-400">留空将放入未规划日期</div>
    </Dialog>
  );
}
