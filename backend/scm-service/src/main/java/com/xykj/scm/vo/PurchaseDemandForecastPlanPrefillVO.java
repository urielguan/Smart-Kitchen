package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购需求预测生成采购计划预填返回
 */
@Data
public class PurchaseDemandForecastPlanPrefillVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long forecastId;
    private String forecastNo;
    private String planName;
    private Long orgId;
    private String orgName;
    private String planDate;
    private BigDecimal budgetAmount;
    private String createdBy;
    private String relatedDocument;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long forecastDetailId;
        private Long materialId;
        private String materialName;
        private String materialSpec;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal estimatedUnitPrice;
        private BigDecimal estimatedAmount;
    }
}
