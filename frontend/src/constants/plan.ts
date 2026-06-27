/** 餐次选项 */
export const MEAL_TYPE_OPTIONS = [
  { value: 'breakfast', label: '早餐' },
  { value: 'lunch', label: '午餐' },
  { value: 'dinner', label: '晚餐' },
  { value: 'supper', label: '宵夜' }
]

/** 计划状态选项 */
export const PLAN_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'pending', label: '待审核' },
  { value: 'approved', label: '已审核' },
  { value: 'rejected', label: '已拒绝' },
  { value: 'cooking', label: '烹饪中' },
  { value: 'completed', label: '已完成' }
]

/** 计划状态映射 */
export const PLAN_STATUS_MAP: Record<string, string> = {
  draft: '草稿',
  pending: '待审核',
  approved: '已审核',
  rejected: '已拒绝',
  cooking: '烹饪中',
  completed: '已完成'
}

/** 餐次映射 */
export const MEAL_TYPE_MAP: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  supper: '宵夜'
}

/**
 * 餐次标签配色（按设计稿 Tag 规范）
 * 早餐=Success 绿 / 午餐=Brand 蓝 / 晚餐=Warning 橙 / 宵夜=中性灰
 */
export interface MealTagStyle {
  background: string
  borderColor: string
  color: string
}

export const MEAL_TYPE_TAG_STYLE: Record<string, MealTagStyle> = {
  breakfast: { background: '#E3F9E9', borderColor: '#2BA471', color: '#2BA471' },
  lunch: { background: '#F2F3FF', borderColor: '#0052D9', color: '#0052D9' },
  dinner: { background: '#FFF1E9', borderColor: '#E37318', color: '#E37318' },
  supper: { background: '#EFEFEF', borderColor: '#C0C5CA', color: '#666666' }
}

/** 调整类型选项 */
export const ADJUST_TYPE_OPTIONS = [
  { value: 'add_recipe', label: '新增菜谱' },
  { value: 'remove_recipe', label: '移除菜谱' },
  { value: 'modify_servings', label: '调整份数' },
  { value: 'modify_count', label: '调整人数' },
  { value: 'other', label: '其他调整' }
]

/** 调整类型映射 */
export const ADJUST_TYPE_MAP: Record<string, string> = {
  add_recipe: '新增菜谱',
  remove_recipe: '移除菜谱',
  modify_servings: '调整份数',
  modify_count: '调整人数',
  other: '其他调整'
}

/** 调整申请状态选项 */
export const ADJUSTMENT_STATUS_OPTIONS = [
  { value: 'pending', label: '待审核' },
  { value: 'approved', label: '已通过' },
  { value: 'rejected', label: '已拒绝' }
]

/** 调整申请状态映射 */
export const ADJUSTMENT_STATUS_MAP: Record<string, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已拒绝'
}

/** 获取状态标签类型 */
export const getStatusType = (status: string): '' | 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    draft: 'info',
    pending: 'warning',
    approved: 'success',
    rejected: 'danger',
    cooking: '',
    completed: 'success'
  }
  return map[status] || 'info'
}

/** 获取营养达标率样式类 */
export const getNutritionClass = (rate: number): string => {
  if (rate >= 80) return 'excellent'
  if (rate >= 60) return 'good'
  return 'needs-improvement'
}

/** 格式化营养达标率 */
export const formatNutritionRate = (rate: number | undefined | null): string => {
  if (rate === undefined || rate === null) return '-'
  return `${rate.toFixed(1)}%`
}

/** 格式化成本 */
export const formatCost = (cost: number | undefined | null): string => {
  if (cost === undefined || cost === null) return '¥0.00'
  return `¥${cost.toFixed(2)}`
}
