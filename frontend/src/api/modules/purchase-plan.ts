import service, { del, get, post, put } from '../index'
import type { RequestConfig } from '../index'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  PurchaseOrderGenerateResult,
  PurchasePlanAttachment,
  PurchasePlanAuditPayload,
  PurchasePlanFormPayload,
  PurchasePlanLinkedOrderRecord,
  PurchasePlanMaterialOption,
  PurchasePlanRecipeMaterialLinkage,
  PurchasePlanQuery,
  PurchasePlanRelatedDocumentItemPrefill,
  PurchasePlanRelatedDocumentOption,
  PurchasePlanRelatedDocumentType,
  PurchasePlanRecord,
  PurchasePlanReverseAuditPayload,
  PurchasePlanReverseAuditResult,
  PurchasePlanStatistics,
  PurchasePlanGenerateOrderPayload,
  PurchasePlanMergeGenerateOrderPayload,
  PurchasePlanVoidApplyPayload,
  PurchasePlanVoidAuditPayload,
  SelectablePurchasePlan,
} from '@/types/purchase-plan'

const BASE = '/v1/scm/purchase-plans'

const buildFormData = (data: PurchasePlanFormPayload, file?: File | null) => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (file) {
    formData.append('file', file)
  }
  return formData
}

const buildGenerateOrderFormData = (data: PurchasePlanGenerateOrderPayload, file?: File | null) => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (file) {
    formData.append('file', file)
  }
  return formData
}

const resolveDownloadFileName = (contentDisposition?: string, fallback = '采购计划附件') => {
  if (!contentDisposition) {
    return fallback
  }
  const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
  if (!filenameMatch) {
    return fallback
  }
  return decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
}

export const purchasePlanApi = {
  getList(params: PurchasePlanQuery): Promise<ApiResponse<PageResponse<PurchasePlanRecord>>> {
    return get(BASE, params)
  },

  getStatistics(orgId?: number): Promise<ApiResponse<PurchasePlanStatistics>> {
    return get(`${BASE}/statistics`, orgId ? { orgId } : undefined)
  },

  getDetail(id: number): Promise<ApiResponse<PurchasePlanRecord>> {
    return get(`${BASE}/${id}`)
  },

  getLinkedPurchaseOrders(
    id: number,
    config?: RequestConfig,
  ): Promise<ApiResponse<PurchasePlanLinkedOrderRecord[]>> {
    return get(`${BASE}/${id}/linked-purchase-orders`, undefined, config)
  },

  uploadAttachment(file: File): Promise<ApiResponse<PurchasePlanAttachment>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/files/upload`, formData)
  },

  deleteAttachment(fileUrl: string, fileName?: string): Promise<ApiResponse<void>> {
    return del(`${BASE}/files`, {
      fileUrl,
      fileName: fileName || undefined,
    })
  },

  create(data: PurchasePlanFormPayload, file?: File | null): Promise<ApiResponse<number>> {
    return post(BASE, buildFormData(data, file))
  },

  update(id: number, data: PurchasePlanFormPayload, file?: File | null): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, buildFormData(data, file))
  },

  audit(id: number, data: PurchasePlanAuditPayload): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/audit`, data)
  },

  reverseAudit(id: number, data: PurchasePlanReverseAuditPayload): Promise<ApiResponse<PurchasePlanReverseAuditResult>> {
    return put(`${BASE}/${id}/reverse-audit`, data)
  },

  applyVoid(id: number, data: PurchasePlanVoidApplyPayload): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/void`, data)
  },

  auditVoid(id: number, data: PurchasePlanVoidAuditPayload): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/void-audit`, data)
  },

  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`)
  },

  getMaterialOptions(orgId?: number): Promise<ApiResponse<PurchasePlanMaterialOption[]>> {
    return get(`${BASE}/material-options`, orgId ? { orgId } : undefined)
  },

  getRelatedDocuments(params?: {
    orgId?: number
    keyword?: string
  }): Promise<ApiResponse<PurchasePlanRelatedDocumentOption[]>> {
    return get(`${BASE}/related-documents`, params)
  },

  getRelatedDocumentItems(
    documentType: PurchasePlanRelatedDocumentType,
    documentId: number,
  ): Promise<ApiResponse<PurchasePlanRelatedDocumentItemPrefill[]>> {
    return get(`${BASE}/related-documents/${documentType}/${documentId}/items`)
  },

  getRecipePlanMaterialLinkage(
    documentId: number,
    excludePlanId?: number,
  ): Promise<ApiResponse<PurchasePlanRecipeMaterialLinkage>> {
    return get(`${BASE}/related-documents/recipePlan/${documentId}/material-linkage`, {
      excludePlanId: excludePlanId || undefined,
    })
  },

  generateOrders(
    id: number,
    data: PurchasePlanGenerateOrderPayload,
    file?: File | null,
  ): Promise<ApiResponse<PurchaseOrderGenerateResult[]>> {
    return post(`${BASE}/${id}/generate-orders`, buildGenerateOrderFormData(data, file))
  },

  mergeGenerateOrder(data: PurchasePlanMergeGenerateOrderPayload): Promise<ApiResponse<PurchaseOrderGenerateResult>> {
    return post(`${BASE}/merge-generate-order`, data)
  },

  getSelectableForOrders(params?: { orgId?: number; keyword?: string }): Promise<ApiResponse<SelectablePurchasePlan[]>> {
    return get(`${BASE}/selectable-for-orders`, params)
  },

  async downloadAttachmentByUrl(fileUrl: string, fileName?: string): Promise<void> {
    const response = await service.get(`${BASE}/files/download`, {
      params: {
        fileUrl,
        fileName: fileName || undefined,
      },
      responseType: 'blob',
    })
    const filename = resolveDownloadFileName(response.headers['content-disposition'], fileName || '采购计划附件')
    const blobUrl = URL.createObjectURL(new Blob([response.data as BlobPart]))
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)
  },

  async downloadAttachment(id: number): Promise<void> {
    const response = await service.get(`${BASE}/${id}/attachment/download`, {
      responseType: 'blob',
    })
    const filename = resolveDownloadFileName(response.headers['content-disposition'], '采购计划附件')
    const blobUrl = URL.createObjectURL(new Blob([response.data as BlobPart]))
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)
  },
}

export default purchasePlanApi
