/** 员工状态（在职/离职） */
export type EmployeeStatus = 'active' | 'left'

/** 账号状态 */
export type AccountStatus = 'active' | 'inactive' | 'locked'

/** 员工性别 */
export type Gender = 'male' | 'female'

/** 职位 */
export type Position = string

/** 账号信息 */
export interface AccountInfo {
  userId: number
  username: string
  accountStatus: AccountStatus
  lastLoginAt?: string
}

/** 员工信息 */
export interface Employee {
  id: number
  employeeNo: string
  realName: string
  gender: Gender
  phone: string
  email?: string
  idCard?: string
  orgId: number
  orgName: string
  position?: Position
  hireDate?: string
  status: EmployeeStatus  // 员工状态（在职/离职）
  roleIds?: number[]
  roleNames?: string
  remark?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
  account?: AccountInfo  // 账号信息（含 accountStatus）
}

/** 员工表单 */
export interface EmployeeForm {
  employeeNo: string
  realName: string
  gender: Gender
  phone: string
  email: string
  idCard: string
  orgId: number | null
  position: Position | undefined
  hireDate: string
  accountStatus: AccountStatus  // 账号状态（启用/禁用）
  status: EmployeeStatus  // 在职状态（在职/离职）
  roleIds: number[]
  remark: string
}

/** 员工查询参数 */
export interface EmployeeQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  orgId?: number
  accountStatus?: AccountStatus
  status?: EmployeeStatus
}

/** 员工统计数据 */
export interface EmployeeStatistics {
  total: number
  activeCount: number
  inactiveCount: number
  newThisMonth: number
}

/** 角色信息（用于权限分配） */
export interface Role {
  id: number
  roleCode: string
  roleName: string
  status: 'active' | 'inactive'
  dataScope: 'all' | 'custom' | 'org' | 'dept' | 'dept_and_child' | 'self'
  dataScopeOrgIds?: number[]
  funcPermissions: string[]
}

/** 组织信息（门店/部门） */
export interface OrgOption {
  id: number
  orgName: string
  orgType: string
  parentId?: number
}

/** 员工导入结果 */
export interface EmployeeImportResult {
  total: number
  successCount: number
  failCount: number
  hasErrors: boolean
  errorFileUrl: string | null
}
