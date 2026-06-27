<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { evaluationApi } from '@/api/modules/evaluation'
import {
  EVALUATION_TYPE_MAP,
  EVALUATION_SOURCE_MAP,
  PROCESS_STATUS_MAP,
  DISPATCH_TYPE_MAP,
  PRIORITY_MAP
} from '@/constants/evaluation'
import { formatDateTime } from '@/utils'
import type { Evaluation, ProcessRecord } from '@/types/evaluation'

interface Props {
  modelValue: boolean
  evaluationId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  evaluationId: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 评价详情 */
const evaluation = ref<Evaluation | null>(null)

/** 头理记录 */
const processRecords = ref<ProcessRecord[]>([])

/** 加载状态 */
const loading = ref(false)

/** 当前 Tab */
const activeTab = ref('base')

/** 获取评价详情 */
const fetchDetail = async () => {
  if (!props.evaluationId) return

  loading.value = true
  try {
    const res = await evaluationApi.getDetail(props.evaluationId)
    if (res.code === 'SUCCESS' && res.data) {
      evaluation.value = res.data
    }
  } catch (error) {
    console.error('获取评价详情失败:', error)
  } finally {
    loading.value = false
  }
}

/** 获取处理记录 */
const fetchProcessRecords = async () => {
  if (!props.evaluationId) return

  try {
    const res = await evaluationApi.getProcessRecords(props.evaluationId)
    if (res.code === 'SUCCESS' && res.data) {
      processRecords.value = res.data
    }
  } catch (error) {
    console.error('获取处理记录失败:', error)
  }
}

/** 监听弹窗显示，获取详情 */
watch(
  () => props.modelValue,
  (val) => {
    if (val && props.evaluationId) {
      fetchDetail()
      fetchProcessRecords()
      activeTab.value = 'base'
    } else {
      evaluation.value = null
      processRecords.value = []
    }
  }
)

/** 获取类型标签 */
const getTypeTag = (type: string): { label: string; type: string } => {
  return EVALUATION_TYPE_MAP[type as keyof typeof EVALUATION_TYPE_MAP] || { label: type, type: 'info' }
}

/** 获取来源标签 */
const getSourceLabel = (source: string): string => {
  return EVALUATION_SOURCE_MAP[source as keyof typeof EVALUATION_SOURCE_MAP] || source
}

/** 获取状态标签 */
const getStatusTag = (status: string): { label: string; type: string } => {
  return PROCESS_STATUS_MAP[status as keyof typeof PROCESS_STATUS_MAP] || { label: status, type: 'info' }
}

/** 获取派单方式标签 */
const getDispatchTag = (dispatchType: string): { label: string; type: string } | undefined => {
  if (!dispatchType) return undefined
  return DISPATCH_TYPE_MAP[dispatchType as keyof typeof DISPATCH_TYPE_MAP]
}

/** 获取优先级标签 */
const getPriorityTag = (priority: string): { label: string; type: string } | undefined => {
  if (!priority) return undefined
  return PRIORITY_MAP[priority as keyof typeof PRIORITY_MAP]
}

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}

/** 弹窗标题 */
const dialogTitle = computed(() => {
  if (evaluation.value) {
    return `${evaluation.value.orderNo} - 评价详情`
  }
  return '评价详情'
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="700px"
    :close-on-click-modal="false"
    append-to-body
    :modal-append-to-body="true"
    :z-index="2000"
    destroy-on-close
    @close="handleClose"
  >
    <div v-loading="loading">
      <template v-if="evaluation">
        <el-tabs v-model="activeTab" class="detail-tabs">
          <!-- 基础信息 Tab -->
          <el-tab-pane label="基础信息" name="base">
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">单据编号</span>
                <span class="detail-value">
                  <code class="order-code">{{ evaluation.orderNo }}</code>
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">类型</span>
                <span class="detail-value">
                  <el-tag :type="getTypeTag(evaluation.type).type" size="small">
                    {{ getTypeTag(evaluation.type).label }}
                  </el-tag>
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">来源</span>
                <span class="detail-value">{{ getSourceLabel(evaluation.source) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">标题/菜品</span>
                <span class="detail-value">
                  <strong>{{ evaluation.dishName || evaluation.title }}</strong>
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">评分</span>
                <span class="detail-value">
                  <el-rate v-model="evaluation.score" disabled :max="5" />
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">标签</span>
                <span class="detail-value">
                  <template v-if="evaluation.tags && evaluation.tags.length > 0">
                    <el-tag
                      v-for="tag in evaluation.tags"
                      :key="tag"
                      type="info"
                      size="small"
                      class="tag-item"
                    >
                      {{ tag }}
                    </el-tag>
                  </template>
                  <span v-else>-</span>
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">投诉人</span>
                <span class="detail-value">{{ evaluation.reviewerName }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">联系电话</span>
                <span class="detail-value">{{ evaluation.reviewerPhone || '-' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">所属门店</span>
                <span class="detail-value">{{ evaluation.orgName }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">处理状态</span>
                <span class="detail-value">
                  <el-tag :type="getStatusTag(evaluation.processStatus).type" size="small">
                    {{ getStatusTag(evaluation.processStatus).label }}
                  </el-tag>
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">优先级</span>
                <span class="detail-value">
                  <el-tag
                    v-if="evaluation.priority"
                    :type="getPriorityTag(evaluation.priority)?.type"
                    size="small"
                  >
                    {{ getPriorityTag(evaluation.priority)?.label }}
                  </el-tag>
                  <span v-else>-</span>
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">获得积分</span>
                <span class="detail-value">{{ evaluation.points }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">创建时间</span>
                <span class="detail-value">{{ formatDateTime(evaluation.createdAt) }}</span>
              </div>
              <div class="detail-item full-width">
                <span class="detail-label">评价内容</span>
                <span class="detail-value content-text">{{ evaluation.content || '-' }}</span>
              </div>
              <div v-if="evaluation.images && evaluation.images.length > 0" class="detail-item full-width">
                <span class="detail-label">评价图片</span>
                <span class="detail-value">
                  <div class="image-list">
                    <el-image
                      v-for="(img, index) in evaluation.images"
                      :key="index"
                      :src="img"
                      :preview-src-list="evaluation.images"
                      fit="cover"
                      class="image-item"
                    />
                  </div>
                </span>
              </div>
            </div>
          </el-tab-pane>

          <!-- 派单信息 Tab -->
          <el-tab-pane label="派单信息" name="dispatch">
            <template v-if="!evaluation.dispatchType">
              <div class="no-dispatch">暂未派单</div>
            </template>
            <template v-else>
              <div class="detail-grid">
                <div class="detail-item">
                  <span class="detail-label">派单方式</span>
                  <span class="detail-value">
                    <el-tag :type="getDispatchTag(evaluation.dispatchType)?.type" size="small">
                      {{ getDispatchTag(evaluation.dispatchType)?.label }}
                    </el-tag>
                  </span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">处理人</span>
                  <span class="detail-value">{{ evaluation.handlerName || '-' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">派单时间</span>
                  <span class="detail-value">{{ evaluation.dispatchTime ? formatDateTime(evaluation.dispatchTime) : '-' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">处理时间</span>
                  <span class="detail-value">{{ evaluation.handleTime ? formatDateTime(evaluation.handleTime) : '-' }}</span>
                </div>
                <div class="detail-item full-width">
                  <span class="detail-label">处理备注</span>
                  <span class="detail-value">{{ evaluation.handleRemark || '-' }}</span>
                </div>
              </div>
            </template>
          </el-tab-pane>

          <!-- 处理记录 Tab -->
          <el-tab-pane label="处理记录" name="records">
            <template v-if="processRecords.length === 0">
              <div class="no-records">暂无处理记录</div>
            </template>
            <template v-else>
              <el-timeline class="record-timeline">
                <el-timeline-item
                  v-for="record in processRecords"
                  :key="record.id"
                  :timestamp="formatDateTime(record.createdAt)"
                  placement="top"
                >
                  <div class="record-content">
                    <div class="record-action">{{ record.action }}</div>
                    <div class="record-operator">操作人：{{ record.operatorName }}</div>
                    <div v-if="record.content" class="record-text">{{ record.content }}</div>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </template>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.detail-tabs {
  min-height: 300px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px 20px;
  padding: 10px 0;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 3px;

  &.full-width {
    grid-column: span 2;
  }
}

.detail-label {
  font-size: 12px;
  color: #909399;
}

.detail-value {
  font-size: 14px;
  color: #303133;
  word-break: break-all;
}

.order-code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
}

.content-text {
  line-height: 1.6;
  white-space: pre-wrap;
}

.tag-item {
  margin-right: 6px;
}

.image-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.image-item {
  width: 80px;
  height: 80px;
  border-radius: 4px;
  cursor: pointer;
}

.no-dispatch,
.no-records {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.record-timeline {
  padding: 10px 0;
}

.record-content {
  .record-action {
    font-weight: 500;
    margin-bottom: 4px;
  }

  .record-operator {
    font-size: 12px;
    color: #909399;
    margin-bottom: 4px;
  }

  .record-text {
    font-size: 13px;
    color: #606266;
  }
}
</style>
