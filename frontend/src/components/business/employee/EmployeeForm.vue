<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { Employee, EmployeeForm as IEmployeeForm, Role } from '@/types/employee'
import type { PermissionNode, OrgTreeNode } from '@/types/role'
import { POSITION_MAP, ACCOUNT_STATUS_OPTIONS, GENDER_OPTIONS, DATA_SCOPE_MAP, FUNC_PERMISSION_MAP, EMPLOYEE_STATUS_OPTIONS } from '@/constants/employee'
import { employeeApi } from '@/api/modules/employee'
import { roleApi } from '@/api/modules/role'
import { orgApi } from '@/api/modules/org'
import { useEmployeeStore } from '@/stores/modules/employee'
import { useUserStore } from '@/stores/modules/user'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildActiveDictOptions } from '@/utils/dict-category'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'

interface Props {
  modelValue: boolean
  employeeId?: number | null
  employeeData?: Employee | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  employeeId: null,
  employeeData: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const employeeStore = useEmployeeStore()
const userStore = useUserStore()
const dictCategoryStore = useDictCategoryStore()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 是否编辑模式 */
const isEdit = computed(() => !!props.employeeId)

/** 弹窗标题 */
const dialogTitle = computed(() => isEdit.value ? '编辑员工' : '新增员工')

/** 当前 Tab */
const activeTab = ref('base')

/** 表单引用 */
const formRef = ref()

/** 提交加载状态 */
const submitting = ref(false)

/** 表单数据 */
const formData = ref<IEmployeeForm>(getDefaultFormData())

/** 原始角色ID列表（用于离职员工提交时比对） */
const originalRoleIds = ref<number[]>([])

/** 编辑中的员工关联的 auth_user.id */
const editingUserId = ref<number | null>(null)

/** 是否编辑的是自己 */
const isSelf = computed(() => isEdit.value && editingUserId.value != null && editingUserId.value === userStore.userInfo?.id)
const positionOptions = computed(() => buildActiveDictOptions(
  dictCategoryStore.getCachedOptions('employee_position'),
  props.employeeId ? formData.value.position : undefined,
  dictCategoryStore.getCachedOptions('employee_position', true),
  POSITION_MAP[formData.value.position || ''] || formData.value.position
))

/** 表单验证规则 */
const formRules = {
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: validatePhone, trigger: 'blur' }
  ],
  orgId: [
    { required: true, message: '请选择所属组织', trigger: 'change' }
  ],
  accountStatus: [
    { required: true, message: '请选择账号状态', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择员工状态', trigger: 'change' }
  ]
}

/** 手机号校验 */
function validatePhone(_rule: any, value: string, callback: (error?: Error) => void) {
  if (value && !/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入11位有效手机号'))
  } else {
    callback()
  }
}

/** 获取默认表单数据 */
function getDefaultFormData(): IEmployeeForm {
  return {
    employeeNo: '',
    realName: '',
    gender: 'male',
    phone: '',
    email: '',
    idCard: '',
    orgId: null,
    position: undefined,
    hireDate: '',
    accountStatus: 'active',
    status: 'active',
    roleIds: [],
    remark: ''
  }
}

/** 监听弹窗显示状态，回填表单 */
watch(
  () => props.modelValue,
  async (val) => {
    if (val) {
      await dictCategoryStore.fetchOptions('employee_position', false, true)
      await Promise.all([loadPermissionTree(), loadOrgTree(), employeeStore.fetchRoles()])

      await nextTick()
      if (props.employeeId) {
        // 编辑模式：从 API 获取完整数据（包含 roleIds）
        await fetchEmployeeDetail(props.employeeId)
      } else {
        // 新增模式，生成员工编号
        formData.value = getDefaultFormData()
        await generateEmpNo()
      }
      activeTab.value = 'base'
    }
  },
  { immediate: true }
)

/** 获取员工详情 */
async function fetchEmployeeDetail(id: number) {
  try {
    const res = await employeeApi.getDetail(id)
    if (res.code === 'SUCCESS' && res.data) {
      fillFormData(res.data)
    }
  } catch (error) {
    console.error('获取员工详情失败:', error)
    ElMessage.error('获取员工详情失败')
  }
}

/** 填充表单数据 */
function fillFormData(data: Employee) {
  const roleIds = data.roleIds || []
  formData.value = {
    employeeNo: data.employeeNo,
    realName: data.realName,
    gender: data.gender,
    phone: data.phone,
    email: data.email || '',
    idCard: data.idCard || '',
    orgId: data.orgId,
    position: data.position,
    hireDate: data.hireDate || '',
    accountStatus: data.account?.accountStatus || 'active',
    status: data.status || 'active',
    roleIds,
    remark: data.remark || ''
  }
  originalRoleIds.value = [...roleIds]
  editingUserId.value = data.account?.userId ?? null
}

/** 生成员工编号 */
async function generateEmpNo() {
  try {
    const res = await employeeApi.generateEmpNo()
    if (res.code === 'SUCCESS' && res.data) {
      formData.value.employeeNo = res.data
    }
  } catch (error) {
    console.error('生成员工编号失败:', error)
  }
}

/** 角色列表 */
const roles = computed(() => employeeStore.roles)

/** 权限树平铺顺序 */
const permissionOrder = ref<string[]>([])

/** 权限编码到中文名映射（来自权限树） */
const permissionNameMap = ref<Record<string, string>>({})

/** 组织ID到名称映射（用于展示自定数据权限组织） */
const orgNameMap = ref<Record<number, string>>({})

/** 加载权限树并构建展示顺序 */
async function loadPermissionTree() {
  try {
    const res = await roleApi.getPermissionTree()
    if (res.code === 'SUCCESS' && res.data) {
      const orderedCodes: string[] = []
      const codeNameMap: Record<string, string> = {}
      const walk = (nodes: PermissionNode[]) => {
        nodes.forEach((node) => {
          if (node.permissionCode) {
            orderedCodes.push(node.permissionCode)
            if (node.permissionName) {
              codeNameMap[node.permissionCode] = node.permissionName
            }
          }
          if (node.children && node.children.length > 0) {
            walk(node.children)
          }
        })
      }
      walk(res.data)
      permissionOrder.value = orderedCodes
      permissionNameMap.value = codeNameMap
    }
  } catch (error) {
    console.error('获取权限树失败:', error)
  }
}

/** 加载组织树并构建组织名称映射 */
async function loadOrgTree() {
  try {
    const res = await orgApi.getTree()
    if (res.code === 'SUCCESS' && res.data) {
      const map: Record<number, string> = {}
      const walk = (nodes: OrgTreeNode[]) => {
        nodes.forEach((node) => {
          map[node.id] = node.orgName
          if (node.children && node.children.length > 0) {
            walk(node.children)
          }
        })
      }
      walk(res.data)
      orgNameMap.value = map
    }
  } catch (error) {
    console.error('获取组织树失败:', error)
  }
}

/** 按权限树顺序展示功能权限标签 */
const getFuncLabels = (keys: string[]): string => {
  if (!keys || keys.length === 0) return '暂无'

  const keySet = new Set(keys)
  const orderedKeys = permissionOrder.value.filter(code => keySet.has(code))
  const extraKeys = keys.filter(code => !orderedKeys.includes(code))
  const displayKeys = [...orderedKeys, ...extraKeys]

  const labels = displayKeys.slice(0, 4).map((code) => {
    return permissionNameMap.value[code] || FUNC_PERMISSION_MAP[code] || code
  })
  const more = displayKeys.length > 4 ? `等 ${displayKeys.length} 项权限` : ''
  return labels.join('、') + more
}

/** 数据权限显示文案 */
const getDataScopeLabel = (role?: Role): string => {
  if (!role || !role.dataScope) return '-'

  if (role.dataScope === 'custom') {
    if (!role.dataScopeOrgIds || role.dataScopeOrgIds.length === 0) {
      return DATA_SCOPE_MAP.custom || '自定数据权限'
    }
    const orgNames = role.dataScopeOrgIds
      .map(id => orgNameMap.value[id])
      .filter((name): name is string => !!name)
    if (orgNames.length === 0) {
      return DATA_SCOPE_MAP.custom || '自定数据权限'
    }
    return `${DATA_SCOPE_MAP.custom || '自定数据权限'}（${orgNames.join('、')}）`
  }

  return DATA_SCOPE_MAP[role.dataScope] || role.dataScope
}

/** 判断角色ID是否发生变化 */
function isRoleIdsChanged(): boolean {
  const current = [...formData.value.roleIds].sort()
  const original = [...originalRoleIds.value].sort()
  if (current.length !== original.length) return true
  return current.some((id, i) => id !== original[i])
}

/** 提交表单 */
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate()

  // 手机号二次校验
  if (!/^1[3-9]\d{9}$/.test(formData.value.phone)) {
    ElMessage.error('请输入11位有效手机号')
    return
  }

  // 离职员工不允许修改角色权限
  if (formData.value.status === 'left' && isRoleIdsChanged()) {
    ElMessage.warning('离职员工不允许修改角色权限')
    return
  }

  // 禁止将自己状态改为离职
  if (isSelf.value && formData.value.status === 'left') {
    ElMessage.warning('不能将自己的员工状态改为离职')
    return
  }

  // 禁止禁用自己的账号
  if (isSelf.value && formData.value.accountStatus === 'inactive') {
    ElMessage.warning('不能禁用自己的账号')
    return
  }

  try {
    submitting.value = true

    // 直接提交，字段名与后端一致：status=在职/离职，accountStatus=启用/禁用
    const submitData = { ...formData.value }

    if (isEdit.value && props.employeeId) {
      const res = await employeeApi.update(props.employeeId, submitData)
      if (res.data?.message) {
        ElMessage.warning(res.data.message)
      } else {
        ElMessage.success('员工信息及权限编辑成功')
      }
    } else {
      await employeeApi.create(submitData)
      ElMessage.success('员工新增成功，权限已同步分配')
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
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="758px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    @close="handleClose"
    class="employee-form-dialog"
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

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" type="card" class="form-tabs">
      <!-- 基础信息 Tab -->
      <el-tab-pane label="基础信息" name="base">
        <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          label-width="100px"
          label-suffix="："
          class="form-content"
        >
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="员工编号" prop="employeeNo">
                <el-input
                  v-model="formData.employeeNo"
                  readonly
                  placeholder="自动生成"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="姓名" prop="realName">
                <el-input v-model="formData.realName" placeholder="请输入姓名" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="性别">
                <el-radio-group v-model="formData.gender">
                  <el-radio
                    v-for="item in GENDER_OPTIONS"
                    :key="item.value"
                    :value="item.value"
                  >
                    {{ item.label }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="手机号" prop="phone">
                <el-input
                  v-model="formData.phone"
                  placeholder="请输入11位手机号"
                  maxlength="11"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属组织" prop="orgId">
                <OrgTreeSelect
                  v-model="formData.orgId"
                  :active-only="true"
                  placeholder="请选择所属组织"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="职位">
                <el-select
                  v-model="formData.position"
                  placeholder="请选择职位"
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in positionOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入职日期">
                <el-date-picker
                  v-model="formData.hireDate"
                  type="date"
                  placeholder="请选择入职日期"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item label="员工状态" prop="status">
                <el-select v-model="formData.status" :disabled="!isEdit" style="width: 100%">
                  <el-option
                    v-for="item in EMPLOYEE_STATUS_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <div v-if="!isEdit" class="form-tip">新增员工默认在职</div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="邮箱">
                <el-input v-model="formData.email" placeholder="请输入邮箱（可选）" maxlength="100" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证号">
                <el-input v-model="formData.idCard" placeholder="请输入身份证号（可选）" maxlength="18" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注">
                <el-input
                  v-model="formData.remark"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入备注"
                  maxlength="500"
                  show-word-limit
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>

      <!-- 权限分配 Tab -->
      <el-tab-pane label="权限分配" name="permission" :disabled="formData.status === 'left'">
        <div v-if="formData.status === 'left'" class="permission-disabled-tip">
          离职员工不允许分配或调整角色权限
        </div>
        <template v-else>
          <div class="permission-tip">
            勾选角色后，员工将继承该角色的功能权限与数据权限：
          </div>
          <div v-if="roles.length === 0" class="no-roles">
            暂无可分配角色，请先在「角色权限管理」中创建角色
          </div>
          <div v-else class="role-list">
            <div
              v-for="role in roles"
              :key="role.id"
              class="role-item"
              :class="{ 'role-item--checked': formData.roleIds.includes(role.id) }"
            >
              <div class="role-header">
                <el-checkbox
                  v-model="formData.roleIds"
                  :value="role.id"
                >
                  <span class="role-name">{{ role.roleName }}</span>
                  <code class="role-code">{{ role.roleCode }}</code>
                  <el-tag
                    :type="role.status === 'active' ? 'success' : 'warning'"
                    size="small"
                  >
                    {{ role.status === 'active' ? '启用' : '禁用' }}
                  </el-tag>
                </el-checkbox>
              </div>
              <div
                v-if="formData.roleIds.includes(role.id)"
                class="role-permission"
              >
                <span class="permission-label">功能模块：</span>
                <span>{{ getFuncLabels(role.funcPermissions || []) }}</span>
                <br>
                <span class="permission-label">数据范围：</span>
                <span>{{ getDataScopeLabel(role) }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>

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
.employee-form-dialog.el-dialog {
  width: 758px;
  max-height: 80vh;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.employee-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.employee-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px 24px;
  height: 480px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.employee-form-dialog.el-dialog .el-dialog__footer {
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
  width: 60px;
  height: 32px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }
}

/* ---- Tab 切换 ---- */
.form-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    margin: 0;
    border-bottom: 1px solid #E1E2E9;
  }

  :deep(.el-tabs__nav-wrap) {
    margin-bottom: -6px;
    overflow: visible !important;
  }

  :deep(.el-tabs__nav-scroll) {
    overflow: visible !important;
  }

  :deep(.el-tabs__nav) {
    border: none;
    overflow: visible !important;
  }

  :deep(.el-tabs__item) {
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    color: #606266;
    height: 36px;
    line-height: 36px;
    padding: 0 20px;
    margin-right: 4px;
    background: #FAFAFA;
    border: 1px solid #F0F0F0 !important;
    border-bottom: 1px solid #E1E2E9 !important;
    border-radius: 0;

    &:hover {
      color: #7288FA;
    }

    &.is-active {
      color: #7288FA;
      background: #FFFFFF;
      border-bottom-color: #FFFFFF !important;
    }

    &.is-disabled {
      color: #C0C4CC;
      cursor: not-allowed;
      background: #FAFAFA;
    }
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 16px 0px 16px 0px;
  }
}

.form-content {
  padding: 0;
}

.form-tip {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

/* ---- 表单 ---- */
:deep(.el-form-item) {
  margin-bottom: 16px;
}

:deep(.el-form-item__label) {
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.85);
  line-height: 32px;
  padding-right: 9px;
}

:deep(.el-form-item.is-required .el-form-item__label::before) {
  color: #FF4D4F !important;
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

:deep(.el-input__inner) {
  font-size: 14px;
  height: 24px;
  line-height: 24px;
}

:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

/* ---- 文本域 ---- */
:deep(.el-textarea__inner) {
  border: 1px solid #D9D9D9;
  border-radius: 2px;
  font-size: 14px;
  padding: 5px 12px;

  &:hover {
    border-color: #7288FA;
  }

  &:focus {
    border-color: #7288FA;
    box-shadow: none;
  }
}

/* ---- 权限分配 Tab ---- */
.permission-tip {
  font-size: 13px;
  color: #606266;
  margin-bottom: 16px;
}

.permission-disabled-tip {
  text-align: center;
  color: #909399;
  padding: 60px 0;
  font-size: 14px;
}

:deep(.el-tag--success) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--warning) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

.no-roles {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.role-list {
  padding-bottom: 8px;
}

.role-item {
  border: 1px solid #E1E2E9;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;

  &.role-item--checked {
    background: #F7F9FC;
  }
}

.role-header {
  display: flex;
  align-items: center;

  :deep(.el-checkbox__label) {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background-color: #7288FA;
    border-color: #7288FA;
  }

  :deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
    color: #7288FA;
  }

  :deep(.el-checkbox__inner:hover) {
    border-color: #7288FA;
  }

  :deep(.el-checkbox__input.is-focus .el-checkbox__inner) {
    border-color: #7288FA;
  }
}

.role-name {
  font-weight: 500;
}

.role-code {
  background: #EFEFEF;
  padding: 0 6px;
  border-radius: 5px;
  font-size: 12px;
  border: 1px solid #C0C5CA;
  height: 24px;
  line-height: 1;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  font-family: PingFang SC;
  color: #606266;
  align-items: center;
  font-family: PingFang SC;
}

.role-permission {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #E1E2E9;
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
}

.permission-label {
  color: #909399;
}
</style>
