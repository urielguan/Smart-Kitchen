import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PurchaseOrderQuery, PurchaseOrderRecord, PurchaseOrderStatistics } from '@/types/purchase'

type PurchaseOrderSearchCache = Pick<PurchaseOrderQuery, 'keyword' | 'status' | 'dateStart' | 'dateEnd'>

const createDefaultSearchParams = (): PurchaseOrderSearchCache => ({
  keyword: '',
  status: '',
  dateStart: '',
  dateEnd: '',
})

const createDefaultStatistics = (): PurchaseOrderStatistics => ({
  total: 0,
  pending: 0,
  approved: 0,
  totalAmount: 0
})

export const usePurchaseOrderStore = defineStore('purchaseOrder', () => {
  const list = ref<PurchaseOrderRecord[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const initialized = ref(false)
  const searchFormCache = ref<PurchaseOrderSearchCache>(createDefaultSearchParams())
  const statistics = ref<PurchaseOrderStatistics>(createDefaultStatistics())
  const pendingEditOrderId = ref<number | null>(null)

  const updateSearchFormCache = (params: PurchaseOrderSearchCache) => {
    searchFormCache.value = { ...searchFormCache.value, ...params }
  }

  const setListCache = (payload: {
    list: PurchaseOrderRecord[]
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

  const setStatisticsCache = (value: PurchaseOrderStatistics) => {
    statistics.value = { ...value }
  }

  const resetSearchCache = () => {
    searchFormCache.value = createDefaultSearchParams()
  }

  const setPendingEditOrderId = (orderId: number | null) => {
    pendingEditOrderId.value = orderId
  }

  const consumePendingEditOrderId = () => {
    const orderId = pendingEditOrderId.value
    pendingEditOrderId.value = null
    return orderId
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    initialized,
    searchFormCache,
    statistics,
    pendingEditOrderId,
    updateSearchFormCache,
    setListCache,
    setStatisticsCache,
    resetSearchCache,
    setPendingEditOrderId,
    consumePendingEditOrderId
  }
})
