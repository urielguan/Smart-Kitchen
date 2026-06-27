package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.RecipePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 菜谱计划 Mapper
 */
@Mapper
public interface RecipePlanMapper extends BaseMapper<RecipePlan> {

    /**
     * 根据日期范围查询计划
     */
    java.util.List<RecipePlan> selectByPlanDateBetween(@Param("startDate") LocalDate startDate,
                                                               @Param("endDate") LocalDate endDate);

    /**
     * 统计各状态的计划数量
     */
    Integer countByStatus(@Param("status") String status);
}
