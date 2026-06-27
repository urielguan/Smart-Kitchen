import type { Gender, EmployeeStatus, AccountStatus } from '@/types/employee'

/** 职位选项 */
export const POSITIONS: { label: string; value: string }[] = [
  { label: '厨师', value: 'chef' },
  { label: '厨工', value: 'cookworker' },
  { label: '店长', value: 'manager' },
  { label: '采购员', value: 'purchaser' }
]

/** 职位映射 */
export const POSITION_MAP: Record<string, string> = {
  chef: '厨师',
  cookworker: '厨工',
  manager: '店长',
  purchaser: '采购员'
}

/** 员工状态选项（在职/离职） */
export const EMPLOYEE_STATUS_OPTIONS: { label: string; value: EmployeeStatus }[] = [
  { label: '在职', value: 'active' },
  { label: '离职', value: 'left' }
]

/** 员工状态映射 */
export const EMPLOYEE_STATUS_MAP: Record<EmployeeStatus, { label: string; type: string }> = {
  active: { label: '在职', type: 'success' },
  left: { label: '离职', type: 'danger' }
}

/** 账号状态选项 */
export const ACCOUNT_STATUS_OPTIONS: { label: string; value: AccountStatus }[] = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' },
  { label: '锁定', value: 'locked' }
]

/** 账号状态映射（用于显示） */
export const ACCOUNT_STATUS_MAP: Record<AccountStatus, { label: string; type: string }> = {
  active: { label: '启用', type: 'success' },
  inactive: { label: '禁用', type: 'warning' },
  locked: { label: '锁定', type: 'danger' }
}

/** 性别选项 */
export const GENDER_OPTIONS: { label: string; value: Gender }[] = [
  { label: '男', value: 'male' },
  { label: '女', value: 'female' }
]

/** 性别映射 */
export const GENDER_MAP: Record<Gender, string> = {
  male: '男',
  female: '女'
}

/** 数据权限范围映射 */
export const DATA_SCOPE_MAP: Record<string, string> = {
  all: '全部数据权限',
  custom: '自定数据权限',
  dept: '本部门数据权限',
  dept_and_child: '本部门及以下数据权限',
  self: '仅本人数据权限',
  org: '本机构数据'
}

/** 功能权限映射 */
export const FUNC_PERMISSION_MAP: Record<string, string> = {
  dashboard: '数据看板',
  supplier: '供应商管理',
  'purchase-plan': '采购计划',
  purchase: '采购订单',
  warehouse: '仓库管理',
  material: '物料管理',
  inventory: '库存汇总',
  inbound: '入库管理',
  outbound: '出库管理',
  stocktake: '盘点管理',
  recipe: '菜谱库',
  plan: '菜谱计划',
  cook: '烹饪记录',
  sample: '留样管理',
  'morning-check': '智能人脸晨检',
  'video-monitor': '视频监控管理',
  device: '设备管理',
  org: '组织管理',
  employee: '员工管理',
  'dict-category': '字典分类维护',
  role: '角色权限管理',
  evaluation: '评价管理'
}
