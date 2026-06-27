/** 出库状态选项 */
export const OUTBOUND_STATUS_OPTIONS = [
  { value: 'draft',     label: '草稿',   type: 'info'    as const },
  { value: 'pending',   label: '待审核', type: 'warning' as const },
  { value: 'approved',  label: '已审核', type: 'success' as const },
  { value: 'completed', label: '已出库', type: 'success' as const },
  { value: 'rejected',   label: '已驳回', type: 'danger'    as const },
] as const

/** 出库类型映射 */
export const OUTBOUND_TYPE_MAP: Record<string, string> = {
  requisition: '领用出库',
  sales:       '销售出库',
  return:      '退货出库',
  transfer:    '调拨出库',
  loss:        '报损出库',
  donation:    '捐赠出库',
  scrap:       '报废出库',
  other:       '其他出库',
}

/** 出库状态映射 */
export const OUTBOUND_STATUS_MAP: Record<string, string> = {
  draft:     '草稿',
  pending:   '待审核',
  approved:  '已审核',
  completed: '已出库',
  rejected:   '已驳回',
}

/** 出库状态标签类型映射 */
export const OUTBOUND_STATUS_TYPE_MAP: Record<string, string> = {
  draft:     'info',
  pending:   'warning',
  approved:  'success',
  completed: 'success',
  rejected:   'danger',
}

/** 不同出库类型需要显示的额外字段 */
export const OUTBOUND_TYPE_FIELDS: Record<string, string[]> = {
  requisition: ['requesterId', 'department'],
  sales:       [],
  return:      ['supplierId'],
  transfer:    ['targetWarehouseId'],
  loss:        [],
  donation:    ['recipientName'],
  scrap:       [],
  other:       [],
}
