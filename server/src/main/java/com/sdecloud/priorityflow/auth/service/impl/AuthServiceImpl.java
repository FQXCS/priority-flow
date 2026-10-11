package com.sdecloud.priorityflow.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.alibaba.fastjson2.JSON;
import com.sdecloud.priorityflow.auth.bean.dto.ChangePasswordDto;
import com.sdecloud.priorityflow.auth.bean.dto.LoginDto;
import com.sdecloud.priorityflow.auth.bean.dto.RefreshDto;
import com.sdecloud.priorityflow.auth.bean.dto.UpdateSettingsDto;
import com.sdecloud.priorityflow.auth.bean.vo.LoginVo;
import com.sdecloud.priorityflow.auth.bean.vo.MePermissionsVo;
import com.sdecloud.priorityflow.auth.bean.vo.ProjectSimpleVo;
import com.sdecloud.priorityflow.auth.bean.vo.UserVo;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserTokenDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserTokenRepository;
import com.sdecloud.priorityflow.auth.security.JwtUtil;
import com.sdecloud.priorityflow.auth.service.AuthService;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.service.ProjectQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证与当前用户服务实现。
 *
 * @author priority-flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 状态：启用 */
    private static final String STATUS_ENABLED = "0";

    private final SysUserRepository sysUserRepository;

    private final SysUserTokenRepository sysUserTokenRepository;

    private final JwtUtil jwtUtil;

    private final ProjectQueryService projectService;

    private final ProjectPermissionChecker projectPermissionChecker;

    /** 刷新令牌有效期（天） */
    @Value("${pf.jwt.refresh-token-days}")
    private long refreshTokenDays;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVo login(LoginDto dto) {
        SysUserDo user = sysUserRepository.findByUsername(dto.getUsername());
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (!STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        log.info("用户登录成功：{}", user.getUsername());
        return issueTokens(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVo refresh(RefreshDto dto) {
        String tokenHash = sha256(dto.getRefreshToken());
        SysUserTokenDo tokenDo = sysUserTokenRepository.findValidByHash(tokenHash);
        if (tokenDo == null || tokenDo.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.REFRESH_INVALID);
        }
        // 轮换：旧令牌立即失效
        tokenDo.setRevoked("1");
        sysUserTokenRepository.updateById(tokenDo);
        SysUserDo user = sysUserRepository.getById(tokenDo.getUserId());
        if (user == null || !STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.REFRESH_INVALID);
        }
        return issueTokens(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            SysUserTokenDo tokenDo = sysUserTokenRepository.findValidByHash(sha256(refreshToken));
            if (tokenDo != null) {
                tokenDo.setRevoked("1");
                sysUserTokenRepository.updateById(tokenDo);
            }
        }
    }

    @Override
    public UserVo profile(String userId) {
        SysUserDo user = sysUserRepository.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        UserVo vo = new UserVo();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public MePermissionsVo permissions(String userId, String projectId) {
        MePermissionsVo vo = new MePermissionsVo();
        SysUserDo user = sysUserRepository.getById(userId);
        vo.setAdmin(user != null && "1".equals(user.getIsAdmin()));

        List<ProjectInfoDo> myProjects = projectService.listMemberProjects(userId);
        vo.setProjectList(myProjects.stream().map(p -> {
            ProjectSimpleVo simple = new ProjectSimpleVo();
            simple.setProjectId(p.getId());
            simple.setProjectName(p.getProjectName());
            simple.setProjectKey(p.getProjectKey());
            return simple;
        }).toList());

        String targetProjectId = projectId;
        if (targetProjectId == null || targetProjectId.isBlank()) {
            targetProjectId = myProjects.isEmpty() ? null : myProjects.get(0).getId();
        }
        // 权限并集一次性下发；非项目成员返回空集
        vo.setPermissions(targetProjectId == null ? List.of()
                : projectPermissionChecker.unionPermissions(userId, targetProjectId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String userId, ChangePasswordDto dto) {
        SysUserDo user = sysUserRepository.getById(userId);
        if (user == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED, "原密码不正确");
        }
        user.setPasswordHash(BCrypt.hashpw(dto.getNewPassword()));
        sysUserRepository.updateById(user);
        // 密码变更后吊销全部刷新令牌，强制重新登录
        sysUserTokenRepository.revokeAllByUserId(userId);
        log.info("用户修改密码：{}", user.getUsername());
    }

    @Override
    public void updateSettings(String userId, UpdateSettingsDto dto) {
        SysUserDo user = sysUserRepository.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        user.setSettings(dto.getSettings() == null ? null : JSON.toJSONString(dto.getSettings()));
        sysUserRepository.updateById(user);
    }

    /**
     * 签发访问令牌 + 刷新令牌（存库哈希）
     */
    private LoginVo issueTokens(SysUserDo user) {
        String refreshToken = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        SysUserTokenDo tokenDo = new SysUserTokenDo();
        tokenDo.setUserId(user.getId());
        tokenDo.setTokenHash(sha256(refreshToken));
        tokenDo.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        tokenDo.setRevoked("0");
        sysUserTokenRepository.save(tokenDo);

        LoginVo vo = new LoginVo();
        vo.setAccessToken(jwtUtil.createAccessToken(user.getId(), user.getUsername()));
        vo.setRefreshToken(refreshToken);
        vo.setExpiresInSeconds(jwtUtil.getAccessTokenSeconds());
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        vo.setUser(userVo);
        return vo;
    }

    private String sha256(String token) {
        return SecureUtil.sha256(token);
    }
}
