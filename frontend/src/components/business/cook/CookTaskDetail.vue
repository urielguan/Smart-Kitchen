<script setup lang="ts">
import { computed } from 'vue'
import type { CookTaskDetail } from '@/types'

const mealTypeMap: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  supper: '夜宵'
}

const statusMap: Record<string, string> = {
  pending: '待烹饪',
  in_progress: '烹饪中',
  completed: '已完成',
  cancelled: '已取消',
  archived: '已归档'
}

const levelMap: Record<string, { text: string; type: 'danger' | 'warning' | 'info' }> = {
  high: { text: '高危', type: 'danger' },
  critical: { text: '严重', type: 'danger' },
  error: { text: '错误', type: 'danger' },
  warning: { text: '警告', type: 'warning' },
  info: { text: '提示', type: 'info' }
}

const violationTypeMap: Record<string, string> = {
  no_mask: '未佩戴口罩',
  no_hat: '未佩戴厨师帽',
  smoking: '吸烟行为',
  phone: '使用手机',
  outsider: '陌生人闯入',
  fighting: '打架斗殴',
  falling: '人员跌倒',
  gathering: '异常聚集'
}

const getLevel = (level: string | null | undefined) => levelMap[level || 'info'] || { text: level || '提示', type: 'info' as const }
const getViolationName = (item: { violationName?: string | null; violationType?: string | null }) => {
  if (item.violationName) return item.violationName
  if (item.violationType) return violationTypeMap[item.violationType] || item.violationType
  return '监控记录'
}

interface Props {
  modelValue: boolean
  task: CookTaskDetail | null
}

const props = defineProps<Props>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const temperatureSummary = computed(() => {
  if (!props.task?.temperatureRecords?.length) return '暂无温度记录'
  const latest = props.task.temperatureRecords[props.task.temperatureRecords.length - 1]
  return latest.temperature ? `${latest.temperature}℃` : '暂无温度记录'
})

const exceptionItems = computed(() => {
  if (!props.task) return []
  const items: Array<{ label: string; value: string; type: 'danger' | 'warning' | 'info' }> = []
  if (props.task.collectionStatus === 'interrupted') {
    items.push({ label: '采集状态', value: '采集中断', type: 'warning' })
  }
  if (props.task.hasSyncException || props.task.syncStatus === 'sync_failed' || props.task.syncStatus === 'conflict_pending') {
    items.push({ label: '同步状态', value: props.task.syncStatus === 'conflict_pending' ? '冲突待处理' : '同步异常', type: 'danger' })
  }
  if (props.task.hasCompensationPending || props.task.compensationStatus === 'pending') {
    items.push({ label: '补偿状态', value: '补偿待处理', type: 'danger' })
  }
  if (props.task.temperatureAbnormal && !props.task.tempAbnormalConfirmed) {
    items.push({ label: '温度复核', value: '温度异常待复核', type: 'warning' })
  }
  return items
})

const formatDateTime = (dt: string | null | undefined) => {
  if (!dt) return null
  return dt.replace('T', ' ').substring(0, 19)
}
</script>

<template>
  <el-dialog v-model="visible" title="烹饪记录详情" width="960px" destroy-on-close>
    <template v-if="task">
      <div class="detail-grid">
        <div class="detail-card">
          <div class="card-title">基础信息</div>
          <div class="detail-item"><span>任务编号</span><strong>{{ task.taskNo }}</strong></div>
          <div class="detail-item"><span>计划单号</span><strong>{{ task.planCode || '-' }}</strong></div>
          <div class="detail-item"><span>菜品名称</span><strong>{{ task.menuName }}</strong></div>
          <div class="detail-item"><span>实施日期</span><strong>{{ task.taskDate || task.planDate }}</strong></div>
          <div class="detail-item"><span>餐次</span><strong>{{ mealTypeMap[task.mealType] || task.mealType }}</strong></div>
          <div class="detail-item"><span>烹饪人</span><strong>{{ task.chefName || '待指派' }}</strong></div>
          <div class="detail-item"><span>烹饪设备</span><strong>{{ task.deviceName || '未指定' }}</strong></div>
          <div class="detail-item"><span>设备位置</span><strong>{{ task.deviceLocation || '未指定' }}</strong></div>
        </div>

        <div class="detail-card">
          <div class="card-title">执行数据</div>
          <div class="detail-item"><span>状态</span><strong>{{ statusMap[task.status] || task.status }}</strong></div>
          <div class="detail-item"><span>计划份数</span><strong>{{ task.plannedQty }}</strong></div>
          <div class="detail-item"><span>实际份数</span><strong>{{ task.actualQty }}</strong></div>
          <div class="detail-item"><span>标准时长</span><strong>{{ task.standardDuration ? `${task.standardDuration}分钟` : '-' }}</strong></div>
          <div class="detail-item"><span>实际时长</span><strong>{{ task.actualDuration ? `${task.actualDuration}分钟` : '-' }}</strong></div>
          <div class="detail-item"><span>当前温度</span><strong>{{ temperatureSummary }}</strong></div>
          <div class="detail-item">
            <span>备料状态</span>
            <strong>
              <el-tag v-if="task.materialPrepStatus === 'pending_prep'" type="warning" size="small">待备料</el-tag>
              <el-tag v-else-if="task.materialPrepStatus === 'prepared'" type="success" size="small">已备料</el-tag>
              <span v-else>--</span>
            </strong>
          </div>
          <div class="detail-item"><span>开始时间</span><strong>{{ formatDateTime(task.startTime) || '-' }}</strong></div>
          <div class="detail-item"><span>完成时间</span><strong>{{ formatDateTime(task.endTime) || '-' }}</strong></div>
        </div>
      </div>

      <div class="section-card">
        <div class="card-title">食材清单</div>
        <el-table :data="task.ingredients" size="small" border>
          <el-table-column prop="materialName" label="食材名称" min-width="160" />
          <el-table-column prop="materialSpec" label="规格" min-width="120" />
          <el-table-column label="用量" width="120">
            <template #default="{ row }">{{ row.quantity || '-' }} {{ row.unit || '' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ row.main ? '主料' : '辅料' }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="section-card">
        <div class="card-title">异常与补偿</div>
        <div v-if="exceptionItems.length" class="exception-list">
          <div v-for="item in exceptionItems" :key="`${item.label}-${item.value}`" class="exception-item">
            <span>{{ item.label }}</span>
            <el-tag :type="item.type" size="small">{{ item.value }}</el-tag>
          </div>
          <div class="detail-item"><span>最近采样时间</span><strong>{{ formatDateTime(task.lastTemperatureRecordAt) || '-' }}</strong></div>
          <div class="detail-item"><span>同步失败原因</span><strong>{{ task.latestSyncFailureReason || '-' }}</strong></div>
          <div class="detail-item"><span>自动重试次数</span><strong>{{ task.syncRetryCount ?? 0 }}</strong></div>
          <div class="detail-item"><span>已达重试上限</span><strong>{{ task.syncRetryLimitReached ? '是' : '否' }}</strong></div>
        </div>
        <el-empty v-else description="当前无异常/补偿信息" :image-size="80" />
      </div>

      <div class="section-card">
        <div class="card-title">AI监控</div>
        <div v-if="task.aiMonitorRecords?.length" class="monitor-list">
          <div v-for="(item, index) in task.aiMonitorRecords" :key="index" class="monitor-item">
            <div class="monitor-header">
              <strong>{{ getViolationName(item) }}</strong>
              <el-tag size="small" :type="getLevel(item.level).type">{{ getLevel(item.level).text }}</el-tag>
            </div>
            <div class="monitor-desc">{{ item.description || '无详细说明' }}</div>
            <div class="monitor-suggestion">建议：{{ item.suggestion || '暂无建议' }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无AI异常记录" :image-size="80" />
      </div>

      <div class="section-card">
        <div class="card-title">备注与步骤</div>
        <p class="text-block">{{ task.remark || '暂无备注' }}</p>
        <p class="text-block">{{ task.cookingSteps || '暂无烹饪步骤' }}</p>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
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

.exception-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.exception-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.monitor-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.monitor-item {
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #ebeef5;
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.monitor-desc,
.monitor-suggestion,
.text-block {
  color: #606266;
  line-height: 1.7;
}

@media (max-width: 768px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
