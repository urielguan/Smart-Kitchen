<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { DispatchRecord } from '@/types/evaluation'
import {
  DISPATCH_TYPE_MAP,
  DISPATCH_STATUS_MAP,
  PRIORITY_MAP
} from '@/constants/evaluation'
import { formatDateTime } from '@/utils'
import { EVALUATION_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: DispatchRecord[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  detail: [dispatch: DispatchRecord]
  process: [dispatch: DispatchRecord]
}>()

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

/** 获取派单方式标签 */
const getDispatchTag = (dispatchType: string): { label: string; type: string } => {
  return DISPATCH_TYPE_MAP[dispatchType as keyof typeof DISPATCH_TYPE_MAP] || { label: dispatchType, type: 'info' }
}

/** 获取状态标签 */
const getStatusTag = (status: string): { label: string; type: string } => {
  return DISPATCH_STATUS_MAP[status as keyof typeof DISPATCH_STATUS_MAP] || { label: status, type: 'info' }
}

/** 获取优先级标签 */
const getPriorityTag = (priority: string): { label: string; type: string } | undefined => {
  if (!priority) return undefined
  return PRIORITY_MAP[priority as keyof typeof PRIORITY_MAP]
}

/** 判断是否可以处理 */
const canProcess = (row: DispatchRecord): boolean => {
  return row.status === 'pending' || row.status === 'processing'
}

/** 详情 */
const handleDetail = (row: DispatchRecord) => emit('detail', row)

/** 处理 */
const handleProcess = (row: DispatchRecord) => emit('process', row)

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
    >
      <el-table-column type="index" label="序号" width="60" align="center" />

      <el-table-column prop="dispatchNo" label="派单编号" min-width="120">
        <template #default="{ row }">
          {{ row.dispatchNo }}
        </template>
      </el-table-column>

      <el-table-column prop="complaintNo" label="投诉编号" min-width="120">
        <template #default="{ row }">
          {{ row.complaintNo }}
        </template>
      </el-table-column>

      <el-table-column prop="complaintTitle" label="标题" min-width="150">
        <template #default="{ row }">
          {{ row.complaintTitle || '-' }}
        </template>
      </el-table-column>

      <el-table-column prop="dispatchType" label="派单方式" min-width="90">
        <template #default="{ row }">
          <el-tag :type="getDispatchTag(row.dispatchType).type" size="small">
            {{ getDispatchTag(row.dispatchType).label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="assignerName" label="派单人" min-width="80">
        <template #default="{ row }">
          {{ row.assignerName || '系统' }}
        </template>
      </el-table-column>

      <el-table-column prop="handlerName" label="处理人" min-width="80" />

      <el-table-column prop="createdAt" label="派单时间" min-width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column prop="deadline" label="截止时间" min-width="160">
        <template #default="{ row }">
          <span :class="{ 'overdue': row.deadline && new Date(row.deadline) < new Date() && row.status != 'completed' && row.status != 'cancelled' }">
            {{ row.deadline ? formatDateTime(row.deadline) : '-' }}
          </span>
        </template>
      </el-table-column>

      <el-table-column prop="status" label="状态" min-width="90">
        <template #default="{ row }">
          <el-tag :type="getStatusTag(row.status).type" size="small">
            {{ getStatusTag(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="priority" label="优先级" min-width="70">
        <template #default="{ row }">
          <el-tag
            v-if="row.priority"
            :type="getPriorityTag(row.priority)?.type"
            size="small"
          >
            {{ getPriorityTag(row.priority)?.label }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" min-width="140" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button
            v-if="canProcess(row)"
            type="success"
            link
            v-permission="EVALUATION_PERMISSIONS.PROCESS"
            @click="handleProcess(row)"
          >
            处理
          </el-button>
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
}

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

:deep(.el-tag--info) {
  background: #F4F4F5;
  border: 1px solid #909399;
  border-radius: 3px;
  color: #909399;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.el-button--primary.is-link) {
  color: #5570F1;
  &:hover { color: #2E45D6; }
  &:focus { color: #5570F1; }
}

:deep(.el-button--success.is-link) {
  color: #43C08B;
  &:hover { color: #1E9E6B; }
  &:focus { color: #43C08B; }
}

:deep(.action-col .cell) {
  overflow: visible;
}

.overdue {
  color: #f56c6c;
  font-weight: 500;
}
</style>