package com.sdecloud.priorityflow.task.controller;

import com.sdecloud.priorityflow.common.api.PageResult;
import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.RequireProjectPermission;
import com.sdecloud.priorityflow.task.bean.dto.TaskCreateDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskDistributeDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskPriorityDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskProgressDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskQueryDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskReasonDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskSortDto;
import com.sdecloud.priorityflow.task.bean.dto.TaskUpdateDto;
import com.sdecloud.priorityflow.task.bean.vo.BoardVo;
import com.sdecloud.priorityflow.task.bean.vo.TaskInfoVo;
import com.sdecloud.priorityflow.task.bean.vo.TaskLogVo;
import com.sdecloud.priorityflow.task.bean.vo.WorkloadVo;
import com.sdecloud.priorityflow.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务接口：CRUD、状态流转、排序、看板、草稿、负载、日志。
 *
 * @author priority-flow
 */
@Tag(name = "任务", description = "任务创建/发布/分发/执行/排序/看板")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TaskController {

    private final TaskService taskService;

    // ---------------- 创建与编辑 ----------------

    @Operation(summary = "创建任务", description = "body 带 action=DRAFT/PUBLISH（projectId 在请求体内，权限在 service 层校验）")
    @PostMapping("/tasks")
    public R<TaskInfoVo> create(@Valid @RequestBody TaskCreateDto dto) {
        return R.ok(taskService.createTask(dto));
    }

    @Operation(summary = "编辑任务", description = "草稿仅创建者可编辑；待分发需发布权限")
    @PutMapping("/tasks/{taskId}")
    public R<TaskInfoVo> update(@PathVariable String taskId, @Valid @RequestBody TaskUpdateDto dto) {
        return R.ok(taskService.updateTask(taskId, dto));
    }

    @Operation(summary = "删除草稿", description = "仅创建者、草稿态")
    @DeleteMapping("/tasks/{taskId}")
    public R<Void> deleteDraft(@PathVariable String taskId) {
        taskService.deleteDraft(taskId);
        return R.ok();
    }

    @Operation(summary = "任务详情")
    @GetMapping("/tasks/{taskId}")
    public R<TaskInfoVo> detail(@PathVariable String taskId) {
        return R.ok(taskService.getTaskDetail(taskId));
    }

    // ---------------- 状态流转 ----------------

    @Operation(summary = "发布任务", description = "DRAFT → PENDING_DIST")
    @PostMapping("/tasks/{taskId}/publish")
    public R<TaskInfoVo> publish(@PathVariable String taskId) {
        return R.ok(taskService.publish(taskId));
    }

    @Operation(summary = "撤回待分发", description = "仅创建者；PENDING_DIST → DRAFT")
    @PostMapping("/tasks/{taskId}/withdraw")
    public R<TaskInfoVo> withdraw(@PathVariable String taskId) {
        return R.ok(taskService.withdraw(taskId));
    }

    @Operation(summary = "分发任务", description = "PENDING_DIST → WAITING，指定执行人")
    @RequireProjectPermission(PermissionConstants.TASK_DISTRIBUTE)
    @PostMapping("/tasks/{taskId}/distribute")
    public R<TaskInfoVo> distribute(@PathVariable String taskId, @Valid @RequestBody TaskDistributeDto dto) {
        return R.ok(taskService.distribute(taskId, dto));
    }

    @Operation(summary = "开始任务", description = "WAITING → IN_PROGRESS，执行人无其他进行中任务")
    @PostMapping("/tasks/{taskId}/start")
    public R<TaskInfoVo> start(@PathVariable String taskId) {
        return R.ok(taskService.start(taskId));
    }

    @Operation(summary = "切换回等待中", description = "IN_PROGRESS → WAITING，保留原排序位")
    @PostMapping("/tasks/{taskId}/switch-back")
    public R<TaskInfoVo> switchBack(@PathVariable String taskId) {
        return R.ok(taskService.switchBack(taskId));
    }

    @Operation(summary = "完成任务", description = "IN_PROGRESS → COMPLETED")
    @PostMapping("/tasks/{taskId}/complete")
    public R<TaskInfoVo> complete(@PathVariable String taskId) {
        return R.ok(taskService.complete(taskId));
    }

    @Operation(summary = "关闭任务", description = "COMPLETED/WAITING → CLOSED，需原因")
    @RequireProjectPermission(PermissionConstants.TASK_CLOSE)
    @PostMapping("/tasks/{taskId}/close")
    public R<TaskInfoVo> close(@PathVariable String taskId, @Valid @RequestBody TaskReasonDto dto) {
        return R.ok(taskService.close(taskId, dto));
    }

    @Operation(summary = "重新打开", description = "CLOSED → IN_PROGRESS 或 WAITING 同优先级桶首，需原因")
    @RequireProjectPermission(PermissionConstants.TASK_REOPEN)
    @PostMapping("/tasks/{taskId}/reopen")
    public R<TaskInfoVo> reopen(@PathVariable String taskId, @Valid @RequestBody TaskReasonDto dto) {
        return R.ok(taskService.reopen(taskId, dto));
    }

    @Operation(summary = "修改优先级", description = "rank 重置为新优先级桶最后")
    @RequireProjectPermission(PermissionConstants.TASK_CHANGE_PRIORITY)
    @PostMapping("/tasks/{taskId}/priority")
    public R<TaskInfoVo> changePriority(@PathVariable String taskId, @Valid @RequestBody TaskPriorityDto dto) {
        return R.ok(taskService.changePriority(taskId, dto));
    }

    @Operation(summary = "设置进度", description = "仅执行人本人、等待中/进行中；0-99，100 仅由完成动作产生")
    @PostMapping("/tasks/{taskId}/progress")
    public R<TaskInfoVo> setProgress(@PathVariable String taskId, @Valid @RequestBody TaskProgressDto dto) {
        return R.ok(taskService.setProgress(taskId, dto));
    }

    // ---------------- 排序 ----------------

    @Operation(summary = "拖拽排序", description = "仅限同优先级桶内调整顺序；服务端计算 LexoRank，body 只传 beforeId/afterId，均空为桶尾")
    @RequireProjectPermission(PermissionConstants.TASK_DRAG_SORT)
    @PutMapping("/tasks/{taskId}/sort-rank")
    public R<TaskInfoVo> sortRank(@PathVariable String taskId, @RequestBody TaskSortDto dto) {
        return R.ok(taskService.sortRank(taskId, dto));
    }

    // ---------------- 看板 / 我的任务 / 草稿 ----------------

    @Operation(summary = "项目看板", description = "一次性返回六列结构化数据（含按执行人分组）")
    @RequireProjectPermission(PermissionConstants.TASK_VIEW_PROJECT)
    @GetMapping("/projects/{projectId}/board")
    public R<BoardVo> board(@PathVariable String projectId, TaskQueryDto query) {
        return R.ok(taskService.getBoard(projectId, query));
    }

    @Operation(summary = "跨项目看板", description = "默认聚合我可见的全部项目任务；projectId 参数非空时收窄到该项目（可见性=成员且具备 pf:task:viewProject）")
    @GetMapping("/board")
    public R<BoardVo> boardAcrossProjects(TaskQueryDto query) {
        return R.ok(taskService.getBoardAcrossProjects(query));
    }

    @Operation(summary = "我的任务", description = "scope=waiting/in_progress/completed；projectId 非空时按项目筛选")
    @GetMapping("/tasks/mine")
    public R<List<TaskInfoVo>> mine(@RequestParam String scope,
                                    @RequestParam(required = false) String projectId) {
        return R.ok(taskService.mine(scope, projectId));
    }

    @Operation(summary = "我的草稿", description = "update_time 倒序分页")
    @GetMapping("/tasks/my-drafts")
    public R<PageResult<TaskInfoVo>> myDrafts(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(taskService.myDrafts(pageNum, pageSize));
    }

    // ---------------- 负载 ----------------

    @Operation(summary = "项目人员负载", description = "人员当前任务可见性（需 pf:task:viewWorkload）")
    @RequireProjectPermission(PermissionConstants.TASK_VIEW_WORKLOAD)
    @GetMapping("/projects/{projectId}/workload")
    public R<List<WorkloadVo>> workload(@PathVariable String projectId) {
        return R.ok(taskService.projectWorkload(projectId));
    }

    // ---------------- 操作日志 ----------------

    @Operation(summary = "任务操作日志", description = "状态历史与操作日志（需 pf:log:view）")
    @RequireProjectPermission(PermissionConstants.LOG_VIEW)
    @GetMapping("/tasks/{taskId}/logs")
    public R<List<TaskLogVo>> logs(@PathVariable String taskId) {
        return R.ok(taskService.getLogs(taskId));
    }
}
