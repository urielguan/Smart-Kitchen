import { get } from '../index'
import service from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'

const BASE = '/v1/wms/inventory'

export interface BatchNoOption {
  batch_no: string
  quantity: number
  unit_cost: number
  expiry_date: string | null
}

export interface InventoryOverviewQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  categoryName?: string
  warehouseId?: number
  locationId?: number
  stockStatus?: string
  shelfLifeLevel?: string
  materialStatus?: string
}

export interface InventoryOverviewItem {
  materialId: number
  materialCode: string
  materialName: string
  categoryName: string
  materialSpec: string
  unit: string
  imageUrl?: string
  warehouseName: string
  locationName: string
  currentStock: number
  minStock?: number
  maxStock?: number
  stockRange: string
  latestBatchNo?: string
  latestProductionDate?: string | null
  shelfLifeDays?: number
  minRemainingDays?: number | null
  stockStatus: string
  shelfLifeLevel: string
  updatedAt?: string
}

export interface InventoryDistributionItem {
  warehouseId?: number
  warehouseName?: string
  locationId?: number
  locationName?: string
  batchNo?: string
  quantity: number
  productionDate?: string | null
  expiryDate?: string | null
  remainingDays?: number | null
}

export interface InventoryShelfLifeSummary {
  materialId: number
  normalQty: number
  warningQty: number
  nearExpiryQty: number
  expiredQty: number
  totalQty: number
  configStatus?: 'ok' | 'missing'
}

export interface InventoryMovementQuery {
  pageNum: number
  pageSize: number
  startDate?: string
  endDate?: string
  bizType?: string
  documentNo?: string
}

export interface InventoryMovementItem {
  bizType: string
  documentNo: string
  operationType: string
  materialId: number
  materialName: string
  spec?: string
  quantity: number
  unit?: string
  postOperationStockQty?: number
  warehouseName?: string
  locationName?: string
  operatorName?: string
  operationTime?: string
}

const parseBlobError = async (blob: Blob, fallbackMessage: string) => {
  const text = (await blob.text()).trim()
  if (!text) {
    return fallbackMessage
  }

  try {
    const payload = JSON.parse(text) as { message?: string }
    if (typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message.trim()
    }
  } catch {
    // ignore invalid json payloads and fall back to raw text below
  }

  return text
}

const ensureBlobDownloadable = async (response: any, fallbackMessage: string) => {
  const contentType = String(response.headers?.['content-type'] || '').toLowerCase()
  if (contentType.includes('application/json') || contentType.includes('text/plain')) {
    const blob = response.data instanceof Blob ? response.data : new Blob([response.data as BlobPart])
    const message = await parseBlobError(blob, fallbackMessage)
    throw new Error(message)
  }
}

const downloadBlob = (response: any, defaultName: string) => {
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

export const inventoryApi = {
  /** 获取可用的批次号列表 */
  getAvailableBatchNos(params: {
    materialId: number
    spec?: string
    warehouseId: number
    locationId?: number
  }): Promise<ApiResponse<BatchNoOption[]>> {
    return get(`${BASE}/batch-nos`, params)
  },

  getOverview(params: InventoryOverviewQuery): Promise<ApiResponse<PageResponse<InventoryOverviewItem>>> {
    return get(`${BASE}/overview`, params)
  },

  getDistribution(materialId: number): Promise<ApiResponse<InventoryDistributionItem[]>> {
    return get(`${BASE}/${materialId}/distribution`)
  },

  getShelfLifeSummary(materialId: number): Promise<ApiResponse<InventoryShelfLifeSummary>> {
    return get(`${BASE}/${materialId}/shelf-life-summary`)
  },

  getMovements(materialId: number, params: InventoryMovementQuery): Promise<ApiResponse<PageResponse<InventoryMovementItem>>> {
    return get(`${BASE}/${materialId}/movements`, params)
  },

  async exportOverview(params: InventoryOverviewQuery): Promise<void> {
    const response = await service.get(`${BASE}/export`, { params, responseType: 'blob' })
    await ensureBlobDownloadable(response, '导出失败，请稍后重试')
    downloadBlob(response, '库存总览.xlsx')
  },

  async exportMovements(materialId: number, params: InventoryMovementQuery): Promise<void> {
    const response = await service.get(`${BASE}/${materialId}/movements/export`, { params, responseType: 'blob' })
    await ensureBlobDownloadable(response, '导出失败，请稍后重试')
    downloadBlob(response, '出入库明细.xlsx')
  }
}

export default inventoryApi
