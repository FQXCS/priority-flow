package com.sdecloud.priorityflow.task.service.handler;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
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
 * 发布处理器：DRAFT → PENDING_DIST。
 * 必填项完整（标题/项目/优先级）；rank 尾插待分发桶（同优先级最后之后）；记 published_time。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class PublishHandler implements TaskActionHandler {

    private final TaskInfoRepository taskInfoRepository;

    private final TaskRankService taskRankService;

    @Override
    public TaskActionEnum support() {
        return TaskActionEnum.PUBLISH;
    }

    @Override
    public void handle(TaskInfoDo task, TaskActionContext ctx) {
        if (task.getTitle() == null || task.getTitle().isBlank()
                || task.getPriority() == null || task.getProjectId() == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "发布任务必须填写标题与优先级");
        }
        task.setTaskStatus(TaskStatusEnum.PENDING_DIST);
        task.setPublishedTime(LocalDateTime.now());
        // rank 尾插待分发桶：同优先级最后一个任务之后（无同优先级任务时队列末尾）
        List<TaskInfoDo> queue = taskInfoRepository.listPendingDistQueue(task.getProjectId());
        int insertIndex = taskRankService.tailIndexOfPriority(queue, task.getPriority());
        task.setSortRank(taskRankService.rankForInsert(queue, insertIndex, task));
        save(task);
    }

    /**
     * 乐观锁落库：并发冲突影响行数为 0 时提示刷新重试
     */
    void save(TaskInfoDo task) {
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
    }
}
