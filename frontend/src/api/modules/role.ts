import { get, post, put, del, request } from '../index'
import type { ApiResponse } from '@/types/api'
import type {
  Role,
  RoleForm,
  RoleGroup,
  RoleGroupForm,
  RoleEmployee,
  RoleStatistics,
  PermissionNode
} from '@/types/role'

const BASE_URL = '/v1/sys/roles'
const GROUP_URL = '/v1/sys/role-groups'

/** 角色权限管理 API */
export const roleApi = {
  // ==================== 分组相关 ====================

  /** 获取分组列表 */
  getGroupList(): Promise<ApiResponse<RoleGroup[]>> {
    return get(GROUP_URL)
  },

  /** 新增分组 */
  createGroup(data: RoleGroupForm): Promise<ApiResponse<RoleGroup>> {
    return post(GROUP_URL, data)
  },

  /** 编辑分组 */
  updateGroup(id: number, data: Partial<RoleGroupForm>): Promise<ApiResponse<RoleGroup>> {
    return put(`${GROUP_URL}/${id}`, data)
  },

  /** 删除分组 */
  deleteGroup(id: number): Promise<ApiResponse<void>> {
    return del(`${GROUP_URL}/${id}`)
  },

  // ==================== 角色相关 ====================

  /** 获取角色列表 */
  getList(params?: { status?: string }): Promise<ApiResponse<Role[]>> {
    return get(BASE_URL, params)
  },

  /** 获取角色详情 */
  getDetail(id: number): Promise<ApiResponse<Role>> {
    return get(`${BASE_URL}/${id}`)
  },

  /** 新增角色 */
  create(data: RoleForm): Promise<ApiResponse<Role>> {
    return post(BASE_URL, data)
  },

  /** 编辑角色 */
  update(id: number, data: Partial<RoleForm>): Promise<ApiResponse<Role>> {
    return put(`${BASE_URL}/${id}`, data)
  },

  /** 删除角色 */
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE_URL}/${id}`)
  },

  /** 获取当前用户可授权限树 */
  getPermissionTree(): Promise<ApiResponse<PermissionNode[]>> {
    return get('/v1/sys/roles/permission-tree')
  },


  /** 获取角色关联的员工（分页） */
  getRoleEmployees(id: number, params?: { pageNum?: number; pageSize?: number }): Promise<ApiResponse<{ list: RoleEmployee[]; total: number }>> {
    return get(`${BASE_URL}/${id}/members`, { pageNum: 1, pageSize: 20, ...params })
  },

  /** 获取统计数据 */
  getStatistics(): Promise<ApiResponse<RoleStatistics>> {
    return get(`${BASE_URL}/statistics`)
  },

  /** 添加角色成员 */
  addRoleMembers(roleId: number, employeeIds: number[]): Promise<ApiResponse<{ roleId: number; addedCount: number; memberCount: number }>> {
    return post(`${BASE_URL}/${roleId}/members`, { employeeIds })
  },

  /** 批量移除角色成员 */
  batchRemoveRoleMembers(roleId: number, employeeIds: number[]): Promise<ApiResponse<{ roleId: number; removedCount: number; memberCount: number }>> {
    return request({ method: 'DELETE', url: `${BASE_URL}/${roleId}/members/batch`, data: { employeeIds } })
  }
}

export default roleApi
