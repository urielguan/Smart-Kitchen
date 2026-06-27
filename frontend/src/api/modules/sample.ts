import { get, post, put } from '@/api'
import service from '@/api'
import type {
  ApiResponse,
  PageResponse,
  SampleOperationLock,
  SampleOperationType,
  SampleHistoryTaskQuery,
  SampleAvailableCookTask,
  SampleDashboard,
  ManualDisposalSupplementPayload,
  SampleHistoryRecordCreatePayload,
  SampleRecord,
  SampleRecordDetail,
  SampleRecordQuery,
  SampleRecordCreatePayload,
  SampleRecordRegisterPayload,
  DisposalPayload,
  DisposalReminderRecord,
  AiEvaluateResult,
  SampleRecordUpdatePayload,
  OperationLog
} from '@/types'
import type { RequestConfig } from '@/api'

const buildLockRequestConfig = (lockToken?: string | null): RequestConfig | undefined => {
  if (!lockToken) {
    return undefined
  }
  return {
    headers: {
      'X-Sample-Lock-Token': lockToken
    }
  }
}

const buildSampleLedgerFileName = () => {
  const now = new Date()
  const pad = (value: number) => value.toString().padStart(2, '0')
  const timestamp = [
    now.getFullYear(),
    pad(now.getMonth() + 1),
    pad(now.getDate()),
    pad(now.getHours()),
    pad(now.getMinutes()),
    pad(now.getSeconds())
  ].join('')
  return `留样台账_${timestamp}.xlsx`
}

/** 获取留样看板数据 */
export const getSampleDashboard = (params?: SampleRecordQuery): Promise<ApiResponse<SampleDashboard>> => {
  return get('/v1/sample/dashboard', params)
}

/** 获取留样记录列表 */
export const getSampleRecordList = (params?: SampleRecordQuery): Promise<ApiResponse<PageResponse<SampleRecord>>> => {
  return get('/v1/sample/records', params)
}

/** 获取手工新增可选烹饪任务 */
export const getSampleManualTaskOptions = (): Promise<ApiResponse<SampleAvailableCookTask[]>> => {
  return get('/v1/sample/manual-task-options')
}

/** 获取历史补录可选烹饪任务 */
export const getSampleHistoryTaskOptions = (params: SampleHistoryTaskQuery): Promise<ApiResponse<SampleAvailableCookTask[]>> => {
  return get('/v1/sample/history-task-options', params)
}

/** 新增留样记录 */
export const createSampleRecord = (data: SampleRecordCreatePayload): Promise<ApiResponse<SampleRecordDetail>> => {
  return post('/v1/sample/records', data)
}

/** 历史补录留样记录 */
export const createSampleHistoryRecord = (data: SampleHistoryRecordCreatePayload): Promise<ApiResponse<SampleRecordDetail>> => {
  return post('/v1/sample/records/history-supplement', data)
}

/** 执行留样登记 */
export const registerSampleRecord = (id: number, data: SampleRecordRegisterPayload, lockToken?: string | null): Promise<ApiResponse<SampleRecordDetail>> => {
  return post(`/v1/sample/records/${id}/register`, data, buildLockRequestConfig(lockToken))
}

/** 获取留样记录详情 */
export const getSampleRecordDetail = (id: number): Promise<ApiResponse<SampleRecordDetail>> => {
  return get(`/v1/sample/records/${id}`)
}

/** 抢占留样操作锁 */
export const acquireSampleOperationLock = (id: number, operationType: SampleOperationType): Promise<ApiResponse<SampleOperationLock>> => {
  return post(`/v1/sample/records/${id}/operation-lock/acquire`, { operationType })
}

/** 续租留样操作锁 */
export const refreshSampleOperationLock = (id: number, lockToken: string): Promise<ApiResponse<SampleOperationLock>> => {
  return post(`/v1/sample/records/${id}/operation-lock/refresh`, { lockToken })
}

/** 释放留样操作锁 */
export const releaseSampleOperationLock = (id: number, lockToken: string): Promise<ApiResponse<null>> => {
  return post(`/v1/sample/records/${id}/operation-lock/release`, { lockToken }, { silentError: true })
}

/** 执行销样 */
export const executeDisposal = (id: number, data: DisposalPayload, lockToken?: string | null): Promise<ApiResponse<SampleRecordDetail>> => {
  return post(`/v1/sample/records/${id}/disposal`, data, buildLockRequestConfig(lockToken))
}

/** 销样手工补录 */
export const manualSupplementDisposal = (id: number, data: ManualDisposalSupplementPayload, lockToken?: string | null): Promise<ApiResponse<SampleRecordDetail>> => {
  return post(`/v1/sample/records/${id}/disposal/manual-supplement`, data, buildLockRequestConfig(lockToken))
}

/** 获取销样提醒列表 */
export const getDisposalReminders = (params?: SampleRecordQuery): Promise<ApiResponse<PageResponse<DisposalReminderRecord>>> => {
  return get('/v1/sample/disposal-reminders', params)
}

/** AI智能评估 */
export const aiEvaluate = (id: number, lockToken?: string | null): Promise<ApiResponse<AiEvaluateResult>> => {
  return post(`/v1/sample/records/${id}/ai-evaluate`, undefined, buildLockRequestConfig(lockToken))
}

/** 编辑留样记录 */
export const updateSampleRecord = (id: number, data: SampleRecordUpdatePayload, lockToken?: string | null): Promise<ApiResponse<SampleRecordDetail>> => {
  return put(`/v1/sample/records/${id}`, data, buildLockRequestConfig(lockToken))
}

/** 作废留样记录 */
export const voidSampleRecord = (id: number, reason: string, lockToken?: string | null): Promise<ApiResponse<SampleRecordDetail>> => {
  return post(`/v1/sample/records/${id}/void?reason=${encodeURIComponent(reason)}`, undefined, buildLockRequestConfig(lockToken))
}

/** 上传留样图片 */
export const uploadSampleImage = (file: File): Promise<ApiResponse<{ imageUrl: string }>> => {
  const formData = new FormData()
  formData.append('file', file)
  return post('/v1/sample/upload-image', formData)
}

/** 归档留样记录 */
export const archiveSampleRecord = (id: number, lockToken?: string | null): Promise<ApiResponse<SampleRecordDetail>> => {
  return put(`/v1/sample/records/${id}/archive`, undefined, buildLockRequestConfig(lockToken))
}

/** 导出留样记录 */
export const exportSampleRecords = async (params?: SampleRecordQuery): Promise<void> => {
  const query = new URLSearchParams()
  if (params?.status) query.set('status', params.status)
  if (params?.sampleDate) query.set('sampleDate', params.sampleDate)
  if (params?.sampleDateEnd) query.set('sampleDateEnd', params.sampleDateEnd)
  if (params?.mealType) query.set('mealType', params.mealType)
  if (params?.menuName) query.set('menuName', params.menuName)
  if (params?.orgId != null) query.set('orgId', String(params.orgId))
  if (params?.showRollbackIsolated) query.set('showRollbackIsolated', 'true')
  const url = '/v1/sample/records/export' + (query.toString() ? '?' + query.toString() : '')

  const response = await service.get(url, {
    responseType: 'blob'
  })

  const contentDisposition = response.headers['content-disposition']
  let filename = buildSampleLedgerFileName()
  if (contentDisposition) {
    const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)/i)
    if (filenameMatch) {
      filename = decodeURIComponent(filenameMatch[1].replace(/['"]/g, ''))
    }
  }

  const blob = new Blob([response.data as BlobPart])
  const blobUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

/** 获取操作日志 */
export const getSampleOperationLogs = (id: number): Promise<ApiResponse<OperationLog[]>> => {
  return get(`/v1/sample/records/${id}/logs`)
}

/** 批量作废留样记录 */
export const batchVoidRecords = (ids: number[], reason: string): Promise<ApiResponse<null>> => {
  return post('/v1/sample/records/batch-void', { ids, reason })
}

/** 批量归档留样记录 */
export const batchArchiveRecords = (ids: number[]): Promise<ApiResponse<null>> => {
  return put('/v1/sample/records/batch-archive', { ids })
}

/** 监管锁定 */
export const lockRecord = (id: number, lockStatus: string): Promise<ApiResponse<SampleRecordDetail>> => {
  return post(`/v1/sample/records/${id}/lock?lockStatus=${encodeURIComponent(lockStatus)}`)
}

/** 解除锁定 */
export const unlockRecord = (id: number): Promise<ApiResponse<SampleRecordDetail>> => {
  return post(`/v1/sample/records/${id}/unlock`)
}
