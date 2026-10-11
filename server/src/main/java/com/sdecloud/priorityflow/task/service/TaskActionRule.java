package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import lombok.Getter;

import java.util.Set;

/**
 * 任务动作规则：允许的前置状态 + 角色权限点/创建者特权/执行人本人约束。
 *
 * @author priority-flow
 */
@Getter
public class TaskActionRule {

    /** 允许的前置状态集合 */
    private final Set<TaskStatusEnum> fromStates;

    /** 所需角色权限点（null 表示不依赖权限点） */
    private final String permissionCode;

    /** 是否仅任务创建者可执行（创建者特权：撤回待分发） */
    private final boolean creatorOnly;

    /** 是否仅任务执行人本人可执行（开始/切换/完成） */
    private final boolean assigneeOnly;

    private TaskActionRule(Set<TaskStatusEnum> fromStates, String permissionCode,
                           boolean creatorOnly, boolean assigneeOnly) {
        this.fromStates = fromStates;
        this.permissionCode = permissionCode;
        this.creatorOnly = creatorOnly;
        this.assigneeOnly = assigneeOnly;
    }

    /**
     * 纯权限点动作
     */
    public static TaskActionRule ofPermission(Set<TaskStatusEnum> fromStates, String permissionCode) {
        return new TaskActionRule(fromStates, permissionCode, false, false);
    }

    /**
     * 仅创建者（创建者特权，不走角色）
     */
    public static TaskActionRule ofCreator(Set<TaskStatusEnum> fromStates) {
        return new TaskActionRule(fromStates, null, true, false);
    }

    /**
     * 权限点 + 执行人本人
     */
    public static TaskActionRule ofAssignee(Set<TaskStatusEnum> fromStates, String permissionCode) {
        return new TaskActionRule(fromStates, permissionCode, false, true);
    }

    /**
     * 判断当前状态是否允许该动作
     */
    public boolean allowsFrom(TaskStatusEnum status) {
        return fromStates.contains(status);
    }

    /**
     * 动作编码（日志/异常提示用）
     */
    public String actionName(TaskActionEnum action) {
        return action.getLabel();
    }
}
