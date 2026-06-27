<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { employeeApi } from '@/api/modules/employee'
import { POSITION_MAP, EMPLOYEE_STATUS_MAP, ACCOUNT_STATUS_MAP } from '@/constants/employee'

interface Props {
  modelValue: boolean
  orgId: number | null
  orgName?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  orgId: null,
  orgName: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 成员列表
const members = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 获取成员列表（只查在职员工）
const fetchMembers = async (page?: number) => {
  if (!props.orgId) return
  if (page !== undefined) {
    pageNum.value = page
  }
  loading.value = true
  try {
    const res = await employeeApi.getList({
      orgId: props.orgId,
      status: 'active',
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    const list = res.data?.list || []
    total.value = res.data?.total || 0
    // 字段映射：employeeNo -> empNo, realName -> name, orgName -> deptName
    members.value = list.map((emp: any) => ({
      empNo: emp.employeeNo,
      name: emp.realName,
      deptName: emp.orgName,
      position: emp.position,
      status: emp.status,
      accountStatus: emp.account?.accountStatus
    }))
  } catch {
    members.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  fetchMembers(page)
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  fetchMembers(1)
}

watch(() => props.modelValue, (val) => {
  if (val && props.orgId) {
    pageNum.value = 1
    fetchMembers()
  }
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="800px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="org-member-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">「{{ orgName }}」关联成员（{{ total }} 人）</span>
        <div class="close-btn" @click="visible = false">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-table :data="members" v-loading="loading" height="400" border empty-text="该组织暂无关联成员">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="empNo" label="员工编号" min-width="150" />
      <el-table-column prop="name" label="姓名" min-width="100" />
      <el-table-column prop="deptName" label="所属组织" min-width="120" />
      <el-table-column prop="position" label="职位" min-width="100">
        <template #default="{ row }">
          {{ POSITION_MAP[row.position as keyof typeof POSITION_MAP] || row.position || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="员工状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="EMPLOYEE_STATUS_MAP[row.status]?.type || 'info'" size="small">
            {{ EMPLOYEE_STATUS_MAP[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="accountStatus" label="账号状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.accountStatus" :type="ACCOUNT_STATUS_MAP[row.accountStatus]?.type || 'info'" size="small">
            {{ ACCOUNT_STATUS_MAP[row.accountStatus]?.label || row.accountStatus }}
          </el-tag>
          <span v-else>-</span>
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
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.org-member-dialog.el-dialog {
  width: 800px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.org-member-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.org-member-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.org-member-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}

/* ---- 表格基础样式 ---- */
.org-member-dialog.el-dialog .el-table {
  --el-table-border-color: #DCDCDC;
  --el-table-header-bg-color: #F8FAFC;
  --el-table-row-hover-bg-color: #F5F7FA;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

.org-member-dialog.el-dialog .el-table .el-table__header-wrapper th.el-table__cell {
  background: #F8FAFC !important;
  color: rgba(0, 0, 0, 0.6);
  font-weight: 500;
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.org-member-dialog.el-dialog .el-table td.el-table__cell {
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.org-member-dialog.el-dialog .el-table .el-table__inner-wrapper::before {
  display: none;
}

.org-member-dialog.el-dialog .el-table--border .el-table__inner-wrapper {
  border-right: none;
  border-bottom: none;
}

.org-member-dialog.el-dialog .el-table--border {
  border: none;
}

.org-member-dialog.el-dialog .el-table .el-table__body-wrapper {
  border-bottom: 1px solid #DCDCDC;
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
  background: #F4F4F5;
  border: 1px solid #909399;
  border-radius: 5px;
  color: #909399;
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

/* ---- 分页 ---- */
.pagination {
  padding: 20px 0 0;
  background: #FFFFFF;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
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
