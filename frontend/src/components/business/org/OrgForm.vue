<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { Organization, OrgForm, OrgTreeNode } from '@/types/org'
import { ORG_STATUS_OPTIONS, ORG_TYPE_MAP } from '@/constants/org'
import { orgApi } from '@/api/modules/org'
import { useOrgStore } from '@/stores/modules/org'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildActiveDictOptions } from '@/utils/dict-category'
import OrgTreeSelect from './OrgTreeSelect.vue'

interface Props {
  modelValue: boolean
  orgId?: number | null
  orgData?: Organization | null
  presetParentId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  orgId: null,
  orgData: null,
  presetParentId: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const orgStore = useOrgStore()
const dictCategoryStore = useDictCategoryStore()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 是否编辑模式 */
const isEdit = computed(() => !!props.orgId)

/** 弹窗标题 */
const dialogTitle = computed(() => isEdit.value ? '编辑组织' : '新增组织')

/** 表单引用 */
const formRef = ref()

/** 提交中 */
const submitting = ref(false)

/** 上级组织不可见时缓存原始 parentId（用于不改上级时原样提交） */
const hiddenParentId = ref<number | null>(null)

/** 是否手动改动过上级组织选择 */
const parentSelectionTouched = ref(false)

/** 是否在程序化回填 parentId，避免误判为用户手动修改 */
const syncingParentId = ref(false)

/** 表单数据 */
const formData = ref<OrgForm>(getDefaultFormData())

const orgTypeOptions = computed(() => {
  return buildActiveDictOptions(
    dictCategoryStore.getCachedOptions('org_type'),
    props.orgData ? formData.value.orgType : undefined,
    dictCategoryStore.getCachedOptions('org_type', true),
    ORG_TYPE_MAP[formData.value.orgType]?.label || formData.value.orgType
  )
})

/** 表单验证规则 */
const formRules = {
  orgCode: [
    { required: true, message: '请输入组织编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '编码只能包含字母、数字、下划线和横线', trigger: 'blur' }
  ],
  orgName: [
    { required: true, message: '请输入组织名称', trigger: 'blur' }
  ],
  orgType: [
    { required: true, message: '请选择组织类型', trigger: 'change' }
  ],
  contactPhone: [
    { pattern: /^$|^1[3-9]\d{9}$|^0\d{2,3}-?\d{7,8}$/, message: '请输入正确的电话号码', trigger: 'blur' }
  ]
}

/** 是否有权限查看当前组织的上级组织 */
const parentOrgOutOfScope = computed(() => {
  if (!isEdit.value || !props.orgData) {
    return false
  }
  const parentId = props.orgData.parentId
  if (!parentId || parentId <= 0) {
    return false
  }
  return !isOrgInTree(parentId)
})

/** 判断组织是否在当前可见组织树中 */
function isOrgInTree(targetId: number, nodes: OrgTreeNode[] = orgStore.allTreeData): boolean {
  for (const node of nodes) {
    if (node.id === targetId) {
      return true
    }
    if (node.children?.length && isOrgInTree(targetId, node.children)) {
      return true
    }
  }
  return false
}

/** 获取默认表单数据 */
function getDefaultFormData(): OrgForm {
  return {
    orgCode: generateOrgCode(),
    orgName: '',
    orgType: 'dept',
    parentId: null,
    leaderName: '',
    contactPhone: '',
    address: '',
    status: 'active',
    sortOrder: 0
  }
}

/** 生成组织编码 */
function generateOrgCode(): string {
  return 'ORG-' + String(Date.now()).slice(-6)
}

/** 监听弹窗显示状态，回填表单 */
watch(
  () => props.modelValue,
  async (val) => {
    if (val) {
      await dictCategoryStore.fetchOptions('org_type', false, true)
      await orgStore.fetchAllTree()
      await nextTick()
      if (props.orgData) {
        fillFormData(props.orgData)
      } else if (!props.orgId) {
        formData.value = getDefaultFormData()
        hiddenParentId.value = null
        parentSelectionTouched.value = false
        // 如果有预设的父组织ID，则设置
        if (props.presetParentId) {
          formData.value.parentId = props.presetParentId
        }
      }
    }
  },
  { immediate: true }
)

/** 监听组织数据变化 */
watch(
  () => props.orgData,
  (data) => {
    if (visible.value && data) {
      fillFormData(data)
    }
  },
  { deep: true }
)

watch(
  () => formData.value.parentId,
  () => {
    if (visible.value && !syncingParentId.value) {
      parentSelectionTouched.value = true
    }
  }
)

/** 填充表单数据 */
function fillFormData(data: Organization) {
  parentSelectionTouched.value = false

  const originalParentId = data.parentId && data.parentId > 0 ? data.parentId : null
  const parentVisible = originalParentId ? isOrgInTree(originalParentId) : true
  hiddenParentId.value = originalParentId && !parentVisible ? originalParentId : null

  syncingParentId.value = true
  formData.value = {
    orgCode: data.orgCode,
    orgName: data.orgName,
    orgType: data.orgType,
    // 上级组织无权限时，不在控件中显示 ID，保存时再回填 hiddenParentId
    parentId: parentVisible ? originalParentId : null,
    leaderName: data.leaderName,
    contactPhone: data.contactPhone,
    address: data.address,
    status: data.status,
    sortOrder: data.sortOrder
  }
  nextTick(() => {
    syncingParentId.value = false
  })
}

/** 提交表单 */
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate()

  try {
    submitting.value = true
    // 构建提交数据，将 null 转换为 0 表示无上级组织
    // 若上级组织无权限且用户未改动上级组织，则保留原始 parentId；改动后按新值提交
    const resolvedParentId = parentSelectionTouched.value
      ? formData.value.parentId
      : (formData.value.parentId ?? hiddenParentId.value)
    const submitData = {
      ...formData.value,
      parentId: resolvedParentId || 0
    }
    if (isEdit.value && props.orgId) {
      await orgApi.update(props.orgId, submitData)
      ElMessage.success('编辑成功')
    } else {
      await orgApi.create(submitData)
      ElMessage.success('新增成功')
    }
    emit('success')
    handleClose()
  } catch {
    // 错误消息已在 API 拦截器中显示，此处不再重复
  } finally {
    submitting.value = false
  }
}

/** 关闭弹窗 */
const handleClose = () => {
  formRef.value?.resetFields()
  formData.value = getDefaultFormData()
  hiddenParentId.value = null
  parentSelectionTouched.value = false
  syncingParentId.value = false
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="680px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    @close="handleClose"
    class="org-form-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ dialogTitle }}</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      label-suffix="："
    >
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="组织名称" prop="orgName">
            <el-input v-model="formData.orgName" placeholder="请输入组织名称" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="组织编码" prop="orgCode">
            <el-input
              v-model="formData.orgCode"
              :readonly="isEdit"
              placeholder="自动生成或手动输入"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="组织类型" prop="orgType">
            <el-select v-model="formData.orgType" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="item in orgTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上级组织">
            <OrgTreeSelect
              v-model="formData.parentId"
              :exclude-id="orgId"
              :active-only="true"
              placeholder="请选择上级组织"
            />
            <div v-if="parentOrgOutOfScope" class="field-warning">
              当前上级组织无数据权限，已隐藏原值，请重新选择可见上级组织。
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="负责人">
            <el-input v-model="formData.leaderName" placeholder="请输入负责人姓名" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序">
            <el-input-number
              v-model="formData.sortOrder"
              :min="0"
              :step="1"
              :precision="0"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-radio-group v-model="formData.status">
              <el-radio
                v-for="item in ORG_STATUS_OPTIONS"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="地址">
            <el-input v-model="formData.address" placeholder="请输入详细地址" maxlength="200" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-save" :loading="submitting" @click="handleSubmit">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.org-form-dialog.el-dialog {
  width: 680px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.org-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.org-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px 12px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.org-form-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>

<style lang="scss" scoped>
/* ---- 头部 ---- */
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 32px;
}

.dialog-title {
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  color: #000000;
}

.close-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE8CC;
  }
}

/* ---- 底部 ---- */
.dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
  padding: 12px 24px 16px;
}

.btn-cancel {
  width: 58px;
  height: 32px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545C;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-save {
  width: 58px;
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  line-height: 22px;
  display: flex;
  justify-content: center;
  align-items: center;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }
}

/* ---- 表单 ---- */
.field-warning {
  margin-top: 4px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.4;
}

/* ---- 输入框 / 选择器 ---- */
:deep(.el-input__wrapper) {
  height: 32px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #D9D9D9 inset !important;
  padding: 4px 12px;

  &:hover {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }

  &.is-focus {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }
}

:deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #FF4D4F inset !important;

  &:hover,
  &.is-focus {
    box-shadow: 0 0 0 1px #FF4D4F inset !important;
  }
}

</style>
