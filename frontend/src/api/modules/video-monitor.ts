import { get, post, del } from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  Camera,
  MonitorQuery,
  MonitorStatistics,
  DeviceStatusEvent,
  VideoRecording,
  RecordingQuery,
  RecordingStatistics,
  ViolationEvent,
  ViolationQuery,
  ViolationHandleForm,
  ViolationStatistics,
  BehaviorAnalysis,
  BehaviorAnalysisDetail,
  BehaviorQuery,
  BehaviorStatistics,
  PTZControlParams,
  VideoClip,
  ClipExtractRequest,
  ClipQuery,
  Screenshot,
  ScreenshotQuery,
  DeviceVisionAnalysisTask,
  EvidencePackage,
  MonitorAuditLog,
  MonitorAuditLogQuery,
} from '@/types/video-monitor'

const BASE = '/v1/device'

export const videoMonitorApi = {
  // ==================== 实时监控 ====================

  /**
   * 获取实时监控列表
   */
  getRealtimeMonitors(params: MonitorQuery): Promise<ApiResponse<PageResponse<Camera>>> {
    return get(`${BASE}/monitors/realtime`, params)
  },

  /**
   * 获取监控统计数据
   */
  getMonitorStatistics(orgId?: number): Promise<ApiResponse<MonitorStatistics>> {
    return get(`${BASE}/monitors/statistics`, { orgId })
  },

  /**
   * 获取摄像头详情
   */
  getCameraDetail(id: number): Promise<ApiResponse<Camera>> {
    return get(`${BASE}/cameras/${id}`)
  },

  /**
   * 云台控制
   */
  ptzControl(params: PTZControlParams): Promise<ApiResponse<void>> {
    return post(`${BASE}/cameras/${params.deviceId}/ptz`, {
      direction: params.direction,
      speed: params.speed,
    })
  },

  /**
   * 重启摄像头转码（重连）
   */
  restartTranscode(deviceId: number): Promise<ApiResponse<{ hlsUrl: string }>> {
    return post(`${BASE}/monitors/cameras/${deviceId}/restart-transcode`)
  },

  // ==================== 视频回放 ====================

  /**
   * 获取视频录像列表
   */
  getRecordings(params: RecordingQuery): Promise<ApiResponse<PageResponse<VideoRecording>>> {
    return get(`${BASE}/recordings`, params)
  },

  /**
   * 获取录像统计（所有符合筛选条件的数据汇总）
   */
  getRecordingStatistics(params: Partial<RecordingQuery>): Promise<ApiResponse<RecordingStatistics>> {
    return get(`${BASE}/recordings/statistics`, params)
  },

  /**
   * 获取录像详情
   */
  getRecordingDetail(id: number): Promise<ApiResponse<VideoRecording>> {
    return get(`${BASE}/recordings/${id}`)
  },

  /**
   * 获取视频回放地址
   */
  getPlaybackUrl(recordingId: number): Promise<ApiResponse<{ playbackUrl: string }>> {
    return get(`${BASE}/recordings/${recordingId}/playback`)
  },

  /**
   * 删除录像
   */
  deleteRecording(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/recordings/${id}`)
  },

  // ==================== AI违规识别 ====================

  /**
   * 获取违规事件列表
   */
  getViolations(params: ViolationQuery): Promise<ApiResponse<PageResponse<ViolationEvent>>> {
    return get(`${BASE}/violations`, params)
  },

  /**
   * 获取违规事件详情
   */
  getViolationDetail(id: number): Promise<ApiResponse<ViolationEvent>> {
    return get(`${BASE}/violations/${id}`)
  },

  /**
   * 处理违规事件
   */
  handleViolation(id: number, data: ViolationHandleForm): Promise<ApiResponse<void>> {
    return post(`${BASE}/violations/${id}/handle`, data)
  },

  /**
   * 批量处理违规事件
   */
  batchHandleViolations(ids: number[], data: ViolationHandleForm): Promise<ApiResponse<void>> {
    return post(`${BASE}/violations/batch-handle`, { ids, ...data })
  },

  /**
   * 复核违规事件
   */
  reviewViolation(id: number, data: { reviewStatus: 'approved' | 'rejected'; reviewRemark?: string }): Promise<ApiResponse<void>> {
    return post(`${BASE}/violations/${id}/review`, data)
  },

  /**
   * 获取违规统计
   */
  getViolationStatistics(orgId?: number): Promise<ApiResponse<ViolationStatistics>> {
    return get(`${BASE}/violations/statistics`, { orgId })
  },

  /**
   * 获取违规事件操作日志
   */
  getViolationLogs(id: number): Promise<ApiResponse<any[]>> {
    return get(`${BASE}/violations/${id}/logs`)
  },

  // ==================== AI人员行为分析 ====================

  /**
   * 获取人员行为分析列表
   */
  getBehaviorAnalysis(params: BehaviorQuery): Promise<ApiResponse<PageResponse<BehaviorAnalysis>>> {
    return get(`${BASE}/behavior-analysis`, params)
  },

  /**
   * 获取行为分析详情（按分析记录ID）
   */
  getBehaviorDetail(id: number): Promise<ApiResponse<BehaviorAnalysisDetail>> {
    return get(`${BASE}/behavior-analysis/${id}`)
  },

  /**
   * 获取员工行为分析详情（按员工ID）
   */
  getEmployeeBehaviorDetail(employeeId: number): Promise<ApiResponse<BehaviorAnalysisDetail>> {
    return get(`${BASE}/behavior-analysis/employee/${employeeId}`)
  },

  /**
   * 获取人员行为统计数据
   */
  getBehaviorStatistics(orgId?: number): Promise<ApiResponse<BehaviorStatistics>> {
    return get(`${BASE}/behavior-analysis/statistics`, { orgId })
  },

  // ==================== 视频片段截取 ====================

  /**
   * 提取视频片段
   */
  extractClip(data: ClipExtractRequest): Promise<ApiResponse<VideoClip>> {
    return post(`${BASE}/clips/extract`, data)
  },

  /**
   * 获取片段列表
   */
  getClipList(params: ClipQuery): Promise<ApiResponse<PageResponse<VideoClip>>> {
    return get(`${BASE}/clips`, params)
  },

  /**
   * 获取片段详情（用于轮询状态）
   */
  getClipDetail(id: number): Promise<ApiResponse<VideoClip>> {
    return get(`${BASE}/clips/${id}`)
  },

  /**
   * 删除片段
   */
  deleteClip(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/clips/${id}`)
  },

  // ==================== 回放截图 ====================

  /**
   * 上传截图
   */
  uploadScreenshot(file: File, data: { recordingId: number; captureTimeOffset: number; purposeTag: string }): Promise<ApiResponse<Screenshot>> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('recordingId', String(data.recordingId))
    formData.append('captureTimeOffset', String(data.captureTimeOffset))
    formData.append('purposeTag', data.purposeTag)
    return post(`${BASE}/screenshots/upload`, formData)
  },

  /**
   * 获取截图列表
   */
  getScreenshotList(params: ScreenshotQuery): Promise<ApiResponse<PageResponse<Screenshot>>> {
    return get(`${BASE}/screenshots`, params)
  },

  analyzeScreenshot(id: number): Promise<ApiResponse<DeviceVisionAnalysisTask>> {
    return post(`${BASE}/screenshots/${id}/analyze`)
  },

  getLatestScreenshotAnalysis(id: number): Promise<ApiResponse<DeviceVisionAnalysisTask>> {
    return get(`${BASE}/screenshots/${id}/analysis/latest`)
  },

  /**
   * 删除截图
   */
  deleteScreenshot(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/screenshots/${id}`)
  },

  // ==================== SSE 实时推送 ====================

  /**
   * 创建SSE连接，监听违规事件和设备状态变更事件
   */
  createViolationSSE(
    onViolation: (event: ViolationEvent) => void,
    onDeviceStatus?: (event: DeviceStatusEvent) => void,
  ): EventSource | null {
    const baseURL = '/api' + BASE + '/events/stream'
    try {
      const eventSource = new EventSource(baseURL)

      eventSource.addEventListener('violation', (e) => {
        try {
          const data = JSON.parse(e.data) as ViolationEvent
          onViolation(data)
        } catch (err) {
          console.error('解析SSE违规事件失败', err)
        }
      })

      eventSource.addEventListener('device_status', (e) => {
        try {
          const data = JSON.parse(e.data) as DeviceStatusEvent
          onDeviceStatus?.(data)
        } catch (err) {
          console.error('解析SSE设备状态事件失败', err)
        }
      })

      eventSource.addEventListener('heartbeat', () => {
        // 心跳，无需处理
      })

      eventSource.onerror = () => {
        console.warn('SSE连接断开，将自动重连')
      }

      return eventSource
    } catch (e) {
      console.error('创建SSE连接失败', e)
      return null
    }
  },

  /**
   * 测试触发一条模拟违规事件
   */
  testTriggerViolation(): Promise<ApiResponse<ViolationEvent>> {
    return post(`${BASE}/violations/test-trigger`)
  },

  // ==================== 证据包导出 ====================

  /**
   * 创建证据包（异步打包）
   */
  createEvidencePackage(data: {
    recordingIds?: number[]
    clipIds?: number[]
    screenshotIds?: number[]
    packageName?: string
    orgId?: number
  }): Promise<ApiResponse<EvidencePackage>> {
    return post(`${BASE}/evidence-packages`, data)
  },

  /**
   * 查询证据包状态（轮询用）
   */
  getEvidencePackageStatus(id: number): Promise<ApiResponse<EvidencePackage>> {
    return get(`${BASE}/evidence-packages/${id}`)
  },

  /**
   * 失败重试
   */
  retryEvidencePackage(id: number): Promise<ApiResponse<EvidencePackage>> {
    return post(`${BASE}/evidence-packages/${id}/retry`)
  },

  /**
   * 证据包历史列表
   */
  getEvidencePackageList(params: {
    pageNum?: number
    pageSize?: number
    orgId?: number
  }): Promise<ApiResponse<PageResponse<EvidencePackage>>> {
    return get(`${BASE}/evidence-packages`, params)
  },

  // ==================== 监控审计日志 ====================

  /**
   * 记录前端操作（画面切换、布局变更等）
   */
  logMonitorAction(data: {
    action: string
    deviceId?: number
    deviceName?: string
    recordingId?: number
    extra?: string
  }): Promise<ApiResponse<void>> {
    return post(`${BASE}/monitor-audit-logs`, data)
  },

  /**
   * 查询监控审计日志
   */
  getMonitorAuditLogs(params: MonitorAuditLogQuery): Promise<ApiResponse<PageResponse<MonitorAuditLog>>> {
    return get(`${BASE}/monitor-audit-logs`, params)
  },
}

export default videoMonitorApi
