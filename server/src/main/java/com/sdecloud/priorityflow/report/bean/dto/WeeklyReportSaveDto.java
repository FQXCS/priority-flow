package com.sdecloud.priorityflow.report.bean.dto;

import com.sdecloud.priorityflow.report.bean.WeeklyReportEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 周报发布入参。
 *
 * @author priority-flow
 */
@Data
public class WeeklyReportSaveDto {

    /** 所属项目 ID */
    @NotBlank(message = "项目 ID 不能为空")
    private String projectId;

    /** 统计周期起始日（含） */
    @NotNull(message = "周期起始日不能为空")
    private LocalDate periodStart;

    /** 统计周期结束日（含） */
    @NotNull(message = "周期结束日不能为空")
    private LocalDate periodEnd;

    /** 本周进展条目（可空，空视为无条目） */
    private List<WeeklyReportEntry> content;

    /** 下周计划条目（可空，空视为无条目） */
    private List<WeeklyReportEntry> planContent;
}
