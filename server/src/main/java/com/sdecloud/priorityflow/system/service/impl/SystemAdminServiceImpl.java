package com.sdecloud.priorityflow.system.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserTokenRepository;
import com.sdecloud.priorityflow.common.api.PageResult;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.project.bean.vo.ProjectVo;
import com.sdecloud.priorityflow.project.service.ProjectService;
import com.sdecloud.priorityflow.system.bean.dto.ResetPasswordDto;
import com.sdecloud.priorityflow.system.bean.dto.UserCreateDto;
import com.sdecloud.priorityflow.system.bean.dto.UserUpdateDto;
import com.sdecloud.priorityflow.system.bean.vo.SystemUserVo;
import com.sdecloud.priorityflow.system.service.SystemAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统管理服务实现。管理员不自动拥有项目业务权限（仅放行系统管理）。
 *
 * @author priority-flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAdminServiceImpl implements SystemAdminService {

    /** 状态：启用 */
    private static final String STATUS_ENABLED = "0";

    private final SysUserRepository sysUserRepository;

    private final SysUserTokenRepository sysUserTokenRepository;

    private final ProjectService projectService;

    @Override
    public PageResult<SystemUserVo> listUsers(String keyword, long pageNum, long pageSize) {
        LambdaQueryWrapper<SysUserDo> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysUserDo::getUsername, keyword)
                    .or().like(SysUserDo::getDisplayName, keyword));
        }
        wrapper.orderByAsc(SysUserDo::getCreateTime);
        Page<SysUserDo> page = sysUserRepository.page(new Page<>(pageNum, pageSize), wrapper);
        Page<SystemUserVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(user -> {
            SystemUserVo vo = new SystemUserVo();
            BeanUtils.copyProperties(user, vo);
            return vo;
        }).toList());
        return PageResult.of(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemUserVo createUser(UserCreateDto dto) {
        if (sysUserRepository.findByUsername(dto.getUsername()) != null) {
            throw new BusinessException(ResultCode.DUPLICATE, "登录名已存在：" + dto.getUsername());
        }
        SysUserDo user = new SysUserDo();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(BCrypt.hashpw(dto.getInitialPassword()));
        user.setDisplayName(dto.getDisplayName());
        user.setEmail(dto.getEmail());
        user.setStatus(STATUS_ENABLED);
        user.setIsAdmin("0");
        user.setRemark(dto.getRemark());
        sysUserRepository.save(user);
        log.info("管理员创建用户：{}", user.getUsername());
        SystemUserVo vo = new SystemUserVo();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemUserVo updateUser(String userId, UserUpdateDto dto) {
        SysUserDo user = requireUser(userId);
        if (dto.getDisplayName() != null) {
            user.setDisplayName(dto.getDisplayName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
            // 禁用用户即时下线：吊销全部刷新令牌
            if ("1".equals(dto.getStatus())) {
                sysUserTokenRepository.revokeAllByUserId(userId);
            }
        }
        if (dto.getRemark() != null) {
            user.setRemark(dto.getRemark());
        }
        sysUserRepository.updateById(user);
        SystemUserVo vo = new SystemUserVo();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String userId, ResetPasswordDto dto) {
        SysUserDo user = requireUser(userId);
        user.setPasswordHash(BCrypt.hashpw(dto.getNewPassword()));
        sysUserRepository.updateById(user);
        sysUserTokenRepository.revokeAllByUserId(userId);
        log.info("管理员重置用户密码：{}", user.getUsername());
    }

    @Override
    public List<ProjectVo> listProjects() {
        return projectService.listAllProjects();
    }

    @Override
    public Map<String, Object> configs() {
        Map<String, Object> configs = new LinkedHashMap<>();
        configs.put("serverTime", LocalDateTime.now().toString());
        configs.put("minioBucketConfigured", true);
        return configs;
    }

    private SysUserDo requireUser(String userId) {
        SysUserDo user = sysUserRepository.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "用户不存在");
        }
        return user;
    }
}
