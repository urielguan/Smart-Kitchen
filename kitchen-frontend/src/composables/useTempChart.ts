import { watch, type Ref } from 'vue'

const CHART_PADDING = 30
const GRID_COLOR = '#2a2e38'
const LINE_COLOR = '#34d399'
const TARGET_COLOR = 'rgba(251, 191, 36, 0.5)'
const TEXT_COLOR = '#8b8f96'
const FILL_COLOR = 'rgba(52, 211, 153, 0.08)'
const BAND_FILL = 'rgba(251, 191, 36, 0.08)'
const BAND_BORDER = 'rgba(251, 191, 36, 0.35)'

export function useTempChart(
  canvasRef: Ref<HTMLCanvasElement | null>,
  data: Ref<number[]>,
  targetTemp?: Ref<number | null>,
  targetTempMin?: Ref<number | null>,
  targetTempMax?: Ref<number | null>
) {
  const draw = () => {
    const canvas = canvasRef.value
    if (!canvas) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const dpr = window.devicePixelRatio || 1
    const rect = canvas.getBoundingClientRect()
    canvas.width = rect.width * dpr
    canvas.height = rect.height * dpr
    ctx.scale(dpr, dpr)

    const w = rect.width
    const h = rect.height
    const plotW = w - CHART_PADDING * 2
    const plotH = h - CHART_PADDING * 2

    ctx.clearRect(0, 0, w, h)

    const values = data.value
    if (values.length < 2) {
      ctx.fillStyle = TEXT_COLOR
      ctx.font = '14px system-ui, sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText('等待温度数据...', w / 2, h / 2)
      return
    }

    const minVal = Math.min(...values) - 10
    const maxVal = Math.max(...values) + 10
    const range = maxVal - minVal || 1

    // Draw grid lines
    ctx.strokeStyle = GRID_COLOR
    ctx.lineWidth = 0.5
    for (let i = 0; i <= 4; i++) {
      const y = CHART_PADDING + (plotH / 4) * i
      ctx.beginPath()
      ctx.moveTo(CHART_PADDING, y)
      ctx.lineTo(w - CHART_PADDING, y)
      ctx.stroke()

      // Y-axis labels
      const val = maxVal - (range / 4) * i
      ctx.fillStyle = TEXT_COLOR
      ctx.font = '10px system-ui, sans-serif'
      ctx.textAlign = 'right'
      ctx.fillText(`${Math.round(val)}°`, CHART_PADDING - 4, y + 3)
    }

    // Draw target line
    if (targetTemp?.value !== null && targetTemp?.value !== undefined) {
      const targetY = CHART_PADDING + plotH * (1 - (targetTemp.value! - minVal) / range)
      if (targetY >= CHART_PADDING && targetY <= CHART_PADDING + plotH) {
        ctx.strokeStyle = TARGET_COLOR
        ctx.lineWidth = 1
        ctx.setLineDash([4, 4])
        ctx.beginPath()
        ctx.moveTo(CHART_PADDING, targetY)
        ctx.lineTo(w - CHART_PADDING, targetY)
        ctx.stroke()
        ctx.setLineDash([])
      }
    }

    // Draw temperature range band (when min/max available)
    const rangeMin = targetTempMin?.value
    const rangeMax = targetTempMax?.value
    if (rangeMin != null && rangeMax != null && rangeMin !== rangeMax) {
      const bandTopY = CHART_PADDING + plotH * (1 - (rangeMax - minVal) / range)
      const bandBotY = CHART_PADDING + plotH * (1 - (rangeMin - minVal) / range)
      const clampedTop = Math.max(CHART_PADDING, Math.min(CHART_PADDING + plotH, bandTopY))
      const clampedBot = Math.max(CHART_PADDING, Math.min(CHART_PADDING + plotH, bandBotY))

      if (clampedBot > clampedTop) {
        // Fill band area
        ctx.fillStyle = BAND_FILL
        ctx.fillRect(CHART_PADDING, clampedTop, plotW, clampedBot - clampedTop)

        // Top border (max)
        ctx.strokeStyle = BAND_BORDER
        ctx.lineWidth = 1
        ctx.setLineDash([4, 4])
        ctx.beginPath()
        ctx.moveTo(CHART_PADDING, clampedTop)
        ctx.lineTo(w - CHART_PADDING, clampedTop)
        ctx.stroke()

        // Bottom border (min)
        ctx.beginPath()
        ctx.moveTo(CHART_PADDING, clampedBot)
        ctx.lineTo(w - CHART_PADDING, clampedBot)
        ctx.stroke()
        ctx.setLineDash([])

        // Label
        ctx.fillStyle = BAND_BORDER
        ctx.font = '10px system-ui, sans-serif'
        ctx.textAlign = 'left'
        ctx.fillText(`${rangeMin}~${rangeMax}°C`, CHART_PADDING + 4, clampedTop - 3)
      }
    }

    // Build path
    const stepX = plotW / (values.length - 1)
    ctx.beginPath()
    values.forEach((val, i) => {
      const x = CHART_PADDING + i * stepX
      const y = CHART_PADDING + plotH * (1 - (val - minVal) / range)
      if (i === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    })

    // Fill area
    const lastX = CHART_PADDING + (values.length - 1) * stepX
    ctx.lineTo(lastX, CHART_PADDING + plotH)
    ctx.lineTo(CHART_PADDING, CHART_PADDING + plotH)
    ctx.closePath()
    ctx.fillStyle = FILL_COLOR
    ctx.fill()

    // Draw line
    ctx.beginPath()
    values.forEach((val, i) => {
      const x = CHART_PADDING + i * stepX
      const y = CHART_PADDING + plotH * (1 - (val - minVal) / range)
      if (i === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    })
    ctx.strokeStyle = LINE_COLOR
    ctx.lineWidth = 2
    ctx.stroke()

    // Draw last point
    if (values.length > 0) {
      const lastVal = values[values.length - 1]
      const lx = CHART_PADDING + (values.length - 1) * stepX
      const ly = CHART_PADDING + plotH * (1 - (lastVal - minVal) / range)
      ctx.beginPath()
      ctx.arc(lx, ly, 4, 0, Math.PI * 2)
      ctx.fillStyle = LINE_COLOR
      ctx.fill()
      ctx.strokeStyle = '#141820'
      ctx.lineWidth = 2
      ctx.stroke()
    }
  }

  watch([data, canvasRef, targetTempMin, targetTempMax], () => {
    requestAnimationFrame(draw)
  }, { deep: true })

  return { draw }
}
