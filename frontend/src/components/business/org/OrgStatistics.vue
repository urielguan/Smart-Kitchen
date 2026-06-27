<script setup lang="ts">
import { computed } from 'vue'
import { useOrgStore } from '@/stores/modules/org'
import StatCard from '@/components/common/StatCard.vue'
import orgTotalIcon from '@/assets/images/org-total.png'
import orgActiveIcon from '@/assets/images/org-active.png'
import orgInactiveIcon from '@/assets/images/org-inactive.png'
import orgTypeIcon from '@/assets/images/org-type.png'

const orgStore = useOrgStore()

const statistics = computed(() => orgStore.statistics)

/** 组织类型数（统计存在的类型数量） */
const typeCount = computed(() => {
  const s = statistics.value
  return [s.groupCount, s.companyCount, s.canteenCount, s.deptCount]
    .filter(count => count > 0).length
})
</script>

<template>
  <div class="stats-cards">
    <StatCard title="组织总数" :value="statistics.total" bgColor="#7288FA" :icon="orgTotalIcon" valueColor="#04FBFF" unit="个"/>
    <StatCard title="启用中" :value="statistics.activeCount" bgColor="#38CB89" :icon="orgActiveIcon" unit="个"/>
    <StatCard title="已停用" :value="statistics.inactiveCount" bgColor="#FF7474" :icon="orgInactiveIcon" unit="个"/>
    <StatCard title="组织类型数" :value="typeCount" bgColor="#3CC7EC" :icon="orgTypeIcon" />
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
