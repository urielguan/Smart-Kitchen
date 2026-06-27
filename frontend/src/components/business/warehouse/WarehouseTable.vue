<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import type { Warehouse } from '@/types/warehouse'
import { WAREHOUSE_TYPE_MAP, WAREHOUSE_STATUS_OPTIONS } from '@/constants/warehouse'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'
import { WAREHOUSE_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: Warehouse[]
  loading?: boolean
}
const props = withDefaults(defineProps<Props>(), { loading: false })
const emit  = defineEmits<{
  detail:   [row: Warehouse]
  edit:     [row: Warehouse]
  delete:   [row: Warehouse]
}>()
const dictCategoryStore = useDictCategoryStore()

const tableContainerRef = ref<HTMLElement | null>(null)
const tableHeight = ref<number | undefined>(undefined)
let resizeObserver: ResizeObserver | null = null

const warehouseTypeLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('warehouse_type', true),
  WAREHOUSE_TYPE_MAP
))

const updateTableHeight = () => {
  if (tableContainerRef.value) tableHeight.value = tableContainerRef.value.clientHeight
}

onMounted(() => {
  dictCategoryStore.fetchOptions('warehouse_type', true)
  if (tableContainerRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableContainerRef.value)
    updateTableHeight()
  }
})
onUnmounted(() => { resizeObserver?.disconnect() })

/** 仓位使用进度百分比（positionUsed/positionTotal）
 *  注：WarehouseVO 无 usedCapacity 字段；规范 4.6 的 usedCapacity/capacity
 *  指的是仓位（Location）详情列的进度条，非此处。
 */
const capacityPercent = (row: Warehouse) => {
  const total = row.positionTotal ?? 0
  if (total === 0) return 0
  return Math.round(((row.positionUsed ?? 0) / total) * 100)
}

const progressColor = (row: Warehouse) => {
  const p = capacityPercent(row)
  if (p > 85) return '#f56c6c'
  if (p > 60) return '#e6a23c'
  return '#67c23a'
}

const statusType = (status: string) => {
  return WAREHOUSE_STATUS_OPTIONS.find(o => o.value === status)?.type ?? 'info'
}

const statusLabel = (status: string) => {
  return WAREHOUSE_STATUS_OPTIONS.find(o => o.value === status)?.label ?? status
}

/** 温湿度状态颜色：超阈值→红色，预警→橙色，正常→绿色 */
const tempColor = (row: Warehouse) => {
  if (row.tempStatus === 'alarm') return '#f56c6c'
  if (row.tempStatus === 'warning') return '#e6a23c'
  return '#67c23a'
}
const humidityColor = (row: Warehouse) => {
  if (row.humidityStatus === 'alarm') return '#f56c6c'
  if (row.humidityStatus === 'warning') return '#e6a23c'
  return '#67c23a'
}
</script>

<template>
  <div ref="tableContainerRef" class="table-container">
    <el-table :data="data" v-loading="loading" stripe :height="tableHeight">
      <el-table-column type="index" label="序号" width="60" align="center" class-name="index-col" />
      <el-table-column prop="warehouseName" label="仓库名称" min-width="140" />
      <el-table-column prop="warehouseCode" label="仓库编码" min-width="140" />
      <el-table-column label="类型" min-width="120">
        <template #default="{ row }">
          {{ row.warehouseTypeName || warehouseTypeLabelMap[row.warehouseType] || row.warehouseType }}
        </template>
      </el-table-column>
      <el-table-column label="温度" min-width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.currentTemperature != null" :style="{ color: tempColor(row) }">
            {{ row.currentTemperature }}℃
          </span>
          <span v-else style="color: #999">—</span>
        </template>
      </el-table-column>
      <el-table-column label="湿度" min-width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.currentHumidity != null" :style="{ color: humidityColor(row) }">
            {{ row.currentHumidity }}%RH
          </span>
          <span v-else style="color: #999">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="managerName"  label="负责人"  min-width="110" />
      <el-table-column prop="managerPhone" label="联系方式" min-width="140" />
      <el-table-column label="仓位使用" min-width="160">
        <template #default="{ row }">
          <el-progress
            :percentage="capacityPercent(row)"
            :color="progressColor(row)"
            :stroke-width="8"
          />
        </template>
      </el-table-column>
      <el-table-column label="库存状态" min-width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="emit('detail', row)">详情</el-button>
          <el-button type="primary" link v-permission="WAREHOUSE_PERMISSIONS.EDIT" @click="emit('edit', row)">编辑</el-button>
          <el-button type="danger"  link v-permission="WAREHOUSE_PERMISSIONS.DELETE" @click="emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  overflow: hidden;

  :deep(.el-table) {
    --el-table-index-cell-vertical-align: middle;
    --el-table-border-color: #E7E7E7;
    --el-table-row-height: 46px;

    .el-table__cell {
      padding-left: 0;
      padding-right: 0;
      font-family: 'PingFang SC', sans-serif;
      font-weight: 400;
      font-size: 14px;
      line-height: 22px;
      color: #000000E5;
    }
  }

  :deep(.el-table__body tr) {
    height: 46px;
    border-bottom: 1px solid #E7E7E7;

    td {
      height: 46px;
    }

    &:nth-child(odd) td {
      background-color: #FFFFFF;
    }

    &:nth-child(even) td {
      background-color: #F5F9FF;
    }
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(.el-table thead th) {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #00000066;
    background-color: #F5F9FF !important;
    border-bottom: 1px solid #E7E7E7;
  }

  :deep(.el-table thead th:first-child) {
    border-top-left-radius: 0;
  }

  :deep(.el-table thead th:last-child) {
    border-top-right-radius: 0;
  }

  :deep(.index-col) {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #000000E5;
  }
}

.capacity-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.capacity-text {
  font-size: 12px;
  color: $text-regular;
  white-space: nowrap;
}
</style>
