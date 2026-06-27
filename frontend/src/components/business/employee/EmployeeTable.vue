<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { Employee } from '@/types/employee'
import { POSITION_MAP, ACCOUNT_STATUS_MAP, EMPLOYEE_STATUS_MAP } from '@/constants/employee'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'
import StatusTag from '@/components/common/StatusTag.vue'
import { EMPLOYEE_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: Employee[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  detail: [employee: Employee]
  edit: [employee: Employee]
  delete: [employee: Employee]
  toggleStatus: [employee: Employee]
}>()

const dictCategoryStore = useDictCategoryStore()

/** 职位标签映射（字典 + 本地常量） */
const positionLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('employee_position', true),
  POSITION_MAP
))

/** 表格容器引用 */
const tableContainerRef = ref<HTMLElement | null>(null)

/** 表格高度 */
const tableHeight = ref<number | undefined>(undefined)

/** ResizeObserver 实例 */
let resizeObserver: ResizeObserver | null = null

/** 计算表格高度 */
const updateTableHeight = () => {
  if (tableContainerRef.value) {
    tableHeight.value = tableContainerRef.value.clientHeight
  }
}

/** 格式化职位 */
const formatPosition = (position: string | undefined): string => {
  if (!position) return '-'
  return positionLabelMap.value[position] || position
}

/** 详情 */
const handleDetail = (row: Employee) => emit('detail', row)

/** 编辑 */
const handleEdit = (row: Employee) => emit('edit', row)

/** 删除 */
const handleDelete = (row: Employee) => emit('delete', row)

/** 启用/禁用 */
const handleToggleStatus = (row: Employee) => emit('toggleStatus', row)

onMounted(() => {
  if (tableContainerRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableContainerRef.value)
    updateTableHeight()
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>

<template>
  <div ref="tableContainerRef" class="table-container">
    <el-table
      :data="data"
      :loading="loading"
      :height="tableHeight"
      :cell-style="{ verticalAlign: 'middle' }"
      row-key="id"
    >
      <el-table-column type="index" label="序号" width="60" align="center" class-name="index-col" />

      <el-table-column prop="employeeNo" label="员工编号" min-width="150" />

      <el-table-column prop="realName" label="姓名" min-width="80" />

      <el-table-column prop="phone" label="手机号" min-width="120" />

      <el-table-column prop="orgName" label="所属组织" min-width="150" />

      <el-table-column prop="position" label="职位" min-width="80">
        <template #default="{ row }">
          {{ formatPosition(row.position) }}
        </template>
      </el-table-column>

      <el-table-column prop="roleNames" label="所属角色" min-width="150">
        <template #default="{ row }">
          <span class="role-names">{{ row.roleNames || '-' }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="status" label="员工状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="EMPLOYEE_STATUS_MAP[row.status]?.type || 'info'" size="small">
            {{ EMPLOYEE_STATUS_MAP[row.status]?.label || row.status || '在职' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="accountStatus" label="账号状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="ACCOUNT_STATUS_MAP[row.account?.accountStatus]?.type || 'info'" size="small">
            {{ ACCOUNT_STATUS_MAP[row.account?.accountStatus]?.label || row.account?.accountStatus || '-' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="hireDate" label="入职日期" min-width="110">
        <template #default="{ row }">
          {{ row.hireDate || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="操作" min-width="220" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button type="primary" link v-permission="EMPLOYEE_PERMISSIONS.EDIT" @click="handleEdit(row)">编辑</el-button>
          <el-button
            v-if="row.account?.accountStatus === 'active'"
            type="warning"
            link
            v-permission="EMPLOYEE_PERMISSIONS.STATUS"
            @click="handleToggleStatus(row)"
          >禁用</el-button>
          <el-button
            v-else-if="row.account?.accountStatus && row.status !== 'left'"
            type="success"
            link
            v-permission="EMPLOYEE_PERMISSIONS.STATUS"
            @click="handleToggleStatus(row)"
          >启用</el-button>
          <el-button type="danger" link v-permission="EMPLOYEE_PERMISSIONS.DELETE" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  flex: 1;
  min-height: 0;
  background: #FFFFFF;
  padding: 0 16px;
  overflow: hidden;

  :deep(.el-table) {
    --el-table-index-cell-vertical-align: middle;
    --el-table-border-color: #E7E7E7;
    --el-table-row-height: 46px;

    .el-table__cell {
      padding-left: 0;
      padding-right: 0;
      font-family: 'PingFang SC', sans-serif;
      font-weight: 400;
      font-size: 14px;
      line-height: 22px;
      color: #000000E5;
    }
  }

  :deep(.el-table__body tr) {
    height: 46px;
    border-bottom: 1px solid #E7E7E7;

    td {
      height: 46px;
    }

    &:nth-child(odd) td {
      background-color: #FFFFFF;
    }

    &:nth-child(even) td {
      background-color: #F5F9FF;
    }
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(.el-table thead th) {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #00000066;
    background-color: #F5F9FF !important;
    border-bottom: 1px solid #E7E7E7;
  }

  :deep(.el-table thead th:first-child) {
    border-top-left-radius: 0;
  }

  :deep(.el-table thead th:last-child) {
    border-top-right-radius: 0;
  }

  :deep(.index-col) {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #000000E5;
  }

  :deep(.el-tag--success) {
    background: #E3F9E9;
    border: 1px solid #2BA471;
    border-radius: 3px;
    color: #2BA471;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  :deep(.el-tag--primary) {
    background: #E8F3FF;
    border: 1px solid #3370FF;
    border-radius: 3px;
    color: #3370FF;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  :deep(.el-tag--warning) {
    background: #FFF1E9;
    border: 1px solid #E37318;
    border-radius: 3px;
    color: #E37318;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  :deep(.el-tag--danger) {
    background: #FFF0ED;
    border: 1px solid #D54941;
    border-radius: 3px;
    color: #D54941;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  /* 操作列：详情、编辑（primary link）按钮颜色 */
  :deep(.el-button--primary.is-link) {
    color: #5570F1;

    &:hover {
      color: #2E45D6;
    }

    &:focus {
      color: #5570F1;
    }
  }

  /* 操作列：删除（danger link）按钮颜色 */
  :deep(.el-button--danger.is-link) {
    color: #FF7474;

    &:hover {
      color: #FF3D3D;
    }

    &:focus {
      color: #FF7474;
    }
  }

  /* 操作列：启用（success link）按钮颜色 */
  :deep(.el-button--success.is-link) {
    color: #43C08B;

    &:hover {
      color: #1E9E6B;
    }

    &:focus {
      color: #43C08B;
    }
  }

  /* 操作列：禁用（warning link）按钮颜色 */
  :deep(.el-button--warning.is-link) {
    color: #ED8A40;

    &:hover {
      color: #C56318;
    }

    &:focus {
      color: #ED8A40;
    }
  }

  /* 操作列：cell 允许溢出，让按钮 focus 描边完整显示 */
  :deep(.action-col .cell) {
    overflow: visible;
  }
}

.role-names {
  font-size: 12px;
  color: #606266;
}
</style>
