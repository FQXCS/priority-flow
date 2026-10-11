package com.sdecloud.priorityflow.collab.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 预签名直传申请入参。
 *
 * @author priority-flow
 */
@Data
public class AttachmentPresignDto {

    /** 文件名 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 内容类型 */
    private String contentType;
}
