import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { supplierApi } from '@/api/modules/supplier'
import type {
  SupplierCancelPayload,
  SupplierDisablePayload,
  Supplier,
  SupplierForm,
  SupplierImportResult,
  SupplierQuery,
  SupplierStatistics,
  SupplierStatus
} from '@/types/supplier'

interface SupplierSubmitResult {
  success: boolean
  errorMessage?: string
}

export const useSupplierStore = defineStore('supplier', () => {
  const createDefaultSearchParams = (): SupplierQuery => ({
    keyword: '',
    status: ''
  })

  /** 列表数据 */
  const list = ref<Supplier[]>([])

  /** 总数 */
  const total = ref(0)

  /** 当前页码 */
  const pageNum = ref(1)

  /** 每页条数 */
  const pageSize = ref(10)

  /** 加载状态 */
  const loading = ref(false)

  /** 导入中状态 */
  const importLoading = ref(false)

  /** 导出中状态 */
  const exportLoading = ref(false)

  /** 搜索参数 */
  const searchParams = ref<SupplierQuery>(createDefaultSearchParams())

  /** 查询表单缓存 */
  const searchFormCache = ref<SupplierQuery>(createDefaultSearchParams())

  /** 列表是否已初始化 */
  const initialized = ref(false)

  /** 统计数据 */
  const statistics = ref<SupplierStatistics>({
    total: 0,
    activeCount: 0,
    pendingCount: 0,
    nearExpireCount: 0
  })

  /** 详情弹窗 */
  const detailVisible = ref(false)

  /** 表单弹窗 */
  const formVisible = ref(false)

  /** 导入弹窗 */
  const importDialogVisible = ref(false)

  /** 导入结果弹窗 */
  const importResultVisible = ref(false)

  /** 导入结果 */
  const importResult = ref<SupplierImportResult | null>(null)

  /** 当前操作供应商 ID */
  const currentId = ref<number | null>(null)

  /** 获取供应商列表 */
  const fetchList = async () => {
    loading.value = true
    let success = false
    try {
      const res = await supplierApi.getList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: searchParams.value.keyword || undefined,
        status: searchParams.value.status || undefined
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list
        total.value = res.data.total
        initialized.value = true
        success = true
      }
    } catch (error: any) {
      ElMessage.error(error.message || '获取供应商列表失败')
    } finally {
      loading.value = false
    }
    return success
  }

  /** 获取统计数据 */
  const fetchStatistics = async () => {
    try {
      const res = await supplierApi.getStatistics()
      if (res.code === 'SUCCESS' && res.data) {
        statistics.value = res.data
      }
    } catch (error: any) {
      console.error('获取供应商统计失败:', error)
    }
  }

  /** 初始化 */
  const init = async () => {
    if (initialized.value) {
      return
    }
    await Promise.all([fetchList(), fetchStatistics()])
  }

  /** 搜索 */
  const search = async (params: SupplierQuery) => {
    searchParams.value = { ...searchParams.value, ...params }
    searchFormCache.value = { ...searchFormCache.value, ...params }
    pageNum.value = 1
    await fetchList()
  }

  /** 重置搜索 */
  const resetSearch = async () => {
    searchParams.value = createDefaultSearchParams()
    searchFormCache.value = createDefaultSearchParams()
    pageNum.value = 1
    await fetchList()
  }

  /** 更新查询表单缓存 */
  const updateSearchFormCache = (params: SupplierQuery) => {
    searchFormCache.value = { ...searchFormCache.value, ...params }
  }

  /** 切换页码 */
  const changePage = async (page: number) => {
    pageNum.value = page
    await fetchList()
  }

  /** 切换每页条数 */
  const changePageSize = async (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  /** 打开详情弹窗 */
  const openDetail = (id: number) => {
    currentId.value = id
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = () => {
    detailVisible.value = false
    currentId.value = null
  }

  /** 打开表单弹窗 */
  const openForm = (id: number | null = null) => {
    currentId.value = id
    formVisible.value = true
  }

  /** 关闭表单弹窗 */
  const closeForm = () => {
    formVisible.value = false
    currentId.value = null
  }

  /** 打开导入弹窗 */
  const openImportDialog = () => {
    importDialogVisible.value = true
  }

  /** 关闭导入弹窗 */
  const closeImportDialog = () => {
    importDialogVisible.value = false
  }

  /** 关闭导入结果弹窗 */
  const closeImportResult = () => {
    importResultVisible.value = false
    importResult.value = null
  }

  /** 获取供应商详情 */
  const getSupplierDetail = async (id: number): Promise<Supplier | undefined> => {
    try {
      const res = await supplierApi.getDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        return res.data
      }
    } catch (error: any) {
      console.error('获取供应商详情失败:', error)
    }
    return undefined
  }

  /** 新增供应商 */
  const addSupplier = async (form: SupplierForm): Promise<SupplierSubmitResult> => {
    try {
      const res = await supplierApi.create(form)
      if (res.code === 'SUCCESS') {
        await fetchList()
        await fetchStatistics()
        return { success: true }
      }
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error?.message
      }
    }
    return { success: false }
  }

  /** 编辑供应商 */
  const updateSupplier = async (id: number, form: SupplierForm): Promise<SupplierSubmitResult> => {
    try {
      const res = await supplierApi.update(id, form)
      if (res.code === 'SUCCESS') {
        await fetchList()
        await fetchStatistics()
        return { success: true }
      }
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error?.message
      }
    }
    return { success: false }
  }

  /** 审核供应商 */
  const auditSupplier = async (
    id: number,
    status: Extract<SupplierStatus, 'active' | 'rejected'>,
    remark: string
  ): Promise<boolean> => {
    try {
      const res = await supplierApi.audit(id, {
        status,
        remark: remark.trim() || undefined
      })
      if (res.code === 'SUCCESS') {
        ElMessage.success(status === 'active' ? '审核通过' : '审核驳回')
        await fetchList()
        await fetchStatistics()
        return true
      }
    } catch {
      // 错误提示由统一响应拦截器处理
    }
    return false
  }

  /** 禁用供应商 */
  const disableSupplier = async (id: number, payload: SupplierDisablePayload): Promise<SupplierSubmitResult> => {
    try {
      const res = await supplierApi.disable(id, payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('供应商已禁用')
        await fetchList()
        await fetchStatistics()
        return { success: true }
      }
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error?.message
      }
    }
    return { success: false }
  }

  /** 启用供应商 */
  const enableSupplier = async (id: number): Promise<boolean> => {
    try {
      const res = await supplierApi.enable(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('供应商已启用')
        await fetchList()
        await fetchStatistics()
        return true
      }
    } catch {
      // 错误提示由统一响应拦截器处理
    }
    return false
  }

  /** 注销供应商 */
  const cancelSupplier = async (id: number, payload: SupplierCancelPayload): Promise<boolean> => {
    try {
      const res = await supplierApi.cancel(id, payload)
      if (res.code === 'SUCCESS') {
        ElMessage.success('供应商已注销')
        await fetchList()
        await fetchStatistics()
        return true
      }
    } catch {
      // 错误提示由统一响应拦截器处理
    }
    return false
  }

  /** 删除供应商 */
  const deleteSupplier = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认删除该供应商吗？删除后不可恢复。', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })

      const res = await supplierApi.delete(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        // 当前页数据为空时回到上一页
        if (list.value.length <= 1 && pageNum.value > 1) {
          pageNum.value -= 1
        }
        await fetchList()
        await fetchStatistics()
      }
    } catch (error: any) {
      if (error !== 'cancel' && error?.message !== 'cancel') {
        // 业务错误提示由统一响应拦截器处理
      }
    }
  }

  /** 处理导入 */
  const handleImport = async (file: File): Promise<boolean> => {
    importLoading.value = true
    try {
      const res = await supplierApi.importSuppliers(file)
      if (res.code === 'SUCCESS' && res.data) {
        importResult.value = res.data
        importDialogVisible.value = false
        importResultVisible.value = true
        await Promise.all([fetchList(), fetchStatistics()])
        ElMessage.success(`导入完成：共 ${res.data.total} 条，成功 ${res.data.successCount} 条`)
        return true
      }
    } catch (error: any) {
      ElMessage.error(error.message || '导入失败')
    } finally {
      importLoading.value = false
    }
    return false
  }

  /** 处理导出 */
  const handleExport = async (params?: SupplierQuery) => {
    exportLoading.value = true
    try {
      await supplierApi.exportSuppliers(params)
    } catch (error: any) {
      ElMessage.error(error.message || '导出失败')
    } finally {
      exportLoading.value = false
    }
  }

  /** 下载模板 */
  const downloadTemplate = async () => {
    try {
      await supplierApi.downloadTemplate()
    } catch (error: any) {
      ElMessage.error(error.message || '下载模板失败')
    }
  }

  /** 下载错误文件 */
  const downloadErrorFile = async () => {
    const fileName = importResult.value?.errorFileUrl?.split('/').filter(Boolean).pop()
    if (!fileName) {
      ElMessage.warning('暂无错误文件可下载')
      return
    }
    try {
      await supplierApi.downloadImportErrorFile(fileName)
    } catch (error: any) {
      ElMessage.error(error.message || '下载错误文件失败')
    }
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    loading,
    importLoading,
    exportLoading,
    initialized,
    searchParams,
    searchFormCache,
    statistics,
    detailVisible,
    formVisible,
    importDialogVisible,
    importResultVisible,
    importResult,
    currentId,
    fetchList,
    fetchStatistics,
    init,
    search,
    resetSearch,
    updateSearchFormCache,
    changePage,
    changePageSize,
    openDetail,
    closeDetail,
    openForm,
    closeForm,
    openImportDialog,
    closeImportDialog,
    closeImportResult,
    getSupplierDetail,
    addSupplier,
    updateSupplier,
    auditSupplier,
    disableSupplier,
    enableSupplier,
    cancelSupplier,
    deleteSupplier,
    handleImport,
    handleExport,
    downloadTemplate,
    downloadErrorFile
  }
})
