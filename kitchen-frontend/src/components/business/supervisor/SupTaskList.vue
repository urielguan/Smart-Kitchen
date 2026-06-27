<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CookTask } from '@/types'
import { formatMealType } from '@/utils/format'
import TaskFilterBar from '@/components/business/shared/TaskFilterBar.vue'
import PaginationBar from '@/components/business/shared/PaginationBar.vue'

const props = defineProps<{
  tasks: CookTask[]
  selectedId?: number | null
  loading?: boolean
  total?: number
  pageNum?: number
  pageSize?: number
}>()

const emit = defineEmits<{
  select: [task: CookTask]
  assign: [task: CookTask]
  'update:pageNum': [page: number]
  'update:pageSize': [size: number]
}>()

type FilterKey = 'all' | 'pending' | 'cooking' | 'done' | 'alert'

const activeFilter = ref<FilterKey>('all')

const filters: { key: FilterKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待烹饪' },
  { key: 'cooking', label: '烹饪中' },
  { key: 'done', label: '已完成' },
  { key: 'alert', label: '异常' }
]

const filteredTasks = computed(() => {
  const source = props.tasks
  let result: CookTask[]
  switch (activeFilter.value) {
    case 'pending':
      result = source.filter((t) => t.status === 'pending')
      break
    case 'cooking':
      result = source.filter((t) => t.status === 'in_progress')
      break
    case 'done':
      result = source.filter((t) => t.status === 'completed' || t.status === 'archived')
      break
    case 'alert':
      result = source.filter((t) => t.temperatureAbnormal || t.aiViolationCount > 0 || t.sensorOnline === false || t.deviceOnline === false)
      break
    default:
      result = [...source]
  }
  // Sort: alert > cooking > pending > done
  const order: Record<string, number> = { alert: 0, cooking: 1, in_progress: 1, pending: 2, done: 3, completed: 3, archived: 3 }
  return result.sort((a, b) => {
    const aState = a.temperatureAbnormal || a.sensorOnline === false ? 'alert' : a.status
    const bState = b.temperatureAbnormal || b.sensorOnline === false ? 'alert' : b.status
    return (order[aState] ?? 4) - (order[bState] ?? 4)
  })
})

const getTaskState = (task: CookTask) => {
  if (task.temperatureAbnormal || task.sensorOnline === false || task.deviceOnline === false) return 'alert'
  if (task.status === 'pending') return 'pending'
  if (task.status === 'in_progress') return 'cooking'
  if (task.status === 'completed' || task.status === 'archived') return 'done'
  return 'pending'
}

const stateLabels: Record<string, string> = {
  pending: '待烹饪',
  cooking: '烹饪中',
  done: '已完成',
  alert: '异常',
  archived: '已归档'
}
</script>

<template>
  <aside class="sup-left">
    <div class="sup-left__header">
      <span class="sup-left__title">全部烹饪任务</span>
      <span class="sup-left__badge">{{ filteredTasks.length }} 项</span>
    </div>

    <TaskFilterBar show-chef-filter show-org-filter />

    <div class="sup-left__filters">
      <button
        v-for="f in filters"
        :key="f.key"
        class="sup-left__fbtn"
        :class="{ 'sup-left__fbtn--on': activeFilter === f.key }"
        @click="activeFilter = f.key"
      >
        {{ f.label }}
      </button>
    </div>

    <div class="sup-left__scroll">
      <div
        v-for="task in filteredTasks"
        :key="task.id"
        class="sup-task"
        :class="[
          `sup-task--${getTaskState(task)}`,
          { 'sup-task--active': task.id === selectedId, 'sup-task--glow': getTaskState(task) === 'alert' }
        ]"
        @click="emit('select', task)"
      >
        <div class="sup-task__top">
          <span class="sup-task__name">{{ task.menuName }}</span>
          <span class="sup-task__badge" :class="`sup-task__badge--${getTaskState(task)}`">
            {{ task.status === 'archived' ? '已归档' : stateLabels[getTaskState(task)] }}
          </span>
        </div>
        <div class="sup-task__date">{{ task.taskDate || (task.startDate && task.endDate ? (task.startDate !== task.endDate ? `${task.startDate}~${task.endDate}` : task.startDate) : (task.planDate || '--')) }} {{ formatMealType(task.mealType) }}</div>
        <div class="sup-task__meta">
          <span>{{ task.deviceName || '--' }}</span>
          <span
            class="sup-task__chef"
            :class="{ 'sup-task__chef--unassigned': !task.chefName && !task.assignedChefName }"
            @click.stop="(task.status === 'pending' || task.status === 'in_progress') && emit('assign', task)"
          >{{ task.chefName || task.assignedChefName || '待分派' }}</span>
          <span :class="task.currentTemp !== null && task.currentTemp !== undefined ? 'sup-task__temp--ok' : ''">
            {{ task.currentTemp ?? '--' }}°C
          </span>
          <span>{{ task.actualDuration ?? task.standardDuration ?? '--' }}min</span>
        </div>
      </div>
      <div v-if="loading && !filteredTasks.length" class="sup-left__empty">加载中...</div>
      <div v-else-if="!filteredTasks.length" class="sup-left__empty">暂无匹配任务</div>
    </div>
    <PaginationBar
      prefix="sup"
      :total="total ?? 0"
      :page-num="pageNum ?? 1"
      :page-size="pageSize ?? 10"
      @update:page-num="emit('update:pageNum', $event)"
      @update:page-size="emit('update:pageSize', $event)"
    />
  </aside>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.sup-left {
  width: 320px;
  min-width: 320px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid $kds-border;
  background: $kds-surface;
}

.sup-left__header {
  padding: 12px 16px 10px;
  border-bottom: 1px solid $kds-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sup-left__title {
  font-size: 13px;
  font-weight: 700;
  color: $kds-text;
  letter-spacing: 0.3px;
}

.sup-left__badge {
  font-family: 'DM Mono', monospace;
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  background: $kds-surface-3;
  color: $kds-text-dim;
}

.sup-left__filters {
  display: flex;
  gap: 5px;
  padding: 8px 16px;
  border-bottom: 1px solid $kds-border;
}

.sup-left__fbtn {
  padding: 4px 11px;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 600;
  background: $kds-surface-2;
  color: $kds-text-dim;
  border: 1px solid transparent;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: $kds-border-light;
    color: $kds-text;
  }

  &--on {
    background: rgba(79, 140, 255, 0.12);
    color: #4f8cff;
    border-color: rgba(79, 140, 255, 0.3);
  }
}

.sup-left__scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}

.sup-task {
  padding: 11px 13px;
  margin-bottom: 5px;
  border-radius: $kds-radius-md;
  background: $kds-surface-2;
  border: 1px solid $kds-border;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    border-radius: 3px 0 0 3px;
  }

  &--pending::before { background: $kds-amber; }
  &--cooking::before { background: $kds-green; }
  &--done::before { background: $kds-indigo; }
  &--alert::before { background: $kds-red; }
  &--archived::before { background: $kds-indigo; }

  &:hover { border-color: $kds-border-light; }

  &--active {
    border-color: #4f8cff;
    background: rgba(79, 140, 255, 0.08);
  }

  &--glow {
    animation: aglow 1.5s infinite;
  }
}

@keyframes aglow {
  0%, 100% { box-shadow: 0 0 0 0 rgba(248, 113, 113, 0.25); }
  50% { box-shadow: 0 0 10px 3px rgba(248, 113, 113, 0.25); }
}

.sup-task__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 4px;
}

.sup-task__name {
  font-size: 14px;
  font-weight: 700;
  color: $kds-text-bright;
}

.sup-task__badge {
  font-size: 9px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;

  &--pending { background: rgba(240, 160, 48, 0.1); color: $kds-amber; }
  &--cooking { background: $kds-green-dim; color: $kds-green; }
  &--done { background: $kds-indigo-dim; color: $kds-indigo; }
  &--alert { background: $kds-red-dim; color: $kds-red; }
  &--archived { background: $kds-indigo-dim; color: $kds-indigo; }
}

.sup-task__meta {
  display: flex;
  gap: 10px;
  font-size: 10px;
  color: $kds-text-dim;
  margin-top: 3px;
}

.sup-task__date {
  font-size: 10px;
  color: $kds-text-dim;
  margin-top: 2px;
  letter-spacing: 0.3px;
}

.sup-task__chef {
  cursor: default;

  &--unassigned {
    color: $kds-amber;
    cursor: pointer;

    &:hover { text-decoration: underline; }
  }
}

.sup-task__temp--ok {
  color: $kds-green;
}

.sup-left__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $kds-text-muted;
  font-size: 14px;
  padding: 40px 0;
}
</style>
