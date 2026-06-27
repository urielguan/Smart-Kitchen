package com.xykj.wms.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class StocktakeOrderApproveDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Size(max = 300, message = "审核备注长度不能超过300")
    private String approveRemark;
}
