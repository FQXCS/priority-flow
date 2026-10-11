package com.sdecloud.priorityflow.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 任务变更领域事件：task 域 service 发出，驱动 WS 推送、站内通知等监听器，避免 service 相互调用。
 *
 * @author priority-flow
 */
@Getter
public class TaskChangedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private final String taskId;

    /** 所属项目 ID */
    private final String projectId;

    /** 变更类型（动作名或 TASK_CREATED/TASK_UPDATED/TASK_SORTED 等） */
    private final String changeType;

    /** 操作人 ID */
    private final String operatorId;

    /** 评论 @ 提及的用户 ID 列表（仅评论事件，可为 null） */
    private final List<String> mentions;

    /** 原因备注（关闭/重新打开等，可为 null） */
    private final String reason;

    public TaskChangedEvent(Object source, String taskId, String projectId, String changeType,
                            String operatorId, List<String> mentions, String reason) {
        super(source);
        this.taskId = taskId;
        this.projectId = projectId;
        this.changeType = changeType;
        this.operatorId = operatorId;
        this.mentions = mentions;
        this.reason = reason;
    }

    /**
     * 构造无附加信息的事件
     */
    public static TaskChangedEvent of(Object source, String taskId, String projectId,
                                      String changeType, String operatorId) {
        return new TaskChangedEvent(source, taskId, projectId, changeType, operatorId, null, null);
    }

    /**
     * 构造携带原因备注的事件（关闭/重新打开等）
     */
    public static TaskChangedEvent of(Object source, String taskId, String projectId,
                                      String changeType, String operatorId, String reason) {
        return new TaskChangedEvent(source, taskId, projectId, changeType, operatorId, null, reason);
    }
}
