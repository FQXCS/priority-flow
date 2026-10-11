package com.sdecloud.priorityflow.auth.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参。
 *
 * @author priority-flow
 */
@Data
public class LoginDto {

    /** 登录名 */
    @NotBlank(message = "登录名不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
