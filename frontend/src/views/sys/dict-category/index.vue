<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { useUserStore } from '@/stores/modules/user'
import {
  DICT_CATEGORY_CODE_MAX_LENGTH,
  DICT_CATEGORY_CODE_PATTERN_MESSAGE,
  DICT_CATEGORY_NAME_MAX_LENGTH,
  DICT_CATEGORY_NAME_PATTERN_MESSAGE,
  DICT_CATEGORY_REMARK_MAX_LENGTH,
  DICT_CATEGORY_REMARK_PATTERN_MESSAGE,
  isValidDictCategoryCode,
  isValidDictCategoryName,
  isValidDictCategoryRemark
} from '@/utils/dict-category-validation'
import type {
  DictCategoryAreaCoefficientEffectScope,
  DictCategoryAreaCoefficientHistory,
  DictCategoryAreaCoefficientRecalcTask,
  DictCategoryAreaSuggestion,
  DictCategoryDetail,
  DictCategoryForm,
  DictCategoryItem,
  DictCategorySourceType,
  DictCategoryType
} from '@/types/dict-category'
import { DICT_CATEGORY_PERMISSIONS } from '@/constants/permission'

const dictStore = useDictCategoryStore()
const userStore = useUserStore()

const searchForm = reactive<{
  keyword: string
  sourceType: DictCategorySourceType | ''
  status: 'active' | 'inactive' | ''
}>({
  keyword: '',
  sourceType: '',
  status: ''
})

const dialogVisible = ref(false)
const drawerVisible = ref(false)
const impactDialogVisible = ref(false)
const areaRecalcTaskDialogVisible = ref(false)
const submitting = ref(false)
const suggesting = ref(false)
const historyLoading = ref(false)
const areaRecalcTaskLoading = ref(false)
const formRef = ref()
const currentItem = ref<DictCategoryItem | null>(null)
const suggestionPreview = ref<DictCategoryAreaSuggestion | null>(null)
const areaCoefficientHistory = ref<DictCategoryAreaCoefficientHistory[]>([])
const currentAreaRecalcTask = ref<DictCategoryAreaCoefficientRecalcTask | null>(null)
const pendingSubmitPayload = ref<DictCategoryForm | null>(null)
const activeAreaRecalcTaskId = ref<number | null>(null)
const areaRecalcPollingTimer = ref<number | null>(null)
const originalAreaCoefficient = ref<number | null>(null)
const originalUpdatedAt = ref<string | null>(null)
const formData = reactive<DictCategoryForm>({
  dictCode: '',
  dictName: '',
  sortOrder: 0,
  areaCoefficient: null,
  areaCoefficientSource: null,
  aiSuggestedAreaCoefficient: null,
  aiSuggestionReason: '',
  aiSuggestionGeneratedAt: '',
  lastKnownUpdatedAt: null,
  remark: ''
})
const impactConfirmState = reactive<{
  acknowledged: boolean
  effectScope: DictCategoryAreaCoefficientEffectScope
}>({
  acknowledged: false,
  effectScope: 'subsequent_only'
})

const sourceTypeOptions = [
  { label: '系统内置', value: 'system' },
  { label: '用户自建', value: 'custom' }
]

const statusOptions = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' }
]

const impactScopeOptions: Array<{ label: string; value: DictCategoryAreaCoefficientEffectScope; description: string }> = [
  {
    label: '仅对后续业务生效',
    value: 'subsequent_only',
    description: '历史库存核算、报表统计、AI测算结果保持原系数口径不变'
  },
  {
    label: '保存后允许管理员手动触发全量回溯重算',
    value: 'manual_recalc',
    description: '保存后可由管理员按新系数重新生成历史核算与统计影子结果日志'
  }
]

const isMaterialCategory = computed(() => dictStore.currentCategory === 'material_category')
const isAdminUser = computed(() => userStore.isAdmin())
const currentDetail = computed<DictCategoryDetail | null>(() => dictStore.detail)
const currentCategoryTitle = computed(() => {
  return dictStore.categories.find(item => item.categoryType === dictStore.currentCategory)?.categoryName || '字典分类'
})
const dialogTitle = computed(() => currentItem.value ? '编辑分类项' : '新增分类项')
const latestRecalcAvailableHistory = computed(() => areaCoefficientHistory.value.find(item => item.recalcAvailable) || null)

const validateAreaCoefficient = (_rule: unknown, value: number | null | undefined, callback: (error?: Error) => void) => {
  if (!isMaterialCategory.value) {
    callback()
    return
  }
  if (value === null || value === undefined || value === '') {
    callback(new Error('请输入物料类别统一面积系数（㎡/单件）'))
    return
  }
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    callback(new Error('物料类别统一面积系数（㎡/单件）必须大于0'))
    return
  }
  const decimalPart = String(value).split('.')[1]
  if (decimalPart && decimalPart.length > 4) {
    callback(new Error('物料类别统一面积系数（㎡/单件）最多支持4位小数'))
    return
  }
  callback()
}

const validateDictCode = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const normalizedValue = value?.trim() || ''
  if (!normalizedValue) {
    callback()
    return
  }
  if (!isValidDictCategoryCode(normalizedValue)) {
    callback(new Error(DICT_CATEGORY_CODE_PATTERN_MESSAGE))
    return
  }
  callback()
}

const validateDictName = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const normalizedValue = value?.trim() || ''
  if (!normalizedValue) {
    callback()
    return
  }
  if (!isValidDictCategoryName(normalizedValue)) {
    callback(new Error(DICT_CATEGORY_NAME_PATTERN_MESSAGE))
    return
  }
  callback()
}

const validateRemark = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const normalizedValue = value?.trim() || ''
  if (!normalizedValue) {
    callback()
    return
  }
  if (!isValidDictCategoryRemark(normalizedValue)) {
    callback(new Error(DICT_CATEGORY_REMARK_PATTERN_MESSAGE))
    return
  }
  callback()
}

const formRules = {
  dictCode: [
    { required: true, message: '请输入分类项编码', trigger: 'blur' },
    { max: DICT_CATEGORY_CODE_MAX_LENGTH, message: '分类项编码长度不能超过50个字符', trigger: 'blur' },
    { validator: validateDictCode, trigger: ['blur', 'change'] }
  ],
  dictName: [
    { required: true, message: '请输入分类项名称', trigger: 'blur' },
    { max: DICT_CATEGORY_NAME_MAX_LENGTH, message: '分类项名称长度不能超过50个字符', trigger: 'blur' },
    { validator: validateDictName, trigger: ['blur', 'change'] }
  ],
  remark: [
    { max: DICT_CATEGORY_REMARK_MAX_LENGTH, message: '备注长度不能超过200个字符', trigger: 'blur' },
    { validator: validateRemark, trigger: ['blur', 'change'] }
  ],
  areaCoefficient: [
    { validator: validateAreaCoefficient, trigger: ['change', 'blur'] }
  ]
}

const isCustomItem = (row: DictCategoryItem) => row.sourceType === 'custom'

const formatAreaCoefficient = (value?: number | null) => {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  return Number(value).toFixed(4)
}

const formatImpactScope = (scope?: DictCategoryAreaCoefficientEffectScope) => {
  return scope === 'manual_recalc' ? '管理员可手动回溯重算' : '仅后续业务生效'
}

const formatRecalcStatus = (status?: string) => {
  switch (status) {
    case 'pending':
      return '待重算'
    case 'running':
      return '重算中'
    case 'completed':
      return '已完成'
    case 'failed':
      return '失败'
    default:
      return '不适用'
  }
}

const getRecalcStatusTagType = (status?: string) => {
  switch (status) {
    case 'pending':
      return 'warning'
    case 'running':
      return 'primary'
    case 'completed':
      return 'success'
    case 'failed':
      return 'danger'
    default:
      return 'info'
  }
}

const getProgressStatus = (status?: string) => {
  if (status === 'completed') {
    return 'success'
  }
  if (status === 'failed') {
    return 'exception'
  }
  return undefined
}

const syncAreaCoefficientSource = () => {
  if (!isMaterialCategory.value) {
    return
  }
  if (formData.areaCoefficient === null || formData.areaCoefficient === undefined) {
    formData.areaCoefficientSource = null
    return
  }
  if (formData.aiSuggestedAreaCoefficient !== null && formData.aiSuggestedAreaCoefficient !== undefined) {
    formData.areaCoefficientSource = Number(formData.areaCoefficient) === Number(formData.aiSuggestedAreaCoefficient)
      ? 'ai'
      : 'manual'
    return
  }
  formData.areaCoefficientSource = 'manual'
}

const syncSearchForm = () => {
  searchForm.keyword = dictStore.query.keyword || ''
  searchForm.sourceType = dictStore.query.sourceType || ''
  searchForm.status = dictStore.query.status || ''
}

const resetImpactConfirmState = () => {
  impactConfirmState.acknowledged = false
  impactConfirmState.effectScope = 'subsequent_only'
  pendingSubmitPayload.value = null
}

const resetAreaRecalcTaskState = () => {
  currentAreaRecalcTask.value = null
  activeAreaRecalcTaskId.value = null
}

const stopAreaRecalcPolling = () => {
  if (areaRecalcPollingTimer.value !== null) {
    window.clearInterval(areaRecalcPollingTimer.value)
    areaRecalcPollingTimer.value = null
  }
}

const maybeStartAreaRecalcPolling = () => {
  stopAreaRecalcPolling()
  if (!activeAreaRecalcTaskId.value || !currentDetail.value) {
    return
  }
  if (!currentAreaRecalcTask.value || !['pending', 'running'].includes(currentAreaRecalcTask.value.status)) {
    return
  }
  areaRecalcPollingTimer.value = window.setInterval(async () => {
    if (!activeAreaRecalcTaskId.value || !currentDetail.value) {
      stopAreaRecalcPolling()
      return
    }
    const taskDetail = await dictStore.getAreaCoefficientRecalcTaskDetail(
      currentDetail.value.categoryType as DictCategoryType,
      currentDetail.value.id,
      activeAreaRecalcTaskId.value
    )
    if (!taskDetail) {
      return
    }
    currentAreaRecalcTask.value = taskDetail
    if (!['pending', 'running'].includes(taskDetail.status)) {
      stopAreaRecalcPolling()
    }
  }, 2000)
}

const sameAreaCoefficient = (left?: number | null, right?: number | null) => {
  if (left === null || left === undefined || left === '') {
    return right === null || right === undefined || right === ''
  }
  if (right === null || right === undefined || right === '') {
    return false
  }
  return Number(left) === Number(right)
}

const hasAreaCoefficientChanged = () => {
  return Boolean(currentItem.value && isMaterialCategory.value && !sameAreaCoefficient(formData.areaCoefficient, originalAreaCoefficient.value))
}

const buildSubmitPayload = (): DictCategoryForm => {
  return {
    dictCode: formData.dictCode.trim(),
    dictName: formData.dictName.trim(),
    sortOrder: Number(formData.sortOrder || 0),
    remark: formData.remark?.trim() || '',
    ...(isMaterialCategory.value ? {
      areaCoefficient: formData.areaCoefficient ?? null,
      areaCoefficientSource: formData.areaCoefficientSource || 'manual',
      aiSuggestedAreaCoefficient: formData.aiSuggestedAreaCoefficient ?? null,
      aiSuggestionReason: formData.aiSuggestionReason?.trim() || null,
      aiSuggestionGeneratedAt: formData.aiSuggestionGeneratedAt || null,
      lastKnownUpdatedAt: originalUpdatedAt.value || null
    } : {})
  }
}

const submitPayload = async (payload: DictCategoryForm) => {
  submitting.value = true
  try {
    const success = currentItem.value
      ? await dictStore.updateItem(currentItem.value.categoryType, currentItem.value.id, payload)
      : await dictStore.createItem(payload)

    if (success) {
      dialogVisible.value = false
      impactDialogVisible.value = false
      resetForm()
    }
  } catch (error: any) {
    if (error?.message) {
      ElMessage.error(error.message)
    }
  } finally {
    submitting.value = false
  }
}

const openAreaRecalcTaskDialog = async (categoryType: DictCategoryType, id: number, taskId: number) => {
  areaRecalcTaskLoading.value = true
  activeAreaRecalcTaskId.value = taskId
  try {
    const taskDetail = await dictStore.getAreaCoefficientRecalcTaskDetail(categoryType, id, taskId)
    if (!taskDetail) {
      ElMessage.error('获取重算任务明细失败，请稍后重试')
      return
    }
    currentAreaRecalcTask.value = taskDetail
    areaRecalcTaskDialogVisible.value = true
    maybeStartAreaRecalcPolling()
  } finally {
    areaRecalcTaskLoading.value = false
  }
}

const handleTabChange = async (categoryType: string) => {
  await dictStore.changeCategory(categoryType as DictCategoryType)
  syncSearchForm()
}

const handleSearch = async () => {
  await dictStore.search({
    keyword: searchForm.keyword.trim(),
    sourceType: searchForm.sourceType,
    status: searchForm.status
  })
}

const handleReset = async () => {
  await dictStore.resetSearch()
  syncSearchForm()
}

const handleTextFieldInput = (field: 'dictCode' | 'dictName' | 'remark') => {
  const validationTask = formRef.value?.validateField?.(field)
  if (validationTask && typeof validationTask.catch === 'function') {
    validationTask.catch(() => undefined)
  }
}

const resetForm = () => {
  formData.dictCode = ''
  formData.dictName = ''
  formData.sortOrder = 0
  formData.areaCoefficient = null
  formData.areaCoefficientSource = null
  formData.aiSuggestedAreaCoefficient = null
  formData.aiSuggestionReason = ''
  formData.aiSuggestionGeneratedAt = ''
  formData.lastKnownUpdatedAt = null
  formData.remark = ''
  suggestionPreview.value = null
  currentItem.value = null
  originalAreaCoefficient.value = null
  originalUpdatedAt.value = null
  resetImpactConfirmState()
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: DictCategoryItem) => {
  const detail = await dictStore.getDetail(row.categoryType, row.id)
  if (!detail) {
    return
  }
  currentItem.value = row
  formData.dictCode = detail.dictCode
  formData.dictName = detail.dictName
  formData.sortOrder = detail.sortOrder
  formData.areaCoefficient = detail.areaCoefficient ?? null
  formData.areaCoefficientSource = detail.areaCoefficientSource ?? null
  formData.aiSuggestedAreaCoefficient = detail.aiSuggestedAreaCoefficient ?? null
  formData.aiSuggestionReason = detail.aiSuggestionReason || ''
  formData.aiSuggestionGeneratedAt = detail.aiSuggestionGeneratedAt || ''
  formData.lastKnownUpdatedAt = detail.updatedAt || null
  formData.remark = detail.remark || ''
  originalAreaCoefficient.value = detail.areaCoefficient ?? null
  originalUpdatedAt.value = detail.updatedAt || null
  suggestionPreview.value = detail.aiSuggestedAreaCoefficient !== null && detail.aiSuggestedAreaCoefficient !== undefined
    ? {
        areaCoefficient: detail.aiSuggestedAreaCoefficient,
        reason: detail.aiSuggestionReason || '',
        generatedAt: detail.aiSuggestionGeneratedAt || ''
      }
    : null
  dialogVisible.value = true
}

const handleDetail = async (row: DictCategoryItem) => {
  const detail = await dictStore.getDetail(row.categoryType, row.id)
  if (!detail) {
    return
  }
  drawerVisible.value = true
}

const handleDelete = async (row: DictCategoryItem) => {
  await dictStore.deleteItem(row.categoryType, row.id)
}

const handleToggleStatus = async (row: DictCategoryItem) => {
  await dictStore.updateItemStatus(row.categoryType, row.id, row.status === 'active' ? 'inactive' : 'active')
}

const handleGetAreaSuggestion = async () => {
  if (!isMaterialCategory.value) {
    return
  }
  const dictName = formData.dictName?.trim()
  if (!dictName) {
    ElMessage.warning('请先输入分类项名称')
    return
  }

  suggesting.value = true
  try {
    const suggestion = await dictStore.getAreaCoefficientSuggestion(dictName)
    if (!suggestion) {
      ElMessage.error('获取AI建议失败，请稍后重试')
      return
    }
    suggestionPreview.value = suggestion
    ElMessage.success('AI建议已生成，可选择应用到正式字段')
  } catch (error: any) {
    ElMessage.error(error?.message || '获取AI建议失败，请稍后重试')
  } finally {
    suggesting.value = false
  }
}

const handleApplyAreaSuggestion = () => {
  if (!suggestionPreview.value) {
    return
  }
  formData.areaCoefficient = suggestionPreview.value.areaCoefficient
  formData.areaCoefficientSource = 'ai'
  formData.aiSuggestedAreaCoefficient = suggestionPreview.value.areaCoefficient
  formData.aiSuggestionReason = suggestionPreview.value.reason
  formData.aiSuggestionGeneratedAt = suggestionPreview.value.generatedAt
  formRef.value?.validateField?.('areaCoefficient')
  ElMessage.success('已应用AI建议系数，可继续手动调整')
}

const handleAreaCoefficientChange = () => {
  syncAreaCoefficientSource()
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  const payload = buildSubmitPayload()
  if (hasAreaCoefficientChanged()) {
    pendingSubmitPayload.value = payload
    impactDialogVisible.value = true
    return
  }
  await submitPayload(payload)
}

const handleImpactConfirmSubmit = async () => {
  if (!pendingSubmitPayload.value) {
    impactDialogVisible.value = false
    return
  }
  if (!impactConfirmState.acknowledged) {
    ElMessage.warning('请先勾选已知悉影响后再保存')
    return
  }
  await submitPayload({
    ...pendingSubmitPayload.value,
    areaCoefficientImpactConfirmed: true,
    areaCoefficientEffectScope: impactConfirmState.effectScope
  })
}

const handleAreaHistoryRecalc = async (historyItem: DictCategoryAreaCoefficientHistory) => {
  if (!currentDetail.value) {
    return
  }
  const result = await dictStore.startAreaCoefficientRecalc(
    currentDetail.value.categoryType as DictCategoryType,
    currentDetail.value.id,
    historyItem.id
  )
  if (!result) {
    return
  }
  await openAreaRecalcTaskDialog(
    currentDetail.value.categoryType as DictCategoryType,
    currentDetail.value.id,
    result.taskId
  )
}

const handleStartLatestAreaRecalc = async () => {
  if (!latestRecalcAvailableHistory.value) {
    ElMessage.warning('当前没有可执行回溯重算的最新修正版本')
    return
  }
  await handleAreaHistoryRecalc(latestRecalcAvailableHistory.value)
}

const handleViewAreaRecalcTask = async (historyItem: DictCategoryAreaCoefficientHistory) => {
  if (!currentDetail.value || !historyItem.recalcTaskId) {
    return
  }
  await openAreaRecalcTaskDialog(
    currentDetail.value.categoryType as DictCategoryType,
    currentDetail.value.id,
    historyItem.recalcTaskId
  )
}

const handlePageChange = async (page: number) => {
  dictStore.pageNum = page
  await dictStore.fetchList()
}

const handleSizeChange = async (size: number) => {
  dictStore.pageSize = size
  dictStore.pageNum = 1
  await dictStore.fetchList()
}

const getDisplayIndex = (index: number) => {
  return (dictStore.pageNum - 1) * dictStore.pageSize + index + 1
}

watch(dialogVisible, (visible) => {
  if (!visible) {
    formRef.value?.clearValidate?.()
    resetForm()
  }
})

watch(impactDialogVisible, (visible) => {
  if (!visible) {
    resetImpactConfirmState()
  }
})

watch(drawerVisible, (visible) => {
  if (!visible) {
    areaCoefficientHistory.value = []
    areaRecalcTaskDialogVisible.value = false
    stopAreaRecalcPolling()
    resetAreaRecalcTaskState()
  }
})

watch(areaRecalcTaskDialogVisible, (visible) => {
  if (!visible) {
    stopAreaRecalcPolling()
    resetAreaRecalcTaskState()
  }
})

onBeforeUnmount(() => {
  stopAreaRecalcPolling()
})

onMounted(async () => {
  await dictStore.init()
  syncSearchForm()
})
</script>

<template>
  <div class="dict-category-page">
    <div class="tabs-toolbar">
      <el-tabs
        :model-value="dictStore.currentCategory"
        class="category-tabs"
        @tab-change="handleTabChange"
      >
        <el-tab-pane
          v-for="tab in dictStore.categories"
          :key="tab.categoryType"
          :label="`${tab.categoryName}（${tab.itemCount}）`"
          :name="tab.categoryType"
        />
      </el-tabs>
    </div>

    <div class="toolbar">
      <el-row :gutter="12" align="middle">
        <el-col :span="5">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入分类项名称/编码"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="4">
          <el-select
            v-model="searchForm.sourceType"
            placeholder="全部来源"
            clearable
          >
            <el-option
              v-for="item in sourceTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-col>
        <el-col :span="6" class="toolbar-actions">
          <el-button
            type="primary"
            v-permission="DICT_CATEGORY_PERMISSIONS.CREATE"
            @click="handleAdd"
          >
            + 新增分类项
          </el-button>
        </el-col>
      </el-row>
    </div>

    <div class="table-card">
      <div class="table-title">{{ currentCategoryTitle }}</div>
      <el-table
        :data="dictStore.list"
        :loading="dictStore.loading"
        stripe
        height="100%"
      >
        <el-table-column
          type="index"
          label="序号"
          width="70"
          align="center"
          :index="getDisplayIndex"
        />
        <el-table-column prop="dictCode" label="分类项编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="dictName" label="分类项名称" min-width="180" show-overflow-tooltip />
        <el-table-column
          v-if="isMaterialCategory"
          prop="areaCoefficient"
          label="物料类别统一面积系数（㎡/单件）"
          min-width="210"
          align="center"
        >
          <template #default="{ row }">
            {{ formatAreaCoefficient(row.areaCoefficient) }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'system' ? 'info' : 'primary'" size="small">
              {{ row.sourceType === 'system' ? '系统内置' : '用户自建' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="referenceCount" label="引用数量" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" min-width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            <template v-if="isCustomItem(row)">
              <el-button
                type="primary"
                link
                v-permission="DICT_CATEGORY_PERMISSIONS.EDIT"
                @click="handleEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                :type="row.status === 'active' ? 'warning' : 'success'"
                link
                v-permission="DICT_CATEGORY_PERMISSIONS.STATUS"
                @click="handleToggleStatus(row)"
              >
                {{ row.status === 'active' ? '禁用' : '启用' }}
              </el-button>
              <el-button
                type="danger"
                link
                v-permission="DICT_CATEGORY_PERMISSIONS.DELETE"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination">
      <span class="total">共 {{ dictStore.total }} 条</span>
      <el-pagination
        :current-page="dictStore.pageNum"
        :page-size="dictStore.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="dictStore.total"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-drawer
      v-model="drawerVisible"
      title="分类项详情"
      size="820px"
    >
      <template v-if="currentDetail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="分类大类">{{ currentDetail.categoryName }}</el-descriptions-item>
          <el-descriptions-item label="分类项编码">{{ currentDetail.dictCode }}</el-descriptions-item>
          <el-descriptions-item label="分类项名称">{{ currentDetail.dictName }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">
            {{ currentDetail.sourceType === 'system' ? '系统内置' : '用户自建' }}
          </el-descriptions-item>
          <el-descriptions-item label="引用数量">{{ currentDetail.referenceCount }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ currentDetail.status === 'active' ? '启用' : '禁用' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ currentDetail.remark || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentDetail.createdAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ currentDetail.updatedAt || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="150px"
      >
        <el-form-item label="分类大类">
          <el-input :model-value="currentCategoryTitle" disabled />
        </el-form-item>
        <el-form-item label="分类项编码" prop="dictCode">
          <el-input
            v-model="formData.dictCode"
            maxlength="50"
            show-word-limit
            placeholder="请输入分类项编码"
            @input="handleTextFieldInput('dictCode')"
          />
        </el-form-item>
        <el-form-item label="分类项名称" prop="dictName">
          <el-input
            v-model="formData.dictName"
            maxlength="50"
            show-word-limit
            placeholder="请输入分类项名称"
            @input="handleTextFieldInput('dictName')"
          />
        </el-form-item>
        <el-form-item
          v-if="isMaterialCategory"
          label="统一面积系数"
          prop="areaCoefficient"
          required
        >
          <div class="area-coefficient-field">
            <el-input-number
              v-model="formData.areaCoefficient"
              :min="0.0001"
              :step="0.0001"
              :precision="4"
              controls-position="right"
              style="width: 100%"
              @change="handleAreaCoefficientChange"
            />
            <el-button
              type="primary"
              plain
              :loading="suggesting"
              @click="handleGetAreaSuggestion"
            >
              AI 获取建议系数
            </el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="isMaterialCategory && suggestionPreview" label="AI建议结果">
          <div class="ai-suggestion-card">
            <div class="ai-suggestion-value">
              建议系数：{{ formatAreaCoefficient(suggestionPreview.areaCoefficient) }} ㎡/单件
            </div>
            <div class="ai-suggestion-reason">{{ suggestionPreview.reason || '—' }}</div>
            <div class="ai-suggestion-footer">
              <span>{{ suggestionPreview.generatedAt || '—' }}</span>
              <el-button type="primary" link @click="handleApplyAreaSuggestion">应用建议</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入备注"
            @input="handleTextFieldInput('remark')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="impactDialogVisible"
      title="面积系数保存后修正确认"
      width="620px"
      :close-on-click-modal="false"
    >
      <div class="impact-dialog-body">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="当前系数变更将影响历史库存核算、报表统计、AI配比测算、采购计划运算。"
        />
        <div class="impact-dialog-block">
          <div class="impact-dialog-label">生效口径</div>
          <el-radio-group v-model="impactConfirmState.effectScope" class="impact-scope-group">
            <el-radio
              v-for="item in impactScopeOptions"
              :key="item.value"
              :label="item.value"
            >
              <div class="impact-scope-option">
                <div class="impact-scope-title">{{ item.label }}</div>
                <div class="impact-scope-description">{{ item.description }}</div>
              </div>
            </el-radio>
          </el-radio-group>
        </div>
        <div class="impact-dialog-block">
          <el-checkbox v-model="impactConfirmState.acknowledged">
            我已知悉本次面积系数变更将影响历史库存核算、报表统计、AI配比测算、采购计划运算。
          </el-checkbox>
        </div>
      </div>
      <template #footer>
        <el-button @click="impactDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleImpactConfirmSubmit">确认保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style lang="scss" scoped>
.dict-category-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

.tabs-toolbar,
.toolbar,
.pagination {
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;
}

.tabs-toolbar {
  padding: 0 16px;
}

.toolbar {
  padding: 20px;

  .el-input,
  .el-select {
    width: 100%;
  }
}

.toolbar-actions {
  text-align: right;
}

.area-coefficient-field {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-suggestion-card {
  width: 100%;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f5f9ff;
  border: 1px solid #d8e8ff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-suggestion-value {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.ai-suggestion-reason {
  font-size: 13px;
  line-height: 1.6;
  color: $text-regular;
}

.ai-suggestion-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: $text-secondary;
}

.impact-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.impact-dialog-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.impact-dialog-label {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.impact-scope-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.impact-scope-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 2px;
}

.impact-scope-title {
  font-size: 14px;
  color: $text-primary;
}

.impact-scope-description {
  font-size: 12px;
  line-height: 1.6;
  color: $text-secondary;
}

.table-card {
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  flex: 1;
  min-height: 0;
  padding: 16px 16px 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 12px;
}

.pagination {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;

  .total {
    font-size: 14px;
    color: $text-regular;
  }
}

:deep(.category-tabs .el-tabs__header) {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .area-coefficient-field {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
