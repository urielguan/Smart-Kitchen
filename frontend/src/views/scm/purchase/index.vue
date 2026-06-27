<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowDown,
  CircleCheck,
  CircleClose,
  Clock,
  Delete,
  Document,
  EditPen,
  Money,
  Plus,
  RefreshRight,
  Search,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, genFileId } from 'element-plus'
import type { UploadInstance, UploadProps, UploadRawFile } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import purchaseApi from '@/api/modules/purchase'
import { inboundApi } from '@/api/modules/inbound'
import { warehouseApi } from '@/api/modules/warehouse'
import { usePurchaseOrderStore } from '@/stores/modules/purchase-order'
import { useUserStore } from '@/stores/modules/user'
import { useOrgStore } from '@/stores/modules/org'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import { INBOUND_STATUS_OPTIONS } from '@/constants/inbound'
import type { Warehouse, Location } from '@/types/warehouse'
import type {
  PurchaseOrderAttachment,
  PurchaseOrderFormPayload,
  PurchaseOrderInspectionPayload,
  PurchaseOrderItem,
  PurchaseOrderLinkedInboundRecord,
  PurchaseOrderLogisticsPayload,
  PurchaseOrderLogisticsStatus,
  PurchaseOrderMaterialOption,
  PurchaseOrderMaintenanceSourceType,
  PurchaseOrderPlanItemOption,
  PurchaseOrderQuery,
  PurchaseOrderRecord,
  PurchaseOrderReverseAuditPayload,
  PurchaseOrderSelectablePlan,
  PurchaseOrderSceneIntegrationConfigOption,
  PurchaseOrderSceneIntegrationLog,
  PurchaseOrderSceneIntegrationLogs,
  PurchaseOrderSceneIntegrationMeta,
  PurchaseOrderSceneIntegrationSyncPayload,
  PurchaseOrderSceneIntegrationTriggerResult,
  PurchaseOrderStatistics,
  PurchaseOrderStatus,
  PurchaseOrderSupplierOption,
  PurchaseOrderTraceabilityPayload,
  PurchaseOrderVoidAuditPayload,
  PurchaseOrderVoidApplyPayload,
} from '@/types/purchase'
import { PURCHASE_PERMISSIONS } from '@/constants/permission'

interface PurchaseOrderFormItem extends PurchaseOrderItem {
  rowId: number
  sourceType: 'manual' | 'plan'
  lockedSource: boolean
  planQuantity: number
  orderedQuantity: number
  remainingQuantity: number
}

interface PurchaseOrderFormState extends Omit<PurchaseOrderRecord, 'id' | 'items'> {
  id: number | null
  items: PurchaseOrderFormItem[]
}

type PurchaseOrderSearchDateRange = string[] | null
type PurchaseOrderMoreAction =
  | 'reverseAudit'
  | 'void'
  | 'voidAudit'
  | 'logistics'
  | 'inspection'
  | 'traceability'
  | 'generateInbound'
  | 'delete'

interface PurchaseOrderMoreActionItem {
  label: string
  command: PurchaseOrderMoreAction
  disabled?: boolean
}

type MaintenanceAttachmentType = 'logistics' | 'inspection' | 'traceability'
type MaintenanceScene = 'logistics' | 'inspection' | 'traceability'

interface SceneIntegrationState {
  configId: number | null
  externalNo: string
  bindingId: number | null
  syncStatus: string
  lastSyncAt: string
  nextSyncAt: string
  lastErrorMessage: string
  allowDocumentSwitch: number
  forceThirdParty: number
  allowManualFallback: number
  autoCoverEnabled: number
  externalNoFieldRule: string
  configOptions: PurchaseOrderSceneIntegrationConfigOption[]
  recentSyncLogs: PurchaseOrderSceneIntegrationLog[]
  recentCallbackLogs: PurchaseOrderSceneIntegrationLog[]
}

const MAX_ATTACHMENT_FILE_SIZE = 10 * 1024 * 1024
const MAX_ATTACHMENT_FILE_SIZE_TEXT = '10MB'

const userStore = useUserStore()
const purchaseOrderStore = usePurchaseOrderStore()
const route = useRoute()
const router = useRouter()
const handledDashboardTraceBatchId = ref('')

const STATUS_MAP: Record<PurchaseOrderStatus, { label: string; type: '' | 'warning' | 'success' | 'danger' | 'info' }> = {
  pending_submit: { label: '草稿', type: 'info' },
  pending_approve: { label: '待审核', type: 'warning' },
  approved: { label: '已审核', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  pending_void_approve: { label: '待作废审核', type: 'warning' },
  voided: { label: '已作废', type: 'info' },
  delivering: { label: '运输中', type: 'warning' },
  pending_receipt: { label: '待入库', type: 'warning' },
  received: { label: '已收货', type: 'success' },
  inspected: { label: '已验收', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  closed: { label: '已关闭', type: 'info' },
  cancelled: { label: '已取消', type: 'info' },
}

const HIDDEN_STATUS_OPTION_VALUES: PurchaseOrderStatus[] = ['received', 'inspected', 'closed', 'cancelled']
const isVisibleStatusOption = (status: PurchaseOrderStatus | '') => !HIDDEN_STATUS_OPTION_VALUES.includes(status as PurchaseOrderStatus)

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'pending_submit' },
  { label: '待审核', value: 'pending_approve' },
  { label: '已审核', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
  { label: '待作废审核', value: 'pending_void_approve' },
  { label: '已作废', value: 'voided' },
  { label: '运输中', value: 'delivering' },
  { label: '待入库', value: 'pending_receipt' },
  { label: '已完成', value: 'completed' },
] as const

const MAINTENANCE_SOURCE_OPTIONS: Array<{ label: string; value: PurchaseOrderMaintenanceSourceType }> = [
  { label: '手工录入', value: 'manual' },
  { label: '第三方接口', value: 'third_party' },
]

const LOGISTICS_STATUS_OPTIONS: Array<{ label: string; value: PurchaseOrderLogisticsStatus }> = [
  { label: '待发货', value: 'pending' },
  { label: '已发货', value: 'shipped' },
  { label: '运输中', value: 'in_transit' },
  { label: '已到货', value: 'arrived' },
]
const LOGISTICS_STATUS_LOCK_MESSAGE = '物料已全部入库，不允许修改物料状态'
const LINKED_INBOUND_LOGISTICS_STATUS_LOCK_MESSAGE = '当前订单已关联入库单，不允许修改物流状态'
const LINKED_INBOUND_LOGISTICS_LOCK_STATUSES = ['draft', 'pending', 'approved', 'completed']
const INTEGRATION_SYNC_STATUS_MAP: Record<string, { label: string; type: '' | 'success' | 'warning' | 'danger' | 'info' }> = {
  pending: { label: '待同步', type: 'info' },
  running: { label: '同步中', type: 'warning' },
  success: { label: '成功', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  ignored: { label: '已忽略', type: 'info' },
  duplicate: { label: '重复回调', type: 'warning' },
}

const normalizeInboundPostStatus = (postStatus?: string) => {
  switch ((postStatus ?? '').trim()) {
    case 'posted':
      return 'posted'
    case 'post_failed':
      return 'post_failed'
    case 'unposted':
    case 'none':
    case '':
      return 'unposted'
    default:
      return postStatus ?? ''
  }
}

const getLinkedInboundEffectiveStatus = (status?: string, postStatus?: string) => {
  if (status === 'completed') return 'completed'
  if (status === 'approved' && normalizeInboundPostStatus(postStatus) === 'posted') return 'completed'
  return status || ''
}

const getLinkedInboundStatusType = (status?: string) =>
  INBOUND_STATUS_OPTIONS.find((item) => item.value === status)?.type ?? 'info'

const getLinkedInboundStatusLabel = (status?: string) =>
  INBOUND_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status ?? '—'

const INSPECTION_RESULT_OPTIONS = [
  { label: '待出结果', value: '待出结果' },
  { label: '合格', value: '合格' },
  { label: '不合格', value: '不合格' },
]
const isInspectionConclusionResult = (result?: string) => result === '合格' || result === '不合格'

const createRowId = (() => {
  let seed = 1000
  return () => {
    seed += 1
    return seed
  }
})()

const roundToCurrency = (value: number) => Number((Number.isFinite(value) ? value : 0).toFixed(2))
const roundToQuantity = (value: number) => Number((Number.isFinite(value) ? value : 0).toFixed(3))

const formatCurrency = (value: number) => `¥${roundToCurrency(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})}`

const formatDate = (date = new Date()) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const formatDateTimeValue = (date = new Date()) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${formatDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const disableFutureProductionDate = (date: Date) => {
  const candidate = new Date(date)
  candidate.setHours(0, 0, 0, 0)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return candidate.getTime() > today.getTime()
}

const isFutureProductionDate = (date?: string | null) => {
  if (!date) {
    return false
  }
  return date > formatDate()
}

const isFutureArrivedAt = (dateTime?: string | null) => {
  if (!dateTime) {
    return false
  }
  return dateTime.slice(0, 10) > formatDate()
}

const isFutureInspectionAt = (dateTime?: string | null) => {
  if (!dateTime) {
    return false
  }
  return dateTime > formatDateTimeValue()
}

const getDefaultOrgId = () => userStore.userInfo?.orgId ?? null
const getDefaultOrgName = () => userStore.userInfo?.orgName || ''
const currentUserName = computed(() => userStore.userInfo?.realName || userStore.userInfo?.userName || '管理员')
const normalizeSearchDateRange = (value: PurchaseOrderSearchDateRange | undefined): string[] => (
  Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && item.length > 0).slice(0, 2)
    : []
)

const createSearchForm = () => ({
  keyword: purchaseOrderStore.searchFormCache.keyword || '',
  status: isVisibleStatusOption((purchaseOrderStore.searchFormCache.status || '') as PurchaseOrderStatus | '')
    ? (purchaseOrderStore.searchFormCache.status || '') as PurchaseOrderStatus | ''
    : '',
  dateRange: purchaseOrderStore.searchFormCache.dateStart && purchaseOrderStore.searchFormCache.dateEnd
    ? [purchaseOrderStore.searchFormCache.dateStart, purchaseOrderStore.searchFormCache.dateEnd]
    : [] as PurchaseOrderSearchDateRange,
})

const searchForm = reactive(createSearchForm())

const currentPage = ref(purchaseOrderStore.pageNum || 1)
const pageSize = ref(purchaseOrderStore.pageSize || 10)
const total = ref(purchaseOrderStore.total || 0)

const tableLoading = ref(false)
const detailLoading = ref(false)
const formOptionLoading = ref(false)
const formSubmitting = ref(false)
const auditSubmitting = ref(false)
const reverseSubmitting = ref(false)
const voidSubmitting = ref(false)
const voidAuditSubmitting = ref(false)
const logisticsSubmitting = ref(false)
const inspectionSubmitting = ref(false)
const traceabilitySubmitting = ref(false)
const logisticsAttachmentUploading = ref(false)
const inspectionAttachmentUploading = ref(false)
const traceabilityAttachmentUploading = ref(false)

const purchaseOrders = ref<PurchaseOrderRecord[]>([])
const statistics = ref<PurchaseOrderStatistics>({
  total: Number(purchaseOrderStore.statistics.total || 0),
  pending: Number(purchaseOrderStore.statistics.pending || 0),
  approved: Number(purchaseOrderStore.statistics.approved || 0),
  totalAmount: roundToCurrency(Number(purchaseOrderStore.statistics.totalAmount || 0)),
})

const supplierOptions = ref<PurchaseOrderSupplierOption[]>([])
const materialOptions = ref<PurchaseOrderMaterialOption[]>([])
const planOptions = ref<PurchaseOrderSelectablePlan[]>([])
const planItemOptions = ref<PurchaseOrderPlanItemOption[]>([])
const attachmentUploadRef = ref<UploadInstance>()
const attachmentFile = ref<File | null>(null)
const attachmentMarkedForDeletion = ref(false)
const logisticsUploadRef = ref<UploadInstance>()
const inspectionUploadRef = ref<UploadInstance>()
const traceabilityUploadRef = ref<UploadInstance>()
const logisticsPersistedAttachmentUrls = ref<string[]>([])
const inspectionPersistedAttachmentUrls = ref<string[]>([])
const traceabilityPersistedAttachmentUrls = ref<string[]>([])

const formVisible = ref(false)
const detailVisible = ref(false)
const auditVisible = ref(false)
const reverseVisible = ref(false)
const voidVisible = ref(false)
const voidAuditVisible = ref(false)
const logisticsVisible = ref(false)
const inspectionVisible = ref(false)
const traceabilityVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const purchaseOrderActivatedOnce = ref(false)

const detailOrder = ref<PurchaseOrderRecord | null>(null)
const linkedInboundRecords = ref<PurchaseOrderLinkedInboundRecord[]>([])
const reverseLinkedInboundCount = ref(0)
const linkedInboundRowClassNames = computed(() => {
  const classNames: string[] = []
  let previousGroupKey = ''
  let currentClassName = 'linked-inbound-group-even'

  linkedInboundRecords.value.forEach((record, index) => {
    const groupKey = record.inboundNo.trim() || `linked-inbound-${record.inboundOrderId || index}`
    if (index === 0) {
      previousGroupKey = groupKey
    } else if (groupKey !== previousGroupKey) {
      currentClassName = currentClassName === 'linked-inbound-group-even'
        ? 'linked-inbound-group-odd'
        : 'linked-inbound-group-even'
      previousGroupKey = groupKey
    }
    classNames.push(currentClassName)
  })

  return classNames
})
const auditTarget = ref<PurchaseOrderRecord | null>(null)
const reverseTarget = ref<PurchaseOrderRecord | null>(null)
const voidTarget = ref<PurchaseOrderRecord | null>(null)
const voidAuditTarget = ref<PurchaseOrderRecord | null>(null)
const logisticsTarget = ref<PurchaseOrderRecord | null>(null)
const inspectionTarget = ref<PurchaseOrderRecord | null>(null)
const traceabilityTarget = ref<PurchaseOrderRecord | null>(null)
const logisticsLinkedInboundRecords = ref<PurchaseOrderLinkedInboundRecord[]>([])
const logisticsStatusLockReason = computed(() => {
  const target = logisticsTarget.value
  if (!target) {
    return ''
  }
  if (logisticsLinkedInboundRecords.value.some((record) =>
    LINKED_INBOUND_LOGISTICS_LOCK_STATUSES.includes(
      getLinkedInboundEffectiveStatus(record.status, record.postStatus)
    )
  )) {
    return LINKED_INBOUND_LOGISTICS_STATUS_LOCK_MESSAGE
  }
  if (target.logisticsStatus !== 'arrived' || !target.items.length) {
    return ''
  }
  return target.items.every((item) => Number(item.remainingInboundQty ?? 0) <= 0)
    ? LOGISTICS_STATUS_LOCK_MESSAGE
    : ''
})
const logisticsStatusLocked = computed(() => Boolean(logisticsStatusLockReason.value))

const auditForm = reactive({
  remark: '',
})

const reverseForm = reactive<PurchaseOrderReverseAuditPayload>({
  reason: '',
})

const voidForm = reactive<PurchaseOrderVoidApplyPayload>({
  reason: '',
})

const voidAuditForm = reactive<PurchaseOrderVoidAuditPayload>({
  approved: true,
  remark: '',
})

const logisticsForm = reactive<PurchaseOrderLogisticsPayload>({
  company: '',
  trackingNo: '',
  logisticsStatus: 'in_transit',
  shippedAt: '',
  arrivedAt: '',
  remark: '',
  sourceType: 'manual',
  syncPayload: '',
})
const logisticsRequiredState = computed(() => {
  const logisticsStatus = logisticsForm.logisticsStatus
  const shipmentInfoRequired = logisticsStatus === 'shipped'
    || logisticsStatus === 'in_transit'
    || logisticsStatus === 'arrived'
  return {
    company: shipmentInfoRequired,
    trackingNo: shipmentInfoRequired,
    shippedAt: shipmentInfoRequired,
    arrivedAt: logisticsStatus === 'arrived',
    attachments: logisticsStatus === 'arrived',
  }
})

const inspectionForm = reactive<PurchaseOrderInspectionPayload>({
  reportNo: '',
  result: '',
  agency: '',
  inspectedAt: '',
  remark: '',
  sourceType: 'manual',
  syncPayload: '',
})
const inspectionRequiredState = computed(() => {
  const conclusionRequired = isInspectionConclusionResult(inspectionForm.result)
  return {
    reportNo: conclusionRequired,
    agency: conclusionRequired,
    inspectedAt: conclusionRequired,
    attachments: conclusionRequired,
  }
})

const traceabilityForm = reactive<PurchaseOrderTraceabilityPayload>({
  traceBatchId: '',
  origin: '',
  remark: '',
  sourceType: 'manual',
  syncPayload: '',
})
const traceabilityRequiredState = {
  traceBatchId: true,
  origin: true,
  attachments: true,
} as const

const createSceneIntegrationState = (): SceneIntegrationState => ({
  configId: null,
  externalNo: '',
  bindingId: null,
  syncStatus: '',
  lastSyncAt: '',
  nextSyncAt: '',
  lastErrorMessage: '',
  allowDocumentSwitch: 1,
  forceThirdParty: 0,
  allowManualFallback: 0,
  autoCoverEnabled: 0,
  externalNoFieldRule: '',
  configOptions: [],
  recentSyncLogs: [],
  recentCallbackLogs: [],
})

const logisticsIntegration = reactive<SceneIntegrationState>(createSceneIntegrationState())
const inspectionIntegration = reactive<SceneIntegrationState>(createSceneIntegrationState())
const traceabilityIntegration = reactive<SceneIntegrationState>(createSceneIntegrationState())
const sceneLogsVisible = ref(false)
const sceneLogsTitle = ref('')
const sceneLogsLoading = ref(false)
const sceneLogsData = reactive<PurchaseOrderSceneIntegrationLogs>({
  binding: null,
  syncLogs: [],
  callbackLogs: [],
})
const logisticsSyncing = ref(false)
const inspectionSyncing = ref(false)
const traceabilitySyncing = ref(false)

// ---- 关联生成入库单 ----
const generateInboundVisible = ref(false)
const generateInboundSubmitting = ref(false)
const generateInboundTarget = ref<PurchaseOrderRecord | null>(null)
const orgStore = useOrgStore()
const generateInboundWarehouses = ref<Warehouse[]>([])

const findOrgNameById = (nodes: any[], orgId: number | null): string | null => {
  if (orgId == null) return null
  for (const node of nodes) {
    if (node.id === orgId) return node.orgName
    if (node.children?.length) {
      const name = findOrgNameById(node.children, orgId)
      if (name) return name
    }
  }
  return null
}

interface GenerateInboundItemRow {
  materialId: number
  materialName: string
  spec: string
  unit: string
  orderQty: number
  inboundQty: number
  remainingInboundQty: number
  // 用户可编辑字段（与入库管理新增一致）
  warehouseId: number | null
  locationId: number | null
  quantity: number | null
  unitCost: number | null
  batchNo: string
  productionDate: string
  // UI 专用
  _locations: Location[]
  selected: boolean
}

const generateInboundForm = reactive({
  orgId: null as number | null,
  orgName: '',
  remark: '',
  items: [] as GenerateInboundItemRow[],
})

const buildGenerateInboundQuantitySummary = () => {
  const summary = new Map<number, number>()
  for (const item of generateInboundForm.items) {
    if (!item.selected || item.materialId == null || item.quantity == null || item.quantity <= 0) {
      continue
    }
    summary.set(item.materialId, (summary.get(item.materialId) || 0) + item.quantity)
  }
  return summary
}

const getGenerateInboundQuantityExceededMessage = (row: GenerateInboundItemRow) => {
  if (!row.selected || row.materialId == null) {
    return ''
  }
  const totalQty = buildGenerateInboundQuantitySummary().get(row.materialId) || 0
  if (totalQty <= row.remainingInboundQty) {
    return ''
  }
  return `同一物料累计入库数量 ${roundToQuantity(totalQty)} 超出当前可入库数量 ${row.remainingInboundQty}，请调整`
}

// ---- 关联生成入库单 - 从来源订单选择物料弹窗 ----
const generateInboundSelectVisible = ref(false)
const generateInboundSelectKeyword = ref('')
const generateInboundSelectLoading = ref(false)
const generateInboundSourceItems = ref<any[]>([])
const generateInboundSelectedIds = ref<number[]>([])

const generateInboundFilteredSource = computed(() => {
  if (!generateInboundSelectKeyword.value?.trim()) return generateInboundSourceItems.value
  const kw = generateInboundSelectKeyword.value.trim().toLowerCase()
  return generateInboundSourceItems.value.filter((item: any) =>
    (item.materialName || '').toLowerCase().includes(kw) ||
    (item.spec || '').toLowerCase().includes(kw)
  )
})

/** 打开从来源订单选择物料弹窗 */
const openGenerateInboundSelectMaterial = async () => {
  if (!generateInboundTarget.value) return
  generateInboundSelectVisible.value = true
  generateInboundSelectKeyword.value = ''
  generateInboundSelectedIds.value = []
  generateInboundSelectLoading.value = true
  try {
    const res = await inboundApi.getSourceOrderItems(generateInboundTarget.value.id)
    if (res.code === 'SUCCESS' && res.data) {
      generateInboundSourceItems.value = res.data
    } else {
      generateInboundSourceItems.value = []
    }
  } catch (e) {
    console.error('加载来源订单物料失败', e)
    generateInboundSourceItems.value = []
  } finally {
    generateInboundSelectLoading.value = false
  }
}

/** 确认选择物料：以追加方式添加到明细 */
const confirmGenerateInboundSelectMaterial = () => {
  if (generateInboundSelectedIds.value.length === 0) {
    ElMessage.warning('请至少选择一条物料')
    return
  }
  const selectedItems = generateInboundSourceItems.value.filter((item: any) =>
    generateInboundSelectedIds.value.includes(item.materialId)
  )
  for (const item of selectedItems) {
    generateInboundForm.items.push({
      materialId: item.materialId,
      materialName: item.materialName || '',
      spec: item.spec || '',
      unit: item.unit || '',
      orderQty: item.orderQty ?? 0,
      inboundQty: item.inboundQty ?? 0,
      remainingInboundQty: item.remainingInboundQty ?? 0,
      warehouseId: null,
      locationId: null,
      quantity: item.remainingInboundQty ?? null,
      unitCost: item.unitPrice ?? null,
      batchNo: '',
      productionDate: '',
      _locations: [],
      selected: true,
    })
  }
  generateInboundSelectVisible.value = false
  ElMessage.success(`已追加 ${selectedItems.length} 条物料`)
}

/** 删除关联生成入库单明细行 */
const removeGenerateInboundItem = (idx: number) => {
  generateInboundForm.items.splice(idx, 1)
}

const resetGenerateInboundWarehouseSelections = () => {
  generateInboundForm.items.forEach((item) => {
    item.warehouseId = null
    item.locationId = null
    item._locations = []
  })
}

const loadGenerateInboundWarehouses = async (orgId: number | null) => {
  if (!orgId) {
    generateInboundWarehouses.value = []
    resetGenerateInboundWarehouseSelections()
    return
  }
  try {
    const res = await warehouseApi.getList({
      pageNum: 1,
      pageSize: 999,
      status: 'active',
      orgId,
    })
    generateInboundWarehouses.value = res.code === 'SUCCESS' && res.data ? res.data.list : []
  } catch {
    generateInboundWarehouses.value = []
  }
  resetGenerateInboundWarehouseSelections()
}

const generateInboundLoadLocations = async (warehouseId: number | null): Promise<Location[]> => {
  if (!warehouseId) return []
  try {
    const res = await warehouseApi.getLocations({ pageNum: 1, pageSize: 999, warehouseId })
    return res.code === 'SUCCESS' && res.data ? res.data.list : []
  } catch { return [] }
}

const onGenerateInboundWarehouseChange = async (row: GenerateInboundItemRow) => {
  row.locationId = null
  row._locations = await generateInboundLoadLocations(row.warehouseId)
}

// 入库组织变化时同步 orgName
watch(() => generateInboundForm.orgId, async (orgId) => {
  generateInboundForm.orgName = findOrgNameById(orgStore.treeData, orgId) ?? ''
  if (!generateInboundVisible.value) {
    return
  }
  await loadGenerateInboundWarehouses(orgId)
})

watch(
  searchForm,
  (value) => {
    const dateRange = normalizeSearchDateRange(value.dateRange)
    purchaseOrderStore.updateSearchFormCache({
      keyword: value.keyword,
      status: value.status || '',
      dateStart: dateRange[0] || '',
      dateEnd: dateRange[1] || '',
    })
  },
  { deep: true }
)

const getStatusLabel = (status: PurchaseOrderStatus) => STATUS_MAP[status]?.label || status
const getStatusType = (status: PurchaseOrderStatus) => STATUS_MAP[status]?.type || 'info'
const getReverseAuditCurrentStatusLabel = (row?: Partial<PurchaseOrderRecord> | null) => {
  if (row?.status === 'approved') {
    return !row.logisticsStatus || row.logisticsStatus === 'pending' ? '待发货' : '已审核'
  }
  return row?.status ? getStatusLabel(row.status as PurchaseOrderStatus) : '—'
}
const isInspectionFilled = (row?: Partial<PurchaseOrderRecord> | null) => Boolean(row?.inspectionFilled)
const isTraceabilityFilled = (row?: Partial<PurchaseOrderRecord> | null) => Boolean(row?.traceabilityFilled)
const isInspectionResultConfirmed = (row?: Partial<PurchaseOrderRecord> | null) =>
  isInspectionConclusionResult(row?.inspectionResult)
const countDistinctInboundOrders = (records: PurchaseOrderLinkedInboundRecord[]) =>
  new Set(records.map((item) => String(item.inboundOrderId || item.inboundNo || ''))).size

const isOrderCreator = (row: PurchaseOrderRecord) => {
  const currentUserId = userStore.userInfo?.id
  return currentUserId !== undefined && currentUserId !== null && row.createdById === currentUserId
}
const hasDeleteAuthority = (row: PurchaseOrderRecord) =>
  userStore.isAdmin() || userStore.hasPermission(PURCHASE_PERMISSIONS.DELETE) || isOrderCreator(row)
const canEdit = (row: PurchaseOrderRecord) => !row.deleted && ['pending_submit', 'pending_approve', 'rejected'].includes(row.status)
const canDelete = (row: PurchaseOrderRecord) => !row.deleted && hasDeleteAuthority(row) && ['pending_submit', 'pending_approve'].includes(row.status)
const canAudit = (row: PurchaseOrderRecord) => !row.deleted && row.status === 'pending_approve'
const canReverseAudit = (row: PurchaseOrderRecord) =>
  !row.deleted && (row.status === 'approved' || row.status === 'pending_receipt')
const canVoid = (row: PurchaseOrderRecord) => !row.deleted && (row.status === 'pending_approve' || row.status === 'approved')
const canVoidAudit = (row: PurchaseOrderRecord) => !row.deleted && row.status === 'pending_void_approve'
const canMaintainLogistics = (row: PurchaseOrderRecord) => !row.deleted && (row.status === 'approved' || row.status === 'delivering' || row.status === 'pending_receipt')
const canMaintainInspection = (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    return false
  }
  if (row.status === 'completed') {
    return !isInspectionResultConfirmed(row)
  }
  return row.status === 'approved' || row.status === 'delivering' || row.status === 'pending_receipt'
}
const canMaintainTraceability = (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    return false
  }
  if (row.status === 'completed') {
    return !isTraceabilityFilled(row)
  }
  return row.status === 'approved' || row.status === 'delivering' || row.status === 'pending_receipt'
}
const canGenerateInbound = (row: PurchaseOrderRecord) => !row.deleted && row.status === 'pending_receipt'
const hasOrderPermission = (permission: string) => userStore.hasPermission(permission)
const getMoreActionItems = (row: PurchaseOrderRecord): PurchaseOrderMoreActionItem[] => {
  const items: PurchaseOrderMoreActionItem[] = []

  if (hasOrderPermission(PURCHASE_PERMISSIONS.APPROVE) && canReverseAudit(row)) {
    items.push({ label: '反审核', command: 'reverseAudit' })
  }

  if (hasOrderPermission(PURCHASE_PERMISSIONS.VOID) && canVoid(row)) {
    items.push({ label: '作废', command: 'void' })
  }

  if (hasOrderPermission(PURCHASE_PERMISSIONS.VOID_AUDIT) && canVoidAudit(row)) {
    items.push({ label: '作废审核', command: 'voidAudit' })
  }

  if (hasOrderPermission(PURCHASE_PERMISSIONS.LOGISTICS) && canMaintainLogistics(row)) {
    items.push({ label: '维护物流', command: 'logistics' })
  }

  if (hasOrderPermission(PURCHASE_PERMISSIONS.INSPECTION) && canMaintainInspection(row)) {
    items.push({ label: '检测报告', command: 'inspection' })
  }

  if (hasOrderPermission(PURCHASE_PERMISSIONS.TRACEABILITY) && canMaintainTraceability(row)) {
    items.push({ label: '溯源信息', command: 'traceability' })
  }

  if (canGenerateInbound(row)) {
    items.push({ label: '关联生成入库单', command: 'generateInbound' })
  }

  if (canDelete(row)) {
    items.push({ label: '删除', command: 'delete' })
  }

  return items
}
const hasMoreActionItems = (row: PurchaseOrderRecord) => getMoreActionItems(row).length > 0
const showVoidDetail = (row?: PurchaseOrderRecord | null) => {
  if (!row) return false
  return row.status === 'pending_void_approve'
    || row.status === 'voided'
    || Boolean(row.voidReason || row.voidRequestedAt || row.voidAuditAt)
}

const validateAttachmentFileSize = (file: File | null | undefined, onInvalid?: () => void) => {
  if (!file) {
    return false
  }
  if (file.size > MAX_ATTACHMENT_FILE_SIZE) {
    onInvalid?.()
    ElMessage.warning(`文件大小不能超过${MAX_ATTACHMENT_FILE_SIZE_TEXT}，请重新选择`)
    return false
  }
  return true
}

const handleUploadChange = (
  target: typeof attachmentFile,
  uploadRef: typeof attachmentUploadRef
): UploadProps['onChange'] => (uploadFile) => {
  const rawFile = uploadFile.raw ?? null
  if (!validateAttachmentFileSize(rawFile, () => {
    target.value = null
    uploadRef.value?.clearFiles()
  })) {
    return
  }
  target.value = rawFile
}

const handleUploadExceed = (
  target: typeof attachmentFile,
  uploadRef: typeof attachmentUploadRef
): UploadProps['onExceed'] => (files) => {
  const rawFile = files[0] as UploadRawFile
  uploadRef.value?.clearFiles()
  if (!validateAttachmentFileSize(rawFile, () => {
    target.value = null
  })) {
    return
  }
  rawFile.uid = genFileId()
  uploadRef.value?.handleStart(rawFile)
  target.value = rawFile
}

const handleAttachmentChange = handleUploadChange(attachmentFile, attachmentUploadRef)
const handleAttachmentExceed = handleUploadExceed(attachmentFile, attachmentUploadRef)

const clearSelectedAttachment = (
  target: typeof attachmentFile = attachmentFile,
  uploadRef: typeof attachmentUploadRef = attachmentUploadRef
) => {
  target.value = null
  uploadRef.value?.clearFiles()
}

const clearPendingAttachment = () => {
  clearSelectedAttachment()
}

const removeCurrentAttachment = () => {
  attachmentMarkedForDeletion.value = true
  formState.value.attachmentName = ''
  formState.value.attachmentUrl = ''
  clearSelectedAttachment()
}

const previewPendingAttachment = (file?: File | null) => {
  if (!file) {
    ElMessage.warning('暂无可查看的附件')
    return
  }
  const previewUrl = URL.createObjectURL(file)
  window.open(previewUrl, '_blank', 'noopener,noreferrer')
  window.setTimeout(() => {
    URL.revokeObjectURL(previewUrl)
  }, 5 * 60 * 1000)
}

const previewAttachment = (url?: string) => {
  if (!url) {
    ElMessage.warning('暂无可查看的附件')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

const formatUploadFileSize = (fileSize: number) => {
  if (fileSize >= 1024 * 1024) {
    return `${(fileSize / 1024 / 1024).toFixed(2)} MB`
  }
  if (fileSize >= 1024) {
    return `${(fileSize / 1024).toFixed(2)} KB`
  }
  return `${fileSize} B`
}

const normalizeAttachment = (attachment: Partial<PurchaseOrderAttachment> | null | undefined): PurchaseOrderAttachment => ({
  id: attachment?.id === undefined || attachment.id === null ? undefined : Number(attachment.id),
  name: attachment?.name || '',
  size: attachment?.size || '',
  url: attachment?.url || '',
  sortOrder: attachment?.sortOrder === undefined || attachment.sortOrder === null ? undefined : Number(attachment.sortOrder),
})

const normalizeAttachmentList = (
  attachments: unknown,
  fallbackName: string,
  fallbackUrl: string,
): PurchaseOrderAttachment[] => {
  if (Array.isArray(attachments) && attachments.length) {
    return attachments
      .map((item) => normalizeAttachment(item as Partial<PurchaseOrderAttachment>))
      .filter((item) => item.url)
  }
  if (!fallbackUrl) {
    return []
  }
  return [{
    name: fallbackName || '附件',
    size: '',
    url: fallbackUrl,
    sortOrder: 1,
  }]
}

const getAttachmentUrls = (attachments: PurchaseOrderAttachment[]) => {
  return attachments
    .map((attachment) => attachment.url)
    .filter((url): url is string => Boolean(url))
}

const buildMaintenanceAttachmentPayload = (attachments: PurchaseOrderAttachment[]) => {
  return attachments
    .filter((attachment) => attachment.url)
    .map((attachment, index) => ({
      id: attachment.id,
      name: attachment.name,
      size: attachment.size || undefined,
      url: attachment.url,
      sortOrder: index + 1,
    }))
}

const getMaintenanceAttachmentList = (record: PurchaseOrderRecord | null, type: MaintenanceAttachmentType) => {
  if (!record) {
    return [] as PurchaseOrderAttachment[]
  }
  if (type === 'logistics') {
    return record.logisticsAttachments
  }
  if (type === 'inspection') {
    return record.inspectionAttachments
  }
  return record.traceabilityAttachments
}

const getMaintenancePersistedAttachmentUrls = (type: MaintenanceAttachmentType) => {
  if (type === 'logistics') {
    return logisticsPersistedAttachmentUrls.value
  }
  if (type === 'inspection') {
    return inspectionPersistedAttachmentUrls.value
  }
  return traceabilityPersistedAttachmentUrls.value
}

const setMaintenanceAttachmentUploading = (type: MaintenanceAttachmentType, loading: boolean) => {
  if (type === 'logistics') {
    logisticsAttachmentUploading.value = loading
    return
  }
  if (type === 'inspection') {
    inspectionAttachmentUploading.value = loading
    return
  }
  traceabilityAttachmentUploading.value = loading
}

const isPersistedMaintenanceAttachment = (type: MaintenanceAttachmentType, attachment: PurchaseOrderAttachment) => {
  return getMaintenancePersistedAttachmentUrls(type).includes(attachment.url)
}

const clearMaintenanceUploadFiles = (type: MaintenanceAttachmentType) => {
  window.setTimeout(() => {
    if (type === 'logistics') {
      logisticsUploadRef.value?.clearFiles()
      return
    }
    if (type === 'inspection') {
      inspectionUploadRef.value?.clearFiles()
      return
    }
    traceabilityUploadRef.value?.clearFiles()
  }, 0)
}

const uploadMaintenanceAttachments = async (
  type: MaintenanceAttachmentType,
  target: PurchaseOrderRecord | null,
  files: File[],
) => {
  if (!target) {
    return
  }
  const attachmentList = getMaintenanceAttachmentList(target, type)

  setMaintenanceAttachmentUploading(type, true)
  try {
    for (const file of files) {
      if (!validateAttachmentFileSize(file)) {
        continue
      }
      const alreadyExists = attachmentList.some((attachment) => attachment.name === file.name)
      if (alreadyExists) {
        ElMessage.warning(`文件 "${file.name}" 已添加`)
        continue
      }
      const res = await purchaseApi.uploadAttachment(file)
      if (res.code === 'SUCCESS' && res.data) {
        const uploadedAttachment = normalizeAttachment({
          ...res.data,
          size: res.data.size || formatUploadFileSize(file.size),
        })
        attachmentList.push(uploadedAttachment)
      }
    }
  } finally {
    setMaintenanceAttachmentUploading(type, false)
    clearMaintenanceUploadFiles(type)
  }
}

const handleMaintenanceAttachmentChange = (type: MaintenanceAttachmentType): UploadProps['onChange'] => async (uploadFile) => {
  const rawFile = uploadFile.raw
  const target = type === 'logistics'
    ? logisticsTarget.value
    : type === 'inspection'
      ? inspectionTarget.value
      : traceabilityTarget.value
  if (!rawFile || !target) {
    clearMaintenanceUploadFiles(type)
    return
  }
  await uploadMaintenanceAttachments(type, target, [rawFile])
}

const handleLogisticsAttachmentChange = handleMaintenanceAttachmentChange('logistics')
const handleInspectionAttachmentChange = handleMaintenanceAttachmentChange('inspection')
const handleTraceabilityAttachmentChange = handleMaintenanceAttachmentChange('traceability')

const cleanupTemporaryMaintenanceAttachments = async (
  attachments: PurchaseOrderAttachment[],
  persistedUrls: string[],
) => {
  const persistedUrlSet = new Set(persistedUrls)
  const temporaryAttachments = attachments.filter((attachment) => attachment.url && !persistedUrlSet.has(attachment.url))
  for (const attachment of temporaryAttachments) {
    try {
      await purchaseApi.deleteAttachment(attachment.url, attachment.name)
    } catch {
      // 请求拦截器已统一提示
    }
  }
}

const removeMaintenanceAttachment = async (type: MaintenanceAttachmentType, index: number) => {
  const target = type === 'logistics'
    ? logisticsTarget.value
    : type === 'inspection'
      ? inspectionTarget.value
      : traceabilityTarget.value
  const attachmentList = getMaintenanceAttachmentList(target, type)
  const attachment = attachmentList[index]
  if (!attachment) {
    return
  }

  const persisted = isPersistedMaintenanceAttachment(type, attachment)
  if (!persisted) {
    await purchaseApi.deleteAttachment(attachment.url, attachment.name)
  }
  attachmentList.splice(index, 1)
  ElMessage.success(persisted ? '附件已移除，保存后生效' : '附件已移除')
}

const downloadStoredAttachment = async (attachment?: Pick<PurchaseOrderAttachment, 'url' | 'name'> | null) => {
  if (!attachment?.url) {
    ElMessage.warning('暂无可下载的附件')
    return
  }
  await purchaseApi.downloadAttachmentByUrl(attachment.url, attachment.name)
}

const downloadAttachment = async (id?: number | null) => {
  if (!id) {
    ElMessage.warning('附件尚未保存')
    return
  }
  await purchaseApi.downloadAttachment(id)
}

const getSupplierById = (supplierId: number | null) => {
  return supplierOptions.value.find((item) => item.id === supplierId) || null
}

const getMaterialById = (materialId: number | null) => {
  return materialOptions.value.find((item) => item.id === materialId) || null
}

const getPlanById = (planId: number) => {
  return planOptions.value.find((item) => item.id === planId) || null
}

const normalizeItem = (item: Partial<PurchaseOrderItem>): PurchaseOrderItem => ({
  id: item.id ? Number(item.id) : undefined,
  planItemId: item.planItemId === undefined || item.planItemId === null ? null : Number(item.planItemId),
  planId: item.planId === undefined || item.planId === null ? null : Number(item.planId),
  planNo: item.planNo || '',
  planName: item.planName || '',
  planOrgName: item.planOrgName || '',
  materialId: item.materialId === undefined || item.materialId === null ? null : Number(item.materialId),
  materialName: item.materialName || '',
  spec: item.spec || '',
  unit: item.unit || '',
  quantity: item.quantity === undefined || item.quantity === null ? null : Number(item.quantity),
  unitPrice: item.unitPrice === undefined || item.unitPrice === null ? (item.unitCost === undefined || item.unitCost === null ? null : Number(item.unitCost)) : Number(item.unitPrice),
  unitCost: item.unitCost === undefined || item.unitCost === null ? (item.unitPrice === undefined || item.unitPrice === null ? null : Number(item.unitPrice)) : Number(item.unitCost),
  subtotal: roundToCurrency(Number(item.subtotal || 0)),
  receivedQty: roundToQuantity(Number(item.receivedQty || 0)),
  inboundQty: roundToQuantity(Number(item.inboundQty || 0)),
  remainingInboundQty: roundToQuantity(Number(item.remainingInboundQty || 0)),
  remark: item.remark || '',
})

const normalizeLinkedInboundRecord = (record: Partial<PurchaseOrderLinkedInboundRecord>): PurchaseOrderLinkedInboundRecord => ({
  inboundOrderId: Number(record.inboundOrderId || 0),
  inboundNo: record.inboundNo || '',
  inboundDate: record.inboundDate || '',
  status: record.status || '',
  postStatus: record.postStatus || '',
  materialName: record.materialName || '',
  spec: record.spec || '',
  unit: record.unit || '',
  inboundQuantity: roundToQuantity(Number(record.inboundQuantity || 0)),
  operatorName: record.operatorName || '',
  createdAt: record.createdAt || '',
})

const hasMeaningfulText = (value: unknown) => typeof value === 'string' && value.trim().length > 0

const hasMeaningfulAttachment = (attachments: PurchaseOrderAttachment[], fallbackUrl?: string) => {
  return Boolean(fallbackUrl) || attachments.some((attachment) => hasMeaningfulText(attachment.url))
}

const normalizeRecord = (record: Partial<PurchaseOrderRecord>): PurchaseOrderRecord => {
  const items = Array.isArray(record.items) ? record.items.map((item) => normalizeItem(item)) : []
  const logisticsAttachments = normalizeAttachmentList(
    record.logisticsAttachments,
    record.logisticsAttachmentName || '',
    record.logisticsAttachmentUrl || '',
  )
  const inspectionAttachments = normalizeAttachmentList(
    record.inspectionAttachments,
    record.inspectionAttachmentName || '',
    record.inspectionAttachmentUrl || '',
  )
  const traceabilityAttachments = normalizeAttachmentList(
    record.traceabilityAttachments,
    record.traceAttachmentName || '',
    record.traceAttachmentUrl || '',
  )
  return {
    id: Number(record.id || 0),
    orderNo: record.orderNo || '',
    supplierId: record.supplierId === undefined || record.supplierId === null ? null : Number(record.supplierId),
    supplierName: record.supplierName || '',
    orgId: record.orgId === undefined || record.orgId === null ? null : Number(record.orgId),
    orgName: record.orgName || getDefaultOrgName(),
    buyerName: record.buyerName || record.createdBy || currentUserName.value,
    createdById: record.createdById === undefined || record.createdById === null ? null : Number(record.createdById),
    createdBy: record.createdBy || record.buyerName || currentUserName.value,
    orderDate: record.orderDate || '',
    expectedArrival: record.expectedArrival || '',
    totalAmount: roundToCurrency(Number(record.totalAmount || 0)),
    logisticsCompany: record.logisticsCompany || '',
    logisticsTrackingNo: record.logisticsTrackingNo || '',
    logisticsStatus: (record.logisticsStatus || '') as PurchaseOrderLogisticsStatus | '',
    logisticsRemark: record.logisticsRemark || '',
    logisticsSourceType: (record.logisticsSourceType || 'manual') as PurchaseOrderMaintenanceSourceType,
    logisticsSyncPayload: record.logisticsSyncPayload || '',
    shippedAt: record.shippedAt || '',
    arrivedAt: record.arrivedAt || '',
    logisticsAttachmentName: record.logisticsAttachmentName || '',
    logisticsAttachmentUrl: record.logisticsAttachmentUrl || '',
    logisticsAttachments,
    inspectionReportNo: record.inspectionReportNo || '',
    inspectionResult: record.inspectionResult || '',
    inspectionAgency: record.inspectionAgency || '',
    inspectionAt: record.inspectionAt || '',
    inspectionRemark: record.inspectionRemark || '',
    inspectionSourceType: (record.inspectionSourceType || 'manual') as PurchaseOrderMaintenanceSourceType,
    inspectionSyncPayload: record.inspectionSyncPayload || '',
    inspectionAttachmentName: record.inspectionAttachmentName || '',
    inspectionAttachmentUrl: record.inspectionAttachmentUrl || '',
    inspectionAttachments,
    inspectionFilled: record.inspectionFilled ?? (
      hasMeaningfulText(record.inspectionReportNo)
      || hasMeaningfulText(record.inspectionResult)
      || hasMeaningfulText(record.inspectionAgency)
      || hasMeaningfulText(record.inspectionAt)
      || hasMeaningfulText(record.inspectionRemark)
      || hasMeaningfulText(record.inspectionSyncPayload)
      || hasMeaningfulAttachment(inspectionAttachments, record.inspectionAttachmentUrl || '')
    ),
    attachmentName: record.attachmentName || '',
    attachmentUrl: record.attachmentUrl || '',
    status: (record.status || 'pending_submit') as PurchaseOrderStatus,
    deleted: Boolean(record.deleted),
    remark: record.remark || '',
    createdAt: record.createdAt || '',
    updatedAt: record.updatedAt || '',
    auditBy: record.auditBy || null,
    auditAt: record.auditAt || null,
    auditRemark: record.auditRemark || '',
    voidReason: record.voidReason || '',
    voidRequestedBy: record.voidRequestedBy || null,
    voidRequestedAt: record.voidRequestedAt || null,
    voidAuditBy: record.voidAuditBy || null,
    voidAuditAt: record.voidAuditAt || null,
    voidAuditRemark: record.voidAuditRemark || '',
    traceBatchId: record.traceBatchId || '',
    traceOrigin: record.traceOrigin || '',
    traceRemark: record.traceRemark || '',
    traceSourceType: (record.traceSourceType || 'manual') as PurchaseOrderMaintenanceSourceType,
    traceSyncPayload: record.traceSyncPayload || '',
    traceAttachmentName: record.traceAttachmentName || '',
    traceAttachmentUrl: record.traceAttachmentUrl || '',
    traceabilityAttachments,
    traceabilityFilled: record.traceabilityFilled ?? (
      hasMeaningfulText(record.traceBatchId)
      || hasMeaningfulText(record.traceOrigin)
      || hasMeaningfulText(record.traceRemark)
      || hasMeaningfulText(record.traceSyncPayload)
      || hasMeaningfulAttachment(traceabilityAttachments, record.traceAttachmentUrl || '')
    ),
    relatedPlanIds: Array.isArray(record.relatedPlanIds) ? record.relatedPlanIds.map((item) => Number(item)) : [],
    relatedPlans: Array.isArray(record.relatedPlans)
      ? record.relatedPlans.map((item) => ({
          id: Number(item.id || 0),
          planNo: item.planNo || '',
          planName: item.planName || '',
          orgName: item.orgName || '',
        }))
      : [],
    items,
  }
}

const syncItemDerivedFields = (item: PurchaseOrderFormItem) => {
  if (item.sourceType === 'manual') {
    const material = getMaterialById(item.materialId)
    if (!material) {
      item.materialName = ''
      item.spec = ''
      item.unit = ''
    } else {
      item.materialName = material.name
      item.unit = material.unit
      if (!item.spec) {
        item.spec = material.spec || ''
      }
      if (item.unitPrice === null || item.unitPrice === undefined) {
        item.unitPrice = roundToCurrency(Number(material.referencePrice || 0))
      }
    }
  }
  item.subtotal = roundToCurrency((Number(item.quantity) || 0) * (Number(item.unitPrice) || 0))
}

const syncOrderTotal = (state: PurchaseOrderFormState) => {
  state.items.forEach((item) => syncItemDerivedFields(item))
  state.totalAmount = roundToCurrency(state.items.reduce((sum, item) => sum + item.subtotal, 0))
}

const createManualItem = (): PurchaseOrderFormItem => ({
  rowId: createRowId(),
  sourceType: 'manual',
  lockedSource: false,
  id: undefined,
  planItemId: null,
  planId: null,
  planNo: '',
  planName: '',
  planOrgName: '',
  materialId: null,
  materialName: '',
  spec: '',
  unit: '',
  quantity: null,
  unitPrice: null,
  unitCost: null,
  subtotal: 0,
  receivedQty: 0,
  remark: '',
  planQuantity: 0,
  orderedQuantity: 0,
  remainingQuantity: 0,
})

const toFormItem = (item: PurchaseOrderItem): PurchaseOrderFormItem => ({
  rowId: createRowId(),
  sourceType: item.planItemId ? 'plan' : 'manual',
  lockedSource: !!item.planItemId,
  id: item.id,
  planItemId: item.planItemId ?? null,
  planId: item.planId ?? null,
  planNo: item.planNo || '',
  planName: item.planName || '',
  planOrgName: item.planOrgName || '',
  materialId: item.materialId,
  materialName: item.materialName,
  spec: item.spec,
  unit: item.unit,
  quantity: item.quantity === undefined || item.quantity === null ? null : Number(item.quantity),
  unitPrice: item.unitPrice === undefined || item.unitPrice === null ? (item.unitCost === undefined || item.unitCost === null ? null : Number(item.unitCost)) : Number(item.unitPrice),
  unitCost: item.unitCost === undefined || item.unitCost === null ? (item.unitPrice === undefined || item.unitPrice === null ? null : Number(item.unitPrice)) : Number(item.unitCost),
  subtotal: roundToCurrency(Number(item.subtotal || 0)),
  receivedQty: roundToQuantity(Number(item.receivedQty || 0)),
  remark: item.remark || '',
  planQuantity: 0,
  orderedQuantity: 0,
  remainingQuantity: item.quantity === undefined || item.quantity === null ? 0 : Number(item.quantity),
})

const createFormState = (): PurchaseOrderFormState => ({
  id: null,
  orderNo: `PO-${formatDate().replace(/-/g, '')}-${Math.floor(Math.random() * 900 + 100)}`,
  supplierId: null,
  supplierName: '',
  orgId: getDefaultOrgId(),
  orgName: getDefaultOrgName(),
  buyerName: currentUserName.value,
  createdById: userStore.userInfo?.id ?? null,
  createdBy: currentUserName.value,
  orderDate: formatDate(),
  expectedArrival: '',
  totalAmount: 0,
  logisticsCompany: '',
  logisticsTrackingNo: '',
  logisticsStatus: '',
  logisticsRemark: '',
  logisticsSourceType: 'manual',
  logisticsSyncPayload: '',
  shippedAt: '',
  arrivedAt: '',
  logisticsAttachmentName: '',
  logisticsAttachmentUrl: '',
  logisticsAttachments: [],
  inspectionReportNo: '',
  inspectionResult: '',
  inspectionAgency: '',
  inspectionAt: '',
  inspectionRemark: '',
  inspectionSourceType: 'manual',
  inspectionSyncPayload: '',
  inspectionAttachmentName: '',
  inspectionAttachmentUrl: '',
  inspectionAttachments: [],
  attachmentName: '',
  attachmentUrl: '',
  status: 'pending_submit',
  deleted: false,
  remark: '',
  createdAt: '',
  updatedAt: '',
  auditBy: null,
  auditAt: null,
  auditRemark: '',
  voidReason: '',
  voidRequestedBy: null,
  voidRequestedAt: null,
  voidAuditBy: null,
  voidAuditAt: null,
  voidAuditRemark: '',
  traceBatchId: '',
  traceOrigin: '',
  traceRemark: '',
  traceSourceType: 'manual',
  traceSyncPayload: '',
  traceAttachmentName: '',
  traceAttachmentUrl: '',
  traceabilityAttachments: [],
  relatedPlanIds: [],
  relatedPlans: [],
  items: [createManualItem()],
})

const toFormState = (record: PurchaseOrderRecord): PurchaseOrderFormState => ({
  id: record.id,
  orderNo: record.orderNo,
  supplierId: record.supplierId,
  supplierName: record.supplierName,
  orgId: record.orgId,
  orgName: record.orgName,
  buyerName: record.buyerName,
  createdById: record.createdById,
  createdBy: record.createdBy,
  orderDate: record.orderDate,
  expectedArrival: record.expectedArrival,
  totalAmount: record.totalAmount,
  logisticsCompany: record.logisticsCompany,
  logisticsTrackingNo: record.logisticsTrackingNo,
  logisticsStatus: record.logisticsStatus,
  logisticsRemark: record.logisticsRemark,
  logisticsSourceType: record.logisticsSourceType,
  logisticsSyncPayload: record.logisticsSyncPayload,
  shippedAt: record.shippedAt,
  arrivedAt: record.arrivedAt,
  logisticsAttachmentName: record.logisticsAttachmentName,
  logisticsAttachmentUrl: record.logisticsAttachmentUrl,
  logisticsAttachments: record.logisticsAttachments.map((attachment) => ({ ...attachment })),
  inspectionReportNo: record.inspectionReportNo,
  inspectionResult: record.inspectionResult,
  inspectionAgency: record.inspectionAgency,
  inspectionAt: record.inspectionAt,
  inspectionRemark: record.inspectionRemark,
  inspectionSourceType: record.inspectionSourceType,
  inspectionSyncPayload: record.inspectionSyncPayload,
  inspectionAttachmentName: record.inspectionAttachmentName,
  inspectionAttachmentUrl: record.inspectionAttachmentUrl,
  inspectionAttachments: record.inspectionAttachments.map((attachment) => ({ ...attachment })),
  attachmentName: record.attachmentName,
  attachmentUrl: record.attachmentUrl,
  status: record.status,
  deleted: record.deleted,
  remark: record.remark,
  createdAt: record.createdAt,
  updatedAt: record.updatedAt,
  auditBy: record.auditBy,
  auditAt: record.auditAt,
  auditRemark: record.auditRemark,
  voidReason: record.voidReason,
  voidRequestedBy: record.voidRequestedBy,
  voidRequestedAt: record.voidRequestedAt,
  voidAuditBy: record.voidAuditBy,
  voidAuditAt: record.voidAuditAt,
  voidAuditRemark: record.voidAuditRemark,
  traceBatchId: record.traceBatchId,
  traceOrigin: record.traceOrigin,
  traceRemark: record.traceRemark,
  traceSourceType: record.traceSourceType,
  traceSyncPayload: record.traceSyncPayload,
  traceAttachmentName: record.traceAttachmentName,
  traceAttachmentUrl: record.traceAttachmentUrl,
  traceabilityAttachments: record.traceabilityAttachments.map((attachment) => ({ ...attachment })),
  relatedPlanIds: [...record.relatedPlanIds],
  relatedPlans: [...record.relatedPlans],
  items: record.items.length ? record.items.map((item) => toFormItem(item)) : [createManualItem()],
})

const formState = ref<PurchaseOrderFormState>(createFormState())

const formDialogTitle = computed(() => formMode.value === 'create' ? '新增采购订单' : '编辑采购订单')

const currentAttachmentDisplayName = computed(() => {
  return attachmentFile.value?.name || formState.value.attachmentName || ''
})

const selectedSupplierHint = computed(() => {
  const supplier = getSupplierById(formState.value.supplierId)
  if (!supplier) {
    return '请选择当前组织下已审核且关键资质有效的供应商；关联单据可独立选择。'
  }
  if (supplier.disabled) {
    return supplier.unavailableReason || '当前供应商已不在采购可选池中，仅保留历史关联，不可再用于新的采购业务关联。'
  }
  return `${supplier.contactName || '未维护联系人'}｜${supplier.contactPhone || '未维护联系电话'}；切换供应商不会影响关联单据选择。`
})

const selectedRelatedPlanHint = computed(() => {
  if (!planOptions.value.length) {
    return '当前权限范围内暂无可关联的采购计划单。'
  }
  if (!formState.value.relatedPlanIds.length) {
    return '直接展示当前权限范围内仍有剩余可关联数量的采购计划单，与供应商选择无关。'
  }
  return `已关联 ${formState.value.relatedPlanIds.length} 个采购计划单：${formState.value.relatedPlanIds
    .map((id) => {
      const plan = getPlanById(id)
      return plan ? `${plan.planNo}｜${plan.planName}${plan.orgName ? `｜${plan.orgName}` : ''}` : ''
    })
    .filter(Boolean)
    .join('、')}`
})

const getMaterialSpecs = (materialId: number | null) => {
  if (!materialId) {
    return Array.from(new Set(
      materialOptions.value
        .map((item) => item.spec)
        .filter((spec): spec is string => Boolean(spec))
    ))
  }
  const material = getMaterialById(materialId)
  return material?.spec ? [material.spec] : []
}

const mergeSupplierOptions = (baseList: PurchaseOrderSupplierOption[], currentState: PurchaseOrderFormState) => {
  const merged = [...baseList]
  if (
    currentState.supplierId
    && currentState.supplierName
    && !merged.some((item) => item.id === currentState.supplierId)
  ) {
    merged.push({
      id: currentState.supplierId,
      name: currentState.supplierName,
      contactName: '',
      contactPhone: '',
      disabled: true,
      unavailableReason: '当前供应商关键资质已过期或已不满足采购准入条件，仅保留历史关联，不可再作为新的采购供应商。',
    })
  }
  return merged
}

const mergeMaterialOptions = (baseList: PurchaseOrderMaterialOption[], items: PurchaseOrderFormItem[]) => {
  const merged = [...baseList]
  items
    .filter((item) => item.sourceType === 'manual')
    .forEach((item) => {
      if (item.materialId && item.materialName && !merged.some((option) => option.id === item.materialId)) {
        merged.push({
          id: item.materialId,
          name: item.materialName,
          unit: item.unit,
          spec: item.spec,
          referencePrice: Number(item.unitPrice || 0),
        })
      }
    })
  return merged
}

const ensureAtLeastOneManualRow = () => {
  if (!formState.value.items.length) {
    formState.value.items = [createManualItem()]
  }
}

const removePlanRows = () => {
  const manualRows = formState.value.items.filter((item) => item.sourceType === 'manual')
  formState.value.items = manualRows.length ? manualRows : [createManualItem()]
  planItemOptions.value = []
  syncOrderTotal(formState.value)
}

const syncPlanItemsToForm = (options: PurchaseOrderPlanItemOption[]) => {
  const manualRows = formState.value.items.filter((item) => item.sourceType === 'manual')
  const existingPlanMap = new Map(
    formState.value.items
      .filter((item) => item.sourceType === 'plan' && item.planItemId)
      .map((item) => [Number(item.planItemId), item])
  )
  const isEditingExistingOrder = formMode.value === 'edit' && !!formState.value.id

  const planRows = options.map((option) => {
    const existing = existingPlanMap.get(option.id)
    const currentQuantity = existing?.quantity === null || existing?.quantity === undefined
      ? null
      : Number(existing.quantity)
    const nextQuantity = currentQuantity === null
      ? (isEditingExistingOrder ? 0 : Number(option.remainingQuantity || 0))
      : Math.min(currentQuantity, Number(option.remainingQuantity || 0))

    const row: PurchaseOrderFormItem = {
      rowId: existing?.rowId || createRowId(),
      sourceType: 'plan',
      lockedSource: true,
      id: existing?.id,
      planItemId: option.id,
      planId: option.planId,
      planNo: option.planNo,
      planName: option.planName,
      planOrgName: option.planOrgName || '',
      materialId: option.materialId,
      materialName: option.materialName,
      spec: option.spec || '',
      unit: option.unit || '',
      quantity: nextQuantity,
      unitPrice: existing?.unitPrice === null || existing?.unitPrice === undefined
        ? Number(option.unitPrice || 0)
        : Number(existing.unitPrice),
      unitCost: existing?.unitCost === null || existing?.unitCost === undefined
        ? Number(option.unitPrice || 0)
        : Number(existing.unitCost),
      subtotal: roundToCurrency(Number(existing?.subtotal || 0)),
      receivedQty: roundToQuantity(Number(existing?.receivedQty || 0)),
      remark: existing?.remark || option.remark || '',
      planQuantity: Number(option.planQuantity || 0),
      orderedQuantity: Number(option.orderedQuantity || 0),
      remainingQuantity: Number(option.remainingQuantity || 0),
    }
    syncItemDerivedFields(row)
    return row
  })

  formState.value.items = [...planRows, ...manualRows]
  ensureAtLeastOneManualRow()
  syncOrderTotal(formState.value)
}

const fetchStatistics = async () => {
  const res = await purchaseApi.getStatistics()
  statistics.value = {
    total: Number(res.data.total || 0),
    pending: Number(res.data.pending || 0),
    approved: Number(res.data.approved || 0),
    totalAmount: roundToCurrency(Number(res.data.totalAmount || 0)),
  }
  purchaseOrderStore.setStatisticsCache(statistics.value)
}

const fetchList = async (page = currentPage.value) => {
  tableLoading.value = true
  try {
    const dateRange = normalizeSearchDateRange(searchForm.dateRange)
    const params: PurchaseOrderQuery = {
      pageNum: page,
      pageSize: pageSize.value,
      keyword: searchForm.keyword.trim() || undefined,
      status: searchForm.status || undefined,
      dateStart: dateRange[0] || undefined,
      dateEnd: dateRange[1] || undefined,
    }
    const res = await purchaseApi.getList(params)
    purchaseOrders.value = (res.data.list || []).map((item) => normalizeRecord(item))
    total.value = Number(res.data.total || 0)
    currentPage.value = Number(res.data.pageNum || page)
    pageSize.value = Number(res.data.pageSize || pageSize.value)
    purchaseOrderStore.setListCache({
      list: purchaseOrders.value.map((item) => normalizeRecord(item)),
      total: total.value,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
  } finally {
    tableLoading.value = false
  }
}

const fetchDetail = async (id: number) => {
  const res = await purchaseApi.getDetail(id)
  return normalizeRecord(res.data)
}

const fetchLinkedInboundRecords = async (id: number) => {
  const res = await purchaseApi.getLinkedInboundRecords(id)
  return Array.isArray(res.data) ? res.data.map((item) => normalizeLinkedInboundRecord(item)) : []
}

const getLinkedInboundRowClassName = ({ rowIndex }: { rowIndex: number }) =>
  linkedInboundRowClassNames.value[rowIndex] || ''

const loadFormBaseOptions = async () => {
  const orgId = formState.value.orgId
  if (!orgId) {
    supplierOptions.value = []
    materialOptions.value = []
    return
  }

  formOptionLoading.value = true
  try {
    const [supplierRes, materialRes] = await Promise.all([
      purchaseApi.getSupplierOptions(orgId),
      purchaseApi.getMaterialOptions(orgId),
    ])
    supplierOptions.value = mergeSupplierOptions(supplierRes.data || [], formState.value)
    materialOptions.value = mergeMaterialOptions(materialRes.data || [], formState.value.items)
  } finally {
    formOptionLoading.value = false
  }
}

const loadPlanOptions = async () => {
  if (!formState.value.orgId) {
    planOptions.value = []
    formState.value.relatedPlanIds = []
    removePlanRows()
    return
  }

  const res = await purchaseApi.getSelectablePlans({
    excludeOrderId: formState.value.id || undefined,
  })
  planOptions.value = res.data || []
  const selectableIds = new Set(planOptions.value.map((item) => item.id))
  formState.value.relatedPlanIds = formState.value.relatedPlanIds.filter((item) => selectableIds.has(item))
}

const loadPlanItems = async () => {
  if (!formState.value.orgId || !formState.value.relatedPlanIds.length) {
    removePlanRows()
    return
  }

  const res = await purchaseApi.getPlanItems({
    planIds: formState.value.relatedPlanIds.join(','),
    excludeOrderId: formState.value.id || undefined,
  })
  planItemOptions.value = res.data || []
  syncPlanItemsToForm(planItemOptions.value)
}

const refreshListAndStatistics = async (page = currentPage.value) => {
  await Promise.all([
    fetchList(page),
    fetchStatistics(),
  ])
}

const hasActiveSearchConditions = () => {
  return Boolean(
    searchForm.keyword.trim()
    || searchForm.status
    || normalizeSearchDateRange(searchForm.dateRange).length
  )
}

const restoreCachedListState = () => {
  purchaseOrders.value = purchaseOrderStore.list.map((item) => normalizeRecord(item))
  total.value = purchaseOrderStore.total
  currentPage.value = purchaseOrderStore.pageNum
  pageSize.value = purchaseOrderStore.pageSize
  statistics.value = {
    total: Number(purchaseOrderStore.statistics.total || 0),
    pending: Number(purchaseOrderStore.statistics.pending || 0),
    approved: Number(purchaseOrderStore.statistics.approved || 0),
    totalAmount: roundToCurrency(Number(purchaseOrderStore.statistics.totalAmount || 0)),
  }
}

const refreshListOnReturnIfNeeded = async () => {
  if (!purchaseOrderStore.initialized) {
    await Promise.all([
      fetchStatistics(),
      fetchList(1),
    ])
    return
  }
  restoreCachedListState()
  if (!hasActiveSearchConditions()) {
    return
  }
  await refreshListAndStatistics(currentPage.value)
}

const applySearch = async () => {
  currentPage.value = 1
  await fetchList(1)
}

const handleDateRangeClear = () => {
  searchForm.dateRange = []
}

const resetSearch = async () => {
  purchaseOrderStore.resetSearchCache()
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.dateRange = []
  await applySearch()
}

const openCreateDialog = async () => {
  formMode.value = 'create'
  formState.value = createFormState()
  attachmentFile.value = null
  attachmentMarkedForDeletion.value = false
  formVisible.value = true
  await loadFormBaseOptions()
  await loadPlanOptions()
}

const openEditDialogById = async (orderId: number) => {
  detailLoading.value = true
  try {
    const detail = await fetchDetail(orderId)
    if (detail.deleted) {
      ElMessage.warning('已删除采购订单仅支持查看，不允许编辑')
      return
    }
    formMode.value = 'edit'
    formState.value = toFormState(detail)
    attachmentFile.value = null
    attachmentMarkedForDeletion.value = false
    formVisible.value = true
    await loadFormBaseOptions()
    await loadPlanOptions()
    await loadPlanItems()
  } finally {
    detailLoading.value = false
  }
}

const openEditDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单仅支持查看，不允许编辑')
    return
  }
  if (!canEdit(row)) {
    ElMessage.warning('仅草稿、待审核或已驳回状态可编辑')
    return
  }
  await openEditDialogById(row.id)
}

const openDetailDialog = async (row: PurchaseOrderRecord) => {
  detailLoading.value = true
  try {
    const [detail, records] = await Promise.all([
      fetchDetail(row.id),
      fetchLinkedInboundRecords(row.id),
    ])
    detailOrder.value = detail
    linkedInboundRecords.value = records
    detailVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

const consumeDashboardDrillDown = async () => {
  const from = typeof route.query.from === 'string' ? route.query.from : ''
  const traceBatchId = typeof route.query.traceBatchId === 'string' ? route.query.traceBatchId.trim() : ''
  const autoOpen = typeof route.query.autoOpen === 'string' ? route.query.autoOpen === '1' : false

  if (from !== 'dashboard' || !traceBatchId || handledDashboardTraceBatchId.value === traceBatchId) {
    return
  }

  handledDashboardTraceBatchId.value = traceBatchId
  searchForm.keyword = traceBatchId
  searchForm.status = ''
  searchForm.dateRange = []
  searchForm.showDeleted = false

  await applySearch()

  const matchedOrder = purchaseOrders.value.find((item) => item.traceBatchId === traceBatchId)
  if (matchedOrder && autoOpen) {
    await openDetailDialog(matchedOrder)
  } else if (!matchedOrder) {
    ElMessage.warning(`未找到追溯批次号为 ${traceBatchId} 的采购订单`)
  }

  const nextQuery = { ...route.query }
  delete nextQuery.from
  delete nextQuery.traceBatchId
  delete nextQuery.autoOpen
  await router.replace({ query: nextQuery })
  handledDashboardTraceBatchId.value = ''
}

const openAuditDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许审核')
    return
  }
  if (!canAudit(row)) {
    ElMessage.warning('仅待审核状态可执行审核')
    return
  }
  detailLoading.value = true
  try {
    auditTarget.value = await fetchDetail(row.id)
    auditForm.remark = ''
    auditVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

const closeAuditDialog = () => {
  auditVisible.value = false
  auditTarget.value = null
  auditForm.remark = ''
}

const openReverseAuditDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许反审核')
    return
  }
  if (!canReverseAudit(row)) {
    ElMessage.warning('当前状态不允许执行反审核')
    return
  }
  detailLoading.value = true
  try {
    const [detail, records] = await Promise.all([
      fetchDetail(row.id),
      fetchLinkedInboundRecords(row.id),
    ])
    reverseTarget.value = detail
    reverseLinkedInboundCount.value = countDistinctInboundOrders(records)
    reverseForm.reason = ''
    reverseVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

const closeReverseAuditDialog = () => {
  reverseVisible.value = false
  reverseTarget.value = null
  reverseLinkedInboundCount.value = 0
  reverseForm.reason = ''
}

const openVoidDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许发起作废')
    return
  }
  if (!canVoid(row)) {
    ElMessage.warning('仅待审核或已审核状态可发起作废申请')
    return
  }
  detailLoading.value = true
  try {
    voidTarget.value = await fetchDetail(row.id)
    voidForm.reason = ''
    voidVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

const closeVoidDialog = () => {
  voidVisible.value = false
  voidTarget.value = null
  voidForm.reason = ''
}

const openVoidAuditDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许执行作废审核')
    return
  }
  if (!canVoidAudit(row)) {
    ElMessage.warning('仅待作废审核状态可执行作废审核')
    return
  }
  detailLoading.value = true
  try {
    voidAuditTarget.value = await fetchDetail(row.id)
    voidAuditForm.approved = true
    voidAuditForm.remark = ''
    voidAuditVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

const closeVoidAuditDialog = () => {
  voidAuditVisible.value = false
  voidAuditTarget.value = null
  voidAuditForm.approved = true
  voidAuditForm.remark = ''
}

const resetLogisticsForm = () => {
  logisticsForm.company = ''
  logisticsForm.trackingNo = ''
  logisticsForm.logisticsStatus = 'in_transit'
  logisticsForm.shippedAt = ''
  logisticsForm.arrivedAt = ''
  logisticsForm.remark = ''
  logisticsForm.sourceType = 'manual'
  logisticsForm.syncPayload = ''
}

const getCurrentLogisticsAttachmentCount = () => {
  return logisticsTarget.value?.logisticsAttachments.filter((attachment) => hasMeaningfulText(attachment.url)).length || 0
}

const validateLogisticsRequiredFields = () => {
  if (!logisticsForm.logisticsStatus) {
    ElMessage.warning('请选择物流状态')
    return false
  }

  if (logisticsRequiredState.value.company && !logisticsForm.company?.trim()) {
    ElMessage.warning('请填写物流公司')
    return false
  }
  if (logisticsRequiredState.value.trackingNo && !logisticsForm.trackingNo?.trim()) {
    ElMessage.warning('请填写物流单号')
    return false
  }
  if (logisticsRequiredState.value.shippedAt && !logisticsForm.shippedAt) {
    ElMessage.warning('请填写发货时间')
    return false
  }
  if (logisticsRequiredState.value.arrivedAt && !logisticsForm.arrivedAt) {
    ElMessage.warning('请填写到货时间')
    return false
  }
  if (logisticsRequiredState.value.attachments && getCurrentLogisticsAttachmentCount() === 0) {
    ElMessage.warning('请上传物流附件')
    return false
  }

  return true
}

const resetInspectionForm = () => {
  inspectionForm.reportNo = ''
  inspectionForm.result = ''
  inspectionForm.agency = ''
  inspectionForm.inspectedAt = ''
  inspectionForm.remark = ''
  inspectionForm.sourceType = 'manual'
  inspectionForm.syncPayload = ''
}

const getCurrentInspectionAttachmentCount = () => {
  return inspectionTarget.value?.inspectionAttachments.filter((attachment) => hasMeaningfulText(attachment.url)).length || 0
}

const validateInspectionRequiredFields = () => {
  if (inspectionRequiredState.value.reportNo && !inspectionForm.reportNo?.trim()) {
    ElMessage.warning('请填写报告编号')
    return false
  }
  if (inspectionRequiredState.value.agency && !inspectionForm.agency?.trim()) {
    ElMessage.warning('请填写检测机构')
    return false
  }
  if (inspectionRequiredState.value.inspectedAt && !inspectionForm.inspectedAt) {
    ElMessage.warning('请填写检测时间')
    return false
  }
  if (inspectionForm.inspectedAt && isFutureInspectionAt(inspectionForm.inspectedAt)) {
    ElMessage.warning('检测时间不能选择未来日期')
    return false
  }
  if (inspectionRequiredState.value.attachments && getCurrentInspectionAttachmentCount() === 0) {
    ElMessage.warning('请上传检测附件')
    return false
  }
  return true
}

const resetTraceabilityForm = () => {
  traceabilityForm.traceBatchId = ''
  traceabilityForm.origin = ''
  traceabilityForm.remark = ''
  traceabilityForm.sourceType = 'manual'
  traceabilityForm.syncPayload = ''
}

const getCurrentTraceabilityAttachmentCount = () => {
  return traceabilityTarget.value?.traceabilityAttachments.filter((attachment) => hasMeaningfulText(attachment.url)).length || 0
}

const validateTraceabilityRequiredFields = () => {
  if (!traceabilityForm.traceBatchId?.trim()) {
    ElMessage.warning('请填写溯源批次码')
    return false
  }
  if (!traceabilityForm.origin?.trim()) {
    ElMessage.warning('请填写来源/产地')
    return false
  }
  if (getCurrentTraceabilityAttachmentCount() === 0) {
    ElMessage.warning('请上传溯源附件')
    return false
  }
  return true
}

const resetSceneIntegrationState = (state: SceneIntegrationState) => {
  Object.assign(state, createSceneIntegrationState())
}

const getSceneIntegrationState = (scene: MaintenanceScene) => {
  if (scene === 'logistics') return logisticsIntegration
  if (scene === 'inspection') return inspectionIntegration
  return traceabilityIntegration
}

const getSceneSyncingRef = (scene: MaintenanceScene) => {
  if (scene === 'logistics') return logisticsSyncing
  if (scene === 'inspection') return inspectionSyncing
  return traceabilitySyncing
}

const getSceneTarget = (scene: MaintenanceScene) => {
  if (scene === 'logistics') return logisticsTarget.value
  if (scene === 'inspection') return inspectionTarget.value
  return traceabilityTarget.value
}

const getSceneSourceType = (scene: MaintenanceScene): PurchaseOrderMaintenanceSourceType => {
  if (scene === 'logistics') return logisticsForm.sourceType || 'manual'
  if (scene === 'inspection') return inspectionForm.sourceType || 'manual'
  return traceabilityForm.sourceType || 'manual'
}

const setSceneSourceType = (scene: MaintenanceScene, value: PurchaseOrderMaintenanceSourceType) => {
  if (scene === 'logistics') {
    logisticsForm.sourceType = value
    return
  }
  if (scene === 'inspection') {
    inspectionForm.sourceType = value
    return
  }
  traceabilityForm.sourceType = value
}

const fillLogisticsFormFromDetail = (detail: PurchaseOrderRecord) => {
  logisticsForm.company = detail.logisticsCompany || ''
  logisticsForm.trackingNo = detail.logisticsTrackingNo || ''
  logisticsForm.logisticsStatus = detail.logisticsStatus || 'in_transit'
  logisticsForm.shippedAt = detail.shippedAt || ''
  logisticsForm.arrivedAt = detail.arrivedAt || ''
  logisticsForm.remark = detail.logisticsRemark || ''
  logisticsForm.sourceType = detail.logisticsSourceType || 'manual'
  logisticsForm.syncPayload = detail.logisticsSyncPayload || ''
}

const fillInspectionFormFromDetail = (detail: PurchaseOrderRecord) => {
  inspectionForm.reportNo = detail.inspectionReportNo || ''
  inspectionForm.result = detail.inspectionResult || ''
  inspectionForm.agency = detail.inspectionAgency || ''
  inspectionForm.inspectedAt = detail.inspectionAt || ''
  inspectionForm.remark = detail.inspectionRemark || ''
  inspectionForm.sourceType = detail.inspectionSourceType || 'manual'
  inspectionForm.syncPayload = detail.inspectionSyncPayload || ''
}

const fillTraceabilityFormFromDetail = (detail: PurchaseOrderRecord) => {
  traceabilityForm.traceBatchId = detail.traceBatchId || ''
  traceabilityForm.origin = detail.traceOrigin || ''
  traceabilityForm.remark = detail.traceRemark || ''
  traceabilityForm.sourceType = detail.traceSourceType || 'manual'
  traceabilityForm.syncPayload = detail.traceSyncPayload || ''
}

const resolveSceneBusinessExternalNo = (scene: MaintenanceScene) => {
  if (scene === 'logistics') return logisticsForm.trackingNo?.trim() || ''
  if (scene === 'inspection') return inspectionForm.reportNo?.trim() || ''
  return traceabilityForm.traceBatchId?.trim() || ''
}

const applySceneIntegrationMeta = (
  scene: MaintenanceScene,
  detail: PurchaseOrderRecord,
  meta?: PurchaseOrderSceneIntegrationMeta | null,
) => {
  const state = getSceneIntegrationState(scene)
  resetSceneIntegrationState(state)
  state.configOptions = meta?.configOptions || []
  state.bindingId = meta?.currentBinding?.bindingId ?? null
  state.configId = meta?.currentBinding?.configId ?? meta?.selectedConfigId ?? state.configOptions[0]?.id ?? null
  state.externalNo = meta?.currentBinding?.externalNo || resolveSceneBusinessExternalNo(scene)
  state.syncStatus = meta?.currentBinding?.syncStatus || ''
  state.lastSyncAt = meta?.currentBinding?.lastSyncAt || ''
  state.nextSyncAt = meta?.currentBinding?.nextSyncAt || ''
  state.lastErrorMessage = meta?.currentBinding?.lastErrorMessage || ''
  state.allowDocumentSwitch = meta?.allowDocumentSwitch ?? 1
  state.forceThirdParty = meta?.forceThirdParty ?? 0
  state.allowManualFallback = meta?.allowManualFallback ?? 0
  state.autoCoverEnabled = meta?.autoCoverEnabled ?? 0
  state.externalNoFieldRule = meta?.externalNoFieldRule || ''
  state.recentSyncLogs = meta?.recentSyncLogs || []
  state.recentCallbackLogs = meta?.recentCallbackLogs || []

  const currentSourceType =
    scene === 'logistics'
      ? detail.logisticsSourceType
      : scene === 'inspection'
        ? detail.inspectionSourceType
        : detail.traceSourceType
  let nextSourceType: PurchaseOrderMaintenanceSourceType =
    (currentSourceType as PurchaseOrderMaintenanceSourceType)
    || ((meta?.defaultMode === 'third_party' && state.configOptions.length > 0) ? 'third_party' : 'manual')
  if (state.forceThirdParty === 1 && (state.configOptions.length > 0 || state.bindingId)) {
    nextSourceType = 'third_party'
  }
  if (nextSourceType === 'third_party' && state.configOptions.length === 0 && !state.bindingId) {
    nextSourceType = 'manual'
  }
  setSceneSourceType(scene, nextSourceType)
}

const canEditSceneSourceType = (scene: MaintenanceScene) => {
  const state = getSceneIntegrationState(scene)
  if (state.forceThirdParty === 1) return false
  if (state.bindingId && state.allowDocumentSwitch !== 1 && state.allowManualFallback !== 1) return false
  return true
}

const getSceneExternalNoLabel = (scene: MaintenanceScene) => {
  if (scene === 'logistics') return '外部物流单号'
  if (scene === 'inspection') return '外部报告编号'
  return '外部批次号'
}

const getSceneIntegrationSummary = (scene: MaintenanceScene) => {
  const state = getSceneIntegrationState(scene)
  return INTEGRATION_SYNC_STATUS_MAP[state.syncStatus || ''] || { label: state.syncStatus || '未同步', type: 'info' as const }
}

const handleSceneSourceTypeChange = (scene: MaintenanceScene) => {
  const state = getSceneIntegrationState(scene)
  const currentSourceType = getSceneSourceType(scene)
  if (currentSourceType === 'third_party' && state.configOptions.length === 0 && !state.bindingId) {
    ElMessage.warning('当前组织在该场景下暂无可用第三方同步方案，请先在第三方接入管理中完成配置')
    setSceneSourceType(scene, 'manual')
    return
  }
  if (currentSourceType === 'third_party' && !state.externalNo) {
    state.externalNo = resolveSceneBusinessExternalNo(scene)
  }
}

const validateThirdPartySceneSave = (scene: MaintenanceScene) => {
  if (getSceneSourceType(scene) !== 'third_party') {
    return true
  }
  const state = getSceneIntegrationState(scene)
  if (!state.configId) {
    ElMessage.warning('请选择同步方案')
    return false
  }
  const externalNo = state.externalNo?.trim() || resolveSceneBusinessExternalNo(scene)
  if (!externalNo) {
    ElMessage.warning(`请填写${getSceneExternalNoLabel(scene)}`)
    return false
  }
  state.externalNo = externalNo
  return true
}

const getSceneDialogTitle = (scene: MaintenanceScene) => {
  if (scene === 'logistics') return '物流同步记录'
  if (scene === 'inspection') return '检测同步记录'
  return '溯源同步记录'
}

const getSceneMaintainLabel = (scene: MaintenanceScene) => {
  if (scene === 'logistics') return '物流'
  if (scene === 'inspection') return '检测'
  return '溯源'
}

const loadSceneIntegrationMetaSafely = async (
  scene: MaintenanceScene,
  orderId: number,
  options: { showFallbackMessage?: boolean } = {},
) => {
  try {
    const res = await purchaseApi.getSceneIntegrationMeta(scene, orderId, { silentError: true })
    return res.data || null
  } catch (error) {
    console.warn(`加载采购订单${getSceneMaintainLabel(scene)}第三方同步信息失败:`, error)
    if (options.showFallbackMessage !== false) {
      ElMessage.warning(`第三方${getSceneMaintainLabel(scene)}同步信息加载失败，已展示基础维护信息`)
    }
    return null
  }
}

const hydrateSceneIntegrationMeta = async (
  scene: MaintenanceScene,
  orderId: number,
  options: { showFallbackMessage?: boolean } = {},
) => {
  const meta = await loadSceneIntegrationMetaSafely(scene, orderId, options)
  const target = getSceneTarget(scene)
  if (!target || target.id !== orderId) {
    return null
  }
  applySceneIntegrationMeta(scene, target, meta)
  return meta
}

const loadSceneIntegrationLogs = async (scene: MaintenanceScene) => {
  const target = getSceneTarget(scene)
  if (!target) return
  sceneLogsLoading.value = true
  try {
    const res = await purchaseApi.getSceneIntegrationLogs(scene, target.id, { silentError: true })
    sceneLogsData.binding = res.data?.binding || null
    sceneLogsData.syncLogs = res.data?.syncLogs || []
    sceneLogsData.callbackLogs = res.data?.callbackLogs || []
    sceneLogsTitle.value = getSceneDialogTitle(scene)
    sceneLogsVisible.value = true
  } catch (error) {
    console.warn(`加载采购订单${getSceneMaintainLabel(scene)}同步记录失败:`, error)
    ElMessage.warning(`第三方${getSceneMaintainLabel(scene)}同步记录加载失败，请稍后重试`)
  } finally {
    sceneLogsLoading.value = false
  }
}

const prettyPayload = (payload?: string) => {
  if (!payload) return ''
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

const applySceneSyncPreview = (scene: MaintenanceScene, normalizedPayload?: string) => {
  if (!normalizedPayload) return
  try {
    const parsed = JSON.parse(normalizedPayload) as Record<string, any>
    if (scene === 'logistics') {
      logisticsForm.trackingNo = parsed.trackingNo || logisticsForm.trackingNo
      logisticsForm.company = parsed.company || logisticsForm.company
      logisticsForm.logisticsStatus = parsed.status || logisticsForm.logisticsStatus
      logisticsForm.shippedAt = parsed.shippedAt || logisticsForm.shippedAt
      logisticsForm.arrivedAt = parsed.arrivedAt || logisticsForm.arrivedAt
      logisticsForm.remark = parsed.remark || logisticsForm.remark
      logisticsForm.syncPayload = prettyPayload(normalizedPayload)
      return
    }
    if (scene === 'inspection') {
      inspectionForm.reportNo = parsed.reportNo || inspectionForm.reportNo
      inspectionForm.result = parsed.result || inspectionForm.result
      inspectionForm.agency = parsed.agency || inspectionForm.agency
      inspectionForm.inspectedAt = parsed.inspectionAt || inspectionForm.inspectedAt
      inspectionForm.remark = parsed.remark || inspectionForm.remark
      inspectionForm.syncPayload = prettyPayload(normalizedPayload)
      return
    }
    traceabilityForm.traceBatchId = parsed.batchId || parsed.traceBatchId || traceabilityForm.traceBatchId
    traceabilityForm.origin = parsed.origin || traceabilityForm.origin
    traceabilityForm.remark = parsed.remark || traceabilityForm.remark
    traceabilityForm.syncPayload = prettyPayload(normalizedPayload)
  } catch {
    if (scene === 'logistics') logisticsForm.syncPayload = normalizedPayload
    if (scene === 'inspection') inspectionForm.syncPayload = normalizedPayload
    if (scene === 'traceability') traceabilityForm.syncPayload = normalizedPayload
  }
}

const refreshSceneAfterSync = async (
  scene: MaintenanceScene,
  orderId: number,
  normalizedPayload?: string,
  queryOnly = false,
) => {
  const [detail, meta] = await Promise.all([
    fetchDetail(orderId),
    loadSceneIntegrationMetaSafely(scene, orderId, { showFallbackMessage: false }),
  ])
  if (scene === 'logistics') {
    logisticsTarget.value = detail
    fillLogisticsFormFromDetail(detail)
    logisticsPersistedAttachmentUrls.value = getAttachmentUrls(detail.logisticsAttachments)
  } else if (scene === 'inspection') {
    inspectionTarget.value = detail
    fillInspectionFormFromDetail(detail)
    inspectionPersistedAttachmentUrls.value = getAttachmentUrls(detail.inspectionAttachments)
  } else {
    traceabilityTarget.value = detail
    fillTraceabilityFormFromDetail(detail)
    traceabilityPersistedAttachmentUrls.value = getAttachmentUrls(detail.traceabilityAttachments)
  }
  if (meta) {
    applySceneIntegrationMeta(scene, detail, meta)
  }
  if (queryOnly) {
    applySceneSyncPreview(scene, normalizedPayload)
  }
}

const triggerSceneIntegrationSyncAction = async (scene: MaintenanceScene, queryOnly = false) => {
  const target = getSceneTarget(scene)
  if (!target) return
  const state = getSceneIntegrationState(scene)
  if (!state.configId) {
    ElMessage.warning('请选择同步方案')
    return
  }
  const externalNo = state.externalNo?.trim() || resolveSceneBusinessExternalNo(scene)
  if (!externalNo) {
    ElMessage.warning(`请填写${getSceneExternalNoLabel(scene)}`)
    return
  }
  state.externalNo = externalNo
  const syncingRef = getSceneSyncingRef(scene)
  syncingRef.value = true
  try {
    const payload: PurchaseOrderSceneIntegrationSyncPayload = {
      configId: state.configId,
      externalNo,
      queryOnly: queryOnly ? 1 : 0,
    }
    const res = await purchaseApi.triggerSceneIntegrationSync(scene, target.id, payload)
    await refreshSceneAfterSync(scene, target.id, res.data?.normalizedPayload, queryOnly)
    await refreshListAndStatistics()
    await refreshOpenDetailOrder(target.id)
    ElMessage.success(res.data?.message || '同步已完成')
  } catch (error) {
    console.warn(`执行采购订单${getSceneMaintainLabel(scene)}第三方同步失败:`, error)
  } finally {
    syncingRef.value = false
  }
}

const refreshOpenDetailOrder = async (id: number) => {
  if (detailVisible.value && detailOrder.value?.id === id) {
    detailOrder.value = await fetchDetail(id)
  }
}

const openLogisticsDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许维护物流信息')
    return
  }
  if (!canMaintainLogistics(row)) {
    ElMessage.warning('仅已审核或运输中状态可维护物流信息')
    return
  }
  detailLoading.value = true
  try {
    const [detail, inboundRecords] = await Promise.all([
      fetchDetail(row.id),
      fetchLinkedInboundRecords(row.id),
    ])
    logisticsTarget.value = detail
    logisticsLinkedInboundRecords.value = inboundRecords
    fillLogisticsFormFromDetail(detail)
    applySceneIntegrationMeta('logistics', detail, null)
    logisticsPersistedAttachmentUrls.value = getAttachmentUrls(detail.logisticsAttachments)
    logisticsUploadRef.value?.clearFiles()
    logisticsVisible.value = true
    void hydrateSceneIntegrationMeta('logistics', detail.id)
  } catch (error) {
    console.warn('打开采购订单物流维护弹窗失败:', error)
  } finally {
    detailLoading.value = false
  }
}

const closeLogisticsDialog = async () => {
  const attachments = logisticsTarget.value?.logisticsAttachments.map((attachment) => ({ ...attachment })) || []
  const persistedUrls = [...logisticsPersistedAttachmentUrls.value]
  logisticsVisible.value = false
  logisticsTarget.value = null
  logisticsLinkedInboundRecords.value = []
  logisticsPersistedAttachmentUrls.value = []
  resetLogisticsForm()
  resetSceneIntegrationState(logisticsIntegration)
  logisticsUploadRef.value?.clearFiles()
  await cleanupTemporaryMaintenanceAttachments(attachments, persistedUrls)
}

const openInspectionDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许维护检测报告')
    return
  }
  if (row.status === 'completed' && isInspectionResultConfirmed(row)) {
    ElMessage.warning('当前订单检测结果已确认，不可修改')
    return
  }
  if (!canMaintainInspection(row)) {
    ElMessage.warning('仅已审核、运输中、待入库或已完成状态可维护检测报告')
    return
  }
  detailLoading.value = true
  try {
    const detail = await fetchDetail(row.id)
    if (detail.status === 'completed' && isInspectionResultConfirmed(detail)) {
      ElMessage.warning('当前订单检测结果已确认，不可修改')
      return
    }
    inspectionTarget.value = detail
    fillInspectionFormFromDetail(detail)
    applySceneIntegrationMeta('inspection', detail, null)
    inspectionPersistedAttachmentUrls.value = getAttachmentUrls(detail.inspectionAttachments)
    inspectionUploadRef.value?.clearFiles()
    inspectionVisible.value = true
    void hydrateSceneIntegrationMeta('inspection', detail.id)
  } catch (error) {
    console.warn('打开采购订单检测维护弹窗失败:', error)
  } finally {
    detailLoading.value = false
  }
}

const closeInspectionDialog = async () => {
  const attachments = inspectionTarget.value?.inspectionAttachments.map((attachment) => ({ ...attachment })) || []
  const persistedUrls = [...inspectionPersistedAttachmentUrls.value]
  inspectionVisible.value = false
  inspectionTarget.value = null
  inspectionPersistedAttachmentUrls.value = []
  resetInspectionForm()
  resetSceneIntegrationState(inspectionIntegration)
  inspectionUploadRef.value?.clearFiles()
  await cleanupTemporaryMaintenanceAttachments(attachments, persistedUrls)
}

const openTraceabilityDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许维护溯源信息')
    return
  }
  if (row.status === 'completed' && isTraceabilityFilled(row)) {
    ElMessage.warning('已归档完成，不可修改')
    return
  }
  if (!canMaintainTraceability(row)) {
    ElMessage.warning('仅已审核、运输中、待入库或已完成状态可维护溯源信息')
    return
  }
  detailLoading.value = true
  try {
    const detail = await fetchDetail(row.id)
    if (detail.status === 'completed' && isTraceabilityFilled(detail)) {
      ElMessage.warning('已归档完成，不可修改')
      return
    }
    traceabilityTarget.value = detail
    fillTraceabilityFormFromDetail(detail)
    applySceneIntegrationMeta('traceability', detail, null)
    traceabilityPersistedAttachmentUrls.value = getAttachmentUrls(detail.traceabilityAttachments)
    traceabilityUploadRef.value?.clearFiles()
    traceabilityVisible.value = true
    void hydrateSceneIntegrationMeta('traceability', detail.id)
  } catch (error) {
    console.warn('打开采购订单溯源维护弹窗失败:', error)
  } finally {
    detailLoading.value = false
  }
}

const closeTraceabilityDialog = async () => {
  const attachments = traceabilityTarget.value?.traceabilityAttachments.map((attachment) => ({ ...attachment })) || []
  const persistedUrls = [...traceabilityPersistedAttachmentUrls.value]
  traceabilityVisible.value = false
  traceabilityTarget.value = null
  traceabilityPersistedAttachmentUrls.value = []
  resetTraceabilityForm()
  resetSceneIntegrationState(traceabilityIntegration)
  traceabilityUploadRef.value?.clearFiles()
  await cleanupTemporaryMaintenanceAttachments(attachments, persistedUrls)
}

const handleSupplierChange = (value: number | null) => {
  const supplier = getSupplierById(value)
  formState.value.supplierName = supplier?.name || ''
}

const handleRelatedPlansChange = async () => {
  await loadPlanItems()
}

const addManualItemRow = () => {
  formState.value.items.push(createManualItem())
  syncOrderTotal(formState.value)
}

const removeItemRow = (index: number) => {
  const target = formState.value.items[index]
  if (!target || target.sourceType !== 'manual') {
    return
  }
  const manualCount = formState.value.items.filter((item) => item.sourceType === 'manual').length
  const planCount = formState.value.items.filter((item) => item.sourceType === 'plan').length
  if (manualCount === 1 && planCount === 0) {
    ElMessage.warning('至少保留一条物料明细')
    return
  }
  formState.value.items.splice(index, 1)
  ensureAtLeastOneManualRow()
  syncOrderTotal(formState.value)
}

const handleManualMaterialChange = (item: PurchaseOrderFormItem) => {
  const material = getMaterialById(item.materialId)
  item.materialName = material?.name || ''
  item.spec = material?.spec || ''
  item.unit = material?.unit || ''
  item.unitPrice = material ? roundToCurrency(Number(material.referencePrice || 0)) : null
  item.unitCost = item.unitPrice
  syncItemDerivedFields(item)
  syncOrderTotal(formState.value)
}

const validateForm = () => {
  if (!formState.value.orgId) {
    ElMessage.warning('当前用户缺少所属组织信息')
    return false
  }
  if (!formState.value.supplierId) {
    ElMessage.warning('请选择供应商')
    return false
  }
  if (!formState.value.orderDate) {
    ElMessage.warning('请选择订单日期')
    return false
  }
  if (!formState.value.expectedArrival) {
    ElMessage.warning('请选择预计到货日期')
    return false
  }
  if (formState.value.expectedArrival < formState.value.orderDate) {
    ElMessage.warning('预计到货日期不能早于订单日期')
    return false
  }

  const candidateItems = formState.value.items.filter((item) => {
    return item.materialId && Number(item.quantity || 0) > 0
  })
  if (!candidateItems.length) {
    ElMessage.warning('请至少填写一条有效的物料明细')
    return false
  }

  const invalidItem = candidateItems.find((item) => {
    if (!item.materialId) return true
    if (!item.quantity || item.quantity <= 0) return true
    if (item.unitPrice === null || item.unitPrice === undefined || item.unitPrice < 0) return true
    if (item.sourceType === 'plan' && item.quantity > item.remainingQuantity) return true
    return false
  })
  if (invalidItem) {
    ElMessage.warning('请检查物料数量、单价及计划关联数量限制')
    return false
  }

  return true
}

const buildPayload = (status: Extract<PurchaseOrderStatus, 'pending_submit' | 'pending_approve'>): PurchaseOrderFormPayload => {
  syncOrderTotal(formState.value)
  return {
    orderNo: formState.value.orderNo || undefined,
    orgId: Number(formState.value.orgId),
    supplierId: Number(formState.value.supplierId),
    orderDate: formState.value.orderDate,
    expectedArrival: formState.value.expectedArrival,
    attachmentName: attachmentFile.value ? undefined : formState.value.attachmentName,
    attachmentUrl: attachmentFile.value ? undefined : formState.value.attachmentUrl,
    remark: formState.value.remark.trim(),
    clearAttachment: attachmentMarkedForDeletion.value && !attachmentFile.value ? true : undefined,
    status,
    relatedPlanIds: formState.value.relatedPlanIds.length ? [...formState.value.relatedPlanIds] : undefined,
    items: formState.value.items
      .filter((item) => item.materialId && Number(item.quantity || 0) > 0)
      .map((item) => ({
        planItemId: item.planItemId || undefined,
        materialId: Number(item.materialId),
        spec: item.spec || undefined,
        quantity: roundToQuantity(Number(item.quantity || 0)),
        unitPrice: roundToCurrency(Number(item.unitPrice || 0)),
        remark: item.remark.trim() || undefined,
      })),
  }
}

const saveForm = async (status: Extract<PurchaseOrderStatus, 'pending_submit' | 'pending_approve'>) => {
  if (!validateForm()) return

  formSubmitting.value = true
  try {
    const payload = buildPayload(status)
    if (formMode.value === 'create') {
      await purchaseApi.create(payload, attachmentFile.value)
      ElMessage.success(status === 'pending_approve' ? '采购订单已提交审核' : '采购订单草稿已保存')
      attachmentFile.value = null
      formVisible.value = false
      await refreshListAndStatistics(1)
      return
    }

    if (!formState.value.id) return
    await purchaseApi.update(formState.value.id, payload, attachmentFile.value)
    ElMessage.success(status === 'pending_approve' ? '采购订单已保存并提交审核' : '采购订单修改已保存')
    attachmentFile.value = null
    formVisible.value = false
    await refreshListAndStatistics()
  } finally {
    formSubmitting.value = false
  }
}

const submitAudit = async (status: Extract<PurchaseOrderStatus, 'approved' | 'rejected'>) => {
  if (!auditTarget.value) return
  auditSubmitting.value = true
  try {
    await purchaseApi.audit(auditTarget.value.id, {
      status,
      remark: auditForm.remark.trim() || undefined,
    })
    closeAuditDialog()
    await refreshListAndStatistics()
    ElMessage.success(status === 'approved' ? '采购订单审核通过' : '采购订单已驳回')
  } finally {
    auditSubmitting.value = false
  }
}

const approveOrder = async () => {
  await submitAudit('approved')
}

const rejectOrder = async () => {
  await submitAudit('rejected')
}

const submitReverseAudit = async () => {
  if (!reverseTarget.value) return
  if (!reverseForm.reason.trim()) {
    ElMessage.warning('请填写反审核原因')
    return
  }

  reverseSubmitting.value = true
  try {
    const targetId = reverseTarget.value.id
    const res = await purchaseApi.reverseAudit(targetId, {
      reason: reverseForm.reason.trim(),
    })
    closeReverseAuditDialog()
    await refreshListAndStatistics()
    if (detailVisible.value && detailOrder.value?.id === targetId) {
      const [detail, records] = await Promise.all([
        fetchDetail(targetId),
        fetchLinkedInboundRecords(targetId),
      ])
      detailOrder.value = detail
      linkedInboundRecords.value = records
    }
    ElMessage.success(
      Number(res.data?.affectedInboundCount || 0) > 0
        ? '反审核成功，采购订单已退回待审核；关联入库单状态、审批任务及关联关系保持不变。'
        : '反审核成功，采购订单已退回待审核'
    )
  } finally {
    reverseSubmitting.value = false
  }
}

const submitVoidApply = async () => {
  if (!voidTarget.value) return
  if (!voidForm.reason.trim()) {
    ElMessage.warning('请输入作废原因')
    return
  }
  voidSubmitting.value = true
  try {
    await purchaseApi.applyVoid(voidTarget.value.id, {
      reason: voidForm.reason.trim(),
    })
    closeVoidDialog()
    await refreshListAndStatistics()
    ElMessage.success('采购订单作废申请已提交')
  } finally {
    voidSubmitting.value = false
  }
}

const submitVoidAudit = async (approved: boolean) => {
  if (!voidAuditTarget.value) return
  voidAuditSubmitting.value = true
  try {
    await purchaseApi.auditVoid(voidAuditTarget.value.id, {
      approved,
      remark: voidAuditForm.remark.trim() || undefined,
    })
    closeVoidAuditDialog()
    await refreshListAndStatistics()
    ElMessage.success(approved ? '采购订单已作废' : '作废申请已驳回，订单已恢复为作废前状态')
  } finally {
    voidAuditSubmitting.value = false
  }
}

const submitLogistics = async () => {
  if (!logisticsTarget.value) return
  if (!validateThirdPartySceneSave('logistics')) return
  if (logisticsStatusLocked.value && logisticsForm.logisticsStatus !== logisticsTarget.value.logisticsStatus) {
    ElMessage.warning(logisticsStatusLockReason.value || LOGISTICS_STATUS_LOCK_MESSAGE)
    return
  }
  if (!validateLogisticsRequiredFields()) return
  if (
    logisticsForm.shippedAt
    && logisticsForm.arrivedAt
    && logisticsForm.arrivedAt < logisticsForm.shippedAt
  ) {
    ElMessage.warning('发货时间不能晚于到货时间')
    return
  }
  if (isFutureArrivedAt(logisticsForm.arrivedAt)) {
    ElMessage.warning('到货时间不能晚于今天')
    return
  }

  logisticsSubmitting.value = true
  try {
    const shouldMoveToPendingReceipt = Boolean(logisticsForm.arrivedAt) || logisticsForm.logisticsStatus === 'arrived'
    await purchaseApi.updateLogistics(logisticsTarget.value.id, {
      company: logisticsForm.company?.trim() || undefined,
      trackingNo: logisticsForm.trackingNo?.trim() || undefined,
      logisticsStatus: logisticsForm.logisticsStatus,
      shippedAt: logisticsForm.shippedAt || undefined,
      arrivedAt: logisticsForm.arrivedAt || undefined,
      remark: logisticsForm.remark?.trim() || undefined,
      sourceType: logisticsForm.sourceType || 'manual',
      syncPayload: logisticsForm.syncPayload?.trim() || undefined,
      integrationConfigId: logisticsForm.sourceType === 'third_party' ? logisticsIntegration.configId || undefined : undefined,
      integrationExternalNo: logisticsForm.sourceType === 'third_party' ? logisticsIntegration.externalNo?.trim() || undefined : undefined,
      attachments: buildMaintenanceAttachmentPayload(logisticsTarget.value.logisticsAttachments),
    })
    const orderId = logisticsTarget.value.id
    logisticsPersistedAttachmentUrls.value = getAttachmentUrls(logisticsTarget.value.logisticsAttachments)
    closeLogisticsDialog()
    await refreshListAndStatistics()
    await refreshOpenDetailOrder(orderId)
    ElMessage.success(shouldMoveToPendingReceipt ? '物流已更新，订单已流转为待入库' : '物流信息已保存')
  } finally {
    logisticsSubmitting.value = false
  }
}

const submitInspection = async () => {
  if (!inspectionTarget.value) return
  if (!validateThirdPartySceneSave('inspection')) return
  if (!validateInspectionRequiredFields()) return
  inspectionSubmitting.value = true
  try {
    await purchaseApi.updateInspection(inspectionTarget.value.id, {
      reportNo: inspectionForm.reportNo?.trim() || undefined,
      result: inspectionForm.result?.trim() || undefined,
      agency: inspectionForm.agency?.trim() || undefined,
      inspectedAt: inspectionForm.inspectedAt || undefined,
      remark: inspectionForm.remark?.trim() || undefined,
      sourceType: inspectionForm.sourceType || 'manual',
      syncPayload: inspectionForm.syncPayload?.trim() || undefined,
      integrationConfigId: inspectionForm.sourceType === 'third_party' ? inspectionIntegration.configId || undefined : undefined,
      integrationExternalNo: inspectionForm.sourceType === 'third_party' ? inspectionIntegration.externalNo?.trim() || undefined : undefined,
      attachments: buildMaintenanceAttachmentPayload(inspectionTarget.value.inspectionAttachments),
    })
    const orderId = inspectionTarget.value.id
    inspectionPersistedAttachmentUrls.value = getAttachmentUrls(inspectionTarget.value.inspectionAttachments)
    closeInspectionDialog()
    await refreshListAndStatistics()
    await refreshOpenDetailOrder(orderId)
    ElMessage.success('检测报告信息已保存')
  } finally {
    inspectionSubmitting.value = false
  }
}

const submitTraceability = async () => {
  if (!traceabilityTarget.value) return
  if (!validateThirdPartySceneSave('traceability')) return
  if (!validateTraceabilityRequiredFields()) return
  traceabilitySubmitting.value = true
  try {
    await purchaseApi.updateTraceability(traceabilityTarget.value.id, {
      traceBatchId: traceabilityForm.traceBatchId?.trim() || undefined,
      origin: traceabilityForm.origin?.trim() || undefined,
      remark: traceabilityForm.remark?.trim() || undefined,
      sourceType: traceabilityForm.sourceType || 'manual',
      syncPayload: traceabilityForm.syncPayload?.trim() || undefined,
      integrationConfigId: traceabilityForm.sourceType === 'third_party' ? traceabilityIntegration.configId || undefined : undefined,
      integrationExternalNo: traceabilityForm.sourceType === 'third_party' ? traceabilityIntegration.externalNo?.trim() || undefined : undefined,
      attachments: buildMaintenanceAttachmentPayload(traceabilityTarget.value.traceabilityAttachments),
    })
    const orderId = traceabilityTarget.value.id
    traceabilityPersistedAttachmentUrls.value = getAttachmentUrls(traceabilityTarget.value.traceabilityAttachments)
    closeTraceabilityDialog()
    await refreshListAndStatistics()
    await refreshOpenDetailOrder(orderId)
    ElMessage.success('溯源信息已保存')
  } finally {
    traceabilitySubmitting.value = false
  }
}

// ---- 关联生成入库单 ----
const openGenerateInboundDialog = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不允许关联生成入库单')
    return
  }
  if (!canGenerateInbound(row)) {
    ElMessage.warning('只有"待入库"状态的采购订单才能关联生成入库单')
    return
  }
  const [detailRes, sourceItemsRes] = await Promise.all([
    purchaseApi.getDetail(row.id),
    inboundApi.getSourceOrderItems(row.id),
  ])
  if (detailRes.code !== 'SUCCESS' || !detailRes.data) {
    ElMessage.error('获取采购订单详情失败')
    return
  }
  if (sourceItemsRes.code !== 'SUCCESS') {
    ElMessage.error('获取采购订单可入库物料失败')
    return
  }
  const detail = detailRes.data
  generateInboundTarget.value = detail as PurchaseOrderRecord
  generateInboundForm.orgId = detail.orgId ?? null
  generateInboundForm.orgName = (detail as any).orgName || ''
  generateInboundForm.remark = ''
  generateInboundForm.items = (sourceItemsRes.data || []).map((item: any) => ({
    materialId: item.materialId ?? 0,
    materialName: item.materialName || '',
    spec: item.spec || '',
    unit: item.unit || '',
    orderQty: roundToQuantity(item.orderQty ?? 0),
    inboundQty: roundToQuantity(item.inboundQty ?? 0),
    remainingInboundQty: roundToQuantity(item.remainingInboundQty ?? 0),
    warehouseId: null as number | null,
    locationId: null as number | null,
    quantity: roundToQuantity(item.remainingInboundQty ?? 0),
    unitCost: item.unitPrice ?? null,
    batchNo: '',
    productionDate: '',
    _locations: [] as Location[],
    selected: true,
  }))
  if (generateInboundForm.items.length === 0) {
    ElMessage.warning('当前采购订单所有物料已全部入库，无需再生成入库单')
    return
  }
  // 加载仓库列表和组织树
  const loadTasks: Promise<void>[] = []
  if (orgStore.treeData.length === 0) {
    loadTasks.push(orgStore.fetchTree().then(() => {}))
  }
  loadTasks.push(loadGenerateInboundWarehouses(generateInboundForm.orgId))
  await Promise.all(loadTasks)
  generateInboundVisible.value = true
}

const closeGenerateInboundDialog = () => {
  generateInboundVisible.value = false
  generateInboundTarget.value = null
}

const submitGenerateInbound = async () => {
  const selectedItems = generateInboundForm.items.filter((i) => i.selected)
  if (!generateInboundForm.orgId) {
    ElMessage.warning('请选择入库组织')
    return
  }
  if (selectedItems.length === 0) {
    ElMessage.warning('请至少选择一条物料')
    return
  }
  // 校验选中项必填字段（仅校验数量 > 0 的物料，数量为 0 的表示本次不入库）
  for (const item of selectedItems) {
    if (!item.quantity || item.quantity <= 0) {
      continue
    }
    if (!item.warehouseId) {
      ElMessage.warning(`物料"${item.materialName}"请选择入库仓库`)
      return
    }
    if (!item.locationId) {
      ElMessage.warning(`物料"${item.materialName}"请选择仓位`)
      return
    }
    if (isFutureProductionDate(item.productionDate)) {
      ElMessage.warning(`物料"${item.materialName}"的生产日期不能晚于今天`)
      return
    }
  }
  // 过滤掉数量为 0 的物料，只提交数量 > 0 的物料
  const validItems = selectedItems.filter((i) => i.quantity && i.quantity > 0)
  if (validItems.length === 0) {
    ElMessage.warning('所有选中物料的入库数量均为0，请至少填写一条数量大于0的物料')
    return
  }
  const exceededRow = validItems.find((item) => getGenerateInboundQuantityExceededMessage(item))
  if (exceededRow) {
    ElMessage.warning(getGenerateInboundQuantityExceededMessage(exceededRow))
    return
  }
  const target = generateInboundTarget.value
  if (!target) return

  generateInboundSubmitting.value = true
  try {
    const payload = {
      orgId: generateInboundForm.orgId,
      orgName: generateInboundForm.orgName,
      sourceType: 'purchase',
      sourceOrderId: target.id,
      sourceOrderNo: target.orderNo,
      supplierId: target.supplierId,
      supplierName: target.supplierName,
      remark: generateInboundForm.remark || `由采购订单${target.orderNo}关联生成`,
      items: generateInboundForm.items
        .map((item, index) => ({ item, index }))
        .filter(({ item }) => item.selected && item.quantity && item.quantity > 0)
        .map(({ item, index }) => ({
        lineKey: String(index),
        materialId: item.materialId,
        materialName: item.materialName,
        spec: item.spec,
        unit: item.unit,
        warehouseId: item.warehouseId,
        locationId: item.locationId,
        quantity: item.quantity,
        unitCost: item.unitCost,
        batchNo: item.batchNo,
        productionDate: item.productionDate,
      })),
    }
    const res = await inboundApi.create(payload)
    if (res.code === 'SUCCESS') {
      ElMessage.success('入库单已创建成功')
      closeGenerateInboundDialog()
      await refreshListAndStatistics(currentPage.value)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成入库单失败')
  } finally {
    generateInboundSubmitting.value = false
  }
}

const deleteOrder = async (row: PurchaseOrderRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购订单不可重复删除')
    return
  }
  if (!hasDeleteAuthority(row)) {
    ElMessage.warning('当前用户无采购订单删除权限')
    return
  }
  if (!canDelete(row)) {
    ElMessage.warning('仅草稿或待审核状态的采购订单允许删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除采购订单“${row.orderNo}”吗？`,
      '删除提示',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
      }
    )
  } catch {
    return
  }

  await purchaseApi.delete(row.id)
  const nextPage = purchaseOrders.value.length === 1 && currentPage.value > 1 ? currentPage.value - 1 : currentPage.value
  await refreshListAndStatistics(nextPage)
  ElMessage.success('采购订单已删除')
}

const handleMoreAction = async (row: PurchaseOrderRecord, command: PurchaseOrderMoreAction) => {
  switch (command) {
    case 'reverseAudit':
      await openReverseAuditDialog(row)
      break
    case 'void':
      await openVoidDialog(row)
      break
    case 'voidAudit':
      await openVoidAuditDialog(row)
      break
    case 'logistics':
      await openLogisticsDialog(row)
      break
    case 'inspection':
      await openInspectionDialog(row)
      break
    case 'traceability':
      await openTraceabilityDialog(row)
      break
    case 'generateInbound':
      await openGenerateInboundDialog(row)
      break
    case 'delete':
      await deleteOrder(row)
      break
  }
}

const handlePageChange = async (page: number) => {
  currentPage.value = page
  await fetchList(page)
}

const handleSizeChange = async (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  await fetchList(1)
}

watch(
  () => formState.value.items,
  () => {
    syncOrderTotal(formState.value)
  },
  { deep: true }
)

onMounted(async () => {
  formState.value.orgId = getDefaultOrgId()
  formState.value.orgName = getDefaultOrgName()
  await refreshListOnReturnIfNeeded()
  const pendingOrderId = purchaseOrderStore.consumePendingEditOrderId()
  if (pendingOrderId) {
    await openEditDialogById(pendingOrderId)
  }
  await consumeDashboardDrillDown()
})

onActivated(async () => {
  if (!purchaseOrderActivatedOnce.value) {
    purchaseOrderActivatedOnce.value = true
  }
  await refreshListAndStatistics(currentPage.value)
  const pendingOrderId = purchaseOrderStore.consumePendingEditOrderId()
  if (pendingOrderId) {
    await openEditDialogById(pendingOrderId)
  }
  await consumeDashboardDrillDown()
})
</script>

<template>
  <div class="purchase-order-page">
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-icon total">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.total }}</div>
          <div class="stat-label">采购订单总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon pending">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.pending }}</div>
          <div class="stat-label">待审核订单</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon approved">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.approved }}</div>
          <div class="stat-label">已审核订单</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amount">
          <el-icon><Money /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value amount-text">{{ formatCurrency(statistics.totalAmount) }}</div>
          <div class="stat-label">订单总金额</div>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-grid">
        <div class="toolbar-field toolbar-search-field">
          <el-input
            class="toolbar-control toolbar-search-input"
            v-model="searchForm.keyword"
            placeholder="请输入订单编号/供应商名称"
            clearable
            @keyup.enter="applySearch"
          />
        </div>
        <div class="toolbar-field">
          <el-select
            class="toolbar-control"
            v-model="searchForm.status"
            placeholder="订单状态"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="option in STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
        <div class="toolbar-field toolbar-date-field">
          <el-date-picker
            class="toolbar-control toolbar-date-picker"
            v-model="searchForm.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="订单日期起"
            end-placeholder="订单日期止"
            @clear="handleDateRangeClear"
            style="width: 100%"
          />
        </div>
        <div class="toolbar-actions">
          <el-button class="toolbar-button" type="primary" :icon="Search" @click="applySearch">搜索</el-button>
          <el-button class="toolbar-button" :icon="RefreshRight" @click="resetSearch">重置</el-button>
          <el-button
            class="toolbar-button"
            type="primary"
            :icon="Plus"
            v-permission="PURCHASE_PERMISSIONS.CREATE"
            @click="openCreateDialog"
          >
            新增采购订单
          </el-button>
        </div>
      </div>
    </div>

    <div v-loading="tableLoading" class="table-wrapper">
      <div class="table-header">
        <div>
          <div class="table-title">采购订单列表</div>
          <div class="table-subtitle">列表、筛选、详情、审核与增删改均通过网关实时访问后端与远程数据库。</div>
        </div>
        <div class="table-summary">
          当前结果 {{ total }} 条
        </div>
      </div>

      <el-table :data="purchaseOrders" stripe border empty-text="当前筛选条件下暂无采购订单">
        <el-table-column prop="orderNo" label="订单编号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column prop="orderDate" label="订单日期" width="120" />
        <el-table-column prop="expectedArrival" label="预计到货" width="120" />
        <el-table-column prop="orgName" label="所属组织" width="120" show-overflow-tooltip />
        <el-table-column label="订单金额" width="130" align="right">
          <template #default="{ row }">
            {{ formatCurrency(row.totalAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <div class="status-tags">
              <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
              <el-tag v-if="row.deleted" type="info">已删除</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="buyerName" label="采购员" width="100" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="360" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <div class="action-buttons__primary">
                <el-button link type="primary" :icon="View" @click="openDetailDialog(row)">详情</el-button>
                <el-button
                  link
                  type="primary"
                  :icon="EditPen"
                  v-permission="PURCHASE_PERMISSIONS.EDIT"
                  :disabled="!canEdit(row)"
                  @click="openEditDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  link
                  type="warning"
                  :icon="CircleCheck"
                  v-permission="PURCHASE_PERMISSIONS.APPROVE"
                  :disabled="!canAudit(row)"
                  @click="openAuditDialog(row)"
                >
                  审核
                </el-button>
              </div>
              <el-dropdown
                v-if="hasMoreActionItems(row)"
                trigger="click"
                @command="(command) => handleMoreAction(row, command as PurchaseOrderMoreAction)"
              >
                <el-button link type="primary" class="action-buttons__more">
                  更多操作
                  <el-icon class="action-buttons__more-icon"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="item in getMoreActionItems(row)"
                      :key="item.command"
                      :command="item.command"
                      :disabled="item.disabled"
                    >
                      {{ item.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

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

    <el-dialog
      v-model="formVisible"
      :title="formDialogTitle"
      width="1280px"
      destroy-on-close
    >
      <el-form v-loading="formSubmitting || formOptionLoading" label-width="100px" class="order-form">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <div class="form-grid">
            <el-form-item label="订单编号">
              <el-input v-model="formState.orderNo" readonly />
            </el-form-item>
            <el-form-item label="供应商" required>
              <el-select
                v-model="formState.supplierId"
                filterable
                clearable
                placeholder="请选择供应商"
                style="width: 100%"
                @change="handleSupplierChange"
              >
                <el-option
                  v-for="supplier in supplierOptions"
                  :key="supplier.id"
                  :label="supplier.name"
                  :value="supplier.id"
                  :disabled="supplier.disabled"
                />
              </el-select>
              <div class="form-field-tip">{{ selectedSupplierHint }}</div>
            </el-form-item>
            <el-form-item label="订单日期" required>
              <el-date-picker
                v-model="formState.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                placeholder="请选择订单日期"
              />
            </el-form-item>
            <el-form-item label="预计到货" required>
              <el-date-picker
                v-model="formState.expectedArrival"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                placeholder="请选择预计到货日期"
              />
            </el-form-item>
            <el-form-item label="关联单据" class="form-item-full">
              <el-select
                v-model="formState.relatedPlanIds"
                multiple
                filterable
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择采购计划单，可独立于供应商先行选择"
                style="width: 100%"
                @change="handleRelatedPlansChange"
              >
                <el-option
                  v-for="plan in planOptions"
                  :key="plan.id"
                  :label="`${plan.planNo}｜${plan.planName}${plan.orgName ? `｜${plan.orgName}` : ''}`"
                  :value="plan.id"
                >
                  <div class="plan-option">
                    <span>{{ plan.planNo }}｜{{ plan.planName }}<template v-if="plan.orgName">｜{{ plan.orgName }}</template></span>
                    <span>剩余 {{ plan.remainingQuantity }}</span>
                  </div>
                </el-option>
              </el-select>
              <div class="form-field-tip">{{ selectedRelatedPlanHint }}</div>
            </el-form-item>
            <el-form-item label="所属组织">
              <el-input v-model="formState.orgName" readonly />
            </el-form-item>
            <el-form-item label="采购员">
              <el-input v-model="formState.buyerName" readonly />
            </el-form-item>
            <el-form-item label="附件" class="form-item-full">
              <div class="attachment-field">
                <el-upload
                  ref="attachmentUploadRef"
                  :auto-upload="false"
                  :show-file-list="false"
                  :limit="1"
                  :on-change="handleAttachmentChange"
                  :on-exceed="handleAttachmentExceed"
                >
                  <el-button type="primary" plain>选择附件</el-button>
                </el-upload>
                <div class="attachment-field-tip">保存时上传到 MinIO；重新选择文件并保存可覆盖当前附件，单个文件不超过10MB。</div>
                <div v-if="currentAttachmentDisplayName" class="attachment-card">
                  <div class="attachment-meta">
                    <span class="attachment-name">{{ currentAttachmentDisplayName }}</span>
                    <span class="attachment-status">{{ attachmentFile ? '待保存覆盖' : '当前附件' }}</span>
                  </div>
                  <div class="attachment-actions">
                    <el-button
                      v-if="!attachmentFile && formState.attachmentUrl"
                      link
                      type="primary"
                      @click="previewAttachment(formState.attachmentUrl)"
                    >
                      在线查看
                    </el-button>
                    <el-button
                      v-if="!attachmentFile && formState.id && formState.attachmentUrl"
                      link
                      type="primary"
                      @click="downloadAttachment(formState.id)"
                    >
                      下载
                    </el-button>
                    <el-button
                      v-if="!attachmentFile && formMode === 'edit' && formState.attachmentUrl"
                      link
                      type="danger"
                      @click="removeCurrentAttachment"
                    >
                      删除
                    </el-button>
                    <el-button
                      v-if="attachmentFile"
                      link
                      type="primary"
                      @click="previewPendingAttachment(attachmentFile)"
                    >
                      在线预览
                    </el-button>
                    <el-button
                      v-if="attachmentFile"
                      link
                      type="danger"
                      @click="clearPendingAttachment"
                    >
                      删除
                    </el-button>
                  </div>
                </div>
                <div v-else class="attachment-empty">暂未上传附件</div>
              </div>
            </el-form-item>
            <el-form-item label="备注" class="form-item-full">
              <el-input
                v-model="formState.remark"
                type="textarea"
                :rows="3"
                placeholder="请输入采购备注"
              />
            </el-form-item>
          </div>
        </div>

        <div class="form-section">
          <div class="section-title item-header">
            <span>物料明细</span>
            <div class="item-summary">
              <span>订单合计：</span>
              <strong>{{ formatCurrency(formState.totalAmount) }}</strong>
            </div>
          </div>

          <div class="item-table-scroll">
            <div class="item-list">
              <div class="item-row item-row-header">
                <div class="item-header-cell">序号</div>
                <div class="item-header-cell">来源</div>
                <div class="item-header-cell">物料名称</div>
                <div class="item-header-cell">规格</div>
                <div class="item-header-cell">单位</div>
                <div class="item-header-cell align-right">数量</div>
                <div class="item-header-cell align-right">单价</div>
                <div class="item-header-cell align-right">小计</div>
                <div class="item-header-cell">备注</div>
                <div class="item-header-cell align-center">操作</div>
              </div>
              <div
                v-for="(item, index) in formState.items"
                :key="item.rowId"
                class="item-row"
              >
                <div class="item-index">#{{ index + 1 }}</div>
                <div class="item-source">
                  <el-tag :type="item.sourceType === 'plan' ? 'warning' : 'info'" size="small">
                    {{ item.sourceType === 'plan' ? '采购计划' : '手工录入' }}
                  </el-tag>
                  <div v-if="item.sourceType === 'plan'" class="item-source-meta">
                    {{ item.planNo }}<template v-if="item.planOrgName">｜{{ item.planOrgName }}</template>
                  </div>
                </div>
                <template v-if="item.sourceType === 'plan'">
                  <div class="readonly-cell">{{ item.materialName }}</div>
                  <div class="readonly-cell">{{ item.spec || '—' }}</div>
                  <div class="readonly-cell">{{ item.unit || '—' }}</div>
                </template>
                <template v-else>
                  <el-select
                    v-model="item.materialId"
                    filterable
                    clearable
                    placeholder="请选择物料"
                    @change="handleManualMaterialChange(item)"
                  >
                    <el-option
                      v-for="material in materialOptions"
                      :key="material.id"
                      :label="material.name"
                      :value="material.id"
                    />
                  </el-select>
                  <el-select
                    v-model="item.spec"
                    filterable
                    clearable
                    placeholder="请选择规格"
                    :no-data-text="item.materialId ? '暂无规格选项' : '请先选择物料'"
                  >
                    <el-option
                      v-for="spec in getMaterialSpecs(item.materialId)"
                      :key="spec"
                      :label="spec"
                      :value="spec"
                    />
                  </el-select>
                  <el-input v-model="item.unit" readonly placeholder="单位" />
                </template>
                <div class="quantity-cell">
                  <el-input-number
                    v-model="item.quantity"
                    :min="0"
                    :max="item.sourceType === 'plan' ? item.remainingQuantity : undefined"
                    :step="0.001"
                    :precision="3"
                    style="width: 100%"
                    placeholder="数量"
                  />
                  <div v-if="item.sourceType === 'plan'" class="quantity-tip">
                    可关联 {{ item.remainingQuantity }} / 计划 {{ item.planQuantity }}
                  </div>
                </div>
                <el-input-number
                  v-model="item.unitPrice"
                  :min="0"
                  :step="0.01"
                  :precision="2"
                  style="width: 100%"
                  placeholder="单价"
                />
                <div class="subtotal-cell">{{ formatCurrency(item.subtotal) }}</div>
                <el-input
                  v-model="item.remark"
                  type="textarea"
                  :autosize="{ minRows: 2, maxRows: 4 }"
                  resize="none"
                  placeholder="备注"
                />
                <div class="item-action">
                  <el-button
                    v-if="item.sourceType === 'manual'"
                    text
                    type="danger"
                    @click="removeItemRow(index)"
                  >
                    删除
                  </el-button>
                  <span v-else class="item-action-placeholder">计划项</span>
                </div>
              </div>
            </div>
          </div>

          <el-button class="add-item-button" @click="addManualItemRow">+ 添加手工物料</el-button>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button :loading="formSubmitting" @click="saveForm('pending_submit')">保存草稿</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="saveForm('pending_approve')">
          {{ formMode === 'create' ? '提交审核' : '保存并提交' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="采购订单详情"
      width="1080px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detailOrder">
          <div class="detail-section">
            <div class="detail-section-title">基本信息</div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">订单编号</span>
                <span class="detail-value">{{ detailOrder.orderNo }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">订单状态</span>
                <span class="detail-value">
                  <el-tag :type="getStatusType(detailOrder.status)">
                    {{ getStatusLabel(detailOrder.status) }}
                  </el-tag>
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">供应商</span>
                <span class="detail-value">{{ detailOrder.supplierName }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">采购员</span>
                <span class="detail-value">{{ detailOrder.buyerName }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">创建人</span>
                <span class="detail-value">{{ detailOrder.createdBy }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">所属组织</span>
                <span class="detail-value">{{ detailOrder.orgName }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">订单金额</span>
                <span class="detail-value amount">{{ formatCurrency(detailOrder.totalAmount) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">订单日期</span>
                <span class="detail-value">{{ detailOrder.orderDate }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">预计到货</span>
                <span class="detail-value">{{ detailOrder.expectedArrival || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">审核人</span>
                <span class="detail-value">{{ detailOrder.auditBy || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">审核时间</span>
                <span class="detail-value">{{ detailOrder.auditAt || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">关联计划</span>
                <span class="detail-value">
                  {{
                    detailOrder.relatedPlans.length
                      ? detailOrder.relatedPlans.map((item) => `${item.planNo}｜${item.planName}${item.orgName ? `｜${item.orgName}` : ''}`).join('、')
                      : '—'
                  }}
                </span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">审核意见</span>
                <span class="detail-value">{{ detailOrder.auditRemark || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">备注</span>
                <span class="detail-value">{{ detailOrder.remark || '—' }}</span>
              </div>
            </div>
          </div>

          <div v-if="showVoidDetail(detailOrder)" class="detail-section">
            <div class="detail-section-title">作废流程</div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">作废原因</span>
                <span class="detail-value">{{ detailOrder.voidReason || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">申请人</span>
                <span class="detail-value">{{ detailOrder.voidRequestedBy || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">申请时间</span>
                <span class="detail-value">{{ detailOrder.voidRequestedAt || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">作废审核人</span>
                <span class="detail-value">{{ detailOrder.voidAuditBy || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">作废审核时间</span>
                <span class="detail-value">{{ detailOrder.voidAuditAt || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">作废审核意见</span>
                <span class="detail-value">{{ detailOrder.voidAuditRemark || '—' }}</span>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">物流追踪</div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">物流公司</span>
                <span class="detail-value">{{ detailOrder.logisticsCompany || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">物流单号</span>
                <span class="detail-value">{{ detailOrder.logisticsTrackingNo || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">物流状态</span>
                <span class="detail-value">
                  {{ detailOrder.logisticsStatus ? LOGISTICS_STATUS_OPTIONS.find((item) => item.value === detailOrder.logisticsStatus)?.label || detailOrder.logisticsStatus : '—' }}
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">数据来源</span>
                <span class="detail-value">{{ detailOrder.logisticsSourceType === 'third_party' ? '第三方接口' : detailOrder.logisticsSourceType ? '手工录入' : '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">发货时间</span>
                <span class="detail-value">{{ detailOrder.shippedAt || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">到货时间</span>
                <span class="detail-value">{{ detailOrder.arrivedAt || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">物流备注</span>
                <span class="detail-value">{{ detailOrder.logisticsRemark || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">同步数据</span>
                <span class="detail-value">{{ detailOrder.logisticsSyncPayload || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">物流附件</span>
                <div class="detail-value detail-attachment-value">
                  <div v-if="detailOrder.logisticsAttachments.length" class="attachment-list">
                    <div
                      v-for="(attachment, index) in detailOrder.logisticsAttachments"
                      :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
                      class="attachment-card"
                    >
                      <div class="attachment-meta">
                        <span class="attachment-name">{{ attachment.name || '物流附件' }}</span>
                        <span class="attachment-status">
                          已上传
                          <template v-if="attachment.size"> · {{ attachment.size }}</template>
                        </span>
                      </div>
                      <div class="attachment-actions">
                        <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                        <el-button link type="primary" @click="downloadStoredAttachment(attachment)">下载</el-button>
                      </div>
                    </div>
                  </div>
                  <span v-else>暂无附件</span>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">检测报告</div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">报告编号</span>
                <span class="detail-value">{{ detailOrder.inspectionReportNo || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">检测结果</span>
                <span class="detail-value">{{ detailOrder.inspectionResult || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">检测机构</span>
                <span class="detail-value">{{ detailOrder.inspectionAgency || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">检测时间</span>
                <span class="detail-value">{{ detailOrder.inspectionAt || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">数据来源</span>
                <span class="detail-value">{{ detailOrder.inspectionSourceType === 'third_party' ? '第三方接口' : detailOrder.inspectionSourceType ? '手工录入' : '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">检测备注</span>
                <span class="detail-value">{{ detailOrder.inspectionRemark || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">同步数据</span>
                <span class="detail-value">{{ detailOrder.inspectionSyncPayload || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">检测附件</span>
                <div class="detail-value detail-attachment-value">
                  <div v-if="detailOrder.inspectionAttachments.length" class="attachment-list">
                    <div
                      v-for="(attachment, index) in detailOrder.inspectionAttachments"
                      :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
                      class="attachment-card"
                    >
                      <div class="attachment-meta">
                        <span class="attachment-name">{{ attachment.name || '检测报告附件' }}</span>
                        <span class="attachment-status">
                          已上传
                          <template v-if="attachment.size"> · {{ attachment.size }}</template>
                        </span>
                      </div>
                      <div class="attachment-actions">
                        <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                        <el-button link type="primary" @click="downloadStoredAttachment(attachment)">下载</el-button>
                      </div>
                    </div>
                  </div>
                  <span v-else>暂无附件</span>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">溯源信息</div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">溯源批次码</span>
                <span class="detail-value">{{ detailOrder.traceBatchId || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">来源/产地</span>
                <span class="detail-value">{{ detailOrder.traceOrigin || '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">数据来源</span>
                <span class="detail-value">{{ detailOrder.traceSourceType === 'third_party' ? '第三方接口' : detailOrder.traceSourceType ? '手工录入' : '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">溯源备注</span>
                <span class="detail-value">{{ detailOrder.traceRemark || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">同步数据</span>
                <span class="detail-value">{{ detailOrder.traceSyncPayload || '—' }}</span>
              </div>
              <div class="detail-item detail-item-full">
                <span class="detail-label">溯源附件</span>
                <div class="detail-value detail-attachment-value">
                  <div v-if="detailOrder.traceabilityAttachments.length" class="attachment-list">
                    <div
                      v-for="(attachment, index) in detailOrder.traceabilityAttachments"
                      :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
                      class="attachment-card"
                    >
                      <div class="attachment-meta">
                        <span class="attachment-name">{{ attachment.name || '溯源附件' }}</span>
                        <span class="attachment-status">
                          已上传
                          <template v-if="attachment.size"> · {{ attachment.size }}</template>
                        </span>
                      </div>
                      <div class="attachment-actions">
                        <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                        <el-button link type="primary" @click="downloadStoredAttachment(attachment)">下载</el-button>
                      </div>
                    </div>
                  </div>
                  <span v-else>暂无附件</span>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">物料明细</div>
            <el-table :data="detailOrder.items" border>
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column label="关联计划" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.planNo ? `${row.planNo}｜${row.planName}${row.planOrgName ? `｜${row.planOrgName}` : ''}` : '手工录入' }}
                </template>
              </el-table-column>
              <el-table-column prop="materialName" label="物料名称" min-width="150" />
              <el-table-column prop="spec" label="规格" min-width="120" />
              <el-table-column prop="unit" label="单位" width="80" align="center" />
              <el-table-column prop="quantity" label="订购数量" width="110" align="right" />
              <el-table-column label="单价" width="120" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(Number(row.unitPrice || row.unitCost || 0)) }}
                </template>
              </el-table-column>
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(row.subtotal) }}
                </template>
              </el-table-column>
              <el-table-column prop="receivedQty" label="已收数量" width="100" align="right" />
              <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            </el-table>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">已关联入库单记录</div>
            <el-table
              class="linked-inbound-table"
              :data="linkedInboundRecords"
              border
              :row-class-name="getLinkedInboundRowClassName"
              empty-text="暂无关联入库单记录"
            >
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="inboundNo" label="入库单号" min-width="160" />
              <el-table-column label="入库日期" width="170">
                <template #default="{ row }">
                  {{ row.inboundDate || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="入库状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag
                    :type="getLinkedInboundStatusType(getLinkedInboundEffectiveStatus(row.status, row.postStatus))"
                    size="small"
                  >
                    {{ getLinkedInboundStatusLabel(getLinkedInboundEffectiveStatus(row.status, row.postStatus)) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="materialName" label="物料名称" min-width="150" />
              <el-table-column prop="spec" label="规格" min-width="120" />
              <el-table-column prop="unit" label="单位" width="80" align="center" />
              <el-table-column label="入库数量" width="110" align="right">
                <template #default="{ row }">
                  {{ row.inboundQuantity }}
                </template>
              </el-table-column>
              <el-table-column label="操作人" min-width="120">
                <template #default="{ row }">
                  {{ row.operatorName || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">
                  {{ row.createdAt || '—' }}
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">附件信息</div>
            <div v-if="detailOrder.attachmentUrl" class="attachment-card">
              <div class="attachment-meta">
                <span class="attachment-name">{{ detailOrder.attachmentName || '附件' }}</span>
                <span class="attachment-status">已上传</span>
              </div>
              <div class="attachment-actions">
                <el-button link type="primary" @click="previewAttachment(detailOrder.attachmentUrl)">在线查看</el-button>
                <el-button link type="primary" @click="downloadAttachment(detailOrder.id)">下载</el-button>
              </div>
            </div>
            <div v-else class="attachment-empty">暂无附件</div>
          </div>
        </template>
      </div>

      <template #footer>
        <el-button
          v-if="detailOrder && hasOrderPermission(PURCHASE_PERMISSIONS.APPROVE) && canReverseAudit(detailOrder)"
          type="warning"
          @click="openReverseAuditDialog(detailOrder)"
        >
          反审核
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="auditVisible"
      title="审核采购订单"
      width="520px"
      destroy-on-close
      @close="closeAuditDialog"
    >
      <template v-if="auditTarget">
        <div class="audit-summary">
          <div><span>订单编号：</span>{{ auditTarget.orderNo }}</div>
          <div><span>供应商：</span>{{ auditTarget.supplierName }}</div>
          <div><span>订单金额：</span>{{ formatCurrency(auditTarget.totalAmount) }}</div>
        </div>
        <el-form label-width="84px">
          <el-form-item label="审核意见">
            <el-input
              v-model="auditForm.remark"
              type="textarea"
              :rows="4"
              placeholder="请输入审核意见"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeAuditDialog">取消</el-button>
        <el-button :loading="auditSubmitting" @click="rejectOrder">驳回</el-button>
        <el-button type="primary" :loading="auditSubmitting" @click="approveOrder">通过</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="reverseVisible"
      title="反审核采购订单"
      width="620px"
      destroy-on-close
      @close="closeReverseAuditDialog"
    >
      <template v-if="reverseTarget">
        <div class="audit-summary">
          <div><span>订单编号：</span>{{ reverseTarget.orderNo }}</div>
          <div><span>供应商：</span>{{ reverseTarget.supplierName }}</div>
          <div><span>当前状态：</span>{{ getReverseAuditCurrentStatusLabel(reverseTarget) }}</div>
          <div><span>关联采购计划数量：</span>{{ reverseTarget.relatedPlans.length }} 个</div>
          <div><span>关联采购入库单数量：</span>{{ reverseLinkedInboundCount }} 个</div>
        </div>
        <el-form label-width="100px">
          <el-form-item label="反审核原因" required>
            <el-input
              v-model="reverseForm.reason"
              type="textarea"
              :rows="4"
              :maxlength="500"
              show-word-limit
              placeholder="请填写反审核原因"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeReverseAuditDialog">取消</el-button>
        <el-button type="danger" :loading="reverseSubmitting" @click="submitReverseAudit">确认反审核</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="voidVisible"
      title="申请作废采购订单"
      width="540px"
      destroy-on-close
      @close="closeVoidDialog"
    >
      <template v-if="voidTarget">
        <div class="audit-summary">
          <div><span>订单编号：</span>{{ voidTarget.orderNo }}</div>
          <div><span>供应商：</span>{{ voidTarget.supplierName }}</div>
          <div><span>当前状态：</span>{{ getStatusLabel(voidTarget.status) }}</div>
        </div>
        <el-form label-width="84px">
          <el-form-item label="作废原因" required>
            <el-input
              v-model="voidForm.reason"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请输入作废原因"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeVoidDialog">取消</el-button>
        <el-button type="primary" :loading="voidSubmitting" @click="submitVoidApply">提交作废申请</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="voidAuditVisible"
      title="审核采购订单作废申请"
      width="560px"
      destroy-on-close
      @close="closeVoidAuditDialog"
    >
      <template v-if="voidAuditTarget">
        <div class="audit-summary">
          <div><span>订单编号：</span>{{ voidAuditTarget.orderNo }}</div>
          <div><span>供应商：</span>{{ voidAuditTarget.supplierName }}</div>
          <div><span>作废原因：</span>{{ voidAuditTarget.voidReason || '—' }}</div>
        </div>
        <el-form label-width="96px">
          <el-form-item label="审核意见">
            <el-input
              v-model="voidAuditForm.remark"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请输入作废审核意见"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeVoidAuditDialog">取消</el-button>
        <el-button :loading="voidAuditSubmitting" @click="submitVoidAudit(false)">驳回作废</el-button>
        <el-button type="primary" :loading="voidAuditSubmitting" @click="submitVoidAudit(true)">通过作废</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="logisticsVisible"
      title="维护物流追踪"
      width="760px"
      destroy-on-close
      @close="closeLogisticsDialog"
    >
      <template v-if="logisticsTarget">
        <div class="audit-summary">
          <div><span>订单编号：</span>{{ logisticsTarget.orderNo }}</div>
          <div><span>供应商：</span>{{ logisticsTarget.supplierName }}</div>
          <div><span>当前状态：</span>{{ getStatusLabel(logisticsTarget.status) }}</div>
        </div>
        <el-form label-width="96px" class="maintenance-form">
          <div class="form-grid">
            <el-form-item label="数据来源">
              <el-select
                v-model="logisticsForm.sourceType"
                :disabled="!canEditSceneSourceType('logistics')"
                style="width: 100%"
                @change="handleSceneSourceTypeChange('logistics')"
              >
                <el-option
                  v-for="option in MAINTENANCE_SOURCE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                  :disabled="option.value === 'manual'
                    ? logisticsIntegration.forceThirdParty === 1
                    : logisticsIntegration.configOptions.length === 0 && !logisticsIntegration.bindingId"
                />
              </el-select>
              <div v-if="logisticsIntegration.configOptions.length === 0 && !logisticsIntegration.bindingId" class="form-field-tip">
                当前组织在物流场景下暂无可用第三方同步方案，可继续手工录入。
              </div>
            </el-form-item>
            <el-form-item v-if="logisticsForm.sourceType === 'third_party'" label="第三方同步" class="form-item-full">
              <div class="integration-panel">
                <div class="integration-grid">
                  <div class="integration-field">
                    <span class="integration-label">同步方案</span>
                    <el-select v-model="logisticsIntegration.configId" style="width: 100%" placeholder="请选择同步方案">
                      <el-option
                        v-for="option in logisticsIntegration.configOptions"
                        :key="option.id"
                        :label="`${option.configName} / ${option.providerName}`"
                        :value="option.id"
                      />
                    </el-select>
                  </div>
                  <div class="integration-field">
                    <span class="integration-label">{{ getSceneExternalNoLabel('logistics') }}</span>
                    <el-input v-model="logisticsIntegration.externalNo" placeholder="请输入第三方查询主键" />
                    <div v-if="logisticsIntegration.externalNoFieldRule" class="form-field-tip">
                      规则提示：{{ logisticsIntegration.externalNoFieldRule }}
                    </div>
                  </div>
                </div>
                <div class="integration-status-row">
                  <span>绑定状态：{{ logisticsIntegration.bindingId ? '已绑定' : '未绑定' }}</span>
                  <span>同步状态：</span>
                  <el-tag :type="getSceneIntegrationSummary('logistics').type" size="small">
                    {{ getSceneIntegrationSummary('logistics').label }}
                  </el-tag>
                  <span>最近同步：{{ logisticsIntegration.lastSyncAt || '—' }}</span>
                  <span>下次同步：{{ logisticsIntegration.nextSyncAt || '—' }}</span>
                </div>
                <div v-if="logisticsIntegration.lastErrorMessage" class="integration-error">
                  最近失败原因：{{ logisticsIntegration.lastErrorMessage }}
                </div>
                <div class="integration-actions">
                  <el-button type="primary" plain :loading="logisticsSyncing" @click="triggerSceneIntegrationSyncAction('logistics', false)">
                    立即同步
                  </el-button>
                  <el-button plain :loading="logisticsSyncing" @click="triggerSceneIntegrationSyncAction('logistics', true)">
                    只查询不覆盖
                  </el-button>
                  <el-button link type="primary" @click="loadSceneIntegrationLogs('logistics')">查看同步记录</el-button>
                </div>
                <div v-if="logisticsIntegration.recentSyncLogs.length || logisticsIntegration.recentCallbackLogs.length" class="integration-log-preview">
                  <div v-for="item in logisticsIntegration.recentSyncLogs.slice(0, 2)" :key="`sync-${item.id}`" class="integration-log-item">
                    <span class="integration-log-title">同步</span>
                    <span>{{ item.createdAt || '—' }}</span>
                    <span>{{ item.message || item.status || '—' }}</span>
                  </div>
                  <div v-for="item in logisticsIntegration.recentCallbackLogs.slice(0, 2)" :key="`callback-${item.id}`" class="integration-log-item">
                    <span class="integration-log-title">回调</span>
                    <span>{{ item.createdAt || '—' }}</span>
                    <span>{{ item.message || item.status || '—' }}</span>
                  </div>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="物流状态" required>
              <el-select
                v-model="logisticsForm.logisticsStatus"
                :disabled="logisticsStatusLocked"
                style="width: 100%"
              >
                <el-option
                  v-for="option in LOGISTICS_STATUS_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <div v-if="logisticsStatusLockReason" class="form-field-warning">{{ logisticsStatusLockReason }}</div>
            </el-form-item>
            <el-form-item label="物流公司" :required="logisticsRequiredState.company">
              <el-input v-model="logisticsForm.company" placeholder="请输入物流公司" />
            </el-form-item>
            <el-form-item label="物流单号" :required="logisticsRequiredState.trackingNo">
              <el-input v-model="logisticsForm.trackingNo" placeholder="请输入物流单号" />
            </el-form-item>
            <el-form-item label="发货时间" :required="logisticsRequiredState.shippedAt">
              <el-date-picker
                v-model="logisticsForm.shippedAt"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                placeholder="请选择发货时间"
              />
            </el-form-item>
            <el-form-item label="到货时间" :required="logisticsRequiredState.arrivedAt">
              <el-date-picker
                v-model="logisticsForm.arrivedAt"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                :editable="false"
                :disabled-date="disableFutureProductionDate"
                placeholder="请选择到货时间"
              />
            </el-form-item>
            <el-form-item label="物流备注" class="form-item-full">
              <el-input
                v-model="logisticsForm.remark"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="请输入物流追踪说明"
              />
            </el-form-item>
            <el-form-item v-if="logisticsForm.sourceType === 'third_party'" label="同步数据" class="form-item-full">
              <el-input
                v-model="logisticsForm.syncPayload"
                type="textarea"
                :rows="4"
                readonly
                placeholder="预留第三方接口同步数据结构，可填写 JSON 或原始返回文本"
              />
            </el-form-item>
            <el-form-item label="物流附件" :required="logisticsRequiredState.attachments" class="form-item-full">
              <div class="attachment-field">
                <el-upload
                  ref="logisticsUploadRef"
                  :auto-upload="false"
                  :show-file-list="false"
                  multiple
                  :on-change="handleLogisticsAttachmentChange"
                >
                  <el-button type="primary" plain :loading="logisticsAttachmentUploading">选择附件</el-button>
                </el-upload>
                <div class="attachment-field-tip">支持多文件批量上传物流签收单、运输回执等附件，保存后统一写入 MinIO。</div>
                <div v-if="logisticsTarget.logisticsAttachments.length" class="attachment-list">
                  <div
                    v-for="(attachment, index) in logisticsTarget.logisticsAttachments"
                    :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
                    class="attachment-card"
                  >
                    <div class="attachment-meta">
                      <span class="attachment-name">{{ attachment.name }}</span>
                      <span class="attachment-status">
                        {{ isPersistedMaintenanceAttachment('logistics', attachment) ? '当前附件' : '待保存附件' }}
                        <template v-if="attachment.size"> · {{ attachment.size }}</template>
                      </span>
                    </div>
                    <div class="attachment-actions">
                      <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                      <el-button link type="primary" @click="downloadStoredAttachment(attachment)">下载</el-button>
                      <el-button link type="danger" @click="removeMaintenanceAttachment('logistics', index)">
                        删除
                      </el-button>
                    </div>
                  </div>
                </div>
                <div v-else class="attachment-empty">暂未上传物流附件</div>
              </div>
            </el-form-item>
          </div>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeLogisticsDialog">取消</el-button>
        <el-button type="primary" :loading="logisticsSubmitting" @click="submitLogistics">
          保存物流信息
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="inspectionVisible"
      title="维护检测报告"
      width="760px"
      destroy-on-close
      @close="closeInspectionDialog"
    >
      <template v-if="inspectionTarget">
        <div class="audit-summary">
          <div><span>订单编号：</span>{{ inspectionTarget.orderNo }}</div>
          <div><span>供应商：</span>{{ inspectionTarget.supplierName }}</div>
          <div><span>当前状态：</span>{{ getStatusLabel(inspectionTarget.status) }}</div>
        </div>
        <el-form label-width="96px" class="maintenance-form">
          <div class="form-grid">
            <el-form-item label="数据来源">
              <el-select
                v-model="inspectionForm.sourceType"
                :disabled="!canEditSceneSourceType('inspection')"
                style="width: 100%"
                @change="handleSceneSourceTypeChange('inspection')"
              >
                <el-option
                  v-for="option in MAINTENANCE_SOURCE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                  :disabled="option.value === 'manual'
                    ? inspectionIntegration.forceThirdParty === 1
                    : inspectionIntegration.configOptions.length === 0 && !inspectionIntegration.bindingId"
                />
              </el-select>
              <div v-if="inspectionIntegration.configOptions.length === 0 && !inspectionIntegration.bindingId" class="form-field-tip">
                当前组织在检测场景下暂无可用第三方同步方案，可继续手工录入。
              </div>
            </el-form-item>
            <el-form-item v-if="inspectionForm.sourceType === 'third_party'" label="第三方同步" class="form-item-full">
              <div class="integration-panel">
                <div class="integration-grid">
                  <div class="integration-field">
                    <span class="integration-label">同步方案</span>
                    <el-select v-model="inspectionIntegration.configId" style="width: 100%" placeholder="请选择同步方案">
                      <el-option
                        v-for="option in inspectionIntegration.configOptions"
                        :key="option.id"
                        :label="`${option.configName} / ${option.providerName}`"
                        :value="option.id"
                      />
                    </el-select>
                  </div>
                  <div class="integration-field">
                    <span class="integration-label">{{ getSceneExternalNoLabel('inspection') }}</span>
                    <el-input v-model="inspectionIntegration.externalNo" placeholder="请输入第三方查询主键" />
                    <div v-if="inspectionIntegration.externalNoFieldRule" class="form-field-tip">
                      规则提示：{{ inspectionIntegration.externalNoFieldRule }}
                    </div>
                  </div>
                </div>
                <div class="integration-status-row">
                  <span>绑定状态：{{ inspectionIntegration.bindingId ? '已绑定' : '未绑定' }}</span>
                  <span>同步状态：</span>
                  <el-tag :type="getSceneIntegrationSummary('inspection').type" size="small">
                    {{ getSceneIntegrationSummary('inspection').label }}
                  </el-tag>
                  <span>最近同步：{{ inspectionIntegration.lastSyncAt || '—' }}</span>
                  <span>下次同步：{{ inspectionIntegration.nextSyncAt || '—' }}</span>
                </div>
                <div v-if="inspectionIntegration.lastErrorMessage" class="integration-error">
                  最近失败原因：{{ inspectionIntegration.lastErrorMessage }}
                </div>
                <div class="integration-actions">
                  <el-button type="primary" plain :loading="inspectionSyncing" @click="triggerSceneIntegrationSyncAction('inspection', false)">
                    立即同步
                  </el-button>
                  <el-button plain :loading="inspectionSyncing" @click="triggerSceneIntegrationSyncAction('inspection', true)">
                    只查询不覆盖
                  </el-button>
                  <el-button link type="primary" @click="loadSceneIntegrationLogs('inspection')">查看同步记录</el-button>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="检测结果">
              <el-select
                v-model="inspectionForm.result"
                clearable
                style="width: 100%"
                placeholder="请选择检测结果"
              >
                <el-option
                  v-for="option in INSPECTION_RESULT_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="报告编号" :required="inspectionRequiredState.reportNo">
              <el-input v-model="inspectionForm.reportNo" placeholder="请输入检测报告编号" />
            </el-form-item>
            <el-form-item label="检测机构" :required="inspectionRequiredState.agency">
              <el-input v-model="inspectionForm.agency" placeholder="请输入检测机构" />
            </el-form-item>
            <el-form-item label="检测时间" :required="inspectionRequiredState.inspectedAt">
              <el-date-picker
                v-model="inspectionForm.inspectedAt"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                :editable="false"
                :disabled-date="disableFutureProductionDate"
                placeholder="请选择检测时间"
              />
            </el-form-item>
            <el-form-item label="检测备注" class="form-item-full">
              <el-input
                v-model="inspectionForm.remark"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="请输入检测说明"
              />
            </el-form-item>
            <el-form-item v-if="inspectionForm.sourceType === 'third_party'" label="同步数据" class="form-item-full">
              <el-input
                v-model="inspectionForm.syncPayload"
                type="textarea"
                :rows="4"
                readonly
                placeholder="预留第三方接口同步数据结构，可填写 JSON 或原始返回文本"
              />
            </el-form-item>
            <el-form-item label="检测附件" :required="inspectionRequiredState.attachments" class="form-item-full">
              <div class="attachment-field">
                <el-upload
                  ref="inspectionUploadRef"
                  :auto-upload="false"
                  :show-file-list="false"
                  multiple
                  :on-change="handleInspectionAttachmentChange"
                >
                  <el-button type="primary" plain :loading="inspectionAttachmentUploading">选择附件</el-button>
                </el-upload>
                <div class="attachment-field-tip">支持多文件批量上传检测报告、质检回执等附件。</div>
                <div v-if="inspectionTarget.inspectionAttachments.length" class="attachment-list">
                  <div
                    v-for="(attachment, index) in inspectionTarget.inspectionAttachments"
                    :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
                    class="attachment-card"
                  >
                    <div class="attachment-meta">
                      <span class="attachment-name">{{ attachment.name }}</span>
                      <span class="attachment-status">
                        {{ isPersistedMaintenanceAttachment('inspection', attachment) ? '当前附件' : '待保存附件' }}
                        <template v-if="attachment.size"> · {{ attachment.size }}</template>
                      </span>
                    </div>
                    <div class="attachment-actions">
                      <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                      <el-button link type="primary" @click="downloadStoredAttachment(attachment)">下载</el-button>
                      <el-button link type="danger" @click="removeMaintenanceAttachment('inspection', index)">
                        删除
                      </el-button>
                    </div>
                  </div>
                </div>
                <div v-else class="attachment-empty">暂未上传检测附件</div>
              </div>
            </el-form-item>
          </div>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeInspectionDialog">取消</el-button>
        <el-button type="primary" :loading="inspectionSubmitting" @click="submitInspection">
          保存检测报告
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="traceabilityVisible"
      title="维护溯源信息"
      width="760px"
      destroy-on-close
      @close="closeTraceabilityDialog"
    >
      <template v-if="traceabilityTarget">
        <div class="audit-summary">
          <div><span>订单编号：</span>{{ traceabilityTarget.orderNo }}</div>
          <div><span>供应商：</span>{{ traceabilityTarget.supplierName }}</div>
          <div><span>当前状态：</span>{{ getStatusLabel(traceabilityTarget.status) }}</div>
        </div>
        <el-form label-width="96px" class="maintenance-form">
          <div class="form-grid">
            <el-form-item label="数据来源">
              <el-select
                v-model="traceabilityForm.sourceType"
                :disabled="!canEditSceneSourceType('traceability')"
                style="width: 100%"
                @change="handleSceneSourceTypeChange('traceability')"
              >
                <el-option
                  v-for="option in MAINTENANCE_SOURCE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                  :disabled="option.value === 'manual'
                    ? traceabilityIntegration.forceThirdParty === 1
                    : traceabilityIntegration.configOptions.length === 0 && !traceabilityIntegration.bindingId"
                />
              </el-select>
              <div v-if="traceabilityIntegration.configOptions.length === 0 && !traceabilityIntegration.bindingId" class="form-field-tip">
                当前组织在溯源场景下暂无可用第三方同步方案，可继续手工录入。
              </div>
            </el-form-item>
            <el-form-item v-if="traceabilityForm.sourceType === 'third_party'" label="第三方同步" class="form-item-full">
              <div class="integration-panel">
                <div class="integration-grid">
                  <div class="integration-field">
                    <span class="integration-label">同步方案</span>
                    <el-select v-model="traceabilityIntegration.configId" style="width: 100%" placeholder="请选择同步方案">
                      <el-option
                        v-for="option in traceabilityIntegration.configOptions"
                        :key="option.id"
                        :label="`${option.configName} / ${option.providerName}`"
                        :value="option.id"
                      />
                    </el-select>
                  </div>
                  <div class="integration-field">
                    <span class="integration-label">{{ getSceneExternalNoLabel('traceability') }}</span>
                    <el-input v-model="traceabilityIntegration.externalNo" placeholder="请输入第三方查询主键" />
                    <div v-if="traceabilityIntegration.externalNoFieldRule" class="form-field-tip">
                      规则提示：{{ traceabilityIntegration.externalNoFieldRule }}
                    </div>
                  </div>
                </div>
                <div class="integration-status-row">
                  <span>绑定状态：{{ traceabilityIntegration.bindingId ? '已绑定' : '未绑定' }}</span>
                  <span>同步状态：</span>
                  <el-tag :type="getSceneIntegrationSummary('traceability').type" size="small">
                    {{ getSceneIntegrationSummary('traceability').label }}
                  </el-tag>
                  <span>最近同步：{{ traceabilityIntegration.lastSyncAt || '—' }}</span>
                  <span>下次同步：{{ traceabilityIntegration.nextSyncAt || '—' }}</span>
                </div>
                <div v-if="traceabilityIntegration.lastErrorMessage" class="integration-error">
                  最近失败原因：{{ traceabilityIntegration.lastErrorMessage }}
                </div>
                <div class="integration-actions">
                  <el-button type="primary" plain :loading="traceabilitySyncing" @click="triggerSceneIntegrationSyncAction('traceability', false)">
                    立即同步
                  </el-button>
                  <el-button plain :loading="traceabilitySyncing" @click="triggerSceneIntegrationSyncAction('traceability', true)">
                    只查询不覆盖
                  </el-button>
                  <el-button link type="primary" @click="loadSceneIntegrationLogs('traceability')">查看同步记录</el-button>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="溯源批次码" :required="traceabilityRequiredState.traceBatchId">
              <el-input v-model="traceabilityForm.traceBatchId" placeholder="请输入溯源批次码" />
            </el-form-item>
            <el-form-item label="来源/产地" :required="traceabilityRequiredState.origin" class="form-item-full">
              <el-input v-model="traceabilityForm.origin" placeholder="请输入来源地、基地或批次来源说明" />
            </el-form-item>
            <el-form-item label="溯源备注" class="form-item-full">
              <el-input
                v-model="traceabilityForm.remark"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="请输入溯源说明"
              />
            </el-form-item>
            <el-form-item v-if="traceabilityForm.sourceType === 'third_party'" label="同步数据" class="form-item-full">
              <el-input
                v-model="traceabilityForm.syncPayload"
                type="textarea"
                :rows="4"
                readonly
                placeholder="预留第三方接口同步数据结构，可填写 JSON 或原始返回文本"
              />
            </el-form-item>
            <el-form-item label="溯源附件" :required="traceabilityRequiredState.attachments" class="form-item-full">
              <div class="attachment-field">
                <el-upload
                  ref="traceabilityUploadRef"
                  :auto-upload="false"
                  :show-file-list="false"
                  multiple
                  :on-change="handleTraceabilityAttachmentChange"
                >
                  <el-button type="primary" plain :loading="traceabilityAttachmentUploading">选择附件</el-button>
                </el-upload>
                <div class="attachment-field-tip">支持多文件批量上传溯源码凭证、基地证明等附件。</div>
                <div v-if="traceabilityTarget.traceabilityAttachments.length" class="attachment-list">
                  <div
                    v-for="(attachment, index) in traceabilityTarget.traceabilityAttachments"
                    :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
                    class="attachment-card"
                  >
                    <div class="attachment-meta">
                      <span class="attachment-name">{{ attachment.name }}</span>
                      <span class="attachment-status">
                        {{ isPersistedMaintenanceAttachment('traceability', attachment) ? '当前附件' : '待保存附件' }}
                        <template v-if="attachment.size"> · {{ attachment.size }}</template>
                      </span>
                    </div>
                    <div class="attachment-actions">
                      <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                      <el-button link type="primary" @click="downloadStoredAttachment(attachment)">下载</el-button>
                      <el-button link type="danger" @click="removeMaintenanceAttachment('traceability', index)">
                        删除
                      </el-button>
                    </div>
                  </div>
                </div>
                <div v-else class="attachment-empty">暂未上传溯源附件</div>
              </div>
            </el-form-item>
          </div>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeTraceabilityDialog">取消</el-button>
        <el-button type="primary" :loading="traceabilitySubmitting" @click="submitTraceability">
          保存溯源信息
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="sceneLogsVisible"
      :title="sceneLogsTitle"
      width="880px"
      destroy-on-close
    >
      <div v-loading="sceneLogsLoading" class="integration-log-dialog">
        <div class="integration-log-section">
          <div class="section-title">同步记录</div>
          <el-table :data="sceneLogsData.syncLogs" border size="small" empty-text="暂无同步记录">
            <el-table-column prop="createdAt" label="时间" min-width="160" />
            <el-table-column prop="providerName" label="平台" min-width="140" />
            <el-table-column prop="triggerType" label="触发方式" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(INTEGRATION_SYNC_STATUS_MAP[row.status || ''] || { type: 'info' }).type" size="small">
                  {{ (INTEGRATION_SYNC_STATUS_MAP[row.status || ''] || { label: row.status || '—' }).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="结果说明" min-width="240" show-overflow-tooltip />
          </el-table>
        </div>
        <div class="integration-log-section">
          <div class="section-title">回调记录</div>
          <el-table :data="sceneLogsData.callbackLogs" border size="small" empty-text="暂无回调记录">
            <el-table-column prop="createdAt" label="时间" min-width="160" />
            <el-table-column prop="providerName" label="平台" min-width="140" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(INTEGRATION_SYNC_STATUS_MAP[row.status || ''] || { type: 'info' }).type" size="small">
                  {{ (INTEGRATION_SYNC_STATUS_MAP[row.status || ''] || { label: row.status || '—' }).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="处理结果" min-width="260" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="sceneLogsVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 关联生成入库单对话框 -->
    <el-dialog
      v-model="generateInboundVisible"
      title="关联生成入库单"
      width="1300px"
      destroy-on-close
    >
      <el-form v-loading="generateInboundSubmitting" label-width="100px">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <div class="form-grid">
            <el-form-item label="入库类型">
              <el-input model-value="采购入库" readonly />
            </el-form-item>
            <el-form-item label="来源单号">
              <el-input :model-value="generateInboundTarget?.orderNo || ''" readonly />
            </el-form-item>
            <el-form-item label="供应商">
              <el-input :model-value="generateInboundTarget?.supplierName || ''" readonly />
            </el-form-item>
            <el-form-item label="入库组织" required>
              <OrgTreeSelect
                v-model="generateInboundForm.orgId"
                placeholder="请选择入库组织"
              />
            </el-form-item>
          </div>
        </div>
        <div class="form-section">
          <div class="section-title" style="display:flex;justify-content:space-between;align-items:center;">
            <span>入库明细</span>
            <el-button type="success" size="small" @click="openGenerateInboundSelectMaterial">从来源订单选择物料</el-button>
          </div>
          <el-table :data="generateInboundForm.items" border size="small" style="width: 100%">
            <el-table-column type="selection" width="45" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.selected" />
              </template>
            </el-table-column>
            <el-table-column prop="materialName" label="物料名称" min-width="100" />
            <el-table-column prop="spec" label="规格" width="90" />
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column prop="orderQty" label="订单数量" width="85" align="right" />
            <el-table-column prop="inboundQty" label="已占用" width="75" align="right" />
            <el-table-column prop="remainingInboundQty" label="可入库" width="75" align="right" />
            <el-table-column label="入库仓库 *" min-width="120">
              <template #default="{ row }">
                <el-select
                  v-model="row.warehouseId"
                  placeholder="选择仓库"
                  size="small"
                  style="width:100%"
                  :disabled="!row.selected"
                  @change="onGenerateInboundWarehouseChange(row)"
                >
                  <el-option v-for="w in generateInboundWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="仓位 *" min-width="110">
              <template #default="{ row }">
                <el-select
                  v-model="row.locationId"
                  placeholder="选择仓位"
                  size="small"
                  style="width:100%"
                  :disabled="!row.warehouseId || !row.selected"
                >
                  <el-option v-for="loc in row._locations" :key="loc.id" :label="loc.locationName" :value="loc.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量 *" width="100">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantity"
                  :min="0"
                  :max="row.remainingInboundQty"
                  :precision="3"
                  :controls="false"
                  size="small"
                  style="width: 100%"
                  :disabled="!row.selected"
                />
                <div v-if="getGenerateInboundQuantityExceededMessage(row)" class="cell-error">{{ getGenerateInboundQuantityExceededMessage(row) }}</div>
                <div v-if="row.remainingInboundQty != null" class="qty-tip" :class="{ 'qty-tip-error': !!getGenerateInboundQuantityExceededMessage(row) }">
                  可入库 {{ row.remainingInboundQty }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单价(元)" width="100" align="right">
              <template #default="{ row }">
                <span style="color: #333">{{ row.unitCost != null ? row.unitCost : '' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="批次号" width="100">
              <template #default="{ row }">
                <el-input v-model="row.batchNo" size="small" :disabled="!row.selected" />
              </template>
            </el-table-column>
            <el-table-column label="生产日期" width="140">
              <template #default="{ row }">
                <el-date-picker
                  v-model="row.productionDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 100%"
                  :disabled="!row.selected"
                  :disabled-date="disableFutureProductionDate"
                />
              </template>
            </el-table-column>
            <el-table-column label="" width="50" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="removeGenerateInboundItem($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="form-section">
          <el-form-item label="备注">
            <el-input
              v-model="generateInboundForm.remark"
              type="textarea"
              :rows="2"
              placeholder="选填"
              maxlength="500"
            />
          </el-form-item>
        </div>
      </el-form>

      <!-- 从来源订单选择物料弹窗 -->
      <el-dialog v-model="generateInboundSelectVisible" title="从来源订单选择物料" width="700px" :close-on-click-modal="false" append-to-body>
        <el-input v-model="generateInboundSelectKeyword" placeholder="搜索物料名称或规格" clearable style="margin-bottom: 12px" />
        <el-table v-loading="generateInboundSelectLoading" :data="generateInboundFilteredSource" border size="small" max-height="400"
          @selection-change="(rows: any[]) => { generateInboundSelectedIds = rows.map((r: any) => r.materialId) }">
          <el-table-column type="selection" width="45" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="spec" label="规格" width="120" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column prop="orderQty" label="订单数量" width="90" align="right" />
          <el-table-column prop="inboundQty" label="已占用" width="80" align="right" />
          <el-table-column prop="remainingInboundQty" label="可入库" width="80" align="right">
            <template #default="{ row }">
              <span style="color: #E6A23C; font-weight: 600">{{ row.remainingInboundQty }}</span>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="generateInboundSelectVisible = false">取消</el-button>
          <el-button type="primary" :disabled="generateInboundSelectedIds.length === 0" @click="confirmGenerateInboundSelectMaterial">
            确认追加（已选 {{ generateInboundSelectedIds.length }} 条）
          </el-button>
        </template>
      </el-dialog>      <template #footer>
        <el-button @click="closeGenerateInboundDialog">取消</el-button>
        <el-button type="primary" :loading="generateInboundSubmitting" @click="submitGenerateInbound">确认生成入库单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.purchase-order-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 22px;
  border-radius: 16px;
  background: linear-gradient(135deg, #ffffff 0%, #f6f9fc 100%);
  border: 1px solid #e7edf4;
  box-shadow: 0 10px 24px rgba(15, 35, 95, 0.06);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
}

.stat-icon.total {
  background: linear-gradient(135deg, #5b8ff9 0%, #3f73f0 100%);
}

.stat-icon.pending {
  background: linear-gradient(135deg, #faad14 0%, #f08a24 100%);
}

.stat-icon.approved {
  background: linear-gradient(135deg, #52c41a 0%, #2db572 100%);
}

.stat-icon.amount {
  background: linear-gradient(135deg, #13c2c2 0%, #0f9b8e 100%);
}

.stat-content {
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
  color: #1f2a37;
}

.stat-value.amount-text {
  font-size: 24px;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7280;
}

.toolbar,
.table-wrapper {
  background: #fff;
  border: 1px solid #e7edf4;
  border-radius: 16px;
  box-shadow: 0 10px 24px rgba(15, 35, 95, 0.05);
}

.toolbar {
  position: static;
  width: 100%;
  max-width: 100%;
  min-height: 64px;
  background: #FFFFFF;
  border-radius: 8px;
  padding: 16px 20px;
  box-sizing: border-box;
  overflow: visible;
  container-type: inline-size;
  container-name: purchase-toolbar;
}

.toolbar-grid {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(148px, 176px) minmax(248px, 300px) max-content;
  gap: 12px 14px;
  align-items: start;
}

.toolbar-field {
  min-width: 0;
  width: 100%;
  max-width: 100%;
  align-self: start;
}

.toolbar-search-field {
  width: 100%;
  min-width: 260px;
  max-width: 320px;
}

.toolbar-date-field {
  width: 100%;
  max-width: 300px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  align-self: start;
  justify-self: end;
  width: max-content;
  min-width: max-content;
  gap: 10px;
  flex-wrap: nowrap;
}

.toolbar-control {
  width: 100%;
}

.toolbar-search-input {
  max-width: none;
}

.toolbar-control :deep(.el-input__wrapper),
.toolbar-control :deep(.el-select__wrapper),
.toolbar-date-picker :deep(.el-range-editor.el-input__wrapper) {
  min-height: 40px;
  padding-top: 0;
  padding-bottom: 0;
}

.toolbar-search-input :deep(.el-input__inner) {
  text-overflow: clip;
}

.toolbar-date-picker :deep(.el-range-editor.el-input__wrapper) {
  justify-content: space-between;
  gap: 6px;
  min-width: 0;
  padding-inline: 10px;
}

.toolbar-date-picker :deep(.el-range-input) {
  height: 24px;
  min-width: 0;
}

.toolbar-date-picker :deep(.el-range-separator) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 24px;
  line-height: 24px;
  color: #6b7280;
}

.toolbar-button {
  min-width: auto;
  height: 32px;
  padding: 0 16px;
  border-radius: 8px;
  margin: 0;
  flex: 0 0 auto;
}

.table-wrapper {
  padding: 18px 20px 14px;
}

.table-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.table-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2a37;
}

.table-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7280;
}

.table-summary,
.pagination .total {
  font-size: 13px;
  color: #6b7280;
}

.status-tags {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  max-width: 100%;
}

.action-buttons__primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.action-buttons__more {
  padding: 0;
  margin: 0;
}

.action-buttons__more-icon {
  margin-left: 4px;
  font-size: 12px;
}

.action-buttons :deep(.el-dropdown) {
  flex: 0 0 auto;
}

.order-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-section,
.detail-section {
  border: 1px solid #e7edf4;
  border-radius: 16px;
  padding: 16px 18px 18px;
  background: #f8fbff;
}

.section-title,
.detail-section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2a37;
  margin-bottom: 14px;
}

.linked-inbound-table {
  --linked-inbound-group-even-bg: var(--el-bg-color);
  --linked-inbound-group-even-hover-bg: var(--el-fill-color-light);
  --linked-inbound-group-odd-bg: var(--el-color-primary-light-9);
  --linked-inbound-group-odd-hover-bg: var(--el-color-primary-light-8);
}

.linked-inbound-table :deep(.linked-inbound-group-even td.el-table__cell) {
  background: var(--linked-inbound-group-even-bg);
}

.linked-inbound-table :deep(.linked-inbound-group-even:hover > td.el-table__cell) {
  background: var(--linked-inbound-group-even-hover-bg);
}

.linked-inbound-table :deep(.linked-inbound-group-odd td.el-table__cell) {
  background: var(--linked-inbound-group-odd-bg);
}

.linked-inbound-table :deep(.linked-inbound-group-odd:hover > td.el-table__cell) {
  background: var(--linked-inbound-group-odd-hover-bg);
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.item-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #6b7280;
}

.item-summary strong {
  color: #d14343;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
}

.form-item-full {
  grid-column: 1 / -1;
}

.form-field-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

.plan-option {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.item-table-scroll {
  overflow-x: auto;
  padding-bottom: 4px;
}

.item-list {
  min-width: 1320px;
}

.item-row {
  display: grid;
  grid-template-columns: 72px 140px 220px 170px 92px 180px 140px 120px 220px 80px;
  gap: 12px;
  align-items: start;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e7edf4;
  border-top: 0;
}

.item-row-header {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f2f6fb;
  border-top: 1px solid #e7edf4;
  border-radius: 12px 12px 0 0;
}

.item-header-cell {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  line-height: 32px;
}

.item-index {
  display: flex;
  align-items: center;
  height: 32px;
  font-size: 13px;
  color: #4b5563;
}

.item-source {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 32px;
}

.item-source-meta {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
  word-break: break-all;
}

.readonly-cell {
  min-height: 32px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid #dbe4ef;
  background: #f8fafc;
  color: #374151;
  font-size: 13px;
}

.quantity-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quantity-tip {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

.align-right {
  text-align: right;
}

.align-center {
  text-align: center;
}

.subtotal-cell {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  font-weight: 700;
  color: #d14343;
}

.item-action {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.item-action-placeholder {
  font-size: 12px;
  color: #9ca3af;
}

.add-item-button {
  margin-top: 14px;
}

.attachment-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.attachment-field-tip {
  font-size: 12px;
  color: #6b7280;
}

.form-field-warning {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-warning);
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.attachment-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #dbe4ef;
  background: #f8fbff;
}

.attachment-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.attachment-name {
  color: #1f2a37;
  font-size: 14px;
  word-break: break-all;
}

.attachment-status {
  color: #6b7280;
  font-size: 12px;
}

.attachment-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.attachment-empty {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px dashed #dbe4ef;
  background: #fafcff;
  color: #6b7280;
}

.integration-panel {
  width: 100%;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid #dbe4ef;
  background: linear-gradient(180deg, #f8fbff 0%, #fdfefe 100%);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.integration-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.integration-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.integration-label {
  font-size: 13px;
  color: #6b7280;
}

.integration-status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  font-size: 13px;
  color: #374151;
}

.integration-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  align-items: center;
}

.integration-error {
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  color: #be123c;
  line-height: 1.6;
}

.integration-log-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.integration-log-item {
  display: grid;
  grid-template-columns: 48px 160px 1fr;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #edf2f7;
  color: #475569;
  font-size: 13px;
}

.integration-log-title {
  font-weight: 600;
  color: #1d4ed8;
}

.integration-log-dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.integration-log-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.detail-item {
  display: flex;
  gap: 10px;
  min-height: 24px;
}

.detail-item-full {
  grid-column: 1 / -1;
}

.detail-label {
  min-width: 72px;
  color: #6b7280;
}

.detail-value {
  color: #1f2a37;
  word-break: break-all;
}

.detail-attachment-value {
  width: 100%;
}

.detail-value.amount {
  color: #d14343;
  font-weight: 700;
}

.maintenance-form {
  margin-top: 12px;
}

.audit-summary {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fbff;
  border: 1px solid #e7edf4;
  color: #374151;
}

.reverse-audit-tip {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
  line-height: 1.6;
}

.reverse-audit-tip--muted {
  background: #f8fafc;
  border-color: #e2e8f0;
  color: #475569;
}

@media (max-width: 1440px) {
  .toolbar-actions {
    gap: 10px;
  }
}

@container purchase-toolbar (max-width: 1120px) {
  .toolbar-grid {
    grid-template-columns: minmax(240px, 1fr) minmax(150px, 176px) minmax(240px, 1fr);
  }

  .toolbar-actions {
    grid-column: 1 / -1;
    justify-self: stretch;
    width: 100%;
    min-width: 0;
    flex-wrap: wrap;
  }

  .toolbar-search-field,
  .toolbar-date-field {
    max-width: none;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }
}

@container purchase-toolbar (max-width: 760px) {
  .toolbar-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .toolbar-field,
  .toolbar-actions {
    grid-column: auto;
  }

  .toolbar-search-field,
  .toolbar-date-field,
  .toolbar-search-input {
    max-width: none;
  }

  .toolbar-actions {
    width: 100%;
    min-width: 0;
    justify-content: flex-end;
  }
}

@media (max-width: 992px) {
  .stats-cards,
  .form-grid,
  .detail-grid,
  .integration-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .toolbar-field,
  .toolbar-actions {
    grid-column: auto;
  }

  .toolbar-search-field,
  .toolbar-date-field,
  .toolbar-search-input {
    max-width: none;
  }

  .toolbar-actions {
    width: 100%;
    min-width: 0;
    justify-content: flex-end;
  }
}

.cell-error {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #f56c6c;
}

.qty-tip {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.2;
  color: #909399;
  white-space: nowrap;
}

.qty-tip-error {
  color: #f56c6c;
}
</style>
