package com.sdecloud.priorityflow.common.permission;

import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.common.security.UserPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目权限切面测试：参数解析（projectId/taskId）、多角色并集校验、无权限拒绝。
 *
 * @author priority-flow
 */
@ExtendWith(MockitoExtension.class)
class ProjectPermissionAspectTest {

    @Mock
    private ProjectPermissionChecker permissionChecker;

    @Mock
    private TaskProjectResolver taskProjectResolver;

    @InjectMocks
    private ProjectPermissionAspect aspect;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private ProceedingJoinPoint joinPoint(String[] paramNames, Object[] args) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(paramNames);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    @Test
    void shouldResolveProjectIdDirectlyAndPass() throws Throwable {
        UserContext.set(new UserPrincipal("u1", "admin", "管理员", false, true));
        when(permissionChecker.hasPermission("u1", "p1", "pf:task:distribute")).thenReturn(true);
        ProceedingJoinPoint joinPoint = joinPoint(new String[]{"projectId", "dto"},
                new Object[]{"p1", new Object()});

        aspect.around(joinPoint, annotation("pf:task:distribute"));

        verify(joinPoint).proceed();
    }

    @Test
    void shouldResolveProjectIdByTaskId() throws Throwable {
        UserContext.set(new UserPrincipal("u1", "admin", "管理员", false, true));
        when(taskProjectResolver.resolveProjectId("t1")).thenReturn("p1");
        when(permissionChecker.hasPermission("u1", "p1", "pf:task:start")).thenReturn(true);
        ProceedingJoinPoint joinPoint = joinPoint(new String[]{"taskId"}, new Object[]{"t1"});

        aspect.around(joinPoint, annotation("pf:task:start"));

        verify(joinPoint).proceed();
    }

    @Test
    void shouldRejectWithoutPermission() throws Throwable {
        UserContext.set(new UserPrincipal("u1", "user", "用户", false, true));
        when(permissionChecker.hasPermission("u1", "p1", "pf:task:distribute")).thenReturn(false);
        ProceedingJoinPoint joinPoint = joinPoint(new String[]{"projectId"}, new Object[]{"p1"});

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.around(joinPoint, annotation("pf:task:distribute")));
        assertEquals(ResultCode.NO_PERMISSION, ex.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldRejectWhenProjectUnresolvable() {
        UserContext.set(new UserPrincipal("u1", "user", "用户", false, true));
        ProceedingJoinPoint joinPoint = joinPoint(new String[]{"keyword"}, new Object[]{"abc"});

        // 项目无法定位时切面先抛异常，不读取注解值（raw mock，避免多余打桩）
        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.around(joinPoint, org.mockito.Mockito.mock(RequireProjectPermission.class)));
        assertEquals(ResultCode.BAD_PARAM, ex.getCode());
    }

    private RequireProjectPermission annotation(String value) {
        RequireProjectPermission annotation = mock(RequireProjectPermission.class);
        when(annotation.value()).thenReturn(value);
        return annotation;
    }
}
