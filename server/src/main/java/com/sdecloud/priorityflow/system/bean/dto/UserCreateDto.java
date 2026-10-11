package com.sdecloud.priorityflow.system.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户入参（系统管理员创建用户并初始化密码）。
 *
 * @author priority-flow
 */
@Data
public class UserCreateDto {

    /** 登录名（字母开头，字母/数字/下划线，3~32 位） */
    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$", message = "登录名需为字母开头的 3~32 位字母/数字/下划线")
    private String username;

    /** 显示名 */
    @NotBlank(message = "显示名不能为空")
    @Size(max = 64, message = "显示名最长 64 字符")
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 初始密码（6~64 位） */
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64, message = "初始密码长度需在 6~64 位之间")
    private String initialPassword;

    /** 备注 */
    private String remark;
}
