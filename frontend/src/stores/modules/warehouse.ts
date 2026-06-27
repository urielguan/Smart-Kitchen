import { defineStore } from 'pinia'
import { computed, h, ref } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { warehouseApi } from '@/api/modules/warehouse'
import type {
  Warehouse,
  WarehouseExportFormat,
  WarehouseImportResult,
  WarehouseImportTarget,
  WarehouseQuery,
  WarehouseStatistics,
} from '@/types/warehouse'

export const useWarehouseStore = defineStore('warehouse', () => {
  const list       = ref<Warehouse[]>([])
  const total      = ref(0)
  const pageNum    = ref(1)
  const pageSize   = ref(10)
  const loading    = ref(false)
  const statistics = ref<WarehouseStatistics>({
    warehouseTotal: 0, activeCount: 0, maintenanceCount: 0, positionTotal: 0,
  })
  const searchParams = ref<WarehouseQuery>({})

  const formVisible         = ref(false)
  const detailVisible       = ref(false)
  const locationFormVisible = ref(false)
  const currentWarehouseId  = ref<number | null>(null)
  const currentLocationId   = ref<number | null>(null)

  const importDialogVisible = ref(false)
  const exportDialogVisible = ref(false)
  const importLoading = ref(false)
  const exportLoading = ref(false)
  const importTarget = ref<WarehouseImportTarget>('warehouse')
  const exportTarget = ref<WarehouseImportTarget>('warehouse')
  const exportFormat = ref<WarehouseExportFormat>('xlsx')
  const lastImportResult = ref<WarehouseImportResult | null>(null)

  const exportParams = computed<WarehouseQuery>(() => ({ ...searchParams.value }))

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await warehouseApi.getList({
        pageNum: pageNum.value, pageSize: pageSize.value, ...searchParams.value,
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value  = res.data.list
        total.value = res.data.total
      }
    } catch {
    } finally {
      loading.value = false
    }
  }

  const fetchStatistics = async () => {
    try {
      const res = await warehouseApi.getStatistics()
      if (res.code === 'SUCCESS' && res.data) statistics.value = res.data
    } catch (e) { console.error('获取统计失败', e) }
  }

  const init = () => Promise.all([fetchList(), fetchStatistics()])

  const search = async (params: WarehouseQuery) => {
    searchParams.value = params; pageNum.value = 1; await fetchList()
  }

  const resetSearch = async () => {
    searchParams.value = {}; pageNum.value = 1; await fetchList()
  }

  const changePage     = async (page: number) => { pageNum.value = page; await fetchList() }
  const changePageSize = async (size: number) => { pageSize.value = size; pageNum.value = 1; await fetchList() }

  const deleteWarehouse = async (id: number) => {
    try {
      await ElMessageBox({
        title: '删除仓库',
        message: () => h('div', { class: 'warehouse-delete-confirm' }, [
          h('div', { class: 'warehouse-delete-confirm__content' }, [
            h(WarningFilled, { class: 'warehouse-delete-confirm__icon' }),
            h('div', { class: 'warehouse-delete-confirm__text' }, [
              h('div', { class: 'warehouse-delete-confirm__title' }, '删除仓库'),
              h('div', { class: 'warehouse-delete-confirm__description' }, '确认删除该仓库？删除后不可恢复。'),
            ]),
          ]),
        ]),
        customClass: 'warehouse-delete-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        showCancelButton: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })
      await warehouseApi.delete(id)
      ElMessage.success('删除成功')
      await fetchList(); await fetchStatistics()
    } catch {
    }
  }

  const openForm    = (id: number | null = null) => { currentWarehouseId.value = id; formVisible.value = true }
  const closeForm   = () => { formVisible.value = false; currentWarehouseId.value = null }
  const openDetail  = (id: number) => { currentWarehouseId.value = id; detailVisible.value = true }
  const closeDetail = () => { detailVisible.value = false; currentWarehouseId.value = null }

  const openLocationForm  = (wid: number, lid: number | null = null) => {
    currentWarehouseId.value = wid; currentLocationId.value = lid; locationFormVisible.value = true
  }
  const closeLocationForm = () => { locationFormVisible.value = false; currentLocationId.value = null }

  const openImportDialog = (target: WarehouseImportTarget) => {
    importTarget.value = target
    importDialogVisible.value = true
    lastImportResult.value = null
  }

  const closeImportDialog = () => {
    importDialogVisible.value = false
    lastImportResult.value = null
  }

  const openExportDialog = (target: WarehouseImportTarget) => {
    exportTarget.value = target
    exportDialogVisible.value = true
  }

  const closeExportDialog = () => {
    exportDialogVisible.value = false
    exportFormat.value = 'xlsx'
  }

  const downloadImportTemplate = async (target = importTarget.value) => {
    if (target === 'warehouse') {
      await warehouseApi.downloadWarehouseTemplate()
      return
    }
    await warehouseApi.downloadLocationTemplate()
  }

  const downloadImportErrorFile = async (fileName: string, target = importTarget.value) => {
    if (!fileName) {
      return
    }
    if (target === 'warehouse') {
      await warehouseApi.downloadWarehouseImportErrorFile(fileName)
      return
    }
    await warehouseApi.downloadLocationImportErrorFile(fileName)
  }

  const handleImport = async (file: File, target = importTarget.value) => {
    importLoading.value = true
    try {
      const res = target === 'warehouse'
        ? await warehouseApi.importWarehouses(file)
        : await warehouseApi.importLocations(file)

      lastImportResult.value = res.data || null
      ElMessage.success(`导入完成，成功 ${res.data?.successCount ?? 0} 条，失败 ${res.data?.failureCount ?? 0} 条`)
      await Promise.all([fetchList(), fetchStatistics()])
      if (!res.data?.errorFileName) {
        closeImportDialog()
      }
      return true
    } catch {
      return false
    } finally {
      importLoading.value = false
    }
  }

  const handleExport = async (target = exportTarget.value) => {
    exportLoading.value = true
    try {
      if (target === 'warehouse') {
        await warehouseApi.exportWarehouses(exportParams.value, exportFormat.value)
      } else {
        await warehouseApi.exportLocations(exportParams.value, exportFormat.value)
      }
      ElMessage.success('导出任务已开始')
      closeExportDialog()
      return true
    } catch {
      return false
    } finally {
      exportLoading.value = false
    }
  }

  return {
    list, total, pageNum, pageSize, loading, statistics, searchParams,
    formVisible, detailVisible, locationFormVisible,
    currentWarehouseId, currentLocationId,
    importDialogVisible, exportDialogVisible, importLoading, exportLoading,
    importTarget, exportTarget, exportFormat, exportParams, lastImportResult,
    fetchList, fetchStatistics, init,
    search, resetSearch, changePage, changePageSize, deleteWarehouse,
    openForm, closeForm, openDetail, closeDetail, openLocationForm, closeLocationForm,
    openImportDialog, closeImportDialog, openExportDialog, closeExportDialog,
    downloadImportTemplate, downloadImportErrorFile, handleImport, handleExport,
  }
})
