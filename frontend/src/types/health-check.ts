/**
 * 晨检状态枚举
 */
export type HealthCheckStatus = 'pending_check' | 'checking' | 'completed_normal' | 'completed_abnormal' | 'archived'

/**
 * 晨检结果枚举
 */
export type HealthCheckResult = 'pending' | 'pass' | 'fail'

/**
 * 晨检任务标签
 */
export type HealthCheckDutyType = 'formal' | 'substitute'

/**
 * 体温状态枚举
 */
export type TemperatureStatus = 'low' | 'normal' | 'high'

/**
 * 健康状况枚举
 */
export type HealthStatus = 'normal' | 'abnormal'

/**
 * 卫生检查结果枚举
 */
export type HygieneCheck = 'pass' | 'fail'

/**
 * 晨检记录查询参数
 */
export interface HealthCheckQuery {
  pageNum?: number
  pageSize?: number
  checkDate?: string
  checkDateStart?: string
  checkDateEnd?: string
  status?: HealthCheckStatus
  checkResult?: HealthCheckResult
  employeeName?: string
  orgId?: number
}

/**
 * 晨检看板数据
 */
export interface HealthDashboard {
  pendingCount: number
  completedCount: number
  normalCount: number
  abnormalCount: number
  totalChecked: number
  passRate: number
  certificateExpiringCount: number
  certificateExpiredCount: number
}

/**
 * 晨检记录列表项
 */
export interface HealthCheckRecord {
  id: number
  checkNo: string
  employeeId: number
  employeeName: string
  avatarUrl?: string
  position?: string
  employeeNo?: string
  hasFaceData?: boolean
  certExpiryDate?: string
  checkDate: string
  checkTime: string
  temperature?: number
  faceMatchScore?: number
  certificateStatus?: string
  handHygiene?: HygieneCheck
  uniformCheck?: HygieneCheck
  healthStatus?: HealthStatus
  checkResult: HealthCheckResult
  failReason?: string
  checkerId?: number
  status: HealthCheckStatus
  shouldCheck?: boolean
  dutyType?: HealthCheckDutyType
  dutyTypeName?: string
  currentOrgId?: number
  currentOrgName?: string
  linkageReason?: string
  createdAt?: string
}

/**
 * 晨检记录详情
 */
export interface HealthCheckRecordDetail extends HealthCheckRecord {
  faceImageUrl?: string
  remark?: string
  linkageUpdatedAt?: string
  orgId?: number
  tenantId?: number
  updatedAt?: string
  movementLogs?: HealthCheckMovementLog[]
}

/**
 * 晨检异动留痕
 */
export interface HealthCheckMovementLog {
  id: number
  eventType: string
  eventName: string
  reasonCode?: string
  reasonDesc?: string
  beforeSummary?: string
  afterSummary?: string
  createdAt?: string
}

/**
 * 晨检联动版本
 */
export interface HealthCheckLinkageVersion {
  version: string
  updatedAt?: string
}

/**
 * 执行晨检请求体
 */
export interface HealthCheckCreatePayload {
  employeeId?: number
  employeeName?: string
  checkDate: string
  temperature?: number
  faceImageUrl?: string
  faceImageBase64?: string
  enableFaceRecognize?: boolean
  faceMatchScore?: number
  faceVerified?: boolean
  handHygiene?: HygieneCheck
  uniformCheck?: HygieneCheck
  checkerId?: number
  remark?: string
  orgId?: number
  tenantId?: number
}

/**
 * 更新晨检请求体
 */
export interface HealthCheckUpdatePayload {
  temperature?: number
  faceImageUrl?: string
  faceMatchScore?: number
  handHygiene?: HygieneCheck
  uniformCheck?: HygieneCheck
  healthStatus?: HealthStatus
  failReason?: string
  remark?: string
}

/**
 * 健康证信息
 */
export interface HealthCertificate {
  id: number
  employeeId: number
  employeeName: string
  certificateNo: string
  issueDate: string
  expiryDate: string
  certificateImages?: string
  issuingAuthority?: string
  status: 'pending' | 'valid' | 'expiring' | 'expired'
  warningDays: number
  remainingDays: number
  remark?: string
  orgId?: number
  createdAt?: string
  updatedAt?: string
}

/**
 * 健康证查询参数
 */
export interface HealthCertificateQuery {
  status?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
  showLeftEmployees?: boolean
}

/**
 * 健康证看板数据
 */
export interface HealthCertificateDashboard {
  totalCount: number
  validCount: number
  expiringCount: number
  expiredCount: number
  unregisteredCount: number
  urgentWarnings: HealthCertificateUrgentWarning[]
}

/**
 * 紧急预警信息
 */
export interface HealthCertificateUrgentWarning {
  employeeId: number
  employeeName: string
  expiryDate: string
  remainDays: number
  warningType: 'expired' | 'expiring'
}

/**
 * 健康证创建/更新请求
 */
export interface HealthCertificatePayload {
  employeeId?: number
  certificateNo: string
  issueDate: string
  expiryDate: string
  certificateImages?: string
  issuingAuthority?: string
  warningDays?: number
  remark?: string
  orgId?: number
  tenantId?: number
}

/**
 * AI一键晨检请求参数
 */
export interface AiCheckPayload {
  faceImage: string
  temperature?: number
  deviceId?: number
  orgId?: number
  expectedEmployeeId?: number
  matchThreshold?: number
  handHygiene?: HygieneCheck
  uniformCheck?: HygieneCheck
}

/**
 * AI一键晨检结果
 */
export interface AiCheckResult {
  checkId: number
  checkNo: string
  employeeId?: number
  employeeName?: string
  position?: string
  employeeNo?: string
  avatarUrl?: string
  faceMatchScore?: number
  faceMatchResult?: 'pass' | 'fail'
  faceImageUrl?: string
  checkTime?: string
  temperature?: number
  tempStatus?: string
  tempCheckResult?: 'pass' | 'fail'
  certNo?: string
  certExpiryDate?: string
  certStatus?: string
  certCheckResult?: 'pass' | 'fail'
  certCheckMessage?: string
  handHygiene?: 'pass' | 'fail'
  handHygieneMessage?: string
  uniformCheck?: 'pass' | 'fail'
  uniformCheckMessage?: string
  checkResult: 'pass' | 'fail'
  failReasons: string[]
  hasWarning: boolean
  warningMessages: string[]
}
