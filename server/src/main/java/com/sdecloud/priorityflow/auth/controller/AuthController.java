package com.sdecloud.priorityflow.auth.controller;

import com.sdecloud.priorityflow.auth.bean.dto.LoginDto;
import com.sdecloud.priorityflow.auth.bean.dto.RefreshDto;
import com.sdecloud.priorityflow.auth.bean.vo.LoginVo;
import com.sdecloud.priorityflow.auth.service.AuthService;
import com.sdecloud.priorityflow.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * @author priority-flow
 */
@Tag(name = "认证", description = "登录/刷新/登出")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "校验账号密码，签发 access(JWT 2h) + refresh token(7d)")
    @PostMapping("/login")
    public R<LoginVo> login(@Valid @RequestBody LoginDto dto) {
        return R.ok(authService.login(dto));
    }

    @Operation(summary = "刷新令牌", description = "校验并轮换 refresh token，签发新的 access")
    @PostMapping("/refresh")
    public R<LoginVo> refresh(@Valid @RequestBody RefreshDto dto) {
        return R.ok(authService.refresh(dto));
    }

    @Operation(summary = "登出", description = "吊销刷新令牌；body 可省略，省略时不做处理")
    @PostMapping("/logout")
    public R<Void> logout(@RequestBody(required = false) RefreshDto dto) {
        authService.logout(dto == null ? null : dto.getRefreshToken());
        return R.ok();
    }
}
