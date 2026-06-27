<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { outboundApi } from '@/api/modules/outbound'
import { materialApi } from '@/api/modules/material'
import { warehouseApi } from '@/api/modules/warehouse'
import { inventoryApi } from '@/api/modules/inventory'
import { getPlanList, getPlanMaterialSummary } from '@/api/modules/plan'
import { useOrgStore } from '@/stores/modules/org'
import { useMaterialStore } from '@/stores/modules/material'
import { useOutboundStore } from '@/stores/modules/outbound'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import { loadOutboundFormOptions } from './outboundFormOptions'
import { applySourceOrderSelection } from './outboundSourceSelection'
import { findAggregateBatchStockViolation } from './outboundAggregateBatchValidation'
import type { OrgTreeNode } from '@/types/org'
import type {
  OutboundOrderAllocation,
  OutboundSourceOrderOption,
  OutboundSuggestionPreviewDetail,
  OutboundSuggestionPreviewRequest,
  OutboundSuggestionPreviewResult,
  OutboundSuggestionRevalidateRequest,
} from '@/types/outbound'

interface SpecOption {
  materialId: number
  spec: string
  unit: string
  materialName: string
}

interface BatchNoOption {
  batch_no: string
  quantity: number
  unit_cost: number
  expiry_date: string | null
}

interface AllocationRow {
  sourceStockDetailId: number
  warehouseId: number
  warehouseName: string
  locationId: number | null
  locationName: string
  batchNo: string
  productionDate?: string
  expiryDate?: string
  quantity: number | null
  sourceType: string
}

interface ItemRow {
  id: number | null
  materialId: number | null
  materialName: string
  spec: string
  unit: string
  warehouseId: number | null
  locationId: number | null
  batchNo: string
  quantity: number | null
  unitCost: number | null
  purpose: string
  remark: string
  allocations: AllocationRow[]
  suggestStatus: string
  suggestMessage: string
  suggestWarnings: string[]
  unmatchedQty: number
  suggestions: OutboundSuggestionPreviewDetail['suggestions']
  _keyword: string
  _specs: SpecOption[]
  _locations: Array<{ id: number; locationName: string }>
  _batchNos: BatchNoOption[]
}

interface Props {
  modelValue: boolean
  orderId?: number | null
}

const props = withDefaults(defineProps<Props>(), { modelValue: false, orderId: null })
const emit = defineEmits<{ 'update:modelValue': [val: boolean]; success: [] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const loading = ref(false)
const submitLoad = ref(false)
const suggestionLoading = ref(false)
const warehouses = ref<any[]>([])
const approvedPlans = ref<any[]>([])
const planLoading = ref(false)
const sourceOrders = ref<OutboundSourceOrderOption[]>([])
const hydratingSourceOrder = ref(false)
const suggestionPreviewVisible = ref(false)
const suggestionPreviewResult = ref<OutboundSuggestionPreviewResult | null>(null)
const sourceSelectionSummary = ref({
  orderNo: '',
  orgName: '',
  planCode: '',
  planDate: '',
  mealType: '',
  planSummary: '',
})
const isRequisitionSourceFlow = computed(() => form.value.outboundType === 'requisition')
const orgStore = useOrgStore()
const materialStore = useMaterialStore()
const outboundStore = useOutboundStore()

const defaultItem = (): ItemRow => ({
  id: null,
  materialId: null,
  materialName: '',
  spec: '',
  unit: '',
  warehouseId: null,
  locationId: null,
  batchNo: '',
  quantity: null,
  unitCost: null,
  purpose: '',
  remark: '',
  allocations: [],
  suggestStatus: '',
  suggestMessage: '',
  suggestWarnings: [],
  unmatchedQty: 0,
  suggestions: [],
  _keyword: '',
  _specs: [],
  _locations: [],
  _batchNos: [],
})

const form = ref({
  outboundType: 'requisition',
  targetOrgId: null as number | null,
  sourceOrderId: null as number | null,
  sourceOrderNo: '',
  remark: '',
  attachments: [] as string[],
  items: [defaultItem()],
})

watch(() => props.modelValue, async (val) => {
  if (!val) return
  await loadOptions()
  if (props.orderId) {
    await loadOrder(props.orderId)
  } else {
    resetForm()
    await loadSourceOrders(form.value.outboundType)
  }
})

watch(
  () => form.value.outboundType,
  async (type) => {
    if (hydratingSourceOrder.value) return
    form.value.sourceOrderId = null
    form.value.sourceOrderNo = ''
    sourceSelectionSummary.value = {
      orderNo: '',
      orgName: '',
      planCode: '',
      planDate: '',
      mealType: '',
      planSummary: '',
    }
    clearAllSuggestions(false)
    await loadSourceOrders(type)
  }
)

const loadOptions = async () => {
  try {
    const result = await loadOutboundFormOptions({
      loadOutboundTypeOptions: () => outboundStore.loadOutboundTypeOptions(),
      getWarehouses: () => warehouseApi.getList({ pageNum: 1, pageSize: 1000 }),
      getMaterials: async () => {
        await materialStore.fetchActiveList()
        return {
          code: 'SUCCESS',
          data: { list: materialStore.activeList },
        }
      },
      ensureOrgTreeLoaded,
      getPlanList: () => getPlanList({ status: 'approved', pageNum: 1, pageSize: 200 }),
      onOutboundTypeError: (message, error) => {
        console.warn(message, error)
      },
      onOrgTreeError: (message, error) => {
        console.warn(message, error)
      },
      onPlanListError: (message, error) => {
        console.warn(message, error)
      },
    })
    warehouses.value = result.warehouses
    approvedPlans.value = result.approvedPlans
  } catch (e) {
    console.error('加载选项失败', e)
  }
}

const ensureOrgTreeLoaded = async () => {
  if (orgStore.treeData.length === 0) {
    await orgStore.fetchTree()
  }
}

const findOrgIdByName = (nodes: OrgTreeNode[], orgName?: string | null): number | null => {
  if (!orgName) return null
  for (const node of nodes) {
    if (node.orgName === orgName) return node.id
    if (node.children?.length) {
      const orgId = findOrgIdByName(node.children, orgName)
      if (orgId != null) return orgId
    }
  }
  return null
}

const loadSourceOrders = async (outboundType: string) => {
  sourceOrders.value = []
  try {
    const res = await outboundApi.getSourceOrderOptions(outboundType)
    if (res.code === 'SUCCESS' && res.data) {
      sourceOrders.value = res.data
    }
  } catch (e) {
    console.error('加载来源单号失败', e)
  }
}

const applySourceSummary = (sourceOrderId: number | null, options?: { preserveSourceOrderNo?: boolean; existingItems?: ItemRow[]; mode?: 'hydrate' | 'clear' | 'skip' | 'fallback' }) => {
  const result = applySourceOrderSelection({
    mode: options?.mode ?? (options?.existingItems ? 'hydrate' : 'select'),
    sourceOrders: sourceOrders.value,
    selectedSourceOrderId: sourceOrderId,
    approvedPlans: approvedPlans.value,
    existingItems: options?.existingItems,
    warehouses: warehouses.value,
  })
  sourceSelectionSummary.value = result.summary
  form.value.sourceOrderId = result.formPatch.sourceOrderId
  if (!options?.preserveSourceOrderNo || result.formPatch.sourceOrderNo || result.formPatch.sourceOrderId == null) {
    form.value.sourceOrderNo = result.formPatch.sourceOrderNo
  }
  return result
}

const onSourceOrderChange = async (id: number | null) => {
  const previousItems = form.value.items
  if (!isRequisitionSourceFlow.value) {
    applySourceSummary(id, { existingItems: previousItems, mode: 'skip' })
    return
  }
  if (!id) {
    applySourceSummary(null, { existingItems: previousItems, mode: 'clear' })
    return
  }

  const result = applySourceSummary(id)
  const matchedPlan = approvedPlans.value.find(item => item.id === id)
  if (!matchedPlan) return

  planLoading.value = true
  try {
    const res = await getPlanMaterialSummary(id)
    if (res.code === 'SUCCESS') {
      const selection = applySourceOrderSelection({
        sourceOrders: sourceOrders.value,
        selectedSourceOrderId: id,
        approvedPlans: approvedPlans.value,
        planMaterials: res.data || [],
        warehouses: warehouses.value,
      })
      sourceSelectionSummary.value = selection.summary
      form.value.sourceOrderId = selection.formPatch.sourceOrderId
      form.value.sourceOrderNo = selection.formPatch.sourceOrderNo
      form.value.items = selection.items.length > 0 ? (selection.items as ItemRow[]).map(item => ({
        ...defaultItem(),
        ...item,
        id: null,
        allocations: [],
        suggestStatus: '',
        suggestMessage: '',
        suggestWarnings: [],
        unmatchedQty: 0,
        suggestions: [],
      })) : previousItems
      if (selection.items.length > 0 && warehouses.value.length === 1 && warehouses.value[0]?.id) {
        for (const item of form.value.items) {
          await handleWarehouseChange(item, warehouses.value[0].id)
        }
      }
      clearAllSuggestions(false)
    }
  } catch (e: any) {
    form.value.items = applySourceOrderSelection({
      mode: 'fallback',
      sourceOrders: sourceOrders.value,
      selectedSourceOrderId: id,
      approvedPlans: approvedPlans.value,
      existingItems: previousItems,
      warehouses: warehouses.value,
    }).items as ItemRow[]
    sourceSelectionSummary.value = result.summary
    ElMessage.error(e.message || '加载食材汇总失败')
  } finally {
    planLoading.value = false
  }
}

const markRowChanged = (row: ItemRow, message = '明细已变更，原建议与分配已清空，请重新生成') => {
  const hadSuggestion = row.suggestions.length > 0 || row.allocations.length > 0 || !!row.suggestStatus
  row.allocations = []
  row.suggestions = []
  row.suggestStatus = ''
  row.suggestMessage = ''
  row.suggestWarnings = []
  row.unmatchedQty = 0
  if (hadSuggestion) {
    ElMessage.warning(message)
  }
}

const handleWarehouseChange = async (row: ItemRow, warehouseId: number) => {
  markRowChanged(row)
  row.warehouseId = warehouseId
  row.locationId = null
  row._locations = []
  row.batchNo = ''
  row._batchNos = []
  if (!warehouseId) return
  try {
    const res = await warehouseApi.getLocations({ warehouseId, pageNum: 1, pageSize: 100 })
    row._locations = res.data?.list || []
  } catch (e) {
    console.error('加载仓位失败', e)
    row._locations = []
  }
  if (row.materialId) {
    await loadBatchNos(row)
    if (row._batchNos.length === 0 && row.materialName) {
      ElMessage.warning(`${row.materialName}在该仓库无库存，请选择其他仓库`)
    }
  }
}

const handleLocationChange = async (row: ItemRow) => {
  markRowChanged(row)
  row.batchNo = ''
  row._batchNos = []
  row.unitCost = null
  if (row.materialId && row.warehouseId) {
    await loadBatchNos(row)
    if (row._batchNos.length === 0) {
      ElMessage.warning('该仓位无此物料的库存，请选择其他仓位或仓库')
    }
  }
}

const handleBatchNoChange = (row: ItemRow, batchNo: string) => {
  row.batchNo = batchNo
  if (!batchNo) {
    row.unitCost = null
    return
  }
  const batch = row._batchNos.find(b => b.batch_no === batchNo)
  if (batch && batch.unit_cost != null) {
    row.unitCost = batch.unit_cost
  }
}

const handleQuantityChange = (row: ItemRow) => {
  if (row.suggestions.length || row.allocations.length || row.suggestStatus) {
    markRowChanged(row)
  }
}

const loadBatchNos = async (row: ItemRow) => {
  row._batchNos = []
  row.batchNo = ''
  if (!row.materialId || !row.warehouseId) return
  try {
    let res = await inventoryApi.getAvailableBatchNos({
      materialId: row.materialId,
      spec: row.spec || undefined,
      warehouseId: row.warehouseId,
      locationId: row.locationId || undefined,
    })
    if (res.code === 'SUCCESS' && (!res.data || res.data.length === 0)) {
      res = await inventoryApi.getAvailableBatchNos({
        materialId: row.materialId,
        warehouseId: row.warehouseId,
        locationId: row.locationId || undefined,
      })
    }
    if (res.code === 'SUCCESS') {
      row._batchNos = res.data || []
    }
  } catch (e) {
    console.error('加载批次号失败', e)
    row._batchNos = []
  }
}

const mapAllocations = (allocations?: OutboundOrderAllocation[]): AllocationRow[] => {
  return (allocations || []).map(allocation => ({
    sourceStockDetailId: allocation.sourceStockDetailId,
    warehouseId: allocation.warehouseId,
    warehouseName: allocation.warehouseName || '',
    locationId: allocation.locationId ?? null,
    locationName: allocation.locationName || '',
    batchNo: allocation.batchNo || '',
    productionDate: allocation.productionDate,
    expiryDate: allocation.expiryDate,
    quantity: allocation.quantity,
    sourceType: allocation.sourceType || 'manual',
  }))
}

const loadOrder = async (id: number) => {
  loading.value = true
  try {
    const res = await outboundApi.getDetail(id)
    if (res.code !== 'SUCCESS' || !res.data) return
    const items = res.data.items || []
    hydratingSourceOrder.value = true
    await loadSourceOrders(res.data.outboundType)
    form.value = {
      outboundType: res.data.outboundType,
      targetOrgId: (res.data as any).targetOrgId ?? null,
      sourceOrderId: res.data.sourceOrderId ?? null,
      sourceOrderNo: res.data.sourceOrderNo ?? '',
      remark: res.data.remark || '',
      attachments: res.data.attachments || [],
      items: items.length > 0 ? await Promise.all(items.map(async (it) => {
        const row = defaultItem()
        row.id = it.id
        row.materialId = it.materialId
        row.materialName = it.materialName
        row.spec = it.spec || ''
        row.unit = it.unit
        row.warehouseId = it.warehouseId ?? null
        row.locationId = it.locationId ?? null
        row.batchNo = it.batchNo || ''
        row.quantity = Number(it.quantity)
        row.unitCost = it.unitCost != null ? Number(it.unitCost) : null
        row.purpose = it.purpose || ''
        row.remark = it.remark || ''
        row._keyword = it.materialName
        row._specs = [{ materialId: it.materialId, spec: it.spec || '', unit: it.unit, materialName: it.materialName }]
        row.allocations = mapAllocations(it.allocations)
        if (it.warehouseId) {
          try {
            const locRes = await warehouseApi.getLocations({ warehouseId: it.warehouseId, pageNum: 1, pageSize: 100 })
            row._locations = locRes.data?.list || []
          } catch {
            row._locations = []
          }
          if (it.materialId) {
            try {
              let batchRes = await inventoryApi.getAvailableBatchNos({
                materialId: it.materialId,
                spec: it.spec || undefined,
                warehouseId: it.warehouseId,
                locationId: it.locationId || undefined,
              })
              if (batchRes.code === 'SUCCESS' && (!batchRes.data || batchRes.data.length === 0)) {
                batchRes = await inventoryApi.getAvailableBatchNos({
                  materialId: it.materialId,
                  warehouseId: it.warehouseId,
                  locationId: it.locationId || undefined,
                })
              }
              if (batchRes.code === 'SUCCESS') {
                row._batchNos = batchRes.data || []
              }
            } catch {
              row._batchNos = []
            }
          }
        }
        return row
      })) : [defaultItem()],
    }
    fileList.value = (res.data.attachments || []).map((url: string) => ({ name: url.split('/').pop() || '附件', url, status: 'success' }))
    applySourceSummary(form.value.sourceOrderId, {
      preserveSourceOrderNo: true,
      existingItems: form.value.items,
    })
  } finally {
    hydratingSourceOrder.value = false
    loading.value = false
  }
}

const resetForm = () => {
  form.value = {
    outboundType: 'requisition',
    targetOrgId: null,
    sourceOrderId: null,
    sourceOrderNo: '',
    remark: '',
    attachments: [],
    items: [defaultItem()],
  }
  suggestionPreviewResult.value = null
  suggestionPreviewVisible.value = false
  sourceSelectionSummary.value = {
    orderNo: '',
    orgName: '',
    planCode: '',
    planDate: '',
    mealType: '',
    planSummary: '',
  }
}

const addItem = () => form.value.items.push(defaultItem())
const removeItem = (idx: number) => {
  if (form.value.items.length <= 1) return
  form.value.items.splice(idx, 1)
  clearAllSuggestions(false)
}

const searchMaterial = async (row: ItemRow, keyword: string, cb: (results: { value: string }[]) => void) => {
  row._keyword = keyword
  if (!keyword) {
    row._specs = []
    row.materialId = null
    row.materialName = ''
    row.spec = ''
    row.unit = ''
    cb([])
    return
  }
  try {
    const res = await materialApi.getList({ pageNum: 1, pageSize: 100, materialName: keyword, status: 'active' })
    if (res.code === 'SUCCESS' && res.data) {
      row._specs = res.data.list.map((m: any) => ({
        materialId: m.id,
        spec: m.materialSpec || m.spec || '',
        unit: m.unit || '',
        materialName: m.materialName,
      }))
      const uniqueNames = [...new Set(res.data.list.map((m: any) => m.materialName))]
      cb(uniqueNames.map((name: string) => ({ value: name })))
      return
    }
  } catch (e) {
    console.error('搜索物料失败', e)
  }
  cb([])
}

const handleMaterialSelect = (row: ItemRow, materialName: string) => {
  markRowChanged(row)
  if (!materialName) {
    row.materialId = null
    row.materialName = ''
    row.spec = ''
    row.unit = ''
    row._specs = []
    return
  }
  row.materialName = materialName
  row.materialId = null
  row.spec = ''
  row.unit = ''
}

const onSpecSelect = async (row: ItemRow, specOption: SpecOption) => {
  markRowChanged(row)
  row.materialId = specOption.materialId
  row.materialName = specOption.materialName
  row.unit = specOption.unit
  row.spec = specOption.spec
  const mat = materialStore.activeList.find(m => m.id === specOption.materialId)
  row.unitCost = mat?.costPrice || mat?.unitCost || null
  if (row.warehouseId) {
    await loadBatchNos(row)
  }
}

const getLineNo = (row: ItemRow) => form.value.items.findIndex(item => item === row) + 1

const buildSuggestionRequest = (rows: ItemRow[]): OutboundSuggestionPreviewRequest | null => {
  if (rows.length === 0) {
    ElMessage.warning('请先录入出库明细')
    return null
  }
  for (const row of rows) {
    if (!row.materialId) {
      ElMessage.warning('请先选择物料后再生成建议')
      return null
    }
    if (!row.spec) {
      ElMessage.warning(`${row.materialName || '当前明细'}请先选择规格后再生成建议`)
      return null
    }
    if (!row.quantity || row.quantity <= 0) {
      ElMessage.warning(`${row.materialName || '当前明细'}的出库数量必须大于0`)
      return null
    }
  }

  return {
    orderId: props.orderId ?? null,
    warehouseScopeType: 'all',
    details: rows.map((row) => ({
      detailId: row.id,
      lineNo: getLineNo(row),
      materialId: row.materialId!,
      materialName: row.materialName,
      specName: row.spec,
      requestQty: Number(row.quantity),
      fixedWarehouseId: row.warehouseId,
      fixedLocationId: row.locationId,
    })),
  }
}

const applySuggestionMetaToRows = (details: OutboundSuggestionPreviewDetail[]) => {
  for (const detail of details) {
    const row = form.value.items[detail.lineNo - 1]
    if (!row) continue
    row.suggestStatus = detail.suggestStatus || ''
    row.suggestMessage = detail.message || ''
    row.suggestWarnings = detail.warnings || []
    row.unmatchedQty = Number(detail.unmatchedQty || 0)
    row.suggestions = detail.suggestions || []
  }
}

const generateSuggestions = async (rows: ItemRow[]) => {
  const request = buildSuggestionRequest(rows)
  if (!request) return
  suggestionLoading.value = true
  try {
    const res = await outboundApi.previewSuggestions(request)
    if (res.code === 'SUCCESS' && res.data) {
      applySuggestionMetaToRows(res.data.details || [])
      suggestionPreviewResult.value = res.data
      suggestionPreviewVisible.value = true
      ElMessage.success('已生成出库建议，请确认后应用')
    }
  } finally {
    suggestionLoading.value = false
  }
}

const generateAllSuggestions = async () => {
  await generateSuggestions(form.value.items)
}

const generateLineSuggestion = async (row: ItemRow) => {
  await generateSuggestions([row])
}

const getSuggestionStatusLabel = (status: string) => {
  switch (status) {
    case 'full_matched':
      return '已完全匹配'
    case 'partial_matched':
      return '部分匹配'
    case 'no_stock':
      return '无库存'
    case 'invalid':
      return '存在异常'
    default:
      return '未生成'
  }
}

const getSuggestionStatusType = (status: string) => {
  switch (status) {
    case 'full_matched':
      return 'success'
    case 'partial_matched':
      return 'warning'
    case 'no_stock':
    case 'invalid':
      return 'danger'
    default:
      return 'info'
  }
}

const getSuggestionSummary = (row: ItemRow) => {
  const source = row.allocations.length > 0 ? row.allocations : row.suggestions
  if (!source.length) return '—'
  const warehouses = [...new Set(source.map(item => item.warehouseName).filter(Boolean))]
  return `${source.length}个批次${warehouses.length ? ` / ${warehouses.join('、')}` : ''}`
}

const getSuggestionBasis = (row: ItemRow) => {
  if (row.suggestions.length > 0) {
    return row.suggestions[0].reason
  }
  if (row.suggestWarnings.length > 0) {
    return row.suggestWarnings[0]
  }
  return row.suggestMessage || '—'
}

const toAllocationRows = (detail: OutboundSuggestionPreviewDetail): AllocationRow[] => {
  return detail.suggestions.map(item => ({
    sourceStockDetailId: item.sourceStockDetailId,
    warehouseId: item.warehouseId,
    warehouseName: item.warehouseName || '',
    locationId: item.locationId ?? null,
    locationName: item.locationName || '',
    batchNo: item.batchNo || '',
    productionDate: item.productionDate,
    expiryDate: item.expiryDate,
    quantity: item.suggestQty,
    sourceType: 'suggestion_apply',
  }))
}

const findSuggestionDetailByLineNo = (lineNo: number) => suggestionPreviewResult.value?.details?.find(item => item.lineNo === lineNo)

const applyRowSuggestion = async (row: ItemRow) => {
  const detail = findSuggestionDetailByLineNo(getLineNo(row))
  if (!detail || !detail.suggestions.length) {
    ElMessage.warning('当前明细暂无可应用的建议')
    return
  }
  if (row.allocations.length > 0) {
    try {
      await ElMessageBox.confirm('应用建议将覆盖当前行已有分配明细，是否继续？', '覆盖现有分配', {
        confirmButtonText: '覆盖',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  row.allocations = toAllocationRows(detail)
  row.suggestStatus = detail.suggestStatus
  row.suggestMessage = detail.message
  row.suggestWarnings = detail.warnings || []
  row.unmatchedQty = Number(detail.unmatchedQty || 0)
  ElMessage.success('建议应用成功，请检查分配明细后保存')
}

const applySuggestedRows = async (rows: ItemRow[]) => {
  const applicable = rows.filter(row => row.suggestions.length > 0)
  if (!applicable.length) {
    ElMessage.warning('暂无可应用的建议结果')
    return
  }
  const rowsWithAllocations = applicable.filter(row => row.allocations.length > 0)
  if (rowsWithAllocations.length > 0) {
    try {
      await ElMessageBox.confirm('应用建议将覆盖已有分配明细，是否继续？', '覆盖现有分配', {
        confirmButtonText: '覆盖',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  for (const row of applicable) {
    const detail = findSuggestionDetailByLineNo(getLineNo(row))
    if (!detail) continue
    row.allocations = toAllocationRows(detail)
    row.suggestStatus = detail.suggestStatus
    row.suggestMessage = detail.message
    row.suggestWarnings = detail.warnings || []
    row.unmatchedQty = Number(detail.unmatchedQty || 0)
  }
  ElMessage.success('建议应用成功，请检查分配明细后保存')
}

const applyAllSuggestions = async () => {
  await applySuggestedRows(form.value.items)
}

const applyFullMatchedSuggestions = async () => {
  await applySuggestedRows(form.value.items.filter(row => row.suggestStatus === 'full_matched'))
}

const clearRowSuggestion = (row: ItemRow) => {
  row.suggestions = []
  row.suggestStatus = ''
  row.suggestMessage = ''
  row.suggestWarnings = []
  row.unmatchedQty = 0
}

const clearAllSuggestions = (showMessage = true) => {
  form.value.items.forEach((row) => clearRowSuggestion(row))
  suggestionPreviewResult.value = null
  suggestionPreviewVisible.value = false
  if (showMessage) {
    ElMessage.success('已清空建议结果')
  }
}

const clearRowAllocations = async (row: ItemRow) => {
  if (!row.allocations.length) return
  try {
    await ElMessageBox.confirm('确认清空当前行分配明细？', '清空分配', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
    row.allocations = []
  } catch {
  }
}

const removeAllocation = (row: ItemRow, index: number) => {
  row.allocations.splice(index, 1)
}

const allocationTotal = (row: ItemRow) => row.allocations.reduce((sum, item) => sum + Number(item.quantity || 0), 0)

const buildRevalidateRequest = (): OutboundSuggestionRevalidateRequest | null => {
  const rows = form.value.items.filter(row => row.allocations.length > 0)
  if (!rows.length) return null
  return {
    orderId: props.orderId ?? null,
    details: rows.map(row => ({
      detailId: row.id,
      lineNo: getLineNo(row),
      materialId: row.materialId,
      specName: row.spec,
      requestQty: Number(row.quantity || 0),
      allocations: row.allocations.map(item => ({
        sourceStockDetailId: item.sourceStockDetailId,
        warehouseId: item.warehouseId,
        locationId: item.locationId,
        batchNo: item.batchNo,
        suggestQty: Number(item.quantity || 0),
      })),
    })),
  }
}

const validateBeforeSubmit = async () => {
  if (!form.value.targetOrgId) {
    ElMessage.warning('请选择领用组织')
    return false
  }
  for (const row of form.value.items) {
    if (!row.materialId || !row.quantity || !row.purpose) {
      ElMessage.warning('请完善出库明细（物料、数量、用途为必填）')
      return false
    }
    if (row.allocations.length > 0) {
      if (allocationTotal(row) !== Number(row.quantity || 0)) {
        ElMessage.warning(`${row.materialName} 的分配总量与出库数量不一致，请重新生成建议或调整分配`)
        return false
      }
    } else if (!row.warehouseId || !row.batchNo) {
      ElMessage.warning('请完善出库明细（手工出库时仓库、批次号为必填）')
      return false
    }
  }

  const manualItems = form.value.items.filter(row => row.allocations.length === 0)
  const overStock = findAggregateBatchStockViolation(manualItems)
  if (overStock) {
    ElMessage.warning(`出库数量超过批次库存：${overStock.materialName}`)
    return false
  }

  const revalidateRequest = buildRevalidateRequest()
  if (revalidateRequest) {
    const res = await outboundApi.revalidateSuggestions(revalidateRequest)
    if (res.code === 'SUCCESS' && res.data && !res.data.valid) {
      ElMessage.warning(res.data.warnings?.[0] || res.data.details?.find(item => !item.valid)?.message || '库存已变化，请重新生成建议')
      return false
    }
  }
  return true
}

const resolveHeaderWarehouseId = () => {
  for (const row of form.value.items) {
    if (row.warehouseId) return row.warehouseId
    if (row.allocations.length > 0) return row.allocations[0].warehouseId
  }
  return null
}

const buildSubmitPayload = () => ({
  outboundType: form.value.outboundType,
  warehouseId: resolveHeaderWarehouseId(),
  targetOrgId: form.value.targetOrgId,
  purpose: form.value.items[0]?.purpose || '',
  sourceOrderId: form.value.sourceOrderId,
  sourceOrderNo: form.value.sourceOrderNo,
  remark: form.value.remark,
  attachments: form.value.attachments,
  items: form.value.items.map(row => ({
    materialId: row.materialId,
    materialName: row.materialName,
    spec: row.spec,
    unit: row.unit,
    warehouseId: row.warehouseId,
    locationId: row.locationId,
    batchNo: row.batchNo,
    quantity: row.quantity,
    unitCost: row.unitCost,
    purpose: row.purpose,
    remark: row.remark,
    allocations: row.allocations.map(item => ({
      sourceStockDetailId: item.sourceStockDetailId,
      warehouseId: item.warehouseId,
      locationId: item.locationId,
      batchNo: item.batchNo,
      productionDate: item.productionDate,
      expiryDate: item.expiryDate,
      quantity: item.quantity,
      sourceType: item.sourceType,
    })),
  })),
})

const handleSubmit = async () => {
  if (!(await validateBeforeSubmit())) {
    return
  }
  submitLoad.value = true
  try {
    const data = buildSubmitPayload()
    if (props.orderId) {
      await outboundApi.update(props.orderId, data)
      ElMessage.success('更新成功')
    } else {
      await outboundApi.create(data)
      ElMessage.success('创建成功')
    }
    emit('success')
    emit('update:modelValue', false)
  } finally {
    submitLoad.value = false
  }
}

const fileList = ref<any[]>([])
const uploadLoading = ref(false)

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

const isAttachmentUrl = (val: string) => val.startsWith('/upload/') || val.startsWith('http://') || val.startsWith('https://')

const handleUploadRequest = async (options: any) => {
  const category = resolveAttachmentCategory(options.file)
  const maxSize = ATTACHMENT_LIMITS[category]
  if (options.file.size > maxSize) {
    const message = attachmentLimitMessage(category)
    ElMessage.warning(message)
    options.onError(new Error(message))
    return
  }
  if (!props.orderId) {
    ElMessage.warning('请先保存出库单后再上传附件')
    options.onError(new Error('请先保存出库单'))
    return
  }
  uploadLoading.value = true
  try {
    await outboundApi.uploadAttachments(props.orderId, [options.file])
    const res = await outboundApi.getDetail(props.orderId)
    if (res.code === 'SUCCESS' && res.data?.attachments) {
      form.value.attachments = res.data.attachments
      fileList.value = res.data.attachments.map((url: string) => ({ name: decodeURIComponent(url.split('/').pop() || '附件'), url, status: 'success' }))
    }
    ElMessage.success('附件上传成功')
    options.onSuccess({})
  } catch (e) {
    ElMessage.error('附件上传失败')
    options.onError(e)
  } finally {
    uploadLoading.value = false
  }
}

const handleRemoveFile = (file: any) => {
  const url = file.url
  form.value.attachments = form.value.attachments?.filter(u => u !== url) || []
}

const previewAttachment = (url: string) => {
  window.open(url, '_blank', 'noopener,noreferrer')
}

const downloadAttachment = async (url: string) => {
  if (props.orderId) {
    await outboundApi.downloadAttachment(props.orderId, url)
  }
}

const getFileName = (url: string) => {
  try {
    return decodeURIComponent(url.split('/').pop() || '附件')
  } catch {
    return '附件'
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="orderId ? '编辑出库单' : '新增出库单'" width="1280px" :close-on-click-modal="false">
    <div v-loading="loading">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="出库类型" required>
              <el-select v-model="form.outboundType" placeholder="请选择" style="width:100%">
                <el-option v-for="t in outboundStore.outboundTypeSelectOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="领用组织" required>
              <OrgTreeSelect v-model="form.targetOrgId" :active-only="true" placeholder="请选择领用组织" />
            </el-form-item>
          </el-col>
          <el-col v-if="isRequisitionSourceFlow" :span="8">
            <el-form-item label="来源单号">
              <el-select
                v-model="form.sourceOrderId"
                placeholder="请选择来源单号"
                style="width:100%"
                clearable
                @change="onSourceOrderChange"
              >
                <el-option v-for="order in sourceOrders" :key="order.id" :label="order.orderNo" :value="order.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="请输入备注" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="form.sourceOrderNo" :gutter="16">
          <el-col :span="12">
            <el-form-item label="来源摘要">
              <div class="source-summary">
                <div class="source-summary__line">来源单号：{{ sourceSelectionSummary.orderNo || form.sourceOrderNo }}</div>
                <div v-if="sourceSelectionSummary.orgName" class="source-summary__line">来源组织：{{ sourceSelectionSummary.orgName }}</div>
                <div v-if="sourceSelectionSummary.planSummary" class="source-summary__line">计划信息：{{ sourceSelectionSummary.planSummary }}</div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="items-section">
          <div class="items-header">
            <span class="section-title">出库明细</span>
            <div class="items-header__actions">
              <el-button type="primary" :loading="suggestionLoading" @click="generateAllSuggestions">生成出库建议</el-button>
              <el-button :disabled="!form.items.some(row => row.suggestions.length)" @click="applyAllSuggestions">一键应用全部建议</el-button>
              <el-button :disabled="!form.items.some(row => row.suggestions.length || row.suggestStatus)" @click="clearAllSuggestions()">清空建议</el-button>
              <el-button type="primary" link @click="addItem">+ 添加明细</el-button>
            </div>
          </div>

          <el-table :data="form.items" border size="small" stripe>
            <el-table-column label="物料名称" min-width="140">
              <template #default="{ row }">
                <el-autocomplete
                  v-model="row._keyword"
                  :fetch-suggestions="(query, cb) => searchMaterial(row, query, cb)"
                  placeholder="输入物料名称搜索"
                  style="width:100%"
                  @select="(item) => handleMaterialSelect(row, item.value)"
                  value-key="value"
                />
              </template>
            </el-table-column>
            <el-table-column label="规格" width="120">
              <template #default="{ row }">
                <el-select
                  v-model="row.spec"
                  placeholder="选择规格"
                  style="width:100%"
                  :disabled="!row._specs.length"
                  @change="(spec) => { const s = row._specs.find(x => x.spec === spec); if (s) onSpecSelect(row, s) }"
                >
                  <el-option v-for="s in row._specs" :key="`${s.materialId}-${s.spec}`" :label="s.spec || '默认'" :value="s.spec" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column label="仓库" min-width="120">
              <template #default="{ row }">
                <el-select
                  v-model="row.warehouseId"
                  placeholder="可留空，建议时自动匹配"
                  style="width:100%"
                  filterable
                  clearable
                  @change="(val) => handleWarehouseChange(row, val)"
                >
                  <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="仓位" min-width="110">
              <template #default="{ row }">
                <el-select
                  v-model="row.locationId"
                  placeholder="可留空"
                  style="width:100%"
                  :disabled="!row.warehouseId"
                  clearable
                  @change="() => handleLocationChange(row)"
                >
                  <el-option v-for="l in row._locations" :key="l.id" :label="l.locationName" :value="l.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="批次号" min-width="130">
              <template #default="{ row }">
                <el-select
                  v-model="row.batchNo"
                  :placeholder="row.allocations.length ? '已由建议分配' : '先选仓位'"
                  style="width:100%"
                  :disabled="row.allocations.length > 0 || !row._batchNos.length"
                  filterable
                  clearable
                  @change="(val: string) => handleBatchNoChange(row, val)"
                >
                  <el-option
                    v-for="b in row._batchNos"
                    :key="b.batch_no"
                    :label="b.batch_no"
                    :value="b.batch_no"
                  >
                    <span style="float:left">{{ b.batch_no }}</span>
                    <span style="float:right; color:#999; font-size:12px">库存: {{ b.quantity }}</span>
                  </el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="100">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantity"
                  :min="1"
                  :precision="0"
                  :step="1"
                  :controls="false"
                  style="width:100%"
                  @change="() => handleQuantityChange(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="单价" width="90">
              <template #default="{ row }">
                <el-input-number v-model="row.unitCost" :min="0" :precision="2" :controls="false" style="width:100%" disabled />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="90" align="right">
              <template #default="{ row }">
                {{ row.quantity && row.unitCost ? (row.quantity * row.unitCost).toFixed(2) : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="建议状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getSuggestionStatusType(row.suggestStatus)" size="small">{{ getSuggestionStatusLabel(row.suggestStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="建议分配概览" min-width="180">
              <template #default="{ row }">{{ getSuggestionSummary(row) }}</template>
            </el-table-column>
            <el-table-column label="未匹配数量" width="110" align="right">
              <template #default="{ row }">{{ row.suggestStatus ? row.unmatchedQty || 0 : '—' }}</template>
            </el-table-column>
            <el-table-column label="建议依据" min-width="160">
              <template #default="{ row }">{{ getSuggestionBasis(row) }}</template>
            </el-table-column>
            <el-table-column label="用途" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.purpose" placeholder="用途" maxlength="200" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center" fixed="right">
              <template #default="{ row, $index }">
                <div class="row-actions">
                  <el-button type="primary" link @click="generateLineSuggestion(row)">生成</el-button>
                  <el-button type="primary" link :disabled="!row.suggestions.length" @click="suggestionPreviewVisible = true">查看</el-button>
                  <el-button type="success" link :disabled="!row.suggestions.length" @click="applyRowSuggestion(row)">应用</el-button>
                  <el-button type="info" link :disabled="!row.suggestions.length && !row.suggestStatus" @click="clearRowSuggestion(row)">清空建议</el-button>
                  <el-button v-if="form.items.length > 1" type="danger" link @click="removeItem($index)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="form.items.some(row => row.allocations.length > 0)" class="allocation-section">
          <div class="section-title">出库分配明细</div>
          <div v-for="row in form.items.filter(item => item.allocations.length > 0)" :key="`${row.id || getLineNo(row)}-allocation`" class="allocation-card">
            <div class="allocation-card__header">
              <div>
                第{{ getLineNo(row) }}行：{{ row.materialName }}<span v-if="row.spec"> / {{ row.spec }}</span>
                <span class="allocation-card__meta">需求 {{ row.quantity || 0 }}，已分配 {{ allocationTotal(row) }}</span>
              </div>
              <el-button type="danger" link @click="clearRowAllocations(row)">清空分配</el-button>
            </div>
            <el-alert
              v-if="allocationTotal(row) !== Number(row.quantity || 0)"
              title="当前分配总量与出库数量不一致，保存前请调整或重新生成建议"
              type="warning"
              :closable="false"
              class="allocation-card__alert"
            />
            <el-table :data="row.allocations" size="small" border>
              <el-table-column prop="warehouseName" label="仓库" min-width="120" />
              <el-table-column prop="locationName" label="仓位" min-width="110" />
              <el-table-column prop="batchNo" label="批次号" min-width="120" />
              <el-table-column prop="productionDate" label="生产日期" width="110" />
              <el-table-column prop="expiryDate" label="到期日" width="110" />
              <el-table-column label="分配数量" width="110">
                <template #default="{ row: allocation }">
                  <el-input-number v-model="allocation.quantity" :min="0" :precision="0" :controls="false" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column label="来源" width="100" align="center">
                <template #default="{ row: allocation }">
                  <el-tag size="small" :type="allocation.sourceType === 'suggestion_apply' ? 'success' : 'info'">
                    {{ allocation.sourceType === 'suggestion_apply' ? '建议应用' : '手工维护' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link @click="removeAllocation(row, $index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <el-form-item label="附件" class="mt-16">
          <div v-if="!props.orderId" class="form-tip">请先保存出库单后再上传附件</div>
          <el-upload
            v-else
            :http-request="handleUploadRequest"
            :file-list="fileList"
            :show-file-list="false"
            :on-remove="handleRemoveFile"
            list-type="text"
            accept="image/*,.jpg,.jpeg,.png,.webp,.mp4,.mov,.pdf,.doc,.docx,.xls,.xlsx"
          >
            <el-button type="primary" link :loading="uploadLoading"><el-icon><Plus /></el-icon> 上传附件</el-button>
          </el-upload>
          <div v-if="form.attachments?.length" class="attachment-list">
            <div v-for="(url, idx) in form.attachments" :key="idx" class="attachment-item">
              <span class="attachment-name">{{ getFileName(url) }}</span>
              <template v-if="isAttachmentUrl(url)">
                <el-button link type="primary" size="small" @click="previewAttachment(url)">查看</el-button>
                <el-button link type="primary" size="small" @click="downloadAttachment(url)">下载</el-button>
              </template>
              <span v-else class="form-tip">（历史附件仅保存了文件名）</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitLoad" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="suggestionPreviewVisible" title="出库建议预览" size="900px">
    <div v-if="suggestionPreviewResult" class="suggestion-preview">
      <div class="suggestion-summary">
        <div class="summary-card">
          <div class="summary-card__value">{{ suggestionPreviewResult.summary.detailCount }}</div>
          <div class="summary-card__label">明细行数</div>
        </div>
        <div class="summary-card success">
          <div class="summary-card__value">{{ suggestionPreviewResult.summary.fullMatchedCount }}</div>
          <div class="summary-card__label">完全匹配</div>
        </div>
        <div class="summary-card warning">
          <div class="summary-card__value">{{ suggestionPreviewResult.summary.partialMatchedCount }}</div>
          <div class="summary-card__label">部分匹配</div>
        </div>
        <div class="summary-card danger">
          <div class="summary-card__value">{{ suggestionPreviewResult.summary.failedCount }}</div>
          <div class="summary-card__label">失败/无库存</div>
        </div>
      </div>

      <el-alert
        v-for="warning in suggestionPreviewResult.warnings"
        :key="warning"
        :title="warning"
        type="warning"
        :closable="false"
        class="preview-alert"
      />

      <div v-for="detail in suggestionPreviewResult.details" :key="`${detail.lineNo}-${detail.detailId || detail.materialId}`" class="preview-card">
        <div class="preview-card__header">
          <div class="preview-card__title">
            第{{ detail.lineNo }}行：{{ detail.materialName }}<span v-if="detail.specName"> / {{ detail.specName }}</span>
          </div>
          <el-tag :type="getSuggestionStatusType(detail.suggestStatus)">{{ getSuggestionStatusLabel(detail.suggestStatus) }}</el-tag>
        </div>
        <div class="preview-card__meta">
          需求数量 {{ detail.requestQty }}，已匹配 {{ detail.matchedQty }}，未匹配 {{ detail.unmatchedQty }}
        </div>
        <div class="preview-card__message">{{ detail.message }}</div>
        <el-alert
          v-for="warning in detail.warnings"
          :key="`${detail.lineNo}-${warning}`"
          :title="warning"
          type="warning"
          :closable="false"
          class="preview-alert"
        />
        <el-table :data="detail.suggestions" size="small" border>
          <el-table-column prop="warehouseName" label="仓库" min-width="120" />
          <el-table-column prop="locationName" label="仓位" min-width="110" />
          <el-table-column prop="batchNo" label="批次号" min-width="120" />
          <el-table-column prop="expiryDate" label="到期日" width="110" />
          <el-table-column prop="remainingShelfLifeDays" label="剩余天数" width="90" align="right" />
          <el-table-column prop="availableQty" label="当前可用库存" width="110" align="right" />
          <el-table-column prop="suggestQty" label="建议出库数量" width="110" align="right" />
          <el-table-column prop="reason" label="建议原因" min-width="180" />
        </el-table>
      </div>
    </div>
    <template #footer>
      <div class="preview-footer">
        <el-button @click="applyFullMatchedSuggestions">仅应用已完全匹配项</el-button>
        <el-button type="primary" @click="applyAllSuggestions">应用全部建议</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style lang="scss" scoped>
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.items-section,
.allocation-section {
  margin-top: 16px;
  border-top: 1px solid $border-base;
  padding-top: 16px;
}

.items-header,
.allocation-card__header,
.preview-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.items-header {
  margin-bottom: 12px;
}

.items-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
  justify-content: center;
}

.source-summary {
  width: 100%;
  min-height: 32px;
  padding: 6px 11px;
  border: 1px solid $border-base;
  border-radius: 4px;
  background: $bg-light;
}

.source-summary__line {
  line-height: 1.6;
  color: $text-regular;
}

.allocation-card {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid $border-base;
  border-radius: 8px;
  background: #fff;
}

.allocation-card__meta {
  margin-left: 8px;
  color: $text-secondary;
  font-size: 12px;
}

.allocation-card__alert {
  margin: 10px 0;
}

.attachment-list {
  margin-top: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.attachment-name,
.form-tip {
  font-size: 13px;
  color: $text-secondary;
}

.suggestion-preview {
  padding: 0 8px 16px;
}

.suggestion-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 14px;
  border-radius: 10px;
  background: #f6f8fb;
  border: 1px solid #e5eaf3;
  text-align: center;
}

.summary-card.success {
  background: #f0f9eb;
  border-color: #d8f0c2;
}

.summary-card.warning {
  background: #fdf6ec;
  border-color: #f7d7a8;
}

.summary-card.danger {
  background: #fef0f0;
  border-color: #f6c0c0;
}

.summary-card__value {
  font-size: 24px;
  font-weight: 700;
  color: $text-primary;
}

.summary-card__label {
  margin-top: 4px;
  color: $text-secondary;
}

.preview-alert {
  margin-bottom: 10px;
}

.preview-card {
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid $border-base;
  border-radius: 10px;
  background: #fff;
}

.preview-card__title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.preview-card__meta,
.preview-card__message {
  margin: 8px 0;
  color: $text-regular;
}

.preview-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.mt-16 {
  margin-top: 16px;
}
</style>
