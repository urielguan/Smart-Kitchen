import type {
  ReviewSource, ComplaintType, ComplaintSource, ComplaintStatus,
  DispatchType, DispatchStatus, Priority, Satisfaction, WorkOrderAction
} from '@/types/evaluation'

// ==================== 评价相关常量 ====================

/** 评价来源选项 */
export const REVIEW_SOURCE_OPTIONS: { label: string; value: ReviewSource }[] = [
  { label: '用餐评价', value: 'meal' },
  { label: '监管反馈', value: 'supervision' },
  { label: '人工录入', value: 'manual' }
]

/** 评价来源映射 */
export const REVIEW_SOURCE_MAP: Record<ReviewSource, string> = {
  meal: '用餐评价',
  supervision: '监管反馈',
  manual: '人工录入'
}

/** 餐次选项 */
export const MEAL_TYPE_OPTIONS = [
  { label: '早餐', value: 'breakfast' },
  { label: '午餐', value: 'lunch' },
  { label: '晚餐', value: 'dinner' }
]

/** 餐次映射 */
export const MEAL_TYPE_MAP: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐'
}

/** 评分选项 */
export const SCORE_OPTIONS: { label: string; value: number }[] = [
  { label: '1分', value: 1 },
  { label: '2分', value: 2 },
  { label: '3分', value: 3 },
  { label: '4分', value: 4 },
  { label: '5分', value: 5 }
]

/** 评分颜色映射 */
export const SCORE_COLOR_MAP: Record<number, string> = {
  1: '#F56C6C',
  2: '#E6A23C',
  3: '#909399',
  4: '#67C23A',
  5: '#67C23A'
}

/** 评分名称映射 */
export const SCORE_NAME_MAP: Record<number, string> = {
  1: '一星',
  2: '二星',
  3: '三星',
  4: '四星',
  5: '五星'
}

/** 评分等级选项（用于搜索筛选） */
export const SCORE_LEVEL_OPTIONS: { label: string; value: string }[] = [
  { label: '好评（4-5星）', value: 'good' },
  { label: '中评（3星）', value: 'medium' },
  { label: '差评（1-2星）', value: 'bad' }
]

// ==================== 投诉相关常量 ====================

/** 投诉类型选项 */
export const COMPLAINT_TYPE_OPTIONS: { label: string; value: ComplaintType }[] = [
  { label: '食品问题', value: 'food' },
  { label: '服务问题', value: 'service' },
  { label: '卫生问题', value: 'hygiene' },
  { label: '其他', value: 'other' }
]

/** 投诉类型映射 */
export const COMPLAINT_TYPE_MAP: Record<ComplaintType, { label: string; type: string }> = {
  food: { label: '食品问题', type: 'danger' },
  service: { label: '服务问题', type: 'warning' },
  hygiene: { label: '卫生问题', type: 'danger' },
  other: { label: '其他', type: 'info' }
}

/** 投诉来源选项 */
export const COMPLAINT_SOURCE_OPTIONS: { label: string; value: ComplaintSource }[] = [
  { label: '用餐评价', value: 'meal' },
  { label: '监管反馈', value: 'supervision' },
  { label: '人工录入', value: 'manual' }
]

/** 投诉来源映射 */
export const COMPLAINT_SOURCE_MAP: Record<ComplaintSource, string> = {
  meal: '用餐评价',
  supervision: '监管反馈',
  manual: '人工录入'
}

/** 投诉状态选项 */
export const COMPLAINT_STATUS_OPTIONS: { label: string; value: ComplaintStatus }[] = [
  { label: '待处理', value: 'pending' },
  { label: '已派单', value: 'dispatched' },
  { label: '处理中', value: 'processing' },
  { label: '已闭环', value: 'closed' }
]

/** 投诉状态映射 */
export const COMPLAINT_STATUS_MAP: Record<ComplaintStatus, { label: string; type: string }> = {
  pending: { label: '待处理', type: 'warning' },
  dispatched: { label: '已派单', type: 'primary' },
  processing: { label: '处理中', type: 'info' },
  closed: { label: '已闭环', type: 'success' }
}

/** 满意度选项 */
export const SATISFACTION_OPTIONS: { label: string; value: Satisfaction }[] = [
  { label: '满意', value: 'satisfied' },
  { label: '一般', value: 'neutral' },
  { label: '不满意', value: 'dissatisfied' }
]

/** 满意度映射 */
export const SATISFACTION_MAP: Record<Satisfaction, { label: string; type: string }> = {
  satisfied: { label: '满意', type: 'success' },
  neutral: { label: '一般', type: 'info' },
  dissatisfied: { label: '不满意', type: 'danger' }
}

// ==================== 派单相关常量 ====================

/** 优先级选项 */
export const PRIORITY_OPTIONS: { label: string; value: Priority }[] = [
  { label: '高', value: 'high' },
  { label: '中', value: 'medium' },
  { label: '低', value: 'low' }
]

/** 优先级映射 */
export const PRIORITY_MAP: Record<Priority, { label: string; type: string }> = {
  high: { label: '高', type: 'danger' },
  medium: { label: '中', type: 'warning' },
  low: { label: '低', type: 'info' }
}

/** 派单方式选项 */
export const DISPATCH_TYPE_OPTIONS: { label: string; value: DispatchType }[] = [
  { label: '自动派单', value: 'auto' },
  { label: '人工派单', value: 'manual' }
]

/** 派单方式映射 */
export const DISPATCH_TYPE_MAP: Record<DispatchType, { label: string; type: string }> = {
  auto: { label: '自动派单', type: 'primary' },
  manual: { label: '人工派单', type: 'warning' }
}

/** 派单状态选项 */
export const DISPATCH_STATUS_OPTIONS: { label: string; value: DispatchStatus }[] = [
  { label: '待处理', value: 'pending' },
  { label: '处理中', value: 'processing' },
  { label: '已完成', value: 'completed' },
  { label: '已取消', value: 'cancelled' }
]

/** 派单状态映射 */
export const DISPATCH_STATUS_MAP: Record<DispatchStatus, { label: string; type: string }> = {
  pending: { label: '待处理', type: 'warning' },
  processing: { label: '处理中', type: 'primary' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'info' }
}

/** 工单操作类型映射 */
export const WORK_ORDER_ACTION_MAP: Record<WorkOrderAction, string> = {
  dispatch: '派单',
  reassign: '改派',
  process: '处理',
  complete: '完成',
  cancel: '取消'
}

// ==================== Tab 相关常量 ====================

/** 评价管理页面Tab类型 */
export type EvaluationTabType = 'review' | 'complaint' | 'dispatch'

/** 评价管理页面Tab选项 */
export const EVALUATION_TAB_OPTIONS: { label: string; value: EvaluationTabType }[] = [
  { label: '评价列表', value: 'review' },
  { label: '投诉列表', value: 'complaint' },
  { label: '派单记录', value: 'dispatch' }
]
