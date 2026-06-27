import { get, post } from '@/api'
import type {
  ApiResponse,
  CookAIMonitorRecord,
  CookDashboard,
  CookTask,
  CookTaskArchivePayload,
  CookTaskAssignPayload,
  CookTaskCancelPayload,
  CookTaskCompletePayload,
  CookTaskDetail,
  CookTaskQuery,
  CookTaskStartPayload,
  TemperatureReportPayload,
  CookTemperaturePoint,
  CookTimelineEvent,
  PageResponse
} from '@/types'

// ==================== 字段映射：后端 VO → 前端类型 ====================

const mapTaskFromBackend = (raw: Record<string, unknown>): CookTask => ({
  id: raw.id as number,
  taskNo: (raw.taskNo ?? '') as string,
  planId: raw.planId as number,
  planDate: (raw.planDate ?? '') as string,
  startDate: (raw.startDate ?? null) as string | null,
  endDate: (raw.endDate ?? null) as string | null,
  taskDate: (raw.taskDate ?? null) as string | null,
  mealType: (raw.mealType ?? '') as string,
  menuName: (raw.menuName ?? '') as string,
  status: (raw.status ?? 'pending') as CookTask['status'],
  plannedQty: (raw.plannedQty ?? 0) as number,
  actualQty: (raw.actualQty ?? 0) as number,
  chefId: (raw.assignedChefId ?? raw.chefId ?? null) as number | string | null,
  chefName: (raw.chefName ?? raw.assignedChefName ?? null) as string | null,
  assignedChefId: (raw.assignedChefId ?? null) as number | string | null,
  assignedChefName: (raw.assignedChefName ?? null) as string | null,
  initiatorId: (raw.initiatorId ?? null) as number | string | null,
  initiatorName: (raw.initiatorName ?? null) as string | null,
  completerId: (raw.completerId ?? null) as number | string | null,
  completerName: (raw.completerName ?? null) as string | null,
  handoffStatus: (raw.handoffStatus ?? null) as CookTask['handoffStatus'],
  handoffRemark: (raw.handoffRemark ?? null) as string | null,
  allowStartTime: (raw.allowStartTime ?? null) as string | null,
  allowEndTime: (raw.allowEndTime ?? null) as string | null,
  materialPrepStatus: (raw.materialPrepStatus ?? null) as string | null,
  deviceId: (raw.deviceId ?? null) as number | string | null,
  deviceName: (raw.deviceName ?? null) as string | null,
  sensorId: (raw.sensorId ?? null) as number | string | null,
  sensorCode: (raw.sensorCode ?? null) as string | null,
  deviceOnline: (raw.deviceOnline ?? null) as boolean | null,
  sensorOnline: (raw.sensorOnline ?? null) as boolean | null,
  deviceStatus: (raw.deviceStatus ?? null) as string | null,
  standardDuration: (raw.standardDuration ?? null) as number | null,
  actualDuration: (raw.actualDuration ?? null) as number | null,
  targetTemp: (raw.targetTemp ?? null) as number | null,
  currentTemp: (raw.currentTemp ?? null) as number | null,
  temperatureAbnormal: (raw.temperatureAbnormal ?? false) as boolean,
  aiViolationCount: (raw.aiViolationCount ?? 0) as number,
  qualityScore: (raw.qualityScore ?? null) as number | null,
  startTime: (raw.startTime ?? null) as string | null,
  endTime: (raw.endTime ?? null) as string | null,
  remark: (raw.remark ?? null) as string | null,
  foodSafetyPass: (raw.foodSafetyPass ?? null) as boolean | null,
  tempAbnormalConfirmed: (raw.tempAbnormalConfirmed ?? false) as boolean,
  tempAbnormalConfirmedBy: (raw.tempAbnormalConfirmedBy ?? null) as number | null,
  tempAbnormalConfirmedAt: (raw.tempAbnormalConfirmedAt ?? null) as string | null,
  collectionStatus: (raw.collectionStatus ?? null) as CookTask['collectionStatus'],
  lastTemperatureRecordAt: (raw.lastTemperatureRecordAt ?? null) as string | null,
  hasSyncException: (raw.hasSyncException ?? false) as boolean,
  syncStatus: (raw.syncStatus ?? null) as CookTask['syncStatus'],
  syncRetryCount: (raw.syncRetryCount ?? null) as number | null,
  syncRetryLimitReached: (raw.syncRetryLimitReached ?? false) as boolean,
  latestSyncFailureReason: (raw.latestSyncFailureReason ?? null) as string | null,
  hasCompensationPending: (raw.hasCompensationPending ?? false) as boolean,
  compensationStatus: (raw.compensationStatus ?? null) as CookTask['compensationStatus'],
  ingredients: ((raw.ingredients ?? []) as string[]).filter(Boolean)
})

const mapTaskDetailFromBackend = (raw: Record<string, unknown>): CookTaskDetail => ({
  ...mapTaskFromBackend(raw),
  planCode: (raw.planCode ?? null) as string | null,
  recipeId: (raw.recipeId ?? raw.menuId ?? null) as number | null,
  recipeCode: (raw.recipeCode ?? null) as string | null,
  recipeDescription: (raw.recipeDescription ?? null) as string | null,
  cookingSteps: (raw.cookingSteps ?? null) as string | null,
  targetTempMin: (raw.targetTempMin ?? null) as number | null,
  targetTempMax: (raw.targetTempMax ?? null) as number | null,
  temperatureRecords: (raw.temperatureRecords ?? []) as CookTemperaturePoint[],
  aiMonitorRecords: (raw.aiMonitorRecords ?? []) as CookAIMonitorRecord[],
  ingredients: ((raw.ingredients ?? []) as Array<Record<string, unknown>>).map((ing) => ({
    materialId: (ing.materialId ?? null) as number | null,
    materialName: (ing.materialName ?? '') as string,
    materialSpec: (ing.materialSpec ?? null) as string | null,
    quantity: (ing.quantity ?? null) as number | null,
    unit: (ing.unit ?? null) as string | null,
    main: (ing.main ?? false) as boolean,
    batchNo: (ing.batchNo ?? null) as string | null,
    traceBatchId: (ing.traceBatchId ?? null) as string | null
  })),
  outboundOrderNo: (raw.outboundOrderNo ?? null) as string | null
})

// ==================== API 调用 ====================

export const getCookDashboard = async (params?: CookTaskQuery): Promise<ApiResponse<CookDashboard>> => {
  const cleanParams: Record<string, unknown> = {}
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== '' && value != null) {
        cleanParams[key] = value
      }
    }
  }
  return await get<CookDashboard>('/v1/cook/dashboard', cleanParams)
}

export const getCookTaskList = async (params?: CookTaskQuery): Promise<ApiResponse<PageResponse<CookTask>>> => {
  // 过滤空值参数，避免空字符串导致后端 LocalDate 绑定异常
  const cleanParams: Record<string, unknown> = {}
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== '' && value != null) {
        cleanParams[key] = value
      }
    }
  }
  const response = await get<PageResponse<Record<string, unknown>>>('/v1/cook/tasks', cleanParams)
  const data = response.data || { list: [], total: 0, pageNum: 1, pageSize: 10 }
  return {
    ...response,
    data: {
      list: (data.list || []).map(mapTaskFromBackend),
      total: data.total,
      pageNum: data.pageNum,
      pageSize: data.pageSize
    }
  }
}

export const getCookTaskDetail = async (id: number): Promise<ApiResponse<CookTaskDetail>> => {
  const response = await get<Record<string, unknown>>(`/v1/cook/tasks/${id}`)
  return {
    ...response,
    data: mapTaskDetailFromBackend(response.data)
  }
}

export const startCookTask = async (id: number, data?: CookTaskStartPayload): Promise<ApiResponse<CookTask>> => {
  const response = await post<Record<string, unknown>>(`/v1/cook/tasks/${id}/start`, data)
  return { ...response, data: mapTaskFromBackend(response.data) }
}

export const completeCookTask = async (id: number, data?: CookTaskCompletePayload): Promise<ApiResponse<CookTask>> => {
  const response = await post<Record<string, unknown>>(`/v1/cook/tasks/${id}/complete`, data)
  return { ...response, data: mapTaskFromBackend(response.data) }
}

export const reportCookTemperature = async (data: TemperatureReportPayload): Promise<ApiResponse<void>> => {
  return await post<void>('/v1/cook/temperature', data)
}

export const cancelCookTask = async (id: number, data: CookTaskCancelPayload): Promise<ApiResponse<CookTask>> => {
  const response = await post<Record<string, unknown>>(`/v1/cook/tasks/${id}/cancel`, data)
  return { ...response, data: mapTaskFromBackend(response.data) }
}

export const assignCookTask = async (id: number, data: CookTaskAssignPayload): Promise<ApiResponse<CookTask>> => {
  const response = await post<Record<string, unknown>>(`/v1/cook/tasks/${id}/assign`, data)
  return { ...response, data: mapTaskFromBackend(response.data) }
}

export const getCookTaskTemperature = async (id: number): Promise<ApiResponse<CookTemperaturePoint[]>> => {
  return await get<CookTemperaturePoint[]>(`/v1/cook/tasks/${id}/temperature`)
}

export const getCookTaskTemperatureSince = async (
  id: number,
  sinceId: number
): Promise<ApiResponse<CookTemperaturePoint[]>> => {
  return await get<CookTemperaturePoint[]>(`/v1/cook/tasks/${id}/temperature/incremental`, { sinceId })
}

export const getCookTaskAiMonitor = async (id: number): Promise<ApiResponse<CookAIMonitorRecord[]>> => {
  return await get<CookAIMonitorRecord[]>(`/v1/cook/tasks/${id}/ai-monitor`)
}

export const acknowledgeCookAlert = async (taskId: number, alertIndex: number): Promise<ApiResponse<void>> => {
  return await post<void>(`/v1/cook/tasks/${taskId}/alerts/${alertIndex}/acknowledge`)
}

export const getCookTaskTimeline = async (id: number): Promise<ApiResponse<CookTimelineEvent[]>> => {
  return await get<CookTimelineEvent[]>(`/v1/cook/tasks/${id}/timeline`)
}

export const confirmTempAbnormal = async (id: number): Promise<ApiResponse<CookTask>> => {
  const response = await post<Record<string, unknown>>(`/v1/cook/tasks/${id}/confirm-temp-abnormal`)
  return { ...response, data: mapTaskFromBackend(response.data) }
}

export const archiveCookTask = async (id: number, data?: CookTaskArchivePayload): Promise<ApiResponse<CookTask>> => {
  const response = await post<Record<string, unknown>>(`/v1/cook/tasks/${id}/archive`, data)
  return { ...response, data: mapTaskFromBackend(response.data) }
}
