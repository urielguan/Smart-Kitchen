<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { useCookStore } from '@/stores/modules/cook'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const cookStore = useCookStore()
const { locale, t } = useI18n()

const syncBadge = computed(() => cookStore.abnormalTasks.length)
const currentRoleLabel = computed(() => (appStore.currentRole === 'supervisor' ? '后厨主管模式' : '一线厨师模式'))
const quickNavItems = computed(() => [
  { label: '总控首页', path: '/dashboard', visible: true },
  { label: 'KDS 操作台', path: '/kds', visible: true }
])

const handleLocaleChange = (value: 'zh-CN' | 'en-US') => {
  appStore.setLocale(value)
}

const handleLogout = () => {
  appStore.logout()
  router.push('/login')
}

const navigateTo = (path: string) => {
  if (route.path !== path) {
    router.push(path)
  }
}

watch(
  () => appStore.locale,
  (value) => {
    locale.value = value
  },
  { immediate: true }
)
</script>

<template>
  <div class="terminal-layout app-shell">
    <header class="terminal-layout__header">
      <div class="terminal-layout__header-main">
        <div>
          <div class="terminal-layout__title">{{ t('common.appName') }}</div>
          <div class="terminal-layout__subtitle">
            {{ currentRoleLabel }} · {{ appStore.online ? t('common.online') : t('common.offline') }} · {{ t('common.syncPending') }} {{ syncBadge }}
          </div>
        </div>
        <div class="terminal-layout__status-grid">
          <div class="terminal-layout__status-card">
            <span>当前餐次</span>
            <strong>{{ cookStore.searchParams.mealType || '全部餐次' }}</strong>
          </div>
          <div class="terminal-layout__status-card">
            <span>当前任务</span>
            <strong>{{ cookStore.currentTask?.menuName || '未选中任务' }}</strong>
          </div>
          <div class="terminal-layout__status-card">
            <span>异常任务</span>
            <strong>{{ cookStore.abnormalTasks.length }}</strong>
          </div>
        </div>
      </div>
      <div class="terminal-layout__actions">
        <nav class="terminal-layout__quick-nav">
          <el-button
            v-for="item in quickNavItems"
            :key="item.path"
            class="touch-secondary-button"
            :type="route.path === item.path ? 'warning' : 'default'"
            @click="navigateTo(item.path)"
          >
            {{ item.label }}
          </el-button>
        </nav>
        <el-segmented
          :model-value="appStore.locale"
          :options="['zh-CN', 'en-US']"
          @change="handleLocaleChange"
        />
        <el-button class="touch-secondary-button" @click="handleLogout">
          {{ t('common.logout') }}
        </el-button>
      </div>
    </header>
    <main class="terminal-layout__content">
      <slot />
    </main>
  </div>
</template>

<style scoped lang="scss">
.terminal-layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at top left, rgba(251, 191, 36, 0.12), transparent 30%),
    linear-gradient(180deg, #fffaf4 0%, #f4f6f8 100%);
}

.terminal-layout__header {
  display: grid;
  gap: 18px;
  padding: 20px 24px;
  background: $bg-dark;
  color: $text-on-dark;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.18);
}

.terminal-layout__header-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.terminal-layout__title {
  font-size: 32px;
  font-weight: 700;
}

.terminal-layout__subtitle {
  margin-top: 8px;
  color: rgba(248, 250, 252, 0.78);
  font-size: 16px;
}

.terminal-layout__status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-width: 520px;
}

.terminal-layout__status-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.1);
  display: grid;
  gap: 6px;

  span {
    color: rgba(248, 250, 252, 0.74);
    font-size: 13px;
  }

  strong {
    font-size: 20px;
    font-weight: 700;
  }
}

.terminal-layout__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.terminal-layout__quick-nav {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.terminal-layout__content {
  flex: 1;
  overflow: auto;
}

@media (max-width: 1200px) {
  .terminal-layout__header-main,
  .terminal-layout__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .terminal-layout__status-grid {
    min-width: 0;
  }
}

@media (max-width: 900px) {
  .terminal-layout__status-grid {
    grid-template-columns: 1fr;
  }

  .terminal-layout__quick-nav {
    flex-direction: column;
  }
}
</style>
