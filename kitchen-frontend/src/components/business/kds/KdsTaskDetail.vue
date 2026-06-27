<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import type { CookTaskDetail, CookTaskActionState } from '@/types'
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
  start: []
  complete: []
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

const showDoneOverlay = ref(false)

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

const steps = computed(() => {
  if (!props.task?.cookingSteps) return []
  return props.task.cookingSteps.split('\n').filter((s) => s.trim())
})

const ingredients = computed(() => props.task?.ingredients || [])

const isPending = computed(() => props.task?.status === 'pending')
const isCooking = computed(() => props.task?.status === 'in_progress')
const isDone = computed(() => props.task?.status === 'completed' || props.task?.status === 'archived')
const isAlert = computed(() => props.task?.temperatureAbnormal || (props.task?.aiViolationCount ?? 0) > 0)

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

const sampleLabel = computed(() => {
  if (!props.task) return ''
  if (props.task.handoffStatus === 'completed') return 'done'
  if (isDone.value) return 'pending'
  return ''
})

const threshTop = computed(() => {
  const refTemp = hasTempRange.value ? props.task!.targetTempMax : props.task?.targetTemp
  if (!refTemp || !tempData.value.length) return '50%'
  const max = Math.max(...tempData.value, refTemp) + 10
  const pct = ((max - refTemp) / max) * 100
  return `${pct}%`
})

const handleComplete = () => {
  emit('complete')
  showDoneOverlay.value = true
  setTimeout(() => { showDoneOverlay.value = false }, 2500)
}

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
let tempRefreshTimer: ReturnType<typeof setInterval> | null = null

watch(() => props.task?.id, (newId) => {
  if (tempRefreshTimer) { clearInterval(tempRefreshTimer); tempRefreshTimer = null }
  if (newId && props.task?.status === 'in_progress') {
    tempRefreshTimer = setInterval(() => cookStore.refreshTaskTemperature(), 30000)
  }
})

onUnmounted(() => {
  if (tempRefreshTimer) { clearInterval(tempRefreshTimer) }
  if (elapsedTimer) { clearInterval(elapsedTimer) }
})
</script>

<template>
  <section class="chef-detail">
    <template v-if="task">
      <!-- Header -->
      <div class="chef-detail__header">
        <div class="chef-detail__dish">{{ task.menuName }}</div>
        <div class="chef-detail__meta">
          <div class="chef-detail__mt">
            <span>📋</span>
            <span class="chef-detail__mt-val">{{ task.taskNo }}</span>
          </div>
          <div class="chef-detail__mt">
            <span>📍</span>
            <span class="chef-detail__mt-val">{{ task.deviceName || '--' }}</span>
          </div>
          <div class="chef-detail__mt">
            <span>⏱</span>
            <span class="chef-detail__mt-val">标准 {{ task.standardDuration ?? '--' }}min</span>
          </div>
          <div v-if="task.targetTemp || hasTempRange" class="chef-detail__mt">
            <span>🌡</span>
            <span class="chef-detail__mt-val">
              <template v-if="hasTempRange">{{ task.targetTempMin }}~{{ task.targetTempMax }}°C</template>
              <template v-else>≥{{ task.targetTemp }}°C</template>
            </span>
          </div>
        </div>
      </div>

      <!-- Body -->
      <div class="chef-detail__body">
        <!-- Steps -->
        <div class="chef-detail__sec" v-if="steps.length">
          <div class="chef-detail__sec-title">烹饪步骤</div>
          <div class="chef-detail__steps">
            <div
              v-for="(step, index) in steps"
              :key="index"
              class="chef-detail__step"
              :class="{
                'chef-detail__step--done': isDone,
                'chef-detail__step--current': isCooking && index === steps.length - 1
              }"
            >
              <span class="chef-detail__step-n">{{ index + 1 }}</span>
              <span>{{ step }}</span>
            </div>
          </div>
        </div>

        <!-- Live data - 2 columns -->
        <div v-if="isCooking || isDone || isAlert" class="chef-detail__live">
          <div class="chef-detail__live-card">
            <div class="chef-detail__live-label">已用时长</div>
            <div class="chef-detail__live-val" :class="{ 'chef-detail__live-val--info': isDone }">
              {{ isCooking ? elapsedText : (task.actualDuration != null ? task.actualDuration + 'min' : '--') }}
            </div>
          </div>
          <div class="chef-detail__live-card">
            <div class="chef-detail__live-label">{{ (task.targetTemp || hasTempRange) ? '实时温度' : '类型' }}</div>
            <div class="chef-detail__live-val" :class="tempOK ? 'chef-detail__live-val--ok' : 'chef-detail__live-val--bad'">
              {{ task.currentTemp ?? '--' }}<span v-if="task.currentTemp !== null" class="chef-detail__live-unit">℃</span>
            </div>
            <div v-if="(task.targetTemp || hasTempRange) && deviation.level !== 'none'" class="chef-detail__temp-deviation" :class="`chef-detail__temp-deviation--${deviation.level}`">
              偏差 {{ deviation.label }}
            </div>
          </div>
        </div>

        <!-- Temperature chart -->
        <div v-if="tempData.length > 1 && (task.targetTemp || hasTempRange)" class="chef-detail__sec">
          <div class="chef-detail__sec-title">温度曲线</div>
          <div class="chef-detail__chart">
            <canvas ref="chartCanvas" class="chef-detail__canvas" />
            <div class="chef-detail__thresh-line" :style="{ top: threshTop }">
              <span class="chef-detail__thresh-lbl">
                <template v-if="hasTempRange">{{ task.targetTempMin }}~{{ task.targetTempMax }}℃</template>
                <template v-else>阈值 {{ task.targetTemp }}℃</template>
              </span>
            </div>
          </div>
          <div v-if="extremes.max !== null || extremes.min !== null" class="chef-detail__temp-extremes">
            <span class="chef-detail__temp-extremes-tag chef-detail__temp-extremes-tag--max">最高 {{ extremes.max }}℃</span>
            <span class="chef-detail__temp-extremes-tag chef-detail__temp-extremes-tag--min">最低 {{ extremes.min }}℃</span>
          </div>
        </div>

        <!-- Live video feed (P0-7) -->
        <div v-if="task.deviceId" class="chef-detail__sec">
          <div class="chef-detail__sec-title">实时监控</div>
          <HlsVideoPlayer :src="videoSrc" :title="task.deviceName || `设备 ${task.deviceId}`" />
        </div>

        <!-- Ingredients -->
        <div class="chef-detail__sec" v-if="ingredients.length">
          <div class="chef-detail__sec-title">
            食材清单
            <span v-if="task.outboundOrderNo" class="chef-detail__trace-order">出库单: {{ task.outboundOrderNo }}</span>
          </div>
          <div class="chef-detail__ing-grid">
            <div v-for="item in ingredients" :key="`${item.materialId}-${item.materialName}`" class="chef-detail__ing">
              <div class="chef-detail__ing-row">
                <span class="chef-detail__ing-name">{{ item.materialName }}</span>
                <span class="chef-detail__ing-qty">{{ item.quantity ?? '--' }} {{ item.unit || '' }}</span>
              </div>
              <div v-if="item.batchNo || item.traceBatchId" class="chef-detail__trace-row">
                <span v-if="item.batchNo" class="chef-detail__trace-tag">批次: {{ item.batchNo }}</span>
                <span v-if="item.traceBatchId" class="chef-detail__trace-tag chef-detail__trace-tag--link">追溯: {{ item.traceBatchId }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- AI Monitor Alerts -->
        <div class="chef-detail__sec" v-if="task.aiMonitorRecords?.length">
          <div class="chef-detail__sec-title">AI 预警记录</div>
          <div class="chef-detail__alert-list">
            <div
              v-for="(rec, idx) in task.aiMonitorRecords"
              :key="`${rec.snapshotTime}-${idx}`"
              class="chef-detail__alert-item"
              :class="{
                'chef-detail__alert-item--critical': rec.level === 'critical',
                'chef-detail__alert-item--warning': rec.level === 'warning'
              }"
            >
              <div class="chef-detail__alert-head">
                <span class="chef-detail__alert-name">{{ rec.violationName || rec.violationType || 'AI 预警' }}</span>
                <span v-if="rec.level" class="chef-detail__alert-level">{{ rec.level }}</span>
              </div>
              <div v-if="rec.description" class="chef-detail__alert-desc">{{ rec.description }}</div>
              <div v-if="rec.suggestion" class="chef-detail__alert-suggestion">{{ rec.suggestion }}</div>
              <div class="chef-detail__alert-footer">
                <span class="chef-detail__alert-time">{{ rec.snapshotTime || '--' }}</span>
                <button
                  v-if="!rec.acknowledged"
                  class="chef-detail__alert-ack-btn"
                  :disabled="actionLoading"
                  @click="cookStore.acknowledgeAlert(task.id!, rec.alertIndex ?? idx)"
                >确认</button>
                <span v-else class="chef-detail__alert-acked">
                  已确认<span v-if="rec.acknowledgedBy"> · {{ rec.acknowledgedBy }}</span><span v-if="rec.acknowledgedAt"> · {{ rec.acknowledgedAt }}</span>
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- P2-13: Operation log independent panel -->
        <div class="chef-detail__log-panel" v-if="cookStore.timelineEvents.length">
          <div class="chef-detail__log-header" @click="logExpanded = !logExpanded">
            <span class="chef-detail__log-title">操作日志</span>
            <span class="chef-detail__log-count">{{ cookStore.timelineEvents.length }} 条</span>
            <span class="chef-detail__log-toggle">{{ logExpanded ? '收起 ▲' : '展开 ▼' }}</span>
          </div>
          <div class="chef-detail__log-body" v-show="logExpanded">
            <TaskTimeline :events="cookStore.timelineEvents" prefix="chef-detail" />
          </div>
        </div>

        <!-- Sample tag -->
        <div v-if="sampleLabel" class="chef-detail__sample">
          <div v-if="sampleLabel === 'done'" class="chef-detail__sample-tag chef-detail__sample-tag--done">
            ✅ 留样已完成
          </div>
          <div v-else class="chef-detail__sample-tag chef-detail__sample-tag--pending">
            📋 留样任务已自动生成
          </div>
        </div>

        <!-- Food safety pass tag (P0-5) -->
        <div v-if="isDone && task.foodSafetyPass != null" class="chef-detail__food-safety" :class="task.foodSafetyPass ? 'chef-detail__food-safety--pass' : 'chef-detail__food-safety--fail'">
          {{ task.foodSafetyPass ? '✅ 食安达标' : '⚠️ 食安不达标' }}
        </div>

        <!-- Temperature abnormal confirm banner (P0-6) -->
        <div v-if="task.temperatureAbnormal && !task.tempAbnormalConfirmed" class="chef-detail__temp-abnormal-banner">
          ⚠️ 温度异常 — 等待主管确认
        </div>
        <div v-if="task.temperatureAbnormal && task.tempAbnormalConfirmed" class="chef-detail__temp-abnormal-confirmed">
          ✅ 温度异常已由主管确认
        </div>

        <!-- Big action buttons -->
        <div class="chef-detail__action-area">
          <button
            v-if="startActionState.visible && !isAlert"
            class="chef-detail__big-btn chef-detail__big-btn--start"
            :disabled="!startActionState.enabled || actionLoading"
            @click="emit('start')"
          >
            🔥 开始烹饪
          </button>
          <button
            v-if="startActionState.visible && isAlert"
            class="chef-detail__big-btn chef-detail__big-btn--alert"
            :disabled="!startActionState.enabled || actionLoading"
            @click="emit('start')"
          >
            ⚡ 恢复烹饪 (温度异常)
          </button>
          <button
            v-if="completeActionState.visible"
            class="chef-detail__big-btn chef-detail__big-btn--finish"
            :disabled="!completeActionState.enabled || actionLoading"
            @click="handleComplete"
          >
            ✅ 烹饪完成
          </button>
          <div v-if="isDone" class="chef-detail__done-text">
            ✅ 烹饪已完成
          </div>
          <div
            v-if="startActionState.visible && !startActionState.enabled && startActionState.reason"
            class="chef-detail__action-reason"
          >
            {{ startActionState.reason }}
          </div>
          <div
            v-if="completeActionState.visible && !completeActionState.enabled && completeActionState.reason"
            class="chef-detail__action-reason"
          >
            {{ completeActionState.reason }}
          </div>
        </div>
      </div>

      <!-- Done overlay -->
      <div class="chef-detail__done-ov" :class="{ 'chef-detail__done-ov--show': showDoneOverlay }">
        <div class="chef-detail__done-circle">✓</div>
        <div class="chef-detail__done-ov-title">烹饪完成</div>
        <div class="chef-detail__done-ov-sub">留样任务已自动生成</div>
      </div>
    </template>

    <!-- Empty -->
    <div v-else class="chef-detail__empty">
      <div class="chef-detail__empty-icon">👨‍🍳</div>
      <div class="chef-detail__empty-text">当前餐次没有分配给您的任务</div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.chef-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  background: $kds-bg;
}

.chef-detail__header {
  padding: 22px 26px 16px;
  border-bottom: 1px solid $kds-border;
  background: $kds-surface;
}

.chef-detail__dish {
  font-size: 30px;
  font-weight: 900;
  color: $kds-text-bright;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.chef-detail__meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.chef-detail__mt {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: $kds-text-dim;
  padding: 3px 10px;
  background: $kds-surface-2;
  border-radius: 6px;
}

.chef-detail__mt-val {
  color: $kds-text;
  font-weight: 500;
}

.chef-detail__mt--warn {
  background: $kds-amber-dim;
  border-color: $kds-amber-border;
}
.chef-detail__mt--warn .chef-detail__mt-val {
  color: $kds-amber;
}

.chef-detail__mt--ok {
  background: $kds-green-dim;
  border-color: $kds-green-border;
}
.chef-detail__mt--ok .chef-detail__mt-val {
  color: $kds-green;
}

.chef-detail__body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 26px;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}

.chef-detail__sec {
  margin-bottom: 20px;
}

.chef-detail__sec-title {
  font-size: 11px;
  font-weight: 700;
  color: $kds-text-dim;
  margin-bottom: 10px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.chef-detail__steps {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chef-detail__step {
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

.chef-detail__step-n {
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

.chef-detail__step--current .chef-detail__step-n {
  background: $kds-green;
  color: #fff;
}

.chef-detail__step--done .chef-detail__step-n {
  background: $kds-indigo;
  color: #fff;
}

.chef-detail__live {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
}

.chef-detail__live-card {
  background: $kds-surface-2;
  border-radius: $kds-radius-xl;
  padding: 18px;
  text-align: center;
  border: 1px solid $kds-border;
}

.chef-detail__live-label {
  font-size: 11px;
  color: $kds-text-dim;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.chef-detail__live-val {
  font-family: 'DM Mono', monospace;
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
  color: $kds-text-bright;

  &--ok { color: $kds-green; }
  &--bad { color: $kds-red; }
  &--info { color: #4f8cff; }
}

.chef-detail__live-unit {
  font-size: 13px;
  color: $kds-text-dim;
  margin-left: 3px;
}

.chef-detail__temp-deviation {
  font-size: 12px;
  font-weight: 600;
  margin-top: 6px;
  padding: 2px 8px;
  border-radius: 6px;
  display: inline-block;

  &--ok { color: $kds-green; background: $kds-green-dim; }
  &--warn { color: $kds-amber; background: $kds-amber-dim; }
  &--bad { color: $kds-red; background: $kds-red-dim; }
}

.chef-detail__temp-extremes {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

.chef-detail__temp-extremes-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 6px;
  font-family: 'DM Mono', monospace;

  &--max { background: rgba(248, 113, 113, 0.1); color: $kds-red; }
  &--min { background: rgba(79, 140, 255, 0.1); color: #4f8cff; }
}

.chef-detail__chart {
  background: $kds-surface-2;
  border-radius: $kds-radius-xl;
  padding: 14px;
  border: 1px solid $kds-border;
  height: 160px;
  position: relative;
}

.chef-detail__canvas {
  width: 100% !important;
  height: 100% !important;
  display: block;
}

.chef-detail__thresh-line {
  position: absolute;
  left: 14px;
  right: 14px;
  border-top: 2px dashed $kds-red;
  opacity: 0.4;
  pointer-events: none;
}

.chef-detail__thresh-lbl {
  position: absolute;
  right: 4px;
  top: -9px;
  font-size: 9px;
  color: $kds-red;
  font-family: 'DM Mono', monospace;
  background: $kds-surface-2;
  padding: 0 3px;
}

.chef-detail__ing-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 6px;
}

.chef-detail__ing {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  background: $kds-surface-2;
  border-radius: $kds-radius-md;
  font-size: 12px;
  border: 1px solid $kds-border;
}

.chef-detail__ing-name { color: $kds-text-dim; }
.chef-detail__ing-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chef-detail__ing-qty {
  font-family: 'DM Mono', monospace;
  font-weight: 500;
  color: $kds-text;
}

.chef-detail__trace-order {
  font-size: 10px;
  font-weight: 400;
  color: $kds-indigo;
  margin-left: 8px;
}

.chef-detail__trace-row {
  display: flex;
  gap: 6px;
  margin-top: 3px;
}

.chef-detail__trace-tag {
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

.chef-detail__alert-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chef-detail__alert-item {
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

.chef-detail__alert-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chef-detail__alert-name {
  font-size: 13px;
  font-weight: 600;
  color: $kds-text;
}

.chef-detail__alert-level {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  background: rgba(248, 113, 113, 0.2);
  color: $kds-red;
}

.chef-detail__alert-item--warning .chef-detail__alert-level {
  background: rgba(251, 191, 36, 0.2);
  color: $kds-amber;
}

.chef-detail__alert-desc {
  font-size: 12px;
  color: $kds-text-dim;
  line-height: 1.4;
}

.chef-detail__alert-suggestion {
  font-size: 11px;
  color: $kds-text-muted;
  font-style: italic;
}

.chef-detail__alert-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}

.chef-detail__alert-time {
  font-size: 10px;
  color: $kds-text-muted;
  font-family: 'DM Mono', monospace;
}

.chef-detail__alert-ack-btn {
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

.chef-detail__alert-acked {
  font-size: 10px;
  color: $kds-green;
  font-weight: 500;
}

.chef-detail__sample { margin-bottom: 16px; }

.chef-detail__sample-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: $kds-radius-md;
  font-size: 12px;
  font-weight: 500;

  &--done { background: $kds-green-dim; color: $kds-green; border: 1px solid $kds-green-border; }
  &--pending { background: $kds-amber-dim; color: $kds-amber; border: 1px solid $kds-amber-border; }
}

.chef-detail__action-area {
  margin-bottom: 8px;
}

.chef-detail__food-safety {
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

.chef-detail__temp-abnormal-banner {
  padding: 10px 14px;
  border-radius: $kds-radius-md;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 12px;
  background: rgba(248, 113, 113, 0.12);
  color: $kds-red;
  border: 1px solid $kds-red-border;
  animation: aglow 1.5s infinite;
}

.chef-detail__temp-abnormal-confirmed {
  padding: 8px 14px;
  border-radius: $kds-radius-md;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 12px;
  background: $kds-green-dim;
  color: $kds-green;
  border: 1px solid $kds-green-border;
}

.chef-detail__big-btn {
  width: 100%;
  padding: 30px;
  border-radius: $kds-radius-xl;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: 1px;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  transition: all 0.15s;
  cursor: pointer;
  font-family: inherit;
  margin-bottom: 8px;

  &:active { transform: scale(0.97); }
  &:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }

  &--start {
    background: linear-gradient(135deg, #16a34a, #15803d);
    color: #fff;
    box-shadow: 0 4px 24px rgba(34, 197, 94, 0.3);
    &:hover:not(:disabled) { box-shadow: 0 6px 32px rgba(34, 197, 94, 0.4); }
  }

  &--finish {
    background: linear-gradient(135deg, #4f8cff, #7c3aed);
    color: #fff;
    box-shadow: 0 4px 24px rgba(79, 140, 255, 0.3);
    &:hover:not(:disabled) { box-shadow: 0 6px 32px rgba(79, 140, 255, 0.4); }
  }

  &--alert {
    background: linear-gradient(135deg, #f87171, #dc2626);
    color: #fff;
    box-shadow: 0 4px 24px rgba(248, 113, 113, 0.3);
    &:hover:not(:disabled) { box-shadow: 0 6px 32px rgba(248, 113, 113, 0.4); }
  }
}

.chef-detail__done-text {
  text-align: center;
  padding: 20px;
  color: $kds-indigo;
  font-size: 16px;
  font-weight: 700;
}

.chef-detail__action-reason {
  font-size: 14px;
  color: $kds-red;
  font-weight: 600;
  padding: 8px 0;
  text-align: center;
}

// Done overlay
.chef-detail__done-ov {
  position: absolute;
  inset: 0;
  background: rgba(10, 12, 16, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 14px;
  z-index: 50;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s;

  &--show {
    opacity: 1;
    pointer-events: auto;
  }
}

.chef-detail__done-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: $kds-green;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  animation: pop 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes pop {
  0% { transform: scale(0); }
  100% { transform: scale(1); }
}

.chef-detail__done-ov-title {
  font-size: 20px;
  font-weight: 700;
  color: $kds-green;
}

.chef-detail__done-ov-sub {
  font-size: 13px;
  color: $kds-text-dim;
}

.chef-detail__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.chef-detail__empty-icon {
  font-size: 48px;
  opacity: 0.3;
}

.chef-detail__empty-text {
  font-size: 14px;
  color: $kds-text-muted;
}

// P2-13: Operation log collapsible panel
.chef-detail__log-panel {
  border-top: 1px solid $kds-border;
  margin-top: 12px;
}

.chef-detail__log-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  cursor: pointer;
  user-select: none;

  &:hover .chef-detail__log-title {
    color: $kds-text-bright;
  }
}

.chef-detail__log-title {
  font-size: 13px;
  font-weight: 700;
  color: $kds-text;
  transition: color 0.15s;
}

.chef-detail__log-count {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 10px;
  background: $kds-surface-3;
  color: $kds-text-dim;
  font-family: 'DM Mono', monospace;
}

.chef-detail__log-toggle {
  margin-left: auto;
  font-size: 11px;
  color: $kds-text-muted;
  transition: color 0.15s;
}

.chef-detail__log-body {
  max-height: 300px;
  overflow-y: auto;
  padding-bottom: 8px;
  scrollbar-width: thin;
  scrollbar-color: $kds-border transparent;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb { background: $kds-border; border-radius: 2px; }
}
</style>
