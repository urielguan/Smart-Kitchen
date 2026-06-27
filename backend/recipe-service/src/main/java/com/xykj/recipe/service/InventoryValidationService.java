package com.xykj.recipe.service;

import com.xykj.recipe.dto.StockValidationDTO;
import com.xykj.recipe.vo.RecipePlanMaterialSummaryVO;

import java.util.List;

/**
 * 库存校验服务接口
 */
public interface InventoryValidationService {

    /**
     * 校验菜谱计划的食材库存
     *
     * @param planId 菜谱计划ID
     * @return 校验结果
     */
    StockValidationDTO validateRecipePlanStock(Long planId);

    /**
     * 校验指定食材列表的库存
     *
     * @param materialIds 物料ID列表
     * @param quantities  需求数量列表（与materialIds一一对应）
     * @return 校验结果
     */
    StockValidationDTO validateMaterialStock(List<Long> materialIds, List<java.math.BigDecimal> quantities);

    /**
     * 预留库存（提交计划时）
     *
     * @param planId 菜谱计划ID
     */
    void reserveStock(Long planId);

    /**
     * 释放预留库存（取消计划时）
     *
     * @param planId 菜谱计划ID
     */
    void releaseReservedStock(Long planId);

    /**
     * 为菜谱计划自动生成领用出库单（备料需求清单）
     *
     * @param planId 菜谱计划ID
     */
    void generateMaterialRequisition(Long planId);

    /**
     * 获取菜谱计划的食材汇总
     *
     * @param planId 菜谱计划ID
     * @return 食材汇总列表（按materialId聚合）
     */
    List<RecipePlanMaterialSummaryVO> getMaterialSummary(Long planId);
}
