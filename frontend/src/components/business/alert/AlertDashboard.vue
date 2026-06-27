<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import type { AlertDashboard } from '@/types/alert'

interface Props {
  dashboard: AlertDashboard
}

const props = defineProps<Props>()

// ========== 趋势面积图 ==========
const PADDING_TOP = 16
const PADDING_BOTTOM = 8
const PADDING_LEFT = 36
const PADDING_RIGHT = 12

const svgContainerRef = ref<HTMLElement>()
const chartWidth = ref(0)
const chartHeight = ref(0)
const hoveredIndex = ref<number | null>(null)

const rawMaxCount = computed(() => {
  const trends = props.dashboard.alertTrends
  if (!trends?.length) return 1
  return Math.max(...trends.map(t => t.count), 1)
})

/** 计算 Y 轴"好看"的最大值和刻度（向上取整到 1/2/5×10^n） */
const yScale = computed(() => {
  const rawMax = rawMaxCount.value
  if (rawMax <= 1) {
    return { max: rawMax || 1, ticks: rawMax === 0 ? [0] : [0, 1] }
  }
  const roughStep = rawMax / 5
  const exp = Math.floor(Math.log10(roughStep))
  const base = Math.pow(10, exp)
  const fraction = roughStep / base
  let niceFraction: number
  if (fraction <= 1) niceFraction = 1
  else if (fraction <= 2) niceFraction = 2
  else if (fraction <= 5) niceFraction = 5
  else niceFraction = 10
  const step = Math.max(niceFraction * base, 1)
  const niceMax = Math.ceil(rawMax / step) * step
  const tickCount = Math.round(niceMax / step)
  const ticks = Array.from({ length: tickCount + 1 }, (_, i) => i * step)
  return { max: niceMax, ticks }
})

/** Y 轴刻度位置（像素坐标） */
const yTicks = computed(() => {
  const scale = yScale.value
  const h = chartHeight.value
  if (!h) return []
  const chartH = Math.max(h - PADDING_TOP - PADDING_BOTTOM, 20)
  return scale.ticks.map(value => ({
    value,
    y: PADDING_TOP + chartH - (value / scale.max) * chartH,
  }))
})

const trendPoints = computed(() => {
  const trends = props.dashboard.alertTrends
  if (!trends?.length || !chartWidth.value || !chartHeight.value) return []

  const w = chartWidth.value
  const h = chartHeight.value
  const chartH = Math.max(h - PADDING_TOP - PADDING_BOTTOM, 20)
  const chartW = Math.max(w - PADDING_LEFT - PADDING_RIGHT, 20)
  const count = trends.length
  const stepX = count > 1 ? chartW / (count - 1) : 0
  const max = yScale.value.max

  return trends.map((t, i) => ({
    x: PADDING_LEFT + (count > 1 ? i * stepX : chartW / 2),
    y: PADDING_TOP + chartH - (t.count / max) * chartH,
    date: t.date,
    count: t.count,
  }))
})

/** Catmull-Rom 转贝塞尔曲线，生成平滑路径（控制点 y 限制在 maxY 以内，防止曲线溢出底部） */
const smoothPath = (pts: { x: number; y: number }[], maxY?: number) => {
  if (pts.length < 2) return ''
  const d = [`M ${pts[0].x} ${pts[0].y}`]
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[i + 2] || p2
    const cp1x = p1.x + (p2.x - p0.x) / 6
    let cp1y = p1.y + (p2.y - p0.y) / 6
    const cp2x = p2.x - (p3.x - p1.x) / 6
    let cp2y = p2.y - (p3.y - p1.y) / 6
    if (maxY !== undefined) {
      cp1y = Math.min(cp1y, maxY)
      cp2y = Math.min(cp2y, maxY)
    }
    d.push(`C ${cp1x} ${cp1y} ${cp2x} ${cp2y} ${p2.x} ${p2.y}`)
  }
  return d.join(' ')
}

const linePathD = computed(() => smoothPath(trendPoints.value, chartHeight.value - PADDING_BOTTOM))

const areaPathD = computed(() => {
  const pts = trendPoints.value
  if (pts.length < 2) return ''
  const bottom = chartHeight.value - PADDING_BOTTOM
  return `${smoothPath(pts, bottom)} L ${pts[pts.length - 1].x} ${bottom} L ${pts[0].x} ${bottom} Z`
})

const tooltipStyle = computed(() => {
  if (hoveredIndex.value === null) return { display: 'none' }
  const p = trendPoints.value[hoveredIndex.value]
  if (!p) return { display: 'none' }
  return { left: `${p.x}px`, top: `${p.y}px` }
})

const onMouseMove = (e: MouseEvent) => {
  const container = svgContainerRef.value
  if (!container) return
  const rect = container.getBoundingClientRect()
  const mouseX = e.clientX - rect.left
  const pts = trendPoints.value
  if (!pts.length) return

  let minDist = Infinity
  let nearest = 0
  pts.forEach((p, i) => {
    const dist = Math.abs(p.x - mouseX)
    if (dist < minDist) {
      minDist = dist
      nearest = i
    }
  })
  hoveredIndex.value = nearest
}

const updateChartSize = () => {
  if (svgContainerRef.value) {
    const rect = svgContainerRef.value.getBoundingClientRect()
    chartWidth.value = rect.width
    chartHeight.value = rect.height
  }
}

/** 计算 X 轴标签位置（与数据点对齐） */
const pointX = (idx: number) => {
  const count = props.dashboard.alertTrends?.length ?? 0
  if (!chartWidth.value || count === 0) return PADDING_LEFT
  const chartW = Math.max(chartWidth.value - PADDING_LEFT - PADDING_RIGHT, 20)
  return PADDING_LEFT + (count > 1 ? idx * chartW / (count - 1) : chartW / 2)
}

watch(() => props.dashboard.alertTrends, () => {
  nextTick(updateChartSize)
}, { deep: true })

onMounted(() => {
  nextTick(updateChartSize)
  window.addEventListener('resize', updateChartSize)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateChartSize)
})

/** 合并统计卡片配置（级别 + 状态） */
const mergedCards = computed(() => [
  { title: '总告警数', value: props.dashboard.totalCount, color: '#5570F1', bgColor: 'rgba(21, 112, 255, 0.08)' },
  { title: '严重', value: props.dashboard.criticalCount, color: '#FDAD00', bgColor: 'rgba(253, 173, 0, 0.08)' },
  { title: '错误', value: props.dashboard.errorCount, color: '#FF1519', bgColor: 'rgba(255, 21, 25, 0.08)' },
  { title: '警告', value: props.dashboard.warningCount, color: '#E96466', bgColor: 'rgba(249, 60, 0, 0.05)' },
  { title: '提示', value: props.dashboard.infoCount, color: '#5570F1', bgColor: 'rgba(119, 21, 255, 0.08)' },
  { title: '待处理', value: props.dashboard.pendingCount, color: '#FDAD00', bgColor: 'rgba(253, 173, 0, 0.08)' },
  { title: '已指派', value: props.dashboard.assignedCount, color: '#06C7B8', bgColor: 'rgba(59, 219, 207, 0.08)' },
  { title: '已处置', value: props.dashboard.handledCount, color: '#FDAD00', bgColor: 'rgba(253, 173, 0, 0.08)' },
  { title: '已复核', value: props.dashboard.reviewedCount, color: '#06C7B8', bgColor: 'rgba(59, 219, 207, 0.08)' },
  { title: '已关闭', value: props.dashboard.closedCount, color: '#5570F1', bgColor: 'rgba(21, 112, 255, 0.08)' },
])
</script>

<template>
  <div class="alert-dashboard">
    <!-- 合并统计：按级别 + 按状态（无标题，184px，卡片 70px） -->
    <div class="stat-summary">
      <div
        v-for="card in mergedCards"
        :key="card.title"
        class="summary-card"
        :style="{ background: card.bgColor }"
      >
        <span class="summary-card__title">{{ card.title }}</span>
        <span class="summary-card__value" :style="{ color: card.color }">{{ card.value }}</span>
      </div>
    </div>

    <!-- 按类型统计 + 近7日趋势（同行，260px，保留标题） -->
    <div class="stat-row">
      <!-- 按类型统计 -->
      <div class="stat-panel" v-if="dashboard.alertTypeStats?.length">
        <div class="section-title">
          <span class="title-bar" />
          按类型统计
        </div>
        <div class="type-chart">
          <div v-for="stat in dashboard.alertTypeStats" :key="stat.alertType" class="type-item">
            <span class="type-name">{{ stat.alertTypeName }}</span>
            <div class="type-bar-track">
              <div
                class="type-bar-fill"
                :style="{ width: (Math.round(stat.count / dashboard.totalCount * 100) || 0) + '%' }"
              />
            </div>
            <span class="type-bar-value">{{ stat.count }}</span>
          </div>
        </div>
      </div>

      <!-- 近7日趋势 -->
      <div class="stat-panel" v-if="dashboard.alertTrends?.length">
        <div class="section-title">
          <span class="title-bar" />
          近7日趋势
        </div>
        <div
          class="trend-chart-wrapper"
          ref="svgContainerRef"
          @mousemove="onMouseMove"
          @mouseleave="hoveredIndex = null"
        >
          <svg class="trend-svg" :width="chartWidth" :height="chartHeight">
            <defs>
              <linearGradient id="trend-area-gradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="rgba(85, 112, 241, 0.6)" />
                <stop offset="100%" stop-color="rgba(85, 112, 241, 0.1)" />
              </linearGradient>
            </defs>
            <!-- Y轴刻度数值（无横线） -->
            <g v-for="(tick, idx) in yTicks" :key="`grid-${idx}`">
              <text
                :x="PADDING_LEFT - 8"
                :y="tick.y + 4"
                text-anchor="end"
                class="trend-svg-axis"
              >{{ tick.value }}</text>
            </g>
            <!-- X轴 + 竖线刻度 -->
            <line
              :x1="PADDING_LEFT"
              :y1="chartHeight - PADDING_BOTTOM"
              :x2="chartWidth - PADDING_RIGHT"
              :y2="chartHeight - PADDING_BOTTOM"
              stroke="#DCDFE6"
              stroke-width="1"
            />
            <line
              v-for="(p, idx) in trendPoints"
              :key="`xtick-${idx}`"
              :x1="p.x"
              :y1="PADDING_TOP"
              :x2="p.x"
              :y2="chartHeight - PADDING_BOTTOM"
              stroke="#EBEEF5"
              stroke-width="1"
            />
            <!-- 面积 -->
            <path :d="areaPathD" fill="url(#trend-area-gradient)" stroke="none" />
            <!-- 折线 -->
            <path
              :d="linePathD"
              fill="none"
              stroke="#5570F1"
              stroke-width="2"
              stroke-linejoin="round"
              stroke-linecap="round"
            />
            <!-- 悬停指示线 -->
            <line
              v-if="hoveredIndex !== null && trendPoints[hoveredIndex]"
              :x1="trendPoints[hoveredIndex].x"
              :y1="PADDING_TOP"
              :x2="trendPoints[hoveredIndex].x"
              :y2="chartHeight - PADDING_BOTTOM"
              stroke="rgba(85, 112, 241, 0.3)"
              stroke-width="1"
              stroke-dasharray="4 2"
            />
            <!-- 数据点 -->
            <circle
              v-for="(p, idx) in trendPoints"
              :key="idx"
              :cx="p.x"
              :cy="p.y"
              :r="hoveredIndex === idx ? 5 : 3.5"
              fill="#5570F1"
              stroke="#fff"
              stroke-width="2"
            />
          </svg>
          <!-- 悬停提示框 -->
          <div
            v-if="hoveredIndex !== null && trendPoints[hoveredIndex]"
            class="trend-tooltip"
            :style="tooltipStyle"
          >
            <div class="trend-tooltip__date">{{ trendPoints[hoveredIndex].date }}</div>
            <div class="trend-tooltip__value">
              <span class="trend-tooltip__dot" />
              数量：{{ trendPoints[hoveredIndex].count }}
            </div>
          </div>
        </div>
        <!-- 日期标签（stat-panel 的独立 flex 子元素） -->
        <div class="trend-x-labels">
          <span
            v-for="(trend, idx) in dashboard.alertTrends"
            :key="`xlabel-${trend.date}`"
            class="trend-x-label"
            :style="{ left: pointX(idx) + 'px' }"
          >{{ trend.date }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.alert-dashboard {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

/* ---- 合并统计栏（级别 + 状态） ---- */
.stat-summary {
  box-sizing: border-box;
  padding: 16px;
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  grid-template-rows: repeat(2, 1fr);
  gap: 12px;
}

.summary-card {
  background: #F6F8FC;
  border-radius: 8px;
  padding: 12px 30px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;

  &__title {
    font-size: 14px;
    color: #000000;
    line-height: 1.2;
  }

  &__value {
    font-family: 'Poppins', 'PingFang SC', sans-serif;
    font-weight: 600;
    font-size: 24px;
    line-height: 1.2;
  }
}

/* ---- 类型 + 趋势同行 ---- */
.stat-row {
  height: 260px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.stat-panel {
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  padding: 16px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.section-title {
  display: flex;
  align-items: center;
  font-weight: 500;
  font-size: 16px;
  color: #303133;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.title-bar {
  width: 4px;
  height: 16px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

/* ---- 类型统计 ---- */
.type-chart {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  padding: 0px 27px 20px 27px;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 12px;

  .type-name {
    width: 90px;
    flex-shrink: 0;
    font-size: 13px;
    color: #333F4E;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.type-bar-track {
  flex: 1;
  height: 14px;
  background: #EBEEF5;
  border-radius: 10px;
  overflow: hidden;
  min-width: 0;
}

.type-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #3CC7EC);
  border-radius: 10px;
  min-width: 28px;
  transition: width 0.3s;
}

.type-bar-value {
  flex-shrink: 0;
  width: 20px;
  text-align: left;
  font-size: 13px;
  color: #666666;
  font-weight: 400;
}

/* ---- 趋势图 ---- */
.trend-chart-wrapper {
  position: relative;
  flex: 1;
  min-height: 0;
}

.trend-svg {
  display: block;
  width: 100%;
  height: 100%;
}

.trend-svg-label {
  font-size: 12px;
  fill: rgba(0, 0, 0, 0.45);
}

.trend-x-labels {
  position: relative;
  flex-shrink: 0;
  height: 20px;
  margin-top: 4px;
}

.trend-x-label {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 14px;
  white-space: nowrap;
}

.trend-svg-axis {
  font-size: 14px;
}

.trend-tooltip {
  position: absolute;
  pointer-events: none;
  transform: translate(-50%, calc(-100% - 8px));
  width: 108px;
  height: 55px;
  box-sizing: border-box;
  background: #FFFFFF;
  border: 1px solid #5570F1;
  border-radius: 5px;
  padding: 8px 16px;
  color: #000000;
  font-size: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  z-index: 10;

  &__date {
    color: #000000;
  }

  &__value {
    color: #000000;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #5570F1;
    flex-shrink: 0;
  }
}

@media (max-width: 1200px) {
  .stat-summary {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}
</style>
