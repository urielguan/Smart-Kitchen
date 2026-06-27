package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商审核参数
 */
@Data
public class SupplierAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审核状态：active/rejected
     */
    @NotBlank(message = "审核状态不能为空")
    @Size(max = 20, message = "审核状态长度不能超过20个字符")
    private String status;

    @Size(max = 500, message = "审核备注长度不能超过500个字符")
    private String remark;
}
