package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜谱计划物料关联计划数量返回。
 */
@Data
public class PurchasePlanRecipeMaterialLinkageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long recipePlanId;
    private String planCode;
    private Long orgId;
    private String orgName;
    private String materialPlanStatus;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long materialId;
        private String materialName;
        private String materialSpec;
        private String unit;
        private BigDecimal originalQty;
        private BigDecimal occupiedQty;
        private BigDecimal availableQty;
        private String materialPlanStatus;
    }
}
