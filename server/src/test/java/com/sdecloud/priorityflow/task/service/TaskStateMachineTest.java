package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.event.TaskChangedEvent;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskOperationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务状态机测试：每个动作的合法/非法流转成对测试 + 权限校验。
 *
 * @author priority-flow
 */
@ExtendWith(MockitoExtension.class)
class TaskStateMachineTest {

    @Mock
    private TaskActionHandler publishHandler;

    @Mock
    private TaskActionHandler startHandler;

    @Mock
    private TaskActionHandler withdrawHandler;

    @Mock
    private TaskOperationLogRepository operationLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ProjectPermissionChecker permissionChecker;

    private TaskStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        // 各 mock 处理器绑定对应动作
        when(publishHandler.support()).thenReturn(TaskActionEnum.PUBLISH);
        when(startHandler.support()).thenReturn(TaskActionEnum.START);
        when(withdrawHandler.support()).thenReturn(TaskActionEnum.WITHDRAW);
        stateMachine = new TaskStateMachine(
                List.of(publishHandler, startHandler, withdrawHandler),
                operationLogRepository, eventPublisher, permissionChecker);
    }

    private TaskInfoDo task(TaskStatusEnum status, String creatorId, String assigneeId) {
        TaskInfoDo task = new TaskInfoDo();
        task.setId("t1");
        task.setProjectId("p1");
        task.setTaskStatus(status);
        task.setCreatorId(creatorId);
        task.setAssigneeId(assigneeId);
        return task;
    }

    private TaskActionContext ctx(String userId) {
        return TaskActionContext.builder().userId(userId).build();
    }

    @Test
    void publishFromDraftShouldExecuteHandlerWriteLogAndEvent() {
        TaskInfoDo task = task(TaskStatusEnum.DRAFT, "u1", null);
        when(permissionChecker.hasPermission("u1", "p1", "pf:task:publish")).thenReturn(true);

        TaskInfoDo result = stateMachine.execute(task, TaskActionEnum.PUBLISH, ctx("u1"));

        assertNotNull(result);
        verify(publishHandler).handle(eq(task), any(TaskActionContext.class));
        // 统一写操作日志
        verify(operationLogRepository).save(any());
        // 统一发布领域事件
        ArgumentCaptor<TaskChangedEvent> captor = ArgumentCaptor.forClass(TaskChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("PUBLISH", captor.getValue().getChangeType());
    }

    @Test
    void publishFromWaitingShouldBeIllegal() {
        TaskInfoDo task = task(TaskStatusEnum.WAITING, "u1", "u2");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.execute(task, TaskActionEnum.PUBLISH, ctx("u1")));
        assertEquals(ResultCode.ILLEGAL_TRANSITION, ex.getCode());
    }

    @Test
    void startByNonAssigneeShouldBeRejected() {
        TaskInfoDo task = task(TaskStatusEnum.WAITING, "u1", "u2");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.execute(task, TaskActionEnum.START, ctx("u3")));
        assertEquals(ResultCode.NOT_ASSIGNEE, ex.getCode());
    }

    @Test
    void startWithoutPermissionShouldBeRejected() {
        TaskInfoDo task = task(TaskStatusEnum.WAITING, "u1", "u2");
        when(permissionChecker.hasPermission("u2", "p1", "pf:task:start")).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.execute(task, TaskActionEnum.START, ctx("u2")));
        assertEquals(ResultCode.NO_PERMISSION, ex.getCode());
    }

    @Test
    void startByAssigneeWithPermissionShouldPass() {
        TaskInfoDo task = task(TaskStatusEnum.WAITING, "u1", "u2");
        when(permissionChecker.hasPermission("u2", "p1", "pf:task:start")).thenReturn(true);
        stateMachine.execute(task, TaskActionEnum.START, ctx("u2"));
        verify(startHandler).handle(eq(task), any(TaskActionContext.class));
    }

    @Test
    void withdrawByNonCreatorShouldBeRejected() {
        TaskInfoDo task = task(TaskStatusEnum.PENDING_DIST, "u1", null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.execute(task, TaskActionEnum.WITHDRAW, ctx("u2")));
        assertEquals(ResultCode.NOT_CREATOR, ex.getCode());
    }

    @Test
    void withdrawByCreatorShouldBypassRolePermission() {
        TaskInfoDo task = task(TaskStatusEnum.PENDING_DIST, "u1", null);
        stateMachine.execute(task, TaskActionEnum.WITHDRAW, ctx("u1"));
        verify(withdrawHandler).handle(eq(task), any(TaskActionContext.class));
    }

    @Test
    void closeFromWaitingShouldBeAllowed() {
        // close 允许 COMPLETED/WAITING 两个来源状态
        TaskActionRule rule = TaskActionRules.get(TaskActionEnum.CLOSE);
        assertEquals(true, rule.allowsFrom(TaskStatusEnum.WAITING));
        assertEquals(true, rule.allowsFrom(TaskStatusEnum.COMPLETED));
        assertEquals(false, rule.allowsFrom(TaskStatusEnum.IN_PROGRESS));
    }

    @Test
    void unknownHandlerActionShouldBeRejected() {
        // 未注册 handler 的动作（如 COMPLETE 未放入 mock 列表）
        TaskInfoDo task = task(TaskStatusEnum.IN_PROGRESS, "u1", "u2");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.execute(task, TaskActionEnum.COMPLETE, ctx("u2")));
        assertEquals(ResultCode.BAD_PARAM, ex.getCode());
    }

    @Test
    void handlerFailureShouldSkipLogAndEvent() {
        TaskInfoDo task = task(TaskStatusEnum.DRAFT, "u1", null);
        when(permissionChecker.hasPermission("u1", "p1", "pf:task:publish")).thenReturn(true);
        doAnswer(invocation -> {
            throw new BusinessException(ResultCode.BAD_PARAM, "必填项缺失");
        }).when(publishHandler).handle(any(), any());

        assertThrows(BusinessException.class,
                () -> stateMachine.execute(task, TaskActionEnum.PUBLISH, ctx("u1")));
        verify(operationLogRepository, org.mockito.Mockito.never()).save(any());
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
    }
}
