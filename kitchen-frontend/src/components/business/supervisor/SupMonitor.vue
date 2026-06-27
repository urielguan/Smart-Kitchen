<script setup lang="ts">
import { computed, onMounted } from 'vue'
import type { CookTask } from '@/types'
import { useAlertNotification } from '@/composables/useAlertNotification'
import HlsVideoPlayer from '@/components/business/shared/HlsVideoPlayer.vue'

const props = defineProps<{
  tasks: CookTask[]
}>()

const staffList = computed(() => {
  const chefMap = new Map<string, { name: string; task: string; busy: boolean }>()
  for (const task of props.tasks) {
    const name = task.chefName || task.assignedChefName || '待分派'
    if (!chefMap.has(name)) {
      chefMap.set(name, { name, task: '空闲', busy: false })
    }
    const entry = chefMap.get(name)!
    if (task.status === 'in_progress') {
      entry.task = `${task.menuName} · ${task.deviceName || '--'}`
      entry.busy = true
    } else if (task.status === 'pending' && !entry.busy) {
      entry.task = `${task.menuName} (待执行)`
    }
  }
  return Array.from(chefMap.values())
})

const alerts = computed(() =>
  props.tasks.filter(
    (t) => t.temperatureAbnormal || t.aiViolationCount > 0 || t.sensorOnline === false || t.deviceOnline === false
  )
)

const alertText = (task: CookTask) => {
  if (task.sensorOnline === false || task.deviceOnline === false) return `${task.menuName} - 传感器离线`
  if (task.temperatureAbnormal) return `${task.menuName} - 温度持续低于阈值`
  if (task.aiViolationCount > 0) return `${task.menuName} - AI预警 ${task.aiViolationCount}次`
  return `${task.menuName} - 异常`
}

const equipmentList = computed(() => {
  const deviceMap = new Map<string, { name: string; online: boolean; temp: string }>()
  for (const task of props.tasks) {
    const name = task.deviceName || `设备${task.deviceId || '?'}`
    if (!deviceMap.has(name)) {
      const online = task.deviceOnline !== false && task.sensorOnline !== false
      deviceMap.set(name, {
        name,
        online,
        temp: task.currentTemp !== null && task.currentTemp !== undefined
          ? `${task.currentTemp}℃`
          : online ? '待机' : '离线'
      })
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
  <aside class="sup-right">
    <div class="sup-right__scroll">
      <!-- Staff -->
      <div class="sup-right__panel-header">
        <span class="sup-right__panel-title">后厨人员</span>
      </div>
      <div class="sup-right__panel-body">
        <div v-for="staff in staffList" :key="staff.name" class="sup-right__staff">
          <div class="sup-right__staff-av">{{ staff.name.charAt(0) }}</div>
          <div class="sup-right__staff-info">
            <div class="sup-right__staff-name">{{ staff.name }}</div>
            <div class="sup-right__staff-task">{{ staff.task }}</div>
          </div>
          <div class="sup-right__staff-dot" :class="staff.busy ? 'sup-right__staff-dot--busy' : 'sup-right__staff-dot--idle'" />
        </div>
        <div v-if="!staffList.length" class="sup-right__empty">暂无人员</div>
      </div>

      <!-- Alerts -->
      <div class="sup-right__panel-header">
        <span class="sup-right__panel-title sup-right__panel-title--alert">实时告警</span>
      </div>
      <div class="sup-right__panel-body">
        <div v-for="task in alerts" :key="task.id" class="sup-right__alert">
          <span class="sup-right__alert-ico">⚠️</span>
          <div>
            <div class="sup-right__alert-txt">{{ alertText(task) }}</div>
          </div>
        </div>
        <div v-if="!alerts.length" class="sup-right__empty">暂无告警</div>
      </div>

      <!-- Equipment -->
      <div class="sup-right__panel-header">
        <span class="sup-right__panel-title">设备状态</span>
      </div>
      <div class="sup-right__panel-body">
        <div v-for="eq in equipmentList" :key="eq.name" class="sup-right__equip">
          <div class="sup-right__eq-dot" :class="{
            'sup-right__eq-dot--on': eq.online,
            'sup-right__eq-dot--off': !eq.online
          }" />
          <span class="sup-right__eq-name">{{ eq.name }}</span>
          <span class="sup-right__eq-temp">{{ eq.temp }}</span>
        </div>
        <div v-if="!equipmentList.length" class="sup-right__empty">暂无设备信息</div>
      </div>

      <!-- Live cameras (P0-7) -->
      <div v-if="cameras.length" class="sup-right__panel-header">
        <span class="sup-right__panel-title">实时监控</span>
      </div>
      <div v-if="cameras.length" class="sup-right__panel-body">
        <div class="sup-right__cam-grid">
          <HlsVideoPlayer
            v-for="cam in cameras"
            :key="cam.id"
            :src="`/hls/${cam.id}/stream.m3u8`"
            :title="cam.name"
          />
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.sup-right {
  width: 260px;
  min-width: 260px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid $kds-border;
  background: $kds-surface;
}

.sup-right__scroll {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}

.sup-right__panel-header {
  padding: 12px 16px 10px;
  border-bottom: 1px solid $kds-border;
}

.sup-right__panel-title {
  font-size: 13px;
  font-weight: 700;
  color: $kds-text;
  letter-spacing: 0.3px;

  &--alert { color: $kds-red; }
}

.sup-right__panel-body {
  padding: 8px 12px;
}

.sup-right__staff {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  background: $kds-surface-2;
  border-radius: $kds-radius-md;
  border: 1px solid $kds-border;
  margin-bottom: 5px;
}

.sup-right__staff-av {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: $kds-surface-3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid $kds-border;
  flex-shrink: 0;
}

.sup-right__staff-info {
  flex: 1;
  min-width: 0;
}

.sup-right__staff-name {
  font-size: 12px;
  font-weight: 600;
  color: $kds-text;
}

.sup-right__staff-task {
  font-size: 10px;
  color: $kds-text-dim;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sup-right__staff-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &--busy { background: $kds-green; }
  &--idle { background: $kds-text-muted; }
}

.sup-right__alert {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 11px;
  background: $kds-red-dim;
  border-radius: $kds-radius-md;
  border: 1px solid $kds-red-border;
  margin-bottom: 5px;
  font-size: 11px;
}

.sup-right__alert-ico {
  color: $kds-red;
  font-size: 13px;
  flex-shrink: 0;
  margin-top: 1px;
}

.sup-right__alert-txt {
  color: $kds-text;
  line-height: 1.4;
}

.sup-right__equip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  background: $kds-surface-2;
  border-radius: $kds-radius-md;
  border: 1px solid $kds-border;
  margin-bottom: 4px;
  font-size: 11px;
}

.sup-right__eq-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;

  &--on { background: $kds-green; }
  &--off { background: $kds-red; }
}

.sup-right__eq-name {
  flex: 1;
  color: $kds-text;
}

.sup-right__eq-temp {
  font-family: 'DM Mono', monospace;
  color: $kds-text-dim;
  font-size: 10px;
}

.sup-right__empty {
  font-size: 12px;
  color: $kds-text-muted;
  text-align: center;
  padding: 12px 0;
}

.sup-right__cam-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
