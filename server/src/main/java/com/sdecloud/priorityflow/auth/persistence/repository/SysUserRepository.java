package com.sdecloud.priorityflow.auth.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;

/**
 * 系统用户仓库。
 *
 * @author priority-flow
 */
public interface SysUserRepository extends IService<SysUserDo> {

    /**
     * 按登录名查询用户
     *
     * @param username 登录名
     * @return 用户，不存在返回 null
     */
    SysUserDo findByUsername(String username);
}
