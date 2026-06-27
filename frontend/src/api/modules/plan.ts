import { get, post, put, del } from '@/api'
import service from '@/api'
import type {
  RecipePlan,
  RecipePlanDetail,
  RecipePlanForm,
  RecipePlanQuery,
  StockValidation,
  RecipePlanAdjustment,
  RecipePlanAdjustmentDetail,
  AINutritionAssessment,
  AIRecommendResult,
  PlanImportResult,
  RecipePlanAuditLogItem,
  CopyPlanResult,
  BatchOperationResult
} from '@/types/plan'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { Recipe } from '@/types/recipe'

/** 获取菜谱计划列表 */
export const getPlanList = (params: Partial<RecipePlanQuery>): Promise<ApiResponse<PageResponse<RecipePlan>>> => {
  return get('/v1/recipe/plans', params)
}

/** 获取菜谱计划统计 */
export const getPlanStatistics = (): Promise<ApiResponse<{ total: number; approvedCount: number; pendingCount: number; totalServings: number }>> => {
  return get('/v1/recipe/plans/statistics')
}

/** 获取菜谱计划详情 */
export const getPlanDetail = (id: number): Promise<ApiResponse<RecipePlanDetail>> => {
  return get(`/v1/recipe/plans/${id}`)
}

/** 获取菜谱计划食材汇总 */
export const getPlanMaterialSummary = (id: number): Promise<ApiResponse<PlanMaterialSummary[]>> => {
  return get(`/v1/recipe/plans/${id}/materials`)
}

/** 菜谱计划食材汇总 */
export interface PlanMaterialSummary {
  materialId: number
  materialName: string
  spec?: string
  unit: string
  totalQuantity: number
  unitCost?: number
}

/** 新增菜谱计划 */
export const createPlan = (data: RecipePlanForm): Promise<ApiResponse<{ id: number; planCode: string }>> => {
  return post('/v1/recipe/plans', data)
}

/** 编辑菜谱计划 */
export const updatePlan = (id: number, data: Partial<RecipePlanForm>): Promise<ApiResponse<{ id: number }>> => {
  return put(`/v1/recipe/plans/${id}`, data)
}

/** 删除菜谱计划 */
export const deletePlan = (id: number): Promise<ApiResponse<null>> => {
  return del(`/v1/recipe/plans/${id}`)
}

/** 提交菜谱计划审核 */
export const submitPlan = (id: number): Promise<ApiResponse<null>> => {
  return post(`/v1/recipe/plans/${id}/submit`)
}

/** 审核菜谱计划 */
export const auditPlan = (id: number, data: { status: string; remark?: string }): Promise<ApiResponse<null>> => {
  return put(`/v1/recipe/plans/${id}/audit`, data)
}

/** 库存校验 */
export const validateStock = (id: number): Promise<ApiResponse<StockValidation>> => {
  return get(`/v1/recipe/plans/${id}/validate-stock`)
}

/** AI营养评估 */
export const getAiNutritionAssessment = (id: number): Promise<ApiResponse<AINutritionAssessment>> => {
  return get(`/v1/recipe/plans/${id}/ai-nutrition-assessment`)
}

/** 规则营养分析 */
export const analyzePlanNutrition = (data: {
  recipes: Array<{ recipeId: number; servings: number }>
  servingCount: number
  targetGroup?: string
  healthStatus?: string
}): Promise<ApiResponse<AINutritionAssessment>> => {
  return post('/v1/recipe/nutrition/analyze', data)
}

/** AI智能推荐菜谱 */
export const getAiRecommendRecipes = (params: Partial<RecipePlanQuery>): Promise<ApiResponse<Recipe[]>> => {
  return post('/v1/recipe/plans/ai-recommend', params)
}

/** AI智能推荐菜谱（增强版，支持预算和周计划） */
export const getAiRecommendRecipesEnhanced = (params: {
  targetGroup?: string
  healthStatus?: string
  budgetLimit?: number
  planDimension?: 'single' | 'week' | 'month'
  weekStartDate?: string
  daysCount?: number
  expectedCount?: number
  considerStock?: boolean
  prioritizeExpiring?: boolean
  // ============= 用餐偏好相关参数 =============
  /** 口味偏好（逗号分隔） */
  flavorPreferences?: string
  /** 辣度级别（0-5） */
  spicyLevel?: number
  /** 禁忌食材ID列表（逗号分隔） */
  avoidIngredientIds?: string
  /** 饮食偏好标签（逗号分隔） */
  dietTags?: string
}): Promise<ApiResponse<AIRecommendResult>> => {
  return post('/v1/recipe/plans/ai-recommend-enhanced', params)
}

/** 菜谱计划调整申请 */
export const createAdjustment = (id: number, data: {
  adjustReason: string
  adjustType: string
  afterData: string
}): Promise<ApiResponse<{ id: number; planId: number; adjustCode?: string; status: string }>> => {
  return post(`/v1/recipe/plans/${id}/adjust`, data)
}

/** 审核调整申请 */
export const auditAdjustment = (id: number, data: { status: string; remark?: string }): Promise<ApiResponse<null>> => {
  return put(`/v1/recipe/plan-adjustments/${id}/audit`, data)
}

/** 获取调整申请列表 */
export const getAdjustmentList = (params: {
  planId?: number
  planCode?: string
  adjustType?: string
  status?: string
  planDateStart?: string
  planDateEnd?: string
  pageNum?: number
  pageSize?: number
}): Promise<ApiResponse<PageResponse<RecipePlanAdjustment>>> => {
  return get('/v1/recipe/plan-adjustments', params)
}

/** 获取调整申请详情 */
export const getAdjustmentDetail = (id: number): Promise<ApiResponse<RecipePlanAdjustmentDetail>> => {
  return get(`/v1/recipe/plan-adjustments/${id}`)
}

/** 导出菜谱计划列表 */
export const exportPlans = async (params: Record<string, any>): Promise<void> => {
  const response = await service.get('/v1/recipe/plans/export', { params, responseType: 'blob' })

  const contentDisposition = response.headers['content-disposition']
  let filename = `菜谱计划列表_${new Date().toISOString().slice(0, 10)}.xlsx`
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

/** 下载菜谱计划导入模板 */
export const downloadPlanImportTemplate = async (): Promise<void> => {
  const response = await service.get('/v1/recipe/plans/import/template', { responseType: 'blob' })
  const contentDisposition = response.headers['content-disposition']
  let filename = '菜谱计划导入模板.xlsx'
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

/** 导入菜谱计划 */
export const importPlans = (file: File): Promise<ApiResponse<PlanImportResult>> => {
  const formData = new FormData()
  formData.append('file', file)
  return post('/v1/recipe/plans/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    silentError: true
  })
}

/** 驳回后重新提交菜谱计划 */
export const resubmitPlan = (id: number, data: Partial<RecipePlanForm>): Promise<ApiResponse<{ id: number; status: string }>> => {
  return put(`/v1/recipe/plans/${id}/resubmit`, data)
}

/** 获取菜谱计划审批历史 */
export const getPlanAuditLog = (id: number): Promise<ApiResponse<RecipePlanAuditLogItem[]>> => {
  return get(`/v1/recipe/plans/${id}/audit-log`)
}

/** 下载菜谱计划导入错误文件 */
export const downloadPlanImportErrorFile = async (fileName: string): Promise<void> => {
  const response = await service.get('/v1/recipe/plans/import/errors/' + fileName, { responseType: 'blob' })
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

/** 复制菜谱计划 */
export const copyPlan = (id: number): Promise<ApiResponse<CopyPlanResult>> => {
  return post(`/v1/recipe/plans/${id}/copy`)
}

/** 撤回已审核的菜谱计划 */
export const withdrawPlan = (id: number, reason: string): Promise<ApiResponse<{ id: number; status: string }>> => {
  return put(`/v1/recipe/plans/${id}/withdraw`, { reason })
}

/** 批量删除菜谱计划 */
export const batchDeletePlans = (planIds: number[]): Promise<ApiResponse<BatchOperationResult>> => {
  return post('/v1/recipe/plans/batch-delete', { planIds })
}

/** 批量审核菜谱计划 */
export const batchAuditPlans = (planIds: number[], status: string, remark?: string): Promise<ApiResponse<BatchOperationResult>> => {
  return put('/v1/recipe/plans/batch-audit', { planIds, status, remark })
}
