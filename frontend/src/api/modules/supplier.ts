import service, { del, get, post, put } from '../index'
import type { RequestConfig } from '../index'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  SupplierCancelPayload,
  SupplierDuplicateCheckResult,
  SupplierDisablePayload,
  Supplier,
  SupplierForm,
  SupplierImportResult,
  SupplierImportValidationResult,
  SupplierQualificationFile,
  SupplierQuery,
  SupplierStatistics,
  SupplierStatus
} from '@/types/supplier'

const BASE = '/v1/scm/suppliers'

interface SupplierListQuery extends SupplierQuery {
  pageNum?: number
  pageSize?: number
  status?: SupplierStatus | 'approved' | ''
}

interface SupplierAuditForm {
  status: Extract<SupplierStatus, 'active' | 'rejected'>
  remark?: string
}

const buildQuery = (params?: Record<string, any>) => {
  const query = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })
  return query.toString()
}

const resolveDownloadFileName = (contentDisposition?: string, fallback = '附件') => {
  if (!contentDisposition) {
    return fallback
  }
  const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
  if (!filenameMatch) {
    return fallback
  }
  return decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
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

export const supplierApi = {
  /** 获取供应商列表（分页） */
  getList(params: SupplierListQuery): Promise<ApiResponse<PageResponse<Supplier>>> {
    return get(BASE, params)
  },

  /** 获取供应商统计数据 */
  getStatistics(): Promise<ApiResponse<SupplierStatistics>> {
    return get(`${BASE}/statistics`)
  },

  /** 获取供应商详情 */
  getDetail(id: number): Promise<ApiResponse<Supplier>> {
    return get(`${BASE}/${id}`)
  },

  /** 上传供应商资质文件 */
  uploadQualificationFile(file: File): Promise<ApiResponse<SupplierQualificationFile>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/files/upload`, formData)
  },

  /** 删除供应商资质文件 */
  deleteQualificationFile(fileUrl: string, fileName?: string): Promise<ApiResponse<void>> {
    return del(`${BASE}/files`, {
      fileUrl,
      fileName: fileName || undefined
    })
  },

  /** 下载供应商资质文件 */
  async downloadQualificationFile(fileUrl: string, fileName?: string): Promise<void> {
    const response = await service.get(`${BASE}/files/download`, {
      params: {
        fileUrl,
        fileName: fileName || undefined
      },
      responseType: 'blob'
    })

    const contentDisposition = response.headers['content-disposition']
    let downloadName = fileName || 'attachment'
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
      if (filenameMatch) {
        downloadName = decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
      }
    }

    triggerBlobDownload(response.data as BlobPart, downloadName)
  },

  /** 下载供应商导入模板 */
  async downloadTemplate(): Promise<void> {
    const response = await service.get(`${BASE}/import/template`, {
      responseType: 'blob'
    })
    const fileName = resolveDownloadFileName(response.headers['content-disposition'], '供应商导入模板.xlsx')
    triggerBlobDownload(response.data as BlobPart, fileName)
  },

  /** 导入前校验供应商文件 */
  validateImportFile(file: File): Promise<ApiResponse<SupplierImportValidationResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/import/validate`, formData, { silentError: true })
  },

  /** 校验供应商编码/名称/证件号是否重复 */
  checkDuplicate(
    params: {
      excludeId?: number
      supplierCode?: string
      supplierName?: string
      licenseNo?: string
      foodLicenseNo?: string
    },
    config?: RequestConfig
  ): Promise<ApiResponse<SupplierDuplicateCheckResult>> {
    return get(`${BASE}/check-duplicate`, params, config)
  },

  /** 导入供应商 */
  importSuppliers(file: File): Promise<ApiResponse<SupplierImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/import`, formData, { silentError: true })
  },

  /** 导出供应商 */
  async exportSuppliers(params?: SupplierQuery): Promise<void> {
    const query = buildQuery(params)
    const url = `${BASE}/export${query ? `?${query}` : ''}`
    const response = await service.get(url, {
      responseType: 'blob'
    })
    const fileName = resolveDownloadFileName(response.headers['content-disposition'], '供应商信息.xlsx')
    triggerBlobDownload(response.data as BlobPart, fileName)
  },

  /** 下载供应商导入错误文件 */
  async downloadImportErrorFile(fileName: string): Promise<void> {
    const response = await service.get(`${BASE}/import/errors/${fileName}`, {
      responseType: 'blob'
    })
    const resolvedFileName = resolveDownloadFileName(
      response.headers['content-disposition'],
      fileName || '供应商导入错误文件.xlsx'
    )
    triggerBlobDownload(response.data as BlobPart, resolvedFileName)
  },

  /** 新增供应商 */
  create(data: SupplierForm): Promise<ApiResponse<number>> {
    return post(BASE, data)
  },

  /** 编辑供应商 */
  update(id: number, data: SupplierForm): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, data)
  },

  /** 审核供应商 */
  audit(id: number, data: SupplierAuditForm): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/audit`, data)
  },

  /** 禁用供应商 */
  disable(id: number, data: SupplierDisablePayload): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/disable`, data)
  },

  /** 启用供应商 */
  enable(id: number): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/enable`)
  },

  /** 注销供应商 */
  cancel(id: number, data: SupplierCancelPayload): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/cancel`, data)
  },

  /** 删除供应商 */
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`)
  }
}

export default supplierApi
