<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { reviewApi } from '@/api/modules/evaluation'
import type { MealReview } from '@/types/evaluation'
import { REVIEW_SOURCE_MAP, MEAL_TYPE_MAP, SCORE_COLOR_MAP } from '@/constants/evaluation'
import { formatDateTime } from '@/utils'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: boolean
  reviewId: number | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

/** 详情数据 */
const detail = ref<MealReview | null>(null)

/** 加载状态 */
const loading = ref(false)

/** 弹窗可见性 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

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

/** 获取评价详情 */
const fetchDetail = async () => {
  if (!props.reviewId) return

  loading.value = true
  try {
    const res = await reviewApi.getDetail(props.reviewId)
    if (res.code === 'SUCCESS' && res.data) {
      detail.value = res.data
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取评价详情失败')
  } finally {
    loading.value = false
  }
}

/** 监听弹窗打开和ID变化 */
watch(
  [() => props.modelValue, () => props.reviewId],
  ([isVisible, id]) => {
    if (isVisible && id) {
      fetchDetail()
    } else if (!isVisible) {
      detail.value = null
    }
  },
  { immediate: true }
)

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="758px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    destroy-on-close
    @close="handleClose"
    class="review-detail-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">评价详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <!-- 评分区域 -->
        <div class="score-section">
          <div class="score-left">
            <span class="score-number" :style="{ color: getScoreColor(detail.overallScore) }">
              {{ detail.overallScore }}分
            </span>
            <el-rate v-model="detail.overallScore" disabled :max="5" size="large" />
            <span class="score-label">综合评分</span>
          </div>
          <div class="score-divider" />
          <div v-if="detail.tasteScore || detail.nutritionScore || detail.portionScore" class="score-right">
            <div v-if="detail.tasteScore" class="score-item">
              <span class="label">口味：</span>
              <el-rate v-model="detail.tasteScore" disabled :max="5" size="default" />
              <span class="value">{{ detail.tasteScore }}分</span>
            </div>
            <div v-if="detail.nutritionScore" class="score-item">
              <span class="label">营养：</span>
              <el-rate v-model="detail.nutritionScore" disabled :max="5" size="default" />
              <span class="value">{{ detail.nutritionScore }}分</span>
            </div>
            <div v-if="detail.portionScore" class="score-item">
              <span class="label">份量：</span>
              <el-rate v-model="detail.portionScore" disabled :max="5" size="default" />
              <span class="value">{{ detail.portionScore }}分</span>
            </div>
          </div>
        </div>

        <!-- 基础信息 -->
        <div class="section-title info-section-title">
          <span class="title-bar" />
          <span>基础信息</span>
        </div>
        <div class="info-table">
          <div class="info-label">评价编号</div>
          <div class="info-value">{{ detail.reviewNo }}</div>
          <div class="info-label">来源</div>
          <div class="info-value">{{ getSourceName(detail.source) }}</div>
          <div class="info-label">菜品名称</div>
          <div class="info-value">{{ detail.menuName || '-' }}</div>
          <div class="info-label">餐次</div>
          <div class="info-value">{{ detail.mealType ? getMealTypeName(detail.mealType) : '-' }}</div>
          <div class="info-label">评价人</div>
          <div class="info-value">{{ detail.employeeName }}</div>
          <div class="info-label">门店</div>
          <div class="info-value">{{ detail.orgName }}</div>
          <div class="info-label">评价日期</div>
          <div class="info-value">{{ detail.reviewDate }}</div>
          <div class="info-label">获得积分</div>
          <div class="info-value"><span class="points-text">+{{ detail.points || 0 }}</span></div>
        </div>

        <!-- 评价内容 -->
        <template v-if="detail.content">
          <div class="section-title info-section-title" style="margin-top: 20px">
            <span class="title-bar" />
            <span>评价内容</span>
          </div>
          <div class="content-box">{{ detail.content }}</div>
        </template>

        <!-- 评价标签 -->
        <template v-if="detail.tags && detail.tags.length > 0">
          <div class="section-title info-section-title" style="margin-top: 20px">
            <span class="title-bar" />
            <span>评价标签</span>
          </div>
          <div class="tags-list">
            <el-tag v-for="tag in detail.tags" :key="tag" size="small" type="info">
              {{ tag }}
            </el-tag>
          </div>
        </template>

        <!-- 评价图片 -->
        <template v-if="detail.images && detail.images.length > 0">
          <div class="section-title info-section-title" style="margin-top: 20px">
            <span class="title-bar" />
            <span>评价图片</span>
          </div>
          <div class="images-list">
            <el-image
              v-for="(img, index) in detail.images"
              :key="index"
              :src="img"
              :preview-src-list="detail.images"
              :initial-index="index"
              fit="cover"
              class="image-item"
            />
          </div>
        </template>

        <!-- 回复信息 -->
        <div class="section-title info-section-title" style="margin-top: 20px">
          <span class="title-bar" />
          <span>回复信息</span>
        </div>
        <template v-if="detail.replyContent">
          <div class="reply-box">
            <div class="reply-content">{{ detail.replyContent }}</div>
            <div class="reply-meta">
              <span v-if="detail.replyByName">回复人：{{ detail.replyByName }}</span>
              <span v-if="detail.replyAt">{{ formatDateTime(detail.replyAt) }}</span>
            </div>
          </div>
        </template>
        <template v-else>
          <span class="no-reply">暂无回复</span>
        </template>

        <!-- 时间信息 -->
        <div class="time-section">
          <span>评价时间：{{ formatDateTime(detail.createdAt) }}</span>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.review-detail-dialog.el-dialog {
  width: 758px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  margin: auto !important;
}

.review-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.review-detail-dialog.el-dialog .el-dialog__body {
  height: 680px;
  padding: 16px 24px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.review-detail-dialog.el-dialog .el-dialog__footer {
  padding: 12px 24px 16px !important;
  flex-shrink: 0;
  border-top: 1px solid #E1E2E9;
  box-sizing: border-box;
  text-align: right;
}

.review-detail-dialog.el-dialog .el-dialog__footer .dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
}
</style>

<style lang="scss" scoped>
/* ---- 头部 ---- */
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 32px;
}

.dialog-title {
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  color: #000000;
}

.close-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE8CC;
  }
}

/* ---- 详情 body ---- */
.detail-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

/* ---- 评分区域 ---- */
.score-section {
  border: 1px solid #EBEEF5;
  border-radius: 8px;
  height: 148px;
  padding: 0 24px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}

.score-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 0px;

  .score-label {
    font-size: 14px;
    font-weight: 400;
    color: #606266;
  }

  .score-value {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .score-number {
    font-size: 20px;
    font-weight: 700;
  }
}

.score-divider {
  width: 1px;
  align-self: stretch;
  margin: 16px 0;
  border-left: 1px dashed #DCDFE6;
}

.score-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 10px;
  padding: 16px 0 16px 24px;

  :deep(.el-rate) {
    height: 20px;
  }
}

.score-item {
  display: flex;
  align-items: center;
  gap: 8px;

  .label {
    color: #606266;
    font-size: 14px;
  }

  .value {
    color: rgba(0, 0, 0, 0.85);
    font-size: 14px;
    font-weight: 500;
  }
}

/* ---- 基础信息表格 ---- */
.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  width: 100%;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

.info-value {
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

/* ---- 区块标题 ---- */
.section-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.info-section-title {
  margin-bottom: 12px;
}

.title-bar {
  display: inline-block;
  width: 4px;
  height: 20px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

.points-text {
  color: #e6a23c;
  font-weight: 600;
}

/* ---- 评价内容 ---- */
.content-box {
  background: #F9FBFF;
  border-radius: 6px;
  padding: 12px 16px;
  line-height: 1.6;
  color: #606266;
}

/* ---- 标签 ---- */
.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

:deep(.el-tag--info) {
  background: #F4F4F5;
  border: 1px solid #909399;
  border-radius: 3px;
  color: #909399;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

/* ---- 图片 ---- */
.images-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-item {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  cursor: pointer;
}

/* ---- 回复信息 ---- */
.reply-box {
  background: #F5F9F5;
  border-radius: 6px;
  padding: 12px 16px;
}

.reply-content {
  line-height: 1.6;
  color: #606266;
  margin-bottom: 8px;
}

.reply-meta {
  display: flex;
  gap: 16px;
  color: #909399;
  font-size: 12px;
}

.no-reply {
  color: #c0c4cc;
}

/* ---- 时间信息 ---- */
.time-section {
  text-align: right;
  color: #909399;
  font-size: 13px;
  padding-top: 12px;
  border-top: 1px solid #E1E2E9;
  margin-top: 16px;
}

/* ---- 底部按钮 ---- */
.btn-cancel {
  width: 58px;
  height: 32px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545C;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}
</style>
