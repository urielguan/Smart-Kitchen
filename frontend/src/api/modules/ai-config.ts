import { del, get, post, put } from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { AiRequestLogItem, AiServiceConfigForm, AiServiceConfigItem, AiServiceConfigQuery } from '@/types/ai-config'

const BASE = '/v1/sys/ai-services'

export const aiConfigApi = {
  page(params: AiServiceConfigQuery): Promise<ApiResponse<PageResponse<AiServiceConfigItem>>> {
    return get(BASE, params)
  },
  detail(id: number): Promise<ApiResponse<AiServiceConfigItem>> {
    return get(`${BASE}/${id}`)
  },
  create(data: AiServiceConfigForm): Promise<ApiResponse<{ id: number }>> {
    return post(BASE, data)
  },
  update(id: number, data: AiServiceConfigForm): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, data)
  },
  remove(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`)
  },
  changeStatus(id: number, status: 'active' | 'inactive'): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}/status`, { status })
  },
  test(id: number): Promise<ApiResponse<{ success: boolean; message: string }>> {
    return post(`${BASE}/${id}/test`)
  },
  logs(id: number, pageNum = 1, pageSize = 20): Promise<ApiResponse<PageResponse<AiRequestLogItem>>> {
    return get(`${BASE}/${id}/logs`, { pageNum, pageSize })
  }
}
