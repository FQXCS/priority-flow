package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 动作规则表：动作 → 允许的前置状态 + 权限点/创建者特权/执行人本人。
 *
 * @author priority-flow
 */
public final class TaskActionRules {

    private static final Map<TaskActionEnum, TaskActionRule> RULES = new EnumMap<>(TaskActionEnum.class);

    static {
        RULES.put(TaskActionEnum.PUBLISH,
                TaskActionRule.ofPermission(Set.of(TaskStatusEnum.DRAFT), PermissionConstants.TASK_PUBLISH));
        // 创建者特权：仅草稿归属人可撤回待分发
        RULES.put(TaskActionEnum.WITHDRAW, TaskActionRule.ofCreator(Set.of(TaskStatusEnum.PENDING_DIST)));
        RULES.put(TaskActionEnum.DISTRIBUTE,
                TaskActionRule.ofPermission(Set.of(TaskStatusEnum.PENDING_DIST), PermissionConstants.TASK_DISTRIBUTE));
        RULES.put(TaskActionEnum.START,
                TaskActionRule.ofAssignee(Set.of(TaskStatusEnum.WAITING), PermissionConstants.TASK_START));
        RULES.put(TaskActionEnum.SWITCH_BACK,
                TaskActionRule.ofAssignee(Set.of(TaskStatusEnum.IN_PROGRESS), PermissionConstants.TASK_SWITCH_BACK));
        RULES.put(TaskActionEnum.COMPLETE,
                TaskActionRule.ofAssignee(Set.of(TaskStatusEnum.IN_PROGRESS), PermissionConstants.TASK_COMPLETE));
        RULES.put(TaskActionEnum.CLOSE,
                TaskActionRule.ofPermission(Set.of(TaskStatusEnum.COMPLETED, TaskStatusEnum.WAITING),
                        PermissionConstants.TASK_CLOSE));
        RULES.put(TaskActionEnum.REOPEN,
                TaskActionRule.ofPermission(Set.of(TaskStatusEnum.CLOSED), PermissionConstants.TASK_REOPEN));
    }

    private TaskActionRules() {
    }

    /**
     * 查询动作规则
     */
    public static TaskActionRule get(TaskActionEnum action) {
        return RULES.get(action);
    }
}
