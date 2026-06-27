package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.RecipeIngredient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜谱食材 Mapper
 */
@Mapper
public interface RecipeIngredientMapper extends BaseMapper<RecipeIngredient> {

    /**
     * 根据菜谱ID查询食材列表
     */
    @Select("SELECT * FROM recipe_ingredient WHERE recipe_id = #{recipeId} AND deleted = 0 ORDER BY is_main DESC, sort_order ASC, id ASC")
    List<RecipeIngredient> selectByRecipeId(@Param("recipeId") Long recipeId);

    /**
     * 批量删除菜谱食材
     */
    int deleteByRecipeId(@Param("recipe_id") Long recipeId);

    /**
     * 批量插入菜谱食材
     */
    void insertBatch(List<RecipeIngredient> ingredients);
}
