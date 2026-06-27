<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCookStore } from '@/stores/modules/cook'
import { useAppStore } from '@/stores/modules/app'
import { getCurrentMealPeriod, getMealTimeRange } from '@/utils/meal-period'

const router = useRouter()
const cookStore = useCookStore()
const appStore = useAppStore()

const db = cookStore.dashboard
const mealPeriod = getCurrentMealPeriod()

let refreshTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null

const now = ref(new Date())
const clock = computed(() => now.value.toLocaleTimeString('zh-CN', { hour12: false }))

onMounted(async () => {
  cookStore.searchParams.mealType = mealPeriod.type
  await cookStore.fetchDashboard()
  refreshTimer = setInterval(() => cookStore.fetchDashboard(), 30000)
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (clockTimer) clearInterval(clockTimer)
})

interface CardConfig {
  label: string
  key: string
  color: string
  unit: string
  route: string
  query: Record<string, string>
}

const cards: CardConfig[] = [
  { label: '任务总数', key: 'totalDishes', color: '#e8eaed', unit: '项', route: '/kds', query: {} },
  { label: '烹饪中', key: 'inProgressCount', color: '#4f8cff', unit: '项', route: '/kds', query: { status: 'in_progress' } },
  { label: '待烹饪', key: 'pendingCount', color: '#fbbf24', unit: '项', route: '/kds', query: { status: 'pending' } },
  { label: '已完成', key: 'completedCount', color: '#34d399', unit: '项', route: '/kds', query: { status: 'completed' } },
  { label: '温度异常', key: 'abnormalTemperatureCount', color: '#f87171', unit: '项', route: '/kds', query: { alert: 'temp' } },
  { label: '时长异常', key: 'durationAbnormalCount', color: '#f87171', unit: '项', route: '/kds', query: { alert: 'duration' } },
]

const getRateColor = (rate: number) => {
  if (rate >= 80) return '#34d399'
  if (rate >= 50) return '#fbbf24'
  return '#f87171'
}

const getValue = (key: string): number => {
  return (db as any)[key] ?? 0
}

const handleClick = (card: CardConfig) => {
  router.push({ path: card.route, query: card.query })
}

const userName = computed(() => appStore.session?.displayName || appStore.session?.username || '--')
const isOnline = computed(() => appStore.online)
</script>

<template>
  <div class="chef-home">
    <!-- Top bar -->
    <header class="chef-home__top">
      <div class="chef-home__top-left">
        <span class="chef-home__logo">KDS</span>
        <span class="chef-home__brand">智慧厨房</span>
        <span class="chef-home__role-tag">一线厨师</span>
        <span class="chef-home__meal-tag">{{ mealPeriod.label }}</span>
        <span class="chef-home__meal-range">{{ getMealTimeRange(mealPeriod.type) }}</span>
      </div>
      <div class="chef-home__top-right">
        <span class="chef-home__user">{{ userName }}</span>
        <span class="chef-home__online" :class="isOnline ? 'chef-home__online--on' : 'chef-home__online--off'">●</span>
        <span class="chef-home__clock">{{ clock }}</span>
        <button class="chef-home__nav-btn" @click="router.push('/kds')">任务列表</button>
        <button class="chef-home__nav-btn" @click="router.push('/dashboard')">主管端</button>
        <button class="chef-home__nav-btn" @click="appStore.logout(); router.push('/login')">退出</button>
      </div>
    </header>

    <!-- Completion rate bar -->
    <div class="chef-home__rate-bar">
      <span class="chef-home__rate-label">完成率</span>
      <div class="chef-home__rate-track">
        <div class="chef-home__rate-fill" :style="{ width: `${db.completionRate}%`, background: getRateColor(db.completionRate) }" />
      </div>
      <span class="chef-home__rate-val" :style="{ color: getRateColor(db.completionRate) }">{{ db.completionRate }}%</span>
    </div>

    <!-- Card grid -->
    <div class="chef-home__grid">
      <div
        v-for="card in cards"
        :key="card.key"
        class="chef-home__card"
        :style="{ '--card-accent': card.color }"
        @click="handleClick(card)"
      >
        <div class="chef-home__card-label">{{ card.label }}</div>
        <div class="chef-home__card-val">{{ getValue(card.key) }}</div>
        <div class="chef-home__card-unit">{{ card.unit }}</div>
      </div>
    </div>

    <!-- Footer -->
    <div class="chef-home__footer">数据每 30 秒自动刷新 · 点击卡片查看详情</div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.chef-home {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $kds-bg;
  color: $kds-text;
  overflow: hidden;
}

.chef-home__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: $kds-surface;
  border-bottom: 1px solid $kds-border;
}

.chef-home__top-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chef-home__logo {
  font-size: 16px;
  font-weight: 900;
  background: linear-gradient(135deg, #4f8cff, #22c55e);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 2px;
}

.chef-home__brand {
  font-size: 14px;
  font-weight: 700;
  color: $kds-text-bright;
}

.chef-home__role-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 8px;
  background: rgba(34, 197, 94, 0.1);
  color: #34d399;
  font-weight: 600;
}

.chef-home__meal-tag {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(79, 140, 255, 0.12);
  color: #4f8cff;
  font-weight: 600;
}

.chef-home__meal-range {
  font-size: 10px;
  color: $kds-text-dim;
}

.chef-home__top-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chef-home__user {
  font-size: 12px;
  color: $kds-text;
  font-weight: 500;
}

.chef-home__online {
  font-size: 8px;
  &--on { color: $kds-green; }
  &--off { color: $kds-red; }
}

.chef-home__clock {
  font-family: 'DM Mono', monospace;
  font-size: 12px;
  color: $kds-text-dim;
}

.chef-home__nav-btn {
  font-size: 11px;
  padding: 4px 12px;
  border-radius: 8px;
  background: $kds-surface-2;
  color: $kds-text-dim;
  border: 1px solid $kds-border;
  cursor: pointer;
  transition: all 0.15s;

  &:hover { border-color: $kds-border-light; color: $kds-text; }
}

.chef-home__rate-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px 8px;
}

.chef-home__rate-label {
  font-size: 12px;
  font-weight: 600;
  color: $kds-text-dim;
}

.chef-home__rate-track {
  flex: 1;
  height: 6px;
  background: $kds-surface-3;
  border-radius: 3px;
  overflow: hidden;
}

.chef-home__rate-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.chef-home__rate-val {
  font-family: 'DM Mono', monospace;
  font-size: 16px;
  font-weight: 700;
}

.chef-home__grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 16px 20px;
}

.chef-home__card {
  background: $kds-surface;
  border: 1px solid $kds-border;
  border-radius: 16px;
  padding: 28px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: var(--card-accent);
    opacity: 0.7;
  }

  &:hover {
    border-color: $kds-border-light;
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  }
}

.chef-home__card-label {
  font-size: 12px;
  color: $kds-text-dim;
  font-weight: 600;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
}

.chef-home__card-val {
  font-family: 'DM Mono', monospace;
  font-size: 48px;
  font-weight: 900;
  color: $kds-text-bright;
  line-height: 1;
}

.chef-home__card-unit {
  font-size: 11px;
  color: $kds-text-muted;
  margin-top: 8px;
}

.chef-home__footer {
  padding: 10px 20px;
  text-align: center;
  font-size: 11px;
  color: $kds-text-muted;
  border-top: 1px solid $kds-border;
}
</style>
