<script setup lang="ts">
import { computed, ref, onActivated, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMaterialStore } from '@/stores/modules/material'
import { STOCK_STATUS_OPTIONS } from '@/constants/material'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { mapDictOptions } from '@/utils/dict-category'
import MaterialStatistics from '@/components/business/material/MaterialStatistics.vue'
import MaterialTable from '@/components/business/material/MaterialTable.vue'
import MaterialForm from '@/components/business/material/MaterialForm.vue'
import MaterialDetail from '@/components/business/material/MaterialDetail.vue'
import MaterialImportDialog from '@/components/business/material/MaterialImportDialog.vue'
import MaterialImportResultDialog from '@/components/business/material/MaterialImportResultDialog.vue'
import MaterialNutritionMappingDialog from '@/components/business/material/MaterialNutritionMappingDialog.vue'
import type { Material, StockStatus } from '@/types/material'
import { MATERIAL_PERMISSIONS } from '@/constants/permission'

const materialStore = useMaterialStore()
const dictCategoryStore = useDictCategoryStore()
const route = useRoute()
const router = useRouter()
const materialActivatedOnce = ref(false)

type NutritionFilter = 'all' | 'unmapped' | 'pending-sync' | 'ready'

/** 搜索表单 */
const searchForm = ref({
  materialName: materialStore.searchParams.materialName || '',
  categoryName: materialStore.searchParams.categoryName,
  stockStatus: materialStore.searchParams.stockStatus as StockStatus | undefined
})

/** 当前编辑的物料 */
const currentMaterial = ref<Material | null>(null)
const nutritionFilter = ref<NutritionFilter>('all')

const nutritionFilterOptions: Array<{ label: string; value: NutritionFilter }> = [
  { label: '全部物料', value: 'all' },
  { label: '待建立映射', value: 'unmapped' },
  { label: '待同步营养', value: 'pending-sync' },
  { label: '已同步营养', value: 'ready' }
]

const routeSource = computed(() => typeof route.query.source === 'string' ? route.query.source : '')
const routeKeyword = computed(() => typeof route.query.keyword === 'string' ? route.query.keyword : '')
const isRecipeContext = computed(() => routeSource.value === 'recipe')
const routeFilterLabel = computed(() => {
  return nutritionFilterOptions.find(item => item.value === nutritionFilter.value)?.label || '全部物料'
})

const materialCategoryOptions = computed(() => mapDictOptions(
  dictCategoryStore.getCachedOptions('material_category')
))

/** 字典缓存被清除后自动重新拉取 */
const dictCached = computed(() => dictCategoryStore.getCachedOptions('material_category'))
watch(dictCached, (val, oldVal) => {
  if (oldVal && oldVal.length > 0 && val.length === 0) {
    dictCategoryStore.fetchOptions('material_category')
  }
})

const refreshMaterialPage = async () => {
  applyRouteQuery()
  await Promise.all([
    dictCategoryStore.fetchOptions('material_category', false, true),
    materialStore.fetchList(),
    materialStore.fetchStatistics()
  ])
}

/** 初始化 */
onMounted(async () => {
  applyRouteQuery()
  await Promise.all([
    dictCategoryStore.fetchOptions('material_category', false, true),
    materialStore.init()
  ])
})

onActivated(async () => {
  if (!materialActivatedOnce.value) {
    materialActivatedOnce.value = true
    return
  }
  await refreshMaterialPage()
})

const matchesNutritionFilter = (material: Material) => {
  if (nutritionFilter.value === 'unmapped') return !material.foodItemId
  if (nutritionFilter.value === 'pending-sync') return !!material.foodItemId && !material.nutritionSourceType
  if (nutritionFilter.value === 'ready') return !!material.nutritionSourceType
  return true
}

const filteredList = computed(() => materialStore.list.filter(matchesNutritionFilter))

const nutritionOverview = computed(() => ({
  unmapped: materialStore.list.filter(item => !item.foodItemId).length,
  pendingSync: materialStore.list.filter(item => !!item.foodItemId && !item.nutritionSourceType).length,
  ready: materialStore.list.filter(item => !!item.nutritionSourceType).length
}))

const applyRouteQuery = () => {
  const keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  const rawFilter = typeof route.query.nutritionStatus === 'string' ? route.query.nutritionStatus : 'all'
  const queryFilter = rawFilter === 'unsynced' ? 'pending-sync' : rawFilter
  if (keyword) {
    searchForm.value.materialName = keyword
    materialStore.searchParams.materialName = keyword
  }
  nutritionFilter.value = nutritionFilterOptions.some(item => item.value === queryFilter) ? queryFilter : 'all'
}

watch(() => route.query, async () => {
  applyRouteQuery()
  if (materialStore.initialized) {
    await materialStore.search(searchForm.value)
  }
}, { deep: true })

/** 搜索 */
const handleSearch = () => {
  materialStore.search(searchForm.value)
}

/** 重置 */
const handleReset = () => {
  searchForm.value = {
    materialName: '',
    categoryName: undefined,
    stockStatus: undefined
  }
  nutritionFilter.value = 'all'
  router.replace({ query: {} })
  materialStore.resetSearch()
}

/** 新增 */
const handleAdd = () => {
  currentMaterial.value = null
  materialStore.openForm(null)
}

/** 详情 */
const handleDetail = (row: Material) => {
  materialStore.openDetail(row.id)
}

/** 编辑 */
const handleEdit = (row: Material) => {
  // 使用深拷贝确保是新的对象引用，触发 watch
  currentMaterial.value = JSON.parse(JSON.stringify(row))
  materialStore.openForm(row.id)
}

/** 删除 */
const handleDelete = (row: Material) => {
  materialStore.deleteMaterial(row.id)
}

/** 切换物料状态 */
const handleToggleStatus = (row: Material) => {
  materialStore.toggleStatus(row)
}

/** 分页改变 */
const handlePageChange = (page: number) => {
  materialStore.changePage(page)
}

/** 每页条数改变 */
const handleSizeChange = (size: number) => {
  materialStore.changePageSize(size)
}

/** 表单提交成功 */
const handleFormSuccess = () => {
  materialStore.fetchList()
  materialStore.fetchStatistics()
  materialStore.fetchActiveList(true)
}

/** 详情页编辑 */
const handleDetailEdit = (material: Material) => {
  // 使用深拷贝确保是新的对象引用，触发 watch
  currentMaterial.value = JSON.parse(JSON.stringify(material))
  materialStore.closeDetail()
  materialStore.openForm(material.id)
}

/** 导入 */
const handleImport = () => {
  materialStore.openImportDialog()
}

/** 导出 */
const exporting = ref(false)
const handleExport = async () => {
  exporting.value = true
  try {
    await materialStore.handleExport(searchForm.value)
  } finally {
    exporting.value = false
  }
}

const handleFoodInit = async () => {
  const result = await materialStore.importFoodJson()
  if (result) {
    await materialStore.fetchFoodLibraryStatus()
  }
}

const handleMapping = (row: Material) => {
  materialStore.openMappingDialog(row)
}

const handleMappingSuccess = async () => {
  await Promise.all([materialStore.fetchList(), materialStore.fetchStatistics()])
}

const handleNutritionFilterChange = (filter: NutritionFilter) => {
  nutritionFilter.value = filter
  router.replace({
    query: {
      ...route.query,
      nutritionStatus: filter === 'all' ? undefined : filter
    }
  })
}

const clearRecipeContext = () => {
  nutritionFilter.value = 'all'
  searchForm.value = {
    materialName: '',
    categoryName: undefined,
    stockStatus: undefined
  }
  router.replace({ path: '/material', query: {} })
}
</script>

<template>
  <div class="material-page">
    <!-- 统计卡片 -->
    <MaterialStatistics />

    <!-- 搜索工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="4">
          <el-input
            v-model="searchForm.materialName"
            placeholder="物料名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="3">
          <el-select
            v-model="searchForm.categoryName"
            placeholder="全部类别"
            clearable
          >
            <el-option
              v-for="item in materialCategoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select
            v-model="searchForm.stockStatus"
            placeholder="全部状态"
            clearable
          >
            <el-option
              v-for="item in STOCK_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="14" style="text-align: right">
          <el-button class="btn-search" @click="handleSearch">查询</el-button>
          <el-button class="btn-reset" @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 数据表格 -->
    <div class="table-wrapper">
      <div v-if="isRecipeContext" class="context-banner">
        <div class="context-copy">
          <strong>当前为菜谱营养处置视图</strong>
          <span>
            这是同一个“物料信息管理”页面，系统已按菜谱来源自动带入筛选：
            {{ routeFilterLabel }}<template v-if="routeKeyword">，关键词“{{ routeKeyword }}”</template>。
          </span>
        </div>
        <el-button class="btn-reset" @click="clearRecipeContext">返回完整物料视图</el-button>
      </div>

      <div class="nutrition-banner">
        <div class="banner-copy">
          <strong v-if="materialStore.foodLibraryStatus === 'ready'">标准食品库已就绪</strong>
          <strong v-else-if="materialStore.foodLibraryStatus === 'unavailable'">标准食品库接口当前不可用</strong>
          <strong v-else>标准食品库状态待检查</strong>
          <span v-if="materialStore.foodLibraryStatus === 'ready'">
            当前已识别 {{ materialStore.foodLibraryCategoryCount }} 个分类，可直接对业务物料做人工映射并同步营养。
          </span>
          <span v-else-if="materialStore.foodLibraryStatus === 'unavailable'">
            {{ materialStore.foodLibraryStatusMessage || '当前联调环境未部署标准食品库接口，物料基础管理可正常使用，营养映射能力暂不可用。' }}
          </span>
          <span v-else>
            物料基础管理已正常加载。为避免缺失接口影响页面打开，标准食品库状态改为在使用营养映射能力时按需检查。
          </span>
          <span v-if="materialStore.foodLibraryStatus !== 'unavailable' && materialStore.foodLibraryStatus !== 'ready'">
            请先执行一次 JSON 初始化，再进入“营养映射”建立物料与标准食品的正式关系。
          </span>
          <span v-if="materialStore.latestFoodImportResult" class="import-result-text">
            最近导入：{{ materialStore.latestFoodImportResult.categoryCount }} 个分类 / {{ materialStore.latestFoodImportResult.itemCount }} 条食品项 / 版本 {{ materialStore.latestFoodImportResult.sourceVersion }}
          </span>
        </div>
        <el-button
          class="btn-sync"
          :loading="materialStore.foodImportLoading"
          :disabled="materialStore.foodLibraryStatus === 'unavailable'"
          @click="handleFoodInit"
        >
          {{ materialStore.foodLibraryReady ? '重新初始化食品库' : '初始化食品库' }}
        </el-button>
      </div>

      <div class="nutrition-filters">
        <button
          v-for="item in nutritionFilterOptions"
          :key="item.value"
          class="filter-chip"
          :class="{ active: nutritionFilter === item.value }"
          @click="handleNutritionFilterChange(item.value)"
        >
          {{ item.label }}
        </button>
        <div class="filter-stats">
          <span>待映射 {{ nutritionOverview.unmapped }}</span>
          <span>待同步 {{ nutritionOverview.pendingSync }}</span>
          <span>已同步 {{ nutritionOverview.ready }}</span>
        </div>
      </div>

      <div class="table-header">
        <el-button class="btn-search" v-permission="MATERIAL_PERMISSIONS.CREATE" @click="handleAdd">+ 新增物料</el-button>
        <el-button class="btn-import" v-permission="MATERIAL_PERMISSIONS.IMPORT" @click="handleImport">导入</el-button>
        <el-button class="btn-export" v-permission="MATERIAL_PERMISSIONS.EXPORT" :loading="exporting" @click="handleExport">导出</el-button>
        <el-button
          class="btn-sync"
          :loading="materialStore.foodImportLoading"
          :disabled="materialStore.foodLibraryStatus === 'unavailable'"
          @click="handleFoodInit"
        >
          初始化食品库
        </el-button>
        <el-button class="btn-help">帮助说明</el-button>
        <el-button class="btn-more"><span class="dots">⋮</span>更多</el-button>
      </div>
      <MaterialTable
        :data="filteredList"
        :loading="materialStore.loading"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete"
        @toggle-status="handleToggleStatus"
        @mapping="handleMapping"
      />
      <div class="pagination">
        <span class="total">
          共 {{ materialStore.total }} 项数据<span v-if="nutritionFilter !== 'all'">，当前筛选命中 {{ filteredList.length }} 项</span>
        </span>
        <el-pagination
          v-model:current-page="materialStore.pageNum"
          v-model:page-size="materialStore.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="materialStore.total"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <MaterialDetail
      v-model="materialStore.detailVisible"
      :material-id="materialStore.currentMaterialId"
      @edit="handleDetailEdit"
      @mapping="handleMapping"
    />

    <!-- 新增/编辑弹窗 -->
    <MaterialForm
      v-model="materialStore.formVisible"
      :material-id="materialStore.currentMaterialId"
      :material-data="currentMaterial"
      @success="handleFormSuccess"
    />

    <!-- 导入弹窗 -->
    <MaterialImportDialog />

    <!-- 导入结果弹窗 -->
    <MaterialImportResultDialog />

    <MaterialNutritionMappingDialog
      v-model="materialStore.mappingDialogVisible"
      :material="materialStore.currentMappingMaterial"
      @success="handleMappingSuccess"
    />
  </div>
</template>

<style lang="scss" scoped>
.material-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.toolbar {
  background: $bg-white;
  padding: 20px;
  border-radius: $border-radius-large;
  margin-bottom: 20px;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;

  .el-input,
  .el-select {
    width: 100%;
  }

  .btn-search {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    color: #fff;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-reset {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    background: #F2F4F8;
    border-color: #F2F4F8;
    border-radius: 6px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);

    &:hover {
      background: #E3E7EF;
      border-color: #E3E7EF;
      color: rgba(0, 0, 0, 0.9);
    }
  }
}

.table-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.context-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: linear-gradient(135deg, rgba(114, 136, 250, 0.08) 0%, rgba(255, 255, 255, 0.98) 100%);
  border-bottom: 1px solid rgba(114, 136, 250, 0.12);

  .context-copy {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  strong {
    font-size: 14px;
    color: #3e63dd;
  }

  span {
    font-size: 12px;
    color: #667085;
    line-height: 1.6;
  }
}

.nutrition-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 16px 8px;

  .banner-copy {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  strong {
    font-size: 15px;
    color: #1f2937;
  }

  span {
    font-size: 13px;
    color: #667085;
  }

  .import-result-text {
    color: #3e63dd;
  }
}

.nutrition-filters {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 8px 16px 4px;
  flex-wrap: wrap;
}

.filter-chip {
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid #dbe3f4;
  background: #f8fbff;
  color: #475467;
  cursor: pointer;

  &.active {
    background: #ecf2ff;
    border-color: #9cb5ff;
    color: #3e63dd;
  }
}

.filter-stats {
  display: flex;
  gap: 16px;
  color: #667085;
  font-size: 13px;
}

.table-header {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
  padding: 16px 16px 16px;
  flex-shrink: 0;

  .btn-search {
    width: 110px;
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
    color: #fff;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-import {
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #7288FA;

    &:hover {
      background: #EEF1FF;
      border-color: #5C75E8;
      color: #5C75E8;
    }
  }

  .btn-export {
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    &:hover {
      background: #F5F7FA;
      border-color: #7288FA;
      color: #7288FA;
    }
  }

  .btn-sync {
    width: 110px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #7288FA;

    &:hover {
      background: #EEF1FF;
      border-color: #5C75E8;
      color: #5C75E8;
    }
  }

  .btn-help {
    width: 84px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    &:hover {
      background: #F5F7FA;
      border-color: #7288FA;
      color: #7288FA;
    }
  }

  .btn-more {
    width: 80px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    &:hover {
      background: #F5F7FA;
      border-color: #7288FA;
      color: #7288FA;
    }

    .dots {
      margin-right: 5px;
      letter-spacing: 1px;
      color: rgba(0, 0, 0, 0.85);
    }
  }
}

.pagination {
  padding: 16px 24px;
  background: #FFFFFF;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.6);
  }

  :deep(.el-pagination .is-active) {
    width: 32px;
    height: 32px;
    background: #7288FA;
    border-radius: 3px;
    color: #fff;
  }

  :deep(.el-pagination .el-pager li:not(.is-active)) {
    width: 32px;
    height: 32px;
    background: #FFFFFF;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    color: #000000E5;
    margin-left: 8px;
  }

  :deep(.el-pagination .el-pager li + li) {
    margin-left: 8px;
  }
}
</style>
