package com.sdecloud.priorityflow.report.service;

import com.sdecloud.priorityflow.report.bean.WeeklyReportEntry;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportDraftVo;
import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 周报聚合器（纯计算，不入库）：从项目任务列表按规则生成「本周进展 / 下周计划」结构化条目。
 *
 * <p>本周进展 = 周期内完成的任务（100%）+ 当前进度 &gt; 0 的未完成任务（当前进度）；
 * 下周计划 = 项目内全部未完成任务（等待中/进行中），目标进度 100%。
 * 草稿、待分发、已关闭任务不计入；段落无任务时为空数组。</p>
 *
 * @author priority-flow
 */
public final class WeeklyReportAggregator {

    /** 队列顺序：优先级降序（同优先级按 sort_rank 升序，与看板一致） */
    private static final Comparator<TaskInfoDo> QUEUE_ORDER = Comparator
            .comparing(TaskInfoDo::getPriority,
                    Comparator.comparingInt((PriorityEnum priority) -> priority == null ? 0 : priority.getUrgency()))
            .reversed()
            .thenComparing(TaskInfoDo::getSortRank, Comparator.nullsLast(Comparator.naturalOrder()));

    private WeeklyReportAggregator() {
    }

    /**
     * 聚合周报草稿。
     *
     * @param tasks         项目内全部未删除任务（含各状态）
     * @param periodStart   统计周期起始日（含）
     * @param periodEnd     统计周期结束日（含）
     * @param assigneeNames 执行人 ID → 显示名映射
     * @return content = 周期内完成 + 有进度的未完成任务；planContent = 全部未完成任务
     */
    public static WeeklyReportDraftVo aggregate(List<TaskInfoDo> tasks, LocalDate periodStart, LocalDate periodEnd,
                                                Map<String, String> assigneeNames) {
        List<WeeklyReportEntry> content = new ArrayList<>(buildCompletedEntries(tasks, periodStart, periodEnd, assigneeNames));
        content.addAll(buildProgressedEntries(tasks, assigneeNames));
        return new WeeklyReportDraftVo(content, buildPlanEntries(tasks, assigneeNames));
    }

    /**
     * 本周进展·周期内完成：已完成且完成时间落在本周期，固定 100%，按完成时间升序
     */
    static List<WeeklyReportEntry> buildCompletedEntries(List<TaskInfoDo> tasks, LocalDate periodStart,
                                                         LocalDate periodEnd, Map<String, String> assigneeNames) {
        return tasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.COMPLETED)
                .filter(t -> t.getCompletedTime() != null)
                .filter(t -> inRange(t.getCompletedTime().toLocalDate(), periodStart, periodEnd))
                .sorted(Comparator.comparing(TaskInfoDo::getCompletedTime))
                .map(t -> new WeeklyReportEntry(t.getTitle(), 100, assigneeNameOf(t, assigneeNames)))
                .toList();
    }

    /**
     * 本周进展·有进度的未完成任务：等待中/进行中且当前进度 &gt; 0，取当前进度，按队列顺序
     */
    static List<WeeklyReportEntry> buildProgressedEntries(List<TaskInfoDo> tasks, Map<String, String> assigneeNames) {
        return tasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.WAITING || t.getTaskStatus() == TaskStatusEnum.IN_PROGRESS)
                .filter(t -> t.getProgress() != null && t.getProgress() > 0)
                .sorted(QUEUE_ORDER)
                .map(t -> new WeeklyReportEntry(t.getTitle(), t.getProgress(), assigneeNameOf(t, assigneeNames)))
                .toList();
    }

    /**
     * 下周计划：项目内全部未完成任务（等待中/进行中，不限日期），目标进度 100%，按队列顺序
     */
    static List<WeeklyReportEntry> buildPlanEntries(List<TaskInfoDo> tasks, Map<String, String> assigneeNames) {
        return tasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.WAITING || t.getTaskStatus() == TaskStatusEnum.IN_PROGRESS)
                .sorted(QUEUE_ORDER)
                .map(t -> new WeeklyReportEntry(t.getTitle(), 100, assigneeNameOf(t, assigneeNames)))
                .toList();
    }

    private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * 执行人显示名（无执行人或映射缺失返回 null，前端留空展示）
     */
    private static String assigneeNameOf(TaskInfoDo task, Map<String, String> assigneeNames) {
        return task.getAssigneeId() == null ? null : assigneeNames.get(task.getAssigneeId());
    }
}
