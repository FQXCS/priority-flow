package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.event.TaskChangedEvent;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.po.TaskOperationLogDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskOperationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务状态机核心：内含「动作 → 允许的前置状态 + 角色权限点/创建者特权 + 额外条件」校验表，
 * 非法流转抛业务异常；execute 成功后统一写操作日志并发布 TaskChangedEvent。
 *
 * @author priority-flow
 */
@Slf4j
@Component
public class TaskStateMachine {

    private final Map<TaskActionEnum, TaskActionHandler> handlerMap = new HashMap<>();

    private final TaskOperationLogRepository operationLogRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final ProjectPermissionChecker permissionChecker;

    public TaskStateMachine(List<TaskActionHandler> handlers,
                            TaskOperationLogRepository operationLogRepository,
                            ApplicationEventPublisher eventPublisher,
                            ProjectPermissionChecker permissionChecker) {
        handlers.forEach(handler -> handlerMap.put(handler.support(), handler));
        this.operationLogRepository = operationLogRepository;
        this.eventPublisher = eventPublisher;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 执行状态流转动作
     *
     * @param task   任务实体（由调用方加载）
     * @param action 动作
     * @param ctx    动作上下文
     * @return 变更后的任务
     */
    public TaskInfoDo execute(TaskInfoDo task, TaskActionEnum action, TaskActionContext ctx) {
        TaskActionRule rule = TaskActionRules.get(action);
        if (rule == null || !handlerMap.containsKey(action)) {
            throw new BusinessException(ResultCode.BAD_PARAM, "不支持的任务动作：" + action);
        }
        TaskStatusEnum fromStatus = task.getTaskStatus();

        // 1. 前置状态校验
        if (!rule.allowsFrom(fromStatus)) {
            throw new BusinessException(ResultCode.ILLEGAL_TRANSITION,
                    "任务当前为「" + fromStatus.getLabel() + "」，不允许" + action.getLabel());
        }
        // 2. 权限校验（创建者特权 / 执行人本人 / 角色权限点）
        validatePermission(task, action, rule, ctx);
        // 3. 动作处理（handler 内含动作特有的额外条件校验与落库）
        handlerMap.get(action).handle(task, ctx);
        // 4. 操作日志（覆盖 PRD 要求的 7 项记录）
        writeOperationLog(task, action, fromStatus, ctx);
        // 5. 领域事件：驱动 WS 推送、通知
        eventPublisher.publishEvent(
                TaskChangedEvent.of(this, task.getId(), task.getProjectId(), action.name(), ctx.getUserId(), ctx.getReason()));
        log.info("任务流转成功：taskId={}，action={}，{} → {}", task.getId(), action.getCode(),
                fromStatus.getCode(), task.getTaskStatus().getCode());
        return task;
    }

    /**
     * 权限校验：创建者特权 → 执行人本人 → 角色权限点
     */
    private void validatePermission(TaskInfoDo task, TaskActionEnum action, TaskActionRule rule, TaskActionContext ctx) {
        if (rule.isCreatorOnly() && !ctx.getUserId().equals(task.getCreatorId())) {
            throw new BusinessException(ResultCode.NOT_CREATOR, "仅任务创建者可" + action.getLabel());
        }
        if (rule.isAssigneeOnly() && !ctx.getUserId().equals(task.getAssigneeId())) {
            throw new BusinessException(ResultCode.NOT_ASSIGNEE, "仅任务执行人可" + action.getLabel());
        }
        if (rule.getPermissionCode() != null
                && !permissionChecker.hasPermission(ctx.getUserId(), task.getProjectId(), rule.getPermissionCode())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无「" + action.getLabel() + "」权限");
        }
    }

    /**
     * 写操作日志：任务ID/原状态/新状态/操作人/操作时间/原因备注/来源IP设备
     */
    private void writeOperationLog(TaskInfoDo task, TaskActionEnum action,
                                   TaskStatusEnum fromStatus, TaskActionContext ctx) {
        TaskOperationLogDo logDo = new TaskOperationLogDo();
        logDo.setTaskId(task.getId());
        logDo.setProjectId(task.getProjectId());
        logDo.setOperatorId(ctx.getUserId());
        logDo.setAction(action.getCode());
        logDo.setFromStatus(fromStatus);
        logDo.setToStatus(task.getTaskStatus());
        logDo.setReason(ctx.getReason());
        logDo.setIp(ctx.getIp());
        logDo.setDevice(ctx.getDevice());
        operationLogRepository.save(logDo);
    }
}
