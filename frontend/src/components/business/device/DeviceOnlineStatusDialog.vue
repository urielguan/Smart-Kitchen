<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceApi } from '@/api/modules/device'
import type { Device } from '@/types'
import {
  ONLINE_STATUS_OPTIONS,
  DEVICE_STATUS_MAP,
  getAllowedManualOnlineStatusOptions,
} from '@/constants/device'

interface Props {
  visible: boolean
  device: Device | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  success: []
}>()

const loading = ref(false)
const form = ref({
  onlineStatus: '',
  reason: '',
})

const onlineStatusMap = Object.fromEntries(ONLINE_STATUS_OPTIONS.map(option => [option.value, option]))

const availableOptions = computed(() =>
  getAllowedManualOnlineStatusOptions(props.device?.onlineStatus)
)

watch(() => props.visible, (visible) => {
  if (!visible) return
  form.value = {
    onlineStatus: availableOptions.value[0]?.value || '',
    reason: '',
  }
})

const getMessageText = (message: unknown, fallback: string) => {
  return typeof message === 'string' && message.trim() ? message.trim() : fallback
}

const handleSubmit = async () => {
  if (!props.device?.id) return
  if (!form.value.onlineStatus) {
    ElMessage.warning('请选择目标在线状态')
    return
  }
  if (!form.value.reason.trim()) {
    ElMessage.warning('请输入在线状态修正原因')
    return
  }

  loading.value = true
  try {
    const res = await deviceApi.updateOnlineStatus(props.device.id, {
      onlineStatus: form.value.onlineStatus,
      reason: form.value.reason.trim(),
      sourceType: 'manual',
    })
    if (res.code === 'SUCCESS') {
      ElMessage.success('在线状态修正成功')
      emit('update:visible', false)
      emit('success')
      return
    }
    ElMessage.error(getMessageText(res.message, '在线状态修正失败'))
  } catch (error: any) {
    ElMessage.error(getMessageText(error?.message, '在线状态修正失败'))
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="在线状态修正"
    width="520px"
    destroy-on-close
    @close="handleClose"
  >
    <div v-if="device" v-loading="loading">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="设备名称">{{ device.deviceName }}</el-descriptions-item>
        <el-descriptions-item label="设备状态">{{ DEVICE_STATUS_MAP[device.status] || device.statusName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前在线状态">
          <el-tag :type="onlineStatusMap[device.onlineStatus]?.type ?? 'info'" size="small">
            {{ device.onlineStatusName }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="100px">
        <el-form-item label="目标状态" required>
          <el-select v-model="form.onlineStatus" placeholder="请选择目标在线状态" style="width: 100%">
            <el-option
              v-for="option in availableOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div class="field-tip">仅支持 online / offline -> fault，或 fault -> online / offline 的人工修正。</div>
        </el-form-item>
        <el-form-item label="修正原因" required>
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="请输入本次在线状态修正原因，例如设备断网已确认或故障恢复"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.field-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
