import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  Recipe,
  RecipeForm,
  RecipeQuery,
  RecipeStatistics,
  HotRecipe,
  RecipeDetailResponse,
  RecipeImportResult
} from '@/types/recipe'
import { mapRecipeDetailToRecipe } from '@/types/recipe'
import {
  getRecipeList,
  getRecipeDetail as getRecipeDetailApi,
  createRecipe as createRecipeApi,
  updateRecipe as updateRecipeApi,
  deleteRecipe as deleteRecipeApi,
  getRecipeDashboard,
  toggleRecipeStatus as toggleRecipeStatusApi,
  getActiveCategories,
  copyRecipe as copyRecipeApi,
  importRecipes as importRecipesApi,
  downloadRecipeImportTemplate as downloadTemplateApi,
  downloadRecipeImportErrorFile as downloadErrorFileApi
} from '@/api/modules/recipe'
import type { RecipeCategoryItem } from '@/types/recipe'

export const useRecipeStore = defineStore('recipe', () => {
  /** 列表数据 */
  const list = ref<Recipe[]>([])

  /** 总数 */
  const total = ref(0)

  /** 当前页码 */
  const pageNum = ref(1)

  /** 每页条数 */
  const pageSize = ref(10)

  /** 加载状态 */
  const loading = ref(false)

  /** 搜索参数 */
  const searchParams = ref<Partial<RecipeQuery>>({
    recipeName: '',
    categoryId: undefined,
    status: undefined
  })

  /** 统计数据 */
  const statistics = ref<RecipeStatistics>({
    totalRecipes: 0,
    activeRecipes: 0,
    inactiveRecipes: 0,
    weeklyNewRecipes: 0,
    monthlyNewRecipes: 0,
    ingredientCoverage: 0,
    nutritionPassRate: 0,
    avgNutritionScore: 0,
    nutritionDistribution: {
      proteinPercent: 0,
      carbsPercent: 0,
      fatPercent: 0,
      avgCalories: 0,
      avgProtein: 0,
      avgCarbs: 0,
      avgFat: 0
    },
    categoryDistribution: [],
    weeklyHotRecipes: [],
    monthlyHotRecipes: [],
    ratingDistribution: {
      fiveStar: 0,
      fourStar: 0,
      threeStar: 0,
      twoStar: 0,
      oneStar: 0,
      avgRating: 0
    }
  })

  /** 热门菜谱 */
  const hotRecipes = ref<HotRecipe[]>([])

  /** 菜谱类别列表（从API获取） */
  const categories = ref<RecipeCategoryItem[]>([])

  /** 详情弹窗 */
  const detailVisible = ref(false)

  /** 表单弹窗 */
  const formVisible = ref(false)

  /** 当前菜谱ID */
  const currentRecipeId = ref<number | null>(null)

  /** 获取菜谱列表 */
  const fetchList = async () => {
    loading.value = true
    try {
      const res = await getRecipeList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      })

      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list || []
        total.value = res.data.total || 0
      }
    } catch (error: any) {
      console.error('获取菜谱列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  /** 获取统计数据 */
  const fetchStatistics = async () => {
    try {
      // 从dashboard API获取统计数据
      const res = await getRecipeDashboard()
      if (res.code === 'SUCCESS' && res.data) {
        statistics.value = res.data
        hotRecipes.value = res.data.weeklyHotRecipes || []
      }
    } catch (error: any) {
      console.error('获取统计数据失败:', error)
    }
  }

  /** 搜索 */
  const search = async (params: Partial<RecipeQuery>) => {
    searchParams.value = { ...searchParams.value, ...params }
    pageNum.value = 1
    await fetchList()
  }

  /** 重置搜索 */
  const resetSearch = async () => {
    searchParams.value = {
      recipeName: '',
      categoryId: undefined,
      status: undefined
    }
    pageNum.value = 1
    await fetchList()
  }

  /** 分页切换 */
  const changePage = async (page: number) => {
    pageNum.value = page
    await fetchList()
  }

  /** 每页条数改变 */
  const changePageSize = async (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  /** 删除菜谱 */
  const deleteRecipe = async (id: number) => {
    try {
      const res = await deleteRecipeApi(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        await fetchList()
        await fetchStatistics()
      } else {
        throw new Error(res.message || '删除失败')
      }
    } catch (error: any) {
      // Error displayed by interceptor
    }
  }

  /** 打开详情弹窗 */
  const openDetail = (id: number) => {
    currentRecipeId.value = id
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = () => {
    detailVisible.value = false
    currentRecipeId.value = null
  }

  /** 打开表单弹窗 */
  const openForm = (id: number | null = null) => {
    currentRecipeId.value = id
    formVisible.value = true
  }

  /** 关闭表单弹窗 */
  const closeForm = () => {
    formVisible.value = false
    currentRecipeId.value = null
  }

  /** 保存菜谱 */
  const saveRecipe = async (data: RecipeForm, recipeId: number | null) => {
    try {
      if (recipeId) {
        // 编辑 - 调用更新API
        const res = await updateRecipeApi(recipeId, data)
        if (res.code === 'SUCCESS') {
          ElMessage.success('编辑成功')
        } else {
          throw new Error(res.message || '编辑失败')
        }
      } else {
        // 新增 - 调用创建API
        const res = await createRecipeApi(data)
        if (res.code === 'SUCCESS') {
          ElMessage.success('新增成功')
        } else {
          throw new Error(res.message || '新增失败')
        }
      }

      closeForm()
      await fetchList()
      await fetchStatistics()
      return true
    } catch (error: any) {
      return false
    }
  }

  /** 获取菜谱详情 */
  const getRecipeDetail = async (id: number): Promise<Recipe | null> => {
    try {
      const res = await getRecipeDetailApi(id)
      if (res.code === 'SUCCESS' && res.data) {
        // 映射后端字段名到前端格式
        return mapRecipeDetailToRecipe(res.data as RecipeDetailResponse)
      }
      return null
    } catch (error) {
      console.error('获取菜谱详情失败:', error)
      return null
    }
  }

  /** 切换菜谱状态 */
  const toggleStatus = async (id: number) => {
    try {
      const res = await toggleRecipeStatusApi(id)

      if (res.code === 'SUCCESS') {
        ElMessage.success('状态切换成功')
        await fetchList()
      } else {
        throw new Error(res.message || '状态切换失败')
      }
    } catch (error: any) {
      // Error displayed by interceptor
    }
  }

  /** 复制菜谱 */
  const copyRecipe = async (id: number) => {
    try {
      const res = await copyRecipeApi(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('菜谱复制成功')
        await fetchList()
        return res.data
      } else {
        throw new Error(res.message || '复制失败')
      }
    } catch (error: any) {
      // Error displayed by interceptor
    }
  }

  /** 获取菜谱类别列表 */
  const fetchCategories = async () => {
    try {
      const res = await getActiveCategories()
      if (res.code === 'SUCCESS' && res.data) {
        categories.value = res.data
      }
    } catch (error: any) {
      console.error('获取菜谱类别失败:', error)
    }
  }

  /** 初始化数据 */
  const init = async () => {
    await Promise.all([fetchList(), fetchStatistics(), fetchCategories()])
  }

  /** 导入弹窗相关状态 */
  const importDialogVisible = ref(false)
  const importResultVisible = ref(false)
  const importResult = ref<RecipeImportResult | null>(null)
  const importLoading = ref(false)

  /** 打开导入弹窗 */
  const openImportDialog = () => {
    importDialogVisible.value = true
  }

  /** 关闭导入弹窗 */
  const closeImportDialog = () => {
    importDialogVisible.value = false
  }

  /** 执行导入 */
  const handleImport = async (file: File): Promise<boolean> => {
    importLoading.value = true
    try {
      const res = await importRecipesApi(file)
      if (res.code === 'SUCCESS' && res.data) {
        importResult.value = res.data
        importDialogVisible.value = false
        importResultVisible.value = true
        await fetchList()
        await fetchStatistics()
        return true
      } else {
        ElMessage.error(res.message || '导入失败')
        return false
      }
    } catch (error: any) {
      ElMessage.error(error.message || '导入失败')
      return false
    } finally {
      importLoading.value = false
    }
  }

  /** 关闭导入结果弹窗 */
  const closeImportResult = () => {
    importResultVisible.value = false
    importResult.value = null
  }

  /** 下载导入模板 */
  const downloadTemplate = async () => {
    try {
      await downloadTemplateApi()
      ElMessage.success('模板下载成功')
    } catch (error: any) {
      ElMessage.error(error.message || '下载模板失败')
    }
  }

  /** 下载导入错误文件 */
  const downloadErrorFile = async () => {
    if (!importResult.value?.errorFileUrl) return
    try {
      const fileName = importResult.value.errorFileUrl.split('/').pop() || 'recipe_import_errors.xlsx'
      await downloadErrorFileApi(fileName)
    } catch (error: any) {
      ElMessage.error(error.message || '下载错误文件失败')
    }
  }

  return {
    // state
    list,
    total,
    pageNum,
    pageSize,
    loading,
    searchParams,
    statistics,
    hotRecipes,
    categories,
    detailVisible,
    formVisible,
    currentRecipeId,
    importDialogVisible,
    importResultVisible,
    importResult,
    importLoading,
    // actions
    fetchList,
    fetchStatistics,
    fetchCategories,
    search,
    resetSearch,
    changePage,
    changePageSize,
    deleteRecipe,
    openDetail,
    closeDetail,
    openForm,
    closeForm,
    saveRecipe,
    getRecipeDetail,
    toggleStatus,
    copyRecipe,
    openImportDialog,
    closeImportDialog,
    handleImport,
    closeImportResult,
    downloadTemplate,
    downloadErrorFile,
    init
  }
})