import type { StockStatus } from '@/types/material'

/** 物料类别选项 */
export const MATERIAL_CATEGORIES: { label: string; value: string }[] = [
  { label: '蔬菜', value: '蔬菜' },
  { label: '肉类', value: '肉类' },
  { label: '水产', value: '水产' },
  { label: '调料', value: '调料' },
  { label: '粮油', value: '粮油' },
  { label: '乳制品', value: '乳制品' }
]

/** 库存状态选项 */
export const STOCK_STATUS_OPTIONS: { label: string; value: StockStatus }[] = [
  { label: '正常', value: 'normal' },
  { label: '库存不足', value: 'low' },
  { label: '库存积压', value: 'high' },
  { label: '已过期', value: 'expired' }
]

/** 库存状态映射 */
export const STOCK_STATUS_MAP: Record<StockStatus, { label: string; type: string }> = {
  normal: { label: '正常', type: 'success' },
  low: { label: '库存不足', type: 'warning' },
  high: { label: '库存积压', type: 'primary' },
  expired: { label: '已过期', type: 'danger' }
}

/** 表格列配置 */
export const MATERIAL_TABLE_COLUMNS = [
  { prop: 'imageUrl', label: '图片', width: 80 },
  { prop: 'materialName', label: '物料名称', minWidth: 120 },
  { prop: 'materialCode', label: '物料编码', width: 120 },
  { prop: 'materialSpec', label: '规格', width: 100 },
  { prop: 'categoryName', label: '类别', width: 100 },
  { prop: 'shelfLifeDays', label: '保质期(天)', width: 100 },
  { prop: 'currentStock', label: '当前库存', width: 100 },
  { prop: 'stockRange', label: '库存范围', width: 120 },
  { prop: 'stockStatus', label: '库存状态', width: 100 },
  { prop: 'action', label: '操作', width: 150, fixed: 'right' }
]