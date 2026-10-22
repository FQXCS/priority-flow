package com.sdecloud.priorityflow.common.controller;

import com.sdecloud.priorityflow.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口：供桌面端在保存服务器地址前探测连通性。
 *
 * @author priority-flow
 */
@Tag(name = "系统", description = "健康检查")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "健康检查", description = "无需认证，返回服务可用状态")
    @GetMapping
    public R<Void> health() {
        return R.ok();
    }
}
