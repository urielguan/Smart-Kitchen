package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商注销参数
 */
@Data
public class SupplierCancelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "注销原因不能为空")
    @Size(max = 500, message = "注销原因长度不能超过500个字符")
    private String reason;
}
