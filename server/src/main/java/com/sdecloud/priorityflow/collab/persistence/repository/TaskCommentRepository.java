package com.sdecloud.priorityflow.collab.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.collab.persistence.po.TaskCommentDo;

import java.util.List;

/**
 * 任务评论仓库。
 *
 * @author priority-flow
 */
public interface TaskCommentRepository extends IService<TaskCommentDo> {

    /**
     * 任务评论（时间正序）
     */
    List<TaskCommentDo> listByTaskId(String taskId);
}
