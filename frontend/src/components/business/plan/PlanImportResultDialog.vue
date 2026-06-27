<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheckFilled, CircleCloseFilled, Download, WarningFilled } from '@element-plus/icons-vue'
import { usePlanStore } from '@/stores/modules/plan'

const planStore = usePlanStore()

const visible = computed({
  get: () => planStore.importResultVisible,
  set: (value: boolean) => {
    if (!value) {
      planStore.closeImportResult()
    }
  }
})

const result = computed(() => planStore.importResult)

const summaryCards = computed(() => {
  const current = result.value
  return [
    { key: 'total', label: '总计划数', value: current?.total ?? 0, tone: 'total' },
    { key: 'created', label: '新增', value: current?.createdCount ?? 0, tone: 'success' },
    { key: 'updated', label: '直接更新', value: current?.updatedCount ?? 0, tone: 'success' },
    { key: 'adjustment', label: '生成调整申请', value: current?.adjustmentCreatedCount ?? 0, tone: 'warning' },
    { key: 'skipped', label: '跳过无变更', value: current?.skippedCount ?? 0, tone: 'info' },
    { key: 'failed', label: '失败', value: current?.failCount ?? 0, tone: 'error' }
  ]
})

const handleClose = () => {
  planStore.closeImportResult()
}

const handleDownloadErrors = () => {
  planStore.downloadErrorFile()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="导入结果"
    width="960px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-result-content">
      <div class="result-overview">
        <div v-for="card in summaryCards" :key="card.key" class="stat-card" :class="card.tone">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
        </div>
      </div>

      <el-alert
        v-if="result?.hasErrors"
        type="error"
        :closable="false"
        show-icon
        class="result-alert"
      >
        <template #title>存在导入失败的计划</template>
        <div class="alert-actions">
          <span>失败计划可下载错误文件修正后重新导入，不影响已成功导入的计划。</span>
          <el-button type="danger" plain @click="handleDownloadErrors">
            <el-icon><Download /></el-icon>
            下载错误文件
          </el-button>
        </div>
      </el-alert>

      <el-alert
        v-else
        type="success"
        :closable="false"
        show-icon
        title="本次导入已完成"
        class="result-alert"
      />

      <el-table
        v-if="result?.records?.length"
        :data="result.records"
        border
        max-height="420"
        class="result-table"
      >
        <el-table-column prop="seqNo" label="导入序号" width="100" />
        <el-table-column prop="planCode" label="计划单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="planDate" label="计划日期" width="120" />
        <el-table-column prop="actionName" label="处理结果" width="140">
          <template #default="{ row }">
            <el-tag
              :type="row.action === 'failed' ? 'danger' : row.action === 'adjustment_created' ? 'warning' : row.action === 'skipped_no_change' ? 'info' : 'success'"
            >
              {{ row.actionName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="320" show-overflow-tooltip />
      </el-table>
    </div>

    <template #footer>
      <el-button type="primary" @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.import-result-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 16px;
  border-radius: 10px;
  background: #f5f7fa;
  border: 1px solid transparent;

  .stat-label {
    font-size: 13px;
    color: #606266;
  }

  .stat-value {
    margin-top: 8px;
    font-size: 28px;
    font-weight: 600;
    line-height: 1;
  }

  &.total {
    background: #eef6ff;
    border-color: #c6e2ff;
    color: #1d4ed8;
  }

  &.success {
    background: #f0f9eb;
    border-color: #d9ecb5;
    color: #389e0d;
  }

  &.warning {
    background: #fff7e6;
    border-color: #ffd591;
    color: #d46b08;
  }

  &.info {
    background: #f4f4f5;
    border-color: #d3d4d6;
    color: #606266;
  }

  &.error {
    background: #fef0f0;
    border-color: #fbc4c4;
    color: #cf1322;
  }
}

.result-alert {
  :deep(.el-alert__content) {
    width: 100%;
  }
}

.alert-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.result-table {
  width: 100%;
}

@media (max-width: 768px) {
  .result-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .alert-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
