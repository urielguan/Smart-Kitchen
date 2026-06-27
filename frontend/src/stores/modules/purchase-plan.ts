import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PurchasePlanQuery, PurchasePlanRecord, PurchasePlanStatistics } from '@/types/purchase-plan'
import type { PurchaseDemandForecastPlanPrefill } from '@/types/purchase-demand-forecast'

type PurchasePlanSearchCache = Pick<PurchasePlanQuery, 'keyword' | 'status'>

const createDefaultSearchParams = (): PurchasePlanSearchCache => ({
  keyword: '',
  status: '',
})

const createDefaultStatistics = (): PurchasePlanStatistics => ({
  total: 0,
  pending: 0,
  approved: 0,
  totalBudget: 0
})

export const usePurchasePlanStore = defineStore('purchasePlan', () => {
  const list = ref<PurchasePlanRecord[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const initialized = ref(false)
  const searchFormCache = ref<PurchasePlanSearchCache>(createDefaultSearchParams())
  const statistics = ref<PurchasePlanStatistics>(createDefaultStatistics())
  const forecastPrefill = ref<PurchaseDemandForecastPlanPrefill | null>(null)

  const updateSearchFormCache = (params: PurchasePlanSearchCache) => {
    searchFormCache.value = { ...searchFormCache.value, ...params }
  }

  const setListCache = (payload: {
    list: PurchasePlanRecord[]
    total: number
    pageNum: number
    pageSize: number
  }) => {
    list.value = payload.list
    total.value = payload.total
    pageNum.value = payload.pageNum
    pageSize.value = payload.pageSize
    initialized.value = true
  }

  const setStatisticsCache = (value: PurchasePlanStatistics) => {
    statistics.value = { ...value }
  }

  const resetSearchCache = () => {
    searchFormCache.value = createDefaultSearchParams()
  }

  const setForecastPrefill = (value: PurchaseDemandForecastPlanPrefill | null) => {
    forecastPrefill.value = value ? { ...value, items: value.items.map((item) => ({ ...item })) } : null
  }

  const consumeForecastPrefill = () => {
    const current = forecastPrefill.value
    forecastPrefill.value = null
    return current
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    initialized,
    searchFormCache,
    statistics,
    forecastPrefill,
    updateSearchFormCache,
    setListCache,
    setStatisticsCache,
    resetSearchCache,
    setForecastPrefill,
    consumeForecastPrefill,
  }
})
