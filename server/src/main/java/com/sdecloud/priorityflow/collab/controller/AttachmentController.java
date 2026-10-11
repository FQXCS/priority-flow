package com.sdecloud.priorityflow.collab.controller;

import com.sdecloud.priorityflow.collab.bean.dto.AttachmentPresignDto;
import com.sdecloud.priorityflow.collab.bean.dto.AttachmentRegisterDto;
import com.sdecloud.priorityflow.collab.bean.vo.AttachmentVo;
import com.sdecloud.priorityflow.collab.bean.vo.PresignVo;
import com.sdecloud.priorityflow.collab.service.AttachmentService;
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
 * 附件接口：MinIO 预签名直传（前端拿 PUT URL 直传对象存储，服务端只登记元数据）。
 *
 * @author priority-flow
 */
@Tag(name = "附件", description = "附件预签名直传与登记")
@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "申请上传预签名 URL", description = "短时效 15 分钟，前端直传 MinIO")
    @RequireProjectPermission(PermissionConstants.ATTACHMENT_UPLOAD)
    @PostMapping("/api/tasks/{taskId}/attachments/presign")
    public R<PresignVo> presign(@PathVariable String taskId, @Valid @RequestBody AttachmentPresignDto dto) {
        return R.ok(attachmentService.presignUpload(taskId, dto));
    }

    @Operation(summary = "登记附件", description = "直传完成后登记元数据（校验对象键归属）")
    @RequireProjectPermission(PermissionConstants.ATTACHMENT_UPLOAD)
    @PostMapping("/api/tasks/{taskId}/attachments")
    public R<AttachmentVo> register(@PathVariable String taskId, @Valid @RequestBody AttachmentRegisterDto dto) {
        return R.ok(attachmentService.register(taskId, dto));
    }

    @Operation(summary = "附件列表", description = "附短时效下载 URL")
    @RequireProjectPermission(PermissionConstants.TASK_VIEW_PROJECT)
    @GetMapping("/api/tasks/{taskId}/attachments")
    public R<List<AttachmentVo>> list(@PathVariable String taskId) {
        return R.ok(attachmentService.listByTask(taskId));
    }
}
