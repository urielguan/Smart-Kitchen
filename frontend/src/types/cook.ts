import type { PageQuery, PageResponse, ApiResponse } from './api'

export interface CookTaskQuery extends Partial<PageQuery> {
  taskDate?: string
  mealType?: string
  status?: string
  taskNo?: string
  chefName?: string
  orgId?: number
}

export interface CookDashboard {
  totalDishes: number
  pendingCount: number
  inProgressCount: number
  completedCount: number
  abnormalTemperatureCount: number
  completionRate: number
}

export interface CookTemperaturePoint {
  recordTime: string | null
  temperature: number | null
  abnormal: boolean
  remark?: string | null
}

export interface CookAIMonitorRecord {
  violationType?: string | null
  violationName?: string | null
  level?: string | null
  description?: string | null
  suggestion?: string | null
  snapshotTime?: string | null
}

export interface CookTask {
  id: number
  taskNo: string
  planId: number
  menuId?: number | null
  planDate: string
  taskDate?: string | null
  mealType: string
  menuName: string
  status: 'pending' | 'in_progress' | 'completed' | 'cancelled' | 'archived' | string
  reviewStatus?: string | null
  assignedChefId?: number | null
  assignedChefName?: string | null
  plannedQty: number
  actualQty: number
  chefName?: string | null
  deviceName?: string | null
  deviceLocation?: string | null
  standardDuration?: number | null
  actualDuration?: number | null
  targetTemp?: number | null
  currentTemp?: number | null
  temperatureAbnormal: boolean
  aiViolationCount: number
  qualityScore?: number | null
  materialPrepStatus?: string | null
  startTime?: string | null
  endTime?: string | null
  remark?: string | null
  foodSafetyPass?: boolean | null
  tempAbnormalConfirmed?: boolean
  tempAbnormalConfirmedBy?: number | null
  tempAbnormalConfirmedAt?: string | null
  collectionStatus?: 'normal' | 'interrupted' | 'pending_recovery' | string | null
  lastTemperatureRecordAt?: string | null
  hasSyncException?: boolean
  syncStatus?: 'normal' | 'sync_failed' | 'conflict_pending' | string | null
  syncRetryCount?: number | null
  syncRetryLimitReached?: boolean
  latestSyncFailureReason?: string | null
  hasCompensationPending?: boolean
  compensationStatus?: 'none' | 'pending' | 'resolved' | string | null
  ingredients: string[]
}

export interface CookTaskDetailIngredient {
  materialId?: number | null
  materialName: string
  materialSpec?: string | null
  quantity?: number | null
  unit?: string | null
  main?: boolean
}

export interface CookTaskDetail {
  id: number
  taskNo: string
  planId: number
  planCode?: string | null
  planDate: string
  taskDate?: string | null
  mealType: string
  recipeId?: number | null
  menuName: string
  recipeCode?: string | null
  recipeDescription?: string | null
  cookingSteps?: string | null
  status: CookTask['status']
  plannedQty: number
  actualQty: number
  chefName?: string | null
  deviceName?: string | null
  deviceLocation?: string | null
  standardDuration?: number | null
  actualDuration?: number | null
  targetTempMin?: number | null
  targetTempMax?: number | null
  currentTemp?: number | null
  temperatureAbnormal: boolean
  aiViolationCount: number
  qualityScore?: number | null
  materialPrepStatus?: string | null
  remark?: string | null
  startTime?: string | null
  endTime?: string | null
  ingredients: CookTaskDetailIngredient[]
  temperatureRecords: CookTemperaturePoint[]
  aiMonitorRecords: CookAIMonitorRecord[]
  foodSafetyPass?: boolean | null
  tempAbnormalConfirmed?: boolean
  tempAbnormalConfirmedBy?: number | null
  tempAbnormalConfirmedAt?: string | null
  collectionStatus?: 'normal' | 'interrupted' | 'pending_recovery' | string | null
  lastTemperatureRecordAt?: string | null
  hasSyncException?: boolean
  syncStatus?: 'normal' | 'sync_failed' | 'conflict_pending' | string | null
  syncRetryCount?: number | null
  syncRetryLimitReached?: boolean
  latestSyncFailureReason?: string | null
  hasCompensationPending?: boolean
  compensationStatus?: 'none' | 'pending' | 'resolved' | string | null
}

export interface CookTaskStartPayload {
  chefId?: number
  chefName?: string
  deviceId?: number
  remark?: string
}

export interface CookTaskCompletePayload {
  actualQty?: number
  remark?: string
}

export interface CookTaskArchivePayload {
  handoffStatus?: string
  handoffRemark?: string
}

export type CookTaskPageResponse = ApiResponse<PageResponse<CookTask>>
