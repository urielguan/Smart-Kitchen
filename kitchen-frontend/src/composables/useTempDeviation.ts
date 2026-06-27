import { computed } from 'vue'

export interface TempDeviationResult {
  /** 偏差值（负数表示低于目标） */
  value: number | null
  /** 显示文本，如 "+3°C" 或 "-2°C" */
  label: string
  /** 状态等级 */
  level: 'ok' | 'warn' | 'bad' | 'none'
}

/**
 * 计算温度偏差
 * @param currentTemp 当前温度
 * @param targetTemp 单一目标温度阈值（>=）
 * @param targetTempMin 目标温度下限
 * @param targetTempMax 目标温度上限
 */
export function useTempDeviation(
  currentTemp: () => number | null | undefined,
  targetTemp: () => number | null | undefined,
  targetTempMin: () => number | null | undefined,
  targetTempMax: () => number | null | undefined
) {
  const deviation = computed<TempDeviationResult>(() => {
    const cur = currentTemp()
    const min = targetTempMin()
    const max = targetTempMax()
    const single = targetTemp()

    if (cur == null) {
      return { value: null, label: '--', level: 'none' }
    }

    let deviationValue: number
    let inRange: boolean

    if (min != null && max != null) {
      // 区间模式
      if (cur >= min && cur <= max) {
        deviationValue = 0
        inRange = true
      } else if (cur < min) {
        deviationValue = cur - min
        inRange = false
      } else {
        deviationValue = cur - max
        inRange = false
      }
    } else if (single != null) {
      // 单阈值模式（>=）
      deviationValue = cur - single
      inRange = cur >= single
    } else {
      return { value: null, label: '--', level: 'none' }
    }

    const sign = deviationValue > 0 ? '+' : ''
    const label = `${sign}${deviationValue}°C`

    let level: TempDeviationResult['level']
    if (inRange) {
      level = 'ok'
    } else if (Math.abs(deviationValue) <= 5) {
      level = 'warn'
    } else {
      level = 'bad'
    }

    return { value: deviationValue, label, level }
  })

  /** 从温度记录中计算最高/最低温 */
  const tempExtremes = (temperatureRecords: { temperature: number | null }[] | undefined) => {
    if (!temperatureRecords?.length) return { max: null as number | null, min: null as number | null }
    const temps = temperatureRecords.map((r) => r.temperature).filter((t): t is number => t !== null)
    if (!temps.length) return { max: null as number | null, min: null as number | null }
    return { max: Math.max(...temps), min: Math.min(...temps) }
  }

  return { deviation, tempExtremes }
}
