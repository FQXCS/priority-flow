package com.sdecloud.priorityflow.report.bean.vo;

import com.sdecloud.priorityflow.report.bean.WeeklyReportEntry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 周报生成草稿出参（实时聚合，不入库）。
 *
 * @author priority-flow
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportDraftVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本周进展条目 */
    private List<WeeklyReportEntry> content;

    /** 下周计划条目 */
    private List<WeeklyReportEntry> planContent;
}
