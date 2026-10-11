package com.sdecloud.priorityflow.task.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.task.persistence.mapper.TaskOperationLogMapper;
import com.sdecloud.priorityflow.task.persistence.po.TaskOperationLogDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskOperationLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务操作日志仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class TaskOperationLogRepositoryImpl extends ServiceImpl<TaskOperationLogMapper, TaskOperationLogDo>
        implements TaskOperationLogRepository {

    @Override
    public List<TaskOperationLogDo> listByTaskId(String taskId) {
        return this.list(new LambdaQueryWrapper<TaskOperationLogDo>()
                .eq(TaskOperationLogDo::getTaskId, taskId)
                .orderByDesc(TaskOperationLogDo::getCreateTime));
    }
}
