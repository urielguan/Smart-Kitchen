<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { MealReview } from '@/types/evaluation'
import { REVIEW_SOURCE_MAP, MEAL_TYPE_MAP, SCORE_COLOR_MAP } from '@/constants/evaluation'
import { EVALUATION_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

interface Props {
  data: MealReview[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  detail: [review: MealReview]
  reply: [review: MealReview]
}>()

/** 表格容器引用 */
const tableContainerRef = ref<HTMLElement | null>(null)

/** 表格高度 */
const tableHeight = ref<number | undefined>(undefined)

/** ResizeObserver 实例 */
let resizeObserver: ResizeObserver | null = null

/** 计算表格高度 */
const updateTableHeight = () => {
  if (tableContainerRef.value) {
    tableHeight.value = tableContainerRef.value.clientHeight
  }
}

/** 获取来源名称 */
const getSourceName = (source: string): string => {
  return REVIEW_SOURCE_MAP[source as keyof typeof REVIEW_SOURCE_MAP] || source
}

/** 获取餐次名称 */
const getMealTypeName = (mealType: string): string => {
  return MEAL_TYPE_MAP[mealType] || mealType
}

/** 获取评分颜色 */
const getScoreColor = (score: number): string => {
  return SCORE_COLOR_MAP[score] || '#909399'
}

/** 详情 */
const handleDetail = (row: MealReview) => emit('detail', row)

/** 回复 */
const handleReply = (row: MealReview) => emit('reply', row)

onMounted(() => {
  if (tableContainerRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableContainerRef.value)
    updateTableHeight()
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>

<template>
  <div ref="tableContainerRef" class="table-container">
    <el-table
      :data="data"
      :loading="loading"
      :height="tableHeight"
    >
      <el-table-column type="index" label="序号" width="60" align="center" />

      <el-table-column prop="reviewNo" label="评价编号" min-width="120">
        <template #default="{ row }">
          {{ row.reviewNo }}
        </template>
      </el-table-column>

      <el-table-column prop="source" label="来源" min-width="100">
        <template #default="{ row }">
          {{ getSourceName(row.source) }}
        </template>
      </el-table-column>

      <el-table-column prop="menuName" label="菜品名称" min-width="120">
        <template #default="{ row }">
          {{ row.menuName || '-' }}
        </template>
      </el-table-column>

      <el-table-column prop="employeeName" label="评价人" min-width="80" />

      <el-table-column prop="orgName" label="门店" min-width="120" />

      <el-table-column prop="overallScore" label="评分" min-width="160">
        <template #default="{ row }">
          <div class="score-cell">
            <el-rate v-model="row.overallScore" disabled :max="5" size="small" />
            <span class="score-text" :style="{ color: getScoreColor(row.overallScore) }">
              {{ row.overallScore }}分
            </span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="content" label="评价内容" min-width="180">
        <template #default="{ row }">
          <el-tooltip v-if="row.content" :content="row.content" placement="top" :show-after="500">
            <span class="content-text">{{ row.content }}</span>
          </el-tooltip>
          <span v-else class="no-content">-</span>
        </template>
      </el-table-column>

      <el-table-column prop="points" label="积分" min-width="70" align="center">
        <template #default="{ row }">
          <span class="points-text">+{{ row.points || 0 }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="createdAt" label="评价时间" min-width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="140" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button v-if="!row.replyContent" v-permission="EVALUATION_PERMISSIONS.REPLY" type="warning" link @click="handleReply(row)">回复</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  flex: 1;
  min-height: 0;
  background: #FFFFFF;
  padding: 0 16px;
  overflow: hidden;
}

:deep(.el-table) {
  --el-table-index-cell-vertical-align: middle;
  --el-table-border-color: #E7E7E7;
  --el-table-row-height: 46px;

  .el-table__cell {
    padding-left: 0;
    padding-right: 0;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #000000E5;
  }
}

:deep(.el-table__body tr) {
  height: 46px;
  border-bottom: 1px solid #E7E7E7;

  td {
    height: 46px;
  }

  &:nth-child(odd) td {
    background-color: #FFFFFF;
  }

  &:nth-child(even) td {
    background-color: #F5F9FF;
  }
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-table thead th) {
  font-family: 'PingFang SC', sans-serif;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  color: #00000066;
  background-color: #F5F9FF !important;
  border-bottom: 1px solid #E7E7E7;
}

:deep(.el-table thead th:first-child) {
  border-top-left-radius: 0;
}

:deep(.el-table thead th:last-child) {
  border-top-right-radius: 0;
}

:deep(.index-col) {
  font-family: 'PingFang SC', sans-serif;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  color: #000000E5;
}

:deep(.el-tag--success) {
  background: #E3F9E9;
  border: 1px solid #2BA471;
  border-radius: 3px;
  color: #2BA471;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.el-button--primary.is-link) {
  color: #5570F1;
  &:hover { color: #2E45D6; }
  &:focus { color: #5570F1; }
}

:deep(.el-button--warning.is-link) {
  color: #ED8A40;
  &:hover { color: #C56318; }
  &:focus { color: #ED8A40; }
}

:deep(.action-col .cell) {
  overflow: visible;
}

.score-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-text {
  font-size: 13px;
  font-weight: 600;
}

.content-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #000000A6;
}

.no-content {
  color: #c0c4cc;
}

.points-text {
  color: #e6a23c;
  font-weight: 600;
}
</style>
