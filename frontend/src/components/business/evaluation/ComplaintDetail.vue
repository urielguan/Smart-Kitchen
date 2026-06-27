<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { complaintApi } from '@/api/modules/evaluation'
import type { Complaint, DispatchRecord, WorkOrderRecord } from '@/types/evaluation'
import {
  COMPLAINT_TYPE_MAP,
  COMPLAINT_SOURCE_MAP,
  COMPLAINT_STATUS_MAP,
  DISPATCH_TYPE_MAP,
  DISPATCH_STATUS_MAP,
  PRIORITY_MAP,
  SATISFACTION_MAP
} from '@/constants/evaluation'
import { formatDateTime } from '@/utils'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: boolean
  complaintId: number | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

/** 详情数据 */
const detail = ref<Complaint | null>(null)

/** 派单信息 */
const dispatchInfo = ref<DispatchRecord | null>(null)

/** 处理记录 */
const processRecords = ref<WorkOrderRecord[]>([])

/** 加载状态 */
const loading = ref(false)

/** 当前Tab */
const activeTab = ref('basic')

/** 弹窗可见性 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 获取投诉类型名称 */
const getComplaintTypeName = (type: string): string => {
  return COMPLAINT_TYPE_MAP[type as keyof typeof COMPLAINT_TYPE_MAP]?.label || type
}

/** 获取来源名称 */
const getSourceName = (source: string): string => {
  return COMPLAINT_SOURCE_MAP[source as keyof typeof COMPLAINT_SOURCE_MAP] || source
}

/** 获取状态标签 */
const getStatusTag = (status: string): { label: string; type: string } => {
  return COMPLAINT_STATUS_MAP[status as keyof typeof COMPLAINT_STATUS_MAP] || { label: status, type: 'info' }
}

/** 获取派单方式名称 */
const getDispatchTypeName = (type: string): string => {
  return DISPATCH_TYPE_MAP[type as keyof typeof DISPATCH_TYPE_MAP]?.label || type
}

/** 获取派单状态标签 */
const getDispatchStatusTag = (status: string): { label: string; type: string } => {
  return DISPATCH_STATUS_MAP[status as keyof typeof DISPATCH_STATUS_MAP] || { label: status, type: 'info' }
}

/** 获取优先级名称 */
const getPriorityName = (priority: string): string => {
  return PRIORITY_MAP[priority as keyof typeof PRIORITY_MAP]?.label || priority
}

/** 获取满意度名称 */
const getSatisfactionName = (satisfaction: string): string => {
  return SATISFACTION_MAP[satisfaction as keyof typeof SATISFACTION_MAP]?.label || satisfaction
}

/** 获取操作名称 */
const getActionName = (action: string): string => {
  const actionMap: Record<string, string> = {
    dispatch: '派单',
    reassign: '转派',
    process: '处理',
    complete: '完成',
    cancel: '取消'
  }
  return actionMap[action] || action
}

/** 获取投诉详情 */
const fetchDetail = async () => {
  if (!props.complaintId) return

  loading.value = true
  try {
    // 获取投诉详情
    const res = await complaintApi.getDetail(props.complaintId)
    if (res.code === 'SUCCESS' && res.data) {
      detail.value = res.data

      // 派单信息直接从详情中获取
      if (res.data.dispatch) {
        dispatchInfo.value = res.data.dispatch
      }

      // 处理记录直接从详情中获取
      if (res.data.records) {
        processRecords.value = res.data.records
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取投诉详情失败')
  } finally {
    loading.value = false
  }
}

/** 监听弹窗打开和ID变化 */
watch(
  [() => props.modelValue, () => props.complaintId],
  ([isVisible, id]) => {
    if (isVisible && id) {
      activeTab.value = 'basic'
      fetchDetail()
    } else if (!isVisible) {
      detail.value = null
      dispatchInfo.value = null
      processRecords.value = []
    }
  },
  { immediate: true }
)

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="758px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    destroy-on-close
    class="complaint-detail-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">投诉详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <el-tabs v-model="activeTab" type="card" class="detail-tabs">
          <!-- 基础信息 Tab -->
          <el-tab-pane label="基础信息" name="basic">
            <div class="info-table">
              <div class="info-label">投诉编号</div>
              <div class="info-value">{{ detail.complaintNo }}</div>
              <div class="info-label">投诉类型</div>
              <div class="info-value">{{ detail.complaintType ? getComplaintTypeName(detail.complaintType) : '-' }}</div>
              <div class="info-label">来源</div>
              <div class="info-value">{{ getSourceName(detail.source) }}</div>
              <div class="info-label">标题</div>
              <div class="info-value">{{ detail.title }}</div>
              <div class="info-label">投诉人</div>
              <div class="info-value">{{ detail.submitterName }}</div>
              <div class="info-label">联系电话</div>
              <div class="info-value">{{ detail.submitterPhone || '-' }}</div>
              <div class="info-label">关联菜品</div>
              <div class="info-value">{{ detail.relatedMenuName || '-' }}</div>
              <div class="info-label">门店</div>
              <div class="info-value">{{ detail.orgName }}</div>
              <div class="info-label">处理状态</div>
              <div class="info-value">
                <el-tag :type="getStatusTag(detail.status).type" size="small">
                  {{ getStatusTag(detail.status).label }}
                </el-tag>
              </div>
              <div class="info-label">优先级</div>
              <div class="info-value">
                <el-tag
                  v-if="detail.priority"
                  :type="PRIORITY_MAP[detail.priority as keyof typeof PRIORITY_MAP]?.type"
                  size="small"
                >
                  {{ getPriorityName(detail.priority) }}
                </el-tag>
                <span v-else>-</span>
              </div>
              <div class="info-label">满意度</div>
              <div class="info-value info-value--span3">
                <template v-if="detail.satisfaction">
                  <el-tag
                    :type="SATISFACTION_MAP[detail.satisfaction as keyof typeof SATISFACTION_MAP]?.type"
                    size="small"
                  >
                    {{ getSatisfactionName(detail.satisfaction) }}
                  </el-tag>
                  <span v-if="detail.satisfactionRemark" class="satisfaction-remark">
                    {{ detail.satisfactionRemark }}
                  </span>
                </template>
                <span v-else>-</span>
              </div>
              <div class="info-label">投诉描述</div>
              <div class="info-value info-value--span3">{{ detail.description || '-' }}</div>
            </div>

            <!-- 投诉图片 -->
            <div v-if="detail.images && detail.images.length > 0" class="images-section">
              <div class="section-title">
                <span class="title-bar" />
                <span>投诉图片</span>
              </div>
              <div class="images-list">
                <el-image
                  v-for="(img, index) in detail.images"
                  :key="index"
                  :src="img"
                  :preview-src-list="detail.images"
                  :initial-index="index"
                  fit="cover"
                  class="image-item"
                />
              </div>
            </div>

            <!-- 时间信息 -->
            <div class="time-section">
              <span>创建时间：{{ formatDateTime(detail.createdAt) }}</span>
            </div>
          </el-tab-pane>

          <!-- 派单信息 Tab -->
          <el-tab-pane label="派单信息" name="dispatch">
            <template v-if="dispatchInfo">
              <div class="info-table">
                <div class="info-label">派单编号</div>
                <div class="info-value">{{ dispatchInfo.dispatchNo }}</div>
                <div class="info-label">派单方式</div>
                <div class="info-value">
                  <el-tag :type="DISPATCH_TYPE_MAP[dispatchInfo.dispatchType as keyof typeof DISPATCH_TYPE_MAP]?.type" size="small">
                    {{ getDispatchTypeName(dispatchInfo.dispatchType) }}
                  </el-tag>
                </div>
                <div class="info-label">派单人</div>
                <div class="info-value">{{ dispatchInfo.assignerName || '系统自动' }}</div>
                <div class="info-label">处理人</div>
                <div class="info-value">{{ dispatchInfo.handlerName }}</div>
                <div class="info-label">派单时间</div>
                <div class="info-value">{{ formatDateTime(dispatchInfo.createdAt) }}</div>
                <div class="info-label">截止时间</div>
                <div class="info-value">{{ dispatchInfo.deadline ? formatDateTime(dispatchInfo.deadline) : '-' }}</div>
                <div class="info-label">优先级</div>
                <div class="info-value">
                  <el-tag
                    v-if="dispatchInfo.priority"
                    :type="PRIORITY_MAP[dispatchInfo.priority as keyof typeof PRIORITY_MAP]?.type"
                    size="small"
                  >
                    {{ getPriorityName(dispatchInfo.priority) }}
                  </el-tag>
                  <span v-else>-</span>
                </div>
                <div class="info-label">状态</div>
                <div class="info-value">
                  <el-tag :type="getDispatchStatusTag(dispatchInfo.status).type" size="small">
                    {{ getDispatchStatusTag(dispatchInfo.status).label }}
                  </el-tag>
                </div>
                <div class="info-label">完成时间</div>
                <div class="info-value info-value--span3">{{ dispatchInfo.completedAt ? formatDateTime(dispatchInfo.completedAt) : '-' }}</div>
                <div class="info-label">派单备注</div>
                <div class="info-value info-value--span3">{{ dispatchInfo.remark || '-' }}</div>
              </div>
            </template>
            <el-empty v-else description="暂无派单信息" />
          </el-tab-pane>

          <!-- 处理记录 Tab -->
          <el-tab-pane label="处理记录" name="records">
            <template v-if="processRecords.length > 0">
              <el-timeline class="record-timeline">
                <el-timeline-item
                  v-for="record in processRecords"
                  :key="record.id"
                  :timestamp="formatDateTime(record.createdAt)"
                  placement="top"
                >
                  <div class="record-card">
                    <div class="record-header">
                      <span class="action-name">{{ getActionName(record.action) }}</span>
                      <span class="operator">操作人：{{ record.operatorName }}</span>
                    </div>
                    <div v-if="record.content" class="record-content">
                      {{ record.content }}
                    </div>
                    <div v-if="record.images && record.images.length > 0" class="record-images">
                      <el-image
                        v-for="(img, index) in record.images"
                        :key="index"
                        :src="img"
                        :preview-src-list="record.images"
                        :initial-index="index"
                        fit="cover"
                        class="record-image"
                      />
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
/* ---- Dialog 容器（unscoped） ---- */
.complaint-detail-dialog.el-dialog {
  width: 758px;
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

.complaint-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.complaint-detail-dialog.el-dialog .el-dialog__body {
  height: 580px;
  padding: 16px 24px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.complaint-detail-dialog.el-dialog .el-dialog__footer {
  padding: 12px 24px 16px !important;
  flex-shrink: 0;
  border-top: 1px solid #E1E2E9;
  box-sizing: border-box;
  text-align: right;
}

.complaint-detail-dialog.el-dialog .el-dialog__footer .dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
}
</style>

<style lang="scss" scoped>
/* ---- 头部 ---- */
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

/* ---- 详情 body ---- */
.detail-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ---- 详情 Tab ---- */
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

/* ---- 基础信息表格 ---- */
.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  width: 100%;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

.info-value {
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;

  &--span3 {
    grid-column: span 3;
    height: auto;
    min-height: 40px;
    padding: 5px 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

/* ---- 区块标题 ---- */
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

/* ---- 满意度备注 ---- */
.satisfaction-remark {
  margin-left: 8px;
  color: #606266;
  font-size: 13px;
}

/* ---- 投诉图片 ---- */
.images-section {
  margin-top: 20px;
}

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

/* ---- 时间信息 ---- */
.time-section {
  text-align: right;
  color: #909399;
  font-size: 13px;
  padding-top: 12px;
  margin-top: 20px;
  border-top: 1px solid #E1E2E9;
}

/* ---- Tag 样式覆写 ---- */
:deep(.el-tag--info) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

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

:deep(.el-tag--primary) {
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

/* ---- 处理记录 ---- */
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

.record-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.record-image {
  width: 60px;
  height: 60px;
  border-radius: 4px;
  cursor: pointer;
}

/* ---- 底部按钮 ---- */
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

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}
</style>
