package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料预警通知VO
 */
@Data
public class MaterialNotificationVO {

    private Long id;

    /**
     * 通知类型
     */
    private String notificationType;

    /**
     * 通知类型名称
     */
    private String notificationTypeName;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 库存批次ID
     */
    private Long inventoryId;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 当前库存数量
     */
    private BigDecimal quantity;

    /**
     * 单位
     */
    private String unit;

    /**
     * 到期日期
     */
    private LocalDate expiryDate;

    /**
     * 剩余天数
     */
    private Integer daysRemaining;

    /**
     * 推荐菜谱列表
     */
    private List<RecommendedRecipe> recommendedRecipes;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 优先级
     */
    private String priority;

    /**
     * 优先级名称
     */
    private String priorityName;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 处理人ID
     */
    private Long handledBy;

    /**
     * 处理人姓名
     */
    private String handledByName;

    /**
     * 处理时间
     */
    private LocalDateTime handledAt;

    /**
     * 处理备注
     */
    private String handleRemark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 推荐菜谱
     */
    @Data
    public static class RecommendedRecipe {
        private Long id;
        private String recipeCode;
        private String recipeName;
        private String categoryName;
        private String imageUrl;
        private BigDecimal estimatedCost;
    }
}
