package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.recipe.entity.RecipePlanAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 菜谱计划调整申请Mapper
 */
@Mapper
public interface RecipePlanAdjustmentMapper extends BaseMapper<RecipePlanAdjustment> {

    /**
     * 分页查询调整申请列表（关联计划信息）
     *
     * @param page 分页参数
     * @param planId 计划ID（可选）
     * @param status 状态（可选）
     * @return 分页结果
     */
    IPage<RecipePlanAdjustment> selectPageByCondition(
            Page<RecipePlanAdjustment> page,
            @Param("planId") Long planId,
            @Param("status") String status);

    /**
     * 根据ID查询调整申请详情（关联计划信息）
     *
     * @param id 调整申请ID
     * @return 调整申请实体（包含计划编码和日期）
     */
    RecipePlanAdjustment selectDetailWithPlanById(@Param("id") Long id);
}
