<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { Alert } from '@/types/alert'
import { ALERT_LEVELS, ALERT_STATUS } from '@/types/alert'
import { ALERT_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'
import { CircleCloseFilled, Document } from '@element-plus/icons-vue'

interface Props {
  data: Alert[]
  loading: boolean
  error?: string | null
}

defineProps<Props>()
const emit = defineEmits<{
  detail: [id: number]
  close: [id: number]
  autoDispatch: [id: number]
  manualDispatch: [id: number]
  retry: []
}>()

const levelMap = Object.fromEntries(ALERT_LEVELS.map(l => [l.value, l]))
const statusMap = Object.fromEntries(ALERT_STATUS.map(s => [s.value, s]))

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

const getLevelTagType = (level: string) => {
  const map: Record<string, string> = {
    critical: 'danger',
    error: 'danger',
    warning: 'warning',
    info: 'info',
  }
  return map[level] || 'info'
}

const extractMaterialName = (content: string) => {
  if (!content) return '-'
  const match = content.match(/物料「([^」]+)」/)
  return match ? match[1] : '-'
}

const getStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    pending: 'warning',
    assigned: 'primary',
    handling: 'warning',
    handled: 'success',
    reviewed: 'success',
    closed: 'info',
  }
  return map[status] || 'info'
}

const getCloseDisabledReason = (row: Alert) => {
  if (row.status === 'closed') return '该告警已关闭'
  if (row.status !== 'reviewed') return '仅复核通过后的告警允许关闭'
  return ''
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
</script>

<template>
  <div ref="tableContainerRef" class="alert-table-wrapper">
    <el-table :data="data" v-loading="loading" :height="tableHeight" :cell-style="{ verticalAlign: 'middle' }" style="width: 100%">
      <template #empty>
        <div v-if="error" class="table-error-state">
          <el-icon :size="32" color="var(--el-color-danger)"><CircleCloseFilled /></el-icon>
          <p class="table-error-title">加载失败</p>
          <p class="table-error-msg">{{ error }}</p>
          <el-button type="primary" link @click="emit('retry')">重试</el-button>
        </div>
        <div v-else class="table-empty-state">
          <el-icon :size="32" color="var(--el-text-color-placeholder)"><Document /></el-icon>
          <p>暂无告警数据</p>
        </div>
      </template>
      <el-table-column prop="alertNo" label="告警编号" width="240" />
      <el-table-column label="告警级别" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="getLevelTagType(row.alertLevel)" size="small">
            {{ row.alertLevelName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="alertTypeName" label="告警类型" width="120" />
      <el-table-column label="设备/物料" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.alertType === 'material' ? extractMaterialName(row.alertContent) : row.deviceName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="alertContent" label="告警内容" min-width="400" show-overflow-tooltip />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)" size="small">
            {{ row.statusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assignedToName" label="处理人" width="100">
        <template #default="{ row }">
          {{ row.assignedToName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="触发时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.triggeredAt) || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button type="primary" link @click="emit('detail', row.id)">
            详情
          </el-button>
          <el-button
            v-if="row.status === 'pending'"
            type="success" link
            v-permission="ALERT_PERMISSIONS.DISPATCH"
            @click="emit('autoDispatch', row.id)"
          >
            自动派单
          </el-button>
          <el-button
            v-if="row.status === 'pending'"
            type="warning" link
            v-permission="ALERT_PERMISSIONS.DISPATCH"
            @click="emit('manualDispatch', row.id)"
          >
            人工派单
          </el-button>
          <el-tooltip
            v-if="row.status !== 'closed'"
            :disabled="!getCloseDisabledReason(row)"
            :content="getCloseDisabledReason(row)"
            placement="top"
          >
            <span>
              <el-button
                type="danger"
                link
                v-permission="ALERT_PERMISSIONS.CLOSE"
                :disabled="!!getCloseDisabledReason(row)"
                @click="emit('close', row.id)"
              >
                关闭
              </el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.alert-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
  padding: 0 16px;
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
  :deep(.el-button--danger.is-link) {
    color: #FF7474;
    &:hover { color: #FF3D3D; }
  }
  :deep(.el-button--warning.is-link) {
    color: #ED8A40;
    &:hover { color: #D57030; }
  }
  :deep(.el-button--success.is-link) {
    color: #43C08B;
    &:hover { color: #36A576; }
  }
}

/* 操作列：cell 允许溢出，让按钮 focus 描边完整显示；统一按钮间距 */
:deep(.action-col .cell) {
  overflow: visible;
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.action-col .cell .el-button) {
  margin-left: 0;
}

/* tooltip 触发 span 居中对齐其内部按钮，避免垂直错位 */
:deep(.action-col .cell > span) {
  display: flex;
  align-items: center;
}
.table-error-state,
.table-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
  gap: 8px;
  p { margin: 0; color: var(--el-text-color-secondary); font-size: 14px; }
}
.table-error-title {
  font-weight: 500;
  color: var(--el-color-danger) !important;
}
.table-error-msg {
  font-size: 12px !important;
  color: var(--el-text-color-placeholder) !important;
  max-width: 320px;
  word-break: break-all;
}
</style>
