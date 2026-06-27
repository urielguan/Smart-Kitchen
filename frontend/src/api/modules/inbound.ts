import service, { get, post, put, del } from '../index'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  InboundAreaValidationPreviewRequest,
  InboundAreaValidationPreviewResponse,
  InboundImportResult,
  InboundOrder,
  InboundOrderForm,
  InboundOrderQuery,
  InboundOrderStatistics,
  InboundSourceOrderOption,
} from '@/types/inbound'

const BASE = '/v1/wms/inbound-orders'

const buildQuery = (params?: Record<string, any>) => {
  const query = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })
  return query.toString()
}

const triggerBlobDownload = (blobPart: BlobPart, fileName: string) => {
  const blobUrl = URL.createObjectURL(new Blob([blobPart]))
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

const downloadFile = async (url: string, fallbackFileName: string, params?: Record<string, any>) => {
  const query = buildQuery(params)
  const response = await service.get(`${url}${query ? `?${query}` : ''}`, {
    responseType: 'blob',
  })
  const fileName = extractFilename(url, response.headers['content-disposition']) || fallbackFileName
  triggerBlobDownload(response.data as BlobPart, fileName)
}

const extractFilename = (url: string, contentDisposition?: string): string => {
  let filename = decodeURIComponent(url.split('/').pop() || '附件')
  if (!contentDisposition) return filename

  const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
  if (filenameMatch) {
    filename = decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
  }
  return filename
}

const openBlobInNewTab = (blob: Blob) => {
  const blobUrl = URL.createObjectURL(blob)
  const newWindow = window.open(blobUrl, '_blank', 'noopener,noreferrer')
  if (!newWindow) {
    URL.revokeObjectURL(blobUrl)
    throw new Error('PREVIEW_WINDOW_BLOCKED')
  }
  newWindow.addEventListener('beforeunload', () => URL.revokeObjectURL(blobUrl), { once: true })
}

const buildActionPayload = (version: number, idempotencyKey?: string) => ({
  version,
  idempotencyKey: idempotencyKey || `${Date.now()}`,
})

export const inboundApi = {
  getList(params: InboundOrderQuery): Promise<ApiResponse<PageResponse<InboundOrder>>> {
    return get(BASE, params)
  },
  getStatistics(): Promise<ApiResponse<InboundOrderStatistics>> {
    return get(`${BASE}/statistics`)
  },
  getDetail(id: number): Promise<ApiResponse<InboundOrder>> {
    return get(`${BASE}/${id}`)
  },
  getSourceOrderOptions(sourceType: string, excludeInboundOrderId?: number | null): Promise<ApiResponse<InboundSourceOrderOption[]>> {
    return get(`${BASE}/source-order-options`, { sourceType, excludeInboundOrderId })
  },
  getSourceOrderItems(purchaseOrderId: number, excludeInboundOrderId?: number | null): Promise<ApiResponse<any[]>> {
    return get(`${BASE}/source-order-items`, { purchaseOrderId, excludeInboundOrderId })
  },
  create(data: InboundOrderForm): Promise<ApiResponse<number>> {
    return post(BASE, data, { silentError: true })
  },
  previewAreaValidation(data: InboundAreaValidationPreviewRequest): Promise<ApiResponse<InboundAreaValidationPreviewResponse>> {
    return post(`${BASE}/area-validation/preview`, data)
  },
  downloadImportTemplate(): Promise<void> {
    return downloadFile(`${BASE}/import/template`, 'inbound-import-template.xlsx')
  },
  importOrders(file: File): Promise<ApiResponse<InboundImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/import`, formData)
  },
  downloadImportErrorFile(fileName: string): Promise<void> {
    return downloadFile(`${BASE}/import/errors/${fileName}`, fileName || 'inbound-import-errors.xlsx')
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

    const filename = extractFilename(url, response.headers['content-disposition'])
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
  async previewAttachment(id: number, url: string): Promise<void> {
    const response = await service.get(`${BASE}/${id}/attachments/preview`, {
      params: { url },
      responseType: 'blob',
    })

    const contentType = response.headers['content-type'] || 'application/octet-stream'
    const blob = new Blob([response.data as BlobPart], { type: contentType })
    openBlobInNewTab(blob)
  },
  getAttachmentPreviewUrl(id: number, url: string): string {
    const query = new URLSearchParams({ url })
    return `${BASE}/${id}/attachments/preview?${query.toString()}`
  },
  update(id: number, data: Partial<InboundOrderForm>): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, data, { silentError: true })
  },
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`)
  },
  submit(id: number, version: number, idempotencyKey?: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/submit`, buildActionPayload(version, idempotencyKey), { silentError: true })
  },
  approve(id: number, version: number, idempotencyKey?: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/approve`, buildActionPayload(version, idempotencyKey), { silentError: true })
  },
  postApproved(id: number, version: number, idempotencyKey?: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/post`, buildActionPayload(version, idempotencyKey), { silentError: true })
  },
  unapprove(id: number, version: number, idempotencyKey?: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/unapprove`, buildActionPayload(version, idempotencyKey), { silentError: true })
  },
  retryPost(id: number, version: number, idempotencyKey?: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/retry-post`, buildActionPayload(version, idempotencyKey), { silentError: true })
  },
  reject(id: number, approveRemark: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/reject`, { approveRemark }, { silentError: true })
  },
  cancel(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/cancel`, {})
  },
}

export default inboundApi
