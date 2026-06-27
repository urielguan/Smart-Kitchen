<script setup lang="ts">
import { ref, onMounted, onActivated, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useEvaluationStore } from '@/stores/modules/evaluation'
import { EVALUATION_PERMISSIONS } from '@/constants/permission'
import {
  REVIEW_SOURCE_OPTIONS,
  SCORE_OPTIONS,
  COMPLAINT_TYPE_OPTIONS,
  COMPLAINT_SOURCE_OPTIONS,
  COMPLAINT_STATUS_OPTIONS,
  DISPATCH_TYPE_OPTIONS,
  DISPATCH_STATUS_OPTIONS
} from '@/constants/evaluation'
import type { EvaluationTabType } from '@/constants/evaluation'
import type { MealReview, Complaint, DispatchRecord, DispatchForm, ProcessWorkOrderForm } from '@/types/evaluation'

// 组件导入
import ReviewTable from '@/components/business/evaluation/ReviewTable.vue'
import ReviewDetail from '@/components/business/evaluation/ReviewDetail.vue'
import ReviewReplyForm from '@/components/business/evaluation/ReviewReplyForm.vue'
import ComplaintTable from '@/components/business/evaluation/ComplaintTable.vue'
import ComplaintDetail from '@/components/business/evaluation/ComplaintDetail.vue'
import DispatchTable from '@/components/business/evaluation/DispatchTable.vue'
import DispatchFormModal from '@/components/business/evaluation/DispatchForm.vue'
import ProcessForm from '@/components/business/evaluation/ProcessForm.vue'
import OrgTreeSelect from '@/components/business/org/OrgTreeSelect.vue'

const evaluationStore = useEvaluationStore()
const route = useRoute()
const evaluationActivatedOnce = ref(false)
const lastDashboardRouteKey = ref('')
const dashboardEntryTitle = computed(() => {
  if (route.query.from !== 'dashboard') return ''
  if (route.query.metric === 'meal-review') return '来自数据监管看板：就餐满意度 / 评价记录联动'
  return '来自数据监管看板'
})

// ==================== 评价搜索表单 ====================
const reviewSearchForm = ref({
  source: evaluationStore.reviewSearchParams.source as string | undefined,
  orgId: evaluationStore.reviewSearchParams.orgId as number | undefined,
  keyword: evaluationStore.reviewSearchParams.keyword || '',
  overallScore: evaluationStore.reviewSearchParams.overallScore as number | undefined,
  timeRange: (
    evaluationStore.reviewSearchParams.startTime && evaluationStore.reviewSearchParams.endTime
      ? [evaluationStore.reviewSearchParams.startTime, evaluationStore.reviewSearchParams.endTime]
      : []
  ) as string[]
})

// ==================== 投诉搜索表单 ====================
const complaintSearchForm = ref({
  complaintType: evaluationStore.complaintSearchParams.complaintType as string | undefined,
  source: evaluationStore.complaintSearchParams.source as string | undefined,
  status: evaluationStore.complaintSearchParams.status as string | undefined,
  priority: evaluationStore.complaintSearchParams.priority as string | undefined,
  orgId: evaluationStore.complaintSearchParams.orgId as number | undefined,
  submitterName: evaluationStore.complaintSearchParams.submitterName || '',
  timeRange: (
    evaluationStore.complaintSearchParams.startTime && evaluationStore.complaintSearchParams.endTime
      ? [evaluationStore.complaintSearchParams.startTime, evaluationStore.complaintSearchParams.endTime]
      : []
  ) as string[]
})

// ==================== 派单搜索表单 ====================
const dispatchSearchForm = ref({
  dispatchType: evaluationStore.dispatchSearchParams.dispatchType as string | undefined,
  status: evaluationStore.dispatchSearchParams.status as string | undefined,
  orgId: evaluationStore.dispatchSearchParams.orgId as number | undefined,
  handlerName: evaluationStore.dispatchSearchParams.handlerName || '',
  timeRange: (
    evaluationStore.dispatchSearchParams.startTime && evaluationStore.dispatchSearchParams.endTime
      ? [evaluationStore.dispatchSearchParams.startTime, evaluationStore.dispatchSearchParams.endTime]
      : []
  ) as string[]
})

// ==================== 初始化 ====================
onMounted(async () => {
  await evaluationStore.init()
  await applyDashboardRouteQuery()
})

onActivated(async () => {
  if (!evaluationActivatedOnce.value) {
    evaluationActivatedOnce.value = true
    return
  }
  if (await applyDashboardRouteQuery()) {
    return
  }
  await evaluationStore.setActiveTab(evaluationStore.activeTab)
})

// ==================== Tab 切换 ====================
// 监听 route query 变化，支持从其他页面跳转时切换 tab
watch(() => route.query.tab, (tab) => {
  if (!tab) return
  const t = tab as string
  if (['review', 'complaint', 'dispatch'].includes(t)) {
    evaluationStore.setActiveTab(t as EvaluationTabType)
  }
})
const handleTabChange = (tab: EvaluationTabType) => {
  evaluationStore.setActiveTab(tab)
}

const applyDashboardRouteQuery = async () => {
  if (route.query.from !== 'dashboard') {
    lastDashboardRouteKey.value = ''
    return false
  }

  const routeKey = JSON.stringify(route.query)
  const openDetailOnce = route.query.autoOpen === '1' && lastDashboardRouteKey.value !== routeKey
  const tab = route.query.tab === 'complaint' || route.query.tab === 'dispatch' ? route.query.tab : 'review'
  await evaluationStore.setActiveTab(tab)

  if (tab === 'review') {
    reviewSearchForm.value = {
      source: typeof route.query.source === 'string' ? route.query.source : undefined,
      orgId: undefined,
      keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
      overallScore: typeof route.query.overallScore === 'string' ? Number(route.query.overallScore) : undefined,
      timeRange: (
        typeof route.query.startDate === 'string' && typeof route.query.endDate === 'string'
          ? [route.query.startDate, route.query.endDate]
          : []
      ) as string[]
    }
    await handleReviewSearch()
    if (openDetailOnce && evaluationStore.reviewList[0]) {
      handleReviewDetail(evaluationStore.reviewList[0])
    }
  } else if (tab === 'complaint') {
    complaintSearchForm.value = {
      complaintType: typeof route.query.complaintType === 'string' ? route.query.complaintType : undefined,
      source: typeof route.query.source === 'string' ? route.query.source : undefined,
      status: typeof route.query.status === 'string' ? route.query.status : undefined,
      priority: typeof route.query.priority === 'string' ? route.query.priority : undefined,
      orgId: undefined,
      submitterName: typeof route.query.submitterName === 'string' ? route.query.submitterName : '',
      timeRange: (
        typeof route.query.startDate === 'string' && typeof route.query.endDate === 'string'
          ? [route.query.startDate, route.query.endDate]
          : []
      ) as string[]
    }
    await handleComplaintSearch()
    if (openDetailOnce && evaluationStore.complaintList[0]) {
      handleComplaintDetail(evaluationStore.complaintList[0])
    }
  } else {
    dispatchSearchForm.value = {
      dispatchType: typeof route.query.dispatchType === 'string' ? route.query.dispatchType : undefined,
      status: typeof route.query.status === 'string' ? route.query.status : undefined,
      orgId: undefined,
      handlerName: typeof route.query.handlerName === 'string' ? route.query.handlerName : '',
      timeRange: (
        typeof route.query.startDate === 'string' && typeof route.query.endDate === 'string'
          ? [route.query.startDate, route.query.endDate]
          : []
      ) as string[]
    }
    await handleDispatchSearch()
    if (openDetailOnce && evaluationStore.dispatchList[0]) {
      handleDispatchDetail(evaluationStore.dispatchList[0])
    }
  }

  lastDashboardRouteKey.value = routeKey
  return true
}

// ==================== 评价相关方法 ====================
/** 搜索评价 */
const handleReviewSearch = () => {
  const hasTimeRange = reviewSearchForm.value.timeRange && reviewSearchForm.value.timeRange.length === 2
  const params: Record<string, any> = {
    source: reviewSearchForm.value.source,
    orgId: reviewSearchForm.value.orgId,
    keyword: reviewSearchForm.value.keyword,
    overallScore: reviewSearchForm.value.overallScore,
    startTime: hasTimeRange ? reviewSearchForm.value.timeRange[0] : undefined,
    endTime: hasTimeRange ? reviewSearchForm.value.timeRange[1] : undefined
  }

  return evaluationStore.searchReviews(params)
}

/** 重置评价搜索 */
const handleReviewReset = () => {
  reviewSearchForm.value = {
    source: undefined,
    orgId: undefined,
    keyword: '',
    overallScore: undefined,
    timeRange: []
  }
  evaluationStore.resetReviewSearch()
}

/** 评价详情 */
const handleReviewDetail = (row: MealReview) => {
  evaluationStore.openReviewDetail(row.id)
}

/** 回复评价 */
const handleReply = (row: MealReview) => {
  evaluationStore.openReplyForm(row.id)
}

/** 提交回复 */
const handleReplySubmit = (data: { replyContent: string }, onSuccess: () => void) => {
  evaluationStore.submitReply(data, onSuccess)
}

/** 导出评价loading */
const reviewExporting = ref(false)

/** 导出评价 */
const handleExportReviews = async () => {
  reviewExporting.value = true
  try {
    await evaluationStore.exportReviews()
  } finally {
    reviewExporting.value = false
  }
}

/** 评价分页 */
const handleReviewPageChange = (page: number) => {
  evaluationStore.changeReviewPage(page)
}

const handleReviewSizeChange = (size: number) => {
  evaluationStore.changeReviewPageSize(size)
}

// ==================== 投诉相关方法 ====================
/** 搜索投诉 */
const handleComplaintSearch = () => {
  const hasTimeRange = complaintSearchForm.value.timeRange && complaintSearchForm.value.timeRange.length === 2
  const params: Record<string, any> = {
    complaintType: complaintSearchForm.value.complaintType,
    source: complaintSearchForm.value.source,
    status: complaintSearchForm.value.status,
    priority: complaintSearchForm.value.priority,
    orgId: complaintSearchForm.value.orgId,
    submitterName: complaintSearchForm.value.submitterName,
    startTime: hasTimeRange ? complaintSearchForm.value.timeRange[0] : undefined,
    endTime: hasTimeRange ? complaintSearchForm.value.timeRange[1] : undefined
  }

  return evaluationStore.searchComplaints(params)
}

/** 重置投诉搜索 */
const handleComplaintReset = () => {
  complaintSearchForm.value = {
    complaintType: undefined,
    source: undefined,
    status: undefined,
    priority: undefined,
    orgId: undefined,
    submitterName: '',
    timeRange: []
  }
  evaluationStore.resetComplaintSearch()
}

/** 投诉详情 */
const handleComplaintDetail = (row: Complaint) => {
  evaluationStore.openComplaintDetail(row.id)
}

/** 自动派单 */
const handleAutoDispatch = (row: Complaint) => {
  evaluationStore.autoDispatch(row.id)
}

/** 导出投诉loading */
const complaintExporting = ref(false)

/** 导出投诉 */
const handleExportComplaints = async () => {
  complaintExporting.value = true
  try {
    await evaluationStore.exportComplaints()
  } finally {
    complaintExporting.value = false
  }
}

/** 人工派单 */
const handleManualDispatch = (row: Complaint) => {
  evaluationStore.openDispatchForm(row.id)
}

/** 提交派单 */
const handleDispatchSubmit = (data: DispatchForm) => {
  evaluationStore.submitDispatch(data)
}

/** 投诉分页 */
const handleComplaintPageChange = (page: number) => {
  evaluationStore.changeComplaintPage(page)
}

const handleComplaintSizeChange = (size: number) => {
  evaluationStore.changeComplaintPageSize(size)
}

// ==================== 派单相关方法 ====================
/** 搜索派单 */
const handleDispatchSearch = () => {
  const hasTimeRange = dispatchSearchForm.value.timeRange && dispatchSearchForm.value.timeRange.length === 2
  const params: Record<string, any> = {
    dispatchType: dispatchSearchForm.value.dispatchType,
    status: dispatchSearchForm.value.status,
    orgId: dispatchSearchForm.value.orgId,
    handlerName: dispatchSearchForm.value.handlerName,
    startTime: hasTimeRange ? dispatchSearchForm.value.timeRange[0] : undefined,
    endTime: hasTimeRange ? dispatchSearchForm.value.timeRange[1] : undefined
  }

  return evaluationStore.searchDispatches(params)
}

/** 重置派单搜索 */
const handleDispatchReset = () => {
  dispatchSearchForm.value = {
    dispatchType: undefined,
    status: undefined,
    orgId: undefined,
    handlerName: '',
    timeRange: []
  }
  evaluationStore.resetDispatchSearch()
}

/** 派单详情 - 复用投诉详情弹窗 */
const handleDispatchDetail = (row: DispatchRecord) => {
  // 派单详情实际上就是显示关联的投诉详情
  evaluationStore.openComplaintDetail(row.complaintId)
}

/** 处理工单 */
const handleProcess = (row: DispatchRecord) => {
  evaluationStore.openProcessForm(row.id)
}

/** 提交处理 */
const handleProcessSubmit = (data: ProcessWorkOrderForm) => {
  evaluationStore.submitProcess(data)
}

/** 派单分页 */
const handleDispatchPageChange = (page: number) => {
  evaluationStore.changeDispatchPage(page)
}

const handleDispatchSizeChange = (size: number) => {
  evaluationStore.changeDispatchPageSize(size)
}
</script>

<template>
  <div class="evaluation-page">
    <el-alert
      v-if="dashboardEntryTitle"
      :title="dashboardEntryTitle"
      type="warning"
      :closable="false"
      show-icon
      class="dashboard-entry-alert"
      description="已按看板当前时间范围筛出真实评价记录，可直接核对评分、投诉和派单闭环情况。"
    />

    <!-- Tab 切换 -->
    <div class="tab-header">
      <el-radio-group :model-value="evaluationStore.activeTab" @change="handleTabChange">
        <el-radio-button value="review">评价列表</el-radio-button>
        <el-radio-button value="complaint">投诉列表</el-radio-button>
        <el-radio-button value="dispatch">派单记录</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ==================== 评价列表 Tab ==================== -->
    <template v-if="evaluationStore.activeTab === 'review'">
      <!-- 搜索工具栏 -->
      <div class="toolbar">
        <el-row :gutter="10" align="middle">
          <el-col :span="4">
            <el-input
              v-model="reviewSearchForm.keyword"
              placeholder="评价人/菜品名称"
              clearable
              @keyup.enter="handleReviewSearch"
            />
          </el-col>
          <el-col :span="3">
            <el-select
              v-model="reviewSearchForm.source"
              placeholder="来源"
              clearable
            >
              <el-option
                v-for="item in REVIEW_SOURCE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-col>
          <el-col :span="3">
            <el-select
              v-model="reviewSearchForm.overallScore"
              placeholder="评分"
              clearable
            >
              <el-option
                v-for="item in SCORE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-col>
          <el-col :span="3">
            <OrgTreeSelect
              v-model="reviewSearchForm.orgId"
              :active-only="true"
              placeholder="全部门店"
            />
          </el-col>
          <el-col :span="5">
            <el-date-picker
              v-model="reviewSearchForm.timeRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-col>
          <el-col :span="6" style="text-align: right">
            <el-button class="btn-search" @click="handleReviewSearch">查询</el-button>
            <el-button class="btn-reset" @click="handleReviewReset">重置</el-button>
          </el-col>
        </el-row>
      </div>

      <!-- 操作栏 + 数据表格 -->
      <div class="table-wrapper">
        <div class="table-header">
          <el-button v-permission="EVALUATION_PERMISSIONS.EXPORT" class="btn-export" :loading="reviewExporting" @click="handleExportReviews">导出</el-button>
        </div>
      <ReviewTable
        :data="evaluationStore.reviewList"
        :loading="evaluationStore.reviewLoading"
        @detail="handleReviewDetail"
        @reply="handleReply"
      />

      <!-- 分页 -->
      <div class="pagination">
        <span class="total">共 {{ evaluationStore.reviewTotal }} 项数据</span>
        <el-pagination
          v-model:current-page="evaluationStore.reviewPageNum"
          v-model:page-size="evaluationStore.reviewPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="evaluationStore.reviewTotal"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="handleReviewPageChange"
          @size-change="handleReviewSizeChange"
        />
      </div>
      </div>
    </template>

    <!-- ==================== 投诉列表 Tab ==================== -->
    <template v-else-if="evaluationStore.activeTab === 'complaint'">
      <!-- 搜索工具栏 -->
      <div class="toolbar">
        <el-row :gutter="10" align="middle">
          <el-col :span="3">
            <el-input
              v-model="complaintSearchForm.submitterName"
              placeholder="投诉人"
              clearable
              @keyup.enter="handleComplaintSearch"
            />
          </el-col>
          <el-col :span="3">
            <el-select
              v-model="complaintSearchForm.complaintType"
              placeholder="投诉类型"
              clearable
            >
              <el-option
                v-for="item in COMPLAINT_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-col>
          <el-col :span="3">
            <el-select
              v-model="complaintSearchForm.source"
              placeholder="来源"
              clearable
            >
              <el-option
                v-for="item in COMPLAINT_SOURCE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-col>
          <el-col :span="3">
            <el-select
              v-model="complaintSearchForm.status"
              placeholder="处理状态"
              clearable
            >
              <el-option
                v-for="item in COMPLAINT_STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-col>
          <el-col :span="3">
            <OrgTreeSelect
              v-model="complaintSearchForm.orgId"
              :active-only="true"
              placeholder="全部门店"
            />
          </el-col>
          <el-col :span="5">
            <el-date-picker
              v-model="complaintSearchForm.timeRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-col>
          <el-col :span="4" style="text-align: right">
            <el-button class="btn-search" @click="handleComplaintSearch">查询</el-button>
            <el-button class="btn-reset" @click="handleComplaintReset">重置</el-button>
          </el-col>
        </el-row>
      </div>

      <!-- 数据表格 -->
      <div class="table-wrapper">
        <div class="table-header">
          <el-button v-permission="EVALUATION_PERMISSIONS.COMPLAINT_EXPORT" class="btn-export" :loading="complaintExporting" @click="handleExportComplaints">导出</el-button>
        </div>
      <ComplaintTable
        :data="evaluationStore.complaintList"
        :loading="evaluationStore.complaintLoading"
        @detail="handleComplaintDetail"
        @auto-dispatch="handleAutoDispatch"
        @manual-dispatch="handleManualDispatch"
      />

      <!-- 分页 -->
      <div class="pagination">
        <span class="total">共 {{ evaluationStore.complaintTotal }} 项数据</span>
        <el-pagination
          v-model:current-page="evaluationStore.complaintPageNum"
          v-model:page-size="evaluationStore.complaintPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="evaluationStore.complaintTotal"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="handleComplaintPageChange"
          @size-change="handleComplaintSizeChange"
        />
      </div>
      </div>
    </template>

    <!-- ==================== 派单记录 Tab ==================== -->
    <template v-else-if="evaluationStore.activeTab === 'dispatch'">
      <!-- 搜索工具栏 -->
      <div class="toolbar">
        <el-row :gutter="10" align="middle">
          <el-col :span="3">
            <el-input
              v-model="dispatchSearchForm.handlerName"
              placeholder="处理人"
              clearable
              @keyup.enter="handleDispatchSearch"
            />
          </el-col>
          <el-col :span="3">
            <el-select
              v-model="dispatchSearchForm.dispatchType"
              placeholder="派单方式"
              clearable
            >
              <el-option
                v-for="item in DISPATCH_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-col>
          <el-col :span="3">
            <el-select
              v-model="dispatchSearchForm.status"
              placeholder="状态"
              clearable
            >
              <el-option
                v-for="item in DISPATCH_STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-col>
          <el-col :span="3">
            <OrgTreeSelect
              v-model="dispatchSearchForm.orgId"
              :active-only="true"
              placeholder="全部门店"
            />
          </el-col>
          <el-col :span="5">
            <el-date-picker
              v-model="dispatchSearchForm.timeRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-col>
          <el-col :span="7" style="text-align: right">
            <el-button class="btn-search" @click="handleDispatchSearch">查询</el-button>
            <el-button class="btn-reset" @click="handleDispatchReset">重置</el-button>
          </el-col>
        </el-row>
      </div>

      <!-- 数据表格 -->
      <div class="table-wrapper" style="padding-top: 16px">
      <DispatchTable
        :data="evaluationStore.dispatchList"
        :loading="evaluationStore.dispatchLoading"
        @detail="handleDispatchDetail"
        @process="handleProcess"
      />

      <!-- 分页 -->
      <div class="pagination">
        <span class="total">共 {{ evaluationStore.dispatchTotal }} 项数据</span>
        <el-pagination
          v-model:current-page="evaluationStore.dispatchPageNum"
          v-model:page-size="evaluationStore.dispatchPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="evaluationStore.dispatchTotal"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="handleDispatchPageChange"
          @size-change="handleDispatchSizeChange"
        />
      </div>
      </div>
    </template>

    <!-- ==================== 弹窗 ==================== -->
    <!-- 评价详情弹窗 -->
    <ReviewDetail
      v-if="evaluationStore.detailType === 'review'"
      v-model="evaluationStore.detailVisible"
      :review-id="evaluationStore.currentDetailId"
    />

    <!-- 投诉详情弹窗 -->
    <ComplaintDetail
      v-if="evaluationStore.detailType === 'complaint'"
      v-model="evaluationStore.detailVisible"
      :complaint-id="evaluationStore.currentDetailId"
    />

    <!-- 派单弹窗 -->
    <DispatchFormModal
      v-model="evaluationStore.dispatchFormVisible"
      :handlers="evaluationStore.handlers"
      @submit="handleDispatchSubmit"
    />

    <!-- 处理工单弹窗 -->
    <ProcessForm
      v-model="evaluationStore.processFormVisible"
      :dispatch-id="evaluationStore.processDispatchId"
      @submit="handleProcessSubmit"
    />

    <!-- 回复评价弹窗 -->
    <ReviewReplyForm
      v-model="evaluationStore.replyFormVisible"
      @submit="handleReplySubmit"
    />
  </div>
</template>

<style lang="scss" scoped>
.evaluation-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tab-header {
  background: $bg-white;
  padding: 15px 20px;
  border-radius: $border-radius-large;
  margin-bottom: 20px;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;

  :deep(.el-radio-button:first-child .el-radio-button__inner) {
    border-radius: 4px 0 0 4px;
  }

  :deep(.el-radio-button:last-child .el-radio-button__inner) {
    border-radius: 0 4px 4px 0;
  }

  :deep(.el-radio-button__inner) {
    border-radius: 0;
    border: 1px solid #DCDFE6;
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    color: #606266;
    background: #FFFFFF;
  }

  :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
    background: #7288FA;
    border-color: #7288FA;
    color: #FFFFFF;
    box-shadow: -1px 0 0 0 #7288FA;
  }
}

.table-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-header {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
  padding: 16px;
  flex-shrink: 0;

  .btn-export {
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    &:hover {
      background: #F5F7FA;
      border-color: #7288FA;
      color: #7288FA;
    }
  }
}

.toolbar {
  background: $bg-white;
  padding: 20px;
  border-radius: $border-radius-large;
  margin-bottom: 20px;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;

  .el-input,
  .el-select {
    width: 100%;
  }

  .btn-search {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    color: #fff;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-reset {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    background: #F2F4F8;
    border-color: #F2F4F8;
    border-radius: 6px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);

    &:hover {
      background: #E3E7EF;
      border-color: #E3E7EF;
      color: rgba(0, 0, 0, 0.9);
    }
  }
}

.pagination {
  padding: 16px 24px;
  background: #FFFFFF;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.6);
  }

  :deep(.el-pagination .is-active) {
    width: 32px;
    height: 32px;
    background: #7288FA;
    border-radius: 3px;
    color: #fff;
  }

  :deep(.el-pagination .el-pager li:not(.is-active)) {
    width: 32px;
    height: 32px;
    background: #FFFFFF;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    color: #000000E5;
    margin-left: 8px;
  }

  :deep(.el-pagination .el-pager li + li) {
    margin-left: 8px;
  }
}
</style>
