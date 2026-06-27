import { defineStore } from 'pinia'
import { ref } from 'vue'
import inventoryApi from '@/api/modules/inventory'
import type { InventoryOverviewItem, InventoryOverviewQuery } from '@/api/modules/inventory'

export const useInventoryStore = defineStore('inventory', () => {
  const list = ref<InventoryOverviewItem[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const detailVisible = ref(false)
  const currentMaterialId = ref<number | null>(null)
  const searchParams = ref<Partial<InventoryOverviewQuery>>({
    keyword: '',
    categoryName: undefined,
    stockStatus: undefined,
    shelfLifeLevel: undefined,
    materialStatus: 'active',
    warehouseId: undefined,
    locationId: undefined
  })

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await inventoryApi.getOverview({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list
        pageNum.value = res.data.pageNum
        total.value = res.data.total
      }
    } catch (error) {
      console.error('获取库存总览失败:', error)
    } finally {
      loading.value = false
    }
  }

  const search = async (params: Partial<InventoryOverviewQuery>) => {
    searchParams.value = { ...searchParams.value, ...params }
    pageNum.value = 1
    await fetchList()
  }

  const resetSearch = async () => {
    searchParams.value = {
      keyword: '',
      categoryName: undefined,
      stockStatus: undefined,
      shelfLifeLevel: undefined,
      materialStatus: 'active',
      warehouseId: undefined,
      locationId: undefined
    }
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

  const openDetail = (id: number) => {
    currentMaterialId.value = id
    detailVisible.value = true
  }

  const closeDetail = () => {
    detailVisible.value = false
    currentMaterialId.value = null
  }

  const exportOverview = async () => {
    await inventoryApi.exportOverview({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...searchParams.value
    })
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    loading,
    detailVisible,
    currentMaterialId,
    searchParams,
    fetchList,
    search,
    resetSearch,
    changePage,
    changePageSize,
    openDetail,
    closeDetail,
    exportOverview
  }
})
