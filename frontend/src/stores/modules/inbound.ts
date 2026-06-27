import { defineStore } from 'pinia'
import { h, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { inboundApi } from '@/api/modules/inbound'
import type {
  InboundImportResult,
  InboundOrder,
  InboundOrderQuery,
  InboundOrderStatistics,
} from '@/types/inbound'

const INBOUND_DELETE_BLOCKED_MESSAGE = '该入库单已提交审批或已完成入库生成库存数据，为保证溯源完整与库存准确，不允许删除。'

export const useInboundStore = defineStore('inbound', () => {
  const list       = ref<InboundOrder[]>([])
  const total      = ref(0)
  const pageNum    = ref(1)
  const pageSize   = ref(10)
  const loading    = ref(false)
  const statistics = ref<InboundOrderStatistics>({
    thisMonthTotalCount: 0,
    thisMonthPendingCount: 0,
    thisMonthApprovedCount: 0,
    thisMonthInboundAmount: 0,
  })
  const searchParams = ref<InboundOrderQuery>({})

  const formVisible    = ref(false)
  const detailVisible  = ref(false)
  const currentId      = ref<number | null>(null)
  const importDialogVisible = ref(false)
  const importLoading = ref(false)
  const lastImportResult = ref<InboundImportResult | null>(null)

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await inboundApi.getList({
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
      const res = await inboundApi.getStatistics()
      if (res.code === 'SUCCESS' && res.data) statistics.value = res.data
    } catch (e) { console.error('获取统计失败', e) }
  }

  const init = () => Promise.all([fetchList(), fetchStatistics()])

  const search = async (params: InboundOrderQuery) => {
    searchParams.value = params; pageNum.value = 1; await fetchList()
  }

  const resetSearch = async () => {
    searchParams.value = {}; pageNum.value = 1; await fetchList()
  }

  const applyStatusFilter = async (status?: string) => {
    searchParams.value = {
      ...searchParams.value,
      status: status || undefined,
    }
    pageNum.value = 1
    await fetchList()
  }

  const changePage     = async (page: number) => { pageNum.value = page; await fetchList() }
  const changePageSize = async (size: number) => { pageSize.value = size; pageNum.value = 1; await fetchList() }

  const showDeleteBlockedDialog = async () => {
    await ElMessageBox({
      title: '入库单删除失败',
      message: () => h('div', { class: 'inbound-delete-blocked-confirm' }, [
        h('div', { class: 'inbound-delete-blocked-confirm__content' }, [
          h(WarningFilled, { class: 'inbound-delete-blocked-confirm__icon' }),
          h('div', { class: 'inbound-delete-blocked-confirm__text' }, [
            h('div', { class: 'inbound-delete-blocked-confirm__title' }, '入库单删除失败'),
            h('div', { class: 'inbound-delete-blocked-confirm__description' }, INBOUND_DELETE_BLOCKED_MESSAGE),
          ]),
        ]),
      ]),
      customClass: 'inbound-delete-blocked-message-box',
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      showCancelButton: false,
      confirmButtonText: '我知道了',
      dangerouslyUseHTMLString: true,
    })
  }

  const handleSubmitStructuredValidationError = (error: Error) => {
    const payload = (error as Error & {
      backendPayload?: {
        code?: string
        message?: string
        fieldErrors?: Array<{ message?: string | null }>
        data?: {
          fieldErrors?: Array<{ message?: string | null }>
        }
      }
    }).backendPayload

    if (payload?.code === 'VALIDATION_FAILED' && payload.message) {
      const fieldErrors = payload.fieldErrors || payload.data?.fieldErrors || []
      const fieldError = fieldErrors.find((item) => typeof item?.message === 'string' && item.message.trim())
      ElMessage.warning(fieldError?.message || payload.message)
      return true
    }
    return false
  }

  const deleteOrder = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认删除该入库单？', '提示', {
        type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消',
      })
      await inboundApi.delete(id)
      ElMessage.success('删除成功')
      await fetchList(); await fetchStatistics()
    } catch (error) {
      if (error instanceof Error && error.message === INBOUND_DELETE_BLOCKED_MESSAGE) {
        await showDeleteBlockedDialog()
      }
    }
  }

  const submitOrder = async (id: number) => {
    const order = list.value.find(item => item.id === id)
    if (!order || order.version == null) {
      ElMessage.error('当前入库单缺少版本信息，请刷新后重试')
      return
    }

    try {
      await inboundApi.submit(id, order.version)
      ElMessage.success('提交成功')
      await fetchList(); await fetchStatistics()
    } catch (error) {
      if (error instanceof Error && handleSubmitStructuredValidationError(error)) return
      ElMessage.error(error instanceof Error ? error.message : '提交失败')
    }
  }

  const cancelOrder = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认取消该入库单？', '提示', {
        type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消',
      })
      await inboundApi.cancel(id)
      ElMessage.success('取消成功')
      await fetchList(); await fetchStatistics()
    } catch {
    }
  }

  const openImportDialog = () => {
    importDialogVisible.value = true
    lastImportResult.value = null
  }

  const closeImportDialog = () => {
    importDialogVisible.value = false
    lastImportResult.value = null
  }

  const downloadImportTemplate = async () => {
    await inboundApi.downloadImportTemplate()
  }

  const downloadImportErrorFile = async (fileName: string) => {
    if (!fileName) return
    await inboundApi.downloadImportErrorFile(fileName)
  }

  const handleImport = async (file: File) => {
    importLoading.value = true
    try {
      const res = await inboundApi.importOrders(file)
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

  const openForm   = (id: number | null = null) => { currentId.value = id; formVisible.value = true }
  const closeForm  = () => { formVisible.value = false; currentId.value = null }
  const openDetail = (id: number) => { currentId.value = id; detailVisible.value = true }
  const closeDetail = () => { detailVisible.value = false; currentId.value = null }

  return {
    list, total, pageNum, pageSize, loading, statistics, searchParams,
    formVisible, detailVisible, currentId,
    importDialogVisible, importLoading, lastImportResult,
    fetchList, fetchStatistics, init,
    search, resetSearch, applyStatusFilter, changePage, changePageSize,
    deleteOrder, submitOrder, cancelOrder,
    openImportDialog, closeImportDialog, downloadImportTemplate, downloadImportErrorFile, handleImport,
    openForm, closeForm, openDetail, closeDetail,
  }
})
