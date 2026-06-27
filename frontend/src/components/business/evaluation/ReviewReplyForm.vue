<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: { replyContent: string }, onSuccess: () => void]
}>()

const formRef = ref<FormInstance>()

const formData = ref({
  replyContent: ''
})

const rules: FormRules = {
  replyContent: [
    { required: true, message: '请输入回复内容', trigger: 'blur' },
    { max: 1000, message: '回复内容不能超过1000字', trigger: 'blur' }
  ]
}

const submitting = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    emit('submit', { replyContent: formData.value.replyContent }, () => {
      formData.value.replyContent = ''
    })
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  formData.value.replyContent = ''
  formRef.value?.resetFields()
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    class="review-reply-form-dialog"
    :show-close="false"
    align-center
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">回复评价</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
      <el-form-item label="回复内容" prop="replyContent">
        <el-input
          v-model="formData.replyContent"
          type="textarea"
          :rows="6"
          placeholder="请输入回复内容"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <button class="btn-cancel" @click="handleClose">取消</button>
        <button class="btn-save" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? '确定中...' : '确定' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

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
  cursor: pointer;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-save {
  width: 60px;
  height: 32px;
  background: #7288FA;
  border: 1px solid #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

:deep(.el-form-item__label) {
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.85);
  line-height: 32px;
  padding-right: 9px;
}

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

:deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}
</style>

<style lang="scss">
.review-reply-form-dialog.el-dialog {
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

.review-reply-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.review-reply-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  display: flex;
  flex-direction: column;
}

.review-reply-form-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>
