package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 物料统计数据视图对象
 */
@Data
public class MaterialStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物料总数
     */
    private Long total;

    /**
     * 启用中物料数
     */
    private Long activeCount;

    /**
     * 已停用物料数
     */
    private Long inactiveCount;

    /**
     * 待完善资料物料数（必填字段缺失）
     */
    private Long incompleteCount;
}