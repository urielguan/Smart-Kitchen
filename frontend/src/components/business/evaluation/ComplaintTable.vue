<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { Complaint } from '@/types/evaluation'
import {
  COMPLAINT_SOURCE_MAP,
  COMPLAINT_STATUS_MAP,
  DISPATCH_TYPE_MAP,
  PRIORITY_MAP
} from '@/constants/evaluation'
import { formatDateTime } from '@/utils'
import { EVALUATION_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: Complaint[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  detail: [complaint: Complaint]
  autoDispatch: [complaint: Complaint]
  manualDispatch: [complaint: Complaint]
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

/** 获取来源名称 */
const getSourceName = (source: string): string => {
  return COMPLAINT_SOURCE_MAP[source as keyof typeof COMPLAINT_SOURCE_MAP] || source
}

/** 获取状态标签 */
const getStatusTag = (status: string): { label: string; type: string } => {
  return COMPLAINT_STATUS_MAP[status as keyof typeof COMPLAINT_STATUS_MAP] || { label: status, type: 'info' }
}

/** 获取派单方式标签 */
const getDispatchTag = (dispatchType: string): { label: string; type: string } | undefined => {
  if (!dispatchType) return undefined
  return DISPATCH_TYPE_MAP[dispatchType as keyof typeof DISPATCH_TYPE_MAP]
}

/** 获取优先级标签 */
const getPriorityTag = (priority: string): { label: string; type: string } | undefined => {
  if (!priority) return undefined
  return PRIORITY_MAP[priority as keyof typeof PRIORITY_MAP]
}

/** 判断是否可以派单 */
const canDispatch = (row: Complaint): boolean => {
  return row.status === 'pending'
}

/** 详情 */
const handleDetail = (row: Complaint) => emit('detail', row)

/** 自动派单 */
const handleAutoDispatch = (row: Complaint) => emit('autoDispatch', row)

/** 人工派单 */
const handleManualDispatch = (row: Complaint) => emit('manualDispatch', row)

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

      <el-table-column prop="complaintNo" label="投诉编号" min-width="120">
        <template #default="{ row }">
          {{ row.complaintNo }}
        </template>
      </el-table-column>

      <el-table-column prop="source" label="来源" min-width="100">
        <template #default="{ row }">
          {{ getSourceName(row.source) }}
        </template>
      </el-table-column>

      <el-table-column prop="title" label="标题" min-width="150">
        <template #default="{ row }">
          {{ row.title }}
        </template>
      </el-table-column>

      <el-table-column prop="submitterName" label="投诉人" min-width="80" />

      <el-table-column prop="orgName" label="门店" min-width="120" />

      <el-table-column prop="status" label="处理状态" min-width="90">
        <template #default="{ row }">
          <el-tag :type="getStatusTag(row.status).type" size="small">
            {{ getStatusTag(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="dispatchType" label="派单方式" min-width="90">
        <template #default="{ row }">
          <el-tag
            v-if="row.dispatchType"
            :type="getDispatchTag(row.dispatchType)?.type"
            size="small"
          >
            {{ getDispatchTag(row.dispatchType)?.label }}
          </el-tag>
          <span v-else class="no-dispatch">-</span>
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

      <el-table-column prop="createdAt" label="创建时间" min-width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="操作" min-width="180" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button
            v-if="canDispatch(row)"
            type="success"
            link
            v-permission="EVALUATION_PERMISSIONS.DISPATCH"
            @click="handleAutoDispatch(row)"
          >
            自动派单
          </el-button>
          <el-button
            v-if="canDispatch(row)"
            type="warning"
            link
            v-permission="EVALUATION_PERMISSIONS.DISPATCH"
            @click="handleManualDispatch(row)"
          >
            人工派单
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

:deep(.el-button--warning.is-link) {
  color: #ED8A40;
  &:hover { color: #C56318; }
  &:focus { color: #ED8A40; }
}

:deep(.action-col .cell) {
  overflow: visible;
}

.no-dispatch {
  color: #909399;
}
</style>