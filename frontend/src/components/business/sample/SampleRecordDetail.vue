<script setup lang="ts">
import { computed, ref, markRaw } from 'vue'
import { Document, Edit, Delete, CircleClose, Box, Cpu, Tickets } from '@element-plus/icons-vue'
import type { SampleRecordDetail as SampleRecordDetailType, AiEvaluateResult, OperationLog } from '@/types'
import { SAMPLE_STATUS_MAP, MEAL_TYPE_MAP, SAMPLE_MANUAL_DISPOSAL_SCENE_MAP, SAMPLE_RECORD_ORIGIN_MAP } from '@/constants/sample'
import SampleAiEvaluate from './SampleAiEvaluate.vue'
import { SAMPLE_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

interface Props {
  modelValue: boolean
  record: SampleRecordDetailType | null
  aiResult: AiEvaluateResult | null
  aiLoading?: boolean
  aiError?: string
  loading?: boolean
  operationLogs?: OperationLog[]
  canUseManualDisposalSupplement?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  operationLogs: () => []
})
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  register: []
  dispose: []
  manualSupplement: []
  aiEvaluate: []
  edit: []
  archive: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const activeTab = ref('detail')

const getStatus = (status: string) => SAMPLE_STATUS_MAP[status] || { label: status, type: 'info' }
const getMealType = (mealType: string) => MEAL_TYPE_MAP[mealType] || mealType
const getTaskStatus = (status?: string | null) => {
  const map: Record<string, { label: string; type: string }> = {
    pending: { label: '待烹饪', type: 'info' },
    in_progress: { label: '烹饪中', type: 'warning' },
    completed: { label: '已完成', type: 'success' },
    cancelled: { label: '已取消', type: 'danger' },
    archived: { label: '已归档', type: 'info' }
  }
  return map[status || ''] || { label: status || '-', type: 'info' }
}

const operationLock = computed(() => props.record?.operationLock || null)
const isOperationLockedByOther = computed(() => Boolean(operationLock.value?.locked && !operationLock.value?.ownedByCurrentUser))
const operationLockTitle = computed(() => {
  if (!operationLock.value?.locked) return ''
  const operator = operationLock.value.operatorName || '其他用户'
  const action = operationLock.value.operationTypeLabel || '处理中'
  return `该记录当前由${operator}执行${action}，仅支持查看详情`
})

const canDispose = computed(() => {
  if (!props.record) return false
  if (props.record.rollbackIsolated) return false
  if (isOperationLockedByOther.value) return false
  return ['sampled', 'evaluated', 'pending_disposal', 'overdue'].includes(props.record.status)
})

const canAiEvaluate = computed(() => {
  if (!props.record) return false
  if (props.record.rollbackIsolated) return false
  if (isOperationLockedByOther.value) return false
  return ['sampled', 'evaluated'].includes(props.record.status) && props.record.aiQualityScore == null
})

const canAiTrigger = computed(() => {
  if (!props.record) return false
  if (props.record.rollbackIsolated) return false
  if (isLocked.value || isOperationLockedByOther.value) return false
  return !['voided', 'archived', 'disposed', 'pending_sample'].includes(props.record.status)
})

const canEdit = computed(() => {
  if (!props.record) return false
  if (props.record.rollbackIsolated) return false
  if (isOperationLockedByOther.value) return false
  return props.record.status === 'sampled'
})

const canArchive = computed(() => {
  if (!props.record) return false
  if (props.record.rollbackIsolated) return false
  if (isOperationLockedByOther.value) return false
  return props.record.status === 'disposed'
})

const canManualSupplement = computed(() => {
  if (!props.record) return false
  if (!props.canUseManualDisposalSupplement) return false
  if (props.record.rollbackIsolated) return false
  if (isOperationLockedByOther.value) return false
  if (isLocked.value) return false
  return ['sampled', 'evaluated', 'pending_disposal', 'overdue'].includes(props.record.status)
})

const isRollbackIsolatedRecord = computed(() => Boolean(props.record?.rollbackIsolated))

const showRegisterButton = computed(() =>
  props.record?.status === 'pending_sample'
  && !isRollbackIsolatedRecord.value
  && !isOperationLockedByOther.value
)

const canRegister = computed(() => {
  if (!props.record) return false
  if (props.record.rollbackIsolated) return false
  return props.record.status === 'pending_sample'
    && ['completed', 'archived'].includes(props.record.taskStatus || '')
    && !isLocked.value
    && !isOperationLockedByOther.value
})

const getActionIcon = (action: string) => {
  const map: Record<string, ReturnType<typeof markRaw>> = {
    create: markRaw(Document),
    auto_create: markRaw(Document),
    manual_create: markRaw(Document),
    history_supplement_create: markRaw(Document),
    offline_delayed_supplement: markRaw(Document),
    manual_disposal_supplement: markRaw(Delete),
    register: markRaw(Edit),
    update: markRaw(Edit),
    dispose: markRaw(Delete),
    void: markRaw(CircleClose),
    rollback_void: markRaw(CircleClose),
    archive: markRaw(Box),
    ai_evaluate: markRaw(Cpu)
  }
  return map[action] || markRaw(Tickets)
}

const isVideo = (url: string) => /\.(mp4)(\?|$)/i.test(url)
const isPdf = (url: string) => /\.(pdf)(\?|$)/i.test(url)

const isLocked = computed(() => {
  if (!props.record) return false
  return props.record.lockStatus === 'investigation' || props.record.lockStatus === 'accident'
})

const lockStatusText = computed(() => {
  if (!props.record?.lockStatus || props.record.lockStatus === 'none') return ''
  return props.record.lockStatus === 'investigation' ? '调查锁定' : '事故锁定'
})

const getLockStatusText = (status?: string | null) => {
  const map: Record<string, string> = { none: '未锁定', investigation: '调查锁定', accident: '事故锁定' }
  return map[status || 'none'] || '未锁定'
}

const hasDisposedThenVoidedTrace = computed(() => {
  if (!props.record) return false
  return props.record.status === 'voided' && !!props.record.disposalAt
})
</script>

<template>
  <el-dialog v-model="visible" title="留样记录详情" width="960px" destroy-on-close>
    <div class="sample-record-detail-dialog" v-loading="loading">
      <template v-if="record">
      <!-- 监管锁定提示 -->
      <el-alert
        v-if="isLocked"
        :title="`该记录已被${lockStatusText}，禁止执行作废、销样、归档操作`"
        type="error"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />

      <el-alert
        v-if="isRollbackIsolatedRecord"
        title="该留样/销样链路已因烹饪任务回滚被系统联动作废并隔离，仅支持管理员查看历史台账与审计轨迹。"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />

      <el-alert
        v-if="isOperationLockedByOther"
        :title="operationLockTitle"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />

      <!-- 操作按钮区 -->
      <div v-if="isRollbackIsolatedRecord" class="action-bar">
        <el-button disabled>留样登记</el-button>
        <el-button disabled>编辑</el-button>
        <el-button disabled>执行销样</el-button>
        <el-button disabled>归档</el-button>
      </div>
      <div v-else class="action-bar">
        <el-tooltip v-if="showRegisterButton" :disabled="canRegister" content="关联烹饪任务未完成或未归档，暂不可留样登记" placement="top">
          <span>
            <el-button type="primary" v-permission="SAMPLE_PERMISSIONS.EDIT" :disabled="!canRegister" @click="emit('register')">留样登记</el-button>
          </span>
        </el-tooltip>
        <el-button v-if="canEdit && !isLocked && !isOperationLockedByOther" type="default" v-permission="SAMPLE_PERMISSIONS.EDIT" @click="emit('edit')">编辑</el-button>
        <el-button v-if="canDispose && !isLocked && !isOperationLockedByOther" type="warning" v-permission="SAMPLE_PERMISSIONS.DISPOSE" @click="emit('dispose')">执行销样</el-button>
        <el-button v-if="canManualSupplement" type="danger" @click="emit('manualSupplement')">销样手工补录</el-button>
        <el-button v-if="canAiEvaluate && !isOperationLockedByOther" type="primary" v-permission="SAMPLE_PERMISSIONS.AI_EVALUATE" @click="emit('aiEvaluate')" :loading="aiLoading">AI评估</el-button>
        <el-button v-if="canArchive && !isLocked && !isOperationLockedByOther" type="info" v-permission="SAMPLE_PERMISSIONS.ARCHIVE" @click="emit('archive')">归档</el-button>
        <el-button v-if="isOperationLockedByOther" type="info" plain disabled>{{ operationLock?.operationTypeLabel || '处理中' }}</el-button>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="详情" name="detail">
          <div class="detail-grid">
            <div class="detail-card">
              <div class="card-title">基础信息</div>
              <div class="detail-item"><span>留样编号</span><strong>{{ record.sampleNo }}</strong></div>
              <div class="detail-item"><span>来源</span><strong>{{ record.sourceLabel || '-' }}</strong></div>
              <div class="detail-item"><span>来源类型</span><strong>{{ SAMPLE_RECORD_ORIGIN_MAP[record.recordOriginType || ''] || record.recordOriginType || '-' }}</strong></div>
              <div class="detail-item"><span>销样来源</span><strong>{{ record.disposalSourceLabel || '-' }}</strong></div>
              <div class="detail-item"><span>烹饪任务</span><strong>{{ record.taskNo || '-' }}</strong></div>
              <div class="detail-item"><span>菜谱名称</span><strong>{{ record.menuName }}</strong></div>
              <div class="detail-item"><span>留样日期</span><strong>{{ record.sampleDate }}</strong></div>
              <div class="detail-item"><span>餐次</span><strong>{{ getMealType(record.mealType) }}</strong></div>
              <div class="detail-item"><span>留样重量</span><strong>{{ record.sampleWeight ? `${record.sampleWeight}g` : '-' }}</strong></div>
              <div class="detail-item"><span>存放位置</span><strong>{{ record.storageLocation || '-' }}</strong></div>
              <div class="detail-item"><span>存放温度</span><strong>{{ record.storageTemp != null ? `${record.storageTemp}℃` : '-' }}</strong></div>
              <div class="detail-item"><span>留样人员</span><strong>{{ record.sampledByName || '-' }}</strong></div>
            </div>

            <div class="detail-card">
              <div class="card-title">状态信息</div>
              <div class="detail-item"><span>状态</span><strong>
                <el-tag :type="(getStatus(record.status).type as any)" size="small">{{ getStatus(record.status).label }}</el-tag>
              </strong></div>
              <div class="detail-item"><span>任务状态</span><strong>
                <el-tag :type="(getTaskStatus(record.taskStatus).type as any)" size="small">{{ getTaskStatus(record.taskStatus).label }}</el-tag>
              </strong></div>
              <div class="detail-item"><span>留样时间</span><strong>{{ formatDateTime(record.sampledAt) || '-' }}</strong></div>
              <div class="detail-item"><span>应销时间</span><strong>{{ formatDateTime(record.disposalDueAt) || '-' }}</strong></div>
              <div class="detail-item"><span>AI评分</span><strong>{{ record.aiQualityScore ?? '-' }}</strong></div>
              <div class="detail-item"><span>创建时间</span><strong>{{ formatDateTime(record.createdAt) || '-' }}</strong></div>
              <div class="detail-item"><span>更新时间</span><strong>{{ formatDateTime(record.updatedAt) || '-' }}</strong></div>
          <div v-if="record.operationLock?.locked" class="detail-item">
            <span>操作锁</span>
            <strong><el-tag :type="record.operationLock?.ownedByCurrentUser ? 'warning' : 'danger'" size="small">{{ record.operationLock?.operationTypeLabel || '处理中' }}</el-tag></strong>
          </div>
          <div v-if="record.operationLock?.locked" class="detail-item">
            <span>锁定人员</span>
            <strong>{{ record.operationLock?.ownedByCurrentUser ? '本人占用' : (record.operationLock?.operatorName || '-') }}</strong>
          </div>
          <div v-if="record.operationLock?.locked" class="detail-item">
            <span>锁到期时间</span>
            <strong>{{ formatDateTime(record.operationLock?.expiresAt) || '-' }}</strong>
          </div>
          <div v-if="record.lockStatus && record.lockStatus !== 'none'" class="detail-item">
            <span>监管锁定</span>
            <strong><el-tag type="danger" size="small">{{ getLockStatusText(record.lockStatus) }}</el-tag></strong>
          </div>
            </div>
          </div>

          <div v-if="record.traceBatchId || record.foodSafetyLedgerNo || record.evidenceChainId" class="section-card">
            <div class="card-title">追溯链信息</div>
            <div class="detail-item"><span>追溯批次号</span><strong>{{ record.traceBatchId || '-' }}</strong></div>
            <div class="detail-item"><span>食安台账编号</span><strong>{{ record.foodSafetyLedgerNo || '-' }}</strong></div>
            <div class="detail-item"><span>证据链编号</span><strong>{{ record.evidenceChainId || '-' }}</strong></div>
          </div>

          <div v-if="record.voidReason" class="section-card" style="border-color: #f56c6c">
            <div class="card-title">作废信息</div>
            <div v-if="hasDisposedThenVoidedTrace" class="detail-item">
              <span>状态轨迹</span>
              <strong>曾已销样，后统一回写为已作废</strong>
            </div>
            <div class="detail-item"><span>作废原因</span><strong style="color: #f56c6c">{{ record.voidReason }}</strong></div>
          </div>

          <div v-if="record.supplementReason || record.supplementRemark" class="section-card" style="border-color: #e6a23c">
            <div class="card-title">补录信息</div>
            <div class="detail-item"><span>补录类型</span><strong>{{ SAMPLE_RECORD_ORIGIN_MAP[record.recordOriginType || ''] || record.recordOriginType || '-' }}</strong></div>
            <div class="detail-item"><span>补录原因</span><strong>{{ record.supplementReason || '-' }}</strong></div>
            <div class="detail-item"><span>补录备注</span><strong>{{ record.supplementRemark || '-' }}</strong></div>
          </div>

          <div v-if="record.disposalSupplementScene || record.disposalSupplementRemark" class="section-card" style="border-color: #f56c6c">
            <div class="card-title">销样手工补录信息</div>
            <div class="detail-item"><span>销样来源</span><strong>{{ record.disposalSourceLabel || '-' }}</strong></div>
            <div class="detail-item"><span>补录场景</span><strong>{{ SAMPLE_MANUAL_DISPOSAL_SCENE_MAP[record.disposalSupplementScene || ''] || record.disposalSupplementScene || '-' }}</strong></div>
            <div class="detail-item"><span>补录备注</span><strong>{{ record.disposalSupplementRemark || '-' }}</strong></div>
            <div class="detail-item"><span>补录时间</span><strong>{{ formatDateTime(record.disposalSupplementedAt) || '-' }}</strong></div>
            <div class="detail-item"><span>补录人员</span><strong>{{ record.disposalSupplementedByName || '-' }}</strong></div>
          </div>

          <div v-if="isRollbackIsolatedRecord" class="section-card rollback-isolation-card">
            <div class="card-title">回滚隔离信息</div>
            <div class="detail-item"><span>隔离时间</span><strong>{{ formatDateTime(record.rollbackIsolatedAt) || '-' }}</strong></div>
            <div class="detail-item"><span>隔离原因</span><strong class="rollback-isolation-reason">{{ record.rollbackIsolationReason || '-' }}</strong></div>
          </div>

          <div class="section-card">
            <div class="card-title">留样照片</div>
            <div v-if="record.sampleImages?.length" class="image-list">
              <template v-for="(file, index) in record.sampleImages" :key="index">
                <video v-if="isVideo(file)" :src="file" controls class="sample-image sample-video" />
                <a v-else-if="isPdf(file)" :href="file" target="_blank" class="file-card">
                  <el-icon :size="32"><Document /></el-icon>
                  <span>PDF 文件</span>
                </a>
                <el-image
                  v-else
                  :src="file"
                  :preview-src-list="record.sampleImages!.filter(u => !isVideo(u) && !isPdf(u))"
                  :initial-index="record.sampleImages!.filter(u => !isVideo(u) && !isPdf(u)).indexOf(file)"
                  fit="cover"
                  class="sample-image"
                />
              </template>
            </div>
            <el-empty v-else description="暂无留样照片" :image-size="80" />
          </div>

          <!-- AI评估面板 -->
          <div class="section-card">
            <div class="card-title">
              AI智能评估
              <span v-if="record.evaluatedAt" class="card-title-extra">评估时间：{{ formatDateTime(record.evaluatedAt) }}</span>
            </div>
            <SampleAiEvaluate
              :result="aiResult"
              :loading="aiLoading"
              :error="aiError"
              :can-trigger="canAiTrigger"
              @trigger="emit('aiEvaluate')"
            />
          </div>

          <div v-if="record.disposalAt || record.disposalRemark || record.disposalImages?.length" class="section-card">
            <div class="card-title">销样信息</div>
            <div class="detail-item"><span>销样来源</span><strong>{{ record.disposalSourceLabel || '-' }}</strong></div>
            <div class="detail-item"><span>销样时间</span><strong>{{ formatDateTime(record.disposalAt) || '-' }}</strong></div>
            <div class="detail-item"><span>销样人员</span><strong>{{ record.disposalByName || '-' }}</strong></div>
            <div class="detail-item"><span>销样备注</span><strong>{{ record.disposalRemark || '-' }}</strong></div>
            <div v-if="record.disposalImages?.length" class="image-list" style="margin-top: 12px">
              <template v-for="(file, index) in record.disposalImages" :key="index">
                <video v-if="isVideo(file)" :src="file" controls class="sample-image sample-video" />
                <a v-else-if="isPdf(file)" :href="file" target="_blank" class="file-card">
                  <el-icon :size="32"><Document /></el-icon>
                  <span>PDF 文件</span>
                </a>
                <el-image
                  v-else
                  :src="file"
                  :preview-src-list="record.disposalImages!.filter(u => !isVideo(u) && !isPdf(u))"
                  :initial-index="record.disposalImages!.filter(u => !isVideo(u) && !isPdf(u)).indexOf(file)"
                  fit="cover"
                  class="sample-image"
                />
              </template>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="操作日志" name="logs">
          <div v-if="operationLogs.length > 0" class="log-timeline">
            <el-timeline>
              <el-timeline-item
                v-for="log in operationLogs"
                :key="log.id"
                :timestamp="formatDateTime(log.createdAt)"
                placement="top"
              >
                <div class="log-item">
                  <el-icon class="log-action-icon"><component :is="getActionIcon(log.action)" /></el-icon>
                  <span class="log-action-name">{{ log.actionName }}</span>
                  <span class="log-operator">{{ log.operatorName || '系统' }}</span>
                  <span v-if="log.terminal" class="log-terminal">{{ log.terminal }}</span>
                  <span v-if="log.content" class="log-content">{{ log.content }}</span>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
          <el-empty v-else description="暂无操作日志" :image-size="80" />
        </el-tab-pane>
      </el-tabs>
      </template>
      <el-empty v-else description="暂无留样记录详情" :image-size="80" />
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.sample-record-detail-dialog {
  min-height: 160px;
}

.action-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.detail-card,
.section-card {
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.card-title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title-extra {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px dashed #e4e7ed;

  &:last-child {
    border-bottom: none;
  }

  span {
    color: #909399;
  }

  strong {
    color: #303133;
    text-align: right;
  }
}

.section-card + .section-card {
  margin-top: 16px;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.sample-image {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.sample-video {
  object-fit: cover;
  background: #000;
}

.file-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  background: #f5f7fa;
  color: #606266;
  text-decoration: none;
  gap: 8px;
  transition: border-color 0.3s;

  &:hover {
    border-color: #409eff;
    color: #409eff;
  }

  span {
    font-size: 12px;
  }
}

.log-timeline {
  padding: 8px 0;
}

.log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-action-icon {
  font-size: 14px;
}

.log-action-name {
  font-weight: 600;
  color: #303133;
}

.log-operator {
  color: #909399;
  font-size: 13px;
}

.log-terminal {
  color: #b0b3b8;
  font-size: 12px;
  background: #f4f4f5;
  padding: 1px 6px;
  border-radius: 3px;
}

.log-content {
  color: #606266;
  font-size: 13px;
}

@media (max-width: 768px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
