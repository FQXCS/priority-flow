package com.sdecloud.priorityflow.task.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.mapper.TaskInfoMapper;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.service.TaskQueueOrder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务信息仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class TaskInfoRepositoryImpl extends ServiceImpl<TaskInfoMapper, TaskInfoDo> implements TaskInfoRepository {

    @Override
    public List<TaskInfoDo> listPendingDistQueue(String projectId) {
        // 显示顺序 = 优先级降序 + 桶内 sort_rank 升序（排序/桶位计算依赖该顺序）
        List<TaskInfoDo> queue = this.list(new LambdaQueryWrapper<TaskInfoDo>()
                .eq(TaskInfoDo::getProjectId, projectId)
                .eq(TaskInfoDo::getTaskStatus, TaskStatusEnum.PENDING_DIST)
                .orderByAsc(TaskInfoDo::getSortRank));
        queue.sort(TaskQueueOrder.DISPLAY_ORDER);
        return queue;
    }

    @Override
    public List<TaskInfoDo> listWaitingQueue(String assigneeId) {
        // 显示顺序 = 优先级降序 + 桶内 sort_rank 升序（排序/桶位计算依赖该顺序）
        List<TaskInfoDo> queue = this.list(new LambdaQueryWrapper<TaskInfoDo>()
                .eq(TaskInfoDo::getAssigneeId, assigneeId)
                .eq(TaskInfoDo::getTaskStatus, TaskStatusEnum.WAITING)
                .orderByAsc(TaskInfoDo::getSortRank));
        queue.sort(TaskQueueOrder.DISPLAY_ORDER);
        return queue;
    }

    @Override
    public List<TaskInfoDo> listInProgressByProject(String projectId, String assigneeId) {
        return this.list(new LambdaQueryWrapper<TaskInfoDo>()
                .eq(TaskInfoDo::getProjectId, projectId)
                .eq(TaskInfoDo::getAssigneeId, assigneeId)
                .eq(TaskInfoDo::getTaskStatus, TaskStatusEnum.IN_PROGRESS));
    }

    @Override
    public List<TaskInfoDo> listInProgressByAssignee(String assigneeId) {
        return this.list(new LambdaQueryWrapper<TaskInfoDo>()
                .eq(TaskInfoDo::getAssigneeId, assigneeId)
                .eq(TaskInfoDo::getTaskStatus, TaskStatusEnum.IN_PROGRESS));
    }

    @Override
    public List<TaskInfoDo> listByProject(String projectId) {
        return this.list(new LambdaQueryWrapper<TaskInfoDo>()
                .eq(TaskInfoDo::getProjectId, projectId));
    }

    @Override
    public Integer maxSeqNo(String projectId) {
        // 原生 SQL 含软删除行：唯一索引不区分删除标记，新编号须越过已删除任务
        return baseMapper.maxSeqNoIncludingDeleted(projectId);
    }

    @Override
    public List<TaskInfoDo> listByAssigneeAndStatus(String assigneeId, List<TaskStatusEnum> statuses) {
        return this.list(new LambdaQueryWrapper<TaskInfoDo>()
                .eq(TaskInfoDo::getAssigneeId, assigneeId)
                .in(TaskInfoDo::getTaskStatus, statuses));
    }

    @Override
    public List<TaskInfoDo> listOverdueUnnotified(LocalDateTime now) {
        return this.list(new LambdaQueryWrapper<TaskInfoDo>()
                .lt(TaskInfoDo::getDeadline, now)
                .in(TaskInfoDo::getTaskStatus, TaskStatusEnum.WAITING, TaskStatusEnum.IN_PROGRESS)
                .eq(TaskInfoDo::getOverdueNotified, "0"));
    }
}
