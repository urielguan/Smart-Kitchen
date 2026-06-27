import { defineStore } from 'pinia'
import { ref, computed, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { reviewApi, complaintApi, dispatchApi } from '@/api/modules/evaluation'
import { employeeApi } from '@/api/modules/employee'

function renderConfirmMessage(title: string, description: string) {
  return () => h('div', { class: 'evaluation-confirm' }, [
    h('div', { class: 'evaluation-confirm__content' }, [
      h(WarningFilled, { class: 'evaluation-confirm__icon' }),
      h('div', { class: 'evaluation-confirm__text' }, [
        h('div', { class: 'evaluation-confirm__title' }, title),
        h('div', { class: 'evaluation-confirm__description' }, description),
      ])
    ])
  ])
}
import type {
  MealReview, ReviewQuery, ReviewStatistics, ReviewReplyForm,
  Complaint, ComplaintQuery, ComplaintStatistics,
  DispatchRecord, DispatchQuery,
  HandlerOption, DispatchForm, ProcessWorkOrderForm
} from '@/types/evaluation'
import type { Employee } from '@/types/employee'
import type { EvaluationTabType } from '@/constants/evaluation'

export const useEvaluationStore = defineStore('evaluation', () => {
  // ==================== 通用状态 ====================
  /** 当前 Tab */
  const activeTab = ref<EvaluationTabType>('review')

  /** 处理人候选员工（全量） */
  const dispatchEmployees = ref<Employee[]>([])

  /** 处理人列表（从全量员工数据构建） */
  const handlers = computed<HandlerOption[]>(() => {
    return dispatchEmployees.value
      .filter(emp => emp.status === 'active')
      .map(emp => ({
        id: emp.id,
        name: emp.realName,
        orgId: emp.orgId,
        orgName: emp.orgName,
        position: emp.position
      }))
  })

  // ==================== 评价列表状态 ====================
  const reviewList = ref<MealReview[]>([])
  const reviewTotal = ref(0)
  const reviewPageNum = ref(1)
  const reviewPageSize = ref(10)
  const reviewLoading = ref(false)
  const reviewStatistics = ref<ReviewStatistics>({
    totalReviews: 0,
    avgScore: 0,
    fiveStarCount: 0,
    fourStarCount: 0,
    threeStarCount: 0,
    twoStarCount: 0,
    oneStarCount: 0,
    satisfactionRate: 0
  })
  const reviewSearchParams = ref<Partial<ReviewQuery>>({
    source: undefined,
    orgId: undefined,
    keyword: '',
    overallScore: undefined,
    startTime: undefined,
    endTime: undefined
  })

  // ==================== 投诉列表状态 ====================
  const complaintList = ref<Complaint[]>([])
  const complaintTotal = ref(0)
  const complaintPageNum = ref(1)
  const complaintPageSize = ref(10)
  const complaintLoading = ref(false)
  const complaintStatistics = ref<ComplaintStatistics>({
    totalComplaints: 0,
    pendingCount: 0,
    dispatchedCount: 0,
    processingCount: 0,
    closedCount: 0,
    satisfactionRate: 0
  })
  const complaintSearchParams = ref<Partial<ComplaintQuery>>({
    complaintType: undefined,
    source: undefined,
    status: undefined,
    priority: undefined,
    orgId: undefined,
    submitterName: '',
    startTime: undefined,
    endTime: undefined
  })

  // ==================== 派单记录状态 ====================
  const dispatchList = ref<DispatchRecord[]>([])
  const dispatchTotal = ref(0)
  const dispatchPageNum = ref(1)
  const dispatchPageSize = ref(10)
  const dispatchLoading = ref(false)
  const dispatchSearchParams = ref<Partial<DispatchQuery>>({
    dispatchType: undefined,
    status: undefined,
    orgId: undefined,
    handlerName: '',
    startTime: undefined,
    endTime: undefined
  })

  // ==================== 弹窗状态 ====================
  /** 详情弹窗 */
  const detailVisible = ref(false)
  const detailType = ref<'review' | 'complaint' | 'dispatch' | null>(null)
  const currentDetailId = ref<number | null>(null)

  /** 派单弹窗 */
  const dispatchFormVisible = ref(false)
  const dispatchComplaintId = ref<number | null>(null)

  /** 处理弹窗 */
  const processFormVisible = ref(false)
  const processDispatchId = ref<number | null>(null)

  /** 回复弹窗 */
  const replyFormVisible = ref(false)
  const replyReviewId = ref<number | null>(null)

  /** 是否已初始化 */
  const initialized = ref(false)

  // ==================== Tab 切换 ====================
  const setActiveTab = async (tab: EvaluationTabType) => {
    activeTab.value = tab
    switch (tab) {
      case 'review':
        await fetchReviewList()
        break
      case 'complaint':
        await fetchComplaintList()
        break
      case 'dispatch':
        await fetchDispatchList()
        break
    }
  }

  // ==================== 评价相关方法 ====================
  /** 获取评价列表 */
  const fetchReviewList = async () => {
    reviewLoading.value = true
    try {
      const params: ReviewQuery = {
        pageNum: reviewPageNum.value,
        pageSize: reviewPageSize.value,
        ...reviewSearchParams.value
      }
      const res = await reviewApi.getList(params)
      if (res.code === 'SUCCESS' && res.data) {
        reviewList.value = res.data.list
        reviewTotal.value = res.data.total
      }
    } catch (error: any) {
      ElMessage.error(error.message || '获取评价列表失败')
    } finally {
      reviewLoading.value = false
    }
  }

  /** 获取评价统计（暂不实现） */
  const fetchReviewStatistics = async () => {
    // 统计接口暂不实现，使用默认值
    reviewStatistics.value = {
      totalReviews: 0,
      avgScore: 0,
      fiveStarCount: 0,
      fourStarCount: 0,
      threeStarCount: 0,
      twoStarCount: 0,
      oneStarCount: 0,
      satisfactionRate: 0
    }
  }

  /** 搜索评价 */
  const searchReviews = async (params: Partial<ReviewQuery>) => {
    reviewSearchParams.value = { ...reviewSearchParams.value, ...params }
    reviewPageNum.value = 1
    await fetchReviewList()
  }

  /** 重置评价搜索 */
  const resetReviewSearch = async () => {
    reviewSearchParams.value = {
      source: undefined,
      orgId: undefined,
      keyword: '',
      overallScore: undefined,
      startTime: undefined,
      endTime: undefined
    }
    reviewPageNum.value = 1
    await fetchReviewList()
  }

  /** 评价分页切换 */
  const changeReviewPage = async (page: number) => {
    reviewPageNum.value = page
    await fetchReviewList()
  }

  const changeReviewPageSize = async (size: number) => {
    reviewPageSize.value = size
    reviewPageNum.value = 1
    await fetchReviewList()
  }

  // ==================== 投诉相关方法 ====================
  /** 获取投诉列表 */
  const fetchComplaintList = async () => {
    complaintLoading.value = true
    try {
      const params: ComplaintQuery = {
        pageNum: complaintPageNum.value,
        pageSize: complaintPageSize.value,
        ...complaintSearchParams.value
      }
      const res = await complaintApi.getList(params)
      if (res.code === 'SUCCESS' && res.data) {
        complaintList.value = res.data.list
        complaintTotal.value = res.data.total
      }
    } catch (error: any) {
      ElMessage.error(error.message || '获取投诉列表失败')
    } finally {
      complaintLoading.value = false
    }
  }

  /** 获取投诉统计（暂不实现） */
  const fetchComplaintStatistics = async () => {
    // 统计接口暂不实现，使用默认值
    complaintStatistics.value = {
      totalComplaints: 0,
      pendingCount: 0,
      dispatchedCount: 0,
      processingCount: 0,
      closedCount: 0,
      satisfactionRate: 0
    }
  }

  /** 搜索投诉 */
  const searchComplaints = async (params: Partial<ComplaintQuery>) => {
    complaintSearchParams.value = { ...complaintSearchParams.value, ...params }
    complaintPageNum.value = 1
    await fetchComplaintList()
  }

  /** 重置投诉搜索 */
  const resetComplaintSearch = async () => {
    complaintSearchParams.value = {
      complaintType: undefined,
      source: undefined,
      status: undefined,
      priority: undefined,
      orgId: undefined,
      submitterName: '',
      startTime: undefined,
      endTime: undefined
    }
    complaintPageNum.value = 1
    await fetchComplaintList()
  }

  /** 投诉分页切换 */
  const changeComplaintPage = async (page: number) => {
    complaintPageNum.value = page
    await fetchComplaintList()
  }

  const changeComplaintPageSize = async (size: number) => {
    complaintPageSize.value = size
    complaintPageNum.value = 1
    await fetchComplaintList()
  }

  // ==================== 派单相关方法 ====================
  /** 获取派单列表 */
  const fetchDispatchList = async () => {
    dispatchLoading.value = true
    try {
      const params: DispatchQuery = {
        pageNum: dispatchPageNum.value,
        pageSize: dispatchPageSize.value,
        ...dispatchSearchParams.value
      }
      const res = await dispatchApi.getList(params)
      if (res.code === 'SUCCESS' && res.data) {
        dispatchList.value = res.data.list
        dispatchTotal.value = res.data.total
      }
    } catch (error: any) {
      ElMessage.error(error.message || '获取派单列表失败')
    } finally {
      dispatchLoading.value = false
    }
  }

  /** 搜索派单 */
  const searchDispatches = async (params: Partial<DispatchQuery>) => {
    dispatchSearchParams.value = { ...dispatchSearchParams.value, ...params }
    dispatchPageNum.value = 1
    await fetchDispatchList()
  }

  /** 重置派单搜索 */
  const resetDispatchSearch = async () => {
    dispatchSearchParams.value = {
      dispatchType: undefined,
      status: undefined,
      orgId: undefined,
      handlerName: '',
      startTime: undefined,
      endTime: undefined
    }
    dispatchPageNum.value = 1
    await fetchDispatchList()
  }

  /** 派单分页切换 */
  const changeDispatchPage = async (page: number) => {
    dispatchPageNum.value = page
    await fetchDispatchList()
  }

  const changeDispatchPageSize = async (size: number) => {
    dispatchPageSize.value = size
    dispatchPageNum.value = 1
    await fetchDispatchList()
  }

  // ==================== 派单操作 ====================
  /** 自动派单 */
  const autoDispatch = async (complaintId: number) => {
    try {
      await ElMessageBox({
        title: '',
        message: renderConfirmMessage('确认自动派单', '系统将根据规则自动分配处理人，确认自动派单？'),
        customClass: 'evaluation-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认派单',
        cancelButtonText: '取消',
      })

      const res = await complaintApi.dispatch(complaintId, { dispatchType: 'auto' })
      if (res.code === 'SUCCESS') {
        const { complaintNo, handlerName, deadline } = res.data || {}
        const deadlineStr = deadline ? `，处理截止 ${deadline.replace('T', ' ')}` : ''
        ElMessage.success(`【${complaintNo}】已自动派单至 ${handlerName}${deadlineStr}`)
        await fetchComplaintList()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // 错误消息已在 API 拦截器中显示，此处不再重复
      }
    }
  }

  /** 打开人工派单弹窗 */
  const openDispatchForm = async (complaintId: number) => {
    await fetchDispatchEmployees()
    dispatchComplaintId.value = complaintId
    dispatchFormVisible.value = true
  }

  /** 关闭派单弹窗 */
  const closeDispatchForm = () => {
    dispatchFormVisible.value = false
    dispatchComplaintId.value = null
  }

  /** 提交人工派单 */
  const submitDispatch = async (data: DispatchForm) => {
    if (!dispatchComplaintId.value) return
    try {
      const res = await complaintApi.dispatch(dispatchComplaintId.value, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('派单成功')
        closeDispatchForm()
        await fetchComplaintList()
      }
    } catch (error: any) {
      // 全局拦截器已弹出错误提示，此处不再重复
    }
  }

  // ==================== 处理工单 ====================
  /** 打开处理弹窗 */
  const openProcessForm = (dispatchId: number) => {
    processDispatchId.value = dispatchId
    processFormVisible.value = true
  }

  /** 关闭处理弹窗 */
  const closeProcessForm = () => {
    processFormVisible.value = false
    processDispatchId.value = null
  }

  /** 提交处理 */
  const submitProcess = async (data: ProcessWorkOrderForm) => {
    if (!processDispatchId.value) return
    try {
      const res = await dispatchApi.process(processDispatchId.value, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('处理成功')
        closeProcessForm()
        await fetchDispatchList()
      }
    } catch (error: any) {
      // 全局拦截器已弹出错误提示，此处不再重复
    }
  }

  // ==================== 详情弹窗 ====================
  /** 打开评价详情 */
  const openReviewDetail = (id: number) => {
    detailType.value = 'review'
    currentDetailId.value = id
    detailVisible.value = true
  }

  /** 打开投诉详情 */
  const openComplaintDetail = (id: number) => {
    detailType.value = 'complaint'
    currentDetailId.value = id
    detailVisible.value = true
  }

  /** 打开派单详情 */
  const openDispatchDetail = (id: number) => {
    detailType.value = 'dispatch'
    currentDetailId.value = id
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = () => {
    detailVisible.value = false
    currentDetailId.value = null
    detailType.value = null
  }

  /** 获取人工派单处理人候选（全量员工） */
  const fetchDispatchEmployees = async () => {
    try {
      const pageSize = 200
      let pageNum = 1
      let allEmployees: Employee[] = []

      while (true) {
        const res = await employeeApi.getList({ pageNum, pageSize, status: 'active', accountStatus: 'active' })
        if (res.code !== 'SUCCESS' || !res.data) break

        const list = res.data.list || []
        allEmployees = allEmployees.concat(list)

        if (list.length < pageSize || allEmployees.length >= res.data.total) {
          break
        }
        pageNum += 1
      }

      dispatchEmployees.value = allEmployees
    } catch (error) {
      console.error('获取派单处理人失败:', error)
      dispatchEmployees.value = []
    }
  }

  // ==================== 回复评价 ====================
  /** 打开回复弹窗 */
  const openReplyForm = (id: number) => {
    replyReviewId.value = id
    replyFormVisible.value = true
  }

  /** 关闭回复弹窗 */
  const closeReplyForm = () => {
    replyFormVisible.value = false
    replyReviewId.value = null
  }

  /** 提交回复 */
  const submitReply = async (data: ReviewReplyForm, onSuccess?: () => void) => {
    if (!replyReviewId.value) return
    try {
      const res = await reviewApi.reply(replyReviewId.value, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('回复成功')
        onSuccess?.()
        closeReplyForm()
        await fetchReviewList()
      }
    } catch (error: any) {
      // 全局拦截器已弹出错误提示
    }
  }

  // ==================== 导出 ====================
  /** 导出评价 */
  const exportReviews = async () => {
    try {
      const params: Partial<ReviewQuery> = {
        ...reviewSearchParams.value
      }
      await reviewApi.exportList(params)
      ElMessage.success('导出成功')
    } catch (error: any) {
      ElMessage.error(error.message || '导出评价失败')
    }
  }

  /** 导出投诉 */
  const exportComplaints = async () => {
    try {
      const params: Partial<ComplaintQuery> = {
        ...complaintSearchParams.value
      }
      await complaintApi.exportList(params)
      ElMessage.success('导出成功')
    } catch (error: any) {
      ElMessage.error(error.message || '导出投诉失败')
    }
  }

  // ==================== 初始化 ====================
  /** 初始化 */
  const init = async () => {
    if (initialized.value) return
    await Promise.all([
      fetchReviewList(),
      fetchComplaintList(),
      fetchDispatchList(),
      fetchDispatchEmployees()
    ])
    initialized.value = true
  }

  /** 根据 Tab 初始化数据 */
  const initByTab = async (tab: EvaluationTabType) => {
    activeTab.value = tab
    switch (tab) {
      case 'review':
        await fetchReviewList()
        break
      case 'complaint':
        await fetchComplaintList()
        break
      case 'dispatch':
        await fetchDispatchList()
        break
    }
  }

  return {
    // 通用状态
    activeTab,
    handlers,

    // 评价状态
    reviewList,
    reviewTotal,
    reviewPageNum,
    reviewPageSize,
    reviewLoading,
    reviewStatistics,
    reviewSearchParams,

    // 投诉状态
    complaintList,
    complaintTotal,
    complaintPageNum,
    complaintPageSize,
    complaintLoading,
    complaintStatistics,
    complaintSearchParams,

    // 派单状态
    dispatchList,
    dispatchTotal,
    dispatchPageNum,
    dispatchPageSize,
    dispatchLoading,
    dispatchSearchParams,

    // 弹窗状态
    detailVisible,
    detailType,
    currentDetailId,
    dispatchFormVisible,
    dispatchComplaintId,
    processFormVisible,
    processDispatchId,
    replyFormVisible,
    replyReviewId,
    initialized,

    // Tab 操作
    setActiveTab,

    // 评价方法
    fetchReviewList,
    fetchReviewStatistics,
    searchReviews,
    resetReviewSearch,
    changeReviewPage,
    changeReviewPageSize,

    // 投诉方法
    fetchComplaintList,
    fetchComplaintStatistics,
    searchComplaints,
    resetComplaintSearch,
    changeComplaintPage,
    changeComplaintPageSize,

    // 派单方法
    fetchDispatchList,
    searchDispatches,
    resetDispatchSearch,
    changeDispatchPage,
    changeDispatchPageSize,

    // 派单操作
    autoDispatch,
    openDispatchForm,
    closeDispatchForm,
    submitDispatch,

    // 处理工单
    openProcessForm,
    closeProcessForm,
    submitProcess,

    // 回复评价
    openReplyForm,
    closeReplyForm,
    submitReply,

    // 导出
    exportReviews,
    exportComplaints,

    // 详情
    openReviewDetail,
    openComplaintDetail,
    openDispatchDetail,
    closeDetail,

    // 初始化
    init,
    initByTab
  }
})
