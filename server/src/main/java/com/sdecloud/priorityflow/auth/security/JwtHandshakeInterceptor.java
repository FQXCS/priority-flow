package com.sdecloud.priorityflow.auth.security;

import com.sdecloud.priorityflow.common.config.StompAuthChannelInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：SockJS 无法自定义请求头，token 经 URL 参数 ?token= 传入，
 * 校验 JWT 后将用户 ID 写入会话属性，供 STOMP CONNECT 恢复 Principal。
 *
 * @author priority-flow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest http = servletRequest.getServletRequest();
            String token = http.getParameter("token");
            String userId = jwtUtil.verifyUserId(token);
            if (userId == null) {
                log.warn("WebSocket 握手 token 无效，拒绝连接");
                return false;
            }
            attributes.put(StompAuthChannelInterceptor.ATTR_WS_USER_ID, userId);
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
