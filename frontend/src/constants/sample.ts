/** 留样状态选项 */
export const SAMPLE_STATUS_OPTIONS = [
  { value: 'pending_sample', label: '待留样', type: 'info' as const },
  { value: 'sampled', label: '已留样', type: 'primary' as const },
  { value: 'evaluated', label: '已评估', type: 'success' as const },
  { value: 'pending_disposal', label: '待销样', type: 'warning' as const },
  { value: 'disposed', label: '已销样', type: 'success' as const },
  { value: 'overdue', label: '超期未销', type: 'danger' as const },
  { value: 'voided', label: '已作废', type: 'info' as const },
  { value: 'archived', label: '已归档', type: 'info' as const },
]

/** 留样状态映射 */
export const SAMPLE_STATUS_MAP: Record<string, { label: string; type: string }> = {
  pending_sample: { label: '待留样', type: 'info' },
  sampled: { label: '已留样', type: 'primary' },
  evaluated: { label: '已评估', type: 'success' },
  pending_disposal: { label: '待销样', type: 'warning' },
  disposed: { label: '已销样', type: 'success' },
  overdue: { label: '超期未销', type: 'danger' },
  voided: { label: '已作废', type: 'info' },
  archived: { label: '已归档', type: 'info' },
}

/** 餐次选项 */
export const MEAL_TYPE_OPTIONS = [
  { value: 'breakfast', label: '早餐' },
  { value: 'lunch', label: '午餐' },
  { value: 'dinner', label: '晚餐' },
  { value: 'supper', label: '宵夜' },
]

/** 餐次名称映射 */
export const MEAL_TYPE_MAP: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  supper: '宵夜',
}

/** 留样来源类型选项 */
export const SAMPLE_RECORD_ORIGIN_OPTIONS = [
  { value: 'manual_history', label: '历史异常补录' },
  { value: 'offline_delayed', label: '离线迟传补录' }
]

/** 销样手工补录场景 */
export const SAMPLE_MANUAL_DISPOSAL_SCENE_OPTIONS = [
  { value: 'system_missing', label: '系统自动生成销样漏单' },
  { value: 'interface_sync_exception', label: '接口异常未同步' },
  { value: 'device_offline', label: '硬件离线断网漏传' },
  { value: 'history_migration_fix', label: '历史数据迁移纠错' },
  { value: 'ops_closure_repair', label: '运维排查补闭环' }
]

/** 留样来源类型映射 */
export const SAMPLE_RECORD_ORIGIN_MAP: Record<string, string> = {
  auto: '系统自动生成',
  manual_daily: '日常手工补录',
  manual_history: '历史异常补录',
  offline_delayed: '离线迟传补录',
  system_backfill: '系统漏单回溯补录'
}

/** 销样来源类型映射 */
export const SAMPLE_DISPOSAL_SOURCE_MAP: Record<string, string> = {
  system_auto: '系统自动生成',
  manual_exception_supplement: '手工异常补录'
}

/** 销样手工补录场景映射 */
export const SAMPLE_MANUAL_DISPOSAL_SCENE_MAP: Record<string, string> = {
  system_missing: '系统自动生成销样漏单',
  interface_sync_exception: '接口异常未同步',
  device_offline: '硬件离线断网漏传',
  history_migration_fix: '历史数据迁移纠错',
  ops_closure_repair: '运维排查补闭环'
}
