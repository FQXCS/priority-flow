import { useCallback, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import type { MilestoneVo } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { COLUMN_WIDTH, MAX_SCALE, MIN_SCALE, type CanvasColumn } from './planCanvasState';
import { DateColumn } from './DateColumn';
import { MilestoneCard } from './MilestoneCard';
import { useCanvasDrag } from './useCanvasDrag';
import { useCanvasViewport } from './useCanvasViewport';

interface Props {
  projectId: string;
  columns: CanvasColumn[];
  canManage: boolean;
  isLoading: boolean;
  onEdit: (milestone: MilestoneVo) => void;
  onDelete: (milestone: MilestoneVo) => void;
  onAddAt: (date?: string) => void;
  onDropCommit: (id: string, columnKey: string, index: number) => void;
}

/** 里程碑画布：可平移缩放的时间轴虚拟画布，卡片自由拖拽（跨列改期、列内排序） */
export function TimelineCanvas({
  projectId,
  columns,
  canManage,
  isLoading,
  onEdit,
  onDelete,
  onAddAt,
  onDropCommit,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const columnElsRef = useRef(new Map<string, HTMLElement>());
  // 供拖拽落点判定读取最新列数据
  const columnsRef = useRef(columns);
  columnsRef.current = columns;

  const { viewport, viewportRef, zoomAtCenter, moveTo, reset } = useCanvasViewport(containerRef, projectId);

  const drag = useCanvasDrag({
    enabled: canManage,
    containerRef,
    viewportRef,
    columnsRef,
    columnElsRef,
    onDrop: (id, target) => onDropCommit(id, target.columnKey, target.index),
    onClick: (id) => {
      const milestone = columnsRef.current.flatMap((c) => c.items).find((i) => i.id === id);
      if (milestone) {
        onEdit(milestone);
      }
    },
  });

  // 背景空白处拖拽平移
  const panRef = useRef<{ pointerId: number; startX: number; startY: number; tx: number; ty: number } | null>(null);
  const [panning, setPanning] = useState(false);

  const handleBackgroundPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) {
      return;
    }
    e.currentTarget.setPointerCapture(e.pointerId);
    panRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, tx: viewport.tx, ty: viewport.ty };
    setPanning(true);
  };
  const handlePanMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const pan = panRef.current;
    if (!pan || e.pointerId !== pan.pointerId) {
      return;
    }
    moveTo(pan.tx + e.clientX - pan.startX, pan.ty + e.clientY - pan.startY);
  };
  const handlePanEnd = (e: ReactPointerEvent<HTMLDivElement>) => {
    const pan = panRef.current;
    if (!pan || e.pointerId !== pan.pointerId) {
      return;
    }
    panRef.current = null;
    setPanning(false);
  };

  const registerEl = useCallback((key: string, el: HTMLDivElement | null) => {
    if (el) {
      columnElsRef.current.set(key, el);
    } else {
      columnElsRef.current.delete(key);
    }
  }, []);

  const draggingId = drag.drag?.id ?? null;
  const targetKey = drag.drag?.target?.columnKey ?? null;
  const insertIndex = drag.drag?.target?.index ?? null;
  const ghostMilestone = drag.drag ? columns.flatMap((c) => c.items).find((i) => i.id === drag.drag!.id) : undefined;

  return (
    <div
      ref={containerRef}
      className={`relative h-full w-full touch-none select-none overflow-hidden bg-zinc-50 dark:bg-zinc-950 ${
        drag.drag || panning ? 'cursor-grabbing' : 'cursor-grab'
      }`}
      onPointerDown={handleBackgroundPointerDown}
      onPointerMove={(e) => {
        drag.onMove(e);
        handlePanMove(e);
      }}
      onPointerUp={(e) => {
        drag.onEnd(true);
        handlePanEnd(e);
      }}
      onPointerCancel={(e) => {
        drag.onEnd(false);
        handlePanEnd(e);
      }}
    >
      {/* 内容层：translate + scale 虚拟画布 */}
      <div
        className="absolute left-0 top-0"
        style={{ transform: `translate(${viewport.tx}px, ${viewport.ty}px) scale(${viewport.scale})`, transformOrigin: '0 0' }}
      >
        <div className="relative flex w-max gap-6 px-12 pb-16 pt-12">
          {columns.map((col) => (
            <DateColumn
              key={col.key}
              column={col}
              canManage={canManage}
              showAxisLine={!!col.date}
              draggingId={draggingId}
              targetKey={targetKey}
              insertIndex={insertIndex}
              registerEl={registerEl}
              onAdd={onAddAt}
              onEdit={onEdit}
              onDelete={onDelete}
              onCardPointerDown={drag.start}
            />
          ))}
        </div>

        {/* 拖拽幽灵卡：跟随光标，随画布缩放 */}
        {drag.drag && ghostMilestone && (
          <div
            className="pointer-events-none absolute z-50"
            style={{ left: drag.drag.x, top: drag.drag.y, width: COLUMN_WIDTH }}
          >
            <div className="rotate-1 rounded-xl shadow-2xl ring-1 ring-blue-200 dark:ring-blue-800">
              <MilestoneCard milestone={ghostMilestone} canManage={false} />
            </div>
          </div>
        )}
      </div>

      {/* 缩放工具栏 */}
      <div
        className="absolute right-4 top-4 z-20 flex items-center gap-0.5 rounded-xl border border-zinc-200 bg-white/90 p-1 shadow-sm backdrop-blur dark:border-zinc-700 dark:bg-zinc-900/90"
        onPointerDown={(e) => e.stopPropagation()}
      >
        <Button variant="ghost" size="icon" className="h-7 w-7 text-base" title={`缩小（下限 ${MIN_SCALE * 100}%）`} onClick={() => zoomAtCenter(1 / 1.2)}>
          −
        </Button>
        <span className="w-11 text-center text-xs tabular-nums text-zinc-500">{Math.round(viewport.scale * 100)}%</span>
        <Button variant="ghost" size="icon" className="h-7 w-7 text-base" title={`放大（上限 ${MAX_SCALE * 100}%）`} onClick={() => zoomAtCenter(1.2)}>
          ＋
        </Button>
        <span className="mx-1 h-4 w-px bg-zinc-200 dark:bg-zinc-700" />
        <Button variant="ghost" size="sm" title="恢复默认视口" onClick={reset}>
          重置视图
        </Button>
      </div>

      {/* 加载中 */}
      {isLoading && <div className="absolute inset-0 z-10 grid place-items-center text-sm text-zinc-400">加载中…</div>}
    </div>
  );
}
