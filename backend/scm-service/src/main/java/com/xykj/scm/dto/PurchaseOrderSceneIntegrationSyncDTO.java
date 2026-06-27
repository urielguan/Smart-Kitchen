package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class PurchaseOrderSceneIntegrationSyncDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "同步方案不能为空")
    private Long configId;

    @NotBlank(message = "第三方外部单号不能为空")
    private String externalNo;

    private Integer queryOnly = 0;
}
