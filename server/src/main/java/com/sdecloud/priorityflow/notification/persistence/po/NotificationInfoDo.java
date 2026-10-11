package com.sdecloud.priorityflow.notification.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内通知实体（桌面通知由客户端收 WS 推送后调 Electron API）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_info")
public class NotificationInfoDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 接收人 ID */
    private String userId;

    /** 通知类型 */
    private String notifyType;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 关联对象类型（TASK 等） */
    private String refType;

    /** 关联对象 ID */
    private String refId;

    /** 是否已读（0 未读 / 1 已读） */
    private String isRead;

    /** 已读时间 */
    private LocalDateTime readTime;
}
