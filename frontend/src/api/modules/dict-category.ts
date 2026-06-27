import { del, get, post, put } from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  DictCategoryAreaCoefficientRecalcTask,
  DictCategoryAreaSuggestion,
  DictCategoryDetail,
  DictCategoryForm,
  DictCategoryItem,
  DictCategoryMeta,
  DictCategoryOption,
  DictCategoryQuery,
  DictCategoryType
} from '@/types/dict-category'

const BASE_URL = '/v1/sys/dict-categories'

const withCategoryType = (url: string, categoryType: DictCategoryType) =>
  `${url}${url.includes('?') ? '&' : '?'}categoryType=${encodeURIComponent(categoryType)}`

export const dictCategoryApi = {
  getCategories(): Promise<ApiResponse<DictCategoryMeta[]>> {
    return get(`${BASE_URL}/categories`)
  },

  getList(params: DictCategoryQuery): Promise<ApiResponse<PageResponse<DictCategoryItem>>> {
    return get(BASE_URL, params)
  },

  getDetail(categoryType: DictCategoryType, id: number): Promise<ApiResponse<DictCategoryDetail>> {
    return get(withCategoryType(`${BASE_URL}/${id}`, categoryType))
  },

  create(data: DictCategoryForm & { categoryType: DictCategoryType }): Promise<ApiResponse<{ id: number }>> {
    return post(BASE_URL, data)
  },

  update(categoryType: DictCategoryType, id: number, data: DictCategoryForm): Promise<ApiResponse<{ id: number }>> {
    return put(withCategoryType(`${BASE_URL}/${id}`, categoryType), data)
  },

  updateStatus(
    categoryType: DictCategoryType,
    id: number,
    status: 'active' | 'inactive'
  ): Promise<ApiResponse<{ id: number }>> {
    return put(withCategoryType(`${BASE_URL}/${id}/status`, categoryType), { status })
  },

  delete(categoryType: DictCategoryType, id: number): Promise<ApiResponse<void>> {
    return del(withCategoryType(`${BASE_URL}/${id}`, categoryType))
  },

  getOptions(
    categoryType: DictCategoryType,
    includeInactive = false
  ): Promise<ApiResponse<DictCategoryOption[]>> {
    return get(`${BASE_URL}/options`, { categoryType, includeInactive })
  },

  getAreaCoefficientSuggestion(data: {
    categoryType: DictCategoryType
    dictName: string
  }): Promise<ApiResponse<DictCategoryAreaSuggestion>> {
    return post(`${BASE_URL}/area-coefficient-suggestion`, data)
  },

  startAreaCoefficientRecalc(
    categoryType: DictCategoryType,
    id: number,
    correctionId: number
  ): Promise<ApiResponse<{ taskId: number; taskNo: string; correctionId: number }>> {
    return post(withCategoryType(`${BASE_URL}/${id}/area-coefficient-recalc`, categoryType), { correctionId })
  },

  getAreaCoefficientRecalcTaskDetail(
    categoryType: DictCategoryType,
    id: number,
    taskId: number
  ): Promise<ApiResponse<DictCategoryAreaCoefficientRecalcTask>> {
    return get(withCategoryType(`${BASE_URL}/${id}/area-coefficient-recalc-tasks/${taskId}`, categoryType))
  }
}

export default dictCategoryApi
