package com.xykj.recipe.service;

import com.xykj.recipe.entity.Recipe;
import com.xykj.recipe.entity.RecipeIngredient;
import com.xykj.recipe.entity.RecipeNutritionResult;

public interface RecipeNutritionSupportService {
    void applyMaterialNutritionSnapshot(RecipeIngredient ingredient);
    RecipeNutritionResult recalculateRecipeNutrition(Recipe recipe);
    RecipeNutritionResult getRecipeNutritionResult(Long recipeId);
}
