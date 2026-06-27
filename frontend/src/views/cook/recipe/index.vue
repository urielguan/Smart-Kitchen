<script setup lang="ts">
import { ref, onMounted, onActivated, computed } from 'vue'
import { useRecipeStore } from '@/stores/modules/recipe'
import RecipeStatistics from '@/components/business/recipe/RecipeStatistics.vue'
import RecipeTable from '@/components/business/recipe/RecipeTable.vue'
import RecipeForm from '@/components/business/recipe/RecipeForm.vue'
import RecipeDetail from '@/components/business/recipe/RecipeDetail.vue'
import RecipeImportDialog from '@/components/business/recipe/RecipeImportDialog.vue'
import RecipeImportResultDialog from '@/components/business/recipe/RecipeImportResultDialog.vue'
import type { Recipe, RecipeForm as RecipeFormType } from '@/types/recipe'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RECIPE_PERMISSIONS } from '@/constants/permission'
import { exportRecipes } from '@/api/modules/recipe'

const recipeStore = useRecipeStore()

/** 搜索表单（从 store 恢复上次的查询条件） */
const searchForm = ref({
  recipeName: recipeStore.searchParams.recipeName || '',
  categoryId: recipeStore.searchParams.categoryId as number | undefined,
  status: recipeStore.searchParams.status as string | undefined
})

/** 表单弹窗 */
const formVisible = ref(false)

/** 详情弹窗 */
const detailVisible = ref(false)

/** 当前编辑的菜谱 */
const currentRecipe = ref<Recipe | null>(null)

/** 当前菜谱ID */
const currentRecipeId = ref<number | null>(null)
const recipeActivatedOnce = ref(false)

/** 操作进行中的菜谱ID，防止并发操作 */
const actionLoadingId = ref<number | null>(null)

/** 类别选项（从API获取） */
const categoryOptions = computed(() => {
  return recipeStore.categories.map(cat => ({
    label: cat.categoryName,
    value: cat.id
  }))
})

/** 初始化：有缓存数据时仅刷新统计和类别，不重置列表 */
onMounted(() => {
  if (recipeStore.list.length > 0) {
    // 已有数据，仅刷新统计和类别下拉
    recipeStore.fetchStatistics()
    recipeStore.fetchCategories()
  } else {
    recipeStore.init()
  }
})

const refreshRecipePage = async () => {
  await Promise.all([
    recipeStore.fetchList(),
    recipeStore.fetchStatistics(),
    recipeStore.fetchCategories()
  ])
}

onActivated(async () => {
  if (!recipeActivatedOnce.value) {
    recipeActivatedOnce.value = true
    return
  }
  await refreshRecipePage()
})

/** 搜索 */
const handleSearch = () => {
  recipeStore.search(searchForm.value)
}

const showLengthTip = ref(false)
let lengthTipTimer: ReturnType<typeof setTimeout> | null = null

const triggerSearchTip = () => {
  if (lengthTipTimer) clearTimeout(lengthTipTimer)
  showLengthTip.value = false
  requestAnimationFrame(() => { showLengthTip.value = true })
  lengthTipTimer = setTimeout(() => { showLengthTip.value = false }, 2000)
}

const handleSearchInput = () => {
  if (searchForm.value.recipeName.length >= 20) triggerSearchTip()
  else showLengthTip.value = false
}

const handleSearchKeydown = (e: KeyboardEvent) => {
  if (searchForm.value.recipeName.length >= 20 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerSearchTip()
  }
}

/** 重置 */
const handleReset = () => {
  searchForm.value = {
    recipeName: '',
    categoryId: undefined,
    status: undefined
  }
  recipeStore.resetSearch()
}

/** 新增 */
const handleAdd = () => {
  currentRecipeId.value = null
  currentRecipe.value = null
  formVisible.value = true
}

/** 详情 */
const handleDetail = async (row: Recipe) => {
  currentRecipeId.value = row.id
  // 从API获取完整详情
  const detail = await recipeStore.getRecipeDetail(row.id)
  if (detail) {
    currentRecipe.value = detail
  } else {
    // 如果获取失败，使用列表数据
    currentRecipe.value = row
  }
  detailVisible.value = true
}

/** 编辑 */
const handleEdit = async (row: Recipe) => {
  currentRecipeId.value = row.id
  // 从API获取完整详情（包含食材、制作步骤等）
  const detail = await recipeStore.getRecipeDetail(row.id)
  if (detail) {
    currentRecipe.value = detail
  } else {
    // 如果获取失败，使用列表数据
    currentRecipe.value = row
  }
  formVisible.value = true
}

/** 导出 */
const handleExport = async () => {
  try {
    await exportRecipes(recipeStore.searchParams)
    ElMessage.success('导出成功')
  } catch (error: any) {
    ElMessage.error(error.message || '导出失败')
  }
}

/** 启用/停用 */
const handleToggle = async (row: Recipe) => {
  if (actionLoadingId.value !== null) return
  const action = row.status === 'active' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}该菜谱？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    actionLoadingId.value = row.id
    await recipeStore.toggleStatus(row.id)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${action}失败`)
    }
  } finally {
    actionLoadingId.value = null
  }
}

/** 删除 */
const handleDelete = async (row: Recipe) => {
  if (actionLoadingId.value !== null) return
  try {
    await ElMessageBox.confirm('确认删除该菜谱？删除后将无法恢复', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    actionLoadingId.value = row.id
    await recipeStore.deleteRecipe(row.id)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  } finally {
    actionLoadingId.value = null
  }
}

/** 复制菜谱 */
const handleCopy = async (row: Recipe) => {
  if (actionLoadingId.value !== null) return
  try {
    actionLoadingId.value = row.id
    // 加载完整详情
    const detail = await recipeStore.getRecipeDetail(row.id)
    if (detail) {
      // 新增模式，但预填数据
      currentRecipeId.value = null
      // 清空编码、修改名称，清掉食材关联 ID
      const copied = {
        ...detail,
        menuCode: '',
        menuName: detail.menuName + '（副本）',
        ingredients: detail.ingredients?.map(ing => ({
          ...ing,
          id: undefined,
          menuId: undefined
        })) || []
      }
      currentRecipe.value = copied
      formVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载菜谱详情失败')
  } finally {
    actionLoadingId.value = null
  }
}

/** 分页改变 */
const handlePageChange = (page: number) => {
  recipeStore.changePage(page)
}

/** 每页条数改变 */
const handleSizeChange = (size: number) => {
  recipeStore.changePageSize(size)
}

/** 表单提交成功 */
const handleFormSuccess = async (data: RecipeFormType) => {
  const success = await recipeStore.saveRecipe(data, currentRecipeId.value)
  if (success) {
    formVisible.value = false
  }
}

/** 详情页编辑 */
const handleDetailEdit = (recipe: Recipe) => {
  currentRecipe.value = recipe
  detailVisible.value = false
  currentRecipeId.value = recipe.id
  formVisible.value = true
}
</script>

<template>
  <div class="recipe-page">
    <RecipeStatistics :statistics="recipeStore.statistics" :hot-recipes="recipeStore.hotRecipes" />

    <!-- 搜索工具栏 -->
    <div class="toolbar toolbar--search">
      <div class="toolbar-inner">
        <div class="search-fields">
          <div class="search-item search-item--input">
            <div class="search-input-wrapper">
              <transition name="input-tip">
                <span v-if="showLengthTip" class="input-length-tip">菜谱名称最多输入20个字</span>
              </transition>
              <input
                v-model="searchForm.recipeName"
                type="text"
                placeholder="请输入菜谱名称"
                class="search-input"
                maxlength="20"
                @input="handleSearchInput"
                @keydown="handleSearchKeydown"
                @keyup.enter="handleSearch"
              />
            </div>
          </div>

          <div class="search-item search-item--select">
            <el-select
              v-model="searchForm.categoryId"
              placeholder="请选择菜谱类别"
              clearable
              class="category-select"
            >
              <el-option
                v-for="item in categoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>

          <div class="search-item search-item--select">
            <el-select
              v-model="searchForm.status"
              placeholder="请选择状态"
              clearable
              class="status-select"
            >
              <el-option label="启用" value="active" />
              <el-option label="停用" value="inactive" />
            </el-select>
          </div>
        </div>

        <div class="toolbar-actions toolbar-actions--search">
          <button class="btn-search" @click="handleSearch">查询</button>
          <button class="btn-reset" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <div class="table-panel">
      <div class="table-panel__header">
        <div class="table-action-bar">
          <button class="btn-primary btn-add" v-permission="RECIPE_PERMISSIONS.CREATE" @click="handleAdd">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            新增菜谱
          </button>

          <button class="btn-outline btn-import" @click="recipeStore.openImportDialog()">导入</button>

          <button class="btn-outline btn-export" @click="handleExport">导出</button>
        </div>
      </div>

      <RecipeTable
        :data="recipeStore.list"
        :loading="recipeStore.loading"
        :action-loading-id="actionLoadingId"
        :page-num="recipeStore.pageNum"
        :page-size="recipeStore.pageSize"
        @detail="handleDetail"
        @edit="handleEdit"
        @copy="handleCopy"
        @delete="handleDelete"
        @toggle="handleToggle"
      />

      <div class="pagination">
        <div class="pagination-info">共 {{ recipeStore.total }} 条</div>
        <el-pagination
          v-model:current-page="recipeStore.pageNum"
          v-model:page-size="recipeStore.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="recipeStore.total"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
          class="custom-pagination"
        />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <RecipeDetail
      v-model="detailVisible"
      :recipe="currentRecipe"
      @edit="handleDetailEdit"
    />

    <!-- 新增/编辑弹窗 -->
    <RecipeForm
      v-model="formVisible"
      :recipe-id="currentRecipeId"
      :recipe-data="currentRecipe"
      @success="handleFormSuccess"
    />

    <!-- 导入弹窗 -->
    <RecipeImportDialog />

    <!-- 导入结果弹窗 -->
    <RecipeImportResultDialog />
  </div>
</template>

<style lang="scss" scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;600;700&display=swap');

.recipe-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 16px 24px 24px;
  background: transparent;
  position: relative;
}

.toolbar {
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(114, 136, 250, 0.06);
  flex-shrink: 0;

  &.toolbar--search {
    padding: 16px;
    margin-bottom: 16px;

    .toolbar-inner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 24px;
      min-height: 32px;
    }

    .search-fields {
      display: flex;
      align-items: center;
      gap: 24px;
      flex: 1;
      min-width: 0;
    }

    .search-item {
      min-width: 0;

      &.search-item--input,
      &.search-item--select {
        width: 200px;
      }
    }

    .search-input-wrapper {
      position: relative;
      width: 100%;

      .input-length-tip {
        position: absolute;
        bottom: calc(100% + 6px);
        left: 0;
        background: #e6a23c;
        color: #fff;
        font-size: 12px;
        padding: 4px 10px;
        border-radius: 4px;
        white-space: nowrap;
        z-index: 10;

        &::after {
          content: '';
          position: absolute;
          top: 100%;
          left: 16px;
          border: 5px solid transparent;
          border-top-color: #e6a23c;
        }
      }

      .input-tip-enter-active,
      .input-tip-leave-active {
        transition: opacity 0.2s;
      }

      .input-tip-enter-from,
      .input-tip-leave-to {
        opacity: 0;
      }

      .search-input {
        width: 100%;
        height: 32px;
        padding: 4px 8px;
        border: 1px solid #dcdcdc;
        border-radius: 3px;
        font-size: 14px;
        line-height: 22px;
        color: rgba(0, 0, 0, 0.9);
        background: #ffffff;
        transition: border-color 0.2s, box-shadow 0.2s;

        &::placeholder {
          color: rgba(0, 0, 0, 0.4);
        }

        &:focus {
          outline: none;
          border-color: #7288fa;
          box-shadow: 0 0 0 2px rgba(114, 136, 250, 0.12);
        }
      }
    }

    .category-select,
    .status-select {
      width: 100%;

      :deep(.el-input__wrapper) {
        border-radius: 3px;
        border: 1px solid #dcdcdc;
        box-shadow: none;
        min-height: 32px;
        padding: 4px 8px;

        &:hover {
          border-color: #c9cdd4;
        }

        &.is-focus {
          border-color: #7288fa;
          box-shadow: 0 0 0 2px rgba(114, 136, 250, 0.12);
        }
      }

      :deep(.el-input__inner) {
        height: 22px;
        font-size: 14px;
        color: rgba(0, 0, 0, 0.9);
      }

      :deep(.el-input__inner::placeholder) {
        color: rgba(0, 0, 0, 0.4);
      }

      :deep(.el-select__caret) {
        color: rgba(0, 0, 0, 0.4);
        font-size: 16px;
      }
    }

    .toolbar-actions--search {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;

      button {
        display: flex;
        flex-direction: row;
        justify-content: center;
        align-items: center;
        gap: 10px;
        width: 60px;
        min-width: 60px;
        height: 32px;
        padding: 5px 16px;
        box-sizing: border-box;
        border: none;
        border-radius: 6px;
        white-space: nowrap;
        flex: none;
        order: 0;
        flex-grow: 0;
        flex-shrink: 0;
        font-size: 14px;
        font-weight: 400;
        line-height: 22px;
      }

      .btn-search {
        background: #7288fa;
        color: #ffffff;
        box-shadow: none;

        &:hover {
          transform: none;
          background: #637aff;
          box-shadow: none;
        }
      }

      .btn-reset {
        background: #f2f4f8;
        color: rgba(0, 0, 0, 0.9);
        border: none;

        &:hover {
          background: #e9edf5;
          border: none;
        }
      }
    }
  }

  @media (max-width: 1200px) {
    &.toolbar--search {
      .toolbar-inner {
        flex-direction: column;
        align-items: stretch;
        gap: 16px;
      }

      .search-fields {
        flex-wrap: wrap;
      }

      .search-item {
        &.search-item--input,
        &.search-item--select {
          width: calc(50% - 12px);
        }
      }

      .toolbar-actions--search {
        justify-content: flex-end;
      }
    }
  }

  @media (max-width: 768px) {
    &.toolbar--search {
      .search-fields {
        flex-direction: column;
      }

      .search-item {
        &.search-item--input,
        &.search-item--select {
          width: 100%;
        }
      }
    }
  }
}

.table-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  margin-bottom: 16px;
  background: #ffffff;
  border-radius: 8px;
}

.table-panel__header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.table-action-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  white-space: nowrap;
  overflow-x: auto;
}

.table-action-bar :deep(.el-dropdown) {
  display: inline-flex;
  flex: none;
}

.table-action-bar button {
  box-sizing: border-box;
  display: flex;
  flex-direction: row;
  justify-content: center;
  align-items: center;
  padding: 5px 16px;
  gap: 8px;
  height: 32px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 13px;
  line-height: 22px;
  text-align: center;
  border-radius: 6px;
  transition: all 0.2s ease;
  flex: none;
  flex-grow: 0;
  flex-shrink: 0;
  white-space: nowrap;
}

.table-action-bar button svg {
  width: 14px;
  height: 14px;
  flex: none;
}

.btn-primary {
  width: 110px;
  background: #7288fa;
  color: #ffffff;
  border: none;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;

  &:hover {
    background: #637aff;
  }
}

.btn-outline {
  background: #ffffff;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
}

.btn-import {
  width: 58px;
  border: 1px solid #7288fa;
  color: #7288fa;

  &:hover:not(:disabled) {
    background: #f5f8ff;
  }
}

.btn-export {
  width: 58px;
  border: 1px solid #bec0ca;
  color: #53545c;

  &:hover {
    background: #fafcff;
  }
}

.btn-help {
  width: 84px;
  background: #ffffff;
  border: 1px solid #bec0ca;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545c;

  &:hover {
    background: #fafcff;
  }
}

.btn-muted {
  width: 80px;
  background: #ffffff;
  border: 1px solid #bec0ca;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545c;

  &:hover {
    background: #fafcff;
  }
}

.import-upload {
  display: inline-flex;

  :deep(.el-upload) {
    display: inline-flex;
  }
}

.pagination {
  width: 100%;
  min-height: 64px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
}

.pagination-info {
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.6);
}

.pagination .custom-pagination {
  :deep(.el-pagination) {
    display: flex;
    align-items: center;
    gap: 16px;

    .el-select .el-input__wrapper {
      min-height: 32px;
      padding: 5px 8px;
      border: 1px solid #dcdcdc;
      border-radius: 3px;
      box-shadow: none;
    }

    .btn-prev,
    .btn-next,
    .el-pager li {
      width: 32px;
      min-width: 32px;
      height: 32px;
      border-radius: 3px;
      border: 1px solid #dcdcdc;
      background: #ffffff;
      color: rgba(0, 0, 0, 0.9);
      font-size: 14px;
      font-weight: 400;
      margin: 0;
    }

    .btn-prev:disabled,
    .btn-next:disabled {
      border-color: transparent;
      background: transparent;
      color: rgba(0, 0, 0, 0.26);
    }

    .el-pager li.is-active {
      background: #7288fa;
      border-color: #7288fa;
      color: rgba(255, 255, 255, 0.9);
    }
  }
}

@media (max-width: 1200px) {
  .table-panel__header {
    flex-direction: column;
    align-items: stretch;
  }

  .table-action-bar {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .recipe-page {
    padding: 16px;
  }

  .table-panel,
  .toolbar.toolbar--search,
  .pagination {
    padding-left: 14px;
    padding-right: 14px;
  }

  .pagination {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
