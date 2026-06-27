package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 调整申请审核结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentAuditResultVO implements Serializable {
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
     * 审核后状态
     */
    private String status;

    /**
     * 是否已更新烹饪任务
     */
    private Boolean cookTaskUpdated;
}
