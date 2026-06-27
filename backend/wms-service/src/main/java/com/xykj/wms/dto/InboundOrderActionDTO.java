package com.xykj.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class InboundOrderActionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull
    private Long version;

    @NotBlank
    @Size(max = 64)
    private String idempotencyKey;

    @Size(max = 500)
    private String remark;

    @Size(max = 500)
    private String approveRemark;
}
