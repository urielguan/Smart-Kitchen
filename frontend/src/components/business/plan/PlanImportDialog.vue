<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Download } from '@element-plus/icons-vue'
import { usePlanStore } from '@/stores/modules/plan'

const planStore = usePlanStore()

const uploadRef = ref()
const fileRef = ref<File | null>(null)
const fileList = ref<any[]>([])

const handleClose = () => {
  fileRef.value = null
  fileList.value = []
  planStore.closeImportDialog()
}

const handleDownloadTemplate = () => {
  planStore.downloadTemplate()
}

const handleChange = (file: any) => {
  const fileName = file.name.toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    ElMessage.warning('请选择 Excel 文件（.xlsx 或 .xls）')
    fileList.value = []
    nextTick(() => { uploadRef.value?.clearFiles() })
    return false
  }
  fileRef.value = file.raw
  fileList.value = [file]
  return false
}

const handleRemove = () => {
  fileRef.value = null
  fileList.value = []
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先删除已选择的文件')
}

const handleConfirm = async () => {
  if (!fileRef.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  await planStore.handleImport(fileRef.value)
}
</script>

<template>
  <el-dialog
    v-model="planStore.importDialogVisible"
    title="导入菜谱计划"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-content">
      <!-- 模板下载 -->
      <div class="template-section">
        <el-button type="primary" link @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        <span class="template-hint">
          下载模板并按要求填写计划数据
        </span>
      </div>

      <el-divider />

      <!-- 文件上传 -->
      <el-upload
        ref="uploadRef"
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
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将 Excel 文件拖到此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            仅支持 .xlsx 或 .xls 格式的 Excel 文件
          </div>
        </template>
      </el-upload>

      <!-- 说明 -->
      <div class="import-tips">
        <h4>导入说明：</h4>
        <ul>
          <li>模板包含两个 Sheet：「计划信息」和「菜谱明细」，通过「导入序号」列关联</li>
          <li>必填项：计划日期、实施开始日期、实施结束日期、餐次、就餐人数</li>
          <li>目标人群为选填，支持留空</li>
          <li>餐次支持填写中文：早餐/午餐/晚餐/夜宵，也兼容 breakfast/lunch/dinner/supper</li>
          <li>菜谱编码必须是系统中已存在的有效菜谱（已停用的菜谱无法导入）</li>
          <li>如果某条计划中的菜谱在菜谱库中不存在，该条计划将导入失败</li>
          <li>计划单号可选：不填则新建计划（草稿状态），填写已有计划单号则更新或生成调整申请</li>
          <li>已审核的计划有修改时，会自动生成调整申请记录，审核通过后生效</li>
          <li>草稿或已驳回的计划可直接更新</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="!fileRef"
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
      margin: 0 0 8px 0;
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
