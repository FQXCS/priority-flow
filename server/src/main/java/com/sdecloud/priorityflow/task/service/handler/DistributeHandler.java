package com.sdecloud.priorityflow.task.service.handler;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.service.TaskActionContext;
import com.sdecloud.priorityflow.task.service.TaskActionHandler;
import com.sdecloud.priorityflow.task.service.TaskRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分发处理器：PENDING_DIST → WAITING。
 * 必须指定执行人；执行人须为项目成员且权限并集包含 pf:task:start（具备执行能力）；
 * 写 distributor_id/distributed_time；rank 分配到执行人队列同优先级桶尾（新作用域重新分配）。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class DistributeHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    private final TaskRankService taskRankService;

    private final ProjectPermissionChecker permissionChecker;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.DISTRIBUTE;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        String assigneeId = ctx.getAssigneeId();
        if (assigneeId == null || assigneeId.isBlank()) {
            throw new BusinessException(ResultCode.BAD_PARAM, "分发必须指定执行人");
        }
        // 执行人必须为项目成员且具备执行能力（权限并集包含 pf:task:start）
        if (!permissionChecker.isProjectMember(assigneeId, task.getProjectId())) {
            throw new BusinessException(ResultCode.ASSIGNEE_INVALID, "执行人不是项目成员");
        }
        if (!permissionChecker.unionPermissions(assigneeId, task.getProjectId())
                .contains(PermissionConstants.TASK_START)) {
            throw new BusinessException(ResultCode.ASSIGNEE_INVALID, "执行人不具备执行能力（缺少开始任务权限）");
        }

        task.setAssigneeId(assigneeId);
        task.setDistributorId(ctx.getUserId());
        task.setDistributedTime(LocalDateTime.now());
        task.setTaskStatus(TaskStatusEnum.WAITING);
        // 新作用域（执行人队列）重新分配 rank：同优先级桶尾
        List<TaskInfoDo> queue = taskInfoRepository.listWaitingQueue(assigneeId);
        int insertIndex = taskRankService.tailIndexOfPriority(queue, task.getPriority());
        task.setSortRank(taskRankService.rankForInsert(queue, insertIndex, task));
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
