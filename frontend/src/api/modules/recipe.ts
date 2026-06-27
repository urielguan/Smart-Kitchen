import { get, post, put, del } from '@/api'
import service from '@/api'
import type {
  Recipe,
  RecipeForm,
  RecipeQuery,
  RecipeStatistics,
  HotRecipe,
  RecipeNutritionResult,
  AINutritionAnalysis,
  AIOptimizationAnalysis,
  AICookingSuggestion,
  RecipeCategoryItem,
  RecipeCategoryForm,
  RecipeCategoryQuery,
  RecipeImportResult
} from '@/types/recipe'
import type {
  RecipePlan,
  RecipePlanDetail,
  RecipePlanQuery,
  RecipePlanForm,
  StockValidation,
  AINutritionAssessment
} from '@/types/plan'
import type { ApiResponse, PageResponse } from '@/types/api'

// ========== 菜谱类别管理 ==========

/** 获取类别列表 */
export const getCategoryList = (params: Partial<RecipeCategoryQuery>): Promise<ApiResponse<PageResponse<RecipeCategoryItem>>> => {
  return get('/v1/recipe/categories', params)
}

/** 获取所有启用的类别（下拉选择用） */
export const getActiveCategories = (): Promise<ApiResponse<RecipeCategoryItem[]>> => {
  return get('/v1/recipe/categories/active')
}

/** 获取类别详情 */
export const getCategoryDetail = (id: number): Promise<ApiResponse<RecipeCategoryItem>> => {
  return get(`/v1/recipe/categories/${id}`)
}

/** 新增类别 */
export const createCategory = (data: RecipeCategoryForm): Promise<ApiResponse<{ id: number; categoryCode: string; categoryName: string }>> => {
  return post('/v1/recipe/categories', data)
}

/** 编辑类别 */
export const updateCategory = (id: number, data: Partial<RecipeCategoryForm>): Promise<ApiResponse<{ id: number; categoryName: string }>> => {
  return put(`/v1/recipe/categories/${id}`, data)
}

/** 删除类别 */
export const deleteCategory = (id: number): Promise<ApiResponse<null>> => {
  return del(`/v1/recipe/categories/${id}`)
}

/** 切换类别状态 */
export const toggleCategoryStatus = (id: number): Promise<ApiResponse<null>> => {
  return put(`/v1/recipe/categories/${id}/status`)
}

// ========== 菜谱管理 ==========

/** 获取菜谱可视化看板 */
export const getRecipeDashboard = (): Promise<ApiResponse<RecipeStatistics>> => {
  return get('/v1/recipe/dashboard')
}

/** 获取菜谱统计数据 */
export const getRecipeStatistics = (): Promise<ApiResponse<RecipeStatistics>> => {
  return get('/v1/recipe/statistics')
}

/** 获取菜谱列表 */
export const getRecipeList = (params: Partial<RecipeQuery>): Promise<ApiResponse<PageResponse<Recipe>>> => {
  return get('/v1/recipe/recipes', params)
}

/** 获取菜谱详情 */
export const getRecipeDetail = (id: number): Promise<ApiResponse<Recipe>> => {
  return get(`/v1/recipe/recipes/${id}`)
}

/** 获取菜谱营养结果 */
export const getRecipeNutritionResult = (id: number): Promise<ApiResponse<RecipeNutritionResult>> => {
  return get(`/v1/recipe/recipes/${id}/nutrition-result`)
}

/** 新增菜谱 */
export const createRecipe = (data: RecipeForm): Promise<ApiResponse<{ id: number; recipeCode: string; recipeName: string }>> => {
  return post('/v1/recipe/recipes', data)
}

/** 编辑菜谱 */
export const updateRecipe = (id: number, data: Partial<RecipeForm>): Promise<ApiResponse<{ id: number; recipeName: string }>> => {
  return put(`/v1/recipe/recipes/${id}`, data)
}

/** 删除菜谱 */
export const deleteRecipe = (id: number): Promise<ApiResponse<null>> => {
  return del(`/v1/recipe/recipes/${id}`)
}

/** 复制菜谱 */
export const copyRecipe = (id: number): Promise<ApiResponse<{ id: number }>> => {
  return post(`/v1/recipe/recipes/${id}/copy`)
}

/** 切换菜谱状态 */
export const toggleRecipeStatus = (id: number): Promise<ApiResponse<null>> => {
  return put(`/v1/recipe/recipes/${id}/status`)
}

/** 导出菜谱列表 */
export const exportRecipes = async (params: Partial<RecipeQuery>): Promise<void> => {
  const response = await service.get('/v1/recipe/recipes/export', { params, responseType: 'blob' })

  const contentDisposition = response.headers['content-disposition']
  let filename = `菜谱列表_${new Date().toISOString().slice(0, 10)}.xlsx`
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)/i)
    if (match) filename = decodeURIComponent(match[1].replace(/['"]/g, ''))
  }

  const blob = new Blob([response.data as BlobPart])
  const blobUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

/** 下载菜谱导入模板 */
export const downloadRecipeImportTemplate = async (): Promise<void> => {
  const response = await service.get('/v1/recipe/recipes/import/template', { responseType: 'blob' })

  const blob = new Blob([response.data as BlobPart])
  const blobUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = '菜谱导入模板.xlsx'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

/** 导入菜谱 */
export const importRecipes = (file: File): Promise<ApiResponse<RecipeImportResult>> => {
  const formData = new FormData()
  formData.append('file', file)
  // 使用 post() 而非 service.post()，确保返回 ApiResponse 而非 AxiosResponse
  return post<RecipeImportResult>('/v1/recipe/recipes/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 下载菜谱导入错误文件 */
export const downloadRecipeImportErrorFile = async (fileName: string): Promise<void> => {
  const response = await service.get(`/v1/recipe/recipes/import/errors/${fileName}`, { responseType: 'blob' })

  const blob = new Blob([response.data as BlobPart])
  const blobUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

/** AI营养成分分析 */
export const getAINutritionAnalysis = (id: number): Promise<ApiResponse<AINutritionAnalysis>> => {
  return get(`/v1/recipe/recipes/${id}/ai-nutrition`)
}

/** AI智能菜谱优化 */
export const getAIOptimizationAnalysis = (id: number): Promise<ApiResponse<AIOptimizationAnalysis>> => {
  return get(`/v1/recipe/recipes/${id}/ai-optimization`)
}

/** AI烹饪参数建议 */
export const getAICookingSuggestion = (data: {
  menuName: string
  cookingSteps: string
  ingredients: Array<{ materialName: string }>
}): Promise<ApiResponse<AICookingSuggestion>> => {
  return post('/v1/recipe/recipes/ai-cooking-suggestion', data)
}

// ========== 菜谱计划管理 ==========

/** 获取计划列表 */
export const getPlanList = (params: Partial<RecipePlanQuery>): Promise<ApiResponse<PageResponse<RecipePlan>>> => {
  return get('/v1/recipe/plans', params)
}

/** 获取计划详情 */
export const getPlanDetail = (id: number): Promise<ApiResponse<RecipePlanDetail>> => {
  return get(`/v1/recipe/plans/${id}`)
}

/** 新增计划 */
export const createPlan = (data: RecipePlanForm): Promise<ApiResponse<{ id: number; planCode: string }>> => {
  return post('/v1/recipe/plans', data)
}

/** 编辑计划 */
export const updatePlan = (id: number, data: Partial<RecipePlanForm>): Promise<ApiResponse<{ id: number }>> => {
  return put(`/v1/recipe/plans/${id}`, data)
}

/** 删除计划 */
export const deletePlan = (id: number): Promise<ApiResponse<null>> => {
  return del(`/v1/recipe/plans/${id}`)
}

/** 提交计划审核 */
export const submitPlan = (id: number): Promise<ApiResponse<null>> => {
  return post(`/v1/recipe/plans/${id}/submit`)
}

/** 审核计划 */
export const auditPlan = (id: number, data: { status: string; remark?: string }): Promise<ApiResponse<null>> => {
  return put(`/v1/recipe/plans/${id}/audit`, data)
}

/** 库存校验 */
export const validatePlanStock = (id: number): Promise<ApiResponse<StockValidation>> => {
  return get(`/v1/recipe/plans/${id}/validate-stock`)
}

/** 创建调整申请 */
export const createAdjustment = (planId: number, data: {
  adjustReason: string
  adjustType: string
  afterData: string
}): Promise<ApiResponse<{ id: number }>> => {
  return post(`/v1/recipe/plans/${planId}/adjust`, data)
}

/** 审核调整申请 */
export const auditAdjustment = (id: number, data: { status: string; remark?: string }): Promise<ApiResponse<null>> => {
  return put(`/v1/recipe/plan-adjustments/${id}/audit`, data)
}

/** AI营养评估 */
export const getPlanAINutritionAssessment = (id: number): Promise<ApiResponse<AINutritionAssessment>> => {
  return get(`/v1/recipe/plans/${id}/ai-nutrition-assessment`)
}

/** AI智能推荐菜谱 */
export const getAIRecommendRecipes = (params: { planId?: number }): Promise<ApiResponse<Recipe[]>> => {
  return post('/v1/recipe/plans/ai-recommend', params)
}

/** AI智能推荐菜谱（增强版，支持预算和周计划） */
export const getAIRecommendRecipesEnhanced = (params: {
  planId?: number
  targetGroup?: string
  healthStatus?: string
  budgetLimit?: number
  planDimension?: 'single' | 'week' | 'month'
  weekStartDate?: string
  daysCount?: number
}): Promise<ApiResponse<import('@/types/plan').AIRecommendResult>> => {
  return post('/v1/recipe/plans/ai-recommend-enhanced', params)
}
