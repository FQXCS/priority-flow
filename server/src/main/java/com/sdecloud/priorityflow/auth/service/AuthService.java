package com.sdecloud.priorityflow.auth.service;

import com.sdecloud.priorityflow.auth.bean.dto.ChangePasswordDto;
import com.sdecloud.priorityflow.auth.bean.dto.LoginDto;
import com.sdecloud.priorityflow.auth.bean.dto.RefreshDto;
import com.sdecloud.priorityflow.auth.bean.dto.UpdateSettingsDto;
import com.sdecloud.priorityflow.auth.bean.vo.LoginVo;
import com.sdecloud.priorityflow.auth.bean.vo.MePermissionsVo;
import com.sdecloud.priorityflow.auth.bean.vo.UserVo;

/**
 * 认证与当前用户服务。
 *
 * @author priority-flow
 */
public interface AuthService {

    /**
     * 登录：校验账号密码，签发 access（JWT 2h）+ refresh token（7d，服务端存库可吊销）
     */
    LoginVo login(LoginDto dto);

    /**
     * 刷新令牌：校验并轮换 refresh token，签发新的 access
     */
    LoginVo refresh(RefreshDto dto);

    /**
     * 登出：吊销刷新令牌（为空则吊销该用户全部令牌）
     */
    void logout(String refreshToken);

    /**
     * 当前用户信息
     */
    UserVo profile(String userId);

    /**
     * 当前用户权限：项目列表 + 指定项目权限并集（projectId 为空时取第一个项目）
     */
    MePermissionsVo permissions(String userId, String projectId);

    /**
     * 修改密码（成功后吊销全部刷新令牌）
     */
    void changePassword(String userId, ChangePasswordDto dto);

    /**
     * 更新个人偏好设置（整体覆盖）
     */
    void updateSettings(String userId, UpdateSettingsDto dto);
}
