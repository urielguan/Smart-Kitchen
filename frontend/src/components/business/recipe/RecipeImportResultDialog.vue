<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheckFilled, CircleCloseFilled, Download, WarningFilled } from '@element-plus/icons-vue'
import { useRecipeStore } from '@/stores/modules/recipe'

const recipeStore = useRecipeStore()

const visible = computed({
  get: () => recipeStore.importResultVisible,
  set: (value) => {
    if (!value) {
      recipeStore.closeImportResult()
    }
  }
})

const result = computed(() => recipeStore.importResult)

const handleClose = () => {
  recipeStore.closeImportResult()
}

const handleDownloadErrors = () => {
  recipeStore.downloadErrorFile()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="导入结果"
    width="680px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-result-content">
      <div class="result-stats">
        <div class="stat-item">
          <div class="stat-icon total">
            <el-icon :size="28"><WarningFilled /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">总条数</span>
            <span class="stat-value">{{ result?.total || 0 }}</span>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon success">
            <el-icon :size="28"><CircleCheckFilled /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">成功</span>
            <span class="stat-value success">{{ result?.successCount || 0 }}</span>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon error">
            <el-icon :size="28"><CircleCloseFilled /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">失败</span>
            <span class="stat-value error">{{ result?.failCount || 0 }}</span>
          </div>
        </div>
      </div>

      <div v-if="result?.hasErrors" class="error-section">
        <el-alert type="error" :closable="false" show-icon>
          <template #title>部分数据导入失败</template>
          <p class="error-desc">请查看失败原因，必要时下载错误文件修正后重新导入。</p>
        </el-alert>

        <el-table
          v-if="result?.failures?.length"
          :data="result.failures"
          border
          max-height="240"
          class="failure-table"
        >
          <el-table-column prop="rowNum" label="行号" width="80" />
          <el-table-column prop="recipeCode" label="菜谱编码" min-width="140" />
          <el-table-column prop="recipeName" label="菜谱名称" min-width="160" />
          <el-table-column prop="errorMessage" label="失败原因" min-width="220" />
        </el-table>

        <el-button
          v-if="result?.errorFileUrl"
          type="danger"
          plain
          class="download-error-btn"
          @click="handleDownloadErrors"
        >
          <el-icon><Download /></el-icon>
          下载错误文件
        </el-button>
      </div>

      <div v-else class="success-section">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="所有菜谱数据导入成功"
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