<script setup lang="ts">
interface Props {
  itemCount?: number
  diffQtyTotal?: number
  diffRate?: number
  surplusAmount?: number
  deficitAmount?: number
}

defineProps<Props>()

const formatNumber = (value?: number, digits = 2) => {
  if (value === undefined || value === null) return '0.00'
  return Number(value).toFixed(digits)
}
</script>

<template>
  <div class="summary-grid">
    <div class="summary-card">
      <div class="summary-value">{{ itemCount ?? 0 }}</div>
      <div class="summary-label">盘点物料数</div>
    </div>
    <div class="summary-card warning">
      <div class="summary-value">{{ formatNumber(diffQtyTotal, 3) }}</div>
      <div class="summary-label">差异数量合计</div>
    </div>
    <div class="summary-card primary">
      <div class="summary-value">{{ formatNumber(diffRate) }}%</div>
      <div class="summary-label">差异率</div>
    </div>
    <div class="summary-card success">
      <div class="summary-value">{{ formatNumber(surplusAmount) }}</div>
      <div class="summary-label">盘盈金额</div>
    </div>
    <div class="summary-card danger">
      <div class="summary-value">{{ formatNumber(deficitAmount) }}</div>
      <div class="summary-label">盘亏金额</div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  background: $bg-white;
  border-radius: $border-radius-large;
  padding: 16px;
  box-shadow: $box-shadow-base;
  border-top: 3px solid #909399;

  &.primary { border-top-color: #409eff; }
  &.success { border-top-color: #67c23a; }
  &.warning { border-top-color: #e6a23c; }
  &.danger { border-top-color: #f56c6c; }
}

.summary-value {
  font-size: 24px;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.2;
}

.summary-label {
  margin-top: 6px;
  font-size: 13px;
  color: $text-regular;
}
</style>
