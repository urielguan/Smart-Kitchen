<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ArrowUp } from '@element-plus/icons-vue'
import { getCertificateDashboard } from '@/api/modules/health-check'
import type { HealthCertificateDashboard } from '@/types/health-check'

const dashboard = ref<HealthCertificateDashboard | null>(null)
const loading = ref(false)
const warningsCollapsed = ref(false)

async function fetchDashboard() {
  loading.value = true
  try {
    const res = await getCertificateDashboard()
    if (res.code === 'SUCCESS') {
      dashboard.value = res.data
    }
  } catch (e) {
    console.error('获取健康证看板失败:', e)
  } finally {
    loading.value = false
  }
}

function getStatusType(status: string) {
  const map: Record<string, string> = {
    expired: 'danger',
    expiring: 'warning',
  }
  return map[status] || 'info'
}

onMounted(fetchDashboard)

defineExpose({ refresh: fetchDashboard })
</script>

<template>
  <div v-loading="loading" class="cert-dashboard">
    <div class="cert-stat-cards">
      <el-card class="cert-stat-card">
        <div class="stat-value">{{ dashboard?.totalCount ?? 0 }}</div>
        <div class="stat-label">健康证总数</div>
      </el-card>
      <el-card class="cert-stat-card valid">
        <div class="stat-value">{{ dashboard?.validCount ?? 0 }}</div>
        <div class="stat-label">有效</div>
      </el-card>
      <el-card class="cert-stat-card expiring">
        <div class="stat-value">{{ dashboard?.expiringCount ?? 0 }}</div>
        <div class="stat-label">即将过期</div>
      </el-card>
      <el-card class="cert-stat-card expired">
        <div class="stat-value">{{ dashboard?.expiredCount ?? 0 }}</div>
        <div class="stat-label">已过期</div>
      </el-card>
      <el-card class="cert-stat-card unregistered">
        <div class="stat-value">{{ dashboard?.unregisteredCount ?? 0 }}</div>
        <div class="stat-label">未办理</div>
      </el-card>
    </div>

    <div v-if="dashboard?.urgentWarnings && dashboard.urgentWarnings.length > 0" class="urgent-section">
      <div class="urgent-header">
        <span class="warning-icon">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <circle cx="8" cy="8" r="8" fill="#E37318"/>
            <rect x="7.2" y="3.5" width="1.6" height="5" rx="0.8" fill="#fff"/>
            <rect x="7.2" y="10" width="1.6" height="1.6" rx="0.8" fill="#fff"/>
          </svg>
        </span>
        <span class="urgent-title">健康证预警</span>
        <span class="urgent-count"><span class="urgent-count-num">{{ dashboard.urgentWarnings.length }}</span> 条提醒</span>
        <span class="collapse-btn" @click="warningsCollapsed = !warningsCollapsed">
          {{ warningsCollapsed ? '展开' : '收起' }}
          <el-icon :class="{ 'is-rotated': warningsCollapsed }"><ArrowUp /></el-icon>
        </span>
      </div>
      <div v-show="!warningsCollapsed" class="urgent-list">
        <div v-for="item in dashboard.urgentWarnings" :key="item.employeeId" class="urgent-item" :class="item.remainDays < 0 ? 'danger' : 'warning'">
          <div class="urgent-info">
            <span class="urgent-name">{{ item.employeeName }}</span>
            <el-tag :type="item.remainDays < 0 ? 'danger' : 'warning'" size="small" effect="plain">
              {{ item.remainDays < 0 ? `已过期${Math.abs(item.remainDays)}天` : `${item.remainDays}天后到期` }}
            </el-tag>
          </div>
          <span class="urgent-date">{{ item.expiryDate }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.cert-dashboard {
  margin-bottom: 0px;
}

.cert-stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;

  @media (max-width: 1200px) {
    grid-template-columns: repeat(3, 1fr);
  }
}

.cert-stat-card {
  text-align: center;
  border: 1px solid #C0C5CA;
  border-radius: 8px;
  box-shadow: none;
  --el-card-border-color: #C0C5CA;
  --el-card-border-radius: 8px;
  :deep(.el-card__body) {
    padding: 12px;
  }

  .stat-value {
    font-size: 24px;
    font-weight: 600;
    color: #7288FA;
  }

  .stat-label {
    font-size: 14px;
    color: #909399;
    margin-top: 4px;
  }

  &.valid .stat-value { color: #38CB89; }
  &.expiring .stat-value { color: #FDAD00; }
  &.expired .stat-value { color: #FF7474; }
  &.unregistered .stat-value { color: #333333; }
}

.urgent-section {
  background: #fff;
  border-radius: $border-radius-large;
  padding: 16px 20px;

  .urgent-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    padding-bottom: 10px;
    border-bottom: 1px solid #f0f0f0;

    .warning-icon {
      display: flex;
      align-items: center;
      flex-shrink: 0;
    }

    .urgent-title {
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
    }

    .urgent-count {
      font-size: 12px;
      color: $text-secondary;

      .urgent-count-num {
        color: #D54941;
      }
    }

    .collapse-btn {
      margin-left: auto;
      font-size: 12px;
      color: $text-secondary;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 2px;

      &:hover {
        color: $text-primary;
      }

      .el-icon {
        transition: transform 0.3s;

        &.is-rotated {
          transform: rotate(180deg);
        }
      }
    }
  }

  .urgent-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .urgent-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    border-radius: 6px;
    border-left: 3px solid;

    &.warning {
      background: #fdf6ec;
      border-left-color: #e6a23c;
    }

    &.danger {
      background: #fef0f0;
      border-left-color: #f56c6c;
    }

    .urgent-info {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .urgent-name {
      font-size: 14px;
      font-weight: 500;
      color: $text-primary;
    }

    .urgent-date {
      font-size: 14px;
      color: $text-secondary;
    }
  }
}
</style>
