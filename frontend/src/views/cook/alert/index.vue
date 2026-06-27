<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { useAlertStore } from '@/stores/modules/alert'
import { useAlertRuleStore } from '@/stores/modules/alert-rule'
import { useUserStore } from '@/stores/modules/user'
import { ALERT_TYPES, ALERT_LEVELS, ALERT_STATUS } from '@/types/alert'
import {
  RULE_TYPES,
  RULE_ALERT_LEVELS,
} from '@/types/alert-rule'
import { ALERT_PERMISSIONS, ALERT_RULE_PERMISSIONS } from '@/constants/permission'
import { DISPATCH_TYPE_OPTIONS } from '@/constants/evaluation'
import { DEVICE_TYPES } from '@/constants/device'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import type { AlertReviewDTO, AlertCloseDTO } from '@/types/alert'
import { formatDateTime } from '@/utils'
import AlertDashboard from '@/components/business/alert/AlertDashboard.vue'
import AlertTable from '@/components/business/alert/AlertTable.vue'
import AlertRuleTable from '@/components/business/alert/AlertRuleTable.vue'
import AlertDispatchTable from '@/components/business/alert/AlertDispatchTable.vue'
import AlertRuleForm from '@/components/business/alert/AlertRuleForm.vue'
import AlertRuleDetail from '@/components/business/alert/AlertRuleDetail.vue'
import AlertDispatchForm from '@/components/business/alert/AlertDispatchForm.vue'
import AlertProcessForm from '@/components/business/alert/AlertProcessForm.vue'
import AlertReviewForm from '@/components/business/alert/AlertReviewForm.vue'
import AlertCloseForm from '@/components/business/alert/AlertCloseForm.vue'
import AlertDispatchDetail from '@/components/business/alert/AlertDispatchDetail.vue'

const alertStore = useAlertStore()
const ruleStore = useAlertRuleStore()
const userStore = useUserStore()
const dictCategoryStore = useDictCategoryStore()
const route = useRoute()
const hasPermission = (code: string) => userStore.hasPermission(code)
const alertActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const dashboardTargetDispatchId = ref<number | null>(null)

// 设备类型选项：从字典加载，缓存失效时自动重新获取（用于策略配置搜索栏）
const deviceTypeOptions = computed(() => {
  const dictOptions = dictCategoryStore.getCachedOptions('device_type')
  if (dictOptions.length > 0) {
    return dictOptions.map(item => ({ label: item.dictName, value: item.value }))
  }
  dictCategoryStore.fetchOptions('device_type')
  return DEVICE_TYPES.map(item => ({ label: item.label, value: item.value }))
})

// ========== Tab 控制 ==========
const TAB_MAP: Record<string, string> = {
  'work-order': 'dispatches',
  'dispatches': 'dispatches',
  'rules': 'rules',
  'alerts': 'alerts',
}
const activeTab = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'pending-alerts') return '来自数据监管看板：未处理告警'
  if (route.query.metric === 'overtime-dispatches') return '来自数据监管看板：超时未处理工单'
  if (route.query.metric === 'rectification-completed') return '来自数据监管看板：整改完成率'
  if (route.query.metric === 'review-passed') return '来自数据监管看板：复查通过率'
  return '来自数据监管看板'
})
watch(() => userStore.userInfo, () => {
  if (activeTab.value) return
  if (hasPermission(ALERT_PERMISSIONS.LIST)) activeTab.value = 'alerts'
  else if (hasPermission(ALERT_RULE_PERMISSIONS.ALERT_RULE)) activeTab.value = 'rules'
  else if (hasPermission(ALERT_PERMISSIONS.DISPATCH_TAB)) activeTab.value = 'dispatches'
}, { immediate: true })

// 监听 route query 变化，支持从其他页面跳转时切换 tab / 打开详情
watch(() => route.query, (query) => {
  if (!query) return
  const tab = query.tab as string
  if (tab && TAB_MAP[tab]) {
    activeTab.value = TAB_MAP[tab]
  }
  if (query.id && !tab) {
    activeTab.value = 'alerts'
  }
  if (query.id) {
    alertStore.openDetail(Number(query.id))
  }
}, { immediate: true })

// ========== Tab 数据加载 ==========
// 切换 tab 时保存/恢复滚动位置，防止页面跳回顶部
let savedScrollTop = 0
const getScrollContainer = () =>
  document.querySelector('.app-layout .content') as HTMLElement | null

watch(activeTab, (tab, oldTab) => {
  if (!tab) return
  // 切换前保存滚动位置（首次加载 oldTab 为空，不保存）
  if (oldTab) {
    const container = getScrollContainer()
    if (container) savedScrollTop = container.scrollTop
  }
  if (tab === 'alerts') {
    alertStore.init()
  } else if (tab === 'rules') {
    ruleStore.fetchList()
  } else if (tab === 'dispatches') {
    alertStore.fetchDispatchList()
  }
  // 切换后恢复滚动位置
  if (oldTab) {
    nextTick(() => {
      const container = getScrollContainer()
      if (container) container.scrollTop = savedScrollTop
    })
  }
}, { immediate: true })

// ========== 告警列表 Tab ==========
const dateRange = ref<[string, string] | null>(
  alertStore.searchParams.startTime && alertStore.searchParams.endTime
    ? [alertStore.searchParams.startTime.slice(0, 10), alertStore.searchParams.endTime.slice(0, 10)]
    : null
)

const searchForm = ref({
  alertType: alertStore.searchParams.alertType as string | undefined,
  alertLevel: alertStore.searchParams.alertLevel as string | undefined,
  status: alertStore.searchParams.status as string | undefined,
})

const handleSearch = () => {
  const params: Record<string, any> = {
    alertType: searchForm.value.alertType,
    alertLevel: searchForm.value.alertLevel,
    status: searchForm.value.status,
  }
  if (dateRange.value) {
    params.startTime = dateRange.value[0] + ' 00:00:00'
    params.endTime = dateRange.value[1] + ' 23:59:59'
  }
  alertStore.search(params)
}

const handleReset = () => {
  searchForm.value = { alertType: undefined, alertLevel: undefined, status: undefined }
  dateRange.value = null
  alertStore.resetSearch()
}

const exporting = ref(false)
const handleExport = async () => {
  exporting.value = true
  try {
    await alertStore.exportAlerts()
  } finally {
    exporting.value = false
  }
}

const handleDetail = (id: number) => alertStore.openDetail(id)

const closeFormVisible = ref(false)
const closingAlertId = ref<number | null>(null)

const handleClose = (id: number) => {
  closingAlertId.value = id
  closeFormVisible.value = true
}

const handleSubmitClose = async (data: AlertCloseDTO, done: () => void) => {
  if (!closingAlertId.value) { done(); return }
  try {
    await alertStore.closeAlert(closingAlertId.value, data)
    closeFormVisible.value = false
  } finally {
    done()
  }
}

const handleAutoDispatch = (id: number) => alertStore.autoDispatch(id)
const handleManualDispatch = (id: number) => alertStore.openDispatchForm(id)

// ========== 告警处理 Tab ==========
const dispatchDateRange = ref<[string, string] | null>(null)
const dispatchSearchForm = ref({
  status: undefined as string | undefined,
  dispatchType: undefined as string | undefined,
  handlerName: '',
})
const dashboardOverdueMode = computed(() => route.query.from === 'dashboard' && route.query.overdue === '1')
const filteredDispatchList = computed(() => {
  if (!dashboardOverdueMode.value) {
    return alertStore.dispatchList
  }

  const now = Date.now()
  return alertStore.dispatchList.filter((item) => {
    if (!item.deadline) return false
    if (!['pending', 'processing'].includes(item.status)) return false
    const deadline = new Date(item.deadline).getTime()
    return Number.isFinite(deadline) && deadline < now
  })
})
const filteredDispatchTotal = computed(() => {
  if (!dashboardOverdueMode.value) {
    return alertStore.dispatchTotal
  }
  return filteredDispatchList.value.length
})

const handleDispatchSearch = () => {
  const params: Record<string, any> = {
    status: dispatchSearchForm.value.status,
    dispatchType: dispatchSearchForm.value.dispatchType,
    handlerName: dispatchSearchForm.value.handlerName || undefined,
  }
  if (dispatchDateRange.value) {
    params.startTime = dispatchDateRange.value[0] + ' 00:00:00'
    params.endTime = dispatchDateRange.value[1] + ' 23:59:59'
  }
  alertStore.searchDispatches(params)
}

const handleDispatchReset = () => {
  dispatchSearchForm.value = { status: undefined, dispatchType: undefined, handlerName: '' }
  dispatchDateRange.value = null
  alertStore.resetDispatchSearch()
}

const handleProcess = (dispatchId: number) => alertStore.openProcessForm(dispatchId)
const handleDispatchDetail = (row: any) => alertStore.openDetail(row.alertId, { dispatchId: row.id })

const reviewFormVisible = ref(false)
const reviewDispatchId = ref<number | null>(null)

const handleReview = (dispatchId: number) => {
  reviewDispatchId.value = dispatchId
  reviewFormVisible.value = true
}

const handleSubmitReview = async (data: AlertReviewDTO, done: () => void) => {
  if (!reviewDispatchId.value) { done(); return }
  try {
    await alertStore.reviewDispatch(reviewDispatchId.value, data)
    reviewFormVisible.value = false
  } finally {
    done()
  }
}

const getDispatchStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    pending: 'warning',
    processing: 'primary',
    completed: 'success',
    reviewed: 'success',
    cancelled: 'info',
  }
  return map[status] || 'info'
}

const getPriorityTagType = (priority: string) => {
  const map: Record<string, string> = { high: 'danger', medium: 'warning', low: 'info' }
  return map[priority] || 'info'
}

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey
  const targetTab = route.query.tab === 'dispatches' ? 'dispatches' : 'alerts'
  activeTab.value = targetTab

  if (targetTab === 'alerts') {
    dashboardTargetDispatchId.value = null
    searchForm.value = {
      alertType: typeof route.query.alertType === 'string' ? route.query.alertType : undefined,
      alertLevel: typeof route.query.alertLevel === 'string' ? route.query.alertLevel : undefined,
      status: typeof route.query.status === 'string' ? route.query.status : undefined,
    }
    dateRange.value = null
    await alertStore.search({
      alertType: searchForm.value.alertType,
      alertLevel: searchForm.value.alertLevel,
      status: searchForm.value.status,
    })
    const targetAlertId = route.query.alertId ? Number(route.query.alertId) : null
    if (openDetailOnce && targetAlertId) {
      alertStore.openDetail(targetAlertId)
    } else if (openDetailOnce && alertStore.list[0]) {
      alertStore.openDetail(alertStore.list[0].id)
    }
  } else {
    dispatchSearchForm.value = {
      status: dashboardOverdueMode.value ? undefined : typeof route.query.status === 'string' ? route.query.status : undefined,
      dispatchType: typeof route.query.dispatchType === 'string' ? route.query.dispatchType : undefined,
      handlerName: typeof route.query.handlerName === 'string' ? route.query.handlerName : '',
    }
    dispatchDateRange.value = null
    await alertStore.searchDispatches({
      status: dispatchSearchForm.value.status,
      dispatchType: dispatchSearchForm.value.dispatchType,
      handlerName: dispatchSearchForm.value.handlerName || undefined,
    })

    const targetDispatchId = route.query.dispatchId ? Number(route.query.dispatchId) : null
    const targetDispatchNo = typeof route.query.dispatchNo === 'string' ? route.query.dispatchNo : ''
    const targetAlertId = route.query.alertId ? Number(route.query.alertId) : null
    const targetRow = targetDispatchId
      ? alertStore.dispatchList.find((item) => item.id === targetDispatchId) || filteredDispatchList.value.find((item) => item.id === targetDispatchId)
      : targetDispatchNo
        ? alertStore.dispatchList.find((item) => item.dispatchNo === targetDispatchNo) || filteredDispatchList.value.find((item) => item.dispatchNo === targetDispatchNo)
        : targetAlertId
          ? alertStore.dispatchList.find((item) => item.alertId === targetAlertId) || filteredDispatchList.value.find((item) => item.alertId === targetAlertId)
          : dashboardOverdueMode.value ? filteredDispatchList.value[0] : alertStore.dispatchList[0]
    dashboardTargetDispatchId.value = targetRow?.id || targetDispatchId || null
    if (openDetailOnce && targetRow) {
      handleDispatchDetail(targetRow)
    }
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

// ========== 策略配置 Tab ==========
const ruleSearchForm = ref({
  ruleName: ruleStore.searchParams.ruleName as string | undefined,
  ruleType: ruleStore.searchParams.ruleType as string | undefined,
  deviceType: ruleStore.searchParams.deviceType as string | undefined,
  alertLevel: ruleStore.searchParams.alertLevel as string | undefined,
  isEnabled: ruleStore.searchParams.isEnabled as number | undefined,
})

const handleRuleSearch = () => {
  ruleStore.search(ruleSearchForm.value)
}

const handleRuleReset = () => {
  ruleSearchForm.value = { ruleName: undefined, ruleType: undefined, deviceType: undefined, alertLevel: undefined, isEnabled: undefined }
  ruleStore.resetSearch()
}
</script>

<template>
  <div class="alert-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已按数据监管看板入口自动定位到具体告警或整改工单，可直接执行详情、派单、处理与复核动作。"
    />

    <!-- 统计看板 -->
    <AlertDashboard :dashboard="alertStore.dashboard" />

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" type="card" class="page-tabs">
      <!-- Tab 1: 告警列表 -->
      <el-tab-pane v-if="hasPermission(ALERT_PERMISSIONS.LIST)" label="告警列表" name="alerts">
        <!-- 搜索工具栏 -->
        <div class="toolbar">
          <el-row :gutter="10" align="middle">
            <el-col :span="3">
              <el-select v-model="searchForm.alertType" placeholder="全部类型" clearable>
                <el-option v-for="t in ALERT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-select v-model="searchForm.alertLevel" placeholder="全部级别" clearable>
                <el-option v-for="l in ALERT_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
                <el-option v-for="s in ALERT_STATUS.filter(s => s.value !== 'handling')" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-col>
            <el-col :span="5">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                :editable="false"
                style="width: 100%"
              />
            </el-col>
            <el-col :span="10" style="text-align: right">
              <el-button class="btn-search" @click="handleSearch">查询</el-button>
              <el-button class="btn-reset" @click="handleReset">重置</el-button>
            </el-col>
          </el-row>
        </div>

        <!-- 告警表格区 -->
        <div class="table-wrapper">
          <div class="table-header">
            <el-button class="btn-export" v-permission="ALERT_PERMISSIONS.EXPORT" :loading="exporting" @click="handleExport">导出</el-button>
          </div>
          <AlertTable
            :data="alertStore.list"
            :loading="alertStore.loading"
            :error="alertStore.listError"
            @retry="handleSearch"
            @detail="handleDetail"
            @close="handleClose"
            @auto-dispatch="handleAutoDispatch"
            @manual-dispatch="handleManualDispatch"
          />
          <!-- 分页 -->
          <div class="pagination">
            <span class="total">共 {{ alertStore.total }} 项数据</span>
            <el-pagination
              v-model:current-page="alertStore.pageNum"
              v-model:page-size="alertStore.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="alertStore.total"
              :pager-count="7"
              layout="sizes, prev, pager, next"
              @current-change="alertStore.changePage"
              @size-change="alertStore.changePageSize"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 策略配置 -->
      <el-tab-pane v-if="hasPermission(ALERT_RULE_PERMISSIONS.ALERT_RULE)" label="策略配置" name="rules">
        <!-- 策略搜索工具栏 -->
        <div class="toolbar">
          <el-row :gutter="10" align="middle">
            <el-col :span="4">
              <el-input v-model="ruleSearchForm.ruleName" placeholder="规则名称" clearable maxlength="100" />
            </el-col>
            <el-col :span="3">
              <el-select v-model="ruleSearchForm.ruleType" placeholder="全部类型" clearable>
                <el-option v-for="t in RULE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-select v-model="ruleSearchForm.deviceType" placeholder="全部设备" clearable>
                <el-option v-for="d in deviceTypeOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-select v-model="ruleSearchForm.alertLevel" placeholder="全部级别" clearable>
                <el-option v-for="l in RULE_ALERT_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-select v-model="ruleSearchForm.isEnabled" placeholder="全部状态" clearable>
                <el-option label="已启用" :value="1" />
                <el-option label="已禁用" :value="0" />
              </el-select>
            </el-col>
            <el-col :span="8" style="text-align: right">
              <el-button class="btn-search" @click="handleRuleSearch">查询</el-button>
              <el-button class="btn-reset" @click="handleRuleReset">重置</el-button>
            </el-col>
          </el-row>
        </div>

        <!-- 策略表格区 -->
        <div class="table-wrapper">
          <div class="table-header">
            <el-button class="btn-primary" v-if="hasPermission(ALERT_RULE_PERMISSIONS.CREATE)" @click="ruleStore.openCreateForm">+ 新增规则</el-button>
          </div>
          <AlertRuleTable
            :data="ruleStore.list"
            :loading="ruleStore.loading"
            @detail="ruleStore.openDetail"
            @edit="ruleStore.openEditForm"
            @delete="ruleStore.deleteRule"
            @toggle-enabled="ruleStore.toggleEnabled"
          />
          <!-- 策略分页 -->
          <div class="pagination">
            <span class="total">共 {{ ruleStore.total }} 项数据</span>
            <el-pagination
              v-model:current-page="ruleStore.pageNum"
              v-model:page-size="ruleStore.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="ruleStore.total"
              :pager-count="7"
              layout="sizes, prev, pager, next"
              @current-change="ruleStore.changePage"
              @size-change="ruleStore.changePageSize"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: 告警处理 -->
      <el-tab-pane v-if="hasPermission(ALERT_PERMISSIONS.DISPATCH_TAB)" label="告警处理" name="dispatches">
        <!-- 工单搜索工具栏 -->
        <div class="toolbar">
          <el-row :gutter="10" align="middle">
            <el-col :span="4">
              <el-input v-model="dispatchSearchForm.handlerName" placeholder="处理人姓名" clearable @keyup.enter="handleDispatchSearch" />
            </el-col>
            <el-col :span="3">
              <el-select v-model="dispatchSearchForm.dispatchType" placeholder="派单方式" clearable>
                <el-option v-for="t in DISPATCH_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-select v-model="dispatchSearchForm.status" placeholder="全部状态" clearable>
                <el-option label="待处理" value="pending" />
                <el-option label="已处理" value="completed" />
                <el-option label="已复核" value="reviewed" />
                <el-option label="已驳回" value="rejected" />
              </el-select>
            </el-col>
            <el-col :span="5">
              <el-date-picker
                v-model="dispatchDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                :editable="false"
                style="width: 100%"
              />
            </el-col>
            <el-col :span="9" style="text-align: right">
              <el-button class="btn-search" @click="handleDispatchSearch">查询</el-button>
              <el-button class="btn-reset" @click="handleDispatchReset">重置</el-button>
            </el-col>
          </el-row>
        </div>

        <!-- 工单表格区 -->
        <div class="table-wrapper">
          <AlertDispatchTable
            :data="alertStore.dispatchList"
            :loading="alertStore.dispatchLoading"
            @detail="handleDetail"
            @process="handleProcess"
            @review="handleReview"
          />
          <!-- 工单分页 -->
          <div class="pagination">
            <span class="total">共 {{ alertStore.dispatchTotal }} 项数据</span>
            <el-pagination
              v-model:current-page="alertStore.dispatchPageNum"
              v-model:page-size="alertStore.dispatchPageSize"
              :page-sizes="[10, 20, 50]"
              :total="alertStore.dispatchTotal"
              :pager-count="7"
              layout="sizes, prev, pager, next"
              @current-change="alertStore.changeDispatchPage"
              @size-change="alertStore.changeDispatchPageSize"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 告警详情弹窗（告警列表和告警处理共用） -->
    <AlertDispatchDetail
      v-model="alertStore.detailVisible"
      :alert-id="alertStore.alertDetailId"
      :dispatch-id="alertStore.alertDetailDispatchId"
    />

    <!-- 人工派单弹窗 -->
    <AlertDispatchForm
      v-model="alertStore.dispatchFormVisible"
      :handlers="alertStore.handlers"
      :alert-id="alertStore.dispatchAlertId"
      @submit="alertStore.submitDispatch"
    />

    <!-- 处理工单弹窗 -->
    <AlertProcessForm
      v-model="alertStore.processFormVisible"
      @submit="(data, done) => alertStore.submitProcess(data).finally(done)"
    />

    <!-- 复核工单弹窗 -->
    <AlertReviewForm
      v-model="reviewFormVisible"
      @submit="handleSubmitReview"
    />

    <!-- 关闭告警弹窗 -->
    <AlertCloseForm v-model="closeFormVisible" @submit="handleSubmitClose" />

    <!-- 策略配置表单弹窗 -->
    <AlertRuleForm v-model="ruleStore.formVisible" />

    <!-- 策略详情弹窗 -->
    <AlertRuleDetail v-model="ruleStore.detailVisible" />
  </div>
</template>

<style lang="scss">
/* ---- 表格基础样式（unscoped，作为各表格组件的统一样式基底） ---- */
.alert-page .el-table {
  --el-table-border-color: #E7E7E7;
  --el-table-row-height: 46px;

  .el-table__cell {
    padding-left: 12px;
    padding-right: 12px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #000000E5;
  }
}

.alert-page .el-table__body tr {
  height: 46px;
  border-bottom: 1px solid #E7E7E7;

  &:nth-child(odd) td {
    background-color: #FFFFFF;
  }

  &:nth-child(even) td {
    background-color: #F5F9FF;
  }
}

.alert-page .el-table__inner-wrapper::before {
  display: none;
}

.alert-page .el-table thead th {
  font-family: 'PingFang SC', sans-serif;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  color: #00000066;
  background-color: #F5F9FF !important;
  border-bottom: 1px solid #E7E7E7;
}

/* ---- Checkbox 颜色 ---- */
.alert-page .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.alert-page .el-checkbox__input.is-indeterminate .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

/* ---- Switch 颜色 ---- */
.alert-page .el-switch.is-checked .el-switch__core {
  background-color: #7288FA;
  border-color: #7288FA;
}

/* ---- 确认弹窗样式 ---- */
.alert-message-box {
  box-sizing: border-box;
  width: 384px;
  max-width: calc(100vw - 32px);
  min-height: 134px;
  padding: 16px;
  border: 0.5px solid #DCDCDC;
  border-radius: 12px;
  background: #FFFFFF;
  box-shadow: 0px 6px 30px 5px rgba(0, 0, 0, 0.05), 0px 16px 24px 2px rgba(0, 0, 0, 0.04), 0px 8px 10px -5px rgba(0, 0, 0, 0.08);

  .el-message-box__header { display: none; }
  .el-message-box__content { padding: 0; }
  .el-message-box__container { padding: 0; }
  .el-message-box__message { width: 100%; }

  .el-message-box__btns {
    display: flex;
    flex-direction: row;
    justify-content: flex-end;
    align-items: flex-start;
    gap: 8px;
    width: 100%;
    height: 32px;
    margin-top: 16px;
    padding: 0;
  }

  .el-message-box__btns .el-button {
    display: flex;
    flex-direction: row;
    justify-content: center;
    align-items: center;
    flex: none;
    margin-left: 0;
    height: 32px;
    padding: 5px 16px;
    border: none;
    border-radius: 3px;
    background: transparent;
    box-shadow: none;
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    line-height: 22px;
    font-weight: 400;
  }

  .el-message-box__btns .el-button--default,
  .el-message-box__btns .el-button--default:hover,
  .el-message-box__btns .el-button--default:focus {
    width: 60px;
    color: rgba(0, 0, 0, 0.9);
    border: none;
    background: transparent;
  }

  .el-message-box__btns .el-button--primary {
    width: 88px;
    color: #0052D9;
    border: none;
    background: transparent;
  }

  .el-message-box__btns .el-button--primary:hover,
  .el-message-box__btns .el-button--primary:focus {
    color: #4D88FF;
  }
}

.alert-confirm {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-end;
  gap: 16px;
}

.alert-confirm__content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}

.alert-confirm__icon {
  flex: none;
  width: 24px;
  height: 24px;
  color: #E37318;
}

.alert-confirm__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.alert-confirm__title {
  width: 100%;
  font-family: 'PingFang SC', sans-serif;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.9);
}

.alert-confirm__description {
  width: 100%;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.6);
}
</style>

<style lang="scss" scoped>
.alert-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;

  &::after {
    content: '';
    flex-shrink: 0;
    height: 16px;
  }
}

/* ---- 页面卡片式 Tab ---- */
.page-tabs {
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    margin: 0 !important;
    padding: 32px 16px 0 !important;
    background: #FFFFFF;
    border: none;
    border-radius: 8px 8px 0 0;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  }

  :deep(.el-tabs__nav-wrap) {
    margin-top: 0;
    margin-bottom: 0;
    overflow: visible !important;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 1px;
      background-color: #E1E2E9;
    }
  }

  :deep(.el-tabs__nav-scroll) {
    overflow: visible !important;
    padding: 0;
  }

  :deep(.el-tabs__nav) {
    border: none;
    overflow: visible !important;
    margin-top: 0;
  }

  :deep(.el-tabs__item) {
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    color: #606266;
    height: 36px;
    line-height: 36px;
    padding: 0 20px;
    margin-right: 4px;
    background: #FAFAFA;
    border: 1px solid #F0F0F0 !important;
    border-bottom: 1px solid #E1E2E9 !important;
    border-radius: 0;

    &:hover {
      color: #7288FA;
    }

    &.is-active {
      color: #7288FA;
      background: #FFFFFF;
      border-bottom-color: #FFFFFF !important;
    }
  }

  :deep(.el-tabs__content) {
    display: flex;
    flex-direction: column;
    padding: 0;
  }

  :deep(.el-tab-pane) {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 0;
  }
}

/* ---- 搜索栏 ---- */
.toolbar {
  margin: 0;
  background: #FFFFFF;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  padding: 24px 16px 16px 16px;
  flex-shrink: 0;

  .el-input,
  .el-select,
  .el-date-editor.el-input,
  .el-date-editor.el-input__wrapper {
    width: 100%;
  }
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

.btn-export {
  width: 58px;
  height: 32px;
  padding: 5px 16px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  color: #606266;

  &:hover {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

/* ---- 表格区 ---- */
.table-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  height: 500px;
  overflow: hidden;
}

.table-header {
  padding: 16px 20px 12px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;

  :deep(.el-button) {
    margin-left: 0;
  }
}

.btn-primary {
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

/* ---- 分页 ---- */
.pagination {
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    color: rgba(0, 0, 0, 0.6);
  }

  :deep(.el-pagination .el-pager) {
    gap: 4px;
  }

  :deep(.el-pagination .is-active) {
    width: 32px; height: 32px;
    background: #7288FA; border-radius: 3px; color: #fff;
  }

  :deep(.el-pagination .el-pager li:not(.is-active)) {
    width: 32px; height: 32px;
    border: 1px solid #DCDCDC; border-radius: 3px;
    color: rgba(0, 0, 0, 0.6);
  }
}
</style>
