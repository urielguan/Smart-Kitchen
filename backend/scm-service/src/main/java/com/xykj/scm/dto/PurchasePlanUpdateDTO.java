package com.xykj.scm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 编辑采购计划入参
 */
@Data
public class PurchasePlanUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String planNo;

    @NotBlank(message = "采购计划名称不能为空")
    @Size(max = 100, message = "计划名称长度不能超过100个字符")
    private String planName;

    @NotNull(message = "所属组织不能为空")
    private Long orgId;

    @NotBlank(message = "计划日期不能为空")
    private String planDate;

    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于0")
    private BigDecimal budgetAmount;

    private String relatedDocument;
    private String remark;

    @NotBlank(message = "状态不能为空")
    private String status;

    @Valid
    @NotEmpty(message = "请至少填写一条物料明细")
    private List<PurchasePlanItemDTO> items;

    @Valid
    private List<PurchasePlanAttachmentDTO> attachments;
}
