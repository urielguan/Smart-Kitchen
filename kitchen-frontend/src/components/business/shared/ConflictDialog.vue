<script setup lang="ts">
import { computed } from 'vue'
import { useOfflineStore } from '@/stores/modules/offline'
import { useCookStore } from '@/stores/modules/cook'
import { describeConflict, canForceRetry } from '@/utils/conflict'

const props = defineProps<{
  prefix: 'chef' | 'sup'
}>()

const offlineStore = useOfflineStore()
const cookStore = useCookStore()

const conflict = computed(() => offlineStore.activeConflict)
const visible = computed(() => offlineStore.hasConflict)

const userActionLabel = computed(() => conflict.value?.userAction || '')
const conflictDescription = computed(() => conflict.value ? describeConflict(conflict.value) : '')
const conflictField = computed(() => conflict.value?.conflictField || '')
const serverLatestValue = computed(() => conflict.value?.serverLatestValue || '')
const showForceRetry = computed(() => conflict.value ? canForceRetry(conflict.value.conflictType) : false)
const operationTime = computed(() => {
  const record = offlineStore.queue.find((item) => item.id === offlineStore.conflictRecordId)
  return record?.createdAt || ''
})

const handleDiscard = async () => {
  offlineStore.resolveConflictDiscard()
  await cookStore.resumeSyncAfterConflict()
}

const handleForceRetry = async () => {
  offlineStore.resolveConflictForceRetry()
  await cookStore.resumeSyncAfterConflict()
}
</script>

<template>
  <Transition name="cflt-fade">
    <div v-if="visible" :class="`${prefix}-cflt`">
      <div :class="`${prefix}-cflt__backdrop`" />
      <div :class="`${prefix}-cflt__panel`">
        <div :class="`${prefix}-cflt__header`">
          <span :class="`${prefix}-cflt__icon`">!</span>
          <span :class="`${prefix}-cflt__title`">同步冲突</span>
        </div>

        <div :class="`${prefix}-cflt__body`">
          <div :class="`${prefix}-cflt__row`">
            <span :class="`${prefix}-cflt__label`">您的操作</span>
            <span :class="`${prefix}-cflt__value`">{{ userActionLabel }}</span>
          </div>
          <div v-if="conflictField" :class="`${prefix}-cflt__row`">
            <span :class="`${prefix}-cflt__label`">冲突字段</span>
            <span :class="`${prefix}-cflt__value`">{{ conflictField }}</span>
          </div>
          <div :class="`${prefix}-cflt__row`">
            <span :class="`${prefix}-cflt__label`">服务器状态</span>
            <span :class="`${prefix}-cflt__value`">{{ conflictDescription }}</span>
          </div>
          <div v-if="serverLatestValue" :class="`${prefix}-cflt__row`">
            <span :class="`${prefix}-cflt__label`">服务器最新值</span>
            <span :class="`${prefix}-cflt__value`">{{ serverLatestValue }}</span>
          </div>
          <div v-if="operationTime" :class="`${prefix}-cflt__row`">
            <span :class="`${prefix}-cflt__label`">操作时间</span>
            <span :class="`${prefix}-cflt__value ${prefix}-cflt__value--mono`">{{ operationTime }}</span>
          </div>
        </div>

        <div :class="`${prefix}-cflt__actions`">
          <button
            :class="`${prefix}-cflt__btn ${prefix}-cflt__btn--discard`"
            @click="handleDiscard"
          >
            放弃操作
          </button>
          <button
            v-if="showForceRetry"
            :class="`${prefix}-cflt__btn ${prefix}-cflt__btn--retry`"
            @click="handleForceRetry"
          >
            强制重试
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

@mixin conflict-styles($pfx) {
  .#{$pfx}-cflt {
    position: fixed;
    inset: 0;
    z-index: 9999;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .#{$pfx}-cflt__backdrop {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.6);
  }

  .#{$pfx}-cflt__panel {
    position: relative;
    width: 380px;
    max-width: 90vw;
    background: $kds-surface-2;
    border: 1px solid $kds-red-border;
    border-radius: $kds-radius-lg;
    padding: 20px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  }

  .#{$pfx}-cflt__header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 16px;
  }

  .#{$pfx}-cflt__icon {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: $kds-red-dim;
    color: $kds-red;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
    flex-shrink: 0;
  }

  .#{$pfx}-cflt__title {
    font-size: $kds-font-lg;
    font-weight: 600;
    color: $kds-text;
  }

  .#{$pfx}-cflt__body {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin-bottom: 20px;
  }

  .#{$pfx}-cflt__row {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .#{$pfx}-cflt__label {
    font-size: $kds-font-xs;
    color: $kds-text-muted;
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .#{$pfx}-cflt__value {
    font-size: $kds-font-sm;
    color: $kds-text;
    line-height: 1.4;

    &--mono {
      font-family: 'DM Mono', monospace;
      color: $kds-text-dim;
    }
  }

  .#{$pfx}-cflt__actions {
    display: flex;
    gap: 10px;
  }

  .#{$pfx}-cflt__btn {
    flex: 1;
    padding: 10px 16px;
    border-radius: $kds-radius-md;
    font-size: $kds-font-sm;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.15s ease;
    border: 1px solid $kds-border;

    &:active {
      transform: scale(0.97);
    }

    &--discard {
      background: $kds-surface-3;
      color: $kds-text;

      &:hover {
        background: $kds-border;
      }
    }

    &--retry {
      background: $kds-red-dim;
      border-color: $kds-red-border;
      color: $kds-red;

      &:hover {
        background: rgba(248, 113, 113, 0.25);
      }
    }
  }
}

@include conflict-styles('chef');
@include conflict-styles('sup');

.cflt-fade-enter-active,
.cflt-fade-leave-active {
  transition: all 0.2s ease;
}

.cflt-fade-enter-from,
.cflt-fade-leave-to {
  opacity: 0;

  .chef-cflt__panel,
  .sup-cflt__panel {
    transform: scale(0.95);
  }
}
</style>
