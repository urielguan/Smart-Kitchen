import { get } from '@/api'
import type { RequestConfig } from '@/api'
import type { ApiResponse } from '@/types/api'
import type { DashboardOverview, RegulatoryDashboardData, RegulatoryQuickRange, TimeRange } from '@/types/dashboard'

/** 获取数据看板总览 */
export const getDashboardOverview = (timeRange: TimeRange): Promise<ApiResponse<DashboardOverview>> => {
  return get('/v1/recipe/dashboard/overview', { timeRange })
}

export interface RegulatoryDashboardSnapshotParams {
  quickRange: RegulatoryQuickRange
  organization?: string
  canteen?: string
  area?: string
  startDate?: string
  endDate?: string
}

/** 获取数据监管看板首页快照 */
export const getRegulatoryDashboardSnapshot = (
  params: RegulatoryDashboardSnapshotParams,
  config?: RequestConfig
): Promise<ApiResponse<RegulatoryDashboardData>> => {
  return get('/v1/dashboard/regulatory/home', params, config)
}
