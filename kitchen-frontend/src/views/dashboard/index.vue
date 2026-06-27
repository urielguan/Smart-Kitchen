<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useCookStore } from '@/stores/modules/cook'
import { useAppStore } from '@/stores/modules/app'
import { useAlertNotification } from '@/composables/useAlertNotification'
import { getCurrentMealPeriod } from '@/utils/meal-period'
import type { CookTask } from '@/types'
import SupTopBar from '@/components/business/supervisor/SupTopBar.vue'
import SupTaskList from '@/components/business/supervisor/SupTaskList.vue'
import SupTaskDetail from '@/components/business/supervisor/SupTaskDetail.vue'
import SupMonitor from '@/components/business/supervisor/SupMonitor.vue'
import OfflineIndicator from '@/components/business/shared/OfflineIndicator.vue'

const route = useRoute()
const cookStore = useCookStore()
const appStore = useAppStore()

const selectedTaskId = computed(() => cookStore.currentTask?.id ?? null)

const currentTaskActionState = computed(() => cookStore.currentTaskActionState)

// P2-14/15: 主管视图告警通知（补充 ElNotification 弹窗，SupMonitor 的 useAlertNotification 保留）
const dashboardAlerts = computed(() =>
  cookStore.list.filter(
    (t) => t.temperatureAbnormal || t.aiViolationCount > 0 || t.deviceOnline === false || t.sensorOnline === false
  )
)
const { requestPermission: requestDashboardPermission } = useAlertNotification(dashboardAlerts)

const handleSelectTask = async (task: CookTask) => {
  await cookStore.openDetail(task.id)
}

const handleStart = async () => {
  if (!cookStore.currentTask) return
  await cookStore.startTaskAction(cookStore.currentTask.id, { chefName: cookStore.currentTask.chefName || undefined })
  if (cookStore.currentTask) await cookStore.loadTaskDetail(cookStore.currentTask.id)
}

const handleComplete = async () => {
  if (!cookStore.currentTask) return
  await cookStore.completeTaskAction(cookStore.currentTask.id, { actualQty: cookStore.currentTask.actualQty || cookStore.currentTask.plannedQty })
  if (cookStore.currentTask) await cookStore.loadTaskDetail(cookStore.currentTask.id)
}

const handleReassign = async () => {
  // Reassign is now handled internally by SupTaskDetail's chef assign dropdown
  // This handler kept as fallback for the emit but should not be triggered
}

const handleAssignFromList = async (task: CookTask) => {
  await cookStore.openDetail(task.id)
}

const handleCancel = async () => {
  if (!cookStore.currentTask) return
  await cookStore.cancelTaskAction(cookStore.currentTask.id, { reason: '主管取消未开始任务' })
  if (cookStore.currentTask) await cookStore.loadTaskDetail(cookStore.currentTask.id)
}

const handleConfirmTempAbnormal = async () => {
  if (!cookStore.currentTask) return
  await cookStore.confirmTempAbnormalAction(cookStore.currentTask.id)
}

const handleArchive = async () => {
  if (!cookStore.currentTask) return
  await cookStore.archiveTaskAction(cookStore.currentTask.id)
  if (cookStore.currentTask) await cookStore.loadTaskDetail(cookStore.currentTask.id)
}


onMounted(async () => {
  cookStore.searchParams.mealType = '' // 默认展示全部餐次
  // P0-2: 从首页指标卡导航带过来的筛选参数
  const queryStatus = route.query.status as string | undefined
  const queryAlert = route.query.alert as string | undefined
  if (queryStatus) {
    cookStore.searchParams.status = queryStatus
  }
  if (queryAlert === 'temp') {
    cookStore.searchParams.keyword = '温度异常'
  } else if (queryAlert === 'duration') {
    cookStore.searchParams.keyword = '时长异常'
  }
  try {
    await cookStore.fetchOrgList()
    await cookStore.fetchChefList()
    await cookStore.refresh()
  } catch {
    // 初始加载失败不阻塞页面，自动刷新会重试
  }
  cookStore.startAutoRefresh()
  requestDashboardPermission()
})

onUnmounted(() => {
  cookStore.stopAutoRefresh()
})
</script>

<template>
  <div class="sup-app">
    <SupTopBar />
    <OfflineIndicator prefix="sup" />
    <div class="sup-layout">
      <SupTaskList
        :tasks="cookStore.list"
        :selected-id="selectedTaskId"
        :loading="cookStore.loading"
        :total="cookStore.total"
        :page-num="cookStore.pageNum"
        :page-size="cookStore.pageSize"
        @select="handleSelectTask"
        @assign="handleAssignFromList"
        @update:page-num="cookStore.changePage"
        @update:page-size="cookStore.changePageSize"
      />
      <SupTaskDetail
        :task="cookStore.currentTask"
        :start-action-state="currentTaskActionState.start"
        :complete-action-state="currentTaskActionState.complete"
        :action-loading="cookStore.actionLoadingId !== null"
        @start="handleStart"
        @complete="handleComplete"
        @reassign="handleReassign"
        @cancel="handleCancel"
        @confirm-temp-abnormal="handleConfirmTempAbnormal"
        @archive="handleArchive"
              />
      <SupMonitor :tasks="cookStore.list" />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.sup-app {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $kds-bg;
  color: $kds-text;
  overflow: hidden;
}

.sup-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
}

@media (max-width: 1100px) {
  .sup-layout {
    // Narrower side panels
  }
}

@media (max-width: 900px) {
  .sup-layout {
    // Hide right panel
  }
}
</style>
