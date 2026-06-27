import { get } from '@/api'
import type { ApiResponse } from '@/types'

export interface SysOrgVO {
  id: number
  name: string
  code?: string
  orgType?: string
  parentId?: number | null
}

export const getOrgList = async (): Promise<ApiResponse<{ list: SysOrgVO[] }>> => {
  return await get<{ list: SysOrgVO[] }>('/v1/sys/organizations', { pageSize: 999, status: 'active' })
}

export const getEmployeeList = async (params?: Record<string, any>): Promise<ApiResponse<any>> => {
  return await get('/v1/sys/employees', { pageSize: 999, status: 'active', ...params })
}
