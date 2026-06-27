<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { stocktakeApi } from '@/api/modules/stocktake'
import { warehouseApi } from '@/api/modules/warehouse'
import { employeeApi } from '@/api/modules/employee'
import { STOCKTAKE_DIFF_DIRECTION_MAP, STOCKTAKE_TYPE_OPTIONS } from '@/constants/stocktake'
import { STOCKTAKE_PERMISSIONS } from '@/constants/permission'
import StocktakeSummaryCard from '@/components/business/stocktake/StocktakeSummaryCard.vue'
import type { Employee } from '@/types/employee'
import type { Location, Warehouse } from '@/types/warehouse'
import type { StocktakeOrderDetail, StocktakeOrderItemForm, StocktakeSnapshotPreviewItem } from '@/types/stocktake'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const orderId = computed(() => {
  const id = route.params.id
  return id ? Number(id) : null
})
const isEdit = computed(() => !!orderId.value)
const readOnlyStatuses = ['pending', 'completed', 'voided']

const pageLoading = ref(false)
const submitLoading = ref(false)
const snapshotLoading = ref(false)
const uploadLoading = ref(false)

const warehouses = ref<Warehouse[]>([])
const locations = ref<Location[]>([])
const employees = ref<Employee[]>([])
const detail = ref<StocktakeOrderDetail | null>(null)
const snapshotPreviewVisible = ref(false)
const snapshotItems = ref<StocktakeSnapshotPreviewItem[]>([])
const fileList = ref<{ name: string; url: string }[]>([])
const uploadInputRef = ref<HTMLInputElement | null>(null)

const form = reactive({
  warehouseId: null as number | null,
  locationId: null as number | null,
  warehouseIds: [] as number[],
  locationIds: [] as number[],
  stocktakeType: 'regular',
  stocktakeDate: '',
  checkerId: null as number | null,
  checkerName: '',
  remark: '',
  attachments: [] as string[],
  items: [] as StocktakeOrderItemForm[],
})

const allWarehouseIds = computed(() => warehouses.value.map((item) => item.id))
const allLocationIds = computed(() => locations.value.map((item) => item.id))
const isAllWarehousesSelected = computed(() => !!warehouses.value.length && form.warehouseIds.length === warehouses.value.length)
const isAllLocationsSelected = computed(() => !!locations.value.length && form.locationIds.length === locations.value.length)

const readonly = computed(() => readOnlyStatuses.includes(detail.value?.status || ''))
const canUpload = computed(() => !!orderId.value && !readonly.value)
const isCreateWithoutSavedOrder = computed(() => !orderId.value)

const today = () => {
  const now = new Date()
  const yyyy = now.getFullYear()
  const mm = `${now.getMonth() + 1}`.padStart(2, '0')
  const dd = `${now.getDate()}`.padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

const resetForm = () => {
  form.warehouseId = null
  form.locationId = null
  form.warehouseIds = []
  form.locationIds = []
  form.stocktakeType = 'regular'
  form.stocktakeDate = today()
  form.checkerId = userStore.userInfo?.id ?? null
  form.checkerName = userStore.userInfo?.realName ?? ''
  form.remark = ''
  form.attachments = []
  form.items = []
  detail.value = null
  snapshotItems.value = []
  fileList.value = []
}

const loadBaseOptions = async () => {
  try {
    const [warehouseRes, employeeRes] = await Promise.all([
      warehouseApi.getList({ pageNum: 1, pageSize: 1000 }),
      employeeApi.getList({ pageNum: 1, pageSize: 1000 }),
    ])
    if (warehouseRes.code === 'SUCCESS') {
      warehouses.value = warehouseRes.data?.list || []
    }
    if (employeeRes.code === 'SUCCESS') {
      employees.value = employeeRes.data?.list || []
    }
  } catch (e) {
    console.error('加载基础选项失败', e)
  }
}

const loadLocations = async (warehouseIds?: number[]) => {
  locations.value = []
  const ids = warehouseIds || []
  if (!ids.length) return
  try {
    const results = await Promise.all(ids.map((warehouseId) => warehouseApi.getLocations({ warehouseId, pageNum: 1, pageSize: 1000 })))
    const locationMap = new Map<number, Location>()
    results.forEach((res) => {
      if (res.code === 'SUCCESS') {
        ;(res.data?.list || []).forEach((item) => locationMap.set(item.id, item))
      }
    })
    locations.value = Array.from(locationMap.values())
  } catch (e) {
    console.error('加载仓位失败', e)
  }
}

const syncFileList = () => {
  fileList.value = (form.attachments || []).map((url) => ({
    name: url.split('/').pop() || '附件',
    url,
  }))
}

const mapSnapshotItemToFormItem = (item: StocktakeSnapshotPreviewItem): StocktakeOrderItemForm => ({
  inventoryId: item.inventoryId ?? null,
  materialId: item.materialId,
  materialName: item.materialName || '',
  spec: item.spec || '',
  unit: item.unit || '',
  warehouseId: item.warehouseId,
  locationId: item.locationId ?? null,
  batchNo: item.batchNo || '',
  expiryDate: item.expiryDate,
  systemQty: item.quantity || 0,
  actualQty: null,
  unitCost: item.unitCost || 0,
  diffReason: '',
  recognitionSource: '',
  aiConfidence: null,
  remark: '',
  lineRemark: '',
})

const loadSnapshotItems = async () => {
  if (orderId.value) return
  if (!form.warehouseIds.length) {
    snapshotItems.value = []
    form.items = []
    return
  }
  try {
    const res = await stocktakeApi.previewSnapshot({
      warehouseIds: form.warehouseIds,
      locationIds: form.locationIds,
    })
    if (res.code === 'SUCCESS') {
      snapshotItems.value = res.data || []
      form.items = (res.data || []).map((item) => mapSnapshotItemToFormItem(item))
      return
    }
  } catch (e) {
    console.error('加载盘点快照失败', e)
  }
  snapshotItems.value = []
  form.items = []
}

const recalcItem = (item: StocktakeOrderItemForm) => {
  const systemQty = Number(item.systemQty || 0)
  if (item.actualQty === null || item.actualQty === undefined || item.actualQty === ('' as any)) {
    return {
      diffQty: 0,
      diffAmount: 0,
      diffDirection: 'normal',
    }
  }
  const actualQty = Number(item.actualQty || 0)
  const diffQty = actualQty - systemQty
  let diffDirection = 'normal'
  if (diffQty > 0) diffDirection = 'surplus'
  if (diffQty < 0) diffDirection = 'deficit'
  return {
    diffQty,
    diffAmount: diffQty * Number(item.unitCost || 0),
    diffDirection,
  }
}

const summary = computed(() => {
  const items = form.items || []
  let diffQtyTotal = 0
  let surplusAmount = 0
  let deficitAmount = 0
  items.forEach((item) => {
    const calc = recalcItem(item)
    diffQtyTotal += Math.abs(calc.diffQty)
    if (calc.diffAmount > 0) surplusAmount += calc.diffAmount
    if (calc.diffAmount < 0) deficitAmount += Math.abs(calc.diffAmount)
  })
  const itemCount = items.length
  const baseQty = items.reduce((sum, item) => sum + Number(item.systemQty || 0), 0)
  const diffRate = baseQty > 0 ? (diffQtyTotal / baseQty) * 100 : 0
  return { itemCount, diffQtyTotal, surplusAmount, deficitAmount, diffRate }
})

const fillHeaderFromDetail = async (data: StocktakeOrderDetail) => {
  const itemWarehouseIds = Array.from(new Set((data.items || []).map((item) => item.warehouseId).filter((id): id is number => typeof id === 'number')))
  const itemLocationIds = Array.from(new Set((data.items || []).map((item) => item.locationId).filter((id): id is number => typeof id === 'number')))
  form.warehouseId = data.warehouseId
  form.locationId = data.locationId ?? null
  form.warehouseIds = itemWarehouseIds.length ? itemWarehouseIds : [data.warehouseId]
  form.locationIds = itemLocationIds.length ? itemLocationIds : (data.locationId ? [data.locationId] : [])
  form.stocktakeType = data.stocktakeType || 'regular'
  form.stocktakeDate = data.stocktakeDate
  form.checkerId = data.checkerId ?? null
  form.checkerName = data.checkerName || ''
  form.remark = data.remark || ''
  form.attachments = data.attachments || []
  await loadLocations(form.warehouseIds)
  form.items = (data.items || []).map((item) => ({
    id: item.id,
    inventoryId: item.inventoryId ?? null,
    materialId: item.materialId,
    materialName: item.materialName,
    spec: item.spec || '',
    unit: item.unit || '',
    warehouseId: item.warehouseId ?? data.warehouseId,
    locationId: item.locationId ?? data.locationId ?? null,
    batchNo: item.batchNo || '',
    expiryDate: item.expiryDate,
    systemQty: item.systemQty || 0,
    actualQty: item.actualQty ?? null,
    unitCost: item.unitCost || 0,
    diffReason: item.diffReason || '',
    recognitionSource: item.recognitionSource || '',
    aiConfidence: item.aiConfidence ?? null,
    remark: item.remark || '',
    lineRemark: item.lineRemark || '',
  }))
  syncFileList()
}

const loadDetail = async (id: number) => {
  pageLoading.value = true
  try {
    const res = await stocktakeApi.getDetail(id)
    if (res.code === 'SUCCESS' && res.data) {
      detail.value = res.data
      await fillHeaderFromDetail(res.data)
    }
  } catch {
  } finally {
    pageLoading.value = false
  }
}

const selectedWarehouseNames = computed(() => warehouses.value
  .filter((item) => form.warehouseIds.includes(item.id))
  .map((item) => item.warehouseName)
  .join('、'))

const selectedLocationNames = computed(() => locations.value
  .filter((item) => form.locationIds.includes(item.id))
  .map((item) => item.locationName)
  .join('、'))

const handleDownloadAttachment = async (url: string) => {
  if (!orderId.value) {
    ElMessage.warning('请先保存盘点单后再下载附件')
    return
  }
  try {
    await stocktakeApi.downloadAttachment(orderId.value, url)
  } catch {
  }
}

const openSnapshotPreview = async () => {
  if (!form.warehouseIds.length) {
    ElMessage.warning('请先选择盘点仓库')
    return
  }
  snapshotLoading.value = true
  try {
    const res = await stocktakeApi.previewSnapshot({
      warehouseIds: form.warehouseIds,
      locationIds: form.locationIds,
    })
    if (res.code === 'SUCCESS') {
      snapshotItems.value = res.data || []
      snapshotPreviewVisible.value = true
    }
  } catch {
  } finally {
    snapshotLoading.value = false
  }
}

const createOrderFromSnapshot = async () => {
  if (!snapshotItems.value.length) {
    ElMessage.warning('当前范围暂无可盘点物料')
    return
  }
  submitLoading.value = true
  try {
    const payload = {
      warehouseId: form.warehouseIds[0] ?? null,
      locationId: form.locationIds[0] ?? null,
      warehouseIds: form.warehouseIds,
      locationIds: form.locationIds,
      stocktakeType: form.stocktakeType,
      stocktakeDate: form.stocktakeDate,
      checkerId: form.checkerId,
      checkerName: form.checkerName,
      remark: form.remark,
      attachments: form.attachments,
    }
    const res = await stocktakeApi.create(payload)
    if (res.code === 'SUCCESS' && res.data) {
      ElMessage.success('创建盘点单成功')
      snapshotPreviewVisible.value = false
      await router.replace(`/stocktake/${res.data}/edit`)
    }
  } catch {
  } finally {
    submitLoading.value = false
  }
}

const buildUpdatePayload = () => ({
  warehouseId: form.warehouseId,
  locationId: form.locationId,
  warehouseIds: form.warehouseIds,
  locationIds: form.locationIds,
  stocktakeType: form.stocktakeType,
  stocktakeDate: form.stocktakeDate,
  checkerId: form.checkerId,
  checkerName: form.checkerName,
  remark: form.remark,
  attachments: form.attachments,
  items: form.items.map((item) => ({
    id: item.id,
    inventoryId: item.inventoryId,
    materialId: item.materialId,
    materialName: item.materialName,
    spec: item.spec,
    unit: item.unit,
    warehouseId: item.warehouseId,
    locationId: item.locationId,
    batchNo: item.batchNo,
    expiryDate: item.expiryDate,
    systemQty: item.systemQty,
    actualQty: item.actualQty,
    unitCost: item.unitCost,
    diffReason: item.diffReason,
    recognitionSource: item.recognitionSource,
    aiConfidence: item.aiConfidence,
    remark: item.remark,
    lineRemark: item.lineRemark,
  })),
})

const handleSave = async () => {
  if (!orderId.value) {
    await openSnapshotPreview()
    return
  }
  submitLoading.value = true
  try {
    await stocktakeApi.update(orderId.value, buildUpdatePayload())
    ElMessage.success('保存成功')
    await loadDetail(orderId.value)
  } catch {
  } finally {
    submitLoading.value = false
  }
}

const handleSubmitReview = async () => {
  if (!orderId.value) {
    ElMessage.warning('请先保存盘点单')
    return
  }
  submitLoading.value = true
  try {
    await stocktakeApi.update(orderId.value, buildUpdatePayload())
    await stocktakeApi.submit(orderId.value)
    ElMessage.success('提交审核成功')
    await loadDetail(orderId.value)
  } catch {
  } finally {
    submitLoading.value = false
  }
}

const handleSelectChecker = (value: number) => {
  const employee = employees.value.find((item) => item.id === value)
  form.checkerName = employee?.realName || ''
}

const handleWarehouseChange = async (values: number[]) => {
  form.warehouseIds = values
  form.warehouseId = values[0] ?? null
  form.locationIds = []
  form.locationId = null
  await loadLocations(values)
  await loadSnapshotItems()
}

const handleLocationChange = async (values: number[]) => {
  form.locationIds = values
  form.locationId = values[0] ?? null
  await loadSnapshotItems()
}

const handleToggleAllWarehouses = (checked: boolean) => {
  handleWarehouseChange(checked ? [...allWarehouseIds.value] : [])
}

const handleToggleAllLocations = (checked: boolean) => {
  handleLocationChange(checked ? [...allLocationIds.value] : [])
}

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

const triggerUpload = () => {
  if (!canUpload.value) {
    ElMessage.warning('请先保存盘点单后再上传附件')
    return
  }
  uploadInputRef.value?.click()
}

const handleFilesSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length || !orderId.value) return
  const oversized = files.find((file) => file.size > ATTACHMENT_LIMITS[resolveAttachmentCategory(file)])
  if (oversized) {
    ElMessage.warning(attachmentLimitMessage(resolveAttachmentCategory(oversized)))
    input.value = ''
    return
  }
  uploadLoading.value = true
  try {
    await stocktakeApi.uploadAttachments(orderId.value, files)
    ElMessage.success('附件上传成功')
    await loadDetail(orderId.value)
  } catch {
  } finally {
    uploadLoading.value = false
    input.value = ''
  }
}

const removeAttachment = (index: number) => {
  form.attachments.splice(index, 1)
  syncFileList()
  ElMessage.info('附件已从当前表单移除，点击”保存”后才会正式生效')
}

const handleRefreshSnapshot = async () => {
  if (!orderId.value) return
  snapshotLoading.value = true
  try {
    const res = await stocktakeApi.refreshSnapshot(orderId.value)
    if (res.code === 'SUCCESS') {
      ElMessage.success('快照已刷新')
      await loadDetail(orderId.value)
    }
  } catch {
  } finally {
    snapshotLoading.value = false
  }
}

const handleBack = () => {
  router.push('/stocktake')
}

const diffLabel = (item: StocktakeOrderItemForm) => STOCKTAKE_DIFF_DIRECTION_MAP[recalcItem(item).diffDirection] || '正常'
const rowClassName = ({ row }: { row: StocktakeOrderItemForm }) => {
  return recalcItem(row).diffQty !== 0 ? 'diff-row' : ''
}

watch(() => route.fullPath, async () => {
  resetForm()
  await loadBaseOptions()
  if (orderId.value) {
    await loadDetail(orderId.value)
  }
}, { immediate: true })

onMounted(async () => {
  if (!form.stocktakeDate) {
    form.stocktakeDate = today()
  }
})
</script>

<template>
  <div class="stocktake-edit-page" v-loading="pageLoading">
    <div class="page-header">
      <div>
        <div class="page-title">{{ isEdit ? '编辑盘点单' : '新增盘点单' }}</div>
        <div class="page-desc">先维护盘点范围与盘点明细，再保存或提交审核。</div>
      </div>
      <div class="page-actions">
        <el-button @click="handleBack">返回列表</el-button>
        <el-button v-if="!readonly" @click="handleSave" :loading="submitLoading">保存</el-button>
        <el-button
          v-if="isEdit && !readonly"
          v-permission="STOCKTAKE_PERMISSIONS.SUBMIT"
          type="primary"
          @click="handleSubmitReview"
          :loading="submitLoading"
        >
          提交审核
        </el-button>
      </div>
    </div>

    <div class="page-card">
      <el-form label-width="96px">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="盘点日期" required>
              <el-date-picker
                v-model="form.stocktakeDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择盘点日期"
                style="width: 100%"
                :disabled="readonly"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="盘点仓库" required>
              <el-select
                v-model="form.warehouseIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择盘点仓库"
                style="width: 100%"
                :disabled="readonly || isEdit"
                @change="handleWarehouseChange"
              >
                <template #header>
                  <el-checkbox :model-value="isAllWarehousesSelected" @change="handleToggleAllWarehouses">全选</el-checkbox>
                </template>
                <el-option v-for="item in warehouses" :key="item.id" :label="item.warehouseName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="盘点仓位">
              <el-select
                v-model="form.locationIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="全部仓位"
                style="width: 100%"
                :disabled="readonly || isEdit || !form.warehouseIds.length"
                @change="handleLocationChange"
              >
                <template #header>
                  <el-checkbox :model-value="isAllLocationsSelected" @change="handleToggleAllLocations">全选</el-checkbox>
                </template>
                <el-option v-for="item in locations" :key="item.id" :label="item.locationName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="盘点类型">
              <el-select v-model="form.stocktakeType" style="width: 100%" :disabled="readonly">
                <el-option v-for="item in STOCKTAKE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="盘点人">
              <el-select v-model="form.checkerId" placeholder="请选择盘点人" filterable style="width: 100%" :disabled="readonly" @change="handleSelectChecker">
                <el-option v-for="item in employees" :key="item.id" :label="item.realName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit :disabled="readonly" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert
        v-if="detail?.rejectRemark"
        type="warning"
        :closable="false"
        class="mb-16"
        :title="`当前单据已驳回，请根据驳回原因修改后重新提交：${detail.rejectRemark}`"
      />

      <div class="toolbar-row">
        <div class="section-title">盘点汇总</div>
        <div class="toolbar-actions">
          <el-button v-if="!isEdit" type="primary" plain :loading="snapshotLoading" @click="openSnapshotPreview">预览快照</el-button>
          <el-button v-if="isEdit && detail?.status === 'rejected'" type="warning" plain :loading="snapshotLoading" @click="handleRefreshSnapshot">刷新快照</el-button>          <el-button :loading="uploadLoading" :disabled="!canUpload" @click="triggerUpload">上传附件</el-button>
          <input ref="uploadInputRef" type="file" multiple accept=".jpg,.jpeg,.png,.webp,.mp4,.mov,.pdf,.doc,.docx,.xls,.xlsx" class="hidden-input" @change="handleFilesSelected" />
        </div>
      </div>

      <StocktakeSummaryCard
        :item-count="summary.itemCount"
        :diff-qty-total="summary.diffQtyTotal"
        :diff-rate="summary.diffRate"
        :surplus-amount="summary.surplusAmount"
        :deficit-amount="summary.deficitAmount"
      />

      <div class="section-title mt-16">附件</div>
      <div v-if="fileList.length" class="attachment-list">
        <div v-for="(file, index) in fileList" :key="file.url" class="attachment-item">
          <a href="javascript:void(0)" @click.prevent="handleDownloadAttachment(file.url)">{{ file.name }}</a>
          <el-button v-if="!readonly" type="danger" link @click="removeAttachment(index)">删除</el-button>
        </div>
      </div>
      <div v-else class="empty-text">
        {{ isCreateWithoutSavedOrder ? '请先保存盘点单后再上传附件' : '暂无附件' }}
      </div>

      <div class="section-title mt-16">盘点明细</div>
      <el-table :data="form.items" border stripe size="small" :row-class-name="rowClassName">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="materialName" label="物料名称" min-width="150" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="batchNo" label="批次号" width="120" />
        <el-table-column prop="systemQty" label="系统库存" width="110" align="right" />
        <el-table-column label="实际库存" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.actualQty"
              :min="0"
              :precision="3"
              :controls="false"
              style="width: 100%"
              :disabled="readonly"
            />
          </template>
        </el-table-column>
        <el-table-column label="差异数量" width="110" align="right">
          <template #default="{ row }">{{ recalcItem(row).diffQty.toFixed(3) }}</template>
        </el-table-column>
        <el-table-column label="差异方向" width="100" align="center">
          <template #default="{ row }">{{ diffLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="差异金额" width="110" align="right">
          <template #default="{ row }">{{ recalcItem(row).diffAmount.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="差异原因" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.diffReason" maxlength="100" :disabled="readonly" />
          </template>
        </el-table-column>
        <el-table-column label="识别来源" width="120">
          <template #default="{ row }">
            <el-input v-model="row.recognitionSource" maxlength="50" :disabled="readonly" />
          </template>
        </el-table-column>
        <el-table-column label="AI 置信度" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.aiConfidence"
              :min="0"
              :max="1"
              :step="0.1"
              :precision="2"
              :controls="false"
              style="width: 100%"
              :disabled="readonly"
            />
          </template>
        </el-table-column>
        <el-table-column label="行备注" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.lineRemark" maxlength="200" :disabled="readonly" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="snapshotPreviewVisible" title="盘点快照预览" width="960px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        :title="`确认快照后会创建正式盘点单，并自动带出当前范围的库存明细。已选择仓库：${selectedWarehouseNames || '未选择'}；已选择仓位：${selectedLocationNames || '全部仓位'}`"
        class="mb-16"
      />
      <el-table :data="snapshotItems" border stripe size="small" max-height="420">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="materialName" label="物料名称" min-width="140" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="batchNo" label="批次号" width="120" />
        <el-table-column prop="quantity" label="系统库存" width="110" align="right" />
        <el-table-column prop="warehouseName" label="仓库" min-width="120" />
        <el-table-column prop="locationName" label="仓位" min-width="120">
          <template #default="{ row }">{{ row.locationName || '全部仓位' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="snapshotPreviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="createOrderFromSnapshot">确认生成盘点单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.stocktake-edit-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.page-card {
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
}

.page-header {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
}

.page-desc {
  margin-top: 6px;
  color: $text-regular;
  font-size: 13px;
}

.page-actions {
  display: flex;
  gap: 12px;
}

.page-card {
  padding: 20px;
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8px;
}

.mt-16 {
  margin-top: 16px;
}

.mb-16 {
  margin-bottom: 16px;
}

.hidden-input {
  display: none;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.empty-text {
  color: $text-regular;
  font-size: 13px;
}

:deep(.diff-row) {
  --el-table-tr-bg-color: #fef0f0;
}
</style>
