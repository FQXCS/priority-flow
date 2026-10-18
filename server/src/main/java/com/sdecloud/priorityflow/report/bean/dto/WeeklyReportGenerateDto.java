package com.sdecloud.priorityflow.report.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 周报生成入参（实时聚合返回草稿，不入库）。
 *
 * @author priority-flow
 */
@Data
public class WeeklyReportGenerateDto {

    /** 所属项目 ID */
    @NotBlank(message = "项目 ID 不能为空")
    private String projectId;

    /** 统计周期起始日（含） */
    @NotNull(message = "周期起始日不能为空")
    private LocalDate periodStart;

    /** 统计周期结束日（含） */
    @NotNull(message = "周期结束日不能为空")
    private LocalDate periodEnd;
}
