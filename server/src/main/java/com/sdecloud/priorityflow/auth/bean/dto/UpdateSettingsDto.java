package com.sdecloud.priorityflow.auth.bean.dto;

import lombok.Data;

import java.util.Map;

/**
 * 个人偏好设置入参（整体覆盖存储）。
 *
 * @author priority-flow
 */
@Data
public class UpdateSettingsDto {

    /** 偏好设置（主题/密度等） */
    private Map<String, Object> settings;
}
