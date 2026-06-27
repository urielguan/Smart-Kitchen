<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { View, Warning, Check, Refresh, Search, CircleCheck, CircleClose, VideoCamera, VideoPlay, Link } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useVideoMonitorStore } from '@/stores/modules/video-monitor'
import VideoPlayer from '@/components/business/video-monitor/VideoPlayer.vue'
import type { ViolationEvent } from '@/types/video-monitor'
import { videoMonitorApi } from '@/api/modules/video-monitor'
import { VIOLATION_TYPES, ALERT_LEVELS, HANDLE_STATUS } from '@/types/video-monitor'
import { VIOLATION_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

const router = useRouter()
const route = useRoute()
const store = useVideoMonitorStore()
const lastDashboardRouteKey = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'ai-violation') {
    return '来自数据监管看板：AI违规识别紧急处置'
  }
  return '来自数据监管看板'
})

// 筛选条件
const filterForm = ref({
  violationType: '',
  alertLevel: '',
  status: '',
  dateRange: [] as string[],
})

// 处理对话框
const handleDialogVisible = ref(false)
const currentViolation = ref<ViolationEvent | null>(null)
const handleForm = ref({
  status: 'resolved' as 'assigned' | 'processing' | 'resolved',
  handleRemark: '',
})

// 复核对话框
const reviewDialogVisible = ref(false)
const reviewForm = ref({
  reviewStatus: 'approved' as 'approved' | 'rejected',
  reviewRemark: '',
})

// 详情对话框
const detailDialogVisible = ref(false)
const detailLoading = computed(() => store.violationDetailLoading)
const detailData = computed(() => store.currentViolationDetail)
const operationLogs = ref<any[]>([])
const detailActiveTab = ref('info')

// 关联录像内联播放
const inlinePlaybackVisible = ref(false)
const inlinePlaybackUrl = ref('')
const inlinePlaybackLoading = ref(false)
const violationPlayerRef = ref<InstanceType<typeof VideoPlayer> | null>(null)

// 播放关联录像
const handlePlayRecording = () => {
  if (!detailData.value?.recordingPlaybackUrl) return
  inlinePlaybackUrl.value = detailData.value.recordingPlaybackUrl
  inlinePlaybackLoading.value = true
  inlinePlaybackVisible.value = true
}

// 跳转到完整回放页（带违规发生时间用于锚点定位）
const navigateToPlayback = () => {
  if (!detailData.value?.recordingId) {
    ElMessage.warning('证据缺失：该违规事件无关联录像')
    return
  }
  detailDialogVisible.value = false
  const query: Record<string, string> = { recordingId: String(detailData.value.recordingId) }
  if (detailData.value.occurredAt) {
    query.occurredAt = String(detailData.value.occurredAt)
  }
  router.push({ path: '/video-playback', query })
}

// 播放器就绪 — 自动跳转到违规发生时间附近
const handleViolationPlayerReady = () => {
  inlinePlaybackLoading.value = false
  if (!detailData.value?.occurredAt || !detailData.value?.recordingStartTime || !violationPlayerRef.value) return
  const occurred = new Date(detailData.value.occurredAt).getTime()
  const start = new Date(detailData.value.recordingStartTime).getTime()
  const offsetSeconds = Math.max(0, (occurred - start) / 1000 - 5)
  violationPlayerRef.value.seekTo(offsetSeconds)
}

// 关闭详情时重置播放状态
watch(detailDialogVisible, (val) => {
  if (!val) {
    inlinePlaybackVisible.value = false
    inlinePlaybackUrl.value = ''
    inlinePlaybackLoading.value = false
    violationPlayerRef.value = null
  }
})

// 违规类型选项 - 扩展更多类型
const violationTypeOptions = computed(() => [
  { label: '全部', value: '' },
  ...Object.entries(VIOLATION_TYPES).map(([key, val]) => ({
    label: val.name,
    value: key,
  })),
])

// 告警级别选项
const alertLevelOptions = [
  { label: '全部', value: '' },
  { label: '提示', value: 'info' },
  { label: '警告', value: 'warning' },
  { label: '紧急', value: 'urgent' },
  { label: '危险', value: 'danger' },
]

// 处理状态选项
const statusOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'pending' },
  { label: '已指派', value: 'assigned' },
  { label: '处理中', value: 'processing' },
  { label: '已解决', value: 'resolved' },
  { label: '待复核', value: 'review_pending' },
  { label: '已复核', value: 'reviewed' },
]

// 获取违规类型名称
const getViolationTypeName = (type: string): string => {
  return VIOLATION_TYPES[type as keyof typeof VIOLATION_TYPES]?.name || type
}

// 获取告警级别标签类型
const getAlertLevelType = (level: string): 'info' | 'warning' | 'danger' => {
  if (level === 'danger' || level === 'urgent') return 'danger'
  if (level === 'warning') return 'warning'
  return 'info'
}

// 获取告警级别名称
const getAlertLevelName = (level: string): string => {
  return ALERT_LEVELS[level as keyof typeof ALERT_LEVELS]?.name || level
}

// 获取状态标签类型
const getStatusType = (status: string): 'danger' | 'warning' | 'primary' | 'success' | 'info' => {
  const colorMap: Record<string, 'danger' | 'warning' | 'primary' | 'success' | 'info'> = {
    pending: 'danger',
    assigned: 'warning',
    processing: 'primary',
    resolved: 'success',
    review_pending: 'warning',
    reviewed: 'info',
  }
  return colorMap[status] || 'info'
}

// 获取状态名称
const getStatusName = (status: string): string => {
  return HANDLE_STATUS[status as keyof typeof HANDLE_STATUS]?.name ||
    (status === 'review_pending' ? '待复核' : status)
}

// 搜索
const handleSearch = async () => {
  const params: Record<string, unknown> = {
    violationType: filterForm.value.violationType || undefined,
    alertLevel: filterForm.value.alertLevel || undefined,
    status: filterForm.value.status || undefined,
  }

  if (filterForm.value.dateRange && filterForm.value.dateRange.length === 2) {
    params.startTime = filterForm.value.dateRange[0]
    params.endTime = filterForm.value.dateRange[1]
  }

  await store.searchViolations(params)
}

// 重置
const handleReset = async () => {
  filterForm.value = {
    violationType: '',
    alertLevel: '',
    status: '',
    dateRange: [],
  }
  await store.searchViolations({})
}

// 查看详情
const handleViewDetail = async (violation: ViolationEvent) => {
  detailDialogVisible.value = true
  detailActiveTab.value = 'info'
  operationLogs.value = []
  await store.fetchViolationDetail(violation.id)
  // 加载操作日志
  try {
    const res = await videoMonitorApi.getViolationLogs(violation.id)
    if (res.code === 'SUCCESS' && res.data) {
      operationLogs.value = res.data
    }
  } catch (e) {
    // 忽略
  }
}

// 打开处理对话框
const openHandleDialog = (violation: ViolationEvent) => {
  currentViolation.value = violation
  handleForm.value = {
    status: 'resolved',
    handleRemark: '',
  }
  handleDialogVisible.value = true
}

// 提交处理
const submitHandle = async () => {
  if (!handleForm.value.handleRemark) {
    ElMessage.warning('请填写处理备注')
    return
  }

  const success = await store.handleViolation(currentViolation.value!.id, {
    status: handleForm.value.status,
    handleRemark: handleForm.value.handleRemark,
  })

  if (success) {
    ElMessage.success('处理成功')
    handleDialogVisible.value = false
    currentViolation.value = null
  } else {
    ElMessage.error('处理失败')
  }
}

// 打开复核对话框
const openReviewDialog = (violation: ViolationEvent) => {
  currentViolation.value = violation
  reviewForm.value = {
    reviewStatus: 'approved',
    reviewRemark: '',
  }
  reviewDialogVisible.value = true
}

// 提交复核
const submitReview = async () => {
  if (!reviewForm.value.reviewRemark) {
    ElMessage.warning('请填写复核备注')
    return
  }

  // 调用复核API
  const success = await store.reviewViolation(currentViolation.value!.id, {
    reviewStatus: reviewForm.value.reviewStatus,
    reviewRemark: reviewForm.value.reviewRemark,
  })

  if (success) {
    ElMessage.success('复核成功')
    reviewDialogVisible.value = false
    currentViolation.value = null
  } else {
    ElMessage.error('复核失败')
  }
}

// 分页
const handlePageChange = (page: number) => {
  store.changeViolationPage(page)
}

// 批量处理
const selectedViolations = ref<ViolationEvent[]>([])
const batchHandleDialogVisible = ref(false)
const batchHandleForm = ref({
  status: 'resolved' as 'assigned' | 'processing' | 'resolved',
  handleRemark: '',
})

const handleSelectionChange = (rows: ViolationEvent[]) => {
  selectedViolations.value = rows
}

const openBatchHandleDialog = () => {
  if (selectedViolations.value.length === 0) {
    ElMessage.warning('请先选择需要处理的违规记录')
    return
  }
  batchHandleForm.value = { status: 'resolved', handleRemark: '' }
  batchHandleDialogVisible.value = true
}

const submitBatchHandle = async () => {
  if (!batchHandleForm.value.handleRemark) {
    ElMessage.warning('请填写处理备注')
    return
  }
  const ids = selectedViolations.value.map(v => v.id)
  const success = await store.batchHandleViolations(ids, {
    status: batchHandleForm.value.status,
    handleRemark: batchHandleForm.value.handleRemark,
  })
  if (success) {
    ElMessage.success(`成功处理 ${ids.length} 条违规记录`)
    batchHandleDialogVisible.value = false
    selectedViolations.value = []
  } else {
    ElMessage.error('批量处理失败')
  }
}

// SSE实时监听 - 新违规插入列表顶部
const newViolationCount = ref(0)

watch(() => store.violationAlertQueue.length, () => {
  newViolationCount.value = store.violationAlertQueue.length
})

const refreshWithNewViolations = () => {
  newViolationCount.value = 0
  store.fetchViolationList()
  store.fetchViolationStatistics()
}

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey

  filterForm.value = {
    violationType: typeof route.query.violationType === 'string' ? route.query.violationType : '',
    alertLevel: typeof route.query.alertLevel === 'string' ? route.query.alertLevel : '',
    status: typeof route.query.status === 'string' ? route.query.status : '',
    dateRange: []
  }

  await handleSearch()

  if (openDetailOnce && store.violationList[0]) {
    await handleViewDetail(store.violationList[0])
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

// 初始化
onMounted(async () => {
  await store.initViolationPage()
  await applyDashboardRouteQuery()
  store.connectSSE()
})

onUnmounted(() => {
  store.disconnectSSE()
})
</script>

<template>
  <div class="violation-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="error"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已自动定位到待处理紧急违规事件，可直接查看证据、播放录像并进入处理或复核动作。"
    />

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon total">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.violationStatistics.totalCount }}</div>
          <div class="stat-label">违规总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon pending">
          <el-icon><View /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.violationStatistics.pendingCount }}</div>
          <div class="stat-label">待处理</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon urgent">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.violationStatistics.urgentCount }}</div>
          <div class="stat-label">紧急事件</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon resolved">
          <el-icon><Check /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.violationStatistics.resolvedCount }}</div>
          <div class="stat-label">已处理</div>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div v-if="newViolationCount > 0" class="new-violation-tip">
      <el-icon><Warning /></el-icon>
      <span>收到 {{ newViolationCount }} 条新违规告警</span>
      <el-button type="primary" size="small" @click="refreshWithNewViolations">刷新查看</el-button>
    </div>
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="filterForm.violationType"
          placeholder="违规类型"
          clearable
          filterable
          style="width: 150px"
        >
          <el-option
            v-for="item in violationTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="filterForm.alertLevel"
          placeholder="告警级别"
          clearable
          style="width: 120px"
        >
          <el-option
            v-for="item in alertLevelOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="filterForm.status"
          placeholder="处理状态"
          clearable
          style="width: 120px"
        >
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-date-picker
          v-model="filterForm.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          :editable="false"
          style="width: 360px"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>
      <div class="toolbar-right">
        <el-button :icon="VideoCamera" @click="router.push('/video-monitor')">
          返回视频监控
        </el-button>
      </div>
    </div>

    <!-- 违规列表 -->
    <div class="violation-table">
      <el-table
        :data="store.violationList"
        v-loading="store.violationLoading"
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column label="违规类型" width="130">
          <template #default="{ row }">
            <span>{{ getViolationTypeName(row.violationType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="告警级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getAlertLevelType(row.alertLevel)" size="small">
              {{ getAlertLevelName(row.alertLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="发生位置" min-width="120" />
        <el-table-column prop="deviceName" label="识别设备" width="140" />
        <el-table-column prop="occurredAt" label="发生时间" width="160" />
        <el-table-column label="置信度" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.confidence != null" :class="{ 'high-confidence': row.confidence >= 90 }">{{ row.confidence }}%</span>
	            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="涉及人员" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.involvedCount != null ? row.involvedCount + '人' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleViewDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="row.status === 'resolved'"
              type="warning"
              link
              :icon="CircleCheck"
              v-permission="VIOLATION_PERMISSIONS.REVIEW"
              @click="openReviewDialog(row)"
            >
              复核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        :current-page="store.violationQuery.pageNum"
        :page-size="store.violationQuery.pageSize"
        :total="store.violationTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="违规事件详情"
      width="700px"
    >
      <div v-loading="detailLoading">
        <el-tabs v-if="detailData" v-model="detailActiveTab">
          <el-tab-pane label="事件详情" name="info">
            <div class="detail-content">
              <!-- 基本信息 -->
              <div class="detail-section">
                <h4>基本信息</h4>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="违规类型">
                    {{ getViolationTypeName(detailData.violationType) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="告警级别">
                    <el-tag :type="getAlertLevelType(detailData.alertLevel)" size="small">
                      {{ getAlertLevelName(detailData.alertLevel) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="发生位置">{{ detailData.location }}</el-descriptions-item>
                  <el-descriptions-item label="识别设备">{{ detailData.deviceName }}</el-descriptions-item>
                  <el-descriptions-item label="发生时间">{{ detailData.occurredAt }}</el-descriptions-item>
                  <el-descriptions-item label="持续时长">{{ detailData.duration != null ? detailData.duration + '秒' : '-' }}</el-descriptions-item>
                  <el-descriptions-item label="置信度">{{ detailData.confidence != null ? detailData.confidence + '%' : '-' }}</el-descriptions-item>
                  <el-descriptions-item label="涉及人数">{{ detailData.involvedCount != null ? detailData.involvedCount + '人' : '-' }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <!-- 视频截图 -->
              <div v-if="detailData.screenshotUrl" class="detail-section">
                <h4>违规截图</h4>
                <el-image
                  :src="detailData.screenshotUrl"
                  :preview-src-list="[detailData.screenshotUrl]"
                  fit="contain"
                  style="max-height: 300px"
                />
              </div>

              <!-- 关联录像 -->
              <div class="detail-section">
                <h4><el-icon><Link /></el-icon> 关联录像</h4>
                <template v-if="detailData.recordingId && detailData.recordingPlaybackUrl">
                  <el-descriptions :column="2" border size="small">
                    <el-descriptions-item label="录像时间段">
                      {{ formatDateTime(detailData.recordingStartTime) }} ~ {{ formatDateTime(detailData.recordingEndTime) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="录像设备">
                      {{ detailData.deviceName }}
                    </el-descriptions-item>
                  </el-descriptions>
                  <div style="margin-top: 12px; display: flex; gap: 10px;">
                    <el-button type="primary" :icon="VideoPlay" size="small" @click="handlePlayRecording">
                      播放录像
                    </el-button>
                    <el-button type="default" :icon="VideoCamera" size="small" @click="navigateToPlayback">
                      查看完整回放
                    </el-button>
                  </div>
                  <!-- 内联播放器 -->
                  <div v-if="inlinePlaybackVisible" class="inline-player-wrapper">
                    <div v-if="inlinePlaybackLoading" class="player-loading">
                      <el-icon class="is-loading"><View /></el-icon>
                      <span>加载录像中...</span>
                    </div>
                    <VideoPlayer
                      ref="violationPlayerRef"
                      :src="inlinePlaybackUrl"
                      type="native"
                      :show-controls="true"
                      :autoplay="true"
                      :muted="false"
                      height="360px"
                      @ready="handleViolationPlayerReady"
                    />
                  </div>
                </template>
                <div v-else class="no-recording-tip">
                  <el-icon><VideoCamera /></el-icon>
                  <span>暂未关联录像（录像可能尚未生成）</span>
                </div>
              </div>

              <!-- 处理信息 -->
              <div v-if="detailData.handlerName" class="detail-section">
                <h4>处理信息</h4>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="处理人">{{ detailData.handlerName }}</el-descriptions-item>
                  <el-descriptions-item label="处理时间">{{ detailData.handledAt }}</el-descriptions-item>
                  <el-descriptions-item label="处理备注" :span="2">{{ detailData.handleRemark }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <!-- 复核信息 -->
              <div v-if="detailData.reviewerName" class="detail-section">
                <h4>复核信息</h4>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="复核人">{{ detailData.reviewerName }}</el-descriptions-item>
                  <el-descriptions-item label="复核时间">{{ detailData.reviewedAt }}</el-descriptions-item>
                  <el-descriptions-item label="复核状态">
                    <el-tag :type="detailData.reviewStatus === 'approved' ? 'success' : 'danger'" size="small">
                      {{ detailData.reviewStatus === 'approved' ? '通过' : '不通过' }}
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="操作日志" name="logs">
            <div v-if="operationLogs.length" class="operation-log-list">
              <el-timeline>
                <el-timeline-item
                  v-for="log in operationLogs"
                  :key="log.id"
                  :timestamp="log.createdAt"
                  placement="top"
                >
                  <div class="log-item">
                    <span class="log-action">{{ log.actionName }}</span>
                    <span v-if="log.operatorName" class="log-operator">by {{ log.operatorName }}</span>
                    <p v-if="log.content" class="log-content">{{ log.content }}</p>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>
            <el-empty v-else description="暂无操作记录" :image-size="80" />
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 复核对话框 -->
    <el-dialog
      v-model="reviewDialogVisible"
      title="二次复核"
      width="500px"
    >
      <el-form :model="reviewForm" label-width="80px">
        <el-form-item label="违规类型">
          <span>{{ currentViolation ? getViolationTypeName(currentViolation.violationType) : '' }}</span>
        </el-form-item>
        <el-form-item label="处理人">
          <span>{{ currentViolation?.handlerName }}</span>
        </el-form-item>
        <el-form-item label="处理时间">
          <span>{{ currentViolation?.handledAt }}</span>
        </el-form-item>
        <el-form-item label="处理备注">
          <span>{{ currentViolation?.handleRemark }}</span>
        </el-form-item>
        <el-form-item label="复核结果" required>
          <el-radio-group v-model="reviewForm.reviewStatus">
            <el-radio value="approved">
              <el-icon><CircleCheck /></el-icon> 通过
            </el-radio>
            <el-radio value="rejected">
              <el-icon><CircleClose /></el-icon> 不通过
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核备注" required>
          <el-input
            v-model="reviewForm.reviewRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入复核说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          v-permission="VIOLATION_PERMISSIONS.REVIEW"
          @click="submitReview"
        >
          确认复核
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style lang="scss" scoped>
.violation-page {
  padding: 20px;
  background: $bg-base;
  min-height: 100%;
}

.dashboard-entry-alert {
  margin-bottom: 16px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
  background: $bg-white;
  border-radius: 8px;
  box-shadow: $box-shadow-light;

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 16px;
    font-size: 24px;
    color: #fff;

    &.total { background: $primary-color; }
    &.pending { background: $warning-color; }
    &.urgent { background: $danger-color; }
    &.resolved { background: $success-color; }
  }

  .stat-content {
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: $text-primary;
    }

    .stat-label {
      font-size: 14px;
      color: $text-secondary;
      margin-top: 4px;
    }
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px;
  background: $bg-white;
  border-radius: 8px;
  box-shadow: $box-shadow-light;

  .toolbar-left {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}

.new-violation-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 16px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 8px;
  color: $danger-color;
  font-size: 14px;
  animation: fadeIn 0.3s;

  .el-icon {
    font-size: 18px;
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

.violation-table {
  background: $bg-white;
  border-radius: 8px;
  box-shadow: $box-shadow-light;
  padding: 16px;
}

.high-confidence {
  color: $success-color;
  font-weight: 600;
}

.operation-log-list {
  padding: 8px 0;
  max-height: 400px;
  overflow-y: auto;

  .log-item {
    .log-action {
      font-weight: 600;
      color: #303133;
    }

    .log-operator {
      margin-left: 8px;
      color: #909399;
      font-size: 13px;
    }

    .log-content {
      margin: 4px 0 0;
      color: #606266;
      font-size: 13px;
      line-height: 1.5;
    }
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding: 16px;
  background: $bg-white;
  border-radius: 8px;
}

.detail-content {
  .detail-section {
    margin-bottom: 24px;

    h4 {
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid $border-base;
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }
}

.inline-player-wrapper {
  margin-top: 12px;
  border: 1px solid $border-base;
  border-radius: 8px;
  overflow: hidden;
  position: relative;

  .player-loading {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    gap: 8px;
    z-index: 10;
    font-size: 14px;
  }
}

.no-recording-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  color: $text-secondary;
  font-size: 13px;
}
</style>
