package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;

/**
 * 任务动作处理器：负责状态变更、时间戳、sort_rank 生命周期调整、乐观锁落库；
 * 状态流转日志与领域事件由状态机统一处理。
 *
 * @author priority-flow
 */
public interface TaskActionHandler {

    /**
     * 本处理器支持的动作
     */
    TaskActionEnum support();

    /**
     * 执行动作（含动作特有的额外条件校验，如"开始"须无进行中任务）
     *
     * @param task 任务实体（已通过前置状态与权限校验）
     * @param ctx  动作上下文
     */
    void handle(TaskInfoDo task, TaskActionContext ctx);
}
