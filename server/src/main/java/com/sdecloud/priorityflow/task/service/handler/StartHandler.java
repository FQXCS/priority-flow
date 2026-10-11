package com.sdecloud.priorityflow.task.service.handler;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.service.TaskActionContext;
import com.sdecloud.priorityflow.task.service.TaskActionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 开始处理器：WAITING → IN_PROGRESS（仅执行人本人）。
 * 校验该执行人当前无进行中任务（跨项目唯一），否则返回 3xxx 引导走切换流程。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class StartHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.START;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        // 全局唯一进行中任务（"人员当前任务"语义：一人同一时刻只有一个进行中任务）
        boolean hasInProgress = taskInfoRepository.listInProgressByAssignee(ctx.getUserId()).stream()
                .anyMatch(t -> !t.getId().equals(task.getId()));
        if (hasInProgress) {
            throw new BusinessException(ResultCode.HAS_IN_PROGRESS);
        }
        task.setTaskStatus(TaskStatusEnum.IN_PROGRESS);
        task.setStartedTime(LocalDateTime.now());
        // sort_rank 列保留原等待队列排序键：切换回等待中时按 PRD 要求恢复原位置
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
