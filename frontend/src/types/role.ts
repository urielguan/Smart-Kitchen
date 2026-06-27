/** 角色状态 */
export type RoleStatus = 'active' | 'inactive'

/** 数据权限范围 */
export type DataScope = 'all' | 'custom' | 'dept' | 'dept_and_child' | 'self'

/** 角色分组 */
export interface RoleGroup {
  id: number
  groupName: string
  orgId?: number
  sortOrder: number
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 角色分组表单 */
export interface RoleGroupForm {
  groupName: string
  orgId?: number | null
  sortOrder: number
  remark?: string
}

/** 权限树节点 */
export interface PermissionNode {
  id: number
  permissionCode: string
  permissionName: string
  permissionType: 'module' | 'menu' | 'button' | 'api' | string
  parentId: number
  moduleCode?: string
  resourcePath?: string
  sortOrder?: number
  children?: PermissionNode[]
}

/** 组织树节点 */
export interface OrgTreeNode {
  id: number
  orgName: string
  orgCode?: string
  parentId: number | null
  orgType?: string
  status?: string
  sortOrder?: number
  disabled?: boolean
  children?: OrgTreeNode[]
}

/** 角色信息 */
export interface Role {
  id: number
  roleName: string
  roleCode: string
  groupId: number
  groupName?: string
  groupVisible?: boolean
  status: RoleStatus
  dataScope: DataScope
  dataScopeOrgIds?: number[]
  funcPermissions: string[]
  funcPermissionNameMap?: Record<string, string>
  memberCount?: number
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 角色表单 */
export interface RoleForm {
  roleName: string
  roleCode: string
  groupId: number | null
  status: RoleStatus
  dataScope: DataScope
  dataScopeOrgIds: number[]
  funcPermissions: string[]
  remark: string
}

/** 角色关联员工 */
export interface RoleEmployee {
  id: number
  employeeNo: string
  realName: string
  orgName: string
  position?: string
  phone?: string
  employeeStatus: string
  status: string
  joinedAt?: string
}

/** 角色统计数据 */
export interface RoleStatistics {
  total: number
  activeCount: number
  inactiveCount: number
  groupCount: number
}
