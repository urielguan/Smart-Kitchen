package com.xykj.scm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 采购计划作废审核入参
 */
@Data
public class PurchasePlanVoidAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "作废审核结果不能为空")
    private Boolean approved;

    private String remark;
}
