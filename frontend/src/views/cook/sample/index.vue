<script setup lang="ts">
import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { useSampleStore } from '@/stores/modules/sample'
import { useUserStore } from '@/stores/modules/user'
import { debounce } from '@/utils'
import type { SampleRecord, SampleRecordQuery, SampleRecordCreatePayload, SampleRecordRegisterPayload, SampleRecordUpdatePayload, DisposalPayload, DisposalReminderRecord, SampleHistoryRecordCreatePayload, ManualDisposalSupplementPayload } from '@/types'
import { SAMPLE_STATUS_OPTIONS, MEAL_TYPE_OPTIONS } from '@/constants/sample'
import SampleStatistics from '@/components/business/sample/SampleStatistics.vue'
import SampleRecordTable from '@/components/business/sample/SampleRecordTable.vue'
import SampleRecordDetail from '@/components/business/sample/SampleRecordDetail.vue'
import SampleRecordForm from '@/components/business/sample/SampleRecordForm.vue'
import SampleHistoryRecordForm from '@/components/business/sample/SampleHistoryRecordForm.vue'
import SampleManualDisposalForm from '@/components/business/sample/SampleManualDisposalForm.vue'
import SampleRecordRegisterForm from '@/components/business/sample/SampleRecordRegisterForm.vue'
import SampleDisposalForm from '@/components/business/sample/SampleDisposalForm.vue'
import SampleRecordEditForm from '@/components/business/sample/SampleRecordEditForm.vue'
import SampleDisposalReminders from '@/components/business/sample/SampleDisposalReminders.vue'
import { SAMPLE_PERMISSIONS } from '@/constants/permission'

const sampleStore = useSampleStore()
const route = useRoute()
const userStore = useUserStore()
const sampleTableRef = ref<InstanceType<typeof SampleRecordTable> | null>(null)
const sampleActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const isAdmin = computed(() => userStore.isAdmin())
const isRollbackIsolatedView = computed(() => Boolean(searchForm.value.showRollbackIsolated))
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'sample-compliance') {
    return '来自数据监管看板：留样合规率预警'
  }
  return '来自数据监管看板'
})
const getTodayDate = () => new Date().toISOString().slice(0, 10)
const hasHistorySupplementRole = (roles?: string[]) => {
  if (!roles?.length) return false
  return roles.some(role => role.includes('系统管理员') || role.includes('超级管理员') || role.includes('运维'))
}
const hasManualDisposalSupplementRole = (roles?: string[]) => {
  if (!roles?.length) return false
  return roles.some(role =>
    role.includes('系统管理员')
    || role.includes('超级管理员')
    || role.includes('运维')
    || role.includes('食堂超级管理员')
  )
}
const canUseHistorySupplement = computed(() => {
  if (!userStore.userInfo) return false
  return userStore.isAdmin()
    || userStore.hasPermission(SAMPLE_PERMISSIONS.HISTORY_SUPPLEMENT)
    || hasHistorySupplementRole(userStore.userInfo.roles)
})
const canUseManualDisposalSupplement = computed(() => {
  if (!userStore.userInfo) return false
  return userStore.isAdmin()
    || userStore.hasPermission(SAMPLE_PERMISSIONS.MANUAL_DISPOSAL_SUPPLEMENT)
    || hasManualDisposalSupplementRole(userStore.userInfo.roles)
})
const canExportSampleLedger = computed(() => {
  if (!userStore.userInfo) return false
  return userStore.isAdmin()
    || userStore.hasPermission(SAMPLE_PERMISSIONS.VIEW)
    || userStore.hasPermission(SAMPLE_PERMISSIONS.EXPORT)
})
const buildEffectiveSearchParams = (): SampleRecordQuery => {
  if (dateRange.value) {
    const [startDate, endDate] = dateRange.value
    searchForm.value.sampleDate = startDate
    searchForm.value.sampleDateEnd = endDate
  } else {
    searchForm.value.sampleDate = ''
    searchForm.value.sampleDateEnd = ''
  }
  return { ...searchForm.value }
}

const searchForm = ref<SampleRecordQuery>({
  status: sampleStore.searchParams.status || '',
  sampleDate: sampleStore.searchParams.sampleDate || '',
  sampleDateEnd: sampleStore.searchParams.sampleDateEnd || '',
  mealType: sampleStore.searchParams.mealType || '',
  menuName: sampleStore.searchParams.menuName || '',
  showRollbackIsolated: sampleStore.searchParams.showRollbackIsolated || false
})

const activeTab = ref('list')
const pollingTimer = ref<ReturnType<typeof setInterval> | null>(null)
const dateRange = ref<[string, string] | null>(
  sampleStore.searchParams.sampleDate && sampleStore.searchParams.sampleDateEnd
    ? [sampleStore.searchParams.sampleDate, sampleStore.searchParams.sampleDateEnd]
    : null
)

const handleDateRangeChange = (val: [string, string] | null) => {
  if (val) {
    searchForm.value.sampleDate = val[0]
    searchForm.value.sampleDateEnd = val[1]
  } else {
    searchForm.value.sampleDate = ''
    searchForm.value.sampleDateEnd = ''
  }
}

onMounted(async () => {
  if (sampleStore.list.length > 0) {
    await sampleStore.fetchDashboard()
  } else {
    await sampleStore.init()
  }
  await applyDashboardRouteQuery()
  pollingTimer.value = setInterval(() => {
    void sampleStore.refresh()
    void sampleStore.fetchReminders()
  }, 60000)
})

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey
  activeTab.value = route.query.tab === 'reminders' ? 'reminders' : 'list'

  if (activeTab.value === 'reminders') {
    await sampleStore.fetchReminders()
    if (openDetailOnce && sampleStore.reminderList[0]) {
      await sampleStore.openDetail(sampleStore.reminderList[0].id)
    }
    lastDashboardRouteKey.value = routeKey
    return true
  }

  searchForm.value = {
    status: typeof route.query.status === 'string' ? route.query.status : '',
    sampleDate: typeof route.query.sampleDate === 'string' ? route.query.sampleDate : '',
    sampleDateEnd: typeof route.query.sampleDateEnd === 'string' ? route.query.sampleDateEnd : '',
    mealType: typeof route.query.mealType === 'string' ? route.query.mealType : '',
    menuName: typeof route.query.menuName === 'string' ? route.query.menuName : '',
    showRollbackIsolated: route.query.showRollbackIsolated === '1'
  }

  dateRange.value = searchForm.value.sampleDate && searchForm.value.sampleDateEnd
    ? [searchForm.value.sampleDate, searchForm.value.sampleDateEnd]
    : null

  await sampleStore.search(buildEffectiveSearchParams())

  if (typeof route.query.recordId === 'string') {
    await sampleStore.openDetail(Number(route.query.recordId))
  } else if (openDetailOnce && sampleStore.list[0]) {
    await sampleStore.openDetail(sampleStore.list[0].id)
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

onActivated(async () => {
  if (!sampleActivatedOnce.value) {
    sampleActivatedOnce.value = true
  } else if (!await applyDashboardRouteQuery()) {
    await Promise.all([
      sampleStore.refresh(),
      sampleStore.fetchReminders()
    ])
  }
})

onUnmounted(() => {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
})

const handleSearch = async () => {
  await sampleStore.search(buildEffectiveSearchParams())
}

const debouncedSearch = debounce(handleSearch, 300)

/** 菜谱名称输入提示 */
const showMenuTip = ref(false)
let menuTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerMenuTip = () => {
  if (menuTipTimer) clearTimeout(menuTipTimer)
  showMenuTip.value = false
  requestAnimationFrame(() => { showMenuTip.value = true })
  menuTipTimer = setTimeout(() => { showMenuTip.value = false }, 2000)
}
const handleMenuInput = () => {
  if (searchForm.value.menuName.length >= 30) triggerMenuTip()
  else showMenuTip.value = false
}
const handleMenuKeydown = (e: KeyboardEvent) => {
  if (searchForm.value.menuName.length >= 30 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerMenuTip()
  }
}

const handleReset = async () => {
  searchForm.value = {
    status: '',
    sampleDate: '',
    sampleDateEnd: '',
    mealType: '',
    menuName: '',
    showRollbackIsolated: false
  }
  dateRange.value = null
  await sampleStore.search({ ...searchForm.value })
}

const handleDetail = async (record: SampleRecord) => {
  await sampleStore.openDetail(record.id)
}

const handleDisposeFromTable = async (record: SampleRecord) => {
  await sampleStore.openDisposalForm(record.id)
}

const handleManualSupplementFromTable = async (record: SampleRecord) => {
  await sampleStore.openManualDisposalForm(record.id)
}

const handleRegisterFromTable = async (record: SampleRecord) => {
  await sampleStore.openRegisterForm(record.id)
}

const handleAiEvaluateFromTable = async (record: SampleRecord) => {
  await sampleStore.triggerAiEvaluate(record.id)
}

const handleVoidFromTable = async (record: SampleRecord) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入作废原因', '作废确认', {
      confirmButtonText: '确认作废',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请说明作废原因',
      inputValidator: (val: string) => val?.trim() ? true : '作废原因不能为空'
    })
    await sampleStore.voidRecord(record.id, value.trim())
  } catch {
    // 用户取消
  }
}

const handleDisposeFromReminder = async (record: DisposalReminderRecord) => {
  await sampleStore.openDisposalForm(record.id)
}

const handleDetailFromReminder = async (record: DisposalReminderRecord) => {
  await sampleStore.openDetail(record.id)
}

const handleDisposalSubmit = async (payload: DisposalPayload) => {
  await sampleStore.submitDisposal(payload)
}

const handleManualDisposalSubmit = async (payload: ManualDisposalSupplementPayload) => {
  await sampleStore.submitManualDisposal(payload)
}

const handleDetailDispose = () => {
  if (sampleStore.currentRecord) {
    void sampleStore.openDisposalForm(sampleStore.currentRecord.id)
  }
}

const handleDetailManualSupplement = async () => {
  if (sampleStore.currentRecord) {
    await sampleStore.openManualDisposalForm(sampleStore.currentRecord.id)
  }
}

const handleDetailRegister = async () => {
  if (sampleStore.currentRecord) {
    await sampleStore.openRegisterForm(sampleStore.currentRecord.id)
  }
}

const handleDetailAiEvaluate = async () => {
  if (sampleStore.currentRecord) {
    await sampleStore.triggerAiEvaluate(sampleStore.currentRecord.id)
  }
}

const handleEdit = async (record: SampleRecord) => {
  await sampleStore.fetchRecordForEdit(record.id)
}

const handleDetailEdit = () => {
  if (sampleStore.currentRecord) {
    void sampleStore.fetchRecordForEdit(sampleStore.currentRecord.id)
  }
}

const handleEditSubmit = async (payload: SampleRecordUpdatePayload) => {
  await sampleStore.submitEdit(payload)
}

const handleReminderPageChange = (page: number) => {
  sampleStore.changeReminderPage(page)
}

const handleReminderSizeChange = (size: number) => {
  sampleStore.changeReminderPageSize(size)
}

const handlePageChange = (page: number) => {
  sampleStore.changePage(page)
}

const handleSizeChange = (size: number) => {
  sampleStore.changePageSize(size)
}

const handleSubmit = async (payload: SampleRecordCreatePayload) => {
  await sampleStore.submitRecord(payload)
}

const handleHistorySubmit = async (payload: SampleHistoryRecordCreatePayload) => {
  await sampleStore.submitHistoryRecord(payload)
}

const handleRegisterSubmit = async (payload: SampleRecordRegisterPayload) => {
  await sampleStore.submitRegister(payload)
}

const handleArchive = async (record: SampleRecord) => {
  try {
    await ElMessageBox.confirm('确认归档该留样记录？归档后记录将不可再修改。', '归档确认', {
      confirmButtonText: '确认归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await sampleStore.archiveRecord(record.id)
  } catch {
    // 用户取消
  }
}

const handleDetailArchive = () => {
  if (sampleStore.currentRecord) {
    handleArchive(sampleStore.currentRecord)
  }
}

const handleExport = async () => {
  await sampleStore.exportRecords(buildEffectiveSearchParams())
}

const handleFilterByStatus = async (status: string) => {
  if (status === 'today_sampled') {
    const today = new Date().toISOString().slice(0, 10)
    searchForm.value.status = ''
    searchForm.value.sampleDate = today
    searchForm.value.sampleDateEnd = today
    dateRange.value = [today, today]
  } else {
    searchForm.value.status = status
    searchForm.value.sampleDate = ''
    searchForm.value.sampleDateEnd = ''
    dateRange.value = null
  }
  await sampleStore.search(buildEffectiveSearchParams())
}

const handleRollbackIsolatedChange = async (value: boolean) => {
  searchForm.value.showRollbackIsolated = value
  handleClearSelection()
  await sampleStore.search(buildEffectiveSearchParams())
}

// ===== 批量操作 =====

const handleSelectionChange = (ids: number[]) => {
  sampleStore.updateSelectedIds(ids)
}

const handleClearSelection = () => {
  sampleStore.clearSelection()
  sampleTableRef.value?.clearSelection()
}

const handleBatchVoid = async () => {
  if (sampleStore.selectedIds.length === 0) {
    ElMessage.warning('请先选择要作废的记录')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      `确认批量作废 ${sampleStore.selectedIds.length} 条留样记录？`,
      '批量作废确认',
      {
        confirmButtonText: '确认作废',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '请说明作废原因',
        inputValidator: (val: string) => val?.trim() ? true : '作废原因不能为空'
      }
    )
    await sampleStore.batchVoid(value.trim())
  } catch {
    // 用户取消
  }
}

const handleBatchArchive = async () => {
  if (sampleStore.selectedIds.length === 0) {
    ElMessage.warning('请先选择要归档的记录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量归档 ${sampleStore.selectedIds.length} 条留样记录？归档后记录将不可再修改。`,
      '批量归档确认',
      {
        confirmButtonText: '确认归档',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await sampleStore.batchArchive()
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="sample-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">留样管理</h1>
        <p class="page-subtitle">管理食品留样记录，追踪留样状态与销样情况</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" v-permission="SAMPLE_PERMISSIONS.CREATE" @click="sampleStore.formVisible = true">新增留样</el-button>
        <el-button v-if="canUseHistorySupplement" type="warning" @click="sampleStore.historyFormVisible = true">历史补录</el-button>
        <el-button v-if="canExportSampleLedger" :icon="Download" :loading="sampleStore.exportLoading" @click="handleExport">导出Excel</el-button>
      </div>
    </div>

    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已自动定位到需补留样或继续处置的记录，可直接进入详情、留样登记或销样处置。"
    />

    <SampleStatistics :dashboard="sampleStore.dashboard" @filter-status="handleFilterByStatus" />

    <div class="content-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="留样列表" name="list">
          <div class="search-section">
            <el-form :model="searchForm" inline class="search-form">
              <el-form-item label="留样日期">
                <el-date-picker
                  v-model="dateRange"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  :editable="false"
                  style="width: 240px"
                  @change="handleDateRangeChange"
                />
              </el-form-item>
              <el-form-item label="餐次">
                <el-select v-model="searchForm.mealType" placeholder="全部餐次" clearable style="width: 120px">
                  <el-option
                    v-for="opt in MEAL_TYPE_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 130px">
                  <el-option
                    v-for="opt in SAMPLE_STATUS_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="关键词">
                <el-tooltip
                  :visible="showMenuTip"
                  content="关键词最多输入30个字符"
                  placement="top"
                >
                  <span class="menu-input-tooltip-trigger">
                    <el-input v-model="searchForm.menuName" placeholder="留样编号/任务编号/菜谱名称" clearable maxlength="30" style="width: 200px" @input="handleMenuInput" @keydown="handleMenuKeydown" />
                  </span>
                </el-tooltip>
              </el-form-item>
              <el-form-item v-if="isAdmin" label="回滚作废台账">
                <el-switch
                  v-model="searchForm.showRollbackIsolated"
                  inline-prompt
                  active-text="仅查看"
                  inactive-text="关闭"
                  @change="handleRollbackIsolatedChange"
                />
              </el-form-item>
              <el-form-item>
                <el-button @click="handleReset">重置</el-button>
                <el-button type="primary" @click="debouncedSearch">查询</el-button>
              </el-form-item>
            </el-form>
          </div>

          <SampleRecordTable
            ref="sampleTableRef"
            :records="sampleStore.list"
            :loading="sampleStore.loading"
            :readonly-mode="isRollbackIsolatedView"
            :can-use-manual-disposal-supplement="canUseManualDisposalSupplement"
            @selection-change="handleSelectionChange"
            @detail="handleDetail"
            @register="handleRegisterFromTable"
            @edit="handleEdit"
            @dispose="handleDisposeFromTable"
            @manual-supplement="handleManualSupplementFromTable"
            @ai-evaluate="handleAiEvaluateFromTable"
            @void-record="handleVoidFromTable"
            @archive="handleArchive"
          />

          <!-- 批量操作栏 -->
          <div v-if="!isRollbackIsolatedView && sampleStore.selectedIds.length > 0" class="batch-action-bar">
            <span class="batch-info">已选择 <strong>{{ sampleStore.selectedIds.length }}</strong> 条记录</span>
            <div class="batch-actions">
              <el-button type="danger" size="small" :loading="sampleStore.batchLoading" v-permission="SAMPLE_PERMISSIONS.BATCH_VOID" @click="handleBatchVoid">批量作废</el-button>
              <el-button type="info" size="small" :loading="sampleStore.batchLoading" v-permission="SAMPLE_PERMISSIONS.BATCH_ARCHIVE" @click="handleBatchArchive">批量归档</el-button>
              <el-button size="small" @click="handleClearSelection">取消选择</el-button>
            </div>
          </div>

          <div class="pagination-wrapper">
            <span class="total-text">共 {{ sampleStore.total }} 条记录</span>
            <el-pagination
              v-model:current-page="sampleStore.pageNum"
              v-model:page-size="sampleStore.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="sampleStore.total"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="reminders">
          <template #label>
            销样提醒
            <el-badge v-if="sampleStore.reminderTotal > 0" :value="sampleStore.reminderTotal" :max="99" class="tab-badge" />
          </template>
          <SampleDisposalReminders
            :reminders="sampleStore.reminderList"
            :total="sampleStore.reminderTotal"
            :page-num="sampleStore.reminderPageNum"
            :page-size="sampleStore.reminderPageSize"
            :loading="sampleStore.reminderLoading"
            @detail="handleDetailFromReminder"
            @dispose="handleDisposeFromReminder"
            @page-change="handleReminderPageChange"
            @size-change="handleReminderSizeChange"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <SampleRecordDetail
      v-model="sampleStore.detailVisible"
      :record="sampleStore.currentRecord"
      :ai-result="sampleStore.aiEvaluateResult"
      :ai-loading="sampleStore.aiEvaluateLoading"
      :ai-error="sampleStore.aiEvaluateError"
      :loading="sampleStore.detailLoading"
      :operation-logs="sampleStore.operationLogs"
      :can-use-manual-disposal-supplement="canUseManualDisposalSupplement"
      @register="handleDetailRegister"
      @dispose="handleDetailDispose"
      @manual-supplement="handleDetailManualSupplement"
      @ai-evaluate="handleDetailAiEvaluate"
      @edit="handleDetailEdit"
      @archive="handleDetailArchive"
    />
    <SampleRecordForm
      v-model="sampleStore.formVisible"
      :loading="sampleStore.formLoading"
      @submit="handleSubmit"
    />
    <SampleHistoryRecordForm
      v-model="sampleStore.historyFormVisible"
      :loading="sampleStore.historyFormLoading"
      @submit="handleHistorySubmit"
    />
    <SampleRecordRegisterForm
      v-model="sampleStore.registerFormVisible"
      :loading="sampleStore.registerFormLoading"
      :record-data="sampleStore.registerTargetRecord"
      @submit="handleRegisterSubmit"
    />
    <SampleDisposalForm
      v-model="sampleStore.disposalFormVisible"
      :loading="sampleStore.disposalLoading"
      @submit="handleDisposalSubmit"
    />
    <SampleManualDisposalForm
      v-model="sampleStore.manualDisposalFormVisible"
      :loading="sampleStore.manualDisposalLoading"
      :record-data="sampleStore.manualDisposalTargetRecord"
      @submit="handleManualDisposalSubmit"
    />
    <SampleRecordEditForm
      v-model="sampleStore.editFormVisible"
      :record-data="sampleStore.editTargetRecord"
      @submit="handleEditSubmit"
    />
  </div>
</template>

<style lang="scss" scoped>
.sample-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

.dashboard-entry-alert {
  margin-bottom: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);

  .header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.page-title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}

.page-subtitle {
  margin: 6px 0 0;
  color: #909399;
  font-size: 14px;
}

.content-card {
  margin-top: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.search-section {
  margin-bottom: 16px;
}

.input-wrapper {
  position: relative;

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
}

.input-tip-enter-active,
.input-tip-leave-active {
  transition: opacity 0.2s;
}
.input-tip-enter-from,
.input-tip-leave-to {
  opacity: 0;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  :deep(.el-form-item) {
    margin-bottom: 0;
    margin-right: 16px;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.batch-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding: 10px 16px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
}

.batch-info {
  color: #409eff;
  font-size: 14px;

  strong {
    color: #303133;
  }
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.total-text {
  color: #909399;
  font-size: 14px;
}

.tab-badge {
  margin-left: 6px;

  :deep(.el-badge__content) {
    font-size: 10px;
  }
}

@media (max-width: 768px) {
  .page-header,
  .pagination-wrapper {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
