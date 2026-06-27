<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { InboundOrder } from '@/types/inbound'
import { INBOUND_STATUS_OPTIONS, INBOUND_SOURCE_TYPE_MAP } from '@/constants/inbound'
import { INBOUND_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: InboundOrder[]
  loading: boolean
}
defineProps<Props>()
const emit = defineEmits<{
  detail: [row: InboundOrder]
  edit:   [row: InboundOrder]
  delete: [row: InboundOrder]
  submit: [row: InboundOrder]
  cancel: [row: InboundOrder]
}>()

const normalizedPostStatus = (postStatus?: string) => {
  switch ((postStatus ?? '').trim()) {
    case 'posted':
      return 'posted'
    case 'post_failed':
      return 'post_failed'
    case 'unposted':
    case 'none':
    case '':
      return 'unposted'
    default:
      return postStatus
  }
}
const effectiveStatus = (row: InboundOrder) => {
  if (row.status === 'completed') return 'completed'
  if (row.status === 'approved' && normalizedPostStatus(row.postStatus) === 'posted') return 'completed'
  return row.status
}
const statusType  = (status: string) =>
  INBOUND_STATUS_OPTIONS.find(o => o.value === status)?.type ?? 'info'
const statusLabel = (status: string) =>
  INBOUND_STATUS_OPTIONS.find(o => o.value === status)?.label ?? status
</script>

<template>
  <el-table :data="data" v-loading="loading" stripe border class="inbound-table">
    <el-table-column prop="inboundNo"    label="入库单号"   min-width="160" />
    <el-table-column label="入库仓库"  min-width="180" show-overflow-tooltip>
      <template #default="{ row }">{{ row.warehouseNames || row.warehouseName || '—' }}</template>
    </el-table-column>
    <el-table-column label="入库来源" width="100">
      <template #default="{ row }">{{ INBOUND_SOURCE_TYPE_MAP[row.sourceType] || row.sourceType }}</template>
    </el-table-column>
    <el-table-column prop="itemCount"    label="品种数" width="80" align="center" />
    <el-table-column prop="totalAmount"  label="总金额(元)" width="110" align="right">
      <template #default="{ row }">
        {{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '—' }}
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90" align="center">
      <template #default="{ row }">
        <el-tag :type="statusType(effectiveStatus(row))" size="small">{{ statusLabel(effectiveStatus(row)) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="createdAt" label="创建时间" width="160" />
    <el-table-column label="操作" width="200" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link @click="emit('detail', row)">详情</el-button>
        <el-button
          v-if="row.status === 'draft' || row.status === 'rejected'"
          type="primary" link v-permission="INBOUND_PERMISSIONS.EDIT" @click="emit('edit', row)">编辑</el-button>
        <el-button
          v-if="row.status === 'draft'"
          type="success" link v-permission="INBOUND_PERMISSIONS.SUBMIT" @click="emit('submit', row)">提交</el-button>
        <el-button
          v-if="row.status === 'rejected'"
          type="warning" link v-permission="INBOUND_PERMISSIONS.CANCEL" @click="emit('cancel', row)">取消</el-button>
        <el-button
          v-if="row.status === 'draft' || row.status === 'cancelled'"
          type="danger" link v-permission="INBOUND_PERMISSIONS.DELETE" @click="emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style lang="scss" scoped>
.inbound-table { width: 100%; }
</style>