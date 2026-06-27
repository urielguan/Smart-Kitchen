import { get, post, put, del } from '../index'
import service from '../index'
import type {
  ApiResponse,
  PageResponse,
} from '@/types/api'
import type {
  OutboundImportResult,
  OutboundImportTask,
  OutboundOrder,
  OutboundOrderForm,
  OutboundOrderQuery,
  OutboundSuggestionPreviewRequest,
  OutboundSuggestionPreviewResult,
  OutboundSuggestionRevalidateRequest,
  OutboundSuggestionRevalidateResult,
  OutboundOrderStatistics,
  OutboundSourceOrderOption,
  OutboundTypeDictionaryOption,
} from '@/types/outbound'

const buildQuery = (params?: Record<string, any>) => {
  const query = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })
  return query.toString()
}

const BASE = '/v1/wms/outbound-orders'

const extractFilename = (url: string, contentDisposition?: string): string => {
  let filename = decodeURIComponent(url.split('/').pop() || '附件')
  if (!contentDisposition) return filename

  const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
  if (filenameMatch) {
    filename = decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
  }
  return filename
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

export const outboundApi = {
  /** 获取出库单列表 */
  getList(params: OutboundOrderQuery): Promise<ApiResponse<PageResponse<OutboundOrder>>> {
    return get(BASE, params)
  },

  /** 获取统计数据 */
  getStatistics(): Promise<ApiResponse<OutboundOrderStatistics>> {
    return get(`${BASE}/statistics`)
  },

  /** 获取出库单详情 */
  getDetail(id: number): Promise<ApiResponse<OutboundOrder>> {
    return get(`${BASE}/${id}`)
  },

  previewSuggestions(data: OutboundSuggestionPreviewRequest): Promise<ApiResponse<OutboundSuggestionPreviewResult>> {
    return post(`${BASE}/suggestions/preview`, data)
  },

  revalidateSuggestions(data: OutboundSuggestionRevalidateRequest): Promise<ApiResponse<OutboundSuggestionRevalidateResult>> {
    return post(`${BASE}/suggestions/revalidate`, data)
  },

  /** 获取出库类型字典 */
  getOutboundTypeOptions(): Promise<ApiResponse<OutboundTypeDictionaryOption[]>> {
    return get(`${BASE}/type-options`)
  },

  /** 获取来源单号候选 */
  getSourceOrderOptions(outboundType: string): Promise<ApiResponse<OutboundSourceOrderOption[]>> {
    return get(`${BASE}/source-order-options`, { outboundType })
  },

  downloadImportTemplate(): Promise<void> {
    return downloadFile(`${BASE}/import/template`, 'outbound-import-template.xlsx')
  },

  importOrders(file: File): Promise<ApiResponse<OutboundImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/import`, formData)
  },

  getImportTask(taskNo: string): Promise<ApiResponse<OutboundImportTask>> {
    return get(`${BASE}/import/tasks/${taskNo}`)
  },

  resumeImportTask(taskNo: string): Promise<ApiResponse<OutboundImportTask>> {
    return post(`${BASE}/import/tasks/${taskNo}/resume`, {})
  },

  terminateImportTask(taskNo: string): Promise<ApiResponse<OutboundImportTask>> {
    return post(`${BASE}/import/tasks/${taskNo}/terminate`, {})
  },

  downloadImportErrorFile(fileName: string): Promise<void> {
    return downloadFile(`${BASE}/import/errors/${fileName}`, fileName || 'outbound-import-errors.xlsx')
  },

  exportList(params: OutboundOrderQuery): Promise<void> {
    return downloadFile(`${BASE}/export`, 'outbound-orders.xlsx', params)
  },

  exportDetails(params: OutboundOrderQuery): Promise<void> {
    return downloadFile(`${BASE}/export/details`, 'outbound-order-details.xlsx', params)
  },

  /** 创建出库单 */
  create(data: OutboundOrderForm): Promise<ApiResponse<number>> {
    return post(BASE, data)
  },

  /** 更新出库单 */
  update(id: number, data: Partial<OutboundOrderForm>): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, data)
  },

  /** 删除出库单 */
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`)
  },

  /** 提交出库单 */
  submit(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/submit`, {})
  },

  /** 审核通过 */
  approve(id: number, approveRemark?: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/approve`, { approveRemark })
  },

  /** 审核驳回 */
  reject(id: number, rejectReason: string): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/reject`, { rejectReason })
  },

  /** 撤回 */
  withdraw(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/withdraw`, {})
  },

  /** 执行出库 */
  execute(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/execute`, {})
  },

  /** 反审核 */
  reverse(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/reverse`, {})
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
}

export default outboundApi
