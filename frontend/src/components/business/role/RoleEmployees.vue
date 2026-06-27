<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoleStore } from '@/stores/modules/role'
import { ROLE_PERMISSIONS } from '@/constants/permission'
import { POSITION_MAP, EMPLOYEE_STATUS_MAP, ACCOUNT_STATUS_MAP } from '@/constants/employee'
import type { RoleEmployee } from '@/types/role'
import EmployeeSelectDialog from './EmployeeSelectDialog.vue'

const roleStore = useRoleStore()

const role = computed(() => roleStore.getCurrentRole())
const employees = computed(() => roleStore.currentEmployees)
const total = computed(() => roleStore.employeeTotal)
const pageNum = computed(() => roleStore.employeePageNum)
const pageSize = computed(() => roleStore.employeePageSize)

// 多选状态（用于批量移除）
const selectedEmployees = ref<RoleEmployee[]>([])
const tableRef = ref()

const handleSelectionChange = (selection: RoleEmployee[]) => {
  selectedEmployees.value = selection
}

const handlePageChange = (page: number) => {
  if (roleStore.currentRoleId) {
    roleStore.fetchRoleEmployees(roleStore.currentRoleId, page)
  }
}

const handleSizeChange = (size: number) => {
  roleStore.employeePageSize = size
  if (roleStore.currentRoleId) {
    roleStore.fetchRoleEmployees(roleStore.currentRoleId, 1)
  }
}

const handleClose = () => {
  selectedEmployees.value = []
  roleStore.closeEmployees()
}

// 添加成员
const handleAddMembers = () => {
  roleStore.openMemberSelect()
}

const handleAddConfirm = (employeeIds: number[]) => {
  roleStore.addRoleMembers(employeeIds)
}

// 移除成员
const handleRemoveMembers = async () => {
  if (selectedEmployees.value.length === 0) return
  const ids = selectedEmployees.value.map(e => e.id)
  const removed = await roleStore.batchRemoveRoleMembers(ids)
  if (removed) {
    selectedEmployees.value = []
    tableRef.value?.clearSelection()
  }
}

// 已有成员 ID（排除用，全量）
const excludedEmployeeIds = computed(() => roleStore.allEmployeeIds)
</script>

<template>
  <el-dialog
    :model-value="roleStore.employeesVisible"
    width="800px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="role-employees-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ role ? `「${role.roleName}」关联员工（${total} 人）` : '关联员工' }}</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <!-- 工具栏 -->
    <div class="dialog-toolbar">
      <el-button class="btn-add-member" type="primary" v-permission="ROLE_PERMISSIONS.MEMBER_ADD" @click="handleAddMembers">
        + 添加成员
      </el-button>
      <el-button
        class="btn-remove-member"
        v-permission="ROLE_PERMISSIONS.MEMBER_REMOVE"
        :disabled="selectedEmployees.length === 0"
        @click="handleRemoveMembers"
      >
        移除成员{{ selectedEmployees.length ? `(${selectedEmployees.length})` : '' }}
      </el-button>
    </div>

    <!-- 成员列表 -->
    <el-table
      ref="tableRef"
      :data="employees"
      height="400"
      border
      empty-text="该角色暂无关联员工"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="employeeNo" label="员工编号" min-width="150" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="orgName" label="所属组织" min-width="150" />
      <el-table-column prop="position" label="职位" min-width="80">
        <template #default="{ row }">
          {{ POSITION_MAP[row.position as keyof typeof POSITION_MAP] || row.position || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" min-width="120">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="employeeStatus" label="员工状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="EMPLOYEE_STATUS_MAP[row.employeeStatus]?.type || 'info'" size="small">
            {{ EMPLOYEE_STATUS_MAP[row.employeeStatus]?.label || row.employeeStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="账号状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="ACCOUNT_STATUS_MAP[row.status]?.type || 'info'" size="small">
            {{ ACCOUNT_STATUS_MAP[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <span class="total">共 {{ total }} 项数据</span>
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        :pager-count="7"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 员工选择弹窗 -->
    <EmployeeSelectDialog
      v-model="roleStore.memberSelectVisible"
      :exclude-ids="excludedEmployeeIds"
      @confirm="handleAddConfirm"
    />
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.role-employees-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.role-employees-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.role-employees-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* ---- 表格基础样式 ---- */
.role-employees-dialog.el-dialog .el-table {
  --el-table-border-color: #DCDCDC;
  --el-table-header-bg-color: #F8FAFC;
  --el-table-row-hover-bg-color: #F5F7FA;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

.role-employees-dialog.el-dialog .el-table .el-table__header-wrapper th.el-table__cell {
  background: #F8FAFC !important;
  color: rgba(0, 0, 0, 0.6);
  font-weight: 500;
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.role-employees-dialog.el-dialog .el-table td.el-table__cell {
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.role-employees-dialog.el-dialog .el-table .el-table__inner-wrapper::before {
  display: none;
}

.role-employees-dialog.el-dialog .el-table--border .el-table__inner-wrapper {
  border-right: none;
  border-bottom: none;
}

.role-employees-dialog.el-dialog .el-table--border {
  border: none;
}

.role-employees-dialog.el-dialog .el-table .el-table__body-wrapper {
  border-bottom: 1px solid #DCDCDC;
}

.role-employees-dialog.el-dialog .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.role-employees-dialog.el-dialog .el-checkbox__input.is-indeterminate .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.role-employees-dialog.el-dialog .el-checkbox__input.is-focus .el-checkbox__inner {
  border-color: #7288FA;
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

/* ---- 工具栏 ---- */
.dialog-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;

  .btn-add-member {
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    color: #fff;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-remove-member {
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #FF7474;
    border-radius: 6px;
    color: #FF7474;

    &:hover {
      background: #FFF0ED;
      border-color: #FF3D3D;
      color: #FF3D3D;
    }

    &.is-disabled {
      opacity: 0.45;
    }
  }
}

/* ---- tag ---- */
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

/* ---- 分页 ---- */
.pagination {
  padding: 20px 0 0;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    color: rgba(0, 0, 0, 0.6);
  }

  :deep(.el-pagination .el-pager) {
    gap: 4px;
  }

  :deep(.el-pagination .is-active) {
    width: 32px;
    height: 32px;
    background: #7288FA;
    border-radius: 3px;
    color: #fff;
  }

  :deep(.el-pagination .el-pager li:not(.is-active)) {
    width: 32px;
    height: 32px;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    color: rgba(0, 0, 0, 0.6);
  }
}
</style>
