<script setup lang="ts">
import { computed, ref, onMounted, onActivated } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { useDeviceStore } from '@/stores/modules/device'
import { ONLINE_STATUS_OPTIONS, DEVICE_STATUS_OPTIONS } from '@/constants/device'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { mapDictOptions } from '@/utils/dict-category'
import DeviceStatistics from '@/components/business/device/DeviceStatistics.vue'
import DeviceTable from '@/components/business/device/DeviceTable.vue'
import DeviceForm from '@/components/business/device/DeviceForm.vue'
import DeviceDetail from '@/components/business/device/DeviceDetail.vue'
import DeviceOnlineStatusDialog from '@/components/business/device/DeviceOnlineStatusDialog.vue'
import DeviceImportDialog from '@/components/business/device/DeviceImportDialog.vue'
import DeviceImportResultDialog from '@/components/business/device/DeviceImportResultDialog.vue'
import { DEVICE_PERMISSIONS } from '@/constants/permission'
import type { Device, DeviceBatchItemResult } from '@/types'

const store = useDeviceStore()
const dictCategoryStore = useDictCategoryStore()
const route = useRoute()
const tableRef = ref<InstanceType<typeof DeviceTable> | null>(null)
const deviceActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'device-online-rate') return '来自数据监管看板：设备在线率异常'
  return '来自数据监管看板'
})

const searchForm = ref({
  keyword: store.searchParams.deviceName || '',
  deviceType: store.searchParams.deviceType as string | undefined,
  onlineStatus: store.searchParams.onlineStatus as string | undefined,
  status: store.searchParams.status as string | undefined,
})

const deviceTypeOptions = computed(() => mapDictOptions(
  dictCategoryStore.getCachedOptions('device_type')
))

onMounted(async () => {
  await Promise.all([
    dictCategoryStore.fetchOptions('device_type', false, true),
    dictCategoryStore.fetchOptions('device_type', true, true)
  ])
  if (store.list.length > 0) {
    await store.fetchStatistics()
  } else {
    await store.init()
  }
  await applyDashboardRouteQuery()
})

onActivated(async () => {
  if (!deviceActivatedOnce.value) {
    deviceActivatedOnce.value = true
    return
  }
  await Promise.all([
    dictCategoryStore.fetchOptions('device_type', false, true),
    dictCategoryStore.fetchOptions('device_type', true, true)
  ])
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
    keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
    deviceType: typeof route.query.deviceType === 'string' ? route.query.deviceType : undefined,
    onlineStatus: typeof route.query.onlineStatus === 'string'
      ? route.query.onlineStatus
      : route.query.metric === 'device-online-rate'
        ? 'offline'
        : undefined,
    status: typeof route.query.status === 'string' ? route.query.status : undefined
  }

  await handleSearch()

  if (openDetailOnce && store.list[0]) {
    handleDetail(store.list[0].id)
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

const handleSearch = () => {
  handleClearSelection()
  return store.search({
    deviceName: searchForm.value.keyword || undefined,
    deviceType: searchForm.value.deviceType,
    onlineStatus: searchForm.value.onlineStatus,
    status: searchForm.value.status,
  })
}

const handleReset = () => {
  handleClearSelection()
  searchForm.value = { keyword: '', deviceType: undefined, onlineStatus: undefined, status: undefined }
  store.resetSearch()
}

/** 设备名称/编码输入提示 */
const showKeywordTip = ref(false)
let keywordTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerKeywordTip = () => {
  if (keywordTipTimer) clearTimeout(keywordTipTimer)
  showKeywordTip.value = false
  requestAnimationFrame(() => { showKeywordTip.value = true })
  keywordTipTimer = setTimeout(() => { showKeywordTip.value = false }, 2000)
}
const handleKeywordInput = () => {
  if (searchForm.value.keyword.length >= 30) triggerKeywordTip()
  else showKeywordTip.value = false
}
const handleKeywordKeydown = (e: KeyboardEvent) => {
  if (searchForm.value.keyword.length >= 30 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerKeywordTip()
  }
}

const handleAdd = () => store.openForm()
const handleEdit = (id: number) => store.openForm(id)
const handleDetail = (id: number) => store.openDetail(id)
const handleDelete = (id: number) => store.deleteDevice(id)
const handleToggleStatus = (id: number) => store.toggleStatus(id)
const handleFormSuccess = () => {
  handleClearSelection()
  return store.init()
}

const handleSelectionChange = (devices: Device[]) => {
  store.updateSelectedDevices(devices)
}

const handleClearSelection = () => {
  store.clearSelection()
  tableRef.value?.clearSelection()
}

const handleBatchEnable = async () => {
  if (!store.hasSelection) return
  try {
    await ElMessageBox.confirm(
      `确认批量启用 ${store.selectedIds.length} 台设备？已启用设备将自动跳过。`,
      '批量启用确认',
      {
        confirmButtonText: '确定启用',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const feedback = await store.batchEnableDevices()
    openBatchResultDialog('批量启用结果', feedback.summaryMessage, feedback.skippedItems || [], feedback.failedItems || [])
  } catch (error: any) {
    if (error !== 'cancel' && error?.message !== 'cancel') {
      throw error
    }
  }
}

const handleBatchDisable = async () => {
  if (!store.hasSelection) return
  try {
    await ElMessageBox.confirm(
      `确认批量禁用 ${store.selectedIds.length} 台设备？已禁用设备将自动跳过；若设备存在进行中的录像任务或未关闭告警，将无法禁用。`,
      '批量禁用确认',
      {
        confirmButtonText: '确定禁用',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const feedback = await store.batchDisableDevices()
    openBatchResultDialog('批量禁用结果', feedback.summaryMessage, feedback.skippedItems || [], feedback.failedItems || [])
  } catch (error: any) {
    if (error !== 'cancel' && error?.message !== 'cancel') {
      throw error
    }
  }
}

const handleBatchDelete = async () => {
  if (!store.hasSelection) return
  try {
    await ElMessageBox.confirm(
      `确认批量删除 ${store.selectedIds.length} 台设备？删除后不可恢复；若设备存在进行中的录像任务或未关闭告警，将无法删除。`,
      '批量删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const feedback = await store.batchDeleteDevices()
    openBatchResultDialog('批量删除结果', feedback.summaryMessage, feedback.skippedItems || [], feedback.failedItems || [])
  } catch (error: any) {
    if (error !== 'cancel' && error?.message !== 'cancel') {
      throw error
    }
  }
}

const onlineStatusDialogVisible = ref(false)
const currentStatusDevice = ref<Device | null>(null)
const batchResultVisible = ref(false)
const batchResultTitle = ref('批量操作结果')
const batchResultSummary = ref('')
const batchSkippedItems = ref<Array<Pick<DeviceBatchItemResult, 'deviceName' | 'deviceCode' | 'reason'>>>([])
const batchFailedItems = ref<Array<Pick<DeviceBatchItemResult, 'deviceName' | 'deviceCode' | 'reason'>>>([])

const openBatchResultDialog = (
  title: string,
  summary?: string,
  skippedItems: Array<Pick<DeviceBatchItemResult, 'deviceName' | 'deviceCode' | 'reason'>> = [],
  failedItems: Array<Pick<DeviceBatchItemResult, 'deviceName' | 'deviceCode' | 'reason'>> = []
) => {
  if (skippedItems.length === 0 && failedItems.length === 0) return
  batchResultTitle.value = title
  batchResultSummary.value = summary || ''
  batchSkippedItems.value = skippedItems
  batchFailedItems.value = failedItems
  batchResultVisible.value = true
}

const handleOnlineStatus = (device: Device) => {
  currentStatusDevice.value = device
  onlineStatusDialogVisible.value = true
}

const handleOnlineStatusSuccess = () => {
  store.init()
}
</script>

<template>
  <div class="device-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已自动筛出当前设备异常范围，可直接查看离线、故障或停用设备明细。"
    />

    <!-- 统计卡片 -->
    <DeviceStatistics :statistics="store.statistics" />

    <!-- 搜索工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="5">
          <el-tooltip :visible="showKeywordTip" content="设备名称/编码最多输入30个字符" placement="top">
            <el-input v-model="searchForm.keyword" placeholder="设备名称/编码" clearable maxlength="30" @keyup.enter="handleSearch" @input="handleKeywordInput" @keydown="handleKeywordKeydown" />
          </el-tooltip>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.deviceType" placeholder="全部类型" clearable>
            <el-option v-for="t in deviceTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.onlineStatus" placeholder="在线状态" clearable>
            <el-option v-for="s in ONLINE_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.status" placeholder="设备状态" clearable>
            <el-option v-for="s in DEVICE_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-col>
        <el-col :span="7" style="text-align:right">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button v-permission="DEVICE_PERMISSIONS.EDIT" :loading="store.exportLoading" @click="store.handleExport()">导出</el-button>
          <el-button v-permission="DEVICE_PERMISSIONS.CREATE" @click="store.openImportDialog()">导入</el-button>
          <el-button type="primary" v-permission="DEVICE_PERMISSIONS.CREATE" @click="handleAdd">+ 新增设备</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 表格 -->
    <DeviceTable
      ref="tableRef"
      :data="store.list"
      :loading="store.loading"
      :error="store.listError"
      @retry="handleSearch"
      @selection-change="handleSelectionChange"
      @detail="handleDetail"
      @edit="handleEdit"
      @online-status="handleOnlineStatus"
      @delete="handleDelete"
      @toggle-status="handleToggleStatus"
    />

    <div v-if="store.hasSelection" class="batch-action-bar">
      <span class="batch-info">已选择 <strong>{{ store.selectedIds.length }}</strong> 台设备</span>
      <div class="batch-actions">
        <el-button type="success" size="small" :loading="store.batchLoading" v-permission="DEVICE_PERMISSIONS.EDIT" @click="handleBatchEnable">批量启用</el-button>
        <el-button type="warning" size="small" :loading="store.batchLoading" v-permission="DEVICE_PERMISSIONS.EDIT" @click="handleBatchDisable">批量停用</el-button>
        <el-button type="danger" size="small" :loading="store.batchLoading" v-permission="DEVICE_PERMISSIONS.DELETE" @click="handleBatchDelete">批量删除</el-button>
        <el-button size="small" @click="handleClearSelection">取消选择</el-button>
      </div>
    </div>

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

    <!-- 详情弹窗 -->
    <DeviceDetail
      v-model:visible="store.detailVisible"
      :device-id="store.currentDeviceId"
    />

    <!-- 新增/编辑弹窗 -->
    <DeviceForm
      v-model:visible="store.formVisible"
      :device-id="store.currentDeviceId"
      @success="handleFormSuccess"
    />

    <DeviceOnlineStatusDialog
      v-model:visible="onlineStatusDialogVisible"
      :device="currentStatusDevice"
      @success="handleOnlineStatusSuccess"
    />

    <el-dialog
      v-model="batchResultVisible"
      :title="batchResultTitle"
      width="640px"
      destroy-on-close
    >
      <div class="batch-result-dialog">
        <el-alert
          v-if="batchResultSummary"
          :title="batchResultSummary"
          type="info"
          :closable="false"
          show-icon
          class="batch-result-summary"
        />

        <div v-if="batchFailedItems.length > 0" class="batch-result-section">
          <div class="batch-result-section__title danger">失败设备</div>
          <div class="batch-result-list">
            <div v-for="(item, index) in batchFailedItems" :key="`failed-${index}`" class="batch-result-item">
              <span class="device-name">{{ item.deviceName || item.deviceCode || '设备' }}</span>
              <span class="reason">{{ item.reason || '未知原因' }}</span>
            </div>
          </div>
        </div>

        <div v-if="batchSkippedItems.length > 0" class="batch-result-section">
          <div class="batch-result-section__title warning">跳过设备</div>
          <div class="batch-result-list">
            <div v-for="(item, index) in batchSkippedItems" :key="`skipped-${index}`" class="batch-result-item">
              <span class="device-name">{{ item.deviceName || item.deviceCode || '设备' }}</span>
              <span class="reason">{{ item.reason || '未执行' }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 导入对话框 -->
    <DeviceImportDialog
      v-model:visible="store.importDialogVisible"
      :loading="store.importLoading"
      @import="store.handleImport"
    />

    <!-- 导入结果对话框 -->
    <DeviceImportResultDialog
      v-model:visible="store.importResultVisible"
      :result="store.importResult"
      @close="store.closeImportResult"
    />
  </div>
</template>

<style lang="scss" scoped>
.device-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.toolbar {
  background: $bg-white;
  padding: 20px;
  border-radius: $border-radius-large;
  margin-bottom: 20px;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;
  .el-input, .el-select { width: 100%; }
}
.pagination {
  background: $bg-white;
  padding: 20px;
  border-radius: $border-radius-large;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  .total { color: $text-regular; font-size: 14px; }
}
.batch-action-bar {
  margin-top: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
}
.batch-info {
  color: #409eff;
  font-size: 14px;
  strong { color: #303133; }
}
.batch-actions {
  display: flex;
  gap: 8px;
}
.batch-result-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.batch-result-summary {
  margin-bottom: 4px;
}
.batch-result-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.batch-result-section__title {
  font-size: 14px;
  font-weight: 600;

  &.danger {
    color: var(--el-color-danger);
  }

  &.warning {
    color: var(--el-color-warning);
  }
}
.batch-result-list {
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.batch-result-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  &:last-child {
    border-bottom: none;
  }

  .device-name {
    flex: 0 0 180px;
    color: $text-primary;
    font-weight: 500;
    word-break: break-all;
  }

  .reason {
    flex: 1;
    color: $text-regular;
    text-align: right;
    word-break: break-all;
  }
}
</style>
