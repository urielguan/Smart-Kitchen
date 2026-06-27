import service, { get, post, put, del } from '../index'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  Warehouse,
  WarehouseForm,
  WarehouseQuery,
  WarehouseStatistics,
  Location,
  LocationForm,
  LocationQuery,
  WarehouseExportFormat,
  WarehouseImportResult,
} from '@/types/warehouse'

const BASE = '/v1/wms/warehouses'
const LOC = '/v1/wms/locations'

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

const downloadFile = async (url: string, fallbackFileName: string, params?: Record<string, any>) => {
  const query = buildQuery(params)
  const response = await service.get(`${url}${query ? `?${query}` : ''}`, {
    responseType: 'blob'
  })
  const fileName = resolveDownloadFileName(response.headers['content-disposition'], fallbackFileName)
  triggerBlobDownload(response.data as BlobPart, fileName)
}

export const warehouseApi = {
  getList(params: WarehouseQuery): Promise<ApiResponse<PageResponse<Warehouse>>> {
    return get(BASE, params)
  },
  getStatistics(): Promise<ApiResponse<WarehouseStatistics>> {
    return get(`${BASE}/statistics`)
  },
  getDetail(id: number): Promise<ApiResponse<Warehouse>> {
    return get(`${BASE}/${id}`)
  },
  create(data: WarehouseForm): Promise<ApiResponse<number>> {
    return post(BASE, data)
  },
  update(id: number, data: Partial<WarehouseForm>): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, data)
  },
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`)
  },
  downloadWarehouseTemplate(): Promise<void> {
    return downloadFile(`${BASE}/import/template`, 'warehouse-import-template.xlsx')
  },
  importWarehouses(file: File): Promise<ApiResponse<WarehouseImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE}/import`, formData)
  },
  exportWarehouses(params: WarehouseQuery, format: WarehouseExportFormat): Promise<void> {
    return downloadFile(`${BASE}/export`, `warehouses.${format}`, { ...params, format })
  },
  downloadWarehouseImportErrorFile(fileName: string): Promise<void> {
    return downloadFile(`${BASE}/import/errors/${fileName}`, fileName || 'warehouse-import-errors.xlsx')
  },
  getLocations(params: LocationQuery): Promise<ApiResponse<PageResponse<Location>>> {
    return get(LOC, params)
  },
  createLocation(data: LocationForm): Promise<ApiResponse<number>> {
    return post(LOC, data)
  },
  updateLocation(id: number, data: Partial<LocationForm>): Promise<ApiResponse<void>> {
    return put(`${LOC}/${id}`, data)
  },
  deleteLocation(id: number): Promise<ApiResponse<void>> {
    return del(`${LOC}/${id}`)
  },
  downloadLocationTemplate(): Promise<void> {
    return downloadFile(`${LOC}/import/template`, 'location-import-template.xlsx')
  },
  importLocations(file: File): Promise<ApiResponse<WarehouseImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${LOC}/import`, formData)
  },
  exportLocations(params: WarehouseQuery, format: WarehouseExportFormat): Promise<void> {
    return downloadFile(`${LOC}/export`, `warehouse-locations.${format}`, { ...params, format })
  },
  downloadLocationImportErrorFile(fileName: string): Promise<void> {
    return downloadFile(`${LOC}/import/errors/${fileName}`, fileName || 'location-import-errors.xlsx')
  },

  // ==================== 传感器-仓位绑定（设备管理调用） ====================

  /** 获取所有仓位列表（绑定下拉用） */
  getAllLocationsForBinding() {
    return get<{ id: number; locationCode: string; locationName: string; warehouseId: number; warehouseName: string }[]>(`${LOC}/all-for-binding`)
  },

  /** 绑定/解绑传感器到仓位（locationId=null 表示解绑） */
  bindSensorToDevice(sensorDeviceId: number, locationId: number | null) {
    return put(`${LOC}/sensor-binding`, { sensorDeviceId, locationId })
  },

  /** 查询传感器当前绑定的仓位 */
  getLocationBySensor(sensorDeviceId: number) {
    return get<{ id: number; locationName: string; locationCode: string; warehouseId?: number; warehouseName?: string } | null>(`${LOC}/by-sensor/${sensorDeviceId}`)
  },
}

export default warehouseApi
