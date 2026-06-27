package com.xykj.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.recipe.dto.RecipeCategoryCreateDTO;
import com.xykj.recipe.dto.RecipeCategoryQueryDTO;
import com.xykj.recipe.vo.RecipeCategoryVO;

import java.util.List;

/**
 * 菜谱类别服务接口
 */
public interface RecipeCategoryService {

    /**
     * 分页查询类别列表
     */
    Page<RecipeCategoryVO> list(RecipeCategoryQueryDTO query);

    /**
     * 获取所有启用的类别（用于下拉选择）
     */
    List<RecipeCategoryVO> listActive();

    /**
     * 获取类别详情
     */
    RecipeCategoryVO getDetail(Long id);

    /**
     * 创建类别
     */
    Long create(RecipeCategoryCreateDTO dto);

    /**
     * 更新类别
     */
    void update(Long id, RecipeCategoryCreateDTO dto);

    /**
     * 删除类别
     */
    void delete(Long id);

    /**
     * 切换类别状态
     */
    void toggleStatus(Long id);
}
