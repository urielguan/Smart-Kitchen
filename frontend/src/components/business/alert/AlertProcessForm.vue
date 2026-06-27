<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { AlertProcessForm } from '@/types/alert'
import { alertApi } from '@/api/modules/alert'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: AlertProcessForm, done: () => void]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const formData = ref<AlertProcessForm>({
  handleResult: '',
  handleAttachments: [],
})

const uploading = ref(false)
const submitting = ref(false)
const fileInput = ref<HTMLInputElement>()

const handleClickUpload = () => {
  if (uploading.value) return
  if ((formData.value.handleAttachments?.length || 0) >= 5) {
    ElMessage.warning('最多上传5个附件')
    return
  }
  fileInput.value?.click()
}

const onFileChange = (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  ;(e.target as HTMLInputElement).value = ''
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('单个附件大小不能超过10MB')
    return
  }
  handleUpload(file)
}

watch(visible, (val) => {
  if (!val) {
    formData.value = { handleResult: '', handleAttachments: [] }
  }
})

const canSubmit = computed(() => formData.value.handleResult.trim().length > 0)

const handleUpload = async (file: File) => {
  uploading.value = true
  try {
    const res = await alertApi.uploadAttachment(file)
    if (res.code === 'SUCCESS' && res.data) {
      if (!formData.value.handleAttachments) formData.value.handleAttachments = []
      formData.value.handleAttachments.push({ url: res.data.fileUrl, name: res.data.fileName })
    }
  } catch (e) {
    // Error displayed by interceptor
  } finally {
    uploading.value = false
  }
}

const removeAttachment = (index: number) => {
  formData.value.handleAttachments?.splice(index, 1)
}

const handleSubmit = () => {
  if (!canSubmit.value) return
  emit('submit', { ...formData.value }, () => {
    submitting.value = false
  })
  submitting.value = true
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="550px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    append-to-body
    destroy-on-close
    class="alert-process-form-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">处理工单</span>
        <div class="close-btn" @click="visible = false">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form label-width="80px">
      <el-form-item label="处理结果" required>
        <el-input
          v-model="formData.handleResult"
          type="textarea"
          :rows="4"
          placeholder="请输入处理结果"
          maxlength="300"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="处理附件" class="upload-form-item">
        <div>
          <input ref="fileInput" type="file" style="display: none" @change="onFileChange" />
          <el-button size="small" :loading="uploading" @click="handleClickUpload">
            {{ uploading ? '上传中...' : '上传附件' }}
          </el-button>
          <span class="upload-tip">最多上传5个附件，单个文件不超过10MB</span>
        </div>
        <div v-if="formData.handleAttachments?.length" class="attachment-list">
          <div v-for="(item, idx) in formData.handleAttachments" :key="idx" class="attachment-item">
            <span class="attachment-name">{{ item.name }}</span>
            <el-button type="danger" link size="small" @click="removeAttachment(idx)">删除</el-button>
          </div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="visible = false">取消</el-button>
        <el-button class="btn-primary" :loading="submitting || uploading" :disabled="!canSubmit" @click="handleSubmit">
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.alert-process-form-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  margin: auto !important;
}

.alert-process-form-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.alert-process-form-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.alert-process-form-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>

<style lang="scss" scoped>
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

.dialog-footer {
  display: flex;
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
  color: #53545C;
  font-size: 13px;

  &:hover, &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-primary {
  min-width: 58px;
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  color: #fff;

  &:hover {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #fff;
  }

  &.is-disabled,
  &.is-disabled:hover {
    background: #D4DBF5;
    border-color: #D4DBF5;
    color: #fff;
  }
}

/* ---- 输入框 / 选择器 ---- */
:deep(.el-input__wrapper) {
  height: 32px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #D9D9D9 inset !important;
  padding: 4px 12px;

  &:hover {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }

  &.is-focus {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }
}

:deep(.el-input__inner) {
  font-size: 14px;
  height: 24px;
  line-height: 24px;
}

:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

/* ---- 文本域 ---- */
:deep(.el-textarea__inner) {
  border: 1px solid #D9D9D9;
  border-radius: 2px;
  font-size: 14px;
  padding: 5px 12px;

  &:hover {
    border-color: #7288FA;
  }

  &:focus {
    border-color: #7288FA;
    box-shadow: none;
  }
}

/* ---- 上传表单项 ---- */
.upload-form-item :deep(.el-form-item__content) {
  flex-direction: column;
  align-items: flex-start;
}

.upload-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

.attachment-list {
  margin-top: 8px;
  .attachment-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 0;
    .attachment-name {
      color: $text-primary;
      font-size: 13px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: 350px;
    }
  }
}
</style>
