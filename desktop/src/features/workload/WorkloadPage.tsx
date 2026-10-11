import { useQuery } from '@tanstack/react-query';
import { taskApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { Avatar } from '@/components/ui/Avatar';
import { Badge, PRIORITY_META, STATUS_META } from '@/components/ui/Badge';
import type { CrossProjectWorkloadVo, WorkloadVo } from '@/api/types';

/** 人员负载页：项目内当前任务 + 等待数；跨项目脱敏视图（只有项目名与优先级，无标题字段） */
export function WorkloadPage() {
  const { currentProjectId, hasPermission } = useAuthStore();

  const { data: workload = [], isLoading } = useQuery({
    queryKey: ['workload', currentProjectId],
    queryFn: () => taskApi.workload(currentProjectId!),
    enabled: !!currentProjectId && hasPermission('pf:task:viewWorkload'),
  });

  const { data: masked = [] } = useQuery({
    queryKey: ['workload', 'cross-project'],
    queryFn: () => taskApi.crossProjectWorkload(),
  });

  if (!currentProjectId) {
    return <div className="grid h-full place-items-center text-sm text-zinc-400">暂无项目</div>;
  }

  return (
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-4xl space-y-6">
        {hasPermission('pf:task:viewWorkload') ? (
          <section>
            <h3 className="mb-3 text-sm font-semibold text-zinc-600 dark:text-zinc-300">项目人员负载</h3>
            {isLoading ? (
              <div className="py-10 text-center text-sm text-zinc-400">加载中…</div>
            ) : (
              <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-zinc-200 text-left text-xs text-zinc-400 dark:border-zinc-700">
                      <th className="px-4 py-2.5 font-medium">成员</th>
                      <th className="px-4 py-2.5 font-medium">当前任务</th>
                      <th className="px-4 py-2.5 font-medium">状态</th>
                      <th className="px-4 py-2.5 text-right font-medium">等待中</th>
                    </tr>
                  </thead>
                  <tbody>
                    {workload.map((row) => (
                      <WorkloadRow key={row.userId} row={row} />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ) : (
          <div className="rounded-xl border border-dashed border-zinc-300 p-6 text-center text-sm text-zinc-400 dark:border-zinc-700">
            你没有「查看人员当前任务」权限（pf:task:viewWorkload）
          </div>
        )}

        {hasPermission('pf:task:viewMasked') && (
          <section>
            <h3 className="mb-1 text-sm font-semibold text-zinc-600 dark:text-zinc-300">跨项目负载（脱敏）</h3>
            <p className="mb-3 text-[11px] text-zinc-400">
              出于数据脱敏要求，此处只显示进行中任务所属项目与优先级，不包含任务标题等明细。
            </p>
            {masked.length === 0 ? (
              <div className="py-8 text-center text-sm text-zinc-400">当前没有进行中的跨项目任务</div>
            ) : (
              <div className="flex flex-wrap gap-2">
                {masked.map((row, index) => (
                  <MaskedChip key={`${row.projectId}-${index}`} row={row} />
                ))}
              </div>
            )}
          </section>
        )}
      </div>
    </div>
  );
}

function WorkloadRow({ row }: { row: WorkloadVo }) {
  const task = row.currentTask;
  return (
    <tr className="border-b border-zinc-100 last:border-0 dark:border-zinc-800">
      <td className="px-4 py-2.5">
        <div className="flex items-center gap-2">
          <Avatar name={row.displayName} size={26} />
          <span className="font-medium">{row.displayName}</span>
        </div>
      </td>
      <td className="px-4 py-2.5">
        {task ? (
          <div>
            <span className="font-mono text-[11px] text-zinc-400">{task.taskNo}</span>
            <span className="ml-2">{task.title}</span>
          </div>
        ) : (
          <span className="text-zinc-400">空闲</span>
        )}
      </td>
      <td className="px-4 py-2.5">
        {task ? (
          <div className="flex gap-1">
            <Badge className={STATUS_META[task.taskStatus]?.badge}>{STATUS_META[task.taskStatus]?.label}</Badge>
            {task.priority && <Badge className={PRIORITY_META[task.priority].badge}>{PRIORITY_META[task.priority].label}</Badge>}
          </div>
        ) : (
          '-'
        )}
      </td>
      <td className="px-4 py-2.5 text-right font-mono">{row.waitingCount}</td>
    </tr>
  );
}

function MaskedChip({ row }: { row: CrossProjectWorkloadVo }) {
  const priority = row.priority && PRIORITY_META[row.priority] ? PRIORITY_META[row.priority] : null;
  return (
    <div className="flex items-center gap-2 rounded-full border border-zinc-200 bg-white px-3 py-1.5 text-xs dark:border-zinc-700 dark:bg-zinc-900">
      <span className="font-medium">{row.projectName}</span>
      {priority && <Badge className={priority.badge}>{priority.label}</Badge>}
    </div>
  );
}
