import { defineStore } from 'pinia'
import { ref } from 'vue'
import { videoMonitorApi } from '@/api/modules/video-monitor'
import { useDeviceStore } from '@/stores/modules/device'
import type {
  Camera,
  MonitorQuery,
  MonitorStatistics,
  DeviceStatusEvent,
  VideoRecording,
  RecordingQuery,
  ViolationEvent,
  ViolationQuery,
  ViolationStatistics,
  ViolationHandleForm,
  BehaviorAnalysis,
  BehaviorAnalysisDetail,
  BehaviorQuery,
  BehaviorStatistics,
  PTZDirection,
  VideoClip,
  ClipExtractRequest,
  ClipQuery,
  Screenshot,
  ScreenshotQuery,
  DeviceVisionAnalysisTask,
  EvidencePackage,
} from '@/types/video-monitor'

export const useVideoMonitorStore = defineStore('videoMonitor', () => {
  // ==================== 实时监控 ====================
  const cameraList = ref<Camera[]>([])
  const cameraTotal = ref(0)
  const cameraLoading = ref(false)
  const monitorQuery = ref<MonitorQuery>({
    pageNum: 1,
    pageSize: 12,
  })

  // 监控统计数据
  const monitorStatistics = ref<MonitorStatistics>({
    totalCameras: 0,
    onlineCameras: 0,
    offlineCameras: 0,
    alertCameras: 0,
  })

  /** 当前选中的摄像头 */
  const selectedCamera = ref<Camera | null>(null)
  /** 布局模式: 1/4/9 画面 */
  const layoutMode = ref<1 | 4 | 9>(4)
  /** 全屏模式 */
  const fullscreenMode = ref(false)

  // ==================== AI违规识别 ====================
  const violationList = ref<ViolationEvent[]>([])
  const violationTotal = ref(0)
  const violationLoading = ref(false)
  const violationQuery = ref<ViolationQuery>({
    pageNum: 1,
    pageSize: 10,
  })

  // 违规详情
  const currentViolationDetail = ref<ViolationEvent | null>(null)
  const violationDetailLoading = ref(false)

  // ==================== AI人员分析 ====================
  const behaviorList = ref<BehaviorAnalysis[]>([])
  const behaviorTotal = ref(0)
  const behaviorLoading = ref(false)
  const behaviorStatistics = ref<BehaviorStatistics>({
    totalEmployees: 0,
    averageEfficiency: 0,
    averageCompliance: 0,
    averageHygiene: 0,
    needImprovementCount: 0,
    benchmarkCount: 0,
    todayAnalysisCount: 0,
    issueCount: 0,
  })
  const behaviorQuery = ref<BehaviorQuery>({
    pageNum: 1,
    pageSize: 10,
  })

  // 行为分析详情
  const currentBehaviorDetail = ref<BehaviorAnalysisDetail | null>(null)
  const behaviorDetailLoading = ref(false)

  // ==================== 视频回放 ====================
  const recordingList = ref<VideoRecording[]>([])
  const recordingTotal = ref(0)
  const recordingLoading = ref(false)
  const recordingQuery = ref<RecordingQuery>({
    pageNum: 1,
    pageSize: 12,
  })

  // ==================== 视频片段截取 ====================
  const clipList = ref<VideoClip[]>([])
  const clipTotal = ref(0)
  const clipLoading = ref(false)
  const clipQuery = ref<ClipQuery>({ pageNum: 1, pageSize: 20 })
  let clipPollingTimer: ReturnType<typeof setInterval> | null = null

  // ==================== 回放截图 ====================
  const screenshotList = ref<Screenshot[]>([])
  const screenshotTotal = ref(0)
  const screenshotLoading = ref(false)

  // ==================== SSE 实时推送 ====================
  let sseConnection: EventSource | null = null
  const sseConnected = ref(false)
  const latestViolationAlert = ref<ViolationEvent | null>(null)
  const violationAlertQueue = ref<ViolationEvent[]>([])
  const latestDeviceStatus = ref<DeviceStatusEvent | null>(null)

  // ==================== 证据包导出 ====================
  const evidencePackageLoading = ref(false)
  const currentEvidencePackage = ref<EvidencePackage | null>(null)
  let packagePollingTimer: ReturnType<typeof setInterval> | null = null

  // ==================== 计算属性 ====================
  /** 违规统计数据（来自专用 API） */
  const violationStatistics = ref<ViolationStatistics>({
    totalCount: 0,
    pendingCount: 0,
    urgentCount: 0,
    resolvedCount: 0,
    todayCount: 0,
  })

  // ==================== 监控相关方法 ====================
  const fetchCameraList = async () => {
    cameraLoading.value = true
    try {
      const res = await videoMonitorApi.getRealtimeMonitors(monitorQuery.value)
      if (res.code === 'SUCCESS' && res.data) {
        cameraList.value = res.data.list
        cameraTotal.value = res.data.total

        // 更新统计数据
        monitorStatistics.value = {
          totalCameras: res.data.total,
          onlineCameras: res.data.list.filter(c => c.onlineStatus === 'online').length,
          offlineCameras: res.data.list.filter(c => c.onlineStatus === 'offline').length,
          alertCameras: res.data.list.filter(c => c.alertCount > 0).length,
        }
      }
    } catch (e) {
      console.error('获取摄像头列表失败', e)
    } finally {
      cameraLoading.value = false
    }
  }

  const fetchMonitorStatistics = async (orgId?: number) => {
    try {
      const res = await videoMonitorApi.getMonitorStatistics(orgId)
      if (res.code === 'SUCCESS' && res.data) {
        monitorStatistics.value = res.data
      }
    } catch (e) {
      console.error('获取监控统计失败', e)
    }
  }

  const searchCameras = async (params: Partial<MonitorQuery>) => {
    monitorQuery.value = { pageNum: 1, pageSize: monitorQuery.value.pageSize, ...params }
    await fetchCameraList()
  }

  const changeCameraPage = async (page: number) => {
    monitorQuery.value.pageNum = page
    await fetchCameraList()
  }

  const changeLayoutMode = (mode: 1 | 4 | 9) => {
    layoutMode.value = mode
    monitorQuery.value.pageSize = mode
    monitorQuery.value.pageNum = 1
    fetchCameraList()
  }

  const selectCamera = (camera: Camera | null) => {
    selectedCamera.value = camera
  }

  const toggleFullscreen = () => {
    fullscreenMode.value = !fullscreenMode.value
  }

  /** 云台控制 */
  const ptzControl = async (deviceId: number, direction: PTZDirection, speed = 5) => {
    try {
      const res = await videoMonitorApi.ptzControl({ deviceId, direction, speed })
      return res.code === 'SUCCESS'
    } catch (e) {
      console.error('云台控制失败', e)
      return false
    }
  }

  // ==================== 违规相关方法 ====================
  const fetchViolationList = async () => {
    violationLoading.value = true
    try {
      const res = await videoMonitorApi.getViolations(violationQuery.value)
      if (res.code === 'SUCCESS' && res.data) {
        violationList.value = res.data.list
        violationTotal.value = res.data.total
      }
    } catch (e) {
      console.error('获取违规列表失败', e)
    } finally {
      violationLoading.value = false
    }
  }

  const fetchViolationStatistics = async () => {
    try {
      const res = await videoMonitorApi.getViolationStatistics()
      if (res.code === 'SUCCESS' && res.data) {
        violationStatistics.value = res.data
      }
    } catch (e) {
      console.error('获取违规统计失败', e)
    }
  }

  const searchViolations = async (params: Partial<ViolationQuery>) => {
    violationQuery.value = { pageNum: 1, pageSize: violationQuery.value.pageSize, ...params }
    await fetchViolationList()
  }

  const changeViolationPage = async (page: number) => {
    violationQuery.value.pageNum = page
    await fetchViolationList()
  }

  const fetchViolationDetail = async (id: number) => {
    violationDetailLoading.value = true
    try {
      const res = await videoMonitorApi.getViolationDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentViolationDetail.value = res.data
      }
    } catch (e) {
      console.error('获取违规详情失败', e)
    } finally {
      violationDetailLoading.value = false
    }
  }

  const handleViolation = async (id: number, data: ViolationHandleForm) => {
    try {
      const res = await videoMonitorApi.handleViolation(id, data)
      if (res.code === 'SUCCESS') {
        await fetchViolationList()
        return true
      }
      return false
    } catch (e) {
      console.error('处理违规失败', e)
      return false
    }
  }

  /** 复核违规事件 */
  const reviewViolation = async (id: number, data: { reviewStatus: 'approved' | 'rejected'; reviewRemark?: string }) => {
    try {
      const res = await videoMonitorApi.reviewViolation(id, data)
      if (res.code === 'SUCCESS') {
        await fetchViolationList()
        return true
      }
      return false
    } catch (e) {
      console.error('复核违规失败', e)
      return false
    }
  }

  /** 批量处理违规 */
  const batchHandleViolations = async (ids: number[], data: ViolationHandleForm) => {
    try {
      const res = await videoMonitorApi.batchHandleViolations(ids, data)
      if (res.code === 'SUCCESS') {
        await fetchViolationList()
        return true
      }
      return false
    } catch (e) {
      console.error('批量处理违规失败', e)
      return false
    }
  }

  // ==================== 人员分析相关方法 ====================
  const fetchBehaviorList = async () => {
    behaviorLoading.value = true
    try {
      const res = await videoMonitorApi.getBehaviorAnalysis(behaviorQuery.value)
      if (res.code === 'SUCCESS' && res.data) {
        behaviorList.value = res.data.list
        behaviorTotal.value = res.data.total
      }
    } catch (e) {
      console.error('获取人员分析列表失败', e)
    } finally {
      behaviorLoading.value = false
    }
  }

  const fetchBehaviorStatistics = async () => {
    try {
      const res = await videoMonitorApi.getBehaviorStatistics(behaviorQuery.value.orgId)
      if (res.code === 'SUCCESS' && res.data) {
        behaviorStatistics.value = res.data
      }
    } catch (e) {
      console.error('获取人员分析统计失败', e)
    }
  }

  const searchBehavior = async (params: Partial<BehaviorQuery>) => {
    behaviorQuery.value = { ...behaviorQuery.value, ...params, pageNum: 1 }
    await fetchBehaviorList()
  }

  const changeBehaviorPage = async (page: number) => {
    behaviorQuery.value.pageNum = page
    await fetchBehaviorList()
  }

  const fetchBehaviorDetail = async (id: number) => {
    behaviorDetailLoading.value = true
    try {
      const res = await videoMonitorApi.getBehaviorDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentBehaviorDetail.value = res.data
      }
    } catch (e) {
      console.error('获取行为分析详情失败', e)
    } finally {
      behaviorDetailLoading.value = false
    }
  }

  // ==================== 录像回放相关方法 ====================
  const fetchRecordingList = async () => {
    recordingLoading.value = true
    try {
      const res = await videoMonitorApi.getRecordings(recordingQuery.value)
      if (res.code === 'SUCCESS' && res.data) {
        recordingList.value = res.data.list
        recordingTotal.value = res.data.total
      }
    } catch (e) {
      console.error('获取录像列表失败', e)
    } finally {
      recordingLoading.value = false
    }
  }

  const searchRecordings = async (params: Partial<RecordingQuery>) => {
    recordingQuery.value = { pageNum: 1, pageSize: 12, ...params }
    await fetchRecordingList()
  }

  const changeRecordingPage = async (page: number) => {
    recordingQuery.value.pageNum = page
    await fetchRecordingList()
  }

  const getPlaybackUrl = async (recordingId: number): Promise<string | null> => {
    try {
      const res = await videoMonitorApi.getPlaybackUrl(recordingId)
      if (res.code === 'SUCCESS' && res.data) {
        return res.data.playbackUrl
      }
      return null
    } catch (e) {
      console.error('获取播放地址失败', e)
      return null
    }
  }

  const deleteRecording = async (id: number): Promise<boolean> => {
    try {
      const res = await videoMonitorApi.deleteRecording(id)
      if (res.code === 'SUCCESS') {
        await fetchRecordingList()
        return true
      }
      return false
    } catch (e) {
      console.error('删除录像失败', e)
      return false
    }
  }

  // ==================== 片段截取相关方法 ====================
  const fetchClipList = async (recordingId?: number) => {
    clipLoading.value = true
    try {
      const params: ClipQuery = { ...clipQuery.value }
      if (recordingId) params.recordingId = recordingId
      const res = await videoMonitorApi.getClipList(params)
      if (res.code === 'SUCCESS' && res.data) {
        clipList.value = res.data.list
        clipTotal.value = res.data.total
      }
    } catch (e) {
      console.error('获取片段列表失败', e)
    } finally {
      clipLoading.value = false
    }
  }

  const extractClip = async (data: ClipExtractRequest): Promise<VideoClip | null> => {
    try {
      const res = await videoMonitorApi.extractClip(data)
      if (res.code === 'SUCCESS' && res.data) {
        // 刷新片段列表
        await fetchClipList(data.recordingId)
        return res.data
      }
      return null
    } catch (e) {
      console.error('提取片段失败', e)
      return null
    }
  }

  const getClipDetail = async (id: number): Promise<VideoClip | null> => {
    try {
      const res = await videoMonitorApi.getClipDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        return res.data
      }
      return null
    } catch (e) {
      console.error('获取片段详情失败', e)
      return null
    }
  }

  /** 开始轮询片段状态（2秒间隔，最多60秒） */
  const startClipPolling = (clipId: number, recordingId: number, onDone?: (clip: VideoClip) => void) => {
    stopClipPolling()
    let attempts = 0
    const maxAttempts = 30 // 2s * 30 = 60s

    clipPollingTimer = setInterval(async () => {
      attempts++
      const clip = await getClipDetail(clipId)
      if (clip && (clip.status === 'completed' || clip.status === 'failed')) {
        stopClipPolling()
        await fetchClipList(recordingId)
        onDone?.(clip)
      }
      if (attempts >= maxAttempts) {
        stopClipPolling()
      }
    }, 2000)
  }

  const stopClipPolling = () => {
    if (clipPollingTimer) {
      clearInterval(clipPollingTimer)
      clipPollingTimer = null
    }
  }

  const deleteClip = async (id: number, recordingId?: number): Promise<boolean> => {
    try {
      const res = await videoMonitorApi.deleteClip(id)
      if (res.code === 'SUCCESS') {
        await fetchClipList(recordingId)
        return true
      }
      return false
    } catch (e) {
      console.error('删除片段失败', e)
      return false
    }
  }

  // ==================== 截图相关方法 ====================
  const fetchScreenshotList = async (recordingId?: number) => {
    screenshotLoading.value = true
    try {
      const params: ScreenshotQuery = {}
      if (recordingId) params.recordingId = recordingId
      const res = await videoMonitorApi.getScreenshotList(params)
      if (res.code === 'SUCCESS' && res.data) {
        screenshotList.value = res.data.list
        screenshotTotal.value = res.data.total
      }
    } catch (e) {
      console.error('获取截图列表失败', e)
    } finally {
      screenshotLoading.value = false
    }
  }

  const uploadScreenshot = async (file: File, data: { recordingId: number; captureTimeOffset: number; purposeTag: string }): Promise<Screenshot | null> => {
    try {
      const res = await videoMonitorApi.uploadScreenshot(file, data)
      if (res.code === 'SUCCESS' && res.data) {
        await fetchScreenshotList(data.recordingId)
        return res.data
      }
      return null
    } catch (e) {
      console.error('上传截图失败', e)
      return null
    }
  }

  const deleteScreenshot = async (id: number, recordingId?: number): Promise<boolean> => {
    try {
      const res = await videoMonitorApi.deleteScreenshot(id)
      if (res.code === 'SUCCESS') {
        await fetchScreenshotList(recordingId)
        return true
      }
      return false
    } catch (e) {
      console.error('删除截图失败', e)
      return false
    }
  }

  const analyzeScreenshot = async (id: number, recordingId?: number): Promise<DeviceVisionAnalysisTask | null> => {
    try {
      const res = await videoMonitorApi.analyzeScreenshot(id)
      if (res.code === 'SUCCESS' && res.data) {
        await fetchScreenshotList(recordingId)
        await fetchViolationList()
        await fetchViolationStatistics()
        return res.data
      }
      return null
    } catch (e) {
      console.error('分析截图失败', e)
      return null
    }
  }

  // ==================== 证据包导出方法 ====================
  const createEvidencePackage = async (data: {
    recordingIds?: number[]
    clipIds?: number[]
    screenshotIds?: number[]
    packageName?: string
    orgId?: number
  }): Promise<EvidencePackage | null> => {
    evidencePackageLoading.value = true
    try {
      const res = await videoMonitorApi.createEvidencePackage(data)
      if (res.code === 'SUCCESS' && res.data) {
        currentEvidencePackage.value = res.data
        return res.data
      }
      return null
    } catch (e) {
      console.error('创建证据包失败', e)
      return null
    } finally {
      evidencePackageLoading.value = false
    }
  }

  const getEvidencePackageStatus = async (id: number): Promise<EvidencePackage | null> => {
    try {
      const res = await videoMonitorApi.getEvidencePackageStatus(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentEvidencePackage.value = res.data
        return res.data
      }
      return null
    } catch (e) {
      console.error('查询证据包状态失败', e)
      return null
    }
  }

  /** 开始轮询打包状态（2秒间隔，最多120秒） */
  const startPackagePolling = (packageId: number, onDone?: (pkg: EvidencePackage) => void) => {
    stopPackagePolling()
    let attempts = 0
    const maxAttempts = 60 // 2s * 60 = 120s

    packagePollingTimer = setInterval(async () => {
      attempts++
      const pkg = await getEvidencePackageStatus(packageId)
      if (pkg && (pkg.status === 'completed' || pkg.status === 'failed')) {
        stopPackagePolling()
        onDone?.(pkg)
      }
      if (attempts >= maxAttempts) {
        stopPackagePolling()
      }
    }, 2000)
  }

  const stopPackagePolling = () => {
    if (packagePollingTimer) {
      clearInterval(packagePollingTimer)
      packagePollingTimer = null
    }
  }

  const retryEvidencePackage = async (id: number): Promise<EvidencePackage | null> => {
    try {
      const res = await videoMonitorApi.retryEvidencePackage(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentEvidencePackage.value = res.data
        return res.data
      }
      return null
    } catch (e) {
      console.error('重试证据包失败', e)
      return null
    }
  }

  // ==================== 初始化方法 ====================
  const initMonitorPage = () => {
    return Promise.all([
      fetchCameraList(),
      fetchMonitorStatistics(),
    ])
  }

  const initViolationPage = () => {
    return Promise.all([
      fetchViolationList(),
      fetchViolationStatistics(),
    ])
  }

  const initBehaviorPage = () => {
    return Promise.all([
      fetchBehaviorList(),
      fetchBehaviorStatistics(),
    ])
  }

  const initRecordingPage = () => {
    return fetchRecordingList()
  }

  // ==================== SSE 方法 ====================
  const connectSSE = () => {
    if (sseConnection) return
    sseConnection = videoMonitorApi.createViolationSSE(
      (violation) => {
        latestViolationAlert.value = violation
        violationAlertQueue.value.unshift(violation)
        // 保留最近20条
        if (violationAlertQueue.value.length > 20) {
          violationAlertQueue.value = violationAlertQueue.value.slice(0, 20)
        }
      },
      (statusEvent) => {
        // 设备在线状态变更 → 更新本地摄像头列表
        handleDeviceStatusChange(statusEvent)
      },
    )
    if (sseConnection) {
      sseConnected.value = true
      sseConnection.addEventListener('error', () => {
        sseConnected.value = false
      })
    }
  }

  const disconnectSSE = () => {
    if (sseConnection) {
      sseConnection.close()
      sseConnection = null
      sseConnected.value = false
    }
  }

  const clearViolationAlert = () => {
    latestViolationAlert.value = null
  }

  /** SSE 设备状态变更：实时更新本地摄像头列表 + 同步设备管理 store */
  const handleDeviceStatusChange = (event: DeviceStatusEvent) => {
    latestDeviceStatus.value = event

    const camera = cameraList.value.find(c => c.id === event.deviceId)
    if (!camera) return

    const oldStatus = camera.onlineStatus
    camera.onlineStatus = event.newStatus as 'online' | 'offline'

    // 更新统计数据
    const wasOnline = oldStatus === 'online'
    const isOnline = event.newStatus === 'online'
    if (wasOnline && !isOnline) {
      monitorStatistics.value.onlineCameras--
      monitorStatistics.value.offlineCameras++
    } else if (!wasOnline && isOnline) {
      monitorStatistics.value.onlineCameras++
      monitorStatistics.value.offlineCameras--
    }

    // 同步更新设备管理 store 中的对应设备在线状态
    const deviceStore = useDeviceStore()
    const device = deviceStore.list.find(d => d.id === event.deviceId)
    if (device && device.onlineStatus !== event.newStatus) {
      device.onlineStatus = event.newStatus
      device.onlineStatusName = event.newStatus === 'online' ? '在线' : '离线'
    }

    console.info(`[SSE] 摄像头状态变更: ${camera.deviceName} ${oldStatus} → ${event.newStatus}`)
  }

  /** 手动重连：调用 restart-transcode API */
  const reconnectCamera = async (deviceId: number): Promise<{ success: boolean; message: string; hlsUrl?: string }> => {
    try {
      const res = await videoMonitorApi.restartTranscode(deviceId)
      if (res.code === 'SUCCESS') {
        const hlsUrl = res.data?.hlsUrl
        const camera = cameraList.value.find(c => c.id === deviceId)
        if (camera && hlsUrl) {
          camera.hlsUrl = hlsUrl
          camera.streamUrl = hlsUrl
        }
        if (selectedCamera.value?.id === deviceId && hlsUrl) {
          selectedCamera.value.hlsUrl = hlsUrl
          selectedCamera.value.streamUrl = hlsUrl
        }
        return { success: true, message: '', hlsUrl }
      }
      return { success: false, message: res.message || '重连失败' }
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || '重连请求失败'
      return { success: false, message: msg }
    }
  }

  return {
    // 实时监控
    cameraList,
    cameraTotal,
    cameraLoading,
    monitorStatistics,
    monitorQuery,
    selectedCamera,
    layoutMode,
    fullscreenMode,
    fetchCameraList,
    fetchMonitorStatistics,
    searchCameras,
    changeCameraPage,
    changeLayoutMode,
    selectCamera,
    toggleFullscreen,
    ptzControl,

    // AI违规
    violationList,
    violationTotal,
    violationLoading,
    violationStatistics,
    violationQuery,
    currentViolationDetail,
    violationDetailLoading,
    fetchViolationList,
    fetchViolationStatistics,
    searchViolations,
    changeViolationPage,
    fetchViolationDetail,
    handleViolation,
    reviewViolation,
    batchHandleViolations,

    // AI人员分析
    behaviorList,
    behaviorTotal,
    behaviorLoading,
    behaviorStatistics,
    behaviorQuery,
    currentBehaviorDetail,
    behaviorDetailLoading,
    fetchBehaviorList,
    fetchBehaviorStatistics,
    searchBehavior,
    changeBehaviorPage,
    fetchBehaviorDetail,

    // 视频回放
    recordingList,
    recordingTotal,
    recordingLoading,
    recordingQuery,
    fetchRecordingList,
    searchRecordings,
    changeRecordingPage,
    getPlaybackUrl,
    deleteRecording,

    // 视频片段截取
    clipList,
    clipTotal,
    clipLoading,
    clipQuery,
    fetchClipList,
    extractClip,
    getClipDetail,
    startClipPolling,
    stopClipPolling,
    deleteClip,

    // 回放截图
    screenshotList,
    screenshotTotal,
    screenshotLoading,
    fetchScreenshotList,
    uploadScreenshot,
    deleteScreenshot,
    analyzeScreenshot,

    // 初始化
    initMonitorPage,
    initViolationPage,
    initBehaviorPage,
    initRecordingPage,

    // SSE
    sseConnected,
    latestViolationAlert,
    violationAlertQueue,
    latestDeviceStatus,
    connectSSE,
    disconnectSSE,
    clearViolationAlert,
    reconnectCamera,

    // 证据包导出
    evidencePackageLoading,
    currentEvidencePackage,
    createEvidencePackage,
    getEvidencePackageStatus,
    startPackagePolling,
    stopPackagePolling,
    retryEvidencePackage,
  }
})
