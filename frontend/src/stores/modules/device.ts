import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceApi } from '@/api/modules/device'
import type {
  Device,
  DeviceDetail,
  DeviceForm,
  DeviceQuery,
  DeviceStatistics,
  DataLog,
  DeviceImportResult,
  DeviceBatchOperationResult,
  DeviceBatchItemResult,
} from '@/types/device'

export const useDeviceStore = defineStore('device', () => {
  type OperationReasonItem = Pick<DeviceBatchItemResult, 'deviceName' | 'deviceCode' | 'reason'>
  type DeviceBatchFeedbackResult = {
    executed: boolean
    result?: DeviceBatchOperationResult
    summaryMessage?: string
    skippedItems?: OperationReasonItem[]
    failedItems?: OperationReasonItem[]
  }

  const list = ref<Device[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const listError = ref<string | null>(null)
  const statsError = ref<string | null>(null)
  const statistics = ref<DeviceStatistics>({
    totalCount: 0,
    onlineCount: 0,
    offlineCount: 0,
    alertCount: 0,
    maintenanceCount: 0,
    deviceTypeStats: [],
  })
  const searchParams = ref<DeviceQuery>({})

  const formVisible = ref(false)
  const detailVisible = ref(false)
  const currentDeviceId = ref<number | null>(null)

  // 导入导出状态
  const importDialogVisible = ref(false)
  const importResultVisible = ref(false)
  const importResult = ref<DeviceImportResult | null>(null)
  const importLoading = ref(false)
  const exportLoading = ref(false)

  // 批量操作状态
  const selectedIds = ref<number[]>([])
  const selectedDevices = ref<Device[]>([])
  const batchLoading = ref(false)
  const hasSelection = computed(() => selectedIds.value.length > 0)

  // 数据日志状态
  const dataLogList = ref<DataLog[]>([])
  const dataLogTotal = ref(0)
  const dataLogLoading = ref(false)

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await deviceApi.getList({
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
      listError.value = (e as any)?.message || '获取设备列表失败'
      console.error('获取设备列表失败', e)
    } finally {
      loading.value = false
    }
  }

  const updateSelectedIds = (ids: number[]) => {
    selectedIds.value = [...ids]
  }

  const updateSelectedDevices = (devices: Device[]) => {
    selectedDevices.value = [...devices]
    selectedIds.value = devices.map(device => device.id)
  }

  const clearSelection = () => {
    selectedIds.value = []
    selectedDevices.value = []
  }

  const getDeviceLabel = (device?: Pick<Device, 'deviceName' | 'deviceCode'> | null) => {
    return device?.deviceName || device?.deviceCode || '设备'
  }

  const getMessageText = (message: unknown, fallback: string) => {
    return typeof message === 'string' && message.trim() ? message.trim() : fallback
  }

  const buildBatchSummaryMessage = (result: DeviceBatchOperationResult, successText: string) => {
    const parts: string[] = []
    if (result.successCount > 0) {
      parts.push(`${successText} ${result.successCount} 台`)
    }
    if (result.skippedCount > 0) {
      parts.push(`跳过 ${result.skippedCount} 台`)
    }
    if (result.failCount > 0) {
      parts.push(`失败 ${result.failCount} 台`)
    }
    return parts.join('，') || '操作完成'
  }

  const buildItemReasonSummary = (items: Array<{ deviceName?: string | null; deviceCode?: string | null; reason?: string }>) => {
    const visibleItems = items.slice(0, 3).map(item => {
      const name = item.deviceName || item.deviceCode || '设备'
      return `${name}：${item.reason || '未知原因'}`
    })
    if (items.length > 3) {
      visibleItems.push(`等 ${items.length} 台`)
    }
    return visibleItems.join('；')
  }

  const handleSingleOperationSuccess = async (message: string) => {
    ElMessage.success(message)
    clearSelection()
    await init()
    return true
  }

  const handleSingleOperationError = (error: unknown, fallback: string) => {
    ElMessage.error(getMessageText((error as any)?.message, fallback))
    return false
  }

  const handleBatchOperationResult = async (result: DeviceBatchOperationResult, successText: string): Promise<DeviceBatchFeedbackResult> => {
    const summaryMessage = buildBatchSummaryMessage(result, successText)
    if (result.failCount === 0 && result.skippedCount === 0) {
      ElMessage.success(summaryMessage)
    }

    clearSelection()
    await init()
    return {
      executed: true,
      result,
      summaryMessage,
      skippedItems: result.skippedItems,
      failedItems: result.failedItems,
    }
  }

  const fetchStatistics = async () => {
    try {
      const res = await deviceApi.getDashboard(searchParams.value.orgId)
      if (res.code === 'SUCCESS' && res.data) {
        statistics.value = res.data
        statsError.value = null
      }
    } catch (e) {
      statsError.value = (e as any)?.message || '获取设备统计失败'
      console.error('获取设备统计失败', e)
    }
  }

  const init = () => Promise.all([fetchList(), fetchStatistics()])

  const search = async (params: DeviceQuery) => {
    clearSelection()
    searchParams.value = params
    pageNum.value = 1
    await fetchList()
  }

  const resetSearch = async () => {
    clearSelection()
    searchParams.value = {}
    pageNum.value = 1
    await fetchList()
  }

  const changePage = async (page: number) => {
    clearSelection()
    pageNum.value = page
    await fetchList()
  }

  const changePageSize = async (size: number) => {
    clearSelection()
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  const deleteDevice = async (id: number) => {
    const device = list.value.find(item => item.id === id)
    const deviceLabel = getDeviceLabel(device)
    try {
      await ElMessageBox.confirm(
        `确定要删除设备「${deviceLabel}」吗？删除后不可恢复；若设备存在进行中的录像任务或未关闭告警，将无法删除。`,
        '删除确认', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
      const res = await deviceApi.delete(id)
      if (res.code === 'SUCCESS') {
        return await handleSingleOperationSuccess('删除成功')
      }
      ElMessage.error(getMessageText(res.message, '删除失败'))
      return false
    } catch (e: any) {
      // ElMessageBox 取消返回 'cancel'，不是 Error
      if (e !== 'cancel' && e?.message !== 'cancel') {
        return handleSingleOperationError(e, '删除失败')
      }
      return false
    }
  }

  const openForm = (id?: number) => {
    currentDeviceId.value = id ?? null
    formVisible.value = true
  }

  const closeForm = () => {
    formVisible.value = false
    currentDeviceId.value = null
  }

  const openDetail = (id: number) => {
    currentDeviceId.value = id
    detailVisible.value = true
  }

  const closeDetail = () => {
    detailVisible.value = false
    currentDeviceId.value = null
  }

  // ========== 启用/禁用 ==========

  const toggleStatus = async (id: number) => {
    const device = list.value.find(d => d.id === id)
    if (!device) return false
    const isActive = device.status === 'active'
    const deviceLabel = getDeviceLabel(device)
    const confirmMessage = isActive
      ? `确定要禁用设备「${deviceLabel}」吗？若设备存在进行中的录像任务或未关闭告警，将无法禁用。`
      : `确定要启用设备「${deviceLabel}」吗？启用后设备状态将恢复为可用。`
    try {
      await ElMessageBox.confirm(
        confirmMessage,
        isActive ? '禁用确认' : '启用确认',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
      const res = await deviceApi.toggleStatus(id)
      if (res.code === 'SUCCESS') {
        return await handleSingleOperationSuccess(isActive ? '设备已禁用' : '设备已启用')
      }
      ElMessage.error(getMessageText(res.message, isActive ? '禁用失败' : '启用失败'))
      return false
    } catch (e: any) {
      if (e !== 'cancel' && e?.message !== 'cancel') {
        return handleSingleOperationError(e, isActive ? '禁用失败' : '启用失败')
      }
      return false
    }
  }

  const batchDeleteDevices = async (): Promise<DeviceBatchFeedbackResult> => {
    if (selectedIds.value.length === 0) {
      ElMessage.warning('请先选择要删除的设备')
      return { executed: false }
    }
    batchLoading.value = true
    try {
      const res = await deviceApi.batchDelete(selectedIds.value)
      if (res.code === 'SUCCESS' && res.data) {
        return await handleBatchOperationResult(res.data, '成功删除')
      }
      ElMessage.error(getMessageText(res.message, '批量删除失败'))
      return { executed: false }
    } catch (e: any) {
      handleSingleOperationError(e, '批量删除失败')
      return { executed: false }
    } finally {
      batchLoading.value = false
    }
  }

  const batchEnableDevices = async (): Promise<DeviceBatchFeedbackResult> => {
    if (selectedDevices.value.length === 0) {
      ElMessage.warning('请先选择要启用的设备')
      return { executed: false }
    }

    const skipped = selectedDevices.value
      .filter(device => device.status === 'active')
      .map(device => ({
        deviceName: device.deviceName,
        deviceCode: device.deviceCode,
        reason: '设备已启用',
      }))
    const executableIds = selectedDevices.value
      .filter(device => device.status !== 'active')
      .map(device => device.id)

    if (executableIds.length === 0) {
      clearSelection()
      await init()
      return {
        executed: true,
        summaryMessage: `跳过 ${skipped.length} 台`,
        skippedItems: skipped,
      }
    }

    batchLoading.value = true
    try {
      const res = await deviceApi.batchEnable(executableIds)
      if (res.code === 'SUCCESS' && res.data) {
        return await handleBatchOperationResult({
          ...res.data,
          skippedCount: res.data.skippedCount + skipped.length,
          skippedItems: [...skipped, ...res.data.skippedItems],
        }, '成功启用')
      }
      ElMessage.error(getMessageText(res.message, '批量启用失败'))
      return { executed: false, skippedItems: skipped }
    } catch (e: any) {
      handleSingleOperationError(e, '批量启用失败')
      return { executed: false, skippedItems: skipped }
    } finally {
      batchLoading.value = false
    }
  }

  const batchDisableDevices = async (): Promise<DeviceBatchFeedbackResult> => {
    if (selectedDevices.value.length === 0) {
      ElMessage.warning('请先选择要禁用的设备')
      return { executed: false }
    }

    const skipped = selectedDevices.value
      .filter(device => device.status !== 'active')
      .map(device => ({
        deviceName: device.deviceName,
        deviceCode: device.deviceCode,
        reason: '设备已禁用',
      }))
    const executableIds = selectedDevices.value
      .filter(device => device.status === 'active')
      .map(device => device.id)

    if (executableIds.length === 0) {
      clearSelection()
      await init()
      return {
        executed: true,
        summaryMessage: `跳过 ${skipped.length} 台`,
        skippedItems: skipped,
      }
    }

    batchLoading.value = true
    try {
      const res = await deviceApi.batchDisable(executableIds)
      if (res.code === 'SUCCESS' && res.data) {
        return await handleBatchOperationResult({
          ...res.data,
          skippedCount: res.data.skippedCount + skipped.length,
          skippedItems: [...skipped, ...res.data.skippedItems],
        }, '成功禁用')
      }
      ElMessage.error(getMessageText(res.message, '批量禁用失败'))
      return { executed: false, skippedItems: skipped }
    } catch (e: any) {
      handleSingleOperationError(e, '批量禁用失败')
      return { executed: false, skippedItems: skipped }
    } finally {
      batchLoading.value = false
    }
  }

  // ========== 导入/导出 ==========

  const openImportDialog = () => {
    importDialogVisible.value = true
  }

  const closeImportDialog = () => {
    importDialogVisible.value = false
  }

  const closeImportResult = () => {
    importResultVisible.value = false
    importResult.value = null
  }

  const handleImport = async (file: File) => {
    importLoading.value = true
    try {
      const res = await deviceApi.importDevices(file)
      if (res.code === 'SUCCESS' && res.data) {
        importResult.value = res.data
        importDialogVisible.value = false
        importResultVisible.value = true
        if (res.data.successCount > 0) {
          await init()
        }
      } else {
        const msg = res.message || '导入失败'
        ElMessage.error(msg)
        if (msg.includes('模板') || msg.includes('格式')) {
          ElMessageBox.alert(
            '上传的文件格式与导入模板不匹配，请先下载导入模板，按模板格式填写数据后再上传。',
            '格式错误',
            { confirmButtonText: '我知道了', type: 'warning' }
          )
        }
      }
    } catch (e: any) {
      const msg = e?.message || '导入失败'
      ElMessage.error(msg)
      if (msg.includes('模板') || msg.includes('格式')) {
        ElMessageBox.alert(
          '上传的文件格式与导入模板不匹配，请先下载导入模板，按模板格式填写数据后再上传。',
          '格式错误',
          { confirmButtonText: '我知道了', type: 'warning' }
        )
      }
    } finally {
      importLoading.value = false
    }
  }

  const handleExport = async () => {
    exportLoading.value = true
    try {
      await deviceApi.exportDevices(searchParams.value)
      ElMessage.success('导出成功')
    } catch (e: any) {
      ElMessage.error(e.message || '导出失败')
    } finally {
      exportLoading.value = false
    }
  }

  // ========== 数据日志 ==========

  const fetchDataLogs = async (deviceId: number, params?: { dataType?: string; startTime?: string; endTime?: string }) => {
    dataLogLoading.value = true
    try {
      const res = await deviceApi.getDataLogs({ deviceId, pageNum: 1, pageSize: 50, ...params })
      if (res.code === 'SUCCESS' && res.data) {
        dataLogList.value = res.data.list
        dataLogTotal.value = res.data.total
      }
    } catch (e) {
      console.error('获取数据日志失败', e)
    } finally {
      dataLogLoading.value = false
    }
  }

  return {
    list, total, pageNum, pageSize, loading, listError, statsError, statistics, searchParams,
    formVisible, detailVisible, currentDeviceId,
    importDialogVisible, importResultVisible, importResult, importLoading, exportLoading,
    selectedIds, selectedDevices, batchLoading, hasSelection,
    dataLogList, dataLogTotal, dataLogLoading,
    fetchList, fetchStatistics, init, search, resetSearch,
    changePage, changePageSize, deleteDevice,
    openForm, closeForm, openDetail, closeDetail,
    toggleStatus, updateSelectedIds, updateSelectedDevices, clearSelection,
    batchDeleteDevices, batchEnableDevices, batchDisableDevices,
    openImportDialog, closeImportDialog, closeImportResult, handleImport, handleExport,
    fetchDataLogs,
  }
})
