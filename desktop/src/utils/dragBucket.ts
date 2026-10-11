/**
 * 拖拽桶约束镜像校验（与后端 TaskQueueOrder 同规则，前端先行拦截）。
 *
 * 排序方案：显示顺序 = 优先级降序（URGENT > HIGH > MEDIUM > LOW）+ 桶内 sort_rank 升序；
 * 拖拽仅允许在同一优先级桶内调整顺序，跨优先级拖拽一律拦截。
 */

const URGENCY: Record<string, number> = { URGENT: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

/**
 * 计算拖拽任务在队列（不含自身）中的优先级桶范围。
 *
 * @param priorities 队列显示顺序的优先级序列（不含拖拽任务）
 * @param dragged 拖拽任务优先级
 * @returns start 桶首下标（桶为空时等于桶尾插入位）；end 桶尾插入位（紧急度不低于拖拽任务的任务数）
 */
export function bucketRange(priorities: string[], dragged: string): { start: number; end: number } {
  const urgency = URGENCY[dragged] ?? 0;
  let start = -1;
  let end = 0;
  for (let i = 0; i < priorities.length; i++) {
    const current = URGENCY[priorities[i]] ?? 0;
    if (current >= urgency) {
      end = i + 1;
    }
    if (current === urgency && start < 0) {
      start = i;
    }
  }
  return { start: start < 0 ? end : start, end };
}

/**
 * 校验拖拽落点是否落在同优先级桶内（跨优先级拖拽一律拒绝）
 *
 * @param insertIndex 按移除拖拽任务后的队列坐标计算的目标插入下标
 */
export function canDropWithinBucket(priorities: string[], dragged: string, insertIndex: number): boolean {
  const { start, end } = bucketRange(priorities, dragged);
  return insertIndex >= start && insertIndex <= end;
}
