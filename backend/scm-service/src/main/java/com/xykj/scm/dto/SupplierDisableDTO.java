package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 供应商禁用参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierDisableDTO extends SupplierUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "禁用原因不能为空")
    @Size(max = 500, message = "禁用原因长度不能超过500个字符")
    private String reason;
}
