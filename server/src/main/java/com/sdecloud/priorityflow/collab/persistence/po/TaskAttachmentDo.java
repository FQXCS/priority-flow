package com.sdecloud.priorityflow.collab.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务附件实体（MinIO object_key + 预签名 URL 直传）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_attachment")
public class TaskAttachmentDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 文件名 */
    private String fileName;

    /** MinIO 对象键 */
    private String objectKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容类型 */
    private String contentType;

    /** 上传人 ID */
    private String uploaderId;
}
