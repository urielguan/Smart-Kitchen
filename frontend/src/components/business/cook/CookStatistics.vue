<script setup lang="ts">
import type { CookDashboard } from '@/types'
import StatCard from '@/components/common/StatCard.vue'

interface Props {
  dashboard: CookDashboard
}

defineProps<Props>()
</script>

<template>
  <div class="cook-statistics">
    <StatCard title="菜品总数" :value="dashboard.totalDishes" color="primary" />
    <StatCard title="待烹饪" :value="dashboard.pendingCount" color="warning" />
    <StatCard title="烹饪中" :value="dashboard.inProgressCount" color="info" />
    <StatCard title="已完成" :value="dashboard.completedCount" color="success" />
    <StatCard title="温度异常" :value="dashboard.abnormalTemperatureCount" color="danger" />
  </div>
</template>

<style lang="scss" scoped>
.cook-statistics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1400px) {
  .cook-statistics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .cook-statistics {
    grid-template-columns: 1fr;
  }
}
</style>
