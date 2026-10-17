import { memo } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import type { MilestoneVo } from '@/api/types';

interface Props {
  milestone: MilestoneVo;
  canManage: boolean;
  /** 管理权限下卡片 pointerdown 启动画布拖拽（由画布容器接管后续 move/up） */
  onCardPointerDown?: (e: ReactPointerEvent<HTMLDivElement>, id: string) => void;
  onEdit?: (milestone: MilestoneVo) => void;
  onDelete?: (milestone: MilestoneVo) => void;
}

/** 里程碑卡片：名称 + 负责人；管理权限下可拖拽（点击打开编辑），悬停显示编辑/删除 */
export const MilestoneCard = memo(function MilestoneCard({
  milestone,
  canManage,
  onCardPointerDown,
  onEdit,
  onDelete,
}: Props) {
  return (
    <div
      data-card-id={milestone.id}
      className={`group relative w-full touch-none rounded-xl border border-zinc-200 bg-white p-4 shadow-sm transition-all dark:border-zinc-700 dark:bg-zinc-900 ${
        canManage
          ? 'cursor-grab hover:border-blue-300 hover:shadow-md active:cursor-grabbing dark:hover:border-blue-800'
          : ''
      }`}
      onPointerDown={canManage ? (e) => onCardPointerDown?.(e, milestone.id) : undefined}
    >
      {canManage && (
        <div
          className="absolute right-2 top-2 hidden items-center gap-0.5 rounded-lg bg-white/90 p-0.5 shadow-sm backdrop-blur group-hover:flex dark:bg-zinc-800/90"
          onPointerDown={(e) => e.stopPropagation()}
        >
          <button
            className="grid h-6 w-6 place-items-center rounded-md text-xs text-zinc-400 hover:bg-zinc-100 hover:text-blue-600 dark:hover:bg-zinc-700 dark:hover:text-blue-300"
            title="编辑"
            onClick={(e) => {
              e.stopPropagation();
              onEdit?.(milestone);
            }}
          >
            ✎
          </button>
          <button
            className="grid h-6 w-6 place-items-center rounded-md text-xs text-zinc-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400"
            title="删除"
            onClick={(e) => {
              e.stopPropagation();
              onDelete?.(milestone);
            }}
          >
            ✕
          </button>
        </div>
      )}
      <div className="break-words pr-8 text-sm font-semibold leading-5 text-zinc-800 dark:text-zinc-100">
        {milestone.name}
      </div>
      {milestone.ownerName && (
        <div className="mt-2 flex items-center gap-1.5 text-xs text-zinc-400 dark:text-zinc-500">
          <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-blue-400" />
          <span className="truncate">{milestone.ownerName}</span>
        </div>
      )}
    </div>
  );
});
