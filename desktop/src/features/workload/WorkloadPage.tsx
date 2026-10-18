import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { taskApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { Avatar } from '@/components/ui/Avatar';
import { Badge, PRIORITY_META } from '@/components/ui/Badge';
import { Select } from '@/components/ui/Select';
import type { WorkloadVo } from '@/api/types';

/** 人员负载页：页内选择项目查看成员当前任务、所属项目与优先级 */
export function WorkloadPage() {
  const { projectList, hasPermission } = useAuthStore();
  // 项目上下文页内自选，默认第一个项目
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const projectId = selectedProjectId || projectList[0]?.projectId || '';

  const { data: workload = [], isLoading } = useQuery({
    queryKey: ['workload', projectId],
    queryFn: () => taskApi.workload(projectId),
    enabled: !!projectId && hasPermission('pf:task:viewWorkload'),
  });

  if (projectList.length === 0) {
    return <div className="grid h-full place-items-center text-sm text-zinc-400">暂无项目</div>;
  }

  return (
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-4xl space-y-6">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-zinc-600 dark:text-zinc-300">人员负载</h3>
          {/* 定宽包裹：Select 基础样式自带 w-full */}
          <div className="w-56">
            <Select
              value={projectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              options={projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` }))}
            />
          </div>
        </div>
        {hasPermission('pf:task:viewWorkload') ? (
          <section>
            {isLoading ? (
              <div className="py-10 text-center text-sm text-zinc-400">加载中…</div>
            ) : (
              <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-zinc-200 text-left text-xs text-zinc-400 dark:border-zinc-700">
                      <th className="px-4 py-2.5 font-medium">成员</th>
                      <th className="px-4 py-2.5 font-medium">所属项目</th>
                      <th className="px-4 py-2.5 font-medium">当前任务</th>
                      <th className="px-4 py-2.5 font-medium">优先级</th>
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
      <td className="px-4 py-2.5 text-zinc-500">{task?.projectName ?? '-'}</td>
      <td className="px-4 py-2.5">
        {task ? (
          <span className="block max-w-[24rem] truncate" title={task.title}>
            {task.title}
          </span>
        ) : (
          <span className="text-zinc-400">空闲</span>
        )}
      </td>
      <td className="px-4 py-2.5">
        {task?.priority ? (
          <Badge className={PRIORITY_META[task.priority].badge}>{PRIORITY_META[task.priority].label}</Badge>
        ) : (
          '-'
        )}
      </td>
    </tr>
  );
}
