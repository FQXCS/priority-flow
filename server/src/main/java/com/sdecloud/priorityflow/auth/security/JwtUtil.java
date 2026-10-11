package com.sdecloud.priorityflow.auth.security;

import cn.hutool.jwt.JWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具（HS256，hutool-jwt）：访问令牌默认 2 小时，载荷含 userId/username。
 *
 * @author priority-flow
 */
@Component
public class JwtUtil {

    /** HS256 签名密钥 */
    private final byte[] secret;

    /** 访问令牌有效期（毫秒） */
    private final long accessTokenMillis;

    public JwtUtil(@Value("${pf.jwt.secret}") String secret,
                   @Value("${pf.jwt.access-token-hours}") long accessTokenHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenMillis = accessTokenHours * 3600_000L;
    }

    /**
     * 签发访问令牌
     */
    public String createAccessToken(String userId, String username) {
        return JWT.create()
                .setKey(secret)
                .setPayload("userId", userId)
                .setPayload("username", username)
                .setExpiresAt(new Date(System.currentTimeMillis() + accessTokenMillis))
                .sign();
    }

    /**
     * 校验令牌（签名 + 有效期），有效返回 userId，否则返回 null
     */
    public String verifyUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            JWT jwt = JWT.of(token).setKey(secret);
            if (!jwt.verify() || !jwt.validate(0)) {
                return null;
            }
            Object userId = jwt.getPayload("userId");
            return userId == null ? null : userId.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 访问令牌有效期（秒），登录时下发给客户端
     */
    public long getAccessTokenSeconds() {
        return accessTokenMillis / 1000;
    }
}
