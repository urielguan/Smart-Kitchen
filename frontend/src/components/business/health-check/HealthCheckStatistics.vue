<script setup lang="ts">
import { computed } from 'vue'
import type { HealthDashboard } from '@/types/health-check'

interface Props {
  dashboard: HealthDashboard | null
}

const props = defineProps<Props>()

const statCards = computed(() => [
  {
    label: '待晨检',
    key: 'pendingCount',
    value: props.dashboard?.pendingCount ?? 0,
    type: 'warning',
    icon: 'Clock',
  },
  {
    label: '已晨检',
    key: 'completedCount',
    value: props.dashboard?.completedCount ?? 0,
    type: 'primary',
    icon: 'CircleCheck',
  },
  {
    label: '正常',
    key: 'normalCount',
    value: props.dashboard?.normalCount ?? 0,
    type: 'success',
    icon: 'SuccessFilled',
  },
  {
    label: '异常',
    key: 'abnormalCount',
    value: props.dashboard?.abnormalCount ?? 0,
    type: 'danger',
    icon: 'WarningFilled',
  },
  {
    label: '通过率',
    key: 'passRate',
    value: `${props.dashboard?.passRate ?? 0}%`,
    type: (props.dashboard?.passRate ?? 0) >= 90 ? 'success' : (props.dashboard?.passRate ?? 0) >= 70 ? 'warning' : 'danger',
    icon: 'TrendCharts',
  },
  {
    label: '健康证即将过期',
    key: 'certificateExpiringCount',
    value: props.dashboard?.certificateExpiringCount ?? 0,
    type: 'warning',
    icon: 'Document',
  },
  {
    label: '健康证已过期',
    key: 'certificateExpiredCount',
    value: props.dashboard?.certificateExpiredCount ?? 0,
    type: 'danger',
    icon: 'DocumentDelete',
  },
])
</script>

<template>
  <div class="health-check-statistics">
    <el-card v-for="card in statCards" :key="card.key" class="stat-card" :body-style="{ padding: '15px' }">
      <div class="stat-content">
        <div class="stat-info">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
        <el-tag :type="card.type as any" size="large" effect="dark">
          {{ card.value }}
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.health-check-statistics {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 16px;
  margin-bottom: 16px;

  @media (max-width: 1400px) {
    grid-template-columns: repeat(4, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  .stat-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .stat-info {
    display: flex;
    flex-direction: column;
  }

  .stat-value {
    font-size: 28px;
    font-weight: 600;
    color: #303133;
  }

  .stat-label {
    font-size: 14px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
