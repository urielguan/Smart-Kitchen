<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { User, TrendCharts, Warning, Refresh, Search, View, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useVideoMonitorStore } from '@/stores/modules/video-monitor'
import type { BehaviorAnalysis } from '@/types/video-monitor'

const router = useRouter()
const store = useVideoMonitorStore()

// 筛选条件
const filterForm = ref({
  employeeName: '',
  hasIssues: undefined as boolean | undefined,
})

// 详情对话框
const detailDialogVisible = ref(false)
const currentDetail = computed(() => store.currentBehaviorDetail)
const detailLoading = computed(() => store.behaviorDetailLoading)

// 评分颜色映射
const getScoreColor = (score: number): string => {
  if (score >= 90) return 'success'
  if (score >= 80) return 'primary'
  if (score >= 70) return 'warning'
  return 'danger'
}

// 评分等级映射
const getScoreLevel = (score: number): string => {
  if (score >= 90) return '优秀'
  if (score >= 80) return '良好'
  if (score >= 70) return '一般'
  if (score >= 60) return '较差'
  return '不及格'
}

// 员工姓名输入提示
const showEmpTip = ref(false)
let empTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerEmpTip = () => {
  if (empTipTimer) clearTimeout(empTipTimer)
  showEmpTip.value = false
  requestAnimationFrame(() => { showEmpTip.value = true })
  empTipTimer = setTimeout(() => { showEmpTip.value = false }, 2000)
}
const handleEmpInput = () => {
  if (filterForm.value.employeeName.length >= 10) triggerEmpTip()
  else showEmpTip.value = false
}
const handleEmpKeydown = (e: KeyboardEvent) => {
  if (filterForm.value.employeeName.length >= 10 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    triggerEmpTip()
  }
}

// 搜索
const handleSearch = () => {
  store.searchBehavior({
    employeeName: filterForm.value.employeeName || undefined,
    hasIssues: filterForm.value.hasIssues,
  })
}

// 重置
const handleReset = () => {
  filterForm.value = {
    employeeName: '',
    hasIssues: undefined,
  }
  store.searchBehavior({})
}

// 查看详情
const handleViewDetail = async (row: BehaviorAnalysis) => {
  detailDialogVisible.value = true
  await store.fetchBehaviorDetail(row.id)
}

// 分页
const handlePageChange = (page: number) => {
  store.changeBehaviorPage(page)
}

// 初始化
onMounted(() => {
  store.initBehaviorPage()
})
</script>

<template>
  <div class="behavior-analysis-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon total">
          <el-icon><User /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.behaviorStatistics.totalEmployees }}</div>
          <div class="stat-label">总员工数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon efficiency">
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.behaviorStatistics.averageEfficiency?.toFixed(1) }}</div>
          <div class="stat-label">平均效率评分</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon warning">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.behaviorStatistics.needImprovementCount }}</div>
          <div class="stat-label">需改进人数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon benchmark">
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ store.behaviorStatistics.benchmarkCount }}</div>
          <div class="stat-label">标杆人数</div>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-tooltip :visible="showEmpTip" content="员工姓名最多输入10个字符" placement="top">
          <el-input
            v-model="filterForm.employeeName"
            placeholder="员工姓名"
            clearable
            maxlength="10"
            style="width: 160px"
            @keyup.enter="handleSearch"
            @input="handleEmpInput"
            @keydown="handleEmpKeydown"
          />
        </el-tooltip>
        <el-select
          v-model="filterForm.hasIssues"
          placeholder="问题状态"
          clearable
          style="width: 120px"
        >
          <el-option label="全部" :value="undefined" />
          <el-option label="存在问题" :value="true" />
          <el-option label="表现正常" :value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>
      <div class="toolbar-right">
        <el-button :icon="VideoCamera" @click="router.push('/video-monitor')">
          返回视频监控
        </el-button>
      </div>
    </div>

    <!-- 人员列表 -->
    <div class="behavior-table">
      <el-table
        :data="store.behaviorList"
        v-loading="store.behaviorLoading"
        stripe
        style="width: 100%"
      >
        <el-table-column label="员工信息" min-width="180">
          <template #default="{ row }">
            <div class="employee-info">
              <el-avatar :size="40" :src="row.avatar">
                {{ row.employeeName?.charAt(0) }}
              </el-avatar>
              <div class="employee-detail">
                <div class="name">{{ row.employeeName }}</div>
                <div class="meta">{{ row.employeeCode }} · {{ row.employeeRole }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="100" />
        <el-table-column label="效率评分" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getScoreColor(row.efficiencyScore)" size="small">
              {{ row.efficiencyScore }}分
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合规评分" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getScoreColor(row.complianceScore)" size="small">
              {{ row.complianceScore }}分
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="卫生评分" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getScoreColor(row.hygieneScore)" size="small">
              {{ row.hygieneScore }}分
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="综合评分" width="120" align="center">
          <template #default="{ row }">
            <span class="overall-score" :class="'score-' + getScoreLevel(row.overallScore)">
              {{ row.overallScore }}分
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasIssues" type="danger" size="small">需关注</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="150">
          <template #default="{ row }">
            <el-tag
              v-for="tag in row.tags"
              :key="tag"
              size="small"
              style="margin-right: 4px"
            >
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleViewDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        :current-page="store.behaviorQuery.pageNum"
        :page-size="store.behaviorQuery.pageSize"
        :total="store.behaviorTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="人员行为分析详情"
      width="900px"
    >
      <div v-if="detailLoading" class="detail-loading">
        <el-icon class="is-loading"><Refresh /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="currentDetail" class="detail-content">
        <!-- 基本信息 -->
        <div class="detail-section">
          <h4>基本信息</h4>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="员工姓名">{{ currentDetail.employeeName }}</el-descriptions-item>
            <el-descriptions-item label="工号">{{ currentDetail.employeeCode }}</el-descriptions-item>
            <el-descriptions-item label="岗位">{{ currentDetail.employeeRole }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ currentDetail.department }}</el-descriptions-item>
            <el-descriptions-item label="工作时长">{{ currentDetail.workDuration }}分钟</el-descriptions-item>
            <el-descriptions-item label="操作次数">{{ currentDetail.operationCount }}次</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 评分详情 -->
        <div class="detail-section">
          <h4>评分详情</h4>
          <div class="score-grid">
            <div class="score-item">
              <div class="score-label">效率评分</div>
              <el-progress
                type="circle"
                :percentage="currentDetail.efficiencyScore"
                :color="currentDetail.efficiencyScore >= 80 ? '#67C23A' : currentDetail.efficiencyScore >= 60 ? '#E6A23C' : '#F56C6C'"
              />
            </div>
            <div class="score-item">
              <div class="score-label">合规评分</div>
              <el-progress
                type="circle"
                :percentage="currentDetail.complianceScore"
                :color="currentDetail.complianceScore >= 80 ? '#67C23A' : currentDetail.complianceScore >= 60 ? '#E6A23C' : '#F56C6C'"
              />
            </div>
            <div class="score-item">
              <div class="score-label">卫生评分</div>
              <el-progress
                type="circle"
                :percentage="currentDetail.hygieneScore"
                :color="currentDetail.hygieneScore >= 80 ? '#67C23A' : currentDetail.hygieneScore >= 60 ? '#E6A23C' : '#F56C6C'"
              />
            </div>
            <div class="score-item">
              <div class="score-label">守时评分</div>
              <el-progress
                type="circle"
                :percentage="currentDetail.punctualityScore || 0"
                :color="(currentDetail.punctualityScore || 0) >= 80 ? '#67C23A' : (currentDetail.punctualityScore || 0) >= 60 ? '#E6A23C' : '#F56C6C'"
              />
            </div>
            <div class="score-item">
              <div class="score-label">协作评分</div>
              <el-progress
                type="circle"
                :percentage="currentDetail.teamworkScore || 0"
                :color="(currentDetail.teamworkScore || 0) >= 80 ? '#67C23A' : (currentDetail.teamworkScore || 0) >= 60 ? '#E6A23C' : '#F56C6C'"
              />
            </div>
          </div>
        </div>

        <!-- 问题和建议 -->
        <div v-if="currentDetail.hasIssues && currentDetail.issues?.length" class="detail-section">
          <h4>发现问题</h4>
          <el-table :data="currentDetail.issues" size="small">
            <el-table-column prop="issueName" label="问题类型" width="120" />
            <el-table-column prop="description" label="问题描述" />
            <el-table-column prop="occurrenceCount" label="发生次数" width="100" align="center" />
          </el-table>
        </div>

        <!-- AI培训建议 -->
        <div v-if="currentDetail.aiTrainingSuggestions?.length" class="detail-section">
          <h4>AI培训建议</h4>
          <ul class="suggestion-list">
            <li v-for="(suggestion, index) in currentDetail.aiTrainingSuggestions" :key="index">
              {{ suggestion }}
            </li>
          </ul>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.behavior-analysis-page {
  padding: 20px;
  background: $bg-base;
  min-height: 100%;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
  background: $bg-white;
  border-radius: 8px;
  box-shadow: $box-shadow-light;

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 16px;
    font-size: 24px;
    color: #fff;

    &.total { background: $primary-color; }
    &.efficiency { background: $success-color; }
    &.warning { background: $warning-color; }
    &.benchmark { background: #9b59b6; }
  }

  .stat-content {
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: $text-primary;
    }

    .stat-label {
      font-size: 14px;
      color: $text-secondary;
      margin-top: 4px;
    }
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px;
  background: $bg-white;
  border-radius: 8px;
  box-shadow: $box-shadow-light;

  .toolbar-left {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}

.behavior-table {
  background: $bg-white;
  border-radius: 8px;
  box-shadow: $box-shadow-light;
  padding: 16px;
}

.employee-info {
  display: flex;
  align-items: center;
  gap: 12px;

  .employee-detail {
    .name {
      font-weight: 600;
      color: $text-primary;
    }

    .meta {
      font-size: 12px;
      color: $text-secondary;
    }
  }
}

.overall-score {
  font-weight: 600;

  &.score-优秀 { color: $success-color; }
  &.score-良好 { color: $primary-color; }
  &.score-一般 { color: $warning-color; }
  &.score-较差, &.score-不及格 { color: $danger-color; }
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding: 16px;
  background: $bg-white;
  border-radius: 8px;
}

.detail-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: $text-secondary;
}

.detail-content {
  .detail-section {
    margin-bottom: 24px;

    h4 {
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid $border-base;
    }
  }
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  text-align: center;

  .score-item {
    .score-label {
      font-size: 14px;
      color: $text-secondary;
      margin-bottom: 12px;
    }
  }
}

.suggestion-list {
  padding-left: 20px;

  li {
    margin-bottom: 8px;
    color: $text-primary;
    line-height: 1.6;
  }
}
</style>
