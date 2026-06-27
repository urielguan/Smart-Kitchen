import { get } from '@/api'
import type { ApiResponse } from '@/types/api'
import type { SupplierQualificationAlert } from '@/types/supplier-alert'
import { isDevPreviewMockEnabled } from '@/dev-preview/env'
import { devPreviewNotificationApi } from '@/dev-preview/notification'

const silent = { silentError: true }

/** 获取当前用户可见的供应商资质临期提醒 */
export const getSupplierQualificationAlerts = (
  limit = 10
): Promise<ApiResponse<SupplierQualificationAlert[]>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.getSupplierQualificationAlerts(limit)
  }
  return get('/v1/scm/supplier-qualification-alerts', { limit }, silent)
}

/** 获取当前用户可见的供应商资质临期提醒数量 */
export const getSupplierQualificationAlertCount = (): Promise<ApiResponse<{ count: number }>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewNotificationApi.getSupplierQualificationAlertCount()
  }
  return get('/v1/scm/supplier-qualification-alerts/count', undefined, silent)
}
