package com.xykj.wms.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.wms.entity.Material;
import com.xykj.wms.mapper.MaterialMapper;
import com.xykj.common.service.NotificationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 物料告警引擎
 * 负责检查物料效期/库存异常，生成/关闭告警
 *
 * 效期告警按批次维度生成（同物料不同批次各自独立告警）
 * 库存告警按物料维度生成（一个物料一条告警，批次明细存在 alert_detail 中）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialAlertEngine {

    private final JdbcTemplate jdbcTemplate;
    private final MaterialMapper materialMapper;
    private final ObjectMapper objectMapper;
    private final NotificationHelper notificationHelper;

    // ========== 异常类型常量 ==========
    public static final String ANOMALY_EXPIRY_WARNING = "material_expiry_warning";
    public static final String ANOMALY_NEAR_EXPIRY = "material_near_expiry";
    public static final String ANOMALY_EXPIRED = "material_expired";
    public static final String ANOMALY_STOCK_HIGH = "material_stock_high";
    public static final String ANOMALY_STOCK_LOW = "material_stock_low";

    private static final Map<String, String> ANOMALY_LABELS = Map.of(
            ANOMALY_EXPIRY_WARNING, "物料效期预警",
            ANOMALY_NEAR_EXPIRY, "物料临期告警",
            ANOMALY_EXPIRED, "物料过期告警",
            ANOMALY_STOCK_HIGH, "库存积压告警",
            ANOMALY_STOCK_LOW, "库存不足告警"
    );

    private static final Map<String, String> ANOMALY_DEFAULT_LEVELS = Map.of(
            ANOMALY_EXPIRY_WARNING, "warning",
            ANOMALY_NEAR_EXPIRY, "error",
            ANOMALY_EXPIRED, "critical",
            ANOMALY_STOCK_HIGH, "warning",
            ANOMALY_STOCK_LOW, "warning"
    );

    // ========== 对外入口（签名不变） ==========

    /**
     * 检查单个物料（实时触发用）
     */
    public void checkMaterial(Long materialId) {
        checkMaterials(List.of(materialId));
    }

    /**
     * 批量检查物料
     */
    public void checkMaterials(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            log.warn("物料告警校验跳过：materialIds为空");
            return;
        }

        log.info("开始物料告警校验：materialIds={}", materialIds);

        List<Map<String, Object>> rules = loadMaterialRules();
        if (rules.isEmpty()) {
            log.warn("物料告警校验跳过：无启用的物料告警规则。请检查 device_alert_rule 表中 rule_type='material' AND is_enabled=1 AND deleted=0 的记录");
            return;
        }
        log.info("加载到 {} 条启用的物料告警规则", rules.size());

        for (Long materialId : materialIds) {
            try {
                checkSingleMaterial(materialId, rules);
            } catch (Exception e) {
                log.error("检查物料告警失败: materialId={}, error={}", materialId, e.getMessage(), e);
            }
        }
    }

    /**
     * 全量扫描所有活跃物料（定时任务用）
     */
    public void checkAllMaterials() {
        List<Map<String, Object>> rules = loadMaterialRules();
        if (rules.isEmpty()) {
            log.info("无启用的物料告警规则，跳过巡检");
            return;
        }

        List<Material> materials = materialMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Material>()
                        .eq(Material::getStatus, "active")
                        .eq(Material::getDeleted, 0));

        log.info("开始物料告警巡检，共 {} 个活跃物料", materials.size());
        int alertCount = 0;

        for (Material material : materials) {
            try {
                alertCount += checkSingleMaterial(material.getId(), rules);
            } catch (Exception e) {
                log.error("检查物料告警失败: materialId={}, error={}", material.getId(), e.getMessage(), e);
            }
        }

        log.info("物料告警巡检完成，共处理 {} 个物料，生成/更新告警 {} 条", materials.size(), alertCount);
    }

    /**
     * 从规则 material_ids 中移除指定物料ID（物料删除时调用）
     */
    public void removeMaterialFromRules(Long materialId) {
        try {
            String materialIdStr = String.valueOf(materialId);
            // 仅查询包含该物料ID的规则，避免全表扫描
            List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                    "SELECT id, material_ids FROM device_alert_rule " +
                            "WHERE rule_type = 'material' AND deleted = 0 AND material_ids LIKE ?",
                    "%" + materialIdStr + "%");
            log.info("解绑物料告警规则: materialId={}, 匹配到 {} 条规则", materialId, rules.size());
            for (Map<String, Object> rule : rules) {
                Long ruleId = ((Number) rule.get("id")).longValue();
                String rawIds = (String) rule.get("material_ids");
                if (rawIds == null || rawIds.isBlank()) continue;
                String updated = Arrays.stream(rawIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && !s.equals(materialIdStr))
                        .collect(Collectors.joining(","));
                jdbcTemplate.update(
                        "UPDATE device_alert_rule SET material_ids = ? WHERE id = ?",
                        updated.isEmpty() ? null : updated, ruleId);
                log.info("从告警规则中移除物料: ruleId={}, materialId={}, 更新后materialIds={}", ruleId, materialId, updated);
            }
        } catch (Exception e) {
            log.error("从告警规则中移除物料ID失败: materialId={}, error={}", materialId, e.getMessage(), e);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 加载所有启用的物料告警规则
     */
    private List<Map<String, Object>> loadMaterialRules() {
        try {
            List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                    "SELECT id, material_ids, alert_level, notify_channels, notify_users, org_id, tenant_id, auto_dispatch, dispatch_scope_roles " +
                            "FROM device_alert_rule WHERE rule_type = 'material' AND is_enabled = 1 AND deleted = 0");
            for (Map<String, Object> rule : rules) {
                log.debug("物料告警规则: id={}, material_ids={}", rule.get("id"), rule.get("material_ids"));
            }
            return rules;
        } catch (Exception e) {
            log.error("加载物料告警规则失败（请检查 device_alert_rule 表是否有 material_ids 列）: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 检查单个物料，返回生成/更新的告警数
     */
    private int checkSingleMaterial(Long materialId, List<Map<String, Object>> rules) {
        // 1. 查物料信息
        Material material = materialMapper.selectById(materialId);
        if (material == null || !"active".equals(material.getStatus()) || material.getDeleted() == 1) {
            log.debug("物料告警跳过：物料不存在/未启用/已删除, materialId={}", materialId);
            return 0;
        }

        // 2. 查匹配的规则
        Map<String, Object> matchedRule = findMatchingRule(rules, materialId);
        if (matchedRule == null) {
            log.warn("物料告警跳过：物料未关联到任何告警规则, materialId={}, materialCode={}", materialId, material.getMaterialCode());
            return 0;
        }

        Long ruleId = ((Number) matchedRule.get("id")).longValue();
        Long orgId = material.getOrgId();
        Long tenantId = material.getTenantId();

        // 3. 查询所有有库存的批次
        List<BatchRow> batches = queryInventoryBatches(materialId);
        BigDecimal currentStock = batches.stream()
                .map(b -> b.quantity != null ? b.quantity : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int nearExpiryDays = material.getNearExpiryDays() != null ? material.getNearExpiryDays() : 7;
        int warningDays = material.getWarningDays() != null ? material.getWarningDays() : 30;
        int alertCount = 0;

        log.info("物料告警检查: materialId={}, name={}, currentStock={}, maxStock={}, minStock={}, 批次数={}, ruleId={}",
                materialId, material.getMaterialName(), currentStock, material.getMaxStock(), material.getMinStock(),
                batches.size(), ruleId);

        // 4. 按批次逐条做效期检查
        for (BatchRow batch : batches) {
            if (batch.expiryDate == null) continue;

            batch.remainingDays = (int) ChronoUnit.DAYS.between(LocalDate.now(), batch.expiryDate);

            String currentType;
            if (batch.remainingDays <= 0) {
                currentType = ANOMALY_EXPIRED;
            } else if (batch.remainingDays <= nearExpiryDays) {
                currentType = ANOMALY_NEAR_EXPIRY;
            } else if (batch.remainingDays <= warningDays) {
                currentType = ANOMALY_EXPIRY_WARNING;
            } else {
                currentType = null;
            }

            if (currentType != null) {
                log.info("批次效期异常: materialId={}, batchNo={}, remainingDays={}, anomalyType={}",
                        materialId, batch.batchNo, batch.remainingDays, currentType);
                alertCount += evaluateExpiryAlert(material, batch, currentType, currentStock,
                        nearExpiryDays, warningDays, ruleId, matchedRule, orgId, tenantId);
            }

            // 按批次更新 inventory.status（按各自到期日）
            updateBatchStatus(batch, nearExpiryDays);
        }

        // 5. 库存检查（物料维度）
        //    注意：当 currentStock 同时 <= minStock 且 >= maxStock 时（典型场景：minStock == maxStock），
        //    仅触发库存不足告警（更紧急），不触发积压告警
        BigDecimal minStock = material.getMinStock();
        BigDecimal maxStock = material.getMaxStock();
        boolean lowMet = minStock != null && currentStock.compareTo(minStock) <= 0;
        boolean highMet = maxStock != null && currentStock.compareTo(maxStock) >= 0;

        if (lowMet) {
            log.info("库存不足条件满足: materialId={}, currentStock={} <= minStock={}", materialId, currentStock, minStock);
            alertCount += evaluateStockAlert(material, ANOMALY_STOCK_LOW, currentStock, batches,
                    nearExpiryDays, warningDays, ruleId, matchedRule, orgId, tenantId);
        }
        if (highMet && !lowMet) {
            log.info("库存积压条件满足: materialId={}, currentStock={} >= maxStock={}", materialId, currentStock, maxStock);
            alertCount += evaluateStockAlert(material, ANOMALY_STOCK_HIGH, currentStock, batches,
                    nearExpiryDays, warningDays, ruleId, matchedRule, orgId, tenantId);
        }

        // 6. 自动关闭已消除的异常
        autoCloseResolvedAlerts(materialId, batches, currentStock, minStock, maxStock, nearExpiryDays, warningDays);

        log.info("物料告警检查完成: materialId={}, 生成告警 {} 条", materialId, alertCount);
        return alertCount;
    }

    /**
     * 查找匹配物料的告警规则
     */
    private Map<String, Object> findMatchingRule(List<Map<String, Object>> rules, Long materialId) {
        String mid = String.valueOf(materialId);
        for (Map<String, Object> rule : rules) {
            String rawIds = (String) rule.get("material_ids");
            if (rawIds == null || rawIds.isEmpty()) continue;
            for (String id : rawIds.split(",")) {
                if (mid.equals(id.trim())) {
                    return rule;
                }
            }
        }
        return null;
    }

    /**
     * 查询物料所有有库存的批次行（含仓库名/仓位名）
     */
    private List<BatchRow> queryInventoryBatches(Long materialId) {
        String sql = "SELECT i.id, i.warehouse_id, w.warehouse_name, i.location_id, l.location_name, " +
                "i.batch_no, i.quantity, i.production_date, i.expiry_date " +
                "FROM wms_inventory i " +
                "LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 " +
                "LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 " +
                "WHERE i.material_id = ? AND i.quantity > 0 " +
                "ORDER BY i.expiry_date ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            BatchRow row = new BatchRow();
            row.inventoryId = rs.getLong("id");
            row.warehouseId = rs.getLong("warehouse_id");
            row.warehouseName = rs.getString("warehouse_name");
            long locId = rs.getLong("location_id");
            row.locationId = rs.wasNull() ? null : locId;
            row.locationName = rs.getString("location_name");
            row.batchNo = rs.getString("batch_no");
            row.quantity = rs.getBigDecimal("quantity");
            row.productionDate = rs.getDate("production_date") != null ? rs.getDate("production_date").toLocalDate() : null;
            row.expiryDate = rs.getDate("expiry_date") != null ? rs.getDate("expiry_date").toLocalDate() : null;
            return row;
        }, materialId);
    }

    // ========== 效期告警（按批次） ==========

    /**
     * 创建效期告警（按批次）
     * 1. 先关闭同物料同批次不同严重度的旧告警（升级/降级）
     * 2. 去重检查
     * 3. INSERT
     */
    private int evaluateExpiryAlert(Material material, BatchRow batch, String anomalyType,
                                     BigDecimal totalStock, int nearExpiryDays, int warningDays,
                                     Long ruleId, Map<String, Object> rule,
                                     Long orgId, Long tenantId) {
        Long materialId = material.getId();

        // 1. 关闭同物料同批次不同严重度的效期告警（升级/降级）
        closeOtherSeverityExpiryAlerts(materialId, batch.inventoryId, anomalyType);

        // 2. 去重
        if (hasUnclosedAlert(materialId, anomalyType, batch.inventoryId)) {
            log.info("物料告警去重跳过: materialId={}, anomalyType={}, inventoryId={}", materialId, anomalyType, batch.inventoryId);
            return 0;
        }

        // 3. 构建告警内容
        String label = ANOMALY_LABELS.get(anomalyType);
        StringBuilder content = new StringBuilder();
        content.append("【").append(label).append("】");
        content.append("物料「").append(material.getMaterialName()).append("」(")
                .append(material.getMaterialCode()).append(")");
        content.append("，仓库：").append(safeStr(batch.warehouseName));
        content.append("，仓位：").append(safeStr(batch.locationName));
        content.append("，批次：").append(safeStr(batch.batchNo));
        content.append("，批次数量").append(batch.quantity.toPlainString()).append(material.getUnit());
        content.append("，到期日期：").append(batch.expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        content.append("（剩余").append(batch.remainingDays).append("天）");
        content.append("，当前总库存：").append(totalStock.toPlainString()).append(material.getUnit());

        // 4. 构建 alert_detail JSON
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("materialId", materialId);
        detail.put("materialCode", material.getMaterialCode());
        detail.put("materialName", material.getMaterialName());
        detail.put("currentStock", totalStock);
        detail.put("unit", material.getUnit());
        detail.put("minStock", material.getMinStock());
        detail.put("maxStock", material.getMaxStock());
        detail.put("nearExpiryDays", nearExpiryDays);
        detail.put("warningDays", warningDays);
        detail.put("anomalyType", anomalyType);
        // 使用 inventoryId 作为批次唯一标识（同 batchNo 可能出现在不同仓库/仓位，并非唯一）
        detail.put("inventoryId", batch.inventoryId);
        detail.put("batchNo", batch.batchNo);
        detail.put("warehouseId", batch.warehouseId);
        detail.put("warehouseName", batch.warehouseName);
        detail.put("locationId", batch.locationId);
        detail.put("locationName", batch.locationName);
        detail.put("batchQuantity", batch.quantity);
        detail.put("expiryDate", batch.expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        detail.put("remainingDays", batch.remainingDays);

        // 告警级别
        String alertLevel = rule.get("alert_level") != null ? (String) rule.get("alert_level") : null;
        if (alertLevel == null || alertLevel.isEmpty()) {
            alertLevel = ANOMALY_DEFAULT_LEVELS.getOrDefault(anomalyType, "warning");
        }

        String alertNo = generateAlertNo(materialId, ruleId);

        try {
            String detailJson = objectMapper.writeValueAsString(detail);
            LocalDateTime now = LocalDateTime.now();

            jdbcTemplate.update(
                    "INSERT INTO device_alert (alert_no, alert_type, alert_rule_id, alert_level, " +
                            "material_id, alert_content, alert_detail, triggered_at, status, org_id, tenant_id, created_at, updated_at) " +
                            "VALUES (?, 'material', ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?)",
                    alertNo, ruleId, alertLevel, materialId,
                    content.toString(), detailJson, now, orgId, tenantId, now, now);

            log.info("物料效期告警已创建: alertNo={}, materialId={}, inventoryId={}, batchNo={}, anomalyType={}", alertNo, materialId, batch.inventoryId, batch.batchNo, anomalyType);

            // 尝试自动派单
            tryAutoDispatch(alertNo, rule);

            // 通知规则配置的通知用户（需包含站内信渠道）
            sendAlertNotification(rule, material, alertNo, content.toString(), alertLevel, orgId, tenantId);

            return 1;
        } catch (Exception e) {
            log.error("创建物料效期告警失败: materialId={}, inventoryId={}, batchNo={}, anomalyType={}, error={}", materialId, batch.inventoryId, batch.batchNo, anomalyType, e.getMessage(), e);
            return 0;
        }
    }

    // ========== 库存告警（按物料） ==========

    /**
     * 创建库存告警（按物料维度，批次明细存入 alert_detail）
     */
    private int evaluateStockAlert(Material material, String anomalyType, BigDecimal currentStock,
                                    List<BatchRow> batches, int nearExpiryDays, int warningDays,
                                    Long ruleId, Map<String, Object> rule,
                                    Long orgId, Long tenantId) {
        Long materialId = material.getId();

        // 去重（物料维度，batchNo=null）
        if (hasUnclosedAlert(materialId, anomalyType, null)) {
            log.info("物料告警去重跳过: materialId={}, anomalyType={}", materialId, anomalyType);
            return 0;
        }

        // 构建告警内容（摘要，不显示具体批次）
        String label = ANOMALY_LABELS.get(anomalyType);
        StringBuilder content = new StringBuilder();
        content.append("【").append(label).append("】");
        content.append("物料「").append(material.getMaterialName()).append("」(")
                .append(material.getMaterialCode()).append(")");
        content.append("，当前库存").append(currentStock.toPlainString()).append(material.getUnit());
        if (anomalyType.equals(ANOMALY_STOCK_HIGH) && material.getMaxStock() != null) {
            content.append("，最高库存").append(material.getMaxStock().toPlainString()).append(material.getUnit());
        }
        if (anomalyType.equals(ANOMALY_STOCK_LOW) && material.getMinStock() != null) {
            content.append("，最低库存").append(material.getMinStock().toPlainString()).append(material.getUnit());
        }
        content.append("，批次明细详见告警详情，共").append(batches.size()).append("个批次");

        // 构建 alert_detail JSON（含批次数组）
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("materialId", materialId);
        detail.put("materialCode", material.getMaterialCode());
        detail.put("materialName", material.getMaterialName());
        detail.put("currentStock", currentStock);
        detail.put("unit", material.getUnit());
        detail.put("minStock", material.getMinStock());
        detail.put("maxStock", material.getMaxStock());
        detail.put("nearExpiryDays", nearExpiryDays);
        detail.put("warningDays", warningDays);
        detail.put("anomalyType", anomalyType);

        // 批次数组
        List<Map<String, Object>> batchesArray = new ArrayList<>();
        for (BatchRow b : batches) {
            Map<String, Object> bMap = new LinkedHashMap<>();
            bMap.put("warehouseName", b.warehouseName);
            bMap.put("locationName", b.locationName);
            bMap.put("batchNo", b.batchNo);
            bMap.put("quantity", b.quantity);
            bMap.put("expiryDate", b.expiryDate != null ? b.expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
            batchesArray.add(bMap);
        }
        detail.put("batches", batchesArray);

        // 告警级别
        String alertLevel = rule.get("alert_level") != null ? (String) rule.get("alert_level") : null;
        if (alertLevel == null || alertLevel.isEmpty()) {
            alertLevel = ANOMALY_DEFAULT_LEVELS.getOrDefault(anomalyType, "warning");
        }

        String alertNo = generateAlertNo(materialId, ruleId);

        try {
            String detailJson = objectMapper.writeValueAsString(detail);
            LocalDateTime now = LocalDateTime.now();

            jdbcTemplate.update(
                    "INSERT INTO device_alert (alert_no, alert_type, alert_rule_id, alert_level, " +
                            "material_id, alert_content, alert_detail, triggered_at, status, org_id, tenant_id, created_at, updated_at) " +
                            "VALUES (?, 'material', ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?)",
                    alertNo, ruleId, alertLevel, materialId,
                    content.toString(), detailJson, now, orgId, tenantId, now, now);

            log.info("物料库存告警已创建: alertNo={}, materialId={}, anomalyType={}, 批次数={}", alertNo, materialId, anomalyType, batches.size());

            // 尝试自动派单
            tryAutoDispatch(alertNo, rule);

            // 通知规则配置的通知用户（需包含站内信渠道）
            sendAlertNotification(rule, material, alertNo, content.toString(), alertLevel, orgId, tenantId);

            return 1;
        } catch (Exception e) {
            log.error("创建物料库存告警失败: materialId={}, anomalyType={}, error={}", materialId, anomalyType, e.getMessage(), e);
            return 0;
        }
    }

    // ========== 自动派单 ==========

    /** 告警级别 → 自动派单优先级映射 */
    private static final Map<String, String> ALERT_LEVEL_TO_PRIORITY = Map.of(
            "critical", "high",
            "error", "high",
            "warning", "medium",
            "info", "low"
    );

    /**
     * 尝试自动派单（规则开启了自动派单时调用，失败保持待处理状态）
     */
    private void tryAutoDispatch(String alertNo, Map<String, Object> matchedRule) {
        try {
            // 检查规则是否开启自动派单
            Object autoDispatchObj = matchedRule.get("auto_dispatch");
            if (autoDispatchObj == null) return;
            int autoDispatch = ((Number) autoDispatchObj).intValue();
            if (autoDispatch != 1) return;

            // 查回告警 ID
            List<Long> alertIds = jdbcTemplate.queryForList(
                    "SELECT id FROM device_alert WHERE alert_no = ? AND deleted = 0 AND status = 'pending'",
                    Long.class, alertNo);
            if (alertIds.isEmpty()) return;
            Long alertId = alertIds.get(0);

            // 解析派单范围角色
            String scopeRoles = (String) matchedRule.get("dispatch_scope_roles");
            Long handlerId = null;
            if (scopeRoles != null && !scopeRoles.isBlank()) {
                List<Long> roleIds = Arrays.stream(scopeRoles.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(Long::parseLong).collect(Collectors.toList());
                if (!roleIds.isEmpty()) {
                    String placeholders = roleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    List<Long> handlers = jdbcTemplate.queryForList(
                            "SELECT e.id FROM sys_employee e " +
                                    "INNER JOIN auth_user u ON e.user_id = u.id " +
                                    "INNER JOIN auth_user_role ur ON ur.user_id = u.id " +
                                    "LEFT JOIN device_alert_dispatch d ON e.id = d.handler_id AND d.status IN ('pending', 'processing') AND d.deleted = 0 " +
                                    "WHERE e.status = 'active' AND u.status = 'active' AND e.deleted = 0 " +
                                    "AND ur.role_id IN (" + placeholders + ") " +
                                    "GROUP BY e.id ORDER BY COUNT(d.id) ASC LIMIT 1",
                            Long.class);
                    if (!handlers.isEmpty()) handlerId = handlers.get(0);
                }
            }
            if (handlerId == null) {
                log.warn("物料告警自动派单失败：没有可用的处理人, alertNo={}", alertNo);
                return;
            }

            String handlerName = jdbcTemplate.queryForObject(
                    "SELECT real_name FROM sys_employee WHERE id = ? AND deleted = 0",
                    String.class, handlerId);
            if (handlerName == null) handlerName = "未知";

            // 查回告警信息
            Map<String, Object> alert = jdbcTemplate.queryForMap(
                    "SELECT alert_no, alert_level, org_id, tenant_id FROM device_alert WHERE id = ?", alertId);

            // 生成派单编号
            String dispatchNo = jdbcTemplate.queryForObject(
                    "SELECT CONCAT('AD-', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(CAST(SUBSTRING(dispatch_no, 13) AS UNSIGNED)), 0) + 1, 4, '0')) " +
                            "FROM device_alert_dispatch WHERE dispatch_no LIKE CONCAT('AD-', DATE_FORMAT(NOW(), '%Y%m%d'), '%') AND deleted = 0",
                    String.class);

            String priority = ALERT_LEVEL_TO_PRIORITY.getOrDefault(
                    alert.get("alert_level") != null ? alert.get("alert_level").toString() : "warning", "medium");
            Long orgId = alert.get("org_id") != null ? ((Number) alert.get("org_id")).longValue() : null;
            Long tenantId = alert.get("tenant_id") != null ? ((Number) alert.get("tenant_id")).longValue() : 1L;

            // INSERT 派单记录
            jdbcTemplate.update(
                    "INSERT INTO device_alert_dispatch (dispatch_no, alert_id, alert_no, dispatch_type, " +
                            "assigner_id, assigner_name, handler_id, handler_name, deadline, priority, " +
                            "status, org_id, tenant_id, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 'auto', 0, '系统', ?, ?, ?, ?, 'pending', ?, ?, NOW(), NOW())",
                    dispatchNo, alertId, alertNo, handlerId, handlerName,
                    LocalDateTime.now().plusHours(24), priority, orgId, tenantId);

            // UPDATE 告警状态
            jdbcTemplate.update(
                    "UPDATE device_alert SET status = 'assigned', assigned_to = ?, assigned_at = NOW(), updated_at = NOW() " +
                            "WHERE id = ? AND status = 'pending'",
                    handlerId, alertId);

            // 查回派单 ID
            Long dispatchId = null;
            List<Long> dispatchIds = jdbcTemplate.queryForList(
                    "SELECT id FROM device_alert_dispatch WHERE dispatch_no = ?", Long.class, dispatchNo);
            if (!dispatchIds.isEmpty()) {
                dispatchId = dispatchIds.get(0);
                // INSERT 工单处理记录
                try {
                    jdbcTemplate.update(
                            "INSERT INTO device_alert_work_order_record (dispatch_id, alert_id, action, action_name, " +
                                    "operator_id, operator_name, content, created_at) " +
                                    "VALUES (?, ?, 'dispatch', '派单', 0, '系统', ?, NOW())",
                            dispatchId, alertId, "自动派单至" + handlerName);
                } catch (Exception e) {
                    log.warn("物料告警工单记录写入失败（不影响派单和通知）: dispatchNo={}", dispatchNo, e);
                }
            }

            // 发送派单通知给处理人
            try {
                Long authUserId = notificationHelper.employeeIdToAuthUserId(handlerId);
                if (authUserId == null) {
                    log.warn("派单通知跳过：处理人未关联 auth_user, handlerId={}, handlerName={}", handlerId, handlerName);
                } else {
                    notificationHelper.send(authUserId, "approval_todo", "alert_dispatch",
                            "告警派单通知", "自动派单：" + alertNo + " → " + handlerName,
                            NotificationHelper.mapAlertLevelToRiskLevel(
                                    alert.get("alert_level") != null ? alert.get("alert_level").toString() : "warning"),
                            "库存管理", dispatchId, "alert_dispatch",
                            tenantId, orgId,
                            "[{\"label\":\"去处理\",\"route\":\"/alert?tab=dispatches\"}]");
                    log.info("物料告警派单通知已发送: alertNo={}, handlerId={}, authUserId={}", alertNo, handlerId, authUserId);
                }
            } catch (Exception e) {
                log.warn("物料告警派单通知发送失败: alertNo={}, handlerId={}", alertNo, handlerId, e);
            }

            log.info("物料告警自动派单成功: alertNo={}, handlerId={}, handlerName={}", alertNo, handlerId, handlerName);
        } catch (Exception e) {
            log.warn("物料告警自动派单失败，保持待处理状态: alertNo={}, error={}", alertNo, e.getMessage());
        }
    }

    // ========== 去重 ==========

    /**
     * 物料告警去重：按 materialId + anomalyType + inventoryId（效期）或 materialId + anomalyType（库存）
     * 同一物料/批次同一异常状态，只要有未关闭的告警（pending/assigned/handling/handled/reviewed）
     * 就不再重复告警。与其他告警类型不同（其他告警仅对 pending 去重）。
     * @param inventoryId 非null=效期告警按库存批次行去重，null=库存告警按物料去重
     */
    private boolean hasUnclosedAlert(Long materialId, String anomalyType, Long inventoryId) {
        List<Map<String, Object>> existingAlerts = jdbcTemplate.queryForList(
                "SELECT alert_detail, status FROM device_alert " +
                        "WHERE material_id = ? AND alert_type = 'material' AND deleted = 0 " +
                        "AND status NOT IN ('closed')",
                materialId);

        for (Map<String, Object> alert : existingAlerts) {
            String detailJson = (String) alert.get("alert_detail");
            if (detailJson == null) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> detail = objectMapper.readValue(detailJson, Map.class);
                if (!anomalyType.equals(detail.get("anomalyType"))) continue;

                Long existingInventoryId = null;
                Object invIdObj = detail.get("inventoryId");
                if (invIdObj instanceof Number) {
                    existingInventoryId = ((Number) invIdObj).longValue();
                }

                if (inventoryId != null) {
                    // 效期告警：inventoryId 必须匹配
                    if (inventoryId.equals(existingInventoryId)) return true;
                } else {
                    // 库存告警：inventoryId 必须为 null（库存告警不含 inventoryId）
                    if (existingInventoryId == null) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ========== 升级/降级关闭 ==========

    /**
     * 效期严重度排序：EXPIRED(3) > NEAR_EXPIRY(2) > WARNING(1)
     */
    private int expirySeverity(String anomalyType) {
        return switch (anomalyType) {
            case ANOMALY_EXPIRED -> 3;
            case ANOMALY_NEAR_EXPIRY -> 2;
            case ANOMALY_EXPIRY_WARNING -> 1;
            default -> 0;
        };
    }

    /**
     * 关闭同物料同批次行不同严重度的效期告警（升级时关低级，降级时关高级）
     * @param inventoryId 库存批次行主键（wms_inventory.id），作为批次唯一标识
     */
    private void closeOtherSeverityExpiryAlerts(Long materialId, Long inventoryId, String currentAnomalyType) {
        int currentSeverity = expirySeverity(currentAnomalyType);
        if (currentSeverity == 0) return;

        List<Map<String, Object>> pendingAlerts = jdbcTemplate.queryForList(
                "SELECT id, alert_detail FROM device_alert " +
                        "WHERE material_id = ? AND alert_type = 'material' AND status = 'pending' AND deleted = 0",
                materialId);

        for (Map<String, Object> alert : pendingAlerts) {
            String detailJson = (String) alert.get("alert_detail");
            if (detailJson == null) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> detail = objectMapper.readValue(detailJson, Map.class);
                String existingType = (String) detail.get("anomalyType");

                Long existingInventoryId = null;
                Object invIdObj = detail.get("inventoryId");
                if (invIdObj instanceof Number) {
                    existingInventoryId = ((Number) invIdObj).longValue();
                }

                // 只处理同 inventoryId 的效期告警
                if (existingInventoryId == null || !existingInventoryId.equals(inventoryId)) continue;
                int existingSeverity = expirySeverity(existingType);
                if (existingSeverity == 0) continue;

                // 关闭不同严重度的告警
                if (existingSeverity != currentSeverity) {
                    jdbcTemplate.update(
                            "UPDATE device_alert SET status = 'closed', updated_at = NOW() WHERE id = ?",
                            alert.get("id"));
                    log.info("效期告警升级/降级关闭: alertId={}, {} -> {}", alert.get("id"), existingType, currentAnomalyType);
                }
            } catch (Exception ignored) {}
        }
    }

    // ========== 自动关闭已恢复的告警 ==========

    /**
     * 自动关闭已消除的异常告警（仅关闭 pending 状态）
     */
    private void autoCloseResolvedAlerts(Long materialId, List<BatchRow> batches,
                                          BigDecimal currentStock, BigDecimal minStock, BigDecimal maxStock,
                                          int nearExpiryDays, int warningDays) {
        List<Map<String, Object>> pendingAlerts = jdbcTemplate.queryForList(
                "SELECT id, alert_detail FROM device_alert " +
                        "WHERE material_id = ? AND alert_type = 'material' AND status = 'pending' AND deleted = 0",
                materialId);

        // 按 inventoryId 建索引，快速查找批次行
        Map<Long, BatchRow> batchMap = new HashMap<>();
        for (BatchRow b : batches) {
            if (b.inventoryId != null) {
                batchMap.put(b.inventoryId, b);
            }
        }

        for (Map<String, Object> alert : pendingAlerts) {
            String detailJson = (String) alert.get("alert_detail");
            if (detailJson == null) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> detail = objectMapper.readValue(detailJson, Map.class);
                String anomalyType = (String) detail.get("anomalyType");

                Long existingInventoryId = null;
                Object invIdObj = detail.get("inventoryId");
                if (invIdObj instanceof Number) {
                    existingInventoryId = ((Number) invIdObj).longValue();
                }
                String batchNo = detail.get("batchNo") != null ? detail.get("batchNo").toString() : null;

                boolean resolved = false;

                if (existingInventoryId != null) {
                    // 效期告警：按 inventoryId 查找批次行
                    BatchRow batch = batchMap.get(existingInventoryId);
                    if (batch == null || batch.expiryDate == null) {
                        // 批次行已不存在（售完/删除），关闭告警
                        resolved = true;
                    } else {
                        int remaining = (int) ChronoUnit.DAYS.between(LocalDate.now(), batch.expiryDate);
                        resolved = isExpiryResolved(anomalyType, remaining, nearExpiryDays, warningDays);
                    }
                } else if (isExpiryAnomalyType(anomalyType)) {
                    // 历史遗留效期告警（无 inventoryId，来自旧逻辑），直接关闭
                    resolved = true;
                } else {
                    // 库存告警：按物料总量判断
                    resolved = isStockResolved(anomalyType, currentStock, minStock, maxStock);
                }

                if (resolved) {
                    jdbcTemplate.update(
                            "UPDATE device_alert SET status = 'closed', updated_at = NOW() WHERE id = ?",
                            alert.get("id"));
                    log.info("物料告警已自动关闭: alertId={}, anomalyType={}, inventoryId={}, batchNo={}",
                            alert.get("id"), anomalyType, existingInventoryId, batchNo);
                }
            } catch (Exception ignored) {}
        }
    }

    private boolean isExpiryAnomalyType(String anomalyType) {
        return ANOMALY_EXPIRY_WARNING.equals(anomalyType)
                || ANOMALY_NEAR_EXPIRY.equals(anomalyType)
                || ANOMALY_EXPIRED.equals(anomalyType);
    }

    private boolean isExpiryResolved(String anomalyType, int remainingDays, int nearExpiryDays, int warningDays) {
        return switch (anomalyType) {
            case ANOMALY_EXPIRED -> remainingDays > 0;
            case ANOMALY_NEAR_EXPIRY -> remainingDays > nearExpiryDays;
            case ANOMALY_EXPIRY_WARNING -> remainingDays > warningDays;
            default -> false;
        };
    }

    private boolean isStockResolved(String anomalyType, BigDecimal currentStock,
                                     BigDecimal minStock, BigDecimal maxStock) {
        return switch (anomalyType) {
            case ANOMALY_STOCK_HIGH -> maxStock == null || currentStock.compareTo(maxStock) < 0;
            case ANOMALY_STOCK_LOW -> minStock == null || currentStock.compareTo(minStock) > 0;
            default -> false;
        };
    }

    // ========== 批次状态更新 ==========

    /**
     * 按批次自身的到期日更新 wms_inventory.status
     */
    private void updateBatchStatus(BatchRow batch, int nearExpiryDays) {
        if (batch.expiryDate == null) return;

        String status;
        if (batch.remainingDays <= 0) {
            status = "expired";
        } else if (batch.remainingDays <= nearExpiryDays) {
            status = "warning";
        } else {
            status = "normal";
        }

        jdbcTemplate.update(
                "UPDATE wms_inventory SET status = ? WHERE id = ?",
                status, batch.inventoryId);
    }

    // ========== 辅助方法 ==========

    private String generateAlertNo(Long materialId, Long ruleId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        // 使用完整 materialId + 随机后缀，避免同毫秒多物料 alert_no 碰撞
        // （原 materialId % 10000 会在 ID≥10000 时与 ID<10000 物料碰撞，导致 UNIQUE 约束冲突）
        int random = ThreadLocalRandom.current().nextInt(10000);
        return "ALT-" + timestamp + "-" + materialId + "-" + ruleId + "-" + random;
    }

    /**
     * 发送告警通知给规则配置的通知用户（需包含站内信渠道）
     */
    private void sendAlertNotification(Map<String, Object> rule, Material material,
                                        String alertNo, String content, String alertLevel,
                                        Long orgId, Long tenantId) {
        try {
            String notifyUsers = rule.get("notify_users") != null ? String.valueOf(rule.get("notify_users")) : null;
            String notifyChannels = rule.get("notify_channels") != null ? String.valueOf(rule.get("notify_channels")) : null;
            if (notifyUsers == null || notifyUsers.isBlank()) return;
            if (notifyChannels == null || !notifyChannels.contains("system")) return;

            // 通过 alertNo 查询告警 ID
            Long alertId = null;
            try {
                alertId = jdbcTemplate.queryForObject(
                        "SELECT id FROM device_alert WHERE alert_no = ?", Long.class, alertNo);
            } catch (Exception ignored) {}

            List<Long> empIds = NotificationHelper.parseCsvIds(notifyUsers);
            List<Long> authUserIds = notificationHelper.employeeIdsToAuthUserIds(empIds);
            if (!authUserIds.isEmpty()) {
                String route = alertId != null
                        ? "[{\"label\":\"查看告警\",\"route\":\"/alert?id=" + alertId + "\"}]"
                        : "[{\"label\":\"查看告警\",\"route\":\"/alert\"}]";
                notificationHelper.sendBatch(authUserIds, "food_safety_alert", "material_alert",
                        "物料告警通知", content,
                        NotificationHelper.mapAlertLevelToRiskLevel(alertLevel),
                        "库存管理", alertId, "alert",
                        tenantId, orgId, route);
            }
        } catch (Exception e) {
            log.warn("物料告警通知发送失败: materialId={}", material.getId(), e);
        }
    }

    private String safeStr(String value) {
        return value != null ? value : "-";
    }

    // ========== 内部数据模型 ==========

    private static class BatchRow {
        Long inventoryId;
        Long warehouseId;
        String warehouseName;
        Long locationId;
        String locationName;
        String batchNo;
        BigDecimal quantity;
        LocalDate productionDate;
        LocalDate expiryDate;
        Integer remainingDays;
    }
}
