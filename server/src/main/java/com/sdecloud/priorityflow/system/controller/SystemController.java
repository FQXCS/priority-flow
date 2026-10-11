package com.sdecloud.priorityflow.system.controller;

import com.sdecloud.priorityflow.common.api.PageResult;
import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.project.bean.vo.ProjectVo;
import com.sdecloud.priorityflow.system.bean.dto.ResetPasswordDto;
import com.sdecloud.priorityflow.system.bean.dto.UserCreateDto;
import com.sdecloud.priorityflow.system.bean.dto.UserUpdateDto;
import com.sdecloud.priorityflow.system.bean.vo.SystemUserVo;
import com.sdecloud.priorityflow.system.service.SystemAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统管理接口（管理员专用，is_admin 用户仅放行本组接口）。
 *
 * @author priority-flow
 */
@Tag(name = "系统管理", description = "用户/项目/配置（管理员专用）")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system")
public class SystemController {

    private final SystemAdminService systemAdminService;

    // ---------------- 用户管理 ----------------

    @Operation(summary = "用户分页列表", description = "keyword 匹配登录名/显示名")
    @GetMapping("/users")
    public R<PageResult<SystemUserVo>> listUsers(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        return R.ok(systemAdminService.listUsers(keyword, pageNum, pageSize));
    }

    @Operation(summary = "创建用户", description = "管理员创建用户并初始化密码")
    @PostMapping("/users")
    public R<SystemUserVo> createUser(@Valid @RequestBody UserCreateDto dto) {
        requireAdmin();
        return R.ok(systemAdminService.createUser(dto));
    }

    @Operation(summary = "更新用户", description = "显示名/邮箱/状态/备注")
    @PutMapping("/users/{userId}")
    public R<SystemUserVo> updateUser(@PathVariable String userId, @Valid @RequestBody UserUpdateDto dto) {
        requireAdmin();
        return R.ok(systemAdminService.updateUser(userId, dto));
    }

    @Operation(summary = "重置密码")
    @PutMapping("/users/{userId}/reset-password")
    public R<Void> resetPassword(@PathVariable String userId, @Valid @RequestBody ResetPasswordDto dto) {
        requireAdmin();
        systemAdminService.resetPassword(userId, dto);
        return R.ok();
    }

    // ---------------- 项目管理 ----------------

    @Operation(summary = "全部项目", description = "含成员数")
    @GetMapping("/projects")
    public R<List<ProjectVo>> listProjects() {
        requireAdmin();
        return R.ok(systemAdminService.listProjects());
    }

    @Operation(summary = "系统配置概览")
    @GetMapping("/configs")
    public R<Map<String, Object>> configs() {
        requireAdmin();
        return R.ok(systemAdminService.configs());
    }

    /**
     * 管理员校验（is_admin 用户仅放行 /system/**）
     */
    private void requireAdmin() {
        if (!UserContext.require().isAdmin()) {
            throw new BusinessException(ResultCode.NEED_ADMIN);
        }
    }
}
