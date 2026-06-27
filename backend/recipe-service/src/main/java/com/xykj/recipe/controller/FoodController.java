package com.xykj.recipe.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.recipe.dto.FoodItemQueryDTO;
import com.xykj.recipe.service.FoodStandardService;
import com.xykj.recipe.vo.FoodCategoryVO;
import com.xykj.recipe.vo.FoodImportResultVO;
import com.xykj.recipe.vo.FoodItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recipe/food")
@RequiredArgsConstructor
public class FoodController {

    private final FoodStandardService foodStandardService;

    @PostMapping("/import-json")
    public R<FoodImportResultVO> importJson() {
        return R.ok(foodStandardService.importJson());
    }

    @GetMapping("/categories")
    public R<List<FoodCategoryVO>> listCategories() {
        return R.ok(foodStandardService.listCategories());
    }

    @GetMapping("/items")
    public R<PageResult<FoodItemVO>> listItems(FoodItemQueryDTO query) {
        return R.ok(foodStandardService.listItems(query));
    }

    @GetMapping("/items/{id}")
    public R<FoodItemVO> getItem(@PathVariable Long id) {
        return R.ok(foodStandardService.getItem(id));
    }
}
