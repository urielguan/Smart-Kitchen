<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCookStore } from '@/stores/modules/cook'
import { useAppStore } from '@/stores/modules/app'
import { formatMealType } from '@/utils/format'
import { getCurrentMealPeriod } from '@/utils/meal-period'
import DashboardOverlay from '@/components/business/shared/DashboardOverlay.vue'

const router = useRouter()
const appStore = useAppStore()
const cookStore = useCookStore()

const clockText = ref('')
const showDashboard = ref(false)
let clockTimer: ReturnType<typeof setInterval> | null = null

const mealPeriod = computed(() => getCurrentMealPeriod())

const stats = computed(() => [
  { label: '待烹饪', value: cookStore.list.filter((t) => t.status === 'pending').length, cls: 'pc' },
  { label: '烹饪中', value: cookStore.list.filter((t) => t.status === 'in_progress').length, cls: 'cc' },
  { label: '已完成', value: cookStore.list.filter((t) => t.status === 'completed').length, cls: 'dc' },
  { label: '异常', value: cookStore.abnormalTasks.length, cls: 'ac' }
])

const mealLabel = computed(() => formatMealType(cookStore.searchParams.mealType))
const userName = computed(() => appStore.session?.displayName || appStore.session?.username || '厨师')
const userInitial = computed(() => userName.value.charAt(0))

const handleLogout = () => {
  appStore.logout()
  router.push('/login')
}

const updateClock = () => {
  const now = new Date()
  clockText.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
}

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<template>
  <header class="chef-topbar">
    <div v-if="cookStore.isHistorical" class="chef-topbar__hist-banner">
      <span>历史数据 · 只读模式</span>
      <span class="chef-topbar__hist-date">{{ cookStore.searchParams.planDate }}</span>
    </div>
    <div class="chef-topbar__left">
      <div class="chef-topbar__logo">
        <div class="chef-topbar__logo-box">KDS</div>
        <span class="chef-topbar__logo-text">智慧厨房</span>
      </div>
      <div class="chef-topbar__role-badge">一线厨师</div>
      <div class="chef-topbar__meal-tag">{{ mealLabel === '--' ? mealPeriod.label : mealLabel }} {{ mealPeriod.timeRange }}</div>
    </div>

    <div class="chef-topbar__center">
      <div v-for="stat in stats" :key="stat.label" class="chef-topbar__pill">
        {{ stat.label }} <span class="chef-topbar__pill-n" :class="stat.cls">{{ stat.value }}</span>
      </div>
    </div>

    <div class="chef-topbar__right">
      <div class="chef-topbar__user">
        <div class="chef-topbar__user-av">{{ userInitial }}</div>
        <span>{{ userName }} (厨师)</span>
        <div class="chef-topbar__online-dot" :class="{ 'chef-topbar__online-dot--offline': !appStore.online }" />
        <span v-if="!appStore.online" class="chef-topbar__offline-label">离线</span>
      </div>
      <div class="chef-topbar__clock">{{ clockText }}</div>
      <div class="chef-topbar__actions">
        <button class="chef-topbar__action-btn" @click="router.push('/home')">首页</button>
        <button class="chef-topbar__action-btn" @click="showDashboard = true">看板</button>
        <button class="chef-topbar__action-btn" @click="router.push('/dashboard')">主管端</button>
        <button class="chef-topbar__action-btn" @click="handleLogout">退出</button>
      </div>
    </div>
  </header>
  <DashboardOverlay v-if="showDashboard" @close="showDashboard = false" />
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.chef-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  height: 50px;
  min-height: 50px;
  background: $kds-surface;
  border-bottom: 1px solid $kds-border;
  position: relative;
}

.chef-topbar__hist-banner {
  position: absolute;
  top: 50px;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 4px 0;
  background: $kds-amber-dim;
  border-bottom: 1px solid $kds-amber-border;
  font-size: 11px;
  font-weight: 600;
  color: $kds-amber;
  z-index: 10;
}

.chef-topbar__hist-date {
  font-family: 'DM Mono', monospace;
  color: $kds-text;
}

.chef-topbar__left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.chef-topbar__logo {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chef-topbar__logo-box {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, #34d399, #059669);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  color: #fff;
}

.chef-topbar__logo-text {
  font-weight: 900;
  font-size: 14px;
  color: $kds-text-bright;
  letter-spacing: 0.5px;
}

.chef-topbar__role-badge {
  padding: 3px 10px;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 700;
  background: $kds-green-dim;
  color: $kds-green;
  border: 1px solid $kds-green-border;
}

.chef-topbar__meal-tag {
  padding: 3px 10px;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 700;
  background: $kds-amber-dim;
  color: $kds-amber;
  border: 1px solid $kds-amber-border;
}

.chef-topbar__center {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chef-topbar__pill {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: $kds-text-dim;
  padding: 4px 10px;
  border-radius: 14px;
  background: $kds-surface-2;
}

.chef-topbar__pill-n {
  font-family: 'DM Mono', 'Courier New', monospace;
  font-weight: 600;
  font-size: 13px;

  &.pc { color: $kds-amber; }
  &.cc { color: $kds-green; }
  &.dc { color: $kds-indigo; }
  &.ac { color: $kds-red; }
}

.chef-topbar__right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.chef-topbar__user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: $kds-text;
}

.chef-topbar__user-av {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: $kds-surface-3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  border: 1px solid $kds-border;
}

.chef-topbar__online-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: $kds-green;
  animation: blink 2s infinite;

  &--offline {
    background: $kds-amber;
    animation: pulse-dot 1.5s infinite;
  }
}

.chef-topbar__offline-label {
  font-size: 10px;
  color: $kds-amber;
  font-weight: 600;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.chef-topbar__clock {
  font-family: 'DM Mono', 'Courier New', monospace;
  font-size: 12px;
  color: $kds-text-dim;
}

.chef-topbar__actions {
  display: flex;
  gap: 8px;
}

.chef-topbar__action-btn {
  @include kds-touch-button;
  padding: 6px 14px;
  background: $kds-surface-2;
  color: $kds-text-dim;
  font-size: 12px;
  min-height: 32px;
  border-radius: 8px;

  &:hover {
    background: $kds-surface-3;
    color: $kds-text;
  }
}
</style>
