import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { outboundApi } from '@/api/modules/outbound'
import { OUTBOUND_TYPE_MAP } from '@/constants/outbound'
import type {
  OutboundImportResult,
  OutboundImportTask,
  OutboundOrder,
  OutboundOrderQuery,
  OutboundOrderStatistics,
  OutboundTypeDictionaryOption,
  OutboundTypeSelectableOption,
} from '@/types/outbound'

const FALLBACK_OUTBOUND_TYPE_CODES = Object.keys(OUTBOUND_TYPE_MAP)

export const resolveOutboundTypeOptions = (options: OutboundTypeDictionaryOption[]): OutboundTypeSelectableOption[] => {
  if (options.length > 0) {
    return options
      .filter((item) => item.status === 'active')
      .map((item) => ({
        value: item.typeCode,
        label: item.typeName,
        status: item.status,
        sortOrder: item.sortOrder ?? 0,
        typeSource: item.typeSource,
        sourceRequirementText: item.sourceRequirementText,
        requiresSourceBiz: item.requiresSourceBiz,
        approvalMode: item.approvalMode,
        supportsAiSuggestion: item.supportsAiSuggestion,
      }))
  }

  return FALLBACK_OUTBOUND_TYPE_CODES.map((code) => ({
    value: code,
    label: OUTBOUND_TYPE_MAP[code],
    status: 'active',
    sortOrder: 0,
    typeSource: 'fallback',
    sourceRequirementText: '',
    requiresSourceBiz: false,
    approvalMode: 'direct',
    supportsAiSuggestion: false,
  }))
}

export const normalizeOutboundTypeOptions = (options: OutboundTypeDictionaryOption[]) => {
  const sorted = [...options].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  const byCode = sorted.reduce<Record<string, OutboundTypeDictionaryOption>>((acc, item) => {
    acc[item.typeCode] = item
    return acc
  }, {})

  return {
    all: sorted,
    selectable: resolveOutboundTypeOptions(sorted),
    byCode,
  }
}

export const useOutboundStore = defineStore('outbound', () => {
  const list = ref<OutboundOrder[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const searchParams = ref<OutboundOrderQuery>({})

  const statistics = ref<OutboundOrderStatistics>({
    totalCount: 0,
    draftCount: 0,
    pendingCount: 0,
    approvedCount: 0,
    completedCount: 0,
    thisMonthAmount: 0,
  })

  const outboundTypeOptions = ref<OutboundTypeDictionaryOption[]>([])
  const outboundTypeOptionMap = ref<Record<string, OutboundTypeDictionaryOption>>({})
  const outboundTypeSelectOptions = ref<OutboundTypeSelectableOption[]>(resolveOutboundTypeOptions([]))
  const outboundTypeOptionsLoaded = ref(false)

  const formVisible = ref(false)
  const detailVisible = ref(false)
  const currentId = ref<number | null>(null)
  const importDialogVisible = ref(false)
  const importLoading = ref(false)
  const importTaskLoading = ref(false)
  const lastImportResult = ref<OutboundImportResult | null>(null)
  const currentImportTask = ref<OutboundImportTask | null>(null)

  const loadOutboundTypeOptions = async () => {
    if (outboundTypeOptionsLoaded.value) {
      return outboundTypeSelectOptions.value
    }

    try {
      const res = await outboundApi.getOutboundTypeOptions()
      if (res.code === 'SUCCESS' && Array.isArray(res.data)) {
        const normalized = normalizeOutboundTypeOptions(res.data)
        outboundTypeOptions.value = normalized.all
        outboundTypeOptionMap.value = normalized.byCode
        outboundTypeSelectOptions.value = normalized.selectable
        outboundTypeOptionsLoaded.value = true
        return outboundTypeSelectOptions.value
      }
    } catch {
    }

    outboundTypeOptions.value = []
    outboundTypeOptionMap.value = {}
    outboundTypeSelectOptions.value = resolveOutboundTypeOptions([])
    outboundTypeOptionsLoaded.value = true
    return outboundTypeSelectOptions.value
  }

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await outboundApi.getList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value,
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list || []
        total.value = res.data.total || 0
      }
    } catch {
    } finally {
      loading.value = false
    }
  }

  const fetchStatistics = async () => {
    try {
      const res = await outboundApi.getStatistics()
      if (res.code === 'SUCCESS' && res.data) {
        statistics.value = res.data
      }
    } catch (e) {
      console.error('获取统计失败', e)
    }
  }

  const init = () => Promise.all([loadOutboundTypeOptions(), fetchList(), fetchStatistics()])

  const search = async (params: OutboundOrderQuery) => {
    searchParams.value = params
    pageNum.value = 1
    await fetchList()
  }

  const resetSearch = async () => {
    searchParams.value = {}
    pageNum.value = 1
    await fetchList()
  }

  const changePage = async (page: number) => { pageNum.value = page; await fetchList() }
  const changePageSize = async (size: number) => { pageSize.value = size; pageNum.value = 1; await fetchList() }

  const deleteOrder = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认删除该出库单？', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      await outboundApi.delete(id)
      ElMessage.success('删除成功')
      await fetchList()
      await fetchStatistics()
    } catch {
    }
  }

  const submitOrder = async (id: number) => {
    try {
      await outboundApi.submit(id)
      ElMessage.success('提交成功')
      await fetchList()
      await fetchStatistics()
    } catch {
    }
  }

  const withdrawOrder = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认撤回该出库单？', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      await outboundApi.withdraw(id)
      ElMessage.success('撤回成功')
      await fetchList()
      await fetchStatistics()
    } catch {
    }
  }

  const approveOrder = async (id: number, approveRemark?: string) => {
    try {
      await outboundApi.approve(id, approveRemark)
      ElMessage.success('审核成功')
      await fetchList()
      await fetchStatistics()
    } catch {
    }
  }

  const rejectOrder = async (id: number, rejectReason: string) => {
    try {
      await outboundApi.reject(id, rejectReason)
      ElMessage.success('驳回成功')
      await fetchList()
      await fetchStatistics()
    } catch {
    }
  }

  const executeOrder = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认执行出库？执行后将扣减库存。', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      await outboundApi.execute(id)
      ElMessage.success('出库成功')
      await fetchList()
      await fetchStatistics()
    } catch {
    }
  }

  const reverseOrder = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认反审核？如已出库将恢复库存。', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      await outboundApi.reverse(id)
      ElMessage.success('反审核成功')
      await fetchList()
      await fetchStatistics()
    } catch {
    }
  }

  const openImportDialog = () => {
    importDialogVisible.value = true
    lastImportResult.value = null
    currentImportTask.value = null
  }

  const closeImportDialog = () => {
    importDialogVisible.value = false
    lastImportResult.value = null
    currentImportTask.value = null
  }

  const downloadImportTemplate = async () => {
    await outboundApi.downloadImportTemplate()
  }

  const exportList = async () => {
    await outboundApi.exportList(searchParams.value)
  }

  const exportDetails = async () => {
    await outboundApi.exportDetails(searchParams.value)
  }

  const fetchImportTask = async (taskNo: string) => {
    if (!taskNo) return null
    importTaskLoading.value = true
    try {
      const res = await outboundApi.getImportTask(taskNo)
      currentImportTask.value = res.data || null
      return currentImportTask.value
    } catch {
      return null
    } finally {
      importTaskLoading.value = false
    }
  }

  const downloadImportErrorFile = async (fileName: string) => {
    if (!fileName) return
    await outboundApi.downloadImportErrorFile(fileName)
  }

  const resumeImportTask = async () => {
    if (!currentImportTask.value?.taskNo) return false
    importTaskLoading.value = true
    try {
      const res = await outboundApi.resumeImportTask(currentImportTask.value.taskNo)
      currentImportTask.value = res.data || currentImportTask.value
      ElMessage.success('任务已继续执行')
      await Promise.all([fetchList(), fetchStatistics()])
      return true
    } catch {
      return false
    } finally {
      importTaskLoading.value = false
    }
  }

  const terminateImportTask = async () => {
    if (!currentImportTask.value?.taskNo) return false
    importTaskLoading.value = true
    try {
      const res = await outboundApi.terminateImportTask(currentImportTask.value.taskNo)
      currentImportTask.value = res.data || currentImportTask.value
      ElMessage.success('任务已终止清空')
      await Promise.all([fetchList(), fetchStatistics()])
      return true
    } catch {
      return false
    } finally {
      importTaskLoading.value = false
    }
  }

  const handleImport = async (file: File) => {
    importLoading.value = true
    try {
      const res = await outboundApi.importOrders(file)
      lastImportResult.value = res.data || null
      currentImportTask.value = res.data?.task || (res.data?.taskNo ? {
        taskNo: res.data.taskNo,
        taskStatus: res.data.taskStatus || '',
        totalCount: res.data.totalCount,
        successCount: res.data.successCount,
        failureCount: res.data.failureCount,
        canResume: res.data.taskStatus === '批量异常暂停',
        canTerminate: res.data.taskStatus === '批量异常暂停',
        errorFileName: res.data.errorFileName || null,
        errors: res.data.errors || [],
      } : null)
      ElMessage.success(`导入完成，成功 ${res.data?.successCount ?? 0} 条，失败 ${res.data?.failureCount ?? 0} 条`)
      await Promise.all([fetchList(), fetchStatistics()])
      return true
    } catch {
      return false
    } finally {
      importLoading.value = false
    }
  }

  const openForm = (id: number | null = null) => {
    currentId.value = id
    formVisible.value = true
  }

  const closeForm = () => {
    formVisible.value = false
    currentId.value = null
  }

  const openDetail = (id: number) => {
    currentId.value = id
    detailVisible.value = true
  }

  const closeDetail = () => {
    detailVisible.value = false
    currentId.value = null
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    loading,
    statistics,
    searchParams,
    outboundTypeOptions,
    outboundTypeOptionMap,
    outboundTypeSelectOptions,
    outboundTypeOptionsLoaded,
    formVisible,
    detailVisible,
    currentId,
    importDialogVisible,
    importLoading,
    importTaskLoading,
    lastImportResult,
    currentImportTask,
    loadOutboundTypeOptions,
    fetchList,
    fetchStatistics,
    init,
    search,
    resetSearch,
    changePage,
    changePageSize,
    deleteOrder,
    submitOrder,
    withdrawOrder,
    approveOrder,
    rejectOrder,
    executeOrder,
    reverseOrder,
    openImportDialog,
    closeImportDialog,
    downloadImportTemplate,
    exportList,
    exportDetails,
    downloadImportErrorFile,
    fetchImportTask,
    resumeImportTask,
    terminateImportTask,
    handleImport,
    openForm,
    closeForm,
    openDetail,
    closeDetail,
  }
})
