<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { employeeApi } from '@/api/modules/employee'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import { POSITION_MAP } from '@/constants/employee'
import type { Employee } from '@/types/employee'

const props = defineProps<{
  modelValue: boolean
  excludeIds: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'confirm': [employeeIds: number[]]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const keyword = ref('')
const orgId = ref<number | undefined>(undefined)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const employees = ref<Employee[]>([])
const selectedEmployees = ref<Employee[]>([])

watch(visible, (val) => {
  if (val) {
    keyword.value = ''
    orgId.value = undefined
    pageNum.value = 1
    selectedEmployees.value = []
    loadEmployees()
  }
})

const loadEmployees = async () => {
  loading.value = true
  try {
    const res = await employeeApi.getList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value?.trim() || undefined,
      orgId: orgId.value || undefined,
      accountStatus: 'active'
    })
    if (res.code === 'SUCCESS' && res.data) {
      employees.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载员工列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  loadEmployees()
}

const handleReset = () => {
  keyword.value = ''
  orgId.value = undefined
  pageNum.value = 1
  loadEmployees()
}

const handleSelectionChange = (selection: Employee[]) => {
  selectedEmployees.value = selection
}

const isExcluded = (id: number) => props.excludeIds.includes(id)

const handleSelectable = (row: Employee) => !isExcluded(row.id)

const handleConfirm = () => {
  emit('confirm', selectedEmployees.value.map(e => e.id))
  visible.value = false
}

const handleClose = () => {
  visible.value = false
}

const formatPosition = (position?: string) => {
  if (!position) return '-'
  return POSITION_MAP[position] || position
}

const handlePageChange = (page: number) => {
  pageNum.value = page
  loadEmployees()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  pageNum.value = 1
  loadEmployees()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="900px"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="employee-select-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">选择员工</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="keyword"
        placeholder="员工编号/姓名/手机号"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
      />
      <OrgTreeSelect
        v-model="orgId"
        placeholder="所属组织"
        :active-only="true"
        style="width: 220px"
      />
      <el-button class="btn-search" @click="handleSearch">查询</el-button>
      <el-button class="btn-reset" @click="handleReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <el-table
      v-loading="loading"
      :data="employees"
      height="400"
      border
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="40" :selectable="handleSelectable" />
      <el-table-column prop="employeeNo" label="员工编号" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="80" />
      <el-table-column prop="orgName" label="所属组织" min-width="120" />
      <el-table-column label="职位" min-width="80">
        <template #default="{ row }">{{ formatPosition(row.position) }}</template>
      </el-table-column>
      <el-table-column label="手机号" min-width="120">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="isExcluded(row.id)" type="info" size="small">已添加</el-tag>
          <el-tag v-else type="success" size="small">可添加</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <span class="total">共 {{ total }} 项数据</span>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :pager-count="7"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-save" :disabled="selectedEmployees.length === 0" @click="handleConfirm">
          确定选择{{ selectedEmployees.length ? `(${selectedEmployees.length})` : '' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.employee-select-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.employee-select-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.employee-select-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.employee-select-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}

/* ---- 表格基础样式 ---- */
.employee-select-dialog.el-dialog .el-table {
  --el-table-border-color: #DCDCDC;
  --el-table-header-bg-color: #F8FAFC;
  --el-table-row-hover-bg-color: #F5F7FA;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

.employee-select-dialog.el-dialog .el-table .el-table__header-wrapper th.el-table__cell {
  background: #F8FAFC !important;
  color: rgba(0, 0, 0, 0.6);
  font-weight: 500;
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.employee-select-dialog.el-dialog .el-table td.el-table__cell {
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.employee-select-dialog.el-dialog .el-table .el-table__inner-wrapper::before {
  display: none;
}

.employee-select-dialog.el-dialog .el-table--border .el-table__inner-wrapper {
  border-right: none;
  border-bottom: none;
}

.employee-select-dialog.el-dialog .el-table--border {
  border: none;
}

.employee-select-dialog.el-dialog .el-table .el-table__body-wrapper {
  border-bottom: 1px solid #DCDCDC;
}

.employee-select-dialog.el-dialog .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.employee-select-dialog.el-dialog .el-checkbox__input.is-indeterminate .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.employee-select-dialog.el-dialog .el-checkbox__input.is-focus .el-checkbox__inner {
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

/* ---- 搜索栏 ---- */
.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  align-items: center;

  :deep(.el-button) {
    margin-left: 0;
  }

  .btn-search {
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

  .btn-reset {
    height: 32px;
    padding: 5px 16px;
    background: #F2F4F8;
    border-color: #F2F4F8;
    border-radius: 6px;
    color: rgba(0, 0, 0, 0.9);

    &:hover {
      background: #E3E7EF;
      border-color: #E3E7EF;
      color: rgba(0, 0, 0, 0.9);
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

/* ---- 底部 ---- */
.dialog-footer {
  display: flex;
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
  font-size: 13px;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-save {
  width: auto;
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #fff;
  }

  &.is-disabled {
    opacity: 0.45;
  }
}
</style>
