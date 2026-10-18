package com.sdecloud.priorityflow.report.bean.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.sdecloud.priorityflow.report.persistence.po.WeeklyReportDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 计划周报出参：继承实体，仅追加项目名称翻译字段。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WeeklyReportVo extends WeeklyReportDo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属项目名称（翻译字段） */
    @TableField(exist = false)
    private String projectName;
}
