package com.sdecloud.priorityflow.collab.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.collab.persistence.mapper.TaskAttachmentMapper;
import com.sdecloud.priorityflow.collab.persistence.po.TaskAttachmentDo;
import com.sdecloud.priorityflow.collab.persistence.repository.TaskAttachmentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务附件仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class TaskAttachmentRepositoryImpl extends ServiceImpl<TaskAttachmentMapper, TaskAttachmentDo>
        implements TaskAttachmentRepository {

    @Override
    public List<TaskAttachmentDo> listByTaskId(String taskId) {
        return this.list(new LambdaQueryWrapper<TaskAttachmentDo>()
                .eq(TaskAttachmentDo::getTaskId, taskId)
                .orderByDesc(TaskAttachmentDo::getCreateTime));
    }
}
