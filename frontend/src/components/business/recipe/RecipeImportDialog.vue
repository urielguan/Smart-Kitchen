<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { useRecipeStore } from '@/stores/modules/recipe'

const recipeStore = useRecipeStore()

const fileRef = ref<File | null>(null)
const fileList = ref<any[]>([])

const resetLocalState = () => {
  fileRef.value = null
  fileList.value = []
}

const handleClose = () => {
  resetLocalState()
  recipeStore.closeImportDialog()
}

const handleDownloadTemplate = () => {
  recipeStore.downloadTemplate()
}

const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

const handleChange = (file: any) => {
  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    ElMessage.warning('请选择 Excel 文件（.xlsx 或 .xls）')
    return false
  }

  if (file.size > MAX_FILE_SIZE) {
    ElMessage.warning('文件大小不能超过 10MB，请压缩后重新上传')
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

const handleConfirm = async () => {
  if (!fileRef.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }

  const success = await recipeStore.handleImport(fileRef.value)
  if (success) {
    resetLocalState()
  }
}
</script>

<template>
  <el-dialog
    v-model="recipeStore.importDialogVisible"
    title="导入菜谱"
    width="560px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-content">
      <div class="template-section">
        <el-button type="primary" link @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        <span class="template-hint">下载模板后按字段说明填写菜谱数据</span>
      </div>

      <el-divider />

      <el-upload
        class="upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        :file-list="fileList"
        accept=".xlsx,.xls"
        :on-change="handleChange"
        :on-remove="handleRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将 Excel 文件拖到此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx 或 .xls 格式的 Excel 文件</div>
        </template>
      </el-upload>

      <div class="import-tips">
        <h4>导入说明：</h4>
        <ul>
          <li>同一菜谱多个食材占多行，菜谱信息重复填写，请勿修改表头。</li>
          <li>系统按"菜谱编码"匹配：匹配到则更新，未匹配到则新增。</li>
          <li>状态支持填写：启用/停用（或active/inactive）。新增默认启用。</li>
          <li>导入完成后会返回成功数、失败数和失败原因，并支持下载错误文件。</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="!fileRef"
        :loading="recipeStore.importLoading"
        @click="handleConfirm"
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
}
</style>