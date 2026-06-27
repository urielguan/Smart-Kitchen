import { get, post, put, del } from '../index'
import type { RequestConfig } from '../index'
import service from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  Material,
  MaterialForm,
  MaterialQuery,
  MaterialStatistics,
  MaterialImportResult,
  FoodCategory,
  FoodImportResult,
  FoodItem,
  MaterialFoodMapping,
  MaterialFoodMappingForm,
  MaterialFoodMappingQuery,
  NutritionSyncResult
} from '@/types/material'

const BASE_URL = '/v1/wms/materials'

/** 物料管理 API */
export const materialApi = {
  /** 获取物料列表（分页） */
  getList(params: MaterialQuery): Promise<ApiResponse<PageResponse<Material>>> {
    return get(BASE_URL, params)
  },

  /** 获取物料详情 */
  getDetail(id: number): Promise<ApiResponse<Material>> {
    return get(`${BASE_URL}/${id}`)
  },

  /** 新增物料 */
  create(data: MaterialForm): Promise<ApiResponse<Material>> {
    return post(BASE_URL, data)
  },

  /** 编辑物料 */
  update(id: number, data: Partial<MaterialForm>): Promise<ApiResponse<Material>> {
    return put(`${BASE_URL}/${id}`, data)
  },

  /** 删除物料 */
  delete(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE_URL}/${id}`)
  },

  /** 更新物料状态 */
  updateStatus(id: number, status: string): Promise<ApiResponse<{ id: number; status: string }>> {
    return put(`${BASE_URL}/${id}/status`, { status })
  },

  /** 批量删除物料 */
  batchDelete(ids: number[]): Promise<ApiResponse<void>> {
    return del(`${BASE_URL}/batch`, { ids })
  },

  /** 获取物料统计数据 */
  getStatistics(): Promise<ApiResponse<MaterialStatistics>> {
    return get(`${BASE_URL}/statistics`)
  },

  /** 上传物料图片（不需要物料ID） */
  uploadImage(file: File): Promise<ApiResponse<{ imageUrl: string }>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(`${BASE_URL}/upload-image`, formData)
  },

  /** 获取物料类别列表 */
  getCategories(): Promise<ApiResponse<any[]>> {
    return get('/v1/wms/material-categories')
  },

  /** 下载物料导入模板 */
  async downloadTemplate(): Promise<void> {
    const response = await service.get(BASE_URL + '/import/template', { responseType: 'blob' })
    downloadBlob(response, '物料导入模板.xlsx')
  },

  /** 导入物料 */
  importMaterials(file: File): Promise<ApiResponse<MaterialImportResult>> {
    const formData = new FormData()
    formData.append('file', file)
    return post(BASE_URL + '/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /** 导出物料 */
  async exportMaterials(params?: Partial<MaterialQuery>): Promise<void> {
    const query = new URLSearchParams()
    if (params?.materialName) query.set('materialName', params.materialName)
    if (params?.categoryName) query.set('categoryName', params.categoryName)
    if (params?.stockStatus) query.set('stockStatus', params.stockStatus)
    if (params?.status) query.set('status', params.status)
    const url = BASE_URL + '/export' + (query.toString() ? '?' + query.toString() : '')
    const response = await service.get(url, { responseType: 'blob' })
    downloadBlob(response, '物料数据导出.xlsx')
  },

  /** 下载导入错误文件 */
  async downloadErrorFile(fileName: string): Promise<void> {
    const response = await service.get(BASE_URL + '/import/errors/' + fileName, { responseType: 'blob' })
    downloadBlob(response, fileName)
  },

  /** 初始化标准食品库 */
  importFoodJson(config?: RequestConfig): Promise<ApiResponse<FoodImportResult>> {
    return post('/v1/recipe/food/import-json', undefined, config)
  },

  /** 获取标准食品分类 */
  getFoodCategories(config?: RequestConfig): Promise<ApiResponse<FoodCategory[]>> {
    return get('/v1/recipe/food/categories', undefined, config)
  },

  /** 获取标准食品列表 */
  getFoodItems(
    params: { pageNum: number; pageSize: number; foodName?: string; categoryId?: number },
    config?: RequestConfig
  ): Promise<ApiResponse<PageResponse<FoodItem>>> {
    return get('/v1/recipe/food/items', params, config)
  },

  /** 获取标准食品详情 */
  getFoodItemDetail(id: number, config?: RequestConfig): Promise<ApiResponse<FoodItem>> {
    return get(`/v1/recipe/food/items/${id}`, undefined, config)
  },

  /** 获取物料-标准食品映射列表 */
  getMaterialFoodMappings(params: MaterialFoodMappingQuery): Promise<ApiResponse<PageResponse<MaterialFoodMapping>>> {
    return get('/v1/wms/material-food-mappings', params)
  },

  /** 新增或更新映射 */
  createMaterialFoodMapping(data: MaterialFoodMappingForm): Promise<ApiResponse<MaterialFoodMapping>> {
    return post('/v1/wms/material-food-mappings', data)
  },

  /** 同步物料营养 */
  syncNutrition(id: number): Promise<ApiResponse<NutritionSyncResult>> {
    return post(`${BASE_URL}/${id}/nutrition-sync`)
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

export default materialApi

export const isFoodLibraryApiUnavailable = (error: unknown): boolean => {
  if (!(error instanceof Error)) return false
  return /(接口不存在|资源不存在|404)/.test(error.message)
}
