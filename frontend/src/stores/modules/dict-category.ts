import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictCategoryApi } from '@/api/modules/dict-category'
import { useUserStore } from '@/stores/modules/user'
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

const DEFAULT_CATEGORY: DictCategoryType = 'recipe_category'

const createDefaultQuery = (categoryType: DictCategoryType = DEFAULT_CATEGORY): DictCategoryQuery => ({
  pageNum: 1,
  pageSize: 10,
  categoryType,
  keyword: '',
  sourceType: '',
  status: ''
})

export const useDictCategoryStore = defineStore('dict-category', () => {
  const userStore = useUserStore()
  const categories = ref<DictCategoryMeta[]>([])
  const list = ref<DictCategoryItem[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const initialized = ref(false)
  const currentCategory = ref<DictCategoryType>(DEFAULT_CATEGORY)
  const query = ref<DictCategoryQuery>(createDefaultQuery())
  const detail = ref<DictCategoryDetail | null>(null)
  const optionsCache = ref<Record<string, DictCategoryOption[]>>({})
  const activeOptionsCache = ref<Record<string, DictCategoryOption[]>>({})
  const initializedTenantKey = ref<string | null>(null)

  const getTenantKey = () => String(userStore.userInfo?.tenantId ?? 0)
  const getOptionCacheKey = (categoryType: DictCategoryType) => `${getTenantKey()}::${categoryType}`

  const resetTenantScopedState = () => {
    categories.value = []
    list.value = []
    total.value = 0
    pageNum.value = 1
    pageSize.value = 10
    query.value = createDefaultQuery(currentCategory.value)
    detail.value = null
    optionsCache.value = {}
    activeOptionsCache.value = {}
    initialized.value = false
  }

  const ensureTenantScopedState = () => {
    const tenantKey = getTenantKey()
    if (initializedTenantKey.value !== tenantKey) {
      resetTenantScopedState()
      initializedTenantKey.value = tenantKey
    }
  }

  const fetchCategories = async () => {
    ensureTenantScopedState()
    const res = await dictCategoryApi.getCategories()
    if (res.code === 'SUCCESS' && res.data) {
      categories.value = res.data
    }
  }

  const fetchList = async () => {
    ensureTenantScopedState()
    loading.value = true
    try {
      const res = await dictCategoryApi.getList({
        ...query.value,
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        categoryType: currentCategory.value
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list
        total.value = res.data.total
      }
    } finally {
      loading.value = false
    }
  }

  const init = async () => {
    ensureTenantScopedState()
    if (initialized.value && initializedTenantKey.value === getTenantKey()) {
      return
    }
    await Promise.all([fetchCategories(), fetchList()])
    initialized.value = true
  }

  const changeCategory = async (categoryType: DictCategoryType) => {
    currentCategory.value = categoryType
    query.value = createDefaultQuery(categoryType)
    pageNum.value = 1
    pageSize.value = 10
    await fetchList()
  }

  const search = async (payload: Partial<DictCategoryQuery>) => {
    query.value = {
      ...query.value,
      ...payload,
      categoryType: currentCategory.value
    }
    pageNum.value = 1
    await fetchList()
  }

  const resetSearch = async () => {
    query.value = createDefaultQuery(currentCategory.value)
    pageNum.value = 1
    await fetchList()
  }

  const getDetail = async (categoryType: DictCategoryType, id: number) => {
    ensureTenantScopedState()
    const res = await dictCategoryApi.getDetail(categoryType, id)
    if (res.code === 'SUCCESS' && res.data) {
      detail.value = res.data
      return res.data
    }
    return undefined
  }

  const invalidateOptions = (categoryType: DictCategoryType) => {
    const cacheKey = getOptionCacheKey(categoryType)
    delete optionsCache.value[cacheKey]
    delete activeOptionsCache.value[cacheKey]
  }

  const refreshCurrentCategory = async () => {
    invalidateOptions(currentCategory.value)
    await Promise.all([fetchCategories(), fetchList()])
  }

  const createItem = async (payload: DictCategoryForm) => {
    const res = await dictCategoryApi.create({
      ...payload,
      categoryType: currentCategory.value
    })
    if (res.code === 'SUCCESS') {
      ElMessage.success('新增成功')
      await refreshCurrentCategory()
      return true
    }
    return false
  }

  const updateItem = async (categoryType: DictCategoryType, id: number, payload: DictCategoryForm) => {
    const res = await dictCategoryApi.update(categoryType, id, payload)
    if (res.code === 'SUCCESS') {
      ElMessage.success('编辑成功')
      invalidateOptions(categoryType)
      await Promise.all([fetchCategories(), fetchList()])
      return true
    }
    return false
  }

  const updateItemStatus = async (
    categoryType: DictCategoryType,
    id: number,
    status: 'active' | 'inactive'
  ) => {
    const actionText = status === 'active' ? '启用' : '禁用'
    try {
      await ElMessageBox.confirm(`确认${actionText}该字典项？`, '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })
      const res = await dictCategoryApi.updateStatus(categoryType, id, status)
      if (res.code === 'SUCCESS') {
        ElMessage.success(`${actionText}成功`)
        invalidateOptions(categoryType)
        await Promise.all([fetchCategories(), fetchList()])
        return true
      }
    } catch (error: any) {
      if (error !== 'cancel' && error !== 'close') {
        throw error
      }
    }
    return false
  }

  const deleteItem = async (categoryType: DictCategoryType, id: number) => {
    try {
      await ElMessageBox.confirm('确认删除该字典项？删除后不可恢复。', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })
      const res = await dictCategoryApi.delete(categoryType, id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        invalidateOptions(categoryType)
        await Promise.all([fetchCategories(), fetchList()])
        return true
      }
    } catch (error: any) {
      if (error !== 'cancel' && error !== 'close') {
        throw error
      }
    }
    return false
  }

  const fetchOptions = async (
    categoryType: DictCategoryType,
    includeInactive = false,
    force = false
  ): Promise<DictCategoryOption[]> => {
    ensureTenantScopedState()
    const cacheRef = includeInactive ? optionsCache.value : activeOptionsCache.value
    const cacheKey = getOptionCacheKey(categoryType)
    if (!force && cacheRef[cacheKey]) {
      return cacheRef[cacheKey]
    }

    const res = await dictCategoryApi.getOptions(categoryType, includeInactive)
    if (res.code === 'SUCCESS' && res.data) {
      cacheRef[cacheKey] = res.data
      return res.data
    }
    return cacheRef[cacheKey] || []
  }

  const getCachedOptions = (categoryType: DictCategoryType, includeInactive = false) => {
    ensureTenantScopedState()
    return (includeInactive ? optionsCache.value : activeOptionsCache.value)[getOptionCacheKey(categoryType)] || []
  }

  const getAreaCoefficientSuggestion = async (dictName: string): Promise<DictCategoryAreaSuggestion | null> => {
    const res = await dictCategoryApi.getAreaCoefficientSuggestion({
      categoryType: currentCategory.value,
      dictName
    })
    if (res.code === 'SUCCESS' && res.data) {
      return res.data
    }
    return null
  }

  const startAreaCoefficientRecalc = async (
    categoryType: DictCategoryType,
    id: number,
    correctionId: number
  ): Promise<{ taskId: number; taskNo: string; correctionId: number } | null> => {
    const res = await dictCategoryApi.startAreaCoefficientRecalc(categoryType, id, correctionId)
    if (res.code === 'SUCCESS' && res.data) {
      ElMessage.success('已开始执行历史重算')
      return res.data
    }
    return null
  }

  const getAreaCoefficientRecalcTaskDetail = async (
    categoryType: DictCategoryType,
    id: number,
    taskId: number
  ): Promise<DictCategoryAreaCoefficientRecalcTask | null> => {
    const res = await dictCategoryApi.getAreaCoefficientRecalcTaskDetail(categoryType, id, taskId)
    if (res.code === 'SUCCESS' && res.data) {
      return res.data
    }
    return null
  }

  return {
    categories,
    list,
    total,
    pageNum,
    pageSize,
    loading,
    currentCategory,
    query,
    detail,
    init,
    fetchCategories,
    fetchList,
    changeCategory,
    search,
    resetSearch,
    getDetail,
    createItem,
    updateItem,
    updateItemStatus,
    deleteItem,
    fetchOptions,
    getCachedOptions,
    invalidateOptions,
    getAreaCoefficientSuggestion,
    startAreaCoefficientRecalc,
    getAreaCoefficientRecalcTaskDetail
  }
})
