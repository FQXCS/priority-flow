package com.sdecloud.priorityflow.report.bean.dto;

import com.sdecloud.priorityflow.report.bean.WeeklyReportEntry;
import lombok.Data;

import java.util.List;

/**
 * 周报编辑入参（周期不可改）。
 *
 * @author priority-flow
 */
@Data
public class WeeklyReportUpdateDto {

    /** 本周进展条目（可空，空视为无条目） */
    private List<WeeklyReportEntry> content;

    /** 下周计划条目（可空，空视为无条目） */
    private List<WeeklyReportEntry> planContent;
}
