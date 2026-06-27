import type { ApiResponse, PageQuery, PageResponse } from './api'

export type CookTaskStatus = 'pending' | 'in_progress' | 'completed' | 'cancelled' | 'archived' | string
export type CookAlertLevel = 'normal' | 'warning' | 'critical' | string
export type CookHandoffStatus = 'pending' | 'ready' | 'completed' | null
export type CookCollectionStatus = 'normal' | 'interrupted' | 'pending_recovery' | string | null
export type CookSyncStatus = 'normal' | 'sync_failed' | 'conflict_pending' | string | null
export type CookCompensationStatus = 'none' | 'pending' | 'resolved' | string | null

export interface CookTaskQuery extends Partial<PageQuery> {
  taskDate?: string
  planDate?: string
  mealType?: string
  status?: string
  chefName?: string
  keyword?: string
  orgId?: number | null
  deviceLocation?: string
  alertLevel?: string
}

export interface OrgOption {
  id: number
  name: string
  code?: string
}

export interface CookDashboard {
  totalDishes: number
  pendingCount: number
  inProgressCount: number
  completedCount: number
  abnormalTemperatureCount: number
  durationAbnormalCount: number
  completionRate: number
}

export interface CookTemperaturePoint {
  id?: number | null
  recordTime: string | null
  temperature: number | null
  abnormal: boolean
  remark?: string | null
}

export interface CookAIMonitorRecord {
  alertIndex?: number | null
  violationType?: string | null
  violationName?: string | null
  level?: string | null
  description?: string | null
  suggestion?: string | null
  snapshotTime?: string | null
  acknowledged?: boolean | null
  acknowledgedBy?: string | null
  acknowledgedAt?: string | null
}

export interface CookTimelineEvent {
  eventType: 'temperature' | 'ai_alert' | 'status_change'
  eventTime: string | null
  title: string
  detail?: string | null
  level?: string | null
  operatorName?: string | null
  temperature?: number | null
  abnormal?: boolean | null
  violationType?: string | null
}

export interface CookTask {
  id: number
  taskNo: string
  planId: number
  planDate: string
  startDate?: string | null
  endDate?: string | null
  taskDate?: string | null
  mealType: string
  menuName: string
  status: CookTaskStatus
  plannedQty: number
  actualQty: number
  chefId?: number | string | null
  chefName?: string | null
  assignedChefId?: number | string | null
  assignedChefName?: string | null
  initiatorId?: number | string | null
  initiatorName?: string | null
  completerId?: number | string | null
  completerName?: string | null
  handoffStatus?: CookHandoffStatus
  handoffRemark?: string | null
  allowStartTime?: string | null
  allowEndTime?: string | null
  materialPrepStatus?: string | null
  deviceId?: number | string | null
  deviceName?: string | null
  sensorId?: number | string | null
  sensorCode?: string | null
  deviceOnline?: boolean | null
  sensorOnline?: boolean | null
  deviceStatus?: string | null
  standardDuration?: number | null
  actualDuration?: number | null
  targetTemp?: number | null
  currentTemp?: number | null
  temperatureAbnormal: boolean
  aiViolationCount: number
  qualityScore?: number | null
  startTime?: string | null
  endTime?: string | null
  remark?: string | null
  foodSafetyPass?: boolean | null
  tempAbnormalConfirmed?: boolean
  tempAbnormalConfirmedBy?: number | null
  tempAbnormalConfirmedAt?: string | null
  collectionStatus?: CookCollectionStatus
  lastTemperatureRecordAt?: string | null
  hasSyncException?: boolean
  syncStatus?: CookSyncStatus
  syncRetryCount?: number | null
  syncRetryLimitReached?: boolean
  latestSyncFailureReason?: string | null
  hasCompensationPending?: boolean
  compensationStatus?: CookCompensationStatus
  ingredients: string[]
}

export interface CookTaskDetailIngredient {
  materialId?: number | null
  materialName: string
  materialSpec?: string | null
  quantity?: number | null
  unit?: string | null
  main?: boolean
  batchNo?: string | null
  traceBatchId?: string | null
}

export interface CookTaskDetail {
  id: number
  taskNo: string
  planId: number
  planDate: string
  startDate?: string | null
  endDate?: string | null
  taskDate?: string | null
  mealType: string
  menuName: string
  status: CookTaskStatus
  plannedQty: number
  actualQty: number
  chefId?: number | string | null
  chefName?: string | null
  assignedChefId?: number | string | null
  assignedChefName?: string | null
  initiatorId?: number | string | null
  initiatorName?: string | null
  completerId?: number | string | null
  completerName?: string | null
  handoffStatus?: CookHandoffStatus
  handoffRemark?: string | null
  allowStartTime?: string | null
  allowEndTime?: string | null
  materialPrepStatus?: string | null
  deviceId?: number | string | null
  deviceName?: string | null
  sensorId?: number | string | null
  sensorCode?: string | null
  deviceOnline?: boolean | null
  sensorOnline?: boolean | null
  deviceStatus?: string | null
  standardDuration?: number | null
  actualDuration?: number | null
  targetTemp?: number | null
  currentTemp?: number | null
  temperatureAbnormal: boolean
  aiViolationCount: number
  qualityScore?: number | null
  startTime?: string | null
  endTime?: string | null
  remark?: string | null
  planCode?: string | null
  recipeId?: number | null
  recipeCode?: string | null
  recipeDescription?: string | null
  cookingSteps?: string | null
  targetTempMin?: number | null
  targetTempMax?: number | null
  temperatureRecords: CookTemperaturePoint[]
  aiMonitorRecords: CookAIMonitorRecord[]
  ingredients: CookTaskDetailIngredient[]
  outboundOrderNo?: string | null
  foodSafetyPass?: boolean | null
  tempAbnormalConfirmed?: boolean
  tempAbnormalConfirmedBy?: number | null
  tempAbnormalConfirmedAt?: string | null
  collectionStatus?: CookCollectionStatus
  lastTemperatureRecordAt?: string | null
  hasSyncException?: boolean
  syncStatus?: CookSyncStatus
  syncRetryCount?: number | null
  syncRetryLimitReached?: boolean
  latestSyncFailureReason?: string | null
  hasCompensationPending?: boolean
  compensationStatus?: CookCompensationStatus
}

export type CookTaskActionReasonCode =
  | 'status_invalid'
  | 'terminal_offline'
  | 'time_window_invalid'
  | 'material_not_prepared'
  | 'device_offline'
  | 'chef_mismatch'
  | 'initiator_only'
  | 'hidden'
  | 'historical_readonly'
  | 'none'

export interface CookTaskActionState {
  visible: boolean
  enabled: boolean
  reason: string
  reasonCode: CookTaskActionReasonCode
}

export interface CookTaskStartPayload {
  chefId?: number
  chefName?: string
  deviceId?: number
  remark?: string
}

export interface CookTaskCompletePayload {
  actualQty?: number
  qualityScore?: number
  remark?: string
}

export interface TemperatureReportPayload {
  taskId: number
  temperature: number
  abnormal?: boolean
  remark?: string
}

export interface CookTaskCancelPayload {
  reason: string
}

export interface CookTaskAssignPayload {
  chefId: number
  chefName: string
}

export interface CookTaskArchivePayload {
  handoffStatus?: string
  handoffRemark?: string
}

export interface ChefOption {
  id: number
  name: string
  position?: string
  orgId?: number | null
  orgName?: string
}

export interface OfflineActionRecord {
  id: string
  type: 'start' | 'temperature' | 'complete'
  taskId: number
  payload?: TemperatureReportPayload | CookTaskStartPayload | CookTaskCompletePayload | CookTaskCancelPayload | CookTaskArchivePayload
  createdAt: string
  syncStatus: 'pending' | 'syncing' | 'failed'
  errorMessage?: string
  retryCount?: number
  nextRetryAt?: string | null
  conflictPending?: boolean
  /** 入队时捕获的任务名称，用于离线状态下展示冲突描述 */
  taskName?: string
}

export type CookTaskPageResponse = ApiResponse<PageResponse<CookTask>>
