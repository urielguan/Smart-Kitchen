import { get, post, put, del } from '@/api'
import service from '@/api'
import type {
  HealthDashboard,
  HealthCheckRecord,
  HealthCheckRecordDetail,
  HealthCheckQuery,
  HealthCheckCreatePayload,
  HealthCheckUpdatePayload,
  HealthCertificate,
  HealthCertificateQuery,
  HealthCertificatePayload,
  HealthCertificateDashboard,
  AiCheckPayload,
  AiCheckResult,
  HealthCheckLinkageVersion,
} from '@/types/health-check'
import type { ApiResponse, PageResponse } from '@/types'

const API_PREFIX = '/v1/health'

/**
 * 获取晨检看板数据
 */
export const getHealthDashboard = (params?: HealthCheckQuery) => {
  return get<ApiResponse<HealthDashboard>>(`${API_PREFIX}/dashboard`, params)
}

/**
 * 获取晨检联动版本
 */
export const getHealthCheckLinkageVersion = () => {
  return get<ApiResponse<HealthCheckLinkageVersion>>(`${API_PREFIX}/linkage/version`)
}

/**
 * 获取待晨检列表
 */
export const getPendingHealthChecks = (params?: HealthCheckQuery) => {
  return get<ApiResponse<PageResponse<HealthCheckRecord>>>(`${API_PREFIX}/records/pending`, params)
}

/**
 * 获取已完成/全部记录列表
 */
export const getHealthCheckRecords = (params?: HealthCheckQuery) => {
  return get<ApiResponse<PageResponse<HealthCheckRecord>>>(`${API_PREFIX}/records`, params)
}

/**
 * 执行晨检（手动）
 */
export const createHealthCheckRecord = (data: HealthCheckCreatePayload) => {
  return post<ApiResponse<HealthCheckRecordDetail>>(`${API_PREFIX}/records`, data)
}

/**
 * 生成今日待晨检任务（幂等，已有则不重复生成）
 */
export const generatePendingTasks = () => {
  return post<ApiResponse<string>>(`${API_PREFIX}/generate-pending`)
}

/**
 * 获取晨检记录详情
 */
export const getHealthCheckRecordDetail = (id: number) => {
  return get<ApiResponse<HealthCheckRecordDetail>>(`${API_PREFIX}/records/${id}`)
}

/**
 * 更新晨检记录
 */
export const updateHealthCheckRecord = (id: number, data: HealthCheckUpdatePayload) => {
  return put<ApiResponse<HealthCheckRecordDetail>>(`${API_PREFIX}/records/${id}`, data)
}

/**
 * 归档晨检记录
 */
export const archiveHealthCheckRecord = (id: number) => {
  return put<ApiResponse<HealthCheckRecordDetail>>(`${API_PREFIX}/records/${id}/archive`)
}

// ============ 健康证管理 API ============

const CERT_PREFIX = `${API_PREFIX}/certificate`

/**
 * 获取健康证看板数据
 */
export const getCertificateDashboard = () => {
  return get<ApiResponse<HealthCertificateDashboard>>(`${CERT_PREFIX}/dashboard`)
}

/**
 * 获取健康证分页列表
 */
export const getCertificatePage = (params?: HealthCertificateQuery) => {
  return get<ApiResponse<PageResponse<HealthCertificate>>>(`${CERT_PREFIX}/page`, params)
}

/**
 * 获取健康证详情
 */
export const getCertificateDetail = (id: number) => {
  return get<ApiResponse<HealthCertificate>>(`${CERT_PREFIX}/${id}`)
}

/**
 * 保存健康证（新增/更新）
 */
export const saveHealthCertificate = (data: HealthCertificatePayload) => {
  return post<ApiResponse<HealthCertificate>>(`${CERT_PREFIX}`, data)
}

/**
 * 获取员工健康证
 */
export const getCertificateByEmployeeId = (employeeId: number) => {
  return get<ApiResponse<HealthCertificate>>(`${CERT_PREFIX}/employee/${employeeId}`)
}

/**
 * 获取健康证列表
 */
export const getCertificateList = (params?: { status?: string }) => {
  return get<ApiResponse<HealthCertificate[]>>(`${CERT_PREFIX}/list`, params)
}

/**
 * 获取即将过期的健康证
 */
export const getExpiringCertificates = (withinDays = 30) => {
  return get<ApiResponse<HealthCertificate[]>>(`${CERT_PREFIX}/expiring`, { withinDays })
}

/**
 * 删除健康证
 */
export const deleteHealthCertificate = (id: number) => {
  return del<ApiResponse<boolean>>(`${CERT_PREFIX}/${id}`)
}

/**
 * 手动刷新健康证状态
 */
export const refreshCertificateStatus = () => {
  return post<ApiResponse<number>>(`${CERT_PREFIX}/refresh-status`)
}

/**
 * 上传健康证照片
 */
export const uploadCertificateImage = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return post<ApiResponse<{ imageUrl: string }>>(`${CERT_PREFIX}/upload-image`, formData)
}

/**
 * 导出健康证数据
 */
export const exportCertificates = async (params?: HealthCertificateQuery) => {
  const query = new URLSearchParams()
  if (params?.keyword) query.set('keyword', params.keyword)
  if (params?.status) query.set('status', params.status)
  if (params?.showLeftEmployees) query.set('showLeftEmployees', 'true')
  const url = CERT_PREFIX + '/export' + (query.toString() ? '?' + query.toString() : '')
  const response = await service.get(url, { responseType: 'blob' })
  // 提取文件名并下载
  const contentDisposition = response.headers['content-disposition']
  let filename = '健康证数据导出.xlsx'
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

// ============ AI一键晨检 API ============

/**
 * AI一键晨检
 * POST /api/v1/health/check-records/ai-check
 */
export const aiCheck = (data: AiCheckPayload) => {
  return post<ApiResponse<AiCheckResult>>(`${API_PREFIX}/check-records/ai-check`, data)
}
