export const STOCKTAKE_TYPE_OPTIONS = [
  { value: 'regular', label: '常规盘点' },
] as const

export const STOCKTAKE_TYPE_MAP: Record<string, string> = {
  regular: '常规盘点',
}

export const STOCKTAKE_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿', type: 'info' as const },
  { value: 'pending', label: '待审核', type: 'warning' as const },
  { value: 'rejected', label: '已驳回', type: 'danger' as const },
  { value: 'completed', label: '已完成', type: 'success' as const },
  { value: 'voided', label: '已作废', type: 'info' as const },
] as const

export const STOCKTAKE_STATUS_MAP: Record<string, string> = {
  draft: '草稿',
  pending: '待审核',
  rejected: '已驳回',
  completed: '已完成',
  voided: '已作废',
}

export const STOCKTAKE_STATUS_TYPE_MAP: Record<string, string> = {
  draft: 'info',
  pending: 'warning',
  rejected: 'danger',
  completed: 'success',
  voided: 'info',
}

export const STOCKTAKE_DIFF_DIRECTION_MAP: Record<string, string> = {
  surplus: '盘盈',
  deficit: '盘亏',
  normal: '正常',
}

export const STOCKTAKE_DIFF_DIRECTION_TYPE_MAP: Record<string, string> = {
  surplus: 'success',
  deficit: 'danger',
  normal: 'info',
}
