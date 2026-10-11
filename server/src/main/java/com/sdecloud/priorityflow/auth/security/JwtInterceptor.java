package com.sdecloud.priorityflow.auth.security;

import cn.hutool.crypto.digest.BCrypt;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.common.security.UserPrincipal;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * JWT 认证拦截器：解析 Authorization: Bearer xxx，装载用户上下文；
 * 每次请求回查用户表（禁用即时生效，<100 人规模开销可忽略）。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    private final SysUserRepository sysUserRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()) : null;
        String userId = jwtUtil.verifyUserId(token);
        if (userId == null) {
            writeUnauthorized(response);
            return false;
        }
        SysUserDo user = sysUserRepository.getById(userId);
        if (user == null) {
            writeUnauthorized(response);
            return false;
        }
        if ("1".equals(user.getStatus())) {
            writeUnauthorized(response);
            return false;
        }
        UserContext.set(new UserPrincipal(user.getId(), user.getUsername(), user.getDisplayName(),
                "1".equals(user.getIsAdmin()), true));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 未认证响应：HTTP 200 + 统一 R 结构（错误码 1001），前端拦截器按 code 跳登录
     */
    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.toJSONString(R.failed(ResultCode.UNAUTHORIZED)));
    }
}
