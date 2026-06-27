<script setup lang="ts">
import { onMounted, nextTick, watch, ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { VideoPlay, Delete, Search, Refresh, Download, Calendar, VideoCamera, Camera, FolderAdd, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useVideoMonitorStore } from '@/stores/modules/video-monitor'
import { videoMonitorApi } from '@/api/modules/video-monitor'
import VideoPlayer from '@/components/business/video-monitor/VideoPlayer.vue'
import type { VideoRecording, VideoClip, ClipExtractRequest, EvidencePackage, MonitorAuditLog, MonitorAuditLogQuery } from '@/types/video-monitor'
import { CLIP_PURPOSE_TAGS, CLIP_STATUS_MAP, EVIDENCE_PACKAGE_STATUS_MAP } from '@/types/video-monitor'
import { VIDEO_PLAYBACK_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

const router = useRouter()
const route = useRoute()
const store = useVideoMonitorStore()

// 筛选条件
const filterForm = ref({
  deviceId: undefined as number | undefined,
  dateRange: [] as string[],
  recordingType: undefined as string | undefined,
})

// 播放对话框
const playDialogVisible = ref(false)
const currentRecording = ref<VideoRecording | null>(null)
const playbackUrl = ref('')
const playbackLoading = ref(false)

// 录像类型映射
const recordingTypeMap: Record<string, { type: 'primary' | 'warning' | 'danger' | 'info'; label: string }> = {
  continuous: { type: 'primary', label: '连续录像' },
  alarm: { type: 'danger', label: '告警录像' },
  manual: { type: 'warning', label: '手动录像' },
}

// 录像类型选项
const recordingTypeOptions = [
  { label: '全部', value: undefined },
  { label: '连续录像', value: 'continuous' },
  { label: '告警录像', value: 'alarm' },
  { label: '手动录像', value: 'manual' },
]

// 计算属性
const recordingTypeTag = (type: string) => recordingTypeMap[type] || { type: 'info', label: type }

// 统计数据（从后端获取全量统计，非仅当前页）
const recordingStatistics = ref({
  totalCount: 0,
  totalFileSize: 0,
  alarmCount: 0,
  aiMarkCount: 0,
})

const fetchStatistics = async () => {
  const params: Record<string, unknown> = {
    deviceId: filterForm.value.deviceId,
    recordingType: filterForm.value.recordingType,
  }
  if (filterForm.value.dateRange?.length === 2) {
    params.startTime = filterForm.value.dateRange[0]
    params.endTime = filterForm.value.dateRange[1]
  }
  try {
    const res = await videoMonitorApi.getRecordingStatistics(params)
    if (res.code === 'SUCCESS' && res.data) {
      recordingStatistics.value = res.data
    }
  } catch {
    // silent — statistics failure should not block the page
  }
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

// 搜索
const handleSearch = () => {
  const params: Record<string, unknown> = {
    deviceId: filterForm.value.deviceId,
    recordingType: filterForm.value.recordingType,
  }
  if (filterForm.value.dateRange && filterForm.value.dateRange.length === 2) {
    params.startTime = filterForm.value.dateRange[0]
    params.endTime = filterForm.value.dateRange[1]
  }

  store.searchRecordings(params)
  fetchStatistics()
}

// 重置
const handleReset = () => {
  filterForm.value = { deviceId: undefined, dateRange: [], recordingType: undefined }
  store.searchRecordings({})
  fetchStatistics()
}

// 播放录像
const handlePlay = async (recording: VideoRecording) => {
  currentRecording.value = recording
  playbackLoading.value = true
  playDialogVisible.value = true
  recordingDuration.value = recording.duration || 0

  // 初始化片段截取表单
  clipForm.value = {
    startTimeOffset: 0,
    endTimeOffset: Math.min(60, recording.duration || 60),
    purposeTag: 'violation_trace',
  }

  // 加载该录像的已有片段
  loadClipsForRecording(recording.id)

  // 加载该录像的截图列表
  store.fetchScreenshotList(recording.id)

  try {
    const url = await store.getPlaybackUrl(recording.id)
    if (url) {
      playbackUrl.value = url
    } else {
      // 如果没有获取到URL，使用录像对象中的URL
      playbackUrl.value = recording.playbackUrl || ''
    }
  } catch (e) {
    ElMessage.error('获取播放地址失败')
    playbackUrl.value = ''
  } finally {
    playbackLoading.value = false
  }
}

// 下载录像
const downloading = ref(false)
const handleDownload = async (recording: VideoRecording) => {
  if (!recording.downloadUrl) {
    ElMessage.info('下载功能暂未开放')
    return
  }
  downloading.value = true
  try {
    const res = await fetch(recording.downloadUrl, { method: 'GET' })
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${recording.deviceName}_${recording.startTime?.replace(/[:T]/g, '-')}.mp4`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('下载已开始')
    videoMonitorApi.logMonitorAction({
      action: 'recording_download',
      deviceId: recording.deviceId,
      deviceName: recording.deviceName,
      recordingId: recording.id,
    }).catch(() => {})
  } catch {
    ElMessage.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 删除录像
const handleDelete = async (recording: VideoRecording) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除录像"${recording.deviceName} - ${formatDateTime(recording.startTime)}"吗？`,
      '删除确认',
      { type: 'warning' }
    )

    const success = await store.deleteRecording(recording.id)
    if (success) {
      ElMessage.success('删除成功')
      fetchStatistics()
    } else {
      ElMessage.error('删除失败')
    }
  } catch {
    // 用户取消
  }
}

// 批量删除
const selectedRecordings = ref<VideoRecording[]>([])

const handleSelectionChange = (selection: VideoRecording[]) => {
  selectedRecordings.value = selection
}

const handleBatchDelete = async () => {
  if (selectedRecordings.value.length === 0) {
    ElMessage.warning('请选择要删除的录像')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRecordings.value.length} 个录像吗？`,
      '批量删除确认',
      { type: 'warning' }
    )

    // 并行删除
    const results = await Promise.all(
      selectedRecordings.value.map(r => store.deleteRecording(r.id))
    )

    const successCount = results.filter(Boolean).length
    ElMessage.success(`成功删除 ${successCount} 个录像`)
    selectedRecordings.value = []
  } catch {
    // 用户取消
  }
}

// 分页
const handlePageChange = (page: number) => {
  store.changeRecordingPage(page)
}

// 每页数量变化
const handleSizeChange = (size: number) => {
  store.recordingQuery.pageSize = size
  store.recordingQuery.pageNum = 1
  store.fetchRecordingList()
}

// 关闭播放对话框
const closePlayDialog = () => {
  playDialogVisible.value = false
  playbackUrl.value = ''
  currentRecording.value = null
  store.stopClipPolling()
  clipForm.value = { startTimeOffset: 0, endTimeOffset: 0, purposeTag: 'violation_trace' }
}

// ==================== 片段截取 ====================
const playerCurrentTime = ref(0)
const clipForm = ref({
  startTimeOffset: 0,
  endTimeOffset: 0,
  purposeTag: 'violation_trace' as 'violation_trace' | 'accident_review' | 'process_review',
})
const clipExtracting = ref(false)
const recordingDuration = ref(0)

// 监听播放器时间更新
const handleTimeUpdate = (time: number) => {
  playerCurrentTime.value = time
}

// 监听播放器就绪（获取时长 + 违规锚点 seek）
const handlePlayerReady = () => {
  // 如果有违规跳转锚点时间，seek 到对应位置
  if (violationSeekTime.value !== null && playerRef.value) {
    playerRef.value.seekTo(violationSeekTime.value)
    violationSeekTime.value = null
  }
}

// 设置开始时间为当前播放位置
const setStartFromPlayer = () => {
  clipForm.value.startTimeOffset = Math.floor(playerCurrentTime.value)
}

// 设置结束时间为当前播放位置
const setEndFromPlayer = () => {
  clipForm.value.endTimeOffset = Math.floor(playerCurrentTime.value)
}

// 格式化秒数为 mm:ss 或 hh:mm:ss
const formatSeconds = (seconds: number): string => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

// 提取片段
const handleExtractClip = async () => {
  if (!currentRecording.value) return

  if (clipForm.value.endTimeOffset <= clipForm.value.startTimeOffset) {
    ElMessage.warning('结束时间必须大于开始时间')
    return
  }

  clipExtracting.value = true
  try {
    const data: ClipExtractRequest = {
      recordingId: currentRecording.value.id,
      startTimeOffset: clipForm.value.startTimeOffset,
      endTimeOffset: clipForm.value.endTimeOffset,
      purposeTag: clipForm.value.purposeTag,
    }

    const clip = await store.extractClip(data)
    if (clip) {
      ElMessage.success('片段截取任务已提交，正在处理中...')
      // 开始轮询
      store.startClipPolling(clip.id, currentRecording.value.id, (updatedClip) => {
        if (updatedClip.status === 'completed') {
          ElMessage.success('片段截取完成')
        } else if (updatedClip.status === 'failed') {
          ElMessage.error('片段截取失败: ' + (updatedClip.failReason || '未知错误'))
        }
      })
    }
  } catch {
    ElMessage.error('片段截取请求失败')
  } finally {
    clipExtracting.value = false
  }
}

// 下载片段
const handleDownloadClip = async (clip: VideoClip) => {
  if (!clip.downloadUrl) {
    ElMessage.info('片段尚未就绪')
    return
  }
  try {
    const res = await fetch(clip.downloadUrl, { method: 'GET' })
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `clip_${clip.id}_${clip.deviceName}.mp4`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('下载已开始')
  } catch {
    ElMessage.error('下载失败，请重试')
  }
}

// 删除片段
const handleDeleteClip = async (clip: VideoClip) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除片段 #${clip.id}（${clip.durationFormat}）吗？`,
      '删除确认',
      { type: 'warning' }
    )
    const success = await store.deleteClip(clip.id, currentRecording.value?.id)
    if (success) {
      ElMessage.success('删除成功')
    } else {
      ElMessage.error('删除失败')
    }
  } catch {
    // 用户取消
  }
}

// 加载指定录像的片段列表
const loadClipsForRecording = (recordingId: number) => {
  store.fetchClipList(recordingId)
}

// ==================== 截图功能 ====================
const playerRef = ref<InstanceType<typeof VideoPlayer> | null>(null)
const capturing = ref(false)

// 截图
const handleCaptureScreenshot = async () => {
  if (!playerRef.value || !currentRecording.value) {
    ElMessage.warning('请先播放视频')
    return
  }

  capturing.value = true
  try {
    const blob = await playerRef.value.captureFrame()
    const file = new File([blob], `screenshot_${Date.now()}.jpg`, { type: 'image/jpeg' })

    const result = await store.uploadScreenshot(file, {
      recordingId: currentRecording.value.id,
      captureTimeOffset: Math.floor(playerCurrentTime.value),
      purposeTag: clipForm.value.purposeTag,
    })

    if (result) {
      ElMessage.success('截图已保存')
    } else {
      ElMessage.error('截图上传失败')
    }
  } catch (e) {
    ElMessage.error('截图失败: ' + (e instanceof Error ? e.message : '未知错误'))
  } finally {
    capturing.value = false
  }
}

// 删除截图
const handleDeleteScreenshot = async (screenshot: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除截图 #${screenshot.id}（${screenshot.captureTimeFormat}）吗？`,
      '删除确认',
      { type: 'warning' }
    )
    const success = await store.deleteScreenshot(screenshot.id, currentRecording.value?.id)
    if (success) {
      ElMessage.success('删除成功')
    } else {
      ElMessage.error('删除失败')
    }
  } catch {
    // 用户取消
  }
}

// 下载截图
const handleDownloadScreenshot = async (screenshot: any) => {
  if (!screenshot.previewUrl) return
  try {
    const res = await fetch(screenshot.previewUrl, { method: 'GET' })
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `screenshot_${screenshot.id}_${screenshot.deviceName}.jpg`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('截图下载失败，请重试')
  }
}

const getAnalysisStatusText = (status?: string) => {
  if (!status) return '未分析'
  const map: Record<string, string> = {
    processing: '分析中',
    alerted: '已生成告警',
    threshold_not_met: '未达阈值',
    failed: '分析失败'
  }
  return map[status] || status
}

const handleAnalyzeScreenshot = async (screenshot: any) => {
  const result = await store.analyzeScreenshot(screenshot.id, currentRecording.value?.id)
  if (!result) {
    ElMessage.error('截图分析失败')
    return
  }
  if (result.taskStatus === 'alerted') {
    ElMessage.success('识别成功，已生成违规告警')
  } else if (result.taskStatus === 'threshold_not_met') {
    ElMessage.warning('识别已完成，但未达到告警阈值')
  } else if (result.taskStatus === 'failed') {
    ElMessage.error(result.errorMessage || 'AI分析失败')
  } else {
    ElMessage.info('分析任务已提交')
  }
}

// ==================== 证据包导出 ====================
const exportDialogVisible = ref(false)
const exportProgressVisible = ref(false)
const exportPackageName = ref('')

// 打开导出对话框
const handleOpenExport = () => {
  if (selectedRecordings.value.length === 0) {
    ElMessage.warning('请先选择要导出的录像')
    return
  }
  exportPackageName.value = ''
  exportDialogVisible.value = true
}

// 确认导出
const handleConfirmExport = async () => {
  exportDialogVisible.value = false
  exportProgressVisible.value = true

  try {
    const result = await store.createEvidencePackage({
      recordingIds: selectedRecordings.value.map(r => r.id),
      packageName: exportPackageName.value || undefined,
    })

    if (!result) {
      ElMessage.error('创建证据包失败')
      exportProgressVisible.value = false
      return
    }

    // 开始轮询打包状态
    store.startPackagePolling(result.id, (pkg) => {
      if (pkg.status === 'completed') {
        ElMessage.success('证据包打包完成，开始下载...')
        handleDownloadPackage(pkg)
        exportProgressVisible.value = false
        selectedRecordings.value = []
      } else if (pkg.status === 'failed') {
        ElMessage.error('证据包打包失败: ' + (pkg.failReason || '未知错误'))
      }
    })
  } catch {
    ElMessage.error('创建证据包失败')
    exportProgressVisible.value = false
  }
}

// 下载证据包 ZIP
const handleDownloadPackage = async (pkg: EvidencePackage) => {
  try {
    const res = await fetch(`/api/v1/device/evidence-packages/${pkg.id}/download`, { method: 'GET' })
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = pkg.fileName || `evidence_${pkg.packageNo}.zip`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载证据包失败')
  }
}

// 重试打包
const handleRetryPackage = async () => {
  if (!store.currentEvidencePackage) return
  const result = await store.retryEvidencePackage(store.currentEvidencePackage.id)
  if (result) {
    ElMessage.info('正在重新打包...')
    store.startPackagePolling(result.id, (pkg) => {
      if (pkg.status === 'completed') {
        ElMessage.success('证据包打包完成，开始下载...')
        handleDownloadPackage(pkg)
        exportProgressVisible.value = false
        selectedRecordings.value = []
      } else if (pkg.status === 'failed') {
        ElMessage.error('证据包打包失败: ' + (pkg.failReason || '未知错误'))
      }
    })
  }
}

// 关闭进度对话框
const handleCloseProgress = () => {
  store.stopPackagePolling()
  exportProgressVisible.value = false
}

// 违规跳转时的锚点时间（用于播放就绪后 seek）
const violationSeekTime = ref<number | null>(null)

// ==================== 审计日志 ====================
const auditLogVisible = ref(false)
const auditLogList = ref<MonitorAuditLog[]>([])
const auditLogTotal = ref(0)
const auditLogLoading = ref(false)
const auditLogQuery = ref<MonitorAuditLogQuery>({
  pageNum: 1,
  pageSize: 10,
})

const handleOpenAuditLog = () => {
  auditLogVisible.value = true
  fetchAuditLogs()
}

const fetchAuditLogs = async () => {
  auditLogLoading.value = true
  try {
    const res = await videoMonitorApi.getMonitorAuditLogs(auditLogQuery.value)
    if (res.code === 'SUCCESS' && res.data) {
      auditLogList.value = res.data.list || []
      auditLogTotal.value = res.data.total || 0
    }
  } catch {
    // silent
  } finally {
    auditLogLoading.value = false
  }
}

const handleAuditLogPageChange = (page: number) => {
  auditLogQuery.value.pageNum = page
  fetchAuditLogs()
}

// 审计日志模块名称映射
const moduleCodeNameMap: Record<string, string> = {
  device_monitor: '实时监控',
  device_recording: '录像回放',
  device_violation: '违规处理',
  device_clip: '片段截取',
  device_screenshot: '回放截图',
  device_evidence: '证据包',
}

// 审计日志结果映射
const resultMap: Record<string, { label: string; type: 'success' | 'danger' }> = {
  success: { label: '成功', type: 'success' },
  failure: { label: '失败', type: 'danger' },
}

// 处理从违规详情页跳转的 recordingId 参数（支持锚点时间定位）
const handleRecordingQuery = () => {
  const qRecordingId = route.query.recordingId
  if (!qRecordingId) return
  const id = Number(qRecordingId)
  const qOccurredAt = route.query.occurredAt as string | undefined

  // 清除 query 参数避免重复触发
  router.replace({ path: '/video-playback' })
  videoMonitorApi.getRecordingDetail(id).then(res => {
    if (res.code === 'SUCCESS' && res.data) {
      handlePlay(res.data)

      // 如果带违规发生时间，计算锚点 seek 时间
      if (qOccurredAt && res.data.startTime) {
        const occurredMs = new Date(qOccurredAt).getTime()
        const startMs = new Date(res.data.startTime).getTime()
        const anchorSeconds = Math.max(0, (occurredMs - startMs) / 1000 - 10)
        violationSeekTime.value = anchorSeconds
      }
    } else {
      ElMessage.warning('证据缺失：关联录像不存在')
    }
  }).catch(() => {
    ElMessage.warning('证据缺失：关联录像不存在或已被清理')
  })
}

// 初始化
onMounted(() => {
  // 优先处理 query 参数（从违规页跳转自动播放），不受其他初始化影响
  handleRecordingQuery()
  store.initRecordingPage()
  store.fetchCameraList()
  fetchStatistics()
})

// keep-alive 缓存页面通过 watch 监听路由 query 变化
watch(
  () => route.query.recordingId,
  (newVal) => {
    if (newVal) {
      handleRecordingQuery()
    }
  },
  { flush: 'sync' }
)
</script>

<template>
  <div class="video-playback-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon total">
          <el-icon><Calendar /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ recordingStatistics.totalCount }}</div>
          <div class="stat-label">录像总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon size">
          <el-icon><VideoPlay /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ formatFileSize(recordingStatistics.totalFileSize) }}</div>
          <div class="stat-label">总存储大小</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon alarm">
          <el-icon><Delete /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ recordingStatistics.alarmCount }}</div>
          <div class="stat-label">告警录像</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon ai">
          <el-icon><Search /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ recordingStatistics.aiMarkCount }}</div>
          <div class="stat-label">AI标记</div>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-date-picker
          v-model="filterForm.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
        />
        <el-select
          v-model="filterForm.deviceId"
          placeholder="选择摄像头"
          clearable
          filterable
          style="width: 200px"
        >
          <el-option
            v-for="camera in store.cameraList"
            :key="camera.id"
            :label="camera.deviceName"
            :value="camera.id"
          />
        </el-select>
        <el-select
          v-model="filterForm.recordingType"
          placeholder="录像类型"
          clearable
          style="width: 120px"
        >
          <el-option
            v-for="item in recordingTypeOptions"
            :key="String(item.value)"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>
      <div class="toolbar-right">
        <el-button :icon="Document" @click="handleOpenAuditLog">
          审计日志
        </el-button>
        <el-button :icon="VideoCamera" @click="router.push('/video-monitor')">
          返回视频监控
        </el-button>
        <el-button
          type="success"
          :icon="FolderAdd"
          :disabled="selectedRecordings.length === 0"
          v-permission="VIDEO_PLAYBACK_PERMISSIONS.EXPORT"
          @click="handleOpenExport"
        >
          导出证据包 ({{ selectedRecordings.length }})
        </el-button>
        <el-button
          type="danger"
          :icon="Delete"
          :disabled="selectedRecordings.length === 0"
          v-permission="VIDEO_PLAYBACK_PERMISSIONS.DELETE"
          @click="handleBatchDelete"
        >
          批量删除 ({{ selectedRecordings.length }})
        </el-button>
      </div>
    </div>

    <!-- 录像列表 -->
    <div class="recording-table">
      <el-table
        :data="store.recordingList"
        v-loading="store.recordingLoading"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="deviceName" label="摄像头名称" min-width="150" />
        <el-table-column prop="location" label="安装位置" min-width="120" />
        <el-table-column label="录像时间" min-width="200">
          <template #default="{ row }">
            <div class="time-range">
              <span>{{ formatDateTime(row.startTime) }}</span>
              <span class="time-separator">至</span>
              <span>{{ formatDateTime(row.endTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="durationFormat" label="时长" width="100" align="center" />
        <el-table-column prop="fileSizeFormat" label="文件大小" width="120" align="center" />
        <el-table-column prop="resolution" label="分辨率" width="120" align="center" />
        <el-table-column label="录像类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="recordingTypeTag(row.recordingType).type" size="small">
              {{ recordingTypeTag(row.recordingType).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI标记" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasAiMarks" type="success" size="small">有</el-tag>
            <el-tag v-else type="info" size="small">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="VideoPlay" @click="handlePlay(row)">
              播放
            </el-button>
            <el-button type="primary" link :icon="Download" v-permission="VIDEO_PLAYBACK_PERMISSIONS.DOWNLOAD" @click="handleDownload(row)">
              下载
            </el-button>
            <el-button type="danger" link :icon="Delete" v-permission="VIDEO_PLAYBACK_PERMISSIONS.DELETE" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        :current-page="store.recordingQuery.pageNum"
        :page-size="store.recordingQuery.pageSize"
        :total="store.recordingTotal"
        :page-sizes="[12, 24, 48, 96]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 播放对话框 -->
    <el-dialog
      v-model="playDialogVisible"
      :title="`视频回放 - ${currentRecording?.deviceName || ''}`"
      width="900px"
      :close-on-click-modal="false"
      @close="closePlayDialog"
    >
      <div v-if="playbackLoading" class="player-loading">
        <el-icon class="is-loading" :size="48"><Refresh /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else class="video-player-container">
        <VideoPlayer
          ref="playerRef"
          v-if="playbackUrl"
          :src="playbackUrl"
          type="native"
          :is-live="false"
          :autoplay="true"
          :show-controls="true"
          height="450px"
          @timeupdate="handleTimeUpdate"
          @ready="handlePlayerReady"
        />
        <div v-else class="no-video">
          <el-icon :size="64"><VideoPlay /></el-icon>
          <p>暂无视频源</p>
        </div>
      </div>

      <!-- 录像信息 -->
      <div v-if="currentRecording" class="recording-info">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="摄像头">{{ currentRecording.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ currentRecording.location }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ currentRecording.durationFormat }}</el-descriptions-item>
          <el-descriptions-item label="分辨率">{{ currentRecording.resolution }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(currentRecording.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatDateTime(currentRecording.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ currentRecording.fileSizeFormat }}</el-descriptions-item>
          <el-descriptions-item label="录像类型">
            <el-tag :type="recordingTypeTag(currentRecording.recordingType).type" size="small">
              {{ recordingTypeTag(currentRecording.recordingType).label }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 片段截取 -->
      <div v-if="currentRecording" class="clip-section">
        <el-divider content-position="left">片段截取</el-divider>
        <div class="clip-form">
          <div class="clip-form-row">
            <div class="clip-time-inputs">
              <div class="clip-time-field">
                <span class="clip-time-label">开始:</span>
                <el-input-number
                  v-model="clipForm.startTimeOffset"
                  :min="0"
                  :max="recordingDuration"
                  :step="1"
                  size="small"
                  style="width: 120px"
                />
                <span class="clip-time-display">{{ formatSeconds(clipForm.startTimeOffset) }}</span>
                <el-button size="small" @click="setStartFromPlayer">设为当前</el-button>
              </div>
              <div class="clip-time-field">
                <span class="clip-time-label">结束:</span>
                <el-input-number
                  v-model="clipForm.endTimeOffset"
                  :min="0"
                  :max="recordingDuration"
                  :step="1"
                  size="small"
                  style="width: 120px"
                />
                <span class="clip-time-display">{{ formatSeconds(clipForm.endTimeOffset) }}</span>
                <el-button size="small" @click="setEndFromPlayer">设为当前</el-button>
              </div>
            </div>
            <div class="clip-purpose-field">
              <el-select v-model="clipForm.purposeTag" size="small" style="width: 120px">
                <el-option
                  v-for="(tag, key) in CLIP_PURPOSE_TAGS"
                  :key="key"
                  :label="tag.name"
                  :value="key"
                />
              </el-select>
              <el-button
                type="primary"
                size="small"
                :loading="clipExtracting"
                v-permission="VIDEO_PLAYBACK_PERMISSIONS.CLIP_EXTRACT"
                @click="handleExtractClip"
              >
                提取片段
              </el-button>
              <el-button
                type="success"
                size="small"
                :icon="Camera"
                :loading="capturing"
                v-permission="VIDEO_PLAYBACK_PERMISSIONS.SCREENSHOT_CAPTURE"
                @click="handleCaptureScreenshot"
              >
                截图
              </el-button>
            </div>
          </div>
          <div class="clip-preview-text">
            截取范围: {{ formatSeconds(clipForm.startTimeOffset) }} - {{ formatSeconds(clipForm.endTimeOffset) }}
            （{{ formatSeconds(clipForm.endTimeOffset - clipForm.startTimeOffset) }}）
            | 当前播放位置: {{ formatSeconds(playerCurrentTime) }}
          </div>
        </div>

        <!-- 已截取片段列表 -->
        <div v-if="store.clipList.length > 0" class="clip-list">
          <el-table :data="store.clipList" size="small" max-height="200">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="时间范围" width="180">
              <template #default="{ row }">
                {{ formatSeconds(row.startTimeOffset) }} - {{ formatSeconds(row.endTimeOffset) }}
              </template>
            </el-table-column>
            <el-table-column prop="durationFormat" label="时长" width="80" align="center" />
            <el-table-column prop="fileSizeFormat" label="大小" width="80" align="center" />
            <el-table-column label="用途" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :color="CLIP_PURPOSE_TAGS[row.purposeTag as keyof typeof CLIP_PURPOSE_TAGS]?.color" style="color: #fff; border: none;">
                  {{ row.purposeTagName }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="CLIP_STATUS_MAP[row.status as keyof typeof CLIP_STATUS_MAP]?.type" size="small">
                  {{ row.statusName }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'completed' && row.downloadUrl"
                  type="primary"
                  link
                  size="small"
                  @click="handleDownloadClip(row)"
                >
                  下载
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  v-permission="VIDEO_PLAYBACK_PERMISSIONS.CLIP_DELETE"
                  @click="handleDeleteClip(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 截图列表 -->
        <div v-if="store.screenshotList.length > 0" class="screenshot-section">
          <el-divider content-position="left">回放截图</el-divider>
          <div class="screenshot-grid">
            <div
              v-for="item in store.screenshotList"
              :key="item.id"
              class="screenshot-card"
            >
              <el-image
                :src="item.previewUrl"
                :preview-src-list="[item.previewUrl]"
                fit="cover"
                class="screenshot-image"
              >
                <template #error>
                  <div class="screenshot-placeholder">
                    <el-icon :size="24"><Camera /></el-icon>
                  </div>
                </template>
              </el-image>
              <div class="screenshot-info">
                <span class="screenshot-time">{{ item.captureTimeFormat }}</span>
                <span class="screenshot-size">{{ item.fileSizeFormat }}</span>
              </div>
              <div class="screenshot-info screenshot-ai-info">
                <span>AI状态：{{ getAnalysisStatusText(item.latestAnalysis?.taskStatus) }}</span>
                <span v-if="item.latestAnalysis?.violationType">
                  {{ item.latestAnalysis.violationType }} {{ item.latestAnalysis.confidence }}%
                </span>
              </div>
              <div class="screenshot-actions">
                <el-button type="warning" link size="small" @click="handleAnalyzeScreenshot(item)">
                  AI分析
                </el-button>
                <el-button type="primary" link size="small" @click="handleDownloadScreenshot(item)">
                  下载
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  v-permission="VIDEO_PLAYBACK_PERMISSIONS.SCREENSHOT_DELETE"
                  @click="handleDeleteScreenshot(item)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button :icon="Download" :loading="downloading" v-permission="VIDEO_PLAYBACK_PERMISSIONS.DOWNLOAD" @click="currentRecording && handleDownload(currentRecording)">
            下载录像
          </el-button>
          <el-button @click="closePlayDialog">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 导出证据包确认对话框 -->
    <el-dialog
      v-model="exportDialogVisible"
      title="导出证据包"
      width="600px"
      :close-on-click-modal="false"
    >
      <div class="export-dialog-content">
        <el-form label-width="100px">
          <el-form-item label="证据包名称">
            <el-input
              v-model="exportPackageName"
              placeholder="留空则自动生成"
              maxlength="100"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="已选录像">
            <div class="export-selected-list">
              <span class="export-count">{{ selectedRecordings.length }} 个录像</span>
              <el-tag
                v-for="r in selectedRecordings.slice(0, 5)"
                :key="r.id"
                size="small"
                style="margin: 2px"
              >
                {{ r.deviceName }} ({{ formatDateTime(r.startTime) }})
              </el-tag>
              <span v-if="selectedRecordings.length > 5" class="export-more">
                ...等 {{ selectedRecordings.length }} 个
              </span>
            </div>
          </el-form-item>
          <el-form-item label="预估大小">
            <span>{{ formatFileSize(selectedRecordings.reduce((s, r) => s + (r.fileSize || 0), 0)) }}</span>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmExport">确认导出</el-button>
      </template>
    </el-dialog>

    <!-- 打包进度对话框 -->
    <el-dialog
      v-model="exportProgressVisible"
      title="证据包打包中"
      width="400px"
      :close-on-click-modal="false"
      :show-close="true"
      @close="handleCloseProgress"
    >
      <div class="export-progress-content">
        <template v-if="store.currentEvidencePackage?.status === 'packing'">
          <el-icon class="is-loading" :size="32"><Refresh /></el-icon>
          <p>正在打包中，请稍候...</p>
          <p class="progress-hint">证据包编号: {{ store.currentEvidencePackage?.packageNo }}</p>
        </template>
        <template v-else-if="store.currentEvidencePackage?.status === 'failed'">
          <el-icon :size="32" color="#F56C6C"><Delete /></el-icon>
          <p>打包失败</p>
          <p class="progress-hint">{{ store.currentEvidencePackage?.failReason }}</p>
          <el-button type="primary" @click="handleRetryPackage" style="margin-top: 12px">重试</el-button>
        </template>
      </div>
    </el-dialog>

    <!-- 审计日志对话框 -->
    <el-dialog
      v-model="auditLogVisible"
      title="监控审计日志"
      width="900px"
      append-to-body
    >
      <el-table :data="auditLogList" v-loading="auditLogLoading" stripe size="small" max-height="500">
        <el-table-column prop="userName" label="操作人" width="90" />
        <el-table-column prop="realName" label="姓名" width="80" />
        <el-table-column label="模块" width="90">
          <template #default="{ row }">
            {{ moduleCodeNameMap[row.moduleCode] || row.moduleName || row.moduleCode }}
          </template>
        </el-table-column>
        <el-table-column prop="operationType" label="操作类型" width="80" />
        <el-table-column prop="operationDesc" label="操作描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" width="60" align="center">
          <template #default="{ row }">
            <el-tag :type="(resultMap[row.result] || resultMap.success).type" size="small">
              {{ (resultMap[row.result] || resultMap.success).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP" width="120" show-overflow-tooltip />
        <el-table-column label="终端" width="60" align="center">
          <template #default="{ row }">
            {{ row.sourceTerminal || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
      <div style="display: flex; justify-content: center; margin-top: 16px">
        <el-pagination
          :current-page="auditLogQuery.pageNum"
          :page-size="auditLogQuery.pageSize"
          :total="auditLogTotal"
          layout="total, prev, pager, next"
          @current-change="handleAuditLogPageChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.video-playback-page {
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
    &.size { background: $success-color; }
    &.alarm { background: $danger-color; }
    &.ai { background: $warning-color; }
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

  .toolbar-right {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}

.recording-table {
  background: $bg-white;
  border-radius: 8px;
  box-shadow: $box-shadow-light;
  padding: 16px;
}

.time-range {
  display: flex;
  flex-direction: column;
  font-size: 13px;

  .time-separator {
    color: $text-secondary;
    font-size: 12px;
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

.player-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 450px;
  color: $text-secondary;
  gap: 12px;
}

.video-player-container {
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.no-video {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 450px;
  color: $text-secondary;
  gap: 12px;
}

.recording-info {
  margin-top: 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.clip-section {
  margin-top: 16px;
}

.clip-form {
  background: $bg-base;
  border-radius: 6px;
  padding: 12px;
}

.clip-form-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.clip-time-inputs {
  display: flex;
  gap: 16px;
}

.clip-time-field {
  display: flex;
  align-items: center;
  gap: 6px;
}

.clip-time-label {
  font-size: 13px;
  color: $text-secondary;
  white-space: nowrap;
}

.clip-time-display {
  font-size: 12px;
  color: $text-secondary;
  min-width: 55px;
  font-family: monospace;
}

.clip-purpose-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.clip-preview-text {
  margin-top: 8px;
  font-size: 12px;
  color: $text-secondary;
}

.clip-list {
  margin-top: 12px;
}

.screenshot-section {
  margin-top: 8px;
}

.screenshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.screenshot-card {
  background: $bg-base;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }
}

.screenshot-image {
  width: 100%;
  height: 120px;
  display: block;
}

.screenshot-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 120px;
  background: #f5f7fa;
  color: #c0c4cc;
}

.screenshot-info {
  padding: 6px 8px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: $text-secondary;
}

.screenshot-actions {
  padding: 0 8px 6px;
  display: flex;
  gap: 4px;
}

// 证据包导出
.export-dialog-content {
  .export-selected-list {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
  }

  .export-count {
    font-weight: 600;
    color: $text-primary;
    margin-right: 8px;
  }

  .export-more {
    font-size: 12px;
    color: $text-secondary;
  }
}

.export-progress-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  gap: 12px;

  p {
    font-size: 14px;
    color: $text-primary;
    margin: 0;
  }

  .progress-hint {
    font-size: 12px;
    color: $text-secondary;
  }
}
</style>
