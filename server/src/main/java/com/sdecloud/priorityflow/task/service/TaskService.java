package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.common.api.PageResult;
import com.sdecloud.priorityflow.task.bean.dto.TaskCreateDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskDistributeDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskPriorityDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskQueryDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskReasonDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskSortDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskUpdateDto;
import com.sdecloud.priorityflow.task.bean.vo.BoardVo;
import com.sdecloud.priorityflow.task.bean.vo.CrossProjectWorkloadVo;
import com.sdecloud.priorityflow.task.bean.vo.TaskInfoVo;
import com.sdecloud.priorityflow.task.bean.vo.TaskLogVo;
import com.sdecloud.priorityflow.task.bean.vo.WorkloadVo;

import java.util.List;

/**
 * 任务服务：CRUD、状态流转、拖拽排序、看板聚合、草稿、负载。
 *
 * @author priority-flow
 */
public interface TaskService {

    /**
     * 创建任务（action=DRAFT 存草稿 / PUBLISH 直接发布）
     */
    TaskInfoVo createTask(TaskCreateDto dto);

    /**
     * 编辑任务（草稿：创建者；待分发：有发布权限者）
     */
    TaskInfoVo updateTask(String taskId, TaskUpdateDto dto);

    /**
     * 发布任务（DRAFT → PENDING_DIST）
     */
    TaskInfoVo publish(String taskId);

    /**
     * 撤回待分发（PENDING_DIST → DRAFT，仅创建者）
     */
    TaskInfoVo withdraw(String taskId);

    /**
     * 分发任务（PENDING_DIST → WAITING）
     */
    TaskInfoVo distribute(String taskId, TaskDistributeDto dto);

    /**
     * 开始任务（WAITING → IN_PROGRESS，执行人无其他进行中任务）
     */
    TaskInfoVo start(String taskId);

    /**
     * 切换回等待中（IN_PROGRESS → WAITING，保留原排序位）
     */
    TaskInfoVo switchBack(String taskId);

    /**
     * 完成任务（IN_PROGRESS → COMPLETED）
     */
    TaskInfoVo complete(String taskId);

    /**
     * 关闭任务（COMPLETED/WAITING → CLOSED）
     */
    TaskInfoVo close(String taskId, TaskReasonDto dto);

    /**
     * 重新打开（CLOSED → IN_PROGRESS 或 WAITING 队首）
     */
    TaskInfoVo reopen(String taskId, TaskReasonDto dto);

    /**
     * 修改优先级（rank 重置为新优先级桶最后）
     */
    TaskInfoVo changePriority(String taskId, TaskPriorityDto dto);

    /**
     * 拖拽排序（服务端算 LexoRank，只收 beforeId/afterId）
     */
    TaskInfoVo sortRank(String taskId, TaskSortDto dto);

    /**
     * 任务详情
     */
    TaskInfoVo getTaskDetail(String taskId);

    /**
     * 删除草稿（仅创建者、草稿态）
     */
    void deleteDraft(String taskId);

    /**
     * 看板聚合（六列结构化数据）
     */
    BoardVo getBoard(String projectId, TaskQueryDto query);

    /**
     * 跨项目看板聚合：默认聚合我可见（成员 + pf:task:viewProject）的全部项目，
     * query.projectId 非空时收窄到该项目（无权限抛 2001）
     */
    BoardVo getBoardAcrossProjects(TaskQueryDto query);

    /**
     * 我的任务（scope = waiting / in_progress / completed；projectId 非空时按项目筛选）
     */
    List<TaskInfoVo> mine(String scope, String projectId);

    /**
     * 我的草稿（分页）
     */
    PageResult<TaskInfoVo> myDrafts(long pageNum, long pageSize);

    /**
     * 项目人员负载（需 pf:task:viewWorkload）
     */
    List<WorkloadVo> projectWorkload(String projectId);

    /**
     * 跨项目负载（脱敏：只回 project+priority，需 pf:task:viewMasked）
     */
    List<CrossProjectWorkloadVo> crossProjectWorkload();

    /**
     * 任务操作日志（需 pf:log:view）
     */
    List<TaskLogVo> getLogs(String taskId);
}
