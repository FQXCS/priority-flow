import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { weeklyReportApi } from '@/api';
import type { WeeklyReportSaveBody } from '@/api';
import { PERMISSIONS, type WeeklyReportVo } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ColumnShell } from '@/features/board/BoardPage';
import {
  composeItemLine,
  formatReportTime,
  groupReportsByProject,
  parseEntries,
  projectColorClass,
  type ReportProjectGroup,
} from './reportUtils';
import { ReportFormDialog } from './ReportFormDialog';

/** 计划周报页：按项目分组的看板（列 = 全部参与项目，列内周期倒序）；生成弹框自动聚合 → 人工编辑确认 → 发布快照 */
export function ReportPage() {
  const { hasPermission, projectList } = useAuthStore();
  const canManage = hasPermission(PERMISSIONS.REPORT_MANAGE);
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<{ open: boolean; report: WeeklyReportVo | null }>({ open: false, report: null });

  const { data: reports = [], isLoading } = useQuery({
    queryKey: ['weekly-reports'],
    queryFn: weeklyReportApi.list,
  });

  const groups = useMemo(() => groupReportsByProject(reports, projectList), [reports, projectList]);

  const closeDialog = () => setDialog({ open: false, report: null });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['weekly-reports'] });

  const saveMutation = useMutation({
    mutationFn: ({ report, body }: { report: WeeklyReportVo | null; body: WeeklyReportSaveBody }) =>
      report
        ? weeklyReportApi.update(report.id, { content: body.content, planContent: body.planContent })
        : weeklyReportApi.create(body),
    onSuccess: (_data, variables) => {
      invalidate();
      toast(variables.report ? '已保存' : '已发布', 'success');
      closeDialog();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => weeklyReportApi.remove(id),
    onSuccess: () => {
      invalidate();
      toast('已删除', 'success');
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  const confirmDelete = (report: WeeklyReportVo) => {
    if (window.confirm(`确定删除「${report.projectName}」${report.periodStart} ~ ${report.periodEnd} 的周报吗？`)) {
      removeMutation.mutate(report.id);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <header className="flex shrink-0 items-center justify-between px-4 py-3">
        <div>
          <h3 className="text-sm font-semibold text-zinc-600 dark:text-zinc-300">计划周报</h3>
          <p className="mt-0.5 text-[11px] text-zinc-400">按项目自动汇总任务看板，发布后为快照记录</p>
        </div>
        {canManage && (
          <Button size="sm" variant="primary" onClick={() => setDialog({ open: true, report: null })}>
            生成周报
          </Button>
        )}
      </header>
      <div className="min-h-0 flex-1 overflow-x-auto px-4 pb-4">
        {isLoading ? (
          <div className="py-16 text-center text-sm text-zinc-400">加载中…</div>
        ) : groups.length === 0 ? (
          <div className="py-16 text-center text-sm text-zinc-400">
            暂无周报{canManage ? '，点击右上角「生成周报」开始' : ''}
          </div>
        ) : (
          <div className="flex h-full min-w-max gap-3">
            {groups.map((group) => (
              <ReportColumn
                key={group.projectId}
                group={group}
                canManage={canManage}
                onEdit={(report) => setDialog({ open: true, report })}
                onDelete={confirmDelete}
              />
            ))}
          </div>
        )}
      </div>
      <ReportFormDialog
        open={dialog.open}
        report={dialog.report}
        reports={reports}
        saving={saveMutation.isPending}
        onSubmit={(payload) => saveMutation.mutate(payload)}
        onClose={closeDialog}
      />
    </div>
  );
}

/** 看板列：列头项目彩色标签 + 数量徽章，列体周报卡片（空列占位） */
function ReportColumn(props: {
  group: ReportProjectGroup;
  canManage: boolean;
  onEdit: (report: WeeklyReportVo) => void;
  onDelete: (report: WeeklyReportVo) => void;
}) {
  const { group, canManage, onEdit, onDelete } = props;
  return (
    <ColumnShell
      title={
        <Badge className={clsx('max-w-[180px]', projectColorClass(group.projectId))}>
          <span className="min-w-0 truncate" title={group.projectName}>
            {group.projectName || '未命名项目'}
          </span>
        </Badge>
      }
      count={group.reports.length}
    >
      {group.reports.length === 0 ? (
        <div className="py-6 text-center text-xs text-zinc-400">暂无周报</div>
      ) : (
        group.reports.map((report) => (
          <ReportCard
            key={report.id}
            report={report}
            canManage={canManage}
            onEdit={() => onEdit(report)}
            onDelete={() => onDelete(report)}
          />
        ))
      )}
    </ColumnShell>
  );
}

function ReportCard(props: { report: WeeklyReportVo; canManage: boolean; onEdit: () => void; onDelete: () => void }) {
  const { report, canManage, onEdit, onDelete } = props;
  return (
    <div className="group rounded-xl border border-zinc-200 bg-white px-3 py-2.5 dark:border-zinc-700 dark:bg-zinc-900">
      <div className="flex items-center gap-2">
        <span className="shrink-0 text-[11px] font-medium text-zinc-400">周报时间</span>
        <span className="min-w-0 truncate text-xs text-zinc-600 dark:text-zinc-300">
          {report.periodStart} ~ {report.periodEnd}
        </span>
        {canManage && (
          <div className="ml-auto hidden shrink-0 gap-1 group-hover:flex">
            <Button size="sm" variant="ghost" onClick={onEdit}>
              编辑
            </Button>
            <Button size="sm" variant="ghost" onClick={onDelete}>
              删除
            </Button>
          </div>
        )}
      </div>
      <FieldBlock title="本周研发进度" items={parseEntries(report.content).map(composeItemLine)} />
      <FieldBlock title="下周计划进度" items={parseEntries(report.planContent).map(composeItemLine)} />
      <div className="mt-2 flex items-center gap-2 text-[11px] text-zinc-400">
        <span className="shrink-0">创建时间</span>
        <span className="min-w-0 truncate">{formatReportTime(report.createTime) || '—'}</span>
      </div>
    </div>
  );
}

/** 字段块：小字标签 + 编号条目列表（1. 2. 3.），空列表占位 */
function FieldBlock(props: { title: string; items: string[] }) {
  const { title, items } = props;
  return (
    <div className="mt-2">
      <div className="text-[11px] font-medium text-zinc-400">{title}</div>
      {items.length === 0 ? (
        <div className="mt-0.5 text-xs text-zinc-400">（无）</div>
      ) : (
        <ol className="mt-0.5 list-decimal space-y-0.5 pl-4 marker:text-zinc-400">
          {items.map((item, index) => (
            <li key={index} className="break-words text-xs text-zinc-600 dark:text-zinc-300">
              {item}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
