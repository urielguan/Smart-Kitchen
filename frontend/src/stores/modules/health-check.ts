import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getHealthDashboard,
  getPendingHealthChecks,
  getHealthCheckRecords,
  createHealthCheckRecord,
  getHealthCheckRecordDetail,
  updateHealthCheckRecord,
  archiveHealthCheckRecord,
  generatePendingTasks,
} from '@/api/modules/health-check'
import type {
  HealthDashboard,
  HealthCheckRecord,
  HealthCheckRecordDetail,
  HealthCheckCreatePayload,
  HealthCheckUpdatePayload,
} from '@/types/health-check'

export const useHealthCheckStore = defineStore('health-check', () => {
  // State
  const dashboard = ref<HealthDashboard | null>(null)
  const pendingList = ref<HealthCheckRecord[]>([])
  const pendingTotal = ref(0)
  const pendingPage = ref(1)
  const pendingPageSize = ref(20)
  const pendingLoading = ref(false)
  const list = ref<HealthCheckRecord[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const currentRecord = ref<HealthCheckRecordDetail | null>(null)
  const detailVisible = ref(false)
  const formVisible = ref(false)
  const formLoading = ref(false)

  // 搜索参数持久化
  const searchParams = ref<Record<string, any>>({
    checkDateStart: '',
    checkDateEnd: '',
    status: '',
    checkResult: '',
    employeeName: ''
  })
  const pendingSearchParams = ref<Record<string, any>>({
    checkDate: '',
    orgId: undefined,
    status: '',
    employeeName: ''
  })

  // Getters
  const pendingCount = computed(() => dashboard.value?.pendingCount ?? 0)
  const completedCount = computed(() => dashboard.value?.completedCount ?? 0)
  const normalCount = computed(() => dashboard.value?.normalCount ?? 0)
  const abnormalCount = computed(() => dashboard.value?.abnormalCount ?? 0)
  const hasData = computed(() => list.value.length > 0)

  // Actions
  async function init() {
    loading.value = true
    try {
      // 先检查并生成今日待晨检任务（幂等）
      await generatePendingTasks()
      // 首次加载默认查当天
      const today = new Date().toISOString().slice(0, 10)
      searchParams.value = {
        checkDateStart: today,
        checkDateEnd: today,
        status: '',
        checkResult: '',
        employeeName: ''
      }
      pendingSearchParams.value = {
        checkDate: today,
        orgId: undefined,
        status: '',
        employeeName: ''
      }
      await Promise.all([fetchDashboard(), fetchPendingList(), fetchList()])
    } catch {
      // 生成失败不影响页面加载
    } finally {
      loading.value = false
    }
  }

  /** 从其他页面返回时，仅刷新看板和待检列表，保留搜索结果 */
  async function refreshMeta() {
    try {
      await generatePendingTasks()
      await Promise.all([fetchDashboard(), fetchPendingList()])
    } catch {
    }
  }

  /** 提交/归档后刷新所有数据，保留当前搜索条件 */
  async function refreshAll() {
    await generatePendingTasks()
    await Promise.all([fetchDashboard(), fetchPendingList(), fetchList()])
  }

  async function fetchDashboard() {
    try {
      const res = await getHealthDashboard()
      if (res.code === 'SUCCESS') {
        dashboard.value = res.data
      }
    } catch (error: any) {
      console.error('获取晨检看板失败:', error)
    }
  }

  async function fetchPendingList() {
    pendingLoading.value = true
    try {
      const res = await getPendingHealthChecks({
        pageNum: pendingPage.value,
        pageSize: pendingPageSize.value,
        ...pendingSearchParams.value,
      })
      if (res.code === 'SUCCESS') {
        pendingList.value = res.data.list
        pendingTotal.value = res.data.total
      }
    } catch (error: any) {
      console.error('获取待晨检列表失败:', error)
    } finally {
      pendingLoading.value = false
    }
  }

  async function fetchList(overrideParams?: Record<string, any>) {
    loading.value = true
    try {
      const res = await getHealthCheckRecords({
        pageNum: page.value,
        pageSize: pageSize.value,
        ...(overrideParams || searchParams.value),
      })
      if (res.code === 'SUCCESS') {
        list.value = res.data.list
        total.value = res.data.total
      }
    } catch (error: any) {
      console.error('获取晨检列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  async function search(params: Record<string, any>) {
    searchParams.value = params
    page.value = 1
    await fetchList()
  }

  async function searchPending(params: Record<string, any>) {
    pendingSearchParams.value = params
    pendingPage.value = 1
    await fetchPendingList()
  }

  async function resetSearch() {
    const today = new Date().toISOString().slice(0, 10)
    searchParams.value = {
      checkDateStart: today,
      checkDateEnd: today,
      status: '',
      checkResult: '',
      employeeName: ''
    }
    page.value = 1
    await fetchList()
  }

  async function resetPendingSearch() {
    const today = new Date().toISOString().slice(0, 10)
    pendingSearchParams.value = {
      checkDate: today,
      orgId: undefined,
      status: '',
      employeeName: ''
    }
    pendingPage.value = 1
    await fetchPendingList()
  }

  async function openDetail(id: number) {
    try {
      const res = await getHealthCheckRecordDetail(id)
      if (res.code === 'SUCCESS') {
        currentRecord.value = res.data
        detailVisible.value = true
      }
    } catch (error: any) {
      // Error displayed by interceptor
    }
  }

  function closeDetail() {
    detailVisible.value = false
    currentRecord.value = null
  }

  async function submitCheck(payload: HealthCheckCreatePayload) {
    formLoading.value = true
    try {
      const res = await createHealthCheckRecord(payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('晨检记录创建成功')
        formVisible.value = false
        await refreshAll()
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      formLoading.value = false
    }
  }

  async function updateCheck(id: number, payload: HealthCheckUpdatePayload) {
    formLoading.value = true
    try {
      const res = await updateHealthCheckRecord(id, payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('更新成功')
        await openDetail(id)
        return true
      }
      return false
    } catch (error: any) {
      return false
    } finally {
      formLoading.value = false
    }
  }

  async function archiveCheck(id: number) {
    try {
      const res = await archiveHealthCheckRecord(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('归档成功')
        closeDetail()
        await refreshAll()
        return true
      }
      return false
    } catch (error: any) {
      return false
    }
  }

  function changePage(newPage: number) {
    page.value = newPage
    fetchList()
  }

  function changePageSize(newSize: number) {
    pageSize.value = newSize
    page.value = 1
    fetchList()
  }

  function changePendingPage(newPage: number) {
    pendingPage.value = newPage
    fetchPendingList()
  }

  function changePendingPageSize(newSize: number) {
    pendingPageSize.value = newSize
    pendingPage.value = 1
    fetchPendingList()
  }

  return {
    // State
    dashboard,
    pendingList,
    pendingTotal,
    pendingPage,
    pendingPageSize,
    pendingLoading,
    list,
    total,
    page,
    pageSize,
    loading,
    currentRecord,
    detailVisible,
    formVisible,
    formLoading,
    searchParams,
    pendingSearchParams,
    // Getters
    pendingCount,
    completedCount,
    normalCount,
    abnormalCount,
    hasData,
    // Actions
    init,
    refreshMeta,
    refreshAll,
    fetchDashboard,
    fetchPendingList,
    fetchList,
    search,
    searchPending,
    resetSearch,
    resetPendingSearch,
    openDetail,
    closeDetail,
    submitCheck,
    updateCheck,
    archiveCheck,
    changePage,
    changePageSize,
    changePendingPage,
    changePendingPageSize,
  }
})
