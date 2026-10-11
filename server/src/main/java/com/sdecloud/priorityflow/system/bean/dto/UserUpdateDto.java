package com.sdecloud.priorityflow.system.bean.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户入参（系统管理员）。
 *
 * @author priority-flow
 */
@Data
public class UserUpdateDto {

    /** 显示名 */
    @Size(max = 64, message = "显示名最长 64 字符")
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 状态（0 启用 / 1 禁用） */
    private String status;

    /** 备注 */
    private String remark;
}
