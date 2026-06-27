<script setup lang="ts">
import { computed, ref, onActivated, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useInboundStore } from '@/stores/modules/inbound'
import { INBOUND_STATUS_OPTIONS, INBOUND_SOURCE_TYPES } from '@/constants/inbound'
import InboundTable  from '@/components/business/inbound/InboundTable.vue'
import InboundForm   from '@/components/business/inbound/InboundForm.vue'
import InboundDetail from '@/components/business/inbound/InboundDetail.vue'
import InboundImportDialog from '@/components/business/inbound/InboundImportDialog.vue'
import type { InboundOrder } from '@/types/inbound'
import { INBOUND_PERMISSIONS } from '@/constants/permission'

const store = useInboundStore()
const route = useRoute()

type StatCardFilter = 'all' | 'pending' | 'approved' | 'completed'

const searchForm = ref({
  inboundNo:   '',
  sourceType:  undefined as string | undefined,
  status:      undefined as string | undefined,
  dateRange:   [] as string[],
})
const inboundActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'inbound-ledger') {
    return '来自数据监管看板：出入库台账完整性预警'
  }
  return '来自数据监管看板'
})

const formatAmount = (value?: number) => `¥${Number(value || 0).toFixed(2)}`

const activeStatFilter = computed<StatCardFilter | null>(() => {
  if (searchForm.value.status === 'pending') return 'pending'
  if (searchForm.value.status === 'approved') return 'approved'
  if (searchForm.value.status === 'completed') return 'completed'
  if (!searchForm.value.status) return 'all'
  return null
})

const syncSearchFormFromStore = () => {
  searchForm.value = {
    inboundNo: store.searchParams.inboundNo ?? '',
    sourceType: store.searchParams.sourceType,
    status: store.searchParams.status,
    dateRange: [store.searchParams.startDate ?? '', store.searchParams.endDate ?? ''].filter(Boolean),
  }
}

onMounted(() => {
  syncSearchFormFromStore()
  void store.init().then(() => applyDashboardRouteQuery())
})

onActivated(async () => {
  if (!inboundActivatedOnce.value) {
    inboundActivatedOnce.value = true
    return
  }
  if (await applyDashboardRouteQuery()) {
    return
  }
  syncSearchFormFromStore()
  await Promise.all([store.fetchList(), store.fetchStatistics()])
})

const handleSearch = () => store.search({
  inboundNo:  searchForm.value.inboundNo  || undefined,
  sourceType: searchForm.value.sourceType,
  status:     searchForm.value.status,
  startDate:  searchForm.value.dateRange?.[0] || undefined,
  endDate:    searchForm.value.dateRange?.[1] || undefined,
})

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey

  searchForm.value = {
    inboundNo: typeof route.query.inboundNo === 'string' ? route.query.inboundNo : '',
    sourceType: typeof route.query.sourceType === 'string' ? route.query.sourceType : undefined,
    status: typeof route.query.status === 'string' ? route.query.status : undefined,
    dateRange: []
  }

  await handleSearch()

  if (openDetailOnce && store.list[0]) {
    store.openDetail(store.list[0].id)
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

const handleReset = () => {
  searchForm.value = { inboundNo: '', sourceType: undefined, status: undefined, dateRange: [] }
  store.resetSearch()
}

const handleStatCardClick = async (card: StatCardFilter) => {
  const nextStatus = activeStatFilter.value === card ? undefined : (card === 'all' ? undefined : card)
  searchForm.value.status = nextStatus
  await store.applyStatusFilter(nextStatus)
}

const isStatCardActive = (card: StatCardFilter) => activeStatFilter.value === card

const handleAdd    = () => store.openForm(null)
const handleImport = () => store.openImportDialog()
const handleEdit   = (row: InboundOrder) => store.openForm(row.id)
const handleDetail = (row: InboundOrder) => store.openDetail(row.id)
const handleDelete = (row: InboundOrder) => store.deleteOrder(row.id)
const handleSubmit = (row: InboundOrder) => store.submitOrder(row.id)
const handleCancel = (row: InboundOrder) => store.cancelOrder(row.id)

const handleFormSuccess = () => {
  store.fetchList()
  store.fetchStatistics()
}
</script>

<template>
  <div class="inbound-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已切换到仓储入库台账视图，可直接核查采购入库单据、补齐台账信息或继续追溯缺失记录。"
    />

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div
        class="stat-card stat-card--clickable"
        :class="[{ 'is-active': isStatCardActive('all') }]"
        @click="handleStatCardClick('all')"
      >
        <div class="stat-value">{{ store.statistics.thisMonthTotalCount }}</div>
        <div class="stat-label">本月入库总单数</div>
      </div>
      <div
        class="stat-card pending stat-card--clickable"
        :class="[{ 'is-active': isStatCardActive('pending') }]"
        @click="handleStatCardClick('pending')"
      >
        <div class="stat-value">{{ store.statistics.thisMonthPendingCount }}</div>
        <div class="stat-label">本月待审批单数</div>
      </div>
      <div
        class="stat-card approved stat-card--clickable"
        :class="[{ 'is-active': isStatCardActive('approved') }]"
        @click="handleStatCardClick('approved')"
      >
        <div class="stat-value">{{ store.statistics.thisMonthApprovedCount }}</div>
        <div class="stat-label">本月已审核单数</div>
      </div>
      <div
        class="stat-card completed stat-card--clickable"
        :class="[{ 'is-active': isStatCardActive('completed') }]"
        @click="handleStatCardClick('completed')"
      >
        <div class="stat-value">{{ formatAmount(store.statistics.thisMonthInboundAmount) }}</div>
        <div class="stat-label">本月入库金额</div>
      </div>
    </div>

    <!-- 搜索工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="5">
          <el-input v-model="searchForm.inboundNo" placeholder="入库单号" clearable @keyup.enter="handleSearch" />
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.sourceType" placeholder="全部来源" clearable>
            <el-option v-for="t in INBOUND_SOURCE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
            <el-option v-for="s in INBOUND_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-col>
        <el-col :span="8">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
        </el-col>
        <el-col :span="3" style="text-align:right">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
      <div class="toolbar-actions">
        <el-button class="btn-import" @click="handleImport">导入</el-button>
        <el-button type="primary" v-permission="INBOUND_PERMISSIONS.CREATE" @click="handleAdd">+ 新增入库单</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <InboundTable
      :data="store.list"
      :loading="store.loading"
      @detail="handleDetail"
      @edit="handleEdit"
      @delete="handleDelete"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />

    <!-- 分页 -->
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

    <!-- 新增/编辑弹窗 -->
    <InboundForm
      v-model="store.formVisible"
      :order-id="store.currentId"
      @success="handleFormSuccess"
    />

    <!-- 详情弹窗 -->
    <InboundDetail
      v-model="store.detailVisible"
      :order-id="store.currentId"
      @refresh="handleFormSuccess"
    />

    <InboundImportDialog
      :visible="store.importDialogVisible"
      @update:visible="(visible) => { if (!visible) store.closeImportDialog() }"
    />
  </div>
</template>

<style lang="scss" scoped>
.inbound-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dashboard-entry-alert {
  margin-bottom: 16px;
}
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.stat-card {
  flex: 1;
  background: $bg-white;
  border-radius: $border-radius-large;
  padding: 20px;
  box-shadow: $box-shadow-base;
  text-align: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  border: 1px solid transparent;
  &.pending  { border-top: 3px solid #e6a23c; }
  &.approved { border-top: 3px solid #67c23a; }
  &.completed { border-top: 3px solid #409eff; }
  &.month    { border-top: 3px solid #409eff; }
  &.stat-card--clickable {
    cursor: pointer;
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 10px 24px rgba(64, 158, 255, 0.12);
    }
  }
  &.is-active {
    border-color: #409eff;
    box-shadow: 0 10px 24px rgba(64, 158, 255, 0.18);
  }
  .stat-value {
    font-size: 28px;
    font-weight: 700;
    color: $text-primary;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 13px;
    color: $text-regular;
    margin-top: 6px;
  }
}
.toolbar {
  background: $bg-white;
  padding: 16px 20px;
  border-radius: $border-radius-large;
  margin-bottom: 16px;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;
  .el-input, .el-select { width: 100%; }
  .toolbar-actions {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
  }
}
.pagination {
  background: $bg-white;
  padding: 16px 20px;
  border-radius: $border-radius-large;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  .total { color: $text-regular; font-size: 14px; }
}
</style>
