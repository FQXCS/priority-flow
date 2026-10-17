import { useCallback, useRef, useState } from 'react';
import type { MutableRefObject, PointerEvent as ReactPointerEvent, RefObject } from 'react';
import type { CanvasColumn, CanvasViewport } from './planCanvasState';

/** 拖拽落点：目标列 key + 列内插入位（按不含被拖卡的列表计） */
export interface DragTarget {
  columnKey: string;
  index: number;
}

/** 拖拽中状态：幽灵卡画布坐标 + 当前落点 */
export interface DragState {
  id: string;
  x: number;
  y: number;
  target: DragTarget | null;
}

interface Options {
  enabled: boolean;
  containerRef: RefObject<HTMLDivElement | null>;
  viewportRef: MutableRefObject<CanvasViewport>;
  columnsRef: MutableRefObject<CanvasColumn[]>;
  columnElsRef: MutableRefObject<Map<string, HTMLElement>>;
  onDrop: (id: string, target: DragTarget) => void;
  /** 位移小于阈值松开视为点击（打开编辑） */
  onClick: (id: string) => void;
}

/** 屏幕像素位移阈值，小于该值视为点击而非拖拽 */
const DRAG_THRESHOLD = 4;

/**
 * 画布卡片拖拽状态机（原生 pointer 事件，不依赖 dnd-kit）：
 * 卡片 pointerdown 里调 start（内部把指针 capture 到画布容器，避免卡片被移出 DOM 后丢事件），
 * move/up 统一由画布容器转发到 onMove/onEnd。
 */
export function useCanvasDrag(opts: Options) {
  const optsRef = useRef(opts);
  optsRef.current = opts;
  const [drag, setDrag] = useState<DragState | null>(null);
  const dragRef = useRef<DragState | null>(null);
  const sessionRef = useRef<{
    pointerId: number;
    id: string;
    startClientX: number;
    startClientY: number;
    grabX: number;
    grabY: number;
    moved: boolean;
  } | null>(null);

  /** 屏幕坐标 → 画布内容坐标 */
  const toCanvas = useCallback((clientX: number, clientY: number) => {
    const { containerRef, viewportRef } = optsRef.current;
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) {
      return { x: 0, y: 0 };
    }
    const v = viewportRef.current;
    return { x: (clientX - rect.left - v.tx) / v.scale, y: (clientY - rect.top - v.ty) / v.scale };
  }, []);

  /** 落点判定：x 归属最近列（列中心距离），y 与列内各卡实测中线比较得插入位 */
  const findDropTarget = useCallback(
    (x: number, y: number, draggingId: string): DragTarget | null => {
      const { columnsRef, columnElsRef, viewportRef } = optsRef.current;
      const scale = viewportRef.current.scale;
      let bestKey: string | null = null;
      let bestDist = Number.POSITIVE_INFINITY;
      for (const col of columnsRef.current) {
        const el = columnElsRef.current.get(col.key);
        if (!el) {
          continue;
        }
        const rect = el.getBoundingClientRect();
        const centerX = toCanvas(rect.left, rect.top).x + rect.width / scale / 2;
        const dist = Math.abs(centerX - x);
        if (dist < bestDist) {
          bestDist = dist;
          bestKey = col.key;
        }
      }
      if (!bestKey) {
        return null;
      }
      const el = columnElsRef.current.get(bestKey)!;
      const cards = Array.from(el.querySelectorAll<HTMLElement>('[data-card-id]')).filter(
        (c) => c.dataset.cardId !== draggingId,
      );
      let index = cards.length;
      for (let i = 0; i < cards.length; i++) {
        const rect = cards[i].getBoundingClientRect();
        const midY = toCanvas(rect.left, rect.top + rect.height / 2).y;
        if (y < midY) {
          index = i;
          break;
        }
      }
      return { columnKey: bestKey, index };
    },
    [toCanvas],
  );

  /** 卡片 pointerdown 入口：记录会话并把指针捕获到画布容器 */
  const start = useCallback(
    (e: ReactPointerEvent<HTMLElement>, id: string) => {
      const o = optsRef.current;
      if (!o.enabled || e.button !== 0) {
        return;
      }
      // 不触发画布背景平移
      e.stopPropagation();
      // 合成指针事件（自动化测试）无活跃指针会抛异常；真实指针始终可捕获
      try {
        o.containerRef.current?.setPointerCapture(e.pointerId);
      } catch {
        // 忽略：无捕获时 move/up 事件按常规冒泡到画布容器
      }
      const point = toCanvas(e.clientX, e.clientY);
      const cardRect = e.currentTarget.getBoundingClientRect();
      const origin = toCanvas(cardRect.left, cardRect.top);
      sessionRef.current = {
        pointerId: e.pointerId,
        id,
        startClientX: e.clientX,
        startClientY: e.clientY,
        grabX: point.x - origin.x,
        grabY: point.y - origin.y,
        moved: false,
      };
    },
    [toCanvas],
  );

  /** 画布容器 pointermove 转发入口（无会话时忽略） */
  const onMove = useCallback(
    (e: ReactPointerEvent<HTMLElement>) => {
      const session = sessionRef.current;
      if (!session || e.pointerId !== session.pointerId) {
        return;
      }
      if (!session.moved) {
        const dx = e.clientX - session.startClientX;
        const dy = e.clientY - session.startClientY;
        if (Math.hypot(dx, dy) < DRAG_THRESHOLD) {
          return;
        }
        session.moved = true;
      }
      const point = toCanvas(e.clientX, e.clientY);
      const next: DragState = {
        id: session.id,
        x: point.x - session.grabX,
        y: point.y - session.grabY,
        target: findDropTarget(point.x, point.y, session.id),
      };
      dragRef.current = next;
      setDrag(next);
    },
    [toCanvas, findDropTarget],
  );

  /** 画布容器 pointerup / pointercancel 转发入口；commit=false 时放弃落点 */
  const onEnd = useCallback(
    (commit: boolean) => {
      const session = sessionRef.current;
      sessionRef.current = null;
      if (!session) {
        return;
      }
      const state = dragRef.current;
      dragRef.current = null;
      setDrag(null);
      if (!session.moved) {
        optsRef.current.onClick(session.id);
        return;
      }
      if (commit && state?.target) {
        optsRef.current.onDrop(session.id, state.target);
      }
    },
    [],
  );

  return { drag, start, onMove, onEnd };
}
