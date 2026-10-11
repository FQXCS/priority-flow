package com.sdecloud.priorityflow.task.service;

import lombok.Builder;
import lombok.Getter;

/**
 * 任务动作上下文：操作人、原因、目标执行人、来源 IP/设备。
 *
 * @author priority-flow
 */
@Getter
@Builder
public class TaskActionContext {

    /** 操作人用户 ID */
    private final String userId;

    /** 原因备注（关闭/重新打开必填） */
    private final String reason;

    /** 目标执行人 ID（分发动作使用） */
    private final String assigneeId;

    /** 来源 IP */
    private final String ip;

    /** 设备（User-Agent 摘要） */
    private final String device;
}
