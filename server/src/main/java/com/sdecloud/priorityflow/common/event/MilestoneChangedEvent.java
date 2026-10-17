package com.sdecloud.priorityflow.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 里程碑变更领域事件：milestone 域 service 发出，驱动 WS 推送等监听器。
 *
 * @author priority-flow
 */
@Getter
public class MilestoneChangedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 里程碑 ID */
    private final String milestoneId;

    /** 所属项目 ID */
    private final String projectId;

    /** 变更类型（MILESTONE_CREATED/MILESTONE_UPDATED/MILESTONE_DELETED） */
    private final String changeType;

    /** 操作人 ID */
    private final String operatorId;

    public MilestoneChangedEvent(Object source, String milestoneId, String projectId,
                                 String changeType, String operatorId) {
        super(source);
        this.milestoneId = milestoneId;
        this.projectId = projectId;
        this.changeType = changeType;
        this.operatorId = operatorId;
    }

    /**
     * 构造事件
     */
    public static MilestoneChangedEvent of(Object source, String milestoneId, String projectId,
                                           String changeType, String operatorId) {
        return new MilestoneChangedEvent(source, milestoneId, projectId, changeType, operatorId);
    }
}
