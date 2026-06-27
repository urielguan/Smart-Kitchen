<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { AlertRule } from '@/types/alert-rule'
import { THRESHOLD_METRICS } from '@/types/alert-rule'
import { ALERT_RULE_PERMISSIONS } from '@/constants/permission'
import { useUserStore } from '@/stores/modules/user'
import { formatDateTime } from '@/utils'

interface Props {
  data: AlertRule[]
  loading: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  detail: [id: number]
  edit: [id: number]
  delete: [id: number]
  toggleEnabled: [id: number]
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

const getConditionSummary = (rule: { ruleType: string; conditionJson: string }) => {
  try {
    const c = JSON.parse(rule.conditionJson)
    if (rule.ruleType === 'threshold') {
      const conds: any[] = (c.conditions && Array.isArray(c.conditions))
        ? c.conditions
        : [{ metric: c.metric, operator: c.operator, value: c.value }]
      const parts = conds.map(cond => {
        const metricLabel = THRESHOLD_METRICS.find(m => m.value === cond.metric)?.label || cond.metric
        return `${metricLabel} ${cond.operator} ${cond.value}`
      })
      const logicWord = c.logic === 'or' ? ' 或 ' : ' 且 '
      return `${parts.join(logicWord)}${c.duration ? `，持续 ${c.duration}秒` : ''}`
    }
    if (rule.ruleType === 'offline') return `离线超过 ${c.offlineMinutes} 分钟`
    if (rule.ruleType === 'material') return '按物料配置自动检测（效期/库存）'
    return rule.conditionJson
  } catch {
    return rule.conditionJson || '-'
  }
}
</script>

<template>
  <div ref="tableContainerRef" class="rule-table-wrapper">
    <el-table :data="data" v-loading="loading" :height="tableHeight" :cell-style="{ verticalAlign: 'middle' }" style="width: 100%">
      <el-table-column prop="ruleName" label="规则名称" width="180" show-overflow-tooltip />
      <el-table-column prop="ruleTypeName" label="规则类型" width="120" />
      <el-table-column label="设备/物料" width="150">
        <template #default="{ row }">
          {{ row.ruleType === 'material' ? '物料' : row.deviceTypeName }}
        </template>
      </el-table-column>
      <el-table-column label="触发条件" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getConditionSummary(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="alertLevelName" label="告警级别" width="110">
        <template #default="{ row }">
          <el-tag :type="getAlertLevelTag(row.alertLevel)" size="small">{{ row.alertLevelName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.isEnabled === 1"
            @change="emit('toggleEnabled', row.id)"
            :disabled="!hasPermission(ALERT_RULE_PERMISSIONS.STATUS)"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          {{ row.updatedAt ? formatDateTime(row.updatedAt) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('detail', row.id)">详情</el-button>
          <el-button v-if="hasPermission(ALERT_RULE_PERMISSIONS.EDIT)" link type="primary" @click="emit('edit', row.id)">编辑</el-button>
          <el-button v-if="hasPermission(ALERT_RULE_PERMISSIONS.DELETE)" link type="danger" @click="emit('delete', row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.rule-table-wrapper {
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

  :deep(.el-switch.is-checked .el-switch__core) {
    background-color: #7288FA;
    border-color: #7288FA;
  }
}

/* 操作列：cell 允许溢出，让按钮 focus 描边完整显示 */
:deep(.action-col .cell) {
  overflow: visible;
}
</style>
