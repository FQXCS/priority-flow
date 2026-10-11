package com.sdecloud.priorityflow.auth.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.auth.persistence.mapper.SysUserTokenMapper;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserTokenDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserTokenRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户刷新令牌仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class SysUserTokenRepositoryImpl extends ServiceImpl<SysUserTokenMapper, SysUserTokenDo>
        implements SysUserTokenRepository {

    @Override
    public SysUserTokenDo findValidByHash(String tokenHash) {
        return this.getOne(new LambdaQueryWrapper<SysUserTokenDo>()
                .eq(SysUserTokenDo::getTokenHash, tokenHash)
                .eq(SysUserTokenDo::getRevoked, "0"), false);
    }

    @Override
    public void revokeAllByUserId(String userId) {
        this.update(new LambdaUpdateWrapper<SysUserTokenDo>()
                .eq(SysUserTokenDo::getUserId, userId)
                .eq(SysUserTokenDo::getRevoked, "0")
                .set(SysUserTokenDo::getRevoked, "1"));
    }
}
