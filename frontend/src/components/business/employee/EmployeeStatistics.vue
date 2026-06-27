<script setup lang="ts">
import { computed } from 'vue'
import { useEmployeeStore } from '@/stores/modules/employee'
import StatCard from '@/components/common/StatCard.vue'

const employeeStore = useEmployeeStore()

const statistics = computed(() => employeeStore.statistics)
</script>

<template>
  <div class="stats-cards">
    <StatCard title="员工总数" :value="statistics.total" />
    <StatCard title="已启用" :value="statistics.activeCount" color="success" />
    <StatCard title="已禁用" :value="statistics.inactiveCount" color="info" />
    <StatCard title="本月新增" :value="statistics.newThisMonth" color="primary" />
  </div>
</template>

<style lang="scss" scoped>
.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
  flex-shrink: 0;
}
</style>
