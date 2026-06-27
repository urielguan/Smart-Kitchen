<script setup lang="ts">
import { computed } from 'vue'
import { useMaterialStore } from '@/stores/modules/material'
import StatCard from '@/components/common/StatCard.vue'
import materialTotalIcon from '@/assets/images/material-total.png'
import materialWarnIcon from '@/assets/images/material-warn.png'
import materialExpiringIcon from '@/assets/images/material-expiring.png'
import materialExpiredIcon from '@/assets/images/material-expired.png'
import materialExpiredOverlayIcon from '@/assets/images/material-expired-overlay.png'

const materialStore = useMaterialStore()

const statistics = computed(() => materialStore.statistics)
</script>

<template>
  <div class="stats-cards">
    <StatCard title="物料总数" :value="statistics.total" bgColor="#7288FA" :icon="materialTotalIcon" valueColor="#04FBFF" unit="个"/>
    <StatCard title="启用中" :value="statistics.activeCount" bgColor="#3CC7EC" :icon="materialWarnIcon" unit="个"/>
    <StatCard title="已停用" :value="statistics.inactiveCount" bgColor="#FDBA00" :icon="materialExpiringIcon" unit="个"/>
    <StatCard title="待完善资料" :value="statistics.incompleteCount" bgColor="#FF7474" :icon="materialExpiredIcon" :overlayIcon="materialExpiredOverlayIcon" unit="个"/>
  </div>
</template>

<style lang="scss" scoped>
.stats-cards {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  flex-shrink: 0;

  :deep(.stat-card) {
    flex: 1;
    min-width: 0;
    height: 100px;
    border-radius: 16px;
  }
}
</style>
