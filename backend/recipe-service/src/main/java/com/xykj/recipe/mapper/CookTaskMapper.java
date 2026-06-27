package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.CookTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 烹饪任务Mapper（跨服务操作cook_task表）
 */
@Mapper
public interface CookTaskMapper extends BaseMapper<CookTask> {

    /**
     * 根据计划ID查询烹饪任务
     */
    @Select("SELECT * FROM cook_task WHERE plan_id = #{planId} AND deleted = 0 ORDER BY id")
    List<CookTask> selectByPlanId(@Param("planId") Long planId);

    /**
     * 统计计划ID对应的任务数量
     */
    @Select("SELECT COUNT(*) FROM cook_task WHERE plan_id = #{planId} AND deleted = 0")
    int countByPlanId(@Param("planId") Long planId);
}
