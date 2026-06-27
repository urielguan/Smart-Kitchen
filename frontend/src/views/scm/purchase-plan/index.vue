<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowDown,
  CircleCheck,
  Clock,
  DataAnalysis,
  Document,
  EditPen,
  Files,
  Money,
  Plus,
  RefreshRight,
  Search,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, genFileId } from 'element-plus'
import type { TableInstance, UploadInstance, UploadProps, UploadRawFile } from 'element-plus'
import { useRouter } from 'vue-router'
import purchaseApi from '@/api/modules/purchase'
import purchaseDemandForecastApi from '@/api/modules/purchase-demand-forecast'
import purchasePlanApi from '@/api/modules/purchase-plan'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import { usePurchaseOrderStore } from '@/stores/modules/purchase-order'
import { usePurchasePlanStore } from '@/stores/modules/purchase-plan'
import { useOrgStore } from '@/stores/modules/org'
import { useUserStore } from '@/stores/modules/user'
import type { PurchaseOrderStatus, PurchaseOrderSupplierOption } from '@/types/purchase'
import type { OrgTreeNode } from '@/types/org'
import type {
  PurchaseDemandForecastMaterialLinkage,
  PurchaseDemandForecastMaterialLinkageItem,
  PurchaseDemandForecastPlanPrefill,
} from '@/types/purchase-demand-forecast'
import type {
  PurchasePlanAttachment,
  PurchasePlanItem,
  PurchasePlanLinkedOrderRecord,
  PurchasePlanMaterialOption,
  PurchasePlanRecipeMaterialLinkage,
  PurchasePlanRecipeMaterialLinkageItem,
  PurchasePlanRelatedDocumentItemPrefill,
  PurchasePlanRelatedDocumentOption,
  PurchasePlanRelatedDocumentType,
  PurchasePlanRecord,
  PurchasePlanReverseAuditPayload,
  PurchasePlanStatistics,
  PurchasePlanStatus,
  PurchasePlanVoidAuditPayload,
  PurchaseOrderLinkItem,
  PurchaseOrderLinkRecord,
} from '@/types/purchase-plan'
import { PURCHASE_PLAN_PERMISSIONS } from '@/constants/permission'

interface OrganizationOption {
  id: number
  orgName: string
}

interface PurchasePlanFormState {
  id: number | null
  planNo: string
  planName: string
  orgId: number | null
  orgName: string
  planDate: string
  createdAt: string
  createdBy: string
  budgetAmount: number | null
  totalAmount: number
  relatedDocument: string
  attachmentName: string
  attachmentUrl: string
  remark: string
  status: PurchasePlanStatus
  auditRemark: string
  auditBy: string | null
  auditAt: string | null
  attachments: PurchasePlanAttachment[]
  items: PurchasePlanItem[]
  orderLinks: PurchaseOrderLinkRecord[]
}

interface PurchaseOrderGenerateItem {
  planItemId: number
  materialName: string
  materialSpec: string
  unit: string
  plannedQuantity: number
  orderedQuantity: number
  remainingQuantity: number
  unitPrice: number | null
  quantityToGenerate: number | null
}

type PurchasePlanMoreAction = 'generateOrder' | 'reverseAudit' | 'void' | 'voidAudit' | 'delete'

interface PurchasePlanMoreActionItem {
  label: string
  command: PurchasePlanMoreAction
  disabled?: boolean
}

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

const userStore = useUserStore()
const orgStore = useOrgStore()
const purchasePlanStore = usePurchasePlanStore()
const purchaseOrderStore = usePurchaseOrderStore()
const router = useRouter()
const MAX_ATTACHMENT_FILE_SIZE = 10 * 1024 * 1024
const MAX_ATTACHMENT_FILE_SIZE_TEXT = '10MB'

const toNullableNumber = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const num = Number(value)
  return Number.isFinite(num) ? num : null
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

ensureUserInfoFromToken()

const ensureUserInfoReady = async () => {
  ensureUserInfoFromToken()
  if (userStore.userInfo?.orgId || !userStore.token) {
    return
  }

  try {
    await userStore.fetchUserInfo()
  } catch (error) {
    console.error('获取采购计划页面用户信息失败:', error)
  } finally {
    ensureUserInfoFromToken()
  }
}

const STATUS_MAP: Record<PurchasePlanStatus, { label: string; type: '' | 'warning' | 'success' | 'danger' | 'info' }> = {
  draft: { label: '草稿', type: 'info' },
  pending: { label: '待审核', type: 'warning' },
  approved: { label: '已审核', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  pending_void_approve: { label: '待作废审核', type: 'warning' },
  voided: { label: '已作废', type: 'info' },
}

const PURCHASE_ORDER_STATUS_MAP: Record<PurchaseOrderStatus, { label: string; type: '' | 'warning' | 'success' | 'danger' | 'info' }> = {
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

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'draft' },
  { label: '待审核', value: 'pending' },
  { label: '已审核', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
  { label: '待作废审核', value: 'pending_void_approve' },
  { label: '已作废', value: 'voided' },
] as const

const createItemId = (() => {
  let seed = 10000
  return () => {
    seed += 1
    return seed
  }
})()

const createAttachmentId = (() => {
  let seed = 900000
  return () => {
    seed += 1
    return seed
  }
})()

const createSearchForm = () => ({
  keyword: purchasePlanStore.searchFormCache.keyword || '',
  status: (purchasePlanStore.searchFormCache.status || '') as PurchasePlanStatus | '',
})

const searchForm = reactive(createSearchForm())

const currentPage = ref(purchasePlanStore.pageNum || 1)
const pageSize = ref(purchasePlanStore.pageSize || 10)
const total = ref(purchasePlanStore.total || 0)

const tableLoading = ref(false)
const formOptionLoading = ref(false)
const formSubmitting = ref(false)
const detailLoading = ref(false)
const relatedDocumentLoading = ref(false)
const auditSubmitting = ref(false)
const reverseSubmitting = ref(false)
const voidSubmitting = ref(false)
const voidAuditSubmitting = ref(false)
const generateSubmitting = ref(false)
const mergeGenerateSubmitting = ref(false)
const purchasePlanTableRef = ref<TableInstance>()

const purchasePlans = ref<PurchasePlanRecord[]>([])
const selectedMergePlans = ref<PurchasePlanRecord[]>([])
const statistics = ref<PurchasePlanStatistics>({
  total: Number(purchasePlanStore.statistics.total || 0),
  pending: Number(purchasePlanStore.statistics.pending || 0),
  approved: Number(purchasePlanStore.statistics.approved || 0),
  totalBudget: roundToCurrency(Number(purchasePlanStore.statistics.totalBudget || 0)),
})

const orgOptions = ref<OrganizationOption[]>([])
const materialOptions = ref<PurchasePlanMaterialOption[]>([])
const relatedDocumentOptions = ref<PurchasePlanRelatedDocumentOption[]>([])
const generateSupplierOptions = ref<PurchaseOrderSupplierOption[]>([])
const generateAttachmentUploadRef = ref<UploadInstance>()
const generateAttachmentFile = ref<File | null>(null)
const attachmentUploading = ref(false)
const persistedAttachmentUrls = ref<string[]>([])
const skipAttachmentCleanup = ref(false)

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const detailVisible = ref(false)
const auditVisible = ref(false)
const reverseVisible = ref(false)
const voidVisible = ref(false)
const voidAuditVisible = ref(false)
const generateVisible = ref(false)
const purchasePlanActivatedOnce = ref(false)

const detailPlan = ref<PurchasePlanRecord | null>(null)
const linkedOrderLoading = ref(false)
const linkedOrderRecords = ref<PurchasePlanLinkedOrderRecord[]>([])
const linkedOrderRowClassNames = computed(() => {
  const classNames: string[] = []
  let previousGroupKey = ''
  let currentClassName = 'linked-order-group-even'

  linkedOrderRecords.value.forEach((record, index) => {
    const groupKey = record.orderNo.trim() || `linked-order-${record.orderId || index}`
    if (index === 0) {
      previousGroupKey = groupKey
    } else if (groupKey !== previousGroupKey) {
      currentClassName = currentClassName === 'linked-order-group-even'
        ? 'linked-order-group-odd'
        : 'linked-order-group-even'
      previousGroupKey = groupKey
    }
    classNames.push(currentClassName)
  })

  return classNames
})
const auditTarget = ref<PurchasePlanRecord | null>(null)
const reverseTarget = ref<PurchasePlanRecord | null>(null)
const voidTarget = ref<PurchasePlanRecord | null>(null)
const voidAuditTarget = ref<PurchasePlanRecord | null>(null)
const generateTarget = ref<PurchasePlanRecord | null>(null)
const generateItems = ref<PurchaseOrderGenerateItem[]>([])
const generateSupplierId = ref<number | null>(null)
let linkedOrderRequestSequence = 0

const auditForm = reactive({
  remark: '',
})

const reverseForm = reactive<PurchasePlanReverseAuditPayload>({
  reason: '',
})

const voidForm = reactive({
  reason: '',
})

const voidAuditForm = reactive<PurchasePlanVoidAuditPayload>({
  approved: true,
  remark: '',
})

function roundToCurrency(value: number) {
  return Number((Number.isFinite(value) ? value : 0).toFixed(2))
}

function roundToQuantity(value: number) {
  return Number((Number.isFinite(value) ? value : 0).toFixed(3))
}

function getGenerateItemSubtotal(item: PurchaseOrderGenerateItem) {
  const quantity = Number(item.quantityToGenerate || 0)
  const unitPrice = Number(item.unitPrice || 0)
  return roundToCurrency(quantity * unitPrice)
}

const formatCurrency = (value: number) => `¥${roundToCurrency(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})}`

const formatNow = () => {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const formatDate = () => formatNow().slice(0, 10)

const generatePlanNo = () => {
  const datePart = formatDate().replace(/-/g, '')
  const sequence = Math.floor(Math.random() * 900 + 100)
  return `PP-${datePart}-${sequence}`
}

const currentUserName = computed(() => userStore.userInfo?.realName || userStore.userInfo?.userName || '管理员')
const getRelatedDocumentTypeLabel = (type: PurchasePlanRelatedDocumentType) => {
  return type === 'purchaseDemandForecast' ? '采购需求预测单' : '菜谱计划单'
}

const getRelatedDocumentOptionByNo = (documentNo: string) => {
  return relatedDocumentOptions.value.find((item) => item.documentNo === documentNo) || null
}

const getMaterialById = (materialId: number | null) => {
  return materialOptions.value.find((item) => item.id === materialId) || null
}

const getOrgNameById = (orgId: number | null) => {
  if (!orgId) return ''
  return orgOptions.value.find((item) => item.id === orgId)?.orgName
    || (userStore.userInfo?.orgId === orgId ? userStore.userInfo.orgName : '')
}

const createEmptyItem = (): PurchasePlanItem => ({
  id: createItemId(),
  materialId: null,
  materialName: '',
  materialSpec: '',
  unit: '',
  quantity: null,
  unitPrice: null,
  subtotal: 0,
  orderedQuantity: 0,
  remark: '',
  sourceForecastDetailId: null,
  lockMaterialSpec: false,
  lockUnitPrice: false,
})

const normalizeItem = (item: Partial<PurchasePlanItem>): PurchasePlanItem => ({
  id: Number(item.id ?? createItemId()),
  materialId: item.materialId ?? null,
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  quantity: item.quantity === null || item.quantity === undefined ? null : Number(item.quantity),
  unitPrice: item.unitPrice === null || item.unitPrice === undefined ? null : Number(item.unitPrice),
  subtotal: roundToCurrency(Number(item.subtotal || 0)),
  orderedQuantity: roundToQuantity(Number(item.orderedQuantity || 0)),
  remark: item.remark || '',
  sourceForecastDetailId: item.sourceForecastDetailId === null || item.sourceForecastDetailId === undefined
    ? null
    : Number(item.sourceForecastDetailId),
  lockMaterialSpec: Boolean(item.lockMaterialSpec),
  lockUnitPrice: Boolean(item.lockUnitPrice),
})

const normalizeOrderLinkItem = (item: Partial<PurchaseOrderLinkItem>): PurchaseOrderLinkItem => ({
  id: item.id ? Number(item.id) : undefined,
  planItemId: Number(item.planItemId || 0),
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  quantity: roundToQuantity(Number(item.quantity || 0)),
  unitPrice: roundToCurrency(Number(item.unitPrice || 0)),
  subtotal: roundToCurrency(Number(item.subtotal || 0)),
  remark: item.remark || '',
})

const normalizeLinkedOrderRecord = (record: Partial<PurchasePlanLinkedOrderRecord>): PurchasePlanLinkedOrderRecord => ({
  orderId: Number(record.orderId || 0),
  orderNo: record.orderNo || '',
  status: record.status || '',
  materialName: record.materialName || '',
  materialSpec: record.materialSpec || '',
  unit: record.unit || '',
  quantity: roundToQuantity(Number(record.quantity || 0)),
  operatorName: record.operatorName || '',
  createdAt: record.createdAt || '',
})

const normalizeAttachment = (attachment: Partial<PurchasePlanAttachment>): PurchasePlanAttachment => ({
  id: Number(attachment.id || createAttachmentId()),
  name: attachment.name || '附件',
  size: attachment.size || '',
  url: attachment.url || '',
  sortOrder: attachment.sortOrder ? Number(attachment.sortOrder) : undefined,
})

const normalizeRecord = (record: Partial<PurchasePlanRecord>): PurchasePlanRecord => {
  const items = Array.isArray(record.items) ? record.items.map((item) => normalizeItem(item)) : []
  const orderLinks = Array.isArray(record.orderLinks)
    ? record.orderLinks.map((order) => ({
        id: Number(order.id || 0),
        orderNo: order.orderNo || '',
        createdAt: order.createdAt || '',
        createdBy: order.createdBy || '',
        items: Array.isArray(order.items) ? order.items.map((item) => normalizeOrderLinkItem(item)) : [],
      }))
    : []
  const attachments = Array.isArray(record.attachments) && record.attachments.length
    ? record.attachments.map((attachment) => normalizeAttachment(attachment))
    : (record.attachmentUrl
        ? [normalizeAttachment({
            name: record.attachmentName || '附件',
            size: '',
            url: record.attachmentUrl,
            sortOrder: 1,
          })]
        : [])

  return {
    id: Number(record.id || 0),
    planNo: record.planNo || '',
    planName: record.planName || '',
    orgId: record.orgId ?? null,
    orgName: record.orgName || getOrgNameById(record.orgId ?? null),
    planDate: record.planDate || '',
    createdAt: record.createdAt || '',
    createdBy: record.createdBy || '',
    budgetAmount: roundToCurrency(Number(record.budgetAmount || 0)),
    totalAmount: roundToCurrency(Number(record.totalAmount || 0)),
    relatedDocument: record.relatedDocument || '',
    attachmentName: record.attachmentName || '',
    attachmentUrl: record.attachmentUrl || '',
    remark: record.remark || '',
    status: (record.status || 'draft') as PurchasePlanStatus,
    deleted: Boolean(record.deleted),
    auditRemark: record.auditRemark || '',
    auditBy: record.auditBy || null,
    auditAt: record.auditAt || null,
    voidOriginalStatus: (record.voidOriginalStatus || null) as Extract<PurchasePlanStatus, 'approved' | 'rejected'> | null,
    voidReason: record.voidReason || '',
    voidRequestedBy: record.voidRequestedBy || null,
    voidRequestedAt: record.voidRequestedAt || null,
    voidAuditBy: record.voidAuditBy || null,
    voidAuditAt: record.voidAuditAt || null,
    voidAuditRemark: record.voidAuditRemark || '',
    mergeLocked: Boolean(record.mergeLocked),
    mergeOrderId: record.mergeOrderId === undefined || record.mergeOrderId === null ? null : Number(record.mergeOrderId),
    generatedOrderCount: Number(record.generatedOrderCount ?? orderLinks.length ?? 0),
    allItemsGenerated: Boolean(record.allItemsGenerated ?? (items.length > 0 && items.every((item) => getRemainingQuantity(item) <= 0))),
    attachments,
    items,
    orderLinks,
  }
}

const createFormState = (orgId?: number | null, orgName?: string): PurchasePlanFormState => ({
  id: null,
  planNo: generatePlanNo(),
  planName: '',
  orgId: orgId ?? userStore.userInfo?.orgId ?? null,
  orgName: orgName || getOrgNameById(orgId ?? userStore.userInfo?.orgId ?? null),
  planDate: formatDate(),
  createdAt: formatNow(),
  createdBy: currentUserName.value,
  budgetAmount: null,
  totalAmount: 0,
  relatedDocument: '',
  attachmentName: '',
  attachmentUrl: '',
  remark: '',
  status: 'draft',
  auditRemark: '',
  auditBy: null,
  auditAt: null,
  attachments: [],
  items: [createEmptyItem()],
  orderLinks: [],
})

const createForecastPrefilledItem = (
  item: PurchaseDemandForecastPlanPrefill['items'][number],
): PurchasePlanItem => ({
  id: createItemId(),
  materialId: item.materialId ?? null,
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  quantity: item.quantity ?? null,
  unitPrice: item.estimatedUnitPrice ?? null,
  subtotal: roundToCurrency(Number(item.quantity || 0) * Number(item.estimatedUnitPrice || 0)),
  orderedQuantity: 0,
  remark: '',
  sourceForecastDetailId: item.forecastDetailId ?? null,
  lockMaterialSpec: true,
  lockUnitPrice: false,
})

const createRelatedDocumentPrefilledItem = (
  item: PurchasePlanRelatedDocumentItemPrefill,
): PurchasePlanItem => ({
  id: createItemId(),
  materialId: item.materialId ?? null,
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  quantity: item.quantity ?? null,
  unitPrice: item.unitPrice ?? null,
  subtotal: roundToCurrency(Number(item.quantity || 0) * Number(item.unitPrice || 0)),
  orderedQuantity: 0,
  remark: '',
  sourceForecastDetailId: item.sourceForecastDetailId ?? null,
  lockMaterialSpec: true,
  lockUnitPrice: false,
})

const normalizeForecastMaterialLinkageItem = (
  item: Partial<PurchaseDemandForecastMaterialLinkageItem>,
): PurchaseDemandForecastMaterialLinkageItem => ({
  forecastDetailId: Number(item.forecastDetailId || 0),
  materialId: Number(item.materialId || 0),
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  originalQty: roundToQuantity(Number(item.originalQty || 0)),
  occupiedQty: roundToQuantity(Number(item.occupiedQty || 0)),
  availableQty: roundToQuantity(Number(item.availableQty || 0)),
  materialPlanStatus: item.materialPlanStatus || '未占用',
})

const normalizeForecastMaterialLinkage = (
  value: Partial<PurchaseDemandForecastMaterialLinkage>,
): PurchaseDemandForecastMaterialLinkage => ({
  forecastId: Number(value.forecastId || 0),
  forecastNo: value.forecastNo || '',
  orgId: Number(value.orgId || 0),
  orgName: value.orgName || '',
  materialPlanStatus: value.materialPlanStatus || '未占用',
  items: Array.isArray(value.items) ? value.items.map((item) => normalizeForecastMaterialLinkageItem(item)) : [],
})

const normalizeRecipePlanMaterialLinkageItem = (
  item: Partial<PurchasePlanRecipeMaterialLinkageItem>,
): PurchasePlanRecipeMaterialLinkageItem => ({
  materialId: Number(item.materialId || 0),
  materialName: item.materialName || '',
  materialSpec: item.materialSpec || '',
  unit: item.unit || '',
  originalQty: roundToQuantity(Number(item.originalQty || 0)),
  occupiedQty: roundToQuantity(Number(item.occupiedQty || 0)),
  availableQty: roundToQuantity(Number(item.availableQty || 0)),
  materialPlanStatus: item.materialPlanStatus || '未占用',
})

const normalizeRecipePlanMaterialLinkage = (
  value: Partial<PurchasePlanRecipeMaterialLinkage>,
): PurchasePlanRecipeMaterialLinkage => ({
  recipePlanId: Number(value.recipePlanId || 0),
  planCode: value.planCode || '',
  orgId: Number(value.orgId || 0),
  orgName: value.orgName || '',
  materialPlanStatus: value.materialPlanStatus || '未占用',
  items: Array.isArray(value.items) ? value.items.map((item) => normalizeRecipePlanMaterialLinkageItem(item)) : [],
})

const toFormState = (record: PurchasePlanRecord): PurchasePlanFormState => ({
  id: record.id,
  planNo: record.planNo,
  planName: record.planName,
  orgId: record.orgId,
  orgName: record.orgName,
  planDate: record.planDate,
  createdAt: record.createdAt,
  createdBy: record.createdBy,
  budgetAmount: record.budgetAmount,
  totalAmount: record.totalAmount,
  relatedDocument: record.relatedDocument,
  attachmentName: record.attachmentName,
  attachmentUrl: record.attachmentUrl,
  remark: record.remark,
  status: record.status,
  auditRemark: record.auditRemark,
  auditBy: record.auditBy,
  auditAt: record.auditAt,
  attachments: record.attachments.map((attachment) => normalizeAttachment(attachment)),
  items: record.items.length ? record.items.map((item) => normalizeItem(item)) : [createEmptyItem()],
  orderLinks: record.orderLinks.map((order) => ({
    ...order,
    items: order.items.map((item) => normalizeOrderLinkItem(item)),
  })),
})

const formState = ref<PurchasePlanFormState>(createFormState())
const forecastMaterialLinkage = ref<PurchaseDemandForecastMaterialLinkage | null>(null)
const recipePlanMaterialLinkage = ref<PurchasePlanRecipeMaterialLinkage | null>(null)

const syncItemDerivedFields = (item: PurchasePlanItem) => {
  const material = getMaterialById(item.materialId)

  item.materialName = material?.name || item.materialName || ''
  item.unit = material?.unit || ''
  if (material && (!item.materialSpec || item.materialSpec !== material.spec)) {
    item.materialSpec = material.spec || ''
  }
  if (material && (item.unitPrice === null || item.unitPrice === 0)) {
    item.unitPrice = roundToCurrency(Number(material.referencePrice || 0))
  }
  item.subtotal = roundToCurrency((Number(item.quantity) || 0) * (Number(item.unitPrice) || 0))
}

const syncPlanTotals = (state: PurchasePlanFormState) => {
  state.items.forEach((item) => syncItemDerivedFields(item))
  state.totalAmount = roundToCurrency(state.items.reduce((sum, item) => sum + item.subtotal, 0))
}

const clearForecastMaterialLinkage = () => {
  forecastMaterialLinkage.value = null
}

const clearRecipePlanMaterialLinkage = () => {
  recipePlanMaterialLinkage.value = null
}

const clearRelatedDocumentMaterialLinkage = () => {
  clearForecastMaterialLinkage()
  clearRecipePlanMaterialLinkage()
}

const getForecastMaterialLinkageItem = (materialId: number | null) => {
  if (!materialId || !forecastMaterialLinkage.value) {
    return null
  }
  return forecastMaterialLinkage.value.items.find((item) => item.materialId === materialId) || null
}

const getRecipePlanMaterialLinkageItem = (materialId: number | null) => {
  if (!materialId || !recipePlanMaterialLinkage.value) {
    return null
  }
  return recipePlanMaterialLinkage.value.items.find((item) => item.materialId === materialId) || null
}

const getForecastMaterialAvailableQty = (materialId: number | null) => {
  const linkageItem = getForecastMaterialLinkageItem(materialId)
  return linkageItem ? roundToQuantity(Number(linkageItem.availableQty || 0)) : 0
}

const getRecipePlanMaterialAvailableQty = (materialId: number | null) => {
  const linkageItem = getRecipePlanMaterialLinkageItem(materialId)
  return linkageItem ? roundToQuantity(Number(linkageItem.availableQty || 0)) : 0
}

const getForecastMaterialRemainingQtyForItem = (item: PurchasePlanItem) => {
  if (!item.materialId) {
    return Number.MAX_SAFE_INTEGER
  }
  const linkageItem = getForecastMaterialLinkageItem(item.materialId)
  if (!linkageItem) {
    return Number.MAX_SAFE_INTEGER
  }
  const otherRowsQty = formState.value.items.reduce((sum, current) => {
    if (current.id === item.id || current.materialId !== item.materialId) {
      return sum
    }
    return roundToQuantity(sum + Number(current.quantity || 0))
  }, 0)
  return roundToQuantity(Math.max(0, getForecastMaterialAvailableQty(item.materialId) - otherRowsQty))
}

const getRecipePlanMaterialRemainingQtyForItem = (item: PurchasePlanItem) => {
  if (!item.materialId) {
    return Number.MAX_SAFE_INTEGER
  }
  const linkageItem = getRecipePlanMaterialLinkageItem(item.materialId)
  if (!linkageItem) {
    return Number.MAX_SAFE_INTEGER
  }
  const otherRowsQty = formState.value.items.reduce((sum, current) => {
    if (current.id === item.id || current.materialId !== item.materialId) {
      return sum
    }
    return roundToQuantity(sum + Number(current.quantity || 0))
  }, 0)
  return roundToQuantity(Math.max(0, getRecipePlanMaterialAvailableQty(item.materialId) - otherRowsQty))
}

const buildLinkedMaterialQuantityExceededMessage = (materialName: string, availableQty: number) => {
  const resolvedMaterialName = materialName || '当前物料'
  const resolvedAvailableQty = roundToQuantity(availableQty)
  return `物料“${resolvedMaterialName}”关联数量不能超过当前可关联数量（${resolvedAvailableQty}）`
}

const buildForecastMaterialQuantityExceededMessage = (item: PurchasePlanItem, availableQty: number) => {
  const materialName = item.materialName || getForecastMaterialLinkageItem(item.materialId)?.materialName || '当前物料'
  return buildLinkedMaterialQuantityExceededMessage(materialName, availableQty)
}

const buildRecipePlanMaterialQuantityExceededMessage = (item: PurchasePlanItem, availableQty: number) => {
  const materialName = item.materialName || getRecipePlanMaterialLinkageItem(item.materialId)?.materialName || '当前物料'
  return buildLinkedMaterialQuantityExceededMessage(materialName, availableQty)
}

const handleForecastLinkedQuantityChange = (item: PurchasePlanItem) => {
  if (!item.materialId) {
    return
  }
  const maxQty = getForecastMaterialRemainingQtyForItem(item)
  if (maxQty === Number.MAX_SAFE_INTEGER) {
    return
  }
  const currentQty = roundToQuantity(Number(item.quantity || 0))
  if (currentQty <= maxQty + 0.0001) {
    return
  }
  item.quantity = maxQty > 0 ? maxQty : null
  syncItemDerivedFields(item)
  syncPlanTotals(formState.value)
  ElMessage.warning(buildForecastMaterialQuantityExceededMessage(item, maxQty))
}

const handleRecipePlanLinkedQuantityChange = (item: PurchasePlanItem) => {
  if (!item.materialId) {
    return
  }
  const maxQty = getRecipePlanMaterialRemainingQtyForItem(item)
  if (maxQty === Number.MAX_SAFE_INTEGER) {
    return
  }
  const currentQty = roundToQuantity(Number(item.quantity || 0))
  if (currentQty <= maxQty + 0.0001) {
    return
  }
  item.quantity = maxQty > 0 ? maxQty : null
  syncItemDerivedFields(item)
  syncPlanTotals(formState.value)
  ElMessage.warning(buildRecipePlanMaterialQuantityExceededMessage(item, maxQty))
}

const getLinkedMaterialRemainingQtyForItem = (item: PurchasePlanItem) => {
  if (getForecastMaterialLinkageItem(item.materialId)) {
    return getForecastMaterialRemainingQtyForItem(item)
  }
  if (getRecipePlanMaterialLinkageItem(item.materialId)) {
    return getRecipePlanMaterialRemainingQtyForItem(item)
  }
  return Number.MAX_SAFE_INTEGER
}

const handleLinkedQuantityChange = (item: PurchasePlanItem) => {
  if (getForecastMaterialLinkageItem(item.materialId)) {
    handleForecastLinkedQuantityChange(item)
    return
  }
  if (getRecipePlanMaterialLinkageItem(item.materialId)) {
    handleRecipePlanLinkedQuantityChange(item)
  }
}

const validateForecastMaterialQuantities = () => {
  if (!forecastMaterialLinkage.value) {
    return true
  }

  const requestedMap = new Map<number, number>()
  const materialMap = new Map<number, PurchasePlanItem>()
  formState.value.items.forEach((item) => {
    if (!item.materialId || !getForecastMaterialLinkageItem(item.materialId)) {
      return
    }
    const quantity = roundToQuantity(Number(item.quantity || 0))
    requestedMap.set(item.materialId, roundToQuantity((requestedMap.get(item.materialId) || 0) + quantity))
    if (!materialMap.has(item.materialId)) {
      materialMap.set(item.materialId, item)
    }
  })

  for (const [materialId, quantity] of requestedMap.entries()) {
    const availableQty = getForecastMaterialAvailableQty(materialId)
    if (quantity > availableQty + 0.0001) {
      ElMessage.warning(buildForecastMaterialQuantityExceededMessage(materialMap.get(materialId)!, availableQty))
      return false
    }
  }
  return true
}

const validateRecipePlanMaterialQuantities = () => {
  if (!recipePlanMaterialLinkage.value) {
    return true
  }

  const requestedMap = new Map<number, number>()
  const materialMap = new Map<number, PurchasePlanItem>()
  formState.value.items.forEach((item) => {
    if (!item.materialId || !getRecipePlanMaterialLinkageItem(item.materialId)) {
      return
    }
    const quantity = roundToQuantity(Number(item.quantity || 0))
    requestedMap.set(item.materialId, roundToQuantity((requestedMap.get(item.materialId) || 0) + quantity))
    if (!materialMap.has(item.materialId)) {
      materialMap.set(item.materialId, item)
    }
  })

  for (const [materialId, quantity] of requestedMap.entries()) {
    const availableQty = getRecipePlanMaterialAvailableQty(materialId)
    if (quantity > availableQty + 0.0001) {
      ElMessage.warning(buildRecipePlanMaterialQuantityExceededMessage(materialMap.get(materialId)!, availableQty))
      return false
    }
  }
  return true
}

const refreshForecastMaterialLinkage = async () => {
  const forecastNo = formState.value.relatedDocument.trim()
  if (!forecastNo) {
    clearForecastMaterialLinkage()
    return
  }
  const selectedOption = getRelatedDocumentOptionByNo(forecastNo)
  if (selectedOption && selectedOption.documentType !== 'purchaseDemandForecast') {
    clearForecastMaterialLinkage()
    return
  }
  try {
    const res = await purchaseDemandForecastApi.getMaterialLinkage({
      forecastNo,
      excludePlanId: formMode.value === 'edit' ? formState.value.id || undefined : undefined,
    })
    forecastMaterialLinkage.value = res.data ? normalizeForecastMaterialLinkage(res.data) : null
  } catch (error) {
    forecastMaterialLinkage.value = null
    console.error('加载采购需求预测可关联数量失败:', error)
  }
}

const refreshRecipePlanMaterialLinkage = async () => {
  const documentNo = formState.value.relatedDocument.trim()
  if (!documentNo) {
    clearRecipePlanMaterialLinkage()
    return
  }
  const selectedOption = getRelatedDocumentOptionByNo(documentNo)
  if (!selectedOption || selectedOption.documentType !== 'recipePlan') {
    clearRecipePlanMaterialLinkage()
    return
  }
  try {
    const res = await purchasePlanApi.getRecipePlanMaterialLinkage(
      selectedOption.id,
      formMode.value === 'edit' ? formState.value.id || undefined : undefined,
    )
    recipePlanMaterialLinkage.value = res.data ? normalizeRecipePlanMaterialLinkage(res.data) : null
  } catch (error) {
    recipePlanMaterialLinkage.value = null
    console.error('加载菜谱计划可关联数量失败:', error)
  }
}

watch(
  () => formState.value.items,
  () => {
    syncPlanTotals(formState.value)
  },
  { deep: true }
)

watch(
  searchForm,
  (value) => {
    purchasePlanStore.updateSearchFormCache({
      keyword: value.keyword,
      status: value.status || '',
    })
  },
  { deep: true }
)

watch(
  () => userStore.userInfo?.orgId,
  async (orgId, previousOrgId) => {
    if (!orgId || orgId === previousOrgId) {
      return
    }
    ensureCurrentOrgOption()
    if (!formState.value.orgId) {
      formState.value.orgId = orgId
      formState.value.orgName = getOrgNameById(orgId)
    }
    await refreshListAndStatistics(1)
  }
)

const relatedDocumentGroups = computed(() => {
  const currentValue = formState.value.relatedDocument.trim()
  const currentOption = currentValue && !relatedDocumentOptions.value.some((item) => item.documentNo === currentValue)
    ? {
        label: '当前已关联',
        options: [
          {
            value: currentValue,
            label: `${currentValue}｜当前记录保留`,
          },
        ],
      }
    : null

  const forecastOptions = relatedDocumentOptions.value
    .filter((item) => item.documentType === 'purchaseDemandForecast')
    .map((item) => ({
      value: item.documentNo,
      label: item.optionLabel,
    }))

  const recipeOptions = relatedDocumentOptions.value
    .filter((item) => item.documentType === 'recipePlan')
    .map((item) => ({
      value: item.documentNo,
      label: item.optionLabel,
    }))

  return [
    ...(currentOption ? [currentOption] : []),
    { label: getRelatedDocumentTypeLabel('recipePlan'), options: recipeOptions },
    { label: getRelatedDocumentTypeLabel('purchaseDemandForecast'), options: forecastOptions },
  ].filter((group) => group.options.length)
})

const formDialogTitle = computed(() => {
  return formMode.value === 'create' ? '新增采购计划' : '编辑采购计划'
})

const currentGenerateAttachmentDisplayName = computed(() => {
  return generateAttachmentFile.value?.name || ''
})

const generateOrderSummary = computed(() => {
  return generateItems.value.reduce((summary, item) => {
    const quantity = Number(item.quantityToGenerate || 0)
    return {
      quantity: roundToQuantity(summary.quantity + quantity),
      amount: roundToCurrency(summary.amount + getGenerateItemSubtotal(item)),
    }
  }, { quantity: 0, amount: 0 })
})

const selectedMergePlanCount = computed(() => selectedMergePlans.value.length)

const getOrderedQuantity = (item: PurchasePlanItem) => roundToQuantity(item.orderedQuantity || 0)
const getRemainingQuantity = (item: PurchasePlanItem) => {
  const quantity = Number(item.quantity || 0)
  return roundToQuantity(Math.max(0, quantity - getOrderedQuantity(item)))
}
const hasOrderedItems = (row: PurchasePlanRecord) => row.items.some((item) => getOrderedQuantity(item) > 0)
const hasRemainingItems = (row: PurchasePlanRecord) => row.items.some((item) => getRemainingQuantity(item) > 0)
const isAllItemsOccupied = (row: PurchasePlanRecord) => row.items.length > 0 && row.items.every((item) => getRemainingQuantity(item) <= 0)
const canDelete = (row: PurchasePlanRecord) => !row.deleted && (row.status === 'draft' || row.status === 'pending')
const canGeneratePurchaseOrder = (row: PurchasePlanRecord) => !row.deleted && row.status === 'approved' && hasRemainingItems(row)
const canMergeGenerate = (row: PurchasePlanRecord) => getMergeDisabledReason(row) === ''
const getGeneratedOrderCount = (row: PurchasePlanRecord) => row.generatedOrderCount || row.orderLinks.length
const getReverseAuditRelationStatusLabel = (row: PurchasePlanRecord) => {
  const occupancyLabel = getOccupancyStatusLabel(row)
  if (occupancyLabel) {
    return occupancyLabel
  }
  return getGeneratedOrderCount(row) > 0 ? '已关联订单' : '未关联订单'
}
const canVoid = (row: PurchasePlanRecord) => !row.deleted && (row.status === 'rejected' || (row.status === 'approved' && getGeneratedOrderCount(row) === 0))
const canVoidAudit = (row: PurchasePlanRecord) => !row.deleted && row.status === 'pending_void_approve'
const hasPlanPermission = (permission: string) => userStore.hasPermission(permission)
function getMergeDisabledReason(row: PurchasePlanRecord) {
  if (row.deleted) return '已删除采购计划不可参与合并生成'
  if (row.status !== 'approved') {
    return `当前状态为${getStatusLabel(row.status)}，仅已审核采购计划可参与合并生成`
  }
  if (!hasRemainingItems(row)) return '当前采购计划已无可合并的未履约物料'
  return ''
}
const getGenerateOrderActionLabel = (row: PurchasePlanRecord) => {
  if (canGeneratePurchaseOrder(row)) return '关联生成采购订单'
  return '已全部生成'
}
const getOccupancyStatusLabel = (row: PurchasePlanRecord) => {
  if (isAllItemsOccupied(row)) return '全部占用'
  if (hasOrderedItems(row) && hasRemainingItems(row)) return '部分占用'
  return ''
}
const getOccupancyStatusType = (row: PurchasePlanRecord) => {
  return getOccupancyStatusLabel(row) === '全部占用' ? 'danger' : 'warning'
}
const getMoreActionItems = (row: PurchasePlanRecord): PurchasePlanMoreActionItem[] => {
  const items: PurchasePlanMoreActionItem[] = []

  if (hasPlanPermission(PURCHASE_PLAN_PERMISSIONS.GENERATE_ORDER) && row.status === 'approved') {
    items.push({
      label: getGenerateOrderActionLabel(row),
      command: 'generateOrder',
      disabled: !canGeneratePurchaseOrder(row),
    })
  }

  if (hasPlanPermission(PURCHASE_PLAN_PERMISSIONS.APPROVE) && canReverseAudit(row)) {
    items.push({ label: '反审核', command: 'reverseAudit' })
  }

  if (hasPlanPermission(PURCHASE_PLAN_PERMISSIONS.DELETE) && canVoid(row)) {
    items.push({ label: '作废', command: 'void' })
  }

  if (hasPlanPermission(PURCHASE_PLAN_PERMISSIONS.APPROVE) && canVoidAudit(row)) {
    items.push({ label: '作废审核', command: 'voidAudit' })
  }

  if (hasPlanPermission(PURCHASE_PLAN_PERMISSIONS.DELETE) && canDelete(row)) {
    items.push({ label: '删除', command: 'delete' })
  }

  return items
}
const hasMoreActionItems = (row: PurchasePlanRecord) => getMoreActionItems(row).length > 0
const shouldShowVoidSection = (row: PurchasePlanRecord) => {
  return row.status === 'pending_void_approve'
    || row.status === 'voided'
    || Boolean(row.voidReason || row.voidRequestedAt || row.voidAuditAt)
}
const formatLinkedItems = (items: PurchaseOrderLinkItem[]) => {
  return items.map((item) => `${item.materialName}${item.quantity}${item.unit}`).join('、')
}

const getStatusLabel = (status: PurchasePlanStatus) => STATUS_MAP[status]?.label || status
const getStatusType = (status: PurchasePlanStatus) => STATUS_MAP[status]?.type || 'info'
const getPurchaseOrderStatusLabel = (status: string) =>
  PURCHASE_ORDER_STATUS_MAP[status as PurchaseOrderStatus]?.label || status || '—'
const getPurchaseOrderStatusType = (status: string) =>
  PURCHASE_ORDER_STATUS_MAP[status as PurchaseOrderStatus]?.type || 'info'
const getAuditResultLabel = (row: PurchasePlanRecord) => {
  if ((row.status === 'pending_void_approve' || row.status === 'voided') && row.voidOriginalStatus) {
    return getStatusLabel(row.voidOriginalStatus)
  }
  return getStatusLabel(row.status)
}
const getLinkedOrderRowClassName = ({ rowIndex }: { rowIndex: number }) =>
  linkedOrderRowClassNames.value[rowIndex] || ''
const canEdit = (row: PurchasePlanRecord) => !row.deleted && (row.status === 'draft' || row.status === 'rejected')
const canAudit = (row: PurchasePlanRecord) => !row.deleted && row.status === 'pending'
const canReverseAudit = (row: PurchasePlanRecord) => !row.deleted && row.status === 'approved'

const isPersistedAttachment = (attachment: PurchasePlanAttachment) => {
  return !!attachment.url && persistedAttachmentUrls.value.includes(attachment.url)
}

const deleteTemporaryAttachment = async (attachment: PurchasePlanAttachment) => {
  if (!attachment.url || isPersistedAttachment(attachment)) {
    return
  }
  try {
    await purchasePlanApi.deleteAttachment(attachment.url, attachment.name)
  } catch {
    // 统一错误提示已由请求拦截器处理
  }
}

const cleanupTransientAttachments = async () => {
  const transientAttachments = formState.value.attachments.filter((attachment) => attachment.url && !isPersistedAttachment(attachment))
  if (!transientAttachments.length) {
    return
  }
  await Promise.allSettled(transientAttachments.map((attachment) => deleteTemporaryAttachment(attachment)))
}

const handleAttachmentFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  if (!input.files?.length) {
    return
  }

  attachmentUploading.value = true
  try {
    for (const file of Array.from(input.files)) {
      if (!validateAttachmentFileSize(file)) {
        continue
      }
      const alreadyExists = formState.value.attachments.some((attachment) => attachment.name === file.name)
      if (alreadyExists) {
        ElMessage.warning(`文件 "${file.name}" 已添加`)
        continue
      }

      try {
        const res = await purchasePlanApi.uploadAttachment(file)
        if (res.code === 'SUCCESS' && res.data) {
          formState.value.attachments.push(normalizeAttachment(res.data))
        }
      } catch {
        // 统一错误提示已由请求拦截器处理
      }
    }
  } finally {
    attachmentUploading.value = false
    input.value = ''
  }
}

const removeAttachment = async (index: number) => {
  const attachment = formState.value.attachments[index]
  if (!attachment) {
    return
  }
  await deleteTemporaryAttachment(attachment)
  formState.value.attachments.splice(index, 1)
}

const handleGenerateAttachmentChange: UploadProps['onChange'] = (uploadFile) => {
  const rawFile = uploadFile.raw ?? null
  if (!validateAttachmentFileSize(rawFile, () => {
    generateAttachmentFile.value = null
    generateAttachmentUploadRef.value?.clearFiles()
  })) {
    return
  }
  generateAttachmentFile.value = rawFile
}

const handleGenerateAttachmentExceed: UploadProps['onExceed'] = (files) => {
  const rawFile = files[0] as UploadRawFile
  generateAttachmentUploadRef.value?.clearFiles()
  if (!validateAttachmentFileSize(rawFile, () => {
    generateAttachmentFile.value = null
  })) {
    return
  }
  rawFile.uid = genFileId()
  generateAttachmentUploadRef.value?.handleStart(rawFile)
  generateAttachmentFile.value = rawFile
}

const clearSelectedGenerateAttachment = () => {
  generateAttachmentFile.value = null
  generateAttachmentUploadRef.value?.clearFiles()
}

const previewAttachment = (url?: string) => {
  if (!url) {
    ElMessage.warning('暂无可查看的附件')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

const downloadAttachmentFile = async (attachment?: Pick<PurchasePlanAttachment, 'url' | 'name'> | null) => {
  if (!attachment?.url) {
    ElMessage.warning('暂无可下载的附件')
    return
  }
  await purchasePlanApi.downloadAttachmentByUrl(attachment.url, attachment.name)
}

const ensureCurrentOrgOption = () => {
  const userOrgId = userStore.userInfo?.orgId
  const userOrgName = userStore.userInfo?.orgName
  if (!userOrgId || !userOrgName) return
  if (!orgOptions.value.some((item) => item.id === userOrgId)) {
    orgOptions.value.unshift({
      id: userOrgId,
      orgName: userOrgName,
    })
  }
}

const getDefaultOrgId = () => {
  ensureUserInfoFromToken()
  ensureCurrentOrgOption()
  return userStore.userInfo?.orgId ?? formState.value.orgId ?? orgOptions.value[0]?.id ?? null
}

const getCurrentUserOrgId = () => {
  ensureUserInfoFromToken()
  return userStore.userInfo?.orgId ?? formState.value.orgId ?? orgOptions.value[0]?.id ?? null
}

const syncCurrentOrgName = (records: PurchasePlanRecord[]) => {
  const userOrgId = getCurrentUserOrgId()
  if (!userOrgId || userStore.userInfo?.orgName) {
    return
  }
  const matched = records.find((item) => item.orgId === userOrgId && item.orgName)
  if (!matched) {
    return
  }
  userStore.setUserInfo({
    ...userStore.userInfo,
    id: userStore.userInfo?.id || 0,
    userName: userStore.userInfo?.userName || '用户',
    realName: userStore.userInfo?.realName || userStore.userInfo?.userName || '用户',
    orgId: userOrgId,
    orgName: matched.orgName,
    roles: userStore.userInfo?.roles || [],
    permissions: userStore.userInfo?.permissions || [],
  })
  ensureCurrentOrgOption()
}

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

const mergeMaterialOptions = (baseList: PurchasePlanMaterialOption[], items: PurchasePlanItem[]) => {
  const merged = [...baseList]
  items.forEach((item) => {
    if (item.materialId && item.materialName && !merged.some((option) => option.id === item.materialId)) {
      merged.push({
        id: item.materialId,
        name: item.materialName,
        unit: item.unit,
        spec: item.materialSpec,
        referencePrice: Number(item.unitPrice || 0),
      })
    }
  })
  return merged
}

const sanitizeFormItems = () => {
  formState.value.items.forEach((item) => {
    if (item.materialId && !materialOptions.value.some((option) => option.id === item.materialId)) {
      item.materialId = null
      item.materialName = ''
      item.materialSpec = ''
      item.unit = ''
      item.unitPrice = null
    }
    syncItemDerivedFields(item)
  })
}

const loadRelatedDocumentOptions = async (orgId: number | null) => {
  if (!orgId) {
    relatedDocumentOptions.value = []
    return
  }

  relatedDocumentLoading.value = true
  try {
    const res = await purchasePlanApi.getRelatedDocuments({ orgId })
    relatedDocumentOptions.value = Array.isArray(res.data) ? res.data : []
  } finally {
    relatedDocumentLoading.value = false
  }
}

const applyRelatedDocumentPrefillItems = (items: PurchasePlanRelatedDocumentItemPrefill[]) => {
  const nextItems = items.length
    ? items.map((item) => createRelatedDocumentPrefilledItem(item))
    : [createEmptyItem()]
  formState.value.items = nextItems
  materialOptions.value = mergeMaterialOptions(materialOptions.value, nextItems)
  syncPlanTotals(formState.value)
}

const loadRelatedDocumentItems = async (documentNo: string) => {
  const selectedOption = getRelatedDocumentOptionByNo(documentNo)
  if (!selectedOption) {
    return
  }

  const res = await purchasePlanApi.getRelatedDocumentItems(selectedOption.documentType, selectedOption.id)
  applyRelatedDocumentPrefillItems(Array.isArray(res.data) ? res.data : [])
}

const loadOrganizations = async () => {
  const flatten = (nodes: OrgTreeNode[], result: OrganizationOption[]) => {
    nodes.forEach((node) => {
      result.push({ id: node.id, orgName: node.orgName })
      if (Array.isArray(node.children) && node.children.length) {
        flatten(node.children, result)
      }
    })
  }

  const options: OrganizationOption[] = []
  if (!orgStore.treeData.length) {
    await orgStore.fetchTree()
  }
  flatten(orgStore.treeData || [], options)

  const deduped = new Map<number, OrganizationOption>()
  options.forEach((item) => {
    if (!deduped.has(item.id)) {
      deduped.set(item.id, item)
    }
  })
  orgOptions.value = Array.from(deduped.values())
  ensureCurrentOrgOption()
}

const fetchStatistics = async () => {
  try {
    const res = await purchasePlanApi.getStatistics()
    statistics.value = {
      total: Number(res.data.total || 0),
      pending: Number(res.data.pending || 0),
      approved: Number(res.data.approved || 0),
      totalBudget: roundToCurrency(Number(res.data.totalBudget || 0)),
    }
    purchasePlanStore.setStatisticsCache(statistics.value)
    return true
  } catch (error) {
    console.error('获取采购计划统计失败:', error)
    return false
  }
}

const fetchList = async (page = currentPage.value) => {
  tableLoading.value = true
  try {
    const res = await purchasePlanApi.getList({
      pageNum: page,
      pageSize: pageSize.value,
      keyword: searchForm.keyword.trim() || undefined,
      status: searchForm.status || undefined,
    })
    purchasePlans.value = (res.data.list || []).map((item) => normalizeRecord(item))
    syncCurrentOrgName(purchasePlans.value)
    total.value = Number(res.data.total || 0)
    currentPage.value = Number(res.data.pageNum || page)
    pageSize.value = Number(res.data.pageSize || pageSize.value)
    purchasePlanStore.setListCache({
      list: purchasePlans.value.map((item) => normalizeRecord(item)),
      total: total.value,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    selectedMergePlans.value = []
    await nextTick()
    purchasePlanTableRef.value?.clearSelection()
    return true
  } catch (error) {
    console.error('获取采购计划列表失败:', error)
    return false
  } finally {
    tableLoading.value = false
  }
}

const fetchDetail = async (id: number) => {
  const res = await purchasePlanApi.getDetail(id)
  return normalizeRecord(res.data)
}

const fetchLinkedPurchaseOrders = async (id: number) => {
  const res = await purchasePlanApi.getLinkedPurchaseOrders(id, {
    silentError: true,
    timeout: 10000,
  })
  return Array.isArray(res.data) ? res.data.map((item) => normalizeLinkedOrderRecord(item)) : []
}

const loadLinkedPurchaseOrdersForDetail = async (id: number) => {
  const requestId = ++linkedOrderRequestSequence
  linkedOrderLoading.value = true
  try {
    const records = await fetchLinkedPurchaseOrders(id)
    if (requestId !== linkedOrderRequestSequence) {
      return
    }
    linkedOrderRecords.value = records
  } catch (error) {
    if (requestId !== linkedOrderRequestSequence) {
      return
    }
    linkedOrderRecords.value = []
    console.error('加载采购计划关联采购订单记录失败:', error)
    ElMessage.warning('关联采购订单记录加载失败，已展示采购计划基础详情')
  } finally {
    if (requestId === linkedOrderRequestSequence) {
      linkedOrderLoading.value = false
    }
  }
}

const loadFormOptions = async (orgId: number | null, currentItems: PurchasePlanItem[] = []) => {
  if (!orgId) {
    materialOptions.value = []
    return
  }

  formOptionLoading.value = true
  try {
    const materialRes = await purchasePlanApi.getMaterialOptions(orgId)
    materialOptions.value = mergeMaterialOptions(materialRes.data || [], currentItems)
    sanitizeFormItems()
    syncPlanTotals(formState.value)
  } finally {
    formOptionLoading.value = false
  }
}

const refreshListAndStatistics = async (page = currentPage.value) => {
  await Promise.all([
    fetchList(page),
    fetchStatistics(),
  ])
}

const hasActiveSearchConditions = () => {
  return Boolean(searchForm.keyword.trim() || searchForm.status)
}

const restoreCachedListState = () => {
  purchasePlans.value = purchasePlanStore.list.map((item) => normalizeRecord(item))
  total.value = purchasePlanStore.total
  currentPage.value = purchasePlanStore.pageNum
  pageSize.value = purchasePlanStore.pageSize
  statistics.value = {
    total: Number(purchasePlanStore.statistics.total || 0),
    pending: Number(purchasePlanStore.statistics.pending || 0),
    approved: Number(purchasePlanStore.statistics.approved || 0),
    totalBudget: roundToCurrency(Number(purchasePlanStore.statistics.totalBudget || 0)),
  }
  syncCurrentOrgName(purchasePlans.value)
}

const refreshListOnReturnIfNeeded = async () => {
  if (!purchasePlanStore.initialized) {
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

const resetSearch = async () => {
  purchasePlanStore.resetSearchCache()
  searchForm.keyword = ''
  searchForm.status = ''
  await applySearch()
}

const openCreateDialog = async () => {
  formMode.value = 'create'
  skipAttachmentCleanup.value = false
  ensureCurrentOrgOption()
  const orgId = getDefaultOrgId()
  formState.value = createFormState(orgId, getOrgNameById(orgId))
  clearRelatedDocumentMaterialLinkage()
  persistedAttachmentUrls.value = []
  formVisible.value = true
  await Promise.all([
    loadFormOptions(formState.value.orgId, formState.value.items),
    loadRelatedDocumentOptions(formState.value.orgId),
  ])
}

const openCreateDialogWithForecastPrefill = async (prefill: PurchaseDemandForecastPlanPrefill) => {
  formMode.value = 'create'
  skipAttachmentCleanup.value = false
  ensureCurrentOrgOption()
  const orgId = Number(prefill.orgId || getDefaultOrgId() || 0) || getDefaultOrgId()
  const orgName = prefill.orgName || getOrgNameById(orgId)
  formState.value = createFormState(orgId, orgName)
  formState.value.planName = prefill.planName || `采购需求预测-${prefill.forecastNo || ''}`
  formState.value.planDate = prefill.planDate || formatDate()
  formState.value.createdBy = prefill.createdBy || currentUserName.value
  formState.value.budgetAmount = prefill.budgetAmount ?? null
  formState.value.relatedDocument = prefill.relatedDocument || prefill.forecastNo || ''
  formState.value.items = prefill.items.length
    ? prefill.items.map((item) => createForecastPrefilledItem(item))
    : [createEmptyItem()]
  clearRelatedDocumentMaterialLinkage()
  persistedAttachmentUrls.value = []
  formVisible.value = true
  await Promise.all([
    loadFormOptions(formState.value.orgId, formState.value.items),
    loadRelatedDocumentOptions(formState.value.orgId),
  ])
  await Promise.all([
    refreshForecastMaterialLinkage(),
    refreshRecipePlanMaterialLinkage(),
  ])
  syncPlanTotals(formState.value)
}

const restoreForecastPrefillIfNeeded = async () => {
  const prefill = purchasePlanStore.consumeForecastPrefill()
  if (!prefill) {
    return
  }
  await openCreateDialogWithForecastPrefill(prefill)
}

const goToForecastPage = async () => {
  await router.push('/purchase-plan/purchase-demand-forecast')
}

const openEditDialog = async (row: PurchasePlanRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购计划仅支持查看，不允许编辑')
    return
  }
  if (!canEdit(row)) {
    ElMessage.warning('仅草稿或已驳回状态可编辑')
    return
  }

  const detail = await fetchDetail(row.id)
  formMode.value = 'edit'
  skipAttachmentCleanup.value = false
  formState.value = toFormState(detail)
  clearRelatedDocumentMaterialLinkage()
  persistedAttachmentUrls.value = formState.value.attachments
    .map((attachment) => attachment.url)
    .filter((url): url is string => Boolean(url))
  formVisible.value = true
  await Promise.all([
    loadFormOptions(formState.value.orgId, formState.value.items),
    loadRelatedDocumentOptions(formState.value.orgId),
  ])
  await Promise.all([
    refreshForecastMaterialLinkage(),
    refreshRecipePlanMaterialLinkage(),
  ])
}

const openDetailDialog = async (row: PurchasePlanRecord) => {
  detailLoading.value = true
  detailPlan.value = null
  linkedOrderLoading.value = false
  linkedOrderRecords.value = []
  try {
    detailPlan.value = await fetchDetail(row.id)
    detailVisible.value = true
    void loadLinkedPurchaseOrdersForDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

const openAuditDialog = async (row: PurchasePlanRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购计划不允许审核')
    return
  }
  if (!canAudit(row)) {
    ElMessage.warning('仅待审核状态可执行审核操作')
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

const openReverseAuditDialog = async (row: PurchasePlanRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购计划不允许反审核')
    return
  }
  if (!canReverseAudit(row)) {
    ElMessage.warning('当前状态不允许执行反审核')
    return
  }

  detailLoading.value = true
  try {
    reverseTarget.value = await fetchDetail(row.id)
    reverseForm.reason = ''
    reverseVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

const closeReverseAuditDialog = () => {
  reverseVisible.value = false
  reverseTarget.value = null
  reverseForm.reason = ''
}

const openVoidDialog = async (row: PurchasePlanRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购计划不允许发起作废')
    return
  }
  if (!canVoid(row)) {
    ElMessage.warning('仅已驳回状态或未关联下游业务的已审核状态可发起作废')
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

const openVoidAuditDialog = async (row: PurchasePlanRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购计划不允许执行作废审核')
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

const closeGenerateDialog = () => {
  generateVisible.value = false
  generateTarget.value = null
  generateItems.value = []
  generateSupplierId.value = null
  generateSupplierOptions.value = []
  generateAttachmentFile.value = null
  generateAttachmentUploadRef.value?.clearFiles()
}

const closeFormDialog = () => {
  clearRelatedDocumentMaterialLinkage()
  formVisible.value = false
}

const closeFormAfterSave = () => {
  skipAttachmentCleanup.value = true
  closeFormDialog()
}

const handleFormCancel = async () => {
  if (attachmentUploading.value) {
    ElMessage.warning('附件上传中，请稍候再关闭')
    return
  }
  await cleanupTransientAttachments()
  closeFormDialog()
}

const handleFormBeforeClose = async (done: () => void) => {
  if (skipAttachmentCleanup.value) {
    skipAttachmentCleanup.value = false
    done()
    return
  }
  if (attachmentUploading.value) {
    ElMessage.warning('附件上传中，请稍候再关闭')
    return
  }
  await cleanupTransientAttachments()
  done()
}

const handleMergeSelectionChange = (selection: PurchasePlanRecord[]) => {
  selectedMergePlans.value = selection
}

const isMergeSelectableRow = (row: PurchasePlanRecord) => canMergeGenerate(row)

const submitMergeGenerateOrder = async () => {
  if (selectedMergePlans.value.length < 2) {
    ElMessage.warning('请至少勾选2条采购计划进行合并生成，单条请使用原有关联生成采购订单')
    return
  }

  mergeGenerateSubmitting.value = true
  try {
    const res = await purchasePlanApi.mergeGenerateOrder({
      planIds: selectedMergePlans.value.map((item) => item.id),
    })
    const orderId = Number(res.data?.id || 0)
    const orderNo = res.data?.orderNo || ''
    if (!orderId) {
      ElMessage.warning('合并生成采购订单成功，但未返回草稿订单编号')
      return
    }
    purchaseOrderStore.setPendingEditOrderId(orderId)
    await router.push('/purchase')
    ElMessage.success(orderNo ? `已生成采购订单草稿 ${orderNo}` : '已生成采购订单草稿')
  } finally {
    mergeGenerateSubmitting.value = false
  }
}

const deletePlan = async (row: PurchasePlanRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购计划不可重复删除')
    return
  }
  if (!canDelete(row)) {
    ElMessage.warning('仅草稿或待审核状态可删除')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除采购计划“${row.planName}”吗？`,
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

  await purchasePlanApi.delete(row.id)
  const nextPage = purchasePlans.value.length === 1 && currentPage.value > 1 ? currentPage.value - 1 : currentPage.value
  await refreshListAndStatistics(nextPage)
  ElMessage.success('采购计划已删除')
}

const submitVoidApply = async () => {
  if (!voidTarget.value) return
  if (!voidForm.reason.trim()) {
    ElMessage.warning('请输入作废原因')
    return
  }

  voidSubmitting.value = true
  try {
    await purchasePlanApi.applyVoid(voidTarget.value.id, {
      reason: voidForm.reason.trim(),
    })
    closeVoidDialog()
    await refreshListAndStatistics()
    ElMessage.success('采购计划作废申请已提交')
  } finally {
    voidSubmitting.value = false
  }
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
    const res = await purchasePlanApi.reverseAudit(targetId, {
      reason: reverseForm.reason.trim(),
    })
    closeReverseAuditDialog()
    await refreshListAndStatistics()
    if (detailVisible.value && detailPlan.value?.id === targetId) {
      detailPlan.value = await fetchDetail(targetId)
      void loadLinkedPurchaseOrdersForDetail(targetId)
    }
    ElMessage.success(
      Number(res.data?.affectedOrderCount || 0) > 0
        ? '反审核成功，采购计划已退回草稿；关联采购订单状态及关联关系保持不变。'
        : '反审核成功，采购计划已退回草稿'
    )
  } finally {
    reverseSubmitting.value = false
  }
}

const submitVoidAudit = async (approved: boolean) => {
  if (!voidAuditTarget.value) return

  voidAuditSubmitting.value = true
  try {
    await purchasePlanApi.auditVoid(voidAuditTarget.value.id, {
      approved,
      remark: voidAuditForm.remark.trim() || undefined,
    })
    closeVoidAuditDialog()
    await refreshListAndStatistics()
    ElMessage.success(approved ? '采购计划已作废' : '作废申请已驳回，采购计划已恢复为作废前状态')
  } finally {
    voidAuditSubmitting.value = false
  }
}

const handleMoreAction = async (row: PurchasePlanRecord, command: PurchasePlanMoreAction) => {
  switch (command) {
    case 'generateOrder':
      await openGenerateDialog(row)
      break
    case 'reverseAudit':
      await openReverseAuditDialog(row)
      break
    case 'void':
      await openVoidDialog(row)
      break
    case 'voidAudit':
      await openVoidAuditDialog(row)
      break
    case 'delete':
      await deletePlan(row)
      break
  }
}

const openGenerateDialog = async (row: PurchasePlanRecord) => {
  if (row.deleted) {
    ElMessage.warning('已删除采购计划不允许关联生成采购订单')
    return
  }
  if (row.status !== 'approved') {
    ElMessage.warning('仅已审核状态可关联生成采购订单')
    return
  }

  detailLoading.value = true
  try {
    const detail = await fetchDetail(row.id)
    if (!canGeneratePurchaseOrder(detail)) {
      ElMessage.warning('该采购计划全部物料均已关联生成完采购订单')
      return
    }

    const supplierRes = await purchaseApi.getSupplierOptions(detail.orgId || undefined)
    generateSupplierOptions.value = supplierRes.data || []
    if (!generateSupplierOptions.value.length) {
      ElMessage.warning('当前组织下暂无资质有效且已审核的供应商，不能生成采购订单')
      return
    }

    generateTarget.value = detail
    generateSupplierId.value = generateSupplierOptions.value[0]?.id || null
    generateAttachmentFile.value = null
    generateAttachmentUploadRef.value?.clearFiles()
    generateItems.value = detail.items.map((item) => ({
      planItemId: item.id,
      materialName: item.materialName,
      materialSpec: item.materialSpec,
      unit: item.unit,
      plannedQuantity: Number(item.quantity || 0),
      orderedQuantity: getOrderedQuantity(item),
      remainingQuantity: getRemainingQuantity(item),
      unitPrice: roundToCurrency(Number(item.unitPrice || 0)),
      quantityToGenerate: null,
    }))
    generateVisible.value = true
  } finally {
    detailLoading.value = false
  }
}

const submitGenerateOrder = async () => {
  if (!generateTarget.value) return

  const selectedItems = generateItems.value
    .filter((item) => Number(item.quantityToGenerate || 0) > 0)
    .map((item) => ({
      planItemId: item.planItemId,
      quantity: roundToQuantity(Number(item.quantityToGenerate || 0)),
      unitPrice: roundToCurrency(Number(item.unitPrice || 0)),
      subtotal: getGenerateItemSubtotal(item),
      materialName: item.materialName,
      remainingQuantity: item.remainingQuantity,
    }))

  if (!selectedItems.length) {
    ElMessage.warning('请至少填写一条本次生成数量')
    return
  }
  if (!generateSupplierId.value) {
    ElMessage.warning('请选择本次生成采购订单的供应商')
    return
  }

  const invalidItem = selectedItems.find((item) => item.quantity > item.remainingQuantity)
  if (invalidItem) {
    ElMessage.warning(`物料“${invalidItem.materialName}”本次生成数量不能超过待关联数量`)
    return
  }

  generateSubmitting.value = true
  try {
    const res = await purchasePlanApi.generateOrders(
      generateTarget.value.id,
      {
        supplierId: Number(generateSupplierId.value),
        items: selectedItems.map((item) => ({
          planItemId: item.planItemId,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          subtotal: item.subtotal,
        })),
      },
      generateAttachmentFile.value,
    )
    const orderNos = (res.data || []).map((item) => item.orderNo).join('、')
    closeGenerateDialog()
    await refreshListAndStatistics()
    ElMessage.success(orderNos ? `已生成采购订单 ${orderNos}` : '采购订单已生成')
  } finally {
    generateSubmitting.value = false
  }
}

const addItemRow = () => {
  formState.value.items.push(createEmptyItem())
  syncPlanTotals(formState.value)
}

const removeItemRow = (index: number) => {
  if (formState.value.items.length === 1) {
    ElMessage.warning('至少保留一条物料明细')
    return
  }
  formState.value.items.splice(index, 1)
  syncPlanTotals(formState.value)
}

const handleMaterialChange = (item: PurchasePlanItem) => {
  const material = getMaterialById(item.materialId)
  item.materialName = material?.name || ''
  item.unit = material?.unit || ''
  item.materialSpec = material?.spec || ''
  item.unitPrice = material ? roundToCurrency(Number(material.referencePrice || 0)) : null
  syncItemDerivedFields(item)
  handleLinkedQuantityChange(item)
  syncPlanTotals(formState.value)
}

const handleOrgChange = async (value: number | null) => {
  formState.value.orgId = value
  formState.value.orgName = getOrgNameById(value)
  clearRelatedDocumentMaterialLinkage()
  await Promise.all([
    loadFormOptions(value, formState.value.items),
    loadRelatedDocumentOptions(value),
  ])
  await Promise.all([
    refreshForecastMaterialLinkage(),
    refreshRecipePlanMaterialLinkage(),
  ])
}

const handleRelatedDocumentChange = async (value?: string) => {
  const documentNo = (value || '').trim()
  if (!documentNo) {
    clearRelatedDocumentMaterialLinkage()
    return
  }
  clearRelatedDocumentMaterialLinkage()

  try {
    await loadRelatedDocumentItems(documentNo)
  } catch (error) {
    console.error('加载关联单据物料明细失败:', error)
  }
  await Promise.all([
    refreshForecastMaterialLinkage(),
    refreshRecipePlanMaterialLinkage(),
  ])
}

const validateForm = () => {
  if (!formState.value.planName.trim()) {
    ElMessage.warning('请填写采购计划名称')
    return false
  }
  if (formState.value.planName.trim().length > 100) {
    ElMessage.warning('计划名称长度不能超过100个字符')
    return false
  }
  if (!formState.value.orgId) {
    ElMessage.warning('请选择所属组织')
    return false
  }
  if (!formState.value.planDate) {
    ElMessage.warning('请选择采购计划日期')
    return false
  }
  if (!formState.value.budgetAmount || formState.value.budgetAmount <= 0) {
    ElMessage.warning('请填写正确的预算金额')
    return false
  }

  const candidateItems = formState.value.items.filter((item) => {
    return item.materialId || item.quantity !== null || item.unitPrice !== null || item.remark.trim()
  })

  if (!candidateItems.length) {
    ElMessage.warning('请至少填写一条完整的物料明细')
    return false
  }

  const invalidItem = candidateItems.find((item) => {
    return !item.materialId || !item.quantity || item.quantity <= 0 || item.unitPrice === null || item.unitPrice < 0
  })
  if (invalidItem) {
    ElMessage.warning('请补全物料、数量和预估单价')
    return false
  }

  if (!validateRecipePlanMaterialQuantities()) {
    return false
  }

  if (!validateForecastMaterialQuantities()) {
    return false
  }

  return true
}

const buildPayload = (status: Extract<PurchasePlanStatus, 'draft' | 'pending'>) => {
  syncPlanTotals(formState.value)
  const items = formState.value.items
    .filter((item) => item.materialId)
    .map((item) => ({
      materialId: Number(item.materialId),
      materialSpec: item.materialSpec || undefined,
      quantity: Number(item.quantity || 0),
      unitPrice: Number(item.unitPrice || 0),
      remark: item.remark || undefined,
    }))

  return {
    planNo: formState.value.planNo || undefined,
    planName: formState.value.planName.trim(),
    orgId: Number(formState.value.orgId),
    planDate: formState.value.planDate,
    budgetAmount: Number(formState.value.budgetAmount || 0),
    relatedDocument: formState.value.relatedDocument,
    remark: formState.value.remark,
    status,
    attachments: formState.value.attachments.map((attachment, index) => ({
      id: attachment.id,
      name: attachment.name,
      size: attachment.size || undefined,
      url: attachment.url,
      sortOrder: index + 1,
    })),
    items,
  }
}

const saveForm = async (status: Extract<PurchasePlanStatus, 'draft' | 'pending'>) => {
  if (!validateForm()) return
  if (attachmentUploading.value) {
    ElMessage.warning('附件上传中，请稍候再提交')
    return
  }

  formSubmitting.value = true
  try {
    const payload = buildPayload(status)
    if (formMode.value === 'create') {
      await purchasePlanApi.create(payload)
      ElMessage.success(status === 'pending' ? '采购计划已提交审核' : '采购计划草稿已保存')
      closeFormAfterSave()
      await refreshListAndStatistics(1)
      return
    }

    if (!formState.value.id) return
    await purchasePlanApi.update(formState.value.id, payload)
    ElMessage.success(status === 'pending' ? '采购计划已保存并提交' : '采购计划修改已保存')
    closeFormAfterSave()
    await refreshListAndStatistics()
  } finally {
    formSubmitting.value = false
  }
}

const submitAudit = async (status: Extract<PurchasePlanStatus, 'approved' | 'rejected'>) => {
  if (!auditTarget.value) return

  auditSubmitting.value = true
  try {
    await purchasePlanApi.audit(auditTarget.value.id, {
      status,
      remark: auditForm.remark.trim() || undefined,
    })
    closeAuditDialog()
    await refreshListAndStatistics()
    ElMessage.success(status === 'approved' ? '采购计划审核通过' : '采购计划已驳回')
  } finally {
    auditSubmitting.value = false
  }
}

const approvePlan = async () => {
  await submitAudit('approved')
}

const rejectPlan = async () => {
  await submitAudit('rejected')
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

onMounted(async () => {
  try {
    void ensureUserInfoReady()
    await loadOrganizations()
    if (!formState.value.orgId) {
      const defaultOrgId = getDefaultOrgId()
      formState.value.orgId = defaultOrgId
      formState.value.orgName = getOrgNameById(defaultOrgId)
    }
    await refreshListOnReturnIfNeeded()
    await restoreForecastPrefillIfNeeded()
  } catch (error) {
    console.error('采购计划页面初始化失败:', error)
  }
})

onActivated(async () => {
  try {
    if (!purchasePlanActivatedOnce.value) {
      purchasePlanActivatedOnce.value = true
    }
    await refreshListAndStatistics(currentPage.value)
    await restoreForecastPrefillIfNeeded()
  } catch (error) {
    console.error('采购计划页面重新激活失败:', error)
  }
})
</script>

<template>
  <div class="purchase-plan-page">
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-icon total">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.total }}</div>
          <div class="stat-label">采购计划总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon pending">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.pending }}</div>
          <div class="stat-label">待审核计划</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon approved">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.approved }}</div>
          <div class="stat-label">已审核计划</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon budget">
          <el-icon><Money /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value amount">{{ formatCurrency(statistics.totalBudget) }}</div>
          <div class="stat-label">预算总额</div>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <el-row class="toolbar-row" :gutter="12" align="middle">
        <el-col :span="8" class="toolbar-col toolbar-search-col">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入采购计划名称/编码"
            clearable
            @keyup.enter="applySearch"
          />
        </el-col>
        <el-col :span="4" class="toolbar-col toolbar-status-col">
          <el-select v-model="searchForm.status" placeholder="计划状态" clearable style="width: 100%">
            <el-option
              v-for="option in STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-col>
        <el-col :span="12" class="toolbar-col toolbar-actions">
          <el-button type="primary" :icon="Search" @click="applySearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="resetSearch">重置</el-button>
          <el-button type="primary" :icon="Plus" v-permission="PURCHASE_PLAN_PERMISSIONS.CREATE" @click="openCreateDialog">新增采购计划</el-button>
          <el-button :icon="DataAnalysis" @click="goToForecastPage">采购需求预测</el-button>
        </el-col>
      </el-row>
    </div>

    <div v-loading="tableLoading" class="table-wrapper">
      <div class="table-header">
        <div>
          <div class="table-title">采购计划列表</div>
          <div class="table-subtitle">实时展示远程数据库中的采购计划数据。</div>
        </div>
        <el-button
          v-permission="PURCHASE_PLAN_PERMISSIONS.GENERATE_ORDER"
          type="primary"
          plain
          :disabled="selectedMergePlanCount < 2"
          :loading="mergeGenerateSubmitting"
          @click="submitMergeGenerateOrder"
        >
          合并生成采购订单
          <template v-if="selectedMergePlanCount">（{{ selectedMergePlanCount }}）</template>
        </el-button>
      </div>

      <el-table
        ref="purchasePlanTableRef"
        :data="purchasePlans"
        stripe
        border
        height="100%"
        empty-text="当前筛选条件下暂无采购计划"
        @selection-change="handleMergeSelectionChange"
      >
        <el-table-column
          type="selection"
          width="52"
          align="center"
          :selectable="isMergeSelectableRow"
        />
        <el-table-column prop="planNo" label="计划编号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="planName" label="计划名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="orgName" label="所属组织" width="120" />
        <el-table-column label="总计划金额" width="130" align="right">
          <template #default="{ row }">
            {{ formatCurrency(row.totalAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="170" align="center">
          <template #default="{ row }">
            <div class="status-tags">
              <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="物料关联订单状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="getOccupancyStatusLabel(row)" :type="getOccupancyStatusType(row)">
              {{ getOccupancyStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="360" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <div class="action-buttons__primary">
                <el-button link type="primary" :icon="View" @click="openDetailDialog(row)">详情</el-button>
                <el-button
                  link
                  type="primary"
                  :icon="EditPen"
                  v-permission="PURCHASE_PLAN_PERMISSIONS.EDIT"
                  :disabled="!canEdit(row)"
                  @click="openEditDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  link
                  type="warning"
                  :icon="Files"
                  v-permission="PURCHASE_PLAN_PERMISSIONS.APPROVE"
                  :disabled="!canAudit(row)"
                  @click="openAuditDialog(row)"
                >
                  审核
                </el-button>
              </div>
              <el-dropdown
                v-if="hasMoreActionItems(row)"
                trigger="click"
                @command="(command) => handleMoreAction(row, command as PurchasePlanMoreAction)"
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
        :page-sizes="[5, 10, 20]"
        :total="total"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="formVisible"
      :title="formDialogTitle"
      width="1080px"
      destroy-on-close
      :before-close="handleFormBeforeClose"
    >
      <el-form v-loading="formOptionLoading || formSubmitting" label-width="100px" class="plan-form">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <div class="form-grid">
            <el-form-item label="计划编号">
              <el-input v-model="formState.planNo" readonly />
            </el-form-item>
            <el-form-item label="计划名称" required>
              <el-input v-model="formState.planName" placeholder="请输入采购计划名称" :maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="所属组织" required>
              <OrgTreeSelect
                v-model="formState.orgId"
                placeholder="请选择所属组织"
                :active-only="true"
                @update:model-value="handleOrgChange"
              />
            </el-form-item>
            <el-form-item label="计划日期" required>
              <el-date-picker
                v-model="formState.planDate"
                type="date"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                placeholder="请选择计划日期"
              />
            </el-form-item>
            <el-form-item label="预算金额" required>
              <el-input-number
                v-model="formState.budgetAmount"
                :min="0"
                :step="100"
                :precision="2"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="创建人">
              <el-input v-model="formState.createdBy" readonly />
            </el-form-item>
            <el-form-item label="关联单据" class="form-item-full">
              <el-select
                v-model="formState.relatedDocument"
                filterable
                clearable
                :loading="relatedDocumentLoading"
                placeholder="请选择关联单据"
                style="width: 100%"
                @change="handleRelatedDocumentChange"
              >
                <el-option-group
                  v-for="group in relatedDocumentGroups"
                  :key="group.label"
                  :label="group.label"
                >
                  <el-option
                    v-for="option in group.options"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-option-group>
              </el-select>
              <div class="form-field-tip">仅显示已审核且实施日期未过期的菜谱计划单，以及周期未结束且全部物料仍有剩余可关联数量的采购需求预测单。</div>
            </el-form-item>
            <el-form-item label="附件" class="form-item-full">
              <div class="attachment-field">
                <div class="upload-area">
                  <label class="upload-btn" for="purchasePlanAttachmentInput">选择附件</label>
                  <input
                    id="purchasePlanAttachmentInput"
                    type="file"
                    multiple
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png"
                    style="display: none"
                    @change="handleAttachmentFileChange"
                  />
                  <span class="attachment-field-tip">支持多选批量上传，上传后可逐个查看、下载、删除，单个文件不超过10MB。</span>
                </div>
                <div v-if="formState.attachments.length" class="attachment-list">
                  <div
                    v-for="(attachment, index) in formState.attachments"
                    :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
                    class="attachment-card"
                  >
                    <div class="attachment-meta">
                      <span class="attachment-name">{{ attachment.name }}</span>
                      <span class="attachment-status">
                        {{ isPersistedAttachment(attachment) ? '当前附件' : '待保存附件' }}
                        <template v-if="attachment.size"> · {{ attachment.size }}</template>
                      </span>
                    </div>
                    <div class="attachment-actions">
                      <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                      <el-button link type="primary" @click="downloadAttachmentFile(attachment)">下载</el-button>
                      <el-button link type="danger" @click="removeAttachment(index)">删除</el-button>
                    </div>
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
                placeholder="请输入计划说明或采购备注"
              />
            </el-form-item>
          </div>
        </div>

        <div class="form-section">
          <div class="section-title item-header">
            <span>计划物料明细</span>
            <div class="item-summary">
              <span>计划合计：</span>
              <strong>{{ formatCurrency(formState.totalAmount) }}</strong>
            </div>
          </div>

          <div class="item-table-scroll">
            <div class="item-list">
              <div class="item-row item-row-header">
                <div class="item-header-cell">序号</div>
                <div class="item-header-cell">物料名称</div>
                <div class="item-header-cell">规格</div>
                <div class="item-header-cell">单位</div>
                <div class="item-header-cell align-right">数量</div>
                <div class="item-header-cell align-right">预估单价</div>
                <div class="item-header-cell align-right">小计</div>
                <div class="item-header-cell">备注</div>
                <div class="item-header-cell align-center">操作</div>
              </div>
              <div
                v-for="(item, index) in formState.items"
                :key="item.id"
                class="item-row"
              >
                <div class="item-index">#{{ index + 1 }}</div>
                <el-select
                  v-model="item.materialId"
                  placeholder="物料名称"
                  filterable
                  @change="handleMaterialChange(item)"
                >
                  <el-option
                    v-for="material in materialOptions"
                    :key="material.id"
                    :label="material.name"
                    :value="material.id"
                  />
                </el-select>
                <el-select
                  v-model="item.materialSpec"
                  placeholder="规格"
                  filterable
                  :disabled="Boolean(item.lockMaterialSpec)"
                  :no-data-text="item.materialId ? '暂无规格选项' : '当前组织下暂无可选规格'"
                >
                  <el-option
                    v-for="spec in getMaterialSpecs(item.materialId)"
                    :key="spec"
                    :label="spec"
                    :value="spec"
                  />
                </el-select>
                <el-input v-model="item.unit" placeholder="单位" readonly />
                <el-input-number
                  v-model="item.quantity"
                  :min="0"
                  :max="getLinkedMaterialRemainingQtyForItem(item)"
                  :step="1"
                  style="width: 100%"
                  placeholder="数量"
                  @change="handleLinkedQuantityChange(item)"
                />
                <el-input-number
                  v-model="item.unitPrice"
                  :min="0"
                  :step="0.1"
                  :precision="2"
                  :disabled="Boolean(item.lockUnitPrice)"
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
                <el-button class="item-action-button" text type="danger" @click="removeItemRow(index)">删除</el-button>
              </div>
            </div>
          </div>

          <el-button class="add-item-button" @click="addItemRow">+ 添加物料</el-button>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="handleFormCancel">取消</el-button>
        <el-button :loading="formSubmitting" @click="saveForm('draft')">保存草稿</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="saveForm('pending')">
          {{ formMode === 'create' ? '提交审核' : '保存并提交' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="采购计划详情"
      width="960px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
      <template v-if="detailPlan">
        <div class="detail-section">
          <div class="detail-section-title">基本信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">计划编号</span>
              <span class="detail-value">{{ detailPlan.planNo }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">状态</span>
              <span class="detail-value">
                <el-tag :type="getStatusType(detailPlan.status)">{{ getStatusLabel(detailPlan.status) }}</el-tag>
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">计划名称</span>
              <span class="detail-value">{{ detailPlan.planName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">所属组织</span>
              <span class="detail-value">{{ detailPlan.orgName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">计划日期</span>
              <span class="detail-value">{{ detailPlan.planDate }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">创建人</span>
              <span class="detail-value">{{ detailPlan.createdBy }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">预算金额</span>
              <span class="detail-value">{{ formatCurrency(detailPlan.budgetAmount) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">总计划金额</span>
              <span class="detail-value">{{ formatCurrency(detailPlan.totalAmount) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">创建时间</span>
              <span class="detail-value">{{ detailPlan.createdAt }}</span>
            </div>
            <div v-if="detailPlan.relatedDocument" class="detail-item detail-span-2">
              <span class="detail-label">关联单据</span>
              <span class="detail-value">{{ detailPlan.relatedDocument }}</span>
            </div>
            <div v-if="detailPlan.remark" class="detail-item detail-span-2">
              <span class="detail-label">备注</span>
              <span class="detail-value">{{ detailPlan.remark }}</span>
            </div>
          </div>
        </div>

        <div v-if="detailPlan.auditAt" class="detail-section">
          <div class="detail-section-title">审核信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">审核时间</span>
              <span class="detail-value">{{ detailPlan.auditAt }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">审核结果</span>
              <span class="detail-value">{{ getAuditResultLabel(detailPlan) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">审核人</span>
              <span class="detail-value">{{ detailPlan.auditBy || '-' }}</span>
            </div>
            <div class="detail-item detail-span-2">
              <span class="detail-label">审核意见</span>
              <span class="detail-value">{{ detailPlan.auditRemark || '-' }}</span>
            </div>
          </div>
        </div>

        <div v-if="shouldShowVoidSection(detailPlan)" class="detail-section">
          <div class="detail-section-title">作废流程</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">原始状态</span>
              <span class="detail-value">{{ detailPlan.voidOriginalStatus ? getStatusLabel(detailPlan.voidOriginalStatus) : '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">作废原因</span>
              <span class="detail-value">{{ detailPlan.voidReason || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">作废申请人</span>
              <span class="detail-value">{{ detailPlan.voidRequestedBy || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">作废申请时间</span>
              <span class="detail-value">{{ detailPlan.voidRequestedAt || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">作废审核人</span>
              <span class="detail-value">{{ detailPlan.voidAuditBy || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">作废审核时间</span>
              <span class="detail-value">{{ detailPlan.voidAuditAt || '—' }}</span>
            </div>
            <div class="detail-item detail-span-2">
              <span class="detail-label">作废审核意见</span>
              <span class="detail-value">{{ detailPlan.voidAuditRemark || '—' }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">附件信息</div>
          <div v-if="detailPlan.attachments.length" class="attachment-list">
            <div
              v-for="(attachment, index) in detailPlan.attachments"
              :key="attachment.id || attachment.url || `${attachment.name}-${index}`"
              class="attachment-card"
            >
              <div class="attachment-meta">
                <span class="attachment-name">{{ attachment.name || '附件' }}</span>
                <span class="attachment-status">
                  已上传
                  <template v-if="attachment.size"> · {{ attachment.size }}</template>
                </span>
              </div>
              <div class="attachment-actions">
                <el-button link type="primary" @click="previewAttachment(attachment.url)">在线查看</el-button>
                <el-button link type="primary" @click="downloadAttachmentFile(attachment)">下载</el-button>
              </div>
            </div>
          </div>
          <div v-else class="attachment-empty">暂无附件</div>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">物料清单</div>
          <el-table :data="detailPlan.items" border size="small" empty-text="暂无物料明细">
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column prop="materialSpec" label="规格" min-width="120" />
            <el-table-column prop="unit" label="单位" width="90" />
            <el-table-column label="预采购数量" width="110" align="center">
              <template #default="{ row }">
                {{ row.quantity ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="预估单价" width="110" align="right">
              <template #default="{ row }">
                {{ formatCurrency(row.unitPrice || 0) }}
              </template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="right">
              <template #default="{ row }">
                {{ formatCurrency(row.subtotal) }}
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">已关联采购订单记录</div>
          <el-table
            v-loading="linkedOrderLoading"
            class="linked-order-table"
            :data="linkedOrderRecords"
            border
            size="small"
            :row-class-name="getLinkedOrderRowClassName"
            empty-text="暂无关联采购订单记录"
          >
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="orderNo" label="采购订单号" min-width="170" />
            <el-table-column label="订单状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getPurchaseOrderStatusType(row.status)" size="small">
                  {{ getPurchaseOrderStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="materialName" label="物料名称" min-width="150" />
            <el-table-column prop="materialSpec" label="规格" min-width="120" />
            <el-table-column prop="unit" label="单位" width="80" align="center" />
            <el-table-column label="采购数量" width="110" align="right">
              <template #default="{ row }">
                {{ row.quantity }}
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
      </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailPlan && canReverseAudit(detailPlan) && hasPlanPermission(PURCHASE_PLAN_PERMISSIONS.APPROVE)"
          type="danger"
          :icon="RefreshRight"
          @click="openReverseAuditDialog(detailPlan)"
        >
          反审核
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="auditVisible"
      title="审核采购计划"
      width="560px"
      destroy-on-close
      @close="closeAuditDialog"
    >
      <template v-if="auditTarget">
        <div class="audit-summary">
          <div class="summary-item">
            <span class="summary-label">计划编号</span>
            <strong>{{ auditTarget.planNo }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">计划名称</span>
            <strong>{{ auditTarget.planName }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">预算金额</span>
            <strong>{{ formatCurrency(auditTarget.budgetAmount) }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">计划合计</span>
            <strong>{{ formatCurrency(auditTarget.totalAmount) }}</strong>
          </div>
        </div>

        <el-form label-width="90px">
          <el-form-item label="审核意见">
            <el-input
              v-model="auditForm.remark"
              type="textarea"
              :rows="4"
              placeholder="请输入审核意见，驳回时建议写明原因"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeAuditDialog">取消</el-button>
        <el-button type="danger" :loading="auditSubmitting" @click="rejectPlan">驳回</el-button>
        <el-button type="success" :loading="auditSubmitting" @click="approvePlan">通过</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="reverseVisible"
      title="反审核采购计划"
      width="620px"
      destroy-on-close
      @close="closeReverseAuditDialog"
    >
      <template v-if="reverseTarget">
        <div class="audit-summary">
          <div class="summary-item">
            <span class="summary-label">计划编号</span>
            <strong>{{ reverseTarget.planNo }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">计划名称</span>
            <strong>{{ reverseTarget.planName }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">当前状态</span>
            <strong>{{ getStatusLabel(reverseTarget.status) }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">当前关联状态</span>
            <strong>{{ getReverseAuditRelationStatusLabel(reverseTarget) }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">受影响订单数</span>
            <strong>{{ getGeneratedOrderCount(reverseTarget) }} 个</strong>
          </div>
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
      title="申请作废采购计划"
      width="560px"
      destroy-on-close
      @close="closeVoidDialog"
    >
      <template v-if="voidTarget">
        <div class="audit-summary">
          <div class="summary-item">
            <span class="summary-label">计划编号</span>
            <strong>{{ voidTarget.planNo }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">计划名称</span>
            <strong>{{ voidTarget.planName }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">当前状态</span>
            <strong>{{ getStatusLabel(voidTarget.status) }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">已关联订单</span>
            <strong>{{ getGeneratedOrderCount(voidTarget) }} 个</strong>
          </div>
        </div>

        <el-form label-width="90px">
          <el-form-item label="作废原因" required>
            <el-input
              v-model="voidForm.reason"
              type="textarea"
              :rows="4"
              :maxlength="500"
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
      title="审核采购计划作废申请"
      width="600px"
      destroy-on-close
      @close="closeVoidAuditDialog"
    >
      <template v-if="voidAuditTarget">
        <div class="audit-summary">
          <div class="summary-item">
            <span class="summary-label">计划编号</span>
            <strong>{{ voidAuditTarget.planNo }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">计划名称</span>
            <strong>{{ voidAuditTarget.planName }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">原始状态</span>
            <strong>{{ voidAuditTarget.voidOriginalStatus ? getStatusLabel(voidAuditTarget.voidOriginalStatus) : '—' }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">作废原因</span>
            <strong>{{ voidAuditTarget.voidReason || '—' }}</strong>
          </div>
        </div>

        <el-form label-width="110px">
          <el-form-item label="审核意见">
            <el-input
              v-model="voidAuditForm.remark"
              type="textarea"
              :rows="4"
              :maxlength="500"
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
      v-model="generateVisible"
      title="关联生成采购订单"
      width="980px"
      destroy-on-close
      @close="closeGenerateDialog"
    >
      <template v-if="generateTarget">
        <div class="audit-summary">
          <div class="summary-item">
            <span class="summary-label">计划编号</span>
            <strong>{{ generateTarget.planNo }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">计划名称</span>
            <strong>{{ generateTarget.planName }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">已关联采购订单</span>
            <strong>{{ getGeneratedOrderCount(generateTarget) }} 个</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">本次生成合计</span>
            <strong>{{ generateOrderSummary.quantity }} / {{ formatCurrency(generateOrderSummary.amount) }}</strong>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">生成设置</div>
          <el-form label-width="100px" class="generate-form">
            <el-form-item label="采购供应商" required>
              <el-select
                v-model="generateSupplierId"
                filterable
                placeholder="请选择本次生成采购订单的供应商"
                style="width: 100%"
              >
                <el-option
                  v-for="supplier in generateSupplierOptions"
                  :key="supplier.id"
                  :label="supplier.name"
                  :value="supplier.id"
                  :disabled="supplier.disabled"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="订单附件">
              <div class="attachment-field">
                <el-upload
                  ref="generateAttachmentUploadRef"
                  :auto-upload="false"
                  :show-file-list="false"
                  :limit="1"
                  :on-change="handleGenerateAttachmentChange"
                  :on-exceed="handleGenerateAttachmentExceed"
                >
                  <el-button type="primary" plain>选择附件</el-button>
                </el-upload>
                <div class="attachment-field-tip">
                  附件会自动归属到本次生成的采购订单，生成成功后可在采购订单详情页查看和下载，单个文件不超过10MB。
                </div>
                <div v-if="currentGenerateAttachmentDisplayName" class="attachment-card">
                  <div class="attachment-meta">
                    <span class="attachment-name">{{ currentGenerateAttachmentDisplayName }}</span>
                    <span class="attachment-status">待随采购订单一起上传</span>
                  </div>
                  <div class="attachment-actions">
                    <el-button link type="danger" @click="clearSelectedGenerateAttachment">
                      清除附件
                    </el-button>
                  </div>
                </div>
                <div v-else class="attachment-empty">本次未选择采购订单附件</div>
              </div>
            </el-form-item>
          </el-form>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">物料关联明细</div>
          <el-table :data="generateItems" border size="small" empty-text="暂无可关联物料">
            <el-table-column prop="materialName" label="物料名称" min-width="150" />
            <el-table-column prop="materialSpec" label="规格" min-width="120" />
            <el-table-column prop="unit" label="单位" width="90" align="center" />
            <el-table-column prop="plannedQuantity" label="计划数量" width="110" align="right" />
            <el-table-column prop="orderedQuantity" label="已关联数量" width="120" align="right" />
            <el-table-column prop="remainingQuantity" label="待关联数量" width="120" align="right" />
            <el-table-column label="本次生成数量" width="150" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantityToGenerate"
                  :min="0"
                  :max="row.remainingQuantity"
                  :step="1"
                  :precision="2"
                  :disabled="row.remainingQuantity <= 0"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="采购单价" width="160" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.unitPrice"
                  :min="0"
                  :step="0.01"
                  :precision="2"
                  :disabled="row.remainingQuantity <= 0"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="130" align="right">
              <template #default="{ row }">
                <span>{{ formatCurrency(getGenerateItemSubtotal(row)) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="generateTarget.orderLinks.length" class="detail-section">
          <div class="detail-section-title">已关联采购订单记录</div>
          <div class="order-link-list">
            <div
              v-for="order in generateTarget.orderLinks"
              :key="order.id"
              class="order-link-card"
            >
              <div class="order-link-head">
                <strong>{{ order.orderNo }}</strong>
                <span>{{ order.createdAt }}</span>
              </div>
              <div class="order-link-meta">创建人：{{ order.createdBy }}</div>
              <div class="order-link-items">{{ formatLinkedItems(order.items) }}</div>
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <el-button @click="closeGenerateDialog">取消</el-button>
        <el-button type="success" :loading="generateSubmitting" @click="submitGenerateOrder">确认生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.purchase-plan-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow: hidden;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  flex-shrink: 0;
}

.stat-card {
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 14px;

  .stat-icon {
    width: 46px;
    height: 46px;
    border-radius: 14px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 20px;

    &.total {
      background: $primary-color;
    }

    &.pending {
      background: $warning-color;
    }

    &.approved {
      background: $success-color;
    }

    &.budget {
      background: #0f766e;
    }
  }

  .stat-content {
    flex: 1;
  }

  .stat-value {
    font-size: 28px;
    line-height: 1.1;
    font-weight: 700;
    color: $text-primary;

    &.amount {
      font-size: 22px;
    }
  }

  .stat-label {
    margin-top: 8px;
    font-size: 13px;
    color: $text-secondary;
  }
}

.toolbar {
  background: $bg-white;
  padding: clamp(18px, 2vw, 24px);
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  overflow: hidden;
  container-type: inline-size;
  container-name: purchase-plan-toolbar;

  .toolbar-row {
    display: grid;
    grid-template-columns: minmax(300px, 1.25fr) minmax(156px, 184px) minmax(260px, max-content);
    align-items: start;
    gap: 12px 16px;
    width: 100%;
    min-width: 0;
    margin: 0 !important;
  }

  .toolbar-col {
    width: 100%;
    min-width: 0;
    max-width: none !important;
    flex: none !important;
    padding-left: 0 !important;
    padding-right: 0 !important;
  }

  .toolbar-search-col {
    max-width: 460px;
  }

  .toolbar-status-col {
    max-width: 184px;
  }

  .toolbar-actions {
    display: flex;
    justify-content: flex-end;
    align-items: flex-start;
    justify-self: stretch;
    width: 100%;
    min-width: 0;
    gap: 12px;
    flex-wrap: wrap;
  }

  :deep(.el-input),
  :deep(.el-select) {
    width: 100%;
    max-width: 100%;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    min-height: 40px;
  }

  :deep(.el-input__inner) {
    text-overflow: clip;
  }
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  padding: 18px 18px 0;
  display: flex;
  flex-direction: column;
}

.table-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
  flex-shrink: 0;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.table-subtitle {
  margin-top: 6px;
  color: $text-secondary;
  font-size: 13px;
}

.status-tags {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

.pagination {
  background: $bg-white;
  padding: 18px 20px;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;

  .total {
    color: $text-regular;
    font-size: 14px;
  }
}

.action-buttons {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
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

.plan-form {
  .form-section + .form-section {
    margin-top: 24px;
  }
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid $border-lighter;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px 20px;
}

.form-item-full {
  grid-column: 1 / -1;
}

.form-field-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: $text-secondary;
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.item-summary {
  font-size: 13px;
  color: $text-secondary;

  strong {
    color: $danger-color;
    font-size: 16px;
    margin-left: 6px;
  }
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 1400px;
  width: max-content;
}

.item-table-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 8px;
  -webkit-overflow-scrolling: touch;
}

.item-row {
  display: grid;
  grid-template-columns: 72px minmax(180px, 1.1fr) minmax(180px, 1.1fr) minmax(150px, 0.9fr) minmax(120px, 0.75fr) minmax(120px, 0.75fr) minmax(140px, 0.9fr) minmax(260px, 1.35fr) 72px;
  gap: 12px;
  align-items: start;
  padding: 12px;
  border: 1px solid $border-lighter;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff, #fcfcfd);
  width: 100%;

  > * {
    min-width: 0;
  }
}

.item-row-header {
  background: #f8fafc;
  border-style: dashed;
  padding: 10px 12px;
  position: sticky;
  top: 0;
  z-index: 2;
}

.item-header-cell {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  line-height: 1.5;
  white-space: normal;
}

.item-row-header .item-header-cell {
  text-align: center;
}

.align-right {
  text-align: right;
}

.align-center {
  text-align: center;
}

.item-index {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba($primary-color, 0.08);
  color: $primary-color;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  justify-self: center;
}

.item-row :deep(.el-select__wrapper),
.item-row :deep(.el-input__wrapper),
.item-row :deep(.el-textarea__inner) {
  min-height: 42px;
  height: auto;
}

.item-row :deep(.el-select__selected-item) {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  line-height: 1.5;
}

.item-row :deep(.el-input-number) {
  width: 100%;
}

.item-row :deep(.el-select__wrapper) {
  justify-content: center;
}

.item-row :deep(.el-select__selected-item),
.item-row :deep(.el-select__placeholder),
.item-row :deep(.el-input__inner),
.item-row :deep(.el-input-number .el-input__inner),
.item-row :deep(.el-textarea__inner) {
  text-align: center;
}

.item-row :deep(.el-textarea__inner) {
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.subtotal-cell {
  font-size: 14px;
  font-weight: 600;
  color: $danger-color;
  text-align: center;
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;
  align-self: center;
}

.item-action-button {
  justify-self: center;
}

.add-item-button {
  margin-top: 12px;
}

.attachment-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.upload-area {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  border: 1px solid $border-base;
  border-radius: $border-radius-base;
  cursor: pointer;
  font-size: 13px;
  color: $text-regular;
  background: $bg-white;
  transition: all 0.2s;

  &:hover {
    border-color: $primary-color;
    color: $primary-color;
  }
}

.attachment-field-tip {
  font-size: 12px;
  color: $text-secondary;
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
  border: 1px solid $border-lighter;
  border-radius: 12px;
  background: #f8fbff;
}

.attachment-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.attachment-name {
  color: $text-primary;
  font-size: 14px;
  word-break: break-all;
}

.attachment-status {
  color: $text-secondary;
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
  border: 1px dashed $border-light;
  border-radius: 12px;
  color: $text-secondary;
  background: #fafcff;
}

.audit-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  background: #f7fafc;
  border: 1px solid #e5edf5;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 18px;
}

.reverse-audit-tip {
  margin-bottom: 18px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
  line-height: 1.6;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;

  strong {
    color: $text-primary;
    font-size: 14px;
  }
}

.summary-label {
  color: $text-secondary;
  font-size: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 12px;
  color: $text-secondary;
}

.detail-value {
  font-size: 14px;
  color: $text-primary;
  word-break: break-all;
}

.detail-section + .detail-section {
  margin-top: 22px;
}

.order-link-list {
  display: grid;
  gap: 12px;
}

.order-link-card {
  border: 1px solid $border-lighter;
  border-radius: 12px;
  padding: 14px 16px;
  background: #fafcff;
}

.order-link-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: $text-primary;
  font-size: 14px;
  line-height: 1.5;

  span {
    color: $text-secondary;
    font-size: 12px;
  }
}

.order-link-meta {
  margin-top: 8px;
  color: $text-secondary;
  font-size: 12px;
}

.order-link-items {
  margin-top: 10px;
  color: $text-regular;
  line-height: 1.6;
  word-break: break-word;
}

.detail-section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid $border-lighter;
}

.linked-order-table {
  --linked-order-group-even-bg: var(--el-bg-color);
  --linked-order-group-even-hover-bg: var(--el-fill-color-light);
  --linked-order-group-odd-bg: var(--el-color-primary-light-9);
  --linked-order-group-odd-hover-bg: var(--el-color-primary-light-8);
}

.linked-order-table :deep(.linked-order-group-even td.el-table__cell) {
  background: var(--linked-order-group-even-bg);
}

.linked-order-table :deep(.linked-order-group-even:hover > td.el-table__cell) {
  background: var(--linked-order-group-even-hover-bg);
}

.linked-order-table :deep(.linked-order-group-odd td.el-table__cell) {
  background: var(--linked-order-group-odd-bg);
}

.linked-order-table :deep(.linked-order-group-odd:hover > td.el-table__cell) {
  background: var(--linked-order-group-odd-hover-bg);
}

.detail-span-2 {
  grid-column: 1 / -1;
}

:deep(.el-dialog__body) {
  padding-top: 14px;
}

:deep(.el-input-number .el-input__wrapper) {
  width: 100%;
}

:deep(.el-table .cell) {
  word-break: break-word;
}

@media (max-width: 1440px) {
  .item-list {
    min-width: 1320px;
  }
}

@container purchase-plan-toolbar (max-width: 960px) {
  .toolbar {
    padding: 18px;

    .toolbar-row {
      grid-template-columns: minmax(0, 1fr) minmax(148px, 184px);
    }

    .toolbar-search-col,
    .toolbar-status-col {
      max-width: none;
    }

    .toolbar-actions {
      grid-column: 1 / -1;
      justify-content: flex-end;
    }
  }
}

@container purchase-plan-toolbar (max-width: 680px) {
  .toolbar {
    padding: 16px;

    .toolbar-row {
      grid-template-columns: 1fr;
      gap: 12px;
    }

    .toolbar-actions {
      grid-column: auto;
      justify-content: flex-end;
    }
  }
}

@media (max-width: 1280px) {
  .stats-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
