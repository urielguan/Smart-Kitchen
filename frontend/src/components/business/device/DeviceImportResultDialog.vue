<script setup lang="ts">
import type { DeviceImportResult } from '@/types/device'
import { CircleCheckFilled, CircleCloseFilled, WarningFilled, Download } from '@element-plus/icons-vue'

interface Props {
  visible: boolean
  result: DeviceImportResult | null
}

defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  close: []
}>()

const handleClose = () => {
  emit('close')
  emit('update:visible', false)
}

const downloadErrorFile = (base64: string) => {
  const binaryStr = atob(base64)
  const bytes = new Uint8Array(binaryStr.length)
  for (let i = 0; i < binaryStr.length; i++) {
    bytes[i] = binaryStr.charCodeAt(i)
  }
  const blob = new Blob([bytes], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `导入错误详情_${new Date().toISOString().slice(0, 10)}.xlsx`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="导入结果"
    width="680px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-if="result" class="import-result-content">
      <!-- 统计信息 — 卡片式布局，对齐供应商导入结果 -->
      <div class="result-stats">
        <div class="stat-item">
          <div class="stat-icon total">
            <el-icon :size="28"><WarningFilled /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">总条数</span>
            <span class="stat-value">{{ result.total }}</span>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon success">
            <el-icon :size="28"><CircleCheckFilled /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">成功</span>
            <span class="stat-value success">{{ result.successCount }}</span>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon error">
            <el-icon :size="28"><CircleCloseFilled /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">失败</span>
            <span class="stat-value error">{{ result.failCount }}</span>
          </div>
        </div>
      </div>

      <!-- 错误详情 -->
      <div v-if="result.hasErrors" class="error-section">
        <el-alert type="error" :closable="false" show-icon>
          <template #title>部分数据导入失败</template>
          <p class="error-desc">成功导入 {{ result.successCount }} 条，失败 {{ result.failCount }} 条。请查看失败原因，必要时下载错误文件修正后重新导入。</p>
        </el-alert>

        <el-table
          v-if="result.errorDetails?.length"
          :data="result.errorDetails"
          border
          max-height="240"
          class="failure-table"
        >
          <el-table-column prop="rowNum" label="行号" width="80" />
          <el-table-column prop="deviceCode" label="设备编码" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deviceCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="deviceName" label="设备名称" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deviceName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="失败原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span style="color: #F56C6C">{{ row.errorMessage }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-button
          v-if="result.errorFileBase64"
          type="danger"
          plain
          class="download-error-btn"
          @click="downloadErrorFile(result.errorFileBase64)"
        >
          <el-icon><Download /></el-icon>
          下载错误文件
        </el-button>
      </div>

      <!-- 全部成功 -->
      <div v-else class="success-section">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="所有设备数据导入成功"
        />
      </div>
    </div>

    <template #footer>
      <el-button type="primary" @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.import-result-content {
  .result-stats {
    display: flex;
    justify-content: space-around;
    padding: 20px 0;
    margin-bottom: 20px;
    background: #f5f7fa;
    border-radius: 8px;

    .stat-item {
      display: flex;
      align-items: center;
      gap: 12px;

      .stat-icon {
        width: 48px;
        height: 48px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;

        &.total {
          background: #e6f7ff;
          color: #1890ff;
        }

        &.success {
          background: #f6ffed;
          color: #52c41a;
        }

        &.error {
          background: #fff2f0;
          color: #ff4d4f;
        }
      }

      .stat-info {
        display: flex;
        flex-direction: column;

        .stat-label {
          font-size: 13px;
          color: #909399;
        }

        .stat-value {
          font-size: 24px;
          font-weight: 600;
          color: #303133;

          &.success {
            color: #67c23a;
          }

          &.error {
            color: #f56c6c;
          }
        }
      }
    }
  }

  .error-section {
    .el-alert {
      margin-bottom: 16px;

      .error-desc {
        margin: 8px 0 0;
        font-size: 13px;
        color: #909399;
      }
    }

    .failure-table {
      margin-bottom: 16px;
    }

    .download-error-btn {
      width: 100%;
    }
  }
}
</style>
