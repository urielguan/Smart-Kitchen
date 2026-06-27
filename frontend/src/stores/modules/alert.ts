import { defineStore } from 'pinia'
import { h, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { alertApi } from '@/api/modules/alert'
import { employeeApi } from '@/api/modules/employee'
import type {
  Alert,
  AlertQuery,
  AlertDashboard,
  AlertDispatchForm,
  AlertDispatchResult,
  AlertDispatch,
  AlertDispatchQuery,
  AlertProcessForm,
  AlertReviewDTO,
  AlertCloseDTO,
  HandlerOption,
} from '@/types/alert'
import type { Employee } from '@/types/employee'

/** 构建告警确认弹窗 message（图标 + 标题 + 描述） */
const renderConfirmMessage = (title: string, description: string) => () =>
  h('div', { class: 'alert-confirm' }, [
    h('div', { class: 'alert-confirm__content' }, [
      h(WarningFilled, { class: 'alert-confirm__icon' }),
      h('div', { class: 'alert-confirm__text' }, [
        h('div', { class: 'alert-confirm__title' }, title),
        h('div', { class: 'alert-confirm__description' }, description),
      ]),
    ]),
  ])

export const useAlertStore = defineStore('alert', () => {
  // ==================== 告警列表状态 ====================
  const list = ref<Alert[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const listError = ref<string | null>(null)
  const statsError = ref<string | null>(null)
  const dashboard = ref<AlertDashboard>({
    totalCount: 0, criticalCount: 0, errorCount: 0, warningCount: 0, infoCount: 0,
    pendingCount: 0, assignedCount: 0, closedCount: 0, handledCount: 0, reviewedCount: 0,
    alertTypeStats: [], alertTrends: [],
  })
  const searchParams = ref<AlertQuery>({})

  // ==================== 详情弹窗 ====================
  const detailVisible = ref(false)
  const alertDetailId = ref<number | null>(null)
  const alertDetailDispatchId = ref<number | null>(null)

  // ==================== 派单弹窗 ====================
  const dispatchFormVisible = ref(false)
  const dispatchAlertId = ref<number | null>(null)

  // ==================== 处理工单弹窗 ====================
  const processFormVisible = ref(false)
  const processDispatchId = ref<number | null>(null)

  // ==================== 派单处理人候选 ====================
  const dispatchEmployees = ref<Employee[]>([])
  const handlers = computed<HandlerOption[]>(() => {
    return dispatchEmployees.value
      .filter(emp => emp.status === 'active')
      .map(emp => ({
        id: emp.id,
        name: emp.realName,
        orgId: emp.orgId,
        orgName: emp.orgName,
        position: emp.position || '',
      }))
  })

  // ==================== 工单列表状态（告警处理 Tab） ====================
  const dispatchList = ref<AlertDispatch[]>([])
  const dispatchTotal = ref(0)
  const dispatchPageNum = ref(1)
  const dispatchPageSize = ref(10)
  const dispatchLoading = ref(false)
  const dispatchSearchParams = ref<Partial<AlertDispatchQuery>>({
    status: undefined,
    dispatchType: undefined,
    handlerName: '',
    startTime: undefined,
    endTime: undefined,
  })

  // ==================== 告警列表方法 ====================
  const exportAlerts = async () => {
    try {
      await alertApi.exportAlerts(searchParams.value)
      ElMessage.success('导出成功')
    } catch (error: any) {
      ElMessage.error(error.message || '导出失败')
    }
  }

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await alertApi.getList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value,
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list
        total.value = res.data.total
        listError.value = null
      }
    } catch (e) {
      listError.value = (e as any)?.message || '获取告警列表失败'
      console.error('获取告警列表失败', e)
    } finally {
      loading.value = false
    }
  }

  const fetchDashboard = async () => {
    try {
      const res = await alertApi.getDashboard(searchParams.value.orgId)
      if (res.code === 'SUCCESS' && res.data) {
        const d = res.data
        dashboard.value = {
          totalCount: d.totalCount ?? 0,
          criticalCount: d.criticalCount ?? 0,
          errorCount: d.errorCount ?? 0,
          warningCount: d.warningCount ?? 0,
          infoCount: d.infoCount ?? 0,
          pendingCount: d.pendingCount ?? 0,
          assignedCount: d.assignedCount ?? 0,
          closedCount: d.closedCount ?? 0,
          handledCount: d.handledCount ?? 0,
          reviewedCount: d.reviewedCount ?? 0,
          alertTypeStats: d.alertTypeStats ?? [],
          alertTrends: d.alertTrends ?? [],
        }
        statsError.value = null
      }
    } catch (e) {
      statsError.value = (e as any)?.message || '获取告警看板失败'
      console.error('获取告警看板失败', e)
    }
  }

  const init = () => Promise.all([fetchList(), fetchDashboard()])

  const search = async (params: AlertQuery) => {
    searchParams.value = params
    pageNum.value = 1
    await fetchList()
  }

  const resetSearch = async () => {
    searchParams.value = {}
    pageNum.value = 1
    await fetchList()
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

  // ==================== 详情 ====================
  const openDetail = (id: number, options?: { dispatchId?: number | null }) => {
    alertDetailId.value = id
    alertDetailDispatchId.value = options?.dispatchId ?? null
    detailVisible.value = true
  }

  const closeDetail = () => {
    detailVisible.value = false
    alertDetailId.value = null
    alertDetailDispatchId.value = null
  }

  // ==================== 派单操作 ====================
  /** 自动派单 */
  const autoDispatch = async (alertId: number) => {
    try {
      await ElMessageBox({
        title: '自动派单确认',
        message: renderConfirmMessage('确认自动派单', '系统将根据规则自动分配处理人，确认自动派单？'),
        customClass: 'alert-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认派单',
        cancelButtonText: '取消',
      })

      const res = await alertApi.dispatch(alertId, { dispatchType: 'auto' })
      if (res.code === 'SUCCESS') {
        const { alertNo, handlerName, deadline } = res.data || {}
        const deadlineStr = deadline ? `，处理截止 ${deadline.replace('T', ' ')}` : ''
        ElMessage.success(`【${alertNo}】已自动派单至 ${handlerName}${deadlineStr}`)
        await init()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // Error displayed by interceptor
      }
    }
  }

  /** 打开人工派单弹窗 */
  const openDispatchForm = async (alertId: number) => {
    await fetchDispatchEmployees()
    dispatchAlertId.value = alertId
    dispatchFormVisible.value = true
  }

  /** 关闭派单弹窗 */
  const closeDispatchForm = () => {
    dispatchFormVisible.value = false
    dispatchAlertId.value = null
  }

  /** 提交人工派单 */
  const submitDispatch = async (data: AlertDispatchForm) => {
    if (!dispatchAlertId.value) return
    try {
      const res = await alertApi.dispatch(dispatchAlertId.value, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('派单成功')
        closeDispatchForm()
        await init()
        return res.data || null
      }
    } catch (e) {
      // Error displayed by interceptor
    }
    return null
  }

  const createDispatch = async (alertId: number, data: AlertDispatchForm): Promise<AlertDispatchResult | null> => {
    try {
      const res = await alertApi.dispatch(alertId, data)
      if (res.code === 'SUCCESS') {
        await Promise.all([fetchList(), fetchDashboard(), fetchDispatchList()])
        return res.data || null
      }
    } catch (e) {
      // Error displayed by interceptor
    }
    return null
  }

  // ==================== 处理工单 ====================
  const openProcessForm = (dispatchId: number) => {
    processDispatchId.value = dispatchId
    processFormVisible.value = true
  }

  const closeProcessForm = () => {
    processFormVisible.value = false
    processDispatchId.value = null
  }

  const submitProcess = async (data: AlertProcessForm) => {
    if (!processDispatchId.value) return
    try {
      const payload: Record<string, any> = {
        handleResult: data.handleResult,
        handleAttachments: data.handleAttachments || undefined,
      }
      const res = await alertApi.processDispatch(processDispatchId.value, payload as AlertProcessForm)
      if (res.code === 'SUCCESS') {
        ElMessage.success('处理成功')
        closeProcessForm()
        await fetchDispatchList()
      }
    } catch (e) {
      // Error displayed by interceptor
    }
  }

  // ==================== 复核工单 ====================
  const reviewDispatch = async (dispatchId: number, data: AlertReviewDTO) => {
    try {
      const res = await alertApi.reviewDispatch(dispatchId, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success(data.reviewResult === 'approved' ? '复核通过' : '已驳回并退回处理')
        await Promise.all([fetchDispatchList(), fetchList(), fetchDashboard()])
      }
    } catch (e) {
      // Error displayed by interceptor
    }
  }

  // ==================== 关闭告警 ====================
  const closeAlert = async (id: number, data: AlertCloseDTO) => {
    try {
      const res = await alertApi.close(id, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('关闭成功')
        await Promise.all([init(), fetchDispatchList()])
      }
    } catch {
      // Error displayed by interceptor
    }
  }

  // ==================== 派单处理人候选 ====================
  const fetchDispatchEmployees = async () => {
    try {
      const pageSize = 200
      let pageNum = 1
      let allEmployees: Employee[] = []

      while (true) {
        const res = await employeeApi.getList({ pageNum, pageSize, status: 'active', accountStatus: 'active' })
        if (res.code !== 'SUCCESS' || !res.data) break

        const list = res.data.list || []
        allEmployees = allEmployees.concat(list)

        if (list.length < pageSize || allEmployees.length >= res.data.total) {
          break
        }
        pageNum += 1
      }

      dispatchEmployees.value = allEmployees
    } catch (error) {
      console.error('获取派单处理人失败:', error)
      dispatchEmployees.value = []
    }
  }

  // ==================== 工单列表（告警处理 Tab） ====================
  const fetchDispatchList = async () => {
    dispatchLoading.value = true
    try {
      const params: AlertDispatchQuery = {
        pageNum: dispatchPageNum.value,
        pageSize: dispatchPageSize.value,
        ...dispatchSearchParams.value,
      }
      const res = await alertApi.listDispatches(params)
      if (res.code === 'SUCCESS' && res.data) {
        dispatchList.value = res.data.list
        dispatchTotal.value = res.data.total
      }
    } catch (error: any) {
      ElMessage.error(error.message || '获取工单列表失败')
    } finally {
      dispatchLoading.value = false
    }
  }

  const searchDispatches = async (params: Partial<AlertDispatchQuery>) => {
    dispatchSearchParams.value = {
      status: params.status,
      dispatchType: params.dispatchType,
      handlerName: params.handlerName ?? '',
      startTime: params.startTime,
      endTime: params.endTime,
    }
    dispatchPageNum.value = 1
    await fetchDispatchList()
  }

  const resetDispatchSearch = async () => {
    dispatchSearchParams.value = {
      status: undefined,
      dispatchType: undefined,
      handlerName: '',
      startTime: undefined,
      endTime: undefined,
    }
    dispatchPageNum.value = 1
    await fetchDispatchList()
  }

  const changeDispatchPage = async (page: number) => {
    dispatchPageNum.value = page
    await fetchDispatchList()
  }

  const changeDispatchPageSize = async (size: number) => {
    dispatchPageSize.value = size
    dispatchPageNum.value = 1
    await fetchDispatchList()
  }

  return {
    // 告警列表状态
    list, total, pageNum, pageSize, loading, listError, statsError, dashboard, searchParams,

    // 详情
    detailVisible, alertDetailId, alertDetailDispatchId,

    // 派单
    dispatchFormVisible, dispatchAlertId, handlers,

    // 处理工单
    processFormVisible, processDispatchId,

    // 工单列表
    dispatchList, dispatchTotal, dispatchPageNum, dispatchPageSize, dispatchLoading, dispatchSearchParams,

    // 告警列表方法
    fetchList, fetchDashboard, init, search, resetSearch,
    changePage, changePageSize, openDetail, closeDetail, exportAlerts,

    // 派单方法
    autoDispatch, openDispatchForm, closeDispatchForm, submitDispatch, createDispatch, fetchDispatchEmployees,

    // 处理工单
    openProcessForm, closeProcessForm, submitProcess,

    // 复核
    reviewDispatch,

    // 关闭
    closeAlert,

    // 工单列表方法
    fetchDispatchList, searchDispatches, resetDispatchSearch,
    changeDispatchPage, changeDispatchPageSize,
  }
})
