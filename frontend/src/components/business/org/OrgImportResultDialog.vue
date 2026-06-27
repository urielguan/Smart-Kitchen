<script setup lang="ts">
import { computed } from 'vue'
import { Download, CircleCheckFilled, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue'
import { useOrgStore } from '@/stores/modules/org'

const orgStore = useOrgStore()

const visible = computed({
  get: () => orgStore.importResultVisible,
  set: (val) => {
    if (!val) orgStore.closeImportResult()
  }
})

const result = computed(() => orgStore.importResult)

const handleClose = () => {
  orgStore.closeImportResult()
}

const handleDownloadErrors = () => {
  orgStore.downloadErrorFile()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="480px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    @close="handleClose"
    class="org-import-result-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">导入结果</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div class="import-result-content">
      <!-- 统计数据 -->
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

      <!-- 失败提示 -->
      <div v-if="result?.hasErrors" class="error-section">
        <el-alert
          type="error"
          :closable="false"
          show-icon
        >
          <template #title>
            部分数据导入失败
          </template>
          <p class="error-desc">
            请下载错误文件查看失败原因，修改后重新导入。
          </p>
        </el-alert>

        <el-button type="danger" plain class="download-error-btn" @click="handleDownloadErrors">
          <el-icon><Download /></el-icon>
          下载错误文件
        </el-button>
      </div>

      <!-- 成功提示 -->
      <div v-else class="success-section">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="所有数据导入成功！"
        />
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-save" @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.org-import-result-dialog.el-dialog {
  width: 480px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.org-import-result-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.org-import-result-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.org-import-result-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>

<style lang="scss" scoped>
/* ---- 头部 ---- */
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 32px;
}

.dialog-title {
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  color: #000000;
}

.close-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE8CC;
  }
}

/* ---- 底部 ---- */
.dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
  padding: 12px 24px 16px;
}

.btn-save {
  padding: 5px 16px;
  width: 60px;
  height: 32px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }
}

/* ---- 内容 ---- */
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
        margin: 8px 0 0 0;
        font-size: 13px;
        color: #909399;
      }
    }

    .download-error-btn {
      width: 100%;
    }
  }

  .success-section {
    .el-alert {
      margin-bottom: 0;
    }
  }
}
</style>
