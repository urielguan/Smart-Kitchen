<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAppStore } from '@/stores/modules/app'
import { useOfflineStore } from '@/stores/modules/offline'
import { useCookStore } from '@/stores/modules/cook'
import ConflictDialog from './ConflictDialog.vue'

const props = defineProps<{
  prefix: 'chef' | 'sup'
}>()

const appStore = useAppStore()
const offlineStore = useOfflineStore()
const cookStore = useCookStore()

const expanded = ref(false)

const pendingItems = computed(() => offlineStore.pendingItems)
const syncingItems = computed(() => offlineStore.syncingItems)
const failedItems = computed(() => offlineStore.failedItems)
const conflictItems = computed(() => offlineStore.conflictItems)
const hasPending = computed(() => offlineStore.pendingCount > 0)
const hasFailed = computed(() => offlineStore.failedCount > 0)
const hasConflictItems = computed(() => conflictItems.value.length > 0)
const hasDetails = computed(() => offlineStore.queue.length > 0)
const showBanner = computed(() => !appStore.online || hasDetails.value)

watch(showBanner, (visible) => {
  if (!visible) {
    expanded.value = false
  }
})

const formatActionLabel = (type: string) => {
  if (type === 'start') return '开始烹饪'
  if (type === 'temperature') return '温度采样'
  if (type === 'complete') return '完成烹饪'
  return '离线操作'
}

const formatTaskLabel = (item: { taskId: number; taskName?: string }) => {
  return item.taskName ? item.taskName : `任务 #${item.taskId}`
}

const formatNextRetry = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const bannerText = computed(() => {
  if (!appStore.online) {
    return '当前离线，操作将在恢复连接后同步'
  }
  if (hasConflictItems.value) {
    return `${conflictItems.value.length} 项冲突待处理`
  }
  if (hasFailed.value) {
    return `${offlineStore.failedCount} 项同步异常待处理`
  }
  if (syncingItems.value.length > 0) {
    return `${syncingItems.value.length} 项同步中`
  }
  return `${offlineStore.pendingCount} 项待同步`
})

const bannerActionText = computed(() => {
  if (hasConflictItems.value || hasFailed.value) {
    return expanded.value ? '收起面板' : '处理异常'
  }
  return expanded.value ? '收起详情' : '查看同步详情'
})

const handleRetry = async (id: string) => {
  offlineStore.retryFailed(id)
  await cookStore.syncOfflineQueue()
}

const handleRetryAll = async () => {
  for (const item of failedItems.value) {
    offlineStore.retryFailed(item.id)
  }
  await cookStore.syncOfflineQueue()
}

const handleDismiss = (id: string) => {
  offlineStore.removeFailed(id)
}
</script>

<template>
  <Transition name="slide-down">
    <div v-if="showBanner" :class="`${prefix}-offline`">
      <div :class="`${prefix}-offline__bar`" @click="expanded = !expanded">
        <span :class="`${prefix}-offline__dot`" />
        <span :class="`${prefix}-offline__text`">{{ bannerText }}</span>
        <span v-if="hasConflictItems" :class="`${prefix}-offline__badge ${prefix}-offline__badge--conflict`">冲突</span>
        <span v-else-if="hasFailed" :class="`${prefix}-offline__badge ${prefix}-offline__badge--failed`">异常</span>
        <span v-else-if="syncingItems.length > 0" :class="`${prefix}-offline__badge`">同步中</span>
        <span v-else-if="hasPending" :class="`${prefix}-offline__badge`">待同步</span>
        <span v-if="hasDetails || !appStore.online" :class="`${prefix}-offline__expand`">{{ bannerActionText }}</span>
      </div>

      <div v-if="expanded && hasDetails" :class="`${prefix}-offline__list`">
        <div v-if="conflictItems.length > 0" :class="`${prefix}-offline__section`">
          <div :class="`${prefix}-offline__section-title ${prefix}-offline__section-title--conflict`">冲突待处理</div>
          <div
            v-for="item in conflictItems"
            :key="`${item.id}-conflict`"
            :class="`${prefix}-offline__item ${prefix}-offline__item--conflict`"
          >
            <div :class="`${prefix}-offline__item-info`">
              <span :class="`${prefix}-offline__item-type`">{{ formatActionLabel(item.type) }}</span>
              <span :class="`${prefix}-offline__item-task`">{{ formatTaskLabel(item) }}</span>
              <span :class="`${prefix}-offline__item-meta`">等待人工处理</span>
            </div>
            <div v-if="item.errorMessage" :class="`${prefix}-offline__item-err`">{{ item.errorMessage }}</div>
          </div>
        </div>

        <div v-if="syncingItems.length > 0" :class="`${prefix}-offline__section`">
          <div :class="`${prefix}-offline__section-title`">同步中</div>
          <div
            v-for="item in syncingItems"
            :key="`${item.id}-syncing`"
            :class="`${prefix}-offline__item`"
          >
            <div :class="`${prefix}-offline__item-info`">
              <span :class="`${prefix}-offline__item-type`">{{ formatActionLabel(item.type) }}</span>
              <span :class="`${prefix}-offline__item-task`">{{ formatTaskLabel(item) }}</span>
              <span :class="`${prefix}-offline__item-meta`">正在同步</span>
            </div>
          </div>
        </div>

        <div v-if="pendingItems.length > 0" :class="`${prefix}-offline__section`">
          <div :class="`${prefix}-offline__section-title`">待同步</div>
          <div
            v-for="item in pendingItems"
            :key="`${item.id}-pending`"
            :class="`${prefix}-offline__item`"
          >
            <div :class="`${prefix}-offline__item-info`">
              <span :class="`${prefix}-offline__item-type`">{{ formatActionLabel(item.type) }}</span>
              <span :class="`${prefix}-offline__item-task`">{{ formatTaskLabel(item) }}</span>
              <span :class="`${prefix}-offline__item-meta`">等待同步</span>
            </div>
          </div>
        </div>

        <div v-if="failedItems.length > 0" :class="`${prefix}-offline__section`">
          <div :class="`${prefix}-offline__section-title ${prefix}-offline__section-title--failed`">同步异常</div>
          <div
            v-for="item in failedItems"
            :key="`${item.id}-failed`"
            :class="`${prefix}-offline__item`"
          >
            <div :class="`${prefix}-offline__item-info`">
              <span :class="`${prefix}-offline__item-type`">{{ formatActionLabel(item.type) }}</span>
              <span :class="`${prefix}-offline__item-task`">{{ formatTaskLabel(item) }}</span>
              <span v-if="item.retryCount" :class="`${prefix}-offline__item-meta`">已重试 {{ item.retryCount }} 次</span>
            </div>
            <div v-if="item.errorMessage" :class="`${prefix}-offline__item-err`">{{ item.errorMessage }}</div>
            <div v-if="item.nextRetryAt" :class="`${prefix}-offline__item-next`">下次自动重试：{{ formatNextRetry(item.nextRetryAt) }}</div>
            <div :class="`${prefix}-offline__item-actions`">
              <button :class="`${prefix}-offline__item-btn ${prefix}-offline__item-btn--retry`" @click.stop="handleRetry(item.id)">重试</button>
              <button :class="`${prefix}-offline__item-btn`" @click.stop="handleDismiss(item.id)">移除</button>
            </div>
          </div>
          <div v-if="failedItems.length > 1" :class="`${prefix}-offline__bulk`">
            <button :class="`${prefix}-offline__bulk-btn`" @click="handleRetryAll">全部重试</button>
            <button :class="`${prefix}-offline__bulk-btn`" @click="offlineStore.clearAllFailed()">清除全部</button>
          </div>
        </div>
      </div>
    </div>
  </Transition>
  <ConflictDialog :prefix="prefix" />
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

@mixin offline-styles($pfx) {
  .#{$pfx}-offline {
    flex-shrink: 0;
  }

  .#{$pfx}-offline__bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 16px;
    background: rgba(251, 191, 36, 0.1);
    border-bottom: 1px solid $kds-amber-border;
    cursor: pointer;
    user-select: none;
  }

  .#{$pfx}-offline__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $kds-amber;
    flex-shrink: 0;
    animation: pulse-dot 2s infinite;
  }

  .#{$pfx}-offline__text {
    font-size: 11px;
    color: $kds-amber;
    font-weight: 500;
  }

  .#{$pfx}-offline__badge {
    font-size: 10px;
    line-height: 1;
    padding: 3px 6px;
    border-radius: 999px;
    border: 1px solid $kds-amber-border;
    color: $kds-amber;
    background: rgba(251, 191, 36, 0.08);

    &--failed {
      color: $kds-red;
      border-color: $kds-red-border;
      background: rgba(248, 113, 113, 0.12);
    }

    &--conflict {
      color: $kds-indigo;
      border-color: rgba(129, 140, 248, 0.35);
      background: rgba(99, 102, 241, 0.16);
    }
  }

  .#{$pfx}-offline__expand {
    font-size: 10px;
    color: $kds-text-muted;
    margin-left: auto;
  }

  .#{$pfx}-offline__list {
    padding: 8px 16px;
    background: rgba(251, 191, 36, 0.05);
    border-bottom: 1px solid $kds-border;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .#{$pfx}-offline__section {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .#{$pfx}-offline__section-title {
    font-size: 10px;
    font-weight: 700;
    color: $kds-text-muted;
    text-transform: uppercase;
    letter-spacing: 0.4px;

    &--failed {
      color: $kds-red;
    }

    &--conflict {
      color: $kds-indigo;
    }
  }

  .#{$pfx}-offline__item {
    padding: 6px 8px;
    background: $kds-surface-2;
    border-radius: 6px;
    border: 1px solid $kds-border;

    &--conflict {
      border-color: rgba(129, 140, 248, 0.35);
      background: rgba(99, 102, 241, 0.08);
    }
  }

  .#{$pfx}-offline__item-info {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
    flex-wrap: wrap;
  }

  .#{$pfx}-offline__item-type {
    font-size: 11px;
    font-weight: 600;
    color: $kds-text;
  }

  .#{$pfx}-offline__item-task {
    font-size: 10px;
    color: $kds-text-muted;
    font-family: 'DM Mono', monospace;
  }

  .#{$pfx}-offline__item-meta,
  .#{$pfx}-offline__item-next {
    font-size: 10px;
    color: $kds-amber;
  }

  .#{$pfx}-offline__item-err {
    font-size: 10px;
    color: $kds-red;
    margin-bottom: 4px;
    line-height: 1.3;
  }

  .#{$pfx}-offline__item-actions {
    display: flex;
    gap: 6px;
  }

  .#{$pfx}-offline__item-btn {
    font-size: 10px;
    padding: 2px 8px;
    border-radius: 4px;
    border: 1px solid $kds-border;
    background: $kds-surface;
    color: $kds-text-dim;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: $kds-text-dim;
      color: $kds-text;
    }

    &--retry {
      border-color: $kds-indigo;
      color: $kds-indigo;
      background: transparent;

      &:hover {
        background: $kds-indigo-dim;
      }
    }
  }

  .#{$pfx}-offline__bulk {
    display: flex;
    gap: 8px;
    margin-top: 4px;
  }

  .#{$pfx}-offline__bulk-btn {
    font-size: 10px;
    padding: 3px 10px;
    border-radius: 4px;
    border: 1px solid $kds-border;
    background: $kds-surface-2;
    color: $kds-text-dim;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      background: $kds-surface-3;
      color: $kds-text;
    }
  }
}

@include offline-styles('chef');
@include offline-styles('sup');

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.2s ease;
  max-height: 420px;
  overflow: hidden;
}

.slide-down-enter-from,
.slide-down-leave-to {
  max-height: 0;
  opacity: 0;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
