<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { getPlanDetail } from '@/api/modules/plan'
import { auditPlanAdjustment, exportAdjustments, getPlanAdjustmentDetail, getPlanAdjustmentList } from '@/api/modules/plan-adjustment'
import { PLAN_ADJUSTMENT_PERMISSIONS } from '@/constants/permission'
import type { AdjustItem, RecipePlanAdjustment, RecipePlanAdjustmentDetail, RecipePlanDetail } from '@/types/plan'

interface SearchForm {
  planCode: string
  adjustType?: string
  status?: string
  dateRange: string[]
}

interface SnapshotRecipe {
  recipeId: number
  recipeName: string
  categoryName?: string
  mealKey?: string
  mealType?: string
  mealName?: string
  mealExpectedCount?: number
  mealSortOrder?: number
  plannedServings?: number
  remark?: string
  sortOrder?: number
}

interface SnapshotMealSchedule {
  mealKey?: string
  mealType?: string
  mealName?: string
  expectedCount?: number
  sortOrder?: number
  recipes?: SnapshotRecipe[]
}

interface AdjustmentSnapshot {
  planCode?: string
  planDate?: string
  startDate?: string
  endDate?: string
  mealType?: string
  expectedCount?: number
  targetGroup?: string
  remark?: string
  mealSchedules?: SnapshotMealSchedule[]
  recipes?: SnapshotRecipe[]
}

const route = useRoute()
const router = useRouter()

const ADJUST_TYPE_OPTIONS = [
  { value: 'add', label: '新增菜谱' },
  { value: 'remove', label: '移除菜谱' },
  { value: 'modify', label: '调整计划' }
] as const

const STATUS_OPTIONS = [
  { value: 'pending', label: '待调整审核' },
  { value: 'approved', label: '调整已审核' },
  { value: 'rejected', label: '调整已驳回' }
] as const

const MEAL_TYPE_MAP: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  supper: '夜宵',
  custom: '自定义餐次',
  multi: '多餐次'
}

const TARGET_GROUP_MAP: Record<string, string> = {
  adult: '普通成人',
  elderly: '老年人',
  child: '儿童',
  teenager: '青少年',
  patient: '病患',
  worker: '体力劳动者'
}

const list = ref<RecipePlanAdjustment[]>([])
const loading = ref(false)
const exportLoading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const searchForm = ref<SearchForm>({
  planCode: '',
  adjustType: undefined,
  status: undefined,
  dateRange: []
})

const activePlanId = ref<number>()
const activePlanCode = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<RecipePlanAdjustmentDetail | null>(null)
const currentPlanDetail = ref<RecipePlanDetail | null>(null)

const getAdjustTypeLabel = (value?: string) => {
  return ADJUST_TYPE_OPTIONS.find(item => item.value === value)?.label || value || '-'
}

const getStatusLabel = (value?: string) => {
  return STATUS_OPTIONS.find(item => item.value === value)?.label || value || '-'
}

const getStatusTagType = (value?: string) => {
  if (value === 'approved') return 'success'
  if (value === 'rejected') return 'danger'
  return 'warning'
}

const getMealTypeLabel = (value?: string) => {
  return value ? MEAL_TYPE_MAP[value] || value : '-'
}

const getMealScheduleLabel = (mealType?: string, mealName?: string) => {
  if (mealType === 'custom') {
    return mealName || '自定义餐次'
  }
  return getMealTypeLabel(mealType)
}

const getTargetGroupLabel = (value?: string) => {
  return value ? TARGET_GROUP_MAP[value] || value : '-'
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

const parsePlanId = (value: unknown) => {
  const resolved = Array.isArray(value) ? value[0] : value
  const planId = Number(resolved)
  return Number.isFinite(planId) && planId > 0 ? planId : undefined
}

const parseSnapshot = (raw?: string): AdjustmentSnapshot | null => {
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as AdjustmentSnapshot | SnapshotRecipe[]
    if (Array.isArray(parsed)) {
      return { recipes: parsed }
    }
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

const detailSnapshot = computed(() => {
  return parseSnapshot(currentDetail.value?.afterData) || parseSnapshot(currentDetail.value?.beforeData)
})

const detailMealSchedules = computed<SnapshotMealSchedule[]>(() => {
  if (detailSnapshot.value?.mealSchedules?.length) {
    return detailSnapshot.value.mealSchedules
  }
  if (currentPlanDetail.value?.mealSchedules?.length) {
    return currentPlanDetail.value.mealSchedules.map(schedule => ({
      mealKey: schedule.mealKey,
      mealType: schedule.mealType,
      mealName: schedule.mealName,
      expectedCount: schedule.expectedCount,
      sortOrder: schedule.sortOrder,
      recipes: schedule.recipes.map(recipe => ({
        recipeId: recipe.recipeId,
        recipeName: recipe.recipeName,
        categoryName: recipe.categoryName,
        mealKey: recipe.mealKey,
        mealType: recipe.mealType,
        mealName: recipe.mealName,
        mealExpectedCount: recipe.mealExpectedCount,
        mealSortOrder: recipe.mealSortOrder,
        plannedServings: recipe.plannedServings,
        remark: recipe.remark,
        sortOrder: recipe.sortOrder
      }))
    }))
  }
  return []
})

const buildMealSummaryText = (schedules: SnapshotMealSchedule[]) =>
  schedules
    .map(schedule => getMealScheduleLabel(schedule.mealType, schedule.mealName))
    .filter(Boolean)
    .join('、') || '-'

const buildExpectedCountSummary = (schedules: SnapshotMealSchedule[]) => {
  if (!schedules.length) return '-'
  if (schedules.length === 1) {
    const count = schedules[0].expectedCount
    return count ? `${count}人` : '-'
  }
  return schedules
    .map(schedule => `${getMealScheduleLabel(schedule.mealType, schedule.mealName)} ${schedule.expectedCount || 0}人`)
    .join('、')
}

const detailRecipes = computed(() => {
  const scheduleRecipes = detailMealSchedules.value.flatMap((schedule, scheduleIndex) =>
    (schedule.recipes || []).map((recipe, recipeIndex) => ({
      ...recipe,
      mealKey: recipe.mealKey || schedule.mealKey,
      mealType: recipe.mealType || schedule.mealType,
      mealName: recipe.mealName || schedule.mealName,
      mealExpectedCount: recipe.mealExpectedCount ?? schedule.expectedCount,
      mealSortOrder: recipe.mealSortOrder ?? schedule.sortOrder ?? scheduleIndex + 1,
      sortOrder: recipe.sortOrder ?? recipeIndex + 1
    }))
  )

  if (scheduleRecipes.length) {
    return [...scheduleRecipes].sort((a, b) => {
      if ((a.mealSortOrder || 0) !== (b.mealSortOrder || 0)) {
        return (a.mealSortOrder || 0) - (b.mealSortOrder || 0)
      }
      return (a.sortOrder || 0) - (b.sortOrder || 0)
    })
  }

  const recipes = detailSnapshot.value?.recipes || []
  return [...recipes].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
})

const implementationRangeText = computed(() => {
  const startDate = detailSnapshot.value?.startDate || currentPlanDetail.value?.startDate
  const endDate = detailSnapshot.value?.endDate || currentPlanDetail.value?.endDate
  if (startDate && endDate) return `${startDate} 至 ${endDate}`
  return startDate || endDate || currentDetail.value?.planDate || '-'
})

const detailExpectedCount = computed(() => {
  if (detailMealSchedules.value.length) {
    return buildExpectedCountSummary(detailMealSchedules.value)
  }
  const count = detailSnapshot.value?.expectedCount ?? currentPlanDetail.value?.expectedCount
  return count ? `${count}人` : '-'
})

const detailMealType = computed(() => {
  if (detailMealSchedules.value.length) {
    return buildMealSummaryText(detailMealSchedules.value)
  }
  if (currentPlanDetail.value?.mealDisplayName) {
    return currentPlanDetail.value.mealDisplayName
  }
  return getMealTypeLabel(detailSnapshot.value?.mealType || currentPlanDetail.value?.mealType)
})

const detailTargetGroup = computed(() => {
  return getTargetGroupLabel(detailSnapshot.value?.targetGroup || currentPlanDetail.value?.targetGroup)
})

const stats = ref([
  { title: '全部申请', value: 0, color: '#409eff' },
  { title: '待调整审核', value: 0, color: '#e6a23c' },
  { title: '调整已审核', value: 0, color: '#67c23a' },
  { title: '调整已驳回', value: 0, color: '#f56c6c' }
])

const filterNotice = computed(() => {
  if (!activePlanId.value) return ''
  return activePlanCode.value
    ? `当前仅展示计划单号【${activePlanCode.value}】的调整申请，点击重置可恢复全部数据。`
    : '当前仅展示指定菜谱计划的调整申请，点击重置可恢复全部数据。'
})

const fetchActivePlanCode = async () => {
  if (!activePlanId.value) {
    activePlanCode.value = ''
    return
  }

  try {
    const res = await getPlanDetail(activePlanId.value)
    if (res.code === 'SUCCESS' && res.data) {
      activePlanCode.value = res.data.planCode
      return
    }
  } catch {
    // ignore
  }

  activePlanCode.value = ''
}

const fetchStats = async () => {
  try {
    const [allRes, pendingRes, approvedRes, rejectedRes] = await Promise.all([
      getPlanAdjustmentList({ pageNum: 1, pageSize: 1 }),
      getPlanAdjustmentList({ status: 'pending', pageNum: 1, pageSize: 1 }),
      getPlanAdjustmentList({ status: 'approved', pageNum: 1, pageSize: 1 }),
      getPlanAdjustmentList({ status: 'rejected', pageNum: 1, pageSize: 1 })
    ])

    stats.value[0].value = allRes.data?.total || 0
    stats.value[1].value = pendingRes.data?.total || 0
    stats.value[2].value = approvedRes.data?.total || 0
    stats.value[3].value = rejectedRes.data?.total || 0
  } catch {
    // ignore
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getPlanAdjustmentList({
      planId: activePlanId.value,
      planCode: searchForm.value.planCode || undefined,
      adjustType: searchForm.value.adjustType || undefined,
      status: searchForm.value.status || undefined,
      planDateStart: searchForm.value.dateRange[0] || undefined,
      planDateEnd: searchForm.value.dateRange[1] || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })

    if (res.code === 'SUCCESS' && res.data) {
      list.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取调整申请列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  fetchList()
}

const handleExport = async () => {
  exportLoading.value = true
  try {
    await exportAdjustments({
      planId: activePlanId.value,
      planCode: searchForm.value.planCode || undefined,
      adjustType: searchForm.value.adjustType || undefined,
      status: searchForm.value.status || undefined,
      planDateStart: searchForm.value.dateRange[0] || undefined,
      planDateEnd: searchForm.value.dateRange[1] || undefined
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exportLoading.value = false
  }
}

const handleReset = async () => {
  searchForm.value = {
    planCode: '',
    adjustType: undefined,
    status: undefined,
    dateRange: []
  }
  activePlanId.value = undefined
  activePlanCode.value = ''
  pageNum.value = 1

  if (route.query.planId) {
    await router.replace({ path: route.path, query: {} })
  }

  await fetchList()
}

const openDetail = async (row: RecipePlanAdjustment) => {
  detailVisible.value = true
  detailLoading.value = true
  currentDetail.value = null
  currentPlanDetail.value = null

  try {
    const [detailRes, planRes] = await Promise.all([
      getPlanAdjustmentDetail(row.id),
      row.planId ? getPlanDetail(row.planId).catch(() => null) : Promise.resolve(null)
    ])

    if (detailRes.code !== 'SUCCESS' || !detailRes.data) {
      throw new Error('获取详情失败')
    }

    currentDetail.value = detailRes.data
    if (planRes?.code === 'SUCCESS' && planRes.data) {
      currentPlanDetail.value = planRes.data
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  currentDetail.value = null
  currentPlanDetail.value = null
}

const auditAdjustment = async (row: RecipePlanAdjustment, status: 'approved' | 'rejected') => {
  const isApprove = status === 'approved'

  try {
    const result = await ElMessageBox.prompt(
      isApprove ? '请输入审核意见（可选）' : '请输入拒绝原因',
      isApprove ? '审核通过' : '审核拒绝',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: isApprove ? '请输入审核意见' : '请输入拒绝原因',
        inputValidator: isApprove ? undefined : (value) => !!value || '请输入拒绝原因'
      }
    )

    await auditPlanAdjustment(row.id, {
      status,
      remark: result.value || undefined
    })

    ElMessage.success(isApprove ? '审核通过成功' : '已拒绝该申请')
    if (detailVisible.value) {
      detailVisible.value = false
      closeDetail()
    }
    await Promise.all([fetchList(), fetchStats()])
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') return
    ElMessage.error(error.message || '审核失败')
  }
}

const syncRouteFilter = async () => {
  activePlanId.value = parsePlanId(route.query.planId)
  await fetchActivePlanCode()
  await fetchList()
}

watch(
  () => route.query.planId,
  async () => {
    await syncRouteFilter()
  }
)

onMounted(async () => {
  await Promise.all([syncRouteFilter(), fetchStats()])
})

const visibleAdjustItems = computed<AdjustItem[]>(() => currentDetail.value?.adjustItems || [])
</script>

<template>
  <div class="adjustment-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">菜谱计划调整管理</h1>
        <p class="page-subtitle">查看并审核菜谱计划调整申请</p>
      </div>
      <el-button
        v-permission="PLAN_ADJUSTMENT_PERMISSIONS.EXPORT"
        :loading="exportLoading"
        @click="handleExport"
      >
        <el-icon><Download /></el-icon>
        导出
      </el-button>
    </div>

    <div class="stats-row">
      <div
        v-for="item in stats"
        :key="item.title"
        class="stat-card"
        :style="{ '--accent': item.color }"
      >
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-title">{{ item.title }}</div>
      </div>
    </div>

    <div class="search-card">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="计划单号">
          <el-input
            v-model="searchForm.planCode"
            placeholder="请输入计划单号"
            clearable
            maxlength="20"
            style="width: 180px"
          />
        </el-form-item>

        <el-form-item label="调整类型">
          <el-select
            v-model="searchForm.adjustType"
            placeholder="全部类型"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="item in ADJUST_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="申请状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="item in STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="申请日期">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :editable="false"
            style="width: 260px"
          />
        </el-form-item>

        <el-form-item>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="filterNotice"
        :title="filterNotice"
        type="info"
        :closable="false"
        show-icon
        class="filter-alert"
      />
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="adjustCode" label="调整单号" width="180" />
        <el-table-column prop="planCode" label="计划单号" width="160" />
        <el-table-column prop="planDate" label="计划日期" width="120" />
        <el-table-column label="调整类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.adjustType === 'add' ? 'success' : row.adjustType === 'remove' ? 'danger' : 'warning'">
              {{ getAdjustTypeLabel(row.adjustType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adjustReason" label="调整原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="申请状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="appliedByName" label="申请人" width="120" />
        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.appliedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 'pending'">
              <el-button
                v-permission="PLAN_ADJUSTMENT_PERMISSIONS.APPROVE"
                link
                type="success"
                @click="auditAdjustment(row, 'approved')"
              >
                通过
              </el-button>
              <el-button
                v-permission="PLAN_ADJUSTMENT_PERMISSIONS.APPROVE"
                link
                type="danger"
                @click="auditAdjustment(row, 'rejected')"
              >
                拒绝
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <span class="total-text">共 {{ total }} 条记录</span>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="handleSearch"
        />
      </div>
    </div>

    <el-dialog
      v-model="detailVisible"
      title="调整申请详情"
      width="980px"
      destroy-on-close
      @closed="closeDetail"
    >
      <div v-loading="detailLoading" class="detail-content">
        <template v-if="currentDetail">
          <section class="detail-section">
            <h3 class="section-title">基本信息</h3>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">调整单号</span>
                <span class="value">{{ currentDetail.adjustCode || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">计划单号</span>
                <span class="value">{{ currentDetail.planCode || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">计划日期</span>
                <span class="value">{{ currentDetail.planDate || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">实施时间范围</span>
                <span class="value">{{ implementationRangeText }}</span>
              </div>
              <div class="info-item">
                <span class="label">餐次</span>
                <span class="value">{{ detailMealType }}</span>
              </div>
              <div class="info-item">
                <span class="label">就餐人数</span>
                <span class="value">{{ detailExpectedCount }}</span>
              </div>
              <div class="info-item">
                <span class="label">目标人群</span>
                <span class="value">{{ detailTargetGroup }}</span>
              </div>
              <div class="info-item">
                <span class="label">调整类型</span>
                <span class="value">{{ getAdjustTypeLabel(currentDetail.adjustType) }}</span>
              </div>
              <div class="info-item">
                <span class="label">申请状态</span>
                <span class="value">{{ getStatusLabel(currentDetail.status) }}</span>
              </div>
              <div class="info-item">
                <span class="label">申请人</span>
                <span class="value">{{ currentDetail.appliedByName || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">申请时间</span>
                <span class="value">{{ formatDateTime(currentDetail.appliedAt) }}</span>
              </div>
              <div class="info-item">
                <span class="label">审核人</span>
                <span class="value">{{ currentDetail.auditedByName || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">审核时间</span>
                <span class="value">{{ formatDateTime(currentDetail.auditedAt) }}</span>
              </div>
              <div class="info-item full-width">
                <span class="label">调整原因</span>
                <span class="value">{{ currentDetail.adjustReason || '-' }}</span>
              </div>
              <div v-if="currentDetail.auditRemark" class="info-item full-width">
                <span class="label">审核意见</span>
                <span class="value">{{ currentDetail.auditRemark }}</span>
              </div>
              <div v-if="detailSnapshot?.remark" class="info-item full-width">
                <span class="label">计划备注</span>
                <span class="value">{{ detailSnapshot.remark }}</span>
              </div>
            </div>
          </section>

          <section v-if="visibleAdjustItems.length" class="detail-section">
            <h3 class="section-title">调整明细</h3>
            <el-table :data="visibleAdjustItems" border size="small">
              <el-table-column prop="fieldLabel" label="调整项" width="140" />
              <el-table-column prop="fieldName" label="调整对象" min-width="160" />
              <el-table-column prop="beforeValue" label="调整前" min-width="180" show-overflow-tooltip />
              <el-table-column prop="afterValue" label="调整后" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>

          <section class="detail-section">
            <div class="section-header">
              <h3 class="section-title">菜谱明细</h3>
              <span class="section-extra">共 {{ detailRecipes.length }} 道菜</span>
            </div>
            <el-table v-if="detailRecipes.length" :data="detailRecipes" border size="small">
              <el-table-column label="序号" width="70" align="center">
                <template #default="{ row, $index }">
                  {{ row.sortOrder || $index + 1 }}
                </template>
              </el-table-column>
              <el-table-column prop="recipeName" label="菜品名称" min-width="180" />
              <el-table-column label="餐次" width="140">
                <template #default="{ row }">
                  {{ getMealScheduleLabel(row.mealType, row.mealName) }}
                </template>
              </el-table-column>
              <el-table-column label="就餐人数" width="110" align="center">
                <template #default="{ row }">
                  {{ row.mealExpectedCount ? `${row.mealExpectedCount}人` : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="categoryName" label="菜品分类" width="140">
                <template #default="{ row }">
                  {{ row.categoryName || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="plannedServings" label="份数" width="100" align="center">
                <template #default="{ row }">
                  {{ row.plannedServings ?? '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.remark || '-' }}
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无菜谱明细" :image-size="88" />
          </section>
        </template>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <template v-if="currentDetail?.status === 'pending'">
            <el-button
              v-permission="PLAN_ADJUSTMENT_PERMISSIONS.APPROVE"
              type="danger"
              plain
              @click="auditAdjustment(currentDetail, 'rejected')"
            >
              拒绝
            </el-button>
            <el-button
              v-permission="PLAN_ADJUSTMENT_PERMISSIONS.APPROVE"
              type="primary"
              @click="auditAdjustment(currentDetail, 'approved')"
            >
              审核通过
            </el-button>
          </template>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.adjustment-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  padding: 18px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
  position: relative;
}

.stat-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 12px 0 0 12px;
  background: var(--accent);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.stat-title {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
}

.search-card,
.table-card {
  padding: 18px 20px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.search-form {
  margin-bottom: 0;
}

.filter-alert {
  margin-top: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.total-text {
  font-size: 13px;
  color: #6b7280;
}

.detail-content {
  min-height: 240px;
}

.detail-section + .detail-section {
  margin-top: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.section-extra {
  font-size: 13px;
  color: #6b7280;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
}

.info-item.full-width {
  grid-column: 1 / -1;
}

.label {
  font-size: 12px;
  color: #6b7280;
}

.value {
  font-size: 14px;
  color: #111827;
  line-height: 1.5;
  word-break: break-all;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 1200px) {
  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .stats-row,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .pagination-wrapper {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
