<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { HandlerOption, DispatchForm } from '@/types/evaluation'
import { PRIORITY_OPTIONS } from '@/constants/evaluation'
import type { FormInstance, FormRules } from 'element-plus'

interface Props {
  modelValue: boolean
  handlers: HandlerOption[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: DispatchForm]
}>()

/** 表单引用 */
const formRef = ref<FormInstance | null>(null)

/** 表单数据 */
const formData = ref<DispatchForm>({
  dispatchType: 'manual',
  handlerId: undefined,
  priority: undefined,
  deadline: undefined,
  remark: ''
})

/** 提交状态 */
const submitting = ref(false)

/** 弹窗可见性 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 表单校验规则 */
const rules: FormRules = {
  handlerId: [
    { required: true, message: '请选择处理人', trigger: 'change' }
  ],
  priority: [
    { required: true, message: '请选择优先级', trigger: 'change' }
  ],
  deadline: [
    { required: true, message: '请选择截止时间', trigger: 'change' }
  ]
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
    dispatchType: 'manual',
    handlerId: undefined,
    priority: undefined,
    deadline: undefined,
    remark: ''
  }
  formRef.value?.clearValidate()
}

/** 将 Date 格式化为本地时间字符串（避免时区偏移） */
const formatLocalDateTime = (date: Date | undefined): string | undefined => {
  if (!date) return undefined
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 提交表单 */
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitting.value = true
    const submitData = {
      ...formData.value,
      deadline: formatLocalDateTime(formData.value.deadline as unknown as Date)
    }
    emit('submit', submitData)
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
    class="dispatch-form-dialog"
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
        <span class="dialog-title">人工派单</span>
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
      <el-form-item label="处理人" prop="handlerId">
        <el-select
          v-model="formData.handlerId"
          placeholder="请选择处理人"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="handler in handlers"
            :key="handler.id"
            :label="handler.orgName ? `${handler.name}（${handler.orgName}）` : handler.name"
            :value="handler.id"
          >
            <div class="handler-option">
              <span>{{ handler.orgName ? `${handler.name}（${handler.orgName}）` : handler.name }}</span>
              <span class="handler-dept">{{ handler.orgName }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="优先级" prop="priority">
        <el-select
          v-model="formData.priority"
          placeholder="请选择优先级"
          clearable
          style="width: 100%"
        >
          <el-option
            v-for="item in PRIORITY_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="截止时间" prop="deadline">
        <el-date-picker
          v-model="formData.deadline"
          type="datetime"
          placeholder="请选择截止时间"
          style="width: 100%"
          :disabled-date="(time: Date) => time.getTime() < Date.now()"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入派单备注"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
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

.handler-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .handler-dept {
    font-size: 12px;
    color: #8C8C8C;
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

:deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #FF4D4F inset !important;

  &:hover,
  &.is-focus {
    box-shadow: 0 0 0 1px #FF4D4F inset !important;
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
.dispatch-form-dialog.el-dialog {
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

.dispatch-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.dispatch-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  display: flex;
  flex-direction: column;
}

.dispatch-form-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>
