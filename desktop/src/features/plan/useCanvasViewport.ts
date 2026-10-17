import { useCallback, useEffect, useRef, useState } from 'react';
import type { RefObject } from 'react';
import {
  DEFAULT_VIEWPORT,
  MAX_SCALE,
  MIN_SCALE,
  loadViewport,
  saveViewport,
  type CanvasViewport,
} from './planCanvasState';

/**
 * 画布视口：translate + scale 状态驱动（transformOrigin 固定 0 0），
 * 提供平移、以光标为锚点的缩放与 localStorage 持久化（按项目区分）。
 */
export function useCanvasViewport(containerRef: RefObject<HTMLDivElement | null>, projectId: string) {
  const [viewport, setViewport] = useState<CanvasViewport>(DEFAULT_VIEWPORT);
  // 供拖拽等非 React 路径同步读取最新视口
  const viewportRef = useRef(viewport);
  viewportRef.current = viewport;

  // 切换项目时恢复该项目的本地视口；projectId 为空（项目列表未水合）时不动，避免覆盖用户已调整的视口
  useEffect(() => {
    if (!projectId) {
      return;
    }
    setViewport(loadViewport(projectId) ?? DEFAULT_VIEWPORT);
  }, [projectId]);

  /** 以屏幕坐标 (clientX, clientY) 为锚点缩放 factor 倍，保持光标下的画布点不动 */
  const zoomAt = useCallback(
    (clientX: number, clientY: number, factor: number) => {
      setViewport((v) => {
        const rect = containerRef.current?.getBoundingClientRect();
        if (!rect) {
          return v;
        }
        const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, v.scale * factor));
        if (scale === v.scale) {
          return v;
        }
        const px = clientX - rect.left;
        const py = clientY - rect.top;
        return {
          scale,
          tx: px - (px - v.tx) * (scale / v.scale),
          ty: py - (py - v.ty) * (scale / v.scale),
        };
      });
    },
    [containerRef],
  );

  /** 以容器中心为锚点缩放（工具栏按钮用） */
  const zoomAtCenter = useCallback(
    (factor: number) => {
      const rect = containerRef.current?.getBoundingClientRect();
      if (!rect) {
        return;
      }
      zoomAt(rect.left + rect.width / 2, rect.top + rect.height / 2, factor);
    },
    [containerRef, zoomAt],
  );

  /** 绝对平移到 (tx, ty) */
  const moveTo = useCallback((tx: number, ty: number) => {
    setViewport((v) => (v.tx === tx && v.ty === ty ? v : { ...v, tx, ty }));
  }, []);

  const reset = useCallback(() => setViewport(DEFAULT_VIEWPORT), []);

  // 视口防抖持久化
  useEffect(() => {
    const timer = window.setTimeout(() => saveViewport(projectId, viewport), 300);
    return () => window.clearTimeout(timer);
  }, [projectId, viewport]);

  // 原生 wheel 监听（React 合成 wheel 会被动化，无法 preventDefault）：Ctrl+滚轮缩放，普通滚轮平移
  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      if (e.ctrlKey || e.metaKey) {
        zoomAt(e.clientX, e.clientY, e.deltaY < 0 ? 1.1 : 1 / 1.1);
      } else {
        setViewport((v) => ({ ...v, tx: v.tx - e.deltaX, ty: v.ty - e.deltaY }));
      }
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [containerRef, zoomAt]);

  return { viewport, viewportRef, zoomAt, zoomAtCenter, moveTo, reset };
}
