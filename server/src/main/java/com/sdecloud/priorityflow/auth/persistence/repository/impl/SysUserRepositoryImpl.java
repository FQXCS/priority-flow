package com.sdecloud.priorityflow.auth.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.auth.persistence.mapper.SysUserMapper;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import org.springframework.stereotype.Repository;

/**
 * 系统用户仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class SysUserRepositoryImpl extends ServiceImpl<SysUserMapper, SysUserDo> implements SysUserRepository {

    @Override
    public SysUserDo findByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUserDo>().eq(SysUserDo::getUsername, username), false);
    }
}
