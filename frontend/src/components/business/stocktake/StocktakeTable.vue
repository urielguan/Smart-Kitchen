<script setup lang="ts">
import type { StocktakeOrderListItem } from '@/types/stocktake'
import { STOCKTAKE_STATUS_MAP, STOCKTAKE_STATUS_TYPE_MAP } from '@/constants/stocktake'
import { STOCKTAKE_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: StocktakeOrderListItem[]
  loading: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  detail: [row: StocktakeOrderListItem]
  edit: [row: StocktakeOrderListItem]
  submit: [row: StocktakeOrderListItem]
  approve: [row: StocktakeOrderListItem]
  void: [row: StocktakeOrderListItem]
}>()

const getStatusType = (status: string) => STOCKTAKE_STATUS_TYPE_MAP[status] || 'info'
const getStatusLabel = (status: string) => STOCKTAKE_STATUS_MAP[status] || status
const formatPercent = (value?: number) => {
  if (value === undefined || value === null) return '—'
  return `${(Number(value) * 100).toFixed(2)}%`
}
</script>

<template>
  <el-table :data="data" v-loading="loading" stripe border class="stocktake-table">
    <el-table-column prop="stocktakeNo" label="盘点单号" width="180" />
    <el-table-column prop="stocktakeDate" label="盘点日期" width="120" />
    <el-table-column prop="checkerName" label="盘点人" width="120" />
    <el-table-column label="仓库" min-width="140" show-overflow-tooltip>
      <template #default="{ row }">{{ row.warehouseNames || row.warehouseName || '—' }}</template>
    </el-table-column>
    <el-table-column label="仓位" min-width="120" show-overflow-tooltip>
      <template #default="{ row }">{{ row.locationNames || row.locationName || '全部仓位' }}</template>
    </el-table-column>
    <el-table-column prop="itemCount" label="物料数" width="90" align="center" />
    <el-table-column label="差异率" width="100" align="right">
      <template #default="{ row }">{{ formatPercent(row.diffRate) }}</template>
    </el-table-column>
    <el-table-column label="状态" width="100" align="center">
      <template #default="{ row }">
        <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="260" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link @click="emit('detail', row)">详情</el-button>
        <el-button
          v-if="row.status === 'draft' || row.status === 'rejected'"
          v-permission="STOCKTAKE_PERMISSIONS.EDIT"
          type="primary"
          link
          @click="emit('edit', row)"
        >
          编辑
        </el-button>
        <el-button
          v-if="row.status === 'draft' || row.status === 'rejected'"
          v-permission="STOCKTAKE_PERMISSIONS.SUBMIT"
          type="success"
          link
          @click="emit('submit', row)"
        >
          {{ row.status === 'rejected' ? '重新提交' : '提交审核' }}
        </el-button>
        <el-button
          v-if="row.status === 'pending'"
          v-permission="STOCKTAKE_PERMISSIONS.APPROVE"
          type="warning"
          link
          @click="emit('approve', row)"
        >
          审核
        </el-button>
        <el-button
          v-if="row.status === 'draft' || row.status === 'rejected'"
          v-permission="STOCKTAKE_PERMISSIONS.VOID"
          type="danger"
          link
          @click="emit('void', row)"
        >
          作废
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style lang="scss" scoped>
.stocktake-table {
  width: 100%;
}
</style>
