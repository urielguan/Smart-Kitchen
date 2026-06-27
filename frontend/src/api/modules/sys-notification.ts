import { get, put, del, post } from '@/api'
import type { RequestConfig } from '@/api'

const BASE = '/v1/sys/notifications'

export interface ExecutableAction {
  label: string
  route: string
}

export interface SysNotificationVO {
  id: number
  messageId: string
  category: string
  categoryName: string
  subCategory: string
  subCategoryName: string
  title: string
  summary: string
  riskLevel: string
  riskLevelName: string
  readStatus: string
  readStatusName: string
  processStatus: string
  processStatusName: string
  sourceModule: string
  relatedBusinessId: number
  relatedBusinessType: string
  sendTime: string
  timeDisplay: string
  executableActions: string
}

export interface SysNotificationDetailVO extends SysNotificationVO {
  body: string
  relatedOrgId: number
  relatedWarehouseId: number
  relatedMaterialId: number
  sourceSnapshot: any
  expiryTime: string
  allowDelete: boolean
}

export interface NotificationStatsVO {
  totalUnread: number
  categoryCounts: Record<string, number>
}

export interface NotificationQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  category?: string
  subCategory?: string
  readStatus?: string
  processStatus?: string
  riskLevel?: string
  startTime?: string
  endTime?: string
}

export const sysNotificationApi = {
  /** 分页列表 */
  getList(params: Partial<NotificationQuery>, config?: RequestConfig) {
    return get(BASE, params, config)
  },

  /** 未读统计 */
  getStats(config?: RequestConfig) {
    return get(`${BASE}/stats`, undefined, config)
  },

  /** 未读数量 */
  getUnreadCount(config?: RequestConfig) {
    return get(`${BASE}/unread-count`, undefined, config)
  },

  /** 详情 */
  getDetail(id: number) {
    return get(`${BASE}/${id}`)
  },

  /** 标记已读 */
  markAsRead(id: number) {
    return put(`${BASE}/${id}/read`)
  },

  /** 标记未读 */
  markAsUnread(id: number) {
    return put(`${BASE}/${id}/unread`)
  },

  /** 批量标记已读 */
  batchMarkAsRead(ids: number[]) {
    return put(`${BASE}/batch-read`, ids)
  },

  /** 批量删除 */
  batchDelete(ids: number[]) {
    return del(`${BASE}/batch`, undefined, { data: ids })
  }
}
