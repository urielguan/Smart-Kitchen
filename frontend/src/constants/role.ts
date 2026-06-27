import type { RoleStatus, DataScope } from '@/types/role'

/** 状态选项 */
export const STATUS_OPTIONS: { label: string; value: RoleStatus }[] = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' }
]

/** 状态映射 */
export const STATUS_MAP: Record<RoleStatus, { label: string; type: string }> = {
  active: { label: '启用', type: 'success' },
  inactive: { label: '禁用', type: 'info' }
}

/** 数据权限范围选项 */
export const DATA_SCOPE_OPTIONS: { label: string; value: DataScope }[] = [
  { label: '全部数据权限', value: 'all' },
  { label: '自定数据权限', value: 'custom' },
  { label: '本部门数据权限', value: 'dept' },
  { label: '本部门及以下数据权限', value: 'dept_and_child' }
]

/** 数据权限范围映射 */
export const DATA_SCOPE_MAP: Record<DataScope, string> = {
  all: '全部数据权限',
  custom: '自定数据权限',
  dept: '本部门数据权限',
  dept_and_child: '本部门及以下数据权限',
  self: '仅本人数据权限'
}

/** 功能模块配置 */
export interface FuncModuleConfig {
  module: string
  key?: string
  children?: { key: string; label: string; actions?: string[] }[]
}

export const FUNC_MODULE_CONFIG: FuncModuleConfig[] = [
  {
    module: '数据概览',
    key: 'dashboard',
    actions: ['查看']
  },
  {
    module: '采购管理',
    children: [
      { key: 'supplier', label: '供应商管理', actions: ['查看', '新增', '编辑', '删除'] },
      { key: 'purchasePlan', label: '采购计划', actions: ['查看', '新增', '编辑', '审核', '删除'] },
      { key: 'purchase', label: '采购订单', actions: ['查看', '新增', '编辑', '审核', '删除'] }
    ]
  },
  {
    module: '仓储管理',
    children: [
      { key: 'warehouse', label: '仓库信息管理', actions: ['查看', '新增', '编辑', '删除'] },
      { key: 'material', label: '物料信息管理', actions: ['查看', '新增', '编辑', '删除'] },
      { key: 'inventory', label: '库存汇总', actions: ['查看'] },
      { key: 'inbound', label: '入库管理', actions: ['查看', '新增', '编辑', '审核', '删除'] },
      { key: 'outbound', label: '出库管理', actions: ['查看', '新增', '编辑', '审核', '删除'] },
      { key: 'stocktake', label: '盘点管理', actions: ['查看', '新增', '编辑', '审核', '删除'] }
    ]
  },
  {
    module: '菜谱营养',
    children: [
      { key: 'recipe', label: '菜谱库管理', actions: ['查看', '新增', '编辑', '删除'] },
      { key: 'plan', label: '菜谱计划', actions: ['查看', '新增', '编辑', '审核', '删除'] }
    ]
  },
  {
    module: '后厨管理',
    children: [
      { key: 'cook', label: '烹饪记录', actions: ['查看', '新增'] },
      { key: 'sample', label: '留样管理', actions: ['查看', '新增', '编辑', '删除'] }
    ]
  },
  {
    module: '系统管理',
    children: [
      { key: 'org', label: '组织管理', actions: ['查看', '新增', '编辑', '删除'] },
      { key: 'employee', label: '员工管理', actions: ['查看', '新增', '编辑', '删除'] },
      { key: 'role', label: '角色权限管理', actions: ['查看', '新增', '编辑', '删除'] },
      { key: 'notification', label: '消息中心', actions: ['查看'] }
    ]
  }
]

/** 获取所有功能权限key列表 */
export const getAllPermissionKeys = (): string[] => {
  const keys: string[] = []
  FUNC_MODULE_CONFIG.forEach(m => {
    if (m.key) {
      keys.push(m.key)
    }
    if (m.children) {
      m.children.forEach(c => keys.push(c.key))
    }
  })
  return keys
}

/** 功能权限名称映射 */
export const PERMISSION_NAME_MAP: Record<string, string> = {
  dashboard: '数据概览',
  supplier: '供应商管理',
  purchasePlan: '采购计划',
  purchase: '采购订单',
  warehouse: '仓库信息管理',
  material: '物料信息管理',
  inventory: '库存汇总',
  inbound: '入库管理',
  outbound: '出库管理',
  stocktake: '盘点管理',
  recipe: '菜谱库管理',
  plan: '菜谱计划',
  cook: '烹饪记录',
  sample: '留样管理',
  org: '组织管理',
  employee: '员工管理',
  role: '角色权限管理',
  notification: '消息中心'
}
