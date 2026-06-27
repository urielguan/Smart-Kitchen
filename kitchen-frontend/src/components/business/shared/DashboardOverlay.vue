<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useCookStore } from '@/stores/modules/cook'

const emit = defineEmits<{ close: [] }>()
const cookStore = useCookStore()

let dashboardTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await cookStore.fetchDashboard()
  dashboardTimer = setInterval(() => cookStore.fetchDashboard(), 30000)
})

onUnmounted(() => {
  if (dashboardTimer) clearInterval(dashboardTimer)
})

const db = cookStore.dashboard

const cards = [
  { label: '总任务', key: 'totalDishes', color: '#e8eaed', unit: '项' },
  { label: '已完成', key: 'completedCount', color: '#34d399', unit: '项' },
  { label: '烹饪中', key: 'inProgressCount', color: '#4f8cff', unit: '项' },
  { label: '待烹饪', key: 'pendingCount', color: '#fbbf24', unit: '项' },
  { label: '完成率', key: 'completionRate', color: '', unit: '%' },
  { label: '异常数', key: 'abnormalTemperatureCount', color: '#f87171', unit: '项' },
] as const

const getValue = (key: string): number => {
  return (db as any)[key] ?? 0
}

const getRateColor = (rate: number) => {
  if (rate >= 80) return '#34d399'
  if (rate >= 50) return '#fbbf24'
  return '#f87171'
}
</script>

<template>
  <div class="dashboard-overlay" @click.self="emit('close')">
    <div class="dashboard-panel">
      <div class="dashboard-panel__header">
        <span class="dashboard-panel__title">烹饪看板</span>
        <button class="dashboard-panel__close" @click="emit('close')">&times;</button>
      </div>
      <div class="dashboard-grid">
        <div v-for="card in cards" :key="card.key" class="dashboard-card">
          <div class="dashboard-card__label">{{ card.label }}</div>
          <div class="dashboard-card__value" :style="{ color: card.key === 'completionRate' ? getRateColor(getValue('completionRate')) : card.color }">
            {{ card.key === 'completionRate' ? getValue('completionRate') : getValue(card.key) }}
            <span class="dashboard-card__unit">{{ card.unit }}</span>
          </div>
        </div>
      </div>
      <div class="dashboard-panel__footer">数据每 30 秒自动刷新</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.dashboard-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
}

.dashboard-panel {
  width: 680px;
  max-width: 90vw;
  background: $kds-surface;
  border: 1px solid $kds-border;
  border-radius: $kds-radius-lg;
  overflow: hidden;
}

.dashboard-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid $kds-border;
}

.dashboard-panel__title {
  font-size: $kds-font-lg;
  font-weight: 700;
  color: $kds-text-bright;
}

.dashboard-panel__close {
  width: 32px;
  height: 32px;
  border-radius: $kds-radius-sm;
  background: $kds-surface-2;
  border: 1px solid $kds-border;
  color: $kds-text-dim;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;

  &:hover {
    background: $kds-surface-3;
    color: $kds-text;
  }
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 16px 20px;
}

.dashboard-card {
  background: $kds-surface-2;
  border: 1px solid $kds-border;
  border-radius: $kds-radius-md;
  padding: 16px;
  text-align: center;
}

.dashboard-card__label {
  font-size: $kds-font-xs;
  color: $kds-text-dim;
  margin-bottom: 8px;
}

.dashboard-card__value {
  font-family: 'DM Mono', 'Courier New', monospace;
  font-size: $kds-font-3xl;
  font-weight: 700;
  line-height: 1;
}

.dashboard-card__unit {
  font-size: $kds-font-xs;
  font-weight: 400;
  opacity: 0.6;
  margin-left: 2px;
}

.dashboard-panel__footer {
  padding: 10px 20px;
  border-top: 1px solid $kds-border;
  font-size: 10px;
  color: $kds-text-muted;
  text-align: center;
}
</style>
