<template>
  <el-dialog
    v-model="visible"
    width="700px"
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="!props.submitting"
    :close-on-press-escape="!props.submitting"
    class="plan-audit-dialog"
    @close="handleClose"
  >

    <!-- Custom Header -->
    <template #header>
      <div class="dialog-header">
        <div class="header-left">
          <div class="header-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
            </svg>
          </div>
          <div class="header-title">
            <h3>审核菜谱计划</h3>
            <span class="header-subtitle">Audit Recipe Plan</span>
          </div>
        </div>
        <button class="close-btn" :disabled="props.submitting" @click="handleClose">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </template>

    <div class="audit-content" v-if="plan">
      <!-- 计划基本信息 -->
      <div class="plan-summary">
        <div class="summary-header">
          <span class="plan-code">{{ plan.planCode }}</span>
          <el-tag :type="getStatusType(plan.status)" size="small">
            {{ RECIPE_PLAN_STATUS_MAP[plan.status] || plan.status }}
          </el-tag>
        </div>
        <div class="summary-info">
          <span class="info-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
            {{ plan.planDate }}
          </span>
          <span class="info-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            {{ mealDisplayText }}
          </span>
          <span class="info-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
            </svg>
            {{ expectedCountText }}
          </span>
          <span class="info-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 2v7c0 1.1.9 2 2 2h4a2 2 0 002-2V2"/>
              <path d="M7 2v20"/>
            </svg>
            {{ plan.recipeCount || 0 }}道菜
          </span>
        </div>
      </div>

      <!-- 库存校验结果 -->
      <div class="stock-validation" v-if="stockResult">
        <div class="validation-header" :class="stockValidationHeaderClass">
          <div class="validation-icon">
            <svg v-if="stockResult.passed" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </div>
          <div class="validation-title">
            <h4>{{ stockValidationTitle }}</h4>
            <p>{{ stockResult.message }}</p>
          </div>
          <button class="revalidate-btn" @click="handleValidateStock" :disabled="validating">
            {{ validating ? '校验中...' : '重新校验' }}
          </button>
        </div>

        <div class="validation-reminder">
          库存校验结果仅作审核提醒，不拦截本次审核操作；系统仍会在 T-7、T-3、T-1 自动复检菜谱计划库存风险。
        </div>

        <!-- 缺货明细 -->
        <div class="shortage-list" v-if="stockResult.shortageItems?.length">
          <h5>缺货明细</h5>
          <el-table :data="stockResult.shortageItems" size="small">
            <el-table-column prop="materialName" label="物料名称" />
            <el-table-column prop="requiredQuantity" label="需求数量" width="100" align="center">
              <template #default="{ row }">
                {{ row.requiredQuantity }} {{ row.unit }}
              </template>
            </el-table-column>
            <el-table-column prop="availableStock" label="可用库存" width="100" align="center">
              <template #default="{ row }">
                {{ row.availableStock }} {{ row.unit }}
              </template>
            </el-table-column>
            <el-table-column prop="shortageQuantity" label="缺货数量" width="100" align="center">
              <template #default="{ row }">
                <span class="shortage-value">{{ row.shortageQuantity }} {{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="补货建议" min-width="140" align="center">
              <template #default="{ row }">
                <span v-if="row.restockSuggestion" class="suggestion-text">{{ row.restockSuggestion }}</span>
                <span v-else class="suggestion-text">需补货 {{ row.shortageQuantity }} {{ row.unit }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 所有物料库存状态 -->
        <div class="stock-status-section" v-if="stockResult.materialStockStatuses?.length">
          <el-collapse>
            <el-collapse-item title="查看所有物料库存状态" name="stockStatus">
              <el-table :data="stockResult.materialStockStatuses" size="small" max-height="300">
                <el-table-column prop="materialName" label="物料名称" min-width="120" />
                <el-table-column label="库存状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.stockStatus === 'sufficient' ? 'success' : 'danger'" size="small">
                      {{ row.stockStatus === 'sufficient' ? '充足' : '缺货' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="需求/库存" width="120" align="center">
                  <template #default="{ row }">
                    {{ row.requiredQuantity }} / {{ row.availableStock }} {{ row.unit }}
                  </template>
                </el-table-column>
                <el-table-column label="临期状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag
                      v-if="row.expiryStatus === 'expired'"
                      type="danger"
                      size="small"
                    >
                      已过期
                    </el-tag>
                    <el-tag
                      v-else-if="row.expiryStatus === 'warning'"
                      type="warning"
                      size="small"
                    >
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
      </div>

      <!-- 审核表单 -->
      <el-form
        ref="formRef"
        :model="auditForm"
        :rules="formRules"
        label-position="top"
        class="audit-form"
      >
        <el-form-item label="审核结果" prop="status">
          <el-radio-group v-model="auditForm.status" class="audit-radio-group" :disabled="props.submitting">
            <el-radio value="approved" class="radio-approved">
              <div class="radio-content">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                <span>审核通过</span>
              </div>
            </el-radio>
            <el-radio value="rejected" class="radio-rejected">
              <div class="radio-content">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="15" y1="9" x2="9" y2="15"/>
                  <line x1="9" y1="9" x2="15" y2="15"/>
                </svg>
                <span>审核拒绝</span>
              </div>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="审核意见" prop="remark">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入审核意见（拒绝时必填）"
            :disabled="props.submitting"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <button class="btn-cancel" :disabled="props.submitting" @click="handleClose">取消</button>
        <button
          class="btn-submit"
          :class="{ approved: auditForm.status === 'approved', rejected: auditForm.status === 'rejected' }"
          @click="handleSubmit"
          :disabled="props.submitting || !auditForm.status"
        >
          {{ props.submitting ? '处理中...' : '确认审核' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { RecipePlanDetail, StockRiskStatus, StockValidation } from '@/types/plan'
import { RECIPE_PLAN_STATUS_MAP, STOCK_RISK_STATUS_MAP } from '@/types/plan'
import { validateStock as validateStockApi } from '@/api/modules/plan'

const props = defineProps<{
  modelValue: boolean
  plan: RecipePlanDetail | null
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': [status: string, remark: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref<FormInstance>()
const validating = ref(false)

/** 库存校验结果 */
const stockResult = ref<StockValidation | null>(null)

const stockValidationTitle = computed(() => {
  if (!stockResult.value) {
    return '库存校验提醒'
  }
  if (stockResult.value.passed && stockResult.value.riskStatus === 'normal') {
    return '库存校验通过'
  }
  const riskStatus = (stockResult.value.riskStatus || 'unknown') as StockRiskStatus
  return `库存校验提醒：${STOCK_RISK_STATUS_MAP[riskStatus] || '待人工确认'}`
})

const stockValidationHeaderClass = computed(() => {
  if (!stockResult.value) {
    return 'failed'
  }
  if (stockResult.value.passed && stockResult.value.riskStatus === 'normal') {
    return 'passed'
  }
  if (stockResult.value.riskStatus === 'warning') {
    return 'warning'
  }
  return 'failed'
})

const mealDisplayText = computed(() => props.plan?.mealDisplayName || props.plan?.mealTypeName || '-')

const expectedCountText = computed(() => {
  if (props.plan?.expectedCountDisplay) return props.plan.expectedCountDisplay
  if (props.plan?.expectedCount) return `${props.plan.expectedCount}人`
  return '-'
})

/** 审核表单 */
const auditForm = ref({
  status: '',
  remark: ''
})

/** 表单验证规则 */
const formRules: FormRules = {
  status: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  remark: [
    {
      validator: (rule, value, callback) => {
        if (auditForm.value.status === 'rejected' && !value?.trim()) {
          callback(new Error('拒绝时必须填写审核意见'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** 监听弹窗打开 */
watch(visible, async (val) => {
  if (val && props.plan) {
    auditForm.value = { status: '', remark: '' }
    stockResult.value = null
    // 自动进行库存校验
    await handleValidateStock()
  }
})

/** 获取状态标签类型 */
const getStatusType = (status: string) => {
  const map: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    draft: 'info',
    pending: 'warning',
    approved: 'success',
    rejected: 'danger'
  }
  return map[status] || 'info'
}

/** 库存校验 */
const handleValidateStock = async () => {
  if (!props.plan) return

  validating.value = true
  try {
    const res = await validateStockApi(props.plan.id)
    if (res.code === 'SUCCESS' && res.data) {
      stockResult.value = res.data
    }
  } catch (error) {
    console.error('库存校验失败:', error)
    stockResult.value = {
      passed: false,
      message: '库存校验失败，系统仅作提醒，不影响当前审核继续，请人工关注库存与效期情况。',
      riskStatus: 'unknown',
      riskStatusName: '待人工确认'
    }
  } finally {
    validating.value = false
  }
}

/** 关闭弹窗 */
const handleClose = () => {
  if (props.submitting) return
  auditForm.value = { status: '', remark: '' }
  stockResult.value = null
  formRef.value?.resetFields()
  visible.value = false
}

/** 提交审核 */
const handleSubmit = async () => {
  if (props.submitting) return

  const valid = await formRef.value?.validate()
  if (!valid) return

  emit('submit', auditForm.value.status, auditForm.value.remark)
}
</script>

<style lang="scss" scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;600;700&display=swap');

$terracotta: #c75b39;
$terracotta-light: #e8a090;
$sage: #7a9e7e;
$sage-dark: #4a7c59;
$golden: #d4a574;
$cream: #faf7f2;
$warm-gray: #8b8178;
$deep-burgundy: #8b4049;

.plan-audit-dialog {
  :deep(.el-dialog__header) {
    padding: 0;
    margin: 0;
  }

  :deep(.el-dialog__body) {
    padding: 0;
  }

  :deep(.el-dialog__footer) {
    padding: 0;
    border-top: 1px solid rgba($terracotta-light, 0.15);
  }
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  background: linear-gradient(135deg, $cream 0%, #ffffff 100%);
  border-bottom: 1px solid rgba($terracotta-light, 0.15);

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .header-icon {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, $sage-dark 0%, $sage 100%);
    border-radius: 12px;
    box-shadow: 0 4px 12px rgba($sage-dark, 0.25);

    svg {
      width: 22px;
      height: 22px;
      color: white;
    }
  }

  .header-title {
    h3 {
      font-family: 'Noto Serif SC', serif;
      font-size: 18px;
      font-weight: 700;
      margin: 0;
      color: $text-primary;
    }

    .header-subtitle {
      font-size: 11px;
      color: $warm-gray;
      letter-spacing: 1px;
      text-transform: uppercase;
    }
  }

  .close-btn {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    background: rgba($warm-gray, 0.08);
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.2s;

    svg {
      width: 18px;
      height: 18px;
      color: $warm-gray;
    }

    &:hover {
      background: rgba($terracotta-light, 0.15);
      svg { color: $terracotta; }
    }
  }
}

.audit-content {
  padding: 24px;
}

.plan-summary {
  background: linear-gradient(135deg, #ffffff 0%, $cream 100%);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 20px;
  border: 1px solid rgba($terracotta-light, 0.15);

  .summary-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;

    .plan-code {
      font-family: 'Noto Serif SC', serif;
      font-size: 18px;
      font-weight: 700;
    }
  }

  .summary-info {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;

    .info-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: $text-secondary;

      svg {
        width: 14px;
        height: 14px;
        color: $golden;
      }
    }
  }
}

.stock-validation {
  margin-bottom: 24px;

  .validation-header {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px;
    border-radius: 10px;
    margin-bottom: 12px;

    &.passed {
      background: rgba($sage, 0.1);
      border: 1px solid rgba($sage, 0.2);

      .validation-icon {
        background: $sage-dark;
      }

      .validation-title h4 { color: $sage-dark; }
    }

    &.failed {
      background: rgba(#d45a5a, 0.08);
      border: 1px solid rgba(#d45a5a, 0.15);

      .validation-icon {
        background: #d45a5a;
      }

      .validation-title h4 { color: #d45a5a; }
    }

    &.warning {
      background: rgba($golden, 0.12);
      border: 1px solid rgba($golden, 0.22);

      .validation-icon {
        background: $golden;
      }

      .validation-title h4 { color: darken($golden, 28%); }
    }

    .validation-icon {
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 10px;

      svg {
        width: 22px;
        height: 22px;
        color: white;
      }
    }

    .validation-title {
      flex: 1;

      h4 {
        font-size: 15px;
        font-weight: 600;
        margin: 0 0 4px 0;
      }

      p {
        font-size: 13px;
        color: $text-secondary;
        margin: 0;
      }
    }

    .revalidate-btn {
      padding: 8px 16px;
      border: 1px solid rgba($terracotta-light, 0.3);
      background: white;
      border-radius: 8px;
      font-size: 13px;
      color: $terracotta;
      cursor: pointer;
      transition: all 0.2s;

      &:hover:not(:disabled) {
        background: rgba($terracotta, 0.05);
        border-color: $terracotta;
      }

      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    }
  }

  .shortage-list {
    h5 {
      font-size: 14px;
      font-weight: 600;
      margin: 0 0 12px 0;
      color: $text-primary;
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

  .validation-reminder {
    margin-bottom: 12px;
    padding: 10px 12px;
    border-radius: 8px;
    background: rgba($golden, 0.12);
    color: $text-secondary;
    font-size: 13px;
    line-height: 1.6;
  }

  .stock-status-section {
    margin-top: 12px;

    :deep(.el-collapse) {
      border: 1px solid rgba($terracotta-light, 0.15);
      border-radius: 8px;
      overflow: hidden;
    }

    :deep(.el-collapse-item__header) {
      background: rgba($cream, 0.5);
      padding: 0 16px;
      font-size: 13px;
      color: $text-secondary;
      height: 40px;
    }

    :deep(.el-collapse-item__content) {
      padding: 12px;
    }

    .normal-status {
      color: $sage-dark;
      font-size: 12px;
    }
  }
}

.audit-form {
  .audit-radio-group {
    display: flex;
    gap: 16px;
    width: 100%;

    :deep(.el-radio) {
      flex: 1;
      margin-right: 0;
      padding: 16px;
      border-radius: 10px;
      border: 2px solid rgba($terracotta-light, 0.15);
      transition: all 0.2s;

      &.radio-approved.is-checked {
        border-color: $sage-dark;
        background: rgba($sage, 0.08);

        .el-radio__inner {
          border-color: $sage-dark;
          background: $sage-dark;
        }
      }

      &.radio-rejected.is-checked {
        border-color: #d45a5a;
        background: rgba(#d45a5a, 0.05);

        .el-radio__inner {
          border-color: #d45a5a;
          background: #d45a5a;
        }
      }

      .radio-content {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;
        font-weight: 500;

        svg {
          width: 18px;
          height: 18px;
        }
      }
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  background: $cream;

  button {
    padding: 10px 24px;
    border: none;
    border-radius: 10px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
  }

  .btn-cancel {
    background: rgba($warm-gray, 0.1);
    color: $warm-gray;

    &:hover {
      background: rgba($warm-gray, 0.15);
    }
  }

  .btn-submit {
    background: rgba($warm-gray, 0.2);
    color: $warm-gray;

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    &.approved {
      background: linear-gradient(135deg, $sage-dark 0%, $sage 100%);
      color: white;

      &:hover:not(:disabled) {
        box-shadow: 0 4px 12px rgba($sage-dark, 0.3);
      }
    }

    &.rejected {
      background: linear-gradient(135deg, #d45a5a 0%, #e07a7a 100%);
      color: white;

      &:hover:not(:disabled) {
        box-shadow: 0 4px 12px rgba(#d45a5a, 0.3);
      }
    }
  }
}
</style>
