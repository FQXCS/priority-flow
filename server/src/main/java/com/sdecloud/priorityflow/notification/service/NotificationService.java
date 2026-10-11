package com.sdecloud.priorityflow.notification.service;

import com.sdecloud.priorityflow.notification.persistence.po.NotificationInfoDo;

import java.util.List;

/**
 * 通知服务：站内通知落库 + WS 推送（/user/queue/notify）。
 *
 * @author priority-flow
 */
public interface NotificationService {

    /**
     * 批量创建通知并推送（去重、排除操作人由调用方完成）
     *
     * @param userIds    接收人列表
     * @param notifyType 通知类型
     * @param title      标题
     * @param content    内容
     * @param refType    关联对象类型
     * @param refId      关联对象 ID
     */
    void notifyUsers(List<String> userIds, String notifyType, String title, String content, String refType, String refId);

    /**
     * 用户通知列表（时间倒序分页）
     */
    List<NotificationInfoDo> listMy(String userId, long pageNum, long pageSize);

    /**
     * 未读数
     */
    long unreadCount(String userId);

    /**
     * 标记已读
     */
    void markRead(String userId, String notificationId);

    /**
     * 全部标记已读
     */
    void markAllRead(String userId);
}
