package com.sdecloud.priorityflow.task.service.handler;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.service.TaskActionContext;
import com.sdecloud.priorityflow.task.service.TaskActionHandler;
import com.sdecloud.priorityflow.task.service.TaskQueueOrder;
import com.sdecloud.priorityflow.task.service.TaskRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重新打开处理器：CLOSED → IN_PROGRESS 或 WAITING（PRD 状态表补漏点）。
 * 必填 reason；若执行人已有进行中任务 → 目标状态改判为 WAITING（rank 置顶到该执行人队列的同优先级桶首），
 * 否则 IN_PROGRESS。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class ReopenHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    private final TaskRankService taskRankService;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.REOPEN;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        if (ctx.getReason() == null || ctx.getReason().isBlank()) {
            throw new BusinessException(ResultCode.BAD_PARAM, "重新打开必须填写原因");
        }
        boolean assigneeBusy = task.getAssigneeId() != null
                && !taskInfoRepository.listInProgressByAssignee(task.getAssigneeId()).isEmpty();
        if (assigneeBusy) {
            // 执行人忙碌：进入等待中队列的同优先级桶首（rank 置顶到桶内最前）
            task.setTaskStatus(TaskStatusEnum.WAITING);
            List<TaskInfoDo> queue = taskInfoRepository.listWaitingQueue(task.getAssigneeId());
            int bucketStart = TaskQueueOrder.bucketStartIndex(queue, task.getPriority());
            task.setSortRank(taskRankService.rankForInsert(queue, bucketStart, task));
        } else {
            // 执行人空闲：直接回到进行中
            task.setTaskStatus(TaskStatusEnum.IN_PROGRESS);
            task.setStartedTime(LocalDateTime.now());
        }
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
