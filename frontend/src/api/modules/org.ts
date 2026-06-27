import { get, post, put, del } from '../index'
import service from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { Organization, OrgForm, OrgQuery, OrgStatistics, OrgTreeNode, OrganizationImportResult } from '@/types/org'

const BASE_URL = '/v1/sys/organizations'

/** 组织管理 API */
export const orgApi = {
  /** 获取组织列表（分页） */
  getList(params: OrgQuery): Promise<ApiResponse<PageResponse<Organization>>> {
    return get(BASE_URL, params)
  },

  /** 获取组织详情 */
  getDetail(id: number): Promise<ApiResponse<Organization>> {
    return get(`${BASE_URL}/${id}`)
  },

  /** 新增组织 */
  create(data: OrgForm): Promise<ApiResponse<Organization>> {
    return post(BASE_URL, data)
  },

  /** 编辑组织 */
  update(id: number, data: Partial<OrgForm>): Promise<ApiResponse<Organization>> {
    return put(`${BASE_URL}/${id}`, data)
  },

  /** 删除组织 */
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE_URL}/${id}`)
  },

  /** 获取组织树 */
  getTree(params?: { orgType?: string; status?: string; keyword?: string; includeChildren?: boolean }): Promise<ApiResponse<OrgTreeNode[]>> {
    return get(`${BASE_URL}/tree`, params)
  },

  /** 获取组织统计数据 */
  getStatistics(): Promise<ApiResponse<OrgStatistics>> {
    return get(`${BASE_URL}/statistics`)
  },

  /** 检查组织编码是否存在 */
  checkOrgCode(orgCode: string, excludeId?: number): Promise<ApiResponse<boolean>> {
    return get(`${BASE_URL}/check-code`, { orgCode, excludeId })
  },

  /** 更新组织状态 */
  updateStatus(id: number, status: 'active' | 'inactive'): Promise<ApiResponse<{ id: number; orgName: string; status: string }>> {
    return put(`${BASE_URL}/${id}/status`, { status })
  },

  // ==================== 导入导出相关 ====================

  /** 下载导入模板 */
  async downloadTemplate(): Promise<void> {
    const response = await service.get(BASE_URL + '/import/template', {
      responseType: 'blob'
    })
    downloadBlob(response, '组织导入模板.xlsx')
  },

  /** 导入组织 */
  importOrganizations(file: File): Promise<ApiResponse<OrganizationImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(BASE_URL + '/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /** 导出组织 */
  async exportOrganizations(params?: { orgType?: string; status?: string; keyword?: string; includeChildren?: boolean }): Promise<void> {
    const query = new URLSearchParams()
    if (params?.orgType) query.set('orgType', params.orgType)
    if (params?.status) query.set('status', params.status)
    if (params?.keyword) query.set('keyword', params.keyword)
    if (params?.includeChildren !== undefined) query.set('includeChildren', String(params.includeChildren))
    const url = BASE_URL + '/export' + (query.toString() ? '?' + query.toString() : '')

    const response = await service.get(url, {
      responseType: 'blob'
    })
    downloadBlob(response, '组织数据导出.xlsx')
  },

  /** 下载导入错误文件 */
  async downloadErrorFile(fileName: string): Promise<void> {
    const response = await service.get(BASE_URL + '/import/errors/' + fileName, {
      responseType: 'blob'
    })
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

export default orgApi
