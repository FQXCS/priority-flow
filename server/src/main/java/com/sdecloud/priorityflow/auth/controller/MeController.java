package com.sdecloud.priorityflow.auth.controller;

import com.sdecloud.priorityflow.auth.bean.dto.ChangePasswordDto;
import com.sdecloud.priorityflow.auth.bean.dto.UpdateSettingsDto;
import com.sdecloud.priorityflow.auth.bean.vo.MePermissionsVo;
import com.sdecloud.priorityflow.auth.bean.vo.UserVo;
import com.sdecloud.priorityflow.auth.service.AuthService;
import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户接口。
 *
 * @author priority-flow
 */
@Tag(name = "当前用户", description = "个人信息/权限/密码/偏好设置")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class MeController {

    private final AuthService authService;

    @Operation(summary = "个人信息")
    @GetMapping("/profile")
    public R<UserVo> profile() {
        return R.ok(authService.profile(UserContext.require().getUserId()));
    }

    @Operation(summary = "权限并集一次性下发", description = "projectId 为空时取我的第一个项目")
    @GetMapping("/permissions")
    public R<MePermissionsVo> permissions(@RequestParam(required = false) String projectId) {
        return R.ok(authService.permissions(UserContext.require().getUserId(), projectId));
    }

    @Operation(summary = "修改密码", description = "成功后吊销全部刷新令牌")
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDto dto) {
        authService.changePassword(UserContext.require().getUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "更新个人偏好设置")
    @PutMapping("/settings")
    public R<Void> updateSettings(@RequestBody UpdateSettingsDto dto) {
        authService.updateSettings(UserContext.require().getUserId(), dto);
        return R.ok();
    }
}
