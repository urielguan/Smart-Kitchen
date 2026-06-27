<script setup lang="ts">
import type { CookTask } from '@/types'
import { formatMealType } from '@/utils/format'

const props = defineProps<{
  task: CookTask
  active?: boolean
}>()

const emit = defineEmits<{
  click: [task: CookTask]
}>()

const stateMap: Record<string, { label: string; color: string }> = {
  pending: { label: '待执行', color: 'amber' },
  in_progress: { label: '烹饪中', color: 'green' },
  completed: { label: '已完成', color: 'indigo' },
  archived: { label: '已归档', color: 'indigo' },
  cancelled: { label: '已取消', color: 'dim' }
}

const getState = () => {
  if (props.task.temperatureAbnormal || props.task.sensorOnline === false || props.task.deviceOnline === false) {
    return { label: '异常', color: 'red' }
  }
  if (props.task.aiViolationCount > 0) {
    return { label: 'AI预警', color: 'red' }
  }
  return stateMap[props.task.status] || { label: props.task.status, color: 'dim' }
}
</script>

<template>
  <div
    class="kds-task-card"
    :class="[{ 'kds-task-card--active': active }, `kds-task-card--${getState().color}`]"
    @click="emit('click', task)"
  >
    <div class="kds-task-card__border" />
    <div class="kds-task-card__content">
      <div class="kds-task-card__header">
        <span class="kds-task-card__name">{{ task.menuName }}</span>
        <span class="kds-task-card__state" :class="`kds-task-card__state--${getState().color}`">
          {{ getState().label }}
        </span>
      </div>
      <div class="kds-task-card__meta">
        <span>{{ task.deviceName || '--' }}</span>
        <span>{{ task.chefName || task.assignedChefName || '待分派' }}</span>
      </div>
      <div class="kds-task-card__footer">
        <span class="kds-task-card__temp">{{ task.currentTemp ?? '--' }}°C</span>
        <span class="kds-task-card__meal">{{ formatMealType(task.mealType) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.kds-task-card {
  display: flex;
  border-radius: $kds-radius-md;
  background: $kds-surface;
  border: 1px solid $kds-border;
  cursor: pointer;
  transition: all 0.15s ease;
  overflow: hidden;

  &:hover {
    background: $kds-surface-2;
    border-color: $kds-border-light;
  }

  &--active {
    background: $kds-surface-2;
    border-color: $kds-amber;
  }
}

.kds-task-card__border {
  width: 4px;
  flex-shrink: 0;

  .kds-task-card--amber & { background: $kds-amber; }
  .kds-task-card--green & { background: $kds-green; }
  .kds-task-card--indigo & { background: $kds-indigo; }
  .kds-task-card--red & { background: $kds-red; }
  .kds-task-card--dim & { background: $kds-text-muted; }
}

.kds-task-card__content {
  flex: 1;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.kds-task-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.kds-task-card__name {
  font-size: $kds-font-md;
  font-weight: 700;
  color: $kds-text-bright;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kds-task-card__state {
  font-size: $kds-font-xs;
  font-weight: 700;
  padding: 2px 10px;
  border-radius: 999px;
  white-space: nowrap;
  flex-shrink: 0;

  &--amber { background: $kds-amber-dim; color: $kds-amber; }
  &--green { background: $kds-green-dim; color: $kds-green; }
  &--indigo { background: $kds-indigo-dim; color: $kds-indigo; }
  &--red {
    background: $kds-red-dim;
    color: $kds-red;
    animation: kds-pulse 2s infinite;
  }
  &--dim { background: $kds-surface-3; color: $kds-text-dim; }
}

.kds-task-card__meta {
  display: flex;
  gap: 12px;
  font-size: $kds-font-sm;
  color: $kds-text-dim;
}

.kds-task-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.kds-task-card__temp {
  font-size: $kds-font-base;
  font-weight: 700;
  color: $kds-green;
}

.kds-task-card__meal {
  font-size: $kds-font-xs;
  color: $kds-text-muted;
}
</style>
