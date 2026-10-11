package com.sdecloud.priorityflow.auth.persistence.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUserDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录名（唯一） */
    private String username;

    /** BCrypt 密码哈希（禁止对外序列化） */
    @JSONField(serialize = false)
    private String passwordHash;

    /** 显示名 */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 头像地址 */
    private String avatarUrl;

    /** 个人偏好设置（JSON：主题/密度等） */
    private String settings;

    /** 状态（0 启用 / 1 禁用） */
    private String status;

    /** 是否系统管理员（0 否 / 1 是） */
    private String isAdmin;
}
