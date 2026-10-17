import { memo } from 'react';
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react';
import type { MilestoneVo } from '@/api/types';
import { COLUMN_WIDTH, type CanvasColumn } from './planCanvasState';
import { MilestoneCard } from './MilestoneCard';

interface Props {
  column: CanvasColumn;
  canManage: boolean;
  /** 是否向右延伸时间轴主线（最后一个日期列为 false，主线不超出末节点） */
  showAxisLine?: boolean;
  draggingId: string | null;
  targetKey: string | null;
  insertIndex: number | null;
  registerEl: (key: string, el: HTMLDivElement | null) => void;
  onAdd?: (date: string) => void;
  onEdit: (milestone: MilestoneVo) => void;
  onDelete: (milestone: MilestoneVo) => void;
  onCardPointerDown: (e: ReactPointerEvent<HTMLDivElement>, id: string) => void;
}

/** 落点占位虚线框 */
function Placeholder() {
  return (
    <div className="h-16 rounded-xl border-2 border-dashed border-blue-300 bg-blue-50/50 dark:border-blue-700 dark:bg-blue-900/20" />
  );
}

/** 画布单列：日期胶囊（或未规划标题）+ 卡片纵向流 + 拖拽落点占位 */
export const DateColumn = memo(function DateColumn({
  column,
  canManage,
  showAxisLine,
  draggingId,
  targetKey,
  insertIndex,
  registerEl,
  onAdd,
  onEdit,
  onDelete,
  onCardPointerDown,
}: Props) {
  const unplanned = !column.date;
  const [year, month, day] = column.date?.split('-') ?? [];
  // 被拖卡从原列移除（仅幽灵卡跟随光标），落点 index 按该列表计
  const list = draggingId ? column.items.filter((m) => m.id !== draggingId) : column.items;
  const showPlaceholder = draggingId !== null && targetKey === column.key;

  const nodes: ReactNode[] = [];
  list.forEach((item, i) => {
    if (showPlaceholder && i === insertIndex) {
      nodes.push(<Placeholder key="__placeholder" />);
    }
    nodes.push(
      <MilestoneCard
        key={item.id}
        milestone={item}
        canManage={canManage}
        onCardPointerDown={onCardPointerDown}
        onEdit={onEdit}
        onDelete={onDelete}
      />,
    );
  });
  if (showPlaceholder && (insertIndex ?? 0) >= list.length) {
    nodes.push(<Placeholder key="__placeholder" />);
  }
  if (nodes.length === 0) {
    nodes.push(
      <div
        key="__empty"
        className="rounded-xl border-2 border-dashed border-zinc-200 px-3 py-5 text-center text-xs text-zinc-300 dark:border-zinc-800 dark:text-zinc-600"
      >
        将卡片拖到这里
      </div>,
    );
  }

  return (
    <div
      ref={(el) => registerEl(column.key, el)}
      className="relative flex shrink-0 flex-col"
      style={{ width: COLUMN_WIDTH }}
    >
      {/* 时间轴主线段：左端藏于胶囊之下（起于本节点），右端跨过列间距接到下一节点 */}
      {showAxisLine && (
        <div className="absolute -z-10 h-px bg-zinc-200 dark:bg-zinc-700" style={{ top: 32, left: 0, right: -24 }} />
      )}
      {/* 轴行：日期胶囊 / 未规划标题 */}
      <div className="flex h-16 items-center">
        {unplanned ? (
          <div className="flex items-center gap-2.5 rounded-full border border-dashed border-zinc-300 bg-white/70 py-2 pl-4 pr-3 dark:border-zinc-700 dark:bg-zinc-900/70">
            <span className="text-sm font-semibold text-zinc-500 dark:text-zinc-400">未规划</span>
            <span className="grid h-6 min-w-6 place-items-center rounded-full bg-zinc-100 px-1.5 text-xs font-semibold text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
              {column.items.length}
            </span>
          </div>
        ) : (
          <div className="flex items-center gap-3 rounded-full border border-zinc-200 bg-white py-2 pl-4 pr-2 shadow-sm dark:border-zinc-700 dark:bg-zinc-900">
            <div className="leading-tight">
              <div className="text-lg font-bold text-zinc-800 dark:text-zinc-100">
                {month}/{day}
              </div>
              <div className="text-[10px] tracking-wide text-zinc-400">{year}</div>
            </div>
            <span className="grid h-6 min-w-6 place-items-center rounded-full bg-blue-50 px-1.5 text-xs font-semibold text-blue-600 dark:bg-blue-900/40 dark:text-blue-300">
              {column.items.length}
            </span>
            {canManage && (
              <button
                className="grid h-7 w-7 place-items-center rounded-full text-zinc-400 transition-colors hover:bg-blue-50 hover:text-blue-600 dark:hover:bg-blue-900/40 dark:hover:text-blue-300"
                title="在该日期新增里程碑"
                // 阻止冒泡到画布容器：否则容器捕获指针用于平移，按钮收不到 pointerup 导致 click 不触发
                onPointerDown={(e) => e.stopPropagation()}
                onClick={() => onAdd?.(column.date!)}
              >
                ＋
              </button>
            )}
          </div>
        )}
      </div>
      {/* 卡片流 */}
      <div className="flex flex-col gap-3 pt-4">{nodes}</div>
    </div>
  );
});
