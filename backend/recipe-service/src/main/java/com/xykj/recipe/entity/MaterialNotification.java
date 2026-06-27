package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料预警通知实体
 * 对应数据库表: recipe_material_notification
 */
@Data
@TableName("recipe_material_notification")
public class MaterialNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通知ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 通知类型: expiring=临期预警, expired=已过期, low_stock=库存不足
     */
    private String notificationType;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 库存批次ID (对应wms_inventory的id)
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
     * 推荐菜谱ID列表（逗号分隔）
     */
    private String recommendedRecipeIds;

    /**
     * 推荐菜谱名称列表（逗号分隔）
     */
    private String recommendedRecipeNames;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 优先级: high=高, medium=中, low=低
     */
    private String priority;

    /**
     * 状态: unread=未读, read=已读, handled=已处理, dismissed=已忽略
     */
    private String status;

    /**
     * 处理人ID
     */
    private Long handledBy;

    /**
     * 处理时间
     */
    private LocalDateTime handledAt;

    /**
     * 处理备注
     */
    private String handleRemark;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
