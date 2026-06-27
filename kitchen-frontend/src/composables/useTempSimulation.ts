import { ref, watch, onUnmounted, type Ref } from 'vue'

export function useTempSimulation(targetTemp: Ref<number | null>, isRunning: Ref<boolean>) {
  const tempHistory = ref<number[]>([])
  const currentTemp = ref<number>(0)
  let intervalId: ReturnType<typeof setInterval> | null = null

  const startSimulation = () => {
    stopSimulation()
    const target = targetTemp.value ?? 180
    currentTemp.value = target

    intervalId = setInterval(() => {
      if (!isRunning.value) return
      const noise = (Math.random() - 0.5) * 6
      const drift = (target - currentTemp.value) * 0.1
      currentTemp.value = Math.round((currentTemp.value + drift + noise) * 10) / 10
      tempHistory.value.push(currentTemp.value)
      if (tempHistory.value.length > 30) {
        tempHistory.value.shift()
      }
    }, 3000)
  }

  const stopSimulation = () => {
    if (intervalId !== null) {
      clearInterval(intervalId)
      intervalId = null
    }
  }

  const resetSimulation = () => {
    stopSimulation()
    tempHistory.value = []
    currentTemp.value = targetTemp.value ?? 0
  }

  watch(isRunning, (running) => {
    if (running) {
      startSimulation()
    } else {
      stopSimulation()
    }
  })

  watch(targetTemp, (newTarget) => {
    if (newTarget !== null) {
      currentTemp.value = newTarget
    }
  })

  onUnmounted(() => {
    stopSimulation()
  })

  return {
    tempHistory,
    currentTemp,
    startSimulation,
    stopSimulation,
    resetSimulation
  }
}
