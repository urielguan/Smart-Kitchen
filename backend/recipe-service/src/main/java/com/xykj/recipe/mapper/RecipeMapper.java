package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 菜谱 Mapper
 */
@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {

    /**
     * 根据类别ID查询菜谱
     */
    Recipe selectByCategoryId(@Param("category_id") Long categoryId);

    /**
     * 根据编码查询菜谱
     */
    Recipe selectByRecipeCode(@Param("recipe_code") String recipeCode);

    /**
     * 统计各状态的菜谱数量
     */
    Integer countByStatus(@Param("status") String status);

    /**
     * 统计菜谱总数
     */
    @Select("SELECT COUNT(*) FROM recipe WHERE deleted = 0")
    Integer selectTotalCount();

    /**
     * 统计营养达标率（评分>=70）
     */
    @Select("SELECT COUNT(*) FROM recipe WHERE deleted = 0 AND nutrition_score >= 70")
    Integer selectNutritionPassCount();
}
