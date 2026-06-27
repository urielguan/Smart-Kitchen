<script setup lang="ts">
import type { DeviceStatistics } from '@/types'
import StatCard from '@/components/common/StatCard.vue'

interface Props {
  statistics: DeviceStatistics
}

defineProps<Props>()
</script>

<template>
  <div class="device-statistics">
    <StatCard title="设备总数" :value="statistics.totalCount" color="primary" />
    <StatCard title="在线数量" :value="statistics.onlineCount" color="success" />
    <StatCard title="离线数量" :value="statistics.offlineCount" color="info" />
    <StatCard title="报警数量" :value="statistics.alertCount" color="danger" />
    <StatCard title="维护中" :value="statistics.maintenanceCount" color="warning" />
  </div>
</template>

<style lang="scss" scoped>
.device-statistics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1400px) {
  .device-statistics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .device-statistics {
    grid-template-columns: 1fr;
  }
}
</style>
