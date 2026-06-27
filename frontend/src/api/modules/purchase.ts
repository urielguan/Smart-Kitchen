import service, { del, get, post, put } from '../index'
import type { RequestConfig } from '../index'
import type { ApiResponse, PageResponse } from '@/types/api'
import { isDevPreviewMockEnabled } from '@/dev-preview/env'
import { devPreviewPurchaseApi } from '@/dev-preview/purchase'
import type {
  PurchaseOrderAttachment,
  PurchaseOrderAuditPayload,
  PurchaseOrderFormPayload,
  PurchaseOrderInspectionPayload,
  PurchaseOrderItem,
  PurchaseOrderLinkedInboundRecord,
  PurchaseOrderLogisticsPayload,
  PurchaseOrderMaterialOption,
  PurchaseOrderPlanItemOption,
  PurchaseOrderQuery,
  PurchaseOrderRecord,
  PurchaseOrderReverseAuditPayload,
  PurchaseOrderReverseAuditResult,
  PurchaseOrderSelectablePlan,
  PurchaseOrderSceneIntegrationLogs,
  PurchaseOrderSceneIntegrationMeta,
  PurchaseOrderSceneIntegrationSyncPayload,
  PurchaseOrderSceneIntegrationTriggerResult,
  PurchaseOrderStatistics,
  PurchaseOrderSupplierOption,
  PurchaseOrderTraceabilityPayload,
  PurchaseOrderVoidApplyPayload,
  PurchaseOrderVoidAuditPayload,
} from '@/types/purchase'

const BASE = '/v1/scm/purchase-orders'

const buildFormData = (data: PurchaseOrderFormPayload, file?: File | null) => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (file) {
    formData.append('file', file)
  }
  return formData
}

const buildMaintenanceFormData = <T extends object>(data: T, file?: File | null) => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (file) {
    formData.append('file', file)
  }
  return formData
}

const resolveDownloadFileName = (contentDisposition?: string, fallback = '采购订单附件') => {
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

export const purchaseApi = {
  getList(params: PurchaseOrderQuery): Promise<ApiResponse<PageResponse<PurchaseOrderRecord>>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getList(params)
    }
    return get(BASE, params)
  },

  getStatistics(orgId?: number): Promise<ApiResponse<PurchaseOrderStatistics>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getStatistics(orgId)
    }
    return get(`${BASE}/statistics`, orgId ? { orgId } : undefined)
  },

  getDetail(id: number): Promise<ApiResponse<PurchaseOrderRecord>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getDetail(id)
    }
    return get(`${BASE}/${id}`)
  },

  create(data: PurchaseOrderFormPayload, file?: File | null): Promise<ApiResponse<number>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.create(data, file)
    }
    return post(BASE, buildFormData(data, file))
  },

  update(id: number, data: PurchaseOrderFormPayload, file?: File | null): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.update(id, data, file)
    }
    return put(`${BASE}/${id}`, buildFormData(data, file))
  },

  audit(id: number, data: PurchaseOrderAuditPayload): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.audit(id, data)
    }
    return put(`${BASE}/${id}/audit`, data)
  },

  reverseAudit(id: number, data: PurchaseOrderReverseAuditPayload): Promise<ApiResponse<PurchaseOrderReverseAuditResult>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.reverseAudit(id, data)
    }
    return put(`${BASE}/${id}/reverse-audit`, data)
  },

  applyVoid(id: number, data: PurchaseOrderVoidApplyPayload): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.applyVoid(id, data)
    }
    return put(`${BASE}/${id}/void`, data)
  },

  auditVoid(id: number, data: PurchaseOrderVoidAuditPayload): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.auditVoid(id, data)
    }
    return put(`${BASE}/${id}/void-audit`, data)
  },

  updateLogistics(id: number, data: PurchaseOrderLogisticsPayload, file?: File | null): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.updateLogistics(id, data)
    }
    return put(`${BASE}/${id}/logistics`, buildMaintenanceFormData(data, file))
  },

  deleteLogisticsAttachment(id: number): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.deleteLogisticsAttachment(id)
    }
    return del(`${BASE}/${id}/logistics/attachment`)
  },

  updateInspection(id: number, data: PurchaseOrderInspectionPayload, file?: File | null): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.updateInspection(id, data)
    }
    return put(`${BASE}/${id}/inspection`, buildMaintenanceFormData(data, file))
  },

  deleteInspectionAttachment(id: number): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.deleteInspectionAttachment(id)
    }
    return del(`${BASE}/${id}/inspection/attachment`)
  },

  updateTraceability(id: number, data: PurchaseOrderTraceabilityPayload, file?: File | null): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.updateTraceability(id, data)
    }
    return put(`${BASE}/${id}/traceability`, buildMaintenanceFormData(data, file))
  },

  getSceneIntegrationMeta(
    scene: 'logistics' | 'inspection' | 'traceability',
    id: number,
    config?: RequestConfig,
  ): Promise<ApiResponse<PurchaseOrderSceneIntegrationMeta>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getSceneIntegrationMeta(scene, id)
    }
    return get(`${BASE}/${id}/${scene}/integration-meta`, undefined, config)
  },

  triggerSceneIntegrationSync(
    scene: 'logistics' | 'inspection' | 'traceability',
    id: number,
    data: PurchaseOrderSceneIntegrationSyncPayload,
  ): Promise<ApiResponse<PurchaseOrderSceneIntegrationTriggerResult>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.triggerSceneIntegrationSync(scene, id, data)
    }
    return post(`${BASE}/${id}/${scene}/integration-sync`, data)
  },

  getSceneIntegrationLogs(
    scene: 'logistics' | 'inspection' | 'traceability',
    id: number,
    config?: RequestConfig,
  ): Promise<ApiResponse<PurchaseOrderSceneIntegrationLogs>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getSceneIntegrationLogs(scene, id)
    }
    return get(`${BASE}/${id}/${scene}/integration-logs`, undefined, config)
  },

  deleteTraceabilityAttachment(id: number): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.deleteTraceabilityAttachment(id)
    }
    return del(`${BASE}/${id}/traceability/attachment`)
  },

  uploadAttachment(file: File): Promise<ApiResponse<PurchaseOrderAttachment>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.uploadAttachment(file)
    }
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/files/upload`, formData)
  },

  deleteAttachment(fileUrl: string, fileName?: string): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.deleteAttachment(fileUrl, fileName)
    }
    return del(`${BASE}/files`, {
      fileUrl,
      fileName: fileName || undefined,
    })
  },

  delete(id: number): Promise<ApiResponse<void>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.delete(id)
    }
    return del(`${BASE}/${id}`)
  },

  getItems(id: number): Promise<ApiResponse<PurchaseOrderItem[]>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getItems(id)
    }
    return get(`${BASE}/${id}/items`)
  },

  getLinkedInboundRecords(id: number): Promise<ApiResponse<PurchaseOrderLinkedInboundRecord[]>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getLinkedInboundRecords(id)
    }
    return get(`${BASE}/${id}/linked-inbound-records`)
  },

  getSupplierOptions(orgId?: number): Promise<ApiResponse<PurchaseOrderSupplierOption[]>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getSupplierOptions(orgId)
    }
    return get(`${BASE}/supplier-options`, orgId ? { orgId } : undefined)
  },

  getMaterialOptions(orgId?: number): Promise<ApiResponse<PurchaseOrderMaterialOption[]>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getMaterialOptions(orgId)
    }
    return get(`${BASE}/material-options`, orgId ? { orgId } : undefined)
  },

  getSelectablePlans(params?: {
    keyword?: string
    excludeOrderId?: number
  }): Promise<ApiResponse<PurchaseOrderSelectablePlan[]>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getSelectablePlans(params)
    }
    return get(`${BASE}/selectable-plans`, params)
  },

  getPlanItems(params: {
    planIds: string
    excludeOrderId?: number
  }): Promise<ApiResponse<PurchaseOrderPlanItemOption[]>> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.getPlanItems(params)
    }
    return get(`${BASE}/plan-items`, params)
  },

  async downloadAttachment(id: number): Promise<void> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.downloadAttachment(id)
    }
    const response = await service.get(`${BASE}/${id}/attachment/download`, {
      responseType: 'blob',
    })
    const filename = resolveDownloadFileName(response.headers['content-disposition'], '采购订单附件')
    triggerBlobDownload(response.data as BlobPart, filename)
  },

  async downloadLogisticsAttachment(id: number): Promise<void> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.downloadLogisticsAttachment(id)
    }
    const response = await service.get(`${BASE}/${id}/logistics/attachment/download`, {
      responseType: 'blob',
    })
    const filename = resolveDownloadFileName(response.headers['content-disposition'], '物流附件')
    triggerBlobDownload(response.data as BlobPart, filename)
  },

  async downloadInspectionAttachment(id: number): Promise<void> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.downloadInspectionAttachment(id)
    }
    const response = await service.get(`${BASE}/${id}/inspection/attachment/download`, {
      responseType: 'blob',
    })
    const filename = resolveDownloadFileName(response.headers['content-disposition'], '检测报告附件')
    triggerBlobDownload(response.data as BlobPart, filename)
  },

  async downloadTraceabilityAttachment(id: number): Promise<void> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.downloadTraceabilityAttachment(id)
    }
    const response = await service.get(`${BASE}/${id}/traceability/attachment/download`, {
      responseType: 'blob',
    })
    const filename = resolveDownloadFileName(response.headers['content-disposition'], '溯源附件')
    triggerBlobDownload(response.data as BlobPart, filename)
  },

  async downloadAttachmentByUrl(fileUrl: string, fileName?: string): Promise<void> {
    if (isDevPreviewMockEnabled()) {
      return devPreviewPurchaseApi.downloadAttachmentByUrl(fileUrl, fileName)
    }
    const response = await service.get(`${BASE}/files/download`, {
      params: {
        fileUrl,
        fileName: fileName || undefined,
      },
      responseType: 'blob',
    })
    const filename = resolveDownloadFileName(response.headers['content-disposition'], fileName || '采购订单附件')
    triggerBlobDownload(response.data as BlobPart, filename)
  },
}

export default purchaseApi
