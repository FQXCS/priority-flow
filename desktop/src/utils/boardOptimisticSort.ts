/**
 * 同列排序的乐观缓存应用（配合 react-query setQueryData，放下卡片立即到位，请求成功后 refresh 对账）。
 *
 * 显示顺序契约与 dragBucket.ts 一致：优先级降序（URGENT > HIGH > MEDIUM > LOW）+ 桶内 sort_rank 升序。
 */
import { bucketRange } from '@/utils/dragBucket';
import type { SortRef } from '@/utils/dragSortRef';
import type { BoardVo, TaskVo } from '@/api/types';

type SortableTask = { id: string; priority?: TaskVo['priority'] };

/**
 * 按落点引用把任务移动到列表显示顺序的目标位置（不可变更新，未移动元素保持对象引用）。
 *
 * @param list 当前作用域列的任务列表（渲染顺序）
 * @param taskId 被拖拽任务 ID
 * @param ref 落点引用（resolveSortRef 的产物）
 * @returns 新列表；ref 为 null 或任务/目标不在列表（缓存已变等异常）时原样返回，交给 refresh() 对账
 */
export function applySortRef<T extends SortableTask>(list: T[], taskId: string, ref: SortRef | null): T[] {
  if (!ref) {
    return list;
  }
  const activeIndex = list.findIndex((t) => t.id === taskId);
  if (activeIndex < 0) {
    return list;
  }
  const task = list[activeIndex];
  const remaining = list.filter((t) => t.id !== taskId);
  let insertIndex: number;
  if (ref.beforeId) {
    const i = remaining.findIndex((t) => t.id === ref.beforeId);
    if (i < 0) {
      return list;
    }
    insertIndex = i;
  } else if (ref.afterId) {
    const i = remaining.findIndex((t) => t.id === ref.afterId);
    if (i < 0) {
      return list;
    }
    insertIndex = i + 1;
  } else {
    // 桶尾插：统一覆盖非空桶"同优先级最后一个之后"与空桶"更高优先级之后"
    insertIndex = bucketRange(remaining.map((t) => t.priority ?? 'LOW'), task.priority ?? 'LOW').end;
  }
  const next = remaining.slice();
  next.splice(insertIndex, 0, task);
  return next;
}

/**
 * 把同列排序定点应用到 BoardVo（不可变更新）：
 * pendingDist 替换 pendingDistList；waiting 替换 waitingList；其他 scope 原样返回。
 */
export function applyBoardSort(board: BoardVo, scope: string, taskId: string, ref: SortRef | null): BoardVo {
  if (scope === 'pendingDist') {
    return { ...board, pendingDistList: applySortRef(board.pendingDistList, taskId, ref) };
  }
  if (scope === 'waiting') {
    return { ...board, waitingList: applySortRef(board.waitingList, taskId, ref) };
  }
  return board;
}
