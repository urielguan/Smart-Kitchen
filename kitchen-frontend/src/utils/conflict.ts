import type { OfflineActionRecord } from '@/types/cook'

/**
 * 冲突类型分类
 */
export type ConflictType =
  | 'already_started'
  | 'already_completed'
  | 'task_cancelled'
  | 'chef_mismatch'
  | 'status_mismatch'

/**
 * 冲突详情
 */
export interface ConflictInfo {
  conflictType: ConflictType
  /** 用户尝试执行的操作描述 */
  userAction: string
  /** 后端返回的原始错误消息 */
  serverMessage: string
  /** 关联的任务 ID */
  taskId: number
  /** 任务名称（入队时捕获） */
  taskName?: string
  /** 冲突字段 */
  conflictField?: string
  /** 服务端最新值 */
  serverLatestValue?: string
}

/**
 * 从 API 错误中检测是否为状态冲突
 * 后端状态冲突返回 422 + 中文消息（非 409）
 */
export function detectConflict(
  error: any,
  record: OfflineActionRecord
): ConflictInfo | null {
  // 网络错误（无响应对象）不是冲突
  if (!error || !error.response) {
    return null
  }

  const data = error.response?.data || {}
  const message: string = data.message || error.message || ''

  // 无消息则无法判断
  if (!message) {
    return null
  }

  const conflictType = matchConflictType(message)
  if (!conflictType) {
    return null
  }

  const structured = extractStructuredConflict(data)
  const inferred = inferConflictDetails(conflictType, message)

  return {
    conflictType,
    userAction: describeUserAction(record),
    serverMessage: message,
    taskId: record.taskId,
    taskName: (record as any).taskName,
    conflictField: structured.conflictField || inferred.conflictField,
    serverLatestValue: structured.serverLatestValue || inferred.serverLatestValue
  }
}

/**
 * 匹配后端错误消息到冲突类型
 */
function matchConflictType(message: string): ConflictType | null {
  if (message.includes('任务状态不允许开始') || message.includes('已在进行中')) {
    return 'already_started'
  }
  if (message.includes('任务状态不允许完成') || message.includes('已完成')) {
    return 'already_completed'
  }
  if (message.includes('已取消') || message.includes('不允许取消')) {
    return 'task_cancelled'
  }
  if (message.includes('已指定给') || message.includes('无权操作')) {
    return 'chef_mismatch'
  }
  // 通用状态不匹配
  if (message.includes('状态') && message.includes('不允许')) {
    return 'status_mismatch'
  }
  return null
}

const toDisplayString = (value: unknown): string | undefined => {
  if (value === null || value === undefined) {
    return undefined
  }

  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed || undefined
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  return undefined
}

const extractStructuredConflict = (data: any) => {
  const payload = data?.data || data?.details || data?.conflict || data
  return {
    conflictField: toDisplayString(
      payload?.conflictField
        ?? payload?.field
        ?? payload?.conflict_field
        ?? payload?.latestField
    ),
    serverLatestValue: toDisplayString(
      payload?.serverLatestValue
        ?? payload?.latestValue
        ?? payload?.serverValue
        ?? payload?.currentValue
        ?? payload?.conflictValue
    )
  }
}

const inferConflictDetails = (conflictType: ConflictType, message: string) => {
  if (conflictType === 'chef_mismatch') {
    const chefMatch = message.match(/已指定给\s*([^，。,\s]+(?:\s*[^，。]*)?)/)
    return {
      conflictField: '指定厨师',
      serverLatestValue: chefMatch?.[1]?.trim() || '其他厨师'
    }
  }

  if (conflictType === 'already_started') {
    return {
      conflictField: '任务状态',
      serverLatestValue: '烹饪中'
    }
  }

  if (conflictType === 'already_completed') {
    return {
      conflictField: '任务状态',
      serverLatestValue: '已完成'
    }
  }

  if (conflictType === 'task_cancelled') {
    return {
      conflictField: '任务状态',
      serverLatestValue: '已取消'
    }
  }

  if (conflictType === 'status_mismatch') {
    return {
      conflictField: '任务状态'
    }
  }

  return {}
}

/**
 * 描述用户尝试执行的操作
 */
export function describeUserAction(record: OfflineActionRecord): string {
  const taskName = (record as any).taskName as string | undefined
  const taskLabel = taskName ? ` ${taskName}` : ` 任务 #${record.taskId}`
  switch (record.type) {
    case 'start':
      return `开始烹饪${taskLabel}`
    case 'complete':
      return `完成烹饪${taskLabel}`
    default:
      return `操作${taskLabel}`
  }
}

/**
 * 描述冲突的服务器端情况
 */
export function describeConflict(conflict: ConflictInfo): string {
  switch (conflict.conflictType) {
    case 'already_started':
      return '该任务已被其他人开始烹饪，当前状态为烹饪中'
    case 'already_completed':
      return '该任务已完成烹饪，无需再次操作'
    case 'task_cancelled':
      return '该任务已被主管取消，无法继续操作'
    case 'chef_mismatch':
      return '该任务已指定给其他厨师，您无权操作'
    case 'status_mismatch':
      return '任务当前状态与您的操作不匹配，可能已被其他人修改'
    default:
      return conflict.serverMessage
  }
}

/**
 * 判断冲突类型是否允许强制重试
 * already_completed 和 task_cancelled 不允许强制重试
 */
export function canForceRetry(conflictType: ConflictType): boolean {
  return conflictType === 'status_mismatch' || conflictType === 'chef_mismatch'
}
