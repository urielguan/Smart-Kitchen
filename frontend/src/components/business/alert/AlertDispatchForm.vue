<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { HandlerOption, AlertDispatchForm } from '@/types/alert'
import { PRIORITY_OPTIONS } from '@/constants/evaluation'
import type { FormInstance, FormRules } from 'element-plus'
import { get } from '@/api'

interface Props {
  modelValue: boolean
  handlers: HandlerOption[]
  alertId?: number | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: AlertDispatchForm]
}>()

const formRef = ref<FormInstance | null>(null)

const formData = ref<AlertDispatchForm>({
  dispatchType: 'manual',
  handlerId: undefined,
  priority: undefined,
  deadline: undefined,
  remark: '',
})

const submitting = ref(false)
const ruleHandlers = ref<HandlerOption[]>([])
const loadingHandlers = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

// 实际显示的处理人列表：有 alertId 时使用规则范围处理人，否则用默认全部
const displayHandlers = computed(() => {
  return props.alertId ? ruleHandlers.value : props.handlers
})

const rules: FormRules = {
  handlerId: [
    { required: true, message: '请选择处理人', trigger: 'change' },
  ],
  priority: [
    { required: true, message: '请选择优先级', trigger: 'change' },
  ],
  deadline: [
    { required: true, message: '请选择截止时间', trigger: 'change' },
  ],
}

// 加载规则范围内的处理人
const loadRuleHandlers = async () => {
  if (!props.alertId) {
    ruleHandlers.value = []
    return
  }
  try {
    loadingHandlers.value = true
    const res = await get(`/v1/device/alerts/${props.alertId}/dispatch-handlers`)
    if (res.code === 'SUCCESS' && Array.isArray(res.data)) {
      ruleHandlers.value = res.data.map((item: any) => ({
        id: item.id,
        name: item.real_name,
        orgId: item.org_id,
        orgName: item.org_name || '',
        position: '',
      }))
    }
  } catch {
    ruleHandlers.value = []
  } finally {
    loadingHandlers.value = false
  }
}

watch(visible, (val) => {
  if (val) {
    loadRuleHandlers()
  } else {
    resetForm()
  }
})

const resetForm = () => {
  formData.value = {
    dispatchType: 'manual',
    handlerId: undefined,
    priority: undefined,
    deadline: undefined,
    remark: '',
  }
  ruleHandlers.value = []
  formRef.value?.clearValidate()
}

const formatLocalDateTime = (date: Date | undefined): string | undefined => {
  if (!date) return undefined
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    const submitData = {
      ...formData.value,
      deadline: formatLocalDateTime(formData.value.deadline as unknown as Date),
    }
    emit('submit', submitData)
  } catch {
    // 校验失败
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="500px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    append-to-body
    destroy-on-close
    class="alert-dispatch-form-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">人工派单</span>
        <div class="close-btn" @click="visible = false">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
      <el-form-item label="处理人" prop="handlerId">
        <el-select v-model="formData.handlerId" placeholder="请选择处理人" filterable :loading="loadingHandlers" style="width: 100%">
          <el-option
            v-for="handler in displayHandlers"
            :key="handler.id"
            :label="handler.orgName ? `${handler.name}（${handler.orgName}）` : handler.name"
            :value="handler.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="优先级" prop="priority">
        <el-select v-model="formData.priority" placeholder="请选择优先级" style="width: 100%">
          <el-option v-for="item in PRIORITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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

      <el-form-item label="备注">
        <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入派单备注" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="visible = false">取消</el-button>
        <el-button class="btn-primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.alert-dispatch-form-dialog.el-dialog {
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

.alert-dispatch-form-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.alert-dispatch-form-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.alert-dispatch-form-dialog .el-dialog__footer {
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
</style>
