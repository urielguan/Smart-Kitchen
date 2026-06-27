/** 菜谱状态 */
export type RecipeStatus = 'active' | 'inactive'

/** 菜谱类别 */
export type RecipeCategory = 'staple' | 'main_dish' | 'soup' | 'side_dish' | 'dessert'

/** 菜谱类别类型（兼容旧名称） */
export type RecipeCategoryType = RecipeCategory

/** 菜谱类别信息（数据库实体） */
export interface RecipeCategoryItem {
  id: number
  categoryCode: string
  categoryName: string
  icon?: string
  sortOrder: number
  status: RecipeStatus
  remark?: string
  recipeCount?: number
  createdAt: string
  updatedAt: string
}

/** 菜谱类别表单 */
export interface RecipeCategoryForm {
  categoryCode: string
  categoryName: string
  icon?: string
  sortOrder?: number
  remark?: string
}

/** 菜谱类别查询参数 */
export interface RecipeCategoryQuery {
  pageNum: number
  pageSize: number
  categoryName?: string
  status?: RecipeStatus
}

/** 营养信息 */
export interface NutritionInfo {
  calories: number       // 热量（整道菜汇总）
  protein: number        // 蛋白质（整道菜汇总）
  carbohydrate: number   // 碳水化合物（整道菜汇总）
  fat: number            // 脂肪（整道菜汇总）
  sodium?: number        // 钠（整道菜汇总）
  fiber?: number         // 膳食纤维（整道菜汇总）
}

export interface RecipeNutritionResult {
  recipeId: number
  calories: number
  protein: number
  carbohydrate: number
  fat: number
  sodium?: number
  fiber?: number
  vitaminA?: number
  vitaminB1?: number
  vitaminB2?: number
  vitaminC?: number
  vitaminD?: number
  vitaminE?: number
  calcium?: number
  iron?: number
  zinc?: number
  nutritionScore?: number
  passStatus?: string
  dataCompleteness?: number
  missingMaterialCount?: number
  missingMaterials?: string[]
  calculatedAt?: string
}

/** 维生素信息 */
export interface VitaminInfo {
  vitaminA?: number      // 维生素A（μg）
  vitaminB1?: number     // 维生素B1（mg）
  vitaminB2?: number     // 维生素B2（mg）
  vitaminC?: number      // 维生素C（mg）
  vitaminD?: number      // 维生素D（μg）
  vitaminE?: number      // 维生素E（mg）
}

/** 菜谱食材配比 */
export interface RecipeIngredient {
  id?: number
  menuId?: number
  materialId: number | null
  materialName: string
  materialSpec: string
  quantity: number
  unit: string
  isMain: boolean | null
  foodItemId?: number | null
  foodItemName?: string
  nutritionSourceType?: string
  quantityInGram?: number | null
  caloriesPer100g?: number | null
  proteinPer100g?: number | null
  carbohydratePer100g?: number | null
  fatPer100g?: number | null
  sodiumPer100g?: number | null
  fiberPer100g?: number | null
  sortOrder?: number
  remark?: string
}

/** 菜谱信息 */
export interface Recipe {
  id: number
  menuCode: string
  menuName: string
  categoryId?: number
  categoryName: string
  menuCategory?: string         // 菜谱类别代码（用于显示颜色）
  description?: string
  imageUrl?: string
  servingSize?: number          // 份量（克）
  cookingTime: number           // 烹饪时长（分钟）
  cookingTempMin?: number       // 最低烹饪温度（℃）
  cookingTempMax?: number       // 最高烹饪温度（℃）
  cookingSteps?: string         // 烹饪步骤
  unitCost?: number             // 单份成本（元）
  nutritionInfo: NutritionInfo  // 营养成分
  vitaminInfo?: VitaminInfo     // 维生素含量
  mineralInfo?: MineralInfo     // 矿物质含量
  nutritionScore?: number       // 营养评分（0-100）
  dataCompleteness?: number
  missingMaterialCount?: number
  missingMaterials?: string[]
  aiSuggestions?: string        // AI优化建议
  nutritionResult?: RecipeNutritionResult
  status: RecipeStatus
  ingredients: RecipeIngredient[]
  createdAt: string
  updatedAt: string
}

/** 后端菜谱详情响应（字段名映射用） */
export interface RecipeDetailResponse {
  id: number
  recipeCode: string
  recipeName: string
  categoryId?: number
  categoryName: string
  description?: string
  imageUrl?: string
  servingSize?: number
  targetCookTime?: number
  targetTempMin?: number
  targetTempMax?: number
  cookingSteps?: string
  unitCost?: number
  nutritionInfo?: NutritionInfo
  vitaminInfo?: VitaminInfo
  mineralInfo?: MineralInfo
  nutritionScore?: number
  aiSuggestions?: string
  status: RecipeStatus
  ingredients?: RecipeIngredient[]
  createdAt: string
  updatedAt: string
}

/** 映射后端菜谱详情到前端Recipe格式 */
export function mapRecipeDetailToRecipe(detail: RecipeDetailResponse): Recipe {
  return {
    id: detail.id,
    menuCode: detail.recipeCode,
    menuName: detail.recipeName,
    categoryId: detail.categoryId,
    categoryName: detail.categoryName,
    description: detail.description,
    imageUrl: detail.imageUrl,
    servingSize: detail.servingSize,
    cookingTime: detail.targetCookTime || 0,
    cookingTempMin: detail.targetTempMin,
    cookingTempMax: detail.targetTempMax,
    cookingSteps: detail.cookingSteps,
    unitCost: detail.unitCost,
    nutritionInfo: detail.nutritionInfo || { calories: 0, protein: 0, carbohydrate: 0, fat: 0 },
    vitaminInfo: detail.vitaminInfo,
    mineralInfo: detail.mineralInfo,
    nutritionScore: detail.nutritionScore,
    aiSuggestions: detail.aiSuggestions,
    status: detail.status,
    ingredients: detail.ingredients || [],
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt
  }
}

/** 矿物质信息 */
export interface MineralInfo {
  calcium?: number   // 钙（mg）
  iron?: number      // 铁（mg）
  zinc?: number      // 锌（mg）
}

/** 菜谱表单 */
export interface RecipeForm {
  menuCode: string
  menuName: string
  menuCategory: string | null
  description: string
  imageUrl: string
  servingSize: number
  cookingTime: number
  cookingTempMin: number
  cookingTempMax: number
  cookingSteps: string
  ingredients: RecipeIngredient[]
  status: RecipeStatus
}

/** 菜谱查询参数 */
export interface RecipeQuery {
  pageNum: number
  pageSize: number
  recipeName?: string
  recipeCode?: string
  categoryId?: number
  status?: RecipeStatus
}

/** 菜谱统计数据 */
export interface RecipeStatistics {
  totalRecipes: number
  activeRecipes: number
  inactiveRecipes: number
  weeklyNewRecipes: number
  monthlyNewRecipes: number
  ingredientCoverage: number
  nutritionPassRate: number
  avgNutritionScore: number
  nutritionDistribution: NutritionDistribution
  categoryDistribution: CategoryStats[]
  weeklyHotRecipes: HotRecipe[]
  monthlyHotRecipes: HotRecipe[]
  ratingDistribution: RatingDistribution
}

/** 营养素分布 */
export interface NutritionDistribution {
  proteinPercent: number
  carbsPercent: number
  fatPercent: number
  avgCalories: number
  avgProtein: number
  avgCarbs: number
  avgFat: number
}

/** 类别统计 */
export interface CategoryStats {
  categoryId: number
  categoryName: string
  categoryIcon: string
  recipeCount: number
  percentage: number
  avgNutritionScore: number
}

/** 热门菜谱 */
export interface HotRecipe {
  recipeId: number
  recipeCode: string
  recipeName: string
  recipeCategory: string | null
  categoryName: string
  serveCount: number
  viewCount: number
  rating: number
  nutritionScore: number
}

/** 评分分布 */
export interface RatingDistribution {
  fiveStar: number
  fourStar: number
  threeStar: number
  twoStar: number
  oneStar: number
  avgRating: number
}

/** 菜谱导入失败记录 */
export interface RecipeImportFailure {
  rowNum: number
  recipeCode: string
  recipeName: string
  errorMessage: string
}

/** 菜谱导入结果 */
export interface RecipeImportResult {
  total: number
  successCount: number
  failCount: number
  createCount: number
  updateCount: number
  hasErrors: boolean
  errorFileUrl: string | null
  errors: string[]
  failures: RecipeImportFailure[]
}

/** AI营养分析结果 */
export interface AINutritionAnalysis {
  recipeId: number
  recipeName: string
  nutritionInfo: NutritionInfo
  vitaminInfo: VitaminInfo
  mineralInfo?: {
    calcium?: number   // 钙（mg）
    iron?: number      // 铁（mg）
    zinc?: number      // 锌（mg）
  }
  analysisTime: string
  dataCompleteness?: number
  missingMaterialCount?: number
  missingMaterials?: string[]
}

/** AI智能菜谱优化分析结果 */
export interface AIOptimizationAnalysis {
  recipeId: number
  recipeName: string
  comprehensiveDashboard: {
    costPercentVsAvg: number    // 预估食材成本较均价的百分比
    nutritionScore: number      // 营养评分
    reviewScore: number         // 评价评分
    complaintCount: number      // 投诉反馈条数（近30天）
  }
  costAnalysis: {
    recentPurchases: Array<{    // 最近采购记录
      materialName: string
      unitPrice: number
      purchaseDate: string
    }>
    highCostAlerts: AICostAlert[]  // 高成本食材预警
  }
  complaintAnalysis: {             // 投诉反馈分析
    tasteIssues: number            // 口味问题数
    qualityIssues: number          // 质量问题数
    portionIssues: number          // 份量问题数
    otherIssues: number            // 其他问题数
    complaintSuggestions: string   // 投诉反馈建议
    recentReviews: Array<{         // 最近3条评价
      score: number
      content: string
      reviewTime: string
    }>
  }
  optimizationSuggestions: AIOptimizationSuggestion[]  // AI优化建议
}

/** AI优化建议项 */
export interface AIOptimizationSuggestion {
  suggestionName: string    // 建议方案名称
  source: string            // 来源（成本分析/营养分析/投诉反馈等）
  priority: 'high' | 'medium' | 'low'  // 优先级
  description: string       // 建议描述
  improvementTrend: string  // 改善后趋势描述
}

/** AI成本预警 */
export interface AICostAlert {
  materialName: string   // 物料名称
  reason: string         // 原因
  currentPrice: number   // 当前价格
  avgPrice: number       // 均价
  aiSuggestion: string   // AI建议
}

/** AI烹饪参数建议 */
export interface AICookingSuggestion {
  suggestedTime: number      // 建议时长（分钟）
  suggestedTempMin: number   // 建议最低温度（℃）
  suggestedTempMax: number   // 建议最高温度（℃）
  reason: string             // 建议原因
  foodSafetyStandard: string // 食品安全标准说明
}
