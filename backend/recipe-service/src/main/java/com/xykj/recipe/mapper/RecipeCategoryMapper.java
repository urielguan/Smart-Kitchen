package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.RecipeCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 菜谱类别 Mapper
 */
@Mapper
public interface RecipeCategoryMapper extends BaseMapper<RecipeCategory> {

    /**
     * 查询所有启用的类别
     */
    java.util.List<RecipeCategory> selectAllActive();

    /**
     * 根据ID查询类别
     */
    RecipeCategory selectById(@Param("id") Long id);
}
