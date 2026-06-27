<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CookTask, CookTaskActionState } from '@/types'
import { formatMealType, formatTaskStatus } from '@/utils/format'

const props = defineProps<{
  tasks: CookTask[]
  loading?: boolean
  actionLoadingId?: number | null
  getStartActionState: (task: CookTask) => CookTaskActionState
  getCompleteActionState: (task: CookTask) => CookTaskActionState
}>()

const emit = defineEmits<{
  detail: [task: CookTask]
  start: [task: CookTask]
  complete: [task: CookTask]
}>()

const { t } = useI18n()

const getTaskVariant = (task: CookTask) => {
  if (task.sensorOnline === false || task.deviceOnline === false || task.temperatureAbnormal) {
    return 'critical'
  }

  if (task.aiViolationCount > 0) {
    return 'warning'
  }

  if (task.status === 'in_progress') {
    return 'active'
  }

  return 'normal'
}

const getTaskSignal = (task: CookTask) => {
  if (task.sensorOnline === false || task.deviceOnline === false) {
    return '设备离线'
  }

  if (task.temperatureAbnormal) {
    return '温度异常'
  }

  if (task.aiViolationCount > 0) {
    return `AI 预警 ${task.aiViolationCount}`
  }

  return '状态正常'
}

const getTaskDurationText = (task: CookTask) => {
  if (task.status === 'in_progress') {
    return `${task.actualDuration ?? 0} min`
  }

  if (task.status === 'completed') {
    return `${task.actualDuration ?? '--'} min`
  }

  return `${task.standardDuration ?? '--'} min`
}
</script>

<template>
  <div class="task-card-list" v-loading="props.loading">
    <div v-for="task in props.tasks" :key="task.id" class="task-card" :class="`task-card--${getTaskVariant(task)}`">
      <div class="task-card__signal">{{ getTaskSignal(task) }}</div>
      <div class="task-card__header">
        <div>
          <div class="task-card__title">{{ task.menuName }}</div>
          <div class="task-card__meta">{{ task.taskNo }} · {{ formatMealType(task.mealType) }}</div>
        </div>
        <el-tag size="large" effect="dark" :type="getTaskVariant(task) === 'critical' ? 'danger' : getTaskVariant(task) === 'warning' ? 'warning' : getTaskVariant(task) === 'active' ? 'success' : 'info'">
          {{ formatTaskStatus(task.status) }}
        </el-tag>
      </div>

      <div class="task-card__core-grid">
        <div class="task-card__core-item">
          <span>执行厨师</span>
          <strong>{{ task.assignedChefName || task.chefName || '--' }}</strong>
        </div>
        <div class="task-card__core-item">
          <span>时长压力</span>
          <strong>{{ getTaskDurationText(task) }}</strong>
        </div>
        <div class="task-card__core-item">
          <span>当前温度</span>
          <strong>{{ task.currentTemp ?? '--' }}{{ task.currentTemp !== null && task.currentTemp !== undefined ? '°C' : '' }}</strong>
        </div>
        <div class="task-card__core-item">
          <span>质量状态</span>
          <strong>{{ getTaskSignal(task) }}</strong>
        </div>
      </div>

      <div class="task-card__actions">
        <el-button class="touch-secondary-button" @click="emit('detail', task)">{{ t('tasks.detail') }}</el-button>
        <div v-if="props.getStartActionState(task).visible" class="task-card__action-item">
          <el-tooltip :disabled="props.getStartActionState(task).enabled || !props.getStartActionState(task).reason" :content="props.getStartActionState(task).reason" placement="top">
            <el-button
              type="warning"
              class="touch-primary-button task-card__action-button"
              :loading="props.actionLoadingId === task.id"
              :disabled="!props.getStartActionState(task).enabled"
              @click="emit('start', task)"
            >
              {{ t('tasks.start') }}
            </el-button>
          </el-tooltip>
          <div v-if="!props.getStartActionState(task).enabled && props.getStartActionState(task).reason" class="task-card__action-reason">
            {{ props.getStartActionState(task).reason }}
          </div>
        </div>
        <div v-if="props.getCompleteActionState(task).visible" class="task-card__action-item">
          <el-button
            type="success"
            class="touch-primary-button task-card__action-button"
            :loading="props.actionLoadingId === task.id"
            :disabled="!props.getCompleteActionState(task).enabled"
            @click="emit('complete', task)"
          >
            {{ t('tasks.complete') }}
          </el-button>
          <div v-if="!props.getCompleteActionState(task).enabled && props.getCompleteActionState(task).reason" class="task-card__action-reason">
            {{ props.getCompleteActionState(task).reason }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.task-card-list {
  display: grid;
  gap: 16px;
}

.task-card {
  @include panel-card;
  padding: 20px;
  border: 2px solid transparent;
  display: grid;
  gap: 16px;
}

.task-card--normal {
  background: $bg-panel;
  border-color: rgba(100, 116, 139, 0.18);
}

.task-card--active {
  background: linear-gradient(180deg, rgba(240, 253, 244, 0.98), rgba(220, 252, 231, 0.98));
  border-color: rgba(47, 179, 109, 0.36);
}

.task-card--warning {
  background: linear-gradient(180deg, rgba(255, 247, 230, 0.98), rgba(255, 237, 213, 0.98));
  border-color: rgba(255, 176, 32, 0.42);
}

.task-card--critical {
  background: linear-gradient(180deg, rgba(255, 241, 242, 0.98), rgba(254, 226, 226, 0.98));
  border-color: rgba(239, 68, 68, 0.48);
}

.task-card__signal {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  padding: 0 16px;
  border-radius: 999px;
  font-size: 16px;
  font-weight: 700;
  width: fit-content;
  background: rgba(15, 23, 42, 0.9);
  color: $text-on-dark;
}

.task-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.task-card__title {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.2;
}

.task-card__meta {
  margin-top: 6px;
  color: $text-regular;
  font-size: 16px;
}

.task-card__core-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.task-card__core-item {
  min-height: 92px;
  padding: 14px;
  border-radius: $border-radius-large;
  background: rgba(255, 255, 255, 0.92);
  display: grid;
  gap: 8px;

  span {
    color: $text-secondary;
    font-size: 14px;
    font-weight: 600;
  }

  strong {
    font-size: 24px;
    line-height: 1.2;
  }
}

.task-card__actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.task-card__action-item {
  display: grid;
  gap: 8px;
}

.task-card__action-button {
  min-height: 64px;
  font-size: 20px;
}

.task-card__action-reason {
  font-size: 15px;
  line-height: 1.5;
  color: $danger-color;
  font-weight: 600;
}

.task-card__actions :deep(.el-button) {
  width: 100%;
}

@media (max-width: 1200px) {
  .task-card__core-grid,
  .task-card__actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .task-card__core-grid,
  .task-card__actions {
    grid-template-columns: 1fr;
  }
}
</style>
