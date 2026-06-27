import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  RecipePlan,
  RecipePlanDetail,
  RecipePlanForm,
  RecipePlanQuery,
  StockValidation,
  PlanImportResult
} from '@/types/plan'
import {
  getPlanList,
  getPlanDetail as getPlanDetailApi,
  createPlan as createPlanApi,
  updatePlan as updatePlanApi,
  deletePlan as deletePlanApi,
  submitPlan as submitPlanApi,
  auditPlan as auditPlanApi,
  validateStock as validateStockApi,
  downloadPlanImportTemplate as downloadPlanImportTemplateApi,
  importPlans as importPlansApi,
  downloadPlanImportErrorFile as downloadPlanImportErrorFileApi,
  resubmitPlan as resubmitPlanApi,
  withdrawPlan as withdrawPlanApi
} from '@/api/modules/plan'

export const usePlanStore = defineStore('plan', () => {
  /** 列表数据 */
  const list = ref<RecipePlan[]>([])

  /** 总数 */
  const total = ref(0)

  /** 当前页码 */
  const pageNum = ref(1)

  /** 每页条数 */
  const pageSize = ref(10)

  /** 加载状态 */
  const loading = ref(false)

  /** 搜索参数 */
  const searchParams = ref<Partial<RecipePlanQuery>>({
    planCode: '',
    orgName: '',
    startDateStart: '',
    startDateEnd: '',
    mealType: undefined,
    status: undefined
  })

  /** 详情弹窗 */
  const detailVisible = ref(false)

  /** 表单弹窗 */
  const formVisible = ref(false)

  /** 当前查看的计划 */
  const currentPlan = ref<RecipePlanDetail | null>(null)

  /** 当前编辑的计划 */
  const editingPlan = ref<RecipePlanDetail | null>(null)

  /** 当前计划ID */
  const currentPlanId = ref<number | null>(null)

  /** 库存校验结果 */
  const stockValidation = ref<StockValidation | null>(null)

  /** 导入弹窗 */
  const importDialogVisible = ref(false)

  /** 导入结果弹窗 */
  const importResultVisible = ref(false)

  /** 导入结果 */
  const importResult = ref<PlanImportResult | null>(null)

  /** 获取计划列表 */
  const fetchList = async () => {
    loading.value = true
    try {
      const res = await getPlanList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      })

      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list || []
        total.value = res.data.total || 0
      }
    } catch (error: any) {
      console.error('获取计划列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  /** 搜索 */
  const search = async (params: Partial<RecipePlanQuery>) => {
    searchParams.value = {
      planCode: params.planCode,
      orgName: params.orgName,
      startDateStart: params.startDateStart,
      startDateEnd: params.startDateEnd,
      mealType: params.mealType,
      status: params.status
    }
    pageNum.value = 1
    await fetchList()
  }

  /** 重置搜索 */
  const resetSearch = async () => {
    searchParams.value = {
      planCode: '',
      orgName: '',
      startDateStart: '',
      startDateEnd: '',
      mealType: undefined,
      status: undefined
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

  /** 获取计划详情 */
  const getPlanDetail = async (id: number): Promise<RecipePlanDetail | null> => {
    try {
      const res = await getPlanDetailApi(id)
      if (res.code === 'SUCCESS' && res.data) {
        return res.data
      }
      return null
    } catch (error) {
      console.error('获取计划详情失败:', error)
      return null
    }
  }

  /** 新增计划 */
  const createPlan = async (data: RecipePlanForm) => {
    try {
      const res = await createPlanApi(data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('新增成功')
        closeForm()
        await fetchList()
        return true
      } else {
        throw new Error(res.message || '新增失败')
      }
    } catch (error: any) {
      return false
    }
  }

  /** 更新计划 */
  const updatePlan = async (id: number, data: Partial<RecipePlanForm>) => {
    try {
      const res = await updatePlanApi(id, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('更新成功')
        closeForm()
        await fetchList()
        return true
      } else {
        throw new Error(res.message || '更新失败')
      }
    } catch (error: any) {
      return false
    }
  }

  /** 删除计划 */
  const deletePlan = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认删除该菜谱计划？删除后将无法恢复', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })

      const res = await deletePlanApi(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        await fetchList()
      } else {
        throw new Error(res.message || '删除失败')
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // Error displayed by interceptor
      }
    }
  }

  /** 提交审核 */
  const submitPlan = async (id: number) => {
    try {
      const res = await submitPlanApi(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('提交成功，等待审核')
        await fetchList()
        return true
      } else {
        throw new Error(res.message || '提交失败')
      }
    } catch (error: any) {
      // Error displayed by interceptor
      return false
    }
  }

  /** 审核计划 */
  const auditPlan = async (id: number, status: 'approved' | 'rejected', remark?: string) => {
    try {
      const res = await auditPlanApi(id, { status, remark })
      if (res.code === 'SUCCESS') {
        ElMessage.success(status === 'approved' ? '审核通过' : '审核拒绝')
        await fetchList()
        return true
      }
      throw new Error(res.message || '审核失败')
    } catch (error: any) {
      // Error displayed by interceptor
      return false
    }
  }

  /** 撤回计划 */
  const withdrawPlan = async (id: number, reason: string) => {
    try {
      const res = await withdrawPlanApi(id, reason)
      if (res.code === 'SUCCESS') {
        ElMessage.success('计划已撤回至草稿状态')
        await fetchList()
        if (currentPlanId.value === id) {
          currentPlan.value = await getPlanDetail(id)
        }
        return true
      }
      throw new Error(res.message || '撤回失败')
    } catch (error: any) {
      ElMessage.error(error?.message || '撤回失败')
      return false
    }
  }

  /** 库存校验 */
  const validateStock = async (id: number): Promise<StockValidation | null> => {
    try {
      const res = await validateStockApi(id)
      if (res.code === 'SUCCESS' && res.data) {
        stockValidation.value = res.data
        return res.data
      }
      ElMessage.error(res.message || '库存校验失败')
      return null
    } catch (error: any) {
      console.error('库存校验失败:', error)
      return null
    }
  }

  /** 打开详情弹窗 */
  const openDetail = async (id: number) => {
    currentPlanId.value = id
    currentPlan.value = await getPlanDetail(id)
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = () => {
    detailVisible.value = false
    currentPlan.value = null
    currentPlanId.value = null
  }

  /** 打开表单弹窗 */
  const openForm = async (id: number | null = null) => {
    currentPlanId.value = id
    if (id) {
      editingPlan.value = await getPlanDetail(id)
    } else {
      editingPlan.value = null
    }
    formVisible.value = true
  }

  /** 关闭表单弹窗 */
  const closeForm = () => {
    formVisible.value = false
    editingPlan.value = null
    currentPlanId.value = null
  }

  /** 初始化数据 */
  const init = async () => {
    await fetchList()
  }

  // ========== 导入相关 ==========

  const openImportDialog = () => { importDialogVisible.value = true }
  const closeImportDialog = () => { importDialogVisible.value = false }
  const closeImportResult = () => {
    importResultVisible.value = false
    importResult.value = null
  }

  const downloadTemplate = async () => {
    try {
      await downloadPlanImportTemplateApi()
    } catch (error: any) {
      ElMessage.error(error?.message || '下载模板失败')
    }
  }

  const handleImport = async (file: File) => {
    try {
      const res = await importPlansApi(file)
      if (res.code === 'SUCCESS' && res.data) {
        importResult.value = res.data
        closeImportDialog()
        importResultVisible.value = true
        await fetchList()
      }
    } catch (error: any) {
      ElMessage.error(error?.message || '导入失败')
    }
  }

  const downloadErrorFile = async () => {
    if (importResult.value?.errorFileUrl) {
      const fileName = importResult.value.errorFileUrl.split('/').pop() || 'error.xlsx'
      try {
        await downloadPlanImportErrorFileApi(fileName)
      } catch (error: any) {
        ElMessage.error(error?.message || '下载错误文件失败')
      }
    }
  }

  /** 驳回后重新提交 */
  const resubmitPlan = async (id: number, data: Partial<RecipePlanForm>) => {
    try {
      const res = await resubmitPlanApi(id, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('重新提交成功，等待审核')
        closeForm()
        await fetchList()
        return true
      } else {
        throw new Error(res.message || '重新提交失败')
      }
    } catch (error: any) {
      ElMessage.error(error?.message || '重新提交失败')
      return false
    }
  }

  return {
    // state
    list,
    total,
    pageNum,
    pageSize,
    loading,
    searchParams,
    detailVisible,
    formVisible,
    currentPlan,
    editingPlan,
    currentPlanId,
    stockValidation,
    // 导入
    importDialogVisible,
    importResultVisible,
    importResult,
    // actions
    fetchList,
    search,
    resetSearch,
    changePage,
    changePageSize,
    getPlanDetail,
    createPlan,
    updatePlan,
    deletePlan,
    submitPlan,
    auditPlan,
    withdrawPlan,
    validateStock,
    openDetail,
    closeDetail,
    openForm,
    closeForm,
    init,
    // 导入 actions
    openImportDialog,
    closeImportDialog,
    closeImportResult,
    downloadTemplate,
    handleImport,
    downloadErrorFile,
    resubmitPlan
  }
})
