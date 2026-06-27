<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { supplierApi } from '@/api/modules/supplier'
import { useSupplierStore } from '@/stores/modules/supplier'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import type {
  Supplier,
  SupplierDisablePayload,
  SupplierForm,
  SupplierQualificationFile,
  SupplierStatus
} from '@/types/supplier'

interface Props {
  modelValue: boolean
  supplierId?: number | null
  supplierData?: Supplier | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  supplierId: null,
  supplierData: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const supplierStore = useSupplierStore()
const dictCategoryStore = useDictCategoryStore()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 是否编辑模式 */
const isEdit = computed(() => !!props.supplierId)

/** 弹窗标题 */
const dialogTitle = computed(() => (isEdit.value ? '编辑供应商' : '新增供应商'))

/** 表单引用 */
const formRef = ref<FormInstance>()

/** 提交中状态 */
const submitting = ref(false)

/** 文件上传中状态 */
const uploading = ref(false)
const supplierNameInputRef = ref<any>()
const supplierCodeInputRef = ref<any>()
const unifiedCreditCodeInputRef = ref<any>()
const licenseNoInputRef = ref<any>()
const foodLicenseNoInputRef = ref<any>()
const supplierNameServerError = ref('')
const supplierCodeServerError = ref('')
const unifiedCreditCodeServerError = ref('')
const licenseNoServerError = ref('')
const foodLicenseNoServerError = ref('')
const DUPLICATE_SUPPLIER_NAME_MESSAGE = '当前租户下已存在相同供应商名称，请修改后重新保存'
const DUPLICATE_SUPPLIER_CODE_MESSAGE = '当前租户下已存在相同供应商编码，请修改后重新保存'
const DUPLICATE_UNIFIED_CREDIT_CODE_FIELD_MESSAGE = '该统一社会信用代码已存在，请勿重复录入'
const DUPLICATE_UNIFIED_CREDIT_CODE_SAVE_MESSAGE = '该统一社会信用代码已存在，不可重复保存'
const DUPLICATE_LICENSE_NO_FIELD_MESSAGE = '当前租户下存在相同营业执照编号的有效供应商，请修改后重试'
const DUPLICATE_LICENSE_NO_SAVE_MESSAGE = '当前租户下存在相同营业执照编号的有效供应商，请修改后重试'
const DUPLICATE_FOOD_LICENSE_NO_FIELD_MESSAGE = '当前租户下存在相同食品许可证号的有效供应商，请修改后重试'
const DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE = '当前租户下存在相同食品许可证号的有效供应商，请修改后重试'

/** 供应商启用状态 */
type SupplierEnabledState = 'enabled' | 'disabled'

const supplierEnabled = ref<SupplierEnabledState>('enabled')
const SUPPLIER_ENABLED_CACHE_KEY = 'smartfood:supplier-enabled-state'

const saveLockedStatuses: SupplierStatus[] = ['active', 'pending', 'rejected', 'disabled']
const persistedFileUrls = ref<string[]>([])
const skipTransientCleanup = ref(false)
const MAX_QUALIFICATION_FILE_SIZE = 10 * 1024 * 1024
const MAX_QUALIFICATION_FILE_SIZE_TEXT = '10MB'
const ALLOWED_QUALIFICATION_FILE_EXTENSIONS = new Set(['pdf', 'jpg', 'jpeg', 'png', 'doc', 'docx', 'xls', 'xlsx'])
const ALLOWED_QUALIFICATION_FILE_MESSAGE = '仅支持 PDF、JPG、PNG、DOC、DOCX、XLS、XLSX 格式的文件'

/** 当前编辑的原始状态 */
const currentStatus = computed<SupplierStatus>(() => props.supplierData?.status || formData.status || 'draft')

/** 当前状态下禁止保存为暂存 */
const saveLocked = computed(() => isEdit.value && saveLockedStatuses.includes(currentStatus.value))

/** 已禁用/已注销供应商不允许编辑 */
const readOnlyEdit = computed(() => isEdit.value && ['disabled', 'cancelled'].includes(currentStatus.value))

/** 编辑状态下切换为禁用后，不允许再保存为暂存 */
const disableSelectedInEdit = computed(() => isEdit.value && !readOnlyEdit.value && supplierEnabled.value === 'disabled')

/** 保存按钮显示控制 */
const showSaveButton = computed(() => !readOnlyEdit.value && !saveLocked.value && !disableSelectedInEdit.value)

const saveLockedAlertText = computed(() => {
  if (disableSelectedInEdit.value) {
    return '当前已选择禁用，仅可提交或取消，提交时必须填写禁用原因。'
  }
  return '当前状态供应商不允许保存为暂存，只能提交或取消。'
})

/** 构建表单数据，确保控件初次渲染时已拿到最终值 */
function createFormData(data?: Supplier | null): SupplierForm {
  const defaultFormData = getDefaultFormData()
  if (!data) {
    return defaultFormData
  }
  return {
    ...defaultFormData,
    supplierCode: data.supplierCode || defaultFormData.supplierCode,
    supplierName: data.supplierName,
    contactName: data.contactName,
    contactPhone: data.contactPhone,
    contactEmail: data.contactEmail,
    address: data.address,
    supplierType: normalizeSupplierType(data.supplierType),
    unifiedCreditCode: data.unifiedCreditCode || '',
    bankAccount: data.bankAccount || '',
    bankName: data.bankName || '',
    licenseNo: data.licenseNo || '',
    licenseExpiresAt: data.licenseExpiresAt,
    foodLicenseNo: data.foodLicenseNo || '',
    foodLicenseExpiresAt: data.foodLicenseExpiresAt,
    status: data.status,
    disableReason: data.disableReason ?? null,
    qualificationFiles: [...(data.qualificationFiles || [])]
  }
}

/** 表单数据 */
const formData = reactive<SupplierForm>(createFormData(props.supplierData))

/** 供应商类型选项，兼容历史数据中的自定义类型 */
const supplierTypeOptions = computed(() => {
  const currentType = normalizeSupplierType(formData.supplierType)
  const activeOptions = dictCategoryStore.getCachedOptions('supplier_type').map((item) => item.value)
  if (!props.supplierId || !currentType || activeOptions.includes(currentType)) {
    return activeOptions
  }
  return [currentType, ...activeOptions]
})

/** 表单验证规则 */
const formRules: FormRules = {
  supplierName: [
    { required: true, message: '请输入供应商名称', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (supplierNameServerError.value) {
          callback(new Error(supplierNameServerError.value))
          return
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ],
  supplierCode: [
    { required: true, message: '请输入供应商编码', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (supplierCodeServerError.value) {
          callback(new Error(supplierCodeServerError.value))
          return
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  unifiedCreditCode: [
    { required: true, message: '请输入社会信用代码', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (unifiedCreditCodeServerError.value) {
          callback(new Error(unifiedCreditCodeServerError.value))
          return
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ],
  contactPhone: [
    {
      required: true,
      message: '请输入联系电话',
      trigger: 'blur'
    },
    {
      pattern: /^$|^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur'
    }
  ],
  bankAccount: [{ required: true, message: '请输入银行账号', trigger: 'blur' }],
  bankName: [{ required: true, message: '请输入开户行', trigger: 'blur' }],
  licenseNo: [
    { required: true, message: '请输入营业执照编号', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (licenseNoServerError.value) {
          callback(new Error(licenseNoServerError.value))
          return
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ],
  licenseExpiresAt: [
    { required: true, message: '请输入执照到期日', trigger: 'change' }
  ],
  foodLicenseNo: [
    {
      validator: (_rule, _value, callback) => {
        if (foodLicenseNoServerError.value) {
          callback(new Error(foodLicenseNoServerError.value))
          return
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ],
  contactEmail: [
    {
      pattern: /^$|^[\w.-]+@[\w.-]+\.\w+$/,
      message: '请输入正确的邮箱地址',
      trigger: 'blur'
    }
  ]
}

/** 获取默认表单数据 */
function getDefaultFormData(): SupplierForm {
  return {
    supplierCode: 'SUP-' + String(Date.now()).slice(-4),
    supplierName: '',
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    address: '',
    supplierType: '',
    unifiedCreditCode: '',
    bankAccount: '',
    bankName: '',
    licenseNo: '',
    licenseExpiresAt: null,
    foodLicenseNo: '',
    foodLicenseExpiresAt: null,
    status: 'draft',
    disableReason: null,
    qualificationFiles: []
  }
}

/** 规范化供应商类型，避免历史数据带空格导致回显失败 */
function normalizeSupplierType(value?: string | null) {
  return value?.trim() || ''
}

function getSupplierEnabledCacheKey(supplierCode?: string | null) {
  const normalizedSupplierCode = supplierCode?.trim()
  return normalizedSupplierCode || null
}

function readSupplierEnabledCache(): Record<string, SupplierEnabledState> {
  if (typeof window === 'undefined') {
    return {}
  }
  try {
    const raw = window.sessionStorage.getItem(SUPPLIER_ENABLED_CACHE_KEY)
    if (!raw) {
      return {}
    }
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') {
      return {}
    }

    return Object.entries(parsed).reduce<Record<string, SupplierEnabledState>>((acc, [key, value]) => {
      if (value === 'enabled' || value === 'disabled') {
        acc[key] = value
      }
      return acc
    }, {})
  } catch {
    return {}
  }
}

function writeSupplierEnabledCache(cache: Record<string, SupplierEnabledState>) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.sessionStorage.setItem(SUPPLIER_ENABLED_CACHE_KEY, JSON.stringify(cache))
  } catch {
    // sessionStorage 不可用时忽略，避免影响已有业务流程
  }
}

function cacheSupplierEnabledState(supplierCode?: string | null) {
  const cacheKey = getSupplierEnabledCacheKey(supplierCode)
  if (!cacheKey) {
    return
  }

  const cache = readSupplierEnabledCache()
  cache[cacheKey] = supplierEnabled.value
  writeSupplierEnabledCache(cache)
}

function resolveSupplierEnabledState(data?: Supplier | null): SupplierEnabledState {
  if (data?.status === 'disabled') {
    return 'disabled'
  }

  const cacheKey = getSupplierEnabledCacheKey(data?.supplierCode)
  if (cacheKey) {
    const cachedState = readSupplierEnabledCache()[cacheKey]
    if (cachedState) {
      return cachedState
    }
  }

  if (data?.status === 'draft' && data.disableReason) {
    return 'disabled'
  }

  return 'enabled'
}

/** 填充表单数据（编辑回填） */
function fillFormData(data?: Supplier | null) {
  Object.assign(formData, createFormData(data))
  clearAllDuplicateServerErrors()
  supplierEnabled.value = resolveSupplierEnabledState(data)
  persistedFileUrls.value = (data?.qualificationFiles || [])
    .map((file) => file.url)
    .filter((url): url is string => !!url)
}

/** 重置表单数据 */
function resetFormData() {
  fillFormData(null)
}

function clearSupplierNameServerError() {
  if (!supplierNameServerError.value) {
    return
  }
  supplierNameServerError.value = ''
  formRef.value?.clearValidate?.(['supplierName'])
}

function clearSupplierCodeServerError() {
  if (!supplierCodeServerError.value) {
    return
  }
  supplierCodeServerError.value = ''
  formRef.value?.clearValidate?.(['supplierCode'])
}

function clearUnifiedCreditCodeServerError() {
  if (!unifiedCreditCodeServerError.value) {
    return
  }
  unifiedCreditCodeServerError.value = ''
  formRef.value?.clearValidate?.(['unifiedCreditCode'])
}

function clearLicenseNoServerError() {
  if (!licenseNoServerError.value) {
    return
  }
  licenseNoServerError.value = ''
  formRef.value?.clearValidate?.(['licenseNo'])
}

function clearFoodLicenseNoServerError() {
  if (!foodLicenseNoServerError.value) {
    return
  }
  foodLicenseNoServerError.value = ''
  formRef.value?.clearValidate?.(['foodLicenseNo'])
}

function clearAllDuplicateServerErrors() {
  supplierNameServerError.value = ''
  supplierCodeServerError.value = ''
  unifiedCreditCodeServerError.value = ''
  licenseNoServerError.value = ''
  foodLicenseNoServerError.value = ''
  formRef.value?.clearValidate?.(['supplierName', 'supplierCode', 'unifiedCreditCode', 'licenseNo', 'foodLicenseNo'])
}

/** 仅在供应商上下文变化时同步表单，避免打开弹窗后再异步回填 */
watch(
  () => props.modelValue,
  async (val) => {
    if (!val) {
      return
    }

    await dictCategoryStore.fetchOptions('supplier_type', false, true)
    await nextTick()

    skipTransientCleanup.value = false
    if (props.supplierId && props.supplierData) {
      fillFormData(props.supplierData)
      return
    }

    if (!props.supplierId) {
      resetFormData()
    }
  },
  {
    immediate: true
  }
)

watch(
  [() => props.supplierId, () => props.supplierData],
  ([supplierId, supplierData]) => {
    skipTransientCleanup.value = false
    if (supplierId && supplierData) {
      fillFormData(supplierData)
      return
    }

    if (!supplierId) {
      resetFormData()
    }
  },
  {
    immediate: true,
    deep: true
  }
)

watch(
  () => formData.supplierName,
  () => {
    clearSupplierNameServerError()
  }
)

watch(
  () => formData.supplierCode,
  () => {
    clearSupplierCodeServerError()
  }
)

watch(
  () => formData.unifiedCreditCode,
  () => {
    clearUnifiedCreditCodeServerError()
  }
)

watch(
  () => formData.licenseNo,
  () => {
    clearLicenseNoServerError()
  }
)

watch(
  () => formData.foodLicenseNo,
  () => {
    clearFoodLicenseNoServerError()
  }
)

const isPersistedFile = (file: SupplierQualificationFile) => {
  return !!file.url && persistedFileUrls.value.includes(file.url)
}

const deleteTemporaryFile = async (file: SupplierQualificationFile) => {
  if (!file.url || isPersistedFile(file)) {
    return
  }
  try {
    await supplierApi.deleteQualificationFile(file.url, file.name)
  } catch {
    // 统一错误提示已由请求拦截器处理
  }
}

const cleanupTransientFiles = async () => {
  const transientFiles = formData.qualificationFiles.filter((file) => file.url && !isPersistedFile(file))
  if (!transientFiles.length) {
    return
  }
  await Promise.allSettled(transientFiles.map((file) => deleteTemporaryFile(file)))
}

const validateQualificationFileSize = (file: File) => {
  if (file.size > MAX_QUALIFICATION_FILE_SIZE) {
    ElMessage.warning(`文件大小不能超过${MAX_QUALIFICATION_FILE_SIZE_TEXT}，请重新选择`)
    return false
  }
  return true
}

const validateQualificationFileType = (file: File) => {
  const extension = file.name.split('.').pop()?.trim().toLowerCase() || ''
  if (!extension || !ALLOWED_QUALIFICATION_FILE_EXTENSIONS.has(extension)) {
    ElMessage.warning(ALLOWED_QUALIFICATION_FILE_MESSAGE)
    return false
  }
  return true
}

/** 处理文件选择 */
const handleFileChange = async (event: Event) => {
  if (readOnlyEdit.value) return
  const input = event.target as HTMLInputElement
  if (!input.files) return

  uploading.value = true
  for (const file of Array.from(input.files)) {
    if (!validateQualificationFileType(file)) {
      continue
    }
    if (!validateQualificationFileSize(file)) {
      continue
    }
    const alreadyExists = formData.qualificationFiles.some((f) => f.name === file.name)
    if (alreadyExists) {
      ElMessage.warning(`文件 "${file.name}" 已添加`)
      continue
    }

    try {
      const res = await supplierApi.uploadQualificationFile(file)
      if (res.code === 'SUCCESS' && res.data) {
        formData.qualificationFiles.push(res.data)
      }
    } catch {
      // 统一错误提示已由请求拦截器处理
    }
  }

  uploading.value = false
  // 清空 input，允许重复选同一文件
  input.value = ''
}

const normalizeSupplierDuplicateFields = () => {
  formData.supplierName = formData.supplierName.trim()
  formData.supplierCode = formData.supplierCode.trim()
  formData.licenseNo = formData.licenseNo.trim()
  formData.foodLicenseNo = formData.foodLicenseNo.trim()
}

const focusDuplicateField = async (
  field: 'supplierName' | 'supplierCode' | 'unifiedCreditCode' | 'licenseNo' | 'foodLicenseNo',
  inputRef: typeof unifiedCreditCodeInputRef
) => {
  await nextTick()
  const form = formRef.value
  if (form?.validateField) {
    try {
      await form.validateField(field)
    } catch {
      // 仅用于触发字段高亮，不影响原有提交流程
    }
  }
  await nextTick()
  inputRef.value?.focus?.()
}

const syncDuplicateCheckResult = async () => {
  const supplierCode = formData.supplierCode.trim()
  const supplierName = formData.supplierName.trim()
  const licenseNo = formData.licenseNo.trim()
  const foodLicenseNo = formData.foodLicenseNo.trim()

  if (!supplierCode && !supplierName && !licenseNo && !foodLicenseNo) {
    clearSupplierNameServerError()
    clearSupplierCodeServerError()
    clearLicenseNoServerError()
    clearFoodLicenseNoServerError()
    return true
  }

  try {
    const res = await supplierApi.checkDuplicate(
      {
        excludeId: props.supplierId || undefined,
        supplierCode: supplierCode || undefined,
        supplierName: supplierName || undefined,
        licenseNo: licenseNo || undefined,
        foodLicenseNo: foodLicenseNo || undefined
      },
      { silentError: true }
    )

    if (res.code === 'SUCCESS' && res.data) {
      supplierCodeServerError.value = res.data.supplierCodeDuplicate
        ? (res.data.supplierCodeMessage || DUPLICATE_SUPPLIER_CODE_MESSAGE)
        : ''
      supplierNameServerError.value = res.data.supplierNameDuplicate
        ? (res.data.supplierNameMessage || DUPLICATE_SUPPLIER_NAME_MESSAGE)
        : ''
      licenseNoServerError.value = res.data.licenseNoDuplicate
        ? (res.data.licenseNoMessage || DUPLICATE_LICENSE_NO_FIELD_MESSAGE)
        : ''
      foodLicenseNoServerError.value = res.data.foodLicenseNoDuplicate
        ? (res.data.foodLicenseNoMessage || DUPLICATE_FOOD_LICENSE_NO_FIELD_MESSAGE)
        : ''

      await nextTick()
      await Promise.allSettled([
        formRef.value?.validateField?.('supplierCode'),
        formRef.value?.validateField?.('supplierName'),
        formRef.value?.validateField?.('licenseNo'),
        formRef.value?.validateField?.('foodLicenseNo')
      ])
      return !res.data.supplierCodeDuplicate
        && !res.data.supplierNameDuplicate
        && !res.data.licenseNoDuplicate
        && !res.data.foodLicenseNoDuplicate
    }
  } catch {
    return true
  }

  return true
}

const handleSupplierNameBlur = async () => {
  normalizeSupplierDuplicateFields()
  if (!formData.supplierName) {
    clearSupplierNameServerError()
    return
  }
  await syncDuplicateCheckResult()
}

const handleSupplierCodeBlur = async () => {
  normalizeSupplierDuplicateFields()
  if (!formData.supplierCode) {
    clearSupplierCodeServerError()
    return
  }
  await syncDuplicateCheckResult()
}

const handleLicenseNoBlur = async () => {
  normalizeSupplierDuplicateFields()
  if (!formData.licenseNo) {
    clearLicenseNoServerError()
    return
  }
  await syncDuplicateCheckResult()
}

const handleFoodLicenseNoBlur = async () => {
  normalizeSupplierDuplicateFields()
  if (!formData.foodLicenseNo) {
    clearFoodLicenseNoServerError()
    return
  }
  await syncDuplicateCheckResult()
}

const applyDuplicateFieldError = async (message?: string) => {
  if (message === DUPLICATE_SUPPLIER_NAME_MESSAGE) {
    supplierNameServerError.value = DUPLICATE_SUPPLIER_NAME_MESSAGE
    await focusDuplicateField('supplierName', supplierNameInputRef)
    return true
  }
  if (message === DUPLICATE_SUPPLIER_CODE_MESSAGE) {
    supplierCodeServerError.value = DUPLICATE_SUPPLIER_CODE_MESSAGE
    await focusDuplicateField('supplierCode', supplierCodeInputRef)
    return true
  }
  if (message === DUPLICATE_UNIFIED_CREDIT_CODE_SAVE_MESSAGE) {
    unifiedCreditCodeServerError.value = DUPLICATE_UNIFIED_CREDIT_CODE_FIELD_MESSAGE
    await focusDuplicateField('unifiedCreditCode', unifiedCreditCodeInputRef)
    return true
  }
  if (message === DUPLICATE_LICENSE_NO_SAVE_MESSAGE) {
    licenseNoServerError.value = DUPLICATE_LICENSE_NO_FIELD_MESSAGE
    await focusDuplicateField('licenseNo', licenseNoInputRef)
    return true
  }
  if (message === DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE) {
    foodLicenseNoServerError.value = DUPLICATE_FOOD_LICENSE_NO_FIELD_MESSAGE
    await focusDuplicateField('foodLicenseNo', foodLicenseNoInputRef)
    return true
  }
  return false
}

/** 删除已选文件 */
const removeFile = async (index: number) => {
  if (readOnlyEdit.value) return
  const file = formData.qualificationFiles[index]
  if (!file) return
  await deleteTemporaryFile(file)
  formData.qualificationFiles.splice(index, 1)
}

/** 统一构建提交参数 */
const buildPayload = (status: SupplierStatus): SupplierForm => ({
  ...formData,
  supplierType: normalizeSupplierType(formData.supplierType),
  foodLicenseExpiresAt: formData.foodLicenseExpiresAt || null,
  qualificationFiles: [...formData.qualificationFiles],
  status
})

/** 采集禁用原因 */
const requestDisableReason = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请填写禁用原因', '禁用供应商', {
      inputType: 'textarea',
      inputPlaceholder: '请输入禁用原因',
      inputValidator: (input) => input.trim() ? true : '禁用原因不能为空',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    return value.trim()
  } catch {
    return null
  }
}

/** 按状态提交表单 */
const submitByStatus = async (status: 'draft' | 'pending') => {
  if (readOnlyEdit.value) return
  if (!formRef.value) return
  if (uploading.value) {
    ElMessage.warning('文件上传中，请稍候再提交')
    return
  }
  try {
    normalizeSupplierDuplicateFields()
    clearAllDuplicateServerErrors()
    await formRef.value.validate()
  } catch {
    return
  }

  const duplicateCheckPassed = await syncDuplicateCheckResult()
  if (!duplicateCheckPassed) {
    return
  }

  submitting.value = true
  try {
    const submitStatus =
      status === 'draft' ? 'draft' : supplierEnabled.value === 'disabled' ? 'disabled' : 'pending'

    if (submitStatus === 'disabled') {
      const reason = await requestDisableReason()
      if (!reason) return

      if (isEdit.value && props.supplierId) {
        const payload: SupplierDisablePayload = {
          ...buildPayload('disabled'),
          disableReason: reason,
          reason
        }
        const result = await supplierStore.disableSupplier(props.supplierId, payload)
        if (result.success) {
          emit('success')
          closeAfterSave()
        } else {
          await applyDuplicateFieldError(result.errorMessage)
        }
        return
      }

      const createPayload: SupplierForm = {
        ...buildPayload('disabled'),
        disableReason: reason
      }
      const result = await supplierStore.addSupplier(createPayload)
      if (result.success) {
        ElMessage.success('提交成功，供应商已禁用')
        emit('success')
        closeAfterSave()
        return
      }
      await applyDuplicateFieldError(result.errorMessage)
      return
    }

    const payload: SupplierForm = {
      ...buildPayload(submitStatus),
      disableReason: null
    }
    const successMessage =
      status === 'draft'
        ? '暂存成功'
        : '提交成功，等待审核'
    if (isEdit.value && props.supplierId) {
      const result = await supplierStore.updateSupplier(props.supplierId, payload)
      if (result.success) {
        if (submitStatus === 'draft') {
          cacheSupplierEnabledState(formData.supplierCode)
        }
        ElMessage.success(successMessage)
        emit('success')
        closeAfterSave()
      } else {
        await applyDuplicateFieldError(result.errorMessage)
      }
    } else {
      const result = await supplierStore.addSupplier(payload)
      if (result.success) {
        if (submitStatus === 'draft') {
          cacheSupplierEnabledState(formData.supplierCode)
        }
        ElMessage.success(successMessage)
        emit('success')
        closeAfterSave()
      } else {
        await applyDuplicateFieldError(result.errorMessage)
      }
    }
  } finally {
    submitting.value = false
  }
}

/** 保存（暂存） */
const handleSave = () => submitByStatus('draft')

/** 提交（待审核） */
const handleSubmit = () => submitByStatus('pending')

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}

const closeAfterSave = () => {
  skipTransientCleanup.value = true
  handleClose()
}

const handleCancel = async () => {
  if (uploading.value) {
    ElMessage.warning('文件上传中，请稍候再关闭')
    return
  }
  await cleanupTransientFiles()
  handleClose()
}

const handleBeforeClose = async (done: () => void) => {
  if (skipTransientCleanup.value) {
    skipTransientCleanup.value = false
    done()
    return
  }
  if (uploading.value) {
    ElMessage.warning('文件上传中，请稍候再关闭')
    return
  }
  await cleanupTransientFiles()
  done()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="760px"
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :disabled="readOnlyEdit"
      label-width="120px"
    >
      <el-alert
        v-if="saveLocked || disableSelectedInEdit"
        :title="saveLockedAlertText"
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-row :gutter="20">
        <!-- ── 基本信息 ── -->
        <el-col :span="24">
          <div class="form-section-title">基本信息</div>
        </el-col>

        <!-- 供应商名称 -->
        <el-col :span="12">
          <el-form-item label="供应商名称" prop="supplierName">
            <el-input
              ref="supplierNameInputRef"
              v-model="formData.supplierName"
              placeholder="请输入供应商名称"
              maxlength="100"
              @blur="handleSupplierNameBlur"
            />
          </el-form-item>
        </el-col>

        <!-- 供应商编码 -->
        <el-col :span="12">
          <el-form-item label="供应商编码" prop="supplierCode">
            <el-input
              ref="supplierCodeInputRef"
              v-model="formData.supplierCode"
              :readonly="isEdit"
              placeholder="如：SUP-001"
              maxlength="50"
              @blur="handleSupplierCodeBlur"
            />
          </el-form-item>
        </el-col>

        <!-- 社会信用代码 -->
        <el-col :span="12">
          <el-form-item label="社会信用代码" prop="unifiedCreditCode">
            <el-input
              ref="unifiedCreditCodeInputRef"
              v-model="formData.unifiedCreditCode"
              placeholder="请输入社会信用代码"
              maxlength="50"
            />
          </el-form-item>
        </el-col>

        <!-- 供应商类型（单选下拉） -->
        <el-col :span="12">
          <el-form-item label="供应商类型">
            <el-select
              v-model="formData.supplierType"
              placeholder="请选择供应商类型"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="t in supplierTypeOptions"
                :key="t"
                :label="t"
                :value="t"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <!-- 启用状态 -->
        <el-col :span="12">
          <el-form-item label="启用状态">
            <el-radio-group v-model="supplierEnabled">
              <el-radio value="enabled">启用</el-radio>
              <el-radio value="disabled">禁用</el-radio>
            </el-radio-group>
            <div v-if="supplierEnabled === 'disabled'" class="status-tip">提交时必须填写禁用原因。</div>
          </el-form-item>
        </el-col>

        <!-- 地址 -->
        <el-col :span="24">
          <el-form-item label="地址">
            <el-input v-model="formData.address" placeholder="请输入供应商地址" maxlength="200" />
          </el-form-item>
        </el-col>

        <!-- ── 联系信息 ── -->
        <el-col :span="24">
          <div class="form-section-title">联系信息</div>
        </el-col>

        <!-- 联系人 -->
        <el-col :span="12">
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="formData.contactName" placeholder="请输入联系人姓名" maxlength="50" />
          </el-form-item>
        </el-col>

        <!-- 联系电话 -->
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" maxlength="20" />
          </el-form-item>
        </el-col>

        <!-- 联系邮箱 -->
        <el-col :span="12">
          <el-form-item label="联系邮箱" prop="contactEmail">
            <el-input v-model="formData.contactEmail" placeholder="请输入联系邮箱" maxlength="100" />
          </el-form-item>
        </el-col>

        <!-- ── 银行信息 ── -->
        <el-col :span="24">
          <div class="form-section-title">银行信息</div>
        </el-col>

        <!-- 银行账号 -->
        <el-col :span="12">
          <el-form-item label="银行账号" prop="bankAccount">
            <el-input v-model="formData.bankAccount" placeholder="请输入银行账号" maxlength="50" />
          </el-form-item>
        </el-col>

        <!-- 开户行 -->
        <el-col :span="12">
          <el-form-item label="开户行" prop="bankName">
            <el-input v-model="formData.bankName" placeholder="请输入开户行" maxlength="100" />
          </el-form-item>
        </el-col>

        <!-- ── 资质信息 ── -->
        <el-col :span="24">
          <div class="form-section-title">资质信息</div>
        </el-col>

        <!-- 营业执照编号 -->
        <el-col :span="12">
          <el-form-item label="营业执照编号" prop="licenseNo">
            <el-input
              ref="licenseNoInputRef"
              v-model="formData.licenseNo"
              placeholder="请输入营业执照编号"
              maxlength="50"
              @blur="handleLicenseNoBlur"
            />
          </el-form-item>
        </el-col>

        <!-- 营业执照到期日 -->
        <el-col :span="12">
          <el-form-item label="执照到期日" prop="licenseExpiresAt">
            <el-date-picker
              v-model="formData.licenseExpiresAt"
              type="date"
              placeholder="请选择到期日"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <!-- 食品经营许可证编号 -->
        <el-col :span="12">
          <el-form-item label="食品许可证号" prop="foodLicenseNo">
            <el-input
              ref="foodLicenseNoInputRef"
              v-model="formData.foodLicenseNo"
              placeholder="请输入食品经营许可证编号"
              maxlength="50"
              @blur="handleFoodLicenseNoBlur"
            />
          </el-form-item>
        </el-col>

        <!-- 食品许可证到期日 -->
        <el-col :span="12">
          <el-form-item label="食品证到期日" prop="foodLicenseExpiresAt">
            <el-date-picker
              v-model="formData.foodLicenseExpiresAt"
              type="date"
              placeholder="请选择到期日"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <!-- ── 资质文件上传 ── -->
        <el-col :span="24">
          <div class="form-section-title">资质文件上传</div>
        </el-col>

        <el-col :span="24">
          <el-form-item label="上传文件">
            <div class="upload-area">
              <!-- 触发选择文件 -->
              <label class="upload-btn" for="qualFileInput">
                <el-icon><Upload /></el-icon>
                <span>选择文件</span>
              </label>
              <input
                id="qualFileInput"
                type="file"
                multiple
                accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx"
                :disabled="readOnlyEdit"
                style="display: none"
                @change="handleFileChange"
              />
              <span class="upload-tip">支持 PDF、图片、Word、Excel，单次可多选，单个文件不超过10MB</span>
            </div>

            <!-- 已选文件列表 -->
            <div v-if="formData.qualificationFiles.length" class="file-list">
              <div
                v-for="(file, index) in formData.qualificationFiles"
                :key="file.id"
                class="file-item"
              >
                <el-icon class="file-icon"><Document /></el-icon>
                <span class="file-name">{{ file.name }}</span>
                <span class="file-size">{{ file.size }}</span>
                <el-icon v-if="!readOnlyEdit" class="file-remove" @click="removeFile(index)"><Close /></el-icon>
              </div>
            </div>
            <div v-else class="file-empty">暂未上传任何文件</div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button v-if="showSaveButton" :disabled="submitting || uploading" @click="handleSave">保存</el-button>
      <el-button v-if="!readOnlyEdit" type="primary" :loading="submitting" :disabled="uploading" @click="handleSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
/** 分区小标题 */
.form-section-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  padding: 4px 0 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid $border-lighter;
  margin-left: 0;
}

/** 上传区域 */
.upload-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
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

.upload-tip {
  font-size: 12px;
  color: $text-secondary;
}

.status-tip {
  margin-top: 6px;
  font-size: 12px;
  color: $warning-color;
}

/** 文件列表 */
.file-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  background: $bg-base;
  border-radius: $border-radius-base;
  border: 1px solid $border-lighter;

  .file-icon {
    color: $primary-color;
    font-size: 16px;
    flex-shrink: 0;
  }

  .file-name {
    flex: 1;
    font-size: 13px;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-size {
    font-size: 12px;
    color: $text-secondary;
    flex-shrink: 0;
  }

  .file-remove {
    font-size: 14px;
    color: $text-secondary;
    cursor: pointer;
    flex-shrink: 0;
    transition: color 0.2s;

    &:hover {
      color: $danger-color;
    }
  }
}

.file-empty {
  margin-top: 8px;
  font-size: 12px;
  color: $text-secondary;
}
</style>
