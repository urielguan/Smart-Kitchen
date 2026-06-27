/**
 * 用餐时段自动检测工具
 * 根据当前时间判断处于哪个餐次，供 TopBar 显示和筛选器默认值使用
 */

export interface MealPeriod {
  type: 'breakfast' | 'lunch' | 'dinner' | 'supper'
  label: string
  timeRange: string
}

const MEAL_PERIODS: MealPeriod[] = [
  { type: 'breakfast', label: '早餐', timeRange: '06:00 - 11:00' },
  { type: 'lunch',     label: '午餐', timeRange: '11:00 - 17:00' },
  { type: 'dinner',    label: '晚餐', timeRange: '17:00 - 21:00' },
  { type: 'supper',    label: '宵夜', timeRange: '21:00 - 06:00' },
]

/** 根据当前时间返回所处用餐时段 */
export function getCurrentMealPeriod(now: Date = new Date()): MealPeriod {
  const hour = now.getHours()
  if (hour >= 6 && hour < 11) return MEAL_PERIODS[0]
  if (hour >= 11 && hour < 17) return MEAL_PERIODS[1]
  if (hour >= 17 && hour < 21) return MEAL_PERIODS[2]
  return MEAL_PERIODS[3]
}

/** 返回所有餐次（供下拉选择使用） */
export function getMealPeriods(): MealPeriod[] {
  return MEAL_PERIODS
}

/** 根据餐次类型返回时间范围文本 */
export function getMealTimeRange(type: string): string {
  return MEAL_PERIODS.find((p) => p.type === type)?.timeRange ?? ''
}
