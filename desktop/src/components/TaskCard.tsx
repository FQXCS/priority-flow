import { memo } from 'react';
import { clsx } from 'clsx';
import { Badge, PRIORITY_META, TYPE_LABELS } from '@/components/ui/Badge';
import { Avatar } from '@/components/ui/Avatar';
import type { TaskVo } from '@/api/types';

export interface TaskCardProps {
  task: TaskVo;
  onClick?: () => void;
  compact?: boolean;
}

/** 优先级色条 + 任务编号 + 标题 + 标签/执行人/截止时间/队列前序（纯展示组件，拖拽由外层 SortableTaskCard 承担） */
export const TaskCard = memo(function TaskCard({ task, onClick, compact }: TaskCardProps) {
  const priority = task.priority ? PRIORITY_META[task.priority] : null;
  const overdue = isOverdue(task);
  const labels = parseLabels(task.labels);

  return (
    <div
      onClick={onClick}
      className={clsx(
        'group relative cursor-pointer overflow-hidden rounded-lg border border-zinc-200 bg-white shadow-sm transition-shadow hover:shadow-md',
        'dark:border-zinc-700 dark:bg-zinc-800',
        compact ? 'px-2.5 py-2' : 'px-3 py-2.5',
      )}
    >
      {/* 优先级色条 */}
      {priority && <span className={clsx('absolute inset-y-0 left-0 w-1', priority.bar)} />}
      <div className={clsx('flex flex-wrap items-center gap-2', priority && 'pl-1.5')}>
        {task.projectName && (
          <Badge className="bg-zinc-100 text-zinc-500 dark:bg-zinc-700 dark:text-zinc-300">
            {task.projectName}
          </Badge>
        )}
        <span className="font-mono text-[11px] text-zinc-400">{task.taskNo}</span>
        {task.taskType && (
          <Badge className="bg-zinc-100 text-zinc-500 dark:bg-zinc-700 dark:text-zinc-300">
            {TYPE_LABELS[task.taskType] ?? task.taskType}
          </Badge>
        )}
        {(task.waitingAheadCount ?? 0) > 0 && (
          <Badge className="bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300">
            前面还有 {task.waitingAheadCount} 个
          </Badge>
        )}
        {overdue && (
          <Badge className="bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300">已逾期</Badge>
        )}
      </div>
      <div className={clsx('mt-1 line-clamp-2 text-sm font-medium text-zinc-800 dark:text-zinc-100', priority && 'pl-1.5')}>
        {task.title}
      </div>
      {!compact && labels.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1 pl-1.5">
          {labels.map((label) => (
            <Badge key={label} className="bg-sky-50 text-sky-600 dark:bg-sky-900/30 dark:text-sky-300">
              {label}
            </Badge>
          ))}
        </div>
      )}
      {!compact && (
        <div className="mt-2 flex items-center gap-2 pl-1.5">
          {task.assigneeName ? (
            <>
              <Avatar name={task.assigneeName} size={20} />
              <span className="text-xs text-zinc-500 dark:text-zinc-400">{task.assigneeName}</span>
            </>
          ) : (
            <span className="text-xs text-zinc-400">未分配</span>
          )}
          <span className="flex-1" />
          {task.deadline && (
            <span className={clsx('text-[11px]', overdue ? 'text-red-500' : 'text-zinc-400')}>
              {task.deadline.slice(0, 10)}
            </span>
          )}
        </div>
      )}
    </div>
  );
});

export function isOverdue(task: TaskVo): boolean {
  return (
    !!task.deadline &&
    new Date(task.deadline).getTime() < Date.now() &&
    (task.taskStatus === 'WAITING' || task.taskStatus === 'IN_PROGRESS')
  );
}

export function parseLabels(labels?: string | null): string[] {
  if (!labels) {
    return [];
  }
  try {
    return JSON.parse(labels) as string[];
  } catch {
    return [];
  }
}
