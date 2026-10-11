package com.sdecloud.priorityflow.auth.bean.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录结果 VO：访问令牌（JWT 2h）+ 刷新令牌（7d，服务端存库可吊销）。
 *
 * @author priority-flow
 */
@Data
public class LoginVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** 访问令牌有效期（秒） */
    private Long expiresInSeconds;

    /** 用户信息 */
    private UserVo user;
}
