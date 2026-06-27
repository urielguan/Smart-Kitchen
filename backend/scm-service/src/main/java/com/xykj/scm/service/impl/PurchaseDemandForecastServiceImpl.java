package com.xykj.scm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.scm.dto.PurchaseDemandForecastGenerateDTO;
import com.xykj.scm.dto.PurchaseDemandForecastPrefillDTO;
import com.xykj.scm.dto.PurchaseDemandForecastQueryDTO;
import com.xykj.scm.entity.MaterialForecastModelConfig;
import com.xykj.scm.entity.MaterialDailyConsumption;
import com.xykj.scm.entity.MaterialDailyFeature;
import com.xykj.scm.entity.PurchaseDemandForecast;
import com.xykj.scm.entity.PurchaseDemandForecastEvaluation;
import com.xykj.scm.entity.PurchaseDemandForecastOptimizationLog;
import com.xykj.scm.entity.PurchaseDemandForecastTask;
import com.xykj.scm.entity.PurchaseDemandForecastTaskItem;
import com.xykj.scm.entity.PurchaseDemandForecastItem;
import com.xykj.scm.entity.RecipeMaterialDailyDemand;
import com.xykj.scm.entity.SupplierDeliveryStat;
import com.xykj.scm.mapper.MaterialForecastModelConfigMapper;
import com.xykj.scm.mapper.MaterialDailyConsumptionMapper;
import com.xykj.scm.mapper.MaterialDailyFeatureMapper;
import com.xykj.scm.mapper.PurchaseDemandForecastEvaluationMapper;
import com.xykj.scm.mapper.PurchaseDemandForecastItemMapper;
import com.xykj.scm.mapper.PurchaseDemandForecastMapper;
import com.xykj.scm.mapper.PurchaseDemandForecastOptimizationLogMapper;
import com.xykj.scm.mapper.PurchaseDemandForecastTaskItemMapper;
import com.xykj.scm.mapper.PurchaseDemandForecastTaskMapper;
import com.xykj.scm.mapper.RecipeMaterialDailyDemandMapper;
import com.xykj.scm.mapper.SupplierDeliveryStatMapper;
import com.xykj.scm.service.PurchaseDemandForecastService;
import com.xykj.scm.support.PurchaseDemandForecastExplanationEngine;
import com.xykj.scm.support.PurchaseDemandForecastLinkageSupport;
import com.xykj.scm.support.PurchaseDemandForecastRuleEngine;
import com.xykj.scm.support.PurchaseDemandForecastVolatileBoostingSupport;
import com.xykj.scm.vo.PurchaseDemandForecastDashboardVO;
import com.xykj.scm.vo.PurchaseDemandForecastItemVO;
import com.xykj.scm.vo.PurchaseDemandForecastLinkedPlanVO;
import com.xykj.scm.vo.PurchaseDemandForecastMaterialLinkageVO;
import com.xykj.scm.vo.PurchaseDemandForecastPlanPrefillVO;
import com.xykj.scm.vo.PurchaseDemandForecastVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 采购需求预测服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseDemandForecastServiceImpl implements PurchaseDemandForecastService {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final Long DEFAULT_USER_ID = 1L;
    private static final String DIMENSION_DAILY = "daily";
    private static final String DIMENSION_WEEKLY = "weekly";
    private static final int HISTORY_DAYS = 30;
    private static final int RECIPE_LOOKAHEAD_DAYS = 7;
    private static final String PURCHASE_PLAN_CREATE_PERMISSION = "purchasePlan:create";
    private static final String FORECAST_PERMISSION_MESSAGE = "当前用户无采购需求预测权限";
    private static final String PREFILL_PERMISSION_MESSAGE = "当前用户无生成采购计划权限";
    private static final String PRIORITY_URGENT = "紧急补货";
    private static final String PRIORITY_HIGH = "优先补货";
    private static final String PRIORITY_NORMAL = "正常补货";
    private static final String EVALUATION_STATUS_PENDING = "pending";
    private static final String EVALUATION_STATUS_COMPLETED = "completed";
    private static final BigDecimal DEFAULT_RECIPE_LOSS_RATE = BigDecimal.valueOf(0.05d).setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_EPSILON = BigDecimal.valueOf(0.001d).setScale(3, RoundingMode.HALF_UP);
    private static final BigDecimal STOCKOUT_PENALTY_FACTOR = BigDecimal.valueOf(2.0d);
    private static final BigDecimal OVER_SUPPLY_PENALTY_FACTOR = BigDecimal.valueOf(1.0d);
    private static final int SUPPLIER_LOOKBACK_DAYS = 180;
    private static final BigDecimal DEFAULT_LEAD_TIME_RISK_FACTOR = BigDecimal.valueOf(0.70d).setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal SUPPLIER_RELIABILITY_WEIGHT = BigDecimal.valueOf(0.45d).setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal SUPPLIER_LEAD_TIME_WEIGHT = BigDecimal.valueOf(0.30d).setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal SUPPLIER_PRICE_WEIGHT = BigDecimal.valueOf(0.25d).setScale(6, RoundingMode.HALF_UP);
    private static final String ORDER_ACTION_NOW = "立即下单";
    private static final String ORDER_ACTION_PLAN = "计划补货";
    private static final String ORDER_ACTION_HOLD = "暂不下单";
    private static final Set<String> INTEGER_ONLY_UNITS = Set.of(
            "个", "件", "箱", "份", "套", "桶", "袋", "盒", "瓶", "包", "支", "卷", "把", "双", "台", "张",
            "片", "根", "只", "块", "颗", "粒", "枚", "罐", "听", "组", "盘", "篮", "捆", "扎", "盆", "提",
            "束", "棵", "朵", "排", "节", "尾", "头"
    );
    private static final Set<String> FRACTIONAL_UNITS = Set.of(
            "kg", "g", "mg", "t", "l", "ml", "m", "cm", "mm", "km", "m2", "m²", "㎡", "cm2", "cm²",
            "mm2", "mm²", "m3", "m³", "cm3", "cm³", "mm3", "mm³",
            "千克", "公斤", "克", "毫克", "吨", "斤", "两", "升", "毫升", "米", "厘米", "毫米", "公分",
            "分米", "平方米", "平方厘米", "平方毫米", "立方米", "立方厘米", "立方毫米"
    );
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> ACTIVITY_TABLE_CANDIDATES = List.of(
            "sys_activity",
            "sys_event",
            "sys_calendar_event",
            "sys_org_activity",
            "sys_campaign",
            "sys_festival_activity"
    );
    private static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),
            MonthDay.of(5, 1),
            MonthDay.of(5, 2),
            MonthDay.of(5, 3),
            MonthDay.of(5, 4),
            MonthDay.of(5, 5),
            MonthDay.of(10, 1),
            MonthDay.of(10, 2),
            MonthDay.of(10, 3),
            MonthDay.of(10, 4),
            MonthDay.of(10, 5),
            MonthDay.of(10, 6),
            MonthDay.of(10, 7)
    );

    private final PurchaseDemandForecastMapper purchaseDemandForecastMapper;
    private final PurchaseDemandForecastItemMapper purchaseDemandForecastItemMapper;
    private final PurchaseDemandForecastEvaluationMapper purchaseDemandForecastEvaluationMapper;
    private final MaterialForecastModelConfigMapper materialForecastModelConfigMapper;
    private final PurchaseDemandForecastOptimizationLogMapper purchaseDemandForecastOptimizationLogMapper;
    private final MaterialDailyConsumptionMapper materialDailyConsumptionMapper;
    private final RecipeMaterialDailyDemandMapper recipeMaterialDailyDemandMapper;
    private final MaterialDailyFeatureMapper materialDailyFeatureMapper;
    private final PurchaseDemandForecastTaskMapper purchaseDemandForecastTaskMapper;
    private final PurchaseDemandForecastTaskItemMapper purchaseDemandForecastTaskItemMapper;
    private final SupplierDeliveryStatMapper supplierDeliveryStatMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final PurchaseDemandForecastLinkageSupport purchaseDemandForecastLinkageSupport;
    private final PurchaseDemandForecastVolatileBoostingSupport purchaseDemandForecastVolatileBoostingSupport;
    private final PurchaseDemandForecastExplanationEngine purchaseDemandForecastExplanationEngine;
    private final PurchaseDemandForecastRuleEngine purchaseDemandForecastRuleEngine;

    @PostConstruct
    public void ensureForecastTables() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_purchase_demand_forecast (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预测单ID',
                      forecast_no VARCHAR(50) NOT NULL COMMENT '预测单号',
                      forecast_name VARCHAR(100) DEFAULT NULL COMMENT '预测名称',
                      forecast_dimension VARCHAR(20) NOT NULL COMMENT '预测维度：daily=日，weekly=周',
                      forecast_days INT NOT NULL DEFAULT 7 COMMENT '预测周期天数',
                      basis_date DATE NOT NULL COMMENT '预测基准日期',
                      horizon_start_date DATE NOT NULL COMMENT '预测周期开始日期',
                      horizon_end_date DATE NOT NULL COMMENT '预测周期结束日期',
                      material_count INT NOT NULL DEFAULT 0 COMMENT '预测物料数',
                      total_suggested_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '建议采购总金额',
                      calendar_factor DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '单位日历因子',
                      holiday_factor DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '节假日因子',
                      activity_factor DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '活动因子',
                      summary_basis VARCHAR(1000) DEFAULT NULL COMMENT '预测摘要依据',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_forecast_no (forecast_no),
                      KEY idx_forecast_org (org_id),
                      KEY idx_forecast_created_at (created_at),
                      KEY idx_forecast_dimension (forecast_dimension)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购需求预测主表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_purchase_demand_forecast_item (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预测明细ID',
                      forecast_id BIGINT NOT NULL COMMENT '预测单ID',
                      material_id BIGINT NOT NULL COMMENT '物料ID',
                      material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
                      material_spec VARCHAR(100) DEFAULT NULL COMMENT '规格',
                      material_unit VARCHAR(20) DEFAULT NULL COMMENT '单位',
                      current_inventory_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '当前库存',
                      historical_plan_avg_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '近30天计划日均量',
                      historical_order_avg_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '近30天订单日均量',
                      recipe_demand_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '未来7天菜谱需求量',
                      suggested_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '建议采购量',
                      confidence_lower_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '置信区间下限',
                      confidence_upper_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '置信区间上限',
                      estimated_unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '预估单价',
                      estimated_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '预估金额',
                      replenishment_priority VARCHAR(20) DEFAULT NULL COMMENT '补货优先级',
                      forecast_basis VARCHAR(1000) DEFAULT NULL COMMENT '预测依据',
                      sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                      PRIMARY KEY (id),
                      KEY idx_forecast_item_forecast (forecast_id),
                      KEY idx_forecast_item_material (material_id),
                      KEY idx_forecast_item_org (org_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购需求预测明细表'
                    """);
            ensureForecastItemColumn(
                    "replenishment_priority",
                    "ALTER TABLE scm_purchase_demand_forecast_item " +
                            "ADD COLUMN replenishment_priority VARCHAR(20) DEFAULT NULL COMMENT '补货优先级' AFTER estimated_amount"
            );
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_material_daily_consumption (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      stat_date DATE NOT NULL COMMENT '统计日期',
                      material_id BIGINT NOT NULL COMMENT '物料ID',
                      opening_stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '期初库存',
                      inbound_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '当日入库量',
                      outbound_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '当日出库量',
                      adjustment_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '库存调整量',
                      closing_stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '期末库存',
                      consumed_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '实际消耗量',
                      data_source VARCHAR(50) DEFAULT NULL COMMENT '计算来源',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_consumption_org_date_material (org_id, tenant_id, stat_date, material_id),
                      KEY idx_consumption_org_date_material (org_id, stat_date, material_id),
                      KEY idx_consumption_material_date (material_id, stat_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料日消耗事实表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_recipe_material_daily_demand (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      demand_date DATE NOT NULL COMMENT '需求日期',
                      recipe_plan_id BIGINT NOT NULL COMMENT '菜谱计划ID',
                      recipe_id BIGINT NOT NULL COMMENT '菜谱ID',
                      material_id BIGINT NOT NULL COMMENT '物料ID',
                      servings DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '计划份数',
                      standard_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '单份标准用量',
                      loss_rate DECIMAL(8,4) NOT NULL DEFAULT 0.0000 COMMENT '损耗率',
                      theoretical_demand_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '理论需求量',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_recipe_demand_org_plan_material (org_id, tenant_id, demand_date, recipe_plan_id, recipe_id, material_id),
                      KEY idx_recipe_demand_org_date_material (org_id, demand_date, material_id),
                      KEY idx_recipe_demand_plan (recipe_plan_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱物料日需求展开表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_material_daily_feature (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      stat_date DATE NOT NULL COMMENT '特征日期',
                      material_id BIGINT NOT NULL COMMENT '物料ID',
                      current_stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '当前库存',
                      available_stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '当前可用库存',
                      avg_consumption_7d DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '近7日均耗',
                      avg_consumption_14d DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '近14日均耗',
                      avg_consumption_30d DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '近30日均耗',
                      std_consumption_30d DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '近30日消耗标准差',
                      recipe_demand_7d DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '未来7天菜谱需求',
                      pending_plan_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '待执行采购计划量',
                      in_transit_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '在途采购量',
                      holiday_factor DECIMAL(8,4) NOT NULL DEFAULT 1.0000 COMMENT '节假日系数',
                      activity_factor DECIMAL(8,4) NOT NULL DEFAULT 1.0000 COMMENT '活动系数',
                      material_category VARCHAR(50) DEFAULT NULL COMMENT '物料类别',
                      forecast_type VARCHAR(50) DEFAULT NULL COMMENT '预测算法类型',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_feature_org_date_material (org_id, tenant_id, stat_date, material_id),
                      KEY idx_feature_org_date_material (org_id, stat_date, material_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料日特征宽表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_purchase_demand_forecast_task (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
                      forecast_no VARCHAR(50) NOT NULL COMMENT '预测单号',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      horizon_type VARCHAR(20) NOT NULL COMMENT '预测维度',
                      horizon_start_date DATE NOT NULL COMMENT '预测开始日期',
                      horizon_end_date DATE NOT NULL COMMENT '预测结束日期',
                      task_status VARCHAR(20) NOT NULL COMMENT '任务状态',
                      material_count INT NOT NULL DEFAULT 0 COMMENT '预测物料数',
                      trigger_type VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '触发类型',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_forecast_task_no (forecast_no),
                      KEY idx_forecast_task_org (org_id, horizon_start_date),
                      KEY idx_forecast_task_status (task_status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购需求预测任务主表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_purchase_demand_forecast_task_item (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务明细ID',
                      task_id BIGINT NOT NULL COMMENT '任务ID',
                      material_id BIGINT NOT NULL COMMENT '物料ID',
                      material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
                      forecast_demand_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '预测需求量',
                      safety_stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '安全库存',
                      available_stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '当前可用库存',
                      in_transit_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '在途量',
                      pending_exec_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '待执行量',
                      suggested_purchase_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '建议采购量',
                      lower_bound_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '置信区间下界',
                      upper_bound_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '置信区间上界',
                      confidence_level DECIMAL(8,4) NOT NULL DEFAULT 80.0000 COMMENT '置信度',
                      forecast_basis VARCHAR(500) DEFAULT NULL COMMENT '预测依据说明',
                      priority_level VARCHAR(20) DEFAULT NULL COMMENT '优先级',
                      model_type VARCHAR(50) DEFAULT NULL COMMENT '算法类型',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_forecast_task_item (task_id, material_id),
                      KEY idx_forecast_task_item_material (material_id),
                      KEY idx_forecast_task_item_priority (priority_level)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购需求预测任务明细表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_purchase_demand_forecast_evaluation (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '评估ID',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      material_id BIGINT NOT NULL COMMENT '物料ID',
                      forecast_date DATE NOT NULL COMMENT '预测日期',
                      horizon_start_date DATE NOT NULL COMMENT '预测窗口开始',
                      horizon_end_date DATE NOT NULL COMMENT '预测窗口结束',
                      predicted_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '预测量',
                      actual_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '实际量',
                      abs_error DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '绝对误差',
                      ape DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '绝对百分比误差',
                      model_type VARCHAR(50) DEFAULT NULL COMMENT '算法类型',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      KEY idx_forecast_eval_org_material (org_id, material_id, forecast_date),
                      KEY idx_forecast_eval_horizon (horizon_start_date, horizon_end_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购需求预测评估表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_material_forecast_model_config (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      material_id BIGINT DEFAULT NULL COMMENT '物料ID',
                      material_category VARCHAR(50) DEFAULT NULL COMMENT '物料类别',
                      material_segment VARCHAR(50) NOT NULL COMMENT '物料分层',
                      model_type VARCHAR(50) NOT NULL COMMENT '模型类型',
                      model_weight DECIMAL(12,6) DEFAULT NULL COMMENT '模型权重',
                      safety_days INT DEFAULT NULL COMMENT '安全天数',
                      lead_time_days INT DEFAULT NULL COMMENT '提前期天数',
                      service_level DECIMAL(12,6) DEFAULT NULL COMMENT '服务水平',
                      alpha_value DECIMAL(12,6) DEFAULT NULL COMMENT 'alpha参数',
                      beta_value DECIMAL(12,6) DEFAULT NULL COMMENT 'beta参数',
                      holiday_factor DECIMAL(12,6) DEFAULT NULL COMMENT '节假日系数',
                      weekend_factor DECIMAL(12,6) DEFAULT NULL COMMENT '周末系数',
                      activity_factor DECIMAL(12,6) DEFAULT NULL COMMENT '活动系数',
                      max_coverage_days INT DEFAULT NULL COMMENT '最大覆盖天数',
                      recipe_correction_min DECIMAL(12,6) DEFAULT NULL COMMENT '菜谱修正系数下限',
                      recipe_correction_max DECIMAL(12,6) DEFAULT NULL COMMENT '菜谱修正系数上限',
                      optimization_score DECIMAL(12,6) DEFAULT NULL COMMENT '优化评分',
                      version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
                      source_type VARCHAR(20) NOT NULL DEFAULT 'system' COMMENT '来源',
                      effective_start_date DATE DEFAULT NULL COMMENT '生效开始日期',
                      effective_end_date DATE DEFAULT NULL COMMENT '生效结束日期',
                      status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态',
                      remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      KEY idx_model_config_org_segment (org_id, material_segment, status),
                      KEY idx_model_config_material (org_id, material_id, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料预测模型配置表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_supplier_delivery_stat (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      supplier_id BIGINT NOT NULL COMMENT '供应商ID',
                      supplier_name VARCHAR(100) DEFAULT NULL COMMENT '供应商名称',
                      stat_start_date DATE DEFAULT NULL COMMENT '统计开始日期',
                      stat_end_date DATE DEFAULT NULL COMMENT '统计结束日期',
                      sample_count INT NOT NULL DEFAULT 0 COMMENT '样本数',
                      avg_lead_time_days DECIMAL(12,6) DEFAULT NULL COMMENT '平均提前期',
                      std_lead_time_days DECIMAL(12,6) DEFAULT NULL COMMENT '提前期标准差',
                      fill_rate DECIMAL(12,6) DEFAULT NULL COMMENT '履约率',
                      on_time_rate DECIMAL(12,6) DEFAULT NULL COMMENT '准时率',
                      last_calculated_at DATETIME DEFAULT NULL COMMENT '最近计算时间',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_supplier_delivery_stat (org_id, tenant_id, supplier_id),
                      KEY idx_supplier_delivery_org (org_id, stat_end_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商履约统计表'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scm_purchase_demand_forecast_optimization_log (
                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                      org_id BIGINT NOT NULL COMMENT '所属组织ID',
                      tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
                      material_id BIGINT DEFAULT NULL COMMENT '物料ID',
                      material_category VARCHAR(50) DEFAULT NULL COMMENT '物料类别',
                      material_segment VARCHAR(50) NOT NULL COMMENT '物料分层',
                      model_type VARCHAR(50) NOT NULL COMMENT '模型类型',
                      candidate_config_json TEXT DEFAULT NULL COMMENT '候选参数集',
                      selected_config_json TEXT DEFAULT NULL COMMENT '选中参数集',
                      score DECIMAL(12,6) DEFAULT NULL COMMENT '目标函数评分',
                      wape DECIMAL(12,6) DEFAULT NULL COMMENT 'WAPE',
                      stockout_rate DECIMAL(12,6) DEFAULT NULL COMMENT '缺货率',
                      oversupply_rate DECIMAL(12,6) DEFAULT NULL COMMENT '过采率',
                      sample_forecast_count INT NOT NULL DEFAULT 0 COMMENT '参与优化的样本数',
                      version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
                      trigger_type VARCHAR(20) NOT NULL DEFAULT 'auto' COMMENT '触发方式',
                      effective_date DATE DEFAULT NULL COMMENT '生效日期',
                      optimized_at DATETIME DEFAULT NULL COMMENT '优化完成时间',
                      created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      KEY idx_opt_log_org_segment (org_id, material_segment, optimized_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购需求预测自动优化日志表'
                    """);

            ensureTableColumn("scm_purchase_demand_forecast", "evaluation_status",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN evaluation_status VARCHAR(20) DEFAULT 'pending' COMMENT '评估状态：pending=待回算，completed=已回算' AFTER summary_basis");
            ensureTableColumn("scm_purchase_demand_forecast", "evaluated_at",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN evaluated_at DATETIME DEFAULT NULL COMMENT '回算完成时间' AFTER evaluation_status");
            ensureTableColumn("scm_purchase_demand_forecast", "overall_wape",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN overall_wape DECIMAL(12,6) DEFAULT NULL COMMENT '整体WAPE' AFTER evaluated_at");
            ensureTableColumn("scm_purchase_demand_forecast", "overall_mape",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN overall_mape DECIMAL(12,6) DEFAULT NULL COMMENT '整体MAPE' AFTER overall_wape");
            ensureTableColumn("scm_purchase_demand_forecast", "overall_rmse",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN overall_rmse DECIMAL(12,6) DEFAULT NULL COMMENT '整体RMSE' AFTER overall_mape");
            ensureTableColumn("scm_purchase_demand_forecast", "bias_rate",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN bias_rate DECIMAL(12,6) DEFAULT NULL COMMENT '整体偏差率' AFTER overall_rmse");
            ensureTableColumn("scm_purchase_demand_forecast", "stockout_rate",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN stockout_rate DECIMAL(12,6) DEFAULT NULL COMMENT '缺货率' AFTER bias_rate");
            ensureTableColumn("scm_purchase_demand_forecast", "oversupply_rate",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN oversupply_rate DECIMAL(12,6) DEFAULT NULL COMMENT '过采率' AFTER stockout_rate");
            ensureTableColumn("scm_purchase_demand_forecast", "optimization_version",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN optimization_version INT NOT NULL DEFAULT 0 COMMENT '自动优化版本号' AFTER oversupply_rate");
            ensureTableColumn("scm_purchase_demand_forecast", "optimization_score",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN optimization_score DECIMAL(12,6) DEFAULT NULL COMMENT '自动优化评分' AFTER optimization_version");
            ensureTableColumn("scm_purchase_demand_forecast", "explanation_summary",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN explanation_summary VARCHAR(1000) DEFAULT NULL COMMENT '解释摘要' AFTER optimization_score");
            ensureTableColumn("scm_purchase_demand_forecast", "approval_summary",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN approval_summary VARCHAR(1000) DEFAULT NULL COMMENT '审批说明摘要' AFTER explanation_summary");
            ensureTableColumn("scm_purchase_demand_forecast", "reorder_triggered_count",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN reorder_triggered_count INT NOT NULL DEFAULT 0 COMMENT '触发订货点物料数' AFTER explanation_summary");
            ensureTableColumn("scm_purchase_demand_forecast", "risk_item_count",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN risk_item_count INT NOT NULL DEFAULT 0 COMMENT '风险物料数' AFTER reorder_triggered_count");
            ensureTableColumn("scm_purchase_demand_forecast", "supplier_recommended_count",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN supplier_recommended_count INT NOT NULL DEFAULT 0 COMMENT '已推荐供应商物料数' AFTER risk_item_count");
            ensureTableColumn("scm_purchase_demand_forecast", "manual_review_count",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN manual_review_count INT NOT NULL DEFAULT 0 COMMENT '建议人工复核物料数' AFTER supplier_recommended_count");
            ensureTableColumn("scm_purchase_demand_forecast", "warning_item_count",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN warning_item_count INT NOT NULL DEFAULT 0 COMMENT '触发预警物料数' AFTER manual_review_count");
            ensureTableColumn("scm_purchase_demand_forecast", "total_optimization_cost",
                    "ALTER TABLE scm_purchase_demand_forecast ADD COLUMN total_optimization_cost DECIMAL(12,2) DEFAULT NULL COMMENT '综合优化成本' AFTER supplier_recommended_count");
            ensureTableColumn("scm_purchase_demand_forecast_item", "forecast_demand_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN forecast_demand_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '预测需求量' AFTER recipe_demand_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "safety_stock_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN safety_stock_qty DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '安全库存' AFTER forecast_demand_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "model_type",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN model_type VARCHAR(50) DEFAULT NULL COMMENT '模型类型' AFTER replenishment_priority");
            ensureTableColumn("scm_purchase_demand_forecast_item", "material_segment",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN material_segment VARCHAR(50) DEFAULT NULL COMMENT '物料分层' AFTER model_type");
            ensureTableColumn("scm_purchase_demand_forecast_item", "explanation_summary",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN explanation_summary VARCHAR(500) DEFAULT NULL COMMENT '解释摘要' AFTER forecast_basis");
            ensureTableColumn("scm_purchase_demand_forecast_item", "explanation_detail",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN explanation_detail VARCHAR(2000) DEFAULT NULL COMMENT '解释详情' AFTER explanation_summary");
            ensureTableColumn("scm_purchase_demand_forecast_item", "explanation_template_code",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN explanation_template_code VARCHAR(50) DEFAULT NULL COMMENT '解释模板编码' AFTER explanation_detail");
            ensureTableColumn("scm_purchase_demand_forecast_item", "explanation_title",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN explanation_title VARCHAR(120) DEFAULT NULL COMMENT '解释标题' AFTER explanation_template_code");
            ensureTableColumn("scm_purchase_demand_forecast_item", "warning_level",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN warning_level VARCHAR(20) DEFAULT NULL COMMENT '预警级别' AFTER explanation_title");
            ensureTableColumn("scm_purchase_demand_forecast_item", "warning_message",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN warning_message VARCHAR(500) DEFAULT NULL COMMENT '预警说明' AFTER warning_level");
            ensureTableColumn("scm_purchase_demand_forecast_item", "approval_note",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN approval_note VARCHAR(500) DEFAULT NULL COMMENT '审批说明' AFTER warning_message");
            ensureTableColumn("scm_purchase_demand_forecast_item", "manual_review_required",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN manual_review_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否建议人工复核' AFTER approval_note");
            ensureTableColumn("scm_purchase_demand_forecast_item", "explanation_factors_json",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN explanation_factors_json TEXT DEFAULT NULL COMMENT '解释证据JSON' AFTER manual_review_required");
            ensureTableColumn("scm_purchase_demand_forecast_item", "anomaly_codes",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN anomaly_codes VARCHAR(500) DEFAULT NULL COMMENT '异常编码' AFTER explanation_factors_json");
            ensureTableColumn("scm_purchase_demand_forecast_item", "explanation_sort_score",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN explanation_sort_score DECIMAL(12,6) DEFAULT NULL COMMENT '解释排序分' AFTER anomaly_codes");
            ensureTableColumn("scm_purchase_demand_forecast_item", "anomaly_flags",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN anomaly_flags VARCHAR(1000) DEFAULT NULL COMMENT '异常标签' AFTER explanation_detail");
            ensureTableColumn("scm_purchase_demand_forecast_item", "actual_consumption_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN actual_consumption_qty DECIMAL(10,3) DEFAULT NULL COMMENT '实际消耗量' AFTER anomaly_flags");
            ensureTableColumn("scm_purchase_demand_forecast_item", "abs_error",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN abs_error DECIMAL(10,3) DEFAULT NULL COMMENT '绝对误差' AFTER actual_consumption_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "ape",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN ape DECIMAL(12,6) DEFAULT NULL COMMENT '绝对百分比误差' AFTER abs_error");
            ensureTableColumn("scm_purchase_demand_forecast_item", "rmse",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN rmse DECIMAL(12,6) DEFAULT NULL COMMENT '均方根误差' AFTER ape");
            ensureTableColumn("scm_purchase_demand_forecast_item", "bias_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN bias_qty DECIMAL(10,3) DEFAULT NULL COMMENT '偏差量' AFTER rmse");
            ensureTableColumn("scm_purchase_demand_forecast_item", "recipe_drive_ratio",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN recipe_drive_ratio DECIMAL(12,6) DEFAULT NULL COMMENT '菜谱驱动占比' AFTER bias_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "demand_active_ratio",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN demand_active_ratio DECIMAL(12,6) DEFAULT NULL COMMENT '需求活跃度' AFTER recipe_drive_ratio");
            ensureTableColumn("scm_purchase_demand_forecast_item", "demand_cv",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN demand_cv DECIMAL(12,6) DEFAULT NULL COMMENT '需求波动系数' AFTER demand_active_ratio");
            ensureTableColumn("scm_purchase_demand_forecast_item", "activity_sensitivity",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN activity_sensitivity DECIMAL(12,6) DEFAULT NULL COMMENT '活动敏感度' AFTER demand_cv");
            ensureTableColumn("scm_purchase_demand_forecast_item", "service_level",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN service_level DECIMAL(12,6) DEFAULT NULL COMMENT '服务水平' AFTER activity_sensitivity");
            ensureTableColumn("scm_purchase_demand_forecast_item", "lead_time_days",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN lead_time_days INT DEFAULT NULL COMMENT '提前期天数' AFTER service_level");
            ensureTableColumn("scm_purchase_demand_forecast_item", "avg_daily_demand_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN avg_daily_demand_qty DECIMAL(10,3) DEFAULT NULL COMMENT '日均需求量' AFTER safety_stock_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "review_period_days",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN review_period_days INT DEFAULT NULL COMMENT '评审周期天数' AFTER avg_daily_demand_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "reorder_point_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN reorder_point_qty DECIMAL(10,3) DEFAULT NULL COMMENT '订货点' AFTER review_period_days");
            ensureTableColumn("scm_purchase_demand_forecast_item", "target_stock_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN target_stock_qty DECIMAL(10,3) DEFAULT NULL COMMENT '目标库存上限' AFTER reorder_point_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "inventory_position_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN inventory_position_qty DECIMAL(10,3) DEFAULT NULL COMMENT '库存位置量' AFTER target_stock_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "theoretical_suggested_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN theoretical_suggested_qty DECIMAL(10,3) DEFAULT NULL COMMENT '理论建议采购量' AFTER inventory_position_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "effective_lead_time_days",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN effective_lead_time_days DECIMAL(12,6) DEFAULT NULL COMMENT '有效提前期天数' AFTER lead_time_days");
            ensureTableColumn("scm_purchase_demand_forecast_item", "min_order_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN min_order_qty DECIMAL(10,3) DEFAULT NULL COMMENT '最小起订量' AFTER effective_lead_time_days");
            ensureTableColumn("scm_purchase_demand_forecast_item", "pack_size",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN pack_size DECIMAL(10,3) DEFAULT NULL COMMENT '包装规格' AFTER min_order_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "max_allowed_stock_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN max_allowed_stock_qty DECIMAL(10,3) DEFAULT NULL COMMENT '最大允许库存' AFTER pack_size");
            ensureTableColumn("scm_purchase_demand_forecast_item", "max_coverage_days",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN max_coverage_days INT DEFAULT NULL COMMENT '最大覆盖天数' AFTER max_allowed_stock_qty");
            ensureTableColumn("scm_purchase_demand_forecast_item", "recommended_supplier_id",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN recommended_supplier_id BIGINT DEFAULT NULL COMMENT '推荐供应商ID' AFTER max_coverage_days");
            ensureTableColumn("scm_purchase_demand_forecast_item", "recommended_supplier_name",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN recommended_supplier_name VARCHAR(100) DEFAULT NULL COMMENT '推荐供应商名称' AFTER recommended_supplier_id");
            ensureTableColumn("scm_purchase_demand_forecast_item", "supplier_score",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN supplier_score DECIMAL(12,6) DEFAULT NULL COMMENT '供应商综合评分' AFTER recommended_supplier_name");
            ensureTableColumn("scm_purchase_demand_forecast_item", "supplier_fill_rate",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN supplier_fill_rate DECIMAL(12,6) DEFAULT NULL COMMENT '供应商履约率' AFTER supplier_score");
            ensureTableColumn("scm_purchase_demand_forecast_item", "supplier_on_time_rate",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN supplier_on_time_rate DECIMAL(12,6) DEFAULT NULL COMMENT '供应商准时率' AFTER supplier_fill_rate");
            ensureTableColumn("scm_purchase_demand_forecast_item", "order_now",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN order_now TINYINT NOT NULL DEFAULT 0 COMMENT '是否建议立即下单' AFTER supplier_on_time_rate");
            ensureTableColumn("scm_purchase_demand_forecast_item", "order_action",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN order_action VARCHAR(20) DEFAULT NULL COMMENT '下单动作建议' AFTER order_now");
            ensureTableColumn("scm_purchase_demand_forecast_item", "shortage_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN shortage_cost DECIMAL(12,2) DEFAULT NULL COMMENT '缺货成本' AFTER order_action");
            ensureTableColumn("scm_purchase_demand_forecast_item", "holding_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN holding_cost DECIMAL(12,2) DEFAULT NULL COMMENT '库存持有成本' AFTER shortage_cost");
            ensureTableColumn("scm_purchase_demand_forecast_item", "expiry_risk_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN expiry_risk_cost DECIMAL(12,2) DEFAULT NULL COMMENT '临期报废成本' AFTER holding_cost");
            ensureTableColumn("scm_purchase_demand_forecast_item", "order_processing_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN order_processing_cost DECIMAL(12,2) DEFAULT NULL COMMENT '下单成本' AFTER expiry_risk_cost");
            ensureTableColumn("scm_purchase_demand_forecast_item", "purchase_price_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN purchase_price_cost DECIMAL(12,2) DEFAULT NULL COMMENT '采购价格成本' AFTER order_processing_cost");
            ensureTableColumn("scm_purchase_demand_forecast_item", "total_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN total_cost DECIMAL(12,2) DEFAULT NULL COMMENT '综合优化成本' AFTER purchase_price_cost");
            ensureTableColumn("scm_purchase_demand_forecast_item", "phase_three_risk_flags",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN phase_three_risk_flags VARCHAR(1000) DEFAULT NULL COMMENT '三期风险标记' AFTER total_cost");
            ensureTableColumn("scm_purchase_demand_forecast_item", "evaluation_status",
                    "ALTER TABLE scm_purchase_demand_forecast_item ADD COLUMN evaluation_status VARCHAR(20) DEFAULT 'pending' COMMENT '评估状态' AFTER lead_time_days");

            ensureTableColumn("scm_material_daily_feature", "recipe_history_30d",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN recipe_history_30d DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '近30天理论菜谱需求' AFTER recipe_demand_7d");
            ensureTableColumn("scm_material_daily_feature", "actual_consumption_30d",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN actual_consumption_30d DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '近30天实际消耗' AFTER recipe_history_30d");
            ensureTableColumn("scm_material_daily_feature", "consumption_days_30d",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN consumption_days_30d INT NOT NULL DEFAULT 0 COMMENT '近30天有消耗天数' AFTER actual_consumption_30d");
            ensureTableColumn("scm_material_daily_feature", "inventory_turnover_days",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN inventory_turnover_days DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '库存周转天数' AFTER consumption_days_30d");
            ensureTableColumn("scm_material_daily_feature", "recipe_drive_ratio",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN recipe_drive_ratio DECIMAL(12,6) DEFAULT NULL COMMENT '菜谱驱动占比' AFTER forecast_type");
            ensureTableColumn("scm_material_daily_feature", "demand_active_ratio",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN demand_active_ratio DECIMAL(12,6) DEFAULT NULL COMMENT '需求活跃度' AFTER recipe_drive_ratio");
            ensureTableColumn("scm_material_daily_feature", "demand_cv",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN demand_cv DECIMAL(12,6) DEFAULT NULL COMMENT '需求波动系数' AFTER demand_active_ratio");
            ensureTableColumn("scm_material_daily_feature", "activity_sensitivity",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN activity_sensitivity DECIMAL(12,6) DEFAULT NULL COMMENT '活动敏感度' AFTER demand_cv");
            ensureTableColumn("scm_material_daily_feature", "material_segment",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN material_segment VARCHAR(50) DEFAULT NULL COMMENT '物料分层' AFTER activity_sensitivity");
            ensureTableColumn("scm_material_daily_feature", "lead_time_days",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN lead_time_days INT DEFAULT NULL COMMENT '提前期天数' AFTER material_segment");
            ensureTableColumn("scm_material_daily_feature", "service_level",
                    "ALTER TABLE scm_material_daily_feature ADD COLUMN service_level DECIMAL(12,6) DEFAULT NULL COMMENT '服务水平' AFTER lead_time_days");

            ensureTableColumn("scm_purchase_demand_forecast_task", "evaluation_status",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN evaluation_status VARCHAR(20) DEFAULT 'pending' COMMENT '评估状态' AFTER trigger_type");
            ensureTableColumn("scm_purchase_demand_forecast_task", "evaluated_at",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN evaluated_at DATETIME DEFAULT NULL COMMENT '回算完成时间' AFTER evaluation_status");
            ensureTableColumn("scm_purchase_demand_forecast_task", "wape",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN wape DECIMAL(12,6) DEFAULT NULL COMMENT 'WAPE' AFTER evaluated_at");
            ensureTableColumn("scm_purchase_demand_forecast_task", "mape",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN mape DECIMAL(12,6) DEFAULT NULL COMMENT 'MAPE' AFTER wape");
            ensureTableColumn("scm_purchase_demand_forecast_task", "rmse",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN rmse DECIMAL(12,6) DEFAULT NULL COMMENT 'RMSE' AFTER mape");
            ensureTableColumn("scm_purchase_demand_forecast_task", "bias_rate",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN bias_rate DECIMAL(12,6) DEFAULT NULL COMMENT '偏差率' AFTER rmse");
            ensureTableColumn("scm_purchase_demand_forecast_task", "stockout_rate",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN stockout_rate DECIMAL(12,6) DEFAULT NULL COMMENT '缺货率' AFTER bias_rate");
            ensureTableColumn("scm_purchase_demand_forecast_task", "oversupply_rate",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN oversupply_rate DECIMAL(12,6) DEFAULT NULL COMMENT '过采率' AFTER stockout_rate");
            ensureTableColumn("scm_purchase_demand_forecast_task", "optimization_version",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN optimization_version INT NOT NULL DEFAULT 0 COMMENT '优化版本号' AFTER oversupply_rate");
            ensureTableColumn("scm_purchase_demand_forecast_task", "optimization_score",
                    "ALTER TABLE scm_purchase_demand_forecast_task ADD COLUMN optimization_score DECIMAL(12,6) DEFAULT NULL COMMENT '优化评分' AFTER optimization_version");

            ensureTableColumn("scm_purchase_demand_forecast_task_item", "material_unit",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN material_unit VARCHAR(20) DEFAULT NULL COMMENT '物料单位' AFTER material_name");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "material_category",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN material_category VARCHAR(50) DEFAULT NULL COMMENT '物料类别' AFTER material_unit");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "material_segment",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN material_segment VARCHAR(50) DEFAULT NULL COMMENT '物料分层' AFTER material_category");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "current_stock_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN current_stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0.000 COMMENT '当前库存' AFTER safety_stock_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "avg_daily_demand_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN avg_daily_demand_qty DECIMAL(18,3) DEFAULT NULL COMMENT '日均需求量' AFTER safety_stock_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "review_period_days",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN review_period_days INT DEFAULT NULL COMMENT '评审周期天数' AFTER avg_daily_demand_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "reorder_point_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN reorder_point_qty DECIMAL(18,3) DEFAULT NULL COMMENT '订货点' AFTER review_period_days");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "target_stock_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN target_stock_qty DECIMAL(18,3) DEFAULT NULL COMMENT '目标库存上限' AFTER reorder_point_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "inventory_position_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN inventory_position_qty DECIMAL(18,3) DEFAULT NULL COMMENT '库存位置量' AFTER available_stock_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "theoretical_suggested_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN theoretical_suggested_qty DECIMAL(18,3) DEFAULT NULL COMMENT '理论建议采购量' AFTER pending_exec_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "feature_snapshot_json",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN feature_snapshot_json TEXT DEFAULT NULL COMMENT '特征快照JSON' AFTER model_type");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "model_param_json",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN model_param_json TEXT DEFAULT NULL COMMENT '模型参数JSON' AFTER feature_snapshot_json");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "explanation_summary",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN explanation_summary VARCHAR(500) DEFAULT NULL COMMENT '解释摘要' AFTER model_param_json");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "explanation_detail",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN explanation_detail VARCHAR(2000) DEFAULT NULL COMMENT '解释详情' AFTER explanation_summary");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "explanation_template_code",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN explanation_template_code VARCHAR(50) DEFAULT NULL COMMENT '解释模板编码' AFTER explanation_detail");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "explanation_title",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN explanation_title VARCHAR(120) DEFAULT NULL COMMENT '解释标题' AFTER explanation_template_code");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "warning_level",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN warning_level VARCHAR(20) DEFAULT NULL COMMENT '预警级别' AFTER explanation_title");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "warning_message",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN warning_message VARCHAR(500) DEFAULT NULL COMMENT '预警说明' AFTER warning_level");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "approval_note",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN approval_note VARCHAR(500) DEFAULT NULL COMMENT '审批说明' AFTER warning_message");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "manual_review_required",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN manual_review_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否建议人工复核' AFTER approval_note");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "explanation_factors_json",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN explanation_factors_json TEXT DEFAULT NULL COMMENT '解释证据JSON' AFTER manual_review_required");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "anomaly_codes",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN anomaly_codes VARCHAR(500) DEFAULT NULL COMMENT '异常编码' AFTER explanation_factors_json");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "explanation_sort_score",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN explanation_sort_score DECIMAL(12,6) DEFAULT NULL COMMENT '解释排序分' AFTER anomaly_codes");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "anomaly_flags",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN anomaly_flags VARCHAR(1000) DEFAULT NULL COMMENT '异常标签' AFTER explanation_detail");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "actual_consumption_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN actual_consumption_qty DECIMAL(18,3) DEFAULT NULL COMMENT '实际消耗量' AFTER anomaly_flags");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "abs_error",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN abs_error DECIMAL(18,3) DEFAULT NULL COMMENT '绝对误差' AFTER actual_consumption_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "ape",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN ape DECIMAL(12,6) DEFAULT NULL COMMENT '绝对百分比误差' AFTER abs_error");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "rmse",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN rmse DECIMAL(12,6) DEFAULT NULL COMMENT '均方根误差' AFTER ape");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "bias_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN bias_qty DECIMAL(18,3) DEFAULT NULL COMMENT '偏差量' AFTER rmse");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "stockout_days",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN stockout_days INT DEFAULT NULL COMMENT '缺货天数' AFTER bias_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "oversupply_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN oversupply_qty DECIMAL(18,3) DEFAULT NULL COMMENT '过采量' AFTER stockout_days");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "evaluation_status",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN evaluation_status VARCHAR(20) DEFAULT 'pending' COMMENT '评估状态' AFTER oversupply_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "evaluated_at",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN evaluated_at DATETIME DEFAULT NULL COMMENT '评估完成时间' AFTER evaluation_status");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "recipe_drive_ratio",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN recipe_drive_ratio DECIMAL(12,6) DEFAULT NULL COMMENT '菜谱驱动占比' AFTER evaluated_at");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "demand_active_ratio",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN demand_active_ratio DECIMAL(12,6) DEFAULT NULL COMMENT '需求活跃度' AFTER recipe_drive_ratio");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "demand_cv",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN demand_cv DECIMAL(12,6) DEFAULT NULL COMMENT '需求波动系数' AFTER demand_active_ratio");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "activity_sensitivity",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN activity_sensitivity DECIMAL(12,6) DEFAULT NULL COMMENT '活动敏感度' AFTER demand_cv");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "lead_time_days",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN lead_time_days INT DEFAULT NULL COMMENT '提前期天数' AFTER activity_sensitivity");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "service_level",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN service_level DECIMAL(12,6) DEFAULT NULL COMMENT '服务水平' AFTER lead_time_days");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "estimated_unit_price",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN estimated_unit_price DECIMAL(10,2) DEFAULT NULL COMMENT '预估单价' AFTER explanation_detail");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "effective_lead_time_days",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN effective_lead_time_days DECIMAL(12,6) DEFAULT NULL COMMENT '有效提前期天数' AFTER service_level");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "min_order_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN min_order_qty DECIMAL(18,3) DEFAULT NULL COMMENT '最小起订量' AFTER effective_lead_time_days");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "pack_size",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN pack_size DECIMAL(18,3) DEFAULT NULL COMMENT '包装规格' AFTER min_order_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "max_allowed_stock_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN max_allowed_stock_qty DECIMAL(18,3) DEFAULT NULL COMMENT '最大允许库存' AFTER pack_size");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "max_coverage_days",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN max_coverage_days INT DEFAULT NULL COMMENT '最大覆盖天数' AFTER max_allowed_stock_qty");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "recommended_supplier_id",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN recommended_supplier_id BIGINT DEFAULT NULL COMMENT '推荐供应商ID' AFTER max_coverage_days");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "recommended_supplier_name",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN recommended_supplier_name VARCHAR(100) DEFAULT NULL COMMENT '推荐供应商名称' AFTER recommended_supplier_id");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "supplier_score",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN supplier_score DECIMAL(12,6) DEFAULT NULL COMMENT '供应商综合评分' AFTER recommended_supplier_name");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "supplier_fill_rate",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN supplier_fill_rate DECIMAL(12,6) DEFAULT NULL COMMENT '供应商履约率' AFTER supplier_score");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "supplier_on_time_rate",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN supplier_on_time_rate DECIMAL(12,6) DEFAULT NULL COMMENT '供应商准时率' AFTER supplier_fill_rate");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "order_now",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN order_now TINYINT NOT NULL DEFAULT 0 COMMENT '是否建议立即下单' AFTER supplier_on_time_rate");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "order_action",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN order_action VARCHAR(20) DEFAULT NULL COMMENT '下单动作建议' AFTER order_now");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "shortage_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN shortage_cost DECIMAL(12,2) DEFAULT NULL COMMENT '缺货成本' AFTER order_action");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "holding_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN holding_cost DECIMAL(12,2) DEFAULT NULL COMMENT '库存持有成本' AFTER shortage_cost");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "expiry_risk_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN expiry_risk_cost DECIMAL(12,2) DEFAULT NULL COMMENT '临期报废成本' AFTER holding_cost");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "order_processing_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN order_processing_cost DECIMAL(12,2) DEFAULT NULL COMMENT '下单成本' AFTER expiry_risk_cost");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "purchase_price_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN purchase_price_cost DECIMAL(12,2) DEFAULT NULL COMMENT '采购价格成本' AFTER order_processing_cost");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "total_cost",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN total_cost DECIMAL(12,2) DEFAULT NULL COMMENT '综合优化成本' AFTER purchase_price_cost");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "phase_three_risk_flags",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN phase_three_risk_flags VARCHAR(1000) DEFAULT NULL COMMENT '三期风险标记' AFTER total_cost");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "optimization_version",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN optimization_version INT NOT NULL DEFAULT 0 COMMENT '优化版本号' AFTER service_level");
            ensureTableColumn("scm_purchase_demand_forecast_task_item", "optimization_score",
                    "ALTER TABLE scm_purchase_demand_forecast_task_item ADD COLUMN optimization_score DECIMAL(12,6) DEFAULT NULL COMMENT '优化评分' AFTER optimization_version");

            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "forecast_id",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN forecast_id BIGINT DEFAULT NULL COMMENT '预测单ID' AFTER tenant_id");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "task_id",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN task_id BIGINT DEFAULT NULL COMMENT '任务ID' AFTER forecast_id");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "task_item_id",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN task_item_id BIGINT DEFAULT NULL COMMENT '任务明细ID' AFTER task_id");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "forecast_item_id",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN forecast_item_id BIGINT DEFAULT NULL COMMENT '预测明细ID' AFTER task_item_id");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "material_name",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN material_name VARCHAR(100) DEFAULT NULL COMMENT '物料名称' AFTER material_id");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "material_segment",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN material_segment VARCHAR(50) DEFAULT NULL COMMENT '物料分层' AFTER material_name");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "bias_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN bias_qty DECIMAL(18,3) DEFAULT NULL COMMENT '偏差量' AFTER ape");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "rmse",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN rmse DECIMAL(12,6) DEFAULT NULL COMMENT '均方根误差' AFTER ape");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "stockout_days",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN stockout_days INT DEFAULT NULL COMMENT '缺货天数' AFTER bias_qty");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "oversupply_qty",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN oversupply_qty DECIMAL(18,3) DEFAULT NULL COMMENT '过采量' AFTER stockout_days");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "wape_contribution",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN wape_contribution DECIMAL(12,6) DEFAULT NULL COMMENT 'WAPE贡献值' AFTER oversupply_qty");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "actual_source",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN actual_source VARCHAR(50) DEFAULT NULL COMMENT '实际量来源' AFTER model_type");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "evaluation_status",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN evaluation_status VARCHAR(20) DEFAULT 'completed' COMMENT '评估状态' AFTER actual_source");
            ensureTableColumn("scm_purchase_demand_forecast_evaluation", "evaluated_at",
                    "ALTER TABLE scm_purchase_demand_forecast_evaluation ADD COLUMN evaluated_at DATETIME DEFAULT NULL COMMENT '评估完成时间' AFTER evaluation_status");
            ensureTableColumn("scm_material_forecast_model_config", "min_order_qty",
                    "ALTER TABLE scm_material_forecast_model_config ADD COLUMN min_order_qty DECIMAL(18,3) DEFAULT NULL COMMENT '最小起订量' AFTER recipe_correction_max");
            ensureTableColumn("scm_material_forecast_model_config", "pack_size",
                    "ALTER TABLE scm_material_forecast_model_config ADD COLUMN pack_size DECIMAL(18,3) DEFAULT NULL COMMENT '包装规格' AFTER min_order_qty");
            ensureTableColumn("scm_material_forecast_model_config", "lead_time_risk_factor",
                    "ALTER TABLE scm_material_forecast_model_config ADD COLUMN lead_time_risk_factor DECIMAL(12,6) DEFAULT NULL COMMENT '提前期波动系数k' AFTER pack_size");
            ensureTableColumn("scm_material_forecast_model_config", "shortage_penalty",
                    "ALTER TABLE scm_material_forecast_model_config ADD COLUMN shortage_penalty DECIMAL(12,6) DEFAULT NULL COMMENT '缺货惩罚系数' AFTER lead_time_risk_factor");
            ensureTableColumn("scm_material_forecast_model_config", "holding_cost_rate",
                    "ALTER TABLE scm_material_forecast_model_config ADD COLUMN holding_cost_rate DECIMAL(12,6) DEFAULT NULL COMMENT '库存持有成本率' AFTER shortage_penalty");
            ensureTableColumn("scm_material_forecast_model_config", "waste_cost_rate",
                    "ALTER TABLE scm_material_forecast_model_config ADD COLUMN waste_cost_rate DECIMAL(12,6) DEFAULT NULL COMMENT '临期报废成本率' AFTER holding_cost_rate");
            ensureTableColumn("scm_material_forecast_model_config", "order_cost",
                    "ALTER TABLE scm_material_forecast_model_config ADD COLUMN order_cost DECIMAL(12,2) DEFAULT NULL COMMENT '单次下单成本' AFTER waste_cost_rate");
            ensureTableColumn("scm_purchase_demand_forecast_optimization_log", "rollback_applied",
                    "ALTER TABLE scm_purchase_demand_forecast_optimization_log ADD COLUMN rollback_applied TINYINT NOT NULL DEFAULT 0 COMMENT '是否触发回退' AFTER optimized_at");
            ensureTableColumn("scm_purchase_demand_forecast_optimization_log", "previous_version_no",
                    "ALTER TABLE scm_purchase_demand_forecast_optimization_log ADD COLUMN previous_version_no INT DEFAULT NULL COMMENT '回退前版本号' AFTER rollback_applied");
            ensureTableColumn("scm_purchase_demand_forecast_optimization_log", "previous_score",
                    "ALTER TABLE scm_purchase_demand_forecast_optimization_log ADD COLUMN previous_score DECIMAL(12,6) DEFAULT NULL COMMENT '回退前评分' AFTER previous_version_no");
            ensureTableColumn("scm_purchase_demand_forecast_optimization_log", "rollback_reason",
                    "ALTER TABLE scm_purchase_demand_forecast_optimization_log ADD COLUMN rollback_reason VARCHAR(255) DEFAULT NULL COMMENT '回退原因' AFTER previous_score");

            log.info("采购需求预测表结构检查完成");
        } catch (Exception ex) {
            log.warn("采购需求预测表结构检查失败: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseDemandForecastVO generate(PurchaseDemandForecastGenerateDTO dto) {
        ensurePermission(PURCHASE_PLAN_CREATE_PERMISSION, FORECAST_PERMISSION_MESSAGE);

        String dimension = normalizeDimension(dto == null ? null : dto.getDimension());
        Long orgId = resolveTargetOrgId(dto == null ? null : dto.getOrgId(), true);
        Long tenantId = resolveTenantId();
        LocalDate basisDate = LocalDate.now();
        int forecastDays = resolveForecastDays(dimension);
        LocalDate outputEndDate = basisDate.plusDays(forecastDays - 1L);
        LocalDate recipeEndDate = basisDate.plusDays(RECIPE_LOOKAHEAD_DAYS - 1L);
        LocalDate historyStartDate = basisDate.minusDays(HISTORY_DAYS - 1L);

        BigDecimal calendarFactor = resolveCalendarFactor(dimension, basisDate, outputEndDate);
        BigDecimal holidayFactor = resolveHolidayFactor(basisDate, outputEndDate);
        ActivityContext activityContext = resolveActivityContext(orgId, basisDate, outputEndDate);
        BigDecimal activityFactor = activityContext.factor();
        refreshSupplierDeliveryStats(orgId, tenantId);
        refreshDueForecastEvaluations(orgId, tenantId);
        optimizeModelConfigs(orgId, tenantId);
        ForecastPreparationSnapshot snapshot = prepareForecastSnapshot(
                orgId,
                tenantId,
                basisDate,
                historyStartDate,
                recipeEndDate,
                forecastDays,
                calendarFactor,
                holidayFactor,
                activityFactor
        );
        Map<String, MaterialForecastModelConfig> segmentModelConfigMap =
                ensureActiveSegmentModelConfigs(orgId, tenantId, snapshot.featureMetrics(), snapshot.materialProfiles());
        Map<Long, SupplierRecommendation> supplierRecommendationMap =
                buildSupplierRecommendationMap(orgId, tenantId, snapshot.materialIds());
        Map<Long, PreviousForecastSnapshot> previousForecastSnapshotMap =
                buildPreviousForecastSnapshotMap(orgId, tenantId, snapshot.materialIds(), basisDate);
        Map<Long, InventoryAuditSnapshot> inventoryAuditSnapshotMap =
                buildInventoryAuditSnapshotMap(orgId, tenantId, snapshot.materialIds());

        PurchaseDemandForecast forecast = new PurchaseDemandForecast();
        forecast.setForecastNo(generateUniqueForecastNo());
        forecast.setForecastName(DIMENSION_DAILY.equals(dimension) ? "采购需求预测（日维度）" : "采购需求预测（周维度）");
        forecast.setForecastDimension(dimension);
        forecast.setForecastDays(forecastDays);
        forecast.setBasisDate(basisDate);
        forecast.setHorizonStartDate(basisDate);
        forecast.setHorizonEndDate(outputEndDate);
        forecast.setCalendarFactor(scaleFactor(calendarFactor));
        forecast.setHolidayFactor(scaleFactor(holidayFactor));
        forecast.setActivityFactor(scaleFactor(activityFactor));
        forecast.setOrgId(orgId);
        forecast.setTenantId(tenantId);
        forecast.setEvaluationStatus(EVALUATION_STATUS_PENDING);
        forecast.setSummaryBasis(buildSummaryBasis(calendarFactor, holidayFactor, activityFactor));
        purchaseDemandForecastMapper.insert(forecast);

        PurchaseDemandForecastTask task = new PurchaseDemandForecastTask();
        task.setForecastNo(forecast.getForecastNo());
        task.setOrgId(orgId);
        task.setTenantId(tenantId);
        task.setHorizonType(dimension);
        task.setHorizonStartDate(basisDate);
        task.setHorizonEndDate(outputEndDate);
        task.setTaskStatus("success");
        task.setMaterialCount(0);
        task.setTriggerType("manual");
        task.setEvaluationStatus(EVALUATION_STATUS_PENDING);
        purchaseDemandForecastTaskMapper.insert(task);

        List<PurchaseDemandForecastItem> items = new ArrayList<>();
        List<PurchaseDemandForecastTaskItem> taskItems = new ArrayList<>();
        int sortOrder = 1;
        for (Long materialId : snapshot.materialIds()) {
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            HistoricalMetric planMetric = snapshot.planMetrics().getOrDefault(materialId, HistoricalMetric.empty());
            HistoricalMetric orderMetric = snapshot.orderMetrics().getOrDefault(materialId, HistoricalMetric.empty());
            InventoryMetric inventoryMetric = snapshot.inventoryMetrics().getOrDefault(materialId, InventoryMetric.empty());
            MaterialProfile profile = resolveMaterialProfile(
                    materialId,
                    snapshot.materialProfiles(),
                    planMetric,
                    orderMetric,
                    inventoryMetric,
                    snapshot.recipeMetrics().getOrDefault(materialId, RecipeMetric.empty())
            );
            FeatureMetric featureMetric = snapshot.featureMetrics().getOrDefault(materialId, FeatureMetric.empty());
            MaterialForecastModelConfig modelConfig = resolveSegmentModelConfig(
                    orgId,
                    tenantId,
                    profile,
                    featureMetric,
                    segmentModelConfigMap
            );
            BigDecimal volatileMlForecastQty = resolveVolatileMlForecastQty(
                    basisDate,
                    forecastDays,
                    featureMetric,
                    snapshot.consumptionByMaterial().get(materialId)
            );
            PurchaseDemandForecastRuleEngine.MaterialRuleContext context = buildMaterialRuleContext(
                    materialId,
                    profile,
                    featureMetric,
                    forecastDays,
                    calendarFactor,
                    holidayFactor,
                    activityFactor,
                    modelConfig,
                    volatileMlForecastQty
            );
            BigDecimal historicalPlanDaily = divide(planMetric.totalQty(), BigDecimal.valueOf(HISTORY_DAYS), 3);
            BigDecimal historicalOrderDaily = divide(orderMetric.totalQty(), BigDecimal.valueOf(HISTORY_DAYS), 3);
            BigDecimal estimatedUnitPrice = resolveEstimatedUnitPrice(
                    orderMetric.avgPrice(),
                    planMetric.avgPrice(),
                    inventoryMetric.referenceUnitCost(),
                    snapshot.fallbackPrices().get(materialId)
            );
            SupplierRecommendation supplierRecommendation =
                    supplierRecommendationMap.getOrDefault(materialId, SupplierRecommendation.empty(materialId));
            PurchaseDemandForecastRuleEngine.ModelParameters modelParameters =
                    overrideLeadTimeParameter(toModelParameters(modelConfig, context), supplierRecommendation);
            PurchaseDemandForecastRuleEngine.RuleResult ruleResult =
                    purchaseDemandForecastRuleEngine.evaluate(context, modelParameters);
            PhaseThreeDecision phaseThreeDecision = buildPhaseThreeDecision(
                    profile,
                    featureMetric,
                    ruleResult,
                    modelParameters,
                    supplierRecommendation,
                    estimatedUnitPrice,
                    forecastDays
            );
            ExplanationSnapshot explanationSnapshot = enrichExplanationSnapshot(
                    buildExplanationSnapshot(profile, featureMetric, ruleResult, modelParameters),
                    phaseThreeDecision
            );
            PurchaseDemandForecastExplanationEngine.ExplanationResult explanationResult =
                    purchaseDemandForecastExplanationEngine.explain(buildExplanationContext(
                            profile,
                            featureMetric,
                            ruleResult,
                            phaseThreeDecision,
                            explanationSnapshot,
                            previousForecastSnapshotMap.get(materialId),
                            inventoryAuditSnapshotMap.get(materialId),
                            activityContext,
                            forecastDays
                    ));
            BigDecimal displayInventoryQty = scaleQuantity(featureMetric.availableStockQty());
            BigDecimal displaySuggestedQty = normalizeSuggestedQtyByUnit(profile.materialUnit(), phaseThreeDecision.finalSuggestedQty());
            BigDecimal displayForecastDemand = scaleQuantity(ruleResult.forecastDemandQty());
            BigDecimal displaySafetyStock = scaleQuantity(ruleResult.safetyStockQty());
            BigDecimal estimatedAmount = scaleAmount(displaySuggestedQty.multiply(estimatedUnitPrice == null ? ZERO_AMOUNT : estimatedUnitPrice));
            ConfidenceRange decisionRange = buildPhaseThreeConfidenceRange(
                    profile.materialUnit(),
                    ruleResult,
                    phaseThreeDecision.finalSuggestedQty()
            );
            BigDecimal lowerQty = decisionRange.lower();
            BigDecimal upperQty = decisionRange.upper();

            if (shouldSkipMaterial(
                    profile,
                    displayInventoryQty,
                    displayForecastDemand,
                    displaySafetyStock,
                    displaySuggestedQty
            )) {
                continue;
            }
            String replenishmentPriority = normalizePriorityLabel(ruleResult.priorityLevel());

            PurchaseDemandForecastItem item = new PurchaseDemandForecastItem();
            item.setForecastId(forecast.getId());
            item.setMaterialId(materialId);
            item.setMaterialName(profile.materialName());
            item.setMaterialSpec(profile.materialSpec());
            item.setMaterialUnit(profile.materialUnit());
            item.setCurrentInventoryQty(displayInventoryQty);
            item.setHistoricalPlanAvgQty(scaleQuantity(historicalPlanDaily));
            item.setHistoricalOrderAvgQty(scaleQuantity(historicalOrderDaily));
            item.setRecipeDemandQty(scaleQuantity(featureMetric.recipeDemand7d()));
            item.setForecastDemandQty(displayForecastDemand);
            item.setSafetyStockQty(displaySafetyStock);
            item.setAvgDailyDemandQty(scaleQuantity(phaseThreeDecision.avgDailyDemandQty()));
            item.setReviewPeriodDays(phaseThreeDecision.reviewPeriodDays());
            item.setReorderPointQty(scaleQuantity(phaseThreeDecision.reorderPointQty()));
            item.setTargetStockQty(scaleQuantity(phaseThreeDecision.targetStockQty()));
            item.setInventoryPositionQty(scaleQuantity(phaseThreeDecision.inventoryPositionQty()));
            item.setTheoreticalSuggestedQty(scaleQuantity(phaseThreeDecision.theoreticalSuggestedQty()));
            item.setSuggestedQty(displaySuggestedQty);
            item.setConfidenceLowerQty(scaleQuantity(lowerQty));
            item.setConfidenceUpperQty(scaleQuantity(upperQty));
            item.setEstimatedUnitPrice(scaleAmount(estimatedUnitPrice));
            item.setEstimatedAmount(estimatedAmount);
            item.setReplenishmentPriority(replenishmentPriority);
            item.setModelType(ruleResult.modelType());
            item.setMaterialSegment(ruleResult.materialSegment());
            item.setForecastBasis(ruleResult.forecastBasis());
            item.setExplanationSummary(explanationResult.summary());
            item.setExplanationDetail(explanationResult.detail());
            item.setExplanationTemplateCode(explanationResult.templateCode());
            item.setExplanationTitle(explanationResult.explanationTitle());
            item.setWarningLevel(explanationResult.warningLevel());
            item.setWarningMessage(explanationResult.warningMessage());
            item.setApprovalNote(explanationResult.approvalNote());
            item.setManualReviewRequired(explanationResult.manualReviewRequired() ? 1 : 0);
            item.setExplanationFactorsJson(toJson(explanationResult.evidenceItems()));
            item.setAnomalyCodes(explanationResult.anomalyCodes());
            item.setExplanationSortScore(scaleRate(explanationResult.sortScore()));
            item.setAnomalyFlags(explanationResult.anomalyFlags());
            item.setRecipeDriveRatio(scaleRate(featureMetric.recipeDriveRatio()));
            item.setDemandActiveRatio(scaleRate(featureMetric.demandActiveRatio()));
            item.setDemandCv(scaleRate(featureMetric.demandCv()));
            item.setActivitySensitivity(scaleRate(featureMetric.activitySensitivity()));
            item.setServiceLevel(scaleRate(ruleResult.serviceLevel()));
            item.setLeadTimeDays(ruleResult.leadTimeDays().intValue());
            item.setEffectiveLeadTimeDays(scaleRate(phaseThreeDecision.effectiveLeadTimeDays()));
            item.setMinOrderQty(scaleQuantity(phaseThreeDecision.minOrderQty()));
            item.setPackSize(scaleQuantity(phaseThreeDecision.packSize()));
            item.setMaxAllowedStockQty(scaleQuantity(phaseThreeDecision.maxAllowedStockQty()));
            item.setMaxCoverageDays(phaseThreeDecision.maxCoverageDays());
            item.setRecommendedSupplierId(phaseThreeDecision.supplierId());
            item.setRecommendedSupplierName(phaseThreeDecision.supplierName());
            item.setSupplierScore(scaleRate(phaseThreeDecision.supplierScore()));
            item.setSupplierFillRate(scaleRate(phaseThreeDecision.supplierFillRate()));
            item.setSupplierOnTimeRate(scaleRate(phaseThreeDecision.supplierOnTimeRate()));
            item.setOrderNow(phaseThreeDecision.orderNow() ? 1 : 0);
            item.setOrderAction(phaseThreeDecision.orderAction());
            item.setShortageCost(scaleAmount(phaseThreeDecision.shortageCost()));
            item.setHoldingCost(scaleAmount(phaseThreeDecision.holdingCost()));
            item.setExpiryRiskCost(scaleAmount(phaseThreeDecision.expiryRiskCost()));
            item.setOrderProcessingCost(scaleAmount(phaseThreeDecision.orderProcessingCost()));
            item.setPurchasePriceCost(scaleAmount(phaseThreeDecision.purchasePriceCost()));
            item.setTotalCost(scaleAmount(phaseThreeDecision.totalCost()));
            item.setPhaseThreeRiskFlags(phaseThreeDecision.riskFlags());
            item.setEvaluationStatus(EVALUATION_STATUS_PENDING);
            item.setSortOrder(sortOrder++);
            item.setOrgId(orgId);
            item.setTenantId(tenantId);
            items.add(item);

            PurchaseDemandForecastTaskItem taskItem = new PurchaseDemandForecastTaskItem();
            taskItem.setTaskId(task.getId());
            taskItem.setMaterialId(materialId);
            taskItem.setMaterialName(profile.materialName());
            taskItem.setMaterialUnit(profile.materialUnit());
            taskItem.setMaterialCategory(profile.materialCategory());
            taskItem.setMaterialSegment(ruleResult.materialSegment());
            taskItem.setForecastDemandQty(displayForecastDemand);
            taskItem.setSafetyStockQty(displaySafetyStock);
            taskItem.setAvgDailyDemandQty(scaleQuantity(phaseThreeDecision.avgDailyDemandQty()));
            taskItem.setReviewPeriodDays(phaseThreeDecision.reviewPeriodDays());
            taskItem.setReorderPointQty(scaleQuantity(phaseThreeDecision.reorderPointQty()));
            taskItem.setTargetStockQty(scaleQuantity(phaseThreeDecision.targetStockQty()));
            taskItem.setCurrentStockQty(scaleQuantity(featureMetric.currentStockQty()));
            taskItem.setAvailableStockQty(displayInventoryQty);
            taskItem.setInventoryPositionQty(scaleQuantity(phaseThreeDecision.inventoryPositionQty()));
            taskItem.setInTransitQty(scaleQuantity(featureMetric.inTransitQty()));
            taskItem.setPendingExecQty(scaleQuantity(featureMetric.pendingPlanQty()));
            taskItem.setTheoreticalSuggestedQty(scaleQuantity(phaseThreeDecision.theoreticalSuggestedQty()));
            taskItem.setSuggestedPurchaseQty(displaySuggestedQty);
            taskItem.setLowerBoundQty(scaleQuantity(lowerQty));
            taskItem.setUpperBoundQty(scaleQuantity(upperQty));
            taskItem.setConfidenceLevel(ruleResult.confidenceLevel());
            taskItem.setForecastBasis(ruleResult.forecastBasis());
            taskItem.setPriorityLevel(replenishmentPriority);
            taskItem.setModelType(ruleResult.modelType());
            taskItem.setFeatureSnapshotJson(buildFeatureSnapshotJson(context, featureMetric));
            taskItem.setModelParamJson(toJson(modelParameters));
            taskItem.setExplanationSummary(explanationResult.summary());
            taskItem.setExplanationDetail(explanationResult.detail());
            taskItem.setExplanationTemplateCode(explanationResult.templateCode());
            taskItem.setExplanationTitle(explanationResult.explanationTitle());
            taskItem.setWarningLevel(explanationResult.warningLevel());
            taskItem.setWarningMessage(explanationResult.warningMessage());
            taskItem.setApprovalNote(explanationResult.approvalNote());
            taskItem.setManualReviewRequired(explanationResult.manualReviewRequired() ? 1 : 0);
            taskItem.setExplanationFactorsJson(toJson(explanationResult.evidenceItems()));
            taskItem.setAnomalyCodes(explanationResult.anomalyCodes());
            taskItem.setExplanationSortScore(scaleRate(explanationResult.sortScore()));
            taskItem.setEstimatedUnitPrice(scaleAmount(estimatedUnitPrice));
            taskItem.setAnomalyFlags(explanationResult.anomalyFlags());
            taskItem.setRecipeDriveRatio(scaleRate(featureMetric.recipeDriveRatio()));
            taskItem.setDemandActiveRatio(scaleRate(featureMetric.demandActiveRatio()));
            taskItem.setDemandCv(scaleRate(featureMetric.demandCv()));
            taskItem.setActivitySensitivity(scaleRate(featureMetric.activitySensitivity()));
            taskItem.setLeadTimeDays(ruleResult.leadTimeDays().intValue());
            taskItem.setServiceLevel(scaleRate(ruleResult.serviceLevel()));
            taskItem.setEffectiveLeadTimeDays(scaleRate(phaseThreeDecision.effectiveLeadTimeDays()));
            taskItem.setMinOrderQty(scaleQuantity(phaseThreeDecision.minOrderQty()));
            taskItem.setPackSize(scaleQuantity(phaseThreeDecision.packSize()));
            taskItem.setMaxAllowedStockQty(scaleQuantity(phaseThreeDecision.maxAllowedStockQty()));
            taskItem.setMaxCoverageDays(phaseThreeDecision.maxCoverageDays());
            taskItem.setRecommendedSupplierId(phaseThreeDecision.supplierId());
            taskItem.setRecommendedSupplierName(phaseThreeDecision.supplierName());
            taskItem.setSupplierScore(scaleRate(phaseThreeDecision.supplierScore()));
            taskItem.setSupplierFillRate(scaleRate(phaseThreeDecision.supplierFillRate()));
            taskItem.setSupplierOnTimeRate(scaleRate(phaseThreeDecision.supplierOnTimeRate()));
            taskItem.setOrderNow(phaseThreeDecision.orderNow() ? 1 : 0);
            taskItem.setOrderAction(phaseThreeDecision.orderAction());
            taskItem.setShortageCost(scaleAmount(phaseThreeDecision.shortageCost()));
            taskItem.setHoldingCost(scaleAmount(phaseThreeDecision.holdingCost()));
            taskItem.setExpiryRiskCost(scaleAmount(phaseThreeDecision.expiryRiskCost()));
            taskItem.setOrderProcessingCost(scaleAmount(phaseThreeDecision.orderProcessingCost()));
            taskItem.setPurchasePriceCost(scaleAmount(phaseThreeDecision.purchasePriceCost()));
            taskItem.setTotalCost(scaleAmount(phaseThreeDecision.totalCost()));
            taskItem.setPhaseThreeRiskFlags(phaseThreeDecision.riskFlags());
            taskItem.setOptimizationVersion(modelParameters.optimizationVersion() == null ? 0 : modelParameters.optimizationVersion());
            taskItem.setOptimizationScore(scaleRate(modelParameters.optimizationScore()));
            taskItem.setEvaluationStatus(EVALUATION_STATUS_PENDING);
            taskItems.add(taskItem);
        }

        items.sort(Comparator
                .comparing(PurchaseDemandForecastItem::getEstimatedAmount, Comparator.nullsLast(BigDecimal::compareTo))
                .reversed()
                .thenComparing(PurchaseDemandForecastItem::getSuggestedQty, Comparator.nullsLast(BigDecimal::compareTo))
                .reversed()
                .thenComparing(PurchaseDemandForecastItem::getMaterialName, Comparator.nullsLast(String::compareTo)));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setSortOrder(i + 1);
            purchaseDemandForecastItemMapper.insert(items.get(i));
        }
        for (PurchaseDemandForecastTaskItem taskItem : taskItems) {
            purchaseDemandForecastTaskItemMapper.insert(taskItem);
        }

        forecast.setMaterialCount(items.size());
        forecast.setTotalSuggestedAmount(scaleAmount(items.stream()
                .map(PurchaseDemandForecastItem::getEstimatedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        forecast.setOptimizationVersion(taskItems.stream()
                .map(PurchaseDemandForecastTaskItem::getOptimizationVersion)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0));
        forecast.setOptimizationScore(scaleRate(taskItems.stream()
                .map(PurchaseDemandForecastTaskItem::getOptimizationScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, taskItems.size())), 6, RoundingMode.HALF_UP)));
        forecast.setReorderTriggeredCount((int) items.stream().filter(item -> Objects.equals(item.getOrderNow(), 1)).count());
        forecast.setRiskItemCount((int) items.stream().filter(item -> StrUtil.isNotBlank(item.getPhaseThreeRiskFlags())).count());
        forecast.setSupplierRecommendedCount((int) items.stream().filter(item -> item.getRecommendedSupplierId() != null).count());
        forecast.setManualReviewCount((int) items.stream().filter(item -> Objects.equals(item.getManualReviewRequired(), 1)).count());
        forecast.setWarningItemCount((int) items.stream()
                .filter(item -> StrUtil.isNotBlank(item.getWarningLevel()))
                .filter(item -> !StrUtil.equalsIgnoreCase(item.getWarningLevel(), PurchaseDemandForecastExplanationEngine.WARNING_LEVEL_LOW))
                .count());
        forecast.setTotalOptimizationCost(scaleAmount(items.stream()
                .map(PurchaseDemandForecastItem::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        forecast.setExplanationSummary(buildForecastExplanationSummary(items));
        forecast.setApprovalSummary(buildForecastApprovalSummary(items));
        purchaseDemandForecastMapper.updateById(forecast);
        task.setMaterialCount(taskItems.size());
        task.setOptimizationVersion(taskItems.stream()
                .map(PurchaseDemandForecastTaskItem::getOptimizationVersion)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0));
        task.setOptimizationScore(scaleRate(taskItems.stream()
                .map(PurchaseDemandForecastTaskItem::getOptimizationScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, taskItems.size())), 6, RoundingMode.HALF_UP)));
        purchaseDemandForecastTaskMapper.updateById(task);

        PurchaseDemandForecastVO result = buildForecastVO(forecast, items);
        String afterData = toJson(Map.of(
                "forecastNo", forecast.getForecastNo(),
                "orgId", forecast.getOrgId(),
                "dimension", forecast.getForecastDimension(),
                "forecastDays", forecast.getForecastDays(),
                "materialCount", forecast.getMaterialCount(),
                "totalSuggestedAmount", forecast.getTotalSuggestedAmount(),
                "taskId", task.getId()
        ));
        auditLogService.log(
                AuditModule.SCM_PURCHASE_DEMAND_FORECAST,
                AuditOperationType.CREATE,
                forecast.getId(),
                forecast.getForecastNo(),
                "执行采购需求预测",
                null,
                afterData
        );
        log.info("采购需求预测完成: forecastId={}, forecastNo={}, orgId={}, itemCount={}",
                forecast.getId(), forecast.getForecastNo(), orgId, items.size());
        return result;
    }

    @Override
    @DataScope
    public PageResult<PurchaseDemandForecastVO> list(PurchaseDemandForecastQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String keyword = normalizeOptionalText(query.getKeyword());
        String dimension = normalizeOptionalText(query.getDimension());
        Long tenantId = resolveTenantId();

        StringBuilder whereSql = new StringBuilder(" WHERE f.deleted = 0 AND f.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (query.getOrgId() != null) {
            ensureOrgAllowed(query.getOrgId());
            whereSql.append(" AND f.org_id = ?");
            args.add(query.getOrgId());
        } else if (query.getOrgIds() != null) {
            List<Long> orgIds = normalizeRequestedOrgIds(query.getOrgIds());
            if (orgIds.isEmpty()) {
                return PageResult.empty((long) pageNum, (long) pageSize);
            }
            whereSql.append(" AND f.org_id IN (").append(placeholders(orgIds.size())).append(")");
            args.addAll(orgIds);
        } else {
            List<Long> allowedOrgIds = resolveAllowedOrgIds();
            if (allowedOrgIds != null) {
                if (allowedOrgIds.isEmpty()) {
                    return PageResult.empty((long) pageNum, (long) pageSize);
                }
                whereSql.append(" AND f.org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
                args.addAll(allowedOrgIds);
            }
        }

        if (StrUtil.isNotBlank(keyword)) {
            whereSql.append(" AND (f.forecast_no LIKE ? OR f.forecast_name LIKE ?)");
            args.add(like(keyword));
            args.add(like(keyword));
        }
        if (StrUtil.isNotBlank(dimension)) {
            whereSql.append(" AND f.forecast_dimension = ?");
            args.add(dimension);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_demand_forecast f" + whereSql,
                Long.class,
                args.toArray()
        );
        if (total == null || total == 0L) {
            return PageResult.empty((long) pageNum, (long) pageSize);
        }

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT f.id, f.forecast_no AS forecastNo, f.forecast_name AS forecastName, f.org_id AS orgId, " +
                        "so.org_name AS orgName, f.forecast_dimension AS dimension, f.forecast_days AS forecastDays, " +
                        "f.basis_date AS basisDate, f.horizon_start_date AS horizonStartDate, f.horizon_end_date AS horizonEndDate, " +
                        "f.material_count AS materialCount, f.total_suggested_amount AS totalSuggestedAmount, " +
                        "f.calendar_factor AS calendarFactor, f.holiday_factor AS holidayFactor, f.activity_factor AS activityFactor, " +
                        "f.summary_basis AS summaryBasis, f.evaluation_status AS evaluationStatus, f.evaluated_at AS evaluatedAt, " +
                        "f.overall_wape AS wape, f.overall_mape AS mape, f.bias_rate AS biasRate, f.stockout_rate AS stockoutRate, " +
                        "f.oversupply_rate AS oversupplyRate, f.optimization_version AS optimizationVersion, " +
                        "f.optimization_score AS optimizationScore, f.explanation_summary AS explanationSummary, " +
                        "f.approval_summary AS approvalSummary, " +
                        "f.reorder_triggered_count AS reorderTriggeredCount, f.risk_item_count AS riskItemCount, " +
                        "f.supplier_recommended_count AS supplierRecommendedCount, f.manual_review_count AS manualReviewCount, " +
                        "f.warning_item_count AS warningItemCount, f.total_optimization_cost AS totalOptimizationCost, " +
                        "f.created_by AS createdBy, f.created_at AS createdAt " +
                        "FROM scm_purchase_demand_forecast f " +
                        "LEFT JOIN sys_organization so ON so.id = f.org_id AND so.deleted = 0 " +
                        whereSql +
                        " ORDER BY f.created_at DESC, f.id DESC LIMIT ? OFFSET ?",
                listArgs.toArray()
        );

        Map<Long, String> operatorNames = resolveOperatorNames(rows.stream()
                .map(row -> toLong(row.get("createdBy")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot> linkageSnapshotMap =
                purchaseDemandForecastLinkageSupport.loadByForecastIds(
                        rows.stream()
                                .map(row -> toLong(row.get("id")))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new)),
                        tenantId,
                        null
                );

        List<PurchaseDemandForecastVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long forecastId = toLong(row.get("id"));
            PurchaseDemandForecastVO vo = new PurchaseDemandForecastVO();
            vo.setId(forecastId);
            vo.setForecastNo(asString(row.get("forecastNo")));
            vo.setForecastName(asString(row.get("forecastName")));
            vo.setOrgId(toLong(row.get("orgId")));
            vo.setOrgName(asString(row.get("orgName")));
            vo.setDimension(asString(row.get("dimension")));
            vo.setForecastDays(toInteger(row.get("forecastDays")));
            vo.setBasisDate(formatDate(row.get("basisDate")));
            vo.setHorizonStartDate(formatDate(row.get("horizonStartDate")));
            vo.setHorizonEndDate(formatDate(row.get("horizonEndDate")));
            vo.setMaterialCount(toInteger(row.get("materialCount")));
            vo.setTotalSuggestedAmount(scaleAmount(toBigDecimal(row.get("totalSuggestedAmount"))));
            vo.setCalendarFactor(scaleFactor(toBigDecimal(row.get("calendarFactor"))));
            vo.setHolidayFactor(scaleFactor(toBigDecimal(row.get("holidayFactor"))));
            vo.setActivityFactor(scaleFactor(toBigDecimal(row.get("activityFactor"))));
            vo.setSummaryBasis(asString(row.get("summaryBasis")));
            vo.setGeneratedBy(operatorNames.getOrDefault(toLong(row.get("createdBy")), resolveOperatorFallback(toLong(row.get("createdBy")))));
            vo.setGeneratedAt(formatDateTime(row.get("createdAt")));
            vo.setEvaluationStatus(asString(row.get("evaluationStatus")));
            vo.setEvaluatedAt(formatDateTime(row.get("evaluatedAt")));
            vo.setWape(scaleRate(toBigDecimal(row.get("wape"))));
            vo.setMape(scaleRate(toBigDecimal(row.get("mape"))));
            vo.setBiasRate(scaleRate(toBigDecimal(row.get("biasRate"))));
            vo.setStockoutRate(scaleRate(toBigDecimal(row.get("stockoutRate"))));
            vo.setOversupplyRate(scaleRate(toBigDecimal(row.get("oversupplyRate"))));
            vo.setOptimizationVersion(toInteger(row.get("optimizationVersion")));
            vo.setOptimizationScore(scaleRate(toBigDecimal(row.get("optimizationScore"))));
            vo.setExplanationSummary(asString(row.get("explanationSummary")));
            vo.setApprovalSummary(asString(row.get("approvalSummary")));
            vo.setReorderTriggeredCount(toInteger(row.get("reorderTriggeredCount")));
            vo.setRiskItemCount(toInteger(row.get("riskItemCount")));
            vo.setSupplierRecommendedCount(toInteger(row.get("supplierRecommendedCount")));
            vo.setManualReviewCount(toInteger(row.get("manualReviewCount")));
            vo.setWarningItemCount(toInteger(row.get("warningItemCount")));
            vo.setTotalOptimizationCost(scaleAmount(toBigDecimal(row.get("totalOptimizationCost"))));
            vo.setMaterialPlanStatus(linkageSnapshotMap.get(forecastId) == null
                    ? PurchaseDemandForecastLinkageSupport.STATUS_UNUSED
                    : linkageSnapshotMap.get(forecastId).getMaterialPlanStatus());
            list.add(vo);
        }
        return PageResult.of(list, (long) pageNum, (long) pageSize, total);
    }

    @Override
    public PurchaseDemandForecastVO getDetail(Long id) {
        PurchaseDemandForecast forecast = getForecastById(id);
        ensureOrgAllowed(forecast.getOrgId());

        List<PurchaseDemandForecastItem> items = purchaseDemandForecastItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseDemandForecastItem>()
                        .eq(PurchaseDemandForecastItem::getForecastId, id)
                        .eq(PurchaseDemandForecastItem::getTenantId, resolveTenantId())
                        .orderByAsc(PurchaseDemandForecastItem::getSortOrder, PurchaseDemandForecastItem::getId)
        );
        return buildForecastVO(forecast, items);
    }

    @Override
    public List<PurchaseDemandForecastLinkedPlanVO> getLinkedPlans(Long id) {
        PurchaseDemandForecast forecast = getForecastById(id);
        ensureOrgAllowed(forecast.getOrgId());

        String forecastNo = StrUtil.trimToEmpty(forecast.getForecastNo());
        if (StrUtil.isBlank(forecastNo)) {
            return Collections.emptyList();
        }

        Long tenantId = resolveTenantId();
        List<Map<String, Object>> planRows = jdbcTemplate.queryForList(
                "SELECT p.id, p.plan_no AS planNo, p.plan_name AS planName, p.status, p.plan_date AS planDate, " +
                        "p.total_amount AS totalAmount, p.created_by AS createdBy, p.created_at AS createdAt " +
                        "FROM scm_purchase_plan p " +
                        "WHERE p.deleted = 0 AND p.tenant_id = ? AND p.org_id = ? AND p.related_document = ? " +
                        "ORDER BY p.created_at DESC, p.id DESC",
                tenantId,
                forecast.getOrgId(),
                forecastNo
        );
        if (planRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> planIds = planRows.stream()
                .map(row -> toLong(row.get("id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, String> operatorNames = resolveOperatorNames(planRows.stream()
                .map(row -> toLong(row.get("createdBy")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        Map<Long, List<PurchaseDemandForecastLinkedPlanVO.Item>> itemMap = new LinkedHashMap<>();
        if (!planIds.isEmpty()) {
            List<Object> itemArgs = new ArrayList<>();
            itemArgs.addAll(planIds);
            List<Map<String, Object>> itemRows = jdbcTemplate.queryForList(
                    "SELECT i.id, i.plan_id AS planId, i.material_id AS materialId, i.material_name AS materialName, " +
                            "i.material_spec AS materialSpec, i.material_unit AS unit, i.plan_qty AS quantity, " +
                            "i.estimate_price AS estimatedUnitPrice, i.estimate_amount AS estimatedAmount, i.remark " +
                            "FROM scm_purchase_plan_item i " +
                            "WHERE i.plan_id IN (" + placeholders(planIds.size()) + ") " +
                            "ORDER BY i.plan_id ASC, i.id ASC",
                    itemArgs.toArray()
            );
            for (Map<String, Object> row : itemRows) {
                Long planId = toLong(row.get("planId"));
                if (planId == null) {
                    continue;
                }
                PurchaseDemandForecastLinkedPlanVO.Item item = new PurchaseDemandForecastLinkedPlanVO.Item();
                item.setId(toLong(row.get("id")));
                item.setMaterialId(toLong(row.get("materialId")));
                item.setMaterialName(asString(row.get("materialName")));
                item.setMaterialSpec(asString(row.get("materialSpec")));
                item.setUnit(asString(row.get("unit")));
                item.setQuantity(scaleQuantity(toBigDecimal(row.get("quantity"))));
                item.setEstimatedUnitPrice(scaleAmount(toBigDecimal(row.get("estimatedUnitPrice"))));
                item.setEstimatedAmount(scaleAmount(toBigDecimal(row.get("estimatedAmount"))));
                item.setRemark(asString(row.get("remark")));
                itemMap.computeIfAbsent(planId, key -> new ArrayList<>()).add(item);
            }
        }

        List<PurchaseDemandForecastLinkedPlanVO> result = new ArrayList<>();
        for (Map<String, Object> row : planRows) {
            Long planId = toLong(row.get("id"));
            Long createdById = toLong(row.get("createdBy"));

            PurchaseDemandForecastLinkedPlanVO vo = new PurchaseDemandForecastLinkedPlanVO();
            vo.setId(planId);
            vo.setPlanNo(asString(row.get("planNo")));
            vo.setPlanName(asString(row.get("planName")));
            vo.setStatus(asString(row.get("status")));
            vo.setPlanDate(formatDate(row.get("planDate")));
            vo.setTotalAmount(scaleAmount(toBigDecimal(row.get("totalAmount"))));
            vo.setCreatedBy(operatorNames.getOrDefault(createdById, resolveOperatorFallback(createdById)));
            vo.setCreatedAt(formatDateTime(row.get("createdAt")));
            vo.setItems(new ArrayList<>(itemMap.getOrDefault(planId, Collections.emptyList())));
            result.add(vo);
        }
        return result;
    }

    @Override
    public PurchaseDemandForecastDashboardVO getDashboard(Long requestedOrgId) {
        Long orgId = resolveTargetOrgId(requestedOrgId, false);
        ensureOrgAllowed(orgId);
        return buildDashboard(orgId, resolveTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseDemandForecastDashboardVO refreshAnalytics(Long requestedOrgId) {
        Long tenantId = resolveTenantId();
        if (requestedOrgId == null && UserContext.getUserId() == null && UserContext.getOrgId() == null) {
            List<Long> orgIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT org_id FROM scm_purchase_demand_forecast WHERE deleted = 0 AND tenant_id = ?",
                    Long.class,
                    tenantId
            );
            PurchaseDemandForecastDashboardVO dashboard = new PurchaseDemandForecastDashboardVO();
            for (Long orgId : orgIds) {
                refreshSupplierDeliveryStats(orgId, tenantId);
                refreshDueForecastEvaluations(orgId, tenantId);
                optimizeModelConfigs(orgId, tenantId);
            }
            if (!orgIds.isEmpty()) {
                dashboard = buildDashboard(orgIds.get(0), tenantId);
            }
            return dashboard;
        }
        Long orgId = resolveTargetOrgId(requestedOrgId, true);
        refreshSupplierDeliveryStats(orgId, tenantId);
        refreshDueForecastEvaluations(orgId, tenantId);
        optimizeModelConfigs(orgId, tenantId);
        PurchaseDemandForecastDashboardVO dashboard = buildDashboard(orgId, tenantId);
        auditLogService.log(
                AuditModule.SCM_PURCHASE_DEMAND_FORECAST,
                AuditOperationType.UPDATE,
                orgId,
                String.valueOf(orgId),
                "刷新采购需求预测二期分析数据",
                null,
                toJson(dashboard)
        );
        return dashboard;
    }

    @Override
    public PurchaseDemandForecastPlanPrefillVO buildPurchasePlanPrefill(PurchaseDemandForecastPrefillDTO dto) {
        ensurePermission(PURCHASE_PLAN_CREATE_PERMISSION, PREFILL_PERMISSION_MESSAGE);

        List<Long> detailIds = normalizeDetailIds(dto == null ? null : dto.getDetailIds());
        if (detailIds.isEmpty()) {
            throw BizException.badRequest("请至少选择一条预测明细");
        }
        Long tenantId = resolveTenantId();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(tenantId);
        args.addAll(detailIds);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT fi.id, fi.forecast_id AS forecastId, f.forecast_no AS forecastNo, f.org_id AS orgId, " +
                        "so.org_name AS orgName, fi.material_id AS materialId, fi.material_name AS materialName, " +
                        "fi.material_spec AS materialSpec, fi.material_unit AS materialUnit, fi.suggested_qty AS suggestedQty, " +
                        "fi.estimated_unit_price AS estimatedUnitPrice, fi.estimated_amount AS estimatedAmount, fi.sort_order AS sortOrder " +
                        "FROM scm_purchase_demand_forecast_item fi " +
                        "JOIN scm_purchase_demand_forecast f ON f.id = fi.forecast_id AND f.deleted = 0 AND f.tenant_id = ? " +
                        "LEFT JOIN sys_organization so ON so.id = f.org_id AND so.deleted = 0 " +
                        "WHERE fi.deleted = 0 AND fi.tenant_id = ? AND fi.id IN (" + placeholders(detailIds.size()) + ") " +
                        "ORDER BY fi.sort_order ASC, fi.id ASC",
                args.toArray()
        );
        if (rows.isEmpty()) {
            throw BizException.badRequest("所选预测明细不存在");
        }

        Long forecastId = null;
        Long orgId = null;
        String forecastNo = null;
        String orgName = null;
        for (Map<String, Object> row : rows) {
            Long currentForecastId = toLong(row.get("forecastId"));
            Long currentOrgId = toLong(row.get("orgId"));
            if (forecastId == null) {
                forecastId = currentForecastId;
                orgId = currentOrgId;
                forecastNo = asString(row.get("forecastNo"));
                orgName = asString(row.get("orgName"));
                continue;
            }
            if (!Objects.equals(forecastId, currentForecastId) || !Objects.equals(orgId, currentOrgId)) {
                throw BizException.badRequest("仅支持同一预测单内的物料批量生成采购计划");
            }
        }
        ensureOrgAllowed(orgId);
        PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot linkageSnapshot =
                purchaseDemandForecastLinkageSupport.loadByForecastNo(forecastNo, tenantId, null, false);
        Map<Long, PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage> linkageItemMap =
                linkageSnapshot == null ? Collections.emptyMap() : linkageSnapshot.toMaterialMap();

        PurchaseDemandForecastPlanPrefillVO vo = new PurchaseDemandForecastPlanPrefillVO();
        vo.setForecastId(forecastId);
        vo.setForecastNo(forecastNo);
        vo.setPlanName("采购需求预测-" + forecastNo);
        vo.setOrgId(orgId);
        vo.setOrgName(orgName);
        vo.setPlanDate(LocalDate.now().format(DATE_FORMATTER));
        vo.setCreatedBy(resolveCurrentUserDisplayName());
        vo.setRelatedDocument(forecastNo);

        BigDecimal budgetAmount = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage linkageItem =
                    materialId == null ? null : linkageItemMap.get(materialId);
            BigDecimal availableQty = linkageItem == null
                    ? scaleQuantity(toBigDecimal(row.get("suggestedQty")))
                    : scaleQuantity(linkageItem.getAvailableQty());
            if (availableQty.compareTo(ZERO_QUANTITY) <= 0) {
                continue;
            }
            PurchaseDemandForecastPlanPrefillVO.Item item = new PurchaseDemandForecastPlanPrefillVO.Item();
            item.setForecastDetailId(toLong(row.get("id")));
            item.setMaterialId(materialId);
            item.setMaterialName(asString(row.get("materialName")));
            item.setMaterialSpec(asString(row.get("materialSpec")));
            item.setUnit(asString(row.get("materialUnit")));
            item.setQuantity(availableQty);
            item.setEstimatedUnitPrice(scaleAmount(toBigDecimal(row.get("estimatedUnitPrice"))));
            item.setEstimatedAmount(scaleAmount(item.getEstimatedUnitPrice().multiply(item.getQuantity())));
            budgetAmount = budgetAmount.add(item.getEstimatedAmount() == null ? BigDecimal.ZERO : item.getEstimatedAmount());
            vo.getItems().add(item);
        }
        if (vo.getItems().isEmpty()) {
            throw BizException.badRequest("所选预测明细可关联数量已全部被占用");
        }
        vo.setBudgetAmount(scaleAmount(budgetAmount));
        return vo;
    }

    @Override
    public PurchaseDemandForecastMaterialLinkageVO getMaterialLinkage(String forecastNo, Long excludePlanId) {
        String normalizedForecastNo = normalizeOptionalText(forecastNo);
        if (normalizedForecastNo == null) {
            return null;
        }

        PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot linkageSnapshot =
                purchaseDemandForecastLinkageSupport.loadByForecastNo(
                        normalizedForecastNo,
                        resolveTenantId(),
                        excludePlanId,
                        false
                );
        if (linkageSnapshot == null) {
            return null;
        }

        ensureOrgAllowed(linkageSnapshot.getOrgId());
        return buildMaterialLinkageVO(linkageSnapshot);
    }

    private PurchaseDemandForecastVO buildForecastVO(PurchaseDemandForecast forecast, List<PurchaseDemandForecastItem> items) {
        PurchaseDemandForecastVO vo = new PurchaseDemandForecastVO();
        List<PurchaseDemandForecastItem> visibleItems = filterVisibleForecastItems(forecast, items, null);
        PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot linkageSnapshot =
                purchaseDemandForecastLinkageSupport.loadByForecastNo(
                        forecast == null ? null : forecast.getForecastNo(),
                        resolveTenantId(),
                        null,
                        false
                );
        Map<Long, PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage> linkageItemMap =
                linkageSnapshot == null ? Collections.emptyMap() : linkageSnapshot.toMaterialMap();
        vo.setId(forecast.getId());
        vo.setForecastNo(forecast.getForecastNo());
        vo.setForecastName(forecast.getForecastName());
        vo.setOrgId(forecast.getOrgId());
        vo.setOrgName(resolveOrgName(forecast.getOrgId()));
        vo.setDimension(forecast.getForecastDimension());
        vo.setForecastDays(forecast.getForecastDays());
        vo.setBasisDate(formatDate(forecast.getBasisDate()));
        vo.setHorizonStartDate(formatDate(forecast.getHorizonStartDate()));
        vo.setHorizonEndDate(formatDate(forecast.getHorizonEndDate()));
        vo.setMaterialCount(visibleItems.size());
        vo.setTotalSuggestedAmount(scaleAmount(visibleItems.stream()
                .map(PurchaseDemandForecastItem::getEstimatedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        vo.setCalendarFactor(scaleFactor(forecast.getCalendarFactor()));
        vo.setHolidayFactor(scaleFactor(forecast.getHolidayFactor()));
        vo.setActivityFactor(scaleFactor(forecast.getActivityFactor()));
        vo.setSummaryBasis(forecast.getSummaryBasis());
        vo.setGeneratedBy(resolveOperatorName(forecast.getCreatedBy()));
        vo.setGeneratedAt(formatDateTime(forecast.getCreatedAt()));
        vo.setEvaluationStatus(forecast.getEvaluationStatus());
        vo.setEvaluatedAt(formatDateTime(forecast.getEvaluatedAt()));
        vo.setWape(scaleRate(forecast.getOverallWape()));
        vo.setMape(scaleRate(forecast.getOverallMape()));
        vo.setBiasRate(scaleRate(forecast.getBiasRate()));
        vo.setStockoutRate(scaleRate(forecast.getStockoutRate()));
        vo.setOversupplyRate(scaleRate(forecast.getOversupplyRate()));
        vo.setOptimizationVersion(forecast.getOptimizationVersion());
        vo.setOptimizationScore(scaleRate(forecast.getOptimizationScore()));
        vo.setExplanationSummary(forecast.getExplanationSummary());
        vo.setApprovalSummary(forecast.getApprovalSummary());
        vo.setReorderTriggeredCount(forecast.getReorderTriggeredCount());
        vo.setRiskItemCount(forecast.getRiskItemCount());
        vo.setSupplierRecommendedCount(forecast.getSupplierRecommendedCount());
        vo.setManualReviewCount(forecast.getManualReviewCount());
        vo.setWarningItemCount(forecast.getWarningItemCount());
        vo.setTotalOptimizationCost(scaleAmount(forecast.getTotalOptimizationCost()));
        vo.setMaterialPlanStatus(linkageSnapshot == null
                ? PurchaseDemandForecastLinkageSupport.STATUS_UNUSED
                : linkageSnapshot.getMaterialPlanStatus());
        if (!visibleItems.isEmpty()) {
            for (PurchaseDemandForecastItem item : visibleItems) {
                PurchaseDemandForecastItemVO itemVO = new PurchaseDemandForecastItemVO();
                PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage linkageItem =
                        linkageItemMap.get(item.getMaterialId());
                itemVO.setId(item.getId());
                itemVO.setMaterialId(item.getMaterialId());
                itemVO.setMaterialName(item.getMaterialName());
                itemVO.setMaterialSpec(item.getMaterialSpec());
                itemVO.setUnit(item.getMaterialUnit());
                itemVO.setCurrentInventoryQty(scaleQuantity(item.getCurrentInventoryQty()));
                itemVO.setHistoricalPlanAvgQty(scaleQuantity(item.getHistoricalPlanAvgQty()));
                itemVO.setHistoricalOrderAvgQty(scaleQuantity(item.getHistoricalOrderAvgQty()));
                itemVO.setRecipeDemandQty(scaleQuantity(item.getRecipeDemandQty()));
                itemVO.setForecastDemandQty(scaleQuantity(item.getForecastDemandQty()));
                itemVO.setSafetyStockQty(scaleQuantity(item.getSafetyStockQty()));
                itemVO.setAvgDailyDemandQty(scaleQuantity(item.getAvgDailyDemandQty()));
                itemVO.setReviewPeriodDays(item.getReviewPeriodDays());
                itemVO.setReorderPointQty(scaleQuantity(item.getReorderPointQty()));
                itemVO.setTargetStockQty(scaleQuantity(item.getTargetStockQty()));
                itemVO.setInventoryPositionQty(scaleQuantity(item.getInventoryPositionQty()));
                itemVO.setTheoreticalSuggestedQty(scaleQuantity(item.getTheoreticalSuggestedQty()));
                itemVO.setSuggestedQty(scaleQuantity(item.getSuggestedQty()));
                itemVO.setConfidenceLowerQty(scaleQuantity(item.getConfidenceLowerQty()));
                itemVO.setConfidenceUpperQty(scaleQuantity(item.getConfidenceUpperQty()));
                itemVO.setConfidenceRate(resolveConfidenceRate(
                        item.getSuggestedQty(),
                        item.getConfidenceLowerQty(),
                        item.getConfidenceUpperQty()
                ));
                itemVO.setEstimatedUnitPrice(scaleAmount(item.getEstimatedUnitPrice()));
                itemVO.setEstimatedAmount(scaleAmount(item.getEstimatedAmount()));
                itemVO.setPriority(normalizePriorityLabel(item.getReplenishmentPriority()));
                itemVO.setModelType(item.getModelType());
                itemVO.setMaterialSegment(item.getMaterialSegment());
                itemVO.setForecastBasis(item.getForecastBasis());
                itemVO.setExplanationSummary(item.getExplanationSummary());
                itemVO.setExplanationDetail(item.getExplanationDetail());
                itemVO.setExplanationTemplateCode(item.getExplanationTemplateCode());
                itemVO.setExplanationTitle(item.getExplanationTitle());
                itemVO.setWarningLevel(item.getWarningLevel());
                itemVO.setWarningMessage(item.getWarningMessage());
                itemVO.setApprovalNote(item.getApprovalNote());
                itemVO.setManualReviewRequired(Objects.equals(item.getManualReviewRequired(), 1));
                itemVO.setAnomalyCodes(item.getAnomalyCodes());
                itemVO.setExplanationSortScore(scaleRate(item.getExplanationSortScore()));
                itemVO.setExplanationFactors(parseExplanationFactorsJson(item.getExplanationFactorsJson()));
                itemVO.setAnomalyFlags(item.getAnomalyFlags());
                itemVO.setActualConsumptionQty(scaleQuantity(item.getActualConsumptionQty()));
                itemVO.setAbsError(scaleQuantity(item.getAbsError()));
                itemVO.setApe(scaleRate(item.getApe()));
                itemVO.setBiasQty(scaleQuantity(item.getBiasQty()));
                itemVO.setRecipeDriveRatio(scaleRate(item.getRecipeDriveRatio()));
                itemVO.setDemandActiveRatio(scaleRate(item.getDemandActiveRatio()));
                itemVO.setDemandCv(scaleRate(item.getDemandCv()));
                itemVO.setActivitySensitivity(scaleRate(item.getActivitySensitivity()));
                itemVO.setServiceLevel(scaleRate(item.getServiceLevel()));
                itemVO.setLeadTimeDays(item.getLeadTimeDays());
                itemVO.setEffectiveLeadTimeDays(scaleRate(item.getEffectiveLeadTimeDays()));
                itemVO.setMinOrderQty(scaleQuantity(item.getMinOrderQty()));
                itemVO.setPackSize(scaleQuantity(item.getPackSize()));
                itemVO.setMaxAllowedStockQty(scaleQuantity(item.getMaxAllowedStockQty()));
                itemVO.setMaxCoverageDays(item.getMaxCoverageDays());
                itemVO.setRecommendedSupplierId(item.getRecommendedSupplierId());
                itemVO.setRecommendedSupplierName(item.getRecommendedSupplierName());
                itemVO.setSupplierScore(scaleRate(item.getSupplierScore()));
                itemVO.setSupplierFillRate(scaleRate(item.getSupplierFillRate()));
                itemVO.setSupplierOnTimeRate(scaleRate(item.getSupplierOnTimeRate()));
                itemVO.setOrderNow(Objects.equals(item.getOrderNow(), 1));
                itemVO.setOrderAction(item.getOrderAction());
                itemVO.setShortageCost(scaleAmount(item.getShortageCost()));
                itemVO.setHoldingCost(scaleAmount(item.getHoldingCost()));
                itemVO.setExpiryRiskCost(scaleAmount(item.getExpiryRiskCost()));
                itemVO.setOrderProcessingCost(scaleAmount(item.getOrderProcessingCost()));
                itemVO.setPurchasePriceCost(scaleAmount(item.getPurchasePriceCost()));
                itemVO.setTotalCost(scaleAmount(item.getTotalCost()));
                itemVO.setPhaseThreeRiskFlags(item.getPhaseThreeRiskFlags());
                itemVO.setEvaluationStatus(item.getEvaluationStatus());
                itemVO.setOccupiedLinkQty(linkageItem == null ? ZERO_QUANTITY : scaleQuantity(linkageItem.getOccupiedQty()));
                itemVO.setAvailableLinkQty(linkageItem == null
                        ? scaleQuantity(item.getSuggestedQty())
                        : scaleQuantity(linkageItem.getAvailableQty()));
                itemVO.setMaterialPlanStatus(linkageItem == null
                        ? PurchaseDemandForecastLinkageSupport.STATUS_UNUSED
                        : linkageItem.getMaterialPlanStatus());
                vo.getItems().add(itemVO);
            }
        }
        return vo;
    }

    private PurchaseDemandForecastMaterialLinkageVO buildMaterialLinkageVO(
            PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot linkageSnapshot
    ) {
        if (linkageSnapshot == null) {
            return null;
        }
        PurchaseDemandForecastMaterialLinkageVO vo = new PurchaseDemandForecastMaterialLinkageVO();
        vo.setForecastId(linkageSnapshot.getForecastId());
        vo.setForecastNo(linkageSnapshot.getForecastNo());
        vo.setOrgId(linkageSnapshot.getOrgId());
        vo.setOrgName(resolveOrgName(linkageSnapshot.getOrgId()));
        vo.setMaterialPlanStatus(linkageSnapshot.getMaterialPlanStatus());
        for (PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage linkageItem : linkageSnapshot.getItems()) {
            PurchaseDemandForecastMaterialLinkageVO.Item item = new PurchaseDemandForecastMaterialLinkageVO.Item();
            item.setForecastDetailId(linkageItem.getForecastDetailId());
            item.setMaterialId(linkageItem.getMaterialId());
            item.setMaterialName(linkageItem.getMaterialName());
            item.setMaterialSpec(linkageItem.getMaterialSpec());
            item.setUnit(linkageItem.getUnit());
            item.setOriginalQty(scaleQuantity(linkageItem.getOriginalQty()));
            item.setOccupiedQty(scaleQuantity(linkageItem.getOccupiedQty()));
            item.setAvailableQty(scaleQuantity(linkageItem.getAvailableQty()));
            item.setMaterialPlanStatus(linkageItem.getMaterialPlanStatus());
            vo.getItems().add(item);
        }
        return vo;
    }

    private PurchaseDemandForecastDashboardVO buildDashboard(Long orgId, Long tenantId) {
        PurchaseDemandForecastDashboardVO vo = new PurchaseDemandForecastDashboardVO();
        vo.setOrgId(orgId);
        vo.setOrgName(resolveOrgName(orgId));

        Map<String, Object> summary = jdbcTemplate.queryForMap(
                "SELECT " +
                        "COALESCE(SUM(CASE WHEN evaluation_status = 'completed' THEN 1 ELSE 0 END), 0) AS evaluatedCount, " +
                        "COALESCE(SUM(CASE WHEN COALESCE(evaluation_status, 'pending') <> 'completed' AND horizon_end_date < CURDATE() THEN 1 ELSE 0 END), 0) AS pendingCount, " +
                        "AVG(overall_wape) AS avgWape, AVG(overall_mape) AS avgMape, AVG(bias_rate) AS avgBiasRate, " +
                        "AVG(stockout_rate) AS avgStockoutRate, AVG(oversupply_rate) AS avgOversupplyRate, " +
                        "COALESCE(SUM(reorder_triggered_count), 0) AS reorderTriggeredCount, " +
                        "COALESCE(SUM(risk_item_count), 0) AS riskItemCount, " +
                        "COALESCE(SUM(supplier_recommended_count), 0) AS supplierRecommendedCount, " +
                        "COALESCE(SUM(manual_review_count), 0) AS manualReviewCount, " +
                        "COALESCE(SUM(warning_item_count), 0) AS warningItemCount, " +
                        "AVG(total_optimization_cost) AS avgOptimizationCost, " +
                        "MAX(evaluated_at) AS lastEvaluatedAt " +
                        "FROM scm_purchase_demand_forecast " +
                        "WHERE deleted = 0 AND org_id = ? AND tenant_id = ?",
                orgId,
                tenantId
        );
        vo.setEvaluatedForecastCount(toInteger(summary.get("evaluatedCount")));
        vo.setPendingEvaluationCount(toInteger(summary.get("pendingCount")));
        vo.setWape(scaleRate(toBigDecimal(summary.get("avgWape"))));
        vo.setMape(scaleRate(toBigDecimal(summary.get("avgMape"))));
        vo.setBiasRate(scaleRate(toBigDecimal(summary.get("avgBiasRate"))));
        vo.setStockoutRate(scaleRate(toBigDecimal(summary.get("avgStockoutRate"))));
        vo.setOversupplyRate(scaleRate(toBigDecimal(summary.get("avgOversupplyRate"))));
        vo.setReorderTriggeredCount(toInteger(summary.get("reorderTriggeredCount")));
        vo.setRiskItemCount(toInteger(summary.get("riskItemCount")));
        vo.setSupplierRecommendedCount(toInteger(summary.get("supplierRecommendedCount")));
        vo.setManualReviewCount(toInteger(summary.get("manualReviewCount")));
        vo.setWarningItemCount(toInteger(summary.get("warningItemCount")));
        vo.setAvgOptimizationCost(scaleAmount(toBigDecimal(summary.get("avgOptimizationCost"))));
        vo.setLastEvaluationAt(formatDateTime(summary.get("lastEvaluatedAt")));

        List<Map<String, Object>> optimizationRows = jdbcTemplate.queryForList(
                "SELECT material_segment AS materialSegment, model_type AS modelType, version_no AS versionNo, score, wape, " +
                        "stockout_rate AS stockoutRate, oversupply_rate AS oversupplyRate, optimized_at AS optimizedAt, " +
                        "rollback_applied AS rollbackApplied, rollback_reason AS rollbackReason " +
                        "FROM scm_purchase_demand_forecast_optimization_log " +
                        "WHERE org_id = ? AND tenant_id = ? " +
                        "ORDER BY optimized_at DESC, id DESC LIMIT 12",
                orgId,
                tenantId
        );
        vo.setOptimizedConfigCount(optimizationRows.size());
        if (!optimizationRows.isEmpty()) {
            vo.setLastOptimizationAt(formatDateTime(optimizationRows.get(0).get("optimizedAt")));
        }
        for (Map<String, Object> row : optimizationRows) {
            PurchaseDemandForecastDashboardVO.OptimizationSummary summaryVO =
                    new PurchaseDemandForecastDashboardVO.OptimizationSummary();
            summaryVO.setMaterialSegment(asString(row.get("materialSegment")));
            summaryVO.setModelType(asString(row.get("modelType")));
            summaryVO.setVersionNo(toInteger(row.get("versionNo")));
            summaryVO.setScore(scaleRate(toBigDecimal(row.get("score"))));
            summaryVO.setWape(scaleRate(toBigDecimal(row.get("wape"))));
            summaryVO.setStockoutRate(scaleRate(toBigDecimal(row.get("stockoutRate"))));
            summaryVO.setOversupplyRate(scaleRate(toBigDecimal(row.get("oversupplyRate"))));
            summaryVO.setOptimizedAt(formatDateTime(row.get("optimizedAt")));
            summaryVO.setRollbackApplied(Objects.equals(toInteger(row.get("rollbackApplied")), 1));
            summaryVO.setRollbackReason(asString(row.get("rollbackReason")));
            vo.getOptimizationSummaries().add(summaryVO);
        }
        List<Map<String, Object>> rollbackRows = jdbcTemplate.queryForList(
                "SELECT COUNT(*) AS rollbackCount, MAX(optimized_at) AS lastRollbackAt " +
                        "FROM scm_purchase_demand_forecast_optimization_log " +
                        "WHERE org_id = ? AND tenant_id = ? AND rollback_applied = 1",
                orgId,
                tenantId
        );
        if (!rollbackRows.isEmpty()) {
            vo.setRollbackCount(toInteger(rollbackRows.get(0).get("rollbackCount")));
            vo.setLastRollbackAt(formatDateTime(rollbackRows.get(0).get("lastRollbackAt")));
        }

        List<Map<String, Object>> segmentRows = jdbcTemplate.queryForList(
                "SELECT ti.material_segment AS materialSegment, ti.model_type AS modelType, COUNT(*) AS materialCount, " +
                        "COALESCE(SUM(ti.suggested_purchase_qty), 0) AS totalSuggestedQty, " +
                        "COALESCE(SUM(ti.actual_consumption_qty), 0) AS totalActualQty, " +
                        "COALESCE(SUM(ti.abs_error), 0) AS totalAbsError, " +
                        "COALESCE(SUM(ti.bias_qty), 0) AS totalBiasQty, " +
                        "COALESCE(SUM(ti.total_cost), 0) AS totalOptimizationCost " +
                        "FROM scm_purchase_demand_forecast_task_item ti " +
                        "JOIN scm_purchase_demand_forecast_task t ON t.id = ti.task_id " +
                        "WHERE t.org_id = ? AND t.tenant_id = ? AND ti.evaluation_status = 'completed' " +
                        "GROUP BY ti.material_segment, ti.model_type " +
                        "ORDER BY materialCount DESC, totalSuggestedQty DESC",
                orgId,
                tenantId
        );
        for (Map<String, Object> row : segmentRows) {
            BigDecimal totalActualQty = scaleQuantity(toBigDecimal(row.get("totalActualQty")));
            BigDecimal totalAbsError = scaleQuantity(toBigDecimal(row.get("totalAbsError")));
            BigDecimal totalBiasQty = scaleQuantity(toBigDecimal(row.get("totalBiasQty")));
            PurchaseDemandForecastDashboardVO.SegmentSummary segmentSummary =
                    new PurchaseDemandForecastDashboardVO.SegmentSummary();
            segmentSummary.setMaterialSegment(asString(row.get("materialSegment")));
            segmentSummary.setModelType(asString(row.get("modelType")));
            segmentSummary.setMaterialCount(toInteger(row.get("materialCount")));
            segmentSummary.setTotalSuggestedQty(scaleQuantity(toBigDecimal(row.get("totalSuggestedQty"))));
            segmentSummary.setTotalActualQty(totalActualQty);
            segmentSummary.setTotalOptimizationCost(scaleAmount(toBigDecimal(row.get("totalOptimizationCost"))));
            segmentSummary.setWape(ratio(totalAbsError, totalActualQty));
            segmentSummary.setBiasRate(ratio(totalBiasQty, totalActualQty));
            vo.getSegmentSummaries().add(segmentSummary);
        }
        List<Map<String, Object>> supplierRows = jdbcTemplate.queryForList(
                "SELECT recommended_supplier_id AS supplierId, MAX(recommended_supplier_name) AS supplierName, COUNT(*) AS recommendCount, " +
                        "AVG(supplier_score) AS avgSupplierScore, AVG(effective_lead_time_days) AS avgEffectiveLeadTimeDays " +
                        "FROM scm_purchase_demand_forecast_task_item ti " +
                        "JOIN scm_purchase_demand_forecast_task t ON t.id = ti.task_id " +
                        "WHERE t.org_id = ? AND t.tenant_id = ? AND ti.recommended_supplier_id IS NOT NULL " +
                        "GROUP BY recommended_supplier_id ORDER BY recommendCount DESC, avgSupplierScore DESC LIMIT 10",
                orgId,
                tenantId
        );
        for (Map<String, Object> row : supplierRows) {
            PurchaseDemandForecastDashboardVO.SupplierSummary supplierSummary =
                    new PurchaseDemandForecastDashboardVO.SupplierSummary();
            supplierSummary.setSupplierId(toLong(row.get("supplierId")));
            supplierSummary.setSupplierName(asString(row.get("supplierName")));
            supplierSummary.setRecommendCount(toInteger(row.get("recommendCount")));
            supplierSummary.setAvgSupplierScore(scaleRate(toBigDecimal(row.get("avgSupplierScore"))));
            supplierSummary.setAvgEffectiveLeadTimeDays(scaleRate(toBigDecimal(row.get("avgEffectiveLeadTimeDays"))));
            vo.getSupplierSummaries().add(supplierSummary);
        }
        return vo;
    }

    private void refreshSupplierDeliveryStats(Long orgId, Long tenantId) {
        LocalDate startDate = LocalDate.now().minusDays(90);
        supplierDeliveryStatMapper.delete(
                new LambdaQueryWrapper<SupplierDeliveryStat>()
                        .eq(SupplierDeliveryStat::getOrgId, orgId)
                        .eq(SupplierDeliveryStat::getTenantId, tenantId)
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT o.supplier_id AS supplierId, MAX(o.supplier_name) AS supplierName, " +
                        "MIN(o.order_date) AS statStartDate, MAX(DATE(COALESCE(o.actual_delivery_at, o.expected_delivery_at, o.updated_at, o.created_at))) AS statEndDate, " +
                        "COUNT(DISTINCT o.id) AS sampleCount, " +
                        "AVG(GREATEST(DATEDIFF(COALESCE(o.actual_delivery_at, o.expected_delivery_at, o.updated_at, o.created_at), o.order_date), 0)) AS avgLeadTimeDays, " +
                        "STDDEV_POP(GREATEST(DATEDIFF(COALESCE(o.actual_delivery_at, o.expected_delivery_at, o.updated_at, o.created_at), o.order_date), 0)) AS stdLeadTimeDays, " +
                        "COALESCE(SUM(COALESCE(i.inbound_qty, i.order_qty - COALESCE(i.remaining_inbound_qty, 0), 0)), 0) / NULLIF(SUM(i.order_qty), 0) AS fillRate, " +
                        "AVG(CASE WHEN o.expected_delivery_at IS NOT NULL AND o.actual_delivery_at IS NOT NULL AND o.actual_delivery_at <= o.expected_delivery_at THEN 1 ELSE 0 END) AS onTimeRate " +
                        "FROM scm_purchase_order o " +
                        "JOIN scm_purchase_order_item i ON i.order_id = o.id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status NOT IN ('draft', 'rejected', 'voided', 'cancelled') " +
                        "  AND o.order_date >= ? " +
                        "GROUP BY o.supplier_id",
                orgId,
                tenantId,
                Date.valueOf(startDate)
        );
        for (Map<String, Object> row : rows) {
            SupplierDeliveryStat stat = new SupplierDeliveryStat();
            stat.setOrgId(orgId);
            stat.setTenantId(tenantId);
            stat.setSupplierId(toLong(row.get("supplierId")));
            stat.setSupplierName(asString(row.get("supplierName")));
            stat.setStatStartDate(safeDate(row.get("statStartDate")));
            stat.setStatEndDate(safeDate(row.get("statEndDate")));
            stat.setSampleCount(toInteger(row.get("sampleCount")));
            stat.setAvgLeadTimeDays(scaleRate(toBigDecimal(row.get("avgLeadTimeDays"))));
            stat.setStdLeadTimeDays(scaleRate(toBigDecimal(row.get("stdLeadTimeDays"))));
            stat.setFillRate(scaleRate(toBigDecimal(row.get("fillRate"))));
            stat.setOnTimeRate(scaleRate(toBigDecimal(row.get("onTimeRate"))));
            stat.setLastCalculatedAt(LocalDateTime.now());
            supplierDeliveryStatMapper.insert(stat);
        }
    }

    private int resolveOrgLeadTimeDays(Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT AVG(avg_lead_time_days) AS avgLeadTimeDays " +
                        "FROM scm_supplier_delivery_stat WHERE org_id = ? AND tenant_id = ?",
                orgId,
                tenantId
        );
        if (rows.isEmpty()) {
            return 3;
        }
        BigDecimal avgLeadTime = scaleRate(toBigDecimal(rows.get(0).get("avgLeadTimeDays")));
        if (avgLeadTime.compareTo(BigDecimal.ZERO) <= 0) {
            return 3;
        }
        return Math.max(1, avgLeadTime.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    private Map<Long, SupplierRecommendation> buildSupplierRecommendationMap(
            Long orgId,
            Long tenantId,
            Collection<Long> materialIds
    ) {
        List<Long> validMaterialIds = normalizeMaterialIds(materialIds);
        if (validMaterialIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDate startDate = LocalDate.now().minusDays(SUPPLIER_LOOKBACK_DAYS);
        List<Object> args = new ArrayList<>();
        args.add(orgId);
        args.add(tenantId);
        args.add(Date.valueOf(startDate));
        args.addAll(validMaterialIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.material_id AS materialId, o.supplier_id AS supplierId, MAX(o.supplier_name) AS supplierName, " +
                        "COUNT(DISTINCT o.id) AS orderCount, AVG(i.unit_price) AS avgUnitPrice, " +
                        "COALESCE(MAX(s.avg_lead_time_days), 0) AS avgLeadTimeDays, " +
                        "COALESCE(MAX(s.std_lead_time_days), 0) AS stdLeadTimeDays, " +
                        "COALESCE(MAX(s.fill_rate), 0) AS fillRate, " +
                        "COALESCE(MAX(s.on_time_rate), 0) AS onTimeRate " +
                        "FROM scm_purchase_order o " +
                        "JOIN scm_purchase_order_item i ON i.order_id = o.id " +
                        "LEFT JOIN scm_supplier_delivery_stat s ON s.org_id = o.org_id AND s.tenant_id = o.tenant_id AND s.supplier_id = o.supplier_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status NOT IN ('draft', 'rejected', 'voided', 'cancelled') " +
                        "  AND o.order_date >= ? " +
                        "  AND i.material_id IN (" + placeholders(validMaterialIds.size()) + ") " +
                        "GROUP BY i.material_id, o.supplier_id",
                args.toArray()
        );
        Map<Long, List<SupplierRecommendation>> candidateMap = new LinkedHashMap<>();
        Map<Long, BigDecimal> minPriceMap = new HashMap<>();
        Map<Long, BigDecimal> minEffectiveLeadMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            Long supplierId = toLong(row.get("supplierId"));
            if (!isValidMaterialId(materialId) || supplierId == null || supplierId <= 0) {
                continue;
            }
            BigDecimal avgLeadTime = positiveOrDefault(toBigDecimal(row.get("avgLeadTimeDays")), BigDecimal.valueOf(resolveOrgLeadTimeDays(orgId, tenantId)));
            BigDecimal stdLeadTime = max(scaleRate(toBigDecimal(row.get("stdLeadTimeDays"))), BigDecimal.ZERO);
            BigDecimal fillRate = clamp(scaleRate(toBigDecimal(row.get("fillRate"))), BigDecimal.ZERO, BigDecimal.ONE);
            BigDecimal onTimeRate = clamp(scaleRate(toBigDecimal(row.get("onTimeRate"))), BigDecimal.ZERO, BigDecimal.ONE);
            BigDecimal avgUnitPrice = scaleAmount(toBigDecimal(row.get("avgUnitPrice")));
            BigDecimal effectiveLeadTime = resolveEffectiveLeadTimeDays(avgLeadTime, stdLeadTime, DEFAULT_LEAD_TIME_RISK_FACTOR);
            SupplierRecommendation recommendation = new SupplierRecommendation(
                    materialId,
                    supplierId,
                    asString(row.get("supplierName")),
                    avgUnitPrice,
                    scaleRate(avgLeadTime),
                    scaleRate(stdLeadTime),
                    scaleRate(effectiveLeadTime),
                    fillRate,
                    onTimeRate,
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    toInteger(row.get("orderCount")),
                    "history"
            );
            candidateMap.computeIfAbsent(materialId, key -> new ArrayList<>()).add(recommendation);
            if (avgUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
                minPriceMap.merge(materialId, avgUnitPrice, BigDecimal::min);
            }
            if (effectiveLeadTime.compareTo(BigDecimal.ZERO) > 0) {
                minEffectiveLeadMap.merge(materialId, effectiveLeadTime, BigDecimal::min);
            }
        }

        SupplierRecommendation fallback = buildFallbackSupplierRecommendation(orgId, tenantId);
        Map<Long, SupplierRecommendation> result = new LinkedHashMap<>();
        for (Long materialId : validMaterialIds) {
            List<SupplierRecommendation> recommendations = candidateMap.get(materialId);
            if (recommendations == null || recommendations.isEmpty()) {
                result.put(materialId, fallback == null ? SupplierRecommendation.empty(materialId) : fallback.withMaterialId(materialId));
                continue;
            }
            BigDecimal minPrice = minPriceMap.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal minEffectiveLeadTime = minEffectiveLeadMap.getOrDefault(materialId, BigDecimal.ZERO);
            SupplierRecommendation best = null;
            for (SupplierRecommendation candidate : recommendations) {
                BigDecimal candidateScore = resolveSupplierScore(candidate, minPrice, minEffectiveLeadTime);
                SupplierRecommendation scored = candidate.withScore(candidateScore);
                if (best == null || scored.score().compareTo(best.score()) > 0) {
                    best = scored;
                }
            }
            result.put(materialId, best == null ? SupplierRecommendation.empty(materialId) : best);
        }
        return result;
    }

    private SupplierRecommendation buildFallbackSupplierRecommendation(Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT supplier_id AS supplierId, supplier_name AS supplierName, avg_lead_time_days AS avgLeadTimeDays, " +
                        "std_lead_time_days AS stdLeadTimeDays, fill_rate AS fillRate, on_time_rate AS onTimeRate, sample_count AS sampleCount " +
                        "FROM scm_supplier_delivery_stat WHERE org_id = ? AND tenant_id = ? " +
                        "ORDER BY fill_rate DESC, on_time_rate DESC, avg_lead_time_days ASC, sample_count DESC LIMIT 1",
                orgId,
                tenantId
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        BigDecimal avgLeadTime = positiveOrDefault(toBigDecimal(row.get("avgLeadTimeDays")), BigDecimal.valueOf(resolveOrgLeadTimeDays(orgId, tenantId)));
        BigDecimal stdLeadTime = max(scaleRate(toBigDecimal(row.get("stdLeadTimeDays"))), BigDecimal.ZERO);
        BigDecimal fillRate = clamp(scaleRate(toBigDecimal(row.get("fillRate"))), BigDecimal.ZERO, BigDecimal.ONE);
        BigDecimal onTimeRate = clamp(scaleRate(toBigDecimal(row.get("onTimeRate"))), BigDecimal.ZERO, BigDecimal.ONE);
        BigDecimal effectiveLeadTime = resolveEffectiveLeadTimeDays(avgLeadTime, stdLeadTime, DEFAULT_LEAD_TIME_RISK_FACTOR);
        BigDecimal score = resolveSupplierScore(
                new SupplierRecommendation(
                        0L,
                        toLong(row.get("supplierId")),
                        asString(row.get("supplierName")),
                        ZERO_AMOUNT,
                        scaleRate(avgLeadTime),
                        scaleRate(stdLeadTime),
                        scaleRate(effectiveLeadTime),
                        fillRate,
                        onTimeRate,
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                        toInteger(row.get("sampleCount")),
                        "fallback"
                ),
                BigDecimal.ZERO,
                effectiveLeadTime
        );
        return new SupplierRecommendation(
                0L,
                toLong(row.get("supplierId")),
                asString(row.get("supplierName")),
                ZERO_AMOUNT,
                scaleRate(avgLeadTime),
                scaleRate(stdLeadTime),
                scaleRate(effectiveLeadTime),
                fillRate,
                onTimeRate,
                score,
                toInteger(row.get("sampleCount")),
                "fallback"
        );
    }

    private BigDecimal resolveSupplierScore(
            SupplierRecommendation recommendation,
            BigDecimal minPrice,
            BigDecimal minEffectiveLeadTime
    ) {
        if (recommendation == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal reliabilityScore = scaleRate(
                recommendation.fillRate().multiply(BigDecimal.valueOf(0.55d))
                        .add(recommendation.onTimeRate().multiply(BigDecimal.valueOf(0.45d)))
        );
        BigDecimal leadTimeScore = recommendation.effectiveLeadTimeDays().compareTo(BigDecimal.ZERO) > 0
                && minEffectiveLeadTime.compareTo(BigDecimal.ZERO) > 0
                ? clamp(minEffectiveLeadTime.divide(recommendation.effectiveLeadTimeDays(), 6, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ONE)
                : BigDecimal.valueOf(0.50d).setScale(6, RoundingMode.HALF_UP);
        BigDecimal priceScore = recommendation.avgUnitPrice().compareTo(BigDecimal.ZERO) > 0
                && minPrice.compareTo(BigDecimal.ZERO) > 0
                ? clamp(minPrice.divide(recommendation.avgUnitPrice(), 6, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ONE)
                : BigDecimal.valueOf(0.50d).setScale(6, RoundingMode.HALF_UP);
        return scaleRate(
                reliabilityScore.multiply(SUPPLIER_RELIABILITY_WEIGHT)
                        .add(leadTimeScore.multiply(SUPPLIER_LEAD_TIME_WEIGHT))
                        .add(priceScore.multiply(SUPPLIER_PRICE_WEIGHT))
        );
    }

    private void refreshDueForecastEvaluations(Long orgId, Long tenantId) {
        List<Map<String, Object>> dueTasks = jdbcTemplate.queryForList(
                "SELECT t.id AS taskId, t.forecast_no AS forecastNo, f.id AS forecastId, t.horizon_start_date AS horizonStartDate, " +
                        "t.horizon_end_date AS horizonEndDate " +
                        "FROM scm_purchase_demand_forecast_task t " +
                        "JOIN scm_purchase_demand_forecast f ON f.forecast_no = t.forecast_no AND f.deleted = 0 AND f.tenant_id = t.tenant_id " +
                        "WHERE t.org_id = ? AND t.tenant_id = ? " +
                        "  AND COALESCE(t.evaluation_status, 'pending') <> 'completed' " +
                        "  AND t.horizon_end_date < CURDATE()",
                orgId,
                tenantId
        );
        for (Map<String, Object> taskRow : dueTasks) {
            Long taskId = toLong(taskRow.get("taskId"));
            Long forecastId = toLong(taskRow.get("forecastId"));
            LocalDate horizonStartDate = safeDate(taskRow.get("horizonStartDate"));
            LocalDate horizonEndDate = safeDate(taskRow.get("horizonEndDate"));
            if (taskId == null || forecastId == null || horizonStartDate == null || horizonEndDate == null) {
                continue;
            }
            List<PurchaseDemandForecastTaskItem> taskItems = purchaseDemandForecastTaskItemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseDemandForecastTaskItem>()
                            .eq(PurchaseDemandForecastTaskItem::getTaskId, taskId)
            );
            List<PurchaseDemandForecastItem> forecastItems = purchaseDemandForecastItemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseDemandForecastItem>()
                            .eq(PurchaseDemandForecastItem::getForecastId, forecastId)
                            .eq(PurchaseDemandForecastItem::getTenantId, tenantId)
            );
            Map<Long, PurchaseDemandForecastItem> forecastItemMap = forecastItems.stream()
                    .filter(item -> isValidMaterialId(item.getMaterialId()))
                    .collect(Collectors.toMap(PurchaseDemandForecastItem::getMaterialId, item -> item, (left, right) -> left));

            List<Long> materialIds = taskItems.stream()
                    .map(PurchaseDemandForecastTaskItem::getMaterialId)
                    .filter(this::isValidMaterialId)
                    .collect(Collectors.toList());
            if (materialIds.isEmpty()) {
                continue;
            }
            List<MaterialDailyConsumption> consumptionRows = materialDailyConsumptionMapper.selectList(
                    new LambdaQueryWrapper<MaterialDailyConsumption>()
                            .eq(MaterialDailyConsumption::getOrgId, orgId)
                            .eq(MaterialDailyConsumption::getTenantId, tenantId)
                            .between(MaterialDailyConsumption::getStatDate, horizonStartDate, horizonEndDate)
                            .in(MaterialDailyConsumption::getMaterialId, materialIds)
            );
            Map<Long, List<MaterialDailyConsumption>> consumptionMap = consumptionRows.stream()
                    .collect(Collectors.groupingBy(MaterialDailyConsumption::getMaterialId, LinkedHashMap::new, Collectors.toList()));

            BigDecimal totalActualQty = BigDecimal.ZERO;
            BigDecimal totalPredictedQty = BigDecimal.ZERO;
            BigDecimal totalAbsError = BigDecimal.ZERO;
            BigDecimal totalSquaredError = BigDecimal.ZERO;
            BigDecimal totalBiasQty = BigDecimal.ZERO;
            BigDecimal totalOversupplyQty = BigDecimal.ZERO;
            BigDecimal totalApe = BigDecimal.ZERO;
            int totalStockoutDays = 0;
            int evaluatedCount = 0;
            int totalObservedDays = taskItems.size() * Math.max(1, horizonStartDate.until(horizonEndDate).getDays() + 1);

            for (PurchaseDemandForecastTaskItem taskItem : taskItems) {
                List<MaterialDailyConsumption> materialConsumptions =
                        consumptionMap.getOrDefault(taskItem.getMaterialId(), Collections.emptyList());
                BigDecimal actualQty = scaleQuantity(materialConsumptions.stream()
                        .map(MaterialDailyConsumption::getConsumedQty)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                BigDecimal predictedQty = scaleQuantity(taskItem.getForecastDemandQty());
                BigDecimal absError = scaleQuantity(predictedQty.subtract(actualQty).abs());
                BigDecimal ape = ratio(absError, actualQty);
                BigDecimal rmse = scaleQuantity(absError);
                BigDecimal squaredError = scaleQuantity(absError.multiply(absError));
                BigDecimal biasQty = scaleQuantity(predictedQty.subtract(actualQty));
                int stockoutDays = (int) materialConsumptions.stream()
                        .filter(row -> scaleQuantity(row.getClosingStockQty()).compareTo(DEFAULT_EPSILON) <= 0
                                && scaleQuantity(row.getConsumedQty()).compareTo(ZERO_QUANTITY) > 0)
                        .count();
                BigDecimal oversupplyQty = scaleQuantity(max(predictedQty.subtract(actualQty), ZERO_QUANTITY));
                String actualSource = materialConsumptions.stream()
                        .map(MaterialDailyConsumption::getDataSource)
                        .filter(StrUtil::isNotBlank)
                        .distinct()
                        .collect(Collectors.joining(","));

                taskItem.setActualConsumptionQty(actualQty);
                taskItem.setAbsError(absError);
                taskItem.setApe(scaleRate(ape));
                taskItem.setRmse(scaleRate(rmse));
                taskItem.setBiasQty(biasQty);
                taskItem.setStockoutDays(stockoutDays);
                taskItem.setOversupplyQty(oversupplyQty);
                taskItem.setEvaluationStatus(EVALUATION_STATUS_COMPLETED);
                taskItem.setEvaluatedAt(LocalDateTime.now());
                purchaseDemandForecastTaskItemMapper.updateById(taskItem);

                PurchaseDemandForecastItem forecastItem = forecastItemMap.get(taskItem.getMaterialId());
                if (forecastItem != null) {
                    forecastItem.setActualConsumptionQty(actualQty);
                    forecastItem.setAbsError(absError);
                    forecastItem.setApe(scaleRate(ape));
                    forecastItem.setRmse(scaleRate(rmse));
                    forecastItem.setBiasQty(biasQty);
                    forecastItem.setEvaluationStatus(EVALUATION_STATUS_COMPLETED);
                    purchaseDemandForecastItemMapper.updateById(forecastItem);
                }

                PurchaseDemandForecastEvaluation evaluation = purchaseDemandForecastEvaluationMapper.selectOne(
                        new LambdaQueryWrapper<PurchaseDemandForecastEvaluation>()
                                .eq(PurchaseDemandForecastEvaluation::getTaskItemId, taskItem.getId())
                                .last("LIMIT 1")
                );
                if (evaluation == null) {
                    evaluation = new PurchaseDemandForecastEvaluation();
                }
                evaluation.setOrgId(orgId);
                evaluation.setTenantId(tenantId);
                evaluation.setForecastId(forecastId);
                evaluation.setTaskId(taskId);
                evaluation.setTaskItemId(taskItem.getId());
                evaluation.setForecastItemId(forecastItem == null ? null : forecastItem.getId());
                evaluation.setMaterialId(taskItem.getMaterialId());
                evaluation.setMaterialName(taskItem.getMaterialName());
                evaluation.setMaterialSegment(taskItem.getMaterialSegment());
                evaluation.setForecastDate(horizonStartDate.minusDays(1));
                evaluation.setHorizonStartDate(horizonStartDate);
                evaluation.setHorizonEndDate(horizonEndDate);
                evaluation.setPredictedQty(predictedQty);
                evaluation.setActualQty(actualQty);
                evaluation.setAbsError(absError);
                evaluation.setApe(scaleRate(ape));
                evaluation.setRmse(scaleRate(rmse));
                evaluation.setBiasQty(biasQty);
                evaluation.setStockoutDays(stockoutDays);
                evaluation.setOversupplyQty(oversupplyQty);
                evaluation.setWapeContribution(absError);
                evaluation.setModelType(taskItem.getModelType());
                evaluation.setActualSource(actualSource);
                evaluation.setEvaluationStatus(EVALUATION_STATUS_COMPLETED);
                evaluation.setEvaluatedAt(LocalDateTime.now());
                if (evaluation.getId() == null) {
                    purchaseDemandForecastEvaluationMapper.insert(evaluation);
                } else {
                    purchaseDemandForecastEvaluationMapper.updateById(evaluation);
                }

                totalActualQty = totalActualQty.add(actualQty);
                totalPredictedQty = totalPredictedQty.add(predictedQty);
                totalAbsError = totalAbsError.add(absError);
                totalSquaredError = totalSquaredError.add(squaredError);
                totalBiasQty = totalBiasQty.add(biasQty);
                totalOversupplyQty = totalOversupplyQty.add(oversupplyQty);
                totalApe = totalApe.add(scaleRate(ape));
                totalStockoutDays += stockoutDays;
                evaluatedCount++;
            }

            PurchaseDemandForecastTask task = purchaseDemandForecastTaskMapper.selectById(taskId);
            if (task != null) {
                task.setEvaluationStatus(EVALUATION_STATUS_COMPLETED);
                task.setEvaluatedAt(LocalDateTime.now());
                task.setWape(ratio(totalAbsError, totalActualQty));
                task.setMape(evaluatedCount == 0 ? ZERO_QUANTITY : scaleRate(totalApe.divide(BigDecimal.valueOf(evaluatedCount), 6, RoundingMode.HALF_UP)));
                task.setRmse(sqrtMean(totalSquaredError, evaluatedCount));
                task.setBiasRate(ratio(totalBiasQty, totalActualQty));
                task.setStockoutRate(totalObservedDays <= 0
                        ? ZERO_QUANTITY
                        : scaleRate(BigDecimal.valueOf(totalStockoutDays).divide(BigDecimal.valueOf(totalObservedDays), 6, RoundingMode.HALF_UP)));
                task.setOversupplyRate(ratio(totalOversupplyQty, totalPredictedQty));
                purchaseDemandForecastTaskMapper.updateById(task);
            }

            PurchaseDemandForecast forecast = purchaseDemandForecastMapper.selectById(forecastId);
            if (forecast != null) {
                forecast.setEvaluationStatus(EVALUATION_STATUS_COMPLETED);
                forecast.setEvaluatedAt(LocalDateTime.now());
                forecast.setOverallWape(ratio(totalAbsError, totalActualQty));
                forecast.setOverallMape(evaluatedCount == 0 ? ZERO_QUANTITY : scaleRate(totalApe.divide(BigDecimal.valueOf(evaluatedCount), 6, RoundingMode.HALF_UP)));
                forecast.setOverallRmse(sqrtMean(totalSquaredError, evaluatedCount));
                forecast.setBiasRate(ratio(totalBiasQty, totalActualQty));
                forecast.setStockoutRate(totalObservedDays <= 0
                        ? ZERO_QUANTITY
                        : scaleRate(BigDecimal.valueOf(totalStockoutDays).divide(BigDecimal.valueOf(totalObservedDays), 6, RoundingMode.HALF_UP)));
                forecast.setOversupplyRate(ratio(totalOversupplyQty, totalPredictedQty));
                purchaseDemandForecastMapper.updateById(forecast);
            }
        }
    }

    private void optimizeModelConfigs(Long orgId, Long tenantId) {
        List<Long> completedTaskIds = purchaseDemandForecastTaskMapper.selectList(
                        new LambdaQueryWrapper<PurchaseDemandForecastTask>()
                                .eq(PurchaseDemandForecastTask::getOrgId, orgId)
                                .eq(PurchaseDemandForecastTask::getTenantId, tenantId)
                                .eq(PurchaseDemandForecastTask::getEvaluationStatus, EVALUATION_STATUS_COMPLETED)
                                .ge(PurchaseDemandForecastTask::getEvaluatedAt, LocalDateTime.now().minusDays(90))
                ).stream()
                .map(PurchaseDemandForecastTask::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (completedTaskIds.isEmpty()) {
            return;
        }
        Map<String, List<PurchaseDemandForecastTaskItem>> segmentItems = purchaseDemandForecastTaskItemMapper.selectList(
                        new LambdaQueryWrapper<PurchaseDemandForecastTaskItem>()
                                .in(PurchaseDemandForecastTaskItem::getTaskId, completedTaskIds)
                                .isNotNull(PurchaseDemandForecastTaskItem::getFeatureSnapshotJson)
                                .eq(PurchaseDemandForecastTaskItem::getEvaluationStatus, EVALUATION_STATUS_COMPLETED)
                ).stream()
                .filter(item -> StrUtil.isNotBlank(item.getMaterialSegment()))
                .collect(Collectors.groupingBy(PurchaseDemandForecastTaskItem::getMaterialSegment, LinkedHashMap::new, Collectors.toList()));

        if (segmentItems.isEmpty()) {
            return;
        }

        Map<String, MaterialForecastModelConfig> activeConfigs = loadActiveSegmentConfigMap(orgId, tenantId);
        for (Map.Entry<String, List<PurchaseDemandForecastTaskItem>> entry : segmentItems.entrySet()) {
            String segment = entry.getKey();
            List<PurchaseDemandForecastTaskItem> items = entry.getValue();
            if (items.size() < 3) {
                continue;
            }
            MaterialForecastModelConfig currentConfig = activeConfigs.get(segment);
            if (currentConfig == null) {
                continue;
            }
            PurchaseDemandForecastRuleEngine.ModelParameters baseParameters = toModelParameters(currentConfig, null);
            List<PurchaseDemandForecastRuleEngine.ModelParameters> candidates = buildCandidateParameters(baseParameters);
            OptimizationCandidateScore bestScore = null;
            for (PurchaseDemandForecastRuleEngine.ModelParameters candidate : candidates) {
                BigDecimal totalAbsError = BigDecimal.ZERO;
                BigDecimal totalActual = BigDecimal.ZERO;
                BigDecimal shortagePenalty = BigDecimal.ZERO;
                BigDecimal overSupplyPenalty = BigDecimal.ZERO;
                int sampleCount = 0;
                for (PurchaseDemandForecastTaskItem item : items) {
                    PurchaseDemandForecastRuleEngine.MaterialRuleContext context = parseRuleContextFromSnapshotJson(item.getFeatureSnapshotJson());
                    if (context == null) {
                        continue;
                    }
                    PurchaseDemandForecastRuleEngine.RuleResult simulated = purchaseDemandForecastRuleEngine.evaluate(context, candidate);
                    BigDecimal predicted = scaleQuantity(simulated.forecastDemandQty());
                    BigDecimal actual = scaleQuantity(item.getActualConsumptionQty());
                    BigDecimal absError = scaleQuantity(predicted.subtract(actual).abs());
                    BigDecimal shortageRate = ratio(max(actual.subtract(predicted), ZERO_QUANTITY), actual);
                    BigDecimal overSupplyRate = ratio(max(predicted.subtract(actual), ZERO_QUANTITY), max(actual, predicted));
                    totalAbsError = totalAbsError.add(absError);
                    totalActual = totalActual.add(actual);
                    shortagePenalty = shortagePenalty.add(shortageRate);
                    overSupplyPenalty = overSupplyPenalty.add(overSupplyRate);
                    sampleCount++;
                }
                if (sampleCount == 0) {
                    continue;
                }
                BigDecimal wape = ratio(totalAbsError, totalActual);
                BigDecimal stockoutRate = scaleRate(shortagePenalty.divide(BigDecimal.valueOf(sampleCount), 6, RoundingMode.HALF_UP));
                BigDecimal oversupplyRate = scaleRate(overSupplyPenalty.divide(BigDecimal.valueOf(sampleCount), 6, RoundingMode.HALF_UP));
                BigDecimal score = scaleRate(
                        wape.add(STOCKOUT_PENALTY_FACTOR.multiply(stockoutRate))
                                .add(OVER_SUPPLY_PENALTY_FACTOR.multiply(oversupplyRate))
                );
                OptimizationCandidateScore candidateScore = new OptimizationCandidateScore(candidate, score, wape, stockoutRate, oversupplyRate, sampleCount);
                if (bestScore == null || candidateScore.score().compareTo(bestScore.score()) < 0) {
                    bestScore = candidateScore;
                }
            }
            if (bestScore == null || !shouldApplyOptimizedConfig(currentConfig, bestScore)) {
                attemptRollbackOptimizedConfig(orgId, tenantId, segment, currentConfig);
                continue;
            }
            deactivateConfig(currentConfig);
            MaterialForecastModelConfig newConfig = copyConfigWithParameters(currentConfig, bestScore.parameters());
            newConfig.setId(null);
            newConfig.setVersionNo((currentConfig.getVersionNo() == null ? 1 : currentConfig.getVersionNo()) + 1);
            newConfig.setOptimizationScore(bestScore.score());
            newConfig.setEffectiveStartDate(LocalDate.now());
            newConfig.setEffectiveEndDate(null);
            newConfig.setStatus("active");
            materialForecastModelConfigMapper.insert(newConfig);

            PurchaseDemandForecastOptimizationLog log = new PurchaseDemandForecastOptimizationLog();
            log.setOrgId(orgId);
            log.setTenantId(tenantId);
            log.setMaterialCategory(currentConfig.getMaterialCategory());
            log.setMaterialSegment(segment);
            log.setModelType(bestScore.parameters().modelType());
            log.setCandidateConfigJson(toJson(candidates));
            log.setSelectedConfigJson(toJson(bestScore.parameters()));
            log.setScore(bestScore.score());
            log.setWape(bestScore.wape());
            log.setStockoutRate(bestScore.stockoutRate());
            log.setOversupplyRate(bestScore.oversupplyRate());
            log.setSampleForecastCount(bestScore.sampleCount());
            log.setVersionNo(newConfig.getVersionNo());
            log.setTriggerType("auto");
            log.setEffectiveDate(LocalDate.now());
            log.setOptimizedAt(LocalDateTime.now());
            log.setRollbackApplied(0);
            purchaseDemandForecastOptimizationLogMapper.insert(log);
        }
    }

    private boolean shouldApplyOptimizedConfig(
            MaterialForecastModelConfig currentConfig,
            OptimizationCandidateScore bestScore
    ) {
        BigDecimal currentScore = scaleRate(currentConfig.getOptimizationScore());
        if (currentScore.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return bestScore.score().compareTo(currentScore.subtract(BigDecimal.valueOf(0.001d))) < 0;
    }

    private void attemptRollbackOptimizedConfig(
            Long orgId,
            Long tenantId,
            String materialSegment,
            MaterialForecastModelConfig currentConfig
    ) {
        if (currentConfig == null || currentConfig.getId() == null) {
            return;
        }
        List<PurchaseDemandForecastOptimizationLog> recentLogs = purchaseDemandForecastOptimizationLogMapper.selectList(
                new LambdaQueryWrapper<PurchaseDemandForecastOptimizationLog>()
                        .eq(PurchaseDemandForecastOptimizationLog::getOrgId, orgId)
                        .eq(PurchaseDemandForecastOptimizationLog::getTenantId, tenantId)
                        .eq(PurchaseDemandForecastOptimizationLog::getMaterialSegment, materialSegment)
                        .orderByDesc(PurchaseDemandForecastOptimizationLog::getOptimizedAt, PurchaseDemandForecastOptimizationLog::getId)
                        .last("LIMIT 2")
        );
        if (recentLogs.size() < 2) {
            return;
        }
        BigDecimal latestScore = scaleRate(recentLogs.get(0).getScore());
        BigDecimal previousScore = scaleRate(recentLogs.get(1).getScore());
        BigDecimal currentScore = scaleRate(currentConfig.getOptimizationScore());
        if (!(latestScore.compareTo(previousScore) > 0 && currentScore.compareTo(latestScore) >= 0)) {
            return;
        }
        List<MaterialForecastModelConfig> historicalConfigs = materialForecastModelConfigMapper.selectList(
                new LambdaQueryWrapper<MaterialForecastModelConfig>()
                        .eq(MaterialForecastModelConfig::getOrgId, orgId)
                        .eq(MaterialForecastModelConfig::getTenantId, tenantId)
                        .isNull(MaterialForecastModelConfig::getMaterialId)
                        .eq(MaterialForecastModelConfig::getMaterialSegment, materialSegment)
                        .orderByDesc(MaterialForecastModelConfig::getVersionNo, MaterialForecastModelConfig::getId)
                        .last("LIMIT 3")
        );
        if (historicalConfigs.size() < 2) {
            return;
        }
        MaterialForecastModelConfig previousStable = historicalConfigs.stream()
                .filter(config -> config.getVersionNo() != null && currentConfig.getVersionNo() != null
                        && config.getVersionNo() < currentConfig.getVersionNo())
                .findFirst()
                .orElse(null);
        if (previousStable == null || previousStable.getId() == null) {
            return;
        }
        deactivateConfig(currentConfig);
        MaterialForecastModelConfig rollbackConfig = copyConfigWithParameters(previousStable, toModelParameters(previousStable, null));
        rollbackConfig.setId(null);
        rollbackConfig.setVersionNo((currentConfig.getVersionNo() == null ? 1 : currentConfig.getVersionNo()) + 1);
        rollbackConfig.setOptimizationScore(scaleRate(previousStable.getOptimizationScore()));
        rollbackConfig.setSourceType("rollback");
        rollbackConfig.setEffectiveStartDate(LocalDate.now());
        rollbackConfig.setEffectiveEndDate(null);
        rollbackConfig.setStatus("active");
        rollbackConfig.setRemark("连续两期优化评分变差，自动回退到稳定版本");
        materialForecastModelConfigMapper.insert(rollbackConfig);

        PurchaseDemandForecastOptimizationLog rollbackLog = new PurchaseDemandForecastOptimizationLog();
        rollbackLog.setOrgId(orgId);
        rollbackLog.setTenantId(tenantId);
        rollbackLog.setMaterialCategory(currentConfig.getMaterialCategory());
        rollbackLog.setMaterialSegment(materialSegment);
        rollbackLog.setModelType(rollbackConfig.getModelType());
        rollbackLog.setSelectedConfigJson(toJson(toModelParameters(rollbackConfig, null)));
        rollbackLog.setScore(scaleRate(previousStable.getOptimizationScore()));
        rollbackLog.setWape(recentLogs.get(0).getWape());
        rollbackLog.setStockoutRate(recentLogs.get(0).getStockoutRate());
        rollbackLog.setOversupplyRate(recentLogs.get(0).getOversupplyRate());
        rollbackLog.setSampleForecastCount(recentLogs.get(0).getSampleForecastCount());
        rollbackLog.setVersionNo(rollbackConfig.getVersionNo());
        rollbackLog.setTriggerType("rollback");
        rollbackLog.setEffectiveDate(LocalDate.now());
        rollbackLog.setOptimizedAt(LocalDateTime.now());
        rollbackLog.setRollbackApplied(1);
        rollbackLog.setPreviousVersionNo(currentConfig.getVersionNo());
        rollbackLog.setPreviousScore(currentScore);
        rollbackLog.setRollbackReason("最近两期优化评分连续变差，已回退到上一稳定版本");
        purchaseDemandForecastOptimizationLogMapper.insert(rollbackLog);
    }

    private void deactivateConfig(MaterialForecastModelConfig currentConfig) {
        if (currentConfig == null || currentConfig.getId() == null) {
            return;
        }
        currentConfig.setStatus("inactive");
        currentConfig.setEffectiveEndDate(LocalDate.now().minusDays(1));
        materialForecastModelConfigMapper.updateById(currentConfig);
    }

    private MaterialForecastModelConfig copyConfigWithParameters(
            MaterialForecastModelConfig source,
            PurchaseDemandForecastRuleEngine.ModelParameters parameters
    ) {
        MaterialForecastModelConfig target = new MaterialForecastModelConfig();
        target.setOrgId(source.getOrgId());
        target.setTenantId(source.getTenantId());
        target.setMaterialId(source.getMaterialId());
        target.setMaterialCategory(source.getMaterialCategory());
        target.setMaterialSegment(parameters.materialSegment());
        target.setModelType(parameters.modelType());
        target.setModelWeight(scaleRate(parameters.modelWeight()));
        target.setSafetyDays(parameters.safetyDays());
        target.setLeadTimeDays(parameters.leadTimeDays());
        target.setServiceLevel(scaleRate(parameters.serviceLevel()));
        target.setAlphaValue(scaleRate(parameters.alphaValue()));
        target.setBetaValue(scaleRate(parameters.betaValue()));
        target.setHolidayFactor(scaleRate(parameters.holidayFactor()));
        target.setWeekendFactor(scaleRate(parameters.weekendFactor()));
        target.setActivityFactor(scaleRate(parameters.activityFactor()));
        target.setMaxCoverageDays(parameters.maxCoverageDays());
        target.setRecipeCorrectionMin(scaleRate(parameters.recipeCorrectionMin()));
        target.setRecipeCorrectionMax(scaleRate(parameters.recipeCorrectionMax()));
        target.setMinOrderQty(scaleQuantity(parameters.minOrderQty()));
        target.setPackSize(scaleQuantity(parameters.packSize()));
        target.setLeadTimeRiskFactor(scaleRate(parameters.leadTimeRiskFactor()));
        target.setShortagePenalty(scaleRate(parameters.shortagePenalty()));
        target.setHoldingCostRate(scaleRate(parameters.holdingCostRate()));
        target.setWasteCostRate(scaleRate(parameters.wasteCostRate()));
        target.setOrderCost(scaleAmount(parameters.orderCost()));
        target.setSourceType(parameters.sourceType());
        target.setRemark("自动优化生成");
        return target;
    }

    private List<PurchaseDemandForecastRuleEngine.ModelParameters> buildCandidateParameters(
            PurchaseDemandForecastRuleEngine.ModelParameters base
    ) {
        List<PurchaseDemandForecastRuleEngine.ModelParameters> candidates = new ArrayList<>();
        candidates.add(base);
        candidates.add(new PurchaseDemandForecastRuleEngine.ModelParameters(
                base.materialSegment(),
                base.modelType(),
                base.modelWeight(),
                Math.max(1, (base.safetyDays() == null ? 3 : base.safetyDays()) + 1),
                Math.max(1, base.leadTimeDays() == null ? 3 : base.leadTimeDays()),
                boundedRate(base.serviceLevel(), 0.02d),
                lowerBoundRate(base.alphaValue(), -0.05d, 0.15d),
                boundedRate(base.betaValue(), 0.02d),
                boundedRate(base.holidayFactor(), 0.03d),
                base.weekendFactor(),
                boundedRate(base.activityFactor(), 0.05d),
                Math.max(1, (base.maxCoverageDays() == null ? 7 : base.maxCoverageDays()) + 1),
                lowerBoundRate(base.recipeCorrectionMin(), -0.02d, 0.50d),
                boundedRate(base.recipeCorrectionMax(), 0.02d),
                scaleQuantity(base.minOrderQty()),
                scaleQuantity(base.packSize()),
                boundedRate(base.leadTimeRiskFactor(), 0.05d, 0.50d),
                scaleRate(base.shortagePenalty()),
                scaleRate(base.holdingCostRate()),
                scaleRate(base.wasteCostRate()),
                scaleAmount(base.orderCost()),
                base.optimizationVersion(),
                base.optimizationScore(),
                "auto"
        ));
        candidates.add(new PurchaseDemandForecastRuleEngine.ModelParameters(
                base.materialSegment(),
                base.modelType(),
                base.modelWeight(),
                Math.max(1, (base.safetyDays() == null ? 3 : base.safetyDays()) - 1),
                Math.max(1, base.leadTimeDays() == null ? 3 : base.leadTimeDays()),
                lowerBoundRate(base.serviceLevel(), -0.03d, 0.70d),
                boundedRate(base.alphaValue(), 0.05d),
                lowerBoundRate(base.betaValue(), -0.02d, 0.03d),
                lowerBoundRate(base.holidayFactor(), -0.03d, 0.80d),
                base.weekendFactor(),
                lowerBoundRate(base.activityFactor(), -0.05d, 0.80d),
                Math.max(1, (base.maxCoverageDays() == null ? 7 : base.maxCoverageDays()) - 1),
                lowerBoundRate(base.recipeCorrectionMin(), 0.02d, 0.50d),
                lowerBoundRate(base.recipeCorrectionMax(), -0.02d, 0.80d),
                scaleQuantity(base.minOrderQty()),
                scaleQuantity(base.packSize()),
                lowerBoundRate(base.leadTimeRiskFactor(), -0.05d, 0.50d),
                scaleRate(base.shortagePenalty()),
                scaleRate(base.holdingCostRate()),
                scaleRate(base.wasteCostRate()),
                scaleAmount(base.orderCost()),
                base.optimizationVersion(),
                base.optimizationScore(),
                "auto"
        ));
        return candidates;
    }

    private BigDecimal boundedRate(BigDecimal base, double delta) {
        return boundedRate(base, delta, 0.99d);
    }

    private BigDecimal boundedRate(BigDecimal base, double delta, double min) {
        BigDecimal value = scaleRate(base == null ? BigDecimal.ZERO : base).add(BigDecimal.valueOf(delta));
        return value.max(BigDecimal.valueOf(min)).min(BigDecimal.valueOf(1.50d));
    }

    private BigDecimal lowerBoundRate(BigDecimal base, double delta, double min) {
        BigDecimal value = scaleRate(base == null ? BigDecimal.ZERO : base).add(BigDecimal.valueOf(delta));
        return value.max(BigDecimal.valueOf(min)).min(BigDecimal.valueOf(1.50d));
    }

    private Map<String, MaterialForecastModelConfig> ensureActiveSegmentModelConfigs(
            Long orgId,
            Long tenantId,
            Map<Long, FeatureMetric> featureMetricMap,
            Map<Long, MaterialProfile> materialProfiles
    ) {
        Map<String, MaterialForecastModelConfig> activeConfigMap = loadActiveSegmentConfigMap(orgId, tenantId);
        for (Map.Entry<Long, FeatureMetric> entry : featureMetricMap.entrySet()) {
            Long materialId = entry.getKey();
            FeatureMetric featureMetric = entry.getValue();
            String segment = featureMetric.materialSegment();
            if (StrUtil.isBlank(segment) || activeConfigMap.containsKey(segment)) {
                continue;
            }
            MaterialProfile profile = materialProfiles.get(materialId);
            PurchaseDemandForecastRuleEngine.MaterialRuleContext context = buildMaterialRuleContext(
                    materialId,
                    profile,
                    featureMetric,
                    RECIPE_LOOKAHEAD_DAYS,
                    BigDecimal.ONE,
                    featureMetric == null ? BigDecimal.ONE : BigDecimal.ONE,
                    featureMetric == null ? BigDecimal.ONE : BigDecimal.ONE,
                    null,
                    null
            );
            PurchaseDemandForecastRuleEngine.ModelParameters defaults =
                    purchaseDemandForecastRuleEngine.resolveDefaultParameters(context);
            MaterialForecastModelConfig config = new MaterialForecastModelConfig();
            config.setOrgId(orgId);
            config.setTenantId(tenantId);
            config.setMaterialCategory(profile == null ? null : profile.materialCategory());
            config.setMaterialSegment(defaults.materialSegment());
            config.setModelType(defaults.modelType());
            config.setModelWeight(scaleRate(defaults.modelWeight()));
            config.setSafetyDays(defaults.safetyDays());
            config.setLeadTimeDays(defaults.leadTimeDays());
            config.setServiceLevel(scaleRate(defaults.serviceLevel()));
            config.setAlphaValue(scaleRate(defaults.alphaValue()));
            config.setBetaValue(scaleRate(defaults.betaValue()));
            config.setHolidayFactor(scaleRate(defaults.holidayFactor()));
            config.setWeekendFactor(scaleRate(defaults.weekendFactor()));
            config.setActivityFactor(scaleRate(defaults.activityFactor()));
            config.setMaxCoverageDays(defaults.maxCoverageDays());
            config.setRecipeCorrectionMin(scaleRate(defaults.recipeCorrectionMin()));
            config.setRecipeCorrectionMax(scaleRate(defaults.recipeCorrectionMax()));
            config.setMinOrderQty(scaleQuantity(defaults.minOrderQty()));
            config.setPackSize(scaleQuantity(defaults.packSize()));
            config.setLeadTimeRiskFactor(scaleRate(defaults.leadTimeRiskFactor()));
            config.setShortagePenalty(scaleRate(defaults.shortagePenalty()));
            config.setHoldingCostRate(scaleRate(defaults.holdingCostRate()));
            config.setWasteCostRate(scaleRate(defaults.wasteCostRate()));
            config.setOrderCost(scaleAmount(defaults.orderCost()));
            config.setOptimizationScore(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
            config.setVersionNo(1);
            config.setSourceType(defaults.sourceType());
            config.setEffectiveStartDate(LocalDate.now());
            config.setStatus("active");
            config.setRemark("二期默认配置初始化");
            materialForecastModelConfigMapper.insert(config);
            activeConfigMap.put(defaults.materialSegment(), config);
        }
        return activeConfigMap;
    }

    private Map<String, MaterialForecastModelConfig> loadActiveSegmentConfigMap(Long orgId, Long tenantId) {
        List<MaterialForecastModelConfig> configs = materialForecastModelConfigMapper.selectList(
                new LambdaQueryWrapper<MaterialForecastModelConfig>()
                        .eq(MaterialForecastModelConfig::getOrgId, orgId)
                        .eq(MaterialForecastModelConfig::getTenantId, tenantId)
                        .isNull(MaterialForecastModelConfig::getMaterialId)
                        .eq(MaterialForecastModelConfig::getStatus, "active")
                        .orderByDesc(MaterialForecastModelConfig::getVersionNo, MaterialForecastModelConfig::getId)
        );
        Map<String, MaterialForecastModelConfig> result = new LinkedHashMap<>();
        for (MaterialForecastModelConfig config : configs) {
            if (config == null || StrUtil.isBlank(config.getMaterialSegment())) {
                continue;
            }
            result.putIfAbsent(config.getMaterialSegment(), config);
        }
        return result;
    }

    private MaterialForecastModelConfig resolveSegmentModelConfig(
            Long orgId,
            Long tenantId,
            MaterialProfile profile,
            FeatureMetric featureMetric,
            Map<String, MaterialForecastModelConfig> activeConfigMap
    ) {
        if (featureMetric != null && StrUtil.isNotBlank(featureMetric.materialSegment())) {
            MaterialForecastModelConfig config = activeConfigMap.get(featureMetric.materialSegment());
            if (config != null) {
                return config;
            }
        }
        Map<Long, FeatureMetric> singletonFeatureMap = new LinkedHashMap<>();
        singletonFeatureMap.put(profile == null ? 0L : profile.materialId(), featureMetric == null ? FeatureMetric.empty() : featureMetric);
        Map<Long, MaterialProfile> singletonProfileMap = new LinkedHashMap<>();
        if (profile != null && profile.materialId() != null) {
            singletonProfileMap.put(profile.materialId(), profile);
        }
        return ensureActiveSegmentModelConfigs(orgId, tenantId, singletonFeatureMap, singletonProfileMap)
                .get(featureMetric == null ? PurchaseDemandForecastRuleEngine.SEGMENT_STABLE : featureMetric.materialSegment());
    }

    private PurchaseDemandForecastRuleEngine.MaterialRuleContext buildMaterialRuleContext(
            Long materialId,
            MaterialProfile profile,
            FeatureMetric featureMetric,
            int forecastDays,
            BigDecimal calendarFactor,
            BigDecimal holidayFactor,
            BigDecimal activityFactor,
            MaterialForecastModelConfig modelConfig,
            BigDecimal volatileMlForecastQty
    ) {
        FeatureMetric normalizedFeature = featureMetric == null ? FeatureMetric.empty() : featureMetric;
        return new PurchaseDemandForecastRuleEngine.MaterialRuleContext(
                materialId,
                profile == null ? "" : profile.materialName(),
                profile == null ? "" : profile.materialCategory(),
                profile == null ? "" : profile.materialUnit(),
                profile == null ? null : profile.shelfLifeDays(),
                profile == null ? null : profile.warningDays(),
                forecastDays,
                normalizedFeature.consumptionDays30d(),
                normalizedFeature.currentStockQty(),
                normalizedFeature.availableStockQty(),
                normalizedFeature.avgConsumption7d(),
                normalizedFeature.avgConsumption14d(),
                normalizedFeature.avgConsumption30d(),
                normalizedFeature.stdConsumption30d(),
                normalizedFeature.recipeDemand7d(),
                normalizedFeature.recipeHistory30d(),
                normalizedFeature.actualConsumption30d(),
                normalizedFeature.pendingPlanQty(),
                normalizedFeature.inTransitQty(),
                profile == null ? ZERO_QUANTITY : profile.minStock(),
                profile == null ? ZERO_QUANTITY : profile.maxStock(),
                calendarFactor,
                holidayFactor,
                activityFactor,
                normalizedFeature.recipeDriveRatio(),
                normalizedFeature.demandActiveRatio(),
                normalizedFeature.demandCv(),
                normalizedFeature.activitySensitivity(),
                normalizedFeature.materialSegment(),
                modelConfig == null ? normalizedFeature.leadTimeDays() : modelConfig.getLeadTimeDays(),
                modelConfig == null ? normalizedFeature.serviceLevel() : modelConfig.getServiceLevel(),
                normalizedFeature.inventoryTurnoverDays(),
                volatileMlForecastQty,
                normalizedFeature.recentConsumptionSeries30d()
        );
    }

    private PurchaseDemandForecastRuleEngine.ModelParameters toModelParameters(
            MaterialForecastModelConfig modelConfig,
            PurchaseDemandForecastRuleEngine.MaterialRuleContext context
    ) {
        if (modelConfig == null) {
            return purchaseDemandForecastRuleEngine.resolveDefaultParameters(context);
        }
        return new PurchaseDemandForecastRuleEngine.ModelParameters(
                modelConfig.getMaterialSegment(),
                modelConfig.getModelType(),
                scaleRate(modelConfig.getModelWeight()),
                modelConfig.getSafetyDays(),
                modelConfig.getLeadTimeDays(),
                scaleRate(modelConfig.getServiceLevel()),
                scaleRate(modelConfig.getAlphaValue()),
                scaleRate(modelConfig.getBetaValue()),
                scaleRate(modelConfig.getHolidayFactor()),
                scaleRate(modelConfig.getWeekendFactor()),
                scaleRate(modelConfig.getActivityFactor()),
                modelConfig.getMaxCoverageDays(),
                scaleRate(modelConfig.getRecipeCorrectionMin()),
                scaleRate(modelConfig.getRecipeCorrectionMax()),
                scaleQuantity(modelConfig.getMinOrderQty()),
                scaleQuantity(modelConfig.getPackSize()),
                scaleRate(modelConfig.getLeadTimeRiskFactor()),
                scaleRate(modelConfig.getShortagePenalty()),
                scaleRate(modelConfig.getHoldingCostRate()),
                scaleRate(modelConfig.getWasteCostRate()),
                scaleAmount(modelConfig.getOrderCost()),
                modelConfig.getVersionNo(),
                scaleRate(modelConfig.getOptimizationScore()),
                modelConfig.getSourceType()
        );
    }

    private String buildFeatureSnapshotJson(
            PurchaseDemandForecastRuleEngine.MaterialRuleContext context,
            FeatureMetric featureMetric
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("materialId", context.materialId());
        snapshot.put("materialName", context.materialName());
        snapshot.put("materialCategory", context.materialCategory());
        snapshot.put("materialUnit", context.materialUnit());
        snapshot.put("shelfLifeDays", context.shelfLifeDays());
        snapshot.put("warningDays", context.warningDays());
        snapshot.put("forecastDays", context.forecastDays());
        snapshot.put("consumptionDays30d", context.consumptionDays30d());
        snapshot.put("currentStockQty", context.currentStockQty());
        snapshot.put("availableStockQty", context.availableStockQty());
        snapshot.put("avgConsumption7d", context.avgConsumption7d());
        snapshot.put("avgConsumption14d", context.avgConsumption14d());
        snapshot.put("avgConsumption30d", context.avgConsumption30d());
        snapshot.put("stdConsumption30d", context.stdConsumption30d());
        snapshot.put("recipeDemand7d", context.recipeDemand7d());
        snapshot.put("recipeHistory30d", context.recipeHistory30d());
        snapshot.put("actualConsumption30d", context.actualConsumption30d());
        snapshot.put("pendingPlanQty", context.pendingPlanQty());
        snapshot.put("inTransitQty", context.inTransitQty());
        snapshot.put("minStock", context.minStock());
        snapshot.put("maxStock", context.maxStock());
        snapshot.put("calendarFactor", context.calendarFactor());
        snapshot.put("holidayFactor", context.holidayFactor());
        snapshot.put("activityFactor", context.activityFactor());
        snapshot.put("recipeDriveRatio", context.recipeDriveRatio());
        snapshot.put("demandActiveRatio", context.demandActiveRatio());
        snapshot.put("demandCv", context.demandCv());
        snapshot.put("activitySensitivity", context.activitySensitivity());
        snapshot.put("materialSegment", context.materialSegment());
        snapshot.put("leadTimeDays", context.leadTimeDays());
        snapshot.put("serviceLevel", context.serviceLevel());
        snapshot.put("inventoryTurnoverDays", context.inventoryTurnoverDays());
        snapshot.put("volatileMlForecastQty", context.volatileMlForecastQty());
        snapshot.put("recentConsumptionSeries30d", context.recentConsumptionSeries30d());
        snapshot.put("featureForecastType", featureMetric == null ? null : featureMetric.forecastType());
        return toJson(snapshot);
    }

    @SuppressWarnings("unchecked")
    private PurchaseDemandForecastRuleEngine.MaterialRuleContext parseRuleContextFromSnapshotJson(String featureSnapshotJson) {
        if (StrUtil.isBlank(featureSnapshotJson)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(featureSnapshotJson, Map.class);
            List<BigDecimal> series = new ArrayList<>();
            Object seriesObj = map.get("recentConsumptionSeries30d");
            if (seriesObj instanceof List<?> list) {
                for (Object value : list) {
                    series.add(scaleQuantity(toBigDecimal(value)));
                }
            }
            return new PurchaseDemandForecastRuleEngine.MaterialRuleContext(
                    toLong(map.get("materialId")),
                    asString(map.get("materialName")),
                    asString(map.get("materialCategory")),
                    asString(map.get("materialUnit")),
                    toInteger(map.get("shelfLifeDays")),
                    toInteger(map.get("warningDays")),
                    toInteger(map.get("forecastDays")),
                    toInteger(map.get("consumptionDays30d")),
                    toBigDecimal(map.get("currentStockQty")),
                    toBigDecimal(map.get("availableStockQty")),
                    toBigDecimal(map.get("avgConsumption7d")),
                    toBigDecimal(map.get("avgConsumption14d")),
                    toBigDecimal(map.get("avgConsumption30d")),
                    toBigDecimal(map.get("stdConsumption30d")),
                    toBigDecimal(map.get("recipeDemand7d")),
                    toBigDecimal(map.get("recipeHistory30d")),
                    toBigDecimal(map.get("actualConsumption30d")),
                    toBigDecimal(map.get("pendingPlanQty")),
                    toBigDecimal(map.get("inTransitQty")),
                    toBigDecimal(map.get("minStock")),
                    toBigDecimal(map.get("maxStock")),
                    toBigDecimal(map.get("calendarFactor")),
                    toBigDecimal(map.get("holidayFactor")),
                    toBigDecimal(map.get("activityFactor")),
                    toBigDecimal(map.get("recipeDriveRatio")),
                    toBigDecimal(map.get("demandActiveRatio")),
                    toBigDecimal(map.get("demandCv")),
                    toBigDecimal(map.get("activitySensitivity")),
                    asString(map.get("materialSegment")),
                    toInteger(map.get("leadTimeDays")),
                    toBigDecimal(map.get("serviceLevel")),
                    toBigDecimal(map.get("inventoryTurnoverDays")),
                    toBigDecimal(map.get("volatileMlForecastQty")),
                    series
            );
        } catch (Exception ex) {
            log.warn("解析采购需求预测特征快照失败: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<PurchaseDemandForecastItemVO.ExplanationFactorVO> parseExplanationFactorsJson(String explanationFactorsJson) {
        if (StrUtil.isBlank(explanationFactorsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(explanationFactorsJson, List.class);
            List<PurchaseDemandForecastItemVO.ExplanationFactorVO> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                PurchaseDemandForecastItemVO.ExplanationFactorVO factorVO = new PurchaseDemandForecastItemVO.ExplanationFactorVO();
                factorVO.setLabel(asString(row.get("label")));
                factorVO.setValue(asString(row.get("value")));
                factorVO.setDescription(asString(row.get("description")));
                result.add(factorVO);
            }
            return result;
        } catch (Exception ex) {
            log.warn("解析采购需求预测解释证据失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private PurchaseDemandForecastRuleEngine.ModelParameters overrideLeadTimeParameter(
            PurchaseDemandForecastRuleEngine.ModelParameters base,
            SupplierRecommendation supplierRecommendation
    ) {
        if (base == null || supplierRecommendation == null || supplierRecommendation.isEmpty()) {
            return base;
        }
        BigDecimal effectiveLeadTime = resolveEffectiveLeadTimeDays(
                supplierRecommendation.avgLeadTimeDays(),
                supplierRecommendation.stdLeadTimeDays(),
                base.leadTimeRiskFactor()
        );
        int adjustedLeadTimeDays = Math.max(1, effectiveLeadTime.setScale(0, RoundingMode.CEILING).intValue());
        return new PurchaseDemandForecastRuleEngine.ModelParameters(
                base.materialSegment(),
                base.modelType(),
                base.modelWeight(),
                base.safetyDays(),
                adjustedLeadTimeDays,
                base.serviceLevel(),
                base.alphaValue(),
                base.betaValue(),
                base.holidayFactor(),
                base.weekendFactor(),
                base.activityFactor(),
                base.maxCoverageDays(),
                base.recipeCorrectionMin(),
                base.recipeCorrectionMax(),
                base.minOrderQty(),
                base.packSize(),
                base.leadTimeRiskFactor(),
                base.shortagePenalty(),
                base.holdingCostRate(),
                base.wasteCostRate(),
                base.orderCost(),
                base.optimizationVersion(),
                base.optimizationScore(),
                base.sourceType()
        );
    }

    private PhaseThreeDecision buildPhaseThreeDecision(
            MaterialProfile profile,
            FeatureMetric featureMetric,
            PurchaseDemandForecastRuleEngine.RuleResult ruleResult,
            PurchaseDemandForecastRuleEngine.ModelParameters modelParameters,
            SupplierRecommendation supplierRecommendation,
            BigDecimal estimatedUnitPrice,
            int forecastDays
    ) {
        BigDecimal forecastDemandQty = scaleQuantity(ruleResult == null ? ZERO_QUANTITY : ruleResult.forecastDemandQty());
        int reviewPeriodDays = Math.max(1, forecastDays);
        BigDecimal avgDailyDemandQty = scaleQuantity(
                reviewPeriodDays <= 0
                        ? forecastDemandQty
                        : divide(forecastDemandQty, BigDecimal.valueOf(reviewPeriodDays), 3)
        );
        BigDecimal safetyStockQty = scaleQuantity(ruleResult == null ? ZERO_QUANTITY : ruleResult.safetyStockQty());
        BigDecimal availableStockQty = scaleQuantity(featureMetric == null ? ZERO_QUANTITY : featureMetric.availableStockQty());
        BigDecimal currentStockQty = scaleQuantity(featureMetric == null ? ZERO_QUANTITY : featureMetric.currentStockQty());
        BigDecimal inTransitQty = scaleQuantity(featureMetric == null ? ZERO_QUANTITY : featureMetric.inTransitQty());
        BigDecimal pendingPlanQty = scaleQuantity(featureMetric == null ? ZERO_QUANTITY : featureMetric.pendingPlanQty());
        BigDecimal inventoryPositionQty = scaleQuantity(availableStockQty.add(inTransitQty).add(pendingPlanQty));
        BigDecimal effectiveLeadTimeDays = resolveEffectiveLeadTimeDays(
                supplierRecommendation.avgLeadTimeDays(),
                supplierRecommendation.stdLeadTimeDays(),
                modelParameters == null ? null : modelParameters.leadTimeRiskFactor()
        );
        if (effectiveLeadTimeDays.compareTo(BigDecimal.ZERO) <= 0 && ruleResult != null && ruleResult.leadTimeDays() != null) {
            effectiveLeadTimeDays = scaleRate(ruleResult.leadTimeDays());
        }
        if (effectiveLeadTimeDays.compareTo(BigDecimal.ZERO) <= 0) {
            effectiveLeadTimeDays = BigDecimal.valueOf(3L).setScale(6, RoundingMode.HALF_UP);
        }

        BigDecimal reorderPointQty = scaleQuantity(
                avgDailyDemandQty.multiply(effectiveLeadTimeDays).add(safetyStockQty)
        );
        BigDecimal targetStockQty = scaleQuantity(
                avgDailyDemandQty.multiply(effectiveLeadTimeDays.add(BigDecimal.valueOf(reviewPeriodDays))).add(safetyStockQty)
        );
        BigDecimal theoreticalSuggestedQty = scaleQuantity(max(targetStockQty.subtract(inventoryPositionQty), ZERO_QUANTITY));

        BigDecimal minOrderQty = resolveConfiguredMinOrderQty(profile == null ? "" : profile.materialUnit(), modelParameters == null ? null : modelParameters.minOrderQty());
        BigDecimal packSize = resolveConfiguredPackSize(profile == null ? "" : profile.materialUnit(), modelParameters == null ? null : modelParameters.packSize());
        BigDecimal batchAdjustedQty = applyBatchOptimization(profile == null ? "" : profile.materialUnit(), theoreticalSuggestedQty, minOrderQty, packSize);
        int maxCoverageDays = resolvePhaseThreeMaxCoverageDays(profile, modelParameters);
        BigDecimal maxAllowedStockQty = scaleQuantity(avgDailyDemandQty.multiply(BigDecimal.valueOf(Math.max(1, maxCoverageDays))));

        List<String> riskFlags = new ArrayList<>();
        BigDecimal finalSuggestedQty = batchAdjustedQty;
        BigDecimal capQty = maxAllowedStockQty.compareTo(BigDecimal.ZERO) <= 0
                ? batchAdjustedQty
                : scaleQuantity(max(maxAllowedStockQty.subtract(currentStockQty), ZERO_QUANTITY));
        if (maxAllowedStockQty.compareTo(BigDecimal.ZERO) > 0 && currentStockQty.add(finalSuggestedQty).compareTo(maxAllowedStockQty) > 0) {
            BigDecimal alignedCapQty = alignQtyByCap(profile == null ? "" : profile.materialUnit(), capQty, packSize);
            if (alignedCapQty.compareTo(finalSuggestedQty) < 0) {
                finalSuggestedQty = alignedCapQty;
                riskFlags.add("高损耗风险");
            }
            if (alignedCapQty.compareTo(capQty) < 0 && packSize.compareTo(BigDecimal.ZERO) > 0) {
                riskFlags.add("包装约束冲突");
            }
        }
        if (profile != null && scaleQuantity(profile.maxStock()).compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal stockCapQty = scaleQuantity(max(scaleQuantity(profile.maxStock()).subtract(currentStockQty), ZERO_QUANTITY));
            if (finalSuggestedQty.compareTo(stockCapQty) > 0) {
                finalSuggestedQty = alignQtyByCap(profile.materialUnit(), stockCapQty, packSize);
                riskFlags.add("最高库存约束");
            }
        }
        finalSuggestedQty = normalizeSuggestedQtyByUnit(profile == null ? "" : profile.materialUnit(), finalSuggestedQty);

        boolean orderNow = inventoryPositionQty.compareTo(reorderPointQty) <= 0 && finalSuggestedQty.compareTo(ZERO_QUANTITY) > 0;
        String orderAction = finalSuggestedQty.compareTo(ZERO_QUANTITY) <= 0
                ? ORDER_ACTION_HOLD
                : (orderNow ? ORDER_ACTION_NOW : ORDER_ACTION_PLAN);

        BigDecimal referenceUnitPrice = firstPositive(scaleAmount(estimatedUnitPrice), BigDecimal.ONE);
        BigDecimal shortageQty = scaleQuantity(max(reorderPointQty.subtract(inventoryPositionQty), ZERO_QUANTITY));
        BigDecimal holdingInventoryQty = scaleQuantity(currentStockQty.add(finalSuggestedQty.divide(BigDecimal.valueOf(2L), 3, RoundingMode.HALF_UP)));
        BigDecimal expiryRiskQty = scaleQuantity(max(currentStockQty.add(batchAdjustedQty).subtract(maxAllowedStockQty), ZERO_QUANTITY));
        BigDecimal shortageCost = calculatePhaseThreeCost(shortageQty, referenceUnitPrice, modelParameters == null ? null : modelParameters.shortagePenalty());
        BigDecimal holdingCost = calculatePhaseThreeCost(holdingInventoryQty, referenceUnitPrice, modelParameters == null ? null : modelParameters.holdingCostRate());
        BigDecimal expiryRiskCost = calculatePhaseThreeCost(expiryRiskQty, referenceUnitPrice, modelParameters == null ? null : modelParameters.wasteCostRate());
        BigDecimal orderProcessingCost = finalSuggestedQty.compareTo(ZERO_QUANTITY) > 0
                ? scaleAmount(modelParameters == null ? BigDecimal.valueOf(30.00d) : modelParameters.orderCost())
                : ZERO_AMOUNT;
        BigDecimal purchasePriceCost = scaleAmount(finalSuggestedQty.multiply(referenceUnitPrice));
        BigDecimal totalCost = scaleAmount(shortageCost.add(holdingCost).add(expiryRiskCost).add(orderProcessingCost).add(purchasePriceCost));

        if (supplierRecommendation == null || supplierRecommendation.isEmpty()) {
            riskFlags.add("缺少供应商建议");
        }
        if (effectiveLeadTimeDays.compareTo(BigDecimal.valueOf(Math.max(1, ruleResult == null || ruleResult.leadTimeDays() == null ? 3 : ruleResult.leadTimeDays().intValue()) + 2L)) >= 0) {
            riskFlags.add("提前期波动偏高");
        }
        if (finalSuggestedQty.compareTo(ZERO_QUANTITY) <= 0 && theoreticalSuggestedQty.compareTo(ZERO_QUANTITY) > 0) {
            riskFlags.add("建议人工确认");
        }

        return new PhaseThreeDecision(
                avgDailyDemandQty,
                reviewPeriodDays,
                reorderPointQty,
                targetStockQty,
                inventoryPositionQty,
                theoreticalSuggestedQty,
                finalSuggestedQty,
                minOrderQty,
                packSize,
                maxCoverageDays,
                maxAllowedStockQty,
                scaleRate(effectiveLeadTimeDays),
                supplierRecommendation.supplierId(),
                supplierRecommendation.supplierName(),
                scaleRate(supplierRecommendation.score()),
                scaleRate(supplierRecommendation.fillRate()),
                scaleRate(supplierRecommendation.onTimeRate()),
                orderNow,
                orderAction,
                shortageCost,
                holdingCost,
                expiryRiskCost,
                orderProcessingCost,
                purchasePriceCost,
                totalCost,
                String.join("、", riskFlags)
        );
    }

    private ConfidenceRange buildPhaseThreeConfidenceRange(
            String unit,
            PurchaseDemandForecastRuleEngine.RuleResult ruleResult,
            BigDecimal finalSuggestedQty
    ) {
        BigDecimal normalizedFinalQty = normalizeSuggestedQtyByUnit(unit, finalSuggestedQty);
        if (ruleResult == null || normalizedFinalQty.compareTo(ZERO_QUANTITY) <= 0) {
            return new ConfidenceRange(ZERO_QUANTITY, ZERO_QUANTITY);
        }
        BigDecimal baseSuggestedQty = scaleQuantity(ruleResult.suggestedPurchaseQty());
        if (baseSuggestedQty.compareTo(ZERO_QUANTITY) <= 0) {
            return new ConfidenceRange(normalizedFinalQty, normalizedFinalQty);
        }
        BigDecimal factor = normalizedFinalQty.divide(baseSuggestedQty, 6, RoundingMode.HALF_UP);
        BigDecimal lower = normalizeSuggestedQtyByUnit(unit, scaleQuantity(ruleResult.lowerBoundQty().multiply(factor)));
        BigDecimal upper = normalizeSuggestedQtyByUnit(unit, scaleQuantity(ruleResult.upperBoundQty().multiply(factor)));
        if (lower.compareTo(normalizedFinalQty) > 0) {
            lower = normalizedFinalQty;
        }
        if (upper.compareTo(normalizedFinalQty) < 0) {
            upper = normalizedFinalQty;
        }
        return new ConfidenceRange(lower, upper);
    }

    private ExplanationSnapshot enrichExplanationSnapshot(
            ExplanationSnapshot base,
            PhaseThreeDecision decision
    ) {
        if (base == null || decision == null) {
            return base;
        }
        String supplierText = StrUtil.isNotBlank(decision.supplierName()) ? "，推荐供应商" + decision.supplierName() : "";
        String summary = decision.finalSuggestedQty().compareTo(ZERO_QUANTITY) <= 0
                ? base.summary() + " 三期优化判断当前库存位置可支撑本周期，暂不建议下单。"
                : base.summary() + String.format(Locale.ROOT,
                " 三期优化建议%s%s，订货点%s，目标库存%s，最终建议量%s。",
                decision.orderAction(),
                supplierText,
                format(decision.reorderPointQty()),
                format(decision.targetStockQty()),
                format(decision.finalSuggestedQty()));
        StringBuilder detail = new StringBuilder(base.detail())
                .append(" 三期优化：库存位置=").append(format(decision.inventoryPositionQty()))
                .append("，有效提前期=").append(format(decision.effectiveLeadTimeDays()))
                .append("天，理论建议量=").append(format(decision.theoreticalSuggestedQty()))
                .append("，综合成本=").append(formatDecimal(decision.totalCost(), 2));
        if (StrUtil.isNotBlank(decision.riskFlags())) {
            detail.append("，风险标记：").append(decision.riskFlags());
        }
        String mergedFlags = mergeRiskFlags(base.anomalyFlags(), decision.riskFlags());
        return new ExplanationSnapshot(summary, detail.append("。").toString(), mergedFlags);
    }

    private BigDecimal resolveEffectiveLeadTimeDays(
            BigDecimal avgLeadTimeDays,
            BigDecimal stdLeadTimeDays,
            BigDecimal riskFactor
    ) {
        BigDecimal avgLead = positiveOrDefault(avgLeadTimeDays, BigDecimal.valueOf(3L));
        BigDecimal stdLead = max(scaleRate(stdLeadTimeDays), BigDecimal.ZERO);
        BigDecimal factor = positiveOrDefault(riskFactor, DEFAULT_LEAD_TIME_RISK_FACTOR);
        return scaleRate(avgLead.add(stdLead.multiply(factor)));
    }

    private BigDecimal resolveConfiguredMinOrderQty(String unit, BigDecimal configuredValue) {
        BigDecimal normalized = scaleQuantity(configuredValue);
        if (normalized.compareTo(BigDecimal.ZERO) > 0) {
            return normalizeSuggestedQtyByUnit(unit, normalized);
        }
        return shouldRoundSuggestedQty(unit) ? BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP) : ZERO_QUANTITY;
    }

    private BigDecimal resolveConfiguredPackSize(String unit, BigDecimal configuredValue) {
        BigDecimal normalized = scaleQuantity(configuredValue);
        if (normalized.compareTo(BigDecimal.ZERO) > 0) {
            return normalizeSuggestedQtyByUnit(unit, normalized);
        }
        return shouldRoundSuggestedQty(unit) ? BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP) : ZERO_QUANTITY;
    }

    private int resolvePhaseThreeMaxCoverageDays(
            MaterialProfile profile,
            PurchaseDemandForecastRuleEngine.ModelParameters modelParameters
    ) {
        if (modelParameters != null && modelParameters.maxCoverageDays() != null && modelParameters.maxCoverageDays() > 0) {
            return modelParameters.maxCoverageDays();
        }
        Integer shelfLifeDays = profile == null ? null : profile.shelfLifeDays();
        if (shelfLifeDays != null && shelfLifeDays > 0 && shelfLifeDays <= 3) {
            return Math.max(1, shelfLifeDays);
        }
        if (shelfLifeDays != null && shelfLifeDays > 0 && shelfLifeDays <= 7) {
            return Math.min(3, shelfLifeDays);
        }
        return 7;
    }

    private BigDecimal applyBatchOptimization(
            String unit,
            BigDecimal theoreticalQty,
            BigDecimal minOrderQty,
            BigDecimal packSize
    ) {
        BigDecimal normalizedTheoreticalQty = normalizeSuggestedQtyByUnit(unit, theoreticalQty);
        if (normalizedTheoreticalQty.compareTo(ZERO_QUANTITY) <= 0) {
            return ZERO_QUANTITY;
        }
        BigDecimal q1 = normalizedTheoreticalQty;
        if (minOrderQty != null && minOrderQty.compareTo(BigDecimal.ZERO) > 0) {
            q1 = max(q1, normalizeSuggestedQtyByUnit(unit, minOrderQty));
        }
        if (packSize == null || packSize.compareTo(BigDecimal.ZERO) <= 0) {
            return normalizeSuggestedQtyByUnit(unit, q1);
        }
        BigDecimal packCount = q1.divide(packSize, 0, RoundingMode.CEILING);
        return normalizeSuggestedQtyByUnit(unit, scaleQuantity(packCount.multiply(packSize)));
    }

    private BigDecimal alignQtyByCap(String unit, BigDecimal capQty, BigDecimal packSize) {
        BigDecimal normalizedCapQty = normalizeSuggestedQtyByUnit(unit, capQty);
        if (normalizedCapQty.compareTo(ZERO_QUANTITY) <= 0 || packSize == null || packSize.compareTo(BigDecimal.ZERO) <= 0) {
            return normalizedCapQty;
        }
        BigDecimal packCount = normalizedCapQty.divide(packSize, 0, RoundingMode.DOWN);
        BigDecimal alignedQty = scaleQuantity(packCount.multiply(packSize));
        if (alignedQty.compareTo(ZERO_QUANTITY) <= 0) {
            return normalizedCapQty;
        }
        return normalizeSuggestedQtyByUnit(unit, alignedQty);
    }

    private BigDecimal calculatePhaseThreeCost(BigDecimal qty, BigDecimal unitPrice, BigDecimal factor) {
        BigDecimal normalizedQty = scaleQuantity(qty);
        if (normalizedQty.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_AMOUNT;
        }
        BigDecimal normalizedFactor = positiveOrDefault(factor, BigDecimal.ONE);
        BigDecimal normalizedUnitPrice = positiveOrDefault(unitPrice, BigDecimal.ONE);
        return scaleAmount(normalizedQty.multiply(normalizedUnitPrice).multiply(normalizedFactor));
    }

    private BigDecimal positiveOrDefault(BigDecimal value, BigDecimal fallback) {
        if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
            return value;
        }
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    private String mergeRiskFlags(String left, String right) {
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        addRiskFlags(flags, left);
        addRiskFlags(flags, right);
        return String.join("、", flags);
    }

    private void addRiskFlags(Set<String> flags, String rawFlags) {
        if (flags == null || StrUtil.isBlank(rawFlags)) {
            return;
        }
        for (String value : rawFlags.split("[、,，]")) {
            if (StrUtil.isNotBlank(value)) {
                flags.add(value.trim());
            }
        }
    }

    private PurchaseDemandForecastExplanationEngine.ExplanationContext buildExplanationContext(
            MaterialProfile profile,
            FeatureMetric featureMetric,
            PurchaseDemandForecastRuleEngine.RuleResult ruleResult,
            PhaseThreeDecision phaseThreeDecision,
            ExplanationSnapshot explanationSnapshot,
            PreviousForecastSnapshot previousForecastSnapshot,
            InventoryAuditSnapshot inventoryAuditSnapshot,
            ActivityContext activityContext,
            int forecastDays
    ) {
        BigDecimal previousForecastQty = previousForecastSnapshot == null
                ? ZERO_QUANTITY
                : scaleQuantity(previousForecastSnapshot.forecastDemandQty());
        BigDecimal forecastDemandQty = ruleResult == null ? ZERO_QUANTITY : scaleQuantity(ruleResult.forecastDemandQty());
        BigDecimal previousForecastChangeRate = ratioDifference(forecastDemandQty, previousForecastQty);
        BigDecimal recipeHistoryWeeklyAvg = scaleQuantity(featureMetric == null
                ? ZERO_QUANTITY
                : divide(scaleQuantity(featureMetric.recipeHistory30d()), BigDecimal.valueOf(30L), 3)
                .multiply(BigDecimal.valueOf(RECIPE_LOOKAHEAD_DAYS)));
        BigDecimal recipeChangeRate = ratioDifference(
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.recipeDemand7d()),
                recipeHistoryWeeklyAvg
        );
        BigDecimal activityBaseline = max(
                scaleQuantity(featureMetric == null ? ZERO_QUANTITY : featureMetric.avgConsumption30d())
                        .multiply(BigDecimal.valueOf(Math.max(1, forecastDays))),
                previousForecastQty
        );
        BigDecimal activityLiftRate = activityBaseline.compareTo(ZERO_QUANTITY) > 0
                ? forecastDemandQty.subtract(activityBaseline).divide(activityBaseline, 6, RoundingMode.HALF_UP)
                : scaleFactor(activityContext == null ? BigDecimal.ONE : activityContext.factor()).subtract(BigDecimal.ONE);
        boolean manualReviewSuggested = (inventoryAuditSnapshot != null && inventoryAuditSnapshot.mismatchDetected())
                || StrUtil.containsAny(StrUtil.blankToDefault(phaseThreeDecision == null ? "" : phaseThreeDecision.riskFlags(), ""),
                "建议人工", "高损耗风险", "包装约束冲突", "最高库存约束");

        return new PurchaseDemandForecastExplanationEngine.ExplanationContext(
                profile == null ? "" : profile.materialName(),
                forecastDays,
                forecastDemandQty,
                previousForecastQty,
                scaleRate(previousForecastChangeRate),
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.avgConsumption30d()),
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.actualConsumption30d()),
                phaseThreeDecision == null ? ZERO_QUANTITY : scaleQuantity(phaseThreeDecision.avgDailyDemandQty()),
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.currentStockQty()),
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.availableStockQty()),
                phaseThreeDecision == null ? ZERO_QUANTITY : scaleQuantity(phaseThreeDecision.inventoryPositionQty()),
                ruleResult == null ? ZERO_QUANTITY : scaleQuantity(ruleResult.safetyStockQty()),
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.inTransitQty()),
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.pendingPlanQty()),
                phaseThreeDecision == null ? ZERO_QUANTITY : scaleQuantity(phaseThreeDecision.finalSuggestedQty()),
                phaseThreeDecision == null ? ZERO_QUANTITY : scaleQuantity(phaseThreeDecision.theoreticalSuggestedQty()),
                featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.recipeDemand7d()),
                featureMetric == null ? ZERO_QUANTITY : scaleRate(featureMetric.recipeDriveRatio()),
                scaleRate(recipeChangeRate),
                activityContext == null ? BigDecimal.ONE : scaleFactor(activityContext.factor()),
                scaleRate(activityLiftRate),
                activityContext == null ? Collections.emptyList() : activityContext.activityNames(),
                ruleResult != null && ruleResult.perishable(),
                phaseThreeDecision == null ? 0 : phaseThreeDecision.maxCoverageDays(),
                ruleResult == null ? "" : ruleResult.modelType(),
                normalizePriorityLabel(ruleResult == null ? "" : ruleResult.priorityLevel()),
                phaseThreeDecision == null ? "" : phaseThreeDecision.orderAction(),
                phaseThreeDecision == null ? "" : phaseThreeDecision.supplierName(),
                phaseThreeDecision == null ? "" : phaseThreeDecision.riskFlags(),
                explanationSnapshot == null ? "" : explanationSnapshot.anomalyFlags(),
                inventoryAuditSnapshot != null && inventoryAuditSnapshot.mismatchDetected(),
                manualReviewSuggested,
                phaseThreeDecision == null ? ZERO_AMOUNT : scaleAmount(phaseThreeDecision.totalCost())
        );
    }

    private Map<Long, PreviousForecastSnapshot> buildPreviousForecastSnapshotMap(
            Long orgId,
            Long tenantId,
            Collection<Long> materialIds,
            LocalDate basisDate
    ) {
        List<Long> validMaterialIds = normalizeMaterialIds(materialIds);
        if (validMaterialIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object> args = new ArrayList<>();
        args.add(orgId);
        args.add(tenantId);
        args.add(Date.valueOf(basisDate));
        args.addAll(validMaterialIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.material_id AS materialId, i.forecast_demand_qty AS forecastDemandQty, " +
                        "f.forecast_no AS forecastNo, f.basis_date AS basisDate " +
                        "FROM scm_purchase_demand_forecast_item i " +
                        "JOIN scm_purchase_demand_forecast f ON f.id = i.forecast_id " +
                        "WHERE f.deleted = 0 AND f.org_id = ? AND f.tenant_id = ? AND f.basis_date < ? " +
                        "AND i.material_id IN (" + placeholders(validMaterialIds.size()) + ") " +
                        "ORDER BY i.material_id ASC, f.basis_date DESC, f.id DESC",
                args.toArray()
        );
        Map<Long, PreviousForecastSnapshot> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (!isValidMaterialId(materialId) || result.containsKey(materialId)) {
                continue;
            }
            result.put(materialId, new PreviousForecastSnapshot(
                    asString(row.get("forecastNo")),
                    safeDate(row.get("basisDate")),
                    scaleQuantity(toBigDecimal(row.get("forecastDemandQty")))
            ));
        }
        return result;
    }

    private Map<Long, InventoryAuditSnapshot> buildInventoryAuditSnapshotMap(
            Long orgId,
            Long tenantId,
            Collection<Long> materialIds
    ) {
        List<Long> validMaterialIds = normalizeMaterialIds(materialIds);
        if (validMaterialIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object> args = new ArrayList<>();
        args.add(orgId);
        args.add(tenantId);
        args.addAll(validMaterialIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.material_id AS materialId, c.stat_date AS statDate, c.opening_stock_qty AS openingStockQty, " +
                        "c.inbound_qty AS inboundQty, c.outbound_qty AS outboundQty, c.adjustment_qty AS adjustmentQty, " +
                        "c.consumed_qty AS consumedQty, c.closing_stock_qty AS closingStockQty " +
                        "FROM scm_material_daily_consumption c " +
                        "JOIN (" +
                        "  SELECT material_id, MAX(stat_date) AS statDate " +
                        "  FROM scm_material_daily_consumption WHERE org_id = ? AND tenant_id = ? " +
                        "  AND material_id IN (" + placeholders(validMaterialIds.size()) + ") GROUP BY material_id" +
                        ") latest ON latest.material_id = c.material_id AND latest.statDate = c.stat_date " +
                        "WHERE c.org_id = ? AND c.tenant_id = ?",
                buildInventoryAuditArgs(orgId, tenantId, validMaterialIds).toArray()
        );
        Map<Long, InventoryAuditSnapshot> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            BigDecimal expectedClosingQty = scaleQuantity(
                    toBigDecimal(row.get("openingStockQty"))
                            .add(toBigDecimal(row.get("inboundQty")))
                            .subtract(toBigDecimal(row.get("outboundQty")))
                            .add(toBigDecimal(row.get("adjustmentQty")))
                            .subtract(toBigDecimal(row.get("consumedQty")))
            );
            BigDecimal actualClosingQty = scaleQuantity(toBigDecimal(row.get("closingStockQty")));
            boolean mismatchDetected = expectedClosingQty.subtract(actualClosingQty).abs().compareTo(BigDecimal.valueOf(0.01d)) > 0;
            result.put(materialId, new InventoryAuditSnapshot(
                    safeDate(row.get("statDate")),
                    mismatchDetected,
                    expectedClosingQty,
                    actualClosingQty
            ));
        }
        return result;
    }

    private List<Object> buildInventoryAuditArgs(Long orgId, Long tenantId, List<Long> materialIds) {
        List<Object> args = new ArrayList<>();
        args.add(orgId);
        args.add(tenantId);
        args.addAll(materialIds);
        args.add(orgId);
        args.add(tenantId);
        return args;
    }

    private ActivityContext resolveActivityContext(Long orgId, LocalDate startDate, LocalDate endDate) {
        String tableName = resolveAvailableActivityTable();
        if (tableName == null) {
            return new ActivityContext(BigDecimal.ONE, Collections.emptyList());
        }
        BigDecimal factor = resolveActivityFactor(orgId, startDate, endDate);
        List<String> activityNames = loadActivityNames(tableName, orgId, startDate, endDate);
        return new ActivityContext(factor, activityNames);
    }

    private List<String> loadActivityNames(String tableName, Long orgId, LocalDate startDate, LocalDate endDate) {
        if (StrUtil.isBlank(tableName)) {
            return Collections.emptyList();
        }
        try {
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    String.class,
                    tableName
            );
            if (columns == null || columns.isEmpty()) {
                return Collections.emptyList();
            }
            String nameColumn = columns.stream()
                    .map(String::toLowerCase)
                    .filter(column -> Set.of("activity_name", "event_name", "name", "title", "subject").contains(column))
                    .findFirst()
                    .orElse(null);
            if (nameColumn == null) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT " + nameColumn + " AS activityName FROM " + tableName +
                            " WHERE org_id = ? AND ((start_date IS NULL OR start_date <= ?) AND (end_date IS NULL OR end_date >= ?)) " +
                            " ORDER BY " + nameColumn + " ASC LIMIT 3",
                    orgId,
                    Date.valueOf(endDate),
                    Date.valueOf(startDate)
            );
            return rows.stream()
                    .map(row -> asString(row.get("activityName")))
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.debug("加载活动名称失败，按空处理: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private BigDecimal ratioDifference(BigDecimal currentValue, BigDecimal baselineValue) {
        BigDecimal current = scaleQuantity(currentValue);
        BigDecimal baseline = scaleQuantity(baselineValue);
        if (baseline.compareTo(ZERO_QUANTITY) <= 0) {
            return current.compareTo(ZERO_QUANTITY) > 0
                    ? BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return current.subtract(baseline).divide(baseline, 6, RoundingMode.HALF_UP);
    }

    private ExplanationSnapshot buildExplanationSnapshot(
            MaterialProfile profile,
            FeatureMetric featureMetric,
            PurchaseDemandForecastRuleEngine.RuleResult ruleResult,
            PurchaseDemandForecastRuleEngine.ModelParameters modelParameters
    ) {
        List<String> anomalies = detectAnomalies(featureMetric, ruleResult);
        BigDecimal avg30 = featureMetric == null ? ZERO_QUANTITY : scaleQuantity(featureMetric.avgConsumption30d());
        BigDecimal coverageDays = ruleResult.coverageDays();
        String summary;
        if (scaleRate(featureMetric == null ? null : featureMetric.recipeDriveRatio()).compareTo(BigDecimal.valueOf(0.60d)) >= 0
                && ruleResult.forecastDemandQty().compareTo(avg30.multiply(BigDecimal.valueOf(Math.max(1, RECIPE_LOOKAHEAD_DAYS)))) > 0) {
            summary = String.format(Locale.ROOT,
                    "未来7天菜谱需求显著拉升，%s按%s测算建议补货%s。",
                    profile == null ? "当前物料" : profile.materialName(),
                    ruleResult.modelType(),
                    format(ruleResult.suggestedPurchaseQty()));
        } else if (scaleRate(featureMetric == null ? null : featureMetric.activitySensitivity()).compareTo(BigDecimal.valueOf(1.20d)) >= 0) {
            summary = String.format(Locale.ROOT,
                    "活动因子放大了该物料需求，系统按%s输出%s。",
                    ruleResult.modelType(),
                    format(ruleResult.suggestedPurchaseQty()));
        } else if (ruleResult.suggestedPurchaseQty().compareTo(ZERO_QUANTITY) <= 0) {
            summary = String.format(Locale.ROOT,
                    "当前库存约可覆盖%s天需求，系统暂不建议补货。",
                    format(coverageDays));
        } else if (PurchaseDemandForecastRuleEngine.SEGMENT_PERISHABLE.equals(ruleResult.materialSegment())) {
            summary = String.format(Locale.ROOT,
                    "该物料为易腐型，系统已按最大覆盖天数限量，建议补货%s。",
                    format(ruleResult.suggestedPurchaseQty()));
        } else {
            summary = String.format(Locale.ROOT,
                    "系统基于%s与历史消耗趋势综合测算，建议补货%s。",
                    ruleResult.materialSegment(),
                    format(ruleResult.suggestedPurchaseQty()));
        }
        String detail = summary + " " + ruleResult.forecastBasis()
                + (anomalies.isEmpty() ? "" : " 异常提示：" + String.join("；", anomalies) + "。");
        return new ExplanationSnapshot(summary, detail, String.join("、", anomalies));
    }

    private List<String> detectAnomalies(
            FeatureMetric featureMetric,
            PurchaseDemandForecastRuleEngine.RuleResult ruleResult
    ) {
        List<String> anomalies = new ArrayList<>();
        if (featureMetric == null || ruleResult == null) {
            return anomalies;
        }
        BigDecimal avg30 = scaleQuantity(featureMetric.avgConsumption30d());
        if (avg30.compareTo(ZERO_QUANTITY) > 0
                && ruleResult.forecastDemandQty().compareTo(avg30.multiply(BigDecimal.valueOf(RECIPE_LOOKAHEAD_DAYS)).multiply(BigDecimal.valueOf(2))) > 0) {
            anomalies.add("预测需求高于近30天均值2倍以上");
        }
        if (scaleQuantity(featureMetric.currentStockQty()).compareTo(scaleQuantity(featureMetric.availableStockQty()).add(BigDecimal.valueOf(20))) > 0) {
            anomalies.add("库存锁定量偏高");
        }
        if (scaleQuantity(featureMetric.recipeDemand7d()).compareTo(ZERO_QUANTITY) <= 0
                && scaleRate(featureMetric.recipeDriveRatio()).compareTo(BigDecimal.valueOf(0.60d)) >= 0) {
            anomalies.add("未来菜谱计划可能缺失");
        }
        if (scaleRate(featureMetric.activitySensitivity()).compareTo(BigDecimal.valueOf(1.50d)) > 0) {
            anomalies.add("活动放大系数过高，建议人工复核");
        }
        return anomalies;
    }

    private String buildForecastExplanationSummary(List<PurchaseDemandForecastItem> items) {
        if (items == null || items.isEmpty()) {
            return "本次预测无有效采购建议。";
        }
        Map<String, Long> segmentCountMap = items.stream()
                .collect(Collectors.groupingBy(item -> StrUtil.blankToDefault(item.getMaterialSegment(), PurchaseDemandForecastRuleEngine.SEGMENT_STABLE),
                        LinkedHashMap::new,
                        Collectors.counting()));
        String dominantSegment = segmentCountMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(PurchaseDemandForecastRuleEngine.SEGMENT_STABLE);
        BigDecimal totalSuggestedQty = items.stream()
                .map(PurchaseDemandForecastItem::getSuggestedQty)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long reorderCount = items.stream().filter(item -> Objects.equals(item.getOrderNow(), 1)).count();
        long riskCount = items.stream().filter(item -> StrUtil.isNotBlank(item.getPhaseThreeRiskFlags())).count();
        long manualReviewCount = items.stream().filter(item -> Objects.equals(item.getManualReviewRequired(), 1)).count();
        BigDecimal totalCost = items.stream()
                .map(PurchaseDemandForecastItem::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format(Locale.ROOT,
                "本次预测共输出%s条建议，主导分层为%s，累计建议采购量%s，其中%s条触发立即下单，%s条带风险标记，%s条建议人工复核，综合优化成本%s。",
                items.size(),
                dominantSegment,
                format(totalSuggestedQty),
                reorderCount,
                riskCount,
                manualReviewCount,
                formatDecimal(totalCost, 2));
    }

    private String buildForecastApprovalSummary(List<PurchaseDemandForecastItem> items) {
        if (items == null || items.isEmpty()) {
            return "当前无需要进入采购审批的预测建议。";
        }
        List<PurchaseDemandForecastItem> manualReviewItems = items.stream()
                .filter(item -> Objects.equals(item.getManualReviewRequired(), 1))
                .sorted(Comparator
                        .comparing(PurchaseDemandForecastItem::getExplanationSortScore, Comparator.nullsLast(BigDecimal::compareTo))
                        .reversed()
                        .thenComparing(PurchaseDemandForecastItem::getMaterialName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        if (manualReviewItems.isEmpty()) {
            return "本次预测未发现必须人工复核的物料，可按系统建议量组织采购审批。";
        }
        String topMaterials = manualReviewItems.stream()
                .limit(3)
                .map(PurchaseDemandForecastItem::getMaterialName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.joining("、"));
        return String.format(Locale.ROOT,
                "本次共有%s条建议需人工复核，重点关注%s；建议先核对库存、菜谱与活动配置，再进入采购审批。",
                manualReviewItems.size(),
                StrUtil.blankToDefault(topMaterials, "高风险物料"));
    }

    private record ExplanationSnapshot(String summary, String detail, String anomalyFlags) {
    }

    private record OptimizationCandidateScore(
            PurchaseDemandForecastRuleEngine.ModelParameters parameters,
            BigDecimal score,
            BigDecimal wape,
            BigDecimal stockoutRate,
            BigDecimal oversupplyRate,
            Integer sampleCount
    ) {
    }

    private PurchaseDemandForecast getForecastById(Long id) {
        PurchaseDemandForecast forecast = purchaseDemandForecastMapper.selectOne(
                new LambdaQueryWrapper<PurchaseDemandForecast>()
                        .eq(PurchaseDemandForecast::getId, id)
                        .eq(PurchaseDemandForecast::getTenantId, resolveTenantId())
                        .last("LIMIT 1")
        );
        if (forecast == null || Boolean.TRUE.equals(isDeleted(forecast.getDeleted()))) {
            throw BizException.notFound("采购需求预测单不存在");
        }
        return forecast;
    }

    private ForecastPreparationSnapshot prepareForecastSnapshot(
            Long orgId,
            Long tenantId,
            LocalDate basisDate,
            LocalDate historyStartDate,
            LocalDate recipeEndDate,
            int forecastDays,
            BigDecimal calendarFactor,
            BigDecimal holidayFactor,
            BigDecimal activityFactor
    ) {
        Map<Long, HistoricalMetric> planMetrics = fetchHistoricalPlanMetrics(orgId, tenantId, historyStartDate, basisDate);
        Map<Long, HistoricalMetric> orderMetrics = fetchHistoricalOrderMetrics(orgId, tenantId, historyStartDate, basisDate);
        Map<Long, InventoryMetric> inventoryMetrics = fetchInventoryMetrics(orgId, tenantId);

        Map<Long, List<MaterialDailyConsumption>> consumptionByMaterial = rebuildMaterialDailyConsumption(
                orgId,
                tenantId,
                historyStartDate,
                basisDate,
                inventoryMetrics
        );
        List<RecipeMaterialDailyDemand> recipeDemandRows = rebuildRecipeMaterialDailyDemand(
                orgId,
                tenantId,
                historyStartDate,
                recipeEndDate
        );

        Map<Long, BigDecimal> recipeHistoryDemandMap = aggregateRecipeDemand(recipeDemandRows, historyStartDate, basisDate);
        Map<Long, RecipeMetric> recipeMetrics = buildRecipeMetricMap(recipeDemandRows, basisDate, recipeEndDate);
        Map<Long, BigDecimal> pendingPlanQtyMap = fetchPendingPlanQuantities(orgId, tenantId);
        Map<Long, BigDecimal> inTransitQtyMap = fetchInTransitQuantities(orgId, tenantId);

        Set<Long> materialIds = new LinkedHashSet<>();
        materialIds.addAll(planMetrics.keySet());
        materialIds.addAll(orderMetrics.keySet());
        materialIds.addAll(inventoryMetrics.keySet());
        materialIds.addAll(recipeMetrics.keySet());
        materialIds.addAll(consumptionByMaterial.keySet());
        materialIds.addAll(pendingPlanQtyMap.keySet());
        materialIds.addAll(inTransitQtyMap.keySet());
        materialIds.removeIf(materialId -> !isValidMaterialId(materialId));

        Map<Long, MaterialProfile> materialProfiles = fetchMaterialProfiles(orgId, tenantId, materialIds);
        Map<Long, BigDecimal> fallbackPrices = fetchFallbackPrices(orgId, tenantId, materialIds);
        Map<Long, FeatureMetric> featureMetrics = rebuildMaterialDailyFeatures(
                orgId,
                tenantId,
                basisDate,
                forecastDays,
                materialIds,
                consumptionByMaterial,
                recipeMetrics,
                recipeHistoryDemandMap,
                inventoryMetrics,
                pendingPlanQtyMap,
                inTransitQtyMap,
                materialProfiles,
                calendarFactor,
                holidayFactor,
                activityFactor
        );

        return new ForecastPreparationSnapshot(
                materialIds,
                planMetrics,
                orderMetrics,
                inventoryMetrics,
                recipeMetrics,
                materialProfiles,
                fallbackPrices,
                featureMetrics,
                recipeHistoryDemandMap,
                consumptionByMaterial
        );
    }

    private Map<Long, List<MaterialDailyConsumption>> rebuildMaterialDailyConsumption(
            Long orgId,
            Long tenantId,
            LocalDate startDate,
            LocalDate endDate,
            Map<Long, InventoryMetric> inventoryMetrics
    ) {
        materialDailyConsumptionMapper.delete(
                new LambdaQueryWrapper<MaterialDailyConsumption>()
                        .eq(MaterialDailyConsumption::getOrgId, orgId)
                        .eq(MaterialDailyConsumption::getTenantId, tenantId)
                        .between(MaterialDailyConsumption::getStatDate, startDate, endDate)
        );

        Map<Long, Map<LocalDate, BigDecimal>> inboundMap = queryMaterialDateQuantityMap(
                "SELECT oi.material_id AS materialId, DATE(COALESCE(o.approved_at, o.updated_at, o.created_at)) AS statDate, " +
                        "COALESCE(SUM(oi.quantity), 0) AS quantity " +
                        "FROM wms_inbound_order_item oi " +
                        "JOIN wms_inbound_order o ON o.id = oi.inbound_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status = 'approved' AND o.post_status = 'success' " +
                        "  AND DATE(COALESCE(o.approved_at, o.updated_at, o.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY oi.material_id, DATE(COALESCE(o.approved_at, o.updated_at, o.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, Map<LocalDate, BigDecimal>> outboundMap = queryMaterialDateQuantityMap(
                "SELECT oi.material_id AS materialId, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) AS statDate, " +
                        "COALESCE(SUM(oi.quantity), 0) AS quantity " +
                        "FROM wms_outbound_order_item oi " +
                        "JOIN wms_outbound_order o ON o.id = oi.outbound_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status = 'completed' " +
                        "  AND DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY oi.material_id, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, Map<LocalDate, BigDecimal>> actualOutboundMap = queryMaterialDateQuantityMap(
                "SELECT oi.material_id AS materialId, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) AS statDate, " +
                        "COALESCE(SUM(oi.quantity), 0) AS quantity " +
                        "FROM wms_outbound_order_item oi " +
                        "JOIN wms_outbound_order o ON o.id = oi.outbound_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status = 'completed' " +
                        "  AND COALESCE(o.outbound_type, '') NOT IN ('return', 'transfer', 'loss', 'scrap') " +
                        "  AND DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY oi.material_id, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, Map<LocalDate, BigDecimal>> lossScrapOutboundMap = queryMaterialDateQuantityMap(
                "SELECT oi.material_id AS materialId, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) AS statDate, " +
                        "COALESCE(SUM(oi.quantity), 0) AS quantity " +
                        "FROM wms_outbound_order_item oi " +
                        "JOIN wms_outbound_order o ON o.id = oi.outbound_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status = 'completed' " +
                        "  AND COALESCE(o.outbound_type, '') IN ('loss', 'scrap') " +
                        "  AND DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY oi.material_id, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, Map<LocalDate, BigDecimal>> nonConsumptionOutboundMap = queryMaterialDateQuantityMap(
                "SELECT oi.material_id AS materialId, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) AS statDate, " +
                        "COALESCE(SUM(oi.quantity), 0) AS quantity " +
                        "FROM wms_outbound_order_item oi " +
                        "JOIN wms_outbound_order o ON o.id = oi.outbound_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status = 'completed' " +
                        "  AND COALESCE(o.outbound_type, '') IN ('return', 'transfer') " +
                        "  AND DATE(COALESCE(o.completed_at, o.updated_at, o.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY oi.material_id, DATE(COALESCE(o.completed_at, o.updated_at, o.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, Map<LocalDate, BigDecimal>> adjustmentMap = queryMaterialDateQuantityMap(
                "SELECT si.material_id AS materialId, DATE(COALESCE(s.approved_at, s.updated_at, s.created_at)) AS statDate, " +
                        "COALESCE(SUM(si.diff_qty), 0) AS quantity " +
                        "FROM wms_stocktake_order_item si " +
                        "JOIN wms_stocktake_order s ON s.id = si.stocktake_id " +
                        "WHERE s.deleted = 0 AND s.org_id = ? AND s.tenant_id = ? " +
                        "  AND s.status = 'approved' " +
                        "  AND DATE(COALESCE(s.approved_at, s.updated_at, s.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY si.material_id, DATE(COALESCE(s.approved_at, s.updated_at, s.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, Map<LocalDate, BigDecimal>> deficitAdjustmentMap = queryMaterialDateQuantityMap(
                "SELECT si.material_id AS materialId, DATE(COALESCE(s.approved_at, s.updated_at, s.created_at)) AS statDate, " +
                        "COALESCE(SUM(CASE " +
                        "  WHEN si.diff_type = 'deficit' THEN ABS(COALESCE(si.diff_qty, 0)) " +
                        "  WHEN si.diff_type IS NULL AND COALESCE(si.diff_qty, 0) < 0 THEN ABS(si.diff_qty) " +
                        "  ELSE 0 END), 0) AS quantity " +
                        "FROM wms_stocktake_order_item si " +
                        "JOIN wms_stocktake_order s ON s.id = si.stocktake_id " +
                        "WHERE s.deleted = 0 AND s.org_id = ? AND s.tenant_id = ? " +
                        "  AND s.status = 'approved' " +
                        "  AND DATE(COALESCE(s.approved_at, s.updated_at, s.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY si.material_id, DATE(COALESCE(s.approved_at, s.updated_at, s.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, Map<LocalDate, BigDecimal>> surplusAdjustmentMap = queryMaterialDateQuantityMap(
                "SELECT si.material_id AS materialId, DATE(COALESCE(s.approved_at, s.updated_at, s.created_at)) AS statDate, " +
                        "COALESCE(SUM(CASE " +
                        "  WHEN si.diff_type = 'surplus' THEN ABS(COALESCE(si.diff_qty, 0)) " +
                        "  WHEN si.diff_type IS NULL AND COALESCE(si.diff_qty, 0) > 0 THEN ABS(si.diff_qty) " +
                        "  ELSE 0 END), 0) AS quantity " +
                        "FROM wms_stocktake_order_item si " +
                        "JOIN wms_stocktake_order s ON s.id = si.stocktake_id " +
                        "WHERE s.deleted = 0 AND s.org_id = ? AND s.tenant_id = ? " +
                        "  AND s.status = 'approved' " +
                        "  AND DATE(COALESCE(s.approved_at, s.updated_at, s.created_at)) BETWEEN ? AND ? " +
                        "GROUP BY si.material_id, DATE(COALESCE(s.approved_at, s.updated_at, s.created_at))",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );

        Set<Long> materialIds = new LinkedHashSet<>();
        materialIds.addAll(inventoryMetrics.keySet());
        materialIds.addAll(inboundMap.keySet());
        materialIds.addAll(outboundMap.keySet());
        materialIds.addAll(actualOutboundMap.keySet());
        materialIds.addAll(lossScrapOutboundMap.keySet());
        materialIds.addAll(nonConsumptionOutboundMap.keySet());
        materialIds.addAll(adjustmentMap.keySet());
        materialIds.addAll(deficitAdjustmentMap.keySet());
        materialIds.addAll(surplusAdjustmentMap.keySet());
        materialIds.removeIf(materialId -> !isValidMaterialId(materialId));

        Map<Long, List<MaterialDailyConsumption>> result = new LinkedHashMap<>();
        for (Long materialId : materialIds) {
            InventoryMetric inventoryMetric = inventoryMetrics.getOrDefault(materialId, InventoryMetric.empty());
            BigDecimal closingStock = scaleQuantity(inventoryMetric.currentStockQty());
            List<MaterialDailyConsumption> rows = new ArrayList<>();
            for (LocalDate date = endDate; !date.isBefore(startDate); date = date.minusDays(1)) {
                BigDecimal inboundQty = quantityAt(inboundMap, materialId, date);
                BigDecimal outboundQty = quantityAt(outboundMap, materialId, date);
                BigDecimal actualOutboundQty = quantityAt(actualOutboundMap, materialId, date);
                BigDecimal lossScrapQty = quantityAt(lossScrapOutboundMap, materialId, date);
                BigDecimal nonConsumptionOutboundQty = quantityAt(nonConsumptionOutboundMap, materialId, date);
                BigDecimal adjustmentQty = quantityAt(adjustmentMap, materialId, date);
                BigDecimal openingStock = scaleQuantity(closingStock.subtract(inboundQty).subtract(adjustmentQty).add(outboundQty));
                BigDecimal deficitQty = quantityAt(deficitAdjustmentMap, materialId, date);
                BigDecimal surplusQty = quantityAt(surplusAdjustmentMap, materialId, date);
                ConsumptionResolution resolution = resolveConsumptionResolution(
                        openingStock,
                        closingStock,
                        inboundQty,
                        outboundQty,
                        actualOutboundQty,
                        lossScrapQty,
                        nonConsumptionOutboundQty,
                        adjustmentQty,
                        deficitQty,
                        surplusQty
                );

                MaterialDailyConsumption row = new MaterialDailyConsumption();
                row.setOrgId(orgId);
                row.setTenantId(tenantId);
                row.setStatDate(date);
                row.setMaterialId(materialId);
                row.setOpeningStockQty(scaleQuantity(openingStock));
                row.setInboundQty(scaleQuantity(inboundQty));
                row.setOutboundQty(scaleQuantity(outboundQty));
                row.setAdjustmentQty(scaleQuantity(adjustmentQty));
                row.setClosingStockQty(scaleQuantity(closingStock));
                row.setConsumedQty(resolution.consumedQty());
                row.setDataSource(resolution.dataSource());
                rows.add(row);

                closingStock = openingStock;
            }
            Collections.reverse(rows);
            for (MaterialDailyConsumption row : rows) {
                materialDailyConsumptionMapper.insert(row);
            }
            result.put(materialId, rows);
        }
        return result;
    }

    private List<RecipeMaterialDailyDemand> rebuildRecipeMaterialDailyDemand(
            Long orgId,
            Long tenantId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        recipeMaterialDailyDemandMapper.delete(
                new LambdaQueryWrapper<RecipeMaterialDailyDemand>()
                        .eq(RecipeMaterialDailyDemand::getOrgId, orgId)
                        .eq(RecipeMaterialDailyDemand::getTenantId, tenantId)
                        .between(RecipeMaterialDailyDemand::getDemandDate, startDate, endDate)
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT rp.id AS recipePlanId, rpi.recipe_id AS recipeId, rp.plan_date AS demandDate, ri.material_id AS materialId, " +
                        "COALESCE(SUM(COALESCE(rpi.planned_servings, rp.total_servings, rp.expected_count, 1)), 0) AS servings, " +
                        "MAX(ri.quantity) AS standardQty " +
                        "FROM recipe_plan rp " +
                        "JOIN recipe_plan_item rpi ON rpi.plan_id = rp.id AND rpi.deleted = 0 " +
                        "JOIN recipe_ingredient ri ON ri.recipe_id = rpi.recipe_id AND ri.deleted = 0 " +
                        "WHERE rp.deleted = 0 AND rp.org_id = ? AND rp.tenant_id = ? " +
                        "  AND rp.status IN ('pending', 'approved', 'cooking') " +
                        "  AND ri.material_id IS NOT NULL " +
                        "  AND rp.plan_date BETWEEN ? AND ? " +
                        "GROUP BY rp.id, rpi.recipe_id, rp.plan_date, ri.material_id",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );

        List<RecipeMaterialDailyDemand> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            BigDecimal servings = scaleQuantity(toBigDecimal(row.get("servings")));
            BigDecimal standardQty = scaleQuantity(toBigDecimal(row.get("standardQty")));
            BigDecimal theoreticalDemandQty = scaleQuantity(
                    servings.multiply(standardQty).multiply(BigDecimal.ONE.add(DEFAULT_RECIPE_LOSS_RATE))
            );
            RecipeMaterialDailyDemand item = new RecipeMaterialDailyDemand();
            item.setOrgId(orgId);
            item.setTenantId(tenantId);
            item.setDemandDate(safeDate(row.get("demandDate")));
            item.setRecipePlanId(toLong(row.get("recipePlanId")));
            item.setRecipeId(toLong(row.get("recipeId")));
            item.setMaterialId(materialId);
            item.setServings(servings);
            item.setStandardQty(standardQty);
            item.setLossRate(DEFAULT_RECIPE_LOSS_RATE);
            item.setTheoreticalDemandQty(theoreticalDemandQty);
            recipeMaterialDailyDemandMapper.insert(item);
            result.add(item);
        }
        return result;
    }

    private Map<Long, FeatureMetric> rebuildMaterialDailyFeatures(
            Long orgId,
            Long tenantId,
            LocalDate basisDate,
            int forecastDays,
            Collection<Long> materialIds,
            Map<Long, List<MaterialDailyConsumption>> consumptionByMaterial,
            Map<Long, RecipeMetric> recipeMetrics,
            Map<Long, BigDecimal> recipeHistoryDemandMap,
            Map<Long, InventoryMetric> inventoryMetrics,
            Map<Long, BigDecimal> pendingPlanQtyMap,
            Map<Long, BigDecimal> inTransitQtyMap,
            Map<Long, MaterialProfile> materialProfiles,
            BigDecimal calendarFactor,
            BigDecimal holidayFactor,
            BigDecimal activityFactor
    ) {
        materialDailyFeatureMapper.delete(
                new LambdaQueryWrapper<MaterialDailyFeature>()
                        .eq(MaterialDailyFeature::getOrgId, orgId)
                        .eq(MaterialDailyFeature::getTenantId, tenantId)
                        .eq(MaterialDailyFeature::getStatDate, basisDate)
        );

        Map<Long, FeatureMetric> result = new LinkedHashMap<>();
        List<Long> validMaterialIds = normalizeMaterialIds(materialIds);
        int orgLeadTimeDays = resolveOrgLeadTimeDays(orgId, tenantId);
        for (Long materialId : validMaterialIds) {
            List<MaterialDailyConsumption> consumptionRows = consumptionByMaterial.getOrDefault(materialId, Collections.emptyList());
            InventoryMetric inventoryMetric = inventoryMetrics.getOrDefault(materialId, InventoryMetric.empty());
            RecipeMetric recipeMetric = recipeMetrics.getOrDefault(materialId, RecipeMetric.empty());
            MaterialProfile profile = materialProfiles.getOrDefault(
                    materialId,
                    new MaterialProfile(materialId, "", "", "", "", null, null, ZERO_QUANTITY, ZERO_QUANTITY)
            );
            BigDecimal avg7 = averageConsumption(consumptionRows, 7);
            BigDecimal avg14 = averageConsumption(consumptionRows, 14);
            BigDecimal avg30 = averageConsumption(consumptionRows, 30);
            BigDecimal std30 = standardDeviation(consumptionRows);
            BigDecimal actualConsumption30d = scaleQuantity(consumptionRows.stream()
                    .map(MaterialDailyConsumption::getConsumedQty)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            int consumptionDays30d = (int) consumptionRows.stream()
                    .map(MaterialDailyConsumption::getConsumedQty)
                    .filter(Objects::nonNull)
                    .filter(qty -> qty.compareTo(ZERO_QUANTITY) > 0)
                    .count();
            BigDecimal recipeHistory30d = scaleQuantity(recipeHistoryDemandMap.getOrDefault(materialId, ZERO_QUANTITY));
            BigDecimal recipeDriveRatio = ratio(recipeHistory30d, actualConsumption30d);
            BigDecimal demandActiveRatio = divide(BigDecimal.valueOf(consumptionDays30d), BigDecimal.valueOf(HISTORY_DAYS), 6);
            BigDecimal demandCv = ratio(std30, avg30);
            BigDecimal activitySensitivity = scaleFactor(activityFactor);
            BigDecimal pendingPlanQty = scaleQuantity(pendingPlanQtyMap.getOrDefault(materialId, ZERO_QUANTITY));
            BigDecimal inTransitQty = scaleQuantity(inTransitQtyMap.getOrDefault(materialId, ZERO_QUANTITY));
            BigDecimal inventoryTurnoverDays = calculateInventoryTurnoverDays(consumptionRows, avg30);
            List<BigDecimal> recentConsumptionSeries = consumptionRows.stream()
                    .map(MaterialDailyConsumption::getConsumedQty)
                    .filter(Objects::nonNull)
                    .map(this::scaleQuantity)
                    .collect(Collectors.toList());

            PurchaseDemandForecastRuleEngine.MaterialRuleContext segmentContext =
                    new PurchaseDemandForecastRuleEngine.MaterialRuleContext(
                            materialId,
                            profile.materialName(),
                            profile.materialCategory(),
                            profile.materialUnit(),
                            profile.shelfLifeDays(),
                            profile.warningDays(),
                            forecastDays,
                            consumptionDays30d,
                            inventoryMetric.currentStockQty(),
                            inventoryMetric.availableQty(),
                            avg7,
                            avg14,
                            avg30,
                            std30,
                            recipeMetric.weeklyDemandQty(),
                            recipeHistory30d,
                            actualConsumption30d,
                            pendingPlanQty,
                            inTransitQty,
                            profile.minStock(),
                            profile.maxStock(),
                            calendarFactor,
                            holidayFactor,
                            activityFactor,
                            recipeDriveRatio,
                            demandActiveRatio,
                            demandCv,
                            activitySensitivity,
                            null,
                            orgLeadTimeDays,
                            BigDecimal.valueOf(0.90d),
                            inventoryTurnoverDays,
                            null,
                            recentConsumptionSeries
                    );
            String materialSegment = purchaseDemandForecastRuleEngine.resolveMaterialSegment(segmentContext);
            PurchaseDemandForecastRuleEngine.ModelParameters defaultParameters =
                    purchaseDemandForecastRuleEngine.resolveDefaultParameters(
                            new PurchaseDemandForecastRuleEngine.MaterialRuleContext(
                                    segmentContext.materialId(),
                                    segmentContext.materialName(),
                                    segmentContext.materialCategory(),
                                    segmentContext.materialUnit(),
                                    segmentContext.shelfLifeDays(),
                                    segmentContext.warningDays(),
                                    segmentContext.forecastDays(),
                                    segmentContext.consumptionDays30d(),
                                    segmentContext.currentStockQty(),
                                    segmentContext.availableStockQty(),
                                    segmentContext.avgConsumption7d(),
                                    segmentContext.avgConsumption14d(),
                                    segmentContext.avgConsumption30d(),
                                    segmentContext.stdConsumption30d(),
                                    segmentContext.recipeDemand7d(),
                                    segmentContext.recipeHistory30d(),
                                    segmentContext.actualConsumption30d(),
                                    segmentContext.pendingPlanQty(),
                                    segmentContext.inTransitQty(),
                                    segmentContext.minStock(),
                                    segmentContext.maxStock(),
                                    segmentContext.calendarFactor(),
                                    segmentContext.holidayFactor(),
                                    segmentContext.activityFactor(),
                                    segmentContext.recipeDriveRatio(),
                                    segmentContext.demandActiveRatio(),
                                    segmentContext.demandCv(),
                                    segmentContext.activitySensitivity(),
                                    materialSegment,
                                    segmentContext.leadTimeDays(),
                                    segmentContext.serviceLevel(),
                                    segmentContext.inventoryTurnoverDays(),
                                    segmentContext.volatileMlForecastQty(),
                                    segmentContext.recentConsumptionSeries30d()
                            )
                    );
            String forecastType = defaultParameters.modelType();

            MaterialDailyFeature feature = new MaterialDailyFeature();
            feature.setOrgId(orgId);
            feature.setTenantId(tenantId);
            feature.setStatDate(basisDate);
            feature.setMaterialId(materialId);
            feature.setCurrentStockQty(scaleQuantity(inventoryMetric.currentStockQty()));
            feature.setAvailableStockQty(scaleQuantity(inventoryMetric.availableQty()));
            feature.setAvgConsumption7d(avg7);
            feature.setAvgConsumption14d(avg14);
            feature.setAvgConsumption30d(avg30);
            feature.setStdConsumption30d(std30);
            feature.setRecipeDemand7d(scaleQuantity(recipeMetric.weeklyDemandQty()));
            feature.setRecipeHistory30d(recipeHistory30d);
            feature.setActualConsumption30d(actualConsumption30d);
            feature.setConsumptionDays30d(consumptionDays30d);
            feature.setInventoryTurnoverDays(inventoryTurnoverDays);
            feature.setPendingPlanQty(pendingPlanQty);
            feature.setInTransitQty(inTransitQty);
            feature.setHolidayFactor(scaleFactor(holidayFactor));
            feature.setActivityFactor(scaleFactor(activityFactor));
            feature.setMaterialCategory(profile.materialCategory());
            feature.setForecastType(forecastType);
            feature.setRecipeDriveRatio(recipeDriveRatio);
            feature.setDemandActiveRatio(demandActiveRatio);
            feature.setDemandCv(demandCv);
            feature.setActivitySensitivity(activitySensitivity);
            feature.setMaterialSegment(materialSegment);
            feature.setLeadTimeDays(defaultParameters.leadTimeDays());
            feature.setServiceLevel(scaleFactor(defaultParameters.serviceLevel()));
            materialDailyFeatureMapper.insert(feature);

            result.put(materialId, new FeatureMetric(
                    scaleQuantity(inventoryMetric.currentStockQty()),
                    scaleQuantity(inventoryMetric.availableQty()),
                    avg7,
                    avg14,
                    avg30,
                    std30,
                    scaleQuantity(recipeMetric.weeklyDemandQty()),
                    recipeHistory30d,
                    pendingPlanQty,
                    inTransitQty,
                    actualConsumption30d,
                    consumptionDays30d,
                    inventoryTurnoverDays,
                    forecastType,
                    recipeDriveRatio,
                    demandActiveRatio,
                    demandCv,
                    activitySensitivity,
                    materialSegment,
                    defaultParameters.leadTimeDays(),
                    scaleFactor(defaultParameters.serviceLevel()),
                    recentConsumptionSeries
            ));
        }
        return result;
    }

    private Map<Long, BigDecimal> fetchPendingPlanQuantities(Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT x.materialId, COALESCE(SUM(x.remainingQty), 0) AS pendingPlanQty FROM (" +
                        "  SELECT i.material_id AS materialId, " +
                        "         GREATEST(i.plan_qty - COALESCE(SUM(CASE WHEN o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "                 AND o.status NOT IN ('rejected', 'voided', 'cancelled') THEN oi.order_qty ELSE 0 END), 0), 0) AS remainingQty " +
                        "  FROM scm_purchase_plan_item i " +
                        "  JOIN scm_purchase_plan p ON p.id = i.plan_id " +
                        "  LEFT JOIN scm_purchase_order_item oi ON oi.plan_item_id = i.id " +
                        "  LEFT JOIN scm_purchase_order o ON o.id = oi.order_id " +
                        "  WHERE p.deleted = 0 AND p.org_id = ? AND p.tenant_id = ? AND p.status = 'approved' " +
                        "  GROUP BY i.id, i.material_id, i.plan_qty " +
                        ") x GROUP BY x.materialId",
                orgId,
                tenantId,
                orgId,
                tenantId
        );
        return toMaterialQuantityMap(rows, "materialId", "pendingPlanQty");
    }

    private Map<Long, BigDecimal> fetchInTransitQuantities(Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.material_id AS materialId, " +
                        "COALESCE(SUM(GREATEST(COALESCE(i.remaining_inbound_qty, i.order_qty - COALESCE(i.inbound_qty, 0)), 0)), 0) AS inTransitQty " +
                        "FROM scm_purchase_order_item i " +
                        "JOIN scm_purchase_order o ON o.id = i.order_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status NOT IN ('rejected', 'voided', 'cancelled', 'closed', 'completed') " +
                        "GROUP BY i.material_id",
                orgId,
                tenantId
        );
        return toMaterialQuantityMap(rows, "materialId", "inTransitQty");
    }

    private Map<Long, Map<LocalDate, BigDecimal>> queryMaterialDateQuantityMap(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        Map<Long, Map<LocalDate, BigDecimal>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            LocalDate statDate = safeDate(row.get("statDate"));
            if (!isValidMaterialId(materialId) || statDate == null) {
                continue;
            }
            result.computeIfAbsent(materialId, key -> new LinkedHashMap<>())
                    .put(statDate, scaleQuantity(toBigDecimal(row.get("quantity"))));
        }
        return result;
    }

    private Map<Long, BigDecimal> toMaterialQuantityMap(List<Map<String, Object>> rows, String materialKey, String quantityKey) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get(materialKey));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            result.put(materialId, scaleQuantity(toBigDecimal(row.get(quantityKey))));
        }
        return result;
    }

    private Map<Long, BigDecimal> aggregateRecipeDemand(
            List<RecipeMaterialDailyDemand> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (RecipeMaterialDailyDemand row : rows) {
            if (row == null || !isValidMaterialId(row.getMaterialId()) || row.getDemandDate() == null) {
                continue;
            }
            if (row.getDemandDate().isBefore(startDate) || row.getDemandDate().isAfter(endDate)) {
                continue;
            }
            result.merge(
                    row.getMaterialId(),
                    scaleQuantity(row.getTheoreticalDemandQty()),
                    BigDecimal::add
            );
        }
        result.replaceAll((key, value) -> scaleQuantity(value));
        return result;
    }

    private Map<Long, RecipeMetric> buildRecipeMetricMap(
            List<RecipeMaterialDailyDemand> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<Long, BigDecimal> demandMap = aggregateRecipeDemand(rows, startDate, endDate);
        Map<Long, RecipeMetric> result = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : demandMap.entrySet()) {
            result.put(entry.getKey(), new RecipeMetric("", "", "", scaleQuantity(entry.getValue())));
        }
        return result;
    }

    private BigDecimal quantityAt(Map<Long, Map<LocalDate, BigDecimal>> source, Long materialId, LocalDate date) {
        if (source == null || materialId == null || date == null) {
            return ZERO_QUANTITY;
        }
        return scaleQuantity(source.getOrDefault(materialId, Collections.emptyMap()).get(date));
    }

    private ConsumptionResolution resolveConsumptionResolution(
            BigDecimal openingStock,
            BigDecimal closingStock,
            BigDecimal inboundQty,
            BigDecimal outboundQty,
            BigDecimal actualOutboundQty,
            BigDecimal lossScrapQty,
            BigDecimal nonConsumptionOutboundQty,
            BigDecimal adjustmentQty,
            BigDecimal deficitQty,
            BigDecimal surplusQty
    ) {
        BigDecimal normalizedOpening = scaleQuantity(openingStock);
        BigDecimal normalizedClosing = scaleQuantity(closingStock);
        BigDecimal normalizedInbound = scaleQuantity(inboundQty);
        BigDecimal normalizedOutbound = scaleQuantity(outboundQty);
        BigDecimal normalizedActualOutbound = scaleQuantity(actualOutboundQty);
        BigDecimal normalizedLossScrap = scaleQuantity(lossScrapQty);
        BigDecimal normalizedNonConsumptionOutbound = scaleQuantity(nonConsumptionOutboundQty);
        BigDecimal normalizedAdjustment = scaleQuantity(adjustmentQty);
        BigDecimal normalizedDeficit = scaleQuantity(deficitQty);
        BigDecimal normalizedSurplus = scaleQuantity(surplusQty);

        boolean hasPrimarySignal = normalizedActualOutbound.compareTo(ZERO_QUANTITY) > 0
                || normalizedLossScrap.compareTo(ZERO_QUANTITY) > 0
                || normalizedDeficit.compareTo(ZERO_QUANTITY) > 0
                || normalizedSurplus.compareTo(ZERO_QUANTITY) > 0;
        if (hasPrimarySignal) {
            BigDecimal actualQty = max(
                    normalizedActualOutbound
                            .add(normalizedLossScrap)
                            .add(normalizedDeficit)
                            .subtract(normalizedSurplus),
                    ZERO_QUANTITY
            );
            return new ConsumptionResolution(
                    scaleQuantity(actualQty),
                    normalizedDeficit.compareTo(ZERO_QUANTITY) > 0 || normalizedSurplus.compareTo(ZERO_QUANTITY) > 0
                            ? "actual_flow+stocktake"
                            : "actual_flow"
            );
        }

        boolean inventoryDeltaEligible = normalizedOpening.compareTo(ZERO_QUANTITY) >= 0
                && normalizedClosing.compareTo(ZERO_QUANTITY) >= 0
                && (normalizedOutbound.compareTo(ZERO_QUANTITY) > 0
                || normalizedAdjustment.compareTo(ZERO_QUANTITY) != 0
                || (normalizedOpening.compareTo(ZERO_QUANTITY) > 0 && normalizedClosing.compareTo(ZERO_QUANTITY) > 0));
        if (inventoryDeltaEligible) {
            BigDecimal inventoryDeltaQty = max(
                    normalizedOpening
                            .add(normalizedInbound)
                            .subtract(normalizedClosing)
                            .subtract(normalizedNonConsumptionOutbound),
                    ZERO_QUANTITY
            );
            return new ConsumptionResolution(scaleQuantity(inventoryDeltaQty), "inventory_delta_fallback");
        }

        if (normalizedInbound.compareTo(ZERO_QUANTITY) > 0) {
            return new ConsumptionResolution(normalizedInbound, "inbound_fallback");
        }
        return new ConsumptionResolution(ZERO_QUANTITY, "no_signal");
    }

    private BigDecimal averageConsumption(List<MaterialDailyConsumption> rows, int dayCount) {
        if (rows == null || rows.isEmpty() || dayCount <= 0) {
            return ZERO_QUANTITY;
        }
        int size = rows.size();
        int fromIndex = Math.max(0, size - dayCount);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = fromIndex; i < size; i++) {
            total = total.add(scaleQuantity(rows.get(i).getConsumedQty()));
        }
        return divide(total, BigDecimal.valueOf(Math.min(dayCount, size)), 3);
    }

    private BigDecimal standardDeviation(List<MaterialDailyConsumption> rows) {
        if (rows == null || rows.isEmpty()) {
            return ZERO_QUANTITY;
        }
        BigDecimal avg = averageConsumption(rows, rows.size());
        double variance = 0D;
        for (MaterialDailyConsumption row : rows) {
            double diff = scaleQuantity(row.getConsumedQty()).subtract(avg).doubleValue();
            variance += diff * diff;
        }
        variance = variance / rows.size();
        return scaleQuantity(BigDecimal.valueOf(Math.sqrt(Math.max(variance, 0D))));
    }

    private BigDecimal averageInventory(List<MaterialDailyConsumption> rows, int dayCount) {
        if (rows == null || rows.isEmpty() || dayCount <= 0) {
            return ZERO_QUANTITY;
        }
        int size = rows.size();
        int fromIndex = Math.max(0, size - dayCount);
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (int i = fromIndex; i < size; i++) {
            MaterialDailyConsumption row = rows.get(i);
            BigDecimal opening = scaleQuantity(row.getOpeningStockQty());
            BigDecimal closing = scaleQuantity(row.getClosingStockQty());
            total = total.add(divide(opening.add(closing), BigDecimal.valueOf(2L), 3));
            count++;
        }
        return count == 0 ? ZERO_QUANTITY : divide(total, BigDecimal.valueOf(count), 3);
    }

    private BigDecimal calculateInventoryTurnoverDays(List<MaterialDailyConsumption> rows, BigDecimal avgConsumptionQty) {
        BigDecimal normalizedAvgConsumption = scaleQuantity(avgConsumptionQty);
        if (rows == null || rows.isEmpty() || normalizedAvgConsumption.compareTo(ZERO_QUANTITY) <= 0) {
            return ZERO_QUANTITY;
        }
        return divide(averageInventory(rows, HISTORY_DAYS), normalizedAvgConsumption, 3);
    }

    private BigDecimal sqrtMean(BigDecimal totalSquaredError, int sampleCount) {
        if (sampleCount <= 0) {
            return ZERO_QUANTITY;
        }
        BigDecimal mean = divide(totalSquaredError, BigDecimal.valueOf(sampleCount), 6);
        return scaleRate(BigDecimal.valueOf(Math.sqrt(Math.max(mean.doubleValue(), 0D))));
    }

    private BigDecimal resolveVolatileMlForecastQty(
            LocalDate basisDate,
            int forecastDays,
            FeatureMetric featureMetric,
            List<MaterialDailyConsumption> consumptionRows
    ) {
        if (featureMetric == null
                || !StrUtil.equals(featureMetric.materialSegment(), PurchaseDemandForecastRuleEngine.SEGMENT_VOLATILE)) {
            return null;
        }
        PurchaseDemandForecastVolatileBoostingSupport.VolatileForecastResult result =
                purchaseDemandForecastVolatileBoostingSupport.forecast(
                        new PurchaseDemandForecastVolatileBoostingSupport.VolatileForecastRequest(
                                basisDate,
                                forecastDays,
                                consumptionRows,
                                featureMetric.currentStockQty(),
                                featureMetric.inventoryTurnoverDays()
                        )
                );
        return result.trained() ? scaleQuantity(result.predictedQty()) : null;
    }

    private LocalDate safeDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return new java.sql.Date(utilDate.getTime()).toLocalDate();
        }
        try {
            return LocalDate.parse(String.valueOf(value).substring(0, 10), DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<Long, HistoricalMetric> fetchHistoricalPlanMetrics(Long orgId, Long tenantId, LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.material_id AS materialId, MAX(i.material_name) AS materialName, MAX(i.material_spec) AS materialSpec, " +
                        "MAX(i.material_unit) AS materialUnit, COALESCE(SUM(i.plan_qty), 0) AS totalQty, " +
                        "COALESCE(AVG(i.estimate_price), 0) AS avgPrice " +
                        "FROM scm_purchase_plan_item i " +
                        "JOIN scm_purchase_plan p ON p.id = i.plan_id " +
                        "WHERE p.deleted = 0 AND p.org_id = ? AND p.tenant_id = ? " +
                        "  AND p.status NOT IN ('rejected', 'voided') " +
                        "  AND p.plan_date BETWEEN ? AND ? " +
                        "GROUP BY i.material_id",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        return toHistoricalMetricMap(rows);
    }

    private Map<Long, HistoricalMetric> fetchHistoricalOrderMetrics(Long orgId, Long tenantId, LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.material_id AS materialId, MAX(i.material_name) AS materialName, MAX(i.material_spec) AS materialSpec, " +
                        "MAX(i.material_unit) AS materialUnit, COALESCE(SUM(i.order_qty), 0) AS totalQty, " +
                        "COALESCE(AVG(i.unit_price), 0) AS avgPrice " +
                        "FROM scm_purchase_order_item i " +
                        "JOIN scm_purchase_order o ON o.id = i.order_id " +
                        "WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? " +
                        "  AND o.status NOT IN ('rejected', 'voided', 'cancelled') " +
                        "  AND o.order_date BETWEEN ? AND ? " +
                        "GROUP BY i.material_id",
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        return toHistoricalMetricMap(rows);
    }

    private Map<Long, InventoryMetric> fetchInventoryMetrics(Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.material_id AS materialId, MAX(i.material_name) AS materialName, MAX(i.spec) AS materialSpec, MAX(i.unit) AS materialUnit, " +
                        "COALESCE(SUM(CASE WHEN i.status <> 'expired' THEN i.quantity ELSE 0 END), 0) AS currentStockQty, " +
                        "COALESCE(SUM(CASE WHEN i.status NOT IN ('expired', 'locked') THEN i.quantity ELSE 0 END), 0) AS availableQty, " +
                        "COALESCE(AVG(CASE WHEN i.status NOT IN ('expired', 'locked') THEN i.unit_cost ELSE NULL END), 0) AS referenceUnitCost " +
                        "FROM wms_inventory i WHERE i.org_id = ? AND i.tenant_id = ? GROUP BY i.material_id",
                orgId,
                tenantId
        );
        Map<Long, InventoryMetric> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            result.put(
                    materialId,
                    new InventoryMetric(
                            asString(row.get("materialName")),
                            asString(row.get("materialSpec")),
                            asString(row.get("materialUnit")),
                            scaleQuantity(toBigDecimal(row.get("currentStockQty"))),
                            scaleQuantity(toBigDecimal(row.get("availableQty"))),
                            scaleAmount(toBigDecimal(row.get("referenceUnitCost")))
                    )
            );
        }
        return result;
    }

    private Map<Long, RecipeMetric> fetchRecipeMetrics(Long orgId, Long tenantId, LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ri.material_id AS materialId, MAX(ri.material_name) AS materialName, MAX(ri.material_spec) AS materialSpec, " +
                        "MAX(ri.unit) AS materialUnit, COALESCE(SUM(ri.quantity * COALESCE(rpi.planned_servings, 1) * (1 + ?)), 0) AS weeklyDemandQty " +
                        "FROM recipe_plan rp " +
                        "JOIN recipe_plan_item rpi ON rpi.plan_id = rp.id AND rpi.deleted = 0 " +
                        "JOIN recipe_ingredient ri ON ri.recipe_id = rpi.recipe_id AND ri.deleted = 0 " +
                        "WHERE rp.deleted = 0 AND rp.org_id = ? AND rp.tenant_id = ? " +
                        "  AND rp.status IN ('pending', 'approved', 'cooking') " +
                        "  AND ri.material_id IS NOT NULL " +
                        "  AND rp.plan_date BETWEEN ? AND ? " +
                        "GROUP BY ri.material_id",
                DEFAULT_RECIPE_LOSS_RATE,
                orgId,
                tenantId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
        Map<Long, RecipeMetric> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            result.put(
                    materialId,
                    new RecipeMetric(
                            asString(row.get("materialName")),
                            asString(row.get("materialSpec")),
                            asString(row.get("materialUnit")),
                            scaleQuantity(toBigDecimal(row.get("weeklyDemandQty")))
                    )
            );
        }
        return result;
    }

    private Map<Long, MaterialProfile> fetchMaterialProfiles(Long orgId, Long tenantId, Collection<Long> materialIds) {
        List<Long> validMaterialIds = normalizeMaterialIds(materialIds);
        if (validMaterialIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, material_name AS materialName, spec AS materialSpec, unit AS materialUnit, " +
                        "material_category AS materialCategory, shelf_life_days AS shelfLifeDays, warning_days AS warningDays, " +
                        "min_stock AS minStock, max_stock AS maxStock " +
                        "FROM wms_material WHERE deleted = 0 AND org_id = ? AND tenant_id = ? AND id IN (" + placeholders(validMaterialIds.size()) + ")",
                appendArgs(orgId, tenantId, validMaterialIds).toArray()
        );
        Map<Long, MaterialProfile> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("id"));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            result.put(materialId, new MaterialProfile(
                    materialId,
                    asString(row.get("materialName")),
                    asString(row.get("materialSpec")),
                    asString(row.get("materialUnit")),
                    asString(row.get("materialCategory")),
                    toInteger(row.get("shelfLifeDays")),
                    toInteger(row.get("warningDays")),
                    scaleQuantity(toBigDecimal(row.get("minStock"))),
                    scaleQuantity(toBigDecimal(row.get("maxStock")))
            ));
        }
        return result;
    }

    private Map<Long, BigDecimal> fetchFallbackPrices(Long orgId, Long tenantId, Collection<Long> materialIds) {
        List<Long> validMaterialIds = normalizeMaterialIds(materialIds);
        if (validMaterialIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT t.materialId, MAX(t.price) AS referencePrice FROM (" +
                        "  SELECT i.material_id AS materialId, AVG(i.unit_price) AS price " +
                        "  FROM scm_purchase_order_item i " +
                        "  JOIN scm_purchase_order o ON o.id = i.order_id " +
                        "  WHERE o.deleted = 0 AND o.org_id = ? AND o.tenant_id = ? AND o.status NOT IN ('rejected', 'voided', 'cancelled') " +
                        "    AND i.material_id IN (" + placeholders(validMaterialIds.size()) + ") " +
                        "  GROUP BY i.material_id " +
                        "  UNION ALL " +
                        "  SELECT i.material_id AS materialId, AVG(i.estimate_price) AS price " +
                        "  FROM scm_purchase_plan_item i " +
                        "  JOIN scm_purchase_plan p ON p.id = i.plan_id " +
                        "  WHERE p.deleted = 0 AND p.org_id = ? AND p.tenant_id = ? AND p.status NOT IN ('rejected', 'voided') " +
                        "    AND i.material_id IN (" + placeholders(validMaterialIds.size()) + ") " +
                        "  GROUP BY i.material_id " +
                        "  UNION ALL " +
                        "  SELECT i.material_id AS materialId, AVG(i.unit_cost) AS price " +
                        "  FROM wms_inventory i " +
                        "  WHERE i.org_id = ? AND i.tenant_id = ? AND i.material_id IN (" + placeholders(validMaterialIds.size()) + ") " +
                        "  GROUP BY i.material_id " +
                        ") t GROUP BY t.materialId",
                appendArgs(orgId, tenantId, validMaterialIds, orgId, tenantId, validMaterialIds, orgId, tenantId, validMaterialIds).toArray()
        );
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            result.put(materialId, scaleAmount(toBigDecimal(row.get("referencePrice"))));
        }
        return result;
    }

    private Map<Long, HistoricalMetric> toHistoricalMetricMap(List<Map<String, Object>> rows) {
        Map<Long, HistoricalMetric> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (!isValidMaterialId(materialId)) {
                continue;
            }
            result.put(materialId, new HistoricalMetric(
                    asString(row.get("materialName")),
                    asString(row.get("materialSpec")),
                    asString(row.get("materialUnit")),
                    scaleQuantity(toBigDecimal(row.get("totalQty"))),
                    scaleAmount(toBigDecimal(row.get("avgPrice")))
            ));
        }
        return result;
    }

    private MaterialProfile resolveMaterialProfile(
            Long materialId,
            Map<Long, MaterialProfile> materialProfiles,
            HistoricalMetric planMetric,
            HistoricalMetric orderMetric,
            InventoryMetric inventoryMetric,
            RecipeMetric recipeMetric
    ) {
        MaterialProfile profile = materialProfiles.get(materialId);
        if (profile != null) {
            return profile;
        }
        String materialName = firstNonBlank(
                planMetric.materialName(),
                orderMetric.materialName(),
                inventoryMetric.materialName(),
                recipeMetric.materialName(),
                "物料" + materialId
        );
        String materialSpec = firstNonBlank(
                planMetric.materialSpec(),
                orderMetric.materialSpec(),
                inventoryMetric.materialSpec(),
                recipeMetric.materialSpec(),
                ""
        );
        String materialUnit = firstNonBlank(
                planMetric.materialUnit(),
                orderMetric.materialUnit(),
                inventoryMetric.materialUnit(),
                recipeMetric.materialUnit(),
                ""
        );
        return new MaterialProfile(materialId, materialName, materialSpec, materialUnit, "", null, null, ZERO_QUANTITY, ZERO_QUANTITY);
    }

    private Map<Long, MaterialProfile> fetchResponseMaterialProfiles(PurchaseDemandForecast forecast, List<PurchaseDemandForecastItem> items) {
        if (forecast == null || items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        return fetchMaterialProfiles(
                forecast.getOrgId(),
                resolveTenantId(),
                items.stream()
                        .map(PurchaseDemandForecastItem::getMaterialId)
                        .filter(this::isValidMaterialId)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }

    private List<PurchaseDemandForecastItem> filterVisibleForecastItems(
            PurchaseDemandForecast forecast,
            List<PurchaseDemandForecastItem> items,
            Map<Long, MaterialProfile> materialProfiles
    ) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<PurchaseDemandForecastItem> visibleItems = new ArrayList<>();
        for (PurchaseDemandForecastItem item : items) {
            if (item == null || scaleQuantity(item.getSuggestedQty()).compareTo(ZERO_QUANTITY) <= 0) {
                continue;
            }
            visibleItems.add(item);
        }
        return visibleItems;
    }

    private MaterialProfile resolveResponseMaterialProfile(PurchaseDemandForecastItem item, MaterialProfile profile) {
        if (profile != null) {
            return profile;
        }
        if (item == null) {
            return new MaterialProfile(null, "", "", "", "", null, null, ZERO_QUANTITY, ZERO_QUANTITY);
        }
        return new MaterialProfile(
                item.getMaterialId(),
                item.getMaterialName(),
                item.getMaterialSpec(),
                item.getMaterialUnit(),
                "",
                null,
                null,
                ZERO_QUANTITY,
                ZERO_QUANTITY
        );
    }

    private ForecastDemandSnapshot buildDemandSnapshot(
            PurchaseDemandForecast forecast,
            PurchaseDemandForecastItem item,
            BigDecimal minStock
    ) {
        BigDecimal weightedDailyDemand = resolveWeightedDailyDemand(item);
        int forecastDays = forecast != null && forecast.getForecastDays() != null && forecast.getForecastDays() > 0
                ? forecast.getForecastDays()
                : resolveForecastDays(forecast == null ? null : forecast.getForecastDimension());
        BigDecimal combinedFactor = resolveCombinedFactor(forecast);
        BigDecimal recipeOutputDemand = resolveRecipeOutputDemand(forecast, item);
        BigDecimal historicalOutputDemand = scaleQuantity(weightedDailyDemand
                .multiply(BigDecimal.valueOf(forecastDays))
                .multiply(combinedFactor));
        BigDecimal expectedDemand = max(recipeOutputDemand, historicalOutputDemand);
        BigDecimal safetyStock = max(
                scaleQuantity(minStock),
                scaleQuantity(weightedDailyDemand.multiply(BigDecimal.valueOf(Math.max(2, forecastDays))))
        );
        return new ForecastDemandSnapshot(scaleQuantity(expectedDemand), scaleQuantity(safetyStock));
    }

    private BigDecimal resolveWeightedDailyDemand(PurchaseDemandForecastItem item) {
        if (item == null) {
            return ZERO_QUANTITY;
        }
        BigDecimal planDaily = scaleQuantity(item.getHistoricalPlanAvgQty());
        BigDecimal orderDaily = scaleQuantity(item.getHistoricalOrderAvgQty());
        return scaleQuantity(planDaily.multiply(BigDecimal.valueOf(0.45d))
                .add(orderDaily.multiply(BigDecimal.valueOf(0.55d))));
    }

    private BigDecimal resolveRecipeOutputDemand(PurchaseDemandForecast forecast, PurchaseDemandForecastItem item) {
        BigDecimal recipeWeeklyDemand = item == null ? ZERO_QUANTITY : scaleQuantity(item.getRecipeDemandQty());
        String dimension = normalizeDimension(forecast == null ? null : forecast.getForecastDimension());
        return DIMENSION_WEEKLY.equals(dimension)
                ? recipeWeeklyDemand
                : divide(recipeWeeklyDemand, BigDecimal.valueOf(RECIPE_LOOKAHEAD_DAYS), 3);
    }

    private BigDecimal resolveCombinedFactor(PurchaseDemandForecast forecast) {
        if (forecast == null) {
            return BigDecimal.ONE;
        }
        return scaleFactor(forecast.getCalendarFactor())
                .multiply(scaleFactor(forecast.getHolidayFactor()))
                .multiply(scaleFactor(forecast.getActivityFactor()));
    }

    private boolean shouldSkipMaterial(
            MaterialProfile profile,
            BigDecimal inventoryQty,
            BigDecimal expectedDemand,
            BigDecimal safetyStock,
            BigDecimal suggestedQty
    ) {
        BigDecimal displaySuggestedQty = scaleQuantity(suggestedQty);
        BigDecimal displayInventoryQty = scaleQuantity(inventoryQty);
        BigDecimal displayExpectedDemand = scaleQuantity(expectedDemand);
        BigDecimal displaySafetyStock = scaleQuantity(safetyStock);
        return profile == null
                || displaySuggestedQty.compareTo(ZERO_QUANTITY) <= 0
                || isInventorySufficient(displayInventoryQty, displayExpectedDemand, displaySafetyStock)
                || isSafetyStockSatisfiedWithoutDemand(displayInventoryQty, displayExpectedDemand, displaySafetyStock);
    }

    private boolean isInventorySufficient(BigDecimal inventoryQty, BigDecimal expectedDemand, BigDecimal safetyStock) {
        BigDecimal targetQty = max(expectedDemand, ZERO_QUANTITY).add(max(safetyStock, ZERO_QUANTITY));
        return max(inventoryQty, ZERO_QUANTITY).compareTo(targetQty) >= 0;
    }

    private boolean isSafetyStockSatisfiedWithoutDemand(BigDecimal inventoryQty, BigDecimal expectedDemand, BigDecimal safetyStock) {
        return max(expectedDemand, ZERO_QUANTITY).compareTo(ZERO_QUANTITY) <= 0
                && max(inventoryQty, ZERO_QUANTITY).compareTo(max(safetyStock, ZERO_QUANTITY)) >= 0;
    }

    private String resolveReplenishmentPriority(
            BigDecimal inventoryQty,
            BigDecimal expectedDemand,
            BigDecimal safetyStock,
            BigDecimal suggestedQty
    ) {
        BigDecimal displaySuggestedQty = scaleQuantity(suggestedQty);
        BigDecimal displayInventoryQty = scaleQuantity(inventoryQty);
        BigDecimal displayExpectedDemand = scaleQuantity(expectedDemand);
        BigDecimal displaySafetyStock = scaleQuantity(safetyStock);
        if (displaySuggestedQty.compareTo(ZERO_QUANTITY) <= 0) {
            return null;
        }

        BigDecimal normalizedInventory = max(displayInventoryQty, ZERO_QUANTITY);
        BigDecimal demandBaseline = max(max(displayExpectedDemand, displaySafetyStock), displaySuggestedQty);
        BigDecimal targetQty = normalizedInventory.add(displaySuggestedQty);
        if (targetQty.compareTo(ZERO_QUANTITY) <= 0) {
            return PRIORITY_NORMAL;
        }

        BigDecimal shortageRatio = displaySuggestedQty.divide(targetQty, 4, RoundingMode.HALF_UP);
        BigDecimal emergencyThreshold = demandBaseline.multiply(BigDecimal.valueOf(0.30d));

        if (normalizedInventory.compareTo(ZERO_QUANTITY) <= 0
                || normalizedInventory.compareTo(emergencyThreshold) < 0
                || shortageRatio.compareTo(BigDecimal.valueOf(0.65d)) >= 0) {
            return normalizePriorityLabel(PRIORITY_URGENT);
        }
        if (normalizedInventory.compareTo(demandBaseline) < 0
                || shortageRatio.compareTo(BigDecimal.valueOf(0.35d)) >= 0) {
            return normalizePriorityLabel(PRIORITY_HIGH);
        }
        return normalizePriorityLabel(PRIORITY_NORMAL);
    }

    private String resolveForecastItemPriority(
            PurchaseDemandForecast forecast,
            PurchaseDemandForecastItem item,
            MaterialProfile profile
    ) {
        if (item == null) {
            return PRIORITY_NORMAL;
        }
        ForecastDemandSnapshot snapshot = buildDemandSnapshot(
                forecast,
                item,
                profile == null ? ZERO_QUANTITY : profile.minStock()
        );
        return normalizePriorityLabel(resolveReplenishmentPriority(
                item.getCurrentInventoryQty(),
                snapshot.expectedDemand(),
                snapshot.safetyStock(),
                item.getSuggestedQty()
        ));
    }

    private String normalizePriorityLabel(String priority) {
        if (PRIORITY_URGENT.equals(priority)) {
            return PRIORITY_URGENT;
        }
        if (PRIORITY_HIGH.equals(priority)) {
            return PRIORITY_HIGH;
        }
        return PRIORITY_NORMAL;
    }

    private BigDecimal resolveConfidenceRate(
            BigDecimal suggestedQty,
            BigDecimal lowerQty,
            BigDecimal upperQty
    ) {
        BigDecimal displaySuggestedQty = scaleQuantity(suggestedQty);
        if (displaySuggestedQty.compareTo(ZERO_QUANTITY) <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal displayLowerQty = scaleQuantity(lowerQty);
        BigDecimal displayUpperQty = scaleQuantity(upperQty);
        BigDecimal bandWidth = displayUpperQty.subtract(displayLowerQty);
        BigDecimal denominator = displaySuggestedQty.multiply(BigDecimal.valueOf(2L));
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal bandRate = bandWidth.divide(denominator, 4, RoundingMode.HALF_UP);
        BigDecimal confidenceRate = BigDecimal.ONE.subtract(bandRate)
                .multiply(BigDecimal.valueOf(100L));
        return clamp(confidenceRate, BigDecimal.ZERO, BigDecimal.valueOf(100L)).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveEstimatedUnitPrice(
            BigDecimal orderPrice,
            BigDecimal planPrice,
            BigDecimal inventoryPrice,
            BigDecimal fallbackPrice
    ) {
        BigDecimal price = firstPositive(orderPrice, planPrice, inventoryPrice, fallbackPrice);
        return scaleAmount(price == null ? BigDecimal.ZERO : price);
    }

    private ConfidenceRange buildConfidenceRange(
            BigDecimal suggestedQty,
            BigDecimal historicalPlanDaily,
            BigDecimal historicalOrderDaily,
            BigDecimal recipeWeeklyDemand,
            BigDecimal inventoryQty,
            BigDecimal estimatedPrice
    ) {
        if (suggestedQty.compareTo(ZERO_QUANTITY) <= 0) {
            return new ConfidenceRange(ZERO_QUANTITY, ZERO_QUANTITY);
        }

        int positiveSourceCount = 0;
        if (historicalPlanDaily.compareTo(ZERO_QUANTITY) > 0) positiveSourceCount++;
        if (historicalOrderDaily.compareTo(ZERO_QUANTITY) > 0) positiveSourceCount++;
        if (recipeWeeklyDemand.compareTo(ZERO_QUANTITY) > 0) positiveSourceCount++;
        if (inventoryQty.compareTo(ZERO_QUANTITY) > 0) positiveSourceCount++;
        if (estimatedPrice.compareTo(ZERO_AMOUNT) > 0) positiveSourceCount++;

        BigDecimal denominator = max(max(historicalPlanDaily, historicalOrderDaily), BigDecimal.ONE);
        BigDecimal divergence = abs(historicalPlanDaily.subtract(historicalOrderDaily))
                .divide(denominator, 4, RoundingMode.HALF_UP);
        BigDecimal bandRate = BigDecimal.valueOf(0.32d)
                .subtract(BigDecimal.valueOf(positiveSourceCount).multiply(BigDecimal.valueOf(0.03d)))
                .add(min(divergence.multiply(BigDecimal.valueOf(0.12d)), BigDecimal.valueOf(0.10d)));
        bandRate = clamp(bandRate, BigDecimal.valueOf(0.12d), BigDecimal.valueOf(0.35d));

        BigDecimal lower = scaleQuantity(suggestedQty.multiply(BigDecimal.ONE.subtract(bandRate)));
        BigDecimal upper = scaleQuantity(suggestedQty.multiply(BigDecimal.ONE.add(bandRate)));
        return new ConfidenceRange(lower, upper);
    }

    private String buildForecastBasis(
            String materialName,
            BigDecimal historicalPlanDaily,
            BigDecimal historicalOrderDaily,
            BigDecimal inventoryQty,
            BigDecimal recipeWeeklyDemand,
            BigDecimal recipeOutputDemand,
            BigDecimal calendarFactor,
            BigDecimal holidayFactor,
            BigDecimal activityFactor,
            BigDecimal expectedDemand,
            BigDecimal safetyStock,
            BigDecimal suggestedQty
    ) {
        String activityText = activityFactor.compareTo(BigDecimal.ONE) > 0
                ? "检测到活动影响"
                : "未检索到活动增量";
        return String.format(
                Locale.ROOT,
                "物料%s：近30天计划日均%s，订单日均%s，当前库存%s，未来7天菜谱需求%s，本次输出需求%s，日历因子%s，节假日因子%s，活动因子%s，安全库存%s，建议采购%s（%s）。",
                materialName,
                formatDecimal(historicalPlanDaily, 3),
                formatDecimal(historicalOrderDaily, 3),
                formatDecimal(inventoryQty, 3),
                formatDecimal(recipeWeeklyDemand, 3),
                formatDecimal(recipeOutputDemand, 3),
                formatDecimal(calendarFactor, 3),
                formatDecimal(holidayFactor, 3),
                formatDecimal(activityFactor, 3),
                formatDecimal(safetyStock, 3),
                formatDecimal(suggestedQty, 3),
                activityText
        );
    }

    private String buildSummaryBasis(BigDecimal calendarFactor, BigDecimal holidayFactor, BigDecimal activityFactor) {
        return String.format(
                Locale.ROOT,
                "已按采购需求预测算法统一综合近30天真实消耗/采购计划/采购订单、未来7天菜谱理论需求、当前库存与待执行/在途量，并叠加单位日历%s、节假日%s、活动%s因子，同时结合分层模型、订货点、目标库存、供应商履约与成本约束进行测算。",
                formatDecimal(calendarFactor, 3),
                formatDecimal(holidayFactor, 3),
                formatDecimal(activityFactor, 3)
        );
    }

    private BigDecimal resolveCalendarFactor(String dimension, LocalDate startDate, LocalDate endDate) {
        int weekendDays = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            DayOfWeek dayOfWeek = cursor.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                weekendDays++;
            }
            cursor = cursor.plusDays(1);
        }
        if (weekendDays <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal baseIncrement = DIMENSION_DAILY.equals(dimension) ? BigDecimal.valueOf(0.06d) : BigDecimal.valueOf(0.03d);
        return BigDecimal.ONE.add(baseIncrement.multiply(BigDecimal.valueOf(weekendDays)));
    }

    private BigDecimal resolveHolidayFactor(LocalDate startDate, LocalDate endDate) {
        int holidayDays = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            if (FIXED_HOLIDAYS.contains(MonthDay.from(cursor))) {
                holidayDays++;
            }
            cursor = cursor.plusDays(1);
        }
        if (holidayDays <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE.add(BigDecimal.valueOf(holidayDays).multiply(BigDecimal.valueOf(0.05d)));
    }

    private BigDecimal resolveActivityFactor(Long orgId, LocalDate startDate, LocalDate endDate) {
        String tableName = resolveAvailableActivityTable();
        if (tableName == null) {
            return BigDecimal.ONE;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName +
                            " WHERE org_id = ? AND " +
                            " ((start_date IS NULL OR start_date <= ?) AND (end_date IS NULL OR end_date >= ?))",
                    Integer.class,
                    orgId,
                    Date.valueOf(endDate),
                    Date.valueOf(startDate)
            );
            if (count == null || count <= 0) {
                return BigDecimal.ONE;
            }
            return BigDecimal.ONE.add(BigDecimal.valueOf(Math.min(count, 4)).multiply(BigDecimal.valueOf(0.04d)));
        } catch (Exception ex) {
            log.debug("活动因子计算失败，按中性因子处理: {}", ex.getMessage());
            return BigDecimal.ONE;
        }
    }

    private String resolveAvailableActivityTable() {
        for (String tableName : ACTIVITY_TABLE_CANDIDATES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    Integer.class,
                    tableName
            );
            if (count != null && count > 0) {
                return tableName;
            }
        }
        return null;
    }

    private List<Long> normalizeDetailIds(List<Long> detailIds) {
        if (detailIds == null || detailIds.isEmpty()) {
            return Collections.emptyList();
        }
        return detailIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeDimension(String dimension) {
        String normalized = StrUtil.trim(dimension);
        if (StrUtil.isBlank(normalized)) {
            normalized = DIMENSION_WEEKLY;
        }
        if (!DIMENSION_DAILY.equals(normalized) && !DIMENSION_WEEKLY.equals(normalized)) {
            throw BizException.badRequest("预测维度仅支持 daily 或 weekly");
        }
        return normalized;
    }

    private int resolveForecastDays(String dimension) {
        return DIMENSION_DAILY.equals(dimension) ? 1 : 7;
    }

    private Long resolveTargetOrgId(Long requestOrgId, boolean required) {
        if (requestOrgId != null) {
            ensureOrgAllowed(requestOrgId);
            return requestOrgId;
        }
        Long currentOrgId = UserContext.getOrgId();
        if (currentOrgId != null) {
            ensureOrgAllowed(currentOrgId);
            return currentOrgId;
        }
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        if (allowedOrgIds == null || allowedOrgIds.isEmpty()) {
            if (required) {
                throw BizException.badRequest("当前用户未绑定可用组织，无法执行预测");
            }
            return null;
        }
        return allowedOrgIds.get(0);
    }

    private void ensurePermission(String permissionCode, String errorMessage) {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.forbidden(errorMessage);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                        "FROM auth_user_role ur " +
                        "JOIN auth_role r ON r.id = ur.role_id " +
                        "JOIN auth_role_permission rp ON rp.role_id = r.id " +
                        "JOIN auth_permission p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ? " +
                        "  AND r.deleted = 0 " +
                        "  AND r.status = 'active' " +
                        "  AND p.status = 'active' " +
                        "  AND p.permission_code = ?",
                Long.class,
                userId,
                permissionCode
        );
        if (count == null || count <= 0L) {
            throw BizException.forbidden(errorMessage);
        }
    }

    private List<Long> resolveAllowedOrgIds() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isAllAccess()) {
            return null;
        }
        return new ArrayList<>(scope.getOrgIds());
    }

    private void ensureOrgAllowed(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    private String generateUniqueForecastNo() {
        String candidate;
        do {
            candidate = "PF-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + ThreadLocalRandom.current().nextInt(100, 1000);
        } while (existsForecastNo(candidate));
        return candidate;
    }

    private boolean existsForecastNo(String forecastNo) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_demand_forecast WHERE deleted = 0 AND forecast_no = ?",
                Long.class,
                forecastNo
        );
        return count != null && count > 0;
    }

    private Map<Long, String> resolveOperatorNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> validUserIds = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (validUserIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, COALESCE(NULLIF(TRIM(real_name), ''), NULLIF(TRIM(username), ''), CAST(id AS CHAR)) AS displayName " +
                        "FROM auth_user WHERE deleted = 0 AND id IN (" + placeholders(validUserIds.size()) + ")",
                validUserIds.toArray()
        );
        Map<Long, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(toLong(row.get("id")), asString(row.get("displayName")));
        }
        return result;
    }

    private String resolveOperatorName(Long userId) {
        if (userId == null) {
            return "系统";
        }
        return resolveOperatorNames(Set.of(userId)).getOrDefault(userId, resolveOperatorFallback(userId));
    }

    private String resolveCurrentUserDisplayName() {
        String realName = normalizeOptionalText(UserContext.getRealName());
        if (StrUtil.isNotBlank(realName)) {
            return realName;
        }
        String username = normalizeOptionalText(UserContext.getUsername());
        if (StrUtil.isNotBlank(username)) {
            return username;
        }
        return resolveOperatorName(resolveCurrentUserId());
    }

    private String resolveOperatorFallback(Long userId) {
        return userId == null ? "系统" : String.valueOf(userId);
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) {
            return "";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT org_name FROM sys_organization WHERE id = ? AND deleted = 0 LIMIT 1",
                orgId
        );
        return rows.isEmpty() ? "" : asString(rows.get(0).get("org_name"));
    }

    private Long resolveTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : DEFAULT_TENANT_ID;
    }

    private Long resolveCurrentUserId() {
        return UserContext.getUserId() != null ? UserContext.getUserId() : DEFAULT_USER_ID;
    }

    private void ensureForecastItemColumn(String columnName, String alterSql) {
        ensureTableColumn("scm_purchase_demand_forecast_item", columnName, alterSql);
    }

    private void ensureTableColumn(String tableName, String columnName, String alterSql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class,
                    tableName,
                    columnName
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ex) {
            log.warn("采购需求预测字段补齐失败: {}.{} - {}", tableName, columnName, ex.getMessage());
        }
    }

    private List<Object> appendArgs(Object first, Object second, Collection<Long> ids) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.add(second);
        args.addAll(ids);
        return args;
    }

    private List<Object> appendArgs(
            Object first,
            Object second,
            Collection<Long> firstIds,
            Object third,
            Object fourth,
            Collection<Long> secondIds,
            Object fifth,
            Object sixth,
            Collection<Long> thirdIds
    ) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.add(second);
        args.addAll(firstIds);
        args.add(third);
        args.add(fourth);
        args.addAll(secondIds);
        args.add(fifth);
        args.add(sixth);
        args.addAll(thirdIds);
        return args;
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private String normalizeOptionalText(String value) {
        return StrUtil.trimToNull(value);
    }

    private List<Long> normalizeRequestedOrgIds(Collection<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> normalized = orgIds.stream()
                .filter(Objects::nonNull)
                .filter(orgId -> orgId > 0L)
                .distinct()
                .collect(Collectors.toList());
        normalized.forEach(this::ensureOrgAllowed);
        return normalized;
    }

    private List<Long> normalizeMaterialIds(Collection<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Collections.emptyList();
        }
        return materialIds.stream()
                .filter(this::isValidMaterialId)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isValidMaterialId(Long materialId) {
        return materialId != null && materialId > 0L;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.format(DATE_FORMATTER);
    }

    private String formatDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate.format(DATE_FORMATTER);
        }
        if (value instanceof Date date) {
            return date.toLocalDate().format(DATE_FORMATTER);
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime().toLocalDate().format(DATE_FORMATTER);
        }
        return String.valueOf(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private String formatDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.format(DATE_TIME_FORMATTER);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        return String.valueOf(value);
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return value == null ? ZERO_QUANTITY : value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleAmount(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleFactor(BigDecimal value) {
        return value == null ? BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP) : value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleRate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP) : value.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal left, BigDecimal right, int scale) {
        if (left == null || right == null || right.compareTo(BigDecimal.ZERO) == 0) {
            return scale == 2 ? ZERO_AMOUNT : ZERO_QUANTITY;
        }
        return left.divide(right, scale, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal normalizedNumerator = numerator == null ? BigDecimal.ZERO : numerator;
        BigDecimal normalizedDenominator = denominator == null ? BigDecimal.ZERO : denominator;
        if (normalizedDenominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return normalizedNumerator.divide(normalizedDenominator, 6, RoundingMode.HALF_UP);
    }

    private String format(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal scaled = value.scale() > 3 ? scaleRate(value) : scaleQuantity(value);
        return scaled.stripTrailingZeros().toPlainString();
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        if (left == null) return right == null ? BigDecimal.ZERO : right;
        if (right == null) return left;
        return left.max(right);
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) {
        if (left == null) return right == null ? BigDecimal.ZERO : right;
        if (right == null) return left;
        return left.min(right);
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.max(min).min(max);
    }

    private BigDecimal abs(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.abs();
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String formatDecimal(BigDecimal value, int scale) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.setScale(scale, RoundingMode.HALF_UP);
        return normalized.stripTrailingZeros().toPlainString();
    }

    private BigDecimal normalizeSuggestedQtyByUnit(String unit, BigDecimal suggestedQty) {
        BigDecimal displaySuggestedQty = scaleQuantity(suggestedQty);
        if (displaySuggestedQty.compareTo(ZERO_QUANTITY) <= 0) {
            return ZERO_QUANTITY;
        }
        if (!shouldRoundSuggestedQty(unit)) {
            return displaySuggestedQty;
        }
        return scaleQuantity(displaySuggestedQty.setScale(0, RoundingMode.HALF_UP));
    }

    private boolean shouldRoundSuggestedQty(String unit) {
        String normalizedUnit = normalizeUnit(unit);
        if (normalizedUnit.isEmpty()) {
            return false;
        }
        if (FRACTIONAL_UNITS.contains(normalizedUnit)) {
            return false;
        }
        return INTEGER_ONLY_UNITS.contains(normalizedUnit);
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : unit.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Boolean isDeleted(Integer deleted) {
        return deleted != null && deleted == 1;
    }

    private record HistoricalMetric(
            String materialName,
            String materialSpec,
            String materialUnit,
            BigDecimal totalQty,
            BigDecimal avgPrice
    ) {
        private static HistoricalMetric empty() {
            return new HistoricalMetric("", "", "", ZERO_QUANTITY, ZERO_AMOUNT);
        }
    }

    private record InventoryMetric(
            String materialName,
            String materialSpec,
            String materialUnit,
            BigDecimal currentStockQty,
            BigDecimal availableQty,
            BigDecimal referenceUnitCost
    ) {
        private static InventoryMetric empty() {
            return new InventoryMetric("", "", "", ZERO_QUANTITY, ZERO_QUANTITY, ZERO_AMOUNT);
        }
    }

    private record RecipeMetric(
            String materialName,
            String materialSpec,
            String materialUnit,
            BigDecimal weeklyDemandQty
    ) {
        private static RecipeMetric empty() {
            return new RecipeMetric("", "", "", ZERO_QUANTITY);
        }
    }

    private record MaterialProfile(
            Long materialId,
            String materialName,
            String materialSpec,
            String materialUnit,
            String materialCategory,
            Integer shelfLifeDays,
            Integer warningDays,
            BigDecimal minStock,
            BigDecimal maxStock
    ) {
    }

    private record FeatureMetric(
            BigDecimal currentStockQty,
            BigDecimal availableStockQty,
            BigDecimal avgConsumption7d,
            BigDecimal avgConsumption14d,
            BigDecimal avgConsumption30d,
            BigDecimal stdConsumption30d,
            BigDecimal recipeDemand7d,
            BigDecimal recipeHistory30d,
            BigDecimal pendingPlanQty,
            BigDecimal inTransitQty,
            BigDecimal actualConsumption30d,
            Integer consumptionDays30d,
            BigDecimal inventoryTurnoverDays,
            String forecastType,
            BigDecimal recipeDriveRatio,
            BigDecimal demandActiveRatio,
            BigDecimal demandCv,
            BigDecimal activitySensitivity,
            String materialSegment,
            Integer leadTimeDays,
            BigDecimal serviceLevel,
            List<BigDecimal> recentConsumptionSeries30d
    ) {
        private static FeatureMetric empty() {
            return new FeatureMetric(
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    0,
                    ZERO_QUANTITY,
                    PurchaseDemandForecastRuleEngine.MODEL_DOUBLE_EXP,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    ZERO_QUANTITY,
                    BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP),
                    PurchaseDemandForecastRuleEngine.SEGMENT_STABLE,
                    3,
                    BigDecimal.valueOf(0.90d).setScale(3, RoundingMode.HALF_UP),
                    Collections.<BigDecimal>emptyList()
            );
        }
    }

    private record SupplierRecommendation(
            Long materialId,
            Long supplierId,
            String supplierName,
            BigDecimal avgUnitPrice,
            BigDecimal avgLeadTimeDays,
            BigDecimal stdLeadTimeDays,
            BigDecimal effectiveLeadTimeDays,
            BigDecimal fillRate,
            BigDecimal onTimeRate,
            BigDecimal score,
            Integer orderCount,
            String sourceType
    ) {
        private static SupplierRecommendation empty(Long materialId) {
            return new SupplierRecommendation(
                    materialId,
                    null,
                    "",
                    ZERO_AMOUNT,
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    0,
                    "empty"
            );
        }

        private boolean isEmpty() {
            return supplierId == null || supplierId <= 0;
        }

        private SupplierRecommendation withMaterialId(Long nextMaterialId) {
            return new SupplierRecommendation(
                    nextMaterialId,
                    supplierId,
                    supplierName,
                    avgUnitPrice,
                    avgLeadTimeDays,
                    stdLeadTimeDays,
                    effectiveLeadTimeDays,
                    fillRate,
                    onTimeRate,
                    score,
                    orderCount,
                    sourceType
            );
        }

        private SupplierRecommendation withScore(BigDecimal nextScore) {
            return new SupplierRecommendation(
                    materialId,
                    supplierId,
                    supplierName,
                    avgUnitPrice,
                    avgLeadTimeDays,
                    stdLeadTimeDays,
                    effectiveLeadTimeDays,
                    fillRate,
                    onTimeRate,
                    nextScore == null ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP) : nextScore.setScale(6, RoundingMode.HALF_UP),
                    orderCount,
                    sourceType
            );
        }
    }

    private record PreviousForecastSnapshot(
            String forecastNo,
            LocalDate basisDate,
            BigDecimal forecastDemandQty
    ) {
    }

    private record InventoryAuditSnapshot(
            LocalDate statDate,
            boolean mismatchDetected,
            BigDecimal expectedClosingQty,
            BigDecimal actualClosingQty
    ) {
    }

    private record ActivityContext(
            BigDecimal factor,
            List<String> activityNames
    ) {
    }

    private record PhaseThreeDecision(
            BigDecimal avgDailyDemandQty,
            Integer reviewPeriodDays,
            BigDecimal reorderPointQty,
            BigDecimal targetStockQty,
            BigDecimal inventoryPositionQty,
            BigDecimal theoreticalSuggestedQty,
            BigDecimal finalSuggestedQty,
            BigDecimal minOrderQty,
            BigDecimal packSize,
            Integer maxCoverageDays,
            BigDecimal maxAllowedStockQty,
            BigDecimal effectiveLeadTimeDays,
            Long supplierId,
            String supplierName,
            BigDecimal supplierScore,
            BigDecimal supplierFillRate,
            BigDecimal supplierOnTimeRate,
            boolean orderNow,
            String orderAction,
            BigDecimal shortageCost,
            BigDecimal holdingCost,
            BigDecimal expiryRiskCost,
            BigDecimal orderProcessingCost,
            BigDecimal purchasePriceCost,
            BigDecimal totalCost,
            String riskFlags
    ) {
    }

    private record ForecastPreparationSnapshot(
            Collection<Long> materialIds,
            Map<Long, HistoricalMetric> planMetrics,
            Map<Long, HistoricalMetric> orderMetrics,
            Map<Long, InventoryMetric> inventoryMetrics,
            Map<Long, RecipeMetric> recipeMetrics,
            Map<Long, MaterialProfile> materialProfiles,
            Map<Long, BigDecimal> fallbackPrices,
            Map<Long, FeatureMetric> featureMetrics,
            Map<Long, BigDecimal> recipeHistoryDemandMap,
            Map<Long, List<MaterialDailyConsumption>> consumptionByMaterial
    ) {
    }

    private record ForecastDemandSnapshot(
            BigDecimal expectedDemand,
            BigDecimal safetyStock
    ) {
    }

    private record ConsumptionResolution(
            BigDecimal consumedQty,
            String dataSource
    ) {
    }

    private record ConfidenceRange(BigDecimal lower, BigDecimal upper) {
    }
}
