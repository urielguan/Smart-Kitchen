<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import inventoryApi, {
  type InventoryDistributionItem,
  type InventoryMovementItem,
  type InventoryShelfLifeSummary
} from '@/api/modules/inventory'
import { INVENTORY_PERMISSIONS } from '@/constants/permission'
import { useInventoryStore } from '@/stores/modules/inventory'
import { formatDate, formatDateTime } from '@/utils'

const inventoryStore = useInventoryStore()
const route = useRoute()
const { list, total, pageNum, pageSize, loading, detailVisible, currentMaterialId } = storeToRefs(inventoryStore)
const inventoryActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')

const searchForm = reactive({
  keyword: '',
  categoryName: '',
  stockStatus: '',
  shelfLifeLevel: '',
  materialStatus: 'active'
})
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'inventory-low-stock') {
    return '来自数据监管看板：库存预警物料'
  }
  if (route.query.metric === 'inventory-expired') {
    return '来自数据监管看板：过期物料统计'
  }
  return '来自数据监管看板'
})

const activeTab = ref('distribution')
const detailLoading = ref(false)
const movementLoading = ref(false)
const distributionList = ref<InventoryDistributionItem[]>([])
const shelfLifeSummary = ref<InventoryShelfLifeSummary | null>(null)
const movementList = ref<InventoryMovementItem[]>([])
const movementTotal = ref(0)
const movementQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  startDate: '',
  endDate: '',
  bizType: '',
  documentNo: ''
})

const OVERVIEW_STOCK_STATUS_OPTIONS = ['normal', 'low', 'high', 'expired']
const OVERVIEW_SHELF_LIFE_LEVEL_OPTIONS = ['normal', 'warning', 'near_expiry', 'expired']
const OVERVIEW_MATERIAL_STATUS_OPTIONS = ['active', 'inactive']
const MOVEMENT_BIZ_TYPE_OPTIONS = ['入库单', '出库单', '盘点单']

const handleExportFailure = async (error: unknown, retry: () => Promise<void>) => {
  const message = error instanceof Error && error.message ? error.message : '导出失败，请稍后重试'
  try {
    await ElMessageBox.confirm(message, '导出失败', {
      type: 'error',
      confirmButtonText: '重试',
      cancelButtonText: '取消'
    })
    await retry()
  } catch {
    // User cancels retry or retry request fails and is handled by the next invocation.
  }
}

const handleSearch = async () => {
  if (!validateOverviewFilters()) return
  await inventoryStore.search({
    keyword: searchForm.keyword || undefined,
    categoryName: searchForm.categoryName || undefined,
    stockStatus: searchForm.stockStatus || undefined,
    shelfLifeLevel: searchForm.shelfLifeLevel || undefined,
    materialStatus: searchForm.materialStatus || 'active'
  })
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.categoryName = ''
  searchForm.stockStatus = ''
  searchForm.shelfLifeLevel = ''
  searchForm.materialStatus = 'active'
  inventoryStore.resetSearch()
}

const handleDetail = (row: { materialId: number }) => {
  inventoryStore.openDetail(row.materialId)
}

const handleExport = async () => {
  try {
    await inventoryStore.exportOverview()
  } catch (error) {
    await handleExportFailure(error, handleExport)
  }
}

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey

  searchForm.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  searchForm.categoryName = typeof route.query.categoryName === 'string' ? route.query.categoryName : ''
  searchForm.stockStatus = typeof route.query.stockStatus === 'string' ? route.query.stockStatus : ''
  searchForm.shelfLifeLevel = typeof route.query.shelfLifeLevel === 'string' ? route.query.shelfLifeLevel : ''
  searchForm.materialStatus = typeof route.query.materialStatus === 'string' ? route.query.materialStatus : 'active'

  await handleSearch()

  if (openDetailOnce && list.value[0]) {
    inventoryStore.openDetail(list.value[0].materialId)
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

const validateOverviewFilters = () => {
  if (searchForm.stockStatus && !OVERVIEW_STOCK_STATUS_OPTIONS.includes(searchForm.stockStatus)) {
    ElMessage.warning('库存状态筛选值无效')
    return false
  }
  if (searchForm.shelfLifeLevel && !OVERVIEW_SHELF_LIFE_LEVEL_OPTIONS.includes(searchForm.shelfLifeLevel)) {
    ElMessage.warning('保质期分层筛选值无效')
    return false
  }
  if (searchForm.materialStatus && !OVERVIEW_MATERIAL_STATUS_OPTIONS.includes(searchForm.materialStatus)) {
    ElMessage.warning('物料状态筛选值无效')
    return false
  }
  return true
}

const validateMovementFilters = () => {
  if (movementQuery.bizType && !MOVEMENT_BIZ_TYPE_OPTIONS.includes(movementQuery.bizType)) {
    ElMessage.warning('单据类型筛选值无效')
    return false
  }
  if (movementQuery.startDate && movementQuery.endDate && movementQuery.startDate > movementQuery.endDate) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return false
  }
  return true
}

const handleDetailLoadFailure = (error: unknown) => {
  console.error('获取库存详情失败:', error)
  const message = error instanceof Error && error.message ? error.message : '库存详情加载失败'
  if (message === '物料不可用') {
    ElMessage.warning(message)
  } else {
    ElMessage.error(message)
  }
  inventoryStore.closeDetail()
  distributionList.value = []
  shelfLifeSummary.value = null
  movementList.value = []
  movementTotal.value = 0
}

const fetchDetailBase = async (materialId: number) => {
  detailLoading.value = true
  try {
    const [distributionRes, summaryRes] = await Promise.all([
      inventoryApi.getDistribution(materialId),
      inventoryApi.getShelfLifeSummary(materialId)
    ])
    distributionList.value = distributionRes.data || []
    shelfLifeSummary.value = summaryRes.data || null
  } catch (error) {
    handleDetailLoadFailure(error)
  } finally {
    detailLoading.value = false
  }
}

const fetchMovements = async () => {
  if (!currentMaterialId.value) return
  movementLoading.value = true
  try {
    const res = await inventoryApi.getMovements(currentMaterialId.value, {
      pageNum: movementQuery.pageNum,
      pageSize: movementQuery.pageSize,
      startDate: movementQuery.startDate || undefined,
      endDate: movementQuery.endDate || undefined,
      bizType: movementQuery.bizType || undefined,
      documentNo: movementQuery.documentNo || undefined
    })
    movementList.value = res.data?.list || []
    movementTotal.value = res.data?.total || 0
  } catch (error) {
    console.error('获取出入库明细失败:', error)
    movementList.value = []
    movementTotal.value = 0
  } finally {
    movementLoading.value = false
  }
}

const handleMovementSearch = () => {
  if (!validateMovementFilters()) return
  movementQuery.pageNum = 1
  fetchMovements()
}

const handleMovementReset = () => {
  movementQuery.pageNum = 1
  movementQuery.pageSize = 10
  movementQuery.startDate = ''
  movementQuery.endDate = ''
  movementQuery.bizType = ''
  movementQuery.documentNo = ''
  fetchMovements()
}

const handleMovementExport = async () => {
  if (!currentMaterialId.value) return
  try {
    await inventoryApi.exportMovements(currentMaterialId.value, {
      pageNum: movementQuery.pageNum,
      pageSize: movementQuery.pageSize,
      startDate: movementQuery.startDate || undefined,
      endDate: movementQuery.endDate || undefined,
      bizType: movementQuery.bizType || undefined,
      documentNo: movementQuery.documentNo || undefined
    })
  } catch (error) {
    await handleExportFailure(error, handleMovementExport)
  }
}

const handleDrawerClose = () => {
  inventoryStore.closeDetail()
  activeTab.value = 'distribution'
  distributionList.value = []
  shelfLifeSummary.value = null
  movementList.value = []
  movementTotal.value = 0
  handleMovementReset()
}

const stockStatusLabel = (status?: string) => {
  switch (status) {
    case 'expired': return '已过期'
    case 'low': return '库存不足'
    case 'high': return '库存积压'
    default: return '正常'
  }
}

const shelfLifeCards = computed(() => {
  const summary = shelfLifeSummary.value
  return [
    { label: '正常', value: summary?.normalQty ?? 0 },
    { label: '预警', value: summary?.warningQty ?? 0 },
    { label: '临期', value: summary?.nearExpiryQty ?? 0 },
    { label: '已过期', value: summary?.expiredQty ?? 0 },
    { label: '合计', value: summary?.totalQty ?? 0 }
  ]
})

const isShelfLifeConfigMissing = computed(() => {
  return shelfLifeSummary.value?.configStatus === 'missing'
})

const hasActiveFilters = computed(() => {
  return Boolean(
    searchForm.keyword ||
    searchForm.categoryName ||
    searchForm.stockStatus ||
    searchForm.shelfLifeLevel ||
    searchForm.materialStatus !== 'active'
  )
})

const emptyStateTitle = computed(() => {
  return hasActiveFilters.value ? '无匹配数据' : '当前条件下暂无库存数据'
})

const emptyStateHint = computed(() => {
  return hasActiveFilters.value
    ? '没有找到符合当前筛选条件的库存数据，请调整筛选条件后重试'
    : '先调整筛选条件，或先完成入库、盘点过账后再回来查看'
})

watch(detailVisible, async (visible) => {
  if (visible && currentMaterialId.value) {
    activeTab.value = 'distribution'
    await fetchDetailBase(currentMaterialId.value)
  }
})

watch(activeTab, (tab) => {
  if (tab === 'movements' && currentMaterialId.value && movementList.value.length === 0) {
    fetchMovements()
  }
})

onMounted(async () => {
  await inventoryStore.fetchList()
  await applyDashboardRouteQuery()
})

onActivated(async () => {
  if (!inventoryActivatedOnce.value) {
    inventoryActivatedOnce.value = true
    return
  }
  if (await applyDashboardRouteQuery()) {
    return
  }
  await inventoryStore.fetchList()
})
</script>

<template>
  <div class="inventory-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已自动定位到库存风险物料，可直接查看分布、保质期分层与出入库流水，继续完成仓储核查。"
    />

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="物料名称/编码/规格" clearable />
        </el-form-item>
        <el-form-item label="物料类别">
          <el-input v-model="searchForm.categoryName" placeholder="请输入物料类别" clearable />
        </el-form-item>
        <el-form-item label="库存状态">
          <el-select v-model="searchForm.stockStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="正常" value="normal" />
            <el-option label="库存不足" value="low" />
            <el-option label="库存积压" value="high" />
            <el-option label="已过期" value="expired" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料状态">
          <el-select v-model="searchForm.materialStatus" placeholder="全部" style="width: 140px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="保质期分层">
          <el-select v-model="searchForm.shelfLifeLevel" placeholder="全部" clearable style="width: 140px">
            <el-option label="正常" value="normal" />
            <el-option label="预警" value="warning" />
            <el-option label="临期" value="near_expiry" />
            <el-option label="已过期" value="expired" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button v-permission="INVENTORY_PERMISSIONS.EXPORT" @click="handleExport">导出总览</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border empty-text="无匹配数据">
        <template #empty>
          <el-empty :description="emptyStateTitle">
            <template #description>
              <div class="table-empty-state">
                <div class="table-empty-title">{{ emptyStateTitle }}</div>
                <div class="table-empty-hint">{{ emptyStateHint }}</div>
                <el-button v-if="hasActiveFilters" type="primary" plain @click="handleReset">一键重置筛选</el-button>
              </div>
            </template>
          </el-empty>
        </template>
        <el-table-column prop="materialCode" label="物料编码" min-width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="categoryName" label="类别" min-width="120" />
        <el-table-column prop="materialSpec" label="规格" min-width="140" />
        <el-table-column prop="warehouseName" label="所在仓库" min-width="120" />
        <el-table-column prop="locationName" label="所在仓位" min-width="120" />
        <el-table-column prop="currentStock" label="当前库存" min-width="100" />
        <el-table-column prop="stockRange" label="库存范围" min-width="120" />
        <el-table-column prop="latestBatchNo" label="最新批次" min-width="120" />
        <el-table-column prop="minRemainingDays" label="最小剩余天数" min-width="120" />
        <el-table-column label="库存状态" min-width="120">
          <template #default="scope">
            {{ stockStatusLabel(scope.row.stockStatus) }}
          </template>
        </el-table-column>
        <el-table-column label="最后更新时间" min-width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.updatedAt) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="handleDetail(scope.row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="inventoryStore.changePage"
          @size-change="inventoryStore.changePageSize"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="库存详情" size="60%" @close="handleDrawerClose">
      <div v-loading="detailLoading" class="detail-content">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="库存分布" name="distribution">
            <el-table :data="distributionList" border>
              <el-table-column prop="warehouseName" label="仓库" min-width="140" />
              <el-table-column prop="locationName" label="仓位" min-width="140" />
              <el-table-column prop="batchNo" label="批次号" min-width="140" />
              <el-table-column label="生产日期" min-width="120">
                <template #default="scope">{{ formatDate(scope.row.productionDate) || '-' }}</template>
              </el-table-column>
              <el-table-column label="到期日期" min-width="120">
                <template #default="scope">{{ formatDate(scope.row.expiryDate) || '-' }}</template>
              </el-table-column>
              <el-table-column prop="remainingDays" label="剩余天数" min-width="100" />
              <el-table-column prop="quantity" label="数量" min-width="100" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="保质期分层" name="shelf-life">
            <div v-if="isShelfLifeConfigMissing" class="summary-empty-state">
              <div class="summary-empty-title">保质期分层配置缺失</div>
              <div class="summary-empty-hint">请先到物料档案补充临期提醒天数和预警天数</div>
            </div>
            <div v-else class="summary-grid">
              <div v-for="item in shelfLifeCards" :key="item.label" class="summary-card">
                <div class="summary-label">{{ item.label }}</div>
                <div class="summary-value">{{ item.value }}</div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="出入库明细" name="movements">
            <el-form :inline="true" :model="movementQuery" class="movement-form">
              <el-form-item label="单据类型">
                <el-select v-model="movementQuery.bizType" placeholder="全部" clearable style="width: 140px">
                  <el-option label="入库单" value="入库单" />
                  <el-option label="出库单" value="出库单" />
                  <el-option label="盘点单" value="盘点单" />
                </el-select>
              </el-form-item>
              <el-form-item label="单据编号">
                <el-input v-model="movementQuery.documentNo" placeholder="请输入单据编号" clearable />
              </el-form-item>
              <el-form-item label="开始日期">
                <el-date-picker v-model="movementQuery.startDate" value-format="YYYY-MM-DD" type="date" placeholder="开始日期" />
              </el-form-item>
              <el-form-item label="结束日期">
                <el-date-picker v-model="movementQuery.endDate" value-format="YYYY-MM-DD" type="date" placeholder="结束日期" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleMovementSearch">查询</el-button>
                <el-button @click="handleMovementReset">重置</el-button>
                <el-button v-permission="INVENTORY_PERMISSIONS.EXPORT" @click="handleMovementExport">导出明细</el-button>
              </el-form-item>
            </el-form>

            <el-table :data="movementList" v-loading="movementLoading" border>
              <el-table-column prop="bizType" label="单据类型" min-width="100" />
              <el-table-column prop="documentNo" label="单据编号" min-width="160" />
              <el-table-column prop="operationType" label="操作类型" min-width="100" />
              <el-table-column prop="materialName" label="物料名称" min-width="140" />
              <el-table-column prop="spec" label="规格" min-width="120" />
              <el-table-column prop="quantity" label="数量" min-width="100" />
              <el-table-column prop="unit" label="单位" min-width="80" />
              <el-table-column prop="postOperationStockQty" label="操作后库存数量" min-width="140" />
              <el-table-column prop="warehouseName" label="仓库" min-width="120" />
              <el-table-column prop="locationName" label="仓位" min-width="120" />
              <el-table-column prop="operatorName" label="操作人" min-width="100" />
              <el-table-column label="操作时间" min-width="180">
                <template #default="scope">{{ formatDateTime(scope.row.operationTime) || '-' }}</template>
              </el-table-column>
            </el-table>

            <div class="pagination-wrap">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
                :total="movementTotal"
                :current-page="movementQuery.pageNum"
                :page-size="movementQuery.pageSize"
                :page-sizes="[10, 20, 50]"
                @current-change="(page: number) => { movementQuery.pageNum = page; fetchMovements() }"
                @size-change="(size: number) => { movementQuery.pageSize = size; movementQuery.pageNum = 1; fetchMovements() }"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.inventory-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-entry-alert {
  margin-bottom: 0;
}

.filter-card,
.table-card {
  border-radius: 12px;
}

.table-empty-state {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.table-empty-title {
  color: $text-primary;
  font-size: 14px;
  font-weight: 600;
}

.table-empty-hint {
  color: $text-secondary;
  font-size: 13px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-content {
  min-height: 240px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}

.summary-empty-state {
  display: flex;
  min-height: 160px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 12px;
  background: #fafafa;
}

.summary-empty-title {
  color: $text-primary;
  font-size: 14px;
  font-weight: 600;
}

.summary-empty-hint {
  color: $text-secondary;
  font-size: 13px;
}

.summary-card {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fafafa;
}

.summary-label {
  color: $text-secondary;
  font-size: 13px;
  margin-bottom: 8px;
}

.summary-value {
  color: $text-primary;
  font-size: 24px;
  font-weight: 600;
}

.movement-form {
  margin-bottom: 16px;
}
</style>
