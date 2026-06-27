<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { AlertCloseDTO } from '@/types/alert'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: AlertCloseDTO, done: () => void]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const closeRemark = ref('')
const archiveRemark = ref('')
const submitting = ref(false)

watch(visible, (val) => {
  if (!val) {
    closeRemark.value = ''
    archiveRemark.value = ''
  }
})

const handleSubmit = () => {
  if (!closeRemark.value.trim()) {
    ElMessage.warning('请输入关闭说明')
    return
  }
  emit('submit', {
    closeRemark: closeRemark.value.trim(),
    archiveRemark: archiveRemark.value.trim() || undefined,
  }, () => {
    submitting.value = false
  })
  submitting.value = true
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="520px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    append-to-body
    destroy-on-close
    class="alert-close-form-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">关闭告警</span>
        <div class="close-btn" @click="visible = false">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form label-width="90px">
      <el-form-item label="关闭说明" required>
        <el-input
          v-model="closeRemark"
          type="textarea"
          :rows="4"
          placeholder="请输入关闭说明"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="归档说明">
        <el-input
          v-model="archiveRemark"
          type="textarea"
          :rows="3"
          placeholder="请输入归档说明（选填）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="visible = false">取消</el-button>
        <el-button class="btn-primary" :loading="submitting" @click="handleSubmit">确定关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.alert-close-form-dialog.el-dialog {
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

.alert-close-form-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.alert-close-form-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.alert-close-form-dialog .el-dialog__footer {
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
  min-width: 80px;
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

:deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}
</style>
