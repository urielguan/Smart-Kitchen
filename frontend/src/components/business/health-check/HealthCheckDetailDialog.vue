<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { formatDate, formatDateTime } from '@/utils'
import type { HealthCheckRecordDetail, HealthCheckUpdatePayload } from '@/types/health-check'
import { MORNING_CHECK_PERMISSIONS } from '@/constants/permission'
import { POSITION_MAP } from '@/constants/employee'
import {
  HEALTH_CHECK_STATUS_MAP,
  HEALTH_CHECK_RESULT_MAP,
  TEMPERATURE_STATUS_MAP,
  HYGIENE_CHECK_MAP,
  HEALTH_STATUS_MAP,
  CERTIFICATE_STATUS_MAP,
  getTemperatureStatus,
} from '@/constants/health-check'

interface Props {
  visible: boolean
  record: HealthCheckRecordDetail | null
  loading: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'update', id: number, data: HealthCheckUpdatePayload): void
  (e: 'archive', record: HealthCheckRecordDetail): void
}>()

/**
 * 人脸验证状态判断
 */
function getFaceVerificationStatus(score?: number): { verified: boolean; label: string; type: string } {
  if (!score && score !== 0) return { verified: false, label: '未检测', type: 'info' }
  if (score >= 80) return { verified: true, label: '验证通过', type: 'success' }
  if (score >= 60) return { verified: false, label: '待确认', type: 'warning' }
  return { verified: false, label: '未通过', type: 'danger' }
}

/**
 * 健康证到期日距离天数
 */
function getCertRemainingDays(expiryDate?: string): number | null {
  if (!expiryDate) return null
  const expiry = new Date(expiryDate)
  const now = new Date()
  return Math.ceil((expiry.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
}

function getDutyTypeTagType(dutyType?: string): 'success' | 'warning' {
  return dutyType === 'substitute' ? 'warning' : 'success'
}

// 异动留痕只做展示翻译，保留后端原始数据结构不变。
const MOVEMENT_ENUM_LABELS: Record<string, string> = {
  pending_check: HEALTH_CHECK_STATUS_MAP.pending_check.label,
  checking: HEALTH_CHECK_STATUS_MAP.checking.label,
  completed_normal: HEALTH_CHECK_STATUS_MAP.completed_normal.label,
  completed_abnormal: HEALTH_CHECK_STATUS_MAP.completed_abnormal.label,
  archived: HEALTH_CHECK_STATUS_MAP.archived.label,
  formal: '正式在岗',
  substitute: '临时替班',
  ...POSITION_MAP,
}

const movementEnumPattern = new RegExp(
  `(^|[^A-Za-z0-9_])(${Object.keys(MOVEMENT_ENUM_LABELS)
    .sort((left, right) => right.length - left.length)
    .join('|')})(?=$|[^A-Za-z0-9_])`,
  'g'
)

function formatMovementText(text?: string | null): string {
  if (!text) return '-'
  return text.replace(movementEnumPattern, (_, prefix: string, token: string) => {
    return `${prefix}${MOVEMENT_ENUM_LABELS[token] || token}`
  })
}

const showArchiveButton = computed(() => Boolean(props.record) && props.record.status !== 'pending_check')
</script>

<template>
  <el-dialog
    :model-value="props.visible"
    title="晨检详情"
    width="760px"
    :close-on-click-modal="false"
    @close="emit('close')"
  >
    <div v-loading="props.loading" class="detail-content">
      <!-- 基本信息区 -->
      <el-descriptions :column="2" border>
        <el-descriptions-item label="晨检编号">
          {{ props.record?.checkNo }}
        </el-descriptions-item>
        <el-descriptions-item label="员工姓名">
          {{ props.record?.employeeName }}
        </el-descriptions-item>
        <el-descriptions-item label="晨检日期">
          {{ props.record?.checkDate }}
        </el-descriptions-item>
        <el-descriptions-item label="晨检时间">
          {{ props.record?.checkTime ? formatDateTime(props.record.checkTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="HEALTH_CHECK_STATUS_MAP[props.record?.status || 'pending_check']?.type">
            {{ HEALTH_CHECK_STATUS_MAP[props.record?.status || 'pending_check']?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="晨检结果">
          <el-tag :type="HEALTH_CHECK_RESULT_MAP[props.record?.checkResult || 'pending']?.type || 'info'">
            {{ HEALTH_CHECK_RESULT_MAP[props.record?.checkResult || 'pending']?.label || '待检' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前归属组织">
          {{ props.record?.currentOrgName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="在岗标签">
          <el-tag
            v-if="props.record?.dutyTypeName"
            :type="getDutyTypeTagType(props.record.dutyType)"
            effect="plain"
          >
            {{ props.record.dutyTypeName }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="当前应检状态">
          <el-tag :type="props.record?.shouldCheck === false ? 'info' : 'success'" effect="plain">
            {{ props.record?.shouldCheck === false ? '已剔除' : '应检中' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="联动更新时间">
          {{ props.record?.linkageUpdatedAt ? formatDateTime(props.record.linkageUpdatedAt) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="联动说明" :span="2">
          {{ props.record?.linkageReason || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 人脸识别区域 -->
      <div class="detail-section">
        <h4 class="section-title">
          <el-icon><CircleCheck /></el-icon>
          人脸识别
        </h4>
        <div class="face-section">
          <div v-if="props.record?.faceImageUrl" class="face-image">
            <el-image :src="props.record.faceImageUrl" style="width: 120px; height: 120px; border-radius: 8px" fit="cover" />
          </div>
          <div class="face-info">
            <div class="info-row">
              <span class="info-label">匹配分数:</span>
              <span class="info-value">{{ props.record?.faceMatchScore != null ? `${props.record.faceMatchScore}%` : '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">验证状态:</span>
              <el-tag
                :type="getFaceVerificationStatus(props.record?.faceMatchScore).type"
                size="small"
              >
                <el-icon v-if="getFaceVerificationStatus(props.record?.faceMatchScore).verified" style="margin-right: 4px"><CircleCheck /></el-icon>
                <el-icon v-else style="margin-right: 4px"><CircleClose /></el-icon>
                {{ getFaceVerificationStatus(props.record?.faceMatchScore).label }}
              </el-tag>
            </div>
          </div>
        </div>
        <div v-if="!props.record?.faceImageUrl && props.record?.faceMatchScore == null" class="empty-section">
          暂无人脸识别数据
        </div>
      </div>

      <!-- 体温区域 -->
      <div class="detail-section">
        <h4 class="section-title">
          <el-icon><CircleCheck /></el-icon>
          体温检测
        </h4>
        <div class="temp-section">
          <span class="temp-value" :class="getTemperatureStatus(props.record?.temperature) === 'high' ? 'is-high' : getTemperatureStatus(props.record?.temperature) === 'low' ? 'is-low' : 'is-normal'">
            {{ props.record?.temperature != null ? `${props.record.temperature}℃` : '-' }}
          </span>
          <el-tag
            v-if="props.record?.temperature != null"
            :type="TEMPERATURE_STATUS_MAP[getTemperatureStatus(props.record.temperature)]?.type"
            size="large"
            class="temp-badge"
          >
            {{ TEMPERATURE_STATUS_MAP[getTemperatureStatus(props.record.temperature)]?.label }}
          </el-tag>
        </div>
      </div>

      <!-- 健康证状态区域 -->
      <div class="detail-section">
        <h4 class="section-title">
          <el-icon><CircleCheck /></el-icon>
          健康证状态
        </h4>
        <div class="cert-section">
          <el-tag v-if="props.record?.certificateStatus" :type="CERTIFICATE_STATUS_MAP[props.record.certificateStatus]?.type" size="large">
            {{ CERTIFICATE_STATUS_MAP[props.record.certificateStatus]?.label }}
          </el-tag>
          <el-tag v-else type="info" size="large">未知</el-tag>
          <span v-if="getCertRemainingDays(props.record?.certExpiryDate) != null" class="cert-expiry-info" :class="{ 'is-expiring': (getCertRemainingDays(props.record?.certExpiryDate) ?? 0) <= 30 && (getCertRemainingDays(props.record?.certExpiryDate) ?? 0) > 0, 'is-expired': (getCertRemainingDays(props.record?.certExpiryDate) ?? 0) <= 0 }">
            有效期至: {{ props.record?.certExpiryDate }}
            <span v-if="(getCertRemainingDays(props.record?.certExpiryDate) ?? 0) <= 30 && (getCertRemainingDays(props.record?.certExpiryDate) ?? 0) > 0">
              (剩余 {{ getCertRemainingDays(props.record?.certExpiryDate) }} 天)
            </span>
            <span v-if="(getCertRemainingDays(props.record?.certExpiryDate) ?? 0) <= 0">
              (已过期)
            </span>
          </span>
        </div>
      </div>

      <!-- 卫生检查区域 -->
      <div class="detail-section">
        <h4 class="section-title">
          <el-icon><CircleCheck /></el-icon>
          卫生检查
        </h4>
        <div class="hygiene-section">
          <div class="hygiene-item">
            <span class="hygiene-label">手部卫生:</span>
            <el-tag
              v-if="props.record?.handHygiene"
              :type="HYGIENE_CHECK_MAP[props.record.handHygiene]?.type"
              size="large"
              class="hygiene-badge"
            >
              <el-icon v-if="props.record.handHygiene === 'pass'" style="margin-right: 4px"><CircleCheck /></el-icon>
              <el-icon v-else style="margin-right: 4px"><CircleClose /></el-icon>
              {{ HYGIENE_CHECK_MAP[props.record.handHygiene]?.label }}
            </el-tag>
            <span v-else class="hygiene-empty">-</span>
          </div>
          <div class="hygiene-item">
            <span class="hygiene-label">着装检查:</span>
            <el-tag
              v-if="props.record?.uniformCheck"
              :type="HYGIENE_CHECK_MAP[props.record.uniformCheck]?.type"
              size="large"
              class="hygiene-badge"
            >
              <el-icon v-if="props.record.uniformCheck === 'pass'" style="margin-right: 4px"><CircleCheck /></el-icon>
              <el-icon v-else style="margin-right: 4px"><CircleClose /></el-icon>
              {{ HYGIENE_CHECK_MAP[props.record.uniformCheck]?.label }}
            </el-tag>
            <span v-else class="hygiene-empty">-</span>
          </div>
        </div>
      </div>

      <!-- 其他信息 -->
      <el-descriptions :column="2" border class="other-info">
        <el-descriptions-item label="健康状况">
          <el-tag v-if="props.record?.healthStatus" :type="HEALTH_STATUS_MAP[props.record.healthStatus]?.type">
            {{ HEALTH_STATUS_MAP[props.record.healthStatus]?.label }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="props.record?.failReason" label="不通过原因" :span="2">
          <span class="fail-reason-text">{{ props.record?.failReason }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="props.record?.remark" label="备注" :span="2">
          {{ props.record?.remark }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="detail-section">
        <h4 class="section-title">
          <el-icon><CircleCheck /></el-icon>
          异动留痕
        </h4>
        <div v-if="props.record?.movementLogs?.length" class="movement-list">
          <div
            v-for="log in props.record.movementLogs"
            :key="log.id"
            class="movement-item"
          >
            <div class="movement-header">
              <span class="movement-title">{{ formatMovementText(log.eventName) }}</span>
              <span class="movement-time">{{ log.createdAt ? formatDateTime(log.createdAt) : '-' }}</span>
            </div>
            <div class="movement-reason">{{ log.reasonDesc ? formatMovementText(log.reasonDesc) : '无附加说明' }}</div>
            <div class="movement-snapshot">
              <span class="snapshot-label">变更前</span>
              <span class="snapshot-value">{{ formatMovementText(log.beforeSummary) }}</span>
            </div>
            <div class="movement-snapshot">
              <span class="snapshot-label">变更后</span>
              <span class="snapshot-value">{{ formatMovementText(log.afterSummary) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-section">暂无异动留痕</div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('close')">关闭</el-button>
        <el-button
          v-if="showArchiveButton"
          type="primary"
          v-permission="MORNING_CHECK_PERMISSIONS.ARCHIVE"
          @click="props.record && emit('archive', props.record)"
        >
          归档
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.detail-content {
  padding: 10px 0;
}

.detail-section {
  margin-top: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #ebeef5;

  .section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 0 12px 0;
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.face-section {
  display: flex;
  align-items: flex-start;
  gap: 20px;

  .face-info {
    display: flex;
    flex-direction: column;
    gap: 10px;

    .info-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .info-label {
      color: #909399;
      font-size: 14px;
      min-width: 80px;
    }

    .info-value {
      font-size: 14px;
      font-weight: 500;
    }
  }
}

.empty-section {
  text-align: center;
  color: #c0c4cc;
  padding: 12px;
  font-size: 14px;
}

.temp-section {
  display: flex;
  align-items: center;
  gap: 16px;

  .temp-value {
    font-size: 28px;
    font-weight: 700;

    &.is-normal {
      color: #67c23a;
    }

    &.is-high {
      color: #f56c6c;
    }

    &.is-low {
      color: #909399;
    }
  }

  .temp-badge {
    font-size: 14px;
  }
}

.cert-section {
  display: flex;
  align-items: center;
  gap: 16px;

  .cert-expiry-info {
    font-size: 14px;
    color: #606266;

    &.is-expiring {
      color: #e6a23c;
      font-weight: 500;
    }

    &.is-expired {
      color: #f56c6c;
      font-weight: 500;
    }
  }
}

.hygiene-section {
  display: flex;
  gap: 40px;

  .hygiene-item {
    display: flex;
    align-items: center;
    gap: 10px;

    .hygiene-label {
      color: #606266;
      font-size: 14px;
    }

    .hygiene-badge {
      font-size: 14px;
    }

    .hygiene-empty {
      color: #c0c4cc;
    }
  }
}

.other-info {
  margin-top: 20px;
}

.fail-reason-text {
  color: #f56c6c;
}

.movement-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.movement-item {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.movement-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.movement-title {
  color: #303133;
  font-weight: 600;
}

.movement-time {
  color: #909399;
  font-size: 12px;
}

.movement-reason {
  margin-bottom: 8px;
  color: #606266;
  line-height: 1.6;
}

.movement-snapshot {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  line-height: 1.6;
}

.snapshot-label {
  color: #909399;
  min-width: 44px;
}

.snapshot-value {
  color: #303133;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
