/** 告警列表项 */
export interface Alert {
  id: number
  alertNo: string
  alertType: string
  alertTypeName: string
  alertRuleId: number | null
  alertRuleName: string | null
  alertLevel: string
  alertLevelName: string
  deviceId: number
  deviceName: string
  deviceType: string
  alertContent: string
  triggeredAt: string
  status: string
  statusName: string
  assignedTo: number | null
  assignedToName: string | null
  assignedToPhone: string | null
  assignedAt: string | null
}

/** 告警详情 */
export interface AlertDetail extends Alert {
  alertDetail: Record<string, any> | null
  alertImages: string[] | null
  alertVideoUrl: string | null
  handledBy: number | null
  handledByName?: string | null
  handledAt: string | null
  handleResult: string | null
  handleImages: string[] | null
  reviewedBy: number | null
  reviewedByName?: string | null
  reviewedAt: string | null
  reviewResult: AlertReviewResult | null
  reviewRemark: string | null
  closedBy: number | null
  closedByName?: string | null
  closedAt: string | null
  closeRemark: string | null
  archivedBy: number | null
  archivedByName?: string | null
  archivedAt: string | null
  archiveRemark: string | null
  createdAt: string
}

/** 告警查询参数 */
export interface AlertQuery {
  pageNum?: number
  pageSize?: number
  orgId?: number
  alertType?: string
  alertLevel?: string
  status?: string
  deviceId?: number
  assignedTo?: number
  startTime?: string
  endTime?: string
}

/** 复核结果 */
export type AlertReviewResult = 'approved' | 'rejected'

/** 告警复核DTO */
export interface AlertReviewDTO {
  reviewResult: AlertReviewResult
  reviewRemark?: string
  reviewAttachments?: AttachmentItem[]
}

/** 告警关闭DTO */
export interface AlertCloseDTO {
  closeRemark: string
  archiveRemark?: string
}

/** 告警看板统计 */
export interface AlertDashboard {
  totalCount: number
  criticalCount: number
  errorCount: number
  warningCount: number
  infoCount: number
  pendingCount: number
  assignedCount: number
  closedCount: number
  handledCount: number
  reviewedCount: number
  alertTypeStats: AlertTypeStat[]
  alertTrends: AlertTrend[]
}

export interface AlertTypeStat {
  alertType: string
  alertTypeName: string
  count: number
}

export interface AlertTrend {
  date: string
  count: number
}

// ==================== 派单相关类型 ====================

/** 派单表单 */
export interface AlertDispatchForm {
  dispatchType: 'auto' | 'manual'
  handlerId?: number
  priority?: 'high' | 'medium' | 'low'
  deadline?: string
  remark?: string
}

export interface AlertDispatchResult {
  dispatchId: number
  dispatchNo: string
  alertId: number
  alertNo: string
  dispatchType: string
  handlerId: number
  handlerName: string
  deadline?: string
  status: string
}

/** 派单记录 */
export interface AlertDispatch {
  id: number
  dispatchNo: string
  alertId: number
  alertNo: string
  alertType: string
  alertTypeName: string
  alertLevel: string
  alertLevelName: string
  alertContent: string
  dispatchType: string
  dispatchTypeName: string
  assignerId: number
  assignerName: string
  handlerId: number
  handlerName: string
  deadline: string | null
  priority: string
  priorityName: string
  status: string
  statusName: string
  handleResult: string | null
  handleAttachments: AttachmentItem[] | null
  completedAt: string | null
  reviewedBy: number | null
  reviewedByName: string | null
  reviewedAt: string | null
  reviewResult: AlertReviewResult | null
  createdAt: string
}

/** 派单查询参数 */
export interface AlertDispatchQuery {
  pageNum?: number
  pageSize?: number
  status?: string
  dispatchType?: string
  handlerName?: string
  startTime?: string
  endTime?: string
}

/** 附件项 */
export interface AttachmentItem {
  url: string
  name: string
}

/** 工单处理表单 */
export interface AlertProcessForm {
  handleResult: string
  handleAttachments?: AttachmentItem[]
}

/** 处理人选项 */
export interface HandlerOption {
  id: number
  name: string
  orgId: number
  orgName: string
  position: string
}

// ==================== 派单详情相关类型 ====================

/** 工单处理记录 */
export interface AlertWorkOrderRecord {
  id: number
  action: string
  actionName: string
  operatorId: number | null
  operatorName: string | null
  content: string | null
  attachments: AttachmentItem[] | null
  createdAt: string
}

/** 派单详情（包含告警信息 + 派单信息 + 处理记录） */
export interface AlertDispatchDetail {
  // 告警信息
  alertId: number
  alertNo: string
  alertType: string
  alertTypeName: string
  alertLevel: string
  alertLevelName: string
  alertStatus: string
  alertStatusName: string
  alertContent: string
  alertDetail: Record<string, any> | null
  alertImages: string[] | null
  alertVideoUrl: string | null
  deviceName: string
  deviceType: string
  deviceTypeName: string
  triggeredAt: string

  // 派单信息
  dispatchId: number
  dispatchNo: string
  dispatchType: string
  dispatchTypeName: string
  assignerId: number
  assignerName: string
  handlerId: number
  handlerName: string
  deadline: string | null
  priority: string
  priorityName: string
  remark: string | null
  status: string
  statusName: string
  handleResult: string | null
  handleAttachments: AttachmentItem[] | null
  completedAt: string | null
  reviewedBy: number | null
  reviewedByName: string | null
  reviewedAt: string | null
  reviewResult: AlertReviewResult | null
  reviewRemark: string | null
  reviewAttachments: AttachmentItem[] | null
  closedBy: number | null
  closedByName: string | null
  closedAt: string | null
  closeRemark: string | null
  archivedBy: number | null
  archivedByName: string | null
  archivedAt: string | null
  archiveRemark: string | null
  createdAt: string

  // 处理记录
  records: AlertWorkOrderRecord[]
}

// ==================== 告警选项常量 ====================

/** 告警类型选项（与规则类型一致） */
export const ALERT_TYPES = [
  { value: 'threshold', label: '阈值告警' },
  { value: 'offline', label: '离线告警' },
  { value: 'device_fault', label: '设备故障' },
  { value: 'ai_event', label: 'AI事件告警' },
  { value: 'material', label: '物料告警' },
]

/** 告警级别选项 */
export const ALERT_LEVELS = [
  { value: 'critical', label: '严重', color: '#F56C6C' },
  { value: 'error', label: '错误', color: '#E6A23C' },
  { value: 'warning', label: '警告', color: '#E6A23C' },
  { value: 'info', label: '提示', color: '#909399' },
]

/** 告警状态选项 */
export const ALERT_STATUS = [
  { value: 'pending', label: '待处理', color: '#909399' },
  { value: 'assigned', label: '已指派', color: '#409EFF' },
  { value: 'handling', label: '处理中', color: '#E6A23C' },
  { value: 'handled', label: '已处置', color: '#67C23A' },
  { value: 'reviewed', label: '已复核', color: '#67C23A' },
  { value: 'closed', label: '已关闭', color: '#909399' },
]

export const ALERT_REVIEW_RESULT_LABELS: Record<AlertReviewResult, string> = {
  approved: '通过',
  rejected: '驳回',
}
