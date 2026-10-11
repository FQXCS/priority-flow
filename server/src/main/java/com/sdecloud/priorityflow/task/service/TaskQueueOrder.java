package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;

import java.util.Comparator;
import java.util.List;

/**
 * 队列显示顺序与拖拽桶约束（纯函数，便于单测）。
 *
 * <p>排序方案（2026-10 重构）：显示顺序 = 优先级降序（URGENT→LOW）+ 同优先级桶内 sort_rank 升序；
 * 拖拽仅允许在同一优先级桶内调整顺序，跨优先级拖拽一律拒绝。</p>
 *
 * <p>前提约束：传入的队列必须已按显示顺序排列（仓储队列方法已排序），桶边界计算依赖桶的连续性。</p>
 *
 * @author priority-flow
 */
public final class TaskQueueOrder {

    /**
     * 队列显示顺序比较器：优先级紧急度降序，同优先级按 sort_rank 升序（rank 为 null 排最后）
     */
    public static final Comparator<TaskInfoDo> DISPLAY_ORDER =
            Comparator.comparing(
                            (TaskInfoDo t) -> t.getPriority() == null ? 0 : t.getPriority().getUrgency(),
                            Comparator.reverseOrder())
                    .thenComparing(TaskInfoDo::getSortRank, Comparator.nullsLast(Comparator.naturalOrder()));

    private TaskQueueOrder() {
    }

    /**
     * 优先级桶起始下标：桶内第一个元素的位置；桶为空时等于桶尾插入位
     */
    public static int bucketStartIndex(List<TaskInfoDo> queue, PriorityEnum priority) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getPriority() == priority) {
                return i;
            }
        }
        return bucketEndIndex(queue, priority);
    }

    /**
     * 优先级桶尾插入下标：紧急度不低于该优先级的任务数（桶为空时为理论桶位置）
     */
    public static int bucketEndIndex(List<TaskInfoDo> queue, PriorityEnum priority) {
        int end = 0;
        for (TaskInfoDo task : queue) {
            if (task.getPriority() != null && task.getPriority().getUrgency() >= priority.getUrgency()) {
                end++;
            }
        }
        return end;
    }

    /**
     * 校验拖拽落点必须落在同优先级桶内 [bucketStartIndex, bucketEndIndex]，
     * 越界即跨优先级拖拽，抛出 IllegalStateException（service 层翻译为业务异常）。
     */
    public static void ensureWithinBucket(List<TaskInfoDo> queue, PriorityEnum priority, int insertIndex) {
        int start = bucketStartIndex(queue, priority);
        int end = bucketEndIndex(queue, priority);
        if (insertIndex < start || insertIndex > end) {
            throw new IllegalStateException(
                    "插入位置 " + insertIndex + " 超出 " + priority.getLabel() + " 桶范围 [" + start + ", " + end + "]");
        }
    }
}
