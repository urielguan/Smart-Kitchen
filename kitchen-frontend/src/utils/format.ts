export const formatPercent = (value?: number | null) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '0%'
  }

  return `${Number(value).toFixed(0)}%`
}

export const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '--'
  }

  return value.replace('T', ' ').slice(0, 16)
}

export const formatMealType = (mealType?: string | null) => {
  const map: Record<string, string> = {
    breakfast: '早餐',
    lunch: '午餐',
    dinner: '晚餐',
    supper: '宵夜'
  }

  return mealType ? map[mealType] || mealType : '--'
}

export const formatTaskStatus = (status?: string | null) => {
  const map: Record<string, string> = {
    pending: '待烹饪',
    in_progress: '烹饪中',
    completed: '已完成',
    cancelled: '已取消',
    archived: '已归档'
  }

  return status ? map[status] || status : '--'
}

export const createOfflineActionId = () => `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
