import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  CookDashboard,
  CookTask,
  CookTaskArchivePayload,
  CookTaskCompletePayload,
  CookTaskDetail,
  CookTaskQuery,
  CookTaskStartPayload,
  CookTemperaturePoint,
  CookAIMonitorRecord
} from '@/types'
import {
  archiveCookTask,
  completeCookTask,
  exportCookTasks,
  getCookDashboard,
  getCookTaskAiMonitor,
  getCookTaskDetail,
  getCookTaskList,
  getCookTaskTemperature,
  startCookTask
} from '@/api/modules/cook'

const emptyDashboard = (): CookDashboard => ({
  totalDishes: 0,
  pendingCount: 0,
  inProgressCount: 0,
  completedCount: 0,
  abnormalTemperatureCount: 0,
  completionRate: 0
})

export const useCookStore = defineStore('cook', () => {
  const list = ref<CookTask[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const dashboard = ref<CookDashboard>(emptyDashboard())
  const searchParams = ref<CookTaskQuery>({
    taskDate: '',
    mealType: '',
    status: '',
    taskNo: '',
    chefName: ''
  })
  const currentTask = ref<CookTaskDetail | null>(null)
  const temperatureRecords = ref<CookTemperaturePoint[]>([])
  const aiMonitorRecords = ref<CookAIMonitorRecord[]>([])
  const detailVisible = ref(false)
  const temperatureVisible = ref(false)
  const actionLoadingId = ref<number | null>(null)

  const hasData = computed(() => list.value.length > 0)

  const fetchDashboard = async () => {
    try {
      const res = await getCookDashboard(searchParams.value)
      if (res.code === 'SUCCESS' && res.data) {
        dashboard.value = res.data
      }
    } catch (error) {
      console.error('获取烹饪看板失败:', error)
    }
  }

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await getCookTaskList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list || []
        total.value = res.data.total || 0
      }
    } catch (error: any) {
      console.error('获取烹饪任务失败:', error)
    } finally {
      loading.value = false
    }
  }

  const refresh = async () => {
    await Promise.all([fetchDashboard(), fetchList()])
  }

  const search = async (params: CookTaskQuery) => {
    searchParams.value = { ...searchParams.value, ...params }
    pageNum.value = 1
    await refresh()
  }

  const resetSearch = async () => {
    searchParams.value = {
      taskDate: '',
      mealType: '',
      status: '',
      taskNo: '',
      chefName: ''
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
    const [detailRes, temperatureRes, aiRes] = await Promise.all([
      getCookTaskDetail(id),
      getCookTaskTemperature(id),
      getCookTaskAiMonitor(id)
    ])

    currentTask.value = detailRes.data || null
    temperatureRecords.value = temperatureRes.data || []
    aiMonitorRecords.value = aiRes.data || []

    if (currentTask.value) {
      currentTask.value.temperatureRecords = temperatureRecords.value
      currentTask.value.aiMonitorRecords = aiMonitorRecords.value
    }
  }

  const openDetail = async (id: number) => {
    await loadTaskDetail(id)
    detailVisible.value = true
  }

  const openTemperature = async (id: number) => {
    await loadTaskDetail(id)
    temperatureVisible.value = true
  }

  const closeDetail = () => {
    detailVisible.value = false
  }

  const closeTemperature = () => {
    temperatureVisible.value = false
  }

  const startTask = async (id: number, payload?: CookTaskStartPayload) => {
    actionLoadingId.value = id
    try {
      const res = await startCookTask(id, payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('已开始烹饪')
        await refresh()
      }
      return true
    } catch (error: any) {
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const completeTaskAction = async (id: number, payload?: CookTaskCompletePayload) => {
    actionLoadingId.value = id
    try {
      const res = await completeCookTask(id, payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('已完成烹饪')
        await refresh()
      }
      return true
    } catch (error: any) {
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const exportTasks = async () => {
    try {
      await exportCookTasks(searchParams.value)
      ElMessage.success('导出成功')
    } catch (error: any) {
      ElMessage.error(error?.message || '导出失败')
    }
  }

  const canArchiveTask = (task: CookTask): boolean => {
    return task.status === 'completed' && task.reviewStatus === 'approved'
  }

  const archiveTask = async (id: number, payload?: CookTaskArchivePayload) => {
    actionLoadingId.value = id
    try {
      const res = await archiveCookTask(id, payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('已归档')
        await refresh()
      }
      return true
    } catch (error: any) {
      ElMessage.error(error?.message || '归档失败')
      return false
    } finally {
      actionLoadingId.value = null
    }
  }

  const batchArchiveTasks = async (ids: number[]) => {
    try {
      const results = await Promise.all(ids.map(id => archiveCookTask(id)))
      const successCount = results.filter(r => r.code === 'SUCCESS').length
      if (successCount > 0) {
        ElMessage.success(`成功归档 ${successCount} 条记录`)
        await refresh()
      }
      if (successCount < ids.length) {
        ElMessage.warning(`${ids.length - successCount} 条记录归档失败`)
      }
      return successCount === ids.length
    } catch (error: any) {
      ElMessage.error(error?.message || '批量归档失败')
      return false
    }
  }

  const init = async () => {
    await refresh()
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    loading,
    dashboard,
    searchParams,
    currentTask,
    temperatureRecords,
    aiMonitorRecords,
    detailVisible,
    temperatureVisible,
    actionLoadingId,
    hasData,
    fetchDashboard,
    fetchList,
    refresh,
    search,
    resetSearch,
    changePage,
    changePageSize,
    loadTaskDetail,
    openDetail,
    openTemperature,
    closeDetail,
    closeTemperature,
    startTask,
    completeTaskAction,
    exportTasks,
    canArchiveTask,
    archiveTask,
    batchArchiveTasks,
    init
  }
})
