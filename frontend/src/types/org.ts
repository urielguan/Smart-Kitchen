/** 组织类型 */
export type OrgType = string

/** 组织状态 */
export type OrgStatus = 'active' | 'inactive'

/** 组织信息 */
export interface Organization {
  id: number
  orgCode: string
  orgName: string
  orgType: OrgType
  parentId: number | null
  parentName?: string
  level: number
  path: string
  leaderName: string
  contactPhone: string
  address: string
  status: OrgStatus
  sortOrder: number
  tenantId: number | null
  createdAt: string
  updatedAt: string
}

/** 组织表单 */
export interface OrgForm {
  orgCode: string
  orgName: string
  orgType: OrgType
  parentId: number | null
  leaderName: string
  contactPhone: string
  address: string
  status: OrgStatus
  sortOrder: number
  remark?: string
}

/** 组织查询参数 */
export interface OrgQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  orgName?: string
  orgCode?: string
  orgType?: OrgType
  status?: OrgStatus
  parentId?: number | null
  includeChildren?: boolean
}

/** 组织统计数据 */
export interface OrgStatistics {
  total: number
  groupCount: number
  companyCount: number
  canteenCount: number
  deptCount: number
  activeCount: number
  inactiveCount: number
}

/** 组织树节点 */
export interface OrgTreeNode {
  id: number
  orgCode: string
  orgName: string
  orgType: OrgType
  parentId: number | null
  parentName?: string
  status?: OrgStatus
  leaderName?: string
  memberCount?: number
  children?: OrgTreeNode[]
}

/** 组织导入结果 */
export interface OrganizationImportResult {
  /** 总条数 */
  total: number
  /** 成功数 */
  successCount: number
  /** 失败数 */
  failCount: number
  /** 是否有错误 */
  hasErrors: boolean
  /** 错误文件下载URL */
  errorFileUrl: string | null
}
