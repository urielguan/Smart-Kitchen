import { defineStore } from 'pinia'
import { h, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { isFoodLibraryApiUnavailable, materialApi } from '@/api/modules/material'
import type {
  Material,
  MaterialQuery,
  MaterialStatistics,
  MaterialImportResult,
  FoodImportResult,
  MaterialFoodMapping
} from '@/types/material'

/** 构建物料确认弹窗 message（图标 + 标题 + 描述） */
const renderConfirmMessage = (title: string, description: string) => () =>
  h('div', { class: 'material-confirm' }, [
    h('div', { class: 'material-confirm__content' }, [
      h(WarningFilled, { class: 'material-confirm__icon' }),
      h('div', { class: 'material-confirm__text' }, [
        h('div', { class: 'material-confirm__title' }, title),
        h('div', { class: 'material-confirm__description' }, description),
      ]),
    ]),
  ])

export const useMaterialStore = defineStore('material', () => {
  /** 列表数据 */
  const list = ref<Material[]>([])

  /** 总数 */
  const total = ref(0)

  /** 当前页码 */
  const pageNum = ref(1)

  /** 每页条数 */
  const pageSize = ref(10)

  /** 加载状态 */
  const loading = ref(false)

  /** 搜索参数 */
  const searchParams = ref<Partial<MaterialQuery>>({
    materialName: '',
    categoryName: undefined,
    stockStatus: undefined
  })

  /** 统计数据 */
  const statistics = ref<MaterialStatistics>({
    total: 0,
    activeCount: 0,
    inactiveCount: 0,
    incompleteCount: 0
  })

  /** 启用物料列表缓存（供跨模块下拉选择使用） */
  const activeList = ref<Material[]>([])

  /** 启用物料列表加载状态 */
  const activeListLoading = ref(false)

  /** 详情弹窗 */
  const detailVisible = ref(false)

  /** 表单弹窗 */
  const formVisible = ref(false)

  /** 当前物料 ID */
  const currentMaterialId = ref<number | null>(null)
  const mappingDialogVisible = ref(false)
  const currentMappingMaterial = ref<Material | null>(null)
  const foodImportLoading = ref(false)
  const latestFoodImportResult = ref<FoodImportResult | null>(null)
  const mappings = ref<MaterialFoodMapping[]>([])
  const foodLibraryReady = ref(false)
  const foodLibraryCategoryCount = ref(0)
  const foodLibraryStatus = ref<'unknown' | 'ready' | 'empty' | 'unavailable'>('unknown')
  const foodLibraryStatusMessage = ref('')

  const updateFoodLibraryStatus = (categoryCount: number) => {
    foodLibraryCategoryCount.value = categoryCount
    foodLibraryReady.value = categoryCount > 0
    foodLibraryStatus.value = categoryCount > 0 ? 'ready' : 'empty'
    foodLibraryStatusMessage.value = ''
  }

  const markFoodLibraryUnavailable = (message = '当前联调环境未部署标准食品库接口，营养映射能力暂不可用') => {
    foodLibraryReady.value = false
    foodLibraryCategoryCount.value = 0
    foodLibraryStatus.value = 'unavailable'
    foodLibraryStatusMessage.value = message
  }

  /** 获取物料列表 */
  const fetchList = async () => {
    loading.value = true
    try {
      const params: MaterialQuery = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      }
      const res = await materialApi.getList(params)
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list
        total.value = res.data.total
      }
    } catch (error: any) {
      // 错误提示由 API 拦截器统一处理，避免重复弹窗
      console.error('获取物料列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  /** 获取统计数据 */
  const fetchStatistics = async () => {
    try {
      const res = await materialApi.getStatistics()
      if (res.code === 'SUCCESS' && res.data) {
        statistics.value = res.data
      }
    } catch (error: any) {
      console.error('获取统计数据失败:', error)
    }
  }

  /** 获取启用物料列表（供跨模块下拉选择使用，懒加载+CRUD后强制刷新） */
  const fetchActiveList = async (force = false) => {
    if (!force && activeList.value.length > 0) return
    activeListLoading.value = true
    try {
      const res = await materialApi.getList({ pageNum: 1, pageSize: 1000, status: 'active' })
      if (res.code === 'SUCCESS' && res.data) {
        activeList.value = res.data.list || []
      }
    } catch (error: any) {
      console.error('获取启用物料列表失败:', error)
    } finally {
      activeListLoading.value = false
    }
  }

  /** 搜索 */
  const search = async (params: Partial<MaterialQuery>) => {
    searchParams.value = { ...searchParams.value, ...params }
    pageNum.value = 1
    await fetchList()
  }

  /** 重置搜索 */
  const resetSearch = async () => {
    searchParams.value = {
      materialName: '',
      categoryName: undefined,
      stockStatus: undefined
    }
    pageNum.value = 1
    await fetchList()
  }

  /** 分页切换 */
  const changePage = async (page: number) => {
    pageNum.value = page
    await fetchList()
  }

  /** 每页条数改变 */
  const changePageSize = async (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  /** 删除物料 */
  const deleteMaterial = async (id: number) => {
    try {
      await ElMessageBox({
        title: '删除物料',
        message: renderConfirmMessage('删除物料', '确认删除该物料？删除后不可恢复。'),
        customClass: 'material-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })

      const res = await materialApi.delete(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        await Promise.all([fetchList(), fetchStatistics(), fetchActiveList(true)])
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // 错误消息已在 API 拦截器中显示，此处不再重复
      }
    }
  }

  /** 打开详情弹窗 */
  const openDetail = (id: number) => {
    currentMaterialId.value = id
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = () => {
    detailVisible.value = false
    currentMaterialId.value = null
  }

  /** 打开表单弹窗 */
  const openForm = (id: number | null = null) => {
    currentMaterialId.value = id
    formVisible.value = true
  }

  const openMappingDialog = (material: Material) => {
    currentMappingMaterial.value = material
    mappingDialogVisible.value = true
  }

  const closeMappingDialog = () => {
    mappingDialogVisible.value = false
    currentMappingMaterial.value = null
  }

  /** 关闭表单弹窗 */
  const closeForm = () => {
    formVisible.value = false
    currentMaterialId.value = null
  }

  /** 切换物料状态 */
  const toggleStatus = async (material: Material) => {
    const newStatus = material.status === 'active' ? 'inactive' : 'active'
    const action = newStatus === 'inactive' ? '停用' : '启用'
    try {
      await ElMessageBox({
        title: `${action}物料`,
        message: renderConfirmMessage(`${action}物料`, `确认${action}物料「${material.materialName}」？`),
        customClass: 'material-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消',
      })
      const res = await materialApi.updateStatus(material.id, newStatus)
      if (res.code === 'SUCCESS') {
        ElMessage.success(`${action}成功`)
        await Promise.all([fetchList(), fetchStatistics(), fetchActiveList(true)])
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // 错误消息已在 API 拦截器中显示
      }
    }
  }

  /** 获取物料详情 */
  const getMaterialDetail = async (id: number): Promise<Material | undefined> => {
    try {
      const res = await materialApi.getDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        return res.data
      }
    } catch (error: any) {
      console.error('获取物料详情失败:', error)
    }
    return undefined
  }

  // ====== 导入导出 ======
  /** 导入弹窗 */
  const importDialogVisible = ref(false)
  /** 导入结果弹窗 */
  const importResultVisible = ref(false)
  /** 导入结果 */
  const importResult = ref<MaterialImportResult | null>(null)

  const openImportDialog = () => { importDialogVisible.value = true }
  const closeImportDialog = () => { importDialogVisible.value = false }
  const closeImportResult = () => { importResultVisible.value = false; importResult.value = null }

  /** 下载导入模板 */
  const downloadTemplate = async () => {
    try {
      await materialApi.downloadTemplate()
    } catch (error: any) {
      console.error('下载模板失败:', error)
    }
  }

  /** 执行导入 */
  const handleImport = async (file: File) => {
    try {
      const res = await materialApi.importMaterials(file)
      if (res.code === 'SUCCESS' && res.data) {
        importResult.value = res.data
        closeImportDialog()
        importResultVisible.value = true
        await Promise.all([fetchList(), fetchStatistics(), fetchActiveList(true)])
      }
    } catch (error: any) {
      console.error('导入失败:', error)
    }
  }

  /** 执行导出 */
  const handleExport = async (params?: Partial<MaterialQuery>) => {
    try {
      await materialApi.exportMaterials(params)
    } catch (error: any) {
      console.error('导出失败:', error)
    }
  }

  /** 初始化标准食品库 */
  const importFoodJson = async () => {
    foodImportLoading.value = true
    try {
      const res = await materialApi.importFoodJson({ silentError: true, timeout: 180000 })
      if (res.code === 'SUCCESS' && res.data) {
        latestFoodImportResult.value = res.data
        updateFoodLibraryStatus(res.data.categoryCount || 0)
        ElMessage.success(`标准食品库初始化完成，共导入 ${res.data.itemCount} 条食品项`)
        return res.data
      }
    } catch (error: any) {
      if (isFoodLibraryApiUnavailable(error)) {
        markFoodLibraryUnavailable('当前联调环境未部署标准食品库接口，暂无法初始化标准食品库')
        ElMessage.warning(foodLibraryStatusMessage.value)
        return null
      }
      console.error('初始化标准食品库失败:', error)
    } finally {
      foodImportLoading.value = false
    }
    return null
  }

  const fetchFoodLibraryStatus = async () => {
    if (foodLibraryStatus.value === 'unavailable') return
    try {
      const res = await materialApi.getFoodCategories({ silentError: true })
      if (res.code === 'SUCCESS' && res.data) {
        updateFoodLibraryStatus(res.data.length)
      }
    } catch (error: any) {
      if (isFoodLibraryApiUnavailable(error)) {
        markFoodLibraryUnavailable()
        return
      }
      console.error('获取标准食品库状态失败:', error)
    }
  }

  const fetchMappings = async () => {
    try {
      const res = await materialApi.getMaterialFoodMappings({
        pageNum: 1,
        pageSize: 100,
        materialId: currentMappingMaterial.value?.id
      })
      if (res.code === 'SUCCESS' && res.data) {
        mappings.value = res.data.list || []
      }
    } catch (error: any) {
      console.error('获取映射列表失败:', error)
    }
  }

  /** 下载错误文件 */
  const downloadErrorFile = async () => {
    if (importResult.value?.errorFileUrl) {
      const fileName = importResult.value.errorFileUrl.split('/').pop() || 'error.xlsx'
      try {
        await materialApi.downloadErrorFile(fileName)
      } catch (error: any) {
        console.error('下载错误文件失败:', error)
      }
    }
  }

  /** 是否已初始化 */
  const initialized = ref(false)

  /** 初始化数据 */
  const init = async () => {
    if (initialized.value) return
    await Promise.all([fetchList(), fetchStatistics()])
    initialized.value = true
  }

  return {
    // state
    list,
    total,
    pageNum,
    pageSize,
    loading,
    searchParams,
    statistics,
    activeList,
    activeListLoading,
    detailVisible,
    formVisible,
    currentMaterialId,
    mappingDialogVisible,
    currentMappingMaterial,
    foodImportLoading,
    latestFoodImportResult,
    mappings,
    foodLibraryReady,
    foodLibraryCategoryCount,
    foodLibraryStatus,
    foodLibraryStatusMessage,
    initialized,
    importDialogVisible,
    importResultVisible,
    importResult,
    // actions
    fetchList,
    fetchStatistics,
    fetchActiveList,
    search,
    resetSearch,
    changePage,
    changePageSize,
    deleteMaterial,
    openDetail,
    closeDetail,
    openForm,
    closeForm,
    openMappingDialog,
    closeMappingDialog,
    toggleStatus,
    getMaterialDetail,
    openImportDialog,
    closeImportDialog,
    closeImportResult,
    downloadTemplate,
    handleImport,
    handleExport,
    downloadErrorFile,
    importFoodJson,
    fetchFoodLibraryStatus,
    fetchMappings,
    init
  }
})
