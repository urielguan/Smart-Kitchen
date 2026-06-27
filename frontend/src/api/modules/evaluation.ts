import { get, post } from '../index'
import service from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  MealReview, ReviewQuery, ReviewStatistics, CreateReviewForm, ReviewReplyForm,
  Complaint, ComplaintQuery, ComplaintStatistics, CreateComplaintForm,
  DispatchRecord, DispatchQuery, DispatchForm, ProcessWorkOrderForm,
  WorkOrderRecord, HandlerOption, ScoreDistribution, HotTag, PointsRanking
} from '@/types/evaluation'

// ==================== 评价相关 API ====================
const REVIEW_BASE_URL = '/v1/sys/reviews'

/** 从 Axios 响应中提取文件名并触发 Blob 下载 */
function downloadBlob(response: any, defaultName: string) {
  const contentDisposition = response.headers['content-disposition']
  let filename = defaultName
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
    if (match) {
      filename = decodeURIComponent(match[1].replace(/['"]/g, ''))
    }
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

export const reviewApi = {
  /** 获取评价统计 */
  getStatistics(params?: { orgId?: number; dateRange?: string }): Promise<ApiResponse<ReviewStatistics>> {
    return get(`${REVIEW_BASE_URL}/statistics`, params)
  },

  /** 获取评价列表（分页） */
  getList(params: ReviewQuery): Promise<ApiResponse<PageResponse<MealReview>>> {
    return get(REVIEW_BASE_URL, params)
  },

  /** 获取评价详情 */
  getDetail(id: number): Promise<ApiResponse<MealReview>> {
    return get(`${REVIEW_BASE_URL}/${id}`)
  },

  /** 新增评价 */
  create(data: CreateReviewForm): Promise<ApiResponse<{ id: number; reviewNo: string; points: number }>> {
    return post(REVIEW_BASE_URL, data)
  },

  /** 回复评价 */
  reply(id: number, data: ReviewReplyForm): Promise<ApiResponse<{ id: number; replyByName: string; replyAt: string }>> {
    return post(`${REVIEW_BASE_URL}/${id}/reply`, data)
  },

  /** 导出评价 */
  async exportList(params: Partial<ReviewQuery>): Promise<void> {
    const response = await service.get(REVIEW_BASE_URL + '/export', {
      params,
      responseType: 'blob'
    })
    downloadBlob(response, '评价数据导出.xlsx')
  },

  /** 获取评分分布 */
  getScoreDistribution(params?: { orgId?: number; dateRange?: string }): Promise<ApiResponse<ScoreDistribution[]>> {
    return get(`${REVIEW_BASE_URL}/score-distribution`, params)
  },

  /** 获取热门标签 */
  getHotTags(params?: { orgId?: number; dateRange?: string; limit?: number }): Promise<ApiResponse<HotTag[]>> {
    return get(`${REVIEW_BASE_URL}/hot-tags`, params)
  },

  /** 获取积分排行 */
  getPointsRanking(params?: { orgId?: number; dateRange?: string; limit?: number }): Promise<ApiResponse<PointsRanking[]>> {
    return get(`${REVIEW_BASE_URL}/points-ranking`, params)
  }
}

// ==================== 投诉相关 API ====================
const COMPLAINT_BASE_URL = '/v1/sys/complaints'

export const complaintApi = {
  /** 获取投诉统计 */
  getStatistics(params?: { orgId?: number; dateRange?: string }): Promise<ApiResponse<ComplaintStatistics>> {
    return get(`${COMPLAINT_BASE_URL}/statistics`, params)
  },

  /** 获取投诉列表（分页） */
  getList(params: ComplaintQuery): Promise<ApiResponse<PageResponse<Complaint>>> {
    return get(COMPLAINT_BASE_URL, params)
  },

  /** 获取投诉详情 */
  getDetail(id: number): Promise<ApiResponse<Complaint>> {
    return get(`${COMPLAINT_BASE_URL}/${id}`)
  },

  /** 新增投诉 */
  create(data: CreateComplaintForm): Promise<ApiResponse<{ id: number; complaintNo: string; status: string }>> {
    return post(COMPLAINT_BASE_URL, data)
  },

  /** 派单（统一接口，支持自动/人工） */
  dispatch(id: number, data: DispatchForm): Promise<ApiResponse<{
    dispatchId: number
    dispatchNo: string
    complaintId: number
    complaintNo: string
    dispatchType: string
    handlerId: number
    handlerName: string
    deadline?: string
    status: string
  }>> {
    return post(`${COMPLAINT_BASE_URL}/${id}/dispatch`, data)
  },

  /** 评价满意度 */
  rateSatisfaction(id: number, satisfaction: string, satisfactionRemark?: string): Promise<ApiResponse<{
    id: number
    complaintNo: string
    satisfaction: string
    satisfactionName: string
  }>> {
    return post(`${COMPLAINT_BASE_URL}/${id}/satisfaction`, { satisfaction, satisfactionRemark })
  },

  /** 导出投诉 */
  async exportList(params: Partial<ComplaintQuery>): Promise<void> {
    const response = await service.get(COMPLAINT_BASE_URL + '/export', {
      params,
      responseType: 'blob'
    })
    downloadBlob(response, '投诉数据导出.xlsx')
  }
}

// ==================== 派单相关 API ====================
const DISPATCH_BASE_URL = '/v1/sys/dispatches'

export const dispatchApi = {
  /** 获取派单列表（分页） */
  getList(params: DispatchQuery): Promise<ApiResponse<PageResponse<DispatchRecord>>> {
    return get(DISPATCH_BASE_URL, params)
  },

  /** 获取派单详情 */
  getDetail(id: number): Promise<ApiResponse<DispatchRecord>> {
    return get(`${DISPATCH_BASE_URL}/${id}`)
  },

  /** 获取处理记录列表 */
  getRecords(id: number): Promise<ApiResponse<{ total: number; list: WorkOrderRecord[] }>> {
    return get(`${DISPATCH_BASE_URL}/${id}/records`)
  },

  /** 处理工单 */
  process(id: number, data: ProcessWorkOrderForm): Promise<ApiResponse<{
    dispatchId: number
    dispatchNo: string
    status: string
    complaintId: number
    complaintStatus: string
    completedAt?: string
  }>> {
    return post(`${DISPATCH_BASE_URL}/${id}/process`, data)
  }
}

// ==================== 通用 API ====================
const COMMON_BASE_URL = '/v1/sys'

export const commonApi = {
  /** 获取处理人列表 */
  getHandlers(params?: { orgId?: number }): Promise<ApiResponse<HandlerOption[]>> {
    return get(`${COMMON_BASE_URL}/handlers`, params)
  }
}

// 兼容旧代码的导出
export const evaluationApi = {
  // 评价相关
  ...reviewApi,
  // 投诉相关
  ...complaintApi,
  // 派单相关
  ...dispatchApi,
  // 通用
  ...commonApi
}

export default evaluationApi
