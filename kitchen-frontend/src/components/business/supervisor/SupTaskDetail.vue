<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import type { CookTask, CookTaskDetail, CookTaskActionState, ChefOption } from '@/types'
import { formatMealType, formatDateTime } from '@/utils/format'
import { useTempChart } from '@/composables/useTempChart'
import { useTempDeviation } from '@/composables/useTempDeviation'
import { useCookStore } from '@/stores/modules/cook'
import TaskTimeline from '@/components/business/shared/TaskTimeline.vue'
import HlsVideoPlayer from '@/components/business/shared/HlsVideoPlayer.vue'

const props = defineProps<{
  task: CookTaskDetail | null
  startActionState: CookTaskActionState
  completeActionState: CookTaskActionState
  actionLoading?: boolean
}>()

const emit = defineEmits<{
  start: [remark?: string]
  complete: []
  reassign: []
  cancel: []
  confirmTempAbnormal: []
  archive: []
}>()

const chartCanvas = ref<HTMLCanvasElement | null>(null)
const tempData = ref<number[]>([])
const logExpanded = ref(true)
const targetTempRef = computed(() => props.task?.targetTemp ?? null)
const targetTempMinRef = computed(() => props.task?.targetTempMin ?? null)
const targetTempMaxRef = computed(() => props.task?.targetTempMax ?? null)
const hasTempRange = computed(() => props.task?.targetTempMin != null && props.task?.targetTempMax != null)
const { draw } = useTempChart(chartCanvas, tempData, targetTempRef, targetTempMinRef, targetTempMaxRef)

const { deviation, tempExtremes } = useTempDeviation(
  () => props.task?.currentTemp,
  () => props.task?.targetTemp,
  () => props.task?.targetTempMin,
  () => props.task?.targetTempMax
)

const extremes = computed(() => tempExtremes(props.task?.temperatureRecords))

const videoSrc = computed(() => {
  if (!props.task?.deviceId) return ''
  return `/hls/${props.task.deviceId}/stream.m3u8`
})

const steps = computed(() => {
  if (!props.task?.cookingSteps) return []
  return props.task.cookingSteps.split('\n').filter((s) => s.trim())
})

const ingredients = computed(() => props.task?.ingredients || [])

const isCooking = computed(() => props.task?.status === 'in_progress')
const isDone = computed(() => props.task?.status === 'completed' || props.task?.status === 'archived')
const isAlert = computed(() => props.task?.temperatureAbnormal || props.task?.sensorOnline === false || props.task?.deviceOnline === false || (props.task?.aiViolationCount ?? 0) > 0)
const canArchiveTask = computed(() => props.task ? cookStore.canArchiveTask(props.task) : false)

const tempOK = computed(() => {
  if (!props.task || props.task.currentTemp == null) return true
  const temp = props.task.currentTemp
  if (hasTempRange.value) {
    return temp >= (props.task!.targetTempMin!) && temp <= (props.task!.targetTempMax!)
  }
  if (props.task.targetTemp != null) {
    return temp >= props.task.targetTemp
  }
  return true
})

const threshTop = computed(() => {
  const refTemp = hasTempRange.value ? props.task!.targetTempMax : props.task?.targetTemp
  if (!refTemp || !tempData.value.length) return '50%'
  const max = Math.max(...tempData.value, refTemp) + 10
  const pct = ((max - refTemp) / max) * 100
  return `${pct}%`
})

const sampleLabel = computed(() => {
  if (!props.task) return ''
  if (props.task.handoffStatus === 'completed') return 'done'
  if (isDone.value) return 'pending'
  return ''
})

/** 已用时长实时计时（精确到秒） */
const elapsedText = ref<string>('--')
let elapsedTimer: ReturnType<typeof setInterval> | null = null

const formatElapsed = (totalSeconds: number): string => {
  const h = Math.floor(totalSeconds / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = totalSeconds % 60
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${m}:${String(s).padStart(2, '0')}`
}

const tickElapsed = () => {
  if (!props.task?.startTime || !isCooking.value) {
    if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
    return
  }
  const start = new Date(props.task.startTime).getTime()
  const totalSeconds = Math.floor((Date.now() - start) / 1000)
  elapsedText.value = formatElapsed(totalSeconds)
}

watch(() => props.task?.id, () => {
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
  if (props.task?.status === 'completed' || props.task?.status === 'archived') {
    const mins = props.task?.actualDuration ?? 0
    elapsedText.value = mins > 0 ? formatElapsed(mins * 60) : '--'
  } else if (props.task?.startTime && isCooking.value) {
    tickElapsed()
    elapsedTimer = setInterval(tickElapsed, 1000)
  } else {
    elapsedText.value = '--'
  }
})

watch(() => props.task?.status, (status) => {
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
  if (status === 'completed' || status === 'archived') {
    const mins = props.task?.actualDuration ?? 0
    elapsedText.value = mins > 0 ? formatElapsed(mins * 60) : '--'
  } else if (status === 'in_progress' && props.task?.startTime) {
    tickElapsed()
    elapsedTimer = setInterval(tickElapsed, 1000)
  } else {
    elapsedText.value = '--'
  }
})

const updateTempData = () => {
  if (!props.task?.temperatureRecords?.length) {
    tempData.value = []
    return
  }
  tempData.value = props.task.temperatureRecords
    .map((r) => r.temperature)
    .filter((t): t is number => t !== null)
  requestAnimationFrame(draw)
}

watch(() => props.task?.id, () => updateTempData())
watch(() => props.task?.temperatureRecords?.length, () => updateTempData())

onMounted(() => updateTempData())

// Temperature incremental refresh (30s interval)
const cookStore = useCookStore()

// Chef assignment
const showAssignDropdown = ref(false)
const chefSearchQuery = ref('')
const assignPanelRef = ref<HTMLElement | null>(null)

const canAssign = computed(() => props.task ? cookStore.canAssignChef(props.task) : false)

const filteredChefList = computed(() => {
  const q = chefSearchQuery.value.trim().toLowerCase()
  if (!q) return cookStore.chefList
  return cookStore.chefList.filter(c =>
    c.name.toLowerCase().includes(q) || (c.position?.toLowerCase().includes(q))
  )
})

const toggleAssign = () => {
  showAssignDropdown.value = !showAssignDropdown.value
  if (showAssignDropdown.value && cookStore.chefList.length === 0) {
    cookStore.fetchChefList()
  }
}

const handleAssignChef = async (chef: ChefOption) => {
  if (!props.task?.id) return
  showAssignDropdown.value = false
  chefSearchQuery.value = ''
  await cookStore.assignChefAction(props.task.id, { chefId: chef.id, chefName: chef.name })
}

const handleClickOutside = (e: MouseEvent) => {
  if (showAssignDropdown.value && assignPanelRef.value && !assignPanelRef.value.contains(e.target as Node)) {
    showAssignDropdown.value = false
  }
}

let tempRefreshTimer: ReturnType<typeof setInterval> | null = null

watch(() => props.task?.id, (newId) => {
  if (tempRefreshTimer) { clearInterval(tempRefreshTimer); tempRefreshTimer = null }
  if (newId && props.task?.status === 'in_progress') {
    tempRefreshTimer = setInterval(() => cookStore.refreshTaskTemperature(), 30000)
  }
  showAssignDropdown.value = false
  chefSearchQuery.value = ''
})

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => {
  if (tempRefreshTimer) { clearInterval(tempRefreshTimer) }
  if (elapsedTimer) { clearInterval(elapsedTimer) }
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <section class="sup-detail">
    <template v-if="task">
      <!-- Header -->
      <div class="sup-detail__header">
        <div class="sup-detail__name">{{ task.menuName }}</div>
        <div class="sup-detail__row">
          <div class="sup-detail__tag">
            <span class="sup-detail__tag-icon">📋</span>
            <span class="sup-detail__tag-val">{{ task.taskNo }}</span>
          </div>
          <div class="sup-detail__tag">
            <span class="sup-detail__tag-icon">📍</span>
            <span class="sup-detail__tag-val">{{ task.deviceName || '--' }}</span>
          </div>
          <div class="sup-detail__tag" :class="{ 'sup-detail__tag--unassigned': !task.chefName && !task.assignedChefName }">
            <span class="sup-detail__tag-icon">👨‍🍳</span>
            <span class="sup-detail__tag-val" v-if="task.chefName || task.assignedChefName">
              {{ task.chefName || task.assignedChefName }}
            </span>
            <span class="sup-detail__tag-val sup-detail__tag-val--placeholder" v-else>待分派</span>
            <button v-if="canAssign" class="sup-detail__assign-btn" :disabled="actionLoading" @click.stop="toggleAssign">
              {{ (task.chefName || task.assignedChefName) ? '更换' : '分派' }}
            </button>
          </div>
          <div class="sup-detail__tag">
            <span class="sup-detail__tag-icon">⏱</span>
            <span class="sup-detail__tag-val">{{ task.standardDuration ?? '--' }}min</span>
          </div>
          <div v-if="task.targetTemp || hasTempRange" class="sup-detail__tag">
            <span class="sup-detail__tag-icon">🌡</span>
            <span class="sup-detail__tag-val">
              <template v-if="hasTempRange">{{ task.targetTempMin }}~{{ task.targetTempMax }}°C</template>
              <template v-else>≥{{ task.targetTemp }}°C</template>
            </span>
          </div>
        </div>
      </div>
      <div class="sup-detail__body">
        <!-- Steps -->
        <div class="sup-detail__sec" v-if="steps.length">
          <div class="sup-detail__sec-title">烹饪步骤</div>
          <div class="sup-detail__steps">
            <div
              v-for="(step, index) in steps"
              :key="index"
              class="sup-detail__step"
              :class="{
                'sup-detail__step--done': isDone,
                'sup-detail__step--current': isCooking && index === steps.length - 1
              }"
            >
              <span class="sup-detail__step-n">{{ index + 1 }}</span>
              <span>{{ step }}</span>
            </div>
          </div>
        </div>

        <!-- Live data -->
        <div v-if="isCooking || isDone || isAlert" class="sup-detail__live">
          <div class="sup-detail__live-card">
            <div class="sup-detail__live-label">已用时长</div>
            <div class="sup-detail__live-val" :class="{ 'sup-detail__live-val--info': isDone }">
              {{ isCooking ? elapsedText : (task.actualDuration != null ? task.actualDuration + 'min' : '--') }}
            </div>
          </div>
          <div class="sup-detail__live-card">
            <div class="sup-detail__live-label">{{ (task.targetTemp || hasTempRange) ? '实时温度' : '类型' }}</div>
            <div class="sup-detail__live-val" :class="tempOK ? 'sup-detail__live-val--ok' : 'sup-detail__live-val--bad'">
              {{ task.currentTemp ?? '--' }}<span v-if="task.currentTemp !== null" class="sup-detail__live-unit">℃</span>
            </div>
            <div v-if="(task.targetTemp || hasTempRange) && deviation.level !== 'none'" class="sup-detail__temp-deviation" :class="`sup-detail__temp-deviation--${deviation.level}`">
              偏差 {{ deviation.label }}
            </div>
          </div>
          <div class="sup-detail__live-card">
            <div class="sup-detail__live-label">状态</div>
            <div class="sup-detail__live-val" :class="{
              'sup-detail__live-val--bad': isAlert,
              'sup-detail__live-val--info': isDone,
              'sup-detail__live-val--ok': isCooking && !isAlert
            }">
              {{ isAlert ? '⚠️异常' : isDone ? '已完成' : '进行中' }}
            </div>
          </div>
        </div>

        <!-- Temperature chart -->
        <div v-if="tempData.length > 1 && (task.targetTemp || hasTempRange)" class="sup-detail__sec">
          <div class="sup-detail__sec-title">温度曲线</div>
          <div class="sup-detail__chart">
            <canvas ref="chartCanvas" class="sup-detail__canvas" />
            <div class="sup-detail__thresh-line" :style="{ top: threshTop }">
              <span class="sup-detail__thresh-lbl">
                <template v-if="hasTempRange">{{ task.targetTempMin }}~{{ task.targetTempMax }}℃</template>
                <template v-else>阈值 {{ task.targetTemp }}℃</template>
              </span>
            </div>
          </div>
          <div v-if="extremes.max !== null || extremes.min !== null" class="sup-detail__temp-extremes">
            <span class="sup-detail__temp-extremes-tag sup-detail__temp-extremes-tag--max">最高 {{ extremes.max }}℃</span>
            <span class="sup-detail__temp-extremes-tag sup-detail__temp-extremes-tag--min">最低 {{ extremes.min }}℃</span>
          </div>
        </div>

        <!-- Live video feed (P0-7) -->
        <div v-if="task.deviceId" class="sup-detail__sec">
          <div class="sup-detail__sec-title">实时监控</div>
          <HlsVideoPlayer :src="videoSrc" :title="task.deviceName || `设备 ${task.deviceId}`" />
        </div>

        <!-- Ingredients -->
        <div class="sup-detail__sec" v-if="ingredients.length">
          <div class="sup-detail__sec-title">
            食材清单
            <span v-if="task.outboundOrderNo" class="sup-detail__trace-order">出库单: {{ task.outboundOrderNo }}</span>
          </div>
          <div class="sup-detail__ing-grid">
            <div v-for="item in ingredients" :key="`${item.materialId}-${item.materialName}`" class="sup-detail__ing">
              <div class="sup-detail__ing-row">
                <span class="sup-detail__ing-name">{{ item.materialName }}</span>
                <span class="sup-detail__ing-qty">{{ item.quantity ?? '--' }} {{ item.unit || '' }}</span>
              </div>
              <div v-if="item.batchNo || item.traceBatchId" class="sup-detail__trace-row">
                <span v-if="item.batchNo" class="sup-detail__trace-tag">批次: {{ item.batchNo }}</span>
                <span v-if="item.traceBatchId" class="sup-detail__trace-tag sup-detail__trace-tag--link">追溯: {{ item.traceBatchId }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- AI Monitor Alerts -->
        <div class="sup-detail__sec" v-if="task.aiMonitorRecords?.length">
          <div class="sup-detail__sec-title">AI 预警记录</div>
          <div class="sup-detail__alert-list">
            <div
              v-for="(rec, idx) in task.aiMonitorRecords"
              :key="`${rec.snapshotTime}-${idx}`"
              class="sup-detail__alert-item"
              :class="{
                'sup-detail__alert-item--critical': rec.level === 'critical',
                'sup-detail__alert-item--warning': rec.level === 'warning'
              }"
            >
              <div class="sup-detail__alert-head">
                <span class="sup-detail__alert-name">{{ rec.violationName || rec.violationType || 'AI 预警' }}</span>
                <span v-if="rec.level" class="sup-detail__alert-level">{{ rec.level }}</span>
              </div>
              <div v-if="rec.description" class="sup-detail__alert-desc">{{ rec.description }}</div>
              <div v-if="rec.suggestion" class="sup-detail__alert-suggestion">{{ rec.suggestion }}</div>
              <div class="sup-detail__alert-footer">
                <span class="sup-detail__alert-time">{{ rec.snapshotTime || '--' }}</span>
                <button
                  v-if="!rec.acknowledged"
                  class="sup-detail__alert-ack-btn"
                  :disabled="actionLoading"
                  @click="cookStore.acknowledgeAlert(task.id!, rec.alertIndex ?? idx)"
                >确认</button>
                <span v-else class="sup-detail__alert-acked">
                  已确认<span v-if="rec.acknowledgedBy"> · {{ rec.acknowledgedBy }}</span><span v-if="rec.acknowledgedAt"> · {{ rec.acknowledgedAt }}</span>
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- P2-13: Operation log independent panel -->
        <div class="sup-detail__log-panel" v-if="cookStore.timelineEvents.length">
          <div class="sup-detail__log-header" @click="logExpanded = !logExpanded">
            <span class="sup-detail__log-title">操作日志</span>
            <span class="sup-detail__log-count">{{ cookStore.timelineEvents.length }} 条</span>
            <span class="sup-detail__log-toggle">{{ logExpanded ? '收起 ▲' : '展开 ▼' }}</span>
          </div>
          <div class="sup-detail__log-body" v-show="logExpanded">
            <TaskTimeline :events="cookStore.timelineEvents" prefix="sup-detail" />
          </div>
        </div>

        <!-- Sample tag -->
        <div v-if="sampleLabel" class="sup-detail__sample">
          <div v-if="sampleLabel === 'done'" class="sup-detail__sample-tag sup-detail__sample-tag--done">
            ✅ 留样已完成
          </div>
          <div v-else class="sup-detail__sample-tag sup-detail__sample-tag--pending">
            📋 留样任务已自动生成
          </div>
        </div>

        <!-- Food safety pass tag (P0-5) -->
        <div v-if="isDone && task.foodSafetyPass != null" class="sup-detail__food-safety" :class="task.foodSafetyPass ? 'sup-detail__food-safety--pass' : 'sup-detail__food-safety--fail'">
          {{ task.foodSafetyPass ? '✅ 食安达标' : '⚠️ 食安不达标' }}
        </div>
      </div>

      <!-- Review bar -->
      <div v-if="!cookStore.isHistorical" class="sup-detail__review">
        <!-- Temperature abnormal confirm (P0-6) -->
        <div v-if="task.temperatureAbnormal && !task.tempAbnormalConfirmed" class="sup-detail__temp-confirm">
          <span class="sup-detail__temp-confirm-text">⚠️ 温度异常待确认</span>
          <button
            class="sup-detail__rbtn sup-detail__rbtn--confirm-temp"
            :disabled="actionLoading"
            @click="emit('confirmTempAbnormal')"
          >确认温度异常</button>
        </div>
        <div v-else-if="task.temperatureAbnormal && task.tempAbnormalConfirmed" class="sup-detail__temp-confirmed">
          ✅ 温度异常已确认
        </div>
        <template v-if="task.status === 'completed'">
          <button v-if="canArchiveTask" class="sup-detail__rbtn sup-detail__rbtn--archive" :disabled="actionLoading" @click="emit('archive')">
            📦 归档
          </button>
        </template>
        <template v-if="task.status === 'archived'">
          <div class="sup-detail__archive-tag">📦 已归档</div>
        </template>
        <template v-if="canAssign">
          <button class="sup-detail__rbtn sup-detail__rbtn--reassign" @click.stop="toggleAssign">
            🔄 {{ (task.assignedChefName || task.chefName) ? '更换厨师' : '分派厨师' }}
          </button>
        </template>
        <template v-if="task.status === 'pending' && !canAssign">
          <button class="sup-detail__rbtn sup-detail__rbtn--reject" @click="emit('cancel')">
            ❌ 取消任务
          </button>
        </template>
      </div>
      <div v-else class="sup-detail__readonly-bar">
        <span class="sup-detail__readonly-tag">历史数据 · 只读</span>
      </div>

      <!-- Chef assign dropdown (positioned at sup-detail level) -->
      <div v-if="showAssignDropdown && canAssign" class="sup-detail__assign-panel" ref="assignPanelRef">
        <div class="sup-detail__assign-search">
          <input type="text" class="sup-detail__assign-input" v-model="chefSearchQuery" placeholder="搜索厨师..." />
        </div>
        <div class="sup-detail__assign-list">
          <div v-for="chef in filteredChefList" :key="chef.id" class="sup-detail__assign-item"
               :class="{ 'sup-detail__assign-item--active': String(chef.id) === String(task?.assignedChefId) }"
               @click="handleAssignChef(chef)">
            <span class="sup-detail__assign-name">{{ chef.name }}</span>
            <span v-if="chef.position" class="sup-detail__assign-pos">{{ chef.position }}</span>
          </div>
          <div v-if="!filteredChefList.length" class="sup-detail__assign-empty">
            {{ cookStore.chefListLoading ? '加载中...' : '暂无可选厨师' }}
          </div>
        </div>
      </div>
    </template>

    <!-- Empty -->
    <div v-else class="sup-detail__empty">
      <div class="sup-detail__empty-icon">📋</div>
      <div class="sup-detail__empty-text">选择左侧任务查看详情</div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.sup-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: $kds-bg;
  position: relative;
}

.sup-detail__header {
  padding: 18px 22px 14px;
  border-bottom: 1px solid $kds-border;
  background: $kds-surface;
  position: relative;
}

.sup-detail__name {
  font-size: 24px;
  font-weight: 900;
  color: $kds-text-bright;
  margin-bottom: 6px;
}

.sup-detail__row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.sup-detail__tag {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: $kds-text-dim;
  padding: 3px 8px;
  background: $kds-surface-2;
  border-radius: 6px;
}

.sup-detail__tag-icon {
  font-size: 12px;
}

.sup-detail__tag-val {
  color: $kds-text;
  font-weight: 500;
}

.sup-detail__tag--warn {
  background: $kds-amber-dim;
  border-color: $kds-amber-border;
}
.sup-detail__tag--warn .sup-detail__tag-val {
  color: $kds-amber;
}

.sup-detail__tag--ok {
  background: $kds-green-dim;
  border-color: $kds-green-border;
}
.sup-detail__tag--ok .sup-detail__tag-val {
  color: $kds-green;
}

.sup-detail__body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 22px;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}

.sup-detail__sec {
  margin-bottom: 20px;
}

.sup-detail__sec-title {
  font-size: 11px;
  font-weight: 700;
  color: $kds-text-dim;
  margin-bottom: 10px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.sup-detail__steps {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.sup-detail__step {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px;
  background: $kds-surface-2;
  border-radius: $kds-radius-md;
  font-size: 12px;
  border: 1px solid $kds-border;
  color: $kds-text;
}

.sup-detail__step-n {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: $kds-surface-3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: $kds-text-dim;
  flex-shrink: 0;
}

.sup-detail__step--current .sup-detail__step-n {
  background: $kds-green;
  color: #fff;
}

.sup-detail__step--done .sup-detail__step-n {
  background: $kds-indigo;
  color: #fff;
}

.sup-detail__live {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  margin-bottom: 20px;
}

.sup-detail__live-card {
  background: $kds-surface-2;
  border-radius: $kds-radius-xl;
  padding: 14px;
  text-align: center;
  border: 1px solid $kds-border;
}

.sup-detail__live-label {
  font-size: 10px;
  color: $kds-text-dim;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.sup-detail__live-val {
  font-family: 'DM Mono', monospace;
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  color: $kds-text-bright;

  &--ok { color: $kds-green; }
  &--bad { color: $kds-red; }
  &--info { color: #4f8cff; }
}

.sup-detail__live-unit {
  font-size: 12px;
  color: $kds-text-dim;
  margin-left: 2px;
}

.sup-detail__temp-deviation {
  font-size: 11px;
  font-weight: 600;
  margin-top: 6px;
  padding: 2px 8px;
  border-radius: 6px;
  display: inline-block;

  &--ok { color: $kds-green; background: $kds-green-dim; }
  &--warn { color: $kds-amber; background: $kds-amber-dim; }
  &--bad { color: $kds-red; background: $kds-red-dim; }
}

.sup-detail__temp-extremes {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

.sup-detail__temp-extremes-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 6px;
  font-family: 'DM Mono', monospace;

  &--max { background: rgba(248, 113, 113, 0.1); color: $kds-red; }
  &--min { background: rgba(79, 140, 255, 0.1); color: #4f8cff; }
}

.sup-detail__chart {
  background: $kds-surface-2;
  border-radius: $kds-radius-xl;
  padding: 14px;
  border: 1px solid $kds-border;
  height: 160px;
  position: relative;
}

.sup-detail__canvas {
  width: 100% !important;
  height: 100% !important;
  display: block;
}

.sup-detail__thresh-line {
  position: absolute;
  left: 14px;
  right: 14px;
  border-top: 2px dashed $kds-red;
  opacity: 0.4;
  pointer-events: none;
}

.sup-detail__thresh-lbl {
  position: absolute;
  right: 4px;
  top: -9px;
  font-size: 9px;
  color: $kds-red;
  font-family: 'DM Mono', monospace;
  background: $kds-surface-2;
  padding: 0 3px;
}

.sup-detail__ing-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 6px;
}

.sup-detail__ing {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  background: $kds-surface-2;
  border-radius: $kds-radius-md;
  font-size: 12px;
  border: 1px solid $kds-border;
}

.sup-detail__ing-name {
  color: $kds-text-dim;
}

.sup-detail__ing-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sup-detail__ing-qty {
  font-family: 'DM Mono', monospace;
  font-weight: 500;
  color: $kds-text;
}

.sup-detail__trace-order {
  font-size: 10px;
  font-weight: 400;
  color: $kds-indigo;
  margin-left: 8px;
}

.sup-detail__trace-row {
  display: flex;
  gap: 6px;
  margin-top: 3px;
}

.sup-detail__trace-tag {
  font-size: 9px;
  padding: 1px 6px;
  border-radius: 4px;
  background: $kds-surface-3;
  color: $kds-text-muted;
  font-family: 'DM Mono', monospace;

  &--link {
    background: $kds-indigo-dim;
    color: $kds-indigo;
  }
}

.sup-detail__alert-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sup-detail__alert-item {
  padding: 12px 14px;
  background: $kds-red-dim;
  border-radius: $kds-radius-md;
  border: 1px solid $kds-red-border;
  display: flex;
  flex-direction: column;
  gap: 4px;

  &--warning {
    background: $kds-amber-dim;
    border-color: $kds-amber-border;
  }
}

.sup-detail__alert-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sup-detail__alert-name {
  font-size: 13px;
  font-weight: 600;
  color: $kds-text;
}

.sup-detail__alert-level {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  background: rgba(248, 113, 113, 0.2);
  color: $kds-red;
}

.sup-detail__alert-item--warning .sup-detail__alert-level {
  background: rgba(251, 191, 36, 0.2);
  color: $kds-amber;
}

.sup-detail__alert-desc {
  font-size: 12px;
  color: $kds-text-dim;
  line-height: 1.4;
}

.sup-detail__alert-suggestion {
  font-size: 11px;
  color: $kds-text-muted;
  font-style: italic;
}

.sup-detail__alert-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}

.sup-detail__alert-time {
  font-size: 10px;
  color: $kds-text-muted;
  font-family: 'DM Mono', monospace;
}

.sup-detail__alert-ack-btn {
  font-size: 10px;
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid $kds-indigo;
  background: transparent;
  color: $kds-indigo;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: $kds-indigo-dim;
  }
}

.sup-detail__alert-acked {
  font-size: 10px;
  color: $kds-green;
  font-weight: 500;
}

.sup-detail__sample {
  margin-top: 8px;
}

.sup-detail__sample-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: $kds-radius-md;
  font-size: 12px;
  font-weight: 500;

  &--done {
    background: $kds-green-dim;
    color: $kds-green;
    border: 1px solid $kds-green-border;
  }

  &--pending {
    background: $kds-amber-dim;
    color: $kds-amber;
    border: 1px solid $kds-amber-border;
  }
}

.sup-detail__review {
  display: flex;
  gap: 10px;
  padding: 14px 22px;
  border-top: 1px solid $kds-border;
  background: $kds-surface;
  flex-wrap: wrap;
}

.sup-detail__remark-row {
  width: 100%;
}

.sup-detail__remark-input {
  width: 100%;
  background: $kds-surface-2;
  border: 1px solid $kds-border;
  border-radius: $kds-radius-sm;
  color: $kds-text;
  font-size: 12px;
  padding: 8px 12px;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;
  margin-bottom: 4px;

  &::placeholder { color: $kds-text-muted; }
  &:focus { border-color: #4f8cff; }
}

.sup-detail__food-safety {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: $kds-radius-md;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 12px;

  &--pass { background: $kds-green-dim; color: $kds-green; border: 1px solid $kds-green-border; }
  &--fail { background: $kds-red-dim; color: $kds-red; border: 1px solid $kds-red-border; }
}

.sup-detail__temp-confirm {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px 0;
}

.sup-detail__temp-confirm-text {
  font-size: 13px;
  font-weight: 600;
  color: $kds-red;
}

.sup-detail__rbtn--confirm-temp {
  background: rgba(248, 113, 113, 0.15);
  border-color: $kds-red-border;
  color: $kds-red;
}

.sup-detail__temp-confirmed {
  width: 100%;
  padding: 8px 0;
  font-size: 12px;
  font-weight: 500;
  color: $kds-green;
}

.sup-detail__rbtn {
  flex: 1;
  padding: 12px;
  border-radius: $kds-radius-md;
  font-size: 13px;
  font-weight: 700;
  border: 1px solid $kds-border;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s;
  cursor: pointer;
  background: $kds-surface-2;
  color: $kds-text;
  font-family: inherit;

  &--approve {
    background: $kds-green-dim;
    color: $kds-green;
    border-color: $kds-green-border;
    &:hover { background: $kds-green; color: #fff; }
  }

  &--reject {
    background: $kds-red-dim;
    color: $kds-red;
    border-color: $kds-red-border;
    &:hover { background: $kds-red; color: #fff; }
  }

  &--reassign {
    background: $kds-amber-dim;
    color: $kds-amber;
    border-color: $kds-amber-border;
    &:hover { background: $kds-amber; color: #fff; }
  }

  &--reviewed {
    text-align: center;
    font-size: 13px;
    color: $kds-green;
    font-weight: 600;
    cursor: default;
    border: none;
    background: none;
  }

  &--archive {
    background: $kds-indigo-dim;
    color: $kds-indigo;
    border-color: $kds-indigo-border;
    &:hover { background: $kds-indigo; color: #fff; }
  }
}

.sup-detail__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.sup-detail__empty-icon {
  font-size: 48px;
  opacity: 0.3;
}

.sup-detail__empty-text {
  font-size: 14px;
  color: $kds-text-muted;
}

.sup-detail__readonly-bar {
  display: flex;
  justify-content: center;
  padding: 14px 22px;
  border-top: 1px solid $kds-border;
  background: $kds-surface;
}

.sup-detail__readonly-tag {
  font-size: 12px;
  font-weight: 600;
  color: $kds-amber;
  padding: 4px 14px;
  background: $kds-amber-dim;
  border: 1px solid $kds-amber-border;
  border-radius: 14px;
}

.sup-detail__archive-tag {
  font-size: 13px;
  font-weight: 700;
  color: $kds-indigo;
  padding: 4px 14px;
  background: $kds-indigo-dim;
  border: 1px solid $kds-indigo-border;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

// P2-13: Operation log collapsible panel
.sup-detail__log-panel {
  border-top: 1px solid $kds-border;
  margin-top: 12px;
}

.sup-detail__log-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  cursor: pointer;
  user-select: none;

  &:hover .sup-detail__log-title {
    color: $kds-text-bright;
  }
}

.sup-detail__log-title {
  font-size: 13px;
  font-weight: 700;
  color: $kds-text;
  transition: color 0.15s;
}

.sup-detail__log-count {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 10px;
  background: $kds-surface-3;
  color: $kds-text-dim;
  font-family: 'DM Mono', monospace;
}

.sup-detail__log-toggle {
  margin-left: auto;
  font-size: 11px;
  color: $kds-text-muted;
  transition: color 0.15s;
}

.sup-detail__log-body {
  max-height: 300px;
  overflow-y: auto;
  padding-bottom: 8px;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}

// Chef assignment
.sup-detail__tag--unassigned {
  border: 1px dashed $kds-amber-border;
}

.sup-detail__tag-val--placeholder {
  color: $kds-amber;
  font-style: italic;
}

.sup-detail__assign-btn {
  font-size: 10px;
  padding: 1px 8px;
  border-radius: 10px;
  border: 1px solid $kds-indigo;
  background: transparent;
  color: $kds-indigo;
  cursor: pointer;
  margin-left: 4px;
  transition: all 0.15s;
  white-space: nowrap;
  font-family: inherit;

  &:hover { background: $kds-indigo-dim; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.sup-detail__assign-panel {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 60px;
  width: 260px;
  max-height: 320px;
  background: $kds-surface-2;
  border: 1px solid $kds-border;
  border-radius: $kds-radius-md;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sup-detail__assign-search {
  padding: 8px;
  border-bottom: 1px solid $kds-border;
}

.sup-detail__assign-input {
  width: 100%;
  background: $kds-surface-3;
  border: 1px solid $kds-border;
  border-radius: 4px;
  color: $kds-text;
  font-size: 12px;
  padding: 6px 10px;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;

  &::placeholder { color: $kds-text-muted; }
  &:focus { border-color: #4f8cff; }
}

.sup-detail__assign-list {
  overflow-y: auto;
  max-height: 260px;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}

.sup-detail__assign-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.1s;

  &:hover { background: $kds-surface-3; }

  &--active {
    background: $kds-indigo-dim;
    color: $kds-indigo;
  }
}

.sup-detail__assign-name {
  font-size: 13px;
  font-weight: 500;
  color: $kds-text;
}

.sup-detail__assign-pos {
  font-size: 10px;
  color: $kds-text-muted;
}

.sup-detail__assign-empty {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: $kds-text-muted;
}
</style>
