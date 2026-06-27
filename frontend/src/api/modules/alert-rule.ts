import { get, post, put, del } from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  AlertRule,
  AlertRuleQuery,
  AlertRuleCreateDTO,
  AlertRuleUpdateDTO,
} from '@/types/alert-rule'

const BASE = '/v1/device/alert-rules'

export const alertRuleApi = {
  /** 分页查询告警规则 */
  getList(params: AlertRuleQuery): Promise<ApiResponse<PageResponse<AlertRule>>> {
    return get(BASE, params)
  },

  /** 获取告警规则详情 */
  getDetail(id: number): Promise<ApiResponse<AlertRule>> {
    return get(`${BASE}/${id}`)
  },

  /** 创建告警规则 */
  create(data: AlertRuleCreateDTO): Promise<ApiResponse<number>> {
    return post(BASE, data)
  },

  /** 更新告警规则 */
  update(id: number, data: AlertRuleUpdateDTO): Promise<ApiResponse<void>> {
    return put(`${BASE}/${id}`, data)
  },

  /** 删除告警规则 */
  deleteRule(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/${id}`)
  },

  /** 切换启用/禁用状态 */
  toggleEnabled(id: number): Promise<ApiResponse<void>> {
    return post(`${BASE}/${id}/toggle-enabled`)
  },
}

export default alertRuleApi
