package com.xykj.recipe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xykj.recipe.dto.StockValidationDTO;
import com.xykj.recipe.entity.RecipeIngredient;
import com.xykj.recipe.entity.RecipePlan;
import com.xykj.recipe.entity.RecipePlanItem;
import com.xykj.recipe.mapper.RecipeIngredientMapper;
import com.xykj.recipe.mapper.RecipePlanItemMapper;
import com.xykj.recipe.mapper.RecipePlanMapper;
import com.xykj.recipe.mapper.WmsStockMapper;
import com.xykj.recipe.service.InventoryValidationService;
import com.xykj.recipe.service.RestockSuggestionService;
import com.xykj.recipe.vo.RecipePlanMaterialSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存校验服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryValidationServiceImpl implements InventoryValidationService {

    private final WmsStockMapper stockMapper;
    private final RecipePlanMapper planMapper;
    private final RecipePlanItemMapper planItemMapper;
    private final RecipeIngredientMapper ingredientMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RestockSuggestionService restockSuggestionService;

    @Override
    public StockValidationDTO validateRecipePlanStock(Long planId) {
        // 获取菜谱计划
        RecipePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            return StockValidationDTO.builder()
                    .passed(false)
                    .message("菜谱计划不存在")
                    .riskStatus("unknown")
                    .riskStatusName("待人工确认")
                    .build();
        }

        // 获取计划明细
        List<RecipePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
        );

        if (items.isEmpty()) {
            return StockValidationDTO.builder()
                    .passed(true)
                    .message("无食材需要校验")
                    .riskStatus("normal")
                    .riskStatusName("正常")
                    .build();
        }

        // 计算每个菜谱的份数
        Map<Long, Integer> recipeServingsMap = items.stream()
                .collect(Collectors.toMap(
                        RecipePlanItem::getRecipeId,
                        item -> item.getPlannedServings() != null ? item.getPlannedServings() : 1,
                        Integer::sum
                ));

        // 获取所有菜谱的食材
        List<Long> recipeIds = new ArrayList<>(recipeServingsMap.keySet());
        List<RecipeIngredient> allIngredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<RecipeIngredient>()
                        .in(RecipeIngredient::getRecipeId, recipeIds)
        );

        // 计算总需求（按物料ID汇总）
        Map<Long, BigDecimal> materialRequirements = new HashMap<>();
        Map<Long, String> materialNames = new HashMap<>();
        Map<Long, String> materialUnits = new HashMap<>();

        for (RecipeIngredient ingredient : allIngredients) {
            Long materialId = ingredient.getMaterialId();
            Integer servings = recipeServingsMap.getOrDefault(ingredient.getRecipeId(), 1);

            // 单份用量 × 份数
            BigDecimal singleQuantity = ingredient.getQuantity() != null ? ingredient.getQuantity() : BigDecimal.ZERO;
            BigDecimal totalQuantity = singleQuantity.multiply(BigDecimal.valueOf(servings));

            // 累加到总需求
            materialRequirements.merge(materialId, totalQuantity, BigDecimal::add);

            // 记录物料名称和单位
            materialNames.putIfAbsent(materialId, ingredient.getMaterialName());
            materialUnits.putIfAbsent(materialId, ingredient.getUnit());
        }

        // 批量查询库存（包含临期信息）
        List<Long> materialIds = new ArrayList<>(materialRequirements.keySet());
        Map<Long, BigDecimal> availableStockMap = new HashMap<>();
        Map<Long, LocalDate> nearestExpiryMap = new HashMap<>();

        if (!materialIds.isEmpty()) {
            List<WmsStockMapper.StockDetail> stockDetails = stockMapper.getStockDetailsByMaterialIds(materialIds);
            for (WmsStockMapper.StockDetail detail : stockDetails) {
                availableStockMap.put(detail.getMaterialId(), detail.getAvailableStock());
                if (detail.getNearestExpiryDate() != null) {
                    nearestExpiryMap.put(detail.getMaterialId(), detail.getNearestExpiryDate());
                }
            }
        }

        log.info("库存校验 - 计划ID: {}, 物料需求: {}, 库存查询结果: {}", planId, materialRequirements, availableStockMap);

        // 校验库存并计算状态
        List<StockValidationDTO.ShortageItem> shortageItems = new ArrayList<>();
        List<StockValidationDTO.MaterialStockStatus> stockStatuses = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Map.Entry<Long, BigDecimal> entry : materialRequirements.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal required = entry.getValue();
            BigDecimal available = availableStockMap.getOrDefault(materialId, BigDecimal.ZERO);
            String materialName = materialNames.get(materialId);
            String unit = materialUnits.get(materialId);

            // 计算库存状态
            String stockStatus;
            BigDecimal shortageQuantity = null;
            if (available.compareTo(required) >= 0) {
                stockStatus = "sufficient";
            } else {
                stockStatus = "shortage";
                shortageQuantity = required.subtract(available).setScale(0, RoundingMode.HALF_UP);
                shortageItems.add(StockValidationDTO.ShortageItem.builder()
                        .materialId(materialId)
                        .materialName(materialName)
                        .requiredQuantity(required.setScale(0, RoundingMode.HALF_UP).intValue())
                        .availableStock(available.setScale(0, RoundingMode.HALF_UP).intValue())
                        .shortageQuantity(shortageQuantity.intValue())
                        .unit(unit)
                        .restockSuggestion(restockSuggestionService.generateSuggestion(
                                materialId, materialName, shortageQuantity, unit))
                        .build());
            }

            // 计算临期状态
            String expiryStatus = "normal";
            LocalDate nearestExpiry = nearestExpiryMap.get(materialId);
            Integer daysToExpiry = null;
            if (nearestExpiry != null) {
                daysToExpiry = (int) ChronoUnit.DAYS.between(today, nearestExpiry);
                if (daysToExpiry < 0) {
                    expiryStatus = "expired";
                } else if (daysToExpiry <= 7) {
                    expiryStatus = "warning";
                }
            }

            stockStatuses.add(StockValidationDTO.MaterialStockStatus.builder()
                    .materialId(materialId)
                    .materialName(materialName)
                    .requiredQuantity(required.setScale(0, RoundingMode.HALF_UP).intValue())
                    .availableStock(available.setScale(0, RoundingMode.HALF_UP).intValue())
                    .shortageQuantity(shortageQuantity != null ? shortageQuantity.intValue() : null)
                    .unit(unit)
                    .stockStatus(stockStatus)
                    .expiryStatus(expiryStatus)
                    .nearestExpiryDate(nearestExpiry)
                    .daysToExpiry(daysToExpiry)
                    .restockSuggestion(shortageQuantity != null ?
                            restockSuggestionService.generateSuggestion(
                                    materialId, materialName, shortageQuantity, unit) : null)
                    .build());
        }

        long warningCount = stockStatuses.stream()
                .filter(s -> "warning".equals(s.getExpiryStatus()))
                .count();
        long expiredCount = stockStatuses.stream()
                .filter(s -> "expired".equals(s.getExpiryStatus()))
                .count();
        String riskStatus = resolveRiskStatus(shortageItems, warningCount, expiredCount);
        String riskStatusName = getRiskStatusName(riskStatus);

        // 构建结果
        if (shortageItems.isEmpty()) {
            String message = buildValidationMessage(shortageItems, warningCount, expiredCount);

            return StockValidationDTO.builder()
                    .passed(true)
                    .message(message)
                    .riskStatus(riskStatus)
                    .riskStatusName(riskStatusName)
                    .shortageItems(shortageItems)
                    .materialStockStatuses(stockStatuses)
                    .build();
        } else {
            String message = buildValidationMessage(shortageItems, warningCount, expiredCount);

            return StockValidationDTO.builder()
                    .passed(false)
                    .message(message)
                    .riskStatus(riskStatus)
                    .riskStatusName(riskStatusName)
                    .shortageItems(shortageItems)
                    .materialStockStatuses(stockStatuses)
                    .build();
        }
    }

    @Override
    public StockValidationDTO validateMaterialStock(List<Long> materialIds, List<BigDecimal> quantities) {
        if (materialIds == null || quantities == null || materialIds.size() != quantities.size()) {
            return StockValidationDTO.builder()
                    .passed(false)
                    .message("参数错误")
                    .riskStatus("unknown")
                    .riskStatusName("待人工确认")
                    .build();
        }

        // 批量查询库存
        Map<Long, BigDecimal> availableStockMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<WmsStockMapper.StockSummary> stockSummaries = stockMapper.getAvailableStockByMaterialIds(materialIds);
            for (WmsStockMapper.StockSummary summary : stockSummaries) {
                availableStockMap.put(summary.getMaterialId(), summary.getAvailableStock());
            }
        }

        // 校验库存
        List<StockValidationDTO.ShortageItem> shortageItems = new ArrayList<>();

        for (int i = 0; i < materialIds.size(); i++) {
            Long materialId = materialIds.get(i);
            BigDecimal required = quantities.get(i);
            BigDecimal available = availableStockMap.getOrDefault(materialId, BigDecimal.ZERO);

            if (available.compareTo(required) < 0) {
                BigDecimal shortage = required.subtract(available).setScale(0, RoundingMode.HALF_UP);
                shortageItems.add(StockValidationDTO.ShortageItem.builder()
                        .materialId(materialId)
                        .requiredQuantity(required.setScale(0, RoundingMode.HALF_UP).intValue())
                        .availableStock(available.setScale(0, RoundingMode.HALF_UP).intValue())
                        .shortageQuantity(shortage.intValue())
                        .build());
            }
        }

        if (shortageItems.isEmpty()) {
            return StockValidationDTO.builder()
                    .passed(true)
                    .message("库存校验通过")
                    .riskStatus("normal")
                    .riskStatusName("正常")
                    .build();
        } else {
            return StockValidationDTO.builder()
                    .passed(false)
                    .message("部分物料库存不足")
                    .riskStatus("shortage")
                    .riskStatusName("库存不足")
                    .shortageItems(shortageItems)
                    .build();
        }
    }

    private String resolveRiskStatus(List<StockValidationDTO.ShortageItem> shortageItems, long warningCount, long expiredCount) {
        if (shortageItems != null && !shortageItems.isEmpty()) {
            return "shortage";
        }
        if (expiredCount > 0) {
            return "expired";
        }
        if (warningCount > 0) {
            return "warning";
        }
        return "normal";
    }

    private String getRiskStatusName(String riskStatus) {
        if (riskStatus == null) {
            return "待人工确认";
        }
        return switch (riskStatus) {
            case "normal" -> "正常";
            case "warning" -> "临期预警";
            case "expired" -> "已过期";
            case "shortage" -> "库存不足";
            default -> "待人工确认";
        };
    }

    private String buildValidationMessage(List<StockValidationDTO.ShortageItem> shortageItems, long warningCount, long expiredCount) {
        if (shortageItems == null || shortageItems.isEmpty()) {
            if (expiredCount > 0) {
                return String.format("库存校验完成，发现%d种物料已过期，请及时处理。", expiredCount);
            }
            if (warningCount > 0) {
                return String.format("库存校验完成，发现%d种物料临期，请提前安排补货或优先消耗。", warningCount);
            }
            return "库存校验通过，当前暂无库存风险。";
        }

        StringBuilder message = new StringBuilder()
                .append("库存校验完成，发现")
                .append(shortageItems.size())
                .append("种物料库存不足。");
        if (expiredCount > 0) {
            message.append(" 同时存在").append(expiredCount).append("种已过期物料。");
        } else if (warningCount > 0) {
            message.append(" 同时存在").append(warningCount).append("种临期物料。");
        }
        message.append("\n请关注以下风险明细：");
        for (StockValidationDTO.ShortageItem item : shortageItems) {
            message.append("\n- ").append(item.getMaterialName())
                    .append("：需").append(item.getRequiredQuantity())
                    .append(item.getUnit())
                    .append("，库存").append(item.getAvailableStock())
                    .append(item.getUnit())
                    .append("，缺口").append(item.getShortageQuantity())
                    .append(item.getUnit());
        }
        return message.toString();
    }

    @Override
    public void reserveStock(Long planId) {
        // TODO: 实现库存预留逻辑
        // 1. 计算总需求
        // 2. 更新wms_stock表，将可用库存转为预留库存
        // 3. 记录库存流水
        log.info("预留库存 - 计划ID: {}", planId);
    }

    @Override
    public void releaseReservedStock(Long planId) {
        // TODO: 实现释放预留库存逻辑
        // 1. 查询预留记录
        // 2. 将预留库存转回可用库存
        // 3. 记录库存流水
        log.info("释放预留库存 - 计划ID: {}", planId);
    }

    @Override
    public void generateMaterialRequisition(Long planId) {
        log.info("为菜谱计划[{}]生成领用出库单", planId);

        // 获取菜谱计划
        RecipePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            log.warn("菜谱计划[{}]不存在，跳过生成出库单", planId);
            return;
        }

        // 获取计划明细
        List<RecipePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
        );
        if (items.isEmpty()) {
            log.info("菜谱计划[{}]无食材明细，跳过生成出库单", planId);
            return;
        }

        // 计算每个菜谱的份数
        Map<Long, Integer> recipeServingsMap = items.stream()
                .collect(Collectors.toMap(
                        RecipePlanItem::getRecipeId,
                        item -> item.getPlannedServings() != null ? item.getPlannedServings() : 1,
                        Integer::sum
                ));

        // 获取所有菜谱的食材
        List<Long> recipeIds = new ArrayList<>(recipeServingsMap.keySet());
        List<RecipeIngredient> allIngredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<RecipeIngredient>()
                        .in(RecipeIngredient::getRecipeId, recipeIds)
        );

        // 汇总物料需求
        Map<Long, BigDecimal> materialRequirements = new LinkedHashMap<>();
        Map<Long, String> materialNames = new HashMap<>();
        Map<Long, String> materialUnits = new HashMap<>();

        for (RecipeIngredient ingredient : allIngredients) {
            Long materialId = ingredient.getMaterialId();
            Integer servings = recipeServingsMap.getOrDefault(ingredient.getRecipeId(), 1);
            BigDecimal singleQuantity = ingredient.getQuantity() != null ? ingredient.getQuantity() : BigDecimal.ZERO;
            BigDecimal totalQuantity = singleQuantity.multiply(BigDecimal.valueOf(servings));
            materialRequirements.merge(materialId, totalQuantity, BigDecimal::add);
            materialNames.putIfAbsent(materialId, ingredient.getMaterialName());
            materialUnits.putIfAbsent(materialId, ingredient.getUnit());
        }

        if (materialRequirements.isEmpty()) {
            log.info("菜谱计划[{}]无物料需求，跳过生成出库单", planId);
            return;
        }

        // 生成出库单号
        String outboundNo = "OUT-REQ-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%04d", new Random().nextInt(10000));

        // 查询仓库ID（取第一个可用仓库）
        Long warehouseId = null;
        try {
            List<Map<String, Object>> warehouses = jdbcTemplate.queryForList(
                    "SELECT id FROM wms_warehouse WHERE deleted = 0 LIMIT 1");
            if (!warehouses.isEmpty()) {
                warehouseId = ((Number) warehouses.get(0).get("id")).longValue();
            }
        } catch (Exception e) {
            log.warn("查询仓库信息失败: {}", e.getMessage());
        }

        // 计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 插入出库单主表
        Long orgId = plan.getOrgId() != null ? plan.getOrgId() : 1L;
        Long tenantId = plan.getTenantId() != null ? plan.getTenantId() : 1L;

        jdbcTemplate.update(
                "INSERT INTO wms_outbound_order (outbound_no, outbound_type, warehouse_id, purpose, remark, status, " +
                        "source_order_id, source_order_no, org_id, tenant_id, created_at, updated_at, deleted) " +
                        "VALUES (?, 'requisition', ?, ?, ?, 'draft', ?, ?, ?, ?, NOW(), NOW(), 0)",
                outboundNo, warehouseId,
                "菜谱计划[" + plan.getPlanCode() + "]自动生成备料需求",
                "系统根据菜谱计划自动生成，请仓库管理员审核执行",
                planId, plan.getPlanCode(),
                orgId, tenantId
        );

        // 获取出库单ID
        Long outboundOrderId = jdbcTemplate.queryForObject(
                "SELECT id FROM wms_outbound_order WHERE outbound_no = ?", Long.class, outboundNo);

        // 插入出库单明细
        for (Map.Entry<Long, BigDecimal> entry : materialRequirements.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal quantity = entry.getValue().setScale(3, RoundingMode.HALF_UP);
            String materialName = materialNames.get(materialId);
            String unit = materialUnits.get(materialId);

            // 查询物料单价
            BigDecimal unitCost = BigDecimal.ZERO;
            try {
                Map<String, Object> materialInfo = jdbcTemplate.queryForMap(
                        "SELECT unit_price FROM wms_material WHERE id = ? AND deleted = 0", materialId);
                if (materialInfo.get("unit_price") != null) {
                    unitCost = (BigDecimal) materialInfo.get("unit_price");
                }
            } catch (Exception e) {
                log.warn("查询物料[{}]单价失败: {}", materialId, e.getMessage());
            }

            BigDecimal lineCost = unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineCost);

            jdbcTemplate.update(
                    "INSERT INTO wms_outbound_order_item (outbound_id, material_id, material_name, " +
                            "quantity, unit, unit_cost, total_cost, remark, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NOW(), NOW())",
                    outboundOrderId, materialId, materialName,
                    quantity, unit, unitCost, lineCost
            );
        }

        // 更新出库单总金额
        jdbcTemplate.update(
                "UPDATE wms_outbound_order SET total_amount = ? WHERE id = ?",
                totalAmount, outboundOrderId);

        // 更新关联烹饪任务的备料状态为 pending_prep
        jdbcTemplate.update(
                "UPDATE cook_task SET material_prep_status = 'pending_prep', updated_at = NOW() " +
                        "WHERE plan_id = ? AND deleted = 0",
                planId);

        log.info("菜谱计划[{}]领用出库单生成完成: outboundNo={}, 物料{}种, 总金额={}",
                planId, outboundNo, materialRequirements.size(), totalAmount);
    }

    @Override
    public List<RecipePlanMaterialSummaryVO> getMaterialSummary(Long planId) {
        RecipePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("菜谱计划不存在");
        }

        List<RecipePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
        );
        if (items.isEmpty()) {
            return List.of();
        }

        // 每个菜谱的计划份数
        Map<Long, Integer> recipeServingsMap = items.stream()
                .collect(Collectors.toMap(
                        RecipePlanItem::getRecipeId,
                        item -> item.getPlannedServings() != null ? item.getPlannedServings() : 1,
                        Integer::sum
                ));

        // 所有菜谱的食材
        List<Long> recipeIds = new ArrayList<>(recipeServingsMap.keySet());
        List<RecipeIngredient> allIngredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<RecipeIngredient>()
                        .in(RecipeIngredient::getRecipeId, recipeIds)
        );

        // 按物料聚合
        Map<Long, BigDecimal> materialQuantities = new LinkedHashMap<>();
        Map<Long, String> materialNames = new HashMap<>();
        Map<Long, String> materialUnits = new HashMap<>();

        for (RecipeIngredient ingredient : allIngredients) {
            Long materialId = ingredient.getMaterialId();
            Integer servings = recipeServingsMap.getOrDefault(ingredient.getRecipeId(), 1);
            BigDecimal singleQty = ingredient.getQuantity() != null ? ingredient.getQuantity() : BigDecimal.ZERO;
            BigDecimal totalQty = singleQty.multiply(BigDecimal.valueOf(servings));
            materialQuantities.merge(materialId, totalQty, BigDecimal::add);
            materialNames.putIfAbsent(materialId, ingredient.getMaterialName());
            materialUnits.putIfAbsent(materialId, ingredient.getUnit());
        }

        // 查询物料规格、单位、单价
        List<RecipePlanMaterialSummaryVO> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : materialQuantities.entrySet()) {
            Long materialId = entry.getKey();
            RecipePlanMaterialSummaryVO vo = new RecipePlanMaterialSummaryVO();
            vo.setMaterialId(materialId);
            vo.setMaterialName(materialNames.get(materialId));
            vo.setTotalQuantity(entry.getValue());

            // 从 wms_material 补充 spec、unit
            try {
                List<Map<String, Object>> matRows = jdbcTemplate.queryForList(
                        "SELECT spec, unit FROM wms_material WHERE id = ? AND deleted = 0", materialId);
                if (!matRows.isEmpty()) {
                    Map<String, Object> mat = matRows.get(0);
                    if (mat.get("spec") != null && !mat.get("spec").toString().isEmpty()) {
                        vo.setSpec(mat.get("spec").toString());
                    }
                    // unit 优先用 wms_material 的，更准确
                    if (mat.get("unit") != null && !mat.get("unit").toString().isEmpty()) {
                        vo.setUnit(mat.get("unit").toString());
                    } else {
                        vo.setUnit(materialUnits.get(materialId));
                    }
                } else {
                    vo.setUnit(materialUnits.get(materialId));
                }
            } catch (Exception e) {
                vo.setUnit(materialUnits.get(materialId));
            }

            // 从 wms_inventory 取最近一次入库单价
            try {
                List<Map<String, Object>> invRows = jdbcTemplate.queryForList(
                        "SELECT unit_cost FROM wms_inventory WHERE material_id = ? ORDER BY created_at DESC LIMIT 1",
                        materialId);
                if (!invRows.isEmpty() && invRows.get(0).get("unit_cost") != null) {
                    vo.setUnitCost(new BigDecimal(invRows.get(0).get("unit_cost").toString()));
                }
            } catch (Exception ignored) {}

            result.add(vo);
        }

        return result;
    }
}
