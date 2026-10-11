/**
 * 拖拽落点引用解析（dnd-kit 排序语义 → 后端 sort-rank 契约）。
 *
 * 后端契约（TaskSortDto）：beforeId = 插到该任务前面，afterId = 插到该任务后面，均空 = 尾插。
 * dnd-kit 排序语义：over 即目标位置元素——向上拖落到其前，向下拖落到其后（arrayMove 语义）。
 */

export interface SortRef {
  /** 目标位置的后邻任务 ID（拖到它前面） */
  beforeId?: string;
  /** 目标位置的前邻任务 ID（拖到它后面） */
  afterId?: string;
}

/**
 * 解析拖拽落点应提交的 beforeId/afterId（二选一）。
 *
 * @param list 当前作用域列的任务列表（渲染顺序）
 * @param activeId 被拖拽任务 ID
 * @param overId 落点任务 ID；undefined 表示落在列空白处
 * @returns 落点引用；均空 = 尾插；返回 null 表示落点为原位，无需发起排序请求
 */
export function resolveSortRef(list: { id: string }[], activeId: string, overId?: string): SortRef | null {
  // 列空白处或异常兜底（落点不在当前列表）→ 尾插
  if (!overId) {
    return {};
  }
  const activeIndex = list.findIndex((t) => t.id === activeId);
  const overIndex = list.findIndex((t) => t.id === overId);
  if (activeIndex < 0 || overIndex < 0) {
    return {};
  }
  // 落回原位，顺序未变
  if (overIndex === activeIndex) {
    return null;
  }
  // 向上拖落到目标前，向下拖落到目标后
  return overIndex < activeIndex ? { beforeId: overId } : { afterId: overId };
}
