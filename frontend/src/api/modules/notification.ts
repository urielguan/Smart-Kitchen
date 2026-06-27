import { get, post, put, del } from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import { isDevPreviewMockEnabled } from '@/dev-preview/env'
import { devPreviewNotificationApi } from '@/dev-preview/notification'

const silent = { silentError: true }

/** 通知相关类型 */
export interface MaterialNotification {
  id: number
  notificationType: string
  notificationTypeName: string
  materialId: number
  materialName: string
  inventoryId: number
  batchNo: string
  quantity: number
  unit: string
  expiryDate: string
  daysRemaining: number
  recommendedRecipes: RecommendedRecipe[]
  title: string
  content: string
  priority: string
  priorityName: string
  status: string
  statusName: string
  handledBy?: number
  handledByName?: string
  handledAt?: string
  handleRemark?: string
  createdAt: string
}

export interface RecommendedRecipe {
  id: number
  recipeCode?: string
  recipeName: string
  categoryName?: string
  imageUrl?: string
  estimatedCost?: number
}

export interface NotificationQuery {
  pageNum: number
  pageSize: number
  notificationType?: string
  status?: string
  priority?: string
  orgId?: number
  keyword?: string
}

export interface NotificationStats {
  total: number
  unread: number
  highPriority: number
  expiring: number
  expired: number
}

/** 获取通知列表 */
export const getNotifications = (params: Partial<NotificationQuery>): Promise<ApiResponse<PageResponse<MaterialNotification>>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.getNotifications(params)
  }
  return get('/v1/recipe/notifications', params, silent)
}

/** 获取通知详情 */
export const getNotificationDetail = (id: number): Promise<ApiResponse<MaterialNotification>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.getNotificationDetail(id)
  }
  return get(`/v1/recipe/notifications/${id}`, undefined, silent)
}

/** 标记为已读 */
export const markAsRead = (id: number): Promise<ApiResponse<null>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.markAsRead(id)
  }
  return put(`/v1/recipe/notifications/${id}/read`, undefined, silent)
}

/** 批量标记为已读 */
export const batchMarkAsRead = (ids: number[]): Promise<ApiResponse<null>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.batchMarkAsRead(ids)
  }
  return put('/v1/recipe/notifications/batch-read', ids, silent)
}

/** 标记为已处理 */
export const markAsHandled = (id: number, remark?: string, handlerId?: number): Promise<ApiResponse<null>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.markAsHandled(id, remark, handlerId)
  }
  return put(`/v1/recipe/notifications/${id}/handle`, null, { ...silent, params: { remark, handlerId } })
}

/** 忽略通知 */
export const dismissNotification = (id: number): Promise<ApiResponse<null>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.dismissNotification(id)
  }
  return put(`/v1/recipe/notifications/${id}/dismiss`, undefined, silent)
}

/** 获取未读数量 */
export const getUnreadCount = (orgId?: number): Promise<ApiResponse<{ count: number }>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.getUnreadCount(orgId)
  }
  return get('/v1/recipe/notifications/unread-count', { orgId }, silent)
}

/** 获取高优先级通知 */
export const getHighPriorityNotifications = (orgId?: number, limit?: number): Promise<ApiResponse<MaterialNotification[]>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.getHighPriorityNotifications(orgId, limit)
  }
  return get('/v1/recipe/notifications/high-priority', { orgId, limit }, silent)
}

/** 获取通知统计 */
export const getNotificationStats = (orgId?: number): Promise<ApiResponse<NotificationStats>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.getNotificationStats(orgId)
  }
  return get('/v1/recipe/notifications/stats', { orgId }, silent)
}

/** 手动扫描临期物料 */
export const scanExpiringMaterials = (days?: number): Promise<ApiResponse<{ generatedCount: number }>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.scanExpiringMaterials(days)
  }
  return post('/v1/recipe/notifications/scan', null, { ...silent, params: { days } })
}
