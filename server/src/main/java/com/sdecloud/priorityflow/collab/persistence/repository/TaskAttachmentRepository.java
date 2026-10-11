package com.sdecloud.priorityflow.collab.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.collab.persistence.po.TaskAttachmentDo;

import java.util.List;

/**
 * 任务附件仓库。
 *
 * @author priority-flow
 */
public interface TaskAttachmentRepository extends IService<TaskAttachmentDo> {

    /**
     * 任务附件（时间倒序）
     */
    List<TaskAttachmentDo> listByTaskId(String taskId);
}
