package com.xykj.recipe.service;

import com.xykj.common.result.PageResult;
import com.xykj.recipe.dto.FoodItemQueryDTO;
import com.xykj.recipe.vo.FoodCategoryVO;
import com.xykj.recipe.vo.FoodImportResultVO;
import com.xykj.recipe.vo.FoodItemVO;

import java.util.List;

public interface FoodStandardService {
    FoodImportResultVO importJson();
    List<FoodCategoryVO> listCategories();
    PageResult<FoodItemVO> listItems(FoodItemQueryDTO query);
    FoodItemVO getItem(Long id);
}
