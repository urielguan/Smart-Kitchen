package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 供应商统计
 */
@Data
public class SupplierStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 供应商总数
     */
    private Long total;

    /**
     * 已审核供应商数
     */
    private Long activeCount;

    /**
     * 待审核供应商数
     */
    private Long pendingCount;

    /**
     * 资质30天内到期数
     */
    private Long nearExpireCount;
}
