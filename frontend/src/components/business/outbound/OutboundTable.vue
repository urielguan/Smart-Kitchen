<script setup lang="ts">
import type { OutboundOrder } from '@/types/outbound'
import { OUTBOUND_STATUS_MAP, OUTBOUND_STATUS_TYPE_MAP, OUTBOUND_TYPE_MAP } from '@/constants/outbound'
import { OUTBOUND_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: OutboundOrder[]
  loading: boolean
}
defineProps<Props>()
const emit = defineEmits<{
  detail:   [row: OutboundOrder]
  edit:     [row: OutboundOrder]
  delete:   [row: OutboundOrder]
  submit:   [row: OutboundOrder]
  withdraw: [row: OutboundOrder]
  approve:  [row: OutboundOrder]
  reject:   [row: OutboundOrder]
  execute:  [row: OutboundOrder]
  reverse:  [row: OutboundOrder]
}>()

const getStatusType  = (status: string) => OUTBOUND_STATUS_TYPE_MAP[status] || 'info'
const getStatusLabel = (status: string) => OUTBOUND_STATUS_MAP[status] || status

const formatTime = (val: string | null | undefined) => {
  if (!val) return '—'
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

</script>

<template>
  <el-table :data="data" v-loading="loading" stripe border class="outbound-table">
    <el-table-column prop="outboundNo" label="出库单号" width="160" />
    <el-table-column label="出库类型" width="110">
      <template #default="{ row }">{{ OUTBOUND_TYPE_MAP[row.outboundType] || row.outboundType }}</template>
    </el-table-column>
    <el-table-column label="出库仓库" min-width="150" show-overflow-tooltip>
      <template #default="{ row }">{{ row.warehouseNames || row.warehouseName || '—' }}</template>
    </el-table-column>
    <el-table-column label="领用组织" min-width="100">
      <template #default="{ row }">{{ row.targetOrgName || '—' }}</template>
    </el-table-column>
    <el-table-column label="关联单据" min-width="140" show-overflow-tooltip>
      <template #default="{ row }">{{ row.sourceOrderNo || '—' }}</template>
    </el-table-column>
    <el-table-column prop="itemCount" label="品种数" width="80" align="center" />
    <el-table-column label="状态" width="90" align="center">
      <template #default="{ row }">
        <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" width="160">
      <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="280" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link @click="emit('detail', row)">详情</el-button>
        <el-button v-if="row.status === 'draft' || row.status === 'rejected'" type="primary" link v-permission="OUTBOUND_PERMISSIONS.EDIT" @click="emit('edit', row)">编辑</el-button>
        <el-button v-if="row.status === 'draft' || row.status === 'rejected'" type="success" link v-permission="OUTBOUND_PERMISSIONS.SUBMIT" @click="emit('submit', row)">提交</el-button>
        <el-button v-if="row.status === 'pending'" type="warning" link v-permission="OUTBOUND_PERMISSIONS.WITHDRAW" @click="emit('withdraw', row)">撤回</el-button>
        <el-button v-if="row.status === 'pending'" type="success" link v-permission="OUTBOUND_PERMISSIONS.AUDIT" @click="emit('approve', row)">审核</el-button>
        <el-button v-if="row.status === 'pending'" type="danger" link v-permission="OUTBOUND_PERMISSIONS.AUDIT" @click="emit('reject', row)">驳回</el-button>
        <el-button v-if="row.status === 'approved'" type="primary" link v-permission="OUTBOUND_PERMISSIONS.EXECUTE" @click="emit('execute', row)">出库</el-button>
        <el-button v-if="row.status === 'approved' || row.status === 'completed'" type="warning" link v-permission="OUTBOUND_PERMISSIONS.REVERSE" @click="emit('reverse', row)">反审核</el-button>
        <el-button v-if="row.status === 'draft' || row.status === 'rejected'" type="danger" link v-permission="OUTBOUND_PERMISSIONS.DELETE" @click="emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style lang="scss" scoped>
.outbound-table { width: 100%; }
</style>