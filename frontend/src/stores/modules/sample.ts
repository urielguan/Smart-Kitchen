import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  SampleDashboard,
  SampleAvailableCookTask,
  ManualDisposalSupplementPayload,
  SampleOperationLock,
  SampleOperationType,
  SampleHistoryRecordCreatePayload,
  SampleRecord,
  SampleRecordDetail,
  SampleRecordQuery,
  SampleRecordCreatePayload,
  DisposalPayload,
  DisposalReminderRecord,
  AiEvaluateResult,
  SampleRecordRegisterPayload,
  SampleRecordUpdatePayload,
  OperationLog
} from '@/types'
import {
  getSampleDashboard,
  createSampleHistoryRecord,
  getSampleRecordList,
  getSampleManualTaskOptions,
  createSampleRecord,
  registerSampleRecord,
  getSampleRecordDetail,
  acquireSampleOperationLock,
  executeDisposal,
  refreshSampleOperationLock,
  releaseSampleOperationLock,
  manualSupplementDisposal,
  getDisposalReminders,
  aiEvaluate,
  updateSampleRecord,
  voidSampleRecord,
  archiveSampleRecord,
  exportSampleRecords,
  getSampleOperationLogs,
  batchVoidRecords,
  batchArchiveRecords
} from '@/api/modules/sample'
import { SAMPLE_STATUS_MAP } from '@/constants/sample'

const emptyDashboard = (): SampleDashboard => ({
  totalSamples: 0,
  pendingDisposal: 0,
  disposed: 0,
  overdue: 0,
  todaySampled: 0
})

const OPERATION_LOCK_HEARTBEAT_MS = 30000
const OPERATION_LOCK_IDLE_TIMEOUT_MS = 5 * 60 * 1000
const OPERATION_LOCK_ACTIVITY_EVENTS = ['mousedown', 'mousemove', 'keydown', 'touchstart', 'scroll'] as const

interface ActiveOperationLockSession {
  recordId: number
  operationType: SampleOperationType
  lockToken: string
  refreshTimer: ReturnType<typeof setInterval> | null
  inactivityTimer: ReturnType<typeof setTimeout> | null
}

export const useSampleStore = defineStore('sample', () => {
  const list = ref<SampleRecord[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const dashboard = ref<SampleDashboard>(emptyDashboard())
  const searchParams = ref<SampleRecordQuery>({
    status: '',
    sampleDate: '',
    sampleDateEnd: '',
    mealType: '',
    menuName: '',
    showRollbackIsolated: false
  })
  const currentRecord = ref<SampleRecordDetail | null>(null)
  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const formVisible = ref(false)
  const formLoading = ref(false)
  const manualTaskOptions = ref<SampleAvailableCookTask[]>([])
  const historyFormVisible = ref(false)
  const historyFormLoading = ref(false)

  // 销样相关
  const disposalFormVisible = ref(false)
  const disposalLoading = ref(false)
  const disposalTargetId = ref<number | null>(null)
  const manualDisposalFormVisible = ref(false)
  const manualDisposalLoading = ref(false)
  const manualDisposalTargetRecord = ref<SampleRecordDetail | null>(null)

  // 编辑相关
  const editFormVisible = ref(false)
  const editTargetRecord = ref<SampleRecordDetail | null>(null)

  // 留样登记相关
  const registerFormVisible = ref(false)
  const registerFormLoading = ref(false)
  const registerTargetRecord = ref<SampleRecordDetail | null>(null)

  // 销样提醒
  const reminderList = ref<DisposalReminderRecord[]>([])
  const reminderTotal = ref(0)
  const reminderLoading = ref(false)
  const reminderPageNum = ref(1)
  const reminderPageSize = ref(10)

  // AI评估
  const aiEvaluateResult = ref<AiEvaluateResult | null>(null)
  const aiEvaluateLoading = ref(false)
  const aiEvaluateError = ref('')

  // 操作日志
  const operationLogs = ref<OperationLog[]>([])

  // 并发操作锁
  const activeOperationLock = ref<ActiveOperationLockSession | null>(null)
  const operationLockRefreshing = ref(false)

  // 导出
  const exportLoading = ref(false)

  // 批量操作
  const selectedIds = ref<number[]>([])
  const batchLoading = ref(false)

  const hasData = computed(() => list.value.length > 0)

  const patchOperationLockState = (recordId: number, operationLock: SampleOperationLock | null) => {
    const normalizedLock = operationLock?.locked ? operationLock : { locked: false }
    list.value = list.value.map(record => record.id === recordId ? { ...record, operationLock: normalizedLock } : record)
    reminderList.value = reminderList.value.map(record => record.id === recordId ? { ...record, operationLock: normalizedLock } : record)
    if (currentRecord.value?.id === recordId) {
      currentRecord.value = { ...currentRecord.value, operationLock: normalizedLock }
    }
    if (editTargetRecord.value?.id === recordId) {
      editTargetRecord.value = { ...editTargetRecord.value, operationLock: normalizedLock }
    }
    if (manualDisposalTargetRecord.value?.id === recordId) {
      manualDisposalTargetRecord.value = { ...manualDisposalTargetRecord.value, operationLock: normalizedLock }
    }
    if (registerTargetRecord.value?.id === recordId) {
      registerTargetRecord.value = { ...registerTargetRecord.value, operationLock: normalizedLock }
    }
  }

  const clearOperationLockState = (recordId: number) => {
    patchOperationLockState(recordId, { locked: false })
  }

  const clearOperationLockTimers = (session: ActiveOperationLockSession | null) => {
    if (!session) return
    if (session.refreshTimer) {
      clearInterval(session.refreshTimer)
      session.refreshTimer = null
    }
    if (session.inactivityTimer) {
      clearTimeout(session.inactivityTimer)
      session.inactivityTimer = null
    }
  }

  const stopOperationActivityListeners = () => {
    if (typeof window === 'undefined') return
    OPERATION_LOCK_ACTIVITY_EVENTS.forEach(eventName => {
      window.removeEventListener(eventName, markOperationLockActivity)
    })
    window.removeEventListener('beforeunload', releaseOperationLockOnBeforeUnload)
  }

  const closeLockBoundDialogs = (recordId?: number) => {
    if (recordId == null || disposalTargetId.value === recordId) {
      disposalFormVisible.value = false
      disposalTargetId.value = null
    }
    if (recordId == null || editTargetRecord.value?.id === recordId) {
      editFormVisible.value = false
      editTargetRecord.value = null
    }
    if (recordId == null || manualDisposalTargetRecord.value?.id === recordId) {
      manualDisposalFormVisible.value = false
      manualDisposalTargetRecord.value = null
    }
    if (recordId == null || registerTargetRecord.value?.id === recordId) {
      registerFormVisible.value = false
      registerTargetRecord.value = null
    }
  }

  const clearActiveOperationLock = (recordId?: number) => {
    const session = activeOperationLock.value
    if (!session) return
    clearOperationLockTimers(session)
    stopOperationActivityListeners()
    clearOperationLockState(recordId ?? session.recordId)
    activeOperationLock.value = null
    operationLockRefreshing.value = false
  }

  const handleManagedOperationLockExpired = async (message = '当前操作锁已失效，已自动退出当前操作页面') => {
    const recordId = activeOperationLock.value?.recordId
    clearActiveOperationLock(recordId)
    closeLockBoundDialogs(recordId)
    ElMessage.warning(message)
    await Promise.allSettled([fetchList(), refreshCurrentDetail(), fetchReminders()])
  }

  const resetOperationLockIdleTimer = () => {
    const session = activeOperationLock.value
    if (!session) return
    if (session.inactivityTimer) {
      clearTimeout(session.inactivityTimer)
    }
    session.inactivityTimer = setTimeout(async () => {
      const expiredSession = activeOperationLock.value
      if (!expiredSession) return
      try {
        await releaseSampleOperationLock(expiredSession.recordId, expiredSession.lockToken)
      } catch {
        // silent
      } finally {
        clearActiveOperationLock(expiredSession.recordId)
        closeLockBoundDialogs(expiredSession.recordId)
        ElMessage.warning('当前操作长时间无操作，系统已自动释放占用锁')
        await Promise.allSettled([fetchList(), refreshCurrentDetail(), fetchReminders()])
      }
    }, OPERATION_LOCK_IDLE_TIMEOUT_MS)
  }

  function markOperationLockActivity() {
    resetOperationLockIdleTimer()
  }

  const refreshManagedOperationLock = async () => {
    const session = activeOperationLock.value
    if (!session || operationLockRefreshing.value) return
    operationLockRefreshing.value = true
    try {
      const res = await refreshSampleOperationLock(session.recordId, session.lockToken)
      if (res.code === 'SUCCESS' && res.data) {
        patchOperationLockState(session.recordId, res.data)
      }
    } catch {
      await handleManagedOperationLockExpired()
    } finally {
      operationLockRefreshing.value = false
    }
  }

  function releaseOperationLockOnBeforeUnload() {
    const session = activeOperationLock.value
    if (!session || typeof window === 'undefined') return
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
      const token = localStorage.getItem('token')
      void fetch(`${baseUrl}/v1/sample/records/${session.recordId}/operation-lock/release`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          'X-Source-Terminal': 'Web'
        },
        body: JSON.stringify({ lockToken: session.lockToken }),
        keepalive: true
      })
    } catch {
      // ignore
    }
  }

  const startManagedOperationLock = (recordId: number, operationType: SampleOperationType, operationLock: SampleOperationLock) => {
    if (!operationLock.locked || !operationLock.lockToken) {
      throw new Error('未获取到有效操作锁')
    }
    clearActiveOperationLock()
    const session: ActiveOperationLockSession = {
      recordId,
      operationType,
      lockToken: operationLock.lockToken,
      refreshTimer: setInterval(() => {
        void refreshManagedOperationLock()
      }, OPERATION_LOCK_HEARTBEAT_MS),
      inactivityTimer: null
    }
    activeOperationLock.value = session
    patchOperationLockState(recordId, operationLock)
    resetOperationLockIdleTimer()
    if (typeof window !== 'undefined') {
      OPERATION_LOCK_ACTIVITY_EVENTS.forEach(eventName => {
        window.addEventListener(eventName, markOperationLockActivity, { passive: true })
      })
      window.addEventListener('beforeunload', releaseOperationLockOnBeforeUnload)
    }
  }

  const acquireManagedOperationLock = async (recordId: number, operationType: SampleOperationType) => {
    const currentSession = activeOperationLock.value
    if (currentSession && currentSession.recordId === recordId && currentSession.operationType === operationType) {
      markOperationLockActivity()
      return true
    }
    if (currentSession) {
      await releaseManagedOperationLock()
    }
    const res = await acquireSampleOperationLock(recordId, operationType)
    if (res.code === 'SUCCESS' && res.data) {
      startManagedOperationLock(recordId, operationType, res.data)
      return true
    }
    return false
  }

  const releaseManagedOperationLock = async () => {
    const session = activeOperationLock.value
    if (!session) return
    try {
      await releaseSampleOperationLock(session.recordId, session.lockToken)
    } catch {
      // silent
    } finally {
      clearActiveOperationLock(session.recordId)
    }
  }

  const runWithOneShotOperationLock = async <T>(
    recordId: number,
    operationType: SampleOperationType,
    executor: (lockToken: string) => Promise<T>
  ): Promise<T | false> => {
    try {
      const lockRes = await acquireSampleOperationLock(recordId, operationType)
      if (lockRes.code !== 'SUCCESS' || !lockRes.data?.lockToken) {
        return false
      }
      const lockToken = lockRes.data.lockToken
      try {
        return await executor(lockToken)
      } finally {
        try {
          await releaseSampleOperationLock(recordId, lockToken)
        } catch {
          // silent
        }
      }
    } catch {
      return false
    }
  }

  const fetchDashboard = async () => {
    try {
      const res = await getSampleDashboard()
      if (res.code === 'SUCCESS' && res.data) {
        dashboard.value = res.data
      }
    } catch (error: any) {
      console.error('获取留样看板失败:', error)
    }
  }

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await getSampleRecordList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list || []
        total.value = res.data.total || 0
      }
    } catch (error: any) {
      console.error('获取留样记录失败:', error)
    } finally {
      loading.value = false
    }
  }

  const refresh = async () => {
    await Promise.all([fetchDashboard(), fetchList()])
  }

  /** 详情弹窗打开时静默刷新当前记录数据 */
  const refreshCurrentDetail = async () => {
    if (!detailVisible.value || !currentRecord.value) return
    const id = currentRecord.value.id
    try {
      const res = await getSampleRecordDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentRecord.value = res.data
        if (res.data.aiAnalysisResult && typeof res.data.aiAnalysisResult === 'object') {
          aiEvaluateResult.value = res.data.aiAnalysisResult as AiEvaluateResult
        }
        fetchOperationLogs(id)
      }
    } catch {
      // silent
    }
  }

  const search = async (params: SampleRecordQuery) => {
    searchParams.value = { ...searchParams.value, ...params }
    pageNum.value = 1
    await refresh()
  }

  const resetSearch = async () => {
    searchParams.value = {
      status: '',
      sampleDate: '',
      sampleDateEnd: '',
      mealType: '',
      menuName: '',
      showRollbackIsolated: false
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

  const openDetail = async (id: number) => {
    detailLoading.value = true
    detailVisible.value = true
    currentRecord.value = null
    aiEvaluateResult.value = null
    operationLogs.value = []
    try {
      const res = await getSampleRecordDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentRecord.value = res.data
        if (res.data.aiAnalysisResult && typeof res.data.aiAnalysisResult === 'object') {
          aiEvaluateResult.value = res.data.aiAnalysisResult as AiEvaluateResult
        }
      }
      // 并行获取操作日志（不阻塞详情展示）
      fetchOperationLogs(id)
    } catch (error: any) {
      detailVisible.value = false
    } finally {
      detailLoading.value = false
    }
  }

  const closeDetail = () => {
    detailVisible.value = false
    currentRecord.value = null
    aiEvaluateResult.value = null
    operationLogs.value = []
  }

  const submitRecord = async (payload: SampleRecordCreatePayload) => {
    formLoading.value = true
    try {
      const res = await createSampleRecord(payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('新增留样任务成功')
        formVisible.value = false
        manualTaskOptions.value = []
        await refresh()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      formLoading.value = false
    }
  }

  const submitHistoryRecord = async (payload: SampleHistoryRecordCreatePayload) => {
    historyFormLoading.value = true
    try {
      const res = await createSampleHistoryRecord(payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('历史补录留样成功')
        historyFormVisible.value = false
        await refresh()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      historyFormLoading.value = false
    }
  }

  const fetchManualTaskOptions = async () => {
    try {
      const res = await getSampleManualTaskOptions()
      if (res.code === 'SUCCESS' && res.data) {
        manualTaskOptions.value = res.data
        return res.data
      }
    } catch (error: any) {
      manualTaskOptions.value = []
    }
    return []
  }

  const openRegisterForm = async (id: number) => {
    registerFormLoading.value = true
    try {
      const locked = await acquireManagedOperationLock(id, 'register')
      if (!locked) {
        return
      }
      const res = await getSampleRecordDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        registerTargetRecord.value = res.data
        registerFormVisible.value = true
      } else {
        await releaseManagedOperationLock()
      }
    } catch (error: any) {
      await releaseManagedOperationLock()
      registerTargetRecord.value = null
    } finally {
      registerFormLoading.value = false
    }
  }

  const closeRegisterForm = () => {
    registerFormVisible.value = false
    registerTargetRecord.value = null
    void releaseManagedOperationLock()
  }

  const submitRegister = async (payload: SampleRecordRegisterPayload) => {
    if (!registerTargetRecord.value) return false
    registerFormLoading.value = true
    try {
      const res = await registerSampleRecord(registerTargetRecord.value.id, payload, activeOperationLock.value?.lockToken)
      if (res.code === 'SUCCESS') {
        ElMessage.success('留样登记成功')
        closeRegisterForm()
        await refresh()
        await refreshCurrentDetail()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      registerFormLoading.value = false
    }
  }

  // ===== 销样 =====

  const openDisposalForm = async (id: number) => {
    const locked = await acquireManagedOperationLock(id, 'dispose')
    if (!locked) {
      return
    }
    disposalTargetId.value = id
    disposalFormVisible.value = true
  }

  const closeDisposalForm = () => {
    disposalFormVisible.value = false
    disposalTargetId.value = null
    void releaseManagedOperationLock()
  }

  const submitDisposal = async (payload: DisposalPayload) => {
    if (!disposalTargetId.value) return false
    disposalLoading.value = true
    try {
      const res = await executeDisposal(disposalTargetId.value, payload, activeOperationLock.value?.lockToken)
      if (res.code === 'SUCCESS') {
        ElMessage.success('销样成功')
        closeDisposalForm()
        await refresh()
        await refreshCurrentDetail()
        fetchReminders()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      disposalLoading.value = false
    }
  }

  const openManualDisposalForm = async (id: number) => {
    manualDisposalLoading.value = true
    try {
      const locked = await acquireManagedOperationLock(id, 'manual_disposal_supplement')
      if (!locked) {
        return
      }
      const res = await getSampleRecordDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        manualDisposalTargetRecord.value = res.data
        manualDisposalFormVisible.value = true
      } else {
        await releaseManagedOperationLock()
      }
    } catch (error: any) {
      await releaseManagedOperationLock()
      manualDisposalTargetRecord.value = null
    } finally {
      manualDisposalLoading.value = false
    }
  }

  const closeManualDisposalForm = () => {
    manualDisposalFormVisible.value = false
    manualDisposalTargetRecord.value = null
    void releaseManagedOperationLock()
  }

  const submitManualDisposal = async (payload: ManualDisposalSupplementPayload) => {
    if (!manualDisposalTargetRecord.value) return false
    manualDisposalLoading.value = true
    try {
      const res = await manualSupplementDisposal(manualDisposalTargetRecord.value.id, payload, activeOperationLock.value?.lockToken)
      if (res.code === 'SUCCESS') {
        ElMessage.success('销样手工补录成功')
        closeManualDisposalForm()
        await refresh()
        await refreshCurrentDetail()
        fetchReminders()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      manualDisposalLoading.value = false
    }
  }

  // ===== 销样提醒 =====

  const fetchReminders = async () => {
    reminderLoading.value = true
    try {
      const res = await getDisposalReminders({
        pageNum: reminderPageNum.value,
        pageSize: reminderPageSize.value
      })
      if (res.code === 'SUCCESS' && res.data) {
        reminderList.value = res.data.list || []
        reminderTotal.value = res.data.total || 0
      }
    } catch (error: any) {
      console.error('获取销样提醒失败:', error)
    } finally {
      reminderLoading.value = false
    }
  }

  const changeReminderPage = async (page: number) => {
    reminderPageNum.value = page
    await fetchReminders()
  }

  const changeReminderPageSize = async (size: number) => {
    reminderPageSize.value = size
    reminderPageNum.value = 1
    await fetchReminders()
  }

  // ===== AI评估 =====

  const triggerAiEvaluate = async (id: number) => {
    aiEvaluateLoading.value = true
    aiEvaluateError.value = ''
    try {
      const result = await runWithOneShotOperationLock(id, 'ai_evaluate', async (lockToken) => {
        const res = await aiEvaluate(id, lockToken)
        if (res.code === 'SUCCESS' && res.data) {
          aiEvaluateResult.value = res.data
          ElMessage.success('AI评估完成')
          await fetchList()
          if (detailVisible.value) {
            await refreshCurrentDetail()
          }
          return true
        }
        return false
      })
      return result || false
    } catch (error: any) {
      aiEvaluateError.value = error.message || 'AI评估服务暂不可用，请稍后重试'
      return false
    } finally {
      aiEvaluateLoading.value = false
    }
  }

  // ===== 编辑 =====

  const fetchRecordForEdit = async (id: number) => {
    try {
      const locked = await acquireManagedOperationLock(id, 'edit')
      if (!locked) {
        return
      }
      const res = await getSampleRecordDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        editTargetRecord.value = res.data
        editFormVisible.value = true
      } else {
        await releaseManagedOperationLock()
      }
    } catch (error: any) {
      await releaseManagedOperationLock()
    }
  }

  const closeEditForm = () => {
    editFormVisible.value = false
    editTargetRecord.value = null
    void releaseManagedOperationLock()
  }

  const submitEdit = async (payload: SampleRecordUpdatePayload) => {
    if (!editTargetRecord.value) return false
    const result = await editRecord(editTargetRecord.value.id, payload)
    if (result) {
      closeEditForm()
    }
    return result
  }

  const editRecord = async (id: number, payload: SampleRecordUpdatePayload) => {
    try {
      const res = await updateSampleRecord(id, payload, activeOperationLock.value?.lockToken)
      if (res.code === 'SUCCESS') {
        ElMessage.success('编辑成功')
        await refresh()
        await refreshCurrentDetail()
        return true
      }
      return false
    } catch (error: any) {
      return false
    }
  }

  // ===== 作废 =====

  const voidRecord = async (id: number, reason: string) => {
    try {
      const result = await runWithOneShotOperationLock(id, 'void', async (lockToken) => {
        const res = await voidSampleRecord(id, reason, lockToken)
        if (res.code === 'SUCCESS') {
          ElMessage.success('作废成功')
          await refresh()
          await refreshCurrentDetail()
          return true
        }
        return false
      })
      return result || false
    } catch (error: any) {
      return false
    }
  }

  // ===== 归档 =====

  const archiveRecord = async (id: number) => {
    try {
      const result = await runWithOneShotOperationLock(id, 'archive', async (lockToken) => {
        const res = await archiveSampleRecord(id, lockToken)
        if (res.code === 'SUCCESS') {
          ElMessage.success('归档成功')
          await refresh()
          await refreshCurrentDetail()
          return true
        }
        return false
      })
      return result || false
    } catch (error: any) {
      return false
    }
  }

  // ===== 操作日志 =====

  const fetchOperationLogs = async (id: number) => {
    try {
      const res = await getSampleOperationLogs(id)
      if (res.code === 'SUCCESS' && res.data) {
        operationLogs.value = res.data
      }
    } catch {
      operationLogs.value = []
    }
  }

  // ===== 导出 =====

  const exportRecords = async (params: SampleRecordQuery) => {
    exportLoading.value = true
    try {
      ElMessage.info('正在生成留样Excel台账，请稍候...')
      await exportSampleRecords(params)
      ElMessage.success('导出成功，留样台账已开始下载')
    } catch (error: any) {
      // Error displayed by interceptor
    } finally {
      exportLoading.value = false
    }
  }

  // ===== 批量操作 =====

  const updateSelectedIds = (ids: number[]) => {
    selectedIds.value = ids
  }

  const clearSelection = () => {
    selectedIds.value = []
  }

  const batchVoid = async (reason: string) => {
    if (selectedIds.value.length === 0) return false
    batchLoading.value = true
    try {
      const selectedRecords = list.value.filter(r => selectedIds.value.includes(r.id))
      const voidable = selectedRecords.filter(r => !['voided', 'archived'].includes(r.status))
      const nonVoidable = selectedRecords.filter(r => ['voided', 'archived'].includes(r.status))

      if (nonVoidable.length > 0) {
        const voidReasonMap: Record<string, string> = {
          voided: '已作废不可再次作废',
          archived: '已归档不可作废'
        }
        const details = nonVoidable.map(r => `${r.sampleNo}：${voidReasonMap[r.status] || '不可作废'}`).join('；')
        if (voidable.length === 0) {
          ElMessage.error(`所选记录均不可作废：${details}`)
          return false
        }
        ElMessage.warning(`${nonVoidable.length} 条记录不可作废：${details}`)
      }

      if (voidable.length === 0) {
        ElMessage.error('所选记录均不可作废')
        return false
      }

      const res = await batchVoidRecords(voidable.map(r => r.id), reason)
      if (res.code === 'SUCCESS') {
        ElMessage.success(`成功作废 ${voidable.length} 条记录${nonVoidable.length > 0 ? `，${nonVoidable.length} 条已跳过` : ''}`)
        clearSelection()
        await refresh()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      batchLoading.value = false
    }
  }

  const batchArchive = async () => {
    if (selectedIds.value.length === 0) return false
    batchLoading.value = true
    try {
      const selectedRecords = list.value.filter(r => selectedIds.value.includes(r.id))
      const archivable = selectedRecords.filter(r => r.status === 'disposed')
      const nonArchivable = selectedRecords.filter(r => r.status !== 'disposed')

      if (nonArchivable.length > 0) {
        const archiveReasonMap: Record<string, string> = {
          voided: '已作废不可归档',
          archived: '已归档不可再次归档'
        }
        const details = nonArchivable.map(r => {
          const reason = archiveReasonMap[r.status] || `${SAMPLE_STATUS_MAP[r.status]?.label || r.status}不可归档`
          return `${r.sampleNo}：${reason}`
        }).join('；')
        if (archivable.length === 0) {
          ElMessage.error(`所选记录均不可归档：${details}`)
          return false
        }
        ElMessage.warning(`${nonArchivable.length} 条记录不可归档：${details}`)
      }

      if (archivable.length === 0) {
        ElMessage.error('所选记录均不可归档')
        return false
      }

      const res = await batchArchiveRecords(archivable.map(r => r.id))
      if (res.code === 'SUCCESS') {
        ElMessage.success(`成功归档 ${archivable.length} 条记录${nonArchivable.length > 0 ? `，${nonArchivable.length} 条已跳过` : ''}`)
        clearSelection()
        await refresh()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      batchLoading.value = false
    }
  }

  const init = async () => {
    await refresh()
    fetchReminders()
  }

  watch(registerFormVisible, (visible, oldVisible) => {
    if (!visible && oldVisible) {
      registerTargetRecord.value = null
      if (activeOperationLock.value?.operationType === 'register') {
        void releaseManagedOperationLock()
      }
    }
  })

  watch(editFormVisible, (visible, oldVisible) => {
    if (!visible && oldVisible) {
      editTargetRecord.value = null
      if (activeOperationLock.value?.operationType === 'edit') {
        void releaseManagedOperationLock()
      }
    }
  })

  watch(disposalFormVisible, (visible, oldVisible) => {
    if (!visible && oldVisible) {
      disposalTargetId.value = null
      if (activeOperationLock.value?.operationType === 'dispose') {
        void releaseManagedOperationLock()
      }
    }
  })

  watch(manualDisposalFormVisible, (visible, oldVisible) => {
    if (!visible && oldVisible) {
      manualDisposalTargetRecord.value = null
      if (activeOperationLock.value?.operationType === 'manual_disposal_supplement') {
        void releaseManagedOperationLock()
      }
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
    currentRecord,
    detailVisible,
    detailLoading,
    formVisible,
    formLoading,
    manualTaskOptions,
    historyFormVisible,
    historyFormLoading,
    disposalFormVisible,
    disposalLoading,
    disposalTargetId,
    manualDisposalFormVisible,
    manualDisposalLoading,
    manualDisposalTargetRecord,
    editFormVisible,
    editTargetRecord,
    registerFormVisible,
    registerFormLoading,
    registerTargetRecord,
    reminderList,
    reminderTotal,
    reminderLoading,
    reminderPageNum,
    reminderPageSize,
    aiEvaluateResult,
    aiEvaluateLoading,
    aiEvaluateError,
    operationLogs,
    activeOperationLock,
    exportLoading,
    selectedIds,
    batchLoading,
    hasData,
    fetchDashboard,
    fetchList,
    refresh,
    search,
    resetSearch,
    changePage,
    changePageSize,
    openDetail,
    closeDetail,
    submitRecord,
    submitHistoryRecord,
    fetchManualTaskOptions,
    openRegisterForm,
    closeRegisterForm,
    submitRegister,
    openDisposalForm,
    closeDisposalForm,
    submitDisposal,
    openManualDisposalForm,
    closeManualDisposalForm,
    submitManualDisposal,
    fetchReminders,
    changeReminderPage,
    changeReminderPageSize,
    triggerAiEvaluate,
    fetchRecordForEdit,
    closeEditForm,
    submitEdit,
    editRecord,
    voidRecord,
    archiveRecord,
    exportRecords,
    updateSelectedIds,
    clearSelection,
    batchVoid,
    batchArchive,
    fetchOperationLogs,
    init
  }
})
