<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue'
import { useInboundStore } from '@/stores/modules/inbound'

interface Props {
  visible: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const inboundStore = useInboundStore()
const fileList = ref<File[]>([])
const fileRef = ref<File | null>(null)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const importResult = computed(() => inboundStore.lastImportResult)
const errorFileName = computed(() => importResult.value?.errorFileName || '')

const resetSelection = () => {
  fileList.value = []
  fileRef.value = null
}

const handleChange = (uploadFile: { raw?: File }) => {
  const raw = uploadFile.raw
  if (!raw) return false
  const isXlsx = raw.name.toLowerCase().endsWith('.xlsx')
  if (!isXlsx) {
    ElMessage.warning('仅支持上传 xlsx 文件')
    resetSelection()
    return false
  }
  if (raw.size > 10 * 1024 * 1024) {
    ElMessage.warning('单个文件不能超过10MB')
    resetSelection()
    return false
  }
  fileRef.value = raw
  fileList.value = [raw]
  return false
}

const handleClose = () => {
  resetSelection()
  inboundStore.closeImportDialog()
  dialogVisible.value = false
}

const handleImport = async () => {
  if (!fileRef.value) {
    ElMessage.warning('请先选择导入文件')
    return
  }
  const success = await inboundStore.handleImport(fileRef.value)
  if (success && !inboundStore.lastImportResult?.errorFileName) {
    handleClose()
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="导入入库单" width="560px" @close="handleClose">
    <div class="inbound-import-dialog">
      <div class="inbound-import-dialog__actions">
        <el-button @click="inboundStore.downloadImportTemplate()">下载导入模板</el-button>
      </div>

      <el-upload
        action="#"
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        accept=".xlsx"
        @change="handleChange"
      >
        <el-button type="primary">选择文件</el-button>
      </el-upload>

      <div v-if="importResult" class="inbound-import-dialog__summary">
        <div class="stat-card total">
          <el-icon><WarningFilled /></el-icon>
          <div>
            <div class="stat-label">总条数</div>
            <div class="stat-value">{{ importResult.totalCount }}</div>
          </div>
        </div>
        <div class="stat-card success">
          <el-icon><CircleCheckFilled /></el-icon>
          <div>
            <div class="stat-label">成功</div>
            <div class="stat-value">{{ importResult.successCount }}</div>
          </div>
        </div>
        <div class="stat-card error">
          <el-icon><CircleCloseFilled /></el-icon>
          <div>
            <div class="stat-label">失败</div>
            <div class="stat-value">{{ importResult.failureCount }}</div>
          </div>
        </div>
      </div>

      <div v-if="importResult?.errors?.length" class="inbound-import-dialog__details">
        <div class="inbound-import-dialog__details-title">失败明细</div>
        <div v-for="item in importResult.errors" :key="`${item.rowNumber}-${item.field}-${item.reason}`" class="inbound-import-dialog__detail-item">
          <span class="detail-row">第 {{ item.rowNumber }} 行</span>
          <span class="detail-field">{{ item.field }}</span>
          <span class="detail-reason">{{ item.reason }}</span>
        </div>
      </div>

      <div v-if="errorFileName" class="inbound-import-dialog__result">
        <span>导入存在失败记录，可下载错误文件继续修正。</span>
        <el-button link type="danger" @click="inboundStore.downloadImportErrorFile(errorFileName)">下载错误文件</el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="inboundStore.importLoading" @click="handleImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.inbound-import-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.inbound-import-dialog__actions {
  display: flex;
  justify-content: flex-start;
}

.inbound-import-dialog__summary {
  display: flex;
  gap: 12px;
}

.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  background: #f5f7fa;

  &.total {
    color: #409eff;
  }

  &.success {
    color: #67c23a;
  }

  &.error {
    color: #f56c6c;
  }
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.inbound-import-dialog__details {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
  padding: 12px;
  border-radius: 8px;
  background: #fff7e6;
}

.inbound-import-dialog__details-title {
  font-weight: 600;
  color: #303133;
}

.inbound-import-dialog__detail-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.detail-row {
  color: #909399;
}

.detail-field {
  color: #e6a23c;
}

.detail-reason {
  flex: 1;
}

.inbound-import-dialog__result {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-radius: 8px;
  background: #fff7e6;
  color: #c45656;
  font-size: 13px;
}
</style>
