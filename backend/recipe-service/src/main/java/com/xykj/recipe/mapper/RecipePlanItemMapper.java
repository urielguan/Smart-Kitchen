package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.RecipePlanItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 菜谱计划明细 Mapper
 */
@Mapper
public interface RecipePlanItemMapper extends BaseMapper<RecipePlanItem> {

    /**
     * 根据计划ID查询明细列表
     */
    java.util.List<RecipePlanItem> selectByPlanId(@Param("plan_id") Long planId);

    /**
     * 批量插入计划明细
     */
    void insertBatch(java.util.List<RecipePlanItem> items);

    /**
     * 批量删除计划明细
     */
    int deleteByPlanId(@Param("plan_id") Long planId);

    /**
     * 获取计划的最大排序号
     */
    @Select("SELECT MAX(sort_order) FROM recipe_plan_item WHERE plan_id = #{planId} AND deleted = 0")
    Integer selectMaxSortByPlanId(@Param("planId") Long planId);
}
