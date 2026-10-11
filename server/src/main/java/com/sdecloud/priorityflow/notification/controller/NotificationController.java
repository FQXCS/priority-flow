package com.sdecloud.priorityflow.notification.controller;

import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.notification.persistence.po.NotificationInfoDo;
import com.sdecloud.priorityflow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知接口。
 *
 * @author priority-flow
 */
@Tag(name = "通知", description = "站内通知（桌面通知经 WS 推送由客户端触发）")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知", description = "时间倒序分页")
    @GetMapping
    public R<List<NotificationInfoDo>> list(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(notificationService.listMy(UserContext.require().getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(notificationService.unreadCount(UserContext.require().getUserId()));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{notificationId}/read")
    public R<Void> markRead(@PathVariable String notificationId) {
        notificationService.markRead(UserContext.require().getUserId(), notificationId);
        return R.ok();
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead(UserContext.require().getUserId());
        return R.ok();
    }
}
