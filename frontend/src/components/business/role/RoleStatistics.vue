<script setup lang="ts">
import { computed } from 'vue'
import { useRoleStore } from '@/stores/modules/role'
import StatCard from '@/components/common/StatCard.vue'

const roleStore = useRoleStore()

const statistics = computed(() => roleStore.statistics)
</script>

<template>
  <div class="stats-cards">
    <StatCard title="角色总数" :value="statistics.total" />
    <StatCard title="已启用" :value="statistics.activeCount" color="success" />
    <StatCard title="已禁用" :value="statistics.inactiveCount" color="info" />
    <StatCard title="分组数量" :value="statistics.groupCount" color="primary" />
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
