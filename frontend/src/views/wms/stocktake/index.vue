<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useStocktakeStore } from '@/stores/modules/stocktake'
import { warehouseApi } from '@/api/modules/warehouse'
import { stocktakeApi } from '@/api/modules/stocktake'
import StatCard from '@/components/common/StatCard.vue'
import StocktakeTable from '@/components/business/stocktake/StocktakeTable.vue'
import StocktakeDetail from '@/components/business/stocktake/StocktakeDetail.vue'
import StocktakeApproveDialog from '@/components/business/stocktake/StocktakeApproveDialog.vue'
import type { Location, Warehouse } from '@/types/warehouse'
import type { StocktakeOrderListItem, StocktakeStatistics } from '@/types/stocktake'
import { STOCKTAKE_STATUS_OPTIONS } from '@/constants/stocktake'
import { STOCKTAKE_PERMISSIONS } from '@/constants/permission'

const router = useRouter()
const store = useStocktakeStore()

const warehouses = ref<Warehouse[]>([])
const locations = ref<Location[]>([])
const approveLoading = ref(false)
const stocktakeOverview = ref<StocktakeStatistics>({
  thisMonthCount: 0,
  pendingCount: 0,
  profitAmountTotal: 0,
  lossAmountTotal: 0,
})

const searchForm = ref({
  stocktakeNo: '',
  dateRange: [] as string[],
  warehouseId: undefined as number | undefined,
  locationId: undefined as number | undefined,
  status: '' as string,
  checkerName: '',
})

const syncSearchFormFromStore = () => {
  searchForm.value = {
    stocktakeNo: store.searchParams.stocktakeNo ?? '',
    dateRange: [store.searchParams.startDate ?? '', store.searchParams.endDate ?? ''].filter(Boolean),
    warehouseId: store.searchParams.warehouseId,
    locationId: store.searchParams.locationId,
    status: store.searchParams.status ?? '',
    checkerName: store.searchParams.checkerName ?? '',
  }
}

const loadWarehouses = async () => {
  try {
    const res = await warehouseApi.getList({ pageNum: 1, pageSize: 1000 })
    if (res.code === 'SUCCESS') {
      warehouses.value = res.data?.list || []
    }
  } catch (e) {
    console.error('加载仓库失败', e)
  }
}

const loadLocations = async (warehouseId?: number) => {
  locations.value = []
  if (!warehouseId) return
  try {
    const res = await warehouseApi.getLocations({ warehouseId, pageNum: 1, pageSize: 1000 })
    if (res.code === 'SUCCESS') {
      locations.value = res.data?.list || []
    }
  } catch (e) {
    console.error('加载仓位失败', e)
  }
}

const loadStocktakeOverview = async () => {
  try {
    const res = await stocktakeApi.getStatistics()
    if (res.code === 'SUCCESS' && res.data) {
      stocktakeOverview.value = res.data
    }
  } catch (e) {
    console.error('加载盘点概览失败', e)
  }
}

onMounted(async () => {
  syncSearchFormFromStore()
  await Promise.all([store.init(), loadWarehouses(), loadStocktakeOverview()])
  await loadLocations(searchForm.value.warehouseId)
})

const formatAmount = (value?: number) => `${Number(value || 0).toFixed(2)}元`

const handleWarehouseChange = async (value?: number) => {
  searchForm.value.locationId = undefined
  await loadLocations(value)
}

const handleSearch = () => store.search({
  stocktakeNo: searchForm.value.stocktakeNo || undefined,
  startDate: searchForm.value.dateRange?.[0] || undefined,
  endDate: searchForm.value.dateRange?.[1] || undefined,
  warehouseId: searchForm.value.warehouseId,
  locationId: searchForm.value.locationId,
  status: (searchForm.value.status || undefined) as any,
  checkerName: searchForm.value.checkerName || undefined,
})

const handleReset = async () => {
  searchForm.value = {
    stocktakeNo: '',
    dateRange: [],
    warehouseId: undefined,
    locationId: undefined,
    status: '',
    checkerName: '',
  }
  locations.value = []
  await store.resetSearch()
}

const handleAdd = () => router.push('/stocktake/create')
const handleEdit = (row: StocktakeOrderListItem) => router.push(`/stocktake/${row.id}/edit`)
const handleDetail = (row: StocktakeOrderListItem) => store.openDetail(row.id)
const handleSubmit = (row: StocktakeOrderListItem) => store.submitOrder(row.id)
const handleApprove = (row: StocktakeOrderListItem) => store.openApprove(row.id)

const handleVoid = async (row: StocktakeOrderListItem) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入作废原因', '作废盘点单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请输入作废原因',
      inputValidator: (val) => !!val.trim(),
    })
    await store.voidOrder(row.id, value)
  } catch {
  }
}

const handleApproveConfirm = async (approveRemark?: string) => {
  if (!store.currentId) return
  approveLoading.value = true
  const ok = await store.approveOrder(store.currentId, approveRemark)
  approveLoading.value = false
  if (ok) {
    store.closeApprove()
  }
}

const handleRejectConfirm = async (rejectRemark: string) => {
  if (!store.currentId) return
  approveLoading.value = true
  const ok = await store.rejectOrder(store.currentId, rejectRemark)
  approveLoading.value = false
  if (ok) {
    store.closeApprove()
  }
}
</script>

<template>
  <div class="stocktake-page">
    <div class="overview-cards">
      <StatCard title="本月盘点次数" :value="stocktakeOverview.thisMonthCount" />
      <StatCard title="待处理盘点单" :value="stocktakeOverview.pendingCount" color="warning" />
      <StatCard title="盘盈总金额" :value="formatAmount(stocktakeOverview.profitAmountTotal)" color="success" />
      <StatCard title="盘亏总金额" :value="formatAmount(stocktakeOverview.lossAmountTotal)" color="danger" />
    </div>

    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="4">
          <el-input v-model="searchForm.stocktakeNo" placeholder="盘点单号" clearable @keyup.enter="handleSearch" />
        </el-col>
        <el-col :span="8">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.warehouseId" placeholder="全部仓库" clearable style="width: 100%" @change="handleWarehouseChange">
            <el-option v-for="item in warehouses" :key="item.id" :label="item.warehouseName" :value="item.id" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.locationId" placeholder="全部仓位" clearable style="width: 100%">
            <el-option v-for="item in locations" :key="item.id" :label="item.locationName" :value="item.id" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 100%">
            <el-option v-for="item in STOCKTAKE_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-col>
      </el-row>
      <el-row :gutter="10" align="middle" class="mt-12">
        <el-col :span="4">
          <el-input v-model="searchForm.checkerName" placeholder="盘点人" clearable @keyup.enter="handleSearch" />
        </el-col>
        <el-col :span="20" style="text-align: right">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
      <div class="toolbar-actions">
        <el-button v-permission="STOCKTAKE_PERMISSIONS.EXPORT" @click="store.exportList">导出</el-button>
        <el-button type="primary" v-permission="STOCKTAKE_PERMISSIONS.CREATE" @click="handleAdd">+ 新增盘点单</el-button>
      </div>
    </div>

    <StocktakeTable
      :data="store.list"
      :loading="store.loading"
      @detail="handleDetail"
      @edit="handleEdit"
      @submit="handleSubmit"
      @approve="handleApprove"
      @void="handleVoid"
    />

    <div class="pagination">
      <span class="total">共 {{ store.total }} 条</span>
      <el-pagination
        v-model:current-page="store.pageNum"
        v-model:page-size="store.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="store.total"
        layout="sizes, prev, pager, next"
        @current-change="store.changePage"
        @size-change="store.changePageSize"
      />
    </div>

    <StocktakeDetail
      :model-value="store.detailVisible"
      :detail="store.currentDetail"
      :loading="store.detailLoading"
      @update:model-value="(value) => { if (!value) store.closeDetail() }"
    />

    <StocktakeApproveDialog
      :model-value="store.approveVisible"
      :loading="approveLoading"
      @update:model-value="(value) => { if (!value) store.closeApprove() }"
      @approve="handleApproveConfirm"
      @reject="handleRejectConfirm"
    />
  </div>
</template>

<style lang="scss" scoped>
.stocktake-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  flex-shrink: 0;
}

.toolbar,
.pagination {
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;
}

.toolbar {
  padding: 16px 20px;

  .toolbar-actions {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}

.pagination {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;

  .total {
    color: $text-regular;
    font-size: 14px;
  }
}

.mt-12 {
  margin-top: 12px;
}
</style>
