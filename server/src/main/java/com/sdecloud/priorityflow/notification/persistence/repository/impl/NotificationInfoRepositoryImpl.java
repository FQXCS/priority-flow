package com.sdecloud.priorityflow.notification.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.notification.persistence.mapper.NotificationInfoMapper;
import com.sdecloud.priorityflow.notification.persistence.po.NotificationInfoDo;
import com.sdecloud.priorityflow.notification.persistence.repository.NotificationInfoRepository;
import org.springframework.stereotype.Repository;

/**
 * 站内通知仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class NotificationInfoRepositoryImpl extends ServiceImpl<NotificationInfoMapper, NotificationInfoDo>
        implements NotificationInfoRepository {

    @Override
    public long countUnread(String userId) {
        return this.count(new LambdaQueryWrapper<NotificationInfoDo>()
                .eq(NotificationInfoDo::getUserId, userId)
                .eq(NotificationInfoDo::getIsRead, "0"));
    }
}
