<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import type { Evaluation } from '@/types/evaluation'
import {
  EVALUATION_TYPE_MAP,
  PROCESS_STATUS_MAP,
  DISPATCH_TYPE_MAP,
  PRIORITY_MAP
} from '@/constants/evaluation'
import { formatDateTime } from '@/utils'

interface Props {
  data: Evaluation[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  detail: [evaluation: Evaluation]
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

/** 获取类型标签 */
const getTypeTag = (type: string): { label: string; type: string } => {
  return EVALUATION_TYPE_MAP[type as keyof typeof EVALUATION_TYPE_MAP] || { label: type, type: 'info' }
}

/** 获取状态标签 */
const getStatusTag = (status: string): { label: string; type: string } => {
  return PROCESS_STATUS_MAP[status as keyof typeof PROCESS_STATUS_MAP] || { label: status, type: 'info' }
}

/** 获取派单方式标签 */
const getDispatchTag = (dispatchType: string): { label: string; type: string } | undefined => {
  if (!dispatchType || dispatchType === 'none') return undefined
  return DISPATCH_TYPE_MAP[dispatchType as keyof typeof DISPATCH_TYPE_MAP]
}

/** 获取优先级标签 */
const getPriorityTag = (priority: string): { label: string; type: string } | undefined => {
  if (!priority) return undefined
  return PRIORITY_MAP[priority as keyof typeof PRIORITY_MAP]
}

/** 判断是否超期 */
const isOverdue = (deadline: string | undefined): boolean => {
  if (!deadline) return false
  return new Date() > new Date(deadline)
}

/** 详情 */
const handleDetail = (row: Evaluation) => emit('detail', row)

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
      stripe
      :height="tableHeight"
    >
      <el-table-column type="index" label="序号" width="60" align="center" />

      <el-table-column prop="orderNo" label="单据编号" min-width="150">
        <template #default="{ row }">
          <code class="order-code">{{ row.orderNo }}</code>
        </template>
      </el-table-column>

      <el-table-column prop="type" label="类型" min-width="80">
        <template #default="{ row }">
          <el-tag :type="getTypeTag(row.type).type" size="small">
            {{ getTypeTag(row.type).label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="title" label="标题" min-width="150">
        <template #default="{ row }">
          <strong>{{ row.dishName || row.title }}</strong>
        </template>
      </el-table-column>

      <el-table-column prop="dispatchType" label="派单方式" min-width="100">
        <template #default="{ row }">
          <el-tag
            v-if="row.dispatchType && row.dispatchType !== 'none'"
            :type="getDispatchTag(row.dispatchType)?.type"
            size="small"
          >
            {{ getDispatchTag(row.dispatchType)?.label }}
          </el-tag>
          <span v-else class="no-dispatch">-</span>
        </template>
      </el-table-column>

      <el-table-column prop="assignerName" label="派单人" min-width="80">
        <template #default="{ row }">
          {{ row.assignerName || '系统' }}
        </template>
      </el-table-column>

      <el-table-column prop="handlerName" label="处理人" min-width="80">
        <template #default="{ row }">
          {{ row.handlerName || '-' }}
        </template>
      </el-table-column>

      <el-table-column prop="assignTime" label="派单时间" min-width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.assignTime) }}
        </template>
      </el-table-column>

      <el-table-column prop="deadline" label="截止时间" min-width="160">
        <template #default="{ row }">
          <span :class="{ overdue: isOverdue(row.deadline) }">
            {{ formatDateTime(row.deadline) }}
          </span>
        </template>
      </el-table-column>

      <el-table-column prop="processStatus" label="处理状态" min-width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusTag(row.processStatus).type" size="small">
            {{ getStatusTag(row.processStatus).label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="priority" label="优先级" min-width="80">
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

      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  flex: 1;
  min-height: 0;
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  overflow: hidden;
}

.order-code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
}

.no-dispatch {
  color: #909399;
}

.overdue {
  color: #f56c6c;
}
</style>
