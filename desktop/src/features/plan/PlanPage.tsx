import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { milestoneApi, projectApi } from '@/api';
import type { MilestoneSaveBody } from '@/api';
import { PERMISSIONS, type MilestoneVo, type ProjectMemberVo } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';

/** 项目规划页：里程碑水平时间轴（按预计完成日期分组、上下交替排布），未规划日期单独分区 */
export function PlanPage() {
  const { projectList, hasPermission } = useAuthStore();
  const canManage = hasPermission(PERMISSIONS.MILESTONE_MANAGE);
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

  const openCreate = () => setDialog({ open: true, milestone: null });
  const openCreateAt = (date: string) => setDialog({ open: true, milestone: null, prefillDate: date });
  const openEdit = (milestone: MilestoneVo) => setDialog({ open: true, milestone });

  if (projectList.length === 0) {
    return <div className="grid h-full place-items-center text-sm text-zinc-400">暂无项目</div>;
  }

  return (
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-6xl space-y-6">
        {/* 头部：标题 + 计数 + 副标题；右侧项目筛选与新增入口 */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-baseline gap-2">
              <h3 className="text-lg font-bold text-zinc-800 dark:text-zinc-100">项目规划</h3>
              <span className="text-sm text-zinc-400">{milestones.length} 项</span>
            </div>
            <p className="mt-1 text-xs text-zinc-400">先确定阶段目标，安排工作时再关联任务。</p>
          </div>
          <div className="flex items-center gap-2">
            {/* 定宽包裹：Select 基础样式自带 w-full */}
            <div className="w-56">
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
        </div>

        {isLoading ? (
          <div className="py-16 text-center text-sm text-zinc-400">加载中…</div>
        ) : (
          <div className="relative overflow-x-auto pb-2">
            {/* 贯穿时间轴的水平线（列高度对称，轴行恰好在垂直居中位置） */}
            <div className="absolute left-0 right-0 top-1/2 h-px bg-zinc-200 dark:bg-zinc-700" />
            <div className="relative flex min-w-max items-stretch gap-10 px-2">
              {dateGroups.map(([date, items], index) => (
                <DateGroupColumn
                  key={date}
                  date={date}
                  items={items}
                  alternate={index % 2 === 1}
                  canManage={canManage}
                  onAdd={() => openCreateAt(date)}
                  onEdit={openEdit}
                />
              ))}
              {canManage && (
                <div className="flex w-44 shrink-0 flex-col">
                  <div className="h-44" />
                  <div className="flex h-14 items-center">
                    <Button variant="outline" onClick={openCreate}>
                      ＋ 新增里程碑
                    </Button>
                  </div>
                  <div className="h-44" />
                </div>
              )}
              {dateGroups.length === 0 && (
                <div className="flex h-[25.5rem] items-center text-sm text-zinc-400">
                  {milestones.length === 0
                    ? canManage
                      ? '暂无里程碑，点击「新增里程碑」开始规划'
                      : '暂无里程碑'
                    : '暂无已规划日期的里程碑'}
                </div>
              )}
            </div>
          </div>
        )}

        {/* 未规划日期 */}
        {unplanned.length > 0 && (
          <div className="border-t border-zinc-200 pt-4 dark:border-zinc-800">
            <h4 className="text-sm font-bold text-zinc-800 dark:text-zinc-100">未规划日期</h4>
            <div className="mt-3 flex flex-wrap items-start gap-3">
              {unplanned.map((item) => (
                <MilestoneCard key={item.id} milestone={item} canManage={canManage} onEdit={openEdit} />
              ))}
            </div>
          </div>
        )}
      </div>

      <MilestoneFormDialog
        open={dialog.open}
        milestone={dialog.milestone}
        prefillDate={dialog.prefillDate}
        projectId={projectId}
        members={members}
        onClose={() => setDialog({ open: false, milestone: null })}
      />
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
}) {
  const { date, items, alternate, canManage, onAdd, onEdit } = props;
  const [year, month, day] = date.split('-');
  const cards = items.map((item) => (
    <MilestoneCard key={item.id} milestone={item} canManage={canManage} onEdit={onEdit} />
  ));
  return (
    <div className="relative flex w-52 shrink-0 flex-col">
      {/* 上卡片区（偶数组）：贴轴底对齐，超出可滚动 */}
      <div className="h-44 overflow-y-auto pb-4">
        {!alternate && items.length > 0 && (
          <div className="relative ml-4 flex min-h-full flex-col justify-end gap-3 pl-4">
            <span className="absolute bottom-0 left-0 top-0 w-px bg-zinc-200 dark:bg-zinc-700" />
            {cards}
          </div>
        )}
      </div>
      {/* 轴行 */}
      <div className="flex h-14 items-center">
        <div className="flex items-center gap-2 rounded-full border border-zinc-200 bg-white px-3 py-1.5 shadow-sm dark:border-zinc-700 dark:bg-zinc-900">
          <div className="leading-tight">
            <div className="text-sm font-semibold text-zinc-800 dark:text-zinc-100">{month}/{day}</div>
            <div className="text-[10px] text-zinc-400">{year}</div>
          </div>
          <span className="grid h-5 min-w-5 place-items-center rounded-full bg-blue-50 px-1 text-[11px] font-medium text-blue-600 dark:bg-blue-900/40 dark:text-blue-300">
            {items.length}
          </span>
          {canManage && (
            <button
              className="grid h-5 w-5 place-items-center rounded-full text-zinc-400 hover:bg-zinc-100 hover:text-blue-600 dark:hover:bg-zinc-800"
              title="在该日期新增里程碑"
              onClick={onAdd}
            >
              ＋
            </button>
          )}
        </div>
      </div>
      {/* 下卡片区（奇数组）：贴轴顶对齐 */}
      <div className="h-44 overflow-y-auto pt-4">
        {alternate && items.length > 0 && (
          <div className="relative ml-4 flex flex-col gap-3 pl-4">
            <span className="absolute bottom-0 left-0 top-0 w-px bg-zinc-200 dark:bg-zinc-700" />
            {cards}
          </div>
        )}
      </div>
    </div>
  );
}

/** 里程碑卡片：名称 + 负责人；有管理权限时点击编辑 */
function MilestoneCard(props: {
  milestone: MilestoneVo;
  canManage: boolean;
  onEdit: (milestone: MilestoneVo) => void;
}) {
  const { milestone, canManage, onEdit } = props;
  return (
    <div
      className={`w-40 shrink-0 rounded-lg border border-zinc-200 bg-white p-3 shadow-sm transition-shadow dark:border-zinc-700 dark:bg-zinc-900 ${
        canManage ? 'cursor-pointer hover:shadow-md' : ''
      }`}
      onClick={canManage ? () => onEdit(milestone) : undefined}
    >
      <div className="break-words text-sm font-semibold text-zinc-800 dark:text-zinc-100">{milestone.name}</div>
      <div className="mt-2 flex items-center gap-1.5 text-xs text-zinc-500 dark:text-zinc-400">
        <Avatar name={milestone.ownerName} size={20} />
        <span className="truncate">{milestone.ownerName ?? '未指定负责人'}</span>
      </div>
    </div>
  );
}

/** 新增/编辑共用弹窗：编辑模式额外提供删除入口 */
function MilestoneFormDialog(props: {
  open: boolean;
  milestone: MilestoneVo | null;
  prefillDate?: string;
  projectId: string;
  members: ProjectMemberVo[];
  onClose: () => void;
}) {
  const { open, milestone, prefillDate, projectId, members, onClose } = props;
  const queryClient = useQueryClient();
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

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['milestones'] });

  const saveMut = useMutation({
    mutationFn: (body: MilestoneSaveBody) =>
      editing && milestone
        ? milestoneApi.update(projectId, milestone.id, body)
        : milestoneApi.create(projectId, body),
    onSuccess: () => {
      invalidate();
      toast(editing ? '已保存' : '已创建', 'success');
      onClose();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  const removeMut = useMutation({
    mutationFn: () => milestoneApi.remove(projectId, milestone!.id),
    onSuccess: () => {
      invalidate();
      toast('已删除', 'success');
      onClose();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  const handleSave = () => {
    if (!name.trim()) {
      toast('目标名称不能为空', 'error');
      return;
    }
    saveMut.mutate({
      name: name.trim(),
      ownerId: ownerId || undefined,
      expectedDate: expectedDate || undefined,
    });
  };

  const handleDelete = () => {
    if (!milestone || !window.confirm(`确定删除里程碑「${milestone.name}」吗？`)) {
      return;
    }
    removeMut.mutate();
  };

  return (
    <Dialog
      open={open}
      title={editing ? '编辑里程碑' : '新增里程碑'}
      onClose={onClose}
      footer={
        <>
          {editing && (
            <Button variant="danger" className="mr-auto" disabled={removeMut.isPending} onClick={handleDelete}>
              删除
            </Button>
          )}
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button variant="primary" disabled={saveMut.isPending} onClick={handleSave}>
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
