package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购需求预测关联采购计划返回
 */
@Data
public class PurchaseDemandForecastLinkedPlanVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String planNo;
    private String planName;
    private String status;
    private String planDate;
    private BigDecimal totalAmount;
    private String createdBy;
    private String createdAt;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private Long materialId;
        private String materialName;
        private String materialSpec;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal estimatedUnitPrice;
        private BigDecimal estimatedAmount;
        private String remark;
    }
}
