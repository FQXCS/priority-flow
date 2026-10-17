import type { MilestoneVo } from '@/api/types';

/** 画布列宽（px），卡片与列同宽 */
export const COLUMN_WIDTH = 280;
/** 未规划列的列 key（日期列 key 为 YYYY-MM-DD） */
export const UNPLANNED_KEY = '__unplanned';
/** 视口缩放范围 */
export const MIN_SCALE = 0.4;
export const MAX_SCALE = 2;
/** 默认视口（内容层自带内边距，原点即可视） */
export const DEFAULT_VIEWPORT: CanvasViewport = { tx: 0, ty: 0, scale: 1 };

/** 画布列：未规划列（无 date）或单个日期分组的里程碑集合 */
export interface CanvasColumn {
  key: string;
  date?: string;
  items: MilestoneVo[];
}

export interface CanvasViewport {
  tx: number;
  ty: number;
  scale: number;
}

interface CanvasState {
  version: 1;
  order: Record<string, string[]>;
  viewport?: CanvasViewport;
}

const storageKey = (projectId: string) => `pf.plan.canvas.v1.${projectId}`;

function readState(projectId: string): CanvasState | null {
  try {
    const raw = localStorage.getItem(storageKey(projectId));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as CanvasState;
    return parsed && parsed.version === 1 ? parsed : null;
  } catch {
    return null;
  }
}

function writeState(projectId: string, patch: Partial<CanvasState>) {
  try {
    const state = readState(projectId) ?? { version: 1 as const, order: {} };
    localStorage.setItem(storageKey(projectId), JSON.stringify({ ...state, ...patch }));
  } catch {
    // 存储不可用（无痕模式等）时静默忽略
  }
}

export function loadOrder(projectId: string): Record<string, string[]> {
  return readState(projectId)?.order ?? {};
}

export function saveOrder(projectId: string, order: Record<string, string[]>) {
  writeState(projectId, { order });
}

export function loadViewport(projectId: string): CanvasViewport | null {
  return readState(projectId)?.viewport ?? null;
}

export function saveViewport(projectId: string, viewport: CanvasViewport) {
  writeState(projectId, { viewport });
}

/** 按持久化顺序对账单列数据：保序交集 + 新 id 追加列尾，失效 id 自动丢弃 */
function reconcileColumn(persisted: string[] | undefined, items: MilestoneVo[]): MilestoneVo[] {
  if (!persisted || persisted.length === 0) {
    return items;
  }
  const byId = new Map(items.map((m) => [m.id, m]));
  const ordered: MilestoneVo[] = [];
  for (const id of persisted) {
    const item = byId.get(id);
    if (item) {
      ordered.push(item);
      byId.delete(id);
    }
  }
  return [...ordered, ...byId.values()];
}

/** 组装画布列：日期列按 expectedDate 升序（后端已排序），未规划列固定最后 */
export function buildColumns(milestones: MilestoneVo[], order: Record<string, string[]>): CanvasColumn[] {
  const unplanned: MilestoneVo[] = [];
  const dateMap = new Map<string, MilestoneVo[]>();
  for (const item of milestones) {
    if (!item.expectedDate) {
      unplanned.push(item);
      continue;
    }
    const list = dateMap.get(item.expectedDate) ?? [];
    list.push(item);
    dateMap.set(item.expectedDate, list);
  }
  const columns: CanvasColumn[] = [];
  for (const [date, items] of dateMap) {
    columns.push({ key: date, date, items: reconcileColumn(order[date], items) });
  }
  columns.push({ key: UNPLANNED_KEY, items: reconcileColumn(order[UNPLANNED_KEY], unplanned) });
  return columns;
}

/** 计算拖拽落下后的列顺序表：被拖卡从所有列移除后插入目标列指定位置 */
export function applyDropToOrder(
  columns: CanvasColumn[],
  draggingId: string,
  targetKey: string,
  index: number,
): Record<string, string[]> {
  const next: Record<string, string[]> = {};
  for (const col of columns) {
    const ids = col.items.map((m) => m.id).filter((mid) => mid !== draggingId);
    if (col.key === targetKey) {
      ids.splice(Math.min(Math.max(index, 0), ids.length), 0, draggingId);
    }
    if (ids.length > 0) {
      next[col.key] = ids;
    }
  }
  return next;
}
