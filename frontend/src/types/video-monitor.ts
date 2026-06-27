/**
 * 视频监控管理 - 类型定义
 */

/** 摄像头设备 */
export interface Camera {
  id: number
  deviceCode: string
  deviceName: string
  location: string
  resolution: string
  frameRate: number
  onlineStatus: 'online' | 'offline'
  status: 'active' | 'inactive' | 'maintenance'
  alertCount: number
  lastUpdatedAt: string
  /** 流媒体地址 */
  streamUrl?: string
  /** HLS 播放地址 */
  hlsUrl?: string
  /** AI识别结果流地址 */
  analysisStreamUrl?: string
  /** AI识别结果流类型 */
  analysisStreamType?: 'mjpeg' | 'hls' | 'flv' | 'native'
  /** 是否优先展示AI识别流 */
  preferAnalysisStream?: boolean
  /** 缩略图 */
  thumbnailUrl?: string
  /** 是否支持云台控制 */
  ptzSupport: boolean
  /** 组织ID */
  orgId?: number
  /** 组织名称 */
  orgName?: string
}

/** 实时监控查询参数 */
export interface MonitorQuery {
  pageNum?: number
  pageSize?: number
  orgId?: number
  onlineStatus?: 'online' | 'offline'
  location?: string
}

/** 监控统计数据 */
export interface MonitorStatistics {
  totalCameras: number
  onlineCameras: number
  offlineCameras: number
  alertCameras: number
}

/** SSE 设备在线状态变更事件 */
export interface DeviceStatusEvent {
  deviceId: number
  deviceName: string
  deviceType: string
  oldStatus: string
  newStatus: string
  timestamp: number
}

/** 视频录像 */
export interface VideoRecording {
  id: number
  deviceId: number
  deviceName: string
  location: string
  startTime: string
  endTime: string
  duration: number
  durationFormat: string
  fileSize: number
  fileSizeFormat: string
  resolution: string
  /** 录像类型: continuous/alarm/manual */
  recordingType: string
  recordingTypeName: string
  /** 录像回放地址 */
  playbackUrl: string
  /** 下载地址 */
  downloadUrl?: string
  /** 缩略图地址 */
  thumbnailUrl?: string
  /** 是否有AI标记 */
  hasAiMarks: boolean
  /** 创建时间 */
  createdAt?: string
}

/** 录像查询参数 */
export interface RecordingQuery {
  deviceId?: number
  startTime?: string
  endTime?: string
  recordingType?: string
  pageNum?: number
  pageSize?: number
}

/** 录像统计数据 */
export interface RecordingStatistics {
  totalCount: number
  totalFileSize: number
  alarmCount: number
  aiMarkCount: number
}

/** AI违规事件 */
export interface ViolationEvent {
  id: number
  violationType: string
  violationTypeName: string
  involvedCount: number
  alertLevel: 'info' | 'warning' | 'urgent' | 'danger'
  alertLevelName?: string
  location: string
  deviceId: number
  deviceName: string
  confidence: number
  occurredAt: string
  duration: number
  status: 'pending' | 'assigned' | 'processing' | 'resolved' | 'reviewed'
  /** 违规视频片段 */
  videoClipUrl?: string
  /** 截图 */
  screenshotUrl?: string
  /** 处理人 */
  handlerName?: string
  /** 处理时间 */
  handledAt?: string
  /** 处理备注 */
  handleRemark?: string
  /** 复核人 */
  reviewerName?: string
  /** 复核时间 */
  reviewedAt?: string
  /** 复核状态 */
  reviewStatus?: 'pending' | 'approved' | 'rejected'
  /** 关联录像ID (null=尚未关联) */
  recordingId?: number | null
  /** 录像回放地址 (仅detail接口返回) */
  recordingPlaybackUrl?: string | null
  /** 录像开始时间 */
  recordingStartTime?: string | null
  /** 录像结束时间 */
  recordingEndTime?: string | null
}

/** 违规事件查询参数 */
export interface ViolationQuery {
  pageNum?: number
  pageSize?: number
  violationType?: string
  alertLevel?: string
  status?: string
  deviceId?: number
  startTime?: string
  endTime?: string
  orgId?: number
}

/** 违规处理表单 */
export interface ViolationHandleForm {
  status: 'assigned' | 'processing' | 'resolved'
  handlerId?: number
  handlerName?: string
  handleRemark?: string
}

/** 违规统计 */
export interface ViolationStatistics {
  totalCount: number
  pendingCount: number
  urgentCount: number
  resolvedCount: number
  todayCount: number
}

/** AI人员行为分析 */
export interface BehaviorAnalysis {
  id: number
  employeeId: number
  employeeName: string
  employeeCode: string
  employeeRole: string
  department: string
  avatar?: string
  efficiencyScore: number
  complianceScore: number
  hygieneScore: number
  punctualityScore?: number
  teamworkScore?: number
  overallScore: number
  workDuration: number
  operationCount: number
  violationCount: number
  hasIssues: boolean
  tags: string[]
  createdAt?: string
}

/** 人员行为分析详情 */
export interface BehaviorAnalysisDetail extends BehaviorAnalysis {
  issues: BehaviorIssue[]
  aiTrainingSuggestions: string[]
  periodStart?: string
  periodEnd?: string
}

/** 行为问题 */
export interface BehaviorIssue {
  id: number
  issueType: string
  issueName: string
  description: string
  occurrenceCount: number
  suggestion?: string
}

/** 人员分析查询参数 */
export interface BehaviorQuery {
  pageNum?: number
  pageSize?: number
  orgId?: number
  hasIssues?: boolean
  employeeName?: string
  startTime?: string
  endTime?: string
}

/** 人员分析统计 */
export interface BehaviorStatistics {
  totalEmployees: number
  averageEfficiency: number
  averageCompliance: number
  averageHygiene: number
  needImprovementCount: number
  benchmarkCount: number
  todayAnalysisCount: number
  issueCount: number
  efficiencyDistribution?: ScoreDistribution
  complianceDistribution?: ScoreDistribution
  hygieneDistribution?: ScoreDistribution
}

/** 评分分布 */
export interface ScoreDistribution {
  excellent: number
  good: number
  average: number
  poor: number
  fail: number
}

/** 云台控制方向 */
export type PTZDirection = 'up' | 'down' | 'left' | 'right' | 'zoom_in' | 'zoom_out' | 'stop'

/** 云台控制参数 */
export interface PTZControlParams {
  deviceId: number
  direction: PTZDirection
  speed?: number
}

// ==================== 视频片段截取 ====================

/** 视频片段 */
export interface VideoClip {
  id: number
  recordingId: number
  deviceId: number
  deviceName: string
  startTimeOffset: number
  endTimeOffset: number
  clipDuration: number
  durationFormat: string
  fileSize: number
  fileSizeFormat: string
  purposeTag: string
  purposeTagName: string
  status: 'processing' | 'completed' | 'failed'
  statusName: string
  failReason?: string
  downloadUrl?: string
  versionNo: number
  createdByName?: string
  createdAt?: string
}

/** 片段截取请求 */
export interface ClipExtractRequest {
  recordingId: number
  startTimeOffset: number
  endTimeOffset: number
  purposeTag: 'violation_trace' | 'accident_review' | 'process_review'
}

/** 片段查询参数 */
export interface ClipQuery {
  recordingId?: number
  deviceId?: number
  purposeTag?: string
  status?: string
  pageNum?: number
  pageSize?: number
}

/** 片段用途标签映射 */
export const CLIP_PURPOSE_TAGS = {
  violation_trace: { name: '违规追溯', color: '#F56C6C' },
  accident_review: { name: '事故核查', color: '#E6A23C' },
  process_review: { name: '流程复检', color: '#409EFF' },
} as const

/** 片段状态映射 */
export const CLIP_STATUS_MAP = {
  processing: { name: '处理中', type: 'warning' as const },
  completed: { name: '已完成', type: 'success' as const },
  failed: { name: '失败', type: 'danger' as const },
} as const

// ==================== 回放截图 ====================

/** 回放截图 */
export interface Screenshot {
  id: number
  recordingId: number
  deviceId: number
  deviceName: string
  captureTimeOffset: number
  captureTimeFormat: string
  fileSize: number
  fileSizeFormat: string
  resolution: string
  purposeTag: string
  purposeTagName: string
  status: string
  statusName: string
  previewUrl?: string
  downloadUrl?: string
  versionNo: number
  createdByName?: string
  createdAt?: string
  latestAnalysis?: DeviceVisionAnalysisTask
}

export interface DeviceVisionAnalysisTask {
  id: number
  screenshotId: number
  recordingId?: number
  deviceId?: number
  deviceName?: string
  taskStatus: 'processing' | 'alerted' | 'threshold_not_met' | 'failed'
  violationType?: string
  confidence?: number
  summary?: string
  modelVersion?: string
  alertId?: number
  errorMessage?: string
  createdAt?: string
}

/** 截图查询参数 */
export interface ScreenshotQuery {
  recordingId?: number
  deviceId?: number
  purposeTag?: string
  pageNum?: number
  pageSize?: number
}

/** 违规类型枚举 */
export const VIOLATION_TYPES = {
  no_mask: { name: '未佩戴口罩', level: 'warning' as const },
  no_hat: { name: '未佩戴厨师帽', level: 'warning' as const },
  no_gloves: { name: '未佩戴手套', level: 'warning' as const },
  smoking: { name: '吸烟行为', level: 'danger' as const },
  phone: { name: '使用手机', level: 'warning' as const },
  outsider: { name: '陌生人闯入', level: 'danger' as const },
  leaving_stove: { name: '动火离人', level: 'danger' as const },
  hygiene_violation: { name: '卫生不当', level: 'warning' as const },
  cross_contamination: { name: '生熟混放', level: 'danger' as const },
  handwashing: { name: '未洗手', level: 'warning' as const },
  zone_violation: { name: '分区作业违规', level: 'warning' as const },
  rodent_detection: { name: '鼠患迹象', level: 'danger' as const },
} as const

/** 告警级别枚举 */
export const ALERT_LEVELS = {
  info: { name: '提示', color: '#909399' },
  warning: { name: '警告', color: '#E6A23C' },
  urgent: { name: '紧急', color: '#F56C6C' },
  danger: { name: '危险', color: '#F56C6C' },
} as const

/** 处理状态枚举 */
export const HANDLE_STATUS = {
  pending: { name: '待处理', color: '#F56C6C' },
  assigned: { name: '已指派', color: '#E6A23C' },
  processing: { name: '处理中', color: '#409EFF' },
  handled: { name: '已处置', color: '#67C23A' },
  resolved: { name: '已解决', color: '#67C23A' },
  reviewed: { name: '已复核', color: '#909399' },
  closed: { name: '已关闭', color: '#909399' },
} as const

/** 证据包状态 */
export type EvidencePackageStatus = 'packing' | 'completed' | 'failed' | 'expired'

/** 证据包 */
export interface EvidencePackage {
  id: number
  packageNo: string
  packageName: string
  status: EvidencePackageStatus
  statusName: string
  recordingIds: number[]
  clipIds: number[]
  screenshotIds: number[]
  fileName?: string
  fileSize?: number
  fileSizeFormat?: string
  itemCount: number
  downloadUrl?: string
  expiresAt?: string
  downloadedAt?: string
  downloadCount: number
  failReason?: string
  createdAt: string
}

/** 证据包状态映射 */
export const EVIDENCE_PACKAGE_STATUS_MAP = {
  packing: { name: '打包中', type: 'warning' as const },
  completed: { name: '已完成', type: 'success' as const },
  failed: { name: '打包失败', type: 'danger' as const },
  expired: { name: '已过期', type: 'info' as const },
} as const

// ==================== 监控审计日志 ====================

/** 监控审计日志 */
export interface MonitorAuditLog {
  id: number
  userId: number
  userName: string
  realName: string
  operationType: string
  moduleCode: string
  moduleName: string
  targetId: number | null
  targetNo: string | null
  operationDesc: string
  result: string
  errorMsg: string | null
  ipAddress: string
  sourceTerminal: string | null
  deviceId: number | null
  recordingId: number | null
  orgId: number | null
  createdAt: string
}

/** 审计日志查询参数 */
export interface MonitorAuditLogQuery {
  pageNum?: number
  pageSize?: number
  operationType?: string
  moduleCode?: string
  sourceTerminal?: string
  deviceId?: number
  recordingId?: number
  orgId?: number
  startTime?: string
  endTime?: string
}
