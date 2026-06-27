<script setup lang="ts">
import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { getHealthCheckLinkageVersion } from '@/api/modules/health-check'
import { useHealthCheckStore } from '@/stores/modules/health-check'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'
import type {
  HealthCheckRecord,
  HealthCheckRecordDetail,
  HealthCheckUpdatePayload,
} from '@/types/health-check'
import {
  COMPLETED_STATUS_OPTIONS,
  HEALTH_CHECK_RESULT_OPTIONS,
  PENDING_STATUS_OPTIONS,
} from '@/constants/health-check'
import HealthCheckStatistics from '@/components/business/health-check/HealthCheckStatistics.vue'
import HealthCheckPendingTable from '@/components/business/health-check/HealthCheckPendingTable.vue'
import HealthCheckRecordTable from '@/components/business/health-check/HealthCheckRecordTable.vue'
import HealthCheckDetailDialog from '@/components/business/health-check/HealthCheckDetailDialog.vue'
import HealthCheckFormWithFace from '@/components/business/health-check/HealthCheckFormWithFace.vue'
import HealthCertificateManager from '@/components/business/health-check/HealthCertificateManager.vue'
import CertificateDashboard from '@/components/business/health-check/CertificateDashboard.vue'

const healthCheckStore = useHealthCheckStore()
const route = useRoute()

const today = new Date().toISOString().slice(0, 10)
const activeTab = ref('pending')
const certDashboardRef = ref<InstanceType<typeof CertificateDashboard> | null>(null)
const morningCheckActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'morning-check') {
    return '来自数据监管看板：晨检完成率异常'
  }
  return '来自数据监管看板'
})

const handleCertChanged = () => {
  certDashboardRef.value?.refresh()
  healthCheckStore.fetchDashboard()
}
const pendingSearchForm = ref<{
  checkDate: string
  orgId: number | null
  status: string
  employeeName: string
}>({
  checkDate: healthCheckStore.pendingSearchParams.checkDate || today,
  orgId: healthCheckStore.pendingSearchParams.orgId ?? null,
  status: healthCheckStore.pendingSearchParams.status || '',
  employeeName: healthCheckStore.pendingSearchParams.employeeName || '',
})

const searchForm = ref<{
  checkDateRange: string[]
  status: string
  checkResult: string
  employeeName: string
}>({
  checkDateRange: [
    healthCheckStore.searchParams.checkDateStart || today,
    healthCheckStore.searchParams.checkDateEnd || today,
  ],
  status: healthCheckStore.searchParams.status || '',
  checkResult: healthCheckStore.searchParams.checkResult || '',
  employeeName: healthCheckStore.searchParams.employeeName || '',
})

const currentEmployee = ref<HealthCheckRecord | null>(null)
const refreshingRealtime = ref(false)
const linkageVersion = ref('')
let linkagePollTimer: ReturnType<typeof setInterval> | null = null
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

/** 从表单中提取搜索参数（包含所有字段，空值也保留以确保覆盖旧条件） */
const getCleanPendingSearchParams = (): Record<string, any> => {
  const form = pendingSearchForm.value
  return {
    checkDate: form.checkDate || '',
    orgId: form.orgId ?? undefined,
    status: form.status || '',
    employeeName: form.employeeName || '',
  }
}

/** 从表单中提取搜索参数（包含所有字段，空值也保留以确保覆盖旧条件） */
const getCleanSearchParams = (): Record<string, any> => {
  const form = searchForm.value
  return {
    checkDateStart: form.checkDateRange?.[0] || '',
    checkDateEnd: form.checkDateRange?.[1] || '',
    status: form.status || '',
    checkResult: form.checkResult || '',
    employeeName: form.employeeName || '',
  }
}

const refreshRealtimeSnapshot = async (includeCompleted = false) => {
  if (refreshingRealtime.value) return
  refreshingRealtime.value = true
  try {
    const tasks: Promise<any>[] = [
      healthCheckStore.fetchDashboard(),
      healthCheckStore.fetchPendingList(),
    ]
    if (includeCompleted || activeTab.value === 'completed') {
      tasks.push(healthCheckStore.fetchList())
    }
    await Promise.all(tasks)
    if (healthCheckStore.detailVisible && healthCheckStore.currentRecord?.checkDate === today) {
      await healthCheckStore.openDetail(healthCheckStore.currentRecord.id)
    }
  } finally {
    refreshingRealtime.value = false
  }
}

const syncLinkageVersion = async (refreshOnChange = true) => {
  try {
    const res = await getHealthCheckLinkageVersion()
    if (res.code !== 'SUCCESS') return
    const nextVersion = res.data?.version || 'none'
    if (!linkageVersion.value) {
      linkageVersion.value = nextVersion
      return
    }
    if (refreshOnChange && linkageVersion.value !== nextVersion) {
      linkageVersion.value = nextVersion
      await refreshRealtimeSnapshot(false)
      return
    }
    linkageVersion.value = nextVersion
  } catch {
    // ignore poll failures
  }
}

const startRealtimePolling = () => {
  stopRealtimePolling()
  linkagePollTimer = setInterval(() => {
    void syncLinkageVersion(true)
  }, 3000)
  autoRefreshTimer = setInterval(() => {
    void refreshRealtimeSnapshot(true)
  }, 30000)
}

const stopRealtimePolling = () => {
  if (linkagePollTimer) {
    clearInterval(linkagePollTimer)
    linkagePollTimer = null
  }
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey
  const targetTab = route.query.tab === 'completed' ? 'completed' : route.query.tab === 'certificate' ? 'certificate' : 'pending'
  activeTab.value = targetTab

  if (targetTab === 'completed') {
    searchForm.value = {
      checkDateRange: [today, today],
      status: typeof route.query.status === 'string' ? route.query.status : '',
      checkResult: typeof route.query.checkResult === 'string' ? route.query.checkResult : '',
      employeeName: typeof route.query.employeeName === 'string' ? route.query.employeeName : '',
    }
    await healthCheckStore.search(getCleanSearchParams())
    if (openDetailOnce && healthCheckStore.list[0]) {
      await healthCheckStore.openDetail(healthCheckStore.list[0].id)
    }
  } else if (targetTab === 'pending') {
    pendingSearchForm.value = {
      checkDate: typeof route.query.checkDate === 'string' ? route.query.checkDate : today,
      orgId: route.query.orgId ? Number(route.query.orgId) : null,
      status: typeof route.query.status === 'string' ? route.query.status : '',
      employeeName: typeof route.query.employeeName === 'string' ? route.query.employeeName : '',
    }
    await healthCheckStore.searchPending(getCleanPendingSearchParams())
    if (openDetailOnce && healthCheckStore.pendingList[0]) {
      await healthCheckStore.openDetail(healthCheckStore.pendingList[0].id)
    }
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

onMounted(async () => {
  if (healthCheckStore.list.length > 0) {
    await healthCheckStore.refreshMeta()
    await healthCheckStore.fetchList()
  } else {
    await healthCheckStore.init()
  }
  await applyDashboardRouteQuery()
  await syncLinkageVersion(false)
  startRealtimePolling()
})

onActivated(async () => {
  if (!morningCheckActivatedOnce.value) {
    morningCheckActivatedOnce.value = true
    return
  }
  if (await applyDashboardRouteQuery()) {
    return
  }
  await refreshRealtimeSnapshot(activeTab.value === 'completed')
})

onUnmounted(() => {
  stopRealtimePolling()
})

const handleSearch = async () => {
  await healthCheckStore.search(getCleanSearchParams())
}

const handlePendingSearch = async () => {
  await healthCheckStore.searchPending(getCleanPendingSearchParams())
}

const handleReset = async () => {
  searchForm.value = {
    checkDateRange: [today, today],
    status: '',
    checkResult: '',
    employeeName: '',
  }
  await healthCheckStore.resetSearch()
}

const handlePendingReset = async () => {
  pendingSearchForm.value = {
    checkDate: today,
    orgId: null,
    status: '',
    employeeName: '',
  }
  await healthCheckStore.resetPendingSearch()
}

/** 员工姓名输入提示 */
const showEmpTip = ref(false)
let empTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerEmpTip = () => {
  if (empTipTimer) clearTimeout(empTipTimer)
  showEmpTip.value = false
  requestAnimationFrame(() => { showEmpTip.value = true })
  empTipTimer = setTimeout(() => { showEmpTip.value = false }, 2000)
}
const handleEmpInput = () => {
  if (searchForm.value.employeeName.length >= 10) triggerEmpTip()
  else showEmpTip.value = false
}
const handleEmpKeydown = (e: KeyboardEvent) => {
  if (searchForm.value.employeeName.length >= 10 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerEmpTip()
  }
}

const handleDetail = async (record: HealthCheckRecord) => {
  await healthCheckStore.openDetail(record.id)
}

const handleCheckFromPending = (record: HealthCheckRecord) => {
  currentEmployee.value = record
  healthCheckStore.formVisible = true
}

const handleUpdateCheck = async (id: number, _payload: HealthCheckUpdatePayload) => {
  // Update handled by store
}

const handleArchive = async (record: HealthCheckRecord | HealthCheckRecordDetail) => {
  try {
    await ElMessageBox.confirm('确认要归档此记录吗？归档后将无法修改。', '归档确认', {
      confirmButtonText: '确认归档',
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false
    })
    await healthCheckStore.archiveCheck(record.id)
  } catch {
    // User cancelled
  }
}

const handlePageChange = (page: number) => {
  healthCheckStore.changePage(page)
}

const handleSizeChange = (size: number) => {
  healthCheckStore.changePageSize(size)
}

const handlePendingPageChange = (page: number) => {
  healthCheckStore.changePendingPage(page)
}

const handlePendingSizeChange = (size: number) => {
  healthCheckStore.changePendingPageSize(size)
}

const handleFormSuccess = async () => {
  healthCheckStore.formVisible = false
  await healthCheckStore.refreshAll()
}
</script>

<template>
  <div class="morning-check-page">

    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已自动切换到异常晨检记录，可直接在列表中查看详情、归档或继续追踪异常人员。"
    />

    <HealthCheckStatistics :dashboard="healthCheckStore.dashboard" />

    <div class="content-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="待晨检" name="pending">
          <div class="search-section">
            <el-form :model="pendingSearchForm" inline class="search-form">
              <el-form-item label="晨检日期">
                <el-date-picker
                  v-model="pendingSearchForm.checkDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择日期"
                  :editable="false"
                  clearable
                  style="width: 160px"
                />
              </el-form-item>
              <el-form-item label="员工所属组织">
                <div style="width: 220px">
                  <OrgTreeSelect
                    v-model="pendingSearchForm.orgId"
                    :active-only="true"
                    placeholder="全部组织"
                  />
                </div>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="pendingSearchForm.status" placeholder="全部状态" clearable style="width: 140px">
                  <el-option
                    v-for="opt in PENDING_STATUS_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="员工姓名">
                <el-input
                  v-model="pendingSearchForm.employeeName"
                  placeholder="请输入员工姓名"
                  clearable
                  maxlength="10"
                  style="width: 160px"
                  @keyup.enter="handlePendingSearch"
                />
              </el-form-item>
              <el-form-item>
                <el-button @click="handlePendingReset">重置</el-button>
                <el-button type="primary" @click="handlePendingSearch">查询</el-button>
              </el-form-item>
            </el-form>
          </div>

          <HealthCheckPendingTable
            :records="healthCheckStore.pendingList"
            :total="healthCheckStore.pendingTotal"
            :page="healthCheckStore.pendingPage"
            :page-size="healthCheckStore.pendingPageSize"
            :loading="healthCheckStore.pendingLoading"
            @check="handleCheckFromPending"
            @detail="handleDetail"
            @page-change="handlePendingPageChange"
            @size-change="handlePendingSizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="已晨检" name="completed">
          <div class="search-section">
            <el-form :model="searchForm" inline class="search-form">
              <el-form-item label="晨检日期">
                <el-date-picker
                  v-model="searchForm.checkDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  :editable="false"
                  clearable
                  style="width: 260px"
                />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 140px">
                  <el-option
                    v-for="opt in COMPLETED_STATUS_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="晨检结果">
                <el-select v-model="searchForm.checkResult" placeholder="全部结果" clearable style="width: 120px">
                  <el-option
                    v-for="opt in HEALTH_CHECK_RESULT_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="员工姓名">
                <el-tooltip :visible="showEmpTip" content="员工姓名最多输入10个字符" placement="top">
                  <el-input
                    v-model="searchForm.employeeName"
                    placeholder="请输入员工姓名"
                    clearable
                    maxlength="10"
                    style="width: 160px"
                    @input="handleEmpInput"
                    @keydown="handleEmpKeydown"
                  />
                </el-tooltip>
              </el-form-item>
              <el-form-item>
                <el-button @click="handleReset">重置</el-button>
                <el-button type="primary" @click="handleSearch">查询</el-button>
              </el-form-item>
            </el-form>
          </div>

          <HealthCheckRecordTable
            :records="healthCheckStore.list"
            :total="healthCheckStore.total"
            :page="healthCheckStore.page"
            :page-size="healthCheckStore.pageSize"
            :loading="healthCheckStore.loading"
            @detail="handleDetail"
            @archive="handleArchive"
            @page-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="健康证管理" name="certificate">
          <CertificateDashboard ref="certDashboardRef" />
          <HealthCertificateManager @changed="handleCertChanged" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 晨检详情弹窗 -->
    <HealthCheckDetailDialog
      :visible="healthCheckStore.detailVisible"
      :record="healthCheckStore.currentRecord"
      :loading="healthCheckStore.loading"
      @close="healthCheckStore.closeDetail"
      @update="handleUpdateCheck"
      @archive="handleArchive"
    />

    <!-- 晨检表单 -->
    <HealthCheckFormWithFace
      v-model:visible="healthCheckStore.formVisible"
      :employee="currentEmployee"
      :pending-list="healthCheckStore.pendingList"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style lang="scss" scoped>
.morning-check-page {
  padding: 0;

  .dashboard-entry-alert {
    margin-bottom: 16px;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }

  .page-title {
    font-size: 24px;
    font-weight: 600;
    margin: 0;
    color: #303133;
  }

  .page-subtitle {
    font-size: 14px;
    color: #909399;
    margin: 8px 0 0;
  }

  .content-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }

  .search-section {
    margin-bottom: 20px;
  }

  .search-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
}
</style>
