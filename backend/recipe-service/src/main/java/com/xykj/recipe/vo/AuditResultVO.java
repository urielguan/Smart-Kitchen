package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 审核结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID
     */
    private Long id;

    /**
     * 审核后状态
     */
    private String status;

    /**
     * 是否已生成烹饪任务
     */
    private Boolean cookTaskGenerated;
}
