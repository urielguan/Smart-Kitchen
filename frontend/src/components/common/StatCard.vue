<script setup lang="ts">
import { computed } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'

interface Props {
  title: string
  value: number | string
  color?: 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'info'
  bgColor?: string
  valueColor?: string
  icon?: string
  unit?: string
  overlayIcon?: string
  clickable?: boolean
  loading?: boolean
  error?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  color: 'default'
})

const colorClass = computed(() => {
  const colorMap = {
    default: '',
    primary: 'primary',
    success: 'success',
    warning: 'warning',
    danger: 'danger',
    info: 'info'
  }
  return colorMap[props.color] || ''
})
</script>

<template>
  <div class="stat-card" :class="{ clickable, colored: !!bgColor }" :style="bgColor ? { background: bgColor } : {}" @click="$emit('click')">
    <div v-if="icon" class="stat-card-icon-wrapper">
      <img :src="icon" alt="" class="stat-card-icon" />
      <img v-if="overlayIcon" :src="overlayIcon" alt="" class="stat-card-overlay-icon" />
    </div>
    <div class="stat-card-content">
      <div class="stat-card-title">{{ title }}</div>
      <div v-if="loading" class="stat-card-value stat-card-value--loading">...</div>
      <div v-else-if="error" class="stat-card-value stat-card-value--error">
        <span>加载失败</span>
        <el-icon :size="14" class="stat-card-retry-icon" @click.stop="$emit('retry')"><RefreshRight /></el-icon>
      </div>
      <div v-else class="stat-card-value" :style="valueColor ? { color: valueColor } : {}">{{ value }}<span v-if="unit" class="stat-card-unit">{{ unit }}</span></div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.stat-card {
  background: #fff;
  padding: 20px;
  border-radius: 16px;
  border: 1px solid $border-lighter;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;

  &-icon-wrapper {
    position: relative;
    width: 70px;
    height: 70px;
    flex-shrink: 0;
    margin-right: 24px;
  }

  &-icon {
    width: 70px;
    height: 70px;
  }

  &-overlay-icon {
    position: absolute;
    width: 40px;
    height: 40px;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
  }

  &-content {
    flex: 1;
  }

  &-title {
    font-size: 14px;
    color: $text-secondary;
    margin-bottom: 10px;
  }

  &-value {
    font-size: 24px;
    font-weight: 600;
    color: $text-primary;

    &--loading {
      color: var(--el-text-color-placeholder);
    }

    &--error {
      font-size: 14px;
      font-weight: 400;
      color: var(--el-color-danger);
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .stat-card-unit {
      font-size: 24px;
      font-weight: 600;
      margin-left: 2px;
    }

    &.primary {
      color: $primary-color;
    }

    &.warning {
      color: $warning-color;
    }

    &.danger {
      color: $danger-color;
    }

    &.success {
      color: $success-color;
    }

    &.info {
      color: $info-color;
    }
  }

  &.colored {
    border: none;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);

    .stat-card-title,
    .stat-card-value {
      color: #fff;
    }
  }

  &.clickable {
    cursor: pointer;
    transition: transform 0.15s, box-shadow 0.15s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }

    &:active {
      transform: translateY(0);
    }
  }
}

.stat-card-retry-icon {
  cursor: pointer;
  transition: color 0.15s;

  &:hover {
    color: var(--el-color-primary);
  }
}
</style>