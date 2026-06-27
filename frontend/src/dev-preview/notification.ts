import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  MaterialNotification,
  NotificationQuery,
  NotificationStats,
} from '@/api/modules/notification'
import type { SupplierQualificationAlert } from '@/types/supplier-alert'

const nowIso = () => new Date().toISOString()

const success = <T>(data: T, message = 'SUCCESS'): ApiResponse<T> => ({
  code: 'SUCCESS',
  message,
  data,
  timestamp: nowIso(),
})

const emptyNotificationPage = (params: Partial<NotificationQuery>): PageResponse<MaterialNotification> => ({
  list: [],
  total: 0,
  pageNum: params.pageNum || 1,
  pageSize: params.pageSize || 20,
})

export const devPreviewNotificationApi = {
  async getNotifications(params: Partial<NotificationQuery>): Promise<ApiResponse<PageResponse<MaterialNotification>>> {
    return success(emptyNotificationPage(params))
  },

  async getNotificationDetail(_id: number): Promise<ApiResponse<MaterialNotification>> {
    throw new Error('本地预览模式下暂无通知详情')
  },

  async markAsRead(_id: number): Promise<ApiResponse<null>> {
    return success(null)
  },

  async batchMarkAsRead(_ids: number[]): Promise<ApiResponse<null>> {
    return success(null)
  },

  async markAsHandled(_id: number, _remark?: string, _handlerId?: number): Promise<ApiResponse<null>> {
    return success(null)
  },

  async dismissNotification(_id: number): Promise<ApiResponse<null>> {
    return success(null)
  },

  async getUnreadCount(_orgId?: number): Promise<ApiResponse<{ count: number }>> {
    return success({ count: 0 })
  },

  async getHighPriorityNotifications(_orgId?: number, _limit?: number): Promise<ApiResponse<MaterialNotification[]>> {
    return success([])
  },

  async getNotificationStats(_orgId?: number): Promise<ApiResponse<NotificationStats>> {
    return success({
      total: 0,
      unread: 0,
      highPriority: 0,
      expiring: 0,
      expired: 0,
    })
  },

  async scanExpiringMaterials(_days?: number): Promise<ApiResponse<{ generatedCount: number }>> {
    return success({ generatedCount: 0 })
  },

  async getSupplierQualificationAlerts(_limit = 10): Promise<ApiResponse<SupplierQualificationAlert[]>> {
    return success([])
  },

  async getSupplierQualificationAlertCount(): Promise<ApiResponse<{ count: number }>> {
    return success({ count: 0 })
  },
}
