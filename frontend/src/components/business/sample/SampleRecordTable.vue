<script setup lang="ts">
import { ref } from 'vue'
import type { SampleRecord } from '@/types'
import { SAMPLE_STATUS_MAP, MEAL_TYPE_MAP } from '@/constants/sample'
import { SAMPLE_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

interface Props {
  records: SampleRecord[]
  loading?: boolean
  readonlyMode?: boolean
  canUseManualDisposalSupplement?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  readonlyMode: false,
  canUseManualDisposalSupplement: false
})

const emit = defineEmits<{
  detail: [record: SampleRecord]
  register: [record: SampleRecord]
  edit: [record: SampleRecord]
  dispose: [record: SampleRecord]
  manualSupplement: [record: SampleRecord]
  aiEvaluate: [record: SampleRecord]
  voidRecord: [record: SampleRecord]
  archive: [record: SampleRecord]
  selectionChange: [ids: number[]]
}>()

const tableRef = ref()
const sampleDateTimeColumnWidth = 170

const getStatus = (status: string) => SAMPLE_STATUS_MAP[status] || { label: status, type: 'info' }
const getMealType = (mealType: string) => MEAL_TYPE_MAP[mealType] || mealType
const getTaskStatus = (status?: string | null) => {
  const map: Record<string, { label: string; type: string }> = {
    pending: { label: '待烹饪', type: 'info' },
    in_progress: { label: '烹饪中', type: 'warning' },
    completed: { label: '已完成', type: 'success' },
    archived: { label: '已归档', type: '' },
    cancelled: { label: '已取消', type: 'danger' }
  }
  return map[status || ''] || { label: status || '-', type: 'info' }
}

const isOperationLockedByOther = (row: SampleRecord) => Boolean(row.operationLock?.locked && !row.operationLock?.ownedByCurrentUser)
const getOperationLockLabel = (row: SampleRecord) => row.operationLock?.operationTypeLabel || '处理中'
const getOperationLockOperator = (row: SampleRecord) => row.operationLock?.operatorName || '其他用户'

const canDispose = (row: SampleRecord) => !row.rollbackIsolated && !isOperationLockedByOther(row) && ['sampled', 'evaluated', 'pending_disposal', 'overdue'].includes(row.status)
const canManualSupplement = (row: SampleRecord) => props.canUseManualDisposalSupplement && canDispose(row)
const canRegister = (row: SampleRecord) => !row.rollbackIsolated && !isOperationLockedByOther(row) && row.status === 'pending_sample' && ['completed', 'archived'].includes(row.taskStatus || '')
const canEdit = (row: SampleRecord) => !row.rollbackIsolated && !isOperationLockedByOther(row) && row.status === 'sampled'
const canAiEvaluate = (row: SampleRecord) => !row.rollbackIsolated && !isOperationLockedByOther(row) && ['sampled', 'evaluated'].includes(row.status) && row.aiQualityScore == null
const canVoid = (row: SampleRecord) => !row.rollbackIsolated && !isOperationLockedByOther(row) && !['voided', 'archived'].includes(row.status)
const canArchive = (row: SampleRecord) => !row.rollbackIsolated && !isOperationLockedByOther(row) && row.status === 'disposed'

const getRowClassName = ({ row }: { row: SampleRecord }) => {
  if (row.rollbackIsolated) return 'rollback-isolated-row'
  return row.status === 'overdue' ? 'overdue-row' : ''
}

defineExpose({
  clearSelection: () => tableRef.value?.clearSelection()
})
</script>

<template>
  <el-table ref="tableRef" :data="records" v-loading="loading" stripe border empty-text="当前条件下暂无留样记录" :row-class-name="getRowClassName" @selection-change="(selection: SampleRecord[]) => emit('selectionChange', selection.map(r => r.id))">
    <el-table-column v-if="!props.readonlyMode" type="selection" width="50" />
    <el-table-column prop="sampleNo" label="留样编号" width="180">
      <template #default="{ row }">
        <el-tooltip :disabled="!row.sampleNo" :content="row.sampleNo" placement="top">
          <span class="cell-ellipsis">{{ row.sampleNo || '-' }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column prop="sourceLabel" label="来源" width="150" align="center">
      <template #default="{ row }">
        <el-tooltip :disabled="!row.sourceLabel" :content="row.sourceLabel" placement="top">
          <span class="cell-ellipsis">{{ row.sourceLabel || '-' }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column prop="disposalSourceLabel" label="销样来源" width="130" align="center">
      <template #default="{ row }">
        <el-tooltip :disabled="!row.disposalSourceLabel" :content="row.disposalSourceLabel" placement="top">
          <span class="cell-ellipsis">{{ row.disposalSourceLabel || '-' }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column prop="taskNo" label="烹饪任务" width="180">
      <template #default="{ row }">
        <el-tooltip :disabled="!row.taskNo" :content="row.taskNo" placement="top">
          <span class="cell-ellipsis">{{ row.taskNo || '-' }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column label="任务状态" width="110" align="center">
      <template #default="{ row }">
        <el-tag :type="(getTaskStatus(row.taskStatus).type as any)" size="small">
          {{ getTaskStatus(row.taskStatus).label }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="menuName" label="菜谱名称" min-width="160">
      <template #default="{ row }">
        <el-tooltip :disabled="!row.menuName" :content="row.menuName" placement="top">
          <span class="cell-ellipsis">{{ row.menuName || '-' }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column prop="sampleDate" label="留样日期" width="120" />
    <el-table-column label="餐次" width="90" align="center">
      <template #default="{ row }">{{ getMealType(row.mealType) }}</template>
    </el-table-column>
    <el-table-column label="留样重量(g)" width="110" align="center">
      <template #default="{ row }">{{ row.sampleWeight ?? '-' }}</template>
    </el-table-column>
    <el-table-column label="AI评分" width="90" align="center">
      <template #default="{ row }">
        <span v-if="row.aiQualityScore != null">{{ row.aiQualityScore }}</span>
        <span v-else class="text-muted">-</span>
      </template>
    </el-table-column>
    <el-table-column prop="storageLocation" label="存放位置" width="120">
      <template #default="{ row }">
        <el-tooltip :disabled="!row.storageLocation" :content="row.storageLocation" placement="top">
          <span class="cell-ellipsis">{{ row.storageLocation || '-' }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="110" align="center">
      <template #default="{ row }">
        <el-tag :type="(getStatus(row.status).type as any)" size="small">
          {{ getStatus(row.status).label }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作锁" width="170" align="center">
      <template #default="{ row }">
        <template v-if="row.operationLock?.locked">
          <el-tooltip :content="`${getOperationLockOperator(row)}：${getOperationLockLabel(row)}`" placement="top">
            <span class="lock-cell">
              <el-tag :type="row.operationLock?.ownedByCurrentUser ? 'warning' : 'danger'" size="small">
                {{ getOperationLockLabel(row) }}
              </el-tag>
              <span class="lock-owner">{{ row.operationLock?.ownedByCurrentUser ? '本人占用' : getOperationLockOperator(row) }}</span>
            </span>
          </el-tooltip>
        </template>
        <span v-else class="text-muted">空闲</span>
      </template>
    </el-table-column>
    <el-table-column label="留样时间" :width="sampleDateTimeColumnWidth">
      <template #default="{ row }">{{ formatDateTime(row.sampledAt) || '-' }}</template>
    </el-table-column>
    <el-table-column label="应销时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.disposalDueAt) || '-' }}</template>
    </el-table-column>
    <el-table-column :width="sampleDateTimeColumnWidth" label="操作" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" @click="emit('detail', row)">详情</el-button>
        <template v-if="props.readonlyMode || row.rollbackIsolated">
          <el-button link type="info" disabled>已锁定</el-button>
        </template>
        <template v-else-if="isOperationLockedByOther(row)">
          <el-tooltip :content="`当前由${getOperationLockOperator(row)}执行${getOperationLockLabel(row)}，仅支持查看详情`" placement="top">
            <span>
              <el-button link type="info" disabled>{{ getOperationLockLabel(row) }}</el-button>
            </span>
          </el-tooltip>
        </template>
        <template v-else>
          <el-tooltip
            v-if="row.status === 'pending_sample'"
            :disabled="canRegister(row)"
            content="关联烹饪任务未完成，暂不可留样登记"
            placement="top"
          >
            <span>
              <el-button
                link
                type="primary"
                v-permission="SAMPLE_PERMISSIONS.EDIT"
                :disabled="!canRegister(row)"
                @click="emit('register', row)"
              >
                留样登记
              </el-button>
            </span>
          </el-tooltip>
          <el-button v-if="canEdit(row)" link type="primary" v-permission="SAMPLE_PERMISSIONS.EDIT" @click="emit('edit', row)">编辑</el-button>
          <el-button v-if="canDispose(row)" link type="warning" v-permission="SAMPLE_PERMISSIONS.DISPOSE" @click="emit('dispose', row)">销样</el-button>
          <el-button v-if="canManualSupplement(row)" link type="danger" @click="emit('manualSupplement', row)">销样手工补录</el-button>
          <el-button v-if="canAiEvaluate(row)" link type="success" v-permission="SAMPLE_PERMISSIONS.AI_EVALUATE" @click="emit('aiEvaluate', row)">AI评估</el-button>
          <el-button v-if="canVoid(row)" link type="danger" v-permission="SAMPLE_PERMISSIONS.VOID" @click="emit('voidRecord', row)">作废</el-button>
          <el-button v-if="canArchive(row)" link type="info" v-permission="SAMPLE_PERMISSIONS.ARCHIVE" @click="emit('archive', row)">归档</el-button>
        </template>
      </template>
    </el-table-column>
  </el-table>
</template>

<style lang="scss" scoped>
.text-muted {
  color: #c0c4cc;
}

.lock-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.lock-owner {
  font-size: 12px;
  color: #909399;
  line-height: 1;
}

.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

:deep(.overdue-row td) {
  background: #fef0f0 !important;
}

:deep(.rollback-isolated-row td) {
  background: #f5f7fa !important;
  color: #909399;
}
</style>
