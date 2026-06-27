package com.xykj.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class StocktakeOrderRejectDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 300, message = "驳回原因长度不能超过300")
    private String rejectRemark;
}
