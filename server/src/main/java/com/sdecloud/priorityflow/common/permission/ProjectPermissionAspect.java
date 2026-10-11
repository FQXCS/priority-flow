package com.sdecloud.priorityflow.common.permission;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 项目权限切面：拦截 {@link RequireProjectPermission} 注解方法，
 * 从方法参数解析 projectId/taskId 后做权限并集校验，无权限抛 2xxx 业务异常。
 *
 * @author priority-flow
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ProjectPermissionAspect {

    private final ProjectPermissionChecker permissionChecker;

    private final TaskProjectResolver taskProjectResolver;

    /**
     * 环绕校验：定位项目 → 权限并集校验 → 放行
     */
    @Around("@annotation(requireProjectPermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequireProjectPermission requireProjectPermission) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        String projectId = null;
        String taskId = null;
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            if (args[i] instanceof String value) {
                switch (paramNames[i]) {
                    case "projectId" -> projectId = value;
                    case "taskId" -> taskId = value;
                    default -> {
                        // 其余参数不参与项目定位
                    }
                }
            }
        }
        // 方法参数只有 taskId 时，经任务反查所属项目
        if (projectId == null && taskId != null) {
            projectId = taskProjectResolver.resolveProjectId(taskId);
        }
        if (projectId == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "无法定位项目上下文");
        }

        UserPrincipal user = UserContext.require();
        if (!permissionChecker.hasPermission(user.getUserId(), projectId, requireProjectPermission.value())) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
        return joinPoint.proceed();
    }
}
