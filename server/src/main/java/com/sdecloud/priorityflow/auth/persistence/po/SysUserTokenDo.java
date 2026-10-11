package com.sdecloud.priorityflow.auth.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户刷新令牌实体（服务端存库，可吊销）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_token")
public class SysUserTokenDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 刷新令牌 SHA-256 哈希 */
    private String tokenHash;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 是否已吊销（0 否 / 1 是） */
    private String revoked;
}
