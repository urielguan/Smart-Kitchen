<script setup lang="ts">
import type { CookTask } from '@/types'

interface Props {
  tasks: CookTask[]
  loading?: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  detail: [task: CookTask]
  temperature: [task: CookTask]
  archive: [task: CookTask]
  'batch-archive': [tasks: CookTask[]]
  'selection-change': [tasks: CookTask[]]
}>()

const statusMap: Record<string, { text: string; type: 'info' | 'warning' | 'primary' | 'success' | 'danger' }> = {
  pending: { text: '待烹饪', type: 'warning' },
  in_progress: { text: '烹饪中', type: 'primary' },
  completed: { text: '已完成', type: 'success' },
  cancelled: { text: '已取消', type: 'info' },
  archived: { text: '已归档', type: 'info' }
}

const mealTypeMap: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  supper: '宵夜'
}

const getStatus = (status: string) => statusMap[status] || { text: status, type: 'info' as const }
const getMealType = (mealType: string) => mealTypeMap[mealType] || mealType
const formatIngredients = (ingredients: string[]) => ingredients?.join('、') || '-'
const formatDateTime = (dt: string | null | undefined) => {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 16)
}
const getExceptionTags = (task: CookTask) => {
  const tags: Array<{ text: string; type: 'danger' | 'warning' | 'info' }> = []
  if (task.collectionStatus === 'interrupted') {
    tags.push({ text: '采集中断', type: 'warning' })
  }
  if (task.hasSyncException || task.syncStatus === 'sync_failed' || task.syncStatus === 'conflict_pending') {
    tags.push({ text: '同步异常', type: 'danger' })
  }
  if (task.hasCompensationPending || task.compensationStatus === 'pending') {
    tags.push({ text: '补偿待处理', type: 'danger' })
  }
  if (task.temperatureAbnormal && !task.tempAbnormalConfirmed) {
    tags.push({ text: '温度待复核', type: 'warning' })
  }
  return tags
}

const canArchive = (task: CookTask): boolean => {
  return task.status === 'completed' && task.reviewStatus === 'approved'
}

const handleSelectionChange = (selection: CookTask[]) => {
  emit('selection-change', selection)
}
</script>

<template>
  <el-table
    :data="tasks"
    v-loading="loading"
    stripe
    border
    empty-text="当前条件下暂无烹饪任务"
    row-key="id"
    @selection-change="handleSelectionChange"
  >
    <el-table-column type="selection" width="45" />
    <el-table-column prop="taskNo" label="任务编号" width="180" show-overflow-tooltip />
    <el-table-column prop="menuName" label="菜品名称" min-width="180" show-overflow-tooltip />
    <el-table-column label="状态" width="110" align="center">
      <template #default="{ row }">
        <el-tag :type="getStatus(row.status).type" size="small">{{ getStatus(row.status).text }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="taskDate" label="实施日期" width="120" />
    <el-table-column label="餐次" width="100">
      <template #default="{ row }">{{ getMealType(row.mealType) }}</template>
    </el-table-column>
    <el-table-column label="计划份数" width="100" align="center">
      <template #default="{ row }">{{ row.plannedQty || 0 }}份</template>
    </el-table-column>
    <el-table-column label="食材" min-width="220" show-overflow-tooltip>
      <template #default="{ row }">{{ formatIngredients(row.ingredients) }}</template>
    </el-table-column>
    <el-table-column prop="chefName" label="烹饪人" width="120">
      <template #default="{ row }">{{ row.chefName || '待指派' }}</template>
    </el-table-column>
    <el-table-column label="烹饪设备" width="120" show-overflow-tooltip>
      <template #default="{ row }">{{ row.deviceName || '未指定' }}</template>
    </el-table-column>
    <el-table-column label="设备位置" width="130" show-overflow-tooltip>
      <template #default="{ row }">{{ row.deviceLocation || '未指定' }}</template>
    </el-table-column>
    <el-table-column label="开始时间" width="160">
      <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
    </el-table-column>
    <el-table-column label="完成时间" width="160">
      <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
    </el-table-column>
    <el-table-column label="备料状态" width="100" align="center">
      <template #default="{ row }">
        <el-tag v-if="row.materialPrepStatus === 'pending_prep'" type="warning" size="small">待备料</el-tag>
        <el-tag v-else-if="row.materialPrepStatus === 'prepared'" type="success" size="small">已备料</el-tag>
        <el-tag v-else type="info" size="small">--</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="AI监控" width="90" align="center">
      <template #default="{ row }">
        <el-tag :type="row.aiViolationCount > 0 ? 'danger' : 'success'" size="small">
          {{ row.aiViolationCount > 0 ? `${row.aiViolationCount}次` : '正常' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="异常状态" min-width="220">
      <template #default="{ row }">
        <div class="exception-tags">
          <el-tag
            v-for="tag in getExceptionTags(row)"
            :key="tag.text"
            :type="tag.type"
            size="small"
          >
            {{ tag.text }}
          </el-tag>
          <span v-if="getExceptionTags(row).length === 0" class="exception-empty">无异常</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="230" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" @click="emit('detail', row)">查看记录</el-button>
        <el-button link type="info" @click="emit('temperature', row)">温度曲线</el-button>
        <el-button v-if="canArchive(row)" link type="warning" @click="emit('archive', row)">归档</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style lang="scss" scoped>
.exception-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.exception-empty {
  color: #909399;
}

.abnormal {
  color: #f56c6c;
  font-weight: 600;
}
</style>