<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { employeeApi } from '@/api/modules/employee'
import { roleApi } from '@/api/modules/role'
import { orgApi } from '@/api/modules/org'
import { POSITION_MAP, ACCOUNT_STATUS_MAP, GENDER_MAP, DATA_SCOPE_MAP, FUNC_PERMISSION_MAP, EMPLOYEE_STATUS_MAP } from '@/constants/employee'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'
import { formatDateTime } from '@/utils'
import type { Employee, Role } from '@/types/employee'
import type { PermissionNode, OrgTreeNode } from '@/types/role'
import { EMPLOYEE_PERMISSIONS } from '@/constants/permission'

interface Props {
  modelValue: boolean
  employeeId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  employeeId: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: [employee: Employee]
}>()

const dictCategoryStore = useDictCategoryStore()

/** 职位标签映射（字典 + 本地常量） */
const positionLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('employee_position', true),
  POSITION_MAP
))

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 员工详情 */
const employee = ref<Employee | null>(null)

/** 加载状态 */
const loading = ref(false)

/** 当前 Tab */
const activeTab = ref('base')

/** 获取员工详情 */
const fetchDetail = async () => {
  if (!props.employeeId) return

  loading.value = true
  try {
    const res = await employeeApi.getDetail(props.employeeId)
    if (res.code === 'SUCCESS' && res.data) {
      employee.value = res.data
    }
  } catch (error) {
    console.error('获取员工详情失败:', error)
  } finally {
    loading.value = false
  }
}

/** 监听弹窗显示，获取详情 */
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.employeeId) {
      await Promise.all([loadPermissionTree(), loadOrgTree(), loadRoles()])
      await fetchDetail()
      activeTab.value = 'base'
    } else {
      employee.value = null
      detailRoles.value = []
    }
  }
)

/** 格式化职位 */
const formatPosition = (position: string | undefined): string => {
  if (!position) return '-'
  return positionLabelMap.value[position] || position
}

/** 格式化性别 */
const formatGender = (gender: string): string => {
  return GENDER_MAP[gender as keyof typeof GENDER_MAP] || gender
}

/** 格式化身份证号（脱敏） */
const formatIdCard = (idCard: string | undefined): string => {
  if (!idCard) return '-'
  return idCard.replace(/(.{6}).+(.{4})/, '$1********$2')
}

/** 格式化账号状态 */
const formatStatus = (status: string): { label: string; type: string } => {
  return ACCOUNT_STATUS_MAP[status as keyof typeof ACCOUNT_STATUS_MAP] || { label: status, type: 'info' }
}

/** 权限树平铺顺序 */
const permissionOrder = ref<string[]>([])

/** 权限编码到中文名映射（来自权限树） */
const permissionNameMap = ref<Record<string, string>>({})

/** 组织ID到名称映射（用于展示自定数据权限组织） */
const orgNameMap = ref<Record<number, string>>({})

/** 角色列表（详情页需要包含禁用角色） */
const detailRoles = ref<Role[]>([])

/** 加载角色列表（包含禁用角色） */
const loadRoles = async () => {
  try {
    const res = await roleApi.getList()
    if (res.code === 'SUCCESS' && res.data) {
      detailRoles.value = res.data
    }
  } catch (error) {
    console.error('获取角色列表失败:', error)
    detailRoles.value = []
  }
}

/** 加载权限树并构建展示顺序 */
const loadPermissionTree = async () => {
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
const loadOrgTree = async () => {
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

/** 获取角色功能权限标签（按权限树顺序） */
const getFuncLabels = (keys: string[]): string => {
  if (!keys || keys.length === 0) return '暂无'

  const keySet = new Set(keys)
  const orderedKeys = permissionOrder.value.filter(code => keySet.has(code))
  const extraKeys = keys.filter(code => !orderedKeys.includes(code))
  const displayKeys = [...orderedKeys, ...extraKeys]

  return displayKeys.map((code) => {
    return permissionNameMap.value[code] || FUNC_PERMISSION_MAP[code] || code
  }).join('、')
}

/** 获取数据权限显示文案 */
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

/** 获取角色信息 */
const getRoleInfo = (roleId: number): Role | undefined => {
  return detailRoles.value.find(r => r.id === roleId)
}

/** 编辑 */
const handleEdit = () => {
  if (employee.value) {
    emit('edit', employee.value)
    visible.value = false
  }
}

/** 关闭弹窗 */
const handleClose = () => {
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
    class="employee-detail-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">员工详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="detail-body">
      <template v-if="employee">
        <el-tabs v-model="activeTab" type="card" class="detail-tabs">
          <!-- 基础信息 Tab -->
          <el-tab-pane label="基础信息" name="base">
            <div class="info-table">
              <div class="info-label">员工编号</div>
              <div class="info-value">{{ employee.employeeNo }}</div>
              <div class="info-label">姓名</div>
              <div class="info-value">{{ employee.realName }}</div>
              <div class="info-label">性别</div>
              <div class="info-value">{{ formatGender(employee.gender) }}</div>
              <div class="info-label">手机号</div>
              <div class="info-value">{{ employee.phone }}</div>
              <div class="info-label">邮箱</div>
              <div class="info-value">{{ employee.email || '—' }}</div>
              <div class="info-label">身份证号</div>
              <div class="info-value">{{ formatIdCard(employee.idCard) }}</div>
              <div class="info-label">所属组织</div>
              <div class="info-value">{{ employee.orgName }}</div>
              <div class="info-label">职位</div>
              <div class="info-value">{{ formatPosition(employee.position) }}</div>
              <div class="info-label">员工状态</div>
              <div class="info-value">
                <el-tag :type="EMPLOYEE_STATUS_MAP[employee.status]?.type || 'success'" size="small">
                  {{ EMPLOYEE_STATUS_MAP[employee.status]?.label || '在职' }}
                </el-tag>
              </div>
              <div class="info-label">账号状态</div>
              <div class="info-value">
                <el-tag :type="formatStatus(employee.account?.accountStatus).type" size="small">
                  {{ formatStatus(employee.account?.accountStatus).label }}
                </el-tag>
              </div>
            </div>
          </el-tab-pane>

          <!-- 权限信息 Tab -->
          <el-tab-pane label="权限信息" name="permission">
            <template v-if="!employee.roleIds || employee.roleIds.length === 0 || !employee.roleIds.some(id => getRoleInfo(id))">
              <div class="no-permission">暂未分配角色/当前登录账号无对应角色权限</div>
            </template>
            <template v-else>
              <template v-for="roleId in employee.roleIds" :key="roleId">
                <div v-if="getRoleInfo(roleId)" class="role-card">
                  <div class="role-header">
                    <strong class="role-name">{{ getRoleInfo(roleId)?.roleName }}</strong>
                    <code class="role-code">{{ getRoleInfo(roleId)?.roleCode }}</code>
                    <el-tag
                      :type="getRoleInfo(roleId)?.status === 'active' ? 'success' : 'warning'"
                      size="small"
                    >
                      {{ getRoleInfo(roleId)?.status === 'active' ? '启用' : '禁用' }}
                    </el-tag>
                  </div>
                  <div class="role-permission-info">
                    <div class="permission-row">
                      <span class="permission-label">数据权限：</span>
                      <span>{{ getDataScopeLabel(getRoleInfo(roleId)) }}</span>
                    </div>
                    <div class="permission-row">
                      <span class="permission-label">
                        功能模块（{{ getRoleInfo(roleId)?.funcPermissions?.length || 0 }}个）：
                      </span>
                      <span>{{ getFuncLabels(getRoleInfo(roleId)?.funcPermissions || []) }}</span>
                    </div>
                  </div>
                </div>
              </template>
            </template>
          </el-tab-pane>

          <!-- 入职信息 Tab -->
          <el-tab-pane label="入职信息" name="hire">
            <div class="info-table">
              <div class="info-label">入职日期</div>
              <div class="info-value">{{ employee.hireDate || '—' }}</div>
              <div class="info-label">创建人</div>
              <div class="info-value">{{ employee.createdBy || '—' }}</div>
              <div class="info-label">创建时间</div>
              <div class="info-value">{{ formatDateTime(employee.createdAt) }}</div>
              <div class="info-label">最后更新</div>
              <div class="info-value">{{ formatDateTime(employee.updatedAt) }}</div>
              <div class="info-label">备注</div>
              <div class="info-value info-value--span3">{{ employee.remark || '—' }}</div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-edit" v-permission="EMPLOYEE_PERMISSIONS.EDIT" @click="handleEdit">编辑</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.employee-detail-dialog.el-dialog {
  width: 758px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  margin: auto !important;
}

.employee-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.employee-detail-dialog.el-dialog .el-dialog__body {
  height: 480px;
  padding: 16px 24px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.employee-detail-dialog.el-dialog .el-dialog__footer {
  padding: 12px 24px 16px !important;
  flex-shrink: 0;
  border-top: 1px solid #E1E2E9;
  box-sizing: border-box;
  text-align: right;
}

.employee-detail-dialog.el-dialog .el-dialog__footer .dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
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

/* ---- 详情 body（v-loading 包裹层，补全 flex 链） ---- */
.detail-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ---- 详情 Tab ---- */
.detail-tabs {
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
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 16px 0px 16px 0px;
  }
}

/* ---- 基础信息表格 ---- */
.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  width: 100%;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

.info-value {
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;

  &--span3 {
    grid-column: span 3;
    height: auto;
    min-height: 40px;
    padding: 5px 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

/* ---- 权限信息 ---- */
.no-permission {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.role-card {
  border: 1px solid #E1E2E9;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
  background: #F9FBFF;
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

:deep(.el-tag--info) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--primary) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--danger) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

.role-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.role-name {
  font-size: 14px;
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
}

.role-permission-info {
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}

.permission-row {
  margin-bottom: 4px;
}

.permission-label {
  color: #909399;
}

/* ---- 底部按钮 ---- */
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

.btn-edit {
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
</style>
