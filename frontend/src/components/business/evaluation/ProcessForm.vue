<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { ProcessWorkOrderForm } from '@/types/evaluation'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: boolean
  dispatchId: number | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: ProcessWorkOrderForm]
}>()

/** 表单引用 */
const formRef = ref<FormInstance | null>(null)

/** 表单数据 */
const formData = ref<ProcessWorkOrderForm>({
  action: 'process',
  content: '',
  images: []
})

/** 提交状态 */
const submitting = ref(false)

/** 弹窗可见性 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 操作选项 */
const actionOptions = [
  { label: '处理中', value: 'process' },
  { label: '已完成', value: 'complete' },
  { label: '取消', value: 'cancel' }
]

/** 表单校验规则 */
const rules: FormRules = {
  action: [
    { required: true, message: '请选择操作类型', trigger: 'change' }
  ],
  content: [
    { required: true, message: '请输入处理内容', trigger: 'blur' },
    { min: 5, max: 500, message: '处理内容长度在 5 到 500 个字符', trigger: 'blur' }
  ]
}

/** 图片上传前校验 */
const beforeUpload = (file: File): boolean => {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB!')
    return false
  }
  return true
}

/** 图片变化处理 */
const handleImageChange = (file: UploadFile, fileList: UploadFile[]) => {
  if (file.raw && beforeUpload(file.raw)) {
    // 这里实际项目中应该上传到服务器获取URL
    // 暂时使用本地预览
    formData.value.images = fileList
      .filter(f => f.raw)
      .map(f => URL.createObjectURL(f.raw!))
  }
}

/** 图片移除 */
const handleImageRemove = (file: UploadFile, fileList: UploadFile[]) => {
  formData.value.images = fileList
    .filter(f => f.raw)
    .map(f => URL.createObjectURL(f.raw!))
}

/** 监听弹窗关闭 */
watch(visible, (val) => {
  if (!val) {
    resetForm()
  }
})

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    action: 'process',
    content: '',
    images: []
  }
  formRef.value?.clearValidate()
}

/** 提交表单 */
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitting.value = true
    emit('submit', { ...formData.value })
  } catch {
    // 校验失败
  } finally {
    submitting.value = false
  }
}

/** 取消 */
const handleCancel = () => {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    class="process-form-dialog"
    :show-close="false"
    align-center
    :close-on-click-modal="false"
    append-to-body
    :modal-append-to-body="true"
    :z-index="2000"
    destroy-on-close
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">处理工单</span>
        <div class="close-btn" @click="handleCancel">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="80px"
    >
      <el-form-item label="操作类型" prop="action">
        <el-radio-group v-model="formData.action">
          <el-radio
            v-for="item in actionOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-radio-group>
      </el-form-item>

      <el-form-item label="处理内容" prop="content">
        <el-input
          v-model="formData.content"
          type="textarea"
          :rows="4"
          placeholder="请输入处理内容"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <!-- 处理工单时，暂不支持上传图片，先注释掉
      <el-form-item label="处理图片" prop="images">
        <el-upload
          action="#"
          list-type="picture-card"
          :auto-upload="false"
          :limit="5"
          accept="image/*"
          :on-change="handleImageChange"
          :on-remove="handleImageRemove"
        >
          <el-icon><Plus /></el-icon>
          <template #tip>
            <div class="upload-tip">
              最多上传5张图片，单张不超过5MB
            </div>
          </template>
        </el-upload>
      </el-form-item>
      -->
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <button class="btn-cancel" @click="handleCancel">取消</button>
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

:deep(.el-input__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

:deep(.el-form-item) {
  margin-bottom: 16px;
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
.process-form-dialog.el-dialog {
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

.process-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.process-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  display: flex;
  flex-direction: column;
}

.process-form-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>
