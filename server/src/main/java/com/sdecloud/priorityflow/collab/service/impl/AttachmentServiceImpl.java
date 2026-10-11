package com.sdecloud.priorityflow.collab.service.impl;

import cn.hutool.core.util.IdUtil;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.collab.bean.dto.AttachmentPresignDto;
import com.sdecloud.priorityflow.collab.bean.dto.AttachmentRegisterDto;
import com.sdecloud.priorityflow.collab.bean.vo.AttachmentVo;
import com.sdecloud.priorityflow.collab.bean.vo.PresignVo;
import com.sdecloud.priorityflow.collab.persistence.po.TaskAttachmentDo;
import com.sdecloud.priorityflow.collab.persistence.repository.TaskAttachmentRepository;
import com.sdecloud.priorityflow.collab.service.AttachmentService;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 附件服务实现：预签名 URL 短时效（默认 15 分钟）。
 *
 * @author priority-flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    /** 对象键前缀：task/{taskId}/ */
    private static final String OBJECT_KEY_PREFIX = "task/";

    private final io.minio.MinioClient minioClient;

    private final com.sdecloud.priorityflow.common.config.MinioConfig minioConfig;

    private final TaskAttachmentRepository taskAttachmentRepository;

    private final TaskInfoRepository taskInfoRepository;

    private final SysUserRepository sysUserRepository;

    /** 预签名有效期（分钟） */
    @Value("${pf.presign-expire-minutes}")
    private int presignExpireMinutes;

    @Override
    public PresignVo presignUpload(String taskId, AttachmentPresignDto dto) {
        requireTask(taskId);
        String objectKey = OBJECT_KEY_PREFIX + taskId + "/" + IdUtil.fastSimpleUUID()
                + "-" + sanitizeFileName(dto.getFileName());
        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .expiry(presignExpireMinutes)
                    .extraQueryParams(dto.getContentType() == null ? Map.of()
                            : Map.of("Content-Type", dto.getContentType()))
                    .build());
            PresignVo vo = new PresignVo();
            vo.setObjectKey(objectKey);
            vo.setUploadUrl(uploadUrl);
            vo.setExpireSeconds(presignExpireMinutes * 60L);
            return vo;
        } catch (Exception e) {
            log.error("生成上传预签名 URL 失败：taskId={}", taskId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "生成上传预签名 URL 失败");
        }
    }

    @Override
    public AttachmentVo register(String taskId, AttachmentRegisterDto dto) {
        String userId = UserContext.require().getUserId();
        requireTask(taskId);
        // 防伪造：对象键必须归属当前任务
        String expectedPrefix = OBJECT_KEY_PREFIX + taskId + "/";
        if (dto.getObjectKey() == null || !dto.getObjectKey().startsWith(expectedPrefix)) {
            throw new BusinessException(ResultCode.BAD_PARAM, "对象键与任务不匹配");
        }
        TaskAttachmentDo attachment = new TaskAttachmentDo();
        attachment.setTaskId(taskId);
        attachment.setFileName(sanitizeFileName(dto.getFileName()));
        attachment.setObjectKey(dto.getObjectKey());
        attachment.setFileSize(dto.getFileSize());
        attachment.setContentType(dto.getContentType());
        attachment.setUploaderId(userId);
        taskAttachmentRepository.save(attachment);

        AttachmentVo vo = new AttachmentVo();
        BeanUtils.copyProperties(attachment, vo);
        vo.setUploaderName(UserContext.require().getDisplayName());
        vo.setDownloadUrl(presignDownload(dto.getObjectKey()));
        return vo;
    }

    @Override
    public List<AttachmentVo> listByTask(String taskId) {
        requireTask(taskId);
        List<TaskAttachmentDo> attachments = taskAttachmentRepository.listByTaskId(taskId);
        Set<String> uploaderIds = attachments.stream().map(TaskAttachmentDo::getUploaderId).collect(Collectors.toSet());
        Map<String, SysUserDo> userMap = uploaderIds.isEmpty() ? Map.of()
                : sysUserRepository.listByIds(uploaderIds).stream()
                        .collect(Collectors.toMap(SysUserDo::getId, Function.identity()));
        return attachments.stream().map(attachment -> {
            AttachmentVo vo = new AttachmentVo();
            BeanUtils.copyProperties(attachment, vo);
            SysUserDo uploader = userMap.get(attachment.getUploaderId());
            vo.setUploaderName(uploader == null ? attachment.getUploaderId() : uploader.getDisplayName());
            vo.setDownloadUrl(presignDownload(attachment.getObjectKey()));
            return vo;
        }).toList();
    }

    /**
     * 生成下载预签名 URL（短时效）
     */
    private String presignDownload(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .expiry(presignExpireMinutes)
                    .build());
        } catch (Exception e) {
            log.error("生成下载预签名 URL 失败：objectKey={}", objectKey, e);
            return null;
        }
    }

    /**
     * 清洗文件名：仅保留常用安全字符，防路径穿越
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "file";
        }
        String cleaned = fileName.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        return cleaned.substring(0, Math.min(180, cleaned.length()));
    }

    private TaskInfoDo requireTask(String taskId) {
        TaskInfoDo task = taskInfoRepository.getById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }
}
