package com.xykj.common.enums;

import lombok.Getter;

/**
 * 审计日志模块
 */
@Getter
public enum AuditModule {

    WMS_MATERIAL("wms_material", "物料管理"),
    WMS_WAREHOUSE("wms_warehouse", "仓库管理"),
    WMS_LOCATION("wms_location", "仓位管理"),
    WMS_INBOUND_ORDER("wms_inbound_order", "入库管理"),
    WMS_OUTBOUND_ORDER("wms_outbound_order", "出库管理"),
    WMS_INVENTORY("wms_inventory", "库存管理"),
    WMS_STOCKTAKE_ORDER("wms_stocktake_order", "盘点管理"),

    SCM_SUPPLIER("scm_supplier", "供应商管理"),
    SCM_PURCHASE_PLAN("scm_purchase_plan", "采购计划"),
    SCM_PURCHASE_DEMAND_FORECAST("scm_purchase_demand_forecast", "采购需求预测"),
    SCM_PURCHASE_ORDER("scm_purchase_order", "采购订单"),
    SCM_RECEIPT_RECORD("scm_receipt_record", "收货记录"),

    RECIPE("recipe", "菜谱管理"),
    RECIPE_CATEGORY("recipe_category", "菜谱分类"),
    RECIPE_PLAN("recipe_plan", "菜谱计划"),
    RECIPE_PLAN_ADJUSTMENT("recipe_plan_adjustment", "菜谱计划调整"),
    RECIPE_MATERIAL_NOTIFICATION("recipe_material_notification", "食材通知"),

    COOK_TASK("cook_task", "烹饪任务"),
    COOK_RECORD("cook_record", "烹饪记录"),
    COOK_TEMPERATURE_RECORD("cook_temperature_record", "烹饪温度记录"),

    SAMPLE_RECORD("sample_record", "留样管理"),

    HEALTH_CERTIFICATE("health_certificate", "健康证管理"),
    HEALTH_CHECK("health_check", "晨检管理"),
    HEALTH_FACE_FEATURE("health_face_feature", "人脸特征"),

    DEVICE_INFO("device_info", "设备管理"),
    DEVICE_ALERT("device_alert", "设备告警"),
    DEVICE_ALERT_RULE("device_alert_rule", "告警规则配置"),
    DEVICE_MONITOR("device_monitor", "实时监控"),
    DEVICE_RECORDING("device_recording", "录像回放"),
    DEVICE_VIOLATION("device_violation", "违规处理"),
    DEVICE_CLIP("device_clip", "片段截取"),
    DEVICE_SCREENSHOT("device_screenshot", "回放截图"),
    DEVICE_EVIDENCE("device_evidence", "证据包"),

    SYS_ORGANIZATION("sys_organization", "组织管理"),
    SYS_EMPLOYEE("sys_employee", "员工管理"),
    SYS_DICT_CATEGORY("sys_dict_category", "字典分类维护"),
    SYS_ROLE_GROUP("sys_role_group", "角色分组"),
    SYS_ROLE("sys_role", "角色管理"),
    SYS_REVIEW("sys_review", "用餐评价"),
    SYS_COMPLAINT("sys_complaint", "投诉管理"),
    SYS_DISPATCH("sys_dispatch", "工单派单"),
    SYS_WORK_ORDER_RECORD("sys_work_order_record", "工单操作记录"),
    SYS_INTEGRATION("sys_integration", "第三方接入管理"),

    AUTH_PROFILE("auth_profile", "个人信息"),
    AUTH_PASSWORD("auth_password", "密码管理"),
    AUTH_PERMISSION("auth_permission", "权限管理");

    private final String code;
    private final String name;

    AuditModule(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
