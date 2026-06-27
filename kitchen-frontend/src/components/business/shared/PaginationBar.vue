<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  total: number
  pageNum: number
  pageSize: number
  prefix: 'chef' | 'sup'
}>()

const emit = defineEmits<{
  'update:pageNum': [page: number]
  'update:pageSize': [size: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const pages = computed(() => {
  const total = totalPages.value
  const current = props.pageNum
  if (total <= 5) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  if (current <= 3) return [1, 2, 3, 4, '...', total]
  if (current >= total - 2) return [1, '...', total - 3, total - 2, total - 1, total]
  return [1, '...', current - 1, current, current + 1, '...', total]
})

const canPrev = computed(() => props.pageNum > 1)
const canNext = computed(() => props.pageNum < totalPages.value)

const sizeOptions = [10, 20, 50]
</script>

<template>
  <div :class="[`${prefix}-pgtn`, { [`${prefix}-pgtn--hidden`]: total === 0 }]">
    <button
      :class="[`${prefix}-pgtn__btn`, { [`${prefix}-pgtn__btn--disabled`]: !canPrev }]"
      :disabled="!canPrev"
      @click="canPrev && emit('update:pageNum', pageNum - 1)"
    >‹</button>

    <template v-for="(p, idx) in pages" :key="idx">
      <span v-if="p === '...'" :class="`${prefix}-pgtn__ellipsis`">…</span>
      <button
        v-else
        :class="[`${prefix}-pgtn__btn`, `${prefix}-pgtn__page`, { [`${prefix}-pgtn__page--active`]: p === pageNum }]"
        @click="emit('update:pageNum', p as number)"
      >{{ p }}</button>
    </template>

    <button
      :class="[`${prefix}-pgtn__btn`, { [`${prefix}-pgtn__btn--disabled`]: !canNext }]"
      :disabled="!canNext"
      @click="canNext && emit('update:pageNum', pageNum + 1)"
    >›</button>

    <select
      :class="`${prefix}-pgtn__select`"
      :value="pageSize"
      @change="emit('update:pageSize', Number(($event.target as HTMLSelectElement).value))"
    >
      <option v-for="s in sizeOptions" :key="s" :value="s">{{ s }}条/页</option>
    </select>

    <span :class="`${prefix}-pgtn__total`">共 {{ total }} 条</span>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

@mixin pagination-styles($pfx) {
  .#{$pfx}-pgtn {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 12px;
    border-top: 1px solid $kds-border;
    background: $kds-surface;
    flex-shrink: 0;

    &--hidden {
      display: none;
    }
  }

  .#{$pfx}-pgtn__btn {
    min-width: 24px;
    height: 24px;
    padding: 0 6px;
    border: 1px solid $kds-border;
    border-radius: 4px;
    background: $kds-surface-2;
    color: $kds-text;
    font-size: 12px;
    cursor: pointer;
    transition: all 0.15s;

    &:hover:not(&--disabled) {
      border-color: #4f8cff;
      color: #4f8cff;
    }

    &--disabled {
      opacity: 0.35;
      cursor: not-allowed;
    }
  }

  .#{$pfx}-pgtn__page--active {
    background: rgba(79, 140, 255, 0.15);
    border-color: #4f8cff;
    color: #4f8cff;
    font-weight: 600;
  }

  .#{$pfx}-pgtn__ellipsis {
    color: $kds-text-muted;
    font-size: 12px;
    padding: 0 2px;
  }

  .#{$pfx}-pgtn__select {
    height: 24px;
    padding: 0 4px;
    border: 1px solid $kds-border;
    border-radius: 4px;
    background: $kds-surface-2;
    color: $kds-text;
    font-size: 11px;
    cursor: pointer;
    margin-left: 4px;

    option {
      background: $kds-surface;
      color: $kds-text;
    }
  }

  .#{$pfx}-pgtn__total {
    font-size: 10px;
    color: $kds-text-muted;
    margin-left: auto;
    white-space: nowrap;
  }
}

@include pagination-styles('chef');
@include pagination-styles('sup');
</style>
