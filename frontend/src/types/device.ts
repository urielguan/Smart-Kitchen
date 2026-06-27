export interface Device {
  id: number
  deviceCode: string
  deviceName: string
  deviceType: string
  deviceTypeName: string
  deviceModel: string
  manufacturer: string
  locationDesc: string
  onlineStatus: string
  onlineStatusName: string
  status: string
  statusName: string
  managerName: string
  managerPhone: string
  orgId?: number
  orgName?: string
  lastHeartbeatAt: string
  updatedAt: string
  typeSpecificSummary: string
}

export interface DeviceDetail extends Device {
  sn: string
  macAddress: string
  ipAddress: string
  managerId: number
  orgId: number
  orgName?: string
  installDate: string
  warrantyExpiresAt: string
  maintenanceCycleDays: number
  lastMaintenanceAt: string
  nextMaintenanceAt: string
  sipConfig: Record<string, any>
  configParams: Record<string, any>
  position3d: { x: number; y: number; z: number }
  model3dUrl: string
  model3dScale: number
  remark: string
  createdBy: number
  createdAt: string
  updatedBy: number
}

export interface DeviceForm {
  deviceCode: string
  deviceName: string
  deviceType: string
  status?: string
  statusChangeReason?: string
  deviceModel?: string
  manufacturer?: string
  sn?: string
  macAddress?: string
  ipAddress?: string
  locationDesc?: string
  sipConfig?: string
  configParams?: string
  installDate?: string
  warrantyExpiresAt?: string
  maintenanceCycleDays?: number
  nextMaintenanceAt?: string
  managerId?: number
  managerName?: string
  managerPhone?: string
  orgId: number
  remark?: string
}

export interface DeviceQuery {
  pageNum?: number
  pageSize?: number
  deviceType?: string
  onlineStatus?: string
  status?: string
  deviceName?: string
  orgId?: number
}

export interface DeviceStatistics {
  totalCount: number
  onlineCount: number
  offlineCount: number
  alertCount: number
  maintenanceCount: number
  deviceTypeStats: DeviceTypeStat[]
}

export interface DeviceTypeStat {
  deviceType: string
  deviceTypeName: string
  count: number
  onlineCount: number
  offlineCount: number
}

export interface DeviceOnlineStatusUpdatePayload {
  onlineStatus: string
  reason?: string
  sourceType?: 'system' | 'manual'
}

export interface DeviceStatusLog {
  statusType: string
  statusTypeName: string
  fromStatus?: string
  fromStatusName?: string
  toStatus?: string
  toStatusName?: string
  sourceType?: string
  sourceTypeName?: string
  reason?: string
  operatorId?: number
  operatorName?: string
  result?: string
  createdAt: string
}

/** 设备数据采集日志 */
export interface DataLog {
  id: number
  deviceId: number
  deviceCode: string
  dataType: string
  dataTypeName: string
  dataValue: number | null
  dataUnit: string | null
  collectedAt: string
  createdAt: string
}

/** 数据日志查询参数 */
export interface DataLogQuery {
  pageNum?: number
  pageSize?: number
  deviceId: number
  dataType?: string
  startTime?: string
  endTime?: string
}

/** 设备批量操作单项结果 */
export interface DeviceBatchItemResult {
  id: number
  deviceCode?: string | null
  deviceName?: string | null
  success: boolean
  reason?: string
  errorCode?: string | null
}

/** 设备批量操作结果 */
export interface DeviceBatchOperationResult {
  totalCount: number
  successCount: number
  failCount: number
  skippedCount: number
  successIds: number[]
  failedItems: DeviceBatchItemResult[]
  skippedItems: DeviceBatchItemResult[]
}

/** 设备导入结果 */
export interface DeviceImportResult {
  total: number
  successCount: number
  failCount: number
  hasErrors: boolean
  errorFileBase64: string | null
  errorDetails: ImportErrorDetail[] | null
}

/** 导入错误详情 */
export interface ImportErrorDetail {
  rowNum: number
  deviceCode: string | null
  deviceName: string | null
  errorMessage: string
}
