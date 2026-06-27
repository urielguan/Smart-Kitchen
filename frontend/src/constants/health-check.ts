import type { HealthCheckStatus, HealthCheckResult, TemperatureStatus, HealthStatus, HygieneCheck } from '@/types/health-check'

/**
 * 晨检状态选项
 */
export const HEALTH_CHECK_STATUS_OPTIONS: { value: HealthCheckStatus; label: string; type: string }[] = [
  { value: 'pending_check', label: '待晨检', type: 'info' },
  { value: 'checking', label: '晨检中', type: 'warning' },
  { value: 'completed_normal', label: '正常', type: 'success' },
  { value: 'completed_abnormal', label: '异常', type: 'danger' },
  { value: 'archived', label: '已归档', type: 'info' },
]

/**
 * 已晨检列表状态选项（排除待晨检、晨检中）
 */
export const COMPLETED_STATUS_OPTIONS = HEALTH_CHECK_STATUS_OPTIONS.filter(
  opt => opt.value !== 'pending_check' && opt.value !== 'checking'
)

/**
 * 待晨检列表状态选项
 */
export const PENDING_STATUS_OPTIONS = HEALTH_CHECK_STATUS_OPTIONS.filter(
  opt => opt.value === 'pending_check' || opt.value === 'checking'
)

/**
 * 晨检状态映射
 */
export const HEALTH_CHECK_STATUS_MAP: Record<HealthCheckStatus, { label: string; type: string }> = {
  pending_check: { label: '待晨检', type: 'info' },
  checking: { label: '晨检中', type: 'warning' },
  completed_normal: { label: '正常', type: 'success' },
  completed_abnormal: { label: '异常', type: 'danger' },
  archived: { label: '已归档', type: 'info' },
}

/**
 * 晨检结果选项
 */
export const HEALTH_CHECK_RESULT_OPTIONS: { value: HealthCheckResult; label: string; type: string }[] = [
  { value: 'pass', label: '通过', type: 'success' },
  { value: 'fail', label: '不通过', type: 'danger' },
]

/**
 * 晨检结果映射
 */
export const HEALTH_CHECK_RESULT_MAP: Record<string, { label: string; type: string }> = {
  pending: { label: '待检', type: 'info' },
  pass: { label: '通过', type: 'success' },
  fail: { label: '不通过', type: 'danger' },
}

/**
 * 体温状态选项
 */
export const TEMPERATURE_STATUS_OPTIONS: { value: TemperatureStatus; label: string; type: string }[] = [
  { value: 'low', label: '偏低', type: 'info' },
  { value: 'normal', label: '正常', type: 'success' },
  { value: 'high', label: '偏高', type: 'danger' },
]

/**
 * 体温状态映射
 */
export const TEMPERATURE_STATUS_MAP: Record<TemperatureStatus, { label: string; type: string }> = {
  low: { label: '偏低', type: 'info' },
  normal: { label: '正常', type: 'success' },
  high: { label: '偏高', type: 'danger' },
}

/**
 * 健康状况选项
 */
export const HEALTH_STATUS_OPTIONS: { value: HealthStatus; label: string; type: string }[] = [
  { value: 'normal', label: '正常', type: 'success' },
  { value: 'abnormal', label: '异常', type: 'danger' },
]

/**
 * 健康状况映射
 */
export const HEALTH_STATUS_MAP: Record<HealthStatus, { label: string; type: string }> = {
  normal: { label: '正常', type: 'success' },
  abnormal: { label: '异常', type: 'danger' },
}

/**
 * 卫生检查选项
 */
export const HYGIENE_CHECK_OPTIONS: { value: HygieneCheck; label: string; type: string }[] = [
  { value: 'pass', label: '合格', type: 'success' },
  { value: 'fail', label: '不合格', type: 'danger' },
]

/**
 * 卫生检查映射
 */
export const HYGIENE_CHECK_MAP: Record<HygieneCheck, { label: string; type: string }> = {
  pass: { label: '合格', type: 'success' },
  fail: { label: '不合格', type: 'danger' },
}

/**
 * 健康证状态映射
 */
export const CERTIFICATE_STATUS_MAP: Record<string, { label: string; type: string }> = {
  valid: { label: '有效', type: 'success' },
  expiring: { label: '即将过期', type: 'warning' },
  expired: { label: '已过期', type: 'danger' },
}

/**
 * 根据体温值获取体温状态
 */
export function getTemperatureStatus(temp?: number): TemperatureStatus {
  if (!temp) return 'normal'
  if (temp < 36.0) return 'low'
  if (temp >= 37.3) return 'high'
  return 'normal'
}
