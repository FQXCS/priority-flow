package com.sdecloud.priorityflow.system.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码入参（系统管理员）。
 *
 * @author priority-flow
 */
@Data
public class ResetPasswordDto {

    /** 新密码（6~64 位） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在 6~64 位之间")
    private String newPassword;
}
