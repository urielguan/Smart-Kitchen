<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { useCookStore } from '@/stores/modules/cook'
import type { CookTask, CookTaskQuery } from '@/types'
import CookStatistics from '@/components/business/cook/CookStatistics.vue'
import CookTaskTable from '@/components/business/cook/CookTaskTable.vue'
import CookTaskDetail from '@/components/business/cook/CookTaskDetail.vue'
import CookTemperatureDialog from '@/components/business/cook/CookTemperatureDialog.vue'

const cookStore = useCookStore()
const route = useRoute()
const cookActivatedOnce = ref(false)
const selectedTasks = ref<CookTask[]>([])
const lastDashboardRouteKey = ref('')
const dashboardTemperatureMode = computed(() => route.query.from === 'dashboard' && route.query.temperatureAbnormal === '1')
const dashboardEntryTitle = computed(() => {
  if (!dashboardTemperatureMode.value) return ''
  return '来自数据监管看板：烹饪温度达标率异常'
})

const canArchive = (task: CookTask): boolean => {
  return task.status === 'completed' && task.reviewStatus === 'approved'
}

const archiveableSelectedCount = computed(() =>
  selectedTasks.value.filter(canArchive).length
)

const hasArchiveableSelection = computed(() => archiveableSelectedCount.value > 0)

const searchForm = ref<CookTaskQuery>({
  taskDate: cookStore.searchParams.taskDate || '',
  mealType: cookStore.searchParams.mealType || '',
  status: cookStore.searchParams.status || '',
  taskNo: cookStore.searchParams.taskNo || '',
  chefName: cookStore.searchParams.chefName || ''
})

const displayedTasks = computed(() => {
  if (!dashboardTemperatureMode.value) {
    return cookStore.list
  }
  return cookStore.list.filter((task) => task.temperatureAbnormal)
})

const displayedTotal = computed(() => {
  if (!dashboardTemperatureMode.value) {
    return cookStore.total
  }
  return displayedTasks.value.length
})

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey

  searchForm.value = {
    planDate: typeof route.query.planDate === 'string' ? route.query.planDate : '',
    mealType: typeof route.query.mealType === 'string' ? route.query.mealType : '',
    status: typeof route.query.status === 'string' ? route.query.status : '',
    taskNo: typeof route.query.taskNo === 'string' ? route.query.taskNo : '',
    chefName: typeof route.query.chefName === 'string' ? route.query.chefName : ''
  }

  await cookStore.search({ ...searchForm.value })

  if (openDetailOnce) {
    const targetTask = dashboardTemperatureMode.value
      ? cookStore.list.find((task) => task.temperatureAbnormal)
      : cookStore.list[0]
    if (targetTask) {
      await cookStore.openTemperature(targetTask.id)
    }
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

/** 初始化：有缓存数据时仅刷新统计，不重置列表 */
onMounted(async () => {
  if (cookStore.list.length > 0) {
    await cookStore.fetchDashboard()
  } else {
    await cookStore.init()
  }
  await applyDashboardRouteQuery()
})

onActivated(async () => {
  if (!cookActivatedOnce.value) {
    cookActivatedOnce.value = true
    return
  }
  if (await applyDashboardRouteQuery()) {
    return
  }
  await cookStore.refresh()
})

const handleSearch = async () => {
  await cookStore.search({ ...searchForm.value })
}

const handleReset = async () => {
  searchForm.value = {
    taskDate: '',
    mealType: '',
    status: '',
    taskNo: '',
    chefName: ''
  }
  await cookStore.resetSearch()
}

 const handleDetail = async (task: CookTask) => {
  await cookStore.openDetail(task.id)
}

const handleTemperature = async (task: CookTask) => {
  await cookStore.openTemperature(task.id)
}

const handleSelectionChange = (tasks: CookTask[]) => {
  selectedTasks.value = tasks
}

const handlePageChange = (page: number) => {
  cookStore.changePage(page)
}

const handleSizeChange = (size: number) => {
  cookStore.changePageSize(size)
}

/** 烹饪人输入提示 */
const showChefTip = ref(false)
let chefTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerChefTip = () => {
  if (chefTipTimer) clearTimeout(chefTipTimer)
  showChefTip.value = false
  requestAnimationFrame(() => { showChefTip.value = true })
  chefTipTimer = setTimeout(() => { showChefTip.value = false }, 2000)
}
const handleChefInput = () => {
  if (searchForm.value.chefName.length >= 10) triggerChefTip()
  else showChefTip.value = false
}
const handleChefKeydown = (e: KeyboardEvent) => {
  if (searchForm.value.chefName.length >= 10 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerChefTip()
  }
}

const handleArchive = async (task: CookTask) => {
  try {
    await ElMessageBox.confirm('确认归档该烹饪记录？归档后记录将无法修改。', '归档确认', {
      confirmButtonText: '确认归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cookStore.archiveTask(task.id)
  } catch {
    // user cancelled
  }
}

const handleBatchArchive = async () => {
  if (selectedTasks.value.length === 0) return
  const eligibleTasks = selectedTasks.value.filter(canArchive)
  if (eligibleTasks.length === 0) {
    ElMessage.warning('所选记录均不满足归档条件（需已完成且复核通过）')
    return
  }
  const skippedCount = selectedTasks.value.length - eligibleTasks.length
  const confirmMsg = skippedCount > 0
    ? `所选 ${selectedTasks.value.length} 条记录中有 ${skippedCount} 条不满足归档条件（需已完成且复核通过），仅归档 ${eligibleTasks.length} 条。确认继续？`
    : `确认批量归档 ${eligibleTasks.length} 条烹饪记录？归档后记录将无法修改。`
  try {
    await ElMessageBox.confirm(confirmMsg, '批量归档确认', {
      confirmButtonText: '确认归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const ids = eligibleTasks.map(t => t.id)
    await cookStore.batchArchiveTasks(ids)
  } catch {
    // user cancelled
  }
}
</script>

<template>
  <div class="cook-record-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">烹饪记录管理</h1>
        <p class="page-subtitle">聚合查看烹饪任务、执行进度与温度异常</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="cookStore.refresh">刷新数据</el-button>
        <el-button type="success" @click="cookStore.exportTasks">导出 Excel</el-button>
      </div>
    </div>

    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="error"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已自动筛出当前页温度异常任务，并优先打开首条温度复核明细，可直接补录温度证据或继续核查。"
    />

    <CookStatistics :dashboard="cookStore.dashboard" />

    <div class="search-card">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="实施日期">
          <el-date-picker
            v-model="searchForm.taskDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :editable="false"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="餐次">
          <el-select v-model="searchForm.mealType" placeholder="全部餐次" clearable style="width: 120px">
            <el-option label="早餐" value="breakfast" />
            <el-option label="午餐" value="lunch" />
            <el-option label="晚餐" value="dinner" />
            <el-option label="宵夜" value="supper" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="待烹饪" value="pending" />
            <el-option label="烹饪中" value="in_progress" />
            <el-option label="已完成" value="completed" />
            <el-option label="已归档" value="archived" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务编号">
          <el-input
            v-model="searchForm.taskNo"
            placeholder="请输入任务编号"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="烹饪人">
          <div class="input-wrapper">
            <transition name="input-tip">
              <span v-if="showChefTip" class="input-length-tip">烹饪人最多输入10个字符</span>
            </transition>
            <el-input v-model="searchForm.chefName" placeholder="请输入烹饪人" clearable maxlength="10" style="width: 160px" @input="handleChefInput" @keydown="handleChefKeydown" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div v-if="selectedTasks.length > 0" class="table-toolbar">
        <span class="toolbar-text">已选择 {{ selectedTasks.length }} 条记录（{{ archiveableSelectedCount }} 条可归档）</span>
        <el-button v-if="hasArchiveableSelection" type="warning" @click="handleBatchArchive">批量归档</el-button>
      </div>

      <CookTaskTable
        :tasks="displayedTasks"
        :loading="cookStore.loading"
        @detail="handleDetail"
        @temperature="handleTemperature"
        @archive="handleArchive"
        @selection-change="handleSelectionChange"
      />

      <div class="pagination-wrapper">
        <span class="total-text">共 {{ displayedTotal }} 条记录</span>
        <el-pagination
          v-model:current-page="cookStore.pageNum"
          v-model:page-size="cookStore.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="cookStore.total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <CookTaskDetail v-model="cookStore.detailVisible" :task="cookStore.currentTask" />
    <CookTemperatureDialog v-model="cookStore.temperatureVisible" :task="cookStore.currentTask" />
  </div>
</template>

<style lang="scss" scoped>
.cook-record-page {
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
}

.header-actions {
  display: flex;
  gap: 10px;
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

.search-card,
.table-card {
  margin-top: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #ecf5ff;
  border-radius: 4px;
}

.toolbar-text {
  color: #409eff;
  font-size: 14px;
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

.total-text {
  color: #909399;
  font-size: 14px;
}

@media (max-width: 768px) {
  .page-header,
  .pagination-wrapper {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
