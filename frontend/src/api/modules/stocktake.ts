import service, { get, post, put } from '../index'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  StocktakeApprovePayload,
  StocktakeOrderDetail,
  StocktakeOrderForm,
  StocktakeOrderListItem,
  StocktakeOrderQuery,
  StocktakeRejectPayload,
  StocktakeSnapshotPreviewItem,
  StocktakeSnapshotPreviewQuery,
  StocktakeStatistics,
  StocktakeVersionDetail,
  StocktakeVersionSummary,
  StocktakeVoidPayload,
} from '@/types/stocktake'

const BASE = '/v1/wms/stocktake-orders'

const appendQuery = (params?: Record<string, any>) => {
  const query = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (item !== undefined && item !== null && item !== '') {
          query.append(key, String(item))
        }
      })
      return
    }
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })
  return query.toString()
}

export const stocktakeApi = {
  getList(params: StocktakeOrderQuery): Promise<ApiResponse<PageResponse<StocktakeOrderListItem>>> {
    return get(BASE, params)
  },

  getStatistics(): Promise<ApiResponse<StocktakeStatistics>> {
    return get(`${BASE}/statistics`)
  },

  getDetail(id: number): Promise<ApiResponse<StocktakeOrderDetail>> {
    return get(`${BASE}/${id}`)
  },

  getVersions(id: number): Promise<ApiResponse<StocktakeVersionSummary[]>> {
    return get(`${BASE}/${id}/versions`)
  },

  getVersionDetail(id: number, versionNo: number): Promise<ApiResponse<StocktakeVersionDetail>> {
    return get(`${BASE}/${id}/versions/${versionNo}`)
  },

  previewSnapshot(params: StocktakeSnapshotPreviewQuery): Promise<ApiResponse<StocktakeSnapshotPreviewItem[]>> {
    const query = appendQuery(params as Record<string, any>)
    return get(`${BASE}/snapshot-preview${query ? `?${query}` : ''}`)
  },

  create(data: StocktakeOrderForm): Promise<ApiResponse<number>> {
    return post(BASE, data)
  },

  update(id: number, data: StocktakeOrderForm): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, data)
  },

  submit(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/submit`, {})
  },

  approve(id: number, data: StocktakeApprovePayload): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/approve`, data)
  },

  reject(id: number, data: StocktakeRejectPayload): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/reject`, data)
  },

  voidOrder(id: number, data: StocktakeVoidPayload): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/void`, data)
  },

  refreshSnapshot(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/refresh-snapshot`, {})
  },

  uploadAttachments(id: number, files: File[]): Promise<ApiResponse<void>> {
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    return post(`${BASE}/${id}/attachments`, formData)
  },

  async downloadAttachment(id: number, url: string): Promise<void> {
    const response = await service.get(`${BASE}/${id}/attachments/download`, {
      params: { url },
      responseType: 'blob',
    })

    const contentDisposition = response.headers['content-disposition']
    let filename = decodeURIComponent(url.split('/').pop() || '附件')
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
      if (filenameMatch) {
        filename = decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
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

  async exportList(params: StocktakeOrderQuery): Promise<void> {
    const query = appendQuery(params)
    const url = `${BASE}/export${query ? `?${query}` : ''}`
    const response = await service.get(url, { responseType: 'blob' })

    const contentDisposition = response.headers['content-disposition']
    let filename = '盘点历史.xlsx'
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
      if (filenameMatch) {
        filename = decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
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
}

export default stocktakeApi
