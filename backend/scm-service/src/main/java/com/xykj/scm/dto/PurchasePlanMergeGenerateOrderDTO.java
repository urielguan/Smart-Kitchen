package com.xykj.scm.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 多选合并生成采购订单入参
 */
@Data
public class PurchasePlanMergeGenerateOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "请至少选择一条采购计划")
    private List<Long> planIds;
}
