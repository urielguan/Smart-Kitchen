<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CookTask } from '@/types'
import { formatMealType } from '@/utils/format'
import { useCookStore } from '@/stores/modules/cook'
import TaskFilterBar from '@/components/business/shared/TaskFilterBar.vue'
import PaginationBar from '@/components/business/shared/PaginationBar.vue'

const cookStore = useCookStore()

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
  'update:pageNum': [page: number]
  'update:pageSize': [size: number]
}>()

type FilterKey = 'mine' | 'available' | 'all'
type AssignmentState = 'mine' | 'available' | 'taken'

const activeFilter = ref<FilterKey>('mine')

const filters: { key: FilterKey; label: string }[] = [
  { key: 'mine', label: '我的任务' },
  { key: 'available', label: '待接取' },
  { key: 'all', label: '全部' }
]

function getAssignmentState(task: CookTask): AssignmentState {
  if (task.assignedChefId && cookStore.matchesCurrentUser(task.assignedChefId)) return 'mine'
  if (!task.assignedChefId && task.status === 'pending') return 'available'
  return 'taken'
}

const filteredTasks = computed(() => {
  const source = props.tasks
  let result: CookTask[]

  switch (activeFilter.value) {
    case 'mine':
      result = source.filter(t =>
        t.assignedChefId && cookStore.matchesCurrentUser(t.assignedChefId)
      )
      break
    case 'available':
      result = source.filter(t =>
        !t.assignedChefId && t.status === 'pending'
      )
      break
    default:
      result = [...source]
  }

  const order: Record<string, number> = { in_progress: 0, pending: 1, completed: 2, archived: 3 }
  return result.sort((a, b) => (order[a.status] ?? 4) - (order[b.status] ?? 4))
})

const emptyMessages: Record<FilterKey, string> = {
  mine: '您当前没有分配的任务',
  available: '当前没有可接取的任务',
  all: '当前餐次没有烹饪任务'
}

function statusClass(task: CookTask): string {
  if (task.status === 'in_progress') return 'cooking'
  if (task.status === 'completed') return 'done'
  if (task.status === 'archived') return 'archived'
  return task.status
}
</script>

<template>
  <aside class="chef-left">
    <div class="chef-left__header">
      <span class="chef-left__title">我的烹饪任务</span>
      <span class="chef-left__badge">{{ filteredTasks.length }} 项</span>
    </div>

    <TaskFilterBar show-org-filter show-location-filter show-alert-level-filter />

    <div class="chef-left__filters">
      <button
        v-for="f in filters"
        :key="f.key"
        class="chef-left__fbtn"
        :class="{ 'chef-left__fbtn--on': activeFilter === f.key }"
        @click="activeFilter = f.key"
      >
        {{ f.label }}
      </button>
    </div>

    <div class="chef-left__scroll">
      <div
        v-for="task in filteredTasks"
        :key="task.id"
        class="chef-task"
        :class="[
          `chef-task--${statusClass(task)}`,
          { 'chef-task--active': task.id === selectedId },
          `chef-task--assign-${getAssignmentState(task)}`
        ]"
        @click="emit('select', task)"
      >
        <div class="chef-task__header">
          <span class="chef-task__name">{{ task.menuName }}</span>
          <span v-if="getAssignmentState(task) === 'mine'" class="chef-task__owner chef-task__owner--mine">我</span>
          <span v-else-if="getAssignmentState(task) === 'taken'" class="chef-task__owner chef-task__owner--other">
            {{ task.assignedChefName || '已指派' }}
          </span>
        </div>
        <div class="chef-task__date">{{ task.taskDate || (task.startDate && task.endDate ? (task.startDate !== task.endDate ? `${task.startDate}~${task.endDate}` : task.startDate) : (task.planDate || '--')) }} {{ formatMealType(task.mealType) }}</div>
        <div class="chef-task__row">
          <span>{{ task.deviceName || '--' }}</span>
          <span :class="(task.currentTemp ?? 0) >= (task.targetTemp ?? 0) ? 'chef-task__temp--ok' : 'chef-task__temp--bad'">
            {{ task.currentTemp ?? '--' }}°C
          </span>
          <span>{{ task.actualDuration ?? task.standardDuration ?? '--' }}min</span>
          <span class="chef-task__badge" :class="`chef-task__badge--${statusClass(task)}`">
            {{ task.status === 'in_progress' ? '烹饪中' : task.status === 'completed' ? '已完成' : task.status === 'archived' ? '已归档' : task.status === 'pending' ? '待烹饪' : task.status }}
          </span>
        </div>
      </div>
      <div v-if="loading && !filteredTasks.length" class="chef-left__empty">加载中...</div>
      <div v-else-if="!filteredTasks.length" class="chef-left__empty">{{ emptyMessages[activeFilter] }}</div>
    </div>
    <PaginationBar
      prefix="chef"
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

.chef-left {
  width: 340px;
  min-width: 340px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid $kds-border;
  background: $kds-surface;
}

.chef-left__header {
  padding: 12px 16px 10px;
  border-bottom: 1px solid $kds-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chef-left__title {
  font-size: 13px;
  font-weight: 700;
  color: $kds-text;
  letter-spacing: 0.3px;
}

.chef-left__badge {
  font-family: 'DM Mono', monospace;
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  background: $kds-surface-3;
  color: $kds-text-dim;
}

.chef-left__filters {
  display: flex;
  gap: 5px;
  padding: 8px 16px;
  border-bottom: 1px solid $kds-border;
}

.chef-left__fbtn {
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

.chef-left__scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}

.chef-task {
  padding: 14px;
  margin-bottom: 6px;
  border-radius: $kds-radius-xl;
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
    width: 4px;
    border-radius: 4px 0 0 4px;
  }

  &--pending::before { background: $kds-amber; }
  &--cooking::before { background: $kds-green; }
  &--done::before, &--completed::before { background: $kds-indigo; }
  &--archived::before { background: $kds-indigo; }
  &--alert::before { background: $kds-red; }

  &:hover { border-color: $kds-border-light; }

  &--active {
    border-color: #4f8cff;
    background: rgba(79, 140, 255, 0.08);
  }

  // === 归属视觉三态 ===

  &--assign-mine {
    border-color: rgba($kds-green, 0.3);
    &::before { background: $kds-green !important; }
  }

  &--assign-taken {
    opacity: 0.5;
    .chef-task__name { color: $kds-text-dim; }
    &:hover { opacity: 0.6; }
  }
}

.chef-task__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.chef-task__name {
  font-size: 16px;
  font-weight: 700;
  color: $kds-text-bright;
}

.chef-task__owner {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 6px;
  white-space: nowrap;
  flex-shrink: 0;

  &--mine {
    background: $kds-green-dim;
    color: $kds-green;
  }

  &--other {
    background: $kds-surface-3;
    color: $kds-text-dim;
  }
}

.chef-task__date {
  font-size: 10px;
  color: $kds-text-dim;
  margin-bottom: 6px;
  letter-spacing: 0.3px;
}

.chef-task__row {
  display: flex;
  gap: 14px;
  font-size: 11px;
  color: $kds-text-dim;
  align-items: center;
}

.chef-task__temp--ok { color: $kds-green; }
.chef-task__temp--bad { color: $kds-red; }

.chef-task__badge {
  font-size: 9px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-left: auto;

  &--pending { background: $kds-amber-dim; color: $kds-amber; }
  &--cooking { background: $kds-green-dim; color: $kds-green; }
  &--done, &--completed { background: $kds-indigo-dim; color: $kds-indigo; }
  &--archived { background: $kds-indigo-dim; color: $kds-indigo; }
  &--alert { background: $kds-red-dim; color: $kds-red; }
}

.chef-left__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $kds-text-muted;
  font-size: 14px;
  padding: 40px 0;
}

@media (max-width: 1100px) {
  .chef-left { width: 280px; min-width: 280px; }
}
</style>
