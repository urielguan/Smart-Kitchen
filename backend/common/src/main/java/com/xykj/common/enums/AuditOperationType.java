package com.xykj.common.enums;

import lombok.Getter;

/**
 * 审计日志操作类型
 */
@Getter
public enum AuditOperationType {

    CREATE("create", "新增"),
    UPDATE("update", "编辑"),
    DELETE("delete", "删除"),
    IMPORT("import", "导入"),
    EXPORT("export", "导出"),
    STATUS_CHANGE("status_change", "状态变更"),
    DISPATCH("dispatch", "派单"),
    REPLY("reply", "回复"),
    PROCESS("process", "处理工单"),
    PROFILE_UPDATE("profile_update", "修改个人信息"),
    PASSWORD_CHANGE("password_change", "修改密码"),

    VIEW("view", "查看"),
    DOWNLOAD("download", "下载"),
    CONTROL("control", "控制");

    private final String code;
    private final String name;

    AuditOperationType(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
