<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { Download, Loading, UploadFilled } from '@element-plus/icons-vue'
import { supplierApi } from '@/api/modules/supplier'
import { useSupplierStore } from '@/stores/modules/supplier'
import type { SupplierImportValidationConflict } from '@/types/supplier'

const MAX_IMPORT_FILE_SIZE = 10 * 1024 * 1024
const FILE_SIZE_LIMIT_MESSAGE = '当前文件超过10MB限制，请拆分文件后重新上传'
const ROW_LIMIT_MESSAGE = '单次导入最多支持5000条供应商数据，请拆分表格后重试'

const supplierStore = useSupplierStore()

const uploadRef = ref<UploadInstance>()
const fileRef = ref<File | null>(null)
const validating = ref(false)
const validationMessage = ref('')
const effectiveRowCount = ref<number | null>(null)
const validationToken = ref(0)
const duplicateConflicts = ref<SupplierImportValidationConflict[]>([])

const resetLocalState = () => {
  fileRef.value = null
  validating.value = false
  validationMessage.value = ''
  effectiveRowCount.value = null
  duplicateConflicts.value = []
  validationToken.value += 1
}

const clearUploadWidgetState = () => {
  const uploadInstance = uploadRef.value as (UploadInstance & { $el?: HTMLElement }) | undefined
  uploadInstance?.clearFiles()
  const inputEl = uploadInstance?.$el?.querySelector('input[type="file"]') as HTMLInputElement | null
  if (inputEl) {
    inputEl.value = ''
  }
}

const handleClose = () => {
  resetLocalState()
  clearUploadWidgetState()
  supplierStore.closeImportDialog()
}

const handleDownloadTemplate = () => {
  supplierStore.downloadTemplate()
}

const handleChange = async (file: UploadFile) => {
  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    ElMessage.warning('请选择 Excel 文件（.xlsx 或 .xls）')
    resetLocalState()
    window.setTimeout(() => {
      clearUploadWidgetState()
    }, 0)
    return false
  }

  const rawFile = file.raw
  if (!rawFile) {
    ElMessage.warning('文件读取失败，请重新选择后再试')
    resetLocalState()
    window.setTimeout(() => {
      clearUploadWidgetState()
    }, 0)
    return false
  }

  if (rawFile.size > MAX_IMPORT_FILE_SIZE) {
    ElMessage.warning(FILE_SIZE_LIMIT_MESSAGE)
    resetLocalState()
    window.setTimeout(() => {
      clearUploadWidgetState()
    }, 0)
    return false
  }

  const currentValidationToken = validationToken.value + 1
  validationToken.value = currentValidationToken
  fileRef.value = rawFile
  validationMessage.value = ''
  effectiveRowCount.value = null
  duplicateConflicts.value = []
  validating.value = true

  try {
    const res = await supplierApi.validateImportFile(rawFile)
    if (currentValidationToken !== validationToken.value) {
      return false
    }

    if (res.code === 'SUCCESS' && res.data) {
      effectiveRowCount.value = res.data.effectiveRowCount
      if (res.data.rowLimitExceeded) {
        validationMessage.value = ROW_LIMIT_MESSAGE
        ElMessage.warning(ROW_LIMIT_MESSAGE)
      } else {
        duplicateConflicts.value = res.data.duplicateConflicts || []
        if (duplicateConflicts.value.length > 0) {
          validationMessage.value = '检测到导入校验冲突，请根据明细修正后重新导入'
          ElMessage.warning(validationMessage.value)
        }
      }
    }
  } catch (error: any) {
    if (currentValidationToken !== validationToken.value) {
      return false
    }
    validationMessage.value = error.message || '文件校验失败，请稍后重试'
    effectiveRowCount.value = null
    duplicateConflicts.value = []
    ElMessage.warning(validationMessage.value)
  } finally {
    if (currentValidationToken === validationToken.value) {
      validating.value = false
    }
  }

  return false
}

const handleRemove = () => {
  resetLocalState()
  clearUploadWidgetState()
}

const handleConfirm = async () => {
  if (!fileRef.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  if (validating.value) {
    ElMessage.warning('文件校验中，请稍候后再导入')
    return
  }
  if (validationMessage.value) {
    ElMessage.warning(validationMessage.value)
    return
  }

  const success = await supplierStore.handleImport(fileRef.value)
  if (success) {
    resetLocalState()
    clearUploadWidgetState()
  }
}
</script>

<template>
  <el-dialog
    v-model="supplierStore.importDialogVisible"
    title="导入供应商"
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
        <span class="template-hint">下载模板后按字段说明填写供应商数据</span>
      </div>

      <el-divider />

      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".xlsx,.xls"
        :on-change="handleChange"
        :on-remove="handleRemove"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将 Excel 文件拖到此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx 或 .xls 格式的 Excel 文件</div>
        </template>
      </el-upload>

      <div v-if="fileRef" class="selected-file">
        <span class="selected-file__name">{{ fileRef.name }}</span>
        <el-button type="danger" link @click="handleRemove">删除</el-button>
      </div>

      <div
        v-if="fileRef && (validating || validationMessage || effectiveRowCount !== null)"
        class="selected-file-status"
        :class="{
          'is-error': !!validationMessage,
          'is-success': !validating && !validationMessage && effectiveRowCount !== null
        }"
      >
        <el-icon v-if="validating" class="selected-file-status__icon is-loading"><Loading /></el-icon>
        <span v-if="validating">正在校验 Excel 数据，请稍候...</span>
        <span v-else-if="validationMessage">{{ validationMessage }}</span>
        <span v-else>当前文件有效数据 {{ effectiveRowCount }} 条，可继续导入</span>
      </div>

      <div v-if="duplicateConflicts.length" class="duplicate-conflict-list">
        <div class="duplicate-conflict-list__title">校验冲突明细</div>
        <div class="duplicate-conflict-list__body">
          <div
            v-for="conflict in duplicateConflicts"
            :key="`${conflict.rowNum}-${conflict.field}-${conflict.message}`"
            class="duplicate-conflict-list__item"
          >
            <span class="duplicate-conflict-list__row">第 {{ conflict.rowNum }} 行</span>
            <span class="duplicate-conflict-list__field">{{ conflict.field }}</span>
            <span class="duplicate-conflict-list__value">{{ conflict.conflictValue || '-' }}</span>
            <span class="duplicate-conflict-list__message">{{ conflict.message }}</span>
          </div>
        </div>
      </div>

      <div class="import-tips">
        <h4>导入说明：</h4>
        <ul>
          <li>单个 Excel 文件大小不能超过 10MB，单次导入最多支持 5000 条供应商数据。</li>
          <li>模板包含供应商必填字段和关键字段，请勿修改表头，且必须填写所属组织编码。</li>
          <li>系统按“供应商编码优先、统一社会信用代码二次校验”执行导入；编码命中仅更新白名单业务字段，不会通过导入变更供应商编码、所属组织编码、创建信息和状态。</li>
          <li>编码未命中但统一社会信用代码命中时，当前行直接失败；统一社会信用代码、营业执照编号和食品许可证号在同租户内必须唯一。</li>
          <li>新增供应商时将按模板中的所属组织编码匹配组织并创建，且仅允许导入当前账号数据权限范围内的组织。</li>
          <li>编码已存在时，系统会校验存量供应商原归属组织是否在当前账号数据权限范围内；不允许通过导入修改供应商所属组织。</li>
          <li>供应商类型支持填写名称或编码，且必须为当前租户启用状态的字典项。</li>
          <li>状态仅支持 `draft`、`pending`；新增留空默认待审核，更新时状态列仅作展示，不会覆盖原状态。</li>
          <li>导入完成后会返回成功数、失败数和失败原因，并支持下载错误文件。</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="!fileRef || validating || !!validationMessage"
        :loading="supplierStore.importLoading"
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

  .selected-file {
    margin-top: 12px;
    padding: 10px 12px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    background: #f8f9fb;

    &__name {
      flex: 1;
      min-width: 0;
      font-size: 13px;
      color: #606266;
      word-break: break-all;
    }
  }

  .selected-file-status {
    margin-top: 8px;
    padding: 8px 12px;
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: #606266;
    background: #f4f4f5;
    display: flex;
    align-items: center;
    gap: 6px;

    &__icon {
      flex-shrink: 0;
    }

    &.is-error {
      color: #e6a23c;
      background: #fdf6ec;
    }

    &.is-success {
      color: #67c23a;
      background: #f0f9eb;
    }
  }

  .duplicate-conflict-list {
    margin-top: 12px;
    border: 1px solid #f3d4d5;
    border-radius: 4px;
    background: #fef0f0;

    &__title {
      padding: 10px 12px;
      font-size: 13px;
      font-weight: 600;
      color: #c45656;
      border-bottom: 1px solid #f6d8d9;
    }

    &__body {
      max-height: 180px;
      overflow-y: auto;
      padding: 8px 12px;
    }

    &__item {
      display: grid;
      grid-template-columns: 80px 88px 1fr;
      gap: 8px;
      padding: 6px 0;
      font-size: 12px;
      line-height: 1.6;
      color: #606266;
    }

    &__row,
    &__field {
      color: #c45656;
      font-weight: 500;
    }

    &__value {
      color: #303133;
      word-break: break-all;
    }

    &__message {
      grid-column: 1 / -1;
      color: #909399;
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
