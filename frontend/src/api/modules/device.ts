import service, { get, post, put, del } from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  Device,
  DeviceDetail,
  DeviceForm,
  DeviceOnlineStatusUpdatePayload,
  DeviceQuery,
  DeviceStatistics,
  DeviceStatusLog,
  DataLog,
  DataLogQuery,
  DeviceImportResult,
  DeviceBatchOperationResult,
} from '@/types/device'

const BASE = '/v1/device'

export const deviceApi = {
  /** 设备管理首页看板 */
  getDashboard(orgId?: number): Promise<ApiResponse<DeviceStatistics>> {
    return get(`${BASE}/dashboard`, { orgId })
  },

  /** 设备列表（分页） */
  getList(params: DeviceQuery): Promise<ApiResponse<PageResponse<Device>>> {
    return get(`${BASE}/list`, params)
  },

  /** 设备详情 */
  getDetail(id: number): Promise<ApiResponse<DeviceDetail>> {
    return get(`${BASE}/${id}`)
  },

  /** 设备状态履历 */
  getStatusLogs(id: number): Promise<ApiResponse<DeviceStatusLog[]>> {
    return get(`${BASE}/${id}/status-logs`)
  },

  /** 新增设备 */
  create(data: DeviceForm): Promise<ApiResponse<{ id: number }>> {
    return post(BASE, data)
  },

  /** 编辑设备 */
  update(id: number, data: Partial<DeviceForm>): Promise<ApiResponse<{ id: number }>> {
    return put(`${BASE}/${id}`, data)
  },

  /** 删除设备 */
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`, { silentError: true })
  },

  /** 批量删除设备 */
  batchDelete(ids: number[]): Promise<ApiResponse<DeviceBatchOperationResult>> {
    return post(`${BASE}/batch-delete`, { ids }, { silentError: true })
  },

  /** 更新设备在线状态 */
  updateOnlineStatus(id: number, data: DeviceOnlineStatusUpdatePayload): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/online-status`, data)
  },

  /** 切换设备启用/停用状态 */
  toggleStatus(id: number): Promise<ApiResponse<{ id: number }>> {
    return put(`${BASE}/${id}/toggle-status`, {}, { silentError: true })
  },

  /** 批量启用设备 */
  batchEnable(ids: number[]): Promise<ApiResponse<DeviceBatchOperationResult>> {
    return post(`${BASE}/batch-enable`, { ids }, { silentError: true })
  },

  /** 批量停用设备 */
  batchDisable(ids: number[]): Promise<ApiResponse<DeviceBatchOperationResult>> {
    return post(`${BASE}/batch-disable`, { ids }, { silentError: true })
  },

  /** 导出设备列表 */
  async exportDevices(params?: DeviceQuery): Promise<void> {
    const query = new URLSearchParams()
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          query.append(key, String(value))
        }
      })
    }
    const url = `${BASE}/export` + (query.toString() ? '?' + query.toString() : '')
    const response = await service.get(url, { responseType: 'blob' })
    const contentDisposition = response.headers?.['content-disposition'] || ''
    let fileName = '设备列表.xlsx'
    const match = contentDisposition.match(/filename\*=UTF-8''(.+)/)
    if (match) {
      fileName = decodeURIComponent(match[1])
    }
    const blob = new Blob([response.data as BlobPart])
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)
  },

  /** 批量导入设备 */
  importDevices(file: File): Promise<ApiResponse<DeviceImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/import`, formData, { silentError: true })
  },

  /** 下载导入模板 */
  downloadTemplate(): void {
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    window.open(`${baseUrl}${BASE}/import/template`, '_blank')
  },

  /** 获取设备数据采集日志 */
  getDataLogs(params: DataLogQuery): Promise<ApiResponse<PageResponse<DataLog>>> {
    return get(`${BASE}/data-logs`, params)
  },
}

export default deviceApi
