package com.sdecloud.priorityflow.task.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;

import java.util.List;

/**
 * 任务信息仓库。
 *
 * @author priority-flow
 */
public interface TaskInfoRepository extends IService<TaskInfoDo> {

    /**
     * 待分发队列（项目作用域，sort_rank 升序）
     */
    List<TaskInfoDo> listPendingDistQueue(String projectId);

    /**
     * 执行人等待队列（执行人作用域，sort_rank 升序）
     */
    List<TaskInfoDo> listWaitingQueue(String assigneeId);

    /**
     * 查询用户在指定项目内的进行中任务
     */
    List<TaskInfoDo> listInProgressByProject(String projectId, String assigneeId);

    /**
     * 查询用户全局进行中任务（开始动作唯一性校验，跨项目只允许一个）
     */
    List<TaskInfoDo> listInProgressByAssignee(String assigneeId);

    /**
     * 项目内全部任务（看板聚合）
     */
    List<TaskInfoDo> listByProject(String projectId);

    /**
     * 项目内当前最大序号
     */
    Integer maxSeqNo(String projectId);

    /**
     * 按状态查询用户任务（我的任务）
     */
    List<TaskInfoDo> listByAssigneeAndStatus(String assigneeId, List<TaskStatusEnum> statuses);

    /**
     * 逾期未通知任务（定时扫描）
     */
    List<TaskInfoDo> listOverdueUnnotified(java.time.LocalDateTime now);
}
