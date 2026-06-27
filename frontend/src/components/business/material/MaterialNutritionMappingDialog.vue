<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { isFoodLibraryApiUnavailable, materialApi } from '@/api/modules/material'
import type { FoodCategory, FoodImportResult, FoodItem, Material, MaterialFoodMapping } from '@/types/material'

const props = defineProps<{
  modelValue: boolean
  material: Material | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loading = ref(false)
const saving = ref(false)
const importLoading = ref(false)
const categories = ref<FoodCategory[]>([])
const foodItems = ref<FoodItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchFoodName = ref('')
const searchCategoryId = ref<number | undefined>()
const currentMapping = ref<MaterialFoodMapping | null>(null)
const selectedFood = ref<FoodItem | null>(null)
const remark = ref('')
const libraryUnavailable = ref(false)
const libraryUnavailableMessage = ref('')

const level2Categories = computed(() => categories.value.filter(item => item.categoryLevel === 2))
const foodLibraryReady = computed(() => categories.value.length > 0)
const currentSelectionText = computed(() => {
  if (!selectedFood.value) return '未选择标准食品项'
  return `${selectedFood.value.foodName} · ${selectedFood.value.foodCode}`
})
const materialKeyword = computed(() => {
  const raw = props.material?.materialName || ''
  return raw.replace(/（.*?）|\(.*?\)|\[.*?]|【.*?】/g, '').trim()
})

const nutritionReady = computed(() => {
  const material = props.material
  if (!material) return false
  return [material.calories, material.protein, material.carbohydrate, material.fat].some(value => value !== null && value !== undefined && Number(value) > 0)
})

const markLibraryUnavailable = (message = '当前联调环境未部署标准食品库接口，暂时无法加载标准食品列表或执行初始化。') => {
  libraryUnavailable.value = true
  libraryUnavailableMessage.value = message
  categories.value = []
  foodItems.value = []
  total.value = 0
  selectedFood.value = null
}

const resetLibraryState = () => {
  libraryUnavailable.value = false
  libraryUnavailableMessage.value = ''
}

const fetchCategories = async () => {
  try {
    const res = await materialApi.getFoodCategories({ silentError: true })
    if (res.code === 'SUCCESS' && res.data) {
      categories.value = res.data
    }
  } catch (error) {
    if (isFoodLibraryApiUnavailable(error)) {
      markLibraryUnavailable()
      return
    }
    throw error
  }
}

const fetchFoodItems = async () => {
  if (libraryUnavailable.value) return
  loading.value = true
  try {
    const res = await materialApi.getFoodItems({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      foodName: searchFoodName.value || undefined,
      categoryId: searchCategoryId.value
    }, { silentError: true })
    if (res.code === 'SUCCESS' && res.data) {
      foodItems.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    if (isFoodLibraryApiUnavailable(error)) {
      markLibraryUnavailable()
      return
    }
    throw error
  } finally {
    loading.value = false
  }
}

const fetchCurrentMapping = async () => {
  if (!props.material?.id) return
  const res = await materialApi.getMaterialFoodMappings({
    pageNum: 1,
    pageSize: 10,
    materialId: props.material.id
  })
  if (res.code === 'SUCCESS' && res.data?.list?.length) {
    currentMapping.value = res.data.list[0]
    remark.value = currentMapping.value.remark || ''
  } else {
    currentMapping.value = null
    remark.value = ''
  }
}

const openDialog = async () => {
  if (!props.material) return
  resetLibraryState()
  selectedFood.value = null
  pageNum.value = 1
  searchFoodName.value = materialKeyword.value
  try {
    await fetchCurrentMapping()
    await fetchCategories()
    if (!libraryUnavailable.value) {
      await fetchFoodItems()
    }
    if (currentMapping.value?.foodItemId && !libraryUnavailable.value) {
      const matched = foodItems.value.find(item => item.id === currentMapping.value?.foodItemId)
      if (matched) {
        selectedFood.value = matched
      } else {
        try {
          const detailRes = await materialApi.getFoodItemDetail(currentMapping.value.foodItemId, { silentError: true })
          if (detailRes.code === 'SUCCESS' && detailRes.data) {
            selectedFood.value = detailRes.data
          }
        } catch (error) {
          if (isFoodLibraryApiUnavailable(error)) {
            markLibraryUnavailable()
            return
          }
          throw error
        }
      }
    }
  } catch (error) {
    console.error('打开标准食品映射弹窗失败:', error)
  }
}

watch(() => props.modelValue, async (value) => {
  if (value) {
    await openDialog()
  }
})

const handleSearch = async () => {
  pageNum.value = 1
  await fetchFoodItems()
}

const handleInitFoodLibrary = async () => {
  importLoading.value = true
  try {
    const res = await materialApi.importFoodJson({ silentError: true })
    if (res.code === 'SUCCESS' && res.data) {
      const data = res.data as FoodImportResult
      ElMessage.success(`标准食品库初始化完成，共导入 ${data.itemCount} 条食品项`)
      resetLibraryState()
      await Promise.all([fetchCategories(), fetchFoodItems()])
    }
  } catch (error) {
    if (isFoodLibraryApiUnavailable(error)) {
      markLibraryUnavailable('当前联调环境未部署标准食品库接口，暂时无法执行标准食品库初始化。')
      ElMessage.warning(libraryUnavailableMessage.value)
      return
    }
    console.error('初始化标准食品库失败:', error)
  } finally {
    importLoading.value = false
  }
}

const handleSave = async () => {
  if (libraryUnavailable.value) {
    ElMessage.warning('当前环境未部署标准食品库接口，暂时无法保存标准食品映射')
    return
  }
  if (!props.material?.id || !selectedFood.value) {
    ElMessage.warning('请先选择一个标准食品项')
    return
  }
  saving.value = true
  try {
    const res = await materialApi.createMaterialFoodMapping({
      materialId: props.material.id,
      foodItemId: selectedFood.value.id,
      matchStatus: 'confirmed',
      remark: remark.value || undefined
    })
    if (res.code === 'SUCCESS') {
      ElMessage.success('标准食品映射已保存，并已同步营养数据')
      await fetchCurrentMapping()
      emit('success')
      visible.value = false
    }
  } finally {
    saving.value = false
  }
}

const handleResync = async () => {
  if (!props.material?.id) return
  saving.value = true
  try {
    const res = await materialApi.syncNutrition(props.material.id)
    if (res.code === 'SUCCESS') {
      ElMessage.success(`营养同步完成：${res.data?.foodName || props.material.materialName}`)
      emit('success')
      visible.value = false
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="1080px"
    destroy-on-close
    class="mapping-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <div>
          <h3>标准食品映射</h3>
          <p>{{ material?.materialName || '未选择物料' }} · {{ material?.materialCode || '-' }}</p>
        </div>
        <div class="header-status">
          <span class="status-chip" :class="nutritionReady ? 'ready' : 'missing'">
            {{ nutritionReady ? '营养已同步' : '营养待同步' }}
          </span>
        </div>
      </div>
    </template>

    <div class="mapping-layout">
      <section class="mapping-panel current">
        <h4>当前物料</h4>
        <div class="info-grid">
          <div><span>名称</span><strong>{{ material?.materialName || '-' }}</strong></div>
          <div><span>编码</span><strong>{{ material?.materialCode || '-' }}</strong></div>
          <div><span>类别</span><strong>{{ material?.categoryName || '-' }}</strong></div>
          <div><span>规格</span><strong>{{ material?.materialSpec || '-' }}</strong></div>
          <div><span>映射检索词</span><strong>{{ materialKeyword || '未提取' }}</strong></div>
          <div><span>营养来源</span><strong>{{ material?.nutritionSourceType || '未同步' }}</strong></div>
          <div><span>同步时间</span><strong>{{ material?.nutritionSyncedAt || '未同步' }}</strong></div>
        </div>

        <div class="mapping-current-card">
          <div class="card-title">当前标准食品</div>
          <div v-if="currentMapping" class="card-body">
            <strong>{{ currentMapping.foodName }}</strong>
            <span>{{ currentMapping.foodCode }}</span>
            <p>{{ currentMapping.remark || '已人工确认映射' }}</p>
          </div>
          <div v-else class="empty-state">
            尚未建立标准食品映射。菜谱营养将无法按真实口径计算。
          </div>
        </div>

        <div class="nutrition-preview">
          <div class="preview-title">当前营养快照</div>
          <div class="preview-grid">
            <span>热量 {{ material?.calories ?? '-' }}</span>
            <span>蛋白 {{ material?.protein ?? '-' }}</span>
            <span>碳水 {{ material?.carbohydrate ?? '-' }}</span>
            <span>脂肪 {{ material?.fat ?? '-' }}</span>
          </div>
        </div>
      </section>

      <section class="mapping-panel select">
        <div class="panel-head">
          <h4>选择标准食品</h4>
          <div class="search-row">
            <el-input v-model="searchFoodName" placeholder="搜索标准食品名称" clearable @keyup.enter="handleSearch" />
            <el-select v-model="searchCategoryId" placeholder="全部分类" clearable>
              <el-option
                v-for="item in level2Categories"
                :key="item.id"
                :label="item.categoryName"
                :value="item.id"
              />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
          </div>
        </div>

        <div v-if="libraryUnavailable" class="library-empty-state">
          <strong>当前环境未部署标准食品库接口</strong>
          <span>{{ libraryUnavailableMessage }}</span>
        </div>

        <div v-else-if="!foodLibraryReady" class="library-empty-state">
          <strong>标准食品库尚未初始化</strong>
          <span>请先导入 JSON 标准食品库，再为业务物料建立人工映射。</span>
          <el-button type="primary" :loading="importLoading" @click="handleInitFoodLibrary">
            初始化标准食品库
          </el-button>
        </div>

        <el-table
          v-else
          :data="foodItems"
          v-loading="loading"
          height="420"
          highlight-current-row
          @current-change="selectedFood = $event"
        >
          <el-table-column prop="foodName" label="标准食品" min-width="180" />
          <el-table-column prop="foodCode" label="编码" width="110" />
          <el-table-column prop="energyKcal" label="热量" width="90" />
          <el-table-column prop="protein" label="蛋白" width="90" />
          <el-table-column prop="carbohydrate" label="碳水" width="90" />
          <el-table-column prop="fat" label="脂肪" width="90" />
        </el-table>

        <div v-if="foodLibraryReady" class="selection-footer">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            layout="prev, pager, next"
            :total="total"
            @current-change="fetchFoodItems"
          />
          <el-input
            v-model="remark"
            maxlength="100"
            placeholder="映射备注，可选"
            class="remark-input"
          />
        </div>
        <div v-if="foodLibraryReady" class="selection-tip">
          当前选择：{{ currentSelectionText }}<span v-if="materialKeyword">，已按“{{ materialKeyword }}”自动检索候选</span>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
        <el-button :disabled="!currentMapping" :loading="saving" @click="handleResync">重新同步营养</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存映射并同步</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;

  h3 {
    margin: 0;
    font-size: 20px;
  }

  p {
    margin: 6px 0 0;
    color: #7a7f8c;
    font-size: 13px;
  }
}

.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;

  &.ready {
    background: #e9f7ef;
    color: #227447;
  }

  &.missing {
    background: #fff3e6;
    color: #b35c00;
  }
}

.mapping-layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
}

.mapping-panel {
  border: 1px solid #ebeef5;
  border-radius: 14px;
  padding: 16px;
  background: #fff;

  h4 {
    margin: 0 0 12px;
    font-size: 15px;
  }
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;

  div {
    background: #f8fafc;
    border-radius: 10px;
    padding: 10px 12px;
  }

  span {
    display: block;
    font-size: 12px;
    color: #8a8f99;
    margin-bottom: 4px;
  }

  strong {
    font-size: 13px;
    color: #1f2937;
    word-break: break-all;
  }
}

.mapping-current-card,
.nutrition-preview {
  border-radius: 12px;
  padding: 14px;
  background: #f8fafc;
  margin-bottom: 14px;
}

.card-title,
.preview-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 4px;

  strong {
    color: #1f2937;
  }

  span,
  p {
    margin: 0;
    font-size: 13px;
    color: #6b7280;
  }
}

.empty-state {
  font-size: 13px;
  color: #8a8f99;
  line-height: 1.6;
}

.preview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  font-size: 13px;
  color: #374151;
}

.panel-head {
  margin-bottom: 12px;
}

.search-row {
  display: grid;
  grid-template-columns: 1.2fr 180px 80px;
  gap: 10px;
  margin-top: 10px;
}

.selection-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  gap: 12px;
}

.remark-input {
  max-width: 260px;
}

.library-empty-state {
  height: 420px;
  border: 1px dashed #d5def5;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #4b5563;

  strong {
    color: #1f2937;
    font-size: 15px;
  }

  span {
    font-size: 13px;
  }
}

.selection-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #6b7280;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
