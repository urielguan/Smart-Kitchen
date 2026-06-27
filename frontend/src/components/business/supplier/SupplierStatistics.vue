<script setup lang="ts">
import { computed } from 'vue'
import { useSupplierStore } from '@/stores/modules/supplier'
import StatCard from '@/components/common/StatCard.vue'

const supplierStore = useSupplierStore()

const statistics = computed(() => supplierStore.statistics)
</script>

<template>
  <div class="stats-cards">
    <StatCard title="供应商总数" :value="statistics.total" />
    <StatCard title="已审核" :value="statistics.activeCount" color="success" />
    <StatCard title="待审核" :value="statistics.pendingCount" color="warning" />
    <StatCard title="资质30天内到期" :value="statistics.nearExpireCount" color="danger" />
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
