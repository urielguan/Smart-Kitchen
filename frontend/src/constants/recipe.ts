import type { RecipeCategory, RecipeStatus } from '@/types/recipe'

/** 菜谱类别映射 */
export const RECIPE_CATEGORY_MAP: Record<RecipeCategory, { label: string; color: string }> = {
  staple: { label: '主食', color: '#E6A23C' },
  main_dish: { label: '荤菜', color: '#F56C6C' },
  soup: { label: '汤品', color: '#409EFF' },
  side_dish: { label: '素菜', color: '#67C23A' },
  dessert: { label: '甜点', color: '#909399' }
}

/** 菜谱类别选项 */
export const RECIPE_CATEGORIES: { label: string; value: RecipeCategory }[] = [
  { label: '主食', value: 'staple' },
  { label: '荤菜', value: 'main_dish' },
  { label: '汤品', value: 'soup' },
  { label: '素菜', value: 'side_dish' },
  { label: '甜点', value: 'dessert' }
]

/** 菜谱状态选项 */
export const RECIPE_STATUS_OPTIONS: { label: string; value: RecipeStatus }[] = [
  { label: '启用', value: 'active' },
  { label: '停用', value: 'inactive' }
]

/** 菜谱状态映射 */
export const RECIPE_STATUS_MAP: Record<RecipeStatus, { label: string; type: string }> = {
  active: { label: '启用', type: 'success' },
  inactive: { label: '停用', type: 'info' }
}

/** 表格列配置 */
export const RECIPE_TABLE_COLUMNS = [
  { prop: 'imageUrl', label: '图片', width: 80 },
  { prop: 'menuName', label: '菜谱名称', minWidth: 120 },
  { prop: 'menuCode', label: '菜谱编码', width: 120 },
  { prop: 'categoryName', label: '菜谱类别', width: 100 },
  { prop: 'cookingTime', label: '烹饪时长(分)', width: 110 },
  { prop: 'cookingTemp', label: '目标温度(℃)', width: 110 },
  { prop: 'nutritionInfo', label: '营养成分', width: 180 },
  { prop: 'nutritionScore', label: '营养评分', width: 100 },
  { prop: 'status', label: '状态', width: 80 },
  { prop: 'updatedAt', label: '更新时间', width: 160 },
  { prop: 'action', label: '操作', width: 200, fixed: 'right' }
]

/** 营养素单位映射 */
export const NUTRITION_UNIT_MAP: Record<string, string> = {
  calories: '千卡',
  protein: 'g',
  carbohydrate: 'g',
  fat: 'g',
  sodium: 'mg',
  fiber: 'g'
}

/** 营养素名称映射 */
export const NUTRITION_NAME_MAP: Record<string, string> = {
  calories: '热量',
  protein: '蛋白质',
  carbohydrate: '碳水',
  fat: '脂肪',
  sodium: '钠',
  fiber: '膳食纤维'
}

/** 优先级映射 */
export const PRIORITY_MAP: Record<string, { label: string; type: string }> = {
  high: { label: '高', type: 'danger' },
  medium: { label: '中', type: 'warning' },
  low: { label: '低', type: 'info' }
}