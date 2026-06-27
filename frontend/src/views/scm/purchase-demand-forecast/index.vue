<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { DataAnalysis, RefreshRight, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import purchaseDemandForecastApi from '@/api/modules/purchase-demand-forecast'
import { PURCHASE_PLAN_PERMISSIONS } from '@/constants/permission'
import { usePurchasePlanStore } from '@/stores/modules/purchase-plan'
import { useUserStore } from '@/stores/modules/user'
import type {
  PurchaseDemandForecastDashboard,
  PurchaseDemandForecastDimension,
  PurchaseDemandForecastExplanationFactor,
  PurchaseDemandForecastItemRecord,
  PurchaseDemandForecastLinkedPlanItemRecord,
  PurchaseDemandForecastLinkedPlanRecord,
  PurchaseDemandForecastPlanPrefill,
  PurchaseDemandForecastRecord,
} from '@/types/purchase-demand-forecast'

interface TokenUserPayload {
  userId?: number | string
  username?: string
  realName?: string
  orgId?: number | string
  orgName?: string
  tenantId?: number | string
  roles?: unknown
  permissions?: unknown
}

interface PurchaseDemandForecastLinkedPlanRow {
  planId: number
  planNo: string
  planName: string
  status: string
  planDate: string
  totalAmount: number
  createdBy: string
  createdAt: string
  materialName: string
  materialSpec: string
  unit: string
  quantity: number | null
  estimatedUnitPrice: number | null
  estimatedAmount: number | null
  remark: string
  groupKey: string
}

const userStore = useUserStore()
const purchasePlanStore = usePurchasePlanStore()
const router = useRouter()

const toNullableNumber = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

const toStringArray = (value: unknown) => {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
    ? value
    : []
}

const parseTokenPayload = (token: string): TokenUserPayload | null => {
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    return JSON.parse(atob(padded)) as TokenUserPayload
  } catch {
    return null
  }
}

const ensureUserInfoFromToken = () => {
  if (userStore.userInfo || !userStore.token) {
    return
  }
  const payload = parseTokenPayload(userStore.token)
  const orgId = toNullableNumber(payload?.orgId)
  if (!payload || !orgId) {
    return
  }
  userStore.setUserInfo({
    id: toNullableNumber(payload.userId) || 0,
    userName: payload.username || '用户',
    realName: payload.realName || payload.username || '用户',
    orgId,
    orgName: payload.orgName || '',
    tenantId: toNullableNumber(payload.tenantId) || undefined,
    roles: toStringArray(payload.roles),
    permissions: toStringArray(payload.permissions),
  })
}

const ensureUserInfoReady = async () => {
  ensureUserInfoFromToken()
  if (userStore.userInfo?.orgId || !userStore.token) {
    return
  }
  try {
    await userStore.fetchUserInfo()
  } catch (error) {
    console.error('获取采购需求预测页面用户信息失败:', error)
  } finally {
    ensureUserInfoFromToken()
  }
}

const PURCHASE_PLAN_STATUS_MAP: Record<string, { label: string; type: '' | 'warning' | 'success' | 'danger' | 'info' }> = {
  draft: { label: '草稿', type: 'info' },
  pending: { label: '待审核', type: 'warning' },
  approved: { label: '已审核', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  pending_void_approve: { label: '待作废审核', type: 'warning' },
  voided: { label: '已作废', type: 'info' },
}

const roundToAmount = (value: unknown) => Number((Number(value || 0)).toFixed(2))
const roundToQuantity = (value: unknown) => Number((Number(value || 0)).toFixed(3))
const roundToRate = (value: unknown) => Number((Number(value || 0)).toFixed(6))
const normalizeExplanationFactors = (value: unknown): PurchaseDemandForecastExplanationFactor[] => Array.isArray(value)
  ? value.map((item) => ({
      label: typeof item?.label === 'string' ? item.label : '',
      value: typeof item?.value === 'string' ? item.value : '',
      description: typeof item?.description === 'string' ? item.description : '',
    }))
  : []
const normalizePriorityLabel = (priority: unknown) => {
  if (priority === '紧急补货') return '紧急补货'
  if (priority === '优先补货') return '优先补货'
  return '正常补货'
}

const normalizeItem = (item: Partial<PurchaseDemandForecastItemRecord>): PurchaseDemandForecastItemRecord => ({
  id: Number(item.id || 0),
  materialId: Number(item.materialId || 0),
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  currentInventoryQty: roundToQuantity(item.currentInventoryQty),
  historicalPlanAvgQty: roundToQuantity(item.historicalPlanAvgQty),
  historicalOrderAvgQty: roundToQuantity(item.historicalOrderAvgQty),
  recipeDemandQty: roundToQuantity(item.recipeDemandQty),
  forecastDemandQty: roundToQuantity(item.forecastDemandQty),
  safetyStockQty: roundToQuantity(item.safetyStockQty),
  avgDailyDemandQty: roundToQuantity(item.avgDailyDemandQty),
  reviewPeriodDays: Number(item.reviewPeriodDays || 0),
  reorderPointQty: roundToQuantity(item.reorderPointQty),
  targetStockQty: roundToQuantity(item.targetStockQty),
  inventoryPositionQty: roundToQuantity(item.inventoryPositionQty),
  theoreticalSuggestedQty: roundToQuantity(item.theoreticalSuggestedQty),
  suggestedQty: roundToQuantity(item.suggestedQty),
  confidenceLowerQty: roundToQuantity(item.confidenceLowerQty),
  confidenceUpperQty: roundToQuantity(item.confidenceUpperQty),
  confidenceRate: Number(item.confidenceRate || 0),
  estimatedUnitPrice: roundToAmount(item.estimatedUnitPrice),
  estimatedAmount: roundToAmount(item.estimatedAmount),
  priority: normalizePriorityLabel(item.priority),
  modelType: item.modelType || '',
  materialSegment: item.materialSegment || '',
  forecastBasis: item.forecastBasis || '',
  explanationSummary: item.explanationSummary || '',
  explanationDetail: item.explanationDetail || '',
  explanationTemplateCode: item.explanationTemplateCode || '',
  explanationTitle: item.explanationTitle || '',
  warningLevel: item.warningLevel || 'low',
  warningMessage: item.warningMessage || '',
  approvalNote: item.approvalNote || '',
  manualReviewRequired: Boolean(item.manualReviewRequired),
  anomalyCodes: item.anomalyCodes || '',
  explanationSortScore: roundToRate(item.explanationSortScore),
  explanationFactors: normalizeExplanationFactors(item.explanationFactors),
  anomalyFlags: item.anomalyFlags || '',
  actualConsumptionQty: roundToQuantity(item.actualConsumptionQty),
  absError: roundToQuantity(item.absError),
  ape: roundToRate(item.ape),
  biasQty: roundToQuantity(item.biasQty),
  recipeDriveRatio: roundToRate(item.recipeDriveRatio),
  demandActiveRatio: roundToRate(item.demandActiveRatio),
  demandCv: roundToRate(item.demandCv),
  activitySensitivity: roundToRate(item.activitySensitivity),
  serviceLevel: roundToRate(item.serviceLevel),
  leadTimeDays: Number(item.leadTimeDays || 0),
  effectiveLeadTimeDays: roundToQuantity(item.effectiveLeadTimeDays),
  minOrderQty: roundToQuantity(item.minOrderQty),
  packSize: roundToQuantity(item.packSize),
  maxAllowedStockQty: roundToQuantity(item.maxAllowedStockQty),
  maxCoverageDays: Number(item.maxCoverageDays || 0),
  recommendedSupplierId: toNullableNumber(item.recommendedSupplierId),
  recommendedSupplierName: item.recommendedSupplierName || '',
  supplierScore: roundToRate(item.supplierScore),
  supplierFillRate: roundToRate(item.supplierFillRate),
  supplierOnTimeRate: roundToRate(item.supplierOnTimeRate),
  orderNow: Boolean(item.orderNow),
  orderAction: item.orderAction || '',
  shortageCost: roundToAmount(item.shortageCost),
  holdingCost: roundToAmount(item.holdingCost),
  expiryRiskCost: roundToAmount(item.expiryRiskCost),
  orderProcessingCost: roundToAmount(item.orderProcessingCost),
  purchasePriceCost: roundToAmount(item.purchasePriceCost),
  totalCost: roundToAmount(item.totalCost),
  phaseThreeRiskFlags: item.phaseThreeRiskFlags || '',
  evaluationStatus: item.evaluationStatus || 'pending',
  occupiedLinkQty: roundToQuantity(item.occupiedLinkQty),
  availableLinkQty: roundToQuantity(item.availableLinkQty),
  materialPlanStatus: item.materialPlanStatus || '未占用',
})

const normalizeRecord = (record: Partial<PurchaseDemandForecastRecord>): PurchaseDemandForecastRecord => ({
  id: Number(record.id || 0),
  forecastNo: record.forecastNo || '',
  forecastName: record.forecastName || '',
  orgId: record.orgId ?? null,
  orgName: record.orgName || '',
  dimension: (record.dimension || 'weekly') as PurchaseDemandForecastDimension,
  forecastDays: Number(record.forecastDays || 0),
  basisDate: record.basisDate || '',
  horizonStartDate: record.horizonStartDate || '',
  horizonEndDate: record.horizonEndDate || '',
  materialCount: Number(record.materialCount || 0),
  totalSuggestedAmount: roundToAmount(record.totalSuggestedAmount),
  calendarFactor: Number(record.calendarFactor || 1),
  holidayFactor: Number(record.holidayFactor || 1),
  activityFactor: Number(record.activityFactor || 1),
  summaryBasis: record.summaryBasis || '',
  generatedBy: record.generatedBy || '',
  generatedAt: record.generatedAt || '',
  evaluationStatus: record.evaluationStatus || 'pending',
  evaluatedAt: record.evaluatedAt || '',
  wape: roundToRate(record.wape),
  mape: roundToRate(record.mape),
  biasRate: roundToRate(record.biasRate),
  stockoutRate: roundToRate(record.stockoutRate),
  oversupplyRate: roundToRate(record.oversupplyRate),
  optimizationVersion: Number(record.optimizationVersion || 0),
  optimizationScore: roundToRate(record.optimizationScore),
  explanationSummary: record.explanationSummary || '',
  approvalSummary: record.approvalSummary || '',
  reorderTriggeredCount: Number(record.reorderTriggeredCount || 0),
  riskItemCount: Number(record.riskItemCount || 0),
  supplierRecommendedCount: Number(record.supplierRecommendedCount || 0),
  manualReviewCount: Number(record.manualReviewCount || 0),
  warningItemCount: Number(record.warningItemCount || 0),
  totalOptimizationCost: roundToAmount(record.totalOptimizationCost),
  materialPlanStatus: record.materialPlanStatus || '未占用',
  items: Array.isArray(record.items) ? record.items.map((item) => normalizeItem(item)) : [],
})

const normalizeDashboard = (value: Partial<PurchaseDemandForecastDashboard>): PurchaseDemandForecastDashboard => ({
  orgId: value.orgId ?? null,
  orgName: value.orgName || '',
  pendingEvaluationCount: Number(value.pendingEvaluationCount || 0),
  evaluatedForecastCount: Number(value.evaluatedForecastCount || 0),
  wape: roundToRate(value.wape),
  mape: roundToRate(value.mape),
  biasRate: roundToRate(value.biasRate),
  stockoutRate: roundToRate(value.stockoutRate),
  oversupplyRate: roundToRate(value.oversupplyRate),
  optimizedConfigCount: Number(value.optimizedConfigCount || 0),
  lastOptimizationAt: value.lastOptimizationAt || '',
  lastEvaluationAt: value.lastEvaluationAt || '',
  reorderTriggeredCount: Number(value.reorderTriggeredCount || 0),
  riskItemCount: Number(value.riskItemCount || 0),
  supplierRecommendedCount: Number(value.supplierRecommendedCount || 0),
  avgOptimizationCost: roundToAmount(value.avgOptimizationCost),
  rollbackCount: Number(value.rollbackCount || 0),
  lastRollbackAt: value.lastRollbackAt || '',
  manualReviewCount: Number(value.manualReviewCount || 0),
  warningItemCount: Number(value.warningItemCount || 0),
  segmentSummaries: Array.isArray(value.segmentSummaries)
    ? value.segmentSummaries.map((item) => ({
        materialSegment: item.materialSegment || '',
        modelType: item.modelType || '',
        materialCount: Number(item.materialCount || 0),
        totalSuggestedQty: roundToQuantity(item.totalSuggestedQty),
        totalActualQty: roundToQuantity(item.totalActualQty),
        totalOptimizationCost: roundToAmount(item.totalOptimizationCost),
        wape: roundToRate(item.wape),
        biasRate: roundToRate(item.biasRate),
      }))
    : [],
  optimizationSummaries: Array.isArray(value.optimizationSummaries)
    ? value.optimizationSummaries.map((item) => ({
        materialSegment: item.materialSegment || '',
        modelType: item.modelType || '',
        versionNo: Number(item.versionNo || 0),
        score: roundToRate(item.score),
        wape: roundToRate(item.wape),
        stockoutRate: roundToRate(item.stockoutRate),
        oversupplyRate: roundToRate(item.oversupplyRate),
        optimizedAt: item.optimizedAt || '',
        rollbackApplied: Boolean(item.rollbackApplied),
        rollbackReason: item.rollbackReason || '',
      }))
    : [],
  supplierSummaries: Array.isArray(value.supplierSummaries)
    ? value.supplierSummaries.map((item) => ({
        supplierId: toNullableNumber(item.supplierId),
        supplierName: item.supplierName || '',
        recommendCount: Number(item.recommendCount || 0),
        avgSupplierScore: roundToRate(item.avgSupplierScore),
        avgEffectiveLeadTimeDays: roundToQuantity(item.avgEffectiveLeadTimeDays),
      }))
    : [],
})

const normalizeLinkedPlanItem = (
  item: Partial<PurchaseDemandForecastLinkedPlanItemRecord>,
): PurchaseDemandForecastLinkedPlanItemRecord => ({
  id: Number(item.id || 0),
  materialId: Number(item.materialId || 0),
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  quantity: roundToQuantity(item.quantity),
  estimatedUnitPrice: roundToAmount(item.estimatedUnitPrice),
  estimatedAmount: roundToAmount(item.estimatedAmount),
  remark: item.remark || '',
})

const normalizeLinkedPlan = (
  record: Partial<PurchaseDemandForecastLinkedPlanRecord>,
): PurchaseDemandForecastLinkedPlanRecord => ({
  id: Number(record.id || 0),
  planNo: record.planNo || '',
  planName: record.planName || '',
  status: record.status || '',
  planDate: record.planDate || '',
  totalAmount: roundToAmount(record.totalAmount),
  createdBy: record.createdBy || '',
  createdAt: record.createdAt || '',
  items: Array.isArray(record.items) ? record.items.map((item) => normalizeLinkedPlanItem(item)) : [],
})

const normalizePrefill = (value: Partial<PurchaseDemandForecastPlanPrefill>): PurchaseDemandForecastPlanPrefill => ({
  forecastId: Number(value.forecastId || 0),
  forecastNo: value.forecastNo || '',
  planName: value.planName || '',
  orgId: Number(value.orgId || 0),
  orgName: value.orgName || '',
  planDate: value.planDate || '',
  budgetAmount: roundToAmount(value.budgetAmount),
  createdBy: value.createdBy || '',
  relatedDocument: value.relatedDocument || '',
  items: Array.isArray(value.items)
    ? value.items.map((item) => ({
        forecastDetailId: Number(item.forecastDetailId || 0),
        materialId: Number(item.materialId || 0),
        materialName: item.materialName || '',
        materialSpec: item.materialSpec || '',
        unit: item.unit || '',
        quantity: roundToQuantity(item.quantity),
        estimatedUnitPrice: roundToAmount(item.estimatedUnitPrice),
        estimatedAmount: roundToAmount(item.estimatedAmount),
      }))
    : [],
})

const dimensionOptions = [
  { label: '日维度', value: 'daily' },
  { label: '周维度', value: 'weekly' },
] as const

const runForm = reactive({
  orgId: userStore.userInfo?.orgId || null,
  dimension: 'weekly' as PurchaseDemandForecastDimension,
})

const historyForm = reactive({
  keyword: '',
  dimension: '' as PurchaseDemandForecastDimension | '',
})

const currentForecast = ref<PurchaseDemandForecastRecord | null>(null)
const selectedItems = ref<PurchaseDemandForecastItemRecord[]>([])
const historyList = ref<PurchaseDemandForecastRecord[]>([])
const historyLoading = ref(false)
const forecastRunning = ref(false)
const dashboardLoading = ref(false)
const analyticsRefreshing = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const detailRecord = ref<PurchaseDemandForecastRecord | null>(null)
const linkedPlansLoading = ref(false)
const detailLinkedPlans = ref<PurchaseDemandForecastLinkedPlanRecord[]>([])
const explanationVisible = ref(false)
const explanationItem = ref<PurchaseDemandForecastItemRecord | null>(null)
const dashboard = ref<PurchaseDemandForecastDashboard | null>(null)
const currentSegmentFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const historyActivatedOnce = ref(false)
const showCurrentForecastExplanationSummary = false
const showCurrentForecastApprovalSummary = false
const showCurrentForecastSegmentColumn = false
const showCurrentForecastModelColumn = false
const showCurrentForecastReorderPointColumn = false
const showCurrentForecastTotalCostColumn = false
const showHistoryEvaluationStatusColumn = false
const showHistoryWapeColumn = false
const showDetailEvaluationStatusSummary = false
const showDetailWapeBiasSummary = false
const showDetailStockoutOversupplySummary = false
const showDetailOptimizationVersionSummary = false
const showDetailReorderRiskSummary = false
const showDetailManualWarningSummary = false
const showDetailSupplierCostSummary = false
const showDetailSegmentColumn = false
const showDetailModelColumn = false
const showDetailReorderPointColumn = false
const showDetailActualConsumptionColumn = false
const showDetailApeColumn = false
const showDetailTotalCostColumn = false
const showDetailExplanationSummary = false
const showDetailApprovalSummary = false

const hasCreatePermission = computed(() => userStore.hasPermission(PURCHASE_PLAN_PERMISSIONS.CREATE))
const canGeneratePlan = computed(() => selectedItems.value.length > 0 && hasCreatePermission.value)

const formatAmount = (value: number) => `¥${roundToAmount(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const formatQuantity = (value: number) => roundToQuantity(value).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 3 })
const getDimensionLabel = (dimension: PurchaseDemandForecastDimension) => dimension === 'daily' ? '日维度' : '周维度'
const formatConfidenceRate = (value: number) => `${Number(value || 0).toFixed(1)}%`
const formatLeadTimeDays = (value: number) => roundToQuantity(value).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
const formatNullableAmount = (value: number | null) => value === null ? '—' : formatAmount(value)
const formatNullableQuantity = (value: number | null) => value === null ? '—' : formatQuantity(value)
const getPriorityTagType = (priority: string) => {
  const normalizedPriority = normalizePriorityLabel(priority)
  if (normalizedPriority === '紧急补货') return 'danger'
  if (normalizedPriority === '优先补货') return 'warning'
  return 'success'
}
const getPriorityTagClass = (priority: string) => {
  const normalizedPriority = normalizePriorityLabel(priority)
  if (normalizedPriority === '紧急补货') return 'priority-tag--urgent'
  if (normalizedPriority === '优先补货') return 'priority-tag--high'
  return 'priority-tag--normal'
}
const getPurchasePlanStatusLabel = (status: string) => PURCHASE_PLAN_STATUS_MAP[status]?.label || status || '—'
const getPurchasePlanStatusType = (status: string) => PURCHASE_PLAN_STATUS_MAP[status]?.type || 'info'
const getMaterialPlanStatusLabel = (status: string) => {
  if (status === '全部占用') return '全部占用'
  if (status === '部分占用') return '部分占用'
  return '未占用'
}
const getMaterialPlanStatusType = (status: string) => {
  const normalizedStatus = getMaterialPlanStatusLabel(status)
  if (normalizedStatus === '全部占用') return 'danger'
  if (normalizedStatus === '部分占用') return 'warning'
  return 'success'
}
const getEvaluationStatusLabel = (status: string) => status === 'completed' ? '已回算' : '待回算'
const getEvaluationStatusType = (status: string) => status === 'completed' ? 'success' : 'warning'
const formatRate = (value: number) => `${(Number(value || 0) * 100).toFixed(1)}%`
const getWarningLevelLabel = (level?: string) => {
  if (level === 'high') return '高预警'
  if (level === 'medium') return '中预警'
  return '低预警'
}
const getWarningLevelTagType = (level?: string) => {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'success'
}
const EXPLANATION_ANOMALY_LABEL_MAP: Record<string, string> = {
  forecast_spike: '预测量突增',
  inventory_mismatch: '库存流水异常',
  recipe_missing: '未来菜谱缺失',
  activity_factor_high: '活动因子过高',
}
const getOrderActionTagType = (action: string) => {
  if (action === '立即下单') return 'danger'
  if (action === '计划补货') return 'warning'
  return 'info'
}
const getRiskFlagText = (flags?: string) => flags && flags.trim() ? flags : '—'
const parseAnomalyCodes = (value?: string) => (value || '')
  .split(',')
  .map((item) => item.trim())
  .filter(Boolean)
const getAnomalyLabel = (code: string) => EXPLANATION_ANOMALY_LABEL_MAP[code] || code
const openExplanation = (item: PurchaseDemandForecastItemRecord) => {
  explanationItem.value = item
  explanationVisible.value = true
}

const detailLinkedPlanRows = computed<PurchaseDemandForecastLinkedPlanRow[]>(() => {
  const rows: PurchaseDemandForecastLinkedPlanRow[] = []

  detailLinkedPlans.value.forEach((plan, index) => {
    const groupKey = plan.planNo.trim() || `linked-plan-${plan.id || index}`
    if (!plan.items.length) {
      rows.push({
        planId: plan.id,
        planNo: plan.planNo,
        planName: plan.planName,
        status: plan.status,
        planDate: plan.planDate,
        totalAmount: plan.totalAmount,
        createdBy: plan.createdBy,
        createdAt: plan.createdAt,
        materialName: '',
        materialSpec: '',
        unit: '',
        quantity: null,
        estimatedUnitPrice: null,
        estimatedAmount: null,
        remark: '',
        groupKey,
      })
      return
    }

    plan.items.forEach((item) => {
      rows.push({
        planId: plan.id,
        planNo: plan.planNo,
        planName: plan.planName,
        status: plan.status,
        planDate: plan.planDate,
        totalAmount: plan.totalAmount,
        createdBy: plan.createdBy,
        createdAt: plan.createdAt,
        materialName: item.materialName,
        materialSpec: item.materialSpec,
        unit: item.unit,
        quantity: item.quantity,
        estimatedUnitPrice: item.estimatedUnitPrice,
        estimatedAmount: item.estimatedAmount,
        remark: item.remark,
        groupKey,
      })
    })
  })

  return rows
})

const currentSegmentOptions = computed(() => {
  const options = new Set<string>()
  currentForecast.value?.items.forEach((item) => {
    if (item.materialSegment) {
      options.add(item.materialSegment)
    }
  })
  return Array.from(options)
})

const filteredCurrentItems = computed(() => {
  const items = currentForecast.value?.items || []
  if (!currentSegmentFilter.value) {
    return items
  }
  return items.filter((item) => item.materialSegment === currentSegmentFilter.value)
})

const linkedPlanRowClassNames = computed(() => {
  const classNames: string[] = []
  let previousGroupKey = ''
  let currentClassName = 'linked-plan-group-even'

  detailLinkedPlanRows.value.forEach((row, index) => {
    const groupKey = row.groupKey
    if (index === 0) {
      previousGroupKey = groupKey
    } else if (groupKey !== previousGroupKey) {
      currentClassName = currentClassName === 'linked-plan-group-even'
        ? 'linked-plan-group-odd'
        : 'linked-plan-group-even'
      previousGroupKey = groupKey
    }
    classNames.push(currentClassName)
  })

  return classNames
})

const getLinkedPlanRowClassName = ({ rowIndex }: { rowIndex: number }) =>
  linkedPlanRowClassNames.value[rowIndex] || ''

const fetchHistory = async (page = currentPage.value) => {
  historyLoading.value = true
  try {
    const res = await purchaseDemandForecastApi.getList({
      pageNum: page,
      pageSize: pageSize.value,
      orgId: runForm.orgId || undefined,
      keyword: historyForm.keyword.trim() || undefined,
      dimension: historyForm.dimension || undefined,
    })
    historyList.value = (res.data?.list || []).map((item) => normalizeRecord(item))
    total.value = Number(res.data?.total || 0)
    currentPage.value = Number(res.data?.pageNum || page)
  } finally {
    historyLoading.value = false
  }
}

const fetchDashboard = async () => {
  if (!runForm.orgId) {
    dashboard.value = null
    return
  }
  dashboardLoading.value = true
  try {
    const res = await purchaseDemandForecastApi.getDashboard(Number(runForm.orgId))
    dashboard.value = normalizeDashboard(res.data || {})
  } finally {
    dashboardLoading.value = false
  }
}

const runForecast = async () => {
  if (!runForm.orgId) {
    ElMessage.warning('请选择所属组织')
    return
  }

  forecastRunning.value = true
  try {
    const res = await purchaseDemandForecastApi.generate({
      orgId: Number(runForm.orgId),
      dimension: runForm.dimension,
    })
    currentForecast.value = normalizeRecord(res.data || {})
    selectedItems.value = []
    currentSegmentFilter.value = ''
    await fetchHistory(1)
    await fetchDashboard()
    ElMessage.success('采购需求预测已生成')
  } finally {
    forecastRunning.value = false
  }
}

const refreshAnalyticsData = async () => {
  if (!runForm.orgId) {
    ElMessage.warning('请选择所属组织')
    return
  }
  analyticsRefreshing.value = true
  try {
    const res = await purchaseDemandForecastApi.refreshAnalytics(Number(runForm.orgId))
    dashboard.value = normalizeDashboard(res.data || {})
    await fetchHistory(currentPage.value)
    if (detailRecord.value?.id) {
      const detailRes = await purchaseDemandForecastApi.getDetail(detailRecord.value.id)
      detailRecord.value = normalizeRecord(detailRes.data || {})
    }
    ElMessage.success('采购需求预测分析数据已刷新')
  } finally {
    analyticsRefreshing.value = false
  }
}

const resetHistory = async () => {
  historyForm.keyword = ''
  historyForm.dimension = ''
  await fetchHistory(1)
}

const openDetail = async (id: number) => {
  detailVisible.value = true
  detailLoading.value = true
  linkedPlansLoading.value = true
  detailRecord.value = null
  detailLinkedPlans.value = []
  try {
    const [detailRes, linkedPlansRes] = await Promise.all([
      purchaseDemandForecastApi.getDetail(id),
      purchaseDemandForecastApi.getLinkedPlans(id),
    ])
    detailRecord.value = normalizeRecord(detailRes.data || {})
    detailLinkedPlans.value = Array.isArray(linkedPlansRes.data)
      ? linkedPlansRes.data.map((item) => normalizeLinkedPlan(item))
      : []
  } finally {
    detailLoading.value = false
    linkedPlansLoading.value = false
  }
}

const handleSelectionChange = (selection: PurchaseDemandForecastItemRecord[]) => {
  selectedItems.value = selection
}

const handlePageChange = async (page: number) => {
  currentPage.value = page
  await fetchHistory(page)
}

const handleSizeChange = async (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  await fetchHistory(1)
}

const handleOrgChange = async (value: number | null) => {
  runForm.orgId = value
  await fetchHistory(1)
  await fetchDashboard()
}

const generatePurchasePlan = async () => {
  if (!selectedItems.value.length) {
    ElMessage.warning('请至少选择一条预测物料')
    return
  }

  const res = await purchaseDemandForecastApi.getPurchasePlanPrefill(selectedItems.value.map((item) => item.id))
  purchasePlanStore.setForecastPrefill(normalizePrefill(res.data || {}))
  await router.push('/purchase-plan')
  ElMessage.success('预测结果已带入新增采购计划')
}

onMounted(async () => {
  await ensureUserInfoReady()
  if (!runForm.orgId) {
    runForm.orgId = userStore.userInfo?.orgId || null
  }
  await fetchHistory(1)
  await fetchDashboard()
})

onActivated(async () => {
  if (!historyActivatedOnce.value) {
    historyActivatedOnce.value = true
    return
  }
  await ensureUserInfoReady()
  if (!runForm.orgId) {
    runForm.orgId = userStore.userInfo?.orgId || null
  }
  await fetchHistory(currentPage.value)
  await fetchDashboard()
})
</script>

<template>
  <div class="purchase-forecast-page">
    <div class="page-header">
      <div>
        <div class="page-title">采购需求预测</div>
        <div class="page-subtitle">综合近30天采购、库存与未来7天菜谱数据，生成可追溯的采购建议。</div>
      </div>
    </div>

    <div class="panel run-panel">
      <div class="panel-title">执行预测</div>
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <OrgTreeSelect
            v-model="runForm.orgId"
            placeholder="请选择所属组织"
            :active-only="true"
            @update:model-value="handleOrgChange"
          />
        </el-col>
        <el-col :span="8">
          <el-radio-group v-model="runForm.dimension">
            <el-radio-button v-for="option in dimensionOptions" :key="option.value" :label="option.value">
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-col>
        <el-col :span="8" class="run-actions">
          <el-button
            type="primary"
            :icon="DataAnalysis"
            :loading="forecastRunning"
            v-permission="PURCHASE_PLAN_PERMISSIONS.CREATE"
            @click="runForecast"
          >
            执行采购需求预测
          </el-button>
        </el-col>
      </el-row>
    </div>

    <div class="panel current-panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">本次预测结果</div>
          <div v-if="currentForecast" class="panel-subtitle">
            {{ currentForecast.forecastNo }} · {{ getDimensionLabel(currentForecast.dimension) }} · {{ currentForecast.generatedAt }}
          </div>
          <div v-else class="panel-subtitle">执行预测后展示当前结果，并支持批量生成采购计划。</div>
        </div>
        <el-button
          type="primary"
          plain
          :disabled="!canGeneratePlan"
          @click="generatePurchasePlan"
        >
          生成采购计划
          <template v-if="selectedItems.length">（{{ selectedItems.length }}）</template>
        </el-button>
      </div>

      <template v-if="currentForecast">
        <div class="summary-grid">
          <div class="summary-card">
            <span class="summary-label">建议物料数</span>
            <strong>{{ currentForecast.materialCount }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">立即下单物料</span>
            <strong>{{ currentForecast.reorderTriggeredCount || 0 }}</strong>
          </div>
          <div class="summary-card forecast-summary-card--hidden">
            <span class="summary-label">风险物料</span>
            <strong>{{ currentForecast.riskItemCount || 0 }}</strong>
          </div>
          <div class="summary-card forecast-summary-card--hidden">
            <span class="summary-label">人工复核物料</span>
            <strong>{{ currentForecast.manualReviewCount || 0 }}</strong>
          </div>
          <div class="summary-card forecast-summary-card--hidden">
            <span class="summary-label">触发预警物料</span>
            <strong>{{ currentForecast.warningItemCount || 0 }}</strong>
          </div>
          <div class="summary-card forecast-summary-card--hidden">
            <span class="summary-label">供应商建议覆盖</span>
            <strong>{{ currentForecast.supplierRecommendedCount || 0 }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">建议总金额</span>
            <strong>{{ formatAmount(currentForecast.totalSuggestedAmount) }}</strong>
          </div>
          <div class="summary-card forecast-summary-card--hidden">
            <span class="summary-label">综合优化成本</span>
            <strong>{{ formatAmount(currentForecast.totalOptimizationCost || 0) }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">输出周期</span>
            <strong>{{ currentForecast.horizonStartDate }} ~ {{ currentForecast.horizonEndDate }}</strong>
          </div>
          <div class="summary-card forecast-summary-card--hidden">
            <span class="summary-label">因子摘要</span>
            <strong>日历 {{ currentForecast.calendarFactor }} / 节假日 {{ currentForecast.holidayFactor }} / 活动 {{ currentForecast.activityFactor }}</strong>
          </div>
        </div>

        <div class="current-basis">{{ currentForecast.summaryBasis }}</div>
        <div
          v-if="showCurrentForecastExplanationSummary && currentForecast.explanationSummary"
          class="phase-two-note"
        >
          {{ currentForecast.explanationSummary }}
        </div>
        <div
          v-if="showCurrentForecastApprovalSummary && currentForecast.approvalSummary"
          class="approval-note"
        >
          {{ currentForecast.approvalSummary }}
        </div>
        <div class="segment-toolbar">
          <el-select v-model="currentSegmentFilter" placeholder="按物料分层筛选" clearable>
            <el-option v-for="segment in currentSegmentOptions" :key="segment" :label="segment" :value="segment" />
          </el-select>
        </div>

        <el-table
          :data="filteredCurrentItems"
          border
          stripe
          empty-text="当前预测单暂无物料建议"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="52" align="center" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
          <el-table-column
            v-if="showCurrentForecastSegmentColumn"
            prop="materialSegment"
            label="物料分层"
            width="130"
            align="center"
          />
          <el-table-column
            v-if="showCurrentForecastModelColumn"
            prop="modelType"
            label="模型"
            width="130"
            align="center"
            show-overflow-tooltip
          />
          <el-table-column prop="materialSpec" label="规格" min-width="120" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="90" align="center" />
          <el-table-column label="当前库存量" width="120" align="right">
            <template #default="{ row }">{{ formatQuantity(row.currentInventoryQty) }}</template>
          </el-table-column>
          <el-table-column label="库存位置" width="120" align="right">
            <template #default="{ row }">{{ formatQuantity(row.inventoryPositionQty || 0) }}</template>
          </el-table-column>
          <el-table-column v-if="showCurrentForecastReorderPointColumn" label="订货点 ROP" width="120" align="right">
            <template #default="{ row }">{{ formatQuantity(row.reorderPointQty || 0) }}</template>
          </el-table-column>
          <el-table-column label="目标库存" width="120" align="right">
            <template #default="{ row }">{{ formatQuantity(row.targetStockQty || 0) }}</template>
          </el-table-column>
          <el-table-column label="建议采购量" width="120" align="right">
            <template #default="{ row }">{{ formatQuantity(row.suggestedQty) }}</template>
          </el-table-column>
          <el-table-column label="预估单价" width="120" align="right">
            <template #default="{ row }">{{ formatAmount(row.estimatedUnitPrice) }}</template>
          </el-table-column>
          <el-table-column label="预估金额" width="120" align="right">
            <template #default="{ row }">{{ formatAmount(row.estimatedAmount) }}</template>
          </el-table-column>
          <el-table-column label="推荐供应商" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.recommendedSupplierName || '—' }}</template>
          </el-table-column>
          <el-table-column label="下单建议" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getOrderActionTagType(row.orderAction || '')">
                {{ row.orderAction || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="showCurrentForecastTotalCostColumn" label="综合成本" width="120" align="right">
            <template #default="{ row }">{{ formatAmount(row.totalCost || 0) }}</template>
          </el-table-column>
          <el-table-column label="风险标记" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ getRiskFlagText(row.phaseThreeRiskFlags) }}</template>
          </el-table-column>
          <el-table-column label="预警级别" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getWarningLevelTagType(row.warningLevel)">
                {{ getWarningLevelLabel(row.warningLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="160" align="center">
            <template #default="{ row }">{{ formatConfidenceRate(row.confidenceRate) }}</template>
          </el-table-column>
          <el-table-column label="优先级" width="110" align="center">
            <template #default="{ row }">
              <el-tag
                :type="getPriorityTagType(row.priority)"
                effect="dark"
                :class="['priority-tag', getPriorityTagClass(row.priority)]"
              >
                {{ normalizePriorityLabel(row.priority) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="explanationSummary" label="智能解释" min-width="260" show-overflow-tooltip />
          <el-table-column prop="forecastBasis" label="预测依据" min-width="320" show-overflow-tooltip />
          <el-table-column label="操作" width="100" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openExplanation(row)">解释详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <div v-else class="empty-block">暂无本次预测结果，请先执行采购需求预测。</div>
    </div>

    <div class="panel analytics-panel" v-loading="dashboardLoading">
      <div class="panel-header">
        <div>
          <div class="panel-title">三期优化看板</div>
          <div class="panel-subtitle">展示自动优化阶段的订货触发、供应商推荐、成本约束与回退结果。</div>
        </div>
        <el-button
          type="primary"
          plain
          :loading="analyticsRefreshing"
          @click="refreshAnalyticsData"
        >
          刷新闭环指标
        </el-button>
      </div>

      <template v-if="dashboard">
        <div class="summary-grid analytics-summary-grid">
          <div class="summary-card">
            <span class="summary-label">待回算预测单</span>
            <strong>{{ dashboard.pendingEvaluationCount }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">已回算预测单</span>
            <strong>{{ dashboard.evaluatedForecastCount }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">WAPE</span>
            <strong>{{ formatRate(dashboard.wape) }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">MAPE</span>
            <strong>{{ formatRate(dashboard.mape) }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">偏差率</span>
            <strong>{{ formatRate(dashboard.biasRate) }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">缺货率</span>
            <strong>{{ formatRate(dashboard.stockoutRate) }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">过采率</span>
            <strong>{{ formatRate(dashboard.oversupplyRate) }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">生效优化配置</span>
            <strong>{{ dashboard.optimizedConfigCount }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">触发订货点物料</span>
            <strong>{{ dashboard.reorderTriggeredCount || 0 }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">风险物料</span>
            <strong>{{ dashboard.riskItemCount || 0 }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">供应商建议覆盖</span>
            <strong>{{ dashboard.supplierRecommendedCount || 0 }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">人工复核物料</span>
            <strong>{{ dashboard.manualReviewCount || 0 }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">触发预警物料</span>
            <strong>{{ dashboard.warningItemCount || 0 }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">平均优化成本</span>
            <strong>{{ formatAmount(dashboard.avgOptimizationCost || 0) }}</strong>
          </div>
          <div class="summary-card">
            <span class="summary-label">自动回退次数</span>
            <strong>{{ dashboard.rollbackCount || 0 }}</strong>
          </div>
        </div>

        <div class="analytics-grid">
          <div class="analytics-block">
            <div class="detail-section-title">分层效果概览</div>
            <el-table :data="dashboard.segmentSummaries" size="small" border empty-text="暂无分层统计">
              <el-table-column prop="materialSegment" label="物料分层" min-width="130" />
              <el-table-column prop="modelType" label="模型" min-width="120" />
              <el-table-column prop="materialCount" label="样本数" width="90" align="center" />
              <el-table-column label="建议总量" width="110" align="right">
                <template #default="{ row }">{{ formatQuantity(row.totalSuggestedQty) }}</template>
              </el-table-column>
              <el-table-column label="实际总量" width="110" align="right">
                <template #default="{ row }">{{ formatQuantity(row.totalActualQty) }}</template>
              </el-table-column>
              <el-table-column label="综合成本" width="120" align="right">
                <template #default="{ row }">{{ formatAmount(row.totalOptimizationCost || 0) }}</template>
              </el-table-column>
              <el-table-column label="WAPE" width="110" align="center">
                <template #default="{ row }">{{ formatRate(row.wape) }}</template>
              </el-table-column>
              <el-table-column label="偏差率" width="110" align="center">
                <template #default="{ row }">{{ formatRate(row.biasRate) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="analytics-block">
            <div class="detail-section-title">自动优化记录</div>
            <el-table :data="dashboard.optimizationSummaries" size="small" border empty-text="暂无自动优化记录">
              <el-table-column prop="materialSegment" label="物料分层" min-width="130" />
              <el-table-column prop="modelType" label="模型" min-width="120" />
              <el-table-column prop="versionNo" label="版本" width="80" align="center" />
              <el-table-column label="评分" width="100" align="center">
                <template #default="{ row }">{{ formatRate(row.score) }}</template>
              </el-table-column>
              <el-table-column label="WAPE" width="100" align="center">
                <template #default="{ row }">{{ formatRate(row.wape) }}</template>
              </el-table-column>
              <el-table-column label="缺货率" width="100" align="center">
                <template #default="{ row }">{{ formatRate(row.stockoutRate) }}</template>
              </el-table-column>
              <el-table-column label="过采率" width="100" align="center">
                <template #default="{ row }">{{ formatRate(row.oversupplyRate) }}</template>
              </el-table-column>
              <el-table-column label="是否回退" width="90" align="center">
                <template #default="{ row }">{{ row.rollbackApplied ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="rollbackReason" label="回退原因" min-width="180" show-overflow-tooltip />
              <el-table-column prop="optimizedAt" label="优化时间" min-width="160" />
            </el-table>
          </div>

          <div class="analytics-block">
            <div class="detail-section-title">推荐供应商概览</div>
            <el-table :data="dashboard.supplierSummaries" size="small" border empty-text="暂无供应商推荐统计">
              <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
              <el-table-column prop="recommendCount" label="推荐次数" width="90" align="center" />
              <el-table-column label="平均评分" width="100" align="center">
                <template #default="{ row }">{{ formatRate(row.avgSupplierScore) }}</template>
              </el-table-column>
              <el-table-column label="平均有效提前期" width="130" align="right">
                <template #default="{ row }">{{ formatLeadTimeDays(row.avgEffectiveLeadTimeDays) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </template>

      <div v-else class="empty-block">暂无三期优化数据，请先选择组织并执行预测或刷新分析指标。</div>
    </div>

    <div class="panel history-panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">历史预测单</div>
          <div class="panel-subtitle">支持按预测单号/名称检索，并查看每一次预测详情。</div>
        </div>
      </div>

      <div class="history-toolbar">
        <el-input
          v-model="historyForm.keyword"
          placeholder="请输入预测单号/名称"
          clearable
          @keyup.enter="fetchHistory(1)"
        />
        <el-select v-model="historyForm.dimension" placeholder="预测维度" clearable>
          <el-option v-for="option in dimensionOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="fetchHistory(1)">搜索</el-button>
        <el-button :icon="RefreshRight" @click="resetHistory">重置</el-button>
      </div>

      <el-table v-loading="historyLoading" :data="historyList" border stripe empty-text="暂无历史预测记录">
        <el-table-column prop="forecastNo" label="预测单号" min-width="170" />
        <el-table-column v-if="showHistoryEvaluationStatusColumn" label="回算状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getEvaluationStatusType(row.evaluationStatus || 'pending')">
              {{ getEvaluationStatusLabel(row.evaluationStatus || 'pending') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物料关联计划状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="getMaterialPlanStatusType(row.materialPlanStatus)">
              {{ getMaterialPlanStatusLabel(row.materialPlanStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="所属组织" width="140" />
        <el-table-column label="预测维度" width="100" align="center">
          <template #default="{ row }">{{ getDimensionLabel(row.dimension) }}</template>
        </el-table-column>
        <el-table-column label="输出周期" min-width="220">
          <template #default="{ row }">{{ row.horizonStartDate }} ~ {{ row.horizonEndDate }}</template>
        </el-table-column>
        <el-table-column prop="materialCount" label="物料数" width="90" align="center" />
        <el-table-column label="建议总金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalSuggestedAmount) }}</template>
        </el-table-column>
        <el-table-column v-if="showHistoryWapeColumn" label="WAPE" width="110" align="center">
          <template #default="{ row }">{{ formatRate(row.wape || 0) }}</template>
        </el-table-column>
        <el-table-column prop="generatedBy" label="操作人" width="120" />
        <el-table-column prop="generatedAt" label="生成时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <span class="total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="采购需求预测详情" width="1100px" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detailRecord">
          <div class="summary-grid detail-summary-grid">
            <div class="summary-card">
              <span class="summary-label">预测单号</span>
              <strong>{{ detailRecord.forecastNo }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">预测维度</span>
              <strong>{{ getDimensionLabel(detailRecord.dimension) }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">所属组织</span>
              <strong>{{ detailRecord.orgName }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">生成时间</span>
              <strong>{{ detailRecord.generatedAt }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">建议总金额</span>
              <strong>{{ formatAmount(detailRecord.totalSuggestedAmount) }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">输出周期</span>
              <strong>{{ detailRecord.horizonStartDate }} ~ {{ detailRecord.horizonEndDate }}</strong>
            </div>
            <div v-if="showDetailEvaluationStatusSummary" class="summary-card">
              <span class="summary-label">回算状态</span>
              <strong>{{ getEvaluationStatusLabel(detailRecord.evaluationStatus || 'pending') }}</strong>
            </div>
            <div v-if="showDetailWapeBiasSummary" class="summary-card">
              <span class="summary-label">WAPE / 偏差率</span>
              <strong>{{ formatRate(detailRecord.wape || 0) }} / {{ formatRate(detailRecord.biasRate || 0) }}</strong>
            </div>
            <div v-if="showDetailStockoutOversupplySummary" class="summary-card">
              <span class="summary-label">缺货率 / 过采率</span>
              <strong>{{ formatRate(detailRecord.stockoutRate || 0) }} / {{ formatRate(detailRecord.oversupplyRate || 0) }}</strong>
            </div>
            <div v-if="showDetailOptimizationVersionSummary" class="summary-card">
              <span class="summary-label">优化版本</span>
              <strong>V{{ detailRecord.optimizationVersion || 0 }}</strong>
            </div>
            <div v-if="showDetailReorderRiskSummary" class="summary-card">
              <span class="summary-label">立即下单 / 风险物料</span>
              <strong>{{ detailRecord.reorderTriggeredCount || 0 }} / {{ detailRecord.riskItemCount || 0 }}</strong>
            </div>
            <div v-if="showDetailManualWarningSummary" class="summary-card">
              <span class="summary-label">人工复核 / 预警物料</span>
              <strong>{{ detailRecord.manualReviewCount || 0 }} / {{ detailRecord.warningItemCount || 0 }}</strong>
            </div>
            <div v-if="showDetailSupplierCostSummary" class="summary-card">
              <span class="summary-label">供应商覆盖 / 综合成本</span>
              <strong>{{ detailRecord.supplierRecommendedCount || 0 }} / {{ formatAmount(detailRecord.totalOptimizationCost || 0) }}</strong>
            </div>
          </div>
          <div class="current-basis">{{ detailRecord.summaryBasis }}</div>
          <div
            v-if="showDetailExplanationSummary && detailRecord.explanationSummary"
            class="phase-two-note"
          >
            {{ detailRecord.explanationSummary }}
          </div>
          <div
            v-if="showDetailApprovalSummary && detailRecord.approvalSummary"
            class="approval-note"
          >
            {{ detailRecord.approvalSummary }}
          </div>
          <el-table :data="detailRecord.items || []" border stripe empty-text="暂无预测明细">
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column
              v-if="showDetailSegmentColumn"
              prop="materialSegment"
              label="物料分层"
              width="130"
              align="center"
            />
            <el-table-column
              v-if="showDetailModelColumn"
              prop="modelType"
              label="模型"
              width="130"
              align="center"
              show-overflow-tooltip
            />
            <el-table-column prop="materialSpec" label="规格" min-width="120" />
            <el-table-column prop="unit" label="单位" width="80" align="center" />
            <el-table-column label="当前库存" width="110" align="right">
              <template #default="{ row }">{{ formatQuantity(row.currentInventoryQty) }}</template>
            </el-table-column>
            <el-table-column label="库存位置" width="110" align="right">
              <template #default="{ row }">{{ formatQuantity(row.inventoryPositionQty || 0) }}</template>
            </el-table-column>
            <el-table-column v-if="showDetailReorderPointColumn" label="订货点 ROP" width="120" align="right">
              <template #default="{ row }">{{ formatQuantity(row.reorderPointQty || 0) }}</template>
            </el-table-column>
            <el-table-column label="目标库存" width="120" align="right">
              <template #default="{ row }">{{ formatQuantity(row.targetStockQty || 0) }}</template>
            </el-table-column>
            <el-table-column label="建议采购量" width="120" align="right">
              <template #default="{ row }">{{ formatQuantity(row.suggestedQty) }}</template>
            </el-table-column>
            <el-table-column label="预估单价" width="120" align="right">
              <template #default="{ row }">{{ formatAmount(row.estimatedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="预估金额" width="120" align="right">
              <template #default="{ row }">{{ formatAmount(row.estimatedAmount) }}</template>
            </el-table-column>
            <el-table-column label="推荐供应商" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.recommendedSupplierName || '—' }}</template>
            </el-table-column>
            <el-table-column label="下单建议" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getOrderActionTagType(row.orderAction || '')">
                  {{ row.orderAction || '—' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="160" align="center">
              <template #default="{ row }">{{ formatConfidenceRate(row.confidenceRate) }}</template>
            </el-table-column>
            <el-table-column v-if="showDetailActualConsumptionColumn" label="实际消耗" width="110" align="right">
              <template #default="{ row }">{{ formatQuantity(row.actualConsumptionQty || 0) }}</template>
            </el-table-column>
            <el-table-column v-if="showDetailApeColumn" label="APE" width="100" align="center">
              <template #default="{ row }">{{ formatRate(row.ape || 0) }}</template>
            </el-table-column>
            <el-table-column v-if="showDetailTotalCostColumn" label="综合成本" width="120" align="right">
              <template #default="{ row }">{{ formatAmount(row.totalCost || 0) }}</template>
            </el-table-column>
            <el-table-column label="风险标记" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ getRiskFlagText(row.phaseThreeRiskFlags) }}</template>
            </el-table-column>
            <el-table-column label="预警级别" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getWarningLevelTagType(row.warningLevel)">
                  {{ getWarningLevelLabel(row.warningLevel) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="110" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="getPriorityTagType(row.priority)"
                  effect="dark"
                  :class="['priority-tag', getPriorityTagClass(row.priority)]"
                >
                  {{ normalizePriorityLabel(row.priority) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="explanationSummary" label="智能解释" min-width="260" show-overflow-tooltip />
            <el-table-column prop="forecastBasis" label="预测依据" min-width="360" show-overflow-tooltip />
            <el-table-column label="操作" width="100" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openExplanation(row)">解释详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="detail-section">
            <div class="detail-section-title">已关联采购计划记录</div>
            <div class="detail-section-subtitle">基于预测单号关联查询当前预测单生成的采购计划及物料明细。</div>
            <el-table
              v-loading="linkedPlansLoading"
              class="linked-plan-table"
              :data="detailLinkedPlanRows"
              border
              size="small"
              :row-class-name="getLinkedPlanRowClassName"
              empty-text="暂无关联采购计划记录"
            >
              <el-table-column prop="planNo" label="采购计划号" min-width="170" />
              <el-table-column prop="planName" label="计划名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="状态" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="getPurchasePlanStatusType(row.status)" size="small">
                    {{ getPurchasePlanStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="planDate" label="计划日期" width="120" align="center" />
              <el-table-column label="计划总金额" width="120" align="right">
                <template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="materialSpec" label="规格" min-width="120" show-overflow-tooltip />
              <el-table-column prop="unit" label="单位" width="80" align="center" />
              <el-table-column label="计划数量" width="110" align="right">
                <template #default="{ row }">{{ formatNullableQuantity(row.quantity) }}</template>
              </el-table-column>
              <el-table-column label="预估单价" width="110" align="right">
                <template #default="{ row }">{{ formatNullableAmount(row.estimatedUnitPrice) }}</template>
              </el-table-column>
              <el-table-column label="小计" width="120" align="right">
                <template #default="{ row }">{{ formatNullableAmount(row.estimatedAmount) }}</template>
              </el-table-column>
              <el-table-column prop="createdBy" label="创建人" width="110" />
              <el-table-column prop="createdAt" label="创建时间" width="170" />
              <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
            </el-table>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="explanationVisible" title="需求预测解释详情" width="880px" destroy-on-close>
      <template v-if="explanationItem">
        <div class="explanation-layout">
          <div class="summary-grid explanation-summary-grid">
            <div class="summary-card">
              <span class="summary-label">物料名称</span>
              <strong>{{ explanationItem.materialName }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">规格 / 单位</span>
              <strong>{{ explanationItem.materialSpec || '—' }} / {{ explanationItem.unit || '—' }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">解释模板</span>
              <strong>{{ explanationItem.explanationTitle || explanationItem.explanationTemplateCode || '—' }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">预警级别</span>
              <strong>
                <el-tag :type="getWarningLevelTagType(explanationItem.warningLevel)">
                  {{ getWarningLevelLabel(explanationItem.warningLevel) }}
                </el-tag>
              </strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">建议动作</span>
              <strong>
                <el-tag :type="getOrderActionTagType(explanationItem.orderAction || '')">
                  {{ explanationItem.orderAction || '—' }}
                </el-tag>
              </strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">建议采购量</span>
              <strong>{{ formatQuantity(explanationItem.suggestedQty) }}</strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">置信区间</span>
              <strong>
                {{ formatQuantity(explanationItem.confidenceLowerQty) }} ~
                {{ formatQuantity(explanationItem.confidenceUpperQty) }}
              </strong>
            </div>
            <div class="summary-card">
              <span class="summary-label">人工复核</span>
              <strong>
                <el-tag :type="explanationItem.manualReviewRequired ? 'danger' : 'success'">
                  {{ explanationItem.manualReviewRequired ? '需要复核' : '无需复核' }}
                </el-tag>
              </strong>
            </div>
          </div>

          <div v-if="explanationItem.explanationSummary" class="phase-two-note">
            {{ explanationItem.explanationSummary }}
          </div>
          <div v-if="explanationItem.warningMessage" class="warning-note">
            {{ explanationItem.warningMessage }}
          </div>
          <div v-if="explanationItem.approvalNote" class="approval-note">
            {{ explanationItem.approvalNote }}
          </div>

          <div class="detail-section">
            <div class="detail-section-title">解释明细</div>
            <div class="detail-section-subtitle">{{ explanationItem.explanationDetail || '暂无解释明细。' }}</div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">异常与风险</div>
            <div class="explanation-tag-group">
              <el-tag
                v-for="code in parseAnomalyCodes(explanationItem.anomalyCodes)"
                :key="code"
                type="danger"
                effect="light"
              >
                {{ getAnomalyLabel(code) }}
              </el-tag>
              <span v-if="!parseAnomalyCodes(explanationItem.anomalyCodes).length" class="empty-inline-text">未触发四期异常规则</span>
            </div>
            <div class="detail-section-subtitle">三期风险标记：{{ getRiskFlagText(explanationItem.phaseThreeRiskFlags) }}</div>
            <div class="detail-section-subtitle">历史异常提示：{{ getRiskFlagText(explanationItem.anomalyFlags) }}</div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">解释因子证据</div>
            <el-table
              :data="explanationItem.explanationFactors || []"
              border
              size="small"
              empty-text="暂无解释因子证据"
            >
              <el-table-column prop="label" label="因子项" min-width="160" />
              <el-table-column prop="value" label="取值" min-width="180" show-overflow-tooltip />
              <el-table-column prop="description" label="说明" min-width="320" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="explanationVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.purchase-forecast-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.panel {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 18px 20px;
}

.page-title,
.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.page-subtitle,
.panel-subtitle,
.current-basis,
.empty-block {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #6b7280;
}

.phase-two-note {
  margin-top: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  color: #0f4c81;
  font-size: 13px;
  line-height: 1.7;
}

.warning-note,
.approval-note {
  margin-top: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
}

.warning-note {
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #9a3412;
}

.approval-note {
  background: #f0fdf4;
  border: 1px solid #86efac;
  color: #166534;
}

.run-actions {
  display: flex;
  justify-content: flex-end;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 14px 16px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.forecast-summary-card--hidden {
  display: none;
}

.summary-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: #6b7280;
}

.summary-card strong {
  color: #111827;
  font-size: 15px;
  line-height: 1.5;
}

.segment-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.segment-toolbar .el-select {
  width: 220px;
}

.analytics-summary-grid {
  margin-bottom: 18px;
}

.analytics-panel {
  display: none;
}

.analytics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.analytics-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

:deep(.priority-tag) {
  min-width: 76px;
  justify-content: center;
  font-weight: 600;
  letter-spacing: 0.5px;
  border: none;
}

:deep(.priority-tag--urgent) {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
}

:deep(.priority-tag--high) {
  box-shadow: inset 0 0 0 1px rgba(120, 53, 15, 0.18);
}

:deep(.priority-tag--normal) {
  box-shadow: inset 0 0 0 1px rgba(20, 83, 45, 0.18);
}

.history-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) 160px auto auto;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.total {
  color: #6b7280;
  font-size: 13px;
}

.empty-block {
  padding: 28px 0;
  text-align: center;
}

.detail-section {
  margin-top: 18px;
}

.detail-section-title {
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.detail-section-subtitle {
  margin-bottom: 12px;
  font-size: 12px;
  color: #6b7280;
}

.explanation-layout {
  display: flex;
  flex-direction: column;
}

.explanation-summary-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 4px;
}

.explanation-tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.empty-inline-text {
  font-size: 12px;
  color: #6b7280;
}

.linked-plan-table {
  --linked-plan-group-even-bg: var(--el-bg-color);
  --linked-plan-group-even-hover-bg: var(--el-fill-color-light);
  --linked-plan-group-odd-bg: var(--el-color-success-light-9);
  --linked-plan-group-odd-hover-bg: var(--el-color-success-light-8);
}

.linked-plan-table :deep(.linked-plan-group-even td.el-table__cell) {
  background: var(--linked-plan-group-even-bg);
}

.linked-plan-table :deep(.linked-plan-group-even:hover > td.el-table__cell) {
  background: var(--linked-plan-group-even-hover-bg);
}

.linked-plan-table :deep(.linked-plan-group-odd td.el-table__cell) {
  background: var(--linked-plan-group-odd-bg);
}

.linked-plan-table :deep(.linked-plan-group-odd:hover > td.el-table__cell) {
  background: var(--linked-plan-group-odd-hover-bg);
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .analytics-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .page-header,
  .panel {
    padding: 16px;
  }

  .panel-header,
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .history-toolbar {
    grid-template-columns: 1fr;
  }

  .explanation-summary-grid {
    grid-template-columns: 1fr;
  }

  .run-actions {
    justify-content: flex-start;
  }
}
</style>
