package com.sdecloud.priorityflow.report.bean;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 周报条目（结构化三段）：任务说明 / 进度 / 人员。
 * content/plan_content 列以 JSON 对象数组存储本结构，列表卡片展示时拼接为「说明 进度% 人员」。
 *
 * @author priority-flow
 */
@Data
@NoArgsConstructor
public class WeeklyReportEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务说明（必填） */
    private String text;

    /** 进度百分比（0-100，可空 = 未填） */
    private Integer progress;

    /** 人员显示名（可空） */
    private String person;

    public WeeklyReportEntry(String text, Integer progress, String person) {
        this.text = text;
        this.progress = progress;
        this.person = person;
    }
}
