<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadFiles } from 'element-plus'
import type { Material } from '@/types/material'
import { INBOUND_SOURCE_TYPES } from '@/constants/inbound'
import { inboundApi } from '@/api/modules/inbound'
import { debounce } from '@/utils'
import { warehouseApi } from '@/api/modules/warehouse'
import { materialApi } from '@/api/modules/material'
import { supplierApi } from '@/api/modules/supplier'
import { useOrgStore } from '@/stores/modules/org'
import { useUserStore } from '@/stores/modules/user'
import { useMaterialStore } from '@/stores/modules/material'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import type { Warehouse, Location } from '@/types/warehouse'
import type { OrgTreeNode } from '@/types/org'
import type { InboundAreaValidationLocationSummary, InboundOrderValidationErrorData, InboundSourceOrderOption } from '@/types/inbound'
import InboundAreaValidationSummary from './InboundAreaValidationSummary.vue'

// 规格选项类型
interface SpecOption {
  materialId: number
  spec: string
  unit: string
  shelfLifeDays: number
}

interface ItemAreaValidation {
  lineKey?: string
  areaCoefficient?: number | null
  expectedOccupiedArea?: number | null
  validationResult?: string | null
  message?: string | null
  field?: string | null
}

// ---- 明细行类型 ----
interface ItemRow {
  warehouseId:    number | null
  locationId:     number | null
  materialId:     number | null
  materialName:   string
  spec:           string
  unit:           string
  quantity:       number | null
  unitCost:       number | null
  batchNo:        string
  productionDate: string
  expiryDate:     string
  // 采购入库自动带出的只读参考字段
  orderQty:             number | null
  inboundQty:           number | null
  remainingInboundQty:  number | null
  // UI 专用
  _keyword:       string
  _shelfLife:     number
  _locations:     Location[]
  _specs:         SpecOption[]  // 可选规格列表
  _areaValidation?: ItemAreaValidation
}

interface FormData {
  sourceType:     string
  orgId:          number | null
  receivingOrgId: number | null
  supplierId:     number | null
  supplierName:   string
  sourceOrderId:  number | null
  sourceOrderNo:  string
  remark:         string
  attachments:    string[]
  items:          ItemRow[]
}

interface AttachmentView {
  name: string
  url: string
  accessible: boolean
}

interface Props {
  modelValue: boolean
  orderId?:   number | null
}
const props = withDefaults(defineProps<Props>(), { modelValue: false, orderId: null })
const emit  = defineEmits<{ 'update:modelValue': [val: boolean]; success: [] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const formRef    = ref<FormInstance>()
const submitting = ref(false)
const orderStatus = ref<string | null>(null)
const warehouses = ref<Warehouse[]>([])
const suppliers = ref<{ id: number; supplierName: string }[]>([])
const sourceOrders = ref<InboundSourceOrderOption[]>([])
const orgStore = useOrgStore()
const userStore = useUserStore()
const materialStore = useMaterialStore()

const SOURCE_ORDER_DISABLED_TYPES = ['donation', 'other']

const defaultRow = (): ItemRow => ({
  warehouseId: null, locationId: null, materialId: null, materialName: '', spec: '', unit: '',
  quantity: null, unitCost: null, batchNo: '', productionDate: '', expiryDate: '',
  orderQty: null, inboundQty: null, remainingInboundQty: null,
  _keyword: '', _shelfLife: 0, _locations: [], _specs: [], _areaValidation: undefined,
})

const formatDate = (date = new Date()) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
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

const getCurrentUserOrgId = () => userStore.userInfo?.orgId ?? null
const resolveWarehouseScopeOrgId = () => form.value.orgId ?? getCurrentUserOrgId()

const form = ref<FormData>({
  sourceType: 'purchase',
  orgId: getCurrentUserOrgId(), receivingOrgId: null, supplierId: null, supplierName: '', sourceOrderId: null, sourceOrderNo: '',
  remark: '', attachments: [],
  items: [defaultRow()],
})

const rules: FormRules = {
  sourceType: [{ required: true, message: '请选择入库类型' }],
}

const isSourceOrderDisabled = computed(() => SOURCE_ORDER_DISABLED_TYPES.includes(form.value.sourceType))

/** 采购入库已选择来源单号时，供应商锁定为采购订单关联的供应商，不允许修改 */
const isSupplierLocked = computed(() =>
  form.value.sourceType === 'purchase' && form.value.sourceOrderId != null
)

/** 是否为采购入库模式且已选择来源订单 */
const isPurchaseMode = computed(() =>
  form.value.sourceType === 'purchase' && form.value.sourceOrderId != null
)

const buildPurchaseQuantitySummary = () => {
  const summary = new Map<number, number>()
  for (const item of form.value.items) {
    if (item.materialId == null || item.quantity == null || item.quantity <= 0) {
      continue
    }
    summary.set(item.materialId, (summary.get(item.materialId) || 0) + item.quantity)
  }
  return summary
}

const getPurchaseQuantityExceededMessage = (row: ItemRow) => {
  if (!isPurchaseMode.value || row.materialId == null || row.remainingInboundQty == null) {
    return ''
  }
  const totalQty = buildPurchaseQuantitySummary().get(row.materialId) || 0
  if (totalQty <= row.remainingInboundQty) {
    return ''
  }
  return `同一物料累计入库数量 ${Number(totalQty.toFixed(3))} 超出当前可入库数量 ${row.remainingInboundQty}，请调整`
}

// ---- 从来源订单选择物料弹窗 ----
const selectDialogVisible = ref(false)
const selectDialogKeyword = ref('')
const selectDialogLoading = ref(false)
const sourceOrderItems = ref<any[]>([])
const selectedMaterialIds = ref<number[]>([])

const filteredSourceItems = computed(() => {
  if (!selectDialogKeyword.value?.trim()) return sourceOrderItems.value
  const kw = selectDialogKeyword.value.trim().toLowerCase()
  return sourceOrderItems.value.filter((item: any) =>
    (item.materialName || '').toLowerCase().includes(kw) ||
    (item.spec || '').toLowerCase().includes(kw)
  )
})

/** 打开"从来源订单选择物料"弹窗 */
const openSelectMaterialDialog = async () => {
  if (!form.value.sourceOrderId) return
  selectDialogVisible.value = true
  selectDialogKeyword.value = ''
  selectedMaterialIds.value = []
  selectDialogLoading.value = true
  try {
    const res = await inboundApi.getSourceOrderItems(form.value.sourceOrderId, props.orderId ?? undefined)
    if (res.code === 'SUCCESS' && res.data) {
      sourceOrderItems.value = res.data
    } else {
      sourceOrderItems.value = []
    }
  } catch (e) {
    console.error('加载来源订单物料失败', e)
    sourceOrderItems.value = []
  } finally {
    selectDialogLoading.value = false
  }
}

/** 确认选择物料：以追加方式添加到入库明细 */
const confirmSelectMaterials = () => {
  if (selectedMaterialIds.value.length === 0) {
    ElMessage.warning('请至少选择一条物料')
    return
  }
  const selectedItems = sourceOrderItems.value.filter((item: any) =>
    selectedMaterialIds.value.includes(item.materialId)
  )
  // 以追加方式添加到明细末尾，不覆盖已有数据
  for (const item of selectedItems) {
    form.value.items.push({
      warehouseId: null,
      locationId: null,
      materialId: item.materialId,
      materialName: item.materialName || '',
      spec: item.spec || '',
      unit: item.unit || '',
      quantity: item.remainingInboundQty ?? null,
      unitCost: item.unitPrice ?? null,
      batchNo: '',
      productionDate: '',
      expiryDate: '',
      orderQty: item.orderQty ?? null,
      inboundQty: item.inboundQty ?? null,
      remainingInboundQty: item.remainingInboundQty ?? null,
      _keyword: item.materialName || '',
      _shelfLife: 0,
      _locations: [],
      _specs: [],
      _areaValidation: undefined,
    })
  }
  selectDialogVisible.value = false
  ElMessage.success(`已追加 ${selectedItems.length} 条物料`)
}

// 文件上传
const fileList = ref<UploadFile[]>([])
const areaValidationSummariesState = ref<InboundAreaValidationLocationSummary[]>([])
const areaValidationGlobalMessageState = ref<string | null>(null)
const isAttachmentUrl = (value: string) => /^(\/upload\/|https?:\/\/)/.test(value)
const getAttachmentName = (value: string) => decodeURIComponent(value.split('/').pop() || value)
const savedAttachments = computed<AttachmentView[]>(() =>
  (form.value.attachments ?? []).map((attachment) => ({
    name: getAttachmentName(attachment),
    url: attachment,
    accessible: isAttachmentUrl(attachment),
  }))
)
const areaValidationSummaries = computed<InboundAreaValidationLocationSummary[]>(() => areaValidationSummariesState.value)
const areaValidationGlobalMessage = computed<string | null>(() => areaValidationGlobalMessageState.value)
const ATTACHMENT_LIMITS = {
  image: 20 * 1024 * 1024,
  video: 200 * 1024 * 1024,
  document: 50 * 1024 * 1024,
} as const

const resolveAttachmentCategory = (file: File) => {
  const type = (file.type || '').toLowerCase()
  const name = (file.name || '').toLowerCase()
  if (type.startsWith('image/') || /\.(jpg|jpeg|png|webp)$/i.test(name)) return 'image'
  if (type.startsWith('video/') || /\.(mp4|mov)$/i.test(name)) return 'video'
  return 'document'
}

const attachmentLimitMessage = (category: keyof typeof ATTACHMENT_LIMITS) => {
  switch (category) {
    case 'image':
      return '图片附件不能超过20MB'
    case 'video':
      return '视频附件不能超过200MB'
    default:
      return '文档附件不能超过50MB'
  }
}

const handleUploadChange = async (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  if (!uploadFile.raw) return true
  const category = resolveAttachmentCategory(uploadFile.raw)
  const maxSize = ATTACHMENT_LIMITS[category]
  if (uploadFile.size != null && uploadFile.size > maxSize) {
    ElMessage.warning(attachmentLimitMessage(category))
    fileList.value = uploadFiles.filter((item) => item.uid !== uploadFile.uid)
    return false
  }
  if (!props.orderId) {
    ElMessage.warning('请先保存入库单后再上传附件')
    fileList.value = []
    return false
  }
  try {
    await inboundApi.uploadAttachments(props.orderId, [uploadFile.raw])
    ElMessage.success('附件上传成功')
    await loadOrder(props.orderId)
  } catch {
    fileList.value = form.value.attachments.map((url, idx) => ({ name: getAttachmentName(url), uid: idx, url } as UploadFile))
  }
  return true
}
const handleRemove = (file: UploadFile) => {
  if (!file.url) return
  const idx = form.value.attachments.indexOf(file.url)
  if (idx > -1) form.value.attachments.splice(idx, 1)
  fileList.value = form.value.attachments.map((url, idx) => ({ name: getAttachmentName(url), uid: idx, url } as UploadFile))
}

const handlePreviewAttachment = async (attachment: AttachmentView) => {
  if (!props.orderId) return
  if (!attachment.accessible) {
    ElMessage.warning('历史附件仅保存了文件名，请重新上传后再查看')
    return
  }
  try {
    await inboundApi.previewAttachment(props.orderId, attachment.url)
  } catch (error) {
    if (error instanceof Error && error.message === 'PREVIEW_WINDOW_BLOCKED') {
      ElMessage.warning('浏览器拦截了预览窗口，请允许弹窗后重试')
    }
  }
}

const handleDownloadAttachment = async (attachment: AttachmentView) => {
  if (!props.orderId) return
  if (!attachment.accessible) {
    ElMessage.warning('历史附件仅保存了文件名，请重新上传后再下载')
    return
  }
  try {
    await inboundApi.downloadAttachment(props.orderId, attachment.url)
  } catch {}
}

const handleDeleteAttachment = (attachment: AttachmentView) => {
  const idx = form.value.attachments.indexOf(attachment.url)
  if (idx > -1) form.value.attachments.splice(idx, 1)
  fileList.value = form.value.attachments.map((url, index) => ({ name: getAttachmentName(url), uid: index, url } as UploadFile))
}

const resetAreaPreview = () => {
  areaValidationSummariesState.value = []
  areaValidationGlobalMessageState.value = null
  form.value.items.forEach((item) => {
    item._areaValidation = undefined
  })
}

const buildAreaPreviewPayload = () => ({
  warehouseId: null,
  items: form.value.items
    .map((item, index) => ({ item, index }))
    .filter(({ item }) =>
      item.warehouseId != null &&
      item.locationId != null &&
      item.materialId != null &&
      item.quantity != null &&
      item.quantity > 0
    )
    .map(({ item, index }) => ({
      lineKey: String(index),
      warehouseId: item.warehouseId,
      locationId: item.locationId,
      materialId: item.materialId,
      quantity: item.quantity,
    })),
})

const hasPreviewableItems = computed(() =>
  form.value.items.some((item) =>
    item.warehouseId != null &&
    item.locationId != null &&
    item.materialId != null &&
    item.quantity != null &&
    item.quantity > 0
  )
)

const applyAreaPreview = (data?: any) => {
  const itemResultMap = new Map((data?.itemResults ?? []).map((result: any) => [result.lineKey, result]))
  form.value.items.forEach((item, index) => {
    const result = itemResultMap.get(String(index))
    item._areaValidation = result
      ? {
          lineKey: result.lineKey,
          areaCoefficient: result.areaCoefficient ?? null,
          expectedOccupiedArea: result.expectedOccupiedArea ?? null,
          validationResult: result.validationResult ?? null,
          message: result.message ?? null,
        }
      : undefined
  })
  areaValidationSummariesState.value = data?.locationSummaries ?? []
  areaValidationGlobalMessageState.value = data?.globalMessage ?? null
}

const refreshAreaPreview = debounce(async () => {
  if (!hasPreviewableItems.value) {
    resetAreaPreview()
    return
  }
  try {
    const result = await inboundApi.previewAreaValidation(buildAreaPreviewPayload())
    if (result.code === 'SUCCESS') {
      applyAreaPreview(result.data)
      return
    }
  } catch {}
  resetAreaPreview()
}, 300)

watch(
  () => form.value.items.map((item) => [item.warehouseId, item.locationId, item.materialId, item.quantity]),
  () => {
    refreshAreaPreview()
  },
  { deep: true }
)

watch(() => props.modelValue, async (val) => {
  if (val) {
    resetAreaPreview()
    await Promise.all([ensureOrgTreeLoaded(), loadSuppliers(), materialStore.fetchActiveList()])
    props.orderId ? await loadOrder(props.orderId) : resetForm()
    await Promise.all([loadWarehouses(), loadSourceOrders(form.value.sourceType)])
  }
})

watch(
  () => form.value.receivingOrgId,
  () => {}
)

// 入库类型变化时重新加载来源单号
watch(() => form.value.sourceType, async (type) => {
  form.value.sourceOrderId = null
  form.value.sourceOrderNo = ''
  await loadSourceOrders(type)
})

const syncWarehouseOptionsToCurrentScope = () => {
  const allowedWarehouseIds = new Set(warehouses.value.map((warehouse) => warehouse.id))
  form.value.items.forEach((item) => {
    if (item.warehouseId != null && !allowedWarehouseIds.has(item.warehouseId)) {
      item.warehouseId = null
      item.locationId = null
      item._locations = []
    }
  })
}

const loadWarehouses = async (orgId: number | null = resolveWarehouseScopeOrgId()) => {
  if (!orgId) {
    warehouses.value = []
    syncWarehouseOptionsToCurrentScope()
    return
  }
  try {
    const res = await warehouseApi.getList({ pageNum: 1, pageSize: 999, status: 'active', orgId })
    warehouses.value = res.code === 'SUCCESS' && res.data ? res.data.list : []
  } catch (e) { console.error(e) }
  syncWarehouseOptionsToCurrentScope()
}

const ensureOrgTreeLoaded = async () => {
  if (orgStore.treeData.length === 0) {
    await orgStore.fetchTree()
  }
}

const findOrgNameById = (nodes: OrgTreeNode[], orgId: number | null): string | null => {
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

const loadSuppliers = async () => {
  try {
    const res = await supplierApi.getList({ pageNum: 1, pageSize: 999, status: 'approved' })
    if (res.code === 'SUCCESS' && res.data) suppliers.value = res.data.list
  } catch (e) { console.error(e) }
}

// 根据入库类型加载来源单号
const loadSourceOrders = async (sourceType: string) => {
  sourceOrders.value = []
  try {
    if (SOURCE_ORDER_DISABLED_TYPES.includes(sourceType)) {
      return
    }
    const res = await inboundApi.getSourceOrderOptions(sourceType, props.orderId ?? undefined)
    if (res.code === 'SUCCESS' && res.data) {
      sourceOrders.value = res.data
    }
  } catch (e) { console.error(e) }
}

// 选择来源单号时自动填充供应商和物料明细
const onSourceOrderChange = async (id: number) => {
  const order = sourceOrders.value.find(o => o.id === id)
  if (order) {
    form.value.sourceOrderNo = order.orderNo
    if (form.value.sourceType === 'purchase') {
      if (order.supplierId) form.value.supplierId = order.supplierId
      if (order.supplierName) form.value.supplierName = order.supplierName
      // 自动加载采购订单未完全入库的物料明细
      await loadPurchaseOrderItems(id)
    }
  } else {
    // 清空来源单号时重置明细和供应商
    if (form.value.sourceType === 'purchase') {
      form.value.supplierId = null
      form.value.supplierName = ''
      form.value.items = [defaultRow()]
    }
  }
}

// 根据采购订单ID加载未完全入库的物料明细
const loadPurchaseOrderItems = async (purchaseOrderId: number) => {
  try {
    const res = await inboundApi.getSourceOrderItems(purchaseOrderId, props.orderId ?? undefined)
    if (res.code === 'SUCCESS' && res.data && res.data.length > 0) {
      form.value.items = res.data.map((item: any) => ({
        warehouseId: null,
        locationId: null,
        materialId: item.materialId,
        materialName: item.materialName || '',
        spec: item.spec || '',
        unit: item.unit || '',
        quantity: item.remainingInboundQty ?? null,
        unitCost: item.unitPrice ?? null,
        batchNo: '',
        productionDate: '',
        expiryDate: '',
        orderQty: item.orderQty ?? null,
        inboundQty: item.inboundQty ?? null,
        remainingInboundQty: item.remainingInboundQty ?? null,
        _keyword: item.materialName || '',
        _shelfLife: 0,
        _locations: [],
        _specs: [],
        _areaValidation: undefined,
      }))
    } else {
      form.value.items = [defaultRow()]
    }
  } catch (e) {
    console.error('加载采购订单物料明细失败', e)
    form.value.items = [defaultRow()]
  }
}

const loadOrder = async (id: number) => {
  try {
    const res = await inboundApi.getDetail(id)
    if (res.code === 'SUCCESS' && res.data) {
      const d = res.data
      orderStatus.value = d.status ?? null

      // 编辑采购入库单时，加载来源订单物料数据用于防超入和追加物料
      let poItemsMap: Map<number, any> = new Map()
      if (d.sourceType === 'purchase' && (d as any).sourceOrderId) {
        try {
          const poRes = await inboundApi.getSourceOrderItems((d as any).sourceOrderId, props.orderId ?? undefined)
          if (poRes.code === 'SUCCESS' && poRes.data) {
            poItemsMap = new Map(poRes.data.map((item: any) => [item.materialId, item]))
          }
        } catch (e) { console.error('加载来源订单物料失败', e) }
      }

      form.value = {
        sourceType:   d.sourceType,
        orgId:        (d as any).orgId ?? null,
        receivingOrgId: (d as any).receivingOrgId ?? null,
        supplierId:   (d as any).supplierId ?? null,
        supplierName: (d as any).supplierName ?? '',
        sourceOrderId: (d as any).sourceOrderId ?? null,
        sourceOrderNo: (d as any).sourceOrderNo ?? '',
        remark:       d.remark ?? '',
        attachments:  (d as any).attachments ?? [],
        items: await Promise.all((d.items ?? []).map(async i => {
          const locations = await loadLocations(i.warehouseId)
          const poItem = poItemsMap.get(i.materialId) || null
          return {
            warehouseId:  i.warehouseId,
            locationId:   i.locationId ?? null,
            materialId:   i.materialId,
            materialName: i.materialName,
            spec:         i.spec ?? '',
            unit:         i.unit,
            quantity:     i.quantity,
            unitCost:     i.unitCost ?? null,
            batchNo:      i.batchNo ?? '',
            productionDate: i.productionDate ?? '',
            expiryDate:     i.expiryDate ?? '',
            orderQty:             poItem?.orderQty ?? null,
            inboundQty:           poItem?.inboundQty ?? null,
            remainingInboundQty:  poItem?.remainingInboundQty ?? null,
            _keyword:       i.materialName,
            _shelfLife:     0,
            _locations:     locations,
            _specs:         [],
          }
        })),
      }
      fileList.value = form.value.attachments.map((url, idx) => ({ name: getAttachmentName(url), uid: idx, url } as UploadFile))
    }
  } catch {}
}

const resetForm = () => {
  orderStatus.value = null
  areaValidationSummariesState.value = []
  areaValidationGlobalMessageState.value = null
  form.value = {
    sourceType: 'purchase',
    orgId: getCurrentUserOrgId(), receivingOrgId: null, supplierId: null, supplierName: '', sourceOrderId: null, sourceOrderNo: '',
    remark: '', attachments: [],
    items: [defaultRow()],
  }
  fileList.value = []
  formRef.value?.clearValidate()
}

// 仓位加载
const loadLocations = async (warehouseId: number | null): Promise<Location[]> => {
  if (!warehouseId) return []
  try {
    const res = await warehouseApi.getLocations({ pageNum: 1, pageSize: 999, warehouseId })
    return res.code === 'SUCCESS' && res.data ? res.data.list : []
  } catch { return [] }
}

const onItemWarehouseChange = async (row: ItemRow) => {
  row.locationId = null
  row._locations = await loadLocations(row.warehouseId)
}

// 物料名称搜索 - 只显示唯一的物料名称
const searchMaterials = (keyword: string, cb: (results: any[]) => void) => {
  if (!keyword?.trim()) { cb([]); return }
  materialApi.getList({ pageNum: 1, pageSize: 20, materialName: keyword, status: 'active' })
    .then(res => {
      if (res.code === 'SUCCESS' && res.data) {
        // 去重，只保留唯一的物料名称
        const seen = new Set<string>()
        const unique = res.data.list.filter((m: any) => {
          if (seen.has(m.materialName)) return false
          seen.add(m.materialName)
          return true
        })
        cb(unique)
      } else {
        cb([])
      }
    })
    .catch(() => cb([]))
}

// 选择物料名称后，加载该物料的所有规格
const onMaterialNameSelect = async (row: ItemRow, material: Material) => {
  row.materialName = material.materialName
  row._keyword = material.materialName
  row.materialId = null  // 清空，等选择规格后再设置
  row.spec = ''
  row.unit = ''
  row._specs = []

  // 根据物料名称加载所有规格
  try {
    const res = await materialApi.getList({ pageNum: 1, pageSize: 100, materialName: material.materialName, status: 'active' })
    if (res.code === 'SUCCESS' && res.data) {
      row._specs = res.data.list.map((m: any) => ({
        materialId: m.id,
        spec: m.materialSpec || m.spec || '',
        unit: m.unit || '',
        shelfLifeDays: m.shelfLifeDays || 0
      }))
    }
  } catch (e) { console.error(e) }
}

// 选择规格后，带出单位和物料ID
const onSpecSelect = (row: ItemRow, specOption: SpecOption) => {
  row.materialId = specOption.materialId
  row.unit = specOption.unit
  row._shelfLife = specOption.shelfLifeDays
  if (row._shelfLife && row.productionDate) {
    row.expiryDate = addDays(row.productionDate, row._shelfLife)
  }
}

const onProductionDateChange = (row: ItemRow) => {
  if (row._shelfLife && row.productionDate) {
    row.expiryDate = addDays(row.productionDate, row._shelfLife)
  }
}

const addDays = (date: string, days: number) => {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

// 行操作
const addItem    = () => form.value.items.push(defaultRow())
const removeItem = (idx: number) => {
  form.value.items.splice(idx, 1)
  // 如果明细全部删空，采购模式下保持空表格等待追加；非采购模式保留一行默认空行
  if (form.value.items.length === 0 && !isPurchaseMode.value) {
    form.value.items.push(defaultRow())
  }
}

const handleStructuredValidationError = (error: Error) => {
  const payload = (error as Error & {
    backendPayload?: {
      code?: string
      message?: string
      data?: InboundOrderValidationErrorData
      fieldErrors?: InboundOrderValidationErrorData['fieldErrors']
      globalMessage?: string | null
    }
  }).backendPayload

  if (payload?.code === 'VALIDATION_FAILED' && payload.data) {
    const fieldErrors = payload.fieldErrors || payload.data.fieldErrors || []
    fieldErrors.forEach((fieldError) => {
      if (fieldError.lineKey == null) return
      const row = form.value.items[Number(fieldError.lineKey)]
      if (!row) return
      row._areaValidation = {
        ...(row._areaValidation ?? {}),
        lineKey: fieldError.lineKey,
        field: fieldError.field ?? null,
        validationResult: '校验失败',
        message: fieldError.message,
      }
    })

    ElMessage.warning(payload.data.globalMessage || payload.message)
    return true
  }
  return false
}

const applyRequiredItemFieldError = (row: ItemRow, field: string, message: string) => {
  row._areaValidation = {
    ...(row._areaValidation ?? {}),
    field,
    validationResult: '校验失败',
    message,
  }
}

const resolveRequiredItemField = (items: ItemRow[]) => {
  for (const row of items) {
    if (!row.warehouseId) return { row, field: 'warehouseId', message: '入库仓库不能为空' }
    if (!row.locationId) return { row, field: 'locationId', message: '仓位不能为空' }
    if (!row.materialId && !row.materialName) return { row, field: 'materialId', message: '物料不能为空' }
    if (!row.spec) return { row, field: 'spec', message: '规格不能为空' }
    if (!row.materialId) return { row, field: 'materialId', message: '物料不能为空' }
    if (!row.unit) return { row, field: 'unit', message: '单位不能为空' }
    if (isFutureProductionDate(row.productionDate)) return { row, field: 'productionDate', message: '生产日期不能晚于今天' }
  }
  return null
}

const handleSubmitLabel = computed(() => {
  if (!props.orderId) return '创建'
  return orderStatus.value === 'rejected' ? '保存并提交' : '保存'
})

const handleSubmit = async () => {
  if (!await formRef.value?.validate().catch(() => false)) return
  // 数量>0的明细需要校验完整性
  const qtyItemsWithIndex = form.value.items
    .map((item, index) => ({ item, index }))
    .filter(({ item }) => item.quantity != null && item.quantity > 0)
  const qtyItems = qtyItemsWithIndex.map(({ item }) => item)
  if (qtyItems.length === 0) {
    ElMessage.warning('请至少填写一条数量大于0的入库明细'); return
  }
  const missingField = resolveRequiredItemField(qtyItems)
  if (missingField) {
    applyRequiredItemFieldError(missingField.row, missingField.field, missingField.message)
    ElMessage.warning(missingField.message)
    return
  }
  const exceededRow = qtyItems.find((item) => getPurchaseQuantityExceededMessage(item))
  if (exceededRow) {
    exceededRow._areaValidation = {
      ...(exceededRow._areaValidation ?? {}),
      field: 'quantity',
      validationResult: '校验失败',
      message: getPurchaseQuantityExceededMessage(exceededRow),
    }
    ElMessage.warning(getPurchaseQuantityExceededMessage(exceededRow))
    return
  }
  // 采购入库模式：防超入校验（按物料汇总数量不超过当前可入库总量）
  if (isPurchaseMode.value && form.value.sourceOrderId) {
    // 加载最新来源订单物料数据用于校验
    try {
      const res = await inboundApi.getSourceOrderItems(form.value.sourceOrderId, props.orderId ?? undefined)
      if (res.code === 'SUCCESS' && res.data) {
        const remainMap = new Map<number, number>()
        for (const po of res.data) {
          remainMap.set(po.materialId, po.remainingInboundQty ?? 0)
        }
        // 按物料ID汇总本次入库数量
        const sumMap = new Map<number, number>()
        for (const item of qtyItems) {
          if (item.materialId != null) {
            sumMap.set(item.materialId, (sumMap.get(item.materialId) || 0) + (item.quantity || 0))
          }
        }
        for (const [materialId, totalQty] of sumMap) {
          const remaining = remainMap.get(materialId)
          if (remaining == null) {
            ElMessage.warning(`物料不在来源采购订单中，不允许入库`)
            return
          }
          if (totalQty > remaining) {
            // 找物料名
            const name = qtyItems.find(i => i.materialId === materialId)?.materialName || ''
            ElMessage.warning(`物料"${name}"本次入库总量 ${totalQty} 超出当前可入库数量 ${remaining}，请调整`)
            return
          }
        }
      }
    } catch (e) {
      console.error('校验来源订单物料失败', e)
    }
  }
  submitting.value = true
  try {
    // 仅提交数量>0的物料明细，数量为0的自动过滤（本次不入库）
    const payload = {
      ...form.value,
      items: qtyItemsWithIndex.map(({ item, index }) => {
        const { _keyword, _shelfLife, _locations, _specs, _areaValidation, orderQty, inboundQty, remainingInboundQty, ...rest } = item
        return {
          ...rest,
          lineKey: String(index),
        }
      }),
    }
    if (props.orderId) {
      const shouldSubmitAfterSave = orderStatus.value === 'rejected'
      await inboundApi.update(props.orderId, payload)
      if (shouldSubmitAfterSave) {
        await inboundApi.submit(props.orderId, form.value.version)
        ElMessage.success('保存并提交成功')
      } else {
        ElMessage.success('更新成功')
      }
    } else {
      await inboundApi.create(payload)
      ElMessage.success('创建成功')
    }
    visible.value = false
    emit('success')
  } catch (error) {
    if (error instanceof Error && handleStructuredValidationError(error)) return
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
  finally { submitting.value = false }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="orderId ? '编辑入库单' : '新增入库单'"
    width="1300px"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="入库类型" prop="sourceType">
            <el-select v-model="form.sourceType" style="width:100%">
              <el-option v-for="t in INBOUND_SOURCE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="入库组织">
            <OrgTreeSelect v-model="form.receivingOrgId" :active-only="true" placeholder="请选择组织" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商">
            <el-select v-model="form.supplierId" placeholder="请选择供应商" style="width:100%" clearable
              :disabled="isSupplierLocked"
              @change="(id: number) => { const s = suppliers.find(x => x.id === id); form.supplierName = s?.supplierName || '' }">
              <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="来源单号">
            <el-select v-model="form.sourceOrderId" placeholder="请选择来源单号" style="width:100%" clearable
              :disabled="isSourceOrderDisabled"
              @change="onSourceOrderChange">
              <el-option v-for="p in sourceOrders" :key="p.id" :label="p.orderNo" :value="p.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="附件">
            <div class="attachment-panel">
              <el-upload
                v-model:file-list="fileList"
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handleUploadChange"
                :on-remove="handleRemove"
                :limit="5"
                accept=".jpg,.jpeg,.png,.webp,.mp4,.mov,.pdf,.doc,.docx,.xls,.xlsx"
              >
                <el-button size="small" type="primary">上传文件</el-button>
              </el-upload>
              <div v-if="savedAttachments.length" class="saved-attachment-list">
                <div v-for="attachment in savedAttachments" :key="attachment.url" class="saved-attachment-item">
                  <span class="saved-attachment-name">{{ attachment.name }}</span>
                  <el-button link type="primary" :disabled="!attachment.accessible" @click="handlePreviewAttachment(attachment)">查看</el-button>
                  <el-button link type="primary" :disabled="!attachment.accessible" @click="handleDownloadAttachment(attachment)">下载</el-button>
                  <el-button link type="danger" @click="handleDeleteAttachment(attachment)">删除</el-button>
                  <span v-if="!attachment.accessible" class="saved-attachment-hint">历史附件需重新上传后才能操作</span>
                </div>
              </div>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="请输入备注信息（可选）" maxlength="500" />
          </el-form-item>
        </el-col>
      </el-row>

      <div class="items-header">
        <span class="items-title">入库明细</span>
        <div class="items-header-actions">
          <el-button v-if="isPurchaseMode" type="success" size="small" @click="openSelectMaterialDialog">从来源订单选择物料</el-button>
          <el-button v-if="!isPurchaseMode" type="primary" size="small" @click="addItem">+ 添加行</el-button>
        </div>
      </div>

      <el-table :data="form.items" border size="small" class="items-table">

        <el-table-column type="index" label="#" width="45" align="center" />

        <el-table-column label="入库仓库 *" min-width="120">
          <template #default="{ row }">
            <el-select v-model="row.warehouseId" placeholder="选择仓库" size="small" style="width:100%" @change="onItemWarehouseChange(row)">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
            </el-select>
            <div v-if="row._areaValidation?.field === 'warehouseId'" class="cell-error">{{ row._areaValidation.message }}</div>
          </template>
        </el-table-column>

        <el-table-column label="仓位 *" min-width="120">
          <template #default="{ row }">
            <el-select v-model="row.locationId" placeholder="选择仓位" size="small" style="width:100%" :disabled="!row.warehouseId">
              <el-option v-for="loc in row._locations" :key="loc.id" :label="loc.locationName" :value="loc.id" />
            </el-select>
            <div v-if="row._areaValidation?.field === 'locationId'" class="cell-error">{{ row._areaValidation.message }}</div>
          </template>
        </el-table-column>

        <el-table-column label="物料名称 *" min-width="150">
          <template #default="{ row }">
            <template v-if="row.orderQty != null">
              <span>{{ row.materialName }}</span>
            </template>
            <el-autocomplete v-else
              v-model="row._keyword"
              :fetch-suggestions="searchMaterials"
              value-key="materialName"
              placeholder="输入名称搜索"
              size="small"
              style="width:100%"
              clearable
              @select="(m: any) => onMaterialNameSelect(row, m)"
              @clear="row.materialId = null; row.materialName = ''; row.spec = ''; row.unit = ''; row._specs = []"
            />
            <div v-if="row._areaValidation?.field === 'materialId'" class="cell-error">{{ row._areaValidation.message }}</div>
          </template>
        </el-table-column>

        <el-table-column label="规格 *" width="130">
          <template #default="{ row }">
            <template v-if="row.orderQty != null">
              <span>{{ row.spec }}</span>
            </template>
            <el-select v-else
              v-model="row.spec"
              placeholder="选择规格"
              size="small"
              style="width:100%"
              :disabled="!row._specs.length"
              @change="(spec: string) => { const s = row._specs.find(x => x.spec === spec); if(s) onSpecSelect(row, s) }"
            >
              <el-option v-for="s in row._specs" :key="s.materialId" :label="s.spec" :value="s.spec" />
            </el-select>
            <div v-if="row._areaValidation?.field === 'spec'" class="cell-error">{{ row._areaValidation.message }}</div>
          </template>
        </el-table-column>

        <el-table-column label="单位 *" width="80">
          <template #default="{ row }">
            <span>{{ row.unit }}</span>
            <div v-if="row._areaValidation?.field === 'unit'" class="cell-error">{{ row._areaValidation.message }}</div>
          </template>
        </el-table-column>

        <el-table-column label="数量 *" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="3"
              :max="row.remainingInboundQty ?? undefined"
              size="small" style="width:100%" controls-position="right" />
            <div v-if="getPurchaseQuantityExceededMessage(row)" class="cell-error">{{ getPurchaseQuantityExceededMessage(row) }}</div>
            <div v-else-if="row._areaValidation?.field === 'quantity'" class="cell-error">{{ row._areaValidation.message }}</div>
            <div v-if="row.remainingInboundQty != null && row.orderQty != null" class="qty-tip" :class="{ 'qty-tip-error': !!getPurchaseQuantityExceededMessage(row) }">
              可入库 {{ row.remainingInboundQty }} / 订单 {{ row.orderQty }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="单价(元)" width="110">
          <template #default="{ row }">
            <template v-if="row.orderQty != null">
              <span>{{ row.unitCost }}</span>
            </template>
            <el-input-number v-else v-model="row.unitCost" :min="0" :precision="2"
              size="small" style="width:100%" controls-position="right" />
          </template>
        </el-table-column>

        <el-table-column label="批次号" width="110">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" size="small" maxlength="50" />
          </template>
        </el-table-column>

        <el-table-column label="生产日期" width="150">
          <template #default="{ row }">
            <el-date-picker v-model="row.productionDate" type="date" value-format="YYYY-MM-DD"
              size="small" style="width:100%" :disabled-date="disableFutureProductionDate" @change="onProductionDateChange(row)" />
            <div v-if="row._areaValidation?.field === 'productionDate'" class="cell-error">{{ row._areaValidation.message }}</div>
          </template>
        </el-table-column>

        <el-table-column label="统一面积系数（㎡/单件）" width="150">
          <template #default="{ row }">
            <span>{{ row._areaValidation?.areaCoefficient ?? '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="本次入库预计占用面积（㎡）" width="170">
          <template #default="{ row }">
            <span>{{ row._areaValidation?.expectedOccupiedArea ?? '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="面积校验结果" width="140">
          <template #default="{ row }">
            <span>{{ row._areaValidation?.validationResult ?? '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="" width="50" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeItem($index)">删</el-button>
          </template>
        </el-table-column>

      </el-table>

      <InboundAreaValidationSummary
        :summaries="areaValidationSummaries"
        :global-message="areaValidationGlobalMessage"
      />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ handleSubmitLabel }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 从来源订单选择物料弹窗 -->
  <el-dialog v-model="selectDialogVisible" title="从来源订单选择物料" width="700px" :close-on-click-modal="false" append-to-body>
    <div class="select-dialog-body">
      <el-input v-model="selectDialogKeyword" placeholder="搜索物料名称或规格" clearable style="margin-bottom: 12px" />
      <el-table v-loading="selectDialogLoading" :data="filteredSourceItems" border size="small" max-height="400"
        @selection-change="(rows: any[]) => { selectedMaterialIds = rows.map((r: any) => r.materialId) }">
        <el-table-column type="selection" width="45" :selectable="(row: any) => true" />
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
    </div>
    <template #footer>
      <el-button @click="selectDialogVisible = false">取消</el-button>
      <el-button type="primary" :disabled="selectedMaterialIds.length === 0" @click="confirmSelectMaterials">
        确认追加（已选 {{ selectedMaterialIds.length }} 条）
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0 8px;
}
.items-title { font-size: 14px; font-weight: 600; color: $text-primary; }
.items-table { width: 100%; }
.cell-error {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #f56c6c;
}
.attachment-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.saved-attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.saved-attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.saved-attachment-name {
  color: $text-primary;
}
.saved-attachment-hint {
  font-size: 12px;
  color: $text-secondary;
}
.disabled-input :deep(.el-input__wrapper) {
  background-color: #f5f7fa !important;
}
.qty-tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.2;
  margin-top: 2px;
  white-space: nowrap;
}
.qty-tip-error {
  color: #f56c6c;
}
.items-header-actions {
  display: flex;
  gap: 8px;
}
.select-dialog-body {
  padding: 0 4px;
}
</style>
