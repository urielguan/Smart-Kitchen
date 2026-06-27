package com.xykj.sample.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.xykj.common.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 元数据自动填充处理器
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
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
        // 使用 setFieldValByName 强制更新，strictUpdateFill 仅在字段为 null 时填充，
        // 从数据库查出的实体已有值会被跳过，导致更新时间/修改人不刷新
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
        Long userId = UserContext.getUserId();
        if (userId != null) {
            this.setFieldValByName("updatedBy", userId, metaObject);
        }
    }
}
