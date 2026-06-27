<script setup lang="ts">
import type { InboundAreaValidationLocationSummary } from '@/types/inbound'

withDefaults(defineProps<{
  summaries: InboundAreaValidationLocationSummary[]
  globalMessage?: string | null
}>(), {
  globalMessage: null,
})
</script>

<template>
  <div class="inbound-area-validation-summary">
    <div v-if="globalMessage" class="summary-global-message">
      {{ globalMessage }}
    </div>

    <div v-if="summaries.length" class="summary-list">
      <div v-for="(summary, index) in summaries" :key="`${summary.locationId ?? 'unknown'}-${index}`" class="summary-item">
        <span class="summary-location">{{ summary.locationName || '未指定仓位' }}</span>
        <span>预计新增面积：{{ summary.expectedIncrementArea ?? '-' }}</span>
        <span>投影占用面积：{{ summary.projectedOccupiedArea ?? '-' }}</span>
        <span>容量上限：{{ summary.locationCapacity ?? '-' }}</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.inbound-area-validation-summary {
  margin-top: 12px;
  padding: 12px;
  border-radius: 4px;
  background: #f5f7fa;
}
.summary-global-message {
  margin-bottom: 8px;
  color: #606266;
}
.summary-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.summary-item {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #606266;
}
.summary-location {
  font-weight: 600;
  color: #303133;
}
</style>
