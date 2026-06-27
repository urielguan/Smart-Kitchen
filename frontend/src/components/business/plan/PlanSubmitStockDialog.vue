<script setup lang="ts">
import { computed } from 'vue'
import type { StockRiskStatus, StockValidation } from '@/types/plan'
import { STOCK_RISK_STATUS_MAP } from '@/types/plan'

interface Props {
  modelValue: boolean
  stockResult: StockValidation | null
  planCode?: string
  title?: string
  confirmable?: boolean
  confirmText?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '库存校验提醒',
  planCode: '',
  confirmable: false,
  confirmText: '继续提交'
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const riskStatus = computed<StockRiskStatus>(() => {
  return props.stockResult?.riskStatus || 'unknown'
})

const riskLabel = computed(() => STOCK_RISK_STATUS_MAP[riskStatus.value] || '待人工确认')

const validationPassed = computed(() => props.stockResult?.passed === true && riskStatus.value === 'normal')

const headerClass = computed(() => {
  if (validationPassed.value) {
    return 'passed'
  }
  if (riskStatus.value === 'warning') {
    return 'warning'
  }
  return 'failed'
})

const headerTitle = computed(() => {
  if (validationPassed.value) {
    return '库存校验通过'
  }
  return `库存校验提醒：${riskLabel.value}`
})

const reminderText = computed(() => {
  return props.confirmable
    ? '本次库存校验仅作风险提醒，不影响继续提交菜谱计划。系统仍会在 T-7、T-3、T-1 自动复检。'
    : '当前结果仅作库存风险提醒，请结合备料、效期和出库情况综合处理。'
})

const handleConfirm = () => {
  emit('confirm')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="820px"
    destroy-on-close
    append-to-body
  >
    <template v-if="stockResult">
      <div class="validation-header" :class="headerClass">
        <div class="validation-icon">
          <svg v-if="validationPassed" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 11-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <div class="validation-title">
          <h4>{{ headerTitle }}</h4>
          <p v-if="planCode" class="plan-code">计划单号：{{ planCode }}</p>
          <p>{{ stockResult.message }}</p>
        </div>
      </div>

      <div class="reminder-banner">
        {{ reminderText }}
      </div>

      <div class="shortage-list" v-if="stockResult.shortageItems?.length">
        <h5>缺货明细（共 {{ stockResult.shortageItems.length }} 种物料）</h5>
        <el-table :data="stockResult.shortageItems" size="small" border>
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="requiredQuantity" label="需求数量" width="110" align="center">
            <template #default="{ row }">{{ Math.round(row.requiredQuantity) }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column prop="availableStock" label="可用库存" width="110" align="center">
            <template #default="{ row }">{{ Math.round(row.availableStock) }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column prop="shortageQuantity" label="缺口数量" width="110" align="center">
            <template #default="{ row }">
              <span class="shortage-value">{{ Math.round(row.shortageQuantity) }} {{ row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="补货建议" min-width="160" align="center">
            <template #default="{ row }">
              <span v-if="row.restockSuggestion" class="suggestion-text">{{ row.restockSuggestion }}</span>
              <span v-else class="suggestion-text">需补货 {{ Math.round(row.shortageQuantity) }} {{ row.unit }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="stock-status-section" v-if="stockResult.materialStockStatuses?.length">
        <el-collapse>
          <el-collapse-item title="查看所有物料库存状态" name="stockStatus">
            <el-table :data="stockResult.materialStockStatuses" size="small" max-height="320" border>
              <el-table-column prop="materialName" label="物料名称" min-width="120" />
              <el-table-column label="库存状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.stockStatus === 'sufficient' ? 'success' : 'danger'" size="small">
                    {{ row.stockStatus === 'sufficient' ? '充足' : '缺货' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="需求/库存" width="130" align="center">
                <template #default="{ row }">
                  {{ Math.round(row.requiredQuantity) }} / {{ Math.round(row.availableStock) }} {{ row.unit }}
                </template>
              </el-table-column>
              <el-table-column label="临期状态" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.expiryStatus === 'expired'" type="danger" size="small">已过期</el-tag>
                  <el-tag v-else-if="row.expiryStatus === 'warning'" type="warning" size="small">
                    临期 ({{ row.daysToExpiry }}天)
                  </el-tag>
                  <span v-else class="normal-status">正常</span>
                </template>
              </el-table-column>
              <el-table-column prop="nearestExpiryDate" label="最近到期" width="110" align="center">
                <template #default="{ row }">
                  <span v-if="row.nearestExpiryDate">{{ row.nearestExpiryDate }}</span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
    </template>

    <template #footer>
      <el-button @click="visible = false">{{ confirmable ? '稍后处理' : '关闭' }}</el-button>
      <el-button v-if="confirmable" type="primary" @click="handleConfirm">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.validation-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 12px;

  &.passed {
    background: #f0f9eb;
    border: 1px solid #c2e7b0;
  }

  &.warning {
    background: #fdf6ec;
    border: 1px solid #f5d7a0;
  }

  &.failed {
    background: #fef0f0;
    border: 1px solid #fbc4c4;
  }

  .validation-icon {
    svg {
      width: 28px;
      height: 28px;
    }
  }

  &.passed .validation-icon svg {
    color: #67c23a;
  }

  &.warning .validation-icon svg {
    color: #e6a23c;
  }

  &.failed .validation-icon svg {
    color: #f56c6c;
  }

  .validation-title {
    flex: 1;

    h4 {
      margin: 0 0 4px;
      font-size: 16px;
      color: #303133;
    }

    .plan-code {
      margin-bottom: 6px;
      color: #606266;
      font-weight: 500;
    }

    p {
      margin: 0;
      font-size: 13px;
      color: #606266;
      white-space: pre-line;
      line-height: 1.6;
    }
  }
}

.reminder-banner {
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.shortage-list {
  h5 {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 10px;
  }

  .shortage-value {
    color: #d45a5a;
    font-weight: 600;
  }

  .suggestion-text {
    color: #e6a23c;
    font-size: 13px;
  }
}

.stock-status-section {
  margin-top: 16px;

  :deep(.el-collapse) {
    border: none;
  }

  :deep(.el-collapse-item__header) {
    background: #f5f7fa;
    padding: 0 12px;
    border-radius: 6px;
    font-size: 13px;
    color: #606266;
  }

  .normal-status {
    color: #67c23a;
  }
}
</style>
