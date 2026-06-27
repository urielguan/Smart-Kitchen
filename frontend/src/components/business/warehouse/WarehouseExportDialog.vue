<script setup lang="ts">
import { computed } from 'vue'
import { useWarehouseStore } from '@/stores/modules/warehouse'
import type { WarehouseExportFormat, WarehouseImportTarget, WarehouseQuery } from '@/types/warehouse'

const props = defineProps<{
  visible: boolean
  target: WarehouseImportTarget
  exportParams: WarehouseQuery
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const warehouseStore = useWarehouseStore()

const dialogTitle = computed(() => props.target === 'warehouse' ? '导出仓库' : '导出仓位')
const targetSummary = computed(() => props.target === 'warehouse'
  ? '将根据当前仓库筛选条件导出仓库数据。'
  : '将根据当前仓库筛选条件导出仓位数据。'
)

const handleClose = () => {
  warehouseStore.closeExportDialog()
  emit('update:visible', false)
}

const handleFormatChange = (value: WarehouseExportFormat) => {
  warehouseStore.exportFormat = value
}

const handleExport = async () => {
  const success = await warehouseStore.handleExport(props.target)
  if (success) {
    emit('update:visible', false)
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="480px"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="export-content">
      <p class="summary">{{ targetSummary }}</p>
      <div class="filters">
        <div class="filter-row">
          <span class="label">仓库名称</span>
          <span class="value">{{ exportParams.warehouseName || '全部' }}</span>
        </div>
        <div class="filter-row">
          <span class="label">仓库类型</span>
          <span class="value">{{ exportParams.warehouseType || '全部' }}</span>
        </div>
        <div class="filter-row">
          <span class="label">状态</span>
          <span class="value">{{ exportParams.status || '全部' }}</span>
        </div>
      </div>

      <el-form label-width="80px">
        <el-form-item label="导出格式">
          <el-radio-group :model-value="warehouseStore.exportFormat" @update:model-value="handleFormatChange">
            <el-radio-button label="xlsx" value="xlsx">xlsx</el-radio-button>
            <el-radio-button label="csv" value="csv">csv</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="warehouseStore.exportLoading" @click="handleExport">
        确认导出
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.export-content {
  .summary {
    margin: 0 0 16px;
    color: #606266;
  }

  .filters {
    margin-bottom: 20px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
  }

  .filter-row {
    display: flex;
    justify-content: space-between;
    line-height: 28px;
    font-size: 13px;

    .label {
      color: #909399;
    }

    .value {
      color: #303133;
    }
  }
}
</style>
