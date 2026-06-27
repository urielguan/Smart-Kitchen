<script setup lang="ts">
import type { DisposalReminderRecord } from '@/types'
import { SAMPLE_STATUS_MAP } from '@/constants/sample'
import { SAMPLE_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

interface Props {
  reminders: DisposalReminderRecord[]
  total?: number
  pageNum?: number
  pageSize?: number
  loading?: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  dispose: [record: DisposalReminderRecord]
  detail: [record: DisposalReminderRecord]
  pageChange: [page: number]
  sizeChange: [size: number]
}>()

const getStatus = (status: string) => SAMPLE_STATUS_MAP[status] || { label: status, type: 'info' }
const formatRemainHours = (hours: number) => {
  if (hours < 0) return `超期 ${Math.abs(hours)} 小时`
  return `剩余 ${hours} 小时`
}
const isOperationLockedByOther = (row: DisposalReminderRecord) => Boolean(row.operationLock?.locked && !row.operationLock?.ownedByCurrentUser)
</script>

<template>
  <div class="disposal-reminders">
    <el-table :data="reminders" v-loading="loading" stripe border empty-text="暂无待销样记录" size="small">
      <el-table-column prop="sampleNo" label="留样编号" width="170">
        <template #default="{ row }">
          <el-tooltip :disabled="!row.sampleNo" :content="row.sampleNo" placement="top">
            <span class="cell-ellipsis">{{ row.sampleNo || '-' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="menuName" label="菜谱名称" min-width="140">
        <template #default="{ row }">
          <el-tooltip :disabled="!row.menuName" :content="row.menuName" placement="top">
            <span class="cell-ellipsis">{{ row.menuName || '-' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="storageLocation" label="存储位置" width="120">
        <template #default="{ row }">
          <el-tooltip :disabled="!row.storageLocation" :content="row.storageLocation" placement="top">
            <span class="cell-ellipsis">{{ row.storageLocation || '-' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="留样时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.sampledAt) || '-' }}</template>
      </el-table-column>
      <el-table-column label="应销时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.disposalDueAt) || '-' }}</template>
      </el-table-column>
      <el-table-column label="剩余时间" width="130" align="center">
        <template #default="{ row }">
          <span :class="{ overdue: row.isOverdue }">{{ formatRemainHours(row.remainHours) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="(getStatus(row.status).type as any)" size="small">{{ getStatus(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作锁" width="150" align="center">
        <template #default="{ row }">
          <template v-if="row.operationLock?.locked">
            <el-tag :type="row.operationLock?.ownedByCurrentUser ? 'warning' : 'danger'" size="small">
              {{ row.operationLock?.operationTypeLabel || '处理中' }}
            </el-tag>
          </template>
          <span v-else class="text-muted">空闲</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="emit('detail', row)">详情</el-button>
          <template v-if="!['disposed','voided','archived'].includes(row.status)">
            <el-tooltip v-if="isOperationLockedByOther(row)" :content="`当前由${row.operationLock?.operatorName || '其他用户'}执行${row.operationLock?.operationTypeLabel || '处理中'}`" placement="top">
              <span>
                <el-button link type="info" size="small" disabled>执行销样</el-button>
              </span>
            </el-tooltip>
            <el-button v-else link type="primary" size="small" v-permission="SAMPLE_PERMISSIONS.DISPOSE" @click="emit('dispose', row)">执行销样</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="(total ?? 0) > 0" class="reminder-pagination">
      <span class="total-text">共 {{ total }} 条</span>
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="(page: number) => emit('pageChange', page)"
        @size-change="(size: number) => emit('sizeChange', size)"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.overdue {
  color: #f56c6c;
  font-weight: 600;
}

.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.text-muted {
  color: #c0c4cc;
}

.reminder-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.total-text {
  color: #909399;
  font-size: 13px;
}
</style>
