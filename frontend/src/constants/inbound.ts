export const INBOUND_SOURCE_TYPES = [
  { value: 'purchase',       label: '采购入库' },
  { value: 'transfer',       label: '调拨入库' },
  { value: 'return',         label: '退货入库' },
  { value: 'material_return', label: '退料入库' },
  { value: 'surplus',        label: '盘盈入库' },
  { value: 'donation',       label: '赠品/捐赠入库' },
  { value: 'other',          label: '其他入库' },
]

export const INBOUND_STATUS_OPTIONS = [
  { value: 'draft',      label: '草稿',   type: 'info'    as const },
  { value: 'pending',    label: '待审批', type: 'warning' as const },
  { value: 'approved',   label: '已审核', type: 'success' as const },
  { value: 'completed',  label: '已入库', type: 'success' as const },
  { value: 'rejected',   label: '已驳回', type: 'danger'  as const },
  { value: 'cancelled',  label: '已取消', type: 'info'    as const },
]

export const INBOUND_SOURCE_TYPE_MAP: Record<string, string> = {
  purchase:        '采购入库',
  transfer:        '调拨入库',
  return:          '退货入库',
  material_return: '退料入库',
  surplus:         '盘盈入库',
  donation:        '赠品/捐赠入库',
  other:           '其他入库',
}

export const INBOUND_STATUS_MAP: Record<string, string> = {
  draft:     '草稿',
  pending:   '待审批',
  approved:  '已审核',
  completed: '已入库',
  rejected:  '已驳回',
  cancelled: '已取消',
}
