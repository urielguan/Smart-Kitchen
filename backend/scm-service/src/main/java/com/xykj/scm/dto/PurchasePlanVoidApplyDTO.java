package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 采购计划作废申请入参
 */
@Data
public class PurchasePlanVoidApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "作废原因不能为空")
    private String reason;
}
