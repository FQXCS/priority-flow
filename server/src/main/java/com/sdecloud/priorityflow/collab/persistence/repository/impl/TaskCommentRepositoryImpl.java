package com.sdecloud.priorityflow.collab.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.collab.persistence.mapper.TaskCommentMapper;
import com.sdecloud.priorityflow.collab.persistence.po.TaskCommentDo;
import com.sdecloud.priorityflow.collab.persistence.repository.TaskCommentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务评论仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class TaskCommentRepositoryImpl extends ServiceImpl<TaskCommentMapper, TaskCommentDo>
        implements TaskCommentRepository {

    @Override
    public List<TaskCommentDo> listByTaskId(String taskId) {
        return this.list(new LambdaQueryWrapper<TaskCommentDo>()
                .eq(TaskCommentDo::getTaskId, taskId)
                .orderByAsc(TaskCommentDo::getCreateTime));
    }
}
