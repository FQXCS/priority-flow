package com.sdecloud.priorityflow.notification.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.notification.persistence.po.NotificationInfoDo;

/**
 * 站内通知仓库。
 *
 * @author priority-flow
 */
public interface NotificationInfoRepository extends IService<NotificationInfoDo> {

    /**
     * 用户未读通知数
     */
    long countUnread(String userId);
}
