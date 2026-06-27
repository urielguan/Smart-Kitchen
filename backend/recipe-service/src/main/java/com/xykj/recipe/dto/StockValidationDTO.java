package com.xykj.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 库存校验结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockValidationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否通过校验
     */
    private Boolean passed;

    /**
     * 校验失败消息
     */
    private String message;

    /**
     * 风险状态: normal=正常, warning=临期预警, expired=已过期, shortage=库存不足, unknown=待确认
     */
    private String riskStatus;

    /**
     * 风险状态名称
     */
    private String riskStatusName;

    /**
     * 缺货物料列表
     */
    private java.util.List<ShortageItem> shortageItems;

    /**
     * 所有物料库存状态列表（包含充足、临期、缺货）
     */
    private java.util.List<MaterialStockStatus> materialStockStatuses;

    /**
     * 物料库存状态
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialStockStatus implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 物料ID
         */
        private Long materialId;

        /**
         * 物料名称
         */
        private String materialName;

        /**
         * 需求数量
         */
        private Integer requiredQuantity;

        /**
         * 可用库存
         */
        private Integer availableStock;

        /**
         * 缺口数量（库存不足时有值）
         */
        private Integer shortageQuantity;

        /**
         * 单位
         */
        private String unit;

        /**
         * 库存状态：sufficient=充足，shortage=缺货
         */
        private String stockStatus;

        /**
         * 临期状态：normal=正常，warning=临期预警，expired=已过期
         */
        private String expiryStatus;

        /**
         * 最近到期日期
         */
        private java.time.LocalDate nearestExpiryDate;

        /**
         * 临期天数（负数表示已过期，正数表示距到期天数）
         */
        private Integer daysToExpiry;

        /**
         * 补货建议（如 "1000g（约7个土豆）"），仅库存不足时有值
         */
        private String restockSuggestion;
    }

    /**
     * 缺货项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShortageItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 物料ID
         */
        private Long materialId;

        /**
         * 物料名称
         */
        private String materialName;

        /**
         * 需求数量
         */
        private Integer requiredQuantity;

        /**
         * 可用库存
         */
        private Integer availableStock;

        /**
         * 缺口数量
         */
        private Integer shortageQuantity;

        /**
         * 单位
         */
        private String unit;

        /**
         * 补货建议（如 "25kg（约1袋）"）
         */
        private String restockSuggestion;
    }
}
