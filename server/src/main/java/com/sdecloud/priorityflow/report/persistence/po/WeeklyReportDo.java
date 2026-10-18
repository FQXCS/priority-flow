package com.sdecloud.priorityflow.report.persistence.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 计划周报实体：发布后的快照记录，编辑即改快照，与任务后续变化无关。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("weekly_report_info")
public class WeeklyReportDo extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属项目 ID */
    private String projectId;

    /** 统计周期起始日（含） */
    @JSONField(format = "yyyy-MM-dd")
    private LocalDate periodStart;

    /** 统计周期结束日（含） */
    @JSONField(format = "yyyy-MM-dd")
    private LocalDate periodEnd;

    /** 本周进展条目（JSON 字符串数组） */
    private String content;

    /** 下周计划条目（JSON 字符串数组） */
    private String planContent;
}
