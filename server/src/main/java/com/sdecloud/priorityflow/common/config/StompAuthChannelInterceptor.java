package com.sdecloud.priorityflow.common.config;

import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP 通道拦截器：CONNECT 时从握手属性恢复 Principal 并写回可变消息头；
 * SUBSCRIBE 时校验用户对目标项目的可见性，无权拒绝订阅。
 *
 * <p>注意：必须经 {@link MessageHeaderAccessor#getAccessor} 取原始可变 accessor，
 * 若用 StompHeaderAccessor.wrap（只读副本）setUser 不会持久化，后续帧将全部无用户。</p>
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** 看板主题前缀 */
    private static final String PROJECT_TOPIC_PREFIX = "/topic/project.";

    /** 个人通知队列前缀 */
    private static final String USER_QUEUE_PREFIX = "/user/queue";

    /** 握手属性中已验证的用户 ID 键 */
    public static final String ATTR_WS_USER_ID = "wsUserId";

    private final ProjectPermissionChecker permissionChecker;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            String userId = sessionAttributes == null ? null : (String) sessionAttributes.get(ATTR_WS_USER_ID);
            if (userId == null) {
                throw new MessagingException("未认证的 WebSocket 连接");
            }
            // 写回可变 accessor：后续帧与 /user 目的地映射都依赖该 Principal
            accessor.setUser(() -> userId);
            return message;
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            String userId = user == null ? null : user.getName();
            if (userId == null) {
                // CONNECT 未携带/未持久化用户时，回退到握手属性中的已验证用户
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                userId = sessionAttributes == null ? null : (String) sessionAttributes.get(ATTR_WS_USER_ID);
            }
            if (userId == null) {
                throw new MessagingException("未认证的订阅");
            }
            if (user == null) {
                // 补挂用户 Principal，供 /user 队列目的地映射使用
                String resolved = userId;
                accessor.setUser(() -> resolved);
            }
            String destination = accessor.getDestination();
            if (destination == null) {
                throw new MessagingException("空订阅目标");
            }
            if (destination.startsWith(PROJECT_TOPIC_PREFIX)) {
                String rest = destination.substring(PROJECT_TOPIC_PREFIX.length());
                String projectId = rest.split("/")[0];
                if (!permissionChecker.isProjectMember(userId, projectId)) {
                    throw new MessagingException("无权订阅该项目：" + projectId);
                }
            } else if (!destination.startsWith(USER_QUEUE_PREFIX)) {
                throw new MessagingException("非法订阅目标：" + destination);
            }
        }
        return message;
    }
}
