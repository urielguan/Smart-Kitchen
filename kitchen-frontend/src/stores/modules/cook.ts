import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  acknowledgeCookAlert,
  archiveCookTask,
  assignCookTask,
  cancelCookTask,
  completeCookTask,
  getCookDashboard,
  getCookTaskAiMonitor,
  getCookTaskDetail,
  getCookTaskList,
  getCookTaskTemperature,
  getCookTaskTemperatureSince,
  getCookTaskTimeline,
  reportCookTemperature,
  startCookTask,
  confirmTempAbnormal
} from '@/api/modules/cook'
import type {
  ChefOption,
  CookDashboard,
  CookTask,
  CookTaskActionReasonCode,
  CookTaskActionState,
  CookTaskArchivePayload,
  CookTaskAssignPayload,
  CookTaskCancelPayload,
  CookTaskCompletePayload,
  CookTaskDetail,
  CookTaskQuery,
  CookTaskStartPayload,
  TemperatureReportPayload,
  CookTimelineEvent
} from '@/types'
import { useAppStore } from './app'
import { useOfflineStore } from './offline'
import { setSuppressErrors } from '@/api'
import { detectConflict } from '@/utils/conflict'
import { getOrgList, getEmployeeList } from '@/api/modules/system'
import type { OrgOption } from '@/types'

const RETRY_LIMIT = 3
const RETRY_DELAY_MS = 5000

const createEmptyDashboard = (): CookDashboard => ({
  totalDishes: 0,
  pendingCount: 0,
  inProgressCount: 0,
  completedCount: 0,
  abnormalTemperatureCount: 0,
  durationAbnormalCount: 0,
  completionRate: 0
})

const normalizeIdentity = (value?: number | string | null) => {
  if (value === null || value === undefined) {
    return ''
  }

  return String(value).trim().toLowerCase()
}

const parseTaskTime = (planDate?: string | null, value?: string | null) => {
  if (!value) {
    return null
  }

  if (/^\d{4}-\d{2}-\d{2}/.test(value)) {
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }

  if (/^\d{2}:\d{2}(:\d{2})?$/.test(value) && planDate) {
    const parsed = new Date(`${planDate}T${value.length === 5 ? `${value}:00` : value}`)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }

  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const formatWindowLabel = (start?: string | null, end?: string | null) => {
  if (start && end) {
    return `${start} - ${end}`
  }

  return start || end || ''
}

const createActionState = (
  visible: boolean,
  enabled: boolean,
  reason = '',
  reasonCode: CookTaskActionReasonCode = 'none'
): CookTaskActionState => ({ visible, enabled, reason, reasonCode })

export const useCookStore = defineStore('cook', () => {
  const appStore = useAppStore()
  const offlineStore = useOfflineStore()

  const list = ref<CookTask[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const dashboard = ref<CookDashboard>(createEmptyDashboard())
  const getTodayString = () => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  const searchParams = ref<CookTaskQuery>({
    taskDate: getTodayString(),
    planDate: getTodayString(),
    mealType: '',
    status: '',
    chefName: '',
    keyword: '',
    orgId: null,
    deviceLocation: '',
    alertLevel: ''
  })
  const currentTask = ref<CookTaskDetail | null>(null)
  const timelineEvents = ref<CookTimelineEvent[]>([])
  const detailLoading = ref(false)
  const detailVisible = ref(false)
  const actionLoadingId = ref<number | null>(null)
  const lastSyncTime = ref<string>('')
  const collectionRuntime = ref<Record<number, {
    collecting: boolean
    lastSampleAt?: string | null
    nextSampleDueAt?: string | null
    interrupted: boolean
    pendingSync: boolean
    syncFailed: boolean
    temperatureAbnormalUnconfirmed: boolean
  }>>({})

  const isHistorical = computed(() => {
    const taskDate = searchParams.value.taskDate
    if (!taskDate) return false
    return taskDate !== getTodayString()
  })

  const orgList = ref<OrgOption[]>([])
  const orgListLoading = ref(false)

  const fetchOrgList = async () => {
    orgListLoading.value = true
    try {
      const res = await getOrgList()
      const raw = (res.data as any)?.list || res.data || []
      orgList.value = Array.isArray(raw)
        ? raw.map((item: any) => ({ id: item.id, name: item.orgName || item.name, code: item.orgCode || item.code }))
        : []
    } catch {
      orgList.value = []
    } finally {
      orgListLoading.value = false
    }
  }

  const chefList = ref<ChefOption[]>([])
  const chefListLoading = ref(false)

  const fetchChefList = async () => {
    chefListLoading.value = true
    try {
      const res = await getEmployeeList()
      const raw = (res.data as any)?.records || (res.data as any)?.list || res.data || []
      chefList.value = Array.isArray(raw)
        ? raw.map((item: any) => ({
            id: item.id,
            name: item.realName || item.name,
            position: item.position,
            orgId: item.orgId,
            orgName: item.orgName
          }))
        : []
    } catch {
      chefList.value = []
    } finally {
      chefListLoading.value = false
    }
  }

  const pendingTasks = computed(() => list.value.filter((item) => item.status === 'pending'))
  const abnormalTasks = computed(() => list.value.filter((item) => item.temperatureAbnormal || item.aiViolationCount > 0))
  const taskRuntimeState = computed(() => (taskId: number) => collectionRuntime.value[taskId] || null)

  const currentUserIdentity = computed(() => {
    const session = appStore.session
    return {
      userId: normalizeIdentity(session?.userId),
      employeeId: normalizeIdentity(session?.employeeId),
      username: normalizeIdentity(session?.username),
      displayName: normalizeIdentity(session?.displayName)
    }
  })

  const matchesCurrentUser = (value?: number | string | null) => {
    const target = normalizeIdentity(value)

    if (!target) {
      return false
    }

    const identity = currentUserIdentity.value
    return [identity.userId, identity.employeeId, identity.username, identity.displayName].filter(Boolean).includes(target)
  }

  const isTaskWithinWindow = (task: Pick<CookTask, 'planDate' | 'allowStartTime' | 'allowEndTime'>) => {
    if (!task.allowStartTime || !task.allowEndTime) {
      return true
    }

    const start = parseTaskTime(task.planDate, task.allowStartTime)
    const end = parseTaskTime(task.planDate, task.allowEndTime)

    if (!start || !end) {
      return true
    }

    const now = new Date()
    return now >= start && now <= end
  }

  const isTaskDeviceOnline = (task: Pick<CookTask, 'deviceOnline' | 'sensorOnline' | 'deviceStatus'>) => {
    if (task.sensorOnline === false || task.deviceOnline === false) {
      return false
    }

    const deviceStatus = normalizeIdentity(task.deviceStatus)
    if (['offline', 'disconnected'].includes(deviceStatus)) {
      return false
    }

    return true
  }

  const getTaskStartActionState = (task: Pick<CookTask, 'status' | 'planDate' | 'allowStartTime' | 'allowEndTime' | 'deviceOnline' | 'sensorOnline' | 'deviceStatus' | 'assignedChefId'>) => {
    if (task.status !== 'pending') {
      return createActionState(false, false, '', 'hidden')
    }

    if (isHistorical.value) {
      return createActionState(true, false, '历史数据，仅可查看', 'historical_readonly')
    }

    if (!appStore.online) {
      return createActionState(true, false, '当前终端离线，暂不可开始烹饪', 'terminal_offline')
    }

    if (!isTaskWithinWindow(task)) {
      const label = formatWindowLabel(task.allowStartTime, task.allowEndTime)
      const reason = label ? `当前不在允许烹饪时段（${label}）` : '当前不在允许烹饪时段'
      return createActionState(true, false, reason, 'time_window_invalid')
    }

    if (!isTaskDeviceOnline(task)) {
      return createActionState(true, false, '传感器离线，请联系管理员', 'device_offline')
    }

    if (task.assignedChefId && !matchesCurrentUser(task.assignedChefId)) {
      return createActionState(true, false, '仅指定厨师可操作', 'chef_mismatch')
    }

    return createActionState(true, true)
  }

  const getTaskCompleteActionState = (task: Pick<CookTask, 'status' | 'initiatorId'>) => {
    if (task.status !== 'in_progress') {
      return createActionState(false, false, '', 'hidden')
    }

    if (isHistorical.value) {
      return createActionState(true, false, '历史数据，仅可查看', 'historical_readonly')
    }

    if (task.initiatorId && !matchesCurrentUser(task.initiatorId)) {
      return createActionState(false, false, '仅发起人可完成此任务', 'initiator_only')
    }

    return createActionState(true, true)
  }

  const canCancelTask = (task: Pick<CookTask, 'status'>) => appStore.currentRole === 'supervisor' && task.status === 'pending' && !isHistorical.value

const canArchiveTask = (task: Pick<CookTask, 'status'>) => task.status === 'completed' && !isHistorical.value

  const canAssignChef = (task: Pick<CookTask, 'status'>) =>
    appStore.currentRole === 'supervisor'
    && (task.status === 'pending' || task.status === 'in_progress')
    && !isHistorical.value

  const buildTaskActionState = (task: CookTask | CookTaskDetail) => ({
    start: getTaskStartActionState(task),
    complete: getTaskCompleteActionState(task)
  })

  const currentTaskActionState = computed(() => {
    if (!currentTask.value) {
      return {
        start: createActionState(false, false, '', 'hidden'),
        complete: createActionState(false, false, '', 'hidden')
      }
    }

    return buildTaskActionState(currentTask.value)
  })

  const syncStamp = () => {
    lastSyncTime.value = new Date().toISOString()
  }

  const upsertRuntimeStateFromTask = (task: CookTask | CookTaskDetail) => {
    collectionRuntime.value[task.id] = {
      collecting: task.status === 'in_progress',
      lastSampleAt: task.lastTemperatureRecordAt ?? null,
      nextSampleDueAt: task.lastTemperatureRecordAt
        ? new Date(new Date(task.lastTemperatureRecordAt).getTime() + 30000).toISOString()
        : null,
      interrupted: task.collectionStatus === 'interrupted',
      pendingSync: !!task.hasSyncException,
      syncFailed: task.syncStatus === 'sync_failed',
      temperatureAbnormalUnconfirmed: !!task.temperatureAbnormal && !task.tempAbnormalConfirmed
    }
  }

  const syncRuntimeStates = (tasks: Array<CookTask | CookTaskDetail>) => {
    for (const task of tasks) {
      upsertRuntimeStateFromTask(task)
    }
  }

  const reconcileCurrentTaskWithList = (tasks: CookTask[]) => {
    if (!currentTask.value) {
      return
    }

    const stillVisible = tasks.some((task) => task.id === currentTask.value?.id)
    if (!stillVisible) {
      currentTask.value = null
      timelineEvents.value = []
      detailVisible.value = false
    }
  }

  const resetSessionState = () => {
    list.value = []
    total.value = 0
    dashboard.value = createEmptyDashboard()
    currentTask.value = null
    timelineEvents.value = []
    detailVisible.value = false
    detailLoading.value = false
    actionLoadingId.value = null
    lastSyncTime.value = ''
    collectionRuntime.value = {}
    stopAutoRefresh()
  }

  const getNextRetryAt = () => new Date(Date.now() + RETRY_DELAY_MS).toISOString()

  const getActionErrorMessage = (fallback: string, task: CookTask | CookTaskDetail | null, type: 'start' | 'complete', error: any) => {
    const rawMessage = typeof error?.message === 'string' ? error.message : ''
    const normalized = rawMessage.toLowerCase()

    const mappedState = task
      ? type === 'start'
        ? getTaskStartActionState(task)
        : getTaskCompleteActionState(task)
      : null

    if (mappedState && !mappedState.enabled && mappedState.reason) {
      return mappedState.reason
    }

    if (normalized.includes('assign') || rawMessage.includes('指定厨师')) {
      return '仅指定厨师可操作'
    }

    if (normalized.includes('initiator') || rawMessage.includes('发起人')) {
      return '仅发起人可完成此任务'
    }

    if (normalized.includes('sensor') || normalized.includes('device') || rawMessage.includes('离线')) {
      return '传感器离线，请联系管理员'
    }

    if (normalized.includes('time window') || rawMessage.includes('时段')) {
      return '当前不在允许烹饪时段'
    }

    if (rawMessage.includes('主管')) {
      return rawMessage
    }

    return rawMessage || fallback
  }

  const fetchDashboard = async () => {
    try {
      const res = await getCookDashboard(searchParams.value)
      dashboard.value = {
        totalDishes: Number(res.data?.totalDishes || 0),
        pendingCount: Number(res.data?.pendingCount || 0),
        inProgressCount: Number(res.data?.inProgressCount || 0),
        completedCount: Number(res.data?.completedCount || 0),
        abnormalTemperatureCount: Number(res.data?.abnormalTemperatureCount || 0),
        durationAbnormalCount: Number(res.data?.durationAbnormalCount || 0),
        completionRate: Number(res.data?.completionRate || 0)
      }
    } catch {
      // dashboard 加载失败不应阻塞任务列表
    }
  }

  const fetchList = async (silent = false) => {
    if (!silent) {
      loading.value = true
    }
    try {
      const res = await getCookTaskList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      })
      const newList = res.data?.list || []
      if (silent && list.value.length > 0) {
        mergeList(newList)
      } else {
        list.value = newList
      }
      reconcileCurrentTaskWithList(list.value)
      syncRuntimeStates(newList)
      total.value = res.data?.total || 0
      syncStamp()
    } finally {
      if (!silent) {
        loading.value = false
      }
    }
  }

  const mergeList = (newList: CookTask[]) => {
    const map = new Map(list.value.map((t) => [t.id, t]))
    for (const item of newList) {
      const existing = map.get(item.id)
      if (existing) {
        Object.assign(existing, item)
        map.delete(item.id)
      } else {
        list.value.push(item)
      }
    }
    for (const stale of map.keys()) {
      const idx = list.value.findIndex((t) => t.id === stale)
      if (idx !== -1) list.value.splice(idx, 1)
    }
  }

  const refresh = async (silent = false) => {
    await Promise.all([fetchDashboard(), fetchList(silent)])
  }

  const search = async (params: CookTaskQuery) => {
    const nextTaskDate = params.taskDate !== undefined
      ? params.taskDate
      : params.planDate !== undefined
        ? params.planDate
        : searchParams.value.taskDate

    const nextPlanDate = params.planDate !== undefined
      ? params.planDate
      : params.taskDate !== undefined
        ? params.taskDate
        : searchParams.value.planDate

    searchParams.value = {
      taskDate: nextTaskDate ?? '',
      planDate: nextPlanDate ?? '',
      mealType: params.mealType ?? '',
      status: params.status ?? '',
      chefName: params.chefName ?? '',
      keyword: params.keyword ?? '',
      orgId: params.orgId ?? null,
      deviceLocation: params.deviceLocation ?? '',
      alertLevel: params.alertLevel ?? ''
    }
    pageNum.value = 1
    await refresh()
  }

  const resetSearch = async () => {
    searchParams.value = {
      taskDate: '',
      planDate: '',
      mealType: '',
      status: '',
      chefName: '',
      keyword: '',
      orgId: null,
      deviceLocation: '',
      alertLevel: ''
    }
    pageNum.value = 1
    await refresh()
  }

  const changePage = async (page: number) => {
    pageNum.value = page
    await fetchList()
  }

  const changePageSize = async (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  const loadTaskDetail = async (id: number) => {
    detailLoading.value = true
    try {
      const [detailRes, temperatureRes, aiRes, timelineRes] = await Promise.all([
        getCookTaskDetail(id),
        getCookTaskTemperature(id),
        getCookTaskAiMonitor(id),
        getCookTaskTimeline(id)
      ])

      currentTask.value = {
        ...detailRes.data,
        temperatureRecords: temperatureRes.data || [],
        aiMonitorRecords: aiRes.data || []
      }
      if (currentTask.value) {
        upsertRuntimeStateFromTask(currentTask.value)
      }
      timelineEvents.value = timelineRes.data || []
      syncStamp()
    } finally {
      detailLoading.value = false
    }
  }

  const refreshTaskTemperature = async () => {
    if (!currentTask.value) return

    const records = currentTask.value.temperatureRecords
    const lastId = records.length > 0
      ? (records[records.length - 1].id ?? null)
      : null

    if (lastId === null) {
      // No incremental support yet (old data without id), do full refresh
      const tempRes = await getCookTaskTemperature(currentTask.value.id)
      currentTask.value = {
        ...currentTask.value,
        temperatureRecords: tempRes.data || []
      }
      return
    }

    const res = await getCookTaskTemperatureSince(currentTask.value.id, lastId)
    const newRecords = res.data || []
    if (newRecords.length > 0) {
      currentTask.value = {
        ...currentTask.value,
        temperatureRecords: [...currentTask.value.temperatureRecords, ...newRecords]
      }
    }
  }

  const openDetail = async (id: number) => {
    detailVisible.value = true
    await loadTaskDetail(id)
  }

  const closeDetail = () => {
    detailVisible.value = false
  }

  const getTaskFromState = (id: number) => {
    if (currentTask.value?.id === id) {
      return currentTask.value
    }

    return list.value.find((item) => item.id === id) || null
  }

  const startTaskAction = async (id: number, payload?: CookTaskStartPayload) => {
    const task = getTaskFromState(id)
    const actionState = task ? getTaskStartActionState(task) : null

    if (actionState?.visible && !actionState.enabled) {
      ElMessage.warning(actionState.reason)
      return false
    }

    if (!appStore.online) {
      offlineStore.enqueue({ type: 'start', taskId: id, payload, taskName: task?.menuName })
      ElMessage.warning('当前离线，开始操作已进入同步队列')
      return false
    }

    actionLoadingId.value = id
    try {
      await startCookTask(id, payload)
      ElMessage.success('已开始烹饪')
      await refresh()
      return true
    } catch (error: any) {
      ElMessage.error(getActionErrorMessage('开始烹饪失败', task, 'start', error))
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const completeTaskAction = async (id: number, payload?: CookTaskCompletePayload) => {
    const task = getTaskFromState(id)
    const actionState = task ? getTaskCompleteActionState(task) : null

    if (actionState?.visible && !actionState.enabled) {
      ElMessage.warning(actionState.reason)
      return false
    }

    if (task && !actionState?.visible) {
      ElMessage.warning('仅发起人可完成此任务')
      return false
    }

    if (!appStore.online) {
      offlineStore.enqueue({ type: 'complete', taskId: id, payload, taskName: task?.menuName })
      if (task) {
        collectionRuntime.value[id] = {
          ...(collectionRuntime.value[id] || {
            collecting: false,
            interrupted: false,
            pendingSync: false,
            syncFailed: false,
            temperatureAbnormalUnconfirmed: false
          }),
          collecting: false,
          pendingSync: true,
          syncFailed: false
        }
      }
      ElMessage.warning('当前离线，完成操作已进入同步队列')
      return false
    }

    actionLoadingId.value = id
    try {
      await completeCookTask(id, payload)
      ElMessage.success('已完成烹饪')
      await refresh()
      return true
    } catch (error: any) {
      ElMessage.error(getActionErrorMessage('完成烹饪失败', task, 'complete', error))
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const reportTemperatureAction = async (payload: TemperatureReportPayload) => {
    const task = getTaskFromState(payload.taskId)
    if (!task) {
      ElMessage.warning('任务不存在或已失效')
      return false
    }

    if (!appStore.online) {
      offlineStore.enqueue({ type: 'temperature', taskId: payload.taskId, payload, taskName: task.menuName })
      collectionRuntime.value[payload.taskId] = {
        ...(collectionRuntime.value[payload.taskId] || {
          collecting: true,
          interrupted: false,
          pendingSync: false,
          syncFailed: false,
          temperatureAbnormalUnconfirmed: false
        }),
        collecting: true,
        lastSampleAt: new Date().toISOString(),
        nextSampleDueAt: new Date(Date.now() + 30000).toISOString(),
        pendingSync: true,
        syncFailed: false,
        temperatureAbnormalUnconfirmed: !!payload.abnormal || !!collectionRuntime.value[payload.taskId]?.temperatureAbnormalUnconfirmed
      }
      ElMessage.warning('当前离线，温度采样已进入同步队列')
      return false
    }

    try {
      await reportCookTemperature(payload)
      collectionRuntime.value[payload.taskId] = {
        ...(collectionRuntime.value[payload.taskId] || {
          collecting: true,
          interrupted: false,
          pendingSync: false,
          syncFailed: false,
          temperatureAbnormalUnconfirmed: false
        }),
        collecting: true,
        lastSampleAt: new Date().toISOString(),
        nextSampleDueAt: new Date(Date.now() + 30000).toISOString(),
        interrupted: false,
        pendingSync: false,
        syncFailed: false,
        temperatureAbnormalUnconfirmed: !!payload.abnormal || !!collectionRuntime.value[payload.taskId]?.temperatureAbnormalUnconfirmed
      }
      return true
    } catch (error: any) {
      ElMessage.error(error?.message || '温度采样上报失败')
      return false
    }
  }

  const cancelTaskAction = async (id: number, payload: CookTaskCancelPayload) => {
    const task = getTaskFromState(id)

    if (!task || !canCancelTask(task)) {
      ElMessage.warning('仅主管可取消未开始任务')
      return false
    }

    actionLoadingId.value = id
    try {
      await cancelCookTask(id, payload)
      ElMessage.success('任务已取消并回退待排产')
      await refresh()
      return true
    } catch (error: any) {
      ElMessage.error(getActionErrorMessage('取消任务失败', task, 'complete', error))
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const archiveTaskAction = async (id: number, payload?: CookTaskArchivePayload) => {
    const task = getTaskFromState(id)

    if (!task || !canArchiveTask(task)) {
      ElMessage.warning('仅已完成的任务可归档')
      return false
    }

    actionLoadingId.value = id
    try {
      await archiveCookTask(id, payload)
      ElMessage.success('任务已归档')
      await refresh()
      return true
    } catch (error: any) {
      ElMessage.error(getActionErrorMessage('归档任务失败', task, 'complete', error))
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const syncOfflineQueue = async () => {
    if (!appStore.online || offlineStore.syncing || offlineStore.queue.length === 0) {
      return
    }

    offlineStore.syncing = true
    let conflictDetected = false
    try {
      for (const record of [...offlineStore.getReadyForRetry()].reverse()) {
        if (offlineStore.hasConflict) {
          break
        }

        offlineStore.markSyncing(record.id)
        try {
          setSuppressErrors(true)
          if (record.type === 'start') {
            await startCookTask(record.taskId, record.payload as CookTaskStartPayload | undefined)
          } else if (record.type === 'temperature') {
            await reportCookTemperature(record.payload as TemperatureReportPayload)
          } else {
            await completeCookTask(record.taskId, record.payload as CookTaskCompletePayload | undefined)
          }
          offlineStore.markDone(record.id)
        } catch (error: any) {
          const conflict = detectConflict(error, record)
          if (conflict) {
            offlineStore.setConflict(record.id, conflict)
            conflictDetected = true
            break
          }
          const retryCount = record.retryCount || 0
          const errorMessage = getActionErrorMessage('同步失败', getTaskFromState(record.taskId), record.type === 'complete' ? 'complete' : 'start', error)
          if (retryCount + 1 >= RETRY_LIMIT) {
            offlineStore.markFailed(record.id, `${errorMessage}（已达到自动重试上限）`)
            collectionRuntime.value[record.taskId] = {
              ...(collectionRuntime.value[record.taskId] || {
                collecting: false,
                interrupted: false,
                pendingSync: false,
                syncFailed: false,
                temperatureAbnormalUnconfirmed: false
              }),
              pendingSync: true,
              syncFailed: true
            }
          } else {
            offlineStore.markFailed(record.id, `${errorMessage}（将自动重试）`, getNextRetryAt())
          }
        } finally {
          setSuppressErrors(false)
        }
      }
      if (!conflictDetected) {
        await refresh()
      }
    } finally {
      offlineStore.syncing = false
    }
  }

  const resumeSyncAfterConflict = async () => {
    offlineStore.clearConflict()
    await syncOfflineQueue()
  }

  const acknowledgeAlert = async (taskId: number, alertIndex: number) => {
    actionLoadingId.value = taskId
    try {
      await acknowledgeCookAlert(taskId, alertIndex)
      ElMessage.success('已确认预警')
      if (currentTask.value?.id === taskId) {
        await loadTaskDetail(taskId)
      }
    } catch (error: any) {
      ElMessage.error(error?.message || '确认预警失败')
    } finally {
      actionLoadingId.value = null
    }
  }

  const confirmTempAbnormalAction = async (id: number) => {
    actionLoadingId.value = id
    try {
      await confirmTempAbnormal(id)
      ElMessage.success('已确认温度异常')
      if (currentTask.value?.id === id) {
        await loadTaskDetail(id)
      }
      return true
    } catch (error: any) {
      ElMessage.error(error?.message || '确认失败')
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const assignChefAction = async (id: number, payload: CookTaskAssignPayload) => {
    const task = getTaskFromState(id)

    if (!task || !canAssignChef(task)) {
      ElMessage.warning('仅主管可分派待烹饪或进行中的任务')
      return false
    }

    actionLoadingId.value = id
    try {
      await assignCookTask(id, payload)
      ElMessage.success(`已分派给 ${payload.chefName}`)
      await refresh()
      if (currentTask.value?.id === id) {
        await loadTaskDetail(id)
      }
      return true
    } catch (error: any) {
      const msg = error?.message || '分派厨师失败'
      ElMessage.error(msg.includes('指定') || msg.includes('不存在') || msg.includes('离职') ? msg : '分派厨师失败')
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  // 自动刷新定时器
  let refreshTimer: ReturnType<typeof setTimeout> | null = null

  // P1-10: 离线时降低刷新频率（5s → 30s）
  const getRefreshInterval = () => appStore.online ? 5000 : 30000

  const scheduleNextRefresh = () => {
    if (isHistorical.value) return
    refreshTimer = setTimeout(async () => {
      await refresh(true)
      scheduleNextRefresh()
    }, getRefreshInterval())
  }

  const startAutoRefresh = () => {
    stopAutoRefresh()
    scheduleNextRefresh()
  }

  const stopAutoRefresh = () => {
    if (refreshTimer) {
      clearTimeout(refreshTimer)
      refreshTimer = null
    }
  }

  watch(isHistorical, (historical) => {
    if (historical) {
      stopAutoRefresh()
    } else {
      startAutoRefresh()
    }
  })

  return {
    list,
    total,
    pageNum,
    pageSize,
    loading,
    dashboard,
    searchParams,
    currentTask,
    timelineEvents,
    detailLoading,
    detailVisible,
    actionLoadingId,
    lastSyncTime,
    isHistorical,
    orgList,
    orgListLoading,
    fetchOrgList,
    chefList,
    chefListLoading,
    fetchChefList,
    pendingTasks,
    abnormalTasks,
    taskRuntimeState,
    currentTaskActionState,
    canCancelTask,
    canArchiveTask,
    canAssignChef,
    fetchDashboard,
    fetchList,
    refresh,
    search,
    resetSearch,
    changePage,
    changePageSize,
    loadTaskDetail,
    refreshTaskTemperature,
    openDetail,
    closeDetail,
    getTaskStartActionState,
    getTaskCompleteActionState,
    buildTaskActionState,
    startTaskAction,
    completeTaskAction,
    reportTemperatureAction,
    cancelTaskAction,
    archiveTaskAction,
    syncOfflineQueue,
    resumeSyncAfterConflict,
    startAutoRefresh,
    acknowledgeAlert,
    confirmTempAbnormalAction,
    assignChefAction,
    matchesCurrentUser,
    resetSessionState,
    stopAutoRefresh
  }
})
