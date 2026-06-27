package com.xykj.scm.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 采购需求预测生成采购计划预填入参
 */
@Data
public class PurchaseDemandForecastPrefillDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "请至少选择一条预测明细")
    private List<Long> detailIds;
}
