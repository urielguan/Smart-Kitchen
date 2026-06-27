package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 批量操作结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 批次ID */
    private String batchId;

    /** 总数 */
    private int totalCount;

    /** 成功数 */
    private int successCount;

    /** 失败数 */
    private int failCount;

    /** 逐条结果 */
    private List<BatchItemResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItemResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 计划ID */
        private Long planId;

        /** 计划编号 */
        private String planCode;

        /** 是否成功 */
        private boolean success;

        /** 失败分类: permission / business_rule / system_error */
        private String failCategory;

        /** 失败原因 */
        private String failReason;
    }
}
