package com.sdecloud.priorityflow.common.security;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;

/**
 * 当前登录用户上下文（ThreadLocal，请求结束时由拦截器清理）。
 *
 * @author priority-flow
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前用户
     */
    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    /**
     * 读取当前用户（可能为 null，供系统任务/事件监听线程使用）
     */
    public static UserPrincipal get() {
        return HOLDER.get();
    }

    /**
     * 读取当前用户，未登录直接抛 1001
     */
    public static UserPrincipal require() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return principal;
    }

    /**
     * 清理线程上下文
     */
    public static void clear() {
        HOLDER.remove();
    }
}
