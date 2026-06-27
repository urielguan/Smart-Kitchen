package com.xykj.scm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 编辑采购订单入参
 */
@Data
public class PurchaseOrderUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;

    @NotNull(message = "所属组织不能为空")
    private Long orgId;

    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    @NotBlank(message = "订单日期不能为空")
    private String orderDate;

    @NotBlank(message = "预计到货日期不能为空")
    private String expectedArrival;

    private String attachmentName;
    private String attachmentUrl;
    private String remark;
    private Boolean clearAttachment;

    @NotBlank(message = "状态不能为空")
    private String status;

    private List<Long> relatedPlanIds;

    @Valid
    @NotEmpty(message = "请至少填写一条物料明细")
    private List<PurchaseOrderItemDTO> items;
}
