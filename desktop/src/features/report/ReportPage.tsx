import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { weeklyReportApi } from '@/api';
import type { WeeklyReportSaveBody } from '@/api';
import { PERMISSIONS, type WeeklyReportVo } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { composeItemLine, parseEntries } from './reportUtils';
import { ReportFormDialog } from './ReportFormDialog';

/** 计划周报页：全部周报按周期倒序平铺；生成弹框自动聚合 → 人工编辑确认 → 发布快照 */
export function ReportPage() {
  const { hasPermission } = useAuthStore();
  const canManage = hasPermission(PERMISSIONS.REPORT_MANAGE);
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<{ open: boolean; report: WeeklyReportVo | null }>({ open: false, report: null });

  const { data: reports = [], isLoading } = useQuery({
    queryKey: ['weekly-reports'],
    queryFn: weeklyReportApi.list,
  });

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
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-3xl">
        <div className="mb-3 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-zinc-600 dark:text-zinc-300">计划周报</h3>
            <p className="mt-0.5 text-[11px] text-zinc-400">按项目自动汇总任务看板，发布后为快照记录</p>
          </div>
          {canManage && (
            <Button size="sm" variant="primary" onClick={() => setDialog({ open: true, report: null })}>
              生成周报
            </Button>
          )}
        </div>
        {isLoading ? (
          <div className="py-16 text-center text-sm text-zinc-400">加载中…</div>
        ) : reports.length === 0 ? (
          <div className="py-16 text-center text-sm text-zinc-400">
            暂无周报{canManage ? '，点击右上角「生成周报」开始' : ''}
          </div>
        ) : (
          <div className="space-y-2">
            {reports.map((report) => (
              <ReportCard
                key={report.id}
                report={report}
                canManage={canManage}
                onEdit={() => setDialog({ open: true, report })}
                onDelete={() => confirmDelete(report)}
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

function ReportCard(props: { report: WeeklyReportVo; canManage: boolean; onEdit: () => void; onDelete: () => void }) {
  const { report, canManage, onEdit, onDelete } = props;
  return (
    <div className="group rounded-xl border border-zinc-200 bg-white px-4 py-3 dark:border-zinc-700 dark:bg-zinc-900">
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium text-zinc-800 dark:text-zinc-100">{report.projectName}</span>
        <span className="text-[11px] text-zinc-400">
          {report.periodStart} ~ {report.periodEnd}
        </span>
        {canManage && (
          <div className="ml-auto hidden gap-1 group-hover:flex">
            <Button size="sm" variant="ghost" onClick={onEdit}>
              编辑
            </Button>
            <Button size="sm" variant="ghost" onClick={onDelete}>
              删除
            </Button>
          </div>
        )}
      </div>
      <ReportSection title="本周进展" items={parseEntries(report.content).map(composeItemLine)} />
      <ReportSection title="下周计划" items={parseEntries(report.planContent).map(composeItemLine)} />
    </div>
  );
}

function ReportSection(props: { title: string; items: string[] }) {
  const { title, items } = props;
  return (
    <div className="mt-2">
      <div className="text-[11px] font-medium text-zinc-400">{title}</div>
      {items.length === 0 ? (
        <div className="mt-0.5 text-xs text-zinc-400">（无）</div>
      ) : (
        <ul className="mt-0.5 space-y-0.5">
          {items.map((item, index) => (
            <li key={index} className="break-words text-xs text-zinc-600 dark:text-zinc-300">
              · {item}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
