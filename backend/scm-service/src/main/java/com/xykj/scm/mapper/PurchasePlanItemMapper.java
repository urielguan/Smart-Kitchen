package com.xykj.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.scm.entity.PurchasePlanItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 采购计划明细 Mapper
 */
@Mapper
public interface PurchasePlanItemMapper extends BaseMapper<PurchasePlanItem> {

    @Delete("DELETE FROM scm_purchase_plan_item WHERE plan_id = #{planId}")
    void deleteByPlanId(@Param("planId") Long planId);
}
