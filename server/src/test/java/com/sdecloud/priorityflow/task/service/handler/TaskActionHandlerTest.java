package com.sdecloud.priorityflow.task.service.handler;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.service.TaskActionContext;
import com.sdecloud.priorityflow.task.service.TaskRankService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 动作处理器特殊条件测试：开始的唯一进行中约束、分发的执行人能力校验、重新打开的改判逻辑。
 *
 * @author priority-flow
 */
@ExtendWith(MockitoExtension.class)
class TaskActionHandlerTest {

    @Mock
    private TaskInfoRepository taskInfoRepository;

    @Mock
    private TaskRankService taskRankService;

    @Mock
    private ProjectPermissionChecker permissionChecker;

    // ---------------- StartHandler ----------------

    @InjectMocks
    private StartHandler startHandler;

    @Test
    void startShouldRejectWhenAssigneeHasOtherInProgressTask() {
        TaskInfoDo task = buildTask("t1");
        task.setAssigneeId("u2");
        TaskInfoDo other = buildTask("t2");
        other.setAssigneeId("u2");
        when(taskInfoRepository.listInProgressByAssignee("u2")).thenReturn(List.of(other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> startHandler.handle(task, ctx("u2")));
        assertEquals(ResultCode.HAS_IN_PROGRESS, ex.getCode());
    }

    @Test
    void startShouldSetInProgressAndKeepRank() {
        TaskInfoDo task = buildTask("t1");
        task.setAssigneeId("u2");
        task.setSortRank("nn");
        task.setVersion(3);
        when(taskInfoRepository.listInProgressByAssignee("u2")).thenReturn(List.of());
        when(taskInfoRepository.updateById(task)).thenReturn(true);

        startHandler.handle(task, ctx("u2"));

        assertEquals(TaskStatusEnum.IN_PROGRESS, task.getTaskStatus());
        assertNotNull(task.getStartedTime());
        // 切换回等待中需保留原 rank：开始时不清空
        assertEquals("nn", task.getSortRank());
    }

    // ---------------- DistributeHandler ----------------

    @InjectMocks
    private DistributeHandler distributeHandler;

    @Test
    void distributeShouldRejectMissingAssignee() {
        TaskInfoDo task = buildTask("t1");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> distributeHandler.handle(task, ctx("u1")));
        assertEquals(ResultCode.BAD_PARAM, ex.getCode());
    }

    @Test
    void distributeShouldRejectNonMemberAssignee() {
        TaskInfoDo task = buildTask("t1");
        when(permissionChecker.isProjectMember("u9", "p1")).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> distributeHandler.handle(task, ctxWithAssignee("u1", "u9")));
        assertEquals(ResultCode.ASSIGNEE_INVALID, ex.getCode());
    }

    @Test
    void distributeShouldRejectAssigneeWithoutStartPermission() {
        TaskInfoDo task = buildTask("t1");
        when(permissionChecker.isProjectMember("u9", "p1")).thenReturn(true);
        when(permissionChecker.unionPermissions("u9", "p1")).thenReturn(List.of(PermissionConstants.TASK_VIEW_PROJECT));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> distributeHandler.handle(task, ctxWithAssignee("u1", "u9")));
        assertEquals(ResultCode.ASSIGNEE_INVALID, ex.getCode());
    }

    @Test
    void distributeShouldAssignTailRankInAssigneeScope() {
        TaskInfoDo task = buildTask("t1");
        when(permissionChecker.isProjectMember("u2", "p1")).thenReturn(true);
        when(permissionChecker.unionPermissions("u2", "p1"))
                .thenReturn(List.of(PermissionConstants.TASK_START));
        when(taskInfoRepository.listWaitingQueue("u2")).thenReturn(List.of());
        when(taskRankService.tailIndexOfPriority(anyList(), any())).thenReturn(0);
        when(taskRankService.rankForInsert(anyList(), anyInt(), any())).thenReturn("nn");
        when(taskInfoRepository.updateById(task)).thenReturn(true);

        distributeHandler.handle(task, ctxWithAssignee("u1", "u2"));

        assertEquals(TaskStatusEnum.WAITING, task.getTaskStatus());
        assertEquals("u2", task.getAssigneeId());
        assertEquals("u1", task.getDistributorId());
        assertNotNull(task.getDistributedTime());
        assertEquals("nn", task.getSortRank());
    }

    // ---------------- ReopenHandler ----------------

    @InjectMocks
    private ReopenHandler reopenHandler;

    @Test
    void reopenShouldRequireReason() {
        TaskInfoDo task = buildTask("t1");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reopenHandler.handle(task, ctx("u1")));
        assertEquals(ResultCode.BAD_PARAM, ex.getCode());
    }

    @Test
    void reopenShouldGoWaitingAtQueueHeadWhenAssigneeBusy() {
        TaskInfoDo task = buildTask("t1");
        task.setAssigneeId("u2");
        when(taskInfoRepository.listInProgressByAssignee("u2")).thenReturn(List.of(buildTask("t9")));
        when(taskInfoRepository.listWaitingQueue("u2")).thenReturn(List.of());
        when(taskRankService.rankForInsert(anyList(), eq(0), any())).thenReturn("aa");
        when(taskInfoRepository.updateById(task)).thenReturn(true);

        reopenHandler.handle(task, ctxWithReason("u1", "问题回归"));

        assertEquals(TaskStatusEnum.WAITING, task.getTaskStatus());
        assertEquals("aa", task.getSortRank());
    }

    @Test
    void reopenShouldGoInProgressWhenAssigneeIdle() {
        TaskInfoDo task = buildTask("t1");
        task.setAssigneeId("u2");
        when(taskInfoRepository.listInProgressByAssignee("u2")).thenReturn(List.of());
        when(taskInfoRepository.updateById(task)).thenReturn(true);

        reopenHandler.handle(task, ctxWithReason("u1", "问题回归"));

        assertEquals(TaskStatusEnum.IN_PROGRESS, task.getTaskStatus());
        assertNotNull(task.getStartedTime());
        assertNull(task.getSortRank());
    }

    // ---------------- 工具 ----------------

    private TaskInfoDo buildTask(String id) {
        TaskInfoDo task = new TaskInfoDo();
        task.setId(id);
        task.setProjectId("p1");
        task.setTaskStatus(TaskStatusEnum.PENDING_DIST);
        return task;
    }

    private TaskActionContext ctx(String userId) {
        return TaskActionContext.builder().userId(userId).build();
    }

    private TaskActionContext ctxWithAssignee(String userId, String assigneeId) {
        return TaskActionContext.builder().userId(userId).assigneeId(assigneeId).build();
    }

    private TaskActionContext ctxWithReason(String userId, String reason) {
        return TaskActionContext.builder().userId(userId).reason(reason).build();
    }
}
