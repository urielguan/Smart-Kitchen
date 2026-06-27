<script setup lang="ts">
import type { HealthCheckRecord } from '@/types/health-check'
import { HEALTH_CHECK_STATUS_MAP } from '@/constants/health-check'
import { POSITION_MAP } from '@/constants/employee'
import { MORNING_CHECK_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

interface Props {
  records: HealthCheckRecord[]
  total: number
  page: number
  pageSize: number
  loading: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'check', record: HealthCheckRecord): void
  (e: 'detail', record: HealthCheckRecord): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
}>()

/**
 * 判断健康证是否即将过期（30天内）或已过期
 */
function getCertExpiryType(dateStr?: string): string {
  if (!dateStr) return 'info'
  const expiry = new Date(dateStr)
  const now = new Date()
  const diffDays = Math.ceil((expiry.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return 'danger'
  if (diffDays <= 30) return 'warning'
  return 'success'
}

function getCertExpiryLabel(dateStr?: string): string {
  if (!dateStr) return '-'
  return dateStr
}
</script>

<template>
  <el-table :data="props.records" v-loading="props.loading" stripe border>
    <el-table-column label="头像" width="70" align="center">
      <template #default="{ row }">
        <el-avatar :size="32" :src="row.avatarUrl">
          {{ row.employeeName?.charAt(0) }}
        </el-avatar>
      </template>
    </el-table-column>
    <el-table-column prop="checkNo" label="晨检编号" width="180" />
    <el-table-column label="员工姓名" width="220">
      <template #default="{ row }">
        <div class="employee-cell">
          <span class="employee-name">{{ row.employeeName }}</span>
          <div class="employee-meta">
            <el-tag
              v-if="row.dutyTypeName"
              size="small"
              effect="plain"
              :type="row.dutyType === 'substitute' ? 'warning' : 'success'"
            >
              {{ row.dutyTypeName }}
            </el-tag>
            <span class="employee-org">{{ row.currentOrgName || '未同步组织' }}</span>
          </div>
        </div>
      </template>
    </el-table-column>
    <el-table-column prop="employeeNo" label="工号" width="110" />
    <el-table-column prop="position" label="岗位" width="120">
      <template #default="{ row }">
        {{ POSITION_MAP[row.position as keyof typeof POSITION_MAP] || row.position || '-' }}
      </template>
    </el-table-column>
    <el-table-column label="人脸录入" width="100" align="center">
      <template #default="{ row }">
        <el-tag :type="row.hasFaceData ? 'success' : 'info'" size="small">
          {{ row.hasFaceData ? '已录入' : '未录入' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="健康证到期" width="130">
      <template #default="{ row }">
        <span :class="{ 'cert-warning': getCertExpiryType(row.certExpiryDate) === 'warning', 'cert-expired': getCertExpiryType(row.certExpiryDate) === 'danger' }">
          {{ getCertExpiryLabel(row.certExpiryDate) }}
        </span>
      </template>
    </el-table-column>
    <el-table-column prop="checkDate" label="晨检日期" width="120" />
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="HEALTH_CHECK_STATUS_MAP[row.status]?.type || 'info'">
          {{ HEALTH_CHECK_STATUS_MAP[row.status]?.label }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" width="180">
      <template #default="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="180" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" size="small" v-permission="MORNING_CHECK_PERMISSIONS.START" @click="emit('check', row)">
          开始晨检
        </el-button>
        <el-button size="small" @click="emit('detail', row)">
          详情
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <div class="pagination-wrapper">
    <span class="total-text">共 {{ props.total }} 条记录</span>
    <el-pagination
      :current-page="props.page"
      :page-size="props.pageSize"
      :total="props.total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @current-change="emit('page-change', $event)"
      @size-change="emit('size-change', $event)"
    />
  </div>
</template>

<style lang="scss" scoped>
.employee-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.employee-name {
  color: #303133;
  font-weight: 500;
  line-height: 1.2;
}

.employee-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.employee-org {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #909399;
  font-size: 12px;
}

.cert-warning {
  color: #e6a23c;
  font-weight: 500;
}

.cert-expired {
  color: #f56c6c;
  font-weight: 500;
}

.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.total-text {
  font-size: 14px;
  color: #909399;
}
</style>
