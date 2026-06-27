<script setup lang="ts">
import { computed } from 'vue'
import { useEvaluationStore } from '@/stores/modules/evaluation'
import StatCard from '@/components/common/StatCard.vue'

const evaluationStore = useEvaluationStore()

const statistics = computed(() => evaluationStore.statistics)
</script>

<template>
  <div class="stats-cards">
    <StatCard title="总评价数" :value="statistics.total" />
    <StatCard title="平均评分" :value="statistics.averageScore.toFixed(1)" color="warning" />
    <StatCard title="满意度" :value="`${statistics.satisfaction}%`" color="success" />
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
