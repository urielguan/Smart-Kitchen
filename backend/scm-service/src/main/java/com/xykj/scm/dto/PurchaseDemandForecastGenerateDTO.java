package com.xykj.scm.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 执行采购需求预测入参
 */
@Data
public class PurchaseDemandForecastGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orgId;

    @Pattern(regexp = "daily|weekly", message = "预测维度仅支持 daily 或 weekly")
    private String dimension = "weekly";
}
