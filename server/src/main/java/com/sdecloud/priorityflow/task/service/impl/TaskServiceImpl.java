package com.sdecloud.priorityflow.task.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.common.api.PageResult;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.event.TaskChangedEvent;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.common.security.UserPrincipal;
import com.sdecloud.priorityflow.common.util.XssUtil;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectInfoRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRepository;
import com.sdecloud.priorityflow.task.bean.dto.TaskCreateDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskDistributeDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskPriorityDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskProgressDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskQueryDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskReasonDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskSortDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskUpdateDto;
import com.sdecloud.priorityflow.task.bean.vo.AssigneeGroupVo;
import com.sdecloud.priorityflow.task.bean.vo.BoardVo;
import com.sdecloud.priorityflow.task.bean.vo.CrossProjectWorkloadVo;
import com.sdecloud.priorityflow.task.bean.vo.TaskInfoVo;
import com.sdecloud.priorityflow.task.bean.vo.TaskLogVo;
import com.sdecloud.priorityflow.task.bean.vo.WorkloadVo;
import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.po.TaskOperationLogDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.persistence.repository.TaskOperationLogRepository;
import com.sdecloud.priorityflow.task.service.TaskActionContext;
import com.sdecloud.priorityflow.task.service.TaskQueueOrder;
import com.sdecloud.priorityflow.task.service.TaskRankService;
import com.sdecloud.priorityflow.task.service.TaskService;
import com.sdecloud.priorityflow.task.service.TaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务服务实现：CRUD / 状态流转编排 / 拖拽排序校验 / 看板聚合 / 负载。
 *
 * <p>排序机制（2026-10 重构）：显示顺序 = 优先级降序 + 同优先级桶内 sort_rank 升序；
 * 拖拽仅允许在同一优先级桶内调整顺序，跨优先级拖拽一律拒绝（服务端兜底校验）。</p>
 *
 * @author priority-flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    /** 已完成/已关闭列返回条数上限 */
    private static final int HISTORY_LIMIT = 200;

    private static final String ACTION_DRAFT = "DRAFT";

    private static final String ACTION_PUBLISH = "PUBLISH";

    private final TaskInfoRepository taskInfoRepository;

    private final TaskOperationLogRepository operationLogRepository;

    private final TaskStateMachine stateMachine;

    private final TaskRankService taskRankService;

    private final ProjectPermissionChecker permissionChecker;

    private final ProjectInfoRepository projectInfoRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final SysUserRepository sysUserRepository;

    private final ApplicationEventPublisher eventPublisher;

    // ---------------- CRUD ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo createTask(TaskCreateDto dto) {
        UserPrincipal me = UserContext.require();
        ProjectInfoDo project = requireProject(dto.getProjectId());
        // projectId 在请求体内，无法经切面解析，在此校验创建草稿权限
        if (!permissionChecker.hasPermission(me.getUserId(), project.getId(), PermissionConstants.TASK_CREATE_DRAFT)) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }

        TaskInfoDo task = new TaskInfoDo();
        task.setProjectId(project.getId());
        task.setTitle(XssUtil.cleanText(dto.getTitle()));
        task.setDescription(XssUtil.cleanRichText(dto.getDescription()));
        task.setPriority(dto.getPriority());
        task.setTaskType(dto.getTaskType());
        task.setLabels(dto.getLabels() == null ? null : JSON.toJSONString(dto.getLabels()));
        task.setEstimatedHours(dto.getEstimatedHours());
        task.setRemainingHours(dto.getRemainingHours());
        task.setExpectedFinishAt(dto.getExpectedFinishAt());
        task.setDeadline(dto.getDeadline());
        task.setCreatorId(me.getUserId());
        task.setTaskStatus(TaskStatusEnum.DRAFT);
        task.setVersion(0);
        task.setOverdueNotified("0");

        // 项目内递增序号：唯一索引兜底 + 冲突重试（<100 人规模冲突极低）
        for (int attempt = 1; ; attempt++) {
            Integer maxSeqNo = taskInfoRepository.maxSeqNo(project.getId());
            task.setSeqNo((maxSeqNo == null ? 0 : maxSeqNo) + 1);
            try {
                taskInfoRepository.save(task);
                break;
            } catch (DuplicateKeyException e) {
                if (attempt >= 3) {
                    throw new BusinessException(ResultCode.CONFLICT_REFRESH, "任务编号生成冲突，请重试");
                }
            }
        }
        writeLog(task, "create", null, me);

        if (ACTION_PUBLISH.equalsIgnoreCase(dto.getAction())) {
            // 直接发布：走状态机（含权限/必填校验、rank 尾插、日志、事件）
            task = stateMachine.execute(task, TaskActionEnum.PUBLISH, buildContext(null, null));
        } else {
            eventPublisher.publishEvent(TaskChangedEvent.of(this, task.getId(), project.getId(),
                    "TASK_CREATED", me.getUserId()));
        }
        return toVo(task, project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo updateTask(String taskId, TaskUpdateDto dto) {
        UserPrincipal me = UserContext.require();
        TaskInfoDo task = requireTask(taskId);
        ProjectInfoDo project = requireProject(task.getProjectId());

        // 草稿：仅创建者；待分发：有发布权限者；其余状态不可编辑
        if (task.getTaskStatus() == TaskStatusEnum.DRAFT) {
            if (!me.getUserId().equals(task.getCreatorId())) {
                throw new BusinessException(ResultCode.NOT_CREATOR);
            }
        } else if (task.getTaskStatus() == TaskStatusEnum.PENDING_DIST) {
            if (!permissionChecker.hasPermission(me.getUserId(), task.getProjectId(), PermissionConstants.TASK_PUBLISH)) {
                throw new BusinessException(ResultCode.NO_PERMISSION, "无编辑待分发任务权限");
            }
        } else {
            throw new BusinessException(ResultCode.ILLEGAL_TRANSITION, "仅草稿或待分发任务可编辑");
        }

        if (dto.getTitle() != null) {
            task.setTitle(XssUtil.cleanText(dto.getTitle()));
        }
        if (dto.getDescription() != null) {
            task.setDescription(XssUtil.cleanRichText(dto.getDescription()));
        }
        if (dto.getPriority() != null) {
            task.setPriority(dto.getPriority());
            // 待分发态改优先级须同步重排：rank 移到新优先级桶尾（草稿态无排序键无需处理）
            if (task.getTaskStatus() == TaskStatusEnum.PENDING_DIST) {
                List<TaskInfoDo> queue = taskInfoRepository.listPendingDistQueue(task.getProjectId()).stream()
                        .filter(t -> !t.getId().equals(task.getId())).toList();
                int idx = taskRankService.tailIndexOfPriority(queue, dto.getPriority());
                task.setSortRank(taskRankService.rankForInsert(queue, idx, task));
            }
        }
        if (dto.getTaskType() != null) {
            task.setTaskType(dto.getTaskType());
        }
        if (dto.getLabels() != null) {
            task.setLabels(JSON.toJSONString(dto.getLabels()));
        }
        if (dto.getEstimatedHours() != null) {
            task.setEstimatedHours(dto.getEstimatedHours());
        }
        if (dto.getRemainingHours() != null) {
            task.setRemainingHours(dto.getRemainingHours());
        }
        if (dto.getExpectedFinishAt() != null) {
            task.setExpectedFinishAt(dto.getExpectedFinishAt());
        }
        if (dto.getDeadline() != null) {
            task.setDeadline(dto.getDeadline());
        }
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
        writeLog(task, "update", null, me);
        eventPublisher.publishEvent(TaskChangedEvent.of(this, task.getId(), task.getProjectId(),
                "TASK_UPDATED", me.getUserId()));
        return toVo(task, project);
    }

    // ---------------- 状态流转（统一编排到状态机） ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo publish(String taskId) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.PUBLISH, buildContext(null, null));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo withdraw(String taskId) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.WITHDRAW, buildContext(null, null));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo distribute(String taskId, TaskDistributeDto dto) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.DISTRIBUTE,
                buildContext(dto.getAssigneeId(), null));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo start(String taskId) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.START, buildContext(null, null));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo switchBack(String taskId) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.SWITCH_BACK, buildContext(null, null));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo complete(String taskId) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.COMPLETE, buildContext(null, null));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo close(String taskId, TaskReasonDto dto) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.CLOSE,
                buildContext(null, dto == null ? null : dto.getReason()));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo reopen(String taskId, TaskReasonDto dto) {
        TaskInfoDo task = stateMachine.execute(requireTask(taskId), TaskActionEnum.REOPEN,
                buildContext(null, dto == null ? null : dto.getReason()));
        return toVo(task, requireProject(task.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo changePriority(String taskId, TaskPriorityDto dto) {
        UserPrincipal me = UserContext.require();
        TaskInfoDo task = requireTask(taskId);
        ProjectInfoDo project = requireProject(task.getProjectId());
        PriorityEnum newPriority = dto.getPriority();

        switch (task.getTaskStatus()) {
            case DRAFT -> {
                // 草稿态：仅创建者，无排序键
                if (!me.getUserId().equals(task.getCreatorId())) {
                    throw new BusinessException(ResultCode.NOT_CREATOR);
                }
                task.setPriority(newPriority);
            }
            case PENDING_DIST -> {
                requirePermission(task.getProjectId(), PermissionConstants.TASK_CHANGE_PRIORITY);
                task.setPriority(newPriority);
                // rank 重置为新优先级桶最后（排除自身后取桶尾）
                List<TaskInfoDo> queue = taskInfoRepository.listPendingDistQueue(task.getProjectId()).stream()
                        .filter(t -> !t.getId().equals(task.getId())).toList();
                int idx = taskRankService.tailIndexOfPriority(queue, newPriority);
                task.setSortRank(taskRankService.rankForInsert(queue, idx, task));
            }
            case WAITING -> {
                requirePermission(task.getProjectId(), PermissionConstants.TASK_CHANGE_PRIORITY);
                task.setPriority(newPriority);
                List<TaskInfoDo> queue = taskInfoRepository.listWaitingQueue(task.getAssigneeId()).stream()
                        .filter(t -> !t.getId().equals(task.getId())).toList();
                int idx = taskRankService.tailIndexOfPriority(queue, newPriority);
                task.setSortRank(taskRankService.rankForInsert(queue, idx, task));
            }
            default -> throw new BusinessException(ResultCode.ILLEGAL_TRANSITION,
                    "任务当前为「" + task.getTaskStatus().getLabel() + "」，不允许修改优先级");
        }
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
        writeLog(task, "changePriority", JSON.toJSONString(java.util.Map.of("priority", newPriority.getCode())), me);
        eventPublisher.publishEvent(TaskChangedEvent.of(this, task.getId(), task.getProjectId(),
                "PRIORITY_CHANGED", me.getUserId()));
        return toVo(task, project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo setProgress(String taskId, TaskProgressDto dto) {
        UserPrincipal me = UserContext.require();
        TaskInfoDo task = requireTask(taskId);
        requireProject(task.getProjectId());
        // 仅执行人本人可更新进度（与开始/完成口径一致）
        if (!me.getUserId().equals(task.getAssigneeId())) {
            throw new BusinessException(ResultCode.NOT_ASSIGNEE, "仅任务执行人可设置进度");
        }
        if (task.getTaskStatus() != TaskStatusEnum.WAITING && task.getTaskStatus() != TaskStatusEnum.IN_PROGRESS) {
            throw new BusinessException(ResultCode.ILLEGAL_TRANSITION,
                    "任务当前为「" + task.getTaskStatus().getLabel() + "」，不允许设置进度");
        }
        task.setProgress(dto.getProgress());
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
        writeLog(task, "setProgress", JSON.toJSONString(java.util.Map.of("progress", dto.getProgress())), me);
        eventPublisher.publishEvent(TaskChangedEvent.of(this, task.getId(), task.getProjectId(),
                "PROGRESS_CHANGED", me.getUserId()));
        return toVo(task, requireProject(task.getProjectId()));
    }

    // ---------------- 拖拽排序 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoVo sortRank(String taskId, TaskSortDto dto) {
        UserPrincipal me = UserContext.require();
        TaskInfoDo task = requireTask(taskId);
        ProjectInfoDo project = requireProject(task.getProjectId());

        if (task.getTaskStatus() == TaskStatusEnum.PENDING_DIST) {
            sortPendingDist(task, dto, me);
        } else if (task.getTaskStatus() == TaskStatusEnum.WAITING) {
            sortWaiting(task, dto, me);
        } else {
            throw new BusinessException(ResultCode.ILLEGAL_TRANSITION, "仅待分发/等待中任务支持拖拽排序");
        }
        if (!taskInfoRepository.updateById(task)) {
            throw new BusinessException(ResultCode.CONFLICT_REFRESH);
        }
        writeLog(task, "sortRank", JSON.toJSONString(java.util.Map.of(
                "beforeId", dto.getBeforeId() == null ? "" : dto.getBeforeId(),
                "afterId", dto.getAfterId() == null ? "" : dto.getAfterId())), me);
        eventPublisher.publishEvent(TaskChangedEvent.of(this, task.getId(), task.getProjectId(),
                "TASK_SORTED", me.getUserId()));
        return toVo(task, project);
    }

    /**
     * 待分发列排序：作用域=项目。需 pf:task:dragSort 权限；落点必须在本优先级桶内（跨级拒绝）。
     */
    private void sortPendingDist(TaskInfoDo task, TaskSortDto dto, UserPrincipal me) {
        if (!permissionChecker.hasPermission(me.getUserId(), task.getProjectId(), PermissionConstants.TASK_DRAG_SORT)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "拖拽排序需要 pf:task:dragSort 权限");
        }
        List<TaskInfoDo> queue = taskInfoRepository.listPendingDistQueue(task.getProjectId()).stream()
                .filter(t -> !t.getId().equals(task.getId())).toList();
        int insertIndex = resolveInsertIndex(queue, dto, task.getPriority());
        validateWithinBucket(queue, task.getPriority(), insertIndex);
        task.setSortRank(taskRankService.rankForInsert(queue, insertIndex, task));
    }

    /**
     * 等待中队列排序：作用域=执行人。
     * 执行人本人可拖拽；拖他人队列需 pf:task:dragCross（跨执行人拖拽）。
     * 落点必须在本优先级桶内（跨级拒绝，对所有操作人生效）。
     * 跨执行人拖拽拒绝（目标位置任务必须属于同一执行人队列）。
     */
    private void sortWaiting(TaskInfoDo task, TaskSortDto dto, UserPrincipal me) {
        String assigneeId = task.getAssigneeId();
        boolean selfDrag = me.getUserId().equals(assigneeId);
        if (!selfDrag && !permissionChecker.hasPermission(me.getUserId(), task.getProjectId(),
                PermissionConstants.TASK_DRAG_CROSS)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "跨执行人拖拽需要 pf:task:dragCross 权限");
        }
        List<TaskInfoDo> queue = taskInfoRepository.listWaitingQueue(assigneeId).stream()
                .filter(t -> !t.getId().equals(task.getId())).toList();
        int insertIndex = resolveInsertIndex(queue, dto, task.getPriority());
        validateWithinBucket(queue, task.getPriority(), insertIndex);
        task.setSortRank(taskRankService.rankForInsert(queue, insertIndex, task));
    }

    /**
     * 拖拽桶约束校验：落点必须在本优先级桶内，跨优先级拖拽一律拒绝。
     */
    private void validateWithinBucket(List<TaskInfoDo> queue, PriorityEnum priority, int insertIndex) {
        try {
            TaskQueueOrder.ensureWithinBucket(queue, priority, insertIndex);
        } catch (IllegalStateException e) {
            throw new BusinessException(ResultCode.DRAG_CROSS_PRIORITY);
        }
    }

    /**
     * 解析目标插入位置：beforeId 前插 / afterId 后插 / 均空则本优先级桶尾。
     * 邻居必须属于同一作用域队列（跨执行人拖拽在此双重拒绝）。
     */
    private int resolveInsertIndex(List<TaskInfoDo> queue, TaskSortDto dto, PriorityEnum priority) {
        if (dto.getBeforeId() != null && !dto.getBeforeId().isBlank()) {
            int idx = indexOfTask(queue, dto.getBeforeId());
            if (idx < 0) {
                throw new BusinessException(ResultCode.DRAG_CROSS_FORBIDDEN, "目标位置不在同一队列作用域内");
            }
            return idx;
        }
        if (dto.getAfterId() != null && !dto.getAfterId().isBlank()) {
            int idx = indexOfTask(queue, dto.getAfterId());
            if (idx < 0) {
                throw new BusinessException(ResultCode.DRAG_CROSS_FORBIDDEN, "目标位置不在同一队列作用域内");
            }
            return idx + 1;
        }
        return taskRankService.tailIndexOfPriority(queue, priority);
    }

    private int indexOfTask(List<TaskInfoDo> queue, String taskId) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    // ---------------- 查询 ----------------

    @Override
    public TaskInfoVo getTaskDetail(String taskId) {
        UserPrincipal me = UserContext.require();
        TaskInfoDo task = requireTask(taskId);
        ProjectInfoDo project = requireProject(task.getProjectId());
        boolean involved = me.getUserId().equals(task.getCreatorId())
                || me.getUserId().equals(task.getAssigneeId())
                || me.getUserId().equals(task.getDistributorId());
        if (!involved && !permissionChecker.hasPermission(me.getUserId(), task.getProjectId(),
                PermissionConstants.TASK_VIEW_PROJECT)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该任务");
        }
        return toVo(task, project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(String taskId) {
        UserPrincipal me = UserContext.require();
        TaskInfoDo task = requireTask(taskId);
        if (!me.getUserId().equals(task.getCreatorId())) {
            throw new BusinessException(ResultCode.NOT_CREATOR);
        }
        if (task.getTaskStatus() != TaskStatusEnum.DRAFT) {
            throw new BusinessException(ResultCode.ILLEGAL_TRANSITION, "仅草稿可删除");
        }
        taskInfoRepository.removeById(taskId);
        writeLog(task, "delete", null, me);
        eventPublisher.publishEvent(TaskChangedEvent.of(this, task.getId(), task.getProjectId(),
                "TASK_DELETED", me.getUserId()));
    }

    @Override
    public BoardVo getBoard(String projectId, TaskQueryDto query) {
        UserPrincipal me = UserContext.require();
        ProjectInfoDo project = requireProject(projectId);
        boolean canSeeWorkload = permissionChecker.hasPermission(me.getUserId(), projectId,
                PermissionConstants.TASK_VIEW_WORKLOAD);

        List<TaskInfoDo> all = filterTasks(taskInfoRepository.listByProject(projectId), query,
                Map.of(project.getId(), project));
        return buildBoardVo(all, project, canSeeWorkload);
    }

    @Override
    public BoardVo getBoardAcrossProjects(TaskQueryDto query) {
        UserPrincipal me = UserContext.require();
        // 可见范围 = 我参与的项目中具备 pf:task:viewProject 权限的项目（与项目看板口径一致）
        List<ProjectInfoDo> visible = projectInfoRepository.listByMemberUserId(me.getUserId()).stream()
                .filter(p -> permissionChecker.hasPermission(me.getUserId(), p.getId(),
                        PermissionConstants.TASK_VIEW_PROJECT))
                .toList();
        // 指定项目筛选：不在可见范围内按无权限处理
        if (query != null && query.getProjectId() != null && !query.getProjectId().isBlank()) {
            String target = query.getProjectId();
            if (visible.stream().noneMatch(p -> p.getId().equals(target))) {
                throw new BusinessException(ResultCode.NO_PERMISSION);
            }
            visible = visible.stream().filter(p -> p.getId().equals(target)).toList();
        }

        List<TaskInfoDo> all = new ArrayList<>();
        for (ProjectInfoDo project : visible) {
            all.addAll(taskInfoRepository.listByProject(project.getId()));
        }
        Map<String, ProjectInfoDo> projectMap = visible.stream()
                .collect(Collectors.toMap(ProjectInfoDo::getId, Function.identity()));
        // 队列前序数量：任一可见项目具备 pf:task:viewWorkload 即计算
        boolean canSeeWorkload = visible.stream().anyMatch(p -> permissionChecker.hasPermission(me.getUserId(),
                p.getId(), PermissionConstants.TASK_VIEW_WORKLOAD));
        // project 传 null：BoardVo 项目元信息留空，任务 VO 按各自所属项目翻译
        return buildBoardVo(filterTasks(all, query, projectMap), null, canSeeWorkload);
    }

    /**
     * 六列看板聚合（项目看板与跨项目看板共用）：project 为 null 表示跨项目视图
     */
    private BoardVo buildBoardVo(List<TaskInfoDo> all, ProjectInfoDo project, boolean canSeeWorkload) {
        UserPrincipal me = UserContext.require();
        LocalDateTime now = LocalDateTime.now();

        // 六列数据
        List<TaskInfoDo> drafts = all.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.DRAFT)
                .filter(t -> me.getUserId().equals(t.getCreatorId()))
                .sorted(Comparator.comparing(TaskInfoDo::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<TaskInfoDo> pending = all.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.PENDING_DIST)
                .sorted(TaskQueueOrder.DISPLAY_ORDER)
                .toList();
        Map<String, List<TaskInfoDo>> waitingByAssignee = groupByAssignee(all, TaskStatusEnum.WAITING, me.getUserId());
        Map<String, List<TaskInfoDo>> inProgressByAssignee = groupInProgress(all);
        List<TaskInfoDo> completed = all.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.COMPLETED)
                .sorted(Comparator.comparing(TaskInfoDo::getCompletedTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(HISTORY_LIMIT)
                .toList();
        List<TaskInfoDo> closed = all.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.CLOSED)
                .sorted(Comparator.comparing(TaskInfoDo::getClosedTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(HISTORY_LIMIT)
                .toList();

        BoardVo board = new BoardVo();
        if (project != null) {
            board.setProjectId(project.getId());
            board.setProjectName(project.getProjectName());
            board.setProjectKey(project.getProjectKey());
        }
        board.setDraftList(toVoList(drafts, project));
        board.setPendingDistList(toVoList(pending, project));
        // 队列前序数量仅对等待中队列有意义（进行中按开始时间倒序，无前序概念）
        board.setWaitingGroupList(toGroupList(waitingByAssignee, project, canSeeWorkload, true));
        board.setInProgressGroupList(toGroupList(inProgressByAssignee, project, canSeeWorkload, false));
        board.setCompletedList(toVoList(completed, project));
        board.setClosedList(toVoList(closed, project));
        return board;
    }

    @Override
    public List<TaskInfoVo> mine(String scope, String projectId) {
        UserPrincipal me = UserContext.require();
        List<TaskInfoDo> tasks;
        switch (scope == null ? "" : scope) {
            case "waiting" -> {
                tasks = taskInfoRepository.listByAssigneeAndStatus(me.getUserId(), List.of(TaskStatusEnum.WAITING));
                tasks = tasks.stream()
                        .sorted(TaskQueueOrder.DISPLAY_ORDER)
                        .toList();
            }
            case "in_progress" -> {
                tasks = taskInfoRepository.listByAssigneeAndStatus(me.getUserId(), List.of(TaskStatusEnum.IN_PROGRESS));
                tasks = tasks.stream().sorted(Comparator.comparing(TaskInfoDo::getStartedTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))).toList();
            }
            case "completed" -> {
                tasks = taskInfoRepository.listByAssigneeAndStatus(me.getUserId(), List.of(TaskStatusEnum.COMPLETED));
                tasks = tasks.stream().sorted(Comparator.comparing(TaskInfoDo::getCompletedTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(HISTORY_LIMIT).toList();
            }
            default -> throw new BusinessException(ResultCode.BAD_PARAM, "scope 仅支持 waiting/in_progress/completed");
        }
        // 项目筛选条件（跨项目默认全量）
        if (projectId != null && !projectId.isBlank()) {
            tasks = tasks.stream().filter(t -> projectId.equals(t.getProjectId())).toList();
        }
        // 任务编号补全
        Map<String, ProjectInfoDo> projects = loadProjects(tasks);
        return tasks.stream().map(t -> toVo(t, projects.get(t.getProjectId()))).toList();
    }

    @Override
    public PageResult<TaskInfoVo> myDrafts(long pageNum, long pageSize) {
        UserPrincipal me = UserContext.require();
        Page<TaskInfoDo> page = taskInfoRepository.page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<TaskInfoDo>()
                        .eq(TaskInfoDo::getCreatorId, me.getUserId())
                        .eq(TaskInfoDo::getTaskStatus, TaskStatusEnum.DRAFT)
                        .orderByDesc(TaskInfoDo::getUpdateTime));
        Map<String, ProjectInfoDo> projects = loadProjects(page.getRecords());
        Page<TaskInfoVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(t -> toVo(t, projects.get(t.getProjectId()))).toList());
        return PageResult.of(voPage);
    }

    @Override
    public List<WorkloadVo> projectWorkload(String projectId) {
        ProjectInfoDo project = requireProject(projectId);
        List<ProjectMemberDo> members = projectMemberRepository.listByProjectId(projectId);
        List<TaskInfoDo> tasks = taskInfoRepository.listByProject(projectId);
        Map<String, List<TaskInfoDo>> byAssignee = tasks.stream()
                .filter(t -> t.getAssigneeId() != null)
                .collect(Collectors.groupingBy(TaskInfoDo::getAssigneeId));
        List<String> userIds = members.stream().map(ProjectMemberDo::getUserId).toList();
        Map<String, SysUserDo> userMap = userIds.isEmpty() ? Map.of()
                : sysUserRepository.listByIds(userIds).stream()
                        .collect(Collectors.toMap(SysUserDo::getId, Function.identity()));

        return members.stream().map(member -> {
            WorkloadVo vo = new WorkloadVo();
            vo.setUserId(member.getUserId());
            SysUserDo user = userMap.get(member.getUserId());
            vo.setDisplayName(user == null ? member.getUserId() : user.getDisplayName());
            List<TaskInfoDo> mine = byAssignee.getOrDefault(member.getUserId(), List.of());
            vo.setCurrentTask(mine.stream()
                    .filter(t -> t.getTaskStatus() == TaskStatusEnum.IN_PROGRESS)
                    .max(Comparator.comparing(TaskInfoDo::getStartedTime,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(t -> toVo(t, project)).orElse(null));
            vo.setWaitingCount((int) mine.stream()
                    .filter(t -> t.getTaskStatus() == TaskStatusEnum.WAITING).count());
            return vo;
        }).toList();
    }

    @Override
    public List<CrossProjectWorkloadVo> crossProjectWorkload() {
        UserPrincipal me = UserContext.require();
        List<ProjectInfoDo> myProjects = projectInfoRepository.listByMemberUserId(me.getUserId());
        List<CrossProjectWorkloadVo> result = new ArrayList<>();
        for (ProjectInfoDo project : myProjects) {
            // 无 projectId 入参，切面无法定位项目：逐项目校验 pf:task:viewMasked
            if (!permissionChecker.hasPermission(me.getUserId(), project.getId(),
                    PermissionConstants.TASK_VIEW_MASKED)) {
                continue;
            }
            // 脱敏：只回项目 + 优先级，不下发任务标题/描述等明细
            taskInfoRepository.listByProject(project.getId()).stream()
                    .filter(t -> t.getTaskStatus() == TaskStatusEnum.IN_PROGRESS)
                    .forEach(t -> {
                        CrossProjectWorkloadVo vo = new CrossProjectWorkloadVo();
                        vo.setProjectId(project.getId());
                        vo.setProjectName(project.getProjectName());
                        vo.setPriority(t.getPriority() == null ? null : t.getPriority().getCode());
                        result.add(vo);
                    });
        }
        return result;
    }

    @Override
    public List<TaskLogVo> getLogs(String taskId) {
        requireTask(taskId);
        List<TaskOperationLogDo> logs = operationLogRepository.listByTaskId(taskId);
        List<String> operatorIds = logs.stream().map(TaskOperationLogDo::getOperatorId).distinct().toList();
        Map<String, SysUserDo> userMap = operatorIds.isEmpty() ? Map.of()
                : sysUserRepository.listByIds(operatorIds).stream()
                        .collect(Collectors.toMap(SysUserDo::getId, Function.identity()));
        return logs.stream().map(logDo -> {
            TaskLogVo vo = new TaskLogVo();
            org.springframework.beans.BeanUtils.copyProperties(logDo, vo);
            SysUserDo operator = userMap.get(logDo.getOperatorId());
            vo.setOperatorName(operator == null ? logDo.getOperatorId() : operator.getDisplayName());
            return vo;
        }).toList();
    }

    // ---------------- 私有工具 ----------------

    /**
     * 看板筛选：执行人/优先级/类型/截止时间区间/仅逾期/关键字（标题、描述、任务编号）。
     * 项目映射用于按任务所属项目翻译任务编号（跨项目视图任务来自多个项目）。
     */
    private List<TaskInfoDo> filterTasks(List<TaskInfoDo> tasks, TaskQueryDto query,
                                         Map<String, ProjectInfoDo> projectMap) {
        if (query == null) {
            return tasks;
        }
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream().filter(t -> {
            if (query.getAssigneeId() != null && !query.getAssigneeId().equals(t.getAssigneeId())) {
                return false;
            }
            if (query.getPriority() != null && query.getPriority() != t.getPriority()) {
                return false;
            }
            if (query.getTaskType() != null && query.getTaskType() != t.getTaskType()) {
                return false;
            }
            if (query.getDeadlineFrom() != null && (t.getDeadline() == null || t.getDeadline().isBefore(query.getDeadlineFrom()))) {
                return false;
            }
            if (query.getDeadlineTo() != null && (t.getDeadline() == null || t.getDeadline().isAfter(query.getDeadlineTo()))) {
                return false;
            }
            if (Boolean.TRUE.equals(query.getOverdueOnly())
                    && !isOverdue(t, now)) {
                return false;
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                String keyword = query.getKeyword().trim();
                boolean hitTitle = t.getTitle() != null && t.getTitle().contains(keyword);
                boolean hitDesc = t.getDescription() != null && t.getDescription().contains(keyword);
                boolean hitTaskNo = buildTaskNo(projectMap.get(t.getProjectId()), t).equalsIgnoreCase(keyword);
                if (!hitTitle && !hitDesc && !hitTaskNo) {
                    return false;
                }
            }
            return true;
        }).toList();
    }

    private boolean isOverdue(TaskInfoDo task, LocalDateTime now) {
        return task.getDeadline() != null && task.getDeadline().isBefore(now)
                && (task.getTaskStatus() == TaskStatusEnum.WAITING
                || task.getTaskStatus() == TaskStatusEnum.IN_PROGRESS);
    }

    private Map<String, List<TaskInfoDo>> groupByAssignee(List<TaskInfoDo> tasks, TaskStatusEnum status, String viewerId) {
        Map<String, List<TaskInfoDo>> grouped = tasks.stream()
                .filter(t -> t.getTaskStatus() == status && t.getAssigneeId() != null)
                .collect(Collectors.groupingBy(TaskInfoDo::getAssigneeId, LinkedHashMap::new, Collectors.toList()));
        grouped.values().forEach(list -> list.sort(TaskQueueOrder.DISPLAY_ORDER));
        return grouped;
    }

    private Map<String, List<TaskInfoDo>> groupInProgress(List<TaskInfoDo> tasks) {
        Map<String, List<TaskInfoDo>> grouped = tasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatusEnum.IN_PROGRESS && t.getAssigneeId() != null)
                .collect(Collectors.groupingBy(TaskInfoDo::getAssigneeId, LinkedHashMap::new, Collectors.toList()));
        grouped.values().forEach(list -> list.sort(Comparator.comparing(TaskInfoDo::getStartedTime,
                Comparator.nullsLast(Comparator.reverseOrder()))));
        return grouped;
    }

    private List<AssigneeGroupVo> toGroupList(Map<String, List<TaskInfoDo>> grouped,
                                              ProjectInfoDo project, boolean canSeeWorkload, boolean withAheadCount) {
        List<AssigneeGroupVo> groups = new ArrayList<>();
        for (Map.Entry<String, List<TaskInfoDo>> entry : grouped.entrySet()) {
            AssigneeGroupVo group = new AssigneeGroupVo();
            group.setUserId(entry.getKey());
            SysUserDo user = sysUserRepository.getById(entry.getKey());
            group.setUserName(user == null ? entry.getKey() : user.getDisplayName());
            // 队列前序数量：对自己创建的任务/有 pf:task:viewWorkload 权限者计算
            List<TaskInfoVo> voList = toVoList(entry.getValue(), project);
            if (withAheadCount) {
                for (int i = 0; i < voList.size(); i++) {
                    TaskInfoVo vo = voList.get(i);
                    if (canSeeWorkload || (vo.getCreatorId() != null && vo.getCreatorId().equals(UserContext.require().getUserId()))) {
                        vo.setWaitingAheadCount(i);
                    }
                }
            }
            group.setTaskList(voList);
            groups.add(group);
        }
        return groups;
    }

    private List<TaskInfoVo> toVoList(List<TaskInfoDo> tasks, ProjectInfoDo project) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<String, ProjectInfoDo> projects = loadProjects(tasks);
        return tasks.stream().map(t -> toVo(t, project != null ? project : projects.get(t.getProjectId()))).toList();
    }

    /**
     * PO → VO：任务编号、人名翻译（批量场景走 toVoList）
     */
    private TaskInfoVo toVo(TaskInfoDo task, ProjectInfoDo project) {
        TaskInfoVo vo = new TaskInfoVo();
        org.springframework.beans.BeanUtils.copyProperties(task, vo);
        String projectKey = project != null ? project.getProjectKey() : null;
        vo.setProjectKey(projectKey);
        vo.setProjectName(project != null ? project.getProjectName() : null);
        vo.setTaskNo(buildTaskNo(project, task));
        // 人名翻译
        Set<String> userIds = new HashSet<>();
        if (task.getAssigneeId() != null) {
            userIds.add(task.getAssigneeId());
        }
        if (task.getCreatorId() != null) {
            userIds.add(task.getCreatorId());
        }
        if (task.getDistributorId() != null) {
            userIds.add(task.getDistributorId());
        }
        if (!userIds.isEmpty()) {
            Map<String, String> nameMap = sysUserRepository.listByIds(userIds).stream()
                    .collect(Collectors.toMap(SysUserDo::getId, SysUserDo::getDisplayName));
            vo.setAssigneeName(nameMap.get(task.getAssigneeId()));
            vo.setCreatorName(nameMap.get(task.getCreatorId()));
            vo.setDistributorName(nameMap.get(task.getDistributorId()));
        }
        return vo;
    }

    private String buildTaskNo(ProjectInfoDo project, TaskInfoDo task) {
        String key = project != null ? project.getProjectKey() : null;
        return (key == null ? "" : key) + "-" + task.getSeqNo();
    }

    private Map<String, ProjectInfoDo> loadProjects(List<TaskInfoDo> tasks) {
        List<String> projectIds = tasks.stream().map(TaskInfoDo::getProjectId).distinct().toList();
        return projectIds.isEmpty() ? Map.of()
                : projectInfoRepository.listByIds(projectIds).stream()
                        .collect(Collectors.toMap(ProjectInfoDo::getId, Function.identity()));
    }

    private TaskInfoDo requireTask(String taskId) {
        TaskInfoDo task = taskInfoRepository.getById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private ProjectInfoDo requireProject(String projectId) {
        ProjectInfoDo project = projectInfoRepository.getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "项目不存在");
        }
        return project;
    }

    private void requirePermission(String projectId, String permissionCode) {
        if (!permissionChecker.hasPermission(UserContext.require().getUserId(), projectId, permissionCode)) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
    }

    /**
     * 组装动作上下文（含来源 IP/设备）
     */
    private TaskActionContext buildContext(String assigneeId, String reason) {
        UserPrincipal me = UserContext.require();
        String ip = null;
        String device = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            ip = attributes.getRequest().getRemoteAddr();
            String userAgent = attributes.getRequest().getHeader("User-Agent");
            if (userAgent != null) {
                device = userAgent.substring(0, Math.min(255, userAgent.length()));
            }
        }
        return TaskActionContext.builder()
                .userId(me.getUserId())
                .assigneeId(assigneeId)
                .reason(reason)
                .ip(ip)
                .device(device)
                .build();
    }

    /**
     * 记录非状态流转动作日志（创建/编辑/排序/删除等）
     */
    private void writeLog(TaskInfoDo task, String action, String detail, UserPrincipal operator) {
        TaskOperationLogDo logDo = new TaskOperationLogDo();
        logDo.setTaskId(task.getId());
        logDo.setProjectId(task.getProjectId());
        logDo.setOperatorId(operator.getUserId());
        logDo.setAction(action);
        logDo.setFromStatus(task.getTaskStatus());
        logDo.setToStatus(task.getTaskStatus());
        logDo.setDetail(detail);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            logDo.setIp(attributes.getRequest().getRemoteAddr());
            String userAgent = attributes.getRequest().getHeader("User-Agent");
            if (userAgent != null) {
                logDo.setDevice(userAgent.substring(0, Math.min(255, userAgent.length())));
            }
        }
        operationLogRepository.save(logDo);
    }
}
