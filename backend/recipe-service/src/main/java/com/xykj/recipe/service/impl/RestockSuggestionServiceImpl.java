package com.xykj.recipe.service.impl;

import com.xykj.recipe.service.RestockSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 补货建议服务实现 - 三层瀑布策略：
 * 1. 规格解析（从 wms_material.spec 解析包装信息）
 * 2. 重量参考表（常见食材平均重量静态 Map）
 * 3. AI 兜底（Phase 2 实现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestockSuggestionServiceImpl implements RestockSuggestionService {

    private final JdbcTemplate jdbcTemplate;

    // ==================== Layer 2: 重量参考表 ====================

    /** key: 食材名称, value: [平均重量(g), 计数单位] */
    private static final Map<String, Object[]> INGREDIENT_WEIGHT_REFERENCE = new HashMap<>();

    static {
        // 蔬菜类
        INGREDIENT_WEIGHT_REFERENCE.put("土豆", new Object[]{150.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("马铃薯", new Object[]{150.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("番茄", new Object[]{200.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("西红柿", new Object[]{200.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("鸡蛋", new Object[]{50.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("洋葱", new Object[]{250.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("胡萝卜", new Object[]{150.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("青椒", new Object[]{100.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("黄瓜", new Object[]{200.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("茄子", new Object[]{300.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("白菜", new Object[]{1500.0, "棵"});
        INGREDIENT_WEIGHT_REFERENCE.put("大白菜", new Object[]{2000.0, "棵"});
        INGREDIENT_WEIGHT_REFERENCE.put("小白菜", new Object[]{200.0, "棵"});
        INGREDIENT_WEIGHT_REFERENCE.put("大蒜", new Object[]{6.0, "瓣"});
        INGREDIENT_WEIGHT_REFERENCE.put("蒜头", new Object[]{50.0, "头"});
        INGREDIENT_WEIGHT_REFERENCE.put("生姜", new Object[]{30.0, "块"});
        INGREDIENT_WEIGHT_REFERENCE.put("大葱", new Object[]{100.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("小葱", new Object[]{15.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("芹菜", new Object[]{100.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("菠菜", new Object[]{200.0, "把"});
        INGREDIENT_WEIGHT_REFERENCE.put("生菜", new Object[]{200.0, "棵"});
        INGREDIENT_WEIGHT_REFERENCE.put("豆芽", new Object[]{250.0, "袋"});
        INGREDIENT_WEIGHT_REFERENCE.put("莲藕", new Object[]{400.0, "节"});
        INGREDIENT_WEIGHT_REFERENCE.put("冬瓜", new Object[]{5000.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("南瓜", new Object[]{2000.0, "块"});
        INGREDIENT_WEIGHT_REFERENCE.put("苦瓜", new Object[]{300.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("丝瓜", new Object[]{300.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("豆角", new Object[]{150.0, "把"});
        INGREDIENT_WEIGHT_REFERENCE.put("西蓝花", new Object[]{350.0, "棵"});
        INGREDIENT_WEIGHT_REFERENCE.put("花菜", new Object[]{500.0, "棵"});

        // 肉禽蛋类
        INGREDIENT_WEIGHT_REFERENCE.put("鸡腿", new Object[]{200.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("鸡翅", new Object[]{80.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("鸡胸肉", new Object[]{300.0, "块"});
        INGREDIENT_WEIGHT_REFERENCE.put("排骨", new Object[]{500.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("猪蹄", new Object[]{500.0, "只"});
        INGREDIENT_WEIGHT_REFERENCE.put("鸭腿", new Object[]{250.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("鸭蛋", new Object[]{70.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("鹌鹑蛋", new Object[]{10.0, "个"});

        // 水产类
        INGREDIENT_WEIGHT_REFERENCE.put("鲫鱼", new Object[]{400.0, "条"});
        INGREDIENT_WEIGHT_REFERENCE.put("鲤鱼", new Object[]{1000.0, "条"});
        INGREDIENT_WEIGHT_REFERENCE.put("虾", new Object[]{20.0, "只"});
        INGREDIENT_WEIGHT_REFERENCE.put("大虾", new Object[]{40.0, "只"});
        INGREDIENT_WEIGHT_REFERENCE.put("带鱼", new Object[]{300.0, "条"});

        // 水果类
        INGREDIENT_WEIGHT_REFERENCE.put("苹果", new Object[]{250.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("橙子", new Object[]{300.0, "个"});
        INGREDIENT_WEIGHT_REFERENCE.put("香蕉", new Object[]{120.0, "根"});
        INGREDIENT_WEIGHT_REFERENCE.put("柠檬", new Object[]{80.0, "个"});

        // 豆制品
        INGREDIENT_WEIGHT_REFERENCE.put("豆腐", new Object[]{500.0, "块"});
        INGREDIENT_WEIGHT_REFERENCE.put("豆腐干", new Object[]{100.0, "块"});
        INGREDIENT_WEIGHT_REFERENCE.put("腐竹", new Object[]{30.0, "根"});
    }

    // ==================== Layer 1: 规格解析正则 ====================

    /** 匹配 "25kg/袋"、"500g/盒"、"10斤/箱" 等格式 */
    private static final Pattern SPEC_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(kg|公斤|g|克|L|升|斤|ml|毫升|个)\\s*/\\s*(\\S+)"
    );

    /** 宽松匹配：无分隔符 "25kg袋"、"500克盒" */
    private static final Pattern SPEC_PATTERN_LOOSE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(kg|公斤|g|克|L|升|斤|ml|毫升|个)\\s*(袋|盒|桶|箱|包|罐|瓶|件|提|卷|支)"
    );

    // ==================== 主方法 ====================

    @Override
    public String generateSuggestion(Long materialId, String materialName,
                                     BigDecimal shortageQty, String unit) {
        if (shortageQty == null || shortageQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // Layer 1: 规格解析
        String suggestion = trySpecBasedSuggestion(materialId, shortageQty, unit);
        if (suggestion != null) {
            return suggestion;
        }

        // Layer 2: 重量参考表
        suggestion = tryWeightReferenceSuggestion(materialName, shortageQty, unit);
        if (suggestion != null) {
            return suggestion;
        }

        // Layer 3: AI 兜底（Phase 2）
        return null;
    }

    // ==================== Layer 1: 规格解析 ====================

    private String trySpecBasedSuggestion(Long materialId, BigDecimal shortageQty, String unit) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT spec, unit FROM wms_material WHERE id = ? AND deleted = 0", materialId);
            String spec = row.get("spec") != null ? row.get("spec").toString().trim() : "";
            String materialUnit = row.get("unit") != null ? row.get("unit").toString().trim() : "";

            if (spec.isEmpty()) {
                return null;
            }

            // 尝试标准格式匹配
            ParsedSpec parsed = parseSpec(spec);
            if (parsed == null) {
                return null;
            }

            // 单位统一转换
            BigDecimal shortageGrams = convertToGrams(shortageQty, unit);
            BigDecimal specGrams = convertToGrams(BigDecimal.valueOf(parsed.amount), parsed.unit);

            if (specGrams.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            // 向上取整计算包装数
            int count = shortageGrams.divide(specGrams, 0, RoundingMode.UP).intValue();

            // 格式化输出
            String shortageDisplay = formatQuantity(shortageQty, unit);
            return String.format("%s（约%d%s）", shortageDisplay, count, parsed.containerName);

        } catch (Exception e) {
            log.debug("规格解析失败 materialId={}: {}", materialId, e.getMessage());
            return null;
        }
    }

    private ParsedSpec parseSpec(String spec) {
        // 标准格式
        Matcher m = SPEC_PATTERN.matcher(spec);
        if (m.find()) {
            return new ParsedSpec(Double.parseDouble(m.group(1)), m.group(2), m.group(3));
        }
        // 宽松格式
        m = SPEC_PATTERN_LOOSE.matcher(spec);
        if (m.find()) {
            return new ParsedSpec(Double.parseDouble(m.group(1)), m.group(2), m.group(3));
        }
        return null;
    }

    // ==================== Layer 2: 重量参考表 ====================

    private String tryWeightReferenceSuggestion(String materialName, BigDecimal shortageQty, String unit) {
        Object[] ref = findWeightRef(materialName);
        if (ref == null) {
            return null;
        }

        double avgWeight = (double) ref[0];
        String countUnit = (String) ref[1];

        BigDecimal shortageGrams = convertToGrams(shortageQty, unit);
        int count = new BigDecimal(shortageGrams.doubleValue() / avgWeight)
                .setScale(0, RoundingMode.UP).intValue();

        String shortageDisplay = formatQuantity(shortageQty, unit);
        return String.format("%s（约%d%s%s）", shortageDisplay, count, countUnit, materialName);
    }

    /**
     * 模糊匹配重量参考表（与 NUTRITION_REFERENCE 的 findNutritionRef 同模式）
     */
    private Object[] findWeightRef(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }
        // 精确匹配
        Object[] exact = INGREDIENT_WEIGHT_REFERENCE.get(materialName);
        if (exact != null) {
            return exact;
        }
        // 模糊匹配：双向 substring contains
        for (Map.Entry<String, Object[]> entry : INGREDIENT_WEIGHT_REFERENCE.entrySet()) {
            if (materialName.contains(entry.getKey()) || entry.getKey().contains(materialName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ==================== 工具方法 ====================

    /** 将数量统一转换为克（重量类）或毫升（体积类） */
    private BigDecimal convertToGrams(BigDecimal qty, String unit) {
        if (unit == null) return qty;
        return switch (unit.toLowerCase()) {
            case "kg", "公斤" -> qty.multiply(BigDecimal.valueOf(1000));
            case "斤" -> qty.multiply(BigDecimal.valueOf(500));
            case "l", "升" -> qty.multiply(BigDecimal.valueOf(1000)); // 液体 1L ≈ 1000g（近似）
            case "g", "克", "ml", "毫升" -> qty;
            default -> qty; // 未知单位按原值处理
        };
    }

    /** 格式化数量显示 */
    private String formatQuantity(BigDecimal qty, String unit) {
        BigDecimal rounded = qty.setScale(0, RoundingMode.HALF_UP);
        return rounded.intValue() + (unit != null ? unit : "");
    }

    // ==================== 内部类 ====================

    private static class ParsedSpec {
        final double amount;
        final String unit;
        final String containerName;

        ParsedSpec(double amount, String unit, String containerName) {
            this.amount = amount;
            this.unit = unit;
            this.containerName = containerName;
        }
    }
}
