<script setup lang="ts">
import { ref, onMounted, onActivated, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { usePlanStore } from '@/stores/modules/plan'
import { useUserStore } from '@/stores/modules/user'
import {
  RECIPE_PLAN_STATUS_OPTIONS,
  RECIPE_PLAN_STATUS_MAP,
  MEAL_TYPE_MAP,
  ADJUSTMENT_STATUS_MAP,
  STOCK_RISK_STATUS_MAP
} from '@/types/plan'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { RecipePlan, RecipePlanForm, RecipePlanDetail, StockValidation, BatchOperationResult } from '@/types/plan'
import { getPlanStatistics, exportPlans, batchDeletePlans, batchAuditPlans } from '@/api/modules/plan'
import PlanDetail from '@/components/business/plan/PlanDetail.vue'
import PlanForm from '@/components/business/plan/PlanForm.vue'
import PlanAdjustmentForm from '@/components/business/plan/PlanAdjustmentForm.vue'
import PlanAudit from '@/components/business/plan/PlanAudit.vue'
import PlanSubmitStockDialog from '@/components/business/plan/PlanSubmitStockDialog.vue'
import PlanImportDialog from '@/components/business/plan/PlanImportDialog.vue'
import PlanImportResultDialog from '@/components/business/plan/PlanImportResultDialog.vue'
import { PLAN_PERMISSIONS } from '@/constants/permission'
import iconPlanTotal from '@/assets/images/icon-plan-total.png'
import iconPlanApproved from '@/assets/images/icon-plan-approved.png'
import iconPlanPending from '@/assets/images/icon-plan-pending.png'
import iconPlanServings from '@/assets/images/icon-plan-servings.png'
import { formatDate } from '@/utils'

const planStore = usePlanStore()
const userStore = useUserStore()
const route = useRoute()

/** 表格序号（跨页连续） */
const indexMethod = (index: number) => (planStore.pageNum - 1) * planStore.pageSize + index + 1

const showPlanCodeTip = ref(false)
let planCodeTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerPlanCodeTip = () => {
  if (planCodeTipTimer) clearTimeout(planCodeTipTimer)
  showPlanCodeTip.value = false
  requestAnimationFrame(() => { showPlanCodeTip.value = true })
  planCodeTipTimer = setTimeout(() => { showPlanCodeTip.value = false }, 2000)
}
const handlePlanCodeInput = () => {
  if (searchForm.value.planCode.length >= 20) triggerPlanCodeTip()
  else showPlanCodeTip.value = false
}
const handlePlanCodeKeydown = (e: KeyboardEvent) => {
  if (searchForm.value.planCode.length >= 20 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerPlanCodeTip()
  }
}

const showOrgNameTip = ref(false)
let orgNameTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerOrgNameTip = () => {
  if (orgNameTipTimer) clearTimeout(orgNameTipTimer)
  showOrgNameTip.value = false
  requestAnimationFrame(() => { showOrgNameTip.value = true })
  orgNameTipTimer = setTimeout(() => { showOrgNameTip.value = false }, 2000)
}
const handleOrgNameInput = () => {
  if (searchForm.value.orgName.length >= 20) triggerOrgNameTip()
  else showOrgNameTip.value = false
}
const handleOrgNameKeydown = (e: KeyboardEvent) => {
  if (searchForm.value.orgName.length >= 20 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerOrgNameTip()
  }
}

/** 搜索表单（从 store 恢复上次的查询条件） */
const searchForm = ref({
  planCode: planStore.searchParams.planCode || '',
  orgName: planStore.searchParams.orgName || '',
  mealType: planStore.searchParams.mealType as string | undefined,
  implDateRange: [
    planStore.searchParams.startDateStart || '',
    planStore.searchParams.startDateEnd || ''
  ].filter(Boolean) as string[],
  status: planStore.searchParams.status as string | undefined
})

/** 详情弹窗 */
const detailVisible = ref(false)
const currentPlan = ref<RecipePlan | null>(null)

/** 表单弹窗 */
const formVisible = ref(false)
const editingPlan = ref<RecipePlan | RecipePlanDetail | null>(null)
const formMode = ref<'create' | 'edit' | 'copy'>('create')
const editMode = ref<'default' | 'rejected'>('default')

/** 审核弹窗 */
const auditVisible = ref(false)
const auditingPlan = ref<RecipePlan | null>(null)
const auditSubmitting = ref(false)

/** 调整申请弹窗 */
const adjustmentVisible = ref(false)
const adjustingPlan = ref<RecipePlanDetail | null>(null)

/** 表格引用 */
const planTableRef = ref()

/** 库存校验结果弹窗 */
const stockDialogVisible = ref(false)
const stockValidationResult = ref<StockValidation | null>(null)
const stockDialogConfirmable = ref(false)
const stockDialogTitle = ref('库存校验提醒')
const stockDialogConfirmText = ref('继续提交')
const stockDialogPlanCode = ref('')
const pendingSubmitPlan = ref<RecipePlan | null>(null)
const planActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'diner') return '来自数据监管看板：就餐人数关联排餐计划'
  if (route.query.metric === 'recipe') return '来自数据监管看板：菜谱执行数关联排餐计划'
  return '来自数据监管看板'
})

/** 统计数据（从后端获取全量统计） */
const planStats = ref({ total: 0, approvedCount: 0, pendingCount: 0, totalServings: 0 })

const statsData = computed(() => [
  { title: '计划总数', value: planStats.value.total, iconImg: iconPlanTotal, bgColor: '#7288FA', valueColor: '#04FBFF' },
  { title: '已审核', value: planStats.value.approvedCount, iconImg: iconPlanApproved, bgColor: '#38CB89', valueColor: '#FFFFFF' },
  { title: '待审核', value: planStats.value.pendingCount, iconImg: iconPlanPending, bgColor: '#FDBA00', valueColor: '#FFFFFF' },
  { title: '总份数', value: planStats.value.totalServings, iconImg: iconPlanServings, bgColor: '#3CC7EC', valueColor: '#FFFFFF' }
])

const fetchStats = async () => {
  try {
    const res = await getPlanStatistics()
    if (res.code === 'SUCCESS') {
      planStats.value = res.data
    }
  } catch { /* ignore */ }
}

/** 初始化：有缓存数据时仅刷新统计，不重置列表 */
onMounted(async () => {
  if (planStore.list.length > 0) {
    await fetchStats()
  } else {
    await Promise.all([
      planStore.init(),
      fetchStats()
    ])
  }
  await applyDashboardRouteQuery()
})

const refreshPlanPage = async () => {
  await Promise.all([
    planStore.fetchList(),
    fetchStats()
  ])
}

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey
  const orgName = typeof route.query.canteen === 'string'
    ? route.query.canteen
    : typeof route.query.organization === 'string'
      ? route.query.organization
      : ''

  searchForm.value = {
    planCode: typeof route.query.planCode === 'string' ? route.query.planCode : '',
    orgName,
    mealType: typeof route.query.mealType === 'string' ? route.query.mealType : undefined,
    implDateRange: (
      typeof route.query.startDate === 'string' && typeof route.query.endDate === 'string'
        ? [route.query.startDate, route.query.endDate]
        : []
    ) as string[],
    status: typeof route.query.status === 'string' ? route.query.status : undefined
  }

  await handleSearch()

  if (openDetailOnce && planStore.list[0]) {
    await handleDetail(planStore.list[0])
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

onActivated(async () => {
  if (!planActivatedOnce.value) {
    planActivatedOnce.value = true
    return
  }
  if (await applyDashboardRouteQuery()) {
    return
  }
  await refreshPlanPage()
})

/** 搜索 */
const handleSearch = () => {
  return planStore.search({
    planCode: searchForm.value.planCode || undefined,
    orgName: searchForm.value.orgName || undefined,
    startDateStart: searchForm.value.implDateRange?.[0] || undefined,
    startDateEnd: searchForm.value.implDateRange?.[1] || undefined,
    mealType: searchForm.value.mealType,
    status: searchForm.value.status
  })
}

/** 重置 */
const handleReset = () => {
  searchForm.value = {
    planCode: '',
    orgName: '',
    mealType: undefined,
    implDateRange: [],
    status: undefined
  }
  planStore.resetSearch()
}

/** 新增 */
const handleAdd = () => {
  editingPlan.value = null
  formMode.value = 'create'
  formVisible.value = true
}

/** 复制计划（打开编辑表单，预填源计划数据） */
const handleCopy = async (row: RecipePlan | RecipePlanDetail) => {
  try {
    const detail = await planStore.getPlanDetail(row.id)
    editingPlan.value = detail
    formMode.value = 'copy'
    formVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '获取计划详情失败')
  }
}

/** 导出 */
const exportLoading = ref(false)
const handleExport = async () => {
  exportLoading.value = true
  try {
    await exportPlans({
      planCode: searchForm.value.planCode || undefined,
      orgName: searchForm.value.orgName || undefined,
      startDateStart: searchForm.value.implDateRange?.[0] || undefined,
      startDateEnd: searchForm.value.implDateRange?.[1] || undefined,
      mealType: searchForm.value.mealType,
      status: searchForm.value.status
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exportLoading.value = false
  }
}

/** 详情 */
const handleDetail = async (row: RecipePlan) => {
  const detail = await planStore.getPlanDetail(row.id)
  if (detail) {
    currentPlan.value = detail
    detailVisible.value = true
  }
}

/** 刷新详情 */
const handleRefreshDetail = async () => {
  if (currentPlan.value) {
    const detail = await planStore.getPlanDetail(currentPlan.value.id)
    if (detail) {
      currentPlan.value = detail
    }
  }
}

/** 从详情页按驳回意见修改 */
const handleModifyRejected = (plan: RecipePlanDetail) => {
  editingPlan.value = plan
  formMode.value = 'edit'
  editMode.value = 'rejected'
  formVisible.value = true
}

/** 编辑 */
const handleEdit = async (row: RecipePlan) => {
  const detail = await planStore.getPlanDetail(row.id)
  if (detail) {
    editingPlan.value = detail
    formMode.value = 'edit'
    editMode.value = 'default'
    formVisible.value = true
  }
}

/** 按驳回意见修改 */
const handleEditRejected = async (row: RecipePlan) => {
  const detail = await planStore.getPlanDetail(row.id)
  if (detail) {
    editingPlan.value = detail
    formMode.value = 'edit'
    editMode.value = 'rejected'
    formVisible.value = true
  }
}

/** 删除 */
const handleDelete = async (row: RecipePlan) => {
  await planStore.deletePlan(row.id)
  fetchStats()
}

/** 提交审核 */
const handleSubmit = async (row: RecipePlan) => {
  try {
    await ElMessageBox.confirm('确认提交该菜谱计划进行审核？', '提示', {
      type: 'info',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })

    // 先进行库存校验
    const validation = await planStore.validateStock(row.id)
    const stockResult = validation ?? buildUnknownStockValidation()
    if (shouldShowStockReminder(stockResult)) {
      openStockDialog({
        plan: row,
        result: stockResult,
        confirmable: true,
        title: '提交前库存校验提醒',
        confirmText: '继续提交'
      })
      return
    }

    const submitted = await planStore.submitPlan(row.id)
    if (submitted) {
      fetchStats()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '提交失败')
    }
  }
}

/** 打开审核弹窗 */
const handleAudit = (row: RecipePlan) => {
  auditingPlan.value = row
  auditVisible.value = true
}

/** 确认审核 */
const confirmAudit = async (status: 'approved' | 'rejected', remark: string) => {
  if (!auditingPlan.value || auditSubmitting.value) return false

  const auditedPlanId = auditingPlan.value.id
  auditSubmitting.value = true
  try {
    const success = await planStore.auditPlan(auditedPlanId, status, remark)
    if (!success) return false

    auditVisible.value = false
    auditingPlan.value = null
    await fetchStats()
    if (currentPlan.value?.id === auditedPlanId) {
      await handleRefreshDetail()
    }
    return true
  } finally {
    auditSubmitting.value = false
  }
}

/** 库存校验 */
const handleValidateStock = async (row: RecipePlan) => {
  const result = await planStore.validateStock(row.id)
  const stockResult = result ?? buildUnknownStockValidation('库存校验失败，系统仅作提醒，请人工关注库存与效期情况。')

  if (shouldShowStockReminder(stockResult)) {
    openStockDialog({
      plan: row,
      result: stockResult,
      confirmable: false,
      title: '库存校验结果'
    })
    return
  }

  ElMessage.success('库存校验通过，当前暂无库存风险')
}

const handleRequestAdjustment = async (plan: RecipePlan | RecipePlanDetail) => {
  const detail = 'recipes' in plan ? plan : await planStore.getPlanDetail(plan.id)
  if (!detail) {
    ElMessage.error('获取计划详情失败')
    return
  }
  adjustingPlan.value = detail
  adjustmentVisible.value = true
}

const handleWithdraw = async (plan: RecipePlan | RecipePlanDetail) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入撤回原因', '撤回菜谱计划', {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请说明撤回原因（必填）',
      inputValidator: (value: string) => value?.trim() ? true : '撤回原因不能为空'
    })

    const success = await planStore.withdrawPlan(plan.id, reason.trim())
    if (!success) return

    await fetchStats()
    if (currentPlan.value?.id === plan.id) {
      await handleRefreshDetail()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.message || '撤回失败')
    }
  }
}

const handleAdjustmentSuccess = async () => {
  adjustmentVisible.value = false
  await planStore.fetchList()
  await fetchStats()
  if (currentPlan.value?.id) {
    await handleRefreshDetail()
  }
  adjustingPlan.value = null
}

/** 多选相关 */
const selectedPlans = ref<RecipePlan[]>([])
const batchResultVisible = ref(false)
const batchResult = ref<BatchOperationResult | null>(null)

const handleSelectionChange = (selection: RecipePlan[]) => {
  selectedPlans.value = selection
}

const clearSelection = () => {
  selectedPlans.value = []
  planTableRef.value?.clearSelection()
}

/** 批量删除 */
const handleBatchDelete = async () => {
  if (selectedPlans.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确认批量删除 ${selectedPlans.value.length} 条菜谱计划？仅草稿状态且无审批/出库/烹饪记录的计划可删除。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    const ids = selectedPlans.value.map(p => p.id)
    const res = await batchDeletePlans(ids)
    if (res.code === 'SUCCESS' && res.data) {
      batchResult.value = res.data
      batchResultVisible.value = true
      await refreshPlanPage()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '批量删除失败')
    }
  }
}

/** 批量审核通过 */
const handleBatchApprove = async () => {
  if (selectedPlans.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确认批量审核通过 ${selectedPlans.value.length} 条菜谱计划？`,
      '批量审核',
      { type: 'info', confirmButtonText: '确认通过', cancelButtonText: '取消' }
    )
    const ids = selectedPlans.value.map(p => p.id)
    const res = await batchAuditPlans(ids, 'approved')
    if (res.code === 'SUCCESS' && res.data) {
      batchResult.value = res.data
      batchResultVisible.value = true
      await refreshPlanPage()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '批量审核失败')
    }
  }
}

/** 批量审核驳回 */
const handleBatchReject = async () => {
  if (selectedPlans.value.length === 0) return
  try {
    const { value: remark } = await ElMessageBox.prompt('请输入驳回原因', '批量驳回', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请说明驳回原因（可选）'
    })
    const ids = selectedPlans.value.map(p => p.id)
    const res = await batchAuditPlans(ids, 'rejected', remark || undefined)
    if (res.code === 'SUCCESS' && res.data) {
      batchResult.value = res.data
      batchResultVisible.value = true
      await refreshPlanPage()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '批量驳回失败')
    }
  }
}

/** 获取失败分类标签 */
const getFailCategoryLabel = (category?: string) => {
  const map: Record<string, string> = {
    permission: '无权限',
    business_rule: '业务规则限制',
    system_error: '系统异常'
  }
  return category ? map[category] || category : '-'
}

watch(adjustmentVisible, (visible) => {
  if (!visible) {
    adjustingPlan.value = null
  }
})

watch(stockDialogVisible, (visible) => {
  if (!visible) {
    pendingSubmitPlan.value = null
    stockDialogConfirmable.value = false
    stockDialogTitle.value = '库存校验提醒'
    stockDialogConfirmText.value = '继续提交'
    stockDialogPlanCode.value = ''
    stockValidationResult.value = null
  }
})

/** 提交表单 */
const handleFormSubmit = async (data: RecipePlanForm, done?: () => void) => {
  try {
    if (formMode.value === 'edit' && editingPlan.value?.id) {
      const success = await planStore.updatePlan(editingPlan.value.id, data)
      if (success) {
        formVisible.value = false
        editingPlan.value = null
        formMode.value = 'create'
        editMode.value = 'default'
      }
    } else {
      const success = await planStore.createPlan(data)
      if (success) {
        formVisible.value = false
        editingPlan.value = null
        formMode.value = 'create'
        editMode.value = 'default'
      }
    }
  } finally {
    fetchStats()
    done?.()
  }
}

/** 保存为草稿（驳回后修改） */
const handleSaveDraft = async (data: RecipePlanForm, done?: () => void) => {
  try {
    if (editingPlan.value?.id) {
      const success = await planStore.updatePlan(editingPlan.value.id, data)
      if (success) {
        formVisible.value = false
        editingPlan.value = null
        formMode.value = 'create'
        editMode.value = 'default'
      }
    }
  } finally {
    fetchStats()
    done?.()
  }
}

/** 重新提交（驳回后） */
const handleResubmit = async (data: RecipePlanForm, done?: () => void) => {
  try {
    if (editingPlan.value?.id) {
      const success = await planStore.resubmitPlan(editingPlan.value.id, data)
      if (success) {
        formVisible.value = false
        editingPlan.value = null
        formMode.value = 'create'
        editMode.value = 'default'
      }
    }
  } finally {
    fetchStats()
    done?.()
  }
}

/** 总页数 */
const totalPages = computed(() => Math.ceil(planStore.total / planStore.pageSize))

/** 分页改变 */
const handlePageChange = (page: number) => {
  planStore.changePage(page)
}

/** 每页条数改变 */
const handleSizeChange = (size: number) => {
  planStore.changePageSize(size)
}

/** 状态标签 Figma 变体（success / default / brand / warning） */
const getStatusVariant = (status: string) => {
  const map: Record<string, string> = {
    draft: 'default',
    pending: 'warning',
    approved: 'success',
    rejected: 'brand',
    completed: 'success'
  }
  return map[status] || 'default'
}

const getAdjustmentStatusVariant = (status?: string) => {
  const map: Record<string, string> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'brand'
  }
  return status ? (map[status] || 'default') : 'default'
}

const getStockRiskVariant = (status?: string) => {
  const map: Record<string, string> = {
    normal: 'success',
    warning: 'warning',
    expired: 'warning',
    shortage: 'warning',
    unknown: 'default'
  }
  return status ? (map[status] || 'default') : 'default'
}

/** 餐次类型 → Figma 标签颜色变体 */
const getMealTypeVariant = (mealType?: string) => {
  const map: Record<string, string> = {
    breakfast: 'success',
    lunch: 'brand',
    dinner: 'warning',
    supper: 'default'
  }
  return mealType ? (map[mealType] || 'default') : 'default'
}

const buildUnknownStockValidation = (message = '库存校验执行失败，系统仅作提醒，不影响继续提交，请人工关注库存与效期情况。'): StockValidation => ({
  passed: false,
  message,
  riskStatus: 'unknown',
  riskStatusName: '待人工确认',
  shortageItems: [],
  materialStockStatuses: []
})

const shouldShowStockReminder = (result: StockValidation | null) => {
  if (!result) {
    return true
  }
  return (result.riskStatus || 'unknown') !== 'normal'
}

const openStockDialog = (options: {
  plan: RecipePlan
  result: StockValidation
  confirmable: boolean
  title: string
  confirmText?: string
}) => {
  pendingSubmitPlan.value = options.confirmable ? options.plan : null
  stockValidationResult.value = options.result
  stockDialogConfirmable.value = options.confirmable
  stockDialogTitle.value = options.title
  stockDialogConfirmText.value = options.confirmText || '继续提交'
  stockDialogPlanCode.value = options.plan.planCode
  stockDialogVisible.value = true
}

const handleStockDialogConfirm = async () => {
  if (!pendingSubmitPlan.value) {
    stockDialogVisible.value = false
    return
  }
  const submitted = await planStore.submitPlan(pendingSubmitPlan.value.id)
  if (submitted) {
    stockDialogVisible.value = false
    fetchStats()
  }
}

type PlanActionCommand = 'edit' | 'delete' | 'submit' | 'audit' | 'validate' | 'edit_rejected' | 'copy' | 'adjust' | 'withdraw'

interface PlanActionItem {
  command: PlanActionCommand
  label: string
  type: 'primary' | 'warning' | 'danger'
}

const getSecondaryActionItems = (row: RecipePlan): PlanActionItem[] => {
  const items: PlanActionItem[] = []

  if (row.status === 'draft' && userStore.hasPermission(PLAN_PERMISSIONS.EDIT)) {
    items.push({ command: 'edit', label: '编辑', type: 'warning' })
  }

  if (row.status === 'rejected' && userStore.hasPermission(PLAN_PERMISSIONS.EDIT)) {
    items.push({ command: 'edit_rejected', label: '按驳回意见修改', type: 'warning' })
  }

  if ((row.status === 'draft' || row.status === 'rejected') && userStore.hasPermission(PLAN_PERMISSIONS.DELETE)) {
    items.push({ command: 'delete', label: '删除', type: 'danger' })
  }

  if (row.status === 'draft' && userStore.hasPermission(PLAN_PERMISSIONS.SUBMIT)) {
    items.push({ command: 'submit', label: '提交', type: 'primary' })
  }

  if (row.status === 'pending' && userStore.hasPermission(PLAN_PERMISSIONS.APPROVE)) {
    items.push({ command: 'audit', label: '审核', type: 'primary' })
  }

  if (row.status === 'approved') {
    if (row.canAdjust !== false && userStore.hasPermission(PLAN_PERMISSIONS.ADJUST)) {
      items.push({ command: 'adjust', label: row.hasPendingAdjustment ? '查看调整进度' : '调整', type: 'primary' })
    }
    if (userStore.hasPermission(PLAN_PERMISSIONS.APPROVE)) {
      items.push({ command: 'withdraw', label: '撤回', type: 'warning' })
    }
    items.push({ command: 'validate', label: '库存', type: 'primary' })
  }

  if (userStore.hasPermission(PLAN_PERMISSIONS.CREATE)) {
    items.push({ command: 'copy', label: '复制', type: 'primary' })
  }

  return items
}

const getVisibleSecondaryActionItems = (row: RecipePlan) => {
  const visible: PlanActionCommand[] = ['edit', 'delete', 'adjust', 'withdraw']
  return getSecondaryActionItems(row).filter(item => visible.includes(item.command))
}

const hasMoreActionItems = (row: RecipePlan) => {
  const visible: PlanActionCommand[] = ['edit', 'delete', 'adjust', 'withdraw']
  return getSecondaryActionItems(row).some(item => !visible.includes(item.command))
}

const getMoreActionItems = (row: RecipePlan) => {
  const visible: PlanActionCommand[] = ['edit', 'delete', 'adjust', 'withdraw']
  return getSecondaryActionItems(row).filter(item => !visible.includes(item.command))
}

const handlePlanAction = (row: RecipePlan, command: PlanActionCommand) => {
  if (command === 'edit') {
    handleEdit(row)
    return
  }
  if (command === 'edit_rejected') {
    handleEditRejected(row)
    return
  }
  if (command === 'delete') {
    handleDelete(row)
    return
  }
  if (command === 'submit') {
    handleSubmit(row)
    return
  }
  if (command === 'audit') {
    handleAudit(row)
    return
  }
  if (command === 'validate') {
    handleValidateStock(row)
    return
  }
  if (command === 'adjust') {
    handleRequestAdjustment(row)
    return
  }
  if (command === 'withdraw') {
    handleWithdraw(row)
    return
  }
  if (command === 'copy') {
    handleCopy(row)
  }
}
</script>

<template>
  <div class="plan-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已按看板当前时间范围和组织范围筛出排餐计划，可直接核对计划份数、菜品明细和审核状态。"
    />

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div
        v-for="stat in statsData"
        :key="stat.title"
        class="stat-card"
        :style="{ background: stat.bgColor }"
      >
        <div class="stat-icon" :class="{ 'stat-icon--image': stat.iconImg }">
          <img v-if="stat.iconImg" :src="stat.iconImg" class="stat-icon-img" alt="" />
          <el-icon v-else-if="stat.icon === 'circle-check'"><CircleCheck /></el-icon>
          <el-icon v-else-if="stat.icon === 'clock'"><Clock /></el-icon>
          <el-icon v-else><Bowl /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-title">{{ stat.title }}</div>
          <div class="stat-value" :style="{ color: stat.valueColor }">{{ stat.value }}<span class="stat-unit">个</span></div>
        </div>
      </div>
    </div>

    <!-- 搜索工具栏 -->
    <div class="search-card">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item>
          <div style="position: relative; display: inline-block;">
            <transition name="input-tip">
              <span v-if="showPlanCodeTip" class="search-length-tip">计划单号最多输入20个字符</span>
            </transition>
            <el-input v-model="searchForm.planCode" placeholder="请输入计划单号" clearable maxlength="20" style="width: 200px" @input="handlePlanCodeInput" @keydown="handlePlanCodeKeydown" />
          </div>
        </el-form-item>
        <el-form-item>
          <div style="position: relative; display: inline-block;">
            <transition name="input-tip">
              <span v-if="showOrgNameTip" class="search-length-tip">门店名称最多输入20个字符</span>
            </transition>
            <el-input v-model="searchForm.orgName" placeholder="请输入门店名称" clearable maxlength="20" style="width: 200px" @input="handleOrgNameInput" @keydown="handleOrgNameKeydown" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.mealType" placeholder="全部餐次" clearable style="width: 200px">
            <el-option label="早餐" value="breakfast" />
            <el-option label="午餐" value="lunch" />
            <el-option label="晚餐" value="dinner" />
            <el-option label="宵夜" value="supper" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="searchForm.implDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 245px"
            :editable="false"
          />
        </el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 200px">
            <el-option
              v-for="item in RECIPE_PLAN_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button class="btn-search" @click="handleSearch">查询</el-button>
          <el-button class="btn-reset" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <!-- 操作工具栏 -->
      <div class="table-toolbar">
        <el-button class="btn-create" v-permission="PLAN_PERMISSIONS.CREATE" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增计划
        </el-button>
        <el-button class="btn-import" v-permission="PLAN_PERMISSIONS.IMPORT" @click="planStore.openImportDialog()">
          导入
        </el-button>
        <el-button class="btn-export" v-permission="PLAN_PERMISSIONS.EXPORT" :loading="exportLoading" @click="handleExport">
          导出
        </el-button>
      </div>

      <!-- 批量操作栏 -->
      <div v-if="selectedPlans.length > 0" class="batch-action-bar">
        <span class="batch-action-bar__info">
          已选择 <strong>{{ selectedPlans.length }}</strong> 项
          <el-button link type="info" @click="clearSelection">清空选择</el-button>
        </span>
        <div class="batch-action-bar__buttons">
          <el-button
            v-permission="PLAN_PERMISSIONS.DELETE"
            type="danger"
            size="small"
            @click="handleBatchDelete"
          >
            批量删除
          </el-button>
          <el-button
            v-permission="PLAN_PERMISSIONS.APPROVE"
            type="success"
            size="small"
            @click="handleBatchApprove"
          >
            批量通过
          </el-button>
          <el-button
            v-permission="PLAN_PERMISSIONS.APPROVE"
            type="warning"
            size="small"
            @click="handleBatchReject"
          >
            批量驳回
          </el-button>
        </div>
      </div>

      <el-table ref="planTableRef" :data="planStore.list" v-loading="planStore.loading" stripe class="plan-table" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column type="index" label="序号" width="70" align="center" :index="indexMethod" />
        <el-table-column prop="planCode" label="计划单号" width="150" />
        <el-table-column prop="orgName" label="适用门店" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.orgName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdByName" label="制定人" width="120">
          <template #default="{ row }">
            <span>{{ row.createdByName || '系统' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="mealType" label="餐次" width="100">
          <template #default="{ row }">
            <span class="status-tag" :class="'status-tag--' + getMealTypeVariant(row.mealType)">{{ row.mealDisplayName || MEAL_TYPE_MAP[row.mealType] || row.mealTypeName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="planDate" label="计划日期" width="120">
          <template #default="{ row }">
            <span>{{ formatDate(row.planDate, 'YYYY/MM/DD') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实施时间范围" width="190">
          <template #default="{ row }">
            <span v-if="row.startDate && row.endDate">{{ formatDate(row.startDate, 'YYYY/MM/DD') }}-{{ formatDate(row.endDate, 'YYYY/MM/DD') }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="recipeCount" label="菜谱数量" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.recipeCount || 0 }} 个</span>
          </template>
        </el-table-column>
        <el-table-column prop="estimatedCost" label="预估总成本" width="120">
          <template #default="{ row }">
            <span class="cost-text">¥{{ Math.round(row.estimatedCost || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-tag" :class="'status-tag--' + getStatusVariant(row.status)">
              {{ RECIPE_PLAN_STATUS_MAP[row.status] || row.statusName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="stockRiskStatus" label="库存风险" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.stockRiskStatus" class="status-tag" :class="'status-tag--' + getStockRiskVariant(row.stockRiskStatus)">
              {{ STOCK_RISK_STATUS_MAP[row.stockRiskStatus] || row.stockRiskStatusName }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="adjustmentStatus" label="调整计划状态" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.adjustmentStatus" class="status-tag" :class="'status-tag--' + getAdjustmentStatusVariant(row.adjustmentStatus)">
              {{ ADJUSTMENT_STATUS_MAP[row.adjustmentStatus] || row.adjustmentStatusName }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="plan-action-buttons">
              <div class="plan-action-buttons__fixed">
                <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
                <el-button
                  v-for="action in getVisibleSecondaryActionItems(row)"
                  :key="action.command"
                  link
                  :type="action.type"
                  @click="handlePlanAction(row, action.command)"
                >
                  {{ action.label }}
                </el-button>
              </div>

              <el-dropdown
                v-if="hasMoreActionItems(row)"
                trigger="click"
                @command="(command) => handlePlanAction(row, command as PlanActionCommand)"
              >
                <el-button link type="primary" class="plan-action-buttons__more">
                  更多
                  <el-icon class="plan-action-buttons__more-icon"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="action in getMoreActionItems(row)"
                      :key="action.command"
                      :command="action.command"
                    >
                      {{ action.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <span class="total-text">共 {{ planStore.total }} 条记录</span>
        <el-pagination
          v-model:current-page="planStore.pageNum"
          v-model:page-size="planStore.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="planStore.total"
          :pager-count="totalPages <= 7 ? 7 : 5"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <PlanDetail
      v-model="detailVisible"
      :plan="currentPlan"
      @refresh="handleRefreshDetail"
      @request-adjustment="handleRequestAdjustment"
      @request-withdraw="handleWithdraw"
      @modify-rejected="handleModifyRejected"
      @copy="handleCopy"
    />

    <!-- 表单弹窗 -->
    <PlanForm
      v-model="formVisible"
      :edit-data="editingPlan"
      :form-mode="formMode"
      :rejection-mode="editMode === 'rejected'"
      @submit="handleFormSubmit"
      @save-draft="handleSaveDraft"
      @resubmit="handleResubmit"
    />

    <!-- 审核弹窗 -->
    <PlanAudit
      v-model="auditVisible"
      :plan="auditingPlan"
      :submitting="auditSubmitting"
      @submit="confirmAudit"
    />

    <PlanAdjustmentForm
      v-model="adjustmentVisible"
      :plan="adjustingPlan"
      @success="handleAdjustmentSuccess"
    />

    <!-- 库存校验结果弹窗 -->
    <PlanSubmitStockDialog
      v-model="stockDialogVisible"
      :stock-result="stockValidationResult"
      :plan-code="stockDialogPlanCode"
      :title="stockDialogTitle"
      :confirmable="stockDialogConfirmable"
      :confirm-text="stockDialogConfirmText"
      @confirm="handleStockDialogConfirm"
    />

    <!-- 导入对话框 -->
    <PlanImportDialog />
    <PlanImportResultDialog />

    <!-- 批量操作结果弹窗 -->
    <el-dialog v-model="batchResultVisible" title="批量操作结果" width="680px" append-to-body>
      <template v-if="batchResult">
        <div class="batch-result-summary">
          <el-tag type="info" size="large">总计 {{ batchResult.totalCount }} 项</el-tag>
          <el-tag type="success" size="large">成功 {{ batchResult.successCount }} 项</el-tag>
          <el-tag type="danger" size="large">失败 {{ batchResult.failCount }} 项</el-tag>
        </div>
        <el-table :data="batchResult.results" border stripe max-height="360" style="margin-top: 16px">
          <el-table-column prop="planCode" label="计划单号" width="150" />
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="失败分类" width="120" align="center">
            <template #default="{ row }">
              <span v-if="!row.success">{{ getFailCategoryLabel(row.failCategory) }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="失败原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.failReason" class="batch-fail-reason">{{ row.failReason }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button type="primary" @click="batchResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.search-length-tip {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  background: #e6a23c;
  color: #fff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 4px;
  white-space: nowrap;
  z-index: 10;

  &::after {
    content: '';
    position: absolute;
    top: 100%;
    left: 16px;
    border: 5px solid transparent;
    border-top-color: #e6a23c;
  }
}
.input-tip-enter-active,
.input-tip-leave-active {
  transition: opacity 0.2s;
}
.input-tip-enter-from,
.input-tip-leave-to {
  opacity: 0;
}

.plan-action-buttons {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  width: 100%;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.plan-action-buttons__fixed {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  min-width: 0;
}

.plan-action-buttons__more {
  padding: 0;
  margin: 0;
  flex: 0 0 auto;
}

.plan-action-buttons__more-icon {
  margin-left: 4px;
  font-size: 12px;
}

.plan-action-buttons :deep(.el-dropdown) {
  flex: 0 0 auto;
}

.plan-page {
  min-height: 100%;
  padding: 20px;
  background-color: #f5f7fa;
}

// 表格工具栏
.table-toolbar {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  padding: 16px;

  // 新增计划：主按钮
  .btn-create {
    background: #7288FA;
    border: none;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
    color: #FFFFFF;
    font-size: 14px;
    height: 32px;
    padding: 5px 16px;

    &:hover,
    &:focus {
      background: #5E74E8;
      color: #FFFFFF;
    }
  }

  // 导入：次按钮
  .btn-import {
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #7288FA;
    font-size: 13px;
    height: 32px;
    padding: 5px 16px;

    &:hover,
    &:focus {
      background: #F0F2FF;
      border-color: #7288FA;
      color: #7288FA;
    }
  }

  // 导出：三级按钮
  .btn-export {
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #53545C;
    font-size: 13px;
    height: 32px;
    padding: 5px 16px;

    &:hover,
    &:focus {
      background: #F5F5F5;
      border-color: #BEC0CA;
      color: #53545C;
    }
  }
}

// 统计卡片
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1.2%;
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 24px;
  padding: 15px 24px;
  min-height: 100px;
  border-radius: 16px;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  }

  .stat-icon {
    width: 70px;
    height: 70px;
    flex: none;
    flex-grow: 0;
    order: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 50%;
    font-size: 33px;
    color: rgba(255, 255, 255, 0.9);

    // 当图标本身就是带圆形底的整张图（如 PNG 导出），容器不再重复绘制底色
    &--image {
      background: transparent;
      border-radius: 0;
    }

    .stat-icon-img {
      width: 70px;
      height: 70px;
      object-fit: contain;
      display: block;
    }
  }

  .stat-content {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .stat-title {
    font-size: 14px;
    font-weight: 400;
    line-height: 20px;
    color: #ffffff;
  }

  .stat-value {
    font-size: 24px;
    font-weight: 600;
    line-height: 34px;
    color: #04FBFF;

    .stat-unit {
      font-family: 'PingFang SC', sans-serif;
      font-weight: 600;
      font-size: 24px;
      line-height: 100%;
      letter-spacing: 0;
      margin-left: 2px;
    }
  }
}

// 搜索卡片
.search-card {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  margin-bottom: 16px;

  .search-form {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 24px;

    :deep(.el-form-item) {
      margin-bottom: 0;
      margin-right: 0;
    }

    :deep(.el-form-item__label) {
      color: #606266;
      font-weight: 500;
    }

    // 输入框、下拉框、日期选择器统一边框
    :deep(.el-input__wrapper),
    :deep(.el-select .el-input__wrapper),
    :deep(.el-date-editor .el-input__wrapper) {
      border-radius: 3px;
      box-shadow: 0 0 0 1px #DCDCDC inset;
    }

    // 按钮组推到右侧
    .search-actions {
      margin-left: auto;
    }

    // 查询按钮：紫色主题
    .btn-search {
      background: #7288FA;
      border: none;
      border-radius: 6px;
      color: #FFFFFF;
      padding: 5px 16px;
      height: 32px;

      &:hover,
      &:focus {
        background: #5E74E8;
        color: #FFFFFF;
      }
    }

    // 重置按钮：浅灰
    .btn-reset {
      background: #F2F4F8;
      border: none;
      border-radius: 6px;
      color: rgba(0, 0, 0, 0.9);
      padding: 5px 16px;
      height: 32px;

      &:hover,
      &:focus {
        background: #E5E8EE;
        color: rgba(0, 0, 0, 0.9);
      }
    }
  }
}

// 表格卡片
.table-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  padding: 20px;

  .batch-action-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 16px;
    margin-bottom: 12px;
    background: #ecf5ff;
    border-radius: 6px;
    border: 1px solid #d9ecff;

    &__info {
      font-size: 14px;
      color: #409eff;

      strong {
        color: #409eff;
        font-size: 16px;
      }
    }

    &__buttons {
      display: flex;
      gap: 8px;
    }
  }

  .link-text {
    color: #409eff;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }

  .cost-text {
    color: #f56c6c;
    font-weight: 500;
  }

  .text-muted {
    color: #c0c4cc;
  }

  // ====== Figma 规范：表格样式 ======
  .plan-table {
    --el-table-border-color: #E7E7E7;
    --el-table-header-bg-color: #F5F9FF;
    --el-table-row-hover-bg-color: #F5F9FF;
    --el-table-tr-bg-color: #FFFFFF;
    border-radius: 8px;
    overflow: hidden;
    font-size: 14px;

    // 表头
    :deep(.el-table__header-wrapper) {
      th.el-table__cell {
        background: #F5F9FF !important;
        border-top: 1px solid #E7E7E7;
        border-bottom: 1px solid #E7E7E7;
        border-left: none;
        border-right: none;
        padding: 12px 16px;
        height: 46px;
        color: rgba(0, 0, 0, 0.4);
        font-weight: 400;
        font-size: 14px;

        .cell {
          color: rgba(0, 0, 0, 0.4);
          font-weight: 400;
          white-space: nowrap;
          padding: 0;
        }
      }
    }

    // 表体单元格
    :deep(.el-table__body-wrapper) {
      td.el-table__cell {
        border-bottom: 1px solid #E7E7E7;
        border-left: none;
        border-right: none;
        padding: 12px 24px 12px 16px;
        color: rgba(0, 0, 0, 0.9);
        font-size: 14px;
        height: 46px;

        .cell {
          padding: 0;
        }
      }

      // 交替行底色：奇数行白、偶数行浅蓝
      tr.el-table__row:nth-child(even) td.el-table__cell {
        background: #FAFCFF;
      }

      // 鼠标悬停时保持底色可读
      tr.el-table__row:hover > td.el-table__cell {
        background: #F5F9FF !important;
      }
    }

    // 去掉默认左右边线
    :deep(.el-table__inner-wrapper::before),
    :deep(.el-table__border-left-patch) {
      display: none;
    }
  }

  // ====== Figma 规范：状态标签 4 变体 ======
  .status-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 24px;
    padding: 0 8px;
    border-radius: 3px;
    border: 1px solid transparent;
    font-size: 12px;
    line-height: 1;
    white-space: nowrap;

    &--success {
      background: #E3F9E9;
      border-color: #2BA471;
      color: #2BA471;
    }

    &--default {
      background: #EFEFEF;
      border-color: #C0C5CA;
      color: #666666;
    }

    &--brand {
      background: #F2F3FF;
      border-color: #0052D9;
      color: #0052D9;
    }

    &--warning {
      background: #FFF1E9;
      border-color: #E37318;
      color: #E37318;
    }
  }

  // ====== Figma 规范：操作列链接按钮 ======
  .plan-action-buttons {
    .el-button.is-link {
      color: #5570F1;
      font-size: 14px;
      padding: 0;

      &:hover,
      &:focus {
        color: #5570F1;
        opacity: 0.8;
      }

      // 删除按钮（danger）红色文本
      &.el-button--danger {
        color: #FF7474;

        &:hover,
        &:focus {
          color: #FF7474;
          opacity: 0.8;
        }
      }
    }

    &__more {
      color: #5570F1;
    }
  }
}

// ====== Figma 规范：分页 ======
.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #E7E7E7;

  .total-text {
    font-size: 14px;
    color: rgba(0, 0, 0, 0.6);
  }

  :deep(.el-pagination) {
    .el-pager li,
    .btn-prev,
    .btn-next {
      width: 32px;
      height: 32px;
      min-width: 32px;
      padding: 0;
      margin: 0 4px;
      background: #FFFFFF;
      border: 1px solid #DCDCDC;
      border-radius: 4px;
      color: rgba(0, 0, 0, 0.9);
      font-size: 14px;
      font-weight: 400;

      &:hover {
        color: #7288FA;
        border-color: #7288FA;
      }
    }

    .el-pager li.is-active {
      background: #7288FA !important;
      border-color: #7288FA !important;
      color: #FFFFFF !important;
      border-radius: 3px;

      &:hover {
        color: #FFFFFF;
      }
    }

    .el-pagination__sizes .el-input__wrapper,
    .el-pagination__jump .el-input__wrapper {
      border-radius: 4px;
      box-shadow: 0 0 0 1px #DCDCDC inset;
    }

    .el-pagination__sizes {
      margin-right: 16px;
    }
  }
}

// 批量操作结果
.batch-result-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
}

.batch-fail-reason {
  color: #f56c6c;
  font-size: 13px;
}

@media (max-width: 1400px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: 1fr;
  }
}
</style>
