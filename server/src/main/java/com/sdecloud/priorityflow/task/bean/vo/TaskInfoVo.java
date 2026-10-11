package com.sdecloud.priorityflow.task.bean.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务 VO（继承 PO）：追加翻译字段与展示字段。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskInfoVo extends TaskInfoDo {

    private static final long serialVersionUID = 1L;

    /** 任务编号（{project_key}-{seq_no}） */
    @TableField(exist = false)
    private String taskNo;

    /** 项目编号前缀 */
    @TableField(exist = false)
    private String projectKey;

    /** 执行人姓名 */
    @TableField(exist = false)
    private String assigneeName;

    /** 创建人姓名 */
    @TableField(exist = false)
    private String creatorName;

    /** 分发人姓名 */
    @TableField(exist = false)
    private String distributorName;

    /** 队列前序数量（等待中队列前面还有几个任务；仅创建者本人/有查看负载权限者可见，否则 null） */
    @TableField(exist = false)
    private Integer waitingAheadCount;
}
