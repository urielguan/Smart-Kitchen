<script setup lang="ts">
import { computed } from 'vue'
import type { CookTimelineEvent } from '@/types'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  events: CookTimelineEvent[]
  prefix: 'chef-detail' | 'sup-detail'
}>()

const dotColor = (event: CookTimelineEvent) => {
  if (event.eventType === 'temperature') {
    return event.abnormal ? 'red' : 'green'
  }
  if (event.eventType === 'ai_alert') return 'amber'
  return 'indigo'
}

const dotIcon = (event: CookTimelineEvent) => {
  if (event.eventType === 'temperature') return event.abnormal ? '🌡' : '🌡'
  if (event.eventType === 'ai_alert') return '⚠️'
  return '📝'
}
</script>

<template>
  <div :class="`${prefix}__timeline`">
    <div
      v-for="(event, idx) in events"
      :key="`${event.eventType}-${idx}`"
      :class="`${prefix}__tl-item`"
    >
      <div :class="[`${prefix}__tl-dot`, `${prefix}__tl-dot--${dotColor(event)}`]">
        {{ dotIcon(event) }}
      </div>
      <div :class="`${prefix}__tl-card`">
        <div :class="`${prefix}__tl-title`">{{ event.title }}</div>
        <div v-if="event.detail && event.detail !== event.title" :class="`${prefix}__tl-detail`">
          {{ event.detail }}
        </div>
        <div :class="`${prefix}__tl-meta`">
          <span v-if="event.operatorName" :class="`${prefix}__tl-operator`">{{ event.operatorName }}</span>
          <span :class="`${prefix}__tl-time`">{{ event.eventTime ? formatDateTime(event.eventTime) : '--' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

@mixin timeline-styles($pfx) {
  .#{$pfx}__timeline {
    position: relative;
    padding-left: 24px;
  }

  .#{$pfx}__tl-item {
    position: relative;
    padding-bottom: 16px;

    &:not(:last-child)::before {
      content: '';
      position: absolute;
      left: -16px;
      top: 24px;
      bottom: 0;
      width: 2px;
      background: $kds-border;
    }
  }

  .#{$pfx}__tl-dot {
    position: absolute;
    left: -24px;
    top: 4px;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;

    &--green { background: $kds-green-dim; border: 2px solid $kds-green; }
    &--red { background: $kds-red-dim; border: 2px solid $kds-red; }
    &--amber { background: $kds-amber-dim; border: 2px solid $kds-amber; }
    &--indigo { background: $kds-indigo-dim; border: 2px solid $kds-indigo; }
  }

  .#{$pfx}__tl-card {
    padding: 10px 12px;
    background: $kds-surface-2;
    border-radius: $kds-radius-md;
    border: 1px solid $kds-border;
  }

  .#{$pfx}__tl-title {
    font-size: 13px;
    font-weight: 600;
    color: $kds-text;
    margin-bottom: 2px;
  }

  .#{$pfx}__tl-detail {
    font-size: 11px;
    color: $kds-text-dim;
    line-height: 1.4;
    margin-bottom: 4px;
  }

  .#{$pfx}__tl-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
  }

  .#{$pfx}__tl-operator {
    font-size: 10px;
    color: $kds-indigo;
    font-weight: 500;
  }

  .#{$pfx}__tl-time {
    font-size: 10px;
    color: $kds-text-muted;
    font-family: 'DM Mono', monospace;
  }
}

@include timeline-styles('chef-detail');
@include timeline-styles('sup-detail');
</style>
