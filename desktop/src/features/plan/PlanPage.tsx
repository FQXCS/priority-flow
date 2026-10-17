import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { milestoneApi, projectApi } from '@/api';
import type { MilestoneSaveBody } from '@/api';
import { PERMISSIONS, type MilestoneVo, type ProjectMemberVo } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { UNPLANNED_KEY, applyDropToOrder, buildColumns, loadOrder, saveOrder } from './planCanvasState';
import { MilestoneFormDialog } from './MilestoneFormDialog';
import { TimelineCanvas } from './TimelineCanvas';

/** 里程碑页：可拖拽时间轴画布（跨列拖拽 = 改期，列内拖拽 = 排序，顺序仅本地保存） */
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
  // 列内卡片顺序（localStorage 持久化，仅前端本地）
  const [order, setOrder] = useState<Record<string, string[]>>({});

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

  // 切换项目时恢复该项目的本地列顺序
  useEffect(() => {
    setOrder(loadOrder(projectId));
  }, [projectId]);

  const columns = useMemo(() => buildColumns(milestones, order), [milestones, order]);

  const closeDialog = () => setDialog({ open: false, milestone: null });
  const openCreate = () => setDialog({ open: true, milestone: null });
  const openCreateAt = (date?: string) => setDialog({ open: true, milestone: null, prefillDate: date });
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

  /** 拖拽落卡：先写本地列顺序；跨列（含 ↔ 未规划）乐观改期，失败回滚 */
  const handleDropCommit = (id: string, columnKey: string, index: number) => {
    const milestone = milestones.find((m) => m.id === id);
    if (!milestone) {
      return;
    }
    const newDate = columnKey === UNPLANNED_KEY ? undefined : columnKey;
    const prevOrder = order;
    const nextOrder = applyDropToOrder(columns, id, columnKey, index);
    setOrder(nextOrder);
    saveOrder(projectId, nextOrder);

    // 同列（含未规划内）仅本地重排
    if ((milestone.expectedDate ?? undefined) === newDate) {
      return;
    }
    // 乐观更新：在首个 await 前的同步段写缓存，卡片放下立即到位
    void queryClient.cancelQueries({ queryKey: ['milestones', projectId] });
    const prevData = queryClient.getQueryData<MilestoneVo[]>(['milestones', projectId]);
    queryClient.setQueryData<MilestoneVo[]>(['milestones', projectId], (old) =>
      old ? old.map((m) => (m.id === id ? { ...m, expectedDate: newDate } : m)) : old,
    );
    milestoneApi
      .update(projectId, id, {
        name: milestone.name,
        ownerId: milestone.ownerId || undefined,
        expectedDate: newDate,
      })
      .then(() => {
        toast(newDate ? `已改期至 ${newDate}` : '已移入未规划日期', 'success');
        invalidate();
      })
      .catch((e: Error) => {
        if (prevData) {
          queryClient.setQueryData(['milestones', projectId], prevData);
        }
        setOrder(prevOrder);
        saveOrder(projectId, prevOrder);
        toast(e.message, 'error');
      });
  };

  if (projectList.length === 0) {
    return <div className="grid h-full place-items-center text-sm text-zinc-400">暂无项目</div>;
  }

  return (
    <div className="flex h-full flex-col">
      {/* 页头：标题 + 计数 + 提示；右侧项目筛选与新增入口 */}
      <header className="flex shrink-0 flex-wrap items-center justify-between gap-4 border-b border-zinc-200 bg-white px-6 py-4 dark:border-zinc-800 dark:bg-zinc-900">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-xl font-bold text-zinc-800 dark:text-zinc-100">里程碑</h1>
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
              {milestones.length} 项
            </span>
          </div>
          <p className="mt-1 text-xs text-zinc-400">
            拖拽卡片可改期或调整顺序，拖动空白平移画布，Ctrl+滚轮缩放。
          </p>
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

      {/* 画布 */}
      <div className="relative min-h-0 flex-1">
        <TimelineCanvas
          projectId={projectId}
          columns={columns}
          canManage={canManage}
          isLoading={isLoading}
          onEdit={openEdit}
          onDelete={confirmDelete}
          onAddAt={openCreateAt}
          onDropCommit={handleDropCommit}
        />
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
