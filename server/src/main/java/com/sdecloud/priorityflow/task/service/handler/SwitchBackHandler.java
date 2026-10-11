package com.sdecloud.priorityflow.task.service.handler;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.rank.LexoRankUtil;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.service.TaskActionContext;
import com.sdecloud.priorityflow.task.service.TaskActionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 切换回等待中处理器：IN_PROGRESS → WAITING（仅执行人本人）。
 * 保留原 sort_rank（进入进行中时未清空），恢复在执行人等待队列中的原位置。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class SwitchBackHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.SWITCH_BACK;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        if (task.getSortRank() == null) {
            // 理论上不可达：开始时保留 rank；防御性兜底给初始键
            task.setSortRank(LexoRankUtil.initial());
        }
        task.setTaskStatus(TaskStatusEnum.WAITING);
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
