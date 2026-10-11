package com.sdecloud.priorityflow.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户信息（由 JWT 拦截器装载，存放于 ThreadLocal）。
 *
 * @author priority-flow
 */
@Data
@AllArgsConstructor
public class UserPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 登录名 */
    private String username;

    /** 显示名 */
    private String displayName;

    /** 是否系统管理员 */
    private boolean admin;

    /** 账号是否启用 */
    private boolean enabled;
}
