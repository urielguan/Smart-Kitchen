<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CookTaskActionState, CookTaskDetail } from '@/types'
import { formatDateTime, formatTaskStatus } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  task: CookTaskDetail | null
  loading?: boolean
  actionLoadingId?: number | null
  startActionState: CookTaskActionState
  completeActionState: CookTaskActionState
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  start: [task: CookTaskDetail]
  complete: [task: CookTaskDetail]
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})
</script>

<template>
  <el-drawer v-model="visible" size="70%" destroy-on-close>
    <template #header>
      <div class="task-detail__header">
        <div>
          <div class="task-detail__title">{{ props.task?.menuName || t('detail.title') }}</div>
          <div class="task-detail__subtitle">{{ props.task?.taskNo }} · {{ formatTaskStatus(props.task?.status) }}</div>
        </div>
      </div>
    </template>

    <div v-if="props.task" class="task-detail" v-loading="props.loading">
      <section class="task-detail__section">
        <div class="task-detail__section-title">{{ t('detail.liveMonitor') }}</div>
        <div class="task-detail__monitor-grid">
          <div class="task-detail__metric">
            <span>{{ t('detail.targetTemp') }}</span>
            <strong>{{ props.task.targetTempMin ?? props.task.targetTempMax ?? '--' }}°C</strong>
          </div>
          <div class="task-detail__metric">
            <span>{{ t('detail.currentTemp') }}</span>
            <strong>{{ props.task.currentTemp ?? '--' }}°C</strong>
          </div>
          <div class="task-detail__metric">
            <span>{{ t('detail.duration') }}</span>
            <strong>{{ props.task.actualDuration ?? '--' }} min</strong>
          </div>
          <div class="task-detail__metric">
            <span>开始时间</span>
            <strong>{{ formatDateTime(props.task.startTime) }}</strong>
          </div>
        </div>
      </section>

      <section class="task-detail__section">
        <div class="task-detail__section-title">业务规则</div>
        <div class="task-detail__rule-grid">
          <div class="task-detail__metric">
            <span>指定厨师</span>
            <strong>{{ props.task.assignedChefName || props.task.chefName || '--' }}</strong>
          </div>
          <div class="task-detail__metric">
            <span>允许时段</span>
            <strong>{{ props.task.allowStartTime || '--' }} ~ {{ props.task.allowEndTime || '--' }}</strong>
          </div>
          <div class="task-detail__metric">
            <span>发起人</span>
            <strong>{{ props.task.initiatorName || '--' }}</strong>
          </div>
          <div class="task-detail__metric">
            <span>设备状态</span>
            <strong>{{ props.task.sensorOnline === false || props.task.deviceOnline === false ? '离线' : '在线' }}</strong>
          </div>
        </div>
      </section>

      <section class="task-detail__section">
        <div class="task-detail__section-title">{{ t('detail.ingredients') }}</div>
        <div class="task-detail__ingredient-list">
          <div v-for="item in props.task.ingredients" :key="`${item.materialId}-${item.materialName}`" class="task-detail__ingredient-item">
            <strong>{{ item.materialName }}</strong>
            <span>{{ item.quantity ?? '--' }} {{ item.unit || '' }}</span>
          </div>
        </div>
      </section>

      <section class="task-detail__section">
        <div class="task-detail__section-title">{{ t('detail.aiAlerts') }}</div>
        <div v-if="props.task.aiMonitorRecords.length" class="task-detail__alert-list">
          <div v-for="(item, index) in props.task.aiMonitorRecords" :key="`${item.snapshotTime}-${index}`" class="task-detail__alert-item">
            <strong>{{ item.violationName || item.violationType || 'AI 预警' }}</strong>
            <p>{{ item.description || '--' }}</p>
            <span>{{ item.snapshotTime || '--' }}</span>
          </div>
        </div>
        <el-empty v-else :description="t('common.noData')" />
      </section>

      <section class="task-detail__section">
        <div class="task-detail__section-title">{{ t('detail.timeline') }}</div>
        <div class="task-detail__timeline">
          <div v-for="(point, index) in props.task.temperatureRecords" :key="`${point.recordTime}-${index}`" class="task-detail__timeline-item">
            <strong>{{ formatDateTime(point.recordTime) }}</strong>
            <span>{{ point.temperature ?? '--' }}°C</span>
            <span>{{ point.remark || (point.abnormal ? '异常采样' : '正常采样') }}</span>
          </div>
        </div>
      </section>

      <div class="task-detail__actions">
        <div v-if="props.startActionState.visible" class="task-detail__action-item">
          <el-tooltip :disabled="props.startActionState.enabled || !props.startActionState.reason" :content="props.startActionState.reason" placement="top">
            <el-button
              type="warning"
              class="touch-primary-button"
              :loading="props.actionLoadingId === props.task.id"
              :disabled="!props.startActionState.enabled"
              @click="emit('start', props.task)"
            >
              {{ t('tasks.start') }}
            </el-button>
          </el-tooltip>
          <div v-if="!props.startActionState.enabled && props.startActionState.reason" class="task-detail__action-reason">
            {{ props.startActionState.reason }}
          </div>
        </div>
        <div v-if="props.completeActionState.visible" class="task-detail__action-item">
          <el-button
            type="success"
            class="touch-primary-button"
            :loading="props.actionLoadingId === props.task.id"
            :disabled="!props.completeActionState.enabled"
            @click="emit('complete', props.task)"
          >
            {{ t('tasks.complete') }}
          </el-button>
          <div v-if="!props.completeActionState.enabled && props.completeActionState.reason" class="task-detail__action-reason">
            {{ props.completeActionState.reason }}
          </div>
        </div>
        <div v-else-if="props.task.status === 'in_progress'" class="task-detail__action-hint">
          仅发起人可完成此任务
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.task-detail {
  display: grid;
  gap: 20px;
}

.task-detail__title {
  font-size: 28px;
  font-weight: 700;
}

.task-detail__subtitle {
  margin-top: 6px;
  color: $text-secondary;
}

.task-detail__section {
  @include panel-card;
  padding: 18px;
}

.task-detail__section-title {
  margin-bottom: 16px;
  font-size: 20px;
  font-weight: 700;
}

.task-detail__monitor-grid,
.task-detail__ingredient-list,
.task-detail__alert-list,
.task-detail__timeline {
  display: grid;
  gap: 12px;
}

.task-detail__monitor-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.task-detail__metric,
.task-detail__ingredient-item,
.task-detail__alert-item,
.task-detail__timeline-item {
  padding: 14px;
  border-radius: $border-radius-base;
  background: $bg-base;
  display: grid;
  gap: 6px;
}

.task-detail__rule-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.task-detail__actions {
  display: grid;
  gap: 12px;
}

.task-detail__action-item {
  display: grid;
  gap: 8px;
}

.task-detail__action-reason,
.task-detail__action-hint {
  font-size: 14px;
  line-height: 1.5;
  color: $danger-color;
}
</style>
