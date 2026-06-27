<template>
  <el-dialog
    v-model="visible"
    width="800px"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    class="recipe-select-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <div class="dialog-header__content">
          <div class="recipe-select-dialog__title">选择菜谱</div>
          <button class="dialog-close" type="button" aria-label="关闭" @click="handleClose">
            <el-icon class="dialog-close__icon"><Close /></el-icon>
          </button>
        </div>
      </div>
    </template>

    <div class="select-dialog">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索菜谱名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="searchCategory" placeholder="选择类别" clearable style="width: 240px">
          <el-option
            v-for="cat in categories"
            :key="cat.id"
            :label="cat.categoryName"
            :value="cat.id"
          />
        </el-select>
        <div class="search-bar__actions">
          <el-button class="search-bar__button search-bar__button--primary" type="primary" @click="handleSearch">查询</el-button>
          <el-button class="search-bar__button search-bar__button--reset" @click="handleReset">重置</el-button>
          <el-button class="search-bar__button search-bar__button--select-all" @click="handleSelectAll">全选</el-button>
        </div>
      </div>

      <!-- 菜谱列表 -->
      <div class="recipe-grid" v-loading="loading">
        <div
          v-for="recipe in recipeList"
          :key="recipe.id"
          class="recipe-card"
          :class="{ selected: isSelected(recipe.id), disabled: isExcluded(recipe.id) }"
          @click="toggleSelect(recipe)"
        >
          <div class="recipe-image">
            <img
              v-if="getRecipeCategoryIcon(recipe.categoryName)"
              :src="getRecipeCategoryIcon(recipe.categoryName)"
              :alt="recipe.categoryName || '菜谱图标'"
              class="recipe-image__icon"
            >
            <span v-else class="placeholder">🍽</span>
          </div>
          <div class="recipe-info">
            <div class="recipe-name">{{ recipe.menuName }}</div>
            <div class="recipe-category">{{ recipe.categoryName || '未分类' }}</div>
          </div>
          <div class="select-indicator" :class="{ 'is-selected': isSelected(recipe.id) }">
            <el-icon v-if="isSelected(recipe.id)"><Check /></el-icon>
          </div>
          <div class="disabled-mask" v-if="isExcluded(recipe.id)">
            <span>已添加</span>
          </div>
        </div>

        <el-empty v-if="!loading && recipeList.length === 0" description="暂无菜谱数据" />
      </div>

      <!-- 已选择 -->
      <div class="selected-bar" v-if="selectedRecipes.length > 0">
        <span class="selected-count">已选择 {{ selectedRecipes.length }} 个菜谱</span>
        <div class="selected-tags">
          <el-tag
            v-for="recipe in selectedRecipes"
            :key="recipe.id"
            closable
            @close="removeSelected(recipe.id)"
          >
            {{ recipe.menuName }}
          </el-tag>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[12, 24, 48]"
          layout="total, prev, pager, next"
          @current-change="loadRecipes"
        />
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="dialog-footer__cancel" @click="handleClose">取消</el-button>
        <el-button class="dialog-footer__confirm" type="primary" :disabled="selectedRecipes.length === 0" @click="handleConfirm">
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Close, Search, Check } from '@element-plus/icons-vue'
import { ref, computed, watch } from 'vue'
import { getRecipeList, getActiveCategories } from '@/api/modules/recipe'
import meatIcon from '@/assets/images/recipe-category-meat.png'
import vegetableIcon from '@/assets/images/recipe-category-vegetable.png'
import stapleIcon from '@/assets/images/recipe-category-staple.png'
import fruitIcon from '@/assets/images/recipe-category-fruit.png'
import soupIcon from '@/assets/images/recipe-category-soup.png'
import type { RecipeCategoryItem } from '@/types/recipe'
import type { Recipe } from '@/types/recipe'

const props = defineProps<{
  modelValue: boolean
  excludeIds?: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'confirm': [recipes: Recipe[]]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const searchKeyword = ref('')
const searchCategory = ref<number | undefined>()
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const recipeList = ref<Recipe[]>([])
const selectedRecipes = ref<Recipe[]>([])

const categories = ref<RecipeCategoryItem[]>([])

const RECIPE_CATEGORY_ICON_MAP: Array<{ keywords: string[]; icon: string }> = [
  { keywords: ['荤', '肉'], icon: meatIcon },
  { keywords: ['素', '蔬', '青菜'], icon: vegetableIcon },
  { keywords: ['主食', '米', '面'], icon: stapleIcon },
  { keywords: ['水果', '果'], icon: fruitIcon },
  { keywords: ['汤', '汤品'], icon: soupIcon }
]

const getRecipeCategoryIcon = (categoryName?: string) => {
  if (!categoryName) return ''
  const matched = RECIPE_CATEGORY_ICON_MAP.find(({ keywords }) =>
    keywords.some(keyword => categoryName.includes(keyword))
  )
  return matched?.icon || ''
}

const fetchCategories = async () => {
  try {
    const res = await getActiveCategories()
    if (res.code === 'SUCCESS' && res.data) {
      categories.value = res.data
    }
  } catch {
    // silent
  }
}

/** 监听弹窗打开 */
watch(visible, (val) => {
  if (val) {
    selectedRecipes.value = []
    searchKeyword.value = ''
    searchCategory.value = undefined
    pageNum.value = 1
    fetchCategories()
    loadRecipes()
  }
})

/** 加载菜谱列表 */
const loadRecipes = async () => {
  loading.value = true
  recipeList.value = []
  total.value = 0
  try {
    const res = await getRecipeList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value || undefined,
      categoryId: searchCategory.value,
      status: 'active'
    })

    if (res.code === 'SUCCESS') {
      recipeList.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载菜谱失败:', error)
  } finally {
    loading.value = false
  }
}

/** 搜索 */
const handleSearch = () => {
  pageNum.value = 1
  loadRecipes()
}

const handleReset = () => {
  searchKeyword.value = ''
  searchCategory.value = undefined
  pageNum.value = 1
  loadRecipes()
}

const handleSelectAll = () => {
  const selectableRecipes = recipeList.value.filter(recipe => !isExcluded(recipe.id))
  const selectedIdSet = new Set(selectedRecipes.value.map(recipe => recipe.id))
  const nextRecipes = selectableRecipes.filter(recipe => !selectedIdSet.has(recipe.id))

  if (!nextRecipes.length) {
    return
  }

  selectedRecipes.value = [...selectedRecipes.value, ...nextRecipes]
}

/** 是否已选择 */
const isSelected = (id: number) => {
  return selectedRecipes.value.some(r => r.id === id)
}

/** 是否在排除列表中 */
const isExcluded = (id: number) => {
  return props.excludeIds?.includes(id)
}

/** 切换选择 */
const toggleSelect = (recipe: Recipe) => {
  if (isExcluded(recipe.id)) return

  const index = selectedRecipes.value.findIndex(r => r.id === recipe.id)
  if (index > -1) {
    selectedRecipes.value.splice(index, 1)
  } else {
    selectedRecipes.value.push(recipe)
  }
}

/** 移除已选择 */
const removeSelected = (id: number) => {
  const index = selectedRecipes.value.findIndex(r => r.id === id)
  if (index > -1) {
    selectedRecipes.value.splice(index, 1)
  }
}

/** 确认选择 */
const handleConfirm = () => {
  emit('confirm', [...selectedRecipes.value])
  handleClose()
}

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}
</script>

<style lang="scss" scoped>
.dialog-header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 12px 8px 0;
  gap: 13px;
}

.dialog-header__content {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  padding: 0;
  width: 100%;
  min-height: 32px;
}

.recipe-select-dialog__title {
  flex: none;
  order: 0;
  flex-grow: 0;
  width: 80px;
  height: 30px;
  margin: 0;
  font-family: 'Poppins', sans-serif;
  font-style: normal;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  text-align: center;
  color: #000000;
}

.dialog-close {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  background: #FFF2E2;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex: none;
}

.dialog-close:hover {
  background: #FFE5C2;
}

.dialog-close__icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #1C1D22;
  font-size: 24px;
}

.select-dialog {
  .dialog-footer {
    display: inline-flex;
    gap: 12px;
  }

  .dialog-footer__cancel {
    box-sizing: border-box;
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    background: #FFFFFF;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    display: inline-flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    flex: none;
    order: 0;
    flex-grow: 0;
  }

  .dialog-footer__cancel :deep(span) {
    width: 26px;
    height: 22px;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 13px;
    line-height: 22px;
    text-align: center;
    color: #53545C;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .dialog-footer__confirm {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    border: none;
    border-radius: 6px;
    background: #7288FA;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
    display: inline-flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    flex: none;
    order: 1;
    flex-grow: 0;
  }

  .dialog-footer__confirm :deep(span) {
    width: 28px;
    height: 22px;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    text-align: center;
    color: #FFFFFF;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .search-bar {
    display: flex;
    gap: 12px;
    margin-top: 8px;
    margin-bottom: 16px;
  }

  .search-bar__actions {
    display: flex;
    gap: 8px;
    margin-left: 18px;
  }

  .search-bar__button {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    border-radius: 6px;
    display: inline-flex;
    flex: none;
    justify-content: center;
    align-items: center;
  }

  .search-bar__button :deep(span) {
    width: 28px;
    height: 22px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    text-align: center;
  }

  .search-bar__button--primary {
    order: 0;
    flex-grow: 0;
    gap: 10px;
    border: none;
    background: #7288FA;
  }

  .search-bar__button--primary :deep(span) {
    color: #FFFFFF;
  }

  .search-bar__button--reset {
    order: 1;
    flex-grow: 0;
    gap: 8px;
    border: none;
    background: #F2F4F8;
  }

  .search-bar__button--reset :deep(span) {
    color: rgba(0, 0, 0, 0.9);
  }

  .search-bar__button--select-all {
    box-sizing: border-box;
    order: 2;
    flex-grow: 0;
    gap: 10px;
    border: 1px solid #DCDCDC;
    background: #FFFFFF;
  }

  .search-bar__button--select-all :deep(span) {
    color: rgba(0, 0, 0, 0.9);
  }

  .recipe-grid {
    display: grid;
    grid-template-columns: repeat(3, 226px);
    column-gap: 30px;
    row-gap: 16px;
    min-height: 300px;
    max-height: 400px;
    overflow-y: auto;
    padding: 4px 0;
    align-content: start;
    justify-content: start;

    .recipe-card {
      position: relative;
      box-sizing: border-box;
      display: flex;
      align-items: center;
      gap: 16px;
      width: 226px;
      height: 54px;
      padding: 9px 12px;
      background: #F8F7F7;
      box-shadow: 0px 12px 16px -4px rgba(220, 220, 220, 0.6);
      border-radius: 12px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover:not(.disabled) {
        transform: translateY(-1px);
      }

      &.selected {
        .select-indicator {
          background: #7288FA;
          border-color: #7288FA;
        }
      }

      &.disabled {
        opacity: 0.6;
        cursor: not-allowed;

        .disabled-mask {
          display: flex;
        }
      }

      .recipe-image {
        width: 36px;
        height: 36px;
        flex: none;
        border-radius: 10.8px;
        background: #7288FA;
        display: flex;
        align-items: center;
        justify-content: center;
        overflow: hidden;

        .recipe-image__icon {
          width: 100%;
          height: 100%;
          display: block;
          object-fit: cover;
        }

        .placeholder {
          font-size: 18px;
          line-height: 1;
          color: rgba(255, 255, 255, 0.9);
        }
      }

      .recipe-info {
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 8px;
        min-width: 0;
        flex: 1;

        .recipe-name {
          font-family: 'PingFang SC', sans-serif;
          font-style: normal;
          font-weight: 500;
          font-size: 13px;
          line-height: 13px;
          color: #667187;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .recipe-category {
          font-family: 'PingFang SC', sans-serif;
          font-style: normal;
          font-weight: 500;
          font-size: 13px;
          line-height: 13px;
          color: #E96466;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .select-indicator {
        box-sizing: border-box;
        width: 16px;
        height: 16px;
        flex: none;
        border: 1px solid #DCDCDC;
        border-radius: 3px;
        background: #FFFFFF;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: rgba(255, 255, 255, 0.9);
      }

      .select-indicator :deep(svg) {
        width: 10px;
        height: 10px;
      }

      .disabled-mask {
        display: none;
        position: absolute;
        inset: 0;
        border-radius: 12px;
        background: rgba(255, 255, 255, 0.82);
        align-items: center;
        justify-content: center;
        font-size: 13px;
        color: #909399;
      }
    }
  }

  .selected-bar {
    margin-top: 16px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 6px;

    .selected-count {
      font-size: 13px;
      color: #606266;
      margin-bottom: 8px;
      display: block;
    }

    .selected-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  }

  .pagination-wrapper {
    margin-top: 8px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>

<style lang="scss">
.recipe-select-dialog .el-dialog__body {
  border-bottom: none !important;
}

.recipe-select-dialog .el-dialog__footer {
  padding: 12px 24px 16px;
  border-top: none !important;
  box-shadow: none !important;
}
</style>
