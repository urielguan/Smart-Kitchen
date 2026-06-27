import { get, post } from '@/api'
import service from '@/api'
import type {
  ApiResponse,
  PageResponse,
  CookDashboard,
  CookTask,
  CookTaskArchivePayload,
  CookTaskCompletePayload,
  CookTaskDetail,
  CookTaskQuery,
  CookTaskStartPayload,
  CookTemperaturePoint,
  CookAIMonitorRecord
} from '@/types'

export const getCookDashboard = (params?: CookTaskQuery): Promise<ApiResponse<CookDashboard>> => {
  return get('/v1/cook/dashboard', params)
}

export const getCookTaskList = (params?: CookTaskQuery): Promise<ApiResponse<PageResponse<CookTask>>> => {
  return get('/v1/cook/tasks', params)
}

export const getCookTaskDetail = (id: number): Promise<ApiResponse<CookTaskDetail>> => {
  return get(`/v1/cook/tasks/${id}`)
}

export const startCookTask = (id: number, data?: CookTaskStartPayload): Promise<ApiResponse<CookTask>> => {
  return post(`/v1/cook/tasks/${id}/start`, data)
}

export const completeCookTask = (id: number, data?: CookTaskCompletePayload): Promise<ApiResponse<CookTask>> => {
  return post(`/v1/cook/tasks/${id}/complete`, data)
}

export const archiveCookTask = (id: number, data?: CookTaskArchivePayload): Promise<ApiResponse<CookTask>> => {
  return post(`/v1/cook/tasks/${id}/archive`, data)
}

export const getCookTaskTemperature = (id: number): Promise<ApiResponse<CookTemperaturePoint[]>> => {
  return get(`/v1/cook/tasks/${id}/temperature`)
}

export const getCookTaskAiMonitor = (id: number): Promise<ApiResponse<CookAIMonitorRecord[]>> => {
  return get(`/v1/cook/tasks/${id}/ai-monitor`)
}

export const exportCookTasks = async (params?: CookTaskQuery): Promise<void> => {
  const query = new URLSearchParams()
  if (params?.taskDate) query.set('taskDate', params.taskDate)
  if (params?.mealType) query.set('mealType', params.mealType)
  if (params?.status) query.set('status', params.status)
  if (params?.taskNo) query.set('taskNo', params.taskNo)
  if (params?.chefName) query.set('chefName', params.chefName)
  const url = '/v1/cook/tasks/export' + (query.toString() ? '?' + query.toString() : '')

  const response = await service.get(url, { responseType: 'blob' })

  const contentDisposition = response.headers['content-disposition']
  let filename = `烹饪记录_${new Date().toISOString().slice(0, 10)}.xlsx`
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)/i)
    if (match) filename = decodeURIComponent(match[1].replace(/['"]/g, ''))
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
