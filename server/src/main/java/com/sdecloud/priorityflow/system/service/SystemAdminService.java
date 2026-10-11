package com.sdecloud.priorityflow.system.service;

import com.sdecloud.priorityflow.common.api.PageResult;
import com.sdecloud.priorityflow.project.bean.vo.ProjectVo;
import com.sdecloud.priorityflow.system.bean.dto.ResetPasswordDto;
import com.sdecloud.priorityflow.system.bean.dto.UserCreateDto;
import com.sdecloud.priorityflow.system.bean.dto.UserUpdateDto;
import com.sdecloud.priorityflow.system.bean.vo.SystemUserVo;

import java.util.List;
import java.util.Map;

/**
 * 系统管理服务（管理员专用：用户管理、项目管理、配置查看）。
 *
 * @author priority-flow
 */
public interface SystemAdminService {

    /**
     * 用户分页列表（keyword 匹配登录名/显示名）
     */
    PageResult<SystemUserVo> listUsers(String keyword, long pageNum, long pageSize);

    /**
     * 创建用户并初始化密码
     */
    SystemUserVo createUser(UserCreateDto dto);

    /**
     * 更新用户（显示名/邮箱/状态/备注）
     */
    SystemUserVo updateUser(String userId, UserUpdateDto dto);

    /**
     * 重置密码（吊销该用户全部刷新令牌）
     */
    void resetPassword(String userId, ResetPasswordDto dto);

    /**
     * 全部项目（含成员数）
     */
    List<ProjectVo> listProjects();

    /**
     * 系统配置概览
     */
    Map<String, Object> configs();
}
