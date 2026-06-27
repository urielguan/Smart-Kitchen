<script setup lang="ts">
import { ref, computed, onActivated, onMounted, watch } from 'vue'
import { useEmployeeStore } from '@/stores/modules/employee'
import { useUserStore } from '@/stores/modules/user'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { ACCOUNT_STATUS_OPTIONS } from '@/constants/employee'
import type { AccountStatus } from '@/types/employee'
import EmployeeTable from '@/components/business/employee/EmployeeTable.vue'
import EmployeeForm from '@/components/business/employee/EmployeeForm.vue'
import EmployeeDetail from '@/components/business/employee/EmployeeDetail.vue'
import EmployeeImportDialog from '@/components/business/employee/EmployeeImportDialog.vue'
import EmployeeImportResultDialog from '@/components/business/employee/EmployeeImportResultDialog.vue'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import type { Employee } from '@/types/employee'
import { EMPLOYEE_PERMISSIONS } from '@/constants/permission'

const employeeStore = useEmployeeStore()
const userStore = useUserStore()
const dictCategoryStore = useDictCategoryStore()
const employeeActivatedOnce = ref(false)

/** 搜索表单 */
const searchForm = ref({
  keyword: employeeStore.searchParams.keyword || '',
  orgId: employeeStore.searchParams.orgId as number | undefined,
  accountStatus: employeeStore.searchParams.accountStatus as AccountStatus | undefined
})

/** 当前编辑的员工 */
const currentEmployee = ref<Employee | null>(null)

/** 字典缓存被清除后自动重新拉取 */
const dictCached = computed(() => dictCategoryStore.getCachedOptions('employee_position', true))
watch(dictCached, (val, oldVal) => {
  if (oldVal && oldVal.length > 0 && val.length === 0) {
    dictCategoryStore.fetchOptions('employee_position', true)
  }
})

const refreshEmployeePage = async () => {
  await Promise.all([
    dictCategoryStore.fetchOptions('employee_position', true, true),
    employeeStore.fetchList(),
    employeeStore.fetchRoles()
  ])
}

/** 初始化 */
onMounted(async () => {
  await Promise.all([
    dictCategoryStore.fetchOptions('employee_position', true, true),
    employeeStore.init()
  ])
})

onActivated(async () => {
  if (!employeeActivatedOnce.value) {
    employeeActivatedOnce.value = true
    return
  }
  await refreshEmployeePage()
})

/** 搜索 */
const handleSearch = () => {
  employeeStore.search(searchForm.value)
}

/** 重置 */
const handleReset = () => {
  searchForm.value = {
    keyword: '',
    orgId: undefined,
    accountStatus: undefined
  }
  employeeStore.resetSearch()
}

/** 新增 */
const handleAdd = () => {
  currentEmployee.value = null
  employeeStore.openForm(null)
}

/** 详情 */
const handleDetail = (row: Employee) => {
  employeeStore.openDetail(row.id)
}

/** 编辑 */
const handleEdit = (row: Employee) => {
  currentEmployee.value = JSON.parse(JSON.stringify(row))
  employeeStore.openForm(row.id)
}

/** 删除 */
const handleDelete = (row: Employee) => {
  employeeStore.deleteEmployee(row.id)
}

/** 启用/禁用 */
const handleToggleStatus = (row: Employee) => {
  employeeStore.toggleAccountStatus(row)
}

/** 导入 */
const handleImport = () => {
  employeeStore.openImportDialog()
}

/** 导出loading */
const exporting = ref(false)

/** 导出 */
const handleExport = async () => {
  exporting.value = true
  try {
    const params = employeeStore.searchParams
    await employeeStore.handleExport({
      keyword: params.keyword?.trim() || undefined,
      orgId: params.orgId,
      accountStatus: params.accountStatus
    })
  } finally {
    exporting.value = false
  }
}

/** 分页改变 */
const handlePageChange = (page: number) => {
  employeeStore.changePage(page)
}

/** 每页条数改变 */
const handleSizeChange = (size: number) => {
  employeeStore.changePageSize(size)
}

/** 表单提交成功 */
const handleFormSuccess = () => {
  employeeStore.fetchList()
  // 如果修改的是当前登录用户对应的员工，刷新用户信息
  if (currentEmployee.value?.account?.userId === userStore.userInfo?.id) {
    userStore.fetchUserInfo()
  }
}

/** 详情页编辑 */
const handleDetailEdit = (employee: Employee) => {
  currentEmployee.value = JSON.parse(JSON.stringify(employee))
  employeeStore.closeDetail()
  employeeStore.openForm(employee.id)
}
</script>

<template>
  <div class="employee-page">
    <!-- 搜索工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="4">
          <el-input
            v-model="searchForm.keyword"
            placeholder="员工编号/姓名/手机号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="4">
          <OrgTreeSelect
            v-model="searchForm.orgId"
            :active-only="true"
            placeholder="全部组织"
          />
        </el-col>
        <el-col :span="3">
          <el-select
            v-model="searchForm.accountStatus"
            placeholder="全部状态"
            clearable
          >
            <el-option
              v-for="item in ACCOUNT_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="13" style="text-align: right">
          <el-button class="btn-search" @click="handleSearch">查询</el-button>
          <el-button class="btn-reset" @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 数据表格 -->
    <div class="table-wrapper">
      <div class="table-header">
        <el-button class="btn-search" v-permission="EMPLOYEE_PERMISSIONS.CREATE" @click="handleAdd">+ 新增员工</el-button>
        <el-button class="btn-import" v-permission="EMPLOYEE_PERMISSIONS.IMPORT" @click="handleImport">导入</el-button>
        <el-button class="btn-export" v-permission="EMPLOYEE_PERMISSIONS.EXPORT" :loading="exporting" @click="handleExport">导出</el-button>
      </div>
      <EmployeeTable
        :data="employeeStore.list"
        :loading="employeeStore.loading"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete"
        @toggle-status="handleToggleStatus"
      />
      <div class="pagination">
        <span class="total">共 {{ employeeStore.total }} 项数据</span>
        <el-pagination
          v-model:current-page="employeeStore.pageNum"
          v-model:page-size="employeeStore.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="employeeStore.total"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <EmployeeDetail
      v-model="employeeStore.detailVisible"
      :employee-id="employeeStore.currentEmployeeId"
      @edit="handleDetailEdit"
    />

    <!-- 新增/编辑弹窗 -->
    <EmployeeForm
      v-model="employeeStore.formVisible"
      :employee-id="employeeStore.currentEmployeeId"
      :employee-data="currentEmployee"
      @success="handleFormSuccess"
    />

    <!-- 导入弹窗 -->
    <EmployeeImportDialog />

    <!-- 导入结果弹窗 -->
    <EmployeeImportResultDialog />
  </div>
</template>

<style lang="scss" scoped>
.employee-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.toolbar {
  background: $bg-white;
  padding: 20px;
  border-radius: $border-radius-large;
  margin-bottom: 20px;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;

  .el-input,
  .el-select {
    width: 100%;
  }

  .btn-search {
    width: 60px;
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
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    background: #F2F4F8;
    border-color: #F2F4F8;
    border-radius: 6px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);

    &:hover {
      background: #E3E7EF;
      border-color: #E3E7EF;
      color: rgba(0, 0, 0, 0.9);
    }
  }
}

.table-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  flex: 1;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-header {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
  padding: 16px;
  flex-shrink: 0;

  .btn-search {
    width: 110px;
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
    color: #fff;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-import {
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #7288FA;

    &:hover {
      background: #EEF1FF;
      border-color: #5C75E8;
      color: #5C75E8;
    }
  }

  .btn-export {
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    &:hover {
      background: #F5F7FA;
      border-color: #7288FA;
      color: #7288FA;
    }
  }
}

.pagination {
  padding: 16px 24px;
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
    background: #FFFFFF;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    color: #000000E5;
    margin-left: 8px;
  }

  :deep(.el-pagination .el-pager li + li) {
    margin-left: 8px;
  }
}
</style>
