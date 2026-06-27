<script setup lang="ts">
import { computed, onMounted } from 'vue'
import type { CookTask } from '@/types'
import { useAlertNotification } from '@/composables/useAlertNotification'
import HlsVideoPlayer from '@/components/business/shared/HlsVideoPlayer.vue'

const props = defineProps<{
  tasks: CookTask[]
}>()

const overviewStats = computed(() => [
  { label: '任务总数', value: props.tasks.length, color: 'text-bright' },
  { label: '完成率', value: completionRate.value, color: 'green' },
  { label: '异常数', value: props.tasks.filter((t) => t.temperatureAbnormal || t.aiViolationCount > 0).length, color: 'red' },
  { label: '设备在线', value: props.tasks.filter((t) => t.deviceOnline !== false && t.sensorOnline !== false).length, color: 'amber' }
])

const completionRate = computed(() => {
  if (!props.tasks.length) return '0%'
  const done = props.tasks.filter((t) => t.status === 'completed' || t.status === 'archived').length
  return `${Math.round((done / props.tasks.length) * 100)}%`
})

const staffList = computed(() => {
  const chefMap = new Map<string, { name: string; count: number; status: string }>()
  for (const task of props.tasks) {
    const name = task.chefName || task.assignedChefName || '未分派'
    if (!chefMap.has(name)) {
      chefMap.set(name, { name, count: 0, status: '空闲' })
    }
    const entry = chefMap.get(name)!
    entry.count++
    if (task.status === 'in_progress') entry.status = '烹饪中'
  }
  return Array.from(chefMap.values())
})

const alerts = computed(() =>
  props.tasks.filter(
    (t) => t.temperatureAbnormal || t.aiViolationCount > 0 || t.sensorOnline === false || t.deviceOnline === false
  )
)

const alertText = (task: CookTask) => {
  if (task.sensorOnline === false || task.deviceOnline === false) return '设备离线'
  if (task.temperatureAbnormal) return '温度异常'
  if (task.aiViolationCount > 0) return `AI预警 ${task.aiViolationCount}次`
  return '异常'
}

const equipmentList = computed(() => {
  const deviceMap = new Map<string, { name: string; online: boolean }>()
  for (const task of props.tasks) {
    const name = task.deviceName || `设备${task.deviceId || '?'}`
    if (!deviceMap.has(name)) {
      deviceMap.set(name, { name, online: task.deviceOnline !== false && task.sensorOnline !== false })
    }
  }
  return Array.from(deviceMap.values())
})

const { requestPermission } = useAlertNotification(alerts)

// Unique cameras from tasks (P0-7)
const cameras = computed(() => {
  const map = new Map<string | number, { id: string | number; name: string }>()
  for (const task of props.tasks) {
    if (task.deviceId && !map.has(task.deviceId)) {
      map.set(task.deviceId, { id: task.deviceId, name: task.deviceName || `设备${task.deviceId}` })
    }
  }
  return Array.from(map.values())
})

onMounted(() => requestPermission())
</script>

<template>
  <aside class="kds-monitor">
    <!-- Overview stats -->
    <div class="kds-monitor__section">
      <div class="kds-monitor__section-label">概览</div>
      <div class="kds-monitor__stats-grid">
        <div v-for="stat in overviewStats" :key="stat.label" class="kds-monitor__stat" :class="`kds-monitor__stat--${stat.color}`">
          <span class="kds-monitor__stat-value">{{ stat.value }}</span>
          <span class="kds-monitor__stat-label">{{ stat.label }}</span>
        </div>
      </div>
    </div>

    <!-- Staff -->
    <div class="kds-monitor__section">
      <div class="kds-monitor__section-label">在岗厨师</div>
      <div class="kds-monitor__staff-list">
        <div v-for="staff in staffList" :key="staff.name" class="kds-monitor__staff-item">
          <span class="kds-monitor__staff-name">{{ staff.name }}</span>
          <span class="kds-monitor__staff-status" :class="{ 'kds-monitor__staff-status--active': staff.status === '烹饪中' }">
            {{ staff.status }} ({{ staff.count }})
          </span>
        </div>
        <div v-if="!staffList.length" class="kds-monitor__empty">暂无在岗人员</div>
      </div>
    </div>

    <!-- Alerts -->
    <div class="kds-monitor__section">
      <div class="kds-monitor__section-label kds-monitor__section-label--alert">实时告警</div>
      <div class="kds-monitor__alert-list">
        <div v-for="task in alerts" :key="task.id" class="kds-monitor__alert-item">
          <span class="kds-monitor__alert-name">{{ task.menuName }}</span>
          <span class="kds-monitor__alert-text">{{ alertText(task) }}</span>
        </div>
        <div v-if="!alerts.length" class="kds-monitor__empty">暂无告警</div>
      </div>
    </div>

    <!-- Equipment -->
    <div class="kds-monitor__section">
      <div class="kds-monitor__section-label">设备状态</div>
      <div class="kds-monitor__equip-list">
        <div v-for="equip in equipmentList" :key="equip.name" class="kds-monitor__equip-item">
          <span class="kds-monitor__equip-name">{{ equip.name }}</span>
          <span class="kds-monitor__equip-dot" :class="{ 'kds-monitor__equip-dot--online': equip.online }" />
        </div>
        <div v-if="!equipmentList.length" class="kds-monitor__empty">暂无设备信息</div>
      </div>
    </div>

    <!-- Live cameras (P0-7) -->
    <div v-if="cameras.length" class="kds-monitor__section">
      <div class="kds-monitor__section-label">实时监控</div>
      <div class="kds-monitor__cam-grid">
        <HlsVideoPlayer
          v-for="cam in cameras"
          :key="cam.id"
          :src="`/hls/${cam.id}/stream.m3u8`"
          :title="cam.name"
        />
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.kds-monitor {
  width: 280px;
  flex-shrink: 0;
  @include kds-scrollable;
  padding: 18px 16px;
  background: $kds-surface;
  border-left: 1px solid $kds-border;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.kds-monitor__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kds-monitor__section-label {
  font-size: $kds-font-xs;
  font-weight: 700;
  color: $kds-text-muted;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding-bottom: 6px;
  border-bottom: 1px solid $kds-border;

  &--alert {
    color: $kds-red;
  }
}

.kds-monitor__stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.kds-monitor__stat {
  padding: 10px;
  border-radius: $kds-radius-md;
  background: $kds-surface-2;
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: center;
}

.kds-monitor__stat-value {
  font-size: $kds-font-xl;
  font-weight: 800;

  .kds-monitor__stat--text-bright & { color: $kds-text-bright; }
  .kds-monitor__stat--green & { color: $kds-green; }
  .kds-monitor__stat--red & { color: $kds-red; }
  .kds-monitor__stat--amber & { color: $kds-amber; }
}

.kds-monitor__stat-label {
  font-size: $kds-font-xs;
  color: $kds-text-muted;
}

.kds-monitor__staff-list,
.kds-monitor__alert-list,
.kds-monitor__equip-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kds-monitor__staff-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: $kds-radius-sm;
  background: $kds-surface-2;
}

.kds-monitor__staff-name {
  font-size: $kds-font-sm;
  color: $kds-text;
  font-weight: 600;
}

.kds-monitor__staff-status {
  font-size: $kds-font-xs;
  color: $kds-text-muted;

  &--active { color: $kds-green; }
}

.kds-monitor__alert-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: $kds-radius-sm;
  background: $kds-red-dim;
  border: 1px solid $kds-red-border;
  animation: kds-pulse 2s infinite;
}

.kds-monitor__alert-name {
  font-size: $kds-font-sm;
  color: $kds-text;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kds-monitor__alert-text {
  font-size: $kds-font-xs;
  color: $kds-red;
  font-weight: 700;
  white-space: nowrap;
  margin-left: 8px;
}

.kds-monitor__equip-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: $kds-radius-sm;
  background: $kds-surface-2;
}

.kds-monitor__equip-name {
  font-size: $kds-font-sm;
  color: $kds-text;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kds-monitor__equip-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: $kds-red;
  flex-shrink: 0;

  &--online { background: $kds-green; }
}

.kds-monitor__empty {
  font-size: $kds-font-sm;
  color: $kds-text-muted;
  text-align: center;
  padding: 12px 0;
}

.kds-monitor__cam-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
