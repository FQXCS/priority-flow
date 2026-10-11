package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 队列显示顺序与拖拽桶约束测试：显示顺序 = 优先级降序 + 桶内 rank 升序；拖拽仅限同优先级桶内。
 *
 * @author priority-flow
 */
class TaskQueueOrderTest {

    private TaskInfoDo task(PriorityEnum priority, String rank) {
        TaskInfoDo task = new TaskInfoDo();
        task.setPriority(priority);
        task.setSortRank(rank);
        return task;
    }

    @Test
    void displayOrderShouldSortByPriorityDescThenRankAsc() {
        List<TaskInfoDo> sorted = List.of(
                task(PriorityEnum.URGENT, "b"),
                task(PriorityEnum.HIGH, "aa"),
                task(PriorityEnum.HIGH, "m"),
                task(PriorityEnum.MEDIUM, "n"),
                task(PriorityEnum.LOW, "c"),
                task(PriorityEnum.LOW, null));
        List<TaskInfoDo> shuffled = List.of(
                task(PriorityEnum.LOW, "c"),
                task(PriorityEnum.HIGH, "m"),
                task(PriorityEnum.URGENT, "b"),
                task(PriorityEnum.LOW, null),
                task(PriorityEnum.MEDIUM, "n"),
                task(PriorityEnum.HIGH, "aa"));
        List<TaskInfoDo> result = shuffled.stream().sorted(TaskQueueOrder.DISPLAY_ORDER).toList();
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(sorted.get(i).getPriority(), result.get(i).getPriority(), "第 " + i + " 位优先级不符");
            assertEquals(sorted.get(i).getSortRank(), result.get(i).getSortRank(), "第 " + i + " 位排序键不符");
        }
    }

    @Test
    void bucketIndexShouldLocateExistingBucket() {
        List<TaskInfoDo> queue = List.of(
                task(PriorityEnum.URGENT, "a"),
                task(PriorityEnum.URGENT, "b"),
                task(PriorityEnum.MEDIUM, "c"),
                task(PriorityEnum.LOW, "d"));
        assertEquals(0, TaskQueueOrder.bucketStartIndex(queue, PriorityEnum.URGENT));
        assertEquals(2, TaskQueueOrder.bucketEndIndex(queue, PriorityEnum.URGENT));
        assertEquals(2, TaskQueueOrder.bucketStartIndex(queue, PriorityEnum.MEDIUM));
        assertEquals(3, TaskQueueOrder.bucketEndIndex(queue, PriorityEnum.MEDIUM));
        assertEquals(3, TaskQueueOrder.bucketStartIndex(queue, PriorityEnum.LOW));
        assertEquals(4, TaskQueueOrder.bucketEndIndex(queue, PriorityEnum.LOW));
    }

    @Test
    void bucketIndexShouldFallbackWhenBucketMissing() {
        // 队列中无 HIGH 任务：理论桶位在 URGENT 与 MEDIUM 之间
        List<TaskInfoDo> queue = List.of(
                task(PriorityEnum.URGENT, "a"),
                task(PriorityEnum.MEDIUM, "b"),
                task(PriorityEnum.LOW, "c"));
        assertEquals(1, TaskQueueOrder.bucketStartIndex(queue, PriorityEnum.HIGH));
        assertEquals(1, TaskQueueOrder.bucketEndIndex(queue, PriorityEnum.HIGH));
        // 空队列
        assertEquals(0, TaskQueueOrder.bucketStartIndex(List.of(), PriorityEnum.LOW));
        assertEquals(0, TaskQueueOrder.bucketEndIndex(List.of(), PriorityEnum.LOW));
    }

    @Test
    void dragWithinBucketShouldPass() {
        List<TaskInfoDo> queue = List.of(
                task(PriorityEnum.URGENT, "a"),
                task(PriorityEnum.MEDIUM, "b"),
                task(PriorityEnum.MEDIUM, "c"),
                task(PriorityEnum.LOW, "d"));
        // MEDIUM 桶范围 [1, 3]：桶首前插、桶尾后插均合法
        assertDoesNotThrow(() -> TaskQueueOrder.ensureWithinBucket(queue, PriorityEnum.MEDIUM, 1));
        assertDoesNotThrow(() -> TaskQueueOrder.ensureWithinBucket(queue, PriorityEnum.MEDIUM, 3));
    }

    @Test
    void crossPriorityDragShouldBeRejected() {
        List<TaskInfoDo> queue = List.of(
                task(PriorityEnum.URGENT, "a"),
                task(PriorityEnum.MEDIUM, "b"),
                task(PriorityEnum.LOW, "c"));
        // LOW 拖到队首（URGENT 前）→ 跨级
        assertThrows(IllegalStateException.class,
                () -> TaskQueueOrder.ensureWithinBucket(queue, PriorityEnum.LOW, 0));
        // URGENT 拖到队尾（LOW 后）→ 跨级
        assertThrows(IllegalStateException.class,
                () -> TaskQueueOrder.ensureWithinBucket(queue, PriorityEnum.URGENT, 3));
    }
}
