package com.sdecloud.priorityflow.collab.bean.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 预签名直传结果 VO。
 *
 * @author priority-flow
 */
@Data
public class PresignVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** MinIO 对象键 */
    private String objectKey;

    /** PUT 上传预签名 URL（短时效 15 分钟） */
    private String uploadUrl;

    /** 有效期（秒） */
    private Long expireSeconds;
}
