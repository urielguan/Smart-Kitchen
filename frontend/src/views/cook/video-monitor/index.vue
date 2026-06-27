<script setup lang="ts">
import { onMounted, onUnmounted, computed, ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Setting, FullScreen, VideoCamera, Warning, DataAnalysis, Refresh } from '@element-plus/icons-vue'
import { ElNotification, ElMessage } from 'element-plus'
import { useVideoMonitorStore } from '@/stores/modules/video-monitor'
import StatCard from '@/components/common/StatCard.vue'
import VideoPlayer from '@/components/business/video-monitor/VideoPlayer.vue'
import PTZControl from '@/components/business/video-monitor/PTZControl.vue'
import type { Camera, PTZDirection, ViolationEvent, DeviceStatusEvent } from '@/types/video-monitor'
import { videoMonitorApi } from '@/api/modules/video-monitor'
import { VIOLATION_PERMISSIONS, VIDEO_PLAYBACK_PERMISSIONS, BEHAVIOR_ANALYSIS_PERMISSIONS } from '@/constants/permission'

const store = useVideoMonitorStore()
const router = useRouter()

// 筛选条件
const filterForm = ref({
  onlineStatus: (store.monitorQuery.onlineStatus as string) || '',
  location: store.monitorQuery.location || '',
})

// 在线状态选项
const statusOptions = [
  { label: '全部', value: '' },
  { label: '在线', value: 'online' },
  { label: '离线', value: 'offline' },
]

// 布局模式
const layoutOptions: { label: string; value: 1 | 4 | 9 }[] = [
  { label: '单画面', value: 1 },
  { label: '4画面', value: 4 },
  { label: '9画面', value: 9 },
]

// PTZ控制面板状态
const ptzPanelCamera = ref<Camera | null>(null)

// 告警弹窗
const alertDialogVisible = ref(false)
const alertAutoCloseTimer = ref<ReturnType<typeof setTimeout> | null>(null)

// 告警锁定状态（锁定到违规摄像头画面，阻止手动切换）
const alertLockedCamera = ref<Camera | null>(null)

// 计算属性
const gridCols = computed(() => {
  switch (store.layoutMode) {
    case 1: return 1
    case 4: return 2
    case 9: return 3
    default: return 2
  }
})

// 播放告警提示音
const playAlertSound = () => {
  try {
    const ctx = new AudioContext()
    const oscillator = ctx.createOscillator()
    const gain = ctx.createGain()
    oscillator.connect(gain)
    gain.connect(ctx.destination)
    oscillator.frequency.value = 800
    oscillator.type = 'sine'
    gain.gain.value = 0.3
    oscillator.start()
    setTimeout(() => {
      oscillator.frequency.value = 1000
    }, 150)
    setTimeout(() => {
      oscillator.stop()
      ctx.close()
    }, 400)
  } catch (e) {
    // 静默处理
  }
}

// 处理违规告警
const handleViolationAlert = (violation: ViolationEvent) => {
  playAlertSound()

  // 自动切换到违规摄像头画面
  if (violation.deviceId) {
    const matchedCamera = store.cameraList.find(c => c.id === violation.deviceId)
    if (matchedCamera) {
      store.changeLayoutMode(1)
      store.selectCamera(matchedCamera)
      alertLockedCamera.value = matchedCamera
    }
  }

  ElNotification({
    title: `违规告警: ${violation.violationTypeName || violation.violationType}`,
    message: `位置: ${violation.location} | 置信度: ${violation.confidence}%`,
    type: 'warning',
    duration: 5000,
    position: 'top-right',
  })

  // 显示告警详情弹窗
  alertDialogVisible.value = true

  // 30秒自动关闭
  if (alertAutoCloseTimer.value) clearTimeout(alertAutoCloseTimer.value)
  alertAutoCloseTimer.value = setTimeout(() => {
    alertDialogVisible.value = false
    store.clearViolationAlert()
  }, 30000)
}

// 监听SSE违规告警
watch(() => store.latestViolationAlert, (violation) => {
  if (violation) {
    handleViolationAlert(violation)
  }
}, { deep: true })

// 搜索位置输入提示
const showLocTip = ref(false)
let locTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerLocTip = () => {
  if (locTipTimer) clearTimeout(locTipTimer)
  showLocTip.value = false
  requestAnimationFrame(() => { showLocTip.value = true })
  locTipTimer = setTimeout(() => { showLocTip.value = false }, 2000)
}
const handleLocInput = () => {
  if (filterForm.value.location.length >= 50) triggerLocTip()
  else showLocTip.value = false
}
const handleLocKeydown = (e: KeyboardEvent) => {
  if (filterForm.value.location.length >= 50 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerLocTip()
  }
}

// 搜索
const handleSearch = () => {
  store.searchCameras({
    onlineStatus: filterForm.value.onlineStatus as 'online' | 'offline' | undefined,
    location: filterForm.value.location || undefined,
  })
}

// 重置
const handleReset = () => {
  filterForm.value = { onlineStatus: '', location: '' }
  store.searchCameras({})
}

// 切换布局
const handleLayoutChange = (mode: 1 | 4 | 9) => {
  if (alertLockedCamera.value) {
    unlockAlertCamera()
  }
  store.changeLayoutMode(mode)
  videoMonitorApi.logMonitorAction({ action: 'layout_change', extra: `layoutMode=${mode}` }).catch(() => {})
}

// 选择摄像头
const handleSelectCamera = (camera: Camera) => {
  if (alertLockedCamera.value) {
    // 告警锁定中，不允许切换
    return
  }
  if (store.layoutMode === 1) {
    store.selectCamera(camera)
    videoMonitorApi.logMonitorAction({
      action: 'camera_switch',
      deviceId: camera.id,
      deviceName: camera.deviceName,
    }).catch(() => {})
  }
}

// 显示PTZ控制
const showPTZControl = (camera: Camera) => {
  if (camera.ptzSupport && camera.onlineStatus === 'online') {
    ptzPanelCamera.value = camera
  }
}

// PTZ控制
const handlePTZControl = async (direction: PTZDirection, speed: number) => {
  if (!ptzPanelCamera.value) return
  await store.ptzControl(ptzPanelCamera.value.id, direction, speed)
}

// 关闭PTZ面板
const closePTZPanel = () => {
  ptzPanelCamera.value = null
}

// 全屏单个摄像头
const toggleCameraFullscreen = (camera: Camera) => {
  const oldMode = store.layoutMode
  store.changeLayoutMode(1)
  store.selectCamera(camera)

  videoMonitorApi.logMonitorAction({
    action: 'camera_switch',
    deviceId: camera.id,
    deviceName: camera.deviceName,
    extra: oldMode === 1 && store.selectedCamera?.id === camera.id ? 'exit_fullscreen' : 'enter_fullscreen',
  }).catch(() => {})

  // 如果之前已经是单画面，退出全屏
  if (oldMode === 1 && store.selectedCamera?.id === camera.id) {
    store.changeLayoutMode(4)
    store.selectCamera(null)
  }
}

// 分页
const handlePageChange = (page: number) => {
  store.changeCameraPage(page)
}

// 获取视频流地址
const getStreamUrl = (camera: Camera): string | undefined => {
  const prefersAnalysis = camera.preferAnalysisStream !== false
  const resolvedUrl = prefersAnalysis
    ? (camera.analysisStreamUrl || camera.hlsUrl || camera.streamUrl)
    : (camera.hlsUrl || camera.streamUrl || camera.analysisStreamUrl)
  if (!resolvedUrl) return undefined

  if (prefersAnalysis && camera.analysisStreamUrl) {
    return camera.analysisStreamUrl
  }

  if (camera.onlineStatus === 'online' || reconnectingCameraId.value === camera.id) {
    return resolvedUrl
  }

  return undefined
}

const getReconnectableStreamUrl = (camera: Camera): string | undefined => {
  return camera.analysisStreamUrl || camera.hlsUrl || camera.streamUrl
}

const getPlayerType = (camera: Camera): 'hls' | 'flv' | 'native' | 'mjpeg' => {
  const prefersAnalysis = camera.preferAnalysisStream !== false
  if (prefersAnalysis && camera.analysisStreamUrl) {
    return camera.analysisStreamType || 'mjpeg'
  }
  if (camera.hlsUrl) {
    return 'hls'
  }
  return 'native'
}

const getPlayerKey = (camera: Camera): string => {
  const resolvedUrl = getStreamUrl(camera) || getReconnectableStreamUrl(camera) || 'no-stream'
  return `${camera.id}-${camera.onlineStatus}-${getPlayerType(camera)}-${resolvedUrl}`
}

// 关闭告警弹窗
const closeAlertDialog = () => {
  alertDialogVisible.value = false
  store.clearViolationAlert()
  unlockAlertCamera()
  if (alertAutoCloseTimer.value) {
    clearTimeout(alertAutoCloseTimer.value)
    alertAutoCloseTimer.value = null
  }
}

// 解锁告警锁定画面，恢复之前的多画面布局
const unlockAlertCamera = () => {
  alertLockedCamera.value = null
}

// 重连相关状态
const reconnectingCameraId = ref<number | null>(null)

// 手动重连摄像头
const handleReconnect = async (camera: Camera) => {
  reconnectingCameraId.value = camera.id
  try {
    const result = await store.reconnectCamera(camera.id)
    if (result.success) {
      ElMessage.success(`正在重连「${camera.deviceName}」，请稍候...`)
    } else {
      ElMessage.warning(result.message || '重连失败，请稍后再试')
      reconnectingCameraId.value = null
    }
  } catch {
    ElMessage.error('重连请求失败')
    reconnectingCameraId.value = null
  }
}

// 监听 SSE 设备状态变更
watch(() => store.latestDeviceStatus, (event: DeviceStatusEvent | null) => {
  if (!event) return

  // 如果有摄像头从离线恢复在线，清除重连状态
  if (event.newStatus === 'online') {
    if (reconnectingCameraId.value === event.deviceId) {
      reconnectingCameraId.value = null
      ElMessage.success(`「${event.deviceName}」已恢复在线`)
    }
  }
})

// 前往违规记录页
const goToViolation = () => {
  closeAlertDialog()
  router.push('/violation')
}

// 初始化
onMounted(() => {
  if (store.cameraList.length > 0) {
    store.fetchMonitorStatistics()
  } else {
    store.initMonitorPage()
  }
  store.connectSSE()
})

onUnmounted(() => {
  store.disconnectSSE()
  alertLockedCamera.value = null
  if (alertAutoCloseTimer.value) {
    clearTimeout(alertAutoCloseTimer.value)
  }
})
</script>

<template>
  <div class="video-monitor-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <StatCard
        title="摄像头总数"
        :value="store.monitorStatistics.totalCameras"
        color="primary"
      />
      <StatCard
        title="在线数量"
        :value="store.monitorStatistics.onlineCameras"
        color="success"
      />
      <StatCard
        title="离线数量"
        :value="store.monitorStatistics.offlineCameras"
        color="default"
      />
      <StatCard
        title="告警数量"
        :value="store.monitorStatistics.alertCameras"
        color="danger"
      />
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <!-- 筛选 -->
        <el-select
          v-model="filterForm.onlineStatus"
          placeholder="在线状态"
          clearable
          style="width: 120px"
          @change="handleSearch"
        >
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-tooltip :visible="showLocTip" content="搜索位置最多输入50个字符" placement="top">
          <el-input
            v-model="filterForm.location"
            placeholder="搜索位置"
            clearable
            maxlength="50"
            style="width: 200px"
            @keyup.enter="handleSearch"
            @input="handleLocInput"
            @keydown="handleLocKeydown"
          />
        </el-tooltip>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <div class="toolbar-right">
        <!-- 违规记录入口 -->
        <el-button type="danger" :icon="Warning" v-permission="VIOLATION_PERMISSIONS.VIOLATION" @click="router.push('/violation')">
          违规记录
        </el-button>
        <!-- 行为分析入口 -->
        <el-button :icon="DataAnalysis" v-permission="BEHAVIOR_ANALYSIS_PERMISSIONS.BEHAVIOR_ANALYSIS" @click="router.push('/behavior-analysis')">
          行为分析
        </el-button>
        <!-- 视频回放入口 -->
        <el-button :icon="VideoCamera" v-permission="VIDEO_PLAYBACK_PERMISSIONS.VIDEO_PLAYBACK" @click="router.push('/video-playback')">
          视频回放
        </el-button>
        <!-- 布局切换 -->
        <el-radio-group
          :model-value="store.layoutMode"
          @change="handleLayoutChange"
        >
          <el-radio-button
            v-for="opt in layoutOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 摄像头列表 -->
    <div
      class="camera-grid"
      :style="{ gridTemplateColumns: `repeat(${gridCols}, 1fr)` }"
    >
      <div
        v-for="camera in store.cameraList"
        :key="camera.id"
        class="camera-card"
        :class="{
          'is-offline': camera.onlineStatus === 'offline',
          'is-selected': store.selectedCamera?.id === camera.id,
          'is-alert-locked': alertLockedCamera?.id === camera.id,
        }"
        @click="handleSelectCamera(camera)"
      >
        <!-- 视频播放器 -->
        <div class="video-area">
          <VideoPlayer
            v-if="getStreamUrl(camera)"
            :key="getPlayerKey(camera)"
            :src="getStreamUrl(camera)"
            :type="getPlayerType(camera)"
            :is-live="true"
            :autoplay="true"
            :muted="true"
            :show-controls="store.layoutMode === 1"
          />

          <!-- 离线遮罩 -->
          <div v-else class="offline-overlay">
            <div class="offline-placeholder">
              <svg class="offline-camera-icon" viewBox="0 0 64 64" fill="none">
                <rect x="8" y="16" width="40" height="28" rx="4" stroke="currentColor" stroke-width="2.5" fill="none"/>
                <path d="M52 22 L60 18 V46 L52 42" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linejoin="round"/>
                <line x1="4" y1="4" x2="60" y2="60" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
              </svg>
              <span class="offline-label">设备离线</span>
            </div>
            <el-button
              v-if="getReconnectableStreamUrl(camera)"
              class="reconnect-btn"
              :loading="reconnectingCameraId === camera.id"
              type="primary"
              size="small"
              @click.stop="handleReconnect(camera)"
            >
              <el-icon v-if="reconnectingCameraId !== camera.id"><Refresh /></el-icon>
              {{ reconnectingCameraId === camera.id ? '重连中...' : '重新连接' }}
            </el-button>
            <span v-else class="offline-hint">未配置视频流地址</span>
          </div>

          <!-- 状态角标 -->
          <div class="status-badge" :class="camera.onlineStatus">
            {{ camera.onlineStatus === 'online' ? '在线' : '离线' }}
          </div>

          <!-- 告警锁定角标 -->
          <div v-if="alertLockedCamera?.id === camera.id" class="lock-badge">
            <el-icon><Warning /></el-icon>
            <span>告警锁定</span>
          </div>

          <!-- 告警角标 -->
          <div v-if="camera.alertCount > 0" class="alert-badge">
            <el-badge :value="camera.alertCount" type="danger">
              <el-icon><Bell /></el-icon>
            </el-badge>
          </div>

          <!-- 操作按钮 (单画面模式) -->
          <div v-if="store.layoutMode === 1" class="camera-actions">
            <el-button
              v-if="camera.ptzSupport && camera.onlineStatus === 'online'"
              type="primary"
              :icon="Setting"
              circle
              size="small"
              title="云台控制"
              @click.stop="showPTZControl(camera)"
            />
            <el-button
              type="primary"
              :icon="FullScreen"
              circle
              size="small"
              title="全屏"
              @click.stop="toggleCameraFullscreen(camera)"
            />
          </div>

          <!-- PTZ控制面板 -->
          <PTZControl
            v-if="ptzPanelCamera?.id === camera.id"
            :visible="true"
            :ptz-support="camera.ptzSupport"
            @control="handlePTZControl"
            @close="closePTZPanel"
          />
        </div>

        <!-- 信息区域 -->
        <div class="info-area">
          <div class="camera-title">{{ camera.deviceName }}</div>
          <div class="camera-location">{{ camera.location }}</div>
          <div class="camera-meta">
            <span class="resolution">{{ camera.resolution }}</span>
            <span class="frame-rate">{{ camera.frameRate }}fps</span>
            <span v-if="camera.analysisStreamUrl && camera.preferAnalysisStream !== false" class="ai-tag">YOLO识别流</span>
            <span v-if="camera.ptzSupport" class="ptz-tag">云台</span>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!store.cameraLoading && store.cameraList.length === 0" class="empty-state">
        <span class="empty-icon">📷</span>
        <span class="empty-text">暂无监控数据</span>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="store.cameraTotal > store.monitorQuery.pageSize!" class="pagination-wrapper">
      <el-pagination
        :current-page="store.monitorQuery.pageNum"
        :page-size="store.monitorQuery.pageSize"
        :total="store.cameraTotal"
        :page-sizes="[4, 9, 12, 24]"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 违规告警弹窗 -->
    <el-dialog
      v-model="alertDialogVisible"
      title="违规告警"
      width="480px"
      :close-on-click-modal="false"
      :show-close="true"
      @close="closeAlertDialog"
    >
      <div v-if="store.latestViolationAlert" class="alert-content">
        <div class="alert-type-row">
          <el-tag type="danger" size="large">{{ store.latestViolationAlert.violationTypeName }}</el-tag>
          <el-tag :type="store.latestViolationAlert.alertLevel === 'danger' ? 'danger' : 'warning'" size="small">
            {{ store.latestViolationAlert.alertLevelName }}
          </el-tag>
        </div>
        <el-descriptions :column="2" border style="margin-top: 16px;">
          <el-descriptions-item label="发生位置">{{ store.latestViolationAlert.location }}</el-descriptions-item>
          <el-descriptions-item label="识别设备">{{ store.latestViolationAlert.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ store.latestViolationAlert.confidence }}%</el-descriptions-item>
          <el-descriptions-item label="涉及人员">{{ store.latestViolationAlert.involvedCount || 1 }}人</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="closeAlertDialog">忽略并解锁</el-button>
        <el-button type="primary" @click="goToViolation">查看详情</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style lang="scss" scoped>
.video-monitor-page {
  padding: 20px;
  background: $bg-base;
  min-height: 100%;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
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

  .toolbar-right {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}

.camera-grid {
  display: grid;
  gap: 16px;
}

.camera-card {
  background: $bg-white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: $box-shadow-light;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    box-shadow: $box-shadow-base;
    transform: translateY(-2px);

    .camera-actions {
      opacity: 1;
    }
  }

  &.is-offline {
    opacity: 0.7;

    .video-area {
      background: $bg-light;
    }
  }

  &.is-selected {
    border: 2px solid $primary-color;
  }

  &.is-alert-locked {
    border: 2px solid $danger-color;
    animation: alert-pulse 1.5s ease-in-out infinite;
  }
}

.video-area {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #1a1a1a;

  .offline-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: #1a1a1a;
    color: $text-secondary;

    .offline-placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      margin-bottom: 16px;
    }

    .offline-camera-icon {
      width: 56px;
      height: 56px;
      color: rgba(255, 255, 255, 0.25);
      margin-bottom: 10px;
    }

    .offline-label {
      font-size: 14px;
      color: rgba(255, 255, 255, 0.5);
      letter-spacing: 1px;
    }

    .reconnect-btn {
      font-size: 12px;
      padding: 6px 14px;
      border-radius: 4px;
      opacity: 0.85;
      transition: opacity 0.3s;

      &:hover {
        opacity: 1;
      }
    }

    .offline-hint {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.3);
    }
  }

  .status-badge {
    position: absolute;
    top: 8px;
    left: 8px;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
    color: $white;

    &.online {
      background: $success-color;
    }

    &.offline {
      background: $text-secondary;
    }
  }

  .alert-badge {
    position: absolute;
    top: 8px;
    right: 8px;
  }

  .lock-badge {
    position: absolute;
    top: 8px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 2px 10px;
    border-radius: 4px;
    background: rgba(245, 108, 108, 0.9);
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    animation: alert-pulse 1.5s ease-in-out infinite;
  }

  .camera-actions {
    position: absolute;
    bottom: 8px;
    right: 8px;
    display: flex;
    gap: 8px;
    opacity: 0;
    transition: opacity 0.3s;

    :deep(.el-button.is-circle) {
      background: rgba(0, 0, 0, 0.6);
      border: none;
      color: #fff;

      &:hover {
        background: rgba(0, 0, 0, 0.8);
      }
    }
  }
}

.info-area {
  padding: 12px;

  .camera-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 4px;
  }

  .camera-location {
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: 8px;
  }

  .camera-meta {
    display: flex;
    gap: 8px;
    font-size: 12px;
    color: $text-secondary;

    .ptz-tag {
      color: $primary-color;
      background: rgba($primary-color, 0.1);
      padding: 0 4px;
      border-radius: 2px;
    }

    .ai-tag {
      color: $success-color;
      background: rgba($success-color, 0.12);
      padding: 0 4px;
      border-radius: 2px;
    }
  }
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: $text-secondary;

  .empty-icon {
    font-size: 64px;
    margin-bottom: 16px;
  }

  .empty-text {
    font-size: 14px;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding: 16px;
  background: $white;
  border-radius: 8px;
}

.alert-content {
  .alert-type-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

@keyframes alert-pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}
</style>
