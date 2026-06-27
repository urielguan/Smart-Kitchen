<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useCookStore } from '@/stores/modules/cook'
import { useAppStore } from '@/stores/modules/app'
import { useAlertNotification } from '@/composables/useAlertNotification'
import { getCurrentMealPeriod } from '@/utils/meal-period'
import type { CookTask } from '@/types'
import KdsTopBar from '@/components/business/kds/KdsTopBar.vue'
import KdsTaskList from '@/components/business/kds/KdsTaskList.vue'
import KdsTaskDetail from '@/components/business/kds/KdsTaskDetail.vue'
import OfflineIndicator from '@/components/business/shared/OfflineIndicator.vue'

const route = useRoute()
const cookStore = useCookStore()
const appStore = useAppStore()

const selectedTaskId = computed(() => cookStore.currentTask?.id ?? null)
const currentTaskActionState = computed(() => cookStore.currentTaskActionState)

// P2-14: 厨师视图告警全覆盖
const alerts = computed(() =>
  cookStore.list.filter(
    (t) => t.temperatureAbnormal || t.aiViolationCount > 0 || t.deviceOnline === false || t.sensorOnline === false
  )
)
const { requestPermission } = useAlertNotification(alerts)

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
  // P1-12: 厨师视图也加载组织列表
  try {
    await cookStore.fetchOrgList()
    await cookStore.refresh()
  } catch {
    // 初始加载失败不阻塞页面，自动刷新会重试
  }
  cookStore.startAutoRefresh()
  requestPermission()
})

onUnmounted(() => {
  cookStore.stopAutoRefresh()
})
</script>

<template>
  <div class="chef-app">
    <KdsTopBar />
    <OfflineIndicator prefix="chef" />
    <div class="chef-layout">
      <KdsTaskList
        :tasks="cookStore.list"
        :selected-id="selectedTaskId"
        :loading="cookStore.loading"
        :total="cookStore.total"
        :page-num="cookStore.pageNum"
        :page-size="cookStore.pageSize"
        @select="handleSelectTask"
        @update:page-num="cookStore.changePage"
        @update:page-size="cookStore.changePageSize"
      />
      <KdsTaskDetail
        :task="cookStore.currentTask"
        :start-action-state="currentTaskActionState.start"
        :complete-action-state="currentTaskActionState.complete"
        :action-loading="cookStore.actionLoadingId !== null"
        @start="handleStart"
        @complete="handleComplete"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.chef-app {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $kds-bg;
  color: $kds-text;
  overflow: hidden;
}

.chef-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
}

@media (max-width: 700px) {
  .chef-layout {
    position: relative;
  }
}
</style>
