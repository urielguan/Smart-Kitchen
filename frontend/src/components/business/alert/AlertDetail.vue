<script setup lang="ts">
import { computed } from 'vue'
import type { AlertDetail } from '@/types/alert'
import { ALERT_REVIEW_RESULT_LABELS } from '@/types/alert'
import { DEVICE_TYPE_MAP } from '@/constants/device'
import { formatDateTime } from '@/utils'

interface Props {
  modelValue: boolean
  alert: AlertDetail | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const getLevelTagType = (level: string) => {
  const map: Record<string, string> = {
    critical: 'danger',
    error: 'danger',
    warning: 'warning',
    info: 'info',
  }
  return map[level] || 'info'
}

const getStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    pending: 'info',
    assigned: 'primary',
    handling: 'warning',
    handled: 'success',
    reviewed: 'success',
    closed: 'info',
  }
  return map[status] || 'info'
}

const formatReviewResult = (result: string | null | undefined) => {
  if (!result) return '-'
  return ALERT_REVIEW_RESULT_LABELS[result as keyof typeof ALERT_REVIEW_RESULT_LABELS] || result
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="告警详情"
    width="800px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="emit('close')"
  >
    <div v-if="alert" class="alert-detail">
      <el-descriptions title="基本信息" :column="2" border>
        <el-descriptions-item label="告警编号">{{ alert.alertNo }}</el-descriptions-item>
        <el-descriptions-item label="告警级别">
          <el-tag :type="getLevelTagType(alert.alertLevel)" size="small">
            {{ alert.alertLevelName }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="告警类型">{{ alert.alertTypeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusTagType(alert.status)" size="small">
            {{ alert.statusName }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="设备名称">{{ alert.deviceName }}</el-descriptions-item>
        <el-descriptions-item label="设备类型">{{ DEVICE_TYPE_MAP[alert.deviceType] || alert.deviceType || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="alert.alertRuleName" label="关联规则">{{ alert.alertRuleName }}</el-descriptions-item>
        <el-descriptions-item v-else label="关联规则">-</el-descriptions-item>
        <el-descriptions-item label="触发时间" :span="2">{{ formatDateTime(alert.triggeredAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="告警内容" :span="2">{{ alert.alertContent }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-if="alert.alertDetail" title="告警数据" :column="2" border class="mt-20">
        <el-descriptions-item
          v-for="(value, key) in alert.alertDetail"
          :key="key"
          :label="key"
        >
          {{ value }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="alert.alertImages?.length" class="image-section mt-20">
        <div class="section-title">告警图片</div>
        <el-image
          v-for="(img, idx) in alert.alertImages"
          :key="idx"
          :src="img"
          :preview-src-list="alert.alertImages"
          fit="cover"
          class="alert-image"
        />
      </div>

      <el-descriptions v-if="alert.assignedTo" title="指派信息" :column="2" border class="mt-20">
        <el-descriptions-item label="指派给">{{ alert.assignedToName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="指派时间">{{ formatDateTime(alert.assignedAt) || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-if="alert.handledAt" title="处置信息" :column="2" border class="mt-20">
        <el-descriptions-item label="处置人">{{ alert.handledByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置时间">{{ formatDateTime(alert.handledAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置结果" :span="2">{{ alert.handleResult }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-if="alert.reviewedAt" title="复核信息" :column="2" border class="mt-20">
        <el-descriptions-item label="复核人">{{ alert.reviewedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="复核时间">{{ formatDateTime(alert.reviewedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="复核结论" :span="2">{{ formatReviewResult(alert.reviewResult) }}</el-descriptions-item>
        <el-descriptions-item v-if="alert.reviewRemark" label="复核备注" :span="2">
          {{ alert.reviewRemark }}
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-if="alert.closedAt || alert.archivedAt" title="闭环信息" :column="2" border class="mt-20">
        <el-descriptions-item label="关闭人">{{ alert.closedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关闭时间">{{ formatDateTime(alert.closedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关闭说明" :span="2">{{ alert.closeRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归档人">{{ alert.archivedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归档时间">{{ formatDateTime(alert.archivedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归档说明" :span="2">{{ alert.archiveRemark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.alert-detail {
  max-height: 60vh;
  overflow-y: auto;
}
.mt-20 {
  margin-top: 20px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 12px;
  color: $text-primary;
}
.image-section {
  .alert-image {
    width: 120px;
    height: 120px;
    margin-right: 10px;
    border-radius: 4px;
  }
}
</style>
