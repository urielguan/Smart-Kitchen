import type { SupplierStatus } from '@/types/supplier'

/** 供应商类型选项（单选） */
export const SUPPLIER_TYPES = ['蔬菜', '肉类', '水产', '调料', '粮油', '乳制品', '饮料', '冷冻食品']

/** 供应商状态选项 */
export const SUPPLIER_STATUS_OPTIONS: { label: string; value: SupplierStatus }[] = [
  { label: '暂存', value: 'draft' },
  { label: '待审核', value: 'pending' },
  { label: '已审核', value: 'active' },
  { label: '已驳回', value: 'rejected' },
  { label: '禁用', value: 'disabled' },
  { label: '已注销', value: 'cancelled' }
]

/** 供应商状态映射 */
export const SUPPLIER_STATUS_MAP: Record<
  SupplierStatus,
  { label: string; tagType: '' | 'success' | 'warning' | 'danger' | 'info' }
> = {
  draft: { label: '暂存', tagType: 'info' },
  pending: { label: '待审核', tagType: 'warning' },
  active: { label: '已审核', tagType: 'success' },
  rejected: { label: '已驳回', tagType: 'danger' },
  disabled: { label: '禁用', tagType: 'info' },
  cancelled: { label: '已注销', tagType: 'info' }
}
