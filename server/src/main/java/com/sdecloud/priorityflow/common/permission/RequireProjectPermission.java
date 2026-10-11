package com.sdecloud.priorityflow.common.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 项目级权限校验注解（风格对齐 hasPermi）。
 * <p>切面从方法参数解析 projectId 或 taskId，查出用户在该项目的多角色权限并集后校验，
 * 无权限抛业务异常（2xxx 权限码）。</p>
 *
 * @author priority-flow
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireProjectPermission {

    /**
     * 权限点编码，见 {@link PermissionConstants}
     */
    String value();
}
