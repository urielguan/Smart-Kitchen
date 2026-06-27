import type { OrgStatus } from '@/types/org'

/** 组织类型选项 */
export const ORG_TYPES: { label: string; value: string }[] = [
  { label: '集团', value: 'group' },
  { label: '分公司', value: 'company' },
  { label: '食堂', value: 'canteen' },
  { label: '部门', value: 'dept' }
]

/** 组织状态选项 */
export const ORG_STATUS_OPTIONS: { label: string; value: OrgStatus }[] = [
  { label: '启用', value: 'active' },
  { label: '停用', value: 'inactive' }
]

/** 组织类型映射 */
export const ORG_TYPE_MAP: Record<string, { label: string; tagType: '' | 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  group: { label: '集团', tagType: 'primary' },
  company: { label: '分公司', tagType: 'success' },
  canteen: { label: '食堂', tagType: 'warning' },
  dept: { label: '部门', tagType: 'info' }
}

/** 组织状态映射 */
export const ORG_STATUS_MAP: Record<OrgStatus, { label: string; tagType: '' | 'success' | 'warning' | 'danger' | 'info' }> = {
  active: { label: '启用', tagType: 'success' },
  inactive: { label: '停用', tagType: 'danger' }
}
