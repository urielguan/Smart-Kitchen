package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 字典项详情 VO
 */
@Data
public class DictCategoryDetailVO extends DictCategoryItemVO {

    private Long orgId;

    private Long tenantId;

    private Long createdBy;

    private Long updatedBy;

    private String remark;

    private BigDecimal aiSuggestedAreaCoefficient;

    private String aiSuggestionReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime aiSuggestionGeneratedAt;
}
