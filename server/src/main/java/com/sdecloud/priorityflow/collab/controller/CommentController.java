package com.sdecloud.priorityflow.collab.controller;

import com.sdecloud.priorityflow.collab.bean.dto.CommentCreateDto;
import com.sdecloud.priorityflow.collab.bean.vo.CommentVo;
import com.sdecloud.priorityflow.collab.service.CommentService;
import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.RequireProjectPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评论接口。
 *
 * @author priority-flow
 */
@Tag(name = "评论", description = "任务评论（支持 @ 提及）")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "评论列表", description = "时间正序")
    @RequireProjectPermission(PermissionConstants.TASK_VIEW_PROJECT)
    @GetMapping("/api/tasks/{taskId}/comments")
    public R<List<CommentVo>> list(@PathVariable String taskId) {
        return R.ok(commentService.listByTask(taskId));
    }

    @Operation(summary = "发表评论", description = "@ 提及的人会收到额外通知")
    @RequireProjectPermission(PermissionConstants.COMMENT_CREATE)
    @PostMapping("/api/tasks/{taskId}/comments")
    public R<CommentVo> create(@PathVariable String taskId, @Valid @RequestBody CommentCreateDto dto) {
        return R.ok(commentService.create(taskId, dto));
    }
}
