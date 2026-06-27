import { get, post } from '@/api'
import service from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  Alert,
  AlertQuery,
  AlertDashboard,
  AlertDispatchForm,
  AlertDispatchResult,
  AlertDispatch,
  AlertDispatchQuery,
  AlertProcessForm,
  AlertReviewDTO,
  AlertCloseDTO,
  AlertDispatchDetail,
} from '@/types/alert'

const BASE = '/v1/device/alerts'

export const alertApi = {
  /** 告警管理首页看板 */
  getDashboard(orgId?: number): Promise<ApiResponse<AlertDashboard>> {
    return get(`${BASE}/dashboard`, { orgId })
  },

  /** 告警列表（分页） */
  getList(params: AlertQuery): Promise<ApiResponse<PageResponse<Alert>>> {
    return get(BASE, params)
  },

  /** 导出告警列表 */
  async exportAlerts(params?: Partial<AlertQuery>): Promise<void> {
    const query = new URLSearchParams()
    if (params?.orgId) query.set('orgId', String(params.orgId))
    if (params?.alertType) query.set('alertType', params.alertType)
    if (params?.alertLevel) query.set('alertLevel', params.alertLevel)
    if (params?.status) query.set('status', params.status)
    if (params?.deviceId) query.set('deviceId', String(params.deviceId))
    if (params?.assignedTo) query.set('assignedTo', String(params.assignedTo))
    if (params?.startTime) query.set('startTime', params.startTime)
    if (params?.endTime) query.set('endTime', params.endTime)
    const url = BASE + '/export' + (query.toString() ? '?' + query.toString() : '')
    const response = await service.get(url, { responseType: 'blob' })
    const contentDisposition = response.headers['content-disposition']
    let filename = '告警数据导出.xlsx'
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
  },

  /** 告警详情（含派单信息和处理记录） */
  getDispatchDetailByAlertId(alertId: number): Promise<ApiResponse<AlertDispatchDetail>> {
    return get(`${BASE}/${alertId}/dispatch-detail`)
  },

  /** 派单工单详情 */
  getDispatchDetail(dispatchId: number): Promise<ApiResponse<AlertDispatchDetail>> {
    return get(`${BASE}/dispatches/${dispatchId}`)
  },

  /** 告警派单 */
  dispatch(id: number, data: AlertDispatchForm): Promise<ApiResponse<AlertDispatchResult>> {
    return post(`${BASE}/${id}/dispatch`, data)
  },

  /** 派单工单列表 */
  listDispatches(params: AlertDispatchQuery): Promise<ApiResponse<PageResponse<AlertDispatch>>> {
    return get(`${BASE}/dispatches`, params)
  },

  /** 处理工单 */
  processDispatch(dispatchId: number, data: AlertProcessForm): Promise<ApiResponse<void>> {
    return post(`${BASE}/dispatches/${dispatchId}/process`, data)
  },

  /** 复核工单 */
  reviewDispatch(dispatchId: number, data: AlertReviewDTO): Promise<ApiResponse<void>> {
    return post(`${BASE}/dispatches/${dispatchId}/review`, data)
  },

  /** 关闭告警 */
  close(id: number, data: AlertCloseDTO): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/close`, data)
  },

  /** 上传附件 */
  uploadAttachment(file: File): Promise<ApiResponse<{ fileUrl: string; fileName: string }>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/upload-attachment`, formData)
  },
}

export default alertApi
