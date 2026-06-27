package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 采购订单反审核入参
 */
@Data
public class PurchaseOrderReverseAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写反审核原因")
    @Size(max = 500, message = "反审核原因不能超过500个字符")
    private String reason;
}
