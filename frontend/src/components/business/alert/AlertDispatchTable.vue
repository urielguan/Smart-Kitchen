<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ALERT_PERMISSIONS } from '@/constants/permission'
import { useUserStore } from '@/stores/modules/user'
import { formatDateTime } from '@/utils'

interface Props {
  data: any[]
  loading: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  detail: [alertId: number]
  process: [dispatchId: number]
  review: [dispatchId: number]
}>()

const userStore = useUserStore()
const hasPermission = (code: string) => userStore.hasPermission(code)

/** 表格容器引用 */
const tableContainerRef = ref<HTMLElement | null>(null)
const tableHeight = ref<number | undefined>(undefined)
let resizeObserver: ResizeObserver | null = null

const updateTableHeight = () => {
  if (tableContainerRef.value) tableHeight.value = tableContainerRef.value.clientHeight
}

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

const getAlertLevelTag = (level: string) => {
  const map: Record<string, string> = { info: 'info', warning: 'warning', error: 'danger', critical: 'danger' }
  return map[level] || 'info'
}

const getDispatchStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    pending: 'warning',
    processing: 'primary',
    completed: 'success',
    reviewed: 'success',
    cancelled: 'info',
    rejected: 'danger',
  }
  return map[status] || 'info'
}

const getPriorityTagType = (priority: string) => {
  const map: Record<string, string> = { high: 'danger', medium: 'warning', low: 'info' }
  return map[priority] || 'info'
}

const isOverdue = (row: any) => {
  return row.deadline && row.status !== 'reviewed' && new Date(row.deadline) < new Date()
}
</script>

<template>
  <div ref="tableContainerRef" class="dispatch-table-wrapper">
    <el-table :data="data" v-loading="loading" :height="tableHeight" :cell-style="{ verticalAlign: 'middle' }" style="width: 100%">
      <el-table-column prop="dispatchNo" label="派单编号" width="170" />
      <el-table-column prop="alertNo" label="告警编号" width="240" />
      <el-table-column label="告警级别" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="getAlertLevelTag(row.alertLevel)" size="small">{{ row.alertLevelName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="alertContent" label="告警内容" min-width="400" show-overflow-tooltip />
      <el-table-column prop="dispatchTypeName" label="派单方式" width="110" />
      <el-table-column prop="assignerName" label="派单人" width="120" />
      <el-table-column prop="handlerName" label="处理人" width="100" />
      <el-table-column label="优先级" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.priorityName" :type="getPriorityTagType(row.priority)" size="small">{{ row.priorityName }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="getDispatchStatusTagType(row.status)" size="small">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="派单时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) || '-' }}</template>
      </el-table-column>
      <el-table-column label="截止时间" width="180">
        <template #default="{ row }">
          <span :style="{ color: isOverdue(row) ? '#F56C6C' : '', fontWeight: isOverdue(row) ? 'bold' : '' }">
            {{ row.deadline ? row.deadline.replace('T', ' ') : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('detail', row.alertId)">详情</el-button>
          <el-button
            v-if="row.status === 'pending' || row.status === 'processing' || row.status === 'rejected'"
            link type="primary"
            v-permission="ALERT_PERMISSIONS.PROCESS"
            @click="emit('process', row.id)"
          >
            处理
          </el-button>
          <el-button
            v-if="row.status === 'completed'"
            link type="warning"
            v-permission="ALERT_PERMISSIONS.REVIEW"
            @click="emit('review', row.id)"
          >
            复核
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.dispatch-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
  padding: 16px 16px 0;
  overflow: hidden;

  :deep(.el-table) {
    flex: 1;
    min-height: 0;
  }

  :deep(.el-table__body tr) {
    height: 46px;
    border-bottom: 1px solid #E7E7E7;

    &:nth-child(odd) td {
      background-color: #FFFFFF;
    }
    &:nth-child(even) td {
      background-color: #F5F9FF;
    }
  }

  :deep(.el-table thead th) {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    color: #00000066;
    background-color: #F5F9FF !important;
    border-bottom: 1px solid #E7E7E7;
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(.el-tag--success) {
    background: #E3F9E9; border: 1px solid #2BA471; border-radius: 3px; color: #2BA471;
    height: 24px; padding: 2px 8px; line-height: 20px;
  }
  :deep(.el-tag--primary) {
    background: #E8F3FF; border: 1px solid #3370FF; border-radius: 3px; color: #3370FF;
    height: 24px; padding: 2px 8px; line-height: 20px;
  }
  :deep(.el-tag--warning) {
    background: #FFF1E9; border: 1px solid #E37318; border-radius: 3px; color: #E37318;
    height: 24px; padding: 2px 8px; line-height: 20px;
  }
  :deep(.el-tag--danger) {
    background: #FFF0ED; border: 1px solid #D54941; border-radius: 3px; color: #D54941;
    height: 24px; padding: 2px 8px; line-height: 20px;
  }
  :deep(.el-tag--info) {
    background: #F4F4F5; border: 1px solid #909399; border-radius: 3px; color: #909399;
    height: 24px; padding: 2px 8px; line-height: 20px;
  }

  :deep(.el-button--primary.is-link) {
    color: #5570F1;
    &:hover { color: #2E45D6; }
  }
  :deep(.el-button--warning.is-link) {
    color: #ED8A40;
    &:hover { color: #D57030; }
  }
}

/* 操作列：cell 允许溢出，让按钮 focus 描边完整显示 */
:deep(.action-col .cell) {
  overflow: visible;
}
</style>
