package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存明细实体（按批次）
 * 对应数据库表: wms_inventory
 */
@Data
@TableName("wms_inventory")
public class Inventory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 库存批次ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓位ID
     */
    private Long locationId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料名称（冗余）
     */
    private String materialName;

    /**
     * 规格说明（冗余）
     */
    private String spec;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 溯源批次码
     */
    private String traceBatchId;

    /**
     * 当前库存数量
     */
    private BigDecimal quantity;

    /**
     * 单位（冗余）
     */
    private String unit;

    /**
     * 单位成本
     */
    private BigDecimal unitCost;

    /**
     * 总库存价值
     */
    private BigDecimal totalCost;

    /**
     * 生产日期
     */
    private LocalDate productionDate;

    /**
     * 到期日期
     */
    private LocalDate expiryDate;

    /**
     * 状态: normal=正常, warning=临期预警, expired=已过期, locked=已锁定
     */
    private String status;

    /**
     * 入库来源: purchase=采购入库, return=退货入库, transfer=调拨入库, stocktake=盘盈
     */
    private String sourceType;

    /**
     * 来源单据ID
     */
    private Long sourceId;

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
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
