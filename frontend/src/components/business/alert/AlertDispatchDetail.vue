<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { alertApi } from '@/api/modules/alert'
import type { AlertDispatchDetail } from '@/types/alert'
import { ALERT_REVIEW_RESULT_LABELS } from '@/types/alert'
import {
  DISPATCH_TYPE_MAP,
  PRIORITY_MAP,
} from '@/constants/evaluation'
import { formatDateTime } from '@/utils'
import { ElMessage } from 'element-plus'

// 物料告警异常类型翻译
const ANOMALY_TYPE_LABELS: Record<string, string> = {
  material_expiry_warning: '物料效期预警',
  material_near_expiry: '物料临期告警',
  material_expired: '物料过期告警',
  material_stock_high: '库存积压告警',
  material_stock_low: '库存不足告警',
}
const anomalyTypeLabel = (type: string | undefined | null) =>
  type ? ANOMALY_TYPE_LABELS[type] ?? type : '-'

interface Props {
  modelValue: boolean
  alertId: number | null
  dispatchId?: number | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const detail = ref<AlertDispatchDetail | null>(null)
const loading = ref(false)
const activeTab = ref('alert')

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const getAlertLevelTag = (level: string) => {
  const map: Record<string, string> = { info: 'info', warning: 'warning', error: 'danger', critical: 'danger' }
  return map[level] || 'info'
}

const getAlertStatusTag = (status: string) => {
  const map: Record<string, string> = { pending: 'info', assigned: 'primary', handling: 'warning', handled: 'success', reviewed: 'success', closed: 'info' }
  return map[status] || 'info'
}

const getDispatchTypeName = (type: string) => {
  return DISPATCH_TYPE_MAP[type as keyof typeof DISPATCH_TYPE_MAP]?.label || type
}

const ALERT_DISPATCH_STATUS_MAP: Record<string, { label: string; type: string }> = {
  pending: { label: '待处理', type: 'warning' },
  processing: { label: '处理中', type: 'primary' },
  completed: { label: '已处理', type: 'success' },
  reviewed: { label: '已复核', type: 'success' },
  cancelled: { label: '已取消', type: 'info' },
  rejected: { label: '已驳回', type: 'danger' },
}

const getDispatchStatusTag = (status: string) => {
  return ALERT_DISPATCH_STATUS_MAP[status] || { label: status, type: 'info' }
}

const getPriorityName = (priority: string) => {
  return PRIORITY_MAP[priority as keyof typeof PRIORITY_MAP]?.label || priority
}

const getPriorityType = (priority: string) => {
  return PRIORITY_MAP[priority as keyof typeof PRIORITY_MAP]?.type || 'info'
}

const getActionName = (action: string) => {
  const map: Record<string, string> = {
    dispatch: '派单',
    process: '处理',
    complete: '完成',
    review: '复核',
    review_rejected: '复核驳回',
    close: '关闭',
    archive: '归档',
    cancel: '取消',
  }
  return map[action] || action
}

const formatReviewResult = (result: string | null | undefined) => {
  if (!result) return '-'
  return ALERT_REVIEW_RESULT_LABELS[result as keyof typeof ALERT_REVIEW_RESULT_LABELS] || result
}

const fetchDetail = async () => {
  if (!props.alertId && !props.dispatchId) return
  loading.value = true
  try {
    const res = props.dispatchId
      ? await alertApi.getDispatchDetail(props.dispatchId)
      : await alertApi.getDispatchDetailByAlertId(props.alertId as number)
    if (res.code === 'SUCCESS' && res.data) {
      detail.value = res.data
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取详情失败')
  } finally {
    loading.value = false
  }
}

watch(
  [() => props.modelValue, () => props.alertId, () => props.dispatchId],
  ([isVisible]) => {
    if (isVisible && (props.alertId || props.dispatchId)) {
      activeTab.value = props.dispatchId ? 'dispatch' : 'alert'
      fetchDetail()
    } else if (!isVisible) {
      detail.value = null
    }
  },
  { immediate: true },
)

const handleClose = () => {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="770px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    append-to-body
    destroy-on-close
    class="alert-detail-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">告警详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="detail-content">
      <template v-if="detail">
        <el-tabs v-model="activeTab" type="card" class="detail-tabs">
          <!-- Tab 1: 告警详情 -->
          <el-tab-pane label="告警详情" name="alert">
            <div class="section-title">
              <span class="title-bar" />
              告警信息
            </div>
            <div class="info-table">
              <div class="info-label">告警编号</div>
              <div class="info-value">{{ detail.alertNo }}</div>
              <div class="info-label">告警级别</div>
              <div class="info-value">
                <el-tag :type="getAlertLevelTag(detail.alertLevel)" size="small">{{ detail.alertLevelName }}</el-tag>
              </div>
              <div class="info-label">告警类型</div>
              <div class="info-value">{{ detail.alertTypeName }}</div>
              <div class="info-label">告警状态</div>
              <div class="info-value">
                <el-tag :type="getAlertStatusTag(detail.alertStatus)" size="small">{{ detail.alertStatusName }}</el-tag>
              </div>
              <div class="info-label">触发时间</div>
              <div class="info-value">{{ formatDateTime(detail.triggeredAt) }}</div>
              <template v-if="detail.alertType === 'material'">
                <div class="info-label">物料编码</div>
                <div class="info-value">{{ detail.alertDetail?.materialCode ?? '-' }}</div>
                <div class="info-label">物料名称</div>
                <div class="info-value info-value--span3">{{ detail.alertDetail?.materialName ?? '-' }}</div>
              </template>
              <template v-else>
                <div class="info-label">设备类型</div>
                <div class="info-value">{{ detail.deviceTypeName || '-' }}</div>
                <div class="info-label">设备名称</div>
                <div class="info-value info-value--span3">{{ detail.deviceName || '-' }}</div>
              </template>
              <div class="info-label">告警内容</div>
              <div class="info-value info-value--span3">{{ detail.alertContent }}</div>
            </div>

            <!-- 物料告警详情 -->
            <div v-if="detail.alertDetail && detail.alertType === 'material'" class="data-section">
              <div class="section-title">
                <span class="title-bar" />
                物料告警详情
              </div>

              <!-- 通用信息 -->
              <div class="info-table">
                <div class="info-label">当前库存</div>
                <div class="info-value">{{ detail.alertDetail.currentStock ?? '-' }}{{ detail.alertDetail.unit ?? '' }}</div>
                <div class="info-label">告警类型</div>
                <div class="info-value">{{ anomalyTypeLabel(detail.alertDetail.anomalyType) }}</div>
                <div class="info-label" v-if="detail.alertDetail.minStock != null">最低库存</div>
                <div class="info-value" v-if="detail.alertDetail.minStock != null">{{ detail.alertDetail.minStock }}{{ detail.alertDetail.unit ?? '' }}</div>
                <div class="info-label" v-if="detail.alertDetail.maxStock != null">最高库存</div>
                <div class="info-value" v-if="detail.alertDetail.maxStock != null">{{ detail.alertDetail.maxStock }}{{ detail.alertDetail.unit ?? '' }}</div>
              </div>

              <!-- 效期告警：单批次信息 -->
              <template v-if="detail.alertDetail.batchNo">
                <div class="section-title sub-section-title">
                  <span class="title-bar" />
                  批次明细
                </div>
                <div class="info-table">
                  <div class="info-label">仓库名</div>
                  <div class="info-value">{{ detail.alertDetail.warehouseName ?? '-' }}</div>
                  <div class="info-label">仓位名</div>
                  <div class="info-value">{{ detail.alertDetail.locationName ?? '-' }}</div>
                  <div class="info-label">批次号</div>
                  <div class="info-value">{{ detail.alertDetail.batchNo }}</div>
                  <div class="info-label">批次数量</div>
                  <div class="info-value">{{ detail.alertDetail.batchQuantity ?? '-' }}{{ detail.alertDetail.unit ?? '' }}</div>
                  <div class="info-label">到期日期</div>
                  <div class="info-value">{{ detail.alertDetail.expiryDate ?? '-' }}</div>
                  <div class="info-label">剩余天数</div>
                  <div class="info-value">{{ detail.alertDetail.remainingDays ?? '-' }}天</div>
                </div>
              </template>

              <!-- 库存告警：多批次列表 -->
              <template v-if="detail.alertDetail.batches && detail.alertDetail.batches.length">
                <div class="section-title sub-section-title">
                  <span class="title-bar" />
                  批次明细（共{{ detail.alertDetail.batches.length }}个批次）
                </div>
                <el-table :data="detail.alertDetail.batches" border :cell-style="{ verticalAlign: 'middle' }">
                  <el-table-column label="仓库名" prop="warehouseName" min-width="100">
                    <template #default="{ row }">{{ row.warehouseName ?? '-' }}</template>
                  </el-table-column>
                  <el-table-column label="仓位名" prop="locationName" min-width="100">
                    <template #default="{ row }">{{ row.locationName ?? '-' }}</template>
                  </el-table-column>
                  <el-table-column label="批次号" prop="batchNo" min-width="120">
                    <template #default="{ row }">{{ row.batchNo ?? '-' }}</template>
                  </el-table-column>
                  <el-table-column label="数量" min-width="100">
                    <template #default="{ row }">{{ row.quantity ?? '-' }}{{ detail.alertDetail.unit ?? '' }}</template>
                  </el-table-column>
                  <el-table-column label="到期日期" prop="expiryDate" min-width="110">
                    <template #default="{ row }">{{ row.expiryDate ?? '无保质期' }}</template>
                  </el-table-column>
                </el-table>
              </template>
            </div>

            <!-- 告警图片 -->
            <div v-if="detail.alertImages && detail.alertImages.length" class="images-section">
              <div class="section-title">
                <span class="title-bar" />
                告警图片
              </div>
              <div class="images-list">
                <el-image
                  v-for="(img, index) in detail.alertImages"
                  :key="index"
                  :src="img"
                  :preview-src-list="detail.alertImages"
                  :initial-index="index"
                  fit="cover"
                  class="image-item"
                />
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 2: 派单信息 -->
          <el-tab-pane label="派单信息" name="dispatch">
            <template v-if="detail.dispatchId">
              <div class="info-table">
                <div class="info-label">派单编号</div>
                <div class="info-value">{{ detail.dispatchNo }}</div>
                <div class="info-label">派单方式</div>
                <div class="info-value">
                  <el-tag :type="DISPATCH_TYPE_MAP[detail.dispatchType as keyof typeof DISPATCH_TYPE_MAP]?.type" size="small">
                    {{ getDispatchTypeName(detail.dispatchType) }}
                  </el-tag>
                </div>
                <div class="info-label">派单人</div>
                <div class="info-value">{{ detail.assignerName || '系统自动' }}</div>
                <div class="info-label">处理人</div>
                <div class="info-value">{{ detail.handlerName }}</div>
                <div class="info-label">派单时间</div>
                <div class="info-value">{{ formatDateTime(detail.createdAt) }}</div>
                <div class="info-label">截止时间</div>
                <div class="info-value">{{ detail.deadline ? formatDateTime(detail.deadline) : '-' }}</div>
                <div class="info-label">优先级</div>
                <div class="info-value">
                  <el-tag v-if="detail.priority" :type="getPriorityType(detail.priority)" size="small">
                    {{ getPriorityName(detail.priority) }}
                  </el-tag>
                  <span v-else>-</span>
                </div>
                <div class="info-label">状态</div>
                <div class="info-value">
                  <el-tag :type="getDispatchStatusTag(detail.status).type" size="small">
                    {{ getDispatchStatusTag(detail.status).label }}
                  </el-tag>
                </div>
                <div class="info-label">派单备注</div>
                <div class="info-value info-value--span3">{{ detail.remark || '-' }}</div>
                <div class="info-label">完成时间</div>
                <div class="info-value info-value--span3">{{ detail.completedAt ? formatDateTime(detail.completedAt) : '-' }}</div>
                <div class="info-label">处理结果</div>
                <div class="info-value info-value--span3">{{ detail.handleResult || '-' }}</div>
                <template v-if="detail.handleAttachments && detail.handleAttachments.length">
                  <div class="info-label">处理附件</div>
                  <div class="info-value info-value--span3">
                    <div class="attachment-list">
                      <div v-for="(item, idx) in detail.handleAttachments" :key="idx" class="attachment-item">
                        <a :href="item.url" target="_blank" class="attachment-link">{{ item.name }}</a>
                      </div>
                    </div>
                  </div>
                </template>
                <template v-if="detail.reviewedBy">
                  <div class="info-label">复核人</div>
                  <div class="info-value">{{ detail.reviewedByName || '-' }}</div>
                  <div class="info-label">复核时间</div>
                  <div class="info-value">{{ detail.reviewedAt ? formatDateTime(detail.reviewedAt) : '-' }}</div>
                  <div class="info-label">复核结果</div>
                  <div class="info-value info-value--span3">{{ formatReviewResult(detail.reviewResult) }}</div>
                  <div class="info-label">{{ detail.reviewResult === 'rejected' ? '驳回原因' : '复核备注' }}</div>
                  <div class="info-value info-value--span3">{{ detail.reviewRemark || '-' }}</div>
                  <template v-if="detail.reviewAttachments && detail.reviewAttachments.length">
                    <div class="info-label">复核附件</div>
                    <div class="info-value info-value--span3">
                      <div class="attachment-list">
                        <div v-for="(item, idx) in detail.reviewAttachments" :key="idx" class="attachment-item">
                          <a :href="item.url" target="_blank" class="attachment-link">{{ item.name }}</a>
                        </div>
                      </div>
                    </div>
                  </template>
                </template>
                <template v-if="detail.closedAt || detail.archivedAt">
                  <div class="info-label">关闭人</div>
                  <div class="info-value">{{ detail.closedByName || '-' }}</div>
                  <div class="info-label">关闭时间</div>
                  <div class="info-value">{{ detail.closedAt ? formatDateTime(detail.closedAt) : '-' }}</div>
                  <div class="info-label">关闭说明</div>
                  <div class="info-value info-value--span3">{{ detail.closeRemark || '-' }}</div>
                  <div class="info-label">归档人</div>
                  <div class="info-value">{{ detail.archivedByName || '-' }}</div>
                  <div class="info-label">归档时间</div>
                  <div class="info-value">{{ detail.archivedAt ? formatDateTime(detail.archivedAt) : '-' }}</div>
                  <div class="info-label">归档说明</div>
                  <div class="info-value info-value--span3">{{ detail.archiveRemark || '-' }}</div>
                </template>
              </div>
            </template>
            <el-empty v-else description="暂无派单信息" />
          </el-tab-pane>

          <!-- Tab 3: 处理记录 -->
          <el-tab-pane label="处理记录" name="records">
            <template v-if="detail.records && detail.records.length > 0">
              <el-timeline class="record-timeline">
                <el-timeline-item
                  v-for="record in detail.records"
                  :key="record.id"
                  :timestamp="formatDateTime(record.createdAt)"
                  placement="top"
                >
                  <div class="record-card">
                    <div class="record-header">
                      <span class="action-name">{{ getActionName(record.action) }}</span>
                      <span class="operator">操作人：{{ record.operatorName || '系统' }}</span>
                    </div>
                    <div v-if="record.content" class="record-content">
                      {{ record.content }}
                    </div>
                    <div v-if="record.attachments && record.attachments.length" class="record-attachments">
                      <a v-for="(item, idx) in record.attachments" :key="idx" :href="item.url" target="_blank" class="attachment-link">
                        {{ item.name }}
                      </a>
                    </div>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </template>
            <el-empty v-else description="暂无处理记录" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器样式（unscoped） ---- */
.alert-detail-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;

  .el-dialog__header {
    padding: 24px 24px 13px;
    margin-right: 0;
    border-bottom: 1px solid #E1E2E9;
  }
  .el-dialog__body {
    height: 520px;
    padding: 16px 24px 24px;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
  .el-dialog__footer {
    padding: 12px 24px 16px !important;
    flex-shrink: 0;
    border-top: 1px solid #E1E2E9;
    box-sizing: border-box;
    text-align: right;
  }
}

/* ---- 批次明细表格（参考组织管理-关联成员） ---- */
.alert-detail-dialog .el-table {
  --el-table-border-color: #DCDCDC;
  --el-table-header-bg-color: #F8FAFC;
  --el-table-row-hover-bg-color: #F5F7FA;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

.alert-detail-dialog .el-table .el-table__header-wrapper th.el-table__cell {
  background: #F8FAFC !important;
  color: rgba(0, 0, 0, 0.6);
  font-weight: 500;
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.alert-detail-dialog .el-table td.el-table__cell {
  border-right: 1px solid #DCDCDC;
  border-bottom: 1px solid #DCDCDC;
}

.alert-detail-dialog .el-table .el-table__inner-wrapper::before {
  display: none;
}

.alert-detail-dialog .el-table--border .el-table__inner-wrapper {
  border-right: none;
  border-bottom: none;
}

.alert-detail-dialog .el-table--border {
  border: none;
}

.alert-detail-dialog .el-table .el-table__body-wrapper {
  border-bottom: 1px solid #DCDCDC;
}
</style>

<style lang="scss" scoped>
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.dialog-title {
  font-size: 20px;
  font-weight: 500;
  color: #000000;
  line-height: 1.2;
}

.close-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE5C7;
  }
}

.detail-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ---- 详情 Tab（scoped :deep 提升优先级，覆盖 Element Plus card 默认边框） ---- */
.detail-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    margin: 0;
    border-bottom: 1px solid #E1E2E9;
  }

  :deep(.el-tabs__nav-wrap) {
    margin-bottom: -6px;
    overflow: visible !important;
  }

  :deep(.el-tabs__nav-scroll) {
    overflow: visible !important;
  }

  :deep(.el-tabs__nav) {
    border: none;
    overflow: visible !important;
  }

  :deep(.el-tabs__item) {
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    color: #606266;
    height: 36px;
    line-height: 36px;
    padding: 0 20px;
    margin-right: 4px;
    background: #FAFAFA;
    border: 1px solid #F0F0F0 !important;
    border-bottom: 1px solid #E1E2E9 !important;
    border-radius: 0;

    &:hover {
      color: #7288FA;
    }
    &.is-active {
      color: #7288FA;
      background: #FFFFFF;
      border-bottom-color: #FFFFFF !important;
    }
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 16px 0;
  }
}

/* ---- info-table 网格 ---- */
.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  padding: 9px 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
  word-break: break-all;
}

.info-value {
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  padding: 9px 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
  word-break: break-all;

  &--span3 {
    grid-column: span 3;
    height: auto;
    min-height: 40px;
    padding: 9px 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

/* ---- section-title（带紫色竖线） ---- */
.section-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 12px;
}

.title-bar {
  display: inline-block;
  width: 4px;
  height: 20px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

.sub-section-title {
  margin-top: 20px;
}

.data-section,
.images-section {
  margin-top: 20px;
}

/* ---- 告警图片 ---- */
.images-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-item {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  cursor: pointer;
}

/* ---- 附件列表 ---- */
.attachment-list {
  .attachment-item {
    padding: 2px 0;
  }
  .attachment-link {
    color: var(--el-color-primary);
    font-size: 13px;
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }
}

/* ---- 处理记录时间线 ---- */
.record-timeline {
  padding: 8px 0;
}

.record-card {
  background: #F5F7FA;
  border-radius: 6px;
  padding: 12px 16px;
}

.record-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;

  .action-name {
    font-weight: 600;
    color: #303133;
  }

  .operator {
    font-size: 12px;
    color: #909399;
  }
}

.record-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.record-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;

  .attachment-link {
    color: var(--el-color-primary);
    font-size: 13px;
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }
}

/* ---- tag ---- */
:deep(.el-tag--success) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--warning) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--info) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--primary) {
  color: #7288FA;
  background: rgba(114, 136, 250, 0.1);
  border: 1px solid rgba(114, 136, 250, 0.3);
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--danger) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

/* ---- footer 按钮 ---- */
.dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
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
</style>
