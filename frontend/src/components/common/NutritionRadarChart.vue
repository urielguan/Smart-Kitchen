<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import * as echarts from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([RadarChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer])

interface NutritionData {
  name: string
  value: number
  target: number
  unit: string
}

interface Props {
  data: NutritionData[]
  title?: string
  height?: string
  showLegend?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  height: '280px',
  showLegend: false
})

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const chartData = computed(() => {
  if (!props.data || props.data.length === 0) {
    return {
      indicators: [],
      values: [],
      targets: []
    }
  }

  const indicators = props.data.map(item => ({
    name: item.name,
    max: Math.max(item.value * 1.3, item.target * 1.2)
  }))

  const values = props.data.map(item => item.value)
  const targets = props.data.map(item => item.target)

  return { indicators, values, targets }
})

const getChartOption = () => {
  const { indicators, values, targets } = chartData.value

  if (indicators.length === 0) {
    return {}
  }

  return {
    backgroundColor: 'transparent',
    title: props.title ? {
      text: props.title,
      left: 'center',
      top: 0,
      textStyle: {
        color: '#303133',
        fontSize: 14,
        fontWeight: 500
      }
    } : undefined,
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e4e7ed',
      borderWidth: 1,
      textStyle: { color: '#606266', fontSize: 12 },
      formatter: (params: any) => {
        if (!params.value || !props.data) return ''
        const data = props.data
        let html = '<div style="padding: 4px 0;">'
        data.forEach((item, index) => {
          const val = params.value[index]
          const percentage = item.target > 0 ? Math.round((val / item.target) * 100) : 0
          const status = percentage >= 80 && percentage <= 120 ? '达标' : percentage < 80 ? '不足' : '过量'
          const statusColor = status === '达标' ? '#67c23a' : status === '不足' ? '#e6a23c' : '#f56c6c'
          html += `<div style="display:flex;justify-content:space-between;gap:20px;padding:2px 0;">
            <span>${item.name}</span>
            <span>${val}${item.unit} <span style="color:${statusColor};font-size:11px;">(${status})</span></span>
          </div>`
        })
        html += '</div>'
        return html
      }
    },
    legend: props.showLegend ? {
      bottom: 0,
      data: ['实际值', '目标值'],
      textStyle: { color: '#606266', fontSize: 12 }
    } : undefined,
    radar: {
      center: ['50%', props.title ? '55%' : '50%'],
      radius: '65%',
      axisName: {
        color: '#606266',
        fontSize: 12,
        fontWeight: 500
      },
      splitNumber: 4,
      splitArea: {
        areaStyle: {
          color: ['rgba(64, 158, 255, 0.05)', 'rgba(64, 158, 255, 0.1)', 'rgba(64, 158, 255, 0.05)', 'rgba(64, 158, 255, 0.1)']
        }
      },
      axisLine: {
        lineStyle: { color: 'rgba(64, 158, 255, 0.3)' }
      },
      splitLine: {
        lineStyle: { color: 'rgba(64, 158, 255, 0.2)' }
      },
      indicator: indicators
    },
    series: [{
      type: 'radar',
      data: [
        {
          value: targets,
          name: '目标值',
          symbol: 'none',
          lineStyle: {
            color: '#909399',
            width: 1,
            type: 'dashed'
          },
          areaStyle: {
            color: 'rgba(144, 147, 153, 0.1)'
          }
        },
        {
          value: values,
          name: '实际值',
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: {
            color: '#409eff',
            width: 2
          },
          itemStyle: {
            color: '#409eff',
            borderColor: '#fff',
            borderWidth: 2
          },
          areaStyle: {
            color: 'rgba(64, 158, 255, 0.25)'
          }
        }
      ]
    }]
  }
}

const initChart = async () => {
  await nextTick()
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    chart.setOption(getChartOption())
  }
}

const updateChart = () => {
  if (chart) {
    chart.setOption(getChartOption())
  }
}

const resizeChart = () => {
  chart?.resize()
}

watch(() => props.data, () => {
  updateChart()
}, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="chartRef" class="nutrition-radar-chart" :style="{ height }">
    <div v-if="!data || data.length === 0" class="empty-state">
      <el-empty description="暂无营养数据" :image-size="80" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.nutrition-radar-chart {
  width: 100%;
  position: relative;

  .empty-state {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
  }
}
</style>
