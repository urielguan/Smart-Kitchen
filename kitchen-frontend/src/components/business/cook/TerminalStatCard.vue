<script setup lang="ts">
const props = defineProps<{
  title: string
  value: string | number
  accent?: 'primary' | 'success' | 'warning' | 'danger'
}>()
</script>

<template>
  <div class="terminal-stat-card" :class="`terminal-stat-card--${props.accent || 'primary'}`">
    <div class="terminal-stat-card__title">{{ props.title }}</div>
    <div class="terminal-stat-card__value">{{ props.value }}</div>
  </div>
</template>

<style scoped lang="scss">
.terminal-stat-card {
  @include panel-card;
  padding: 20px;
  border: 2px solid transparent;
}

.terminal-stat-card__title {
  color: $text-secondary;
  font-size: 16px;
}

.terminal-stat-card__value {
  margin-top: 10px;
  font-size: 36px;
  font-weight: 700;
}

.terminal-stat-card--primary {
  border-color: rgba(255, 122, 0, 0.24);
}

.terminal-stat-card--success {
  border-color: rgba(47, 179, 109, 0.24);
}

.terminal-stat-card--warning {
  border-color: rgba(255, 176, 32, 0.24);
}

.terminal-stat-card--danger {
  border-color: rgba(239, 68, 68, 0.24);
}
</style>
