import clsx from 'clsx';
import type { ReactNode } from 'react';

/** 优先级徽章（含左侧色条语义） */
export const PRIORITY_META: Record<string, { label: string; bar: string; badge: string }> = {
  URGENT: { label: '紧急', bar: 'bg-red-500', badge: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300' },
  HIGH: { label: '高', bar: 'bg-orange-500', badge: 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300' },
  MEDIUM: { label: '中', bar: 'bg-blue-500', badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300' },
  LOW: { label: '低', bar: 'bg-zinc-400', badge: 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300' },
};

export const STATUS_META: Record<string, { label: string; badge: string }> = {
  DRAFT: { label: '草稿', badge: 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300' },
  PENDING_DIST: { label: '待分发', badge: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300' },
  WAITING: { label: '等待中', badge: 'bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300' },
  IN_PROGRESS: { label: '进行中', badge: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300' },
  COMPLETED: { label: '已完成', badge: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' },
  CLOSED: { label: '已关闭', badge: 'bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400' },
};

export const TYPE_LABELS: Record<string, string> = {
  REQUIREMENT: '需求',
  DEVELOP: '开发',
  TEST: '测试',
  BUG: '缺陷',
  DESIGN: '设计',
  OPS: '运维',
  OTHER: '其他',
};

export function Badge({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-medium leading-4',
        className,
      )}
    >
      {children}
    </span>
  );
}
