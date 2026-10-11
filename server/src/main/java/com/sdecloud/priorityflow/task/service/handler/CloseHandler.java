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
 * 关闭处理器：COMPLETED/WAITING → CLOSED。记 closed_time，清空排序键。
 * 原因备注由状态机统一写入操作日志。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class CloseHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.CLOSE;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        task.setTaskStatus(TaskStatusEnum.CLOSED);
        task.setClosedTime(LocalDateTime.now());
        task.setSortRank(null);
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
