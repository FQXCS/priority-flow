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

/**
 * 撤回处理器：PENDING_DIST → DRAFT（仅创建者，规则表已约束）。
 * 清空排序键与发布/分发痕迹，回到草稿态。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class WithdrawHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.WITHDRAW;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        task.setTaskStatus(TaskStatusEnum.DRAFT);
        task.setSortRank(null);
        task.setPublishedTime(null);
        task.setDistributorId(null);
        task.setDistributedTime(null);
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
