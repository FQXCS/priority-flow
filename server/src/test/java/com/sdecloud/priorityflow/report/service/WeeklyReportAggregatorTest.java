package com.sdecloud.priorityflow.report.service;

import com.sdecloud.priorityflow.report.bean.WeeklyReportEntry;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportDraftVo;
import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 周报聚合器单测：内存构造任务验证生成规则
 * （本周进展 = 周期内完成 + 进度&gt;0 的未完成任务；下周计划 = 全部未完成任务目标 100%）。
 *
 * @author priority-flow
 */
class WeeklyReportAggregatorTest {

    /** 默认统计周期：2026-10-05（周一）~ 2026-10-11（周日） */
    private static final LocalDate START = LocalDate.of(2026, 10, 5);
    private static final LocalDate END = LocalDate.of(2026, 10, 11);
    private static final Map<String, String> NAMES = Map.of("u1", "张三", "u2", "李四");

    /** 构造任务（进度默认 50，其余按需覆盖） */
    private static TaskInfoDo task(String title, TaskStatusEnum status, PriorityEnum priority, String sortRank,
                                   Integer progress, LocalDateTime completedTime, LocalDateTime expectedFinishAt,
                                   LocalDateTime deadline, String assigneeId) {
        TaskInfoDo t = new TaskInfoDo();
        t.setTitle(title);
        t.setTaskStatus(status);
        t.setPriority(priority);
        t.setSortRank(sortRank);
        t.setProgress(progress);
        t.setCompletedTime(completedTime);
        t.setExpectedFinishAt(expectedFinishAt);
        t.setDeadline(deadline);
        t.setAssigneeId(assigneeId);
        return t;
    }

    @Test
    void completedWithinPeriodOnlySortedAsc() {
        List<TaskInfoDo> tasks = List.of(
                task("晚完成", TaskStatusEnum.COMPLETED, PriorityEnum.MEDIUM, "b", 100,
                        LocalDateTime.of(2026, 10, 10, 18, 0), null, null, "u1"),
                task("早完成", TaskStatusEnum.COMPLETED, PriorityEnum.MEDIUM, "a", 100,
                        LocalDateTime.of(2026, 10, 5, 0, 0), null, null, "u2"),
                task("周期前完成", TaskStatusEnum.COMPLETED, PriorityEnum.MEDIUM, "c", 100,
                        LocalDateTime.of(2026, 10, 4, 23, 59), null, null, "u1"),
                task("周期后完成", TaskStatusEnum.COMPLETED, PriorityEnum.MEDIUM, "d", 100,
                        LocalDateTime.of(2026, 10, 12, 0, 0), null, null, "u1"),
                task("完成但无时间", TaskStatusEnum.COMPLETED, PriorityEnum.MEDIUM, "e", 100,
                        null, null, null, "u1"));
        List<WeeklyReportEntry> entries = WeeklyReportAggregator.buildCompletedEntries(tasks, START, END, NAMES);
        assertEquals(List.of(
                new WeeklyReportEntry("早完成", 100, "李四"),
                new WeeklyReportEntry("晚完成", 100, "张三")), entries);
    }

    @Test
    void progressedTasksSortedByQueueOrder() {
        List<TaskInfoDo> tasks = List.of(
                task("中优B", TaskStatusEnum.IN_PROGRESS, PriorityEnum.MEDIUM, "b", 40, null, null, null, "u1"),
                task("紧急", TaskStatusEnum.IN_PROGRESS, PriorityEnum.URGENT, "z", 80, null, null, null, "u2"),
                task("中优A", TaskStatusEnum.IN_PROGRESS, PriorityEnum.MEDIUM, "a", 20, null, null, null, "u1"),
                task("等待中有进度", TaskStatusEnum.WAITING, PriorityEnum.HIGH, "a", 30, null, null, null, "u2"),
                task("无进度", TaskStatusEnum.IN_PROGRESS, PriorityEnum.LOW, null, null, null, null, null, "u2"),
                task("零进度", TaskStatusEnum.IN_PROGRESS, PriorityEnum.HIGH, "c", 0, null, null, null, "u1"));
        List<WeeklyReportEntry> entries = WeeklyReportAggregator.buildProgressedEntries(tasks, NAMES);
        // 仅进度 > 0 的任务进入本周进展，按优先级降序（同优先级 sort_rank 升序）
        assertEquals(List.of(
                new WeeklyReportEntry("紧急", 80, "李四"),
                new WeeklyReportEntry("等待中有进度", 30, "李四"),
                new WeeklyReportEntry("中优A", 20, "张三"),
                new WeeklyReportEntry("中优B", 40, "张三")), entries);
    }

    @Test
    void planIncludesAllUnfinishedTasksWithTarget100() {
        List<TaskInfoDo> tasks = List.of(
                task("数据迁移", TaskStatusEnum.WAITING, PriorityEnum.MEDIUM, "b", null,
                        null, LocalDateTime.of(2026, 10, 13, 18, 0), null, "u1"),
                task("远期重构", TaskStatusEnum.WAITING, PriorityEnum.MEDIUM, "d", 0,
                        null, LocalDateTime.of(2026, 11, 2, 18, 0), null, "u1"),
                task("接口联调", TaskStatusEnum.IN_PROGRESS, PriorityEnum.URGENT, "a", 55,
                        null, null, null, "u2"),
                task("已完不进计划", TaskStatusEnum.COMPLETED, PriorityEnum.MEDIUM, "e", 100,
                        LocalDateTime.of(2026, 10, 6, 10, 0), null, null, "u1"),
                task("已关闭不进计划", TaskStatusEnum.CLOSED, PriorityEnum.MEDIUM, "f", 100,
                        LocalDateTime.of(2026, 10, 6, 10, 0), null, null, "u1"),
                task("草稿不进计划", TaskStatusEnum.DRAFT, PriorityEnum.MEDIUM, "g", 0,
                        null, null, null, "u1"),
                task("待分发不进计划", TaskStatusEnum.PENDING_DIST, PriorityEnum.MEDIUM, "h", 0,
                        null, null, null, null));
        List<WeeklyReportEntry> entries = WeeklyReportAggregator.buildPlanEntries(tasks, NAMES);
        // 全部未完成任务（不限日期、含零进度），目标进度一律 100%，按队列顺序
        assertEquals(List.of(
                new WeeklyReportEntry("接口联调", 100, "李四"),
                new WeeklyReportEntry("数据迁移", 100, "张三"),
                new WeeklyReportEntry("远期重构", 100, "张三")), entries);
    }

    @Test
    void otherStatusesExcludedFromAllSections() {
        List<TaskInfoDo> tasks = List.of(
                task("草稿", TaskStatusEnum.DRAFT, PriorityEnum.MEDIUM, "a", 0, null,
                        LocalDateTime.of(2026, 10, 12, 0, 0), null, "u1"),
                task("待分发", TaskStatusEnum.PENDING_DIST, PriorityEnum.MEDIUM, "b", 0, null,
                        LocalDateTime.of(2026, 10, 12, 0, 0), null, null),
                task("已关闭", TaskStatusEnum.CLOSED, PriorityEnum.MEDIUM, "c", 100,
                        LocalDateTime.of(2026, 10, 6, 10, 0), null, null, "u1"));
        WeeklyReportDraftVo draft = WeeklyReportAggregator.aggregate(tasks, START, END, NAMES);
        assertTrue(draft.getContent().isEmpty());
        assertTrue(draft.getPlanContent().isEmpty());
    }

    @Test
    void aggregateCombinesCompletedThenProgressed() {
        List<TaskInfoDo> tasks = List.of(
                task("进行中", TaskStatusEnum.IN_PROGRESS, PriorityEnum.HIGH, "a", 55, null, null, null, "u1"),
                task("已完成", TaskStatusEnum.COMPLETED, PriorityEnum.MEDIUM, "b", 100,
                        LocalDateTime.of(2026, 10, 7, 10, 0), null, null, "u2"));
        WeeklyReportDraftVo draft = WeeklyReportAggregator.aggregate(tasks, START, END, NAMES);
        assertEquals(List.of(
                new WeeklyReportEntry("已完成", 100, "李四"),
                new WeeklyReportEntry("进行中", 55, "张三")), draft.getContent());
        // 下周计划 = 全部未完成任务，目标 100%
        assertEquals(List.of(new WeeklyReportEntry("进行中", 100, "张三")), draft.getPlanContent());
    }

    @Test
    void missingAssigneePersonNull() {
        List<TaskInfoDo> tasks = List.of(
                task("无执行人", TaskStatusEnum.IN_PROGRESS, PriorityEnum.MEDIUM, "a", 30, null, null, null, null),
                task("映射缺失", TaskStatusEnum.IN_PROGRESS, PriorityEnum.MEDIUM, "b", 30, null, null, null, "ghost"));
        List<WeeklyReportEntry> entries = WeeklyReportAggregator.buildProgressedEntries(tasks, NAMES);
        assertEquals(2, entries.size());
        assertNull(entries.get(0).getPerson());
        assertNull(entries.get(1).getPerson());
    }
}
