package com.sdecloud.priorityflow.task.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务操作日志实体（一张表同时满足状态历史与操作日志）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_operation_log")
public class TaskOperationLogDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 项目 ID */
    private String projectId;

    /** 操作人 ID */
    private String operatorId;

    /** 动作（PUBLISH/DISTRIBUTE/START 等状态流转动作，或 CREATE/UPDATE/COMMENT 等记录动作） */
    private String action;

    /** 原状态 */
    private TaskStatusEnum fromStatus;

    /** 新状态 */
    private TaskStatusEnum toStatus;

    /** 原因备注 */
    private String reason;

    /** 详情（JSON 扩展信息） */
    private String detail;

    /** 来源 IP */
    private String ip;

    /** 设备（User-Agent 摘要） */
    private String device;
}
