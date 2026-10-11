package com.sdecloud.priorityflow.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.common.security.UserPrincipal;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充：create_by/create_time/update_by/update_time。
 *
 * @author priority-flow
 */
@Component
public class AuditMetaHandler implements MetaObjectHandler {

    private static final String FIELD_CREATE_BY = "createBy";
    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_UPDATE_BY = "updateBy";
    private static final String FIELD_UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        String operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, FIELD_CREATE_BY, String.class, operator);
        this.strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_UPDATE_BY, String.class, operator);
        this.strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, FIELD_UPDATE_BY, String.class, currentOperator());
        this.strictUpdateFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 取当前操作人；系统线程（定时任务/事件监听）中可能无登录上下文
     */
    private String currentOperator() {
        UserPrincipal principal = UserContext.get();
        return principal == null ? null : principal.getUserId();
    }
}
