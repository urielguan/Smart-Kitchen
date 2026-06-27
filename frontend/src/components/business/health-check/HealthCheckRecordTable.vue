<script setup lang="ts">
import type { HealthCheckRecord } from '@/types/health-check'
import { formatDateTime } from '@/utils'
import { MORNING_CHECK_PERMISSIONS } from '@/constants/permission'
import {
  HEALTH_CHECK_STATUS_MAP,
  HEALTH_CHECK_RESULT_MAP,
  TEMPERATURE_STATUS_MAP,
  getTemperatureStatus,
} from '@/constants/health-check'

interface Props {
  records: HealthCheckRecord[]
  total: number
  page: number
  pageSize: number
  loading: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'detail', record: HealthCheckRecord): void
  (e: 'archive', record: HealthCheckRecord): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
}>()
</script>

<template>
  <el-table :data="props.records" v-loading="props.loading" stripe border>
    <el-table-column prop="checkNo" label="晨检编号" width="180" />
    <el-table-column prop="employeeName" label="员工姓名" width="120" />
    <el-table-column prop="checkDate" label="晨检日期" width="120" />
    <el-table-column prop="checkTime" label="晨检时间" width="180">
      <template #default="{ row }">
        {{ row.checkTime ? formatDateTime(row.checkTime) : '-' }}
      </template>
    </el-table-column>
    <el-table-column prop="temperature" label="体温" width="100">
      <template #default="{ row }">
        <el-tag
          v-if="row.temperature"
          :type="TEMPERATURE_STATUS_MAP[getTemperatureStatus(row.temperature)]?.type"
        >
          {{ row.temperature }}℃
        </el-tag>
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column label="晨检结果" width="100">
      <template #default="{ row }">
        <el-tag :type="HEALTH_CHECK_RESULT_MAP[row.checkResult]?.type || 'info'">
          {{ HEALTH_CHECK_RESULT_MAP[row.checkResult]?.label }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="HEALTH_CHECK_STATUS_MAP[row.status]?.type || 'info'">
          {{ HEALTH_CHECK_STATUS_MAP[row.status]?.label }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="150" fixed="right">
      <template #default="{ row }">
        <el-button size="small" @click="emit('detail', row)">详情</el-button>
        <el-button
          v-if="row.status !== 'archived'"
          type="warning"
          size="small"
          v-permission="MORNING_CHECK_PERMISSIONS.ARCHIVE"
          @click="emit('archive', row)"
        >
          归档
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