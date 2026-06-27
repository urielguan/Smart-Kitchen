package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 审核采购订单入参
 */
@Data
public class PurchaseOrderAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "审核状态不能为空")
    private String status;

    private String remark;
}
