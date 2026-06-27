export interface Warehouse {
  id: number
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  warehouseTypeName?: string
  capacity: number
  capacityUnit: string
  address: string
  managerName: string
  managerPhone: string
  status: 'active' | 'inactive' | 'maintenance' | 'archived'
  remark?: string
  version: number
  positionTotal: number
  positionUsed: number
  positionIdle: number
  temperatureMin?: number
  temperatureMax?: number
  humidityMin?: number
  humidityMax?: number
  currentTemperature?: number
  currentHumidity?: number
  tempStatus?: 'normal' | 'warning' | 'alarm'
  humidityStatus?: 'normal' | 'warning' | 'alarm'
  locationSensorData?: LocationSensorData[]
  createdAt: string
  updatedAt: string
}

export interface LocationSensorData {
  locationId: number
  locationName: string
  currentTemperature?: number
  currentHumidity?: number
  tempStatus: 'normal' | 'warning' | 'alarm'
  humidityStatus: 'normal' | 'warning' | 'alarm'
  dataCollectedAt?: string
}

export interface WarehouseForm {
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  capacity?: number
  capacityUnit?: string
  address?: string
  managerName?: string
  managerPhone?: string
  status?: string
  remark?: string
  version?: number
  temperatureMin?: number
  temperatureMax?: number
  humidityMin?: number
  humidityMax?: number
}

export interface WarehouseQuery {
  pageNum?: number
  pageSize?: number
  warehouseName?: string
  warehouseCode?: string
  warehouseType?: string
  status?: string
  orgId?: number
}

export type WarehouseImportTarget = 'warehouse' | 'location'
export type WarehouseExportFormat = 'xlsx' | 'csv'

export interface WarehouseImportResultError {
  rowNumber: number
  field: string
  reason: string
}

export interface WarehouseImportResult {
  successCount: number
  failureCount: number
  totalCount: number
  partialSuccess: boolean
  errors: WarehouseImportResultError[]
  errorFileName?: string
}

export interface WarehouseStatistics {
  warehouseTotal: number
  activeCount: number
  maintenanceCount: number
  positionTotal: number
}

export interface Location {
  id: number
  locationCode: string
  locationName: string
  locationType?: string
  regionCode?: string
  shelfCode?: string
  slotCode?: string
  warehouseId: number
  capacity?: number
  capacityUnit?: string
  usedCapacity: number
  temperatureMin?: number
  temperatureMax?: number
  humidityMin?: number
  humidityMax?: number
  sensorDeviceId?: number
  materialTypes?: string
  status: 'available' | 'occupied' | 'maintenance' | 'inactive' | 'archived'
  remark?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface LocationForm {
  warehouseId: number
  locationCode: string
  locationName: string
  locationType?: string
  regionCode?: string
  shelfCode?: string
  slotCode?: string
  capacity?: number
  capacityUnit?: string
  temperatureMin?: number
  temperatureMax?: number
  humidityMin?: number
  humidityMax?: number
  sensorDeviceId?: number
  materialTypes?: string
  status?: string
  remark?: string
  version?: number
}

export interface LocationQuery {
  warehouseId: number
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: string
}
