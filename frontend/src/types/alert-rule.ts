/** 告警规则 */
export interface AlertRule {
  id: number
  ruleName: string
  ruleType: string
  ruleTypeName: string
  deviceType: string | null
  deviceTypeName: string
  deviceIds: string | null
  deviceNames: string[] | null
  materialIds: string | null
  materialNames: string[] | null
  conditionJson: string
  alertLevel: string
  alertLevelName: string
  notifyChannels: string | null
  notifyUsers: string | null
  dispatchScopeRoles: string | null
  dispatchScopeRoleNames: string[] | null
  isEnabled: number
  autoDispatch: number
  orgId: number | null
  createdBy: number | null
  createdAt: string | null
  updatedBy: number | null
  updatedAt: string | null
}

/** 告警规则查询参数 */
export interface AlertRuleQuery {
  pageNum?: number
  pageSize?: number
  orgId?: number
  ruleName?: string
  ruleType?: string
  deviceType?: string
  alertLevel?: string
  isEnabled?: number
}

/** 创建告警规则DTO */
export interface AlertRuleCreateDTO {
  ruleName: string
  ruleType: string
  deviceType?: string
  deviceIds?: string
  materialIds?: string
  conditionJson: string
  alertLevel: string
  notifyChannels?: string
  notifyUsers?: string
  dispatchScopeRoles?: string
  isEnabled?: number
  autoDispatch?: number
}

/** 更新告警规则DTO */
export interface AlertRuleUpdateDTO {
  ruleName: string
  ruleType: string
  deviceType?: string
  deviceIds?: string
  materialIds?: string
  conditionJson: string
  alertLevel: string
  notifyChannels?: string
  notifyUsers?: string
  dispatchScopeRoles?: string
  isEnabled?: number
  autoDispatch?: number
}

/** 规则类型选项 */
export const RULE_TYPES = [
  { value: 'threshold', label: '阈值告警' },
  { value: 'offline', label: '离线告警' },
  { value: 'ai_event', label: 'AI事件告警' },
  { value: 'material', label: '物料告警' },
] as const

/** 告警级别选项（规则配置用） */
export const RULE_ALERT_LEVELS = [
  { value: 'critical', label: '严重', color: '#F56C6C' },
  { value: 'error', label: '错误', color: '#F56C6C' },
  { value: 'warning', label: '警告', color: '#E6A23C' },
  { value: 'info', label: '提示', color: '#909399' },
] as const

/** 阈值条件指标选项 */
export const THRESHOLD_METRICS = [
  { value: 'temperature', label: '温度' },
  { value: 'humidity', label: '湿度' },
  { value: 'gas', label: '气体浓度' },
] as const

/** 比较运算符选项 */
export const COMPARE_OPERATORS = [
  { value: '>', label: '大于 (>)' },
  { value: '<', label: '小于 (<)' },
  { value: '>=', label: '大于等于 (>=)' },
  { value: '<=', label: '小于等于 (<=)' },
] as const

/** 逻辑连接符选项 */
export const LOGIC_OPERATORS = [
  { value: 'and', label: '并且 (AND)' },
  { value: 'or', label: '或者 (OR)' },
] as const

/** 通知渠道选项 */
export const NOTIFY_CHANNELS = [
  { value: 'system', label: '站内信' },
  { value: 'sms', label: '短信' },
  { value: 'email', label: '邮件' },
] as const
