export const WAREHOUSE_TYPES = [
  { value: 'normal', label: '常温库' },
  { value: 'cold',   label: '冷藏库' },
  { value: 'freeze', label: '冷冻库' },
  { value: 'dry',    label: '干货库' },
]

export const WAREHOUSE_STATUS_OPTIONS = [
  { value: 'active',      label: '启用',   type: 'success' as const },
  { value: 'inactive',    label: '停用',   type: 'danger'  as const },
  { value: 'maintenance', label: '维护中', type: 'warning' as const },
  { value: 'archived',    label: '已归档', type: 'info'    as const },
]

export const LOCATION_STATUS_OPTIONS = [
  { value: 'available',   label: '可用',   type: 'success' as const },
  { value: 'occupied',    label: '占用',   type: 'info'    as const },
  { value: 'maintenance', label: '维护中', type: 'warning' as const },
  { value: 'inactive',    label: '停用',   type: 'danger'  as const },
  { value: 'archived',    label: '已归档', type: 'info'    as const },
]

export const WAREHOUSE_TYPE_MAP: Record<string, string> = {
  normal: '常温库', cold: '冷藏库', freeze: '冷冻库', dry: '干货库',
}
