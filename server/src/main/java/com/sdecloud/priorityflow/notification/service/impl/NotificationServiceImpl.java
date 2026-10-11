package com.sdecloud.priorityflow.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.notification.persistence.po.NotificationInfoDo;
import com.sdecloud.priorityflow.notification.persistence.repository.NotificationInfoRepository;
import com.sdecloud.priorityflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知服务实现：落库 + WS 推送个人队列。
 *
 * @author priority-flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 个人通知 WS 目标 */
    private static final String NOTIFY_USER_DEST = "/queue/notify";

    private final NotificationInfoRepository notificationInfoRepository;

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void notifyUsers(List<String> userIds, String notifyType, String title, String content,
                            String refType, String refId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds.stream().distinct().toList()) {
            NotificationInfoDo notification = new NotificationInfoDo();
            notification.setUserId(userId);
            notification.setNotifyType(notifyType);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setRefType(refType);
            notification.setRefId(refId);
            notification.setIsRead("0");
            try {
                notificationInfoRepository.save(notification);
            } catch (Exception e) {
                // 通知失败不影响主流程
                log.error("站内通知落库失败：userId={}, type={}", userId, notifyType, e);
                continue;
            }
            // WS 推送：/user/queue/notify；channel=DESKTOP 的桌面通知由客户端收到后调 Electron API
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", notification.getId());
            payload.put("notifyType", notifyType);
            payload.put("title", title);
            payload.put("content", content);
            payload.put("refType", refType);
            payload.put("refId", refId);
            payload.put("channel", "DESKTOP");
            try {
                messagingTemplate.convertAndSendToUser(userId, NOTIFY_USER_DEST, payload);
            } catch (Exception e) {
                log.warn("WS 通知推送失败（用户可能离线）：userId={}", userId);
            }
        }
    }

    @Override
    public List<NotificationInfoDo> listMy(String userId, long pageNum, long pageSize) {
        return notificationInfoRepository.page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<NotificationInfoDo>()
                        .eq(NotificationInfoDo::getUserId, userId)
                        .orderByDesc(NotificationInfoDo::getCreateTime)).getRecords();
    }

    @Override
    public long unreadCount(String userId) {
        return notificationInfoRepository.countUnread(userId);
    }

    @Override
    public void markRead(String userId, String notificationId) {
        NotificationInfoDo notification = notificationInfoRepository.getById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            return;
        }
        notification.setIsRead("1");
        notification.setReadTime(LocalDateTime.now());
        notificationInfoRepository.updateById(notification);
    }

    @Override
    public void markAllRead(String userId) {
        notificationInfoRepository.update(new LambdaUpdateWrapper<NotificationInfoDo>()
                .eq(NotificationInfoDo::getUserId, userId)
                .eq(NotificationInfoDo::getIsRead, "0")
                .set(NotificationInfoDo::getIsRead, "1")
                .set(NotificationInfoDo::getReadTime, LocalDateTime.now()));
    }
}
