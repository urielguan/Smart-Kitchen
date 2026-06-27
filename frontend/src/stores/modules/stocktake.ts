import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stocktakeApi } from '@/api/modules/stocktake'
import type { StocktakeOrderDetail, StocktakeOrderListItem, StocktakeOrderQuery } from '@/types/stocktake'

export const useStocktakeStore = defineStore('stocktake', () => {
  const list = ref<StocktakeOrderListItem[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const detailLoading = ref(false)
  const searchParams = ref<StocktakeOrderQuery>({})

  const detailVisible = ref(false)
  const approveVisible = ref(false)
  const currentId = ref<number | null>(null)
  const currentDetail = ref<StocktakeOrderDetail | null>(null)

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await stocktakeApi.getList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value,
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list || []
        total.value = res.data.total || 0
      }
    } catch {
    } finally {
      loading.value = false
    }
  }

  const fetchDetail = async (id: number) => {
    detailLoading.value = true
    try {
      const res = await stocktakeApi.getDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentDetail.value = res.data
      }
    } catch {
    } finally {
      detailLoading.value = false
    }
  }

  const init = () => fetchList()

  const search = async (params: StocktakeOrderQuery) => {
    searchParams.value = params
    pageNum.value = 1
    await fetchList()
  }

  const resetSearch = async () => {
    searchParams.value = {}
    pageNum.value = 1
    await fetchList()
  }

  const changePage = async (page: number) => {
    pageNum.value = page
    await fetchList()
  }

  const changePageSize = async (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  const openDetail = async (id: number) => {
    currentId.value = id
    detailVisible.value = true
    await fetchDetail(id)
  }

  const closeDetail = () => {
    detailVisible.value = false
    currentId.value = null
    currentDetail.value = null
  }

  const openApprove = (id: number) => {
    currentId.value = id
    approveVisible.value = true
  }

  const closeApprove = () => {
    approveVisible.value = false
    currentId.value = null
  }

  const submitOrder = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认提交该盘点单审核？', '提交确认', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      await stocktakeApi.submit(id)
      ElMessage.success('提交成功')
      await fetchList()
      return true
    } catch {
      return false
    }
  }

  const voidOrder = async (id: number, voidReason: string) => {
    try {
      await stocktakeApi.voidOrder(id, { voidReason })
      ElMessage.success('作废成功')
      await fetchList()
      return true
    } catch {
      return false
    }
  }

  const approveOrder = async (id: number, approveRemark?: string) => {
    try {
      await stocktakeApi.approve(id, { approveRemark })
      ElMessage.success('审核通过')
      await fetchList()
      return true
    } catch {
      return false
    }
  }

  const rejectOrder = async (id: number, rejectRemark: string) => {
    try {
      await stocktakeApi.reject(id, { rejectRemark })
      ElMessage.success('已驳回')
      await fetchList()
      return true
    } catch {
      return false
    }
  }

  const exportList = async () => {
    try {
      await stocktakeApi.exportList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value,
      })
    } catch {
    }
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    loading,
    detailLoading,
    searchParams,
    detailVisible,
    approveVisible,
    currentId,
    currentDetail,
    fetchList,
    fetchDetail,
    init,
    search,
    resetSearch,
    changePage,
    changePageSize,
    openDetail,
    closeDetail,
    openApprove,
    closeApprove,
    submitOrder,
    voidOrder,
    approveOrder,
    rejectOrder,
    exportList,
  }
})
