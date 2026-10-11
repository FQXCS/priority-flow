package com.sdecloud.priorityflow.task.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.task.persistence.po.TaskOperationLogDo;

import java.util.List;

/**
 * 任务操作日志仓库。
 *
 * @author priority-flow
 */
public interface TaskOperationLogRepository extends IService<TaskOperationLogDo> {

    /**
     * 任务操作日志（时间倒序）
     */
    List<TaskOperationLogDo> listByTaskId(String taskId);
}
