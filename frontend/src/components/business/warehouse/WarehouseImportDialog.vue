<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { useWarehouseStore } from '@/stores/modules/warehouse'
import type { WarehouseImportTarget } from '@/types/warehouse'

const props = defineProps<{
  visible: boolean
  target: WarehouseImportTarget
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const warehouseStore = useWarehouseStore()
const fileRef = ref<File | null>(null)
const fileList = ref<any[]>([])
const MAX_FILE_SIZE = 10 * 1024 * 1024

const dialogTitle = computed(() => props.target === 'warehouse' ? '导入仓库' : '导入仓位')
const templateHint = computed(() => props.target === 'warehouse'
  ? '下载模板后按字段说明填写仓库数据'
  : '下载模板后按字段说明填写仓位数据'
)
const tips = computed(() => props.target === 'warehouse'
  ? [
      '仅支持 .xlsx 格式，单个文件大小不超过 10MB。',
      '请勿修改模板表头；如仓库编码重复或数据重复，系统会拦截该行并记录失败原因。',
      '导入成功后会自动刷新仓库列表和统计数据。'
    ]
  : [
      '仅支持 .xlsx 格式，单个文件大小不超过 10MB。',
      '请先确认仓位所属仓库编码填写正确；如仓位编码重复或数据重复，系统会拦截该行并记录失败原因。',
      '导入成功后会自动刷新当前页面数据，并支持下载错误文件继续修正。'
    ]
)

const errorFileName = computed(() => warehouseStore.lastImportResult?.errorFileName || '')

const resetLocalState = () => {
  fileRef.value = null
  fileList.value = []
}

watch(() => props.visible, (visible) => {
  if (!visible) {
    resetLocalState()
  }
})

const handleClose = () => {
  resetLocalState()
  warehouseStore.closeImportDialog()
  emit('update:visible', false)
}

const handleDownloadTemplate = () => {
  warehouseStore.downloadImportTemplate(props.target)
}

const handleChange = (file: any) => {
  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx')) {
    ElMessage.warning('请选择 .xlsx 格式文件')
    return false
  }

  if ((file.size || file.raw?.size || 0) > MAX_FILE_SIZE) {
    ElMessage.warning('文件大小不能超过 10MB')
    return false
  }

  fileRef.value = file.raw
  fileList.value = [file]
  return false
}

const handleRemove = () => {
  resetLocalState()
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先删除已选择的文件')
}

const handleImport = async () => {
  if (!fileRef.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }

  const success = await warehouseStore.handleImport(fileRef.value, props.target)
  if (success && !errorFileName.value) {
    resetLocalState()
    emit('update:visible', false)
  }
}

const handleDownloadErrorFile = () => {
  if (!errorFileName.value) {
    return
  }
  warehouseStore.downloadImportErrorFile(errorFileName.value, props.target)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="560px"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="import-content">
      <div class="template-section">
        <el-button type="primary" link @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        <span class="template-hint">{{ templateHint }}</span>
      </div>

      <el-upload
        class="upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        :file-list="fileList"
        accept=".xlsx"
        :on-change="handleChange"
        :on-remove="handleRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将 Excel 文件拖到此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx 格式，大小不超过 10MB</div>
        </template>
      </el-upload>

      <div class="import-tips">
        <h4>导入说明：</h4>
        <ul>
          <li v-for="tip in tips" :key="tip">{{ tip }}</li>
        </ul>
      </div>

      <div v-if="errorFileName" class="error-file-section">
        <span>导入存在失败记录，可下载错误文件继续修正。</span>
        <el-button type="danger" link @click="handleDownloadErrorFile">下载错误文件</el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="!fileRef"
        :loading="warehouseStore.importLoading"
        @click="handleImport"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.import-content {
  .template-section {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;

    .template-hint {
      color: #909399;
      font-size: 13px;
    }
  }

  .upload-area {
    width: 100%;

    :deep(.el-upload-dragger) {
      width: 100%;
    }
  }

  .import-tips {
    margin-top: 16px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;

    h4 {
      margin: 0 0 8px;
      font-size: 13px;
      color: #606266;
    }

    ul {
      margin: 0;
      padding-left: 20px;

      li {
        font-size: 12px;
        color: #909399;
        line-height: 1.8;
      }
    }
  }

  .error-file-section {
    margin-top: 12px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px;
    border-radius: 4px;
    background: #fff7e6;
    color: #c45656;
    font-size: 13px;
  }
}
</style>
