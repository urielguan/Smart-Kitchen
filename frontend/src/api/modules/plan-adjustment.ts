import { get, post, put } from '@/api'
import service from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { RecipePlanAdjustment, RecipePlanAdjustmentDetail } from '@/types/plan'

/** 创建菜谱计划调整申请 */
export const createPlanAdjustment = (planId: number, data: {
  adjustReason: string
  adjustType: string
  afterData: string
}): Promise<ApiResponse<{ id: number; planId: number; adjustCode?: string; status: string }>> => {
  return post(`/v1/recipe/plans/${planId}/adjust`, data)
}

/** 审核菜谱计划调整申请 */
export const auditPlanAdjustment = (id: number, data: { status: string; remark?: string }): Promise<ApiResponse<null>> => {
  return put(`/v1/recipe/plan-adjustments/${id}/audit`, data)
}

/** 获取菜谱计划调整申请列表 */
export const getPlanAdjustmentList = (params: {
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

/** 获取菜谱计划调整申请详情 */
export const getPlanAdjustmentDetail = (id: number): Promise<ApiResponse<RecipePlanAdjustmentDetail>> => {
  return get(`/v1/recipe/plan-adjustments/${id}`)
}

/** 导出菜谱计划调整申请列表 */
export const exportAdjustments = async (params: Record<string, any>): Promise<void> => {
  const response = await service.get('/v1/recipe/plan-adjustments/export', { params, responseType: 'blob' })

  const contentDisposition = response.headers['content-disposition']
  let filename = `调整申请列表_${new Date().toISOString().slice(0, 10)}.xlsx`
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)/i)
    if (match) filename = decodeURIComponent(match[1].replace(/['"]/g, ''))
  }

  const blob = new Blob([response.data as BlobPart], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const blobUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  setTimeout(() => URL.revokeObjectURL(blobUrl), 5000)
}
