<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Download } from '@element-plus/icons-vue'
import { useMaterialStore } from '@/stores/modules/material'

const materialStore = useMaterialStore()

const fileRef = ref<File | null>(null)
const fileList = ref<any[]>([])
const importing = ref(false)

const handleClose = () => {
  fileRef.value = null
  fileList.value = []
  materialStore.closeImportDialog()
}

const handleDownloadTemplate = () => {
  materialStore.downloadTemplate()
}

const handleChange = (file: any) => {
  const fileName = file.name.toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    ElMessage.warning('请选择 Excel 文件（.xlsx 或 .xls）')
    fileList.value = []
    fileRef.value = null
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过10MB')
    fileList.value = []
    fileRef.value = null
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
  importing.value = true
  try {
    await materialStore.handleImport(fileRef.value)
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="materialStore.importDialogVisible"
    width="500px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    @close="handleClose"
    class="material-import-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">导入物料</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div class="import-content">
      <!-- 模板下载 -->
      <div class="template-section">
        <el-button class="btn-download-template" type="primary" link @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        <span class="template-hint">
          下载模板并按要求填写物料数据
        </span>
      </div>

      <!-- 文件上传 -->
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
          <li>物料编码留空时自动生成（格式：MAT+6位数字），填写已存在的编码时将覆盖更新</li>
          <li>物料名称、规格、单位、类别、保质期、最低库存、最高库存为必填项</li>
          <li>临期提醒天数默认7天，预警天数默认30天</li>
          <li>类别以模板说明行中列出的有效值为准，可在「字典分类维护 - 物料类别」中管理</li>
          <li>状态为 active（启用）或 inactive（停用），留空默认为启用</li>
          <li>同一租户下物料名称和规格组合不能重复</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button
          class="btn-save"
          :disabled="!fileRef"
          :loading="importing"
          @click="handleConfirm"
        >
          确认导入
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.material-import-dialog.el-dialog {
  width: 500px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.material-import-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.material-import-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.material-import-dialog.el-dialog .el-dialog__footer {
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

.btn-cancel {
  width: 58px;
  height: 32px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545C;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-save {
  padding: 5px 16px;
  width: 88px;
  height: 32px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  line-height: 22px;
  flex: none;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }

  &.is-disabled,
  &.is-disabled:hover,
  &.is-disabled:focus {
    background: #D4DBF5;
    border-color: #D4DBF5;
    color: #FFFFFF;
    cursor: not-allowed;
    box-shadow: none;
  }
}

/* ---- 内容 ---- */
.import-content {
  .template-section {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;

    :deep(.btn-download-template) {
      color: #0052D9;

      &:hover,
      &:focus {
        color: #4D94F5;
      }
    }

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

    :deep(.el-upload__text em) {
      color: #7288FA;
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
