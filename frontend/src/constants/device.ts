/** 设备类型选项 */
export const DEVICE_TYPES = [
  { value: 'camera', label: '监控摄像头', icon: '📷' },
  { value: 'sensor', label: '温湿度传感器', icon: '🌡️' },
  { value: 'scale', label: '食材检测设备', icon: '⚖️' },
  { value: 'gas_detector', label: '气体监测设备', icon: '💨' },
  { value: 'sample_terminal', label: '智能留样设备', icon: '🔬' },
  { value: 'health_terminal', label: '智能晨检设备', icon: '🏥' },
]

/** 在线状态选项 */
export const ONLINE_STATUS_OPTIONS = [
  { value: 'online', label: '在线', type: 'success' as const },
  { value: 'offline', label: '离线', type: 'info' as const },
  { value: 'fault', label: '故障', type: 'danger' as const },
]

/** 设备状态选项 */
export const DEVICE_STATUS_OPTIONS = [
  { value: 'active', label: '启用', type: 'success' as const },
  { value: 'inactive', label: '停用', type: 'info' as const },
  { value: 'maintenance', label: '维护中', type: 'warning' as const },
]

/** 设备类型名称映射 */
export const DEVICE_TYPE_MAP: Record<string, string> = Object.fromEntries(
  DEVICE_TYPES.map(t => [t.value, t.label])
)

/** 在线状态名称映射 */
export const ONLINE_STATUS_MAP: Record<string, string> = Object.fromEntries(
  ONLINE_STATUS_OPTIONS.map(s => [s.value, s.label])
)

/** 设备状态名称映射 */
export const DEVICE_STATUS_MAP: Record<string, string> = Object.fromEntries(
  DEVICE_STATUS_OPTIONS.map(s => [s.value, s.label])
)

export const DEVICE_BUSINESS_STATUS_ROLE_KEYWORDS = ['设备管理员', '组织管理员', '运维', '管理员']
export const DEVICE_ONLINE_STATUS_ROLE_KEYWORDS = ['运维', '管理员']

export const hasDeviceRoleKeyword = (roles: string[] | undefined, keywords: string[]) => {
  if (!roles || roles.length === 0) return false
  return roles.some(role => keywords.some(keyword => role.includes(keyword)))
}

export const getAllowedBusinessStatusOptions = (currentStatus?: string | null) => {
  if (!currentStatus) return DEVICE_STATUS_OPTIONS
  switch (currentStatus) {
    case 'active':
      return DEVICE_STATUS_OPTIONS.filter(option => ['active', 'inactive', 'maintenance'].includes(option.value))
    case 'inactive':
      return DEVICE_STATUS_OPTIONS.filter(option => ['inactive', 'active'].includes(option.value))
    case 'maintenance':
      return DEVICE_STATUS_OPTIONS.filter(option => ['maintenance', 'active', 'inactive'].includes(option.value))
    default:
      return DEVICE_STATUS_OPTIONS.filter(option => option.value === currentStatus)
  }
}

export const getAllowedManualOnlineStatusOptions = (currentStatus?: string | null) => {
  if (!currentStatus) return []
  switch (currentStatus) {
    case 'online':
      return ONLINE_STATUS_OPTIONS.filter(option => option.value === 'fault')
    case 'offline':
      return ONLINE_STATUS_OPTIONS.filter(option => option.value === 'fault')
    case 'fault':
      return ONLINE_STATUS_OPTIONS.filter(option => ['online', 'offline'].includes(option.value))
    default:
      return []
  }
}

/** 设备特有配置字段 key → 中文标签映射 */
export const DEVICE_CONFIG_LABEL_MAP: Record<string, string> = {
  // 监控摄像头
  area: '监控区域',
  aiAlgorithm: 'AI算法',
  rtspUrl: 'RTSP地址',
  frameRate: '帧率',
  streamUrl: '流地址',
  alertCount: '告警数量',
  ptzSupport: '云台支持',
  resolution: '分辨率',
  recordingEnabled: '录像启用',
  gateway: '网关地址',
  subnetMask: '子网掩码',
  sipServer: 'SIP服务器',
  sipPort: 'SIP端口',
  sipUsername: 'SIP用户名',
  sipPassword: 'SIP密码',
  channelNo: '通道号',
  // 温湿度传感器
  location: '监测位置',
  collectFrequency: '采集频率',
  // 食材检测设备
  detectCategory: '检测品类',
  calibrationTime: '校准时间',
  // 气体监测设备
  gasType: '监测气体',
  alertThreshold: '告警阈值',
  // 智能留样设备
  cookingArea: '烹饪区域',
  accuracy: '称重精度(%)',
  // 智能晨检设备
  tempThreshold: '体温阈值(℃)',
}
