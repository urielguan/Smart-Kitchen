<script setup lang="ts">
import { computed } from 'vue'
import { useWarehouseStore } from '@/stores/modules/warehouse'
import StatCard from '@/components/common/StatCard.vue'
import warehouseTotalIcon from '@/assets/images/warehouse-total.png'
import warehouseActiveIcon from '@/assets/images/warehouse-active.png'
import warehouseMaintenanceIcon from '@/assets/images/warehouse-maintenance.png'
import warehousePositionIcon from '@/assets/images/warehouse-position.png'

const store = useWarehouseStore()
const stats = computed(() => store.statistics)
</script>

<template>
  <div class="stats-cards">
    <StatCard title="仓库总数"  :value="stats.warehouseTotal" bgColor="#7288FA" :icon="warehouseTotalIcon" valueColor="#04FBFF" unit="个" />
    <StatCard title="启用中"   :value="stats.activeCount" bgColor="#38CB89" :icon="warehouseActiveIcon" unit="个" />
    <StatCard title="维护中"   :value="stats.maintenanceCount" bgColor="#FDBA00" :icon="warehouseMaintenanceIcon" unit="个" />
    <StatCard title="仓位总数" :value="stats.positionTotal" bgColor="#3CC7EC" :icon="warehousePositionIcon" unit="个" />
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
