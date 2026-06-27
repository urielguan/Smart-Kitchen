package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 调整申请结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 调整申请ID
     */
    private Long id;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 调整编码
     */
    private String adjustCode;

    /**
     * 申请状态
     */
    private String status;
}
