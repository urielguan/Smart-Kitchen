<script setup lang="ts">
import { ref, onMounted, onActivated, computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { useOutboundStore } from '@/stores/modules/outbound'
import { OUTBOUND_STATUS_OPTIONS } from '@/constants/outbound'
import OutboundTable from '@/components/business/outbound/OutboundTable.vue'
import OutboundForm from '@/components/business/outbound/OutboundForm.vue'
import OutboundDetail from '@/components/business/outbound/OutboundDetail.vue'
import OutboundImportDialog from '@/components/business/outbound/OutboundImportDialog.vue'
import type { OutboundOrder } from '@/types/outbound'
import { OUTBOUND_PERMISSIONS } from '@/constants/permission'

const store = useOutboundStore()
const route = useRoute()
const outboundActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'waste-rate') return '来自数据监管看板：食材浪费率联动出库明细'
  return '来自数据监管看板'
})

const searchForm = ref({
  outboundNo: '',
  outboundType: undefined as string | undefined,
  status: undefined as string | undefined,
  dateRange: [] as string[],
})

const syncSearchFormFromStore = () => {
  searchForm.value = {
    outboundNo: store.searchParams.outboundNo ?? '',
    outboundType: store.searchParams.outboundType,
    status: store.searchParams.status,
    dateRange: [store.searchParams.startDate ?? '', store.searchParams.endDate ?? ''].filter(Boolean),
  }
}

onMounted(async () => {
  syncSearchFormFromStore()
  await store.init()
  await applyDashboardRouteQuery()
})

onActivated(async () => {
  if (!outboundActivatedOnce.value) {
    outboundActivatedOnce.value = true
    return
  }
  if (await applyDashboardRouteQuery()) {
    return
  }
  await store.init()
})

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey
  searchForm.value = {
    outboundNo: typeof route.query.outboundNo === 'string' ? route.query.outboundNo : '',
    outboundType: typeof route.query.outboundType === 'string' ? route.query.outboundType : undefined,
    status: typeof route.query.status === 'string'
      ? route.query.status
      : route.query.metric === 'waste-rate'
        ? 'completed'
        : undefined,
    dateRange: (
      typeof route.query.startDate === 'string' && typeof route.query.endDate === 'string'
        ? [route.query.startDate, route.query.endDate]
        : []
    ) as string[],
  }

  await handleSearch()

  if (openDetailOnce && store.list[0]) {
    handleDetail(store.list[0])
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

const handleSearch = () => store.search({
  outboundNo: searchForm.value.outboundNo || undefined,
  outboundType: searchForm.value.outboundType,
  status: searchForm.value.status,
  startDate: searchForm.value.dateRange?.[0] || undefined,
  endDate: searchForm.value.dateRange?.[1] || undefined,
})

const handleReset = () => {
  searchForm.value = { outboundNo: '', outboundType: undefined, status: undefined, dateRange: [] }
  store.resetSearch()
}

const handleAdd = () => store.openForm(null)
const handleImport = () => store.openImportDialog()
const handleExportList = () => store.exportList()
const handleExportDetails = () => store.exportDetails()
const handleEdit = (row: OutboundOrder) => store.openForm(row.id)
const handleDetail = (row: OutboundOrder) => store.openDetail(row.id)
const handleDelete = (row: OutboundOrder) => store.deleteOrder(row.id)
const handleSubmit = (row: OutboundOrder) => store.submitOrder(row.id)
const handleWithdraw = (row: OutboundOrder) => store.withdrawOrder(row.id)
const handleApprove = (row: OutboundOrder) => store.approveOrder(row.id)

const handleReject = async (row: OutboundOrder) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回出库单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请输入驳回原因',
      inputValidator: (val) => !!val.trim(),
    })
    await store.rejectOrder(row.id, value)
  } catch {
  }
}

const handleExecute = (row: OutboundOrder) => store.executeOrder(row.id)
const handleReverse = (row: OutboundOrder) => store.reverseOrder(row.id)
const formatAmount = (value?: number) => `¥${Number(value || 0).toFixed(2)}`

const handleFormSuccess = () => {
  store.fetchList()
  store.fetchStatistics()
}

const handleStatusFilter = (status: string) => {
  searchForm.value.status = status
  handleSearch()
}
</script>

<template>
  <div class="outbound-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已按看板时间范围筛出真实出库单据，可直接核对已出库记录、单据状态和出库金额。"
    />

    <div class="stats-row">
      <div class="stat-card" @click="handleStatusFilter('')">
        <div class="stat-value">{{ store.statistics.totalCount }}</div>
        <div class="stat-label">全部</div>
      </div>
      <div class="stat-card draft" @click="handleStatusFilter('draft')">
        <div class="stat-value">{{ store.statistics.draftCount }}</div>
        <div class="stat-label">草稿</div>
      </div>
      <div class="stat-card pending" @click="handleStatusFilter('pending')">
        <div class="stat-value">{{ store.statistics.pendingCount }}</div>
        <div class="stat-label">待审核</div>
      </div>
      <div class="stat-card approved" @click="handleStatusFilter('approved')">
        <div class="stat-value">{{ store.statistics.approvedCount }}</div>
        <div class="stat-label">已审核</div>
      </div>
      <div class="stat-card completed" @click="handleStatusFilter('completed')">
        <div class="stat-value">{{ store.statistics.completedCount }}</div>
        <div class="stat-label">已出库</div>
      </div>
      <div class="stat-card month">
        <div class="stat-value">{{ formatAmount(store.statistics.thisMonthAmount) }}</div>
        <div class="stat-label">本月出库金额</div>
      </div>
    </div>

    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="5">
          <el-input v-model="searchForm.outboundNo" placeholder="出库单号" clearable @keyup.enter="handleSearch" />
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.outboundType" placeholder="全部类型" clearable>
            <el-option v-for="t in store.outboundTypeSelectOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
            <el-option v-for="s in OUTBOUND_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
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
        <el-button @click="handleImport">导入</el-button>
        <el-button @click="handleExportList">导出列表</el-button>
        <el-button @click="handleExportDetails">导出明细</el-button>
        <el-button type="primary" v-permission="OUTBOUND_PERMISSIONS.CREATE" @click="handleAdd">+ 新增出库单</el-button>
      </div>
    </div>

    <OutboundTable
      :data="store.list"
      :loading="store.loading"
      @detail="handleDetail"
      @edit="handleEdit"
      @delete="handleDelete"
      @submit="handleSubmit"
      @withdraw="handleWithdraw"
      @approve="handleApprove"
      @reject="handleReject"
      @execute="handleExecute"
      @reverse="handleReverse"
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

    <OutboundForm
      v-model="store.formVisible"
      :order-id="store.currentId"
      @success="handleFormSuccess"
    />

    <OutboundImportDialog
      v-model:visible="store.importDialogVisible"
      @update:visible="(value) => { if (!value) store.closeImportDialog() }"
    />

    <OutboundDetail
      v-model="store.detailVisible"
      :order-id="store.currentId"
      @refresh="handleFormSuccess"
    />
  </div>
</template>

<style lang="scss" scoped>
.outbound-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
  cursor: pointer;
  transition: transform 0.2s;

  &:hover { transform: translateY(-2px); }
  &.draft { border-top: 3px solid #909399; }
  &.pending { border-top: 3px solid #e6a23c; }
  &.approved { border-top: 3px solid #67c23a; }
  &.completed { border-top: 3px solid #409eff; }
  &.month { border-top: 3px solid #9b59b6; }

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

  .el-input,
  .el-select {
    width: 100%;
  }

  .toolbar-actions {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
    gap: 12px;
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

  .total {
    color: $text-regular;
    font-size: 14px;
  }
}
</style>
