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
 * 完成处理器：IN_PROGRESS → COMPLETED（仅执行人本人）。记 completed_time，清空排序键。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class CompleteHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.COMPLETE;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        task.setTaskStatus(TaskStatusEnum.COMPLETED);
        task.setProgress(100);
        task.setCompletedTime(LocalDateTime.now());
        task.setSortRank(null);
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
