import { get, post, put, del } from '../index'
import service from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { Employee, EmployeeForm, EmployeeQuery, EmployeeImportResult, Role, OrgOption } from '@/types/employee'

const BASE_URL = '/v1/sys/employees'

/** 员工管理 API */
export const employeeApi = {
  /** 获取员工列表（分页） */
  getList(params: EmployeeQuery): Promise<ApiResponse<PageResponse<Employee>>> {
    return get(BASE_URL, params)
  },

  /** 获取员工详情 */
  getDetail(id: number): Promise<ApiResponse<Employee>> {
    return get(`${BASE_URL}/${id}`)
  },

  /** 新增员工 */
  create(data: EmployeeForm): Promise<ApiResponse<Employee>> {
    return post(BASE_URL, data)
  },

  /** 编辑员工 */
  update(id: number, data: Partial<EmployeeForm>): Promise<ApiResponse<Employee>> {
    return put(`${BASE_URL}/${id}`, data)
  },

  /** 删除员工 */
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE_URL}/${id}`)
  },

  /** 修改账号状态（启用/禁用） */
  updateAccountStatus(id: number, accountStatus: string): Promise<ApiResponse<void>> {
    return put(`${BASE_URL}/${id}/account-status`, { accountStatus })
  },

  /** 生成员工编号 */
  generateEmpNo(): Promise<ApiResponse<string>> {
    return get(`${BASE_URL}/generate-emp-no`)
  },

  /** 获取角色列表（用于权限分配） */
  getRoles(params?: { status?: string }): Promise<ApiResponse<Role[]>> {
    return get('/v1/sys/roles', params)
  },

  /** 获取组织列表（门店/部门） */
  getOrgs(): Promise<ApiResponse<OrgOption[]>> {
    return get('/v1/sys/orgs')
  },

  /** 下载员工导入模板 */
  async downloadTemplate(): Promise<void> {
    const response = await service.get(BASE_URL + '/import/template', { responseType: 'blob' })
    downloadBlob(response, '员工导入模板.xlsx')
  },

  /** 导入员工 */
  importEmployees(file: File): Promise<ApiResponse<EmployeeImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(BASE_URL + '/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /** 导出员工 */
  async exportEmployees(params?: { keyword?: string; orgId?: number; accountStatus?: string }): Promise<void> {
    const query = new URLSearchParams()
    if (params?.keyword) query.set('keyword', params.keyword)
    if (params?.orgId) query.set('orgId', String(params.orgId))
    if (params?.accountStatus) query.set('accountStatus', params.accountStatus)
    const url = BASE_URL + '/export' + (query.toString() ? '?' + query.toString() : '')
    const response = await service.get(url, { responseType: 'blob' })
    downloadBlob(response, '员工数据导出.xlsx')
  },

  /** 下载导入错误文件 */
  async downloadErrorFile(fileName: string): Promise<void> {
    const response = await service.get(BASE_URL + '/import/errors/' + fileName, { responseType: 'blob' })
    downloadBlob(response, fileName)
  }
}

/** 从 Axios 响应中提取文件名并触发 Blob 下载 */
function downloadBlob(response: any, defaultName: string) {
  const contentDisposition = response.headers['content-disposition']
  let filename = defaultName
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
    if (match) {
      filename = decodeURIComponent(match[1].replace(/['"]/g, ''))
    }
  }
  const blob = new Blob([response.data as BlobPart])
  const blobUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

export default employeeApi
