package com.xykj.auth.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.xykj.common.context.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        Long userId = UserContext.getUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
            this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        }

        if (metaObject.hasSetter("orgId") && this.getFieldValByName("orgId", metaObject) == null) {
            Long orgId = UserContext.getOrgId();
            this.setFieldValByName("orgId", orgId != null ? orgId : 0L, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
        Long userId = UserContext.getUserId();
        if (userId != null) {
            this.setFieldValByName("updatedBy", userId, metaObject);
        }
    }
}
