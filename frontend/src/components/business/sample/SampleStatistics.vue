<script setup lang="ts">
import type { SampleDashboard } from '@/types'
import StatCard from '@/components/common/StatCard.vue'

interface Props {
  dashboard: SampleDashboard
}

defineProps<Props>()

const emit = defineEmits<{
  filterStatus: [status: string]
}>()
</script>

<template>
  <div class="sample-statistics">
    <StatCard title="总留样数" :value="dashboard.totalSamples" color="primary" clickable @click="emit('filterStatus', '')" />
    <StatCard title="待销样" :value="dashboard.pendingDisposal" color="warning" clickable @click="emit('filterStatus', 'pending_disposal')" />
    <StatCard title="已销样" :value="dashboard.disposed" color="success" clickable @click="emit('filterStatus', 'disposed')" />
    <StatCard title="超期未销" :value="dashboard.overdue" color="danger" clickable @click="emit('filterStatus', 'overdue')" />
    <StatCard title="今日留样" :value="dashboard.todaySampled" color="info" clickable @click="emit('filterStatus', 'today_sampled')" />
  </div>
</template>

<style lang="scss" scoped>
.sample-statistics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1400px) {
  .sample-statistics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .sample-statistics {
    grid-template-columns: 1fr;
  }
}
</style>
