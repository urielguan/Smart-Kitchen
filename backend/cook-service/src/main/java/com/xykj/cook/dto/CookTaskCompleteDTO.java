package com.xykj.cook.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 完成烹饪DTO
 */
@Data
public class CookTaskCompleteDTO {

    /**
     * 实际完成份数
     */
    private Integer actualQty;

    /**
     * 质量评分（0-100）
     */
    private BigDecimal qualityScore;

    /**
     * 完成备注
     */
    private String remark;
}
