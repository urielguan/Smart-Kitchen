import { get, post } from '../index'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  PurchaseDemandForecastDashboard,
  PurchaseDemandForecastGeneratePayload,
  PurchaseDemandForecastLinkedPlanRecord,
  PurchaseDemandForecastMaterialLinkage,
  PurchaseDemandForecastPlanPrefill,
  PurchaseDemandForecastQuery,
  PurchaseDemandForecastRecord,
} from '@/types/purchase-demand-forecast'

const BASE = '/v1/scm/purchase-demand-forecasts'

export const purchaseDemandForecastApi = {
  generate(data: PurchaseDemandForecastGeneratePayload): Promise<ApiResponse<PurchaseDemandForecastRecord>> {
    return post(`${BASE}/generate`, data)
  },

  getList(params: PurchaseDemandForecastQuery): Promise<ApiResponse<PageResponse<PurchaseDemandForecastRecord>>> {
    return get(BASE, params)
  },

  getDetail(id: number): Promise<ApiResponse<PurchaseDemandForecastRecord>> {
    return get(`${BASE}/${id}`)
  },

  getDashboard(orgId?: number): Promise<ApiResponse<PurchaseDemandForecastDashboard>> {
    return get(`${BASE}/dashboard`, orgId ? { orgId } : undefined)
  },

  refreshAnalytics(orgId?: number): Promise<ApiResponse<PurchaseDemandForecastDashboard>> {
    return post(`${BASE}/refresh-analytics${orgId ? `?orgId=${orgId}` : ''}`)
  },

  getLinkedPlans(id: number): Promise<ApiResponse<PurchaseDemandForecastLinkedPlanRecord[]>> {
    return get(`${BASE}/${id}/linked-plans`)
  },

  getMaterialLinkage(params: {
    forecastNo: string
    excludePlanId?: number
  }): Promise<ApiResponse<PurchaseDemandForecastMaterialLinkage | null>> {
    return get(`${BASE}/material-linkage`, params)
  },

  getPurchasePlanPrefill(detailIds: number[]): Promise<ApiResponse<PurchaseDemandForecastPlanPrefill>> {
    return post(`${BASE}/purchase-plan-prefill`, { detailIds })
  },
}

export default purchaseDemandForecastApi
