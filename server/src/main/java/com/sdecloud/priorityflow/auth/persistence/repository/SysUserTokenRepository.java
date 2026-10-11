package com.sdecloud.priorityflow.auth.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserTokenDo;

/**
 * 用户刷新令牌仓库。
 *
 * @author priority-flow
 */
public interface SysUserTokenRepository extends IService<SysUserTokenDo> {

    /**
     * 按令牌哈希查询有效令牌（未吊销）
     *
     * @param tokenHash SHA-256 哈希
     * @return 令牌记录，不存在或已吊销返回 null
     */
    SysUserTokenDo findValidByHash(String tokenHash);

    /**
     * 吊销该用户全部刷新令牌（登出/禁用场景）
     */
    void revokeAllByUserId(String userId);
}
