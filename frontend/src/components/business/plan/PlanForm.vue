<template>
  <el-dialog
    v-model="visible"
    width="758px"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    class="plan-form-dialog"
    @closed="handleClosed"
  >
    <template #header>
      <div class="dialog-header">
        <div class="dialog-header__content">
          <span class="dialog-title">{{ dialogTitle }}</span>
          <button class="dialog-close" type="button" @click="visible = false">
            <el-icon class="dialog-close__icon"><Close /></el-icon>
          </button>
        </div>
      </div>
    </template>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="left"
      label-width="80px"
      class="plan-form"
    >
      <el-alert
        v-if="isAdjustmentMode && (editData?.planCode || editData?.adjustmentHint)"
        type="info"
        :closable="false"
        show-icon
        class="mb-16"
      >
        <template #title>
          <span v-if="editData?.planCode">当前调整计划单号：{{ editData.planCode }}</span>
          <span v-else>调整说明</span>
        </template>
        <div v-if="editData?.adjustmentHint" class="adjustment-hint-text">{{ editData.adjustmentHint }}</div>
      </el-alert>

      <el-alert
        v-if="rejectionMode && editData && 'auditRemark' in editData && editData.auditRemark"
        type="error"
        :closable="false"
        show-icon
        class="mb-16"
      >
        <template #title>
          <span class="alert-title">驳回原因：</span>{{ editData.auditRemark }}
        </template>
      </el-alert>

      <!-- === 分区1：基础信息 === -->
      <div class="form-section">
        <div class="section-header">
          <span class="section-bar"></span>
          <span class="section-title">基础信息</span>
        </div>
        <div class="section-body section-body--basic">
          <div class="basic-grid">
            <el-form-item class="basic-item basic-item--left basic-item--date" label="计划日期：">
              <el-input class="readonly-date-input" :model-value="displayCurrentDate" readonly />
            </el-form-item>

            <el-form-item class="basic-item basic-item--right basic-item--range" label="实施时间范围：" prop="planDate" required>
              <div v-if="planDimension === 'day'" class="day-range-fields day-range-fields--range-look">
                <div class="day-range-fields__content">
                  <el-date-picker
                    v-model="dayStartDate"
                    class="day-range-fields__picker day-range-fields__picker--start"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :editable="false"
                    placeholder="开始日期"
                    style="width: 100%"
                    :disabled-date="disableDayStartDate"
                  />
                  <span class="day-range-fields__separator">-</span>
                  <el-date-picker
                    v-model="dayEndDate"
                    class="day-range-fields__picker day-range-fields__picker--end"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :editable="false"
                    placeholder="结束日期"
                    style="width: 100%"
                    :disabled-date="disableDayEndDate"
                  />
                </div>
                <el-icon class="day-range-fields__calendar"><Calendar /></el-icon>
              </div>
              <el-date-picker
                v-else-if="planDimension === 'week'"
                v-model="weekAnchorDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择周内任意一天"
                style="width: 100%"
                @change="handleWeekAnchorChange"
              />
              <el-date-picker
                v-else
                v-model="monthValue"
                type="month"
                value-format="YYYY-MM"
                placeholder="请选择实施月份"
                style="width: 100%"
                @change="handleMonthChange"
              />
            </el-form-item>

            <el-form-item class="basic-item basic-item--left basic-item--meal" label="餐次：" required>
              <el-select
                :model-value="formData.mealSchedules[0]?.mealType || ''"
                placeholder="请选择餐次"
                @update:model-value="handleTopMealTypeChange"
              >
                <el-option
                  v-for="option in mealTypeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item class="basic-item basic-item--right basic-item--org" label="实施组织：" prop="orgId" required>
              <button
                type="button"
                class="plan-org-trigger"
                @click="orgPickerVisible = true"
              >
                <span
                  class="plan-org-trigger__text"
                  :class="{ 'is-placeholder': !formData.orgName }"
                >
                  {{ formData.orgName || '请选择实施组织' }}
                </span>
              </button>
            </el-form-item>

            <el-form-item class="basic-item basic-item--left basic-item--count" label="就餐人数：" required>
              <el-input-number
                v-model="formData.expectedCount"
                :min="0"
                :max="999999"
                :precision="0"
                style="width: 100%"
                @change="handleTopExpectedCountChange"
              />
            </el-form-item>
          </div>
          <div v-if="planDimension === 'week'" class="field-tip field-tip--basic">选择任意一天后，系统自动按自然周生成开始和结束日期。</div>
          <div v-else-if="planDimension === 'month'" class="field-tip field-tip--basic">系统自动按所选自然月生成开始和结束日期。</div>
        </div>
      </div>

      <!-- === 分区2：人群膳食画像 === -->
      <div class="form-section form-section--no-divider">
        <div class="section-header section-header--with-hint">
          <span class="section-bar"></span>
          <span class="section-title">人群膳食画像</span>
          <span v-if="props.formMode !== 'create'" class="field-hint field-hint--inline">根据就餐人群特征，AI将推荐更合适的菜谱搭配</span>
        </div>
        <div class="section-body section-body--profile">
          <div class="profile-grid">
            <el-form-item class="profile-item profile-item--target" label="目标人群：">
              <el-select
                v-model="formData.targetGroup"
                placeholder="请选择目标人群"
                style="width: 100%"
              >
                <el-option
                  v-for="option in targetGroupOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item class="profile-item profile-item--health" label="健康状况：">
              <el-select
                v-model="formData.healthStatus"
                multiple
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="请选择健康状况"
                style="width: 100%"
              >
                <el-option
                  v-for="option in healthStatusOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item class="profile-textarea-item" label="饮食限制说明：">
            <el-input
              v-model="formData.dietRestrictions"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="请输入饮食限制说明"
            />
          </el-form-item>

          <div class="nutrition-target-bar">
            <span class="nutrition-label">人均每日营养目标参考</span>
            <span class="nutrition-tag">{{ targetGroupLabel }}</span>
            <span class="nutrition-values">{{ nutritionSummary }}</span>
          </div>
        </div>
      </div>

      <!-- === 分区3：用餐偏好 === -->
      <div class="form-section form-section--no-divider">
        <div class="section-header section-header--with-hint">
          <span class="section-bar"></span>
          <span class="section-title">用餐偏好</span>
          <span v-if="props.formMode !== 'create'" class="field-hint field-hint--inline">设置就餐人员的口味偏好，AI将优先推荐符合偏好的菜谱</span>
        </div>
        <div class="section-body section-body--preference">
          <div class="preference-grid">
            <el-form-item class="preference-item preference-item--left" label="口味偏好：">
              <el-select
                v-model="selectedFlavorPreferences"
                multiple
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="请选择口味偏好"
                style="width: 100%"
              >
                <el-option
                  v-for="option in flavorPreferenceOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item class="preference-item preference-item--right" label="饮食偏好：">
              <el-select
                v-model="selectedDietTags"
                multiple
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="请选择饮食偏好"
                style="width: 100%"
              >
                <el-option
                  v-for="option in dietTagOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item class="preference-item preference-item--left" label="辣度级别：">
              <el-select
                v-model="formData.spicyLevel"
                placeholder="请选择辣度级别"
                style="width: 100%"
              >
                <el-option
                  v-for="option in spicyLevelOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item class="preference-item preference-item--left" label="禁忌食材：">
              <el-input
                v-model="formData.avoidIngredientIds"
                placeholder="请输入禁忌食材"
              />
            </el-form-item>
          </div>

          <el-form-item v-if="isAdjustmentMode" label="调整原因" prop="adjustReason">
            <el-input
              v-model="adjustReason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="请输入调整原因"
            />
          </el-form-item>
        </div>
      </div>

      <!-- === 分区4：菜谱明细 === -->
      <div class="form-section form-section--no-divider form-section--recipe-detail">
        <div class="section-header section-header--detail-actions">
          <div class="section-header__title-wrap">
            <span class="section-bar"></span>
            <span class="section-title">菜谱明细</span>
          </div>
          <div class="detail-header-actions">
            <el-button class="detail-action-btn detail-action-btn--add" @click="handleAddRecipeRow">
              <span class="detail-action-btn__icon" aria-hidden="true">+</span>
              <span>添加菜谱</span>
            </el-button>
            <el-button
              v-if="props.formMode === 'create' || props.formMode === 'edit'"
              class="detail-action-btn detail-action-btn--ai"
              :loading="aiRecommendLoading"
              @click="handleAiRecommend"
            >AI推荐</el-button>
          </div>
        </div>
        <div class="section-body">
          <div class="dimension-bar">
            <span class="dimension-label">计划维度：</span>
            <div class="dimension-segmented">
              <button
                type="button"
                class="dimension-segmented__item"
                :class="{ 'is-active': planDimension === 'day' }"
                @click="handlePlanDimensionChange('day')"
              >单次</button>
              <button
                type="button"
                class="dimension-segmented__item"
                :class="{ 'is-active': planDimension === 'week' }"
                @click="handlePlanDimensionChange('week')"
              >周计划</button>
              <button
                type="button"
                class="dimension-segmented__item"
                :class="{ 'is-active': planDimension === 'month' }"
                @click="handlePlanDimensionChange('month')"
              >月计划</button>
            </div>
            <div class="dimension-range-box">
              <span class="dimension-range-box__label">预算限制：</span>
              <div class="dimension-range-stepper">
                <button type="button" class="dimension-range-stepper__btn" @click="handleAiBudgetLimitChange(Math.max(0, (aiBudgetLimit || 0) - 10))">-</button>
                <div class="dimension-range-stepper__value">{{ aiBudgetLimit || 0 }}</div>
                <button type="button" class="dimension-range-stepper__btn" @click="handleAiBudgetLimitChange((aiBudgetLimit || 0) + 10)">+</button>
              </div>
              <span class="dimension-range-box__unit">元</span>
            </div>
            <label class="dimension-check">
              <input v-model="aiConsiderStock" type="checkbox" />
              <span>考虑库存</span>
            </label>
            <label class="dimension-check">
              <input v-model="aiPrioritizeExpiring" type="checkbox" />
              <span>优先临期食材</span>
            </label>
          </div>

        <div
          v-for="(schedule, scheduleIndex) in formData.mealSchedules"
          :key="schedule.mealKey || scheduleIndex"
          class="meal-schedule-card"
        >
          <div v-if="schedule.recipes.length" class="recipe-table">
            <div
              v-for="(row, rowIndex) in schedule.recipes"
              :key="`${schedule.mealKey || scheduleIndex}-${row.recipeId}`"
              class="recipe-info-card"
              :class="{ 'is-collapsed': isRecipeCollapsed(schedule, row.recipeId) }"
            >
              <div class="recipe-info-card__header">
                <div class="recipe-info-card__meta">
                  <span class="recipe-info-card__index">{{ rowIndex + 1 }}</span>
                  <span class="recipe-info-card__name">{{ getRecipeName(row.recipeId) }}</span>
                  <span class="recipe-info-card__tag">{{ getRecipeCategory(row.recipeId) || '标签' }}</span>
                </div>
                <div class="recipe-info-card__controls">
                  <div class="recipe-info-card__controls-main">
                    <div class="recipe-info-card__stepper-wrap">
                      <div class="recipe-info-card__stepper">
                        <button type="button" class="recipe-info-card__stepper-btn" @click="row.plannedServings = Math.max(1, Number(row.plannedServings || 1) - 1); handleRecipeServingsChange(schedule, row)">-</button>
                        <div class="recipe-info-card__stepper-value">{{ row.plannedServings || 1 }}</div>
                        <button type="button" class="recipe-info-card__stepper-btn" @click="row.plannedServings = Number(row.plannedServings || 1) + 1; handleRecipeServingsChange(schedule, row)">+</button>
                      </div>
                      <span class="recipe-info-card__stepper-unit">份</span>
                    </div>
                    <div class="recipe-info-card__pricing-group">
                      <div class="recipe-info-card__price-block">
                        <div class="recipe-info-card__price-unit">¥{{ Number(getRecipeUnitCost(row.recipeId) || 0).toFixed(0) }}</div>
                        <div class="recipe-info-card__price-total">¥{{ Number((getRecipeUnitCost(row.recipeId) || 0) * (row.plannedServings || 0)).toFixed(0) }}</div>
                      </div>
                      <button type="button" class="recipe-info-card__collapse" @click="toggleRecipeCollapsed(schedule, row.recipeId)">
                        <span>{{ isRecipeCollapsed(schedule, row.recipeId) ? '展开' : '收起' }}</span>
                        <span
                          class="recipe-info-card__collapse-icon"
                          :class="isRecipeCollapsed(schedule, row.recipeId) ? 'is-expanded' : 'is-collapsed'"
                          aria-hidden="true"
                        ></span>
                      </button>
                    </div>
                  </div>
                  <button type="button" class="recipe-info-card__delete" @click="removeScheduleRecipe(schedule, rowIndex)">×</button>
                </div>
              </div>
              <div v-if="!isRecipeCollapsed(schedule, row.recipeId)">
                <div class="recipe-info-card__divider"></div>
                <div class="recipe-info-card__material-list recipe-info-card__material-list--expanded">
                  <span
                    v-for="ingredient in getRecipeIngredientTags(row.recipeId, row.plannedServings)"
                    :key="ingredient.key"
                    class="recipe-info-card__material"
                  >
                    {{ ingredient.text }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="summary-bar summary-bar--cost-total">
          <span class="summary-bar__label">预估总成本:</span>
          <span class="summary-bar__amount">{{ formatMoney(totalEstimatedCost) }}</span>
          <span class="summary-bar__meta">（{{ totalRecipeCount }} 道菜 × {{ totalServings }} 份）</span>
        </div>
        </div>
      </div>

      <div v-if="props.formMode !== 'create' && props.formMode !== 'edit'" class="form-section form-section--no-divider nutrition-analysis-section">
        <div class="nutrition-analysis-section__header">
          <div class="nutrition-analysis-section__title-wrap">
            <span class="nutrition-analysis-section__bar"></span>
            <span class="nutrition-analysis-section__title">AI营养分析</span>
          </div>
          <el-button
            class="nutrition-analysis-section__action"
            type="primary"
            :loading="nutritionAnalysisLoading"
            @click="handleGenerateNutritionAnalysis"
          >生成分析</el-button>
        </div>
        <div class="nutrition-analysis-section__body">
          <div class="nutrition-analysis-section__panel">
            <div class="nutrition-analysis-panel__summary">
              <span class="nutrition-analysis-panel__summary-title">营养成分汇总</span>
              <span class="nutrition-analysis-panel__summary-meta">共 {{ totalServings }} 份 / {{ analysisServingCount }} 人 / 预估 ¥{{ formatMoney(totalEstimatedCost) }}</span>
            </div>
            <div class="nutrition-analysis-panel__cards">
              <div
                v-for="card in nutritionAnalysisCards"
                :key="card.key"
                class="nutrition-analysis-card"
              >
                <img :src="card.icon" :alt="card.label" class="nutrition-analysis-card__icon">
                <div class="nutrition-analysis-card__content">
                  <span class="nutrition-analysis-card__value">{{ card.total }}</span>
                  <span class="nutrition-analysis-card__label">{{ card.label }}</span>
                  <span class="nutrition-analysis-card__per-capita">人均 {{ card.perCapita }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="nutrition-balance-panel">
            <div class="nutrition-balance-panel__header">
              <span class="nutrition-balance-panel__title">营养均衡度</span>
              <div class="nutrition-balance-panel__score-badge" :class="nutritionBalanceBadgeClass">
                <span class="nutrition-balance-panel__score">{{ nutritionBalanceScore }}</span>
                <span class="nutrition-balance-panel__grade">{{ nutritionBalanceBadgeText }}</span>
              </div>
            </div>
            <div class="nutrition-balance-panel__rows">
              <div
                v-for="item in nutritionBalanceItems"
                :key="item.key"
                class="nutrition-balance-panel__row"
              >
                <span class="nutrition-balance-panel__label">{{ item.label }}</span>
                <div class="nutrition-balance-panel__track">
                  <div
                    class="nutrition-balance-panel__fill"
                    :class="`is-${item.key}`"
                    :style="{ width: `${item.value}%` }"
                  ></div>
                </div>
                <span class="nutrition-balance-panel__value">{{ item.displayValue }}</span>
              </div>
            </div>
          </div>

          <div class="nutrition-suggestion-panel">
            <div class="nutrition-suggestion-panel__title">AI优化建议</div>
            <ul v-if="nutritionSuggestionItems.length" class="nutrition-suggestion-panel__list">
              <li
                v-for="(item, index) in nutritionSuggestionItems"
                :key="`${index}-${item}`"
                class="nutrition-suggestion-panel__item"
              >
                {{ item }}
              </li>
            </ul>
            <div v-else class="nutrition-suggestion-panel__empty">暂无优化建议</div>
          </div>
        </div>
      </div>

      <div class="form-section form-section--no-divider">
        <div class="section-header">
          <span class="section-bar"></span>
          <span class="section-title">备注信息</span>
        </div>
        <div class="section-body section-body--remark">
          <el-form-item class="profile-textarea-item profile-textarea-item--remark" prop="remark">
            <el-input
              v-model="formData.remark"
              type="textarea"
              :rows="3"
              maxlength="200"
              placeholder="备注"
            />
          </el-form-item>
        </div>
      </div>

      <RecipeSelectDialog
        v-model="recipePickerVisible"
        :exclude-ids="currentRecipeExcludeIds"
        @confirm="handleRecipePickerConfirm"
      />

      <PlanOrgSelectDialog
        v-model="orgPickerVisible"
        :selected-org-id="formData.orgId ?? null"
        :selected-org-name="formData.orgName"
        @confirm="handleOrgPickerConfirm"
      />
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <template v-if="rejectionMode">
          <el-button class="btn-cancel" @click="visible = false">取消</el-button>
          <el-button class="btn-confirm" :loading="submitting" @click="handleSaveDraft">保存为草稿</el-button>
          <el-button class="btn-confirm" type="primary" :loading="submitting" @click="handleResubmit">重新提交</el-button>
        </template>
        <template v-else>
          <el-button class="btn-cancel" @click="visible = false">取消</el-button>
          <el-button class="btn-confirm" type="primary" :loading="submitting" @click="handleSubmit">
            {{ isAdjustmentMode ? '提交调整申请' : '确定' }}
          </el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, Close } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { getRecipeDetail } from '@/api/modules/recipe'
import { analyzePlanNutrition, getAiRecommendRecipesEnhanced } from '@/api/modules/plan'
import PlanOrgSelectDialog from '@/components/business/plan/PlanOrgSelectDialog.vue'
import RecipeSelectDialog from '@/components/business/plan/RecipeSelectDialog.vue'
import caloriesIcon from '@/assets/images/nutrition-calories.png'
import proteinIcon from '@/assets/images/nutrition-protein.png'
import carbsIcon from '@/assets/images/nutrition-carbs.png'
import fatIcon from '@/assets/images/nutrition-fat.png'
import { MEAL_TYPE_OPTIONS, MEAL_TYPE_MAP } from '@/constants/plan'
import type { NutritionInfo, Recipe, RecipeIngredient } from '@/types/recipe'
import {
  DIET_TAG_OPTIONS,
  FLAVOR_PREFERENCE_OPTIONS,
  SPICY_LEVEL_OPTIONS
} from '@/types/plan'
import type {
  RecipePlan,
  RecipePlanDetail,
  RecipePlanForm,
  RecipePlanFormItem,
  RecipePlanFormSubmitPayload,
  RecipePlanMealFormItem,
  AINutritionAssessment
} from '@/types/plan'

type PlanDimension = 'day' | 'week' | 'month'

interface RecipeMeta {
  id: number
  menuName: string
  categoryName?: string
  unitCost?: number
  nutritionInfo?: NutritionInfo
  ingredients?: RecipeIngredient[]
  cookingSteps?: string
  cookingTime?: number
  status?: string
  loading?: boolean
  loadFailed?: boolean
}

interface IngredientRow {
  materialName: string
  unit: string
  perServingQuantity: number
  totalQuantity: number
  isMain: boolean
}

interface IngredientTagItem {
  key: string
  text: string
}

const mealTypeOptions = [...MEAL_TYPE_OPTIONS, { value: 'custom', label: '自定义餐次' }]

/** 餐次标签文案 */
const mealTagLabel = (mealType: string): string => MEAL_TYPE_MAP[mealType] || ''

const targetGroupOptions = [
  { value: 'adult', label: '普通成人' },
  { value: 'elderly', label: '老年人' },
  { value: 'child', label: '儿童' },
  { value: 'teenager', label: '青少年' },
  { value: 'patient', label: '病患' },
  { value: 'worker', label: '体力劳动者' }
]

const healthStatusOptions = [
  { value: 'diabetes', label: '糖尿病' },
  { value: 'hypertension', label: '高血压' },
  { value: 'hyperlipidemia', label: '高血脂' },
  { value: 'obesity', label: '肥胖' },
  { value: 'gout', label: '痛风' },
  { value: 'kidney_disease', label: '肾病' },
  { value: 'stomach_disease', label: '胃病' },
  { value: 'anemia', label: '贫血' }
]

const flavorPreferenceOptions = FLAVOR_PREFERENCE_OPTIONS
const dietTagOptions = DIET_TAG_OPTIONS
const spicyLevelOptions = SPICY_LEVEL_OPTIONS

const props = withDefaults(defineProps<{
  modelValue: boolean
  editData?: RecipePlanDetail | RecipePlan | null
  mode?: 'default' | 'adjustment'
  rejectionMode?: boolean
  formMode?: 'create' | 'edit' | 'copy'
}>(), {
  editData: null,
  mode: 'default',
  rejectionMode: false,
  formMode: 'create'
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: RecipePlanFormSubmitPayload, done?: () => void]
  'save-draft': [payload: RecipePlanFormSubmitPayload, done?: () => void]
  resubmit: [payload: RecipePlanFormSubmitPayload, done?: () => void]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
const nutritionAnalysisLoading = ref(false)
const aiRecommendLoading = ref(false)
const aiBudgetLimit = ref(0)
const aiConsiderStock = ref(true)
const aiPrioritizeExpiring = ref(false)
const nutritionAnalysisResult = ref<AINutritionAssessment | null>(null)
const isInitializing = ref(false)
const recipePickerVisible = ref(false)
const orgPickerVisible = ref(false)
const currentRecipeExcludeIds = ref<number[]>([])
const adjustReason = ref('')
const originalSnapshot = ref('')
const planDimension = ref<PlanDimension>('day')
const weekAnchorDate = ref('')
const monthValue = ref('')
const recipeMetaMap = ref<Record<number, RecipeMeta>>({})
const manualServingKeys = ref<string[]>([])
const collapsedRecipeKeys = ref<string[]>([])

const createMealKey = () => `meal-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`

const createEmptyMealSchedule = (sortOrder = 1): RecipePlanMealFormItem => ({
  mealKey: createMealKey(),
  mealType: '',
  mealName: '',
  expectedCount: 1,
  sortOrder,
  recipes: []
})

const createEmptyForm = (): RecipePlanForm => ({
  planDate: '',
  startDate: '',
  endDate: '',
  orgId: undefined,
  orgName: '',
  mealType: '',
  expectedCount: 1,
  targetGroup: '',
  healthStatus: [],
  dietRestrictions: '',
  aiNutritionAssessment: '',
  remark: '',
  recipes: [],
  mealSchedules: [createEmptyMealSchedule()],
  flavorPreferences: '',
  spicyLevel: 0,
  avoidIngredientIds: '',
  dietTags: ''
})


const syncSelectedOrg = (orgId?: number, orgName = '') => {
  formData.value.orgId = orgId
  formData.value.orgName = orgId ? orgName : ''
}

const formData = ref<RecipePlanForm>(createEmptyForm())

const isAdjustmentMode = computed(() => props.mode === 'adjustment')
const editData = computed(() => props.editData)

const dialogTitle = computed(() => {
  if (isAdjustmentMode.value) return '调整菜谱计划'
  if (props.formMode === 'copy') return '复制菜谱计划'
  return props.editData ? '编辑菜谱计划' : '新增菜谱计划'
})

const totalRecipeCount = computed(() =>
  formData.value.mealSchedules.reduce((sum, schedule) => sum + schedule.recipes.length, 0)
)

const totalServings = computed(() =>
  formData.value.mealSchedules.reduce((sum, schedule) => sum + getScheduleTotalServings(schedule), 0)
)

const totalEstimatedCost = computed(() =>
  formData.value.mealSchedules.reduce((sum, schedule) => sum + getScheduleTotalEstimatedCost(schedule), 0)
)

const implementationRangeText = computed(() => {
  if (!formData.value.startDate || !formData.value.endDate) return '-'
  if (formData.value.startDate === formData.value.endDate) return formData.value.startDate
  return `${formData.value.startDate} 至 ${formData.value.endDate}`
})

const dayStartDate = computed({
  get: () => formData.value.startDate || '',
  set: (value: string) => {
    if (!value) {
      formData.value.planDate = ''
      formData.value.startDate = ''
      formData.value.endDate = ''
      return
    }
    formData.value.planDate = value
    formData.value.startDate = value
    if (!formData.value.endDate || formData.value.endDate < value) {
      formData.value.endDate = value
    }
  }
})

const dayEndDate = computed({
  get: () => formData.value.endDate || '',
  set: (value: string) => {
    if (!value) {
      formData.value.endDate = formData.value.startDate || ''
      return
    }
    formData.value.endDate = value
  }
})

const disableDayStartDate = (date: Date) => formatDate(date) < todayString()

const disableDayEndDate = (date: Date) => {
  if (disableDayStartDate(date)) return true
  if (!formData.value.startDate) return false
  return formatDate(date) < formData.value.startDate
}

const selectedFlavorPreferences = computed<string[]>({
  get: () => normalizeMultiValue(formData.value.flavorPreferences),
  set: (value) => {
    formData.value.flavorPreferences = [...value].join(',')
  }
})

const selectedDietTags = computed<string[]>({
  get: () => normalizeMultiValue(formData.value.dietTags),
  set: (value) => {
    formData.value.dietTags = [...value].join(',')
  }
})

/** 目标人群标签（用于营养目标展示） */
const targetGroupLabel = computed(() => {
  const opt = targetGroupOptions.find(o => o.value === formData.value.targetGroup)
  return opt?.label || '普通成人'
})

/** 当前日期显示（基础信息中的固定展示值） */
const displayCurrentDate = computed(() => todayString())

/** 营养目标摘要文本（基于目标人群） */
const nutritionSummary = computed(() => {
  const map: Record<string, string> = {
    adult: '热量2000 kcal、蛋白质65 g、碳水300 g、脂肪60 g、钠2000 mg、膳食纤维25 g',
    elderly: '热量 1800 kcal · 蛋白质 75 g · 钙 1000 mg · 脂肪 50 g',
    child: '热量 1600 kcal · 蛋白质 55 g · 碳水 220 g · 钙 800 mg',
    teenager: '热量 2400 kcal · 蛋白质 80 g · 碳水 350 g · 脂肪 70 g',
    patient: '热量 1800 kcal · 蛋白质 70 g · 碳水 250 g · 脂肪 45 g',
    worker: '热量 2800 kcal · 蛋白质 85 g · 碳水 400 g · 脂肪 75 g'
  }
  return map[formData.value.targetGroup] || map.adult
})

const analysisServingCount = computed(() => Number(formData.value.expectedCount || 0))

const clampPercent = (value: number) => Math.min(100, Math.max(0, Number(value || 0)))

const nutritionBalanceGradeMap: Record<string, { text: string; modifier: string }> = {
  优秀: { text: '优秀', modifier: 'is-excellent' },
  良好: { text: '良好', modifier: 'is-good' },
  达标: { text: '达标', modifier: 'is-normal' },
  需改进: { text: '需改进', modifier: 'is-warning' },
  无数据: { text: '无数据', modifier: 'is-pending' }
}

const nutritionBalanceBadgeText = computed(() => {
  const grade = nutritionAnalysisResult.value?.grade || ''
  return nutritionBalanceGradeMap[grade]?.text || '待分析'
})

const nutritionBalanceBadgeClass = computed(() => {
  const grade = nutritionAnalysisResult.value?.grade || ''
  return nutritionBalanceGradeMap[grade]?.modifier || 'is-pending'
})

const nutritionBalanceItems = computed(() => {
  const dietStructure = nutritionAnalysisResult.value?.dietStructure

  if (!dietStructure) {
    return [
      { key: 'protein', label: '蛋白质', value: 0, displayValue: '0%' },
      { key: 'carbohydrate', label: '碳水', value: 0, displayValue: '0%' },
      { key: 'fat', label: '脂肪', value: 0, displayValue: '0%' }
    ]
  }

  return [
    {
      key: 'protein',
      label: '蛋白质',
      value: clampPercent(dietStructure.proteinRatio),
      displayValue: `${Math.round(Number(dietStructure.proteinRatio || 0))}%`
    },
    {
      key: 'carbohydrate',
      label: '碳水',
      value: clampPercent(dietStructure.carbohydrateRatio),
      displayValue: `${Math.round(Number(dietStructure.carbohydrateRatio || 0))}%`
    },
    {
      key: 'fat',
      label: '脂肪',
      value: clampPercent(dietStructure.fatRatio),
      displayValue: `${Math.round(Number(dietStructure.fatRatio || 0))}%`
    }
  ]
})

const nutritionBalanceScore = computed(() => Number(nutritionAnalysisResult.value?.overallScore || 0))

const nutritionSuggestionItems = computed(() => {
  const suggestions = nutritionAnalysisResult.value?.suggestions || []
  if (suggestions.length) return suggestions

  const aiSuggestion = nutritionAnalysisResult.value?.aiOptimizationSuggestions?.trim()
  if (aiSuggestion) {
    return aiSuggestion
      .split(/\n+|；|;/)
      .map(item => item.trim())
      .filter(Boolean)
  }

  const fallback = nutritionAnalysisResult.value?.dietStructure?.evaluation || nutritionAnalysisResult.value?.assessment || ''
  return fallback ? [fallback] : []
})

const nutritionAnalysisCards = computed(() => {
  const analysis = nutritionAnalysisResult.value
  const formatCardValue = (value: number, digits = 1) => Number(value || 0).toFixed(digits)

  if (!analysis) {
    return [
      {
        key: 'calories',
        icon: caloriesIcon,
        total: formatCardValue(0, 0),
        label: '总热量 (kcal)',
        perCapita: formatCardValue(0, 0)
      },
      {
        key: 'protein',
        icon: proteinIcon,
        total: formatCardValue(0, 1),
        label: '蛋白质 (g)',
        perCapita: formatCardValue(0, 1)
      },
      {
        key: 'carbs',
        icon: carbsIcon,
        total: formatCardValue(0, 1),
        label: '碳水化合物 (g)',
        perCapita: formatCardValue(0, 1)
      },
      {
        key: 'fat',
        icon: fatIcon,
        total: formatCardValue(0, 1),
        label: '脂肪 (g)',
        perCapita: formatCardValue(0, 1)
      }
    ]
  }

  return [
    {
      key: 'calories',
      icon: caloriesIcon,
      total: formatCardValue(analysis.totalCalories, 0),
      label: '总热量 (kcal)',
      perCapita: formatCardValue(analysis.avgCalories, 0)
    },
    {
      key: 'protein',
      icon: proteinIcon,
      total: formatCardValue(analysis.totalProtein, 1),
      label: '蛋白质 (g)',
      perCapita: formatCardValue(analysis.avgProtein, 1)
    },
    {
      key: 'carbs',
      icon: carbsIcon,
      total: formatCardValue(analysis.totalCarbohydrate, 1),
      label: '碳水化合物 (g)',
      perCapita: formatCardValue(analysis.avgCarbohydrate, 1)
    },
    {
      key: 'fat',
      icon: fatIcon,
      total: formatCardValue(analysis.totalFat, 1),
      label: '脂肪 (g)',
      perCapita: formatCardValue(analysis.avgFat, 1)
    }
  ]
})

const normalizeTargetGroup = (targetGroup?: string) => {
  if (!targetGroup) return 'adult'
  if (targetGroup === 'general') return 'adult'
  return targetGroup
}

const handleAiBudgetLimitChange = (value: number | undefined) => {
  aiBudgetLimit.value = Math.max(0, Number(value || 0))
}

const getAnalysisPlanDimension = () => {
  if (planDimension.value === 'day') return 'single'
  return planDimension.value
}

const getPlanDaysCount = () => {
  if (!formData.value.startDate || !formData.value.endDate) return undefined
  return diffDays(formData.value.startDate, formData.value.endDate) + 1
}

const buildAiRecommendPayload = () => ({
  targetGroup: normalizeTargetGroup(formData.value.targetGroup),
  healthStatus: normalizeHealthStatus(formData.value.healthStatus || []).join(','),
  expectedCount: Number(formData.value.expectedCount || 0),
  budgetLimit: aiBudgetLimit.value > 0 ? aiBudgetLimit.value : undefined,
  planDimension: getAnalysisPlanDimension(),
  weekStartDate: formData.value.startDate || undefined,
  daysCount: getPlanDaysCount(),
  considerStock: aiConsiderStock.value,
  prioritizeExpiring: aiPrioritizeExpiring.value,
  flavorPreferences: normalizeMultiValue(formData.value.flavorPreferences).join(','),
  spicyLevel: Number(formData.value.spicyLevel || 0),
  avoidIngredientIds: formData.value.avoidIngredientIds?.trim() || '',
  dietTags: normalizeMultiValue(formData.value.dietTags).join(',')
})

const applyRecommendedRecipes = async (recipes: Recipe[]) => {
  const schedule = ensureDefaultMealSchedule()
  const nextExpectedCount = normalizePositiveInteger(schedule.expectedCount || formData.value.expectedCount) || 1

  recipes.forEach((recipe) => {
    mergeRecipeMeta({
      id: recipe.id,
      menuName: recipe.menuName,
      categoryName: recipe.categoryName,
      unitCost: recipe.unitCost,
      nutritionInfo: recipe.nutritionInfo,
      status: recipe.status
    })
  })

  schedule.recipes = recipes.map((recipe) => ({
    recipeId: recipe.id,
    plannedServings: nextExpectedCount,
    remark: ''
  }))

  manualServingKeys.value = manualServingKeys.value.filter((key) => !key.startsWith(`${schedule.mealKey || schedule.sortOrder || 0}-`))
  collapsedRecipeKeys.value = collapsedRecipeKeys.value.filter((key) => !key.startsWith(`${schedule.mealKey || schedule.sortOrder || 0}-`))

  await ensureRecipeDetails(recipes.map((recipe) => recipe.id))
}

const handleAiRecommend = async () => {
  if (aiRecommendLoading.value) return
  if (!validateDimensionSelection() || !validateImplementationRange()) return

  const payload = buildAiRecommendPayload()
  if (!payload.expectedCount) {
    ElMessage.warning('请先填写有效的就餐人数')
    return
  }

  aiRecommendLoading.value = true
  try {
    const res = await getAiRecommendRecipesEnhanced(payload)
    if (res.code === 'SUCCESS' && res.data?.recipes?.length) {
      await applyRecommendedRecipes(res.data.recipes)
      ElMessage.success(res.data.recommendReason || 'AI已完成菜谱推荐')
      return
    }
    ElMessage.warning(res.message || '暂无可用的AI推荐结果')
  } catch (error: any) {
    ElMessage.error(error?.message || 'AI推荐失败')
  } finally {
    aiRecommendLoading.value = false
  }
}

const formatPercentage = (value: number) => `${Number(value || 0).toFixed(0)}%`

const buildNutritionAnalysisPayload = () => ({
  recipes: formData.value.mealSchedules.flatMap((schedule) =>
    schedule.recipes.map((item) => ({
      recipeId: item.recipeId,
      servings: Number(item.plannedServings || 0)
    }))
  ),
  servingCount: Number(formData.value.expectedCount || 0),
  targetGroup: normalizeTargetGroup(formData.value.targetGroup),
  healthStatus: normalizeHealthStatus(formData.value.healthStatus || []).join(',')
})

const resetNutritionAnalysisState = () => {
  nutritionAnalysisLoading.value = false
  nutritionAnalysisResult.value = null
}

const handleGenerateNutritionAnalysis = async () => {
  if (nutritionAnalysisLoading.value) return

  if (!validateMealSchedules()) return

  const payload = buildNutritionAnalysisPayload()
  if (!payload.recipes.length) {
    ElMessage.warning('请先添加菜谱后再生成分析')
    return
  }
  if (!payload.servingCount) {
    ElMessage.warning('请先填写有效的就餐人数')
    return
  }

  nutritionAnalysisLoading.value = true
  try {
    const res = await analyzePlanNutrition(payload)
    if (res.code === 'SUCCESS' && res.data) {
      nutritionAnalysisResult.value = res.data
      ElMessage.success('营养分析已生成')
      return
    }
    ElMessage.warning(res.message || '生成营养分析失败')
  } catch (error: any) {
    ElMessage.error(error?.message || '生成营养分析失败')
  } finally {
    nutritionAnalysisLoading.value = false
  }
}

/** 顶层餐次变更：同步到第一个餐次卡片，避免基础信息与明细区首个餐次脱节 */
const handleTopMealTypeChange = (value: string | number | boolean) => {
  const nextMealType = String(value || '')
  formData.value.mealType = nextMealType
  const firstSchedule = formData.value.mealSchedules[0]
  if (!firstSchedule) return
  handleMealTypeChange(firstSchedule, nextMealType)
}

/** 顶层就餐人数变更：同步到所有餐次的预计人数及非手动覆盖的菜谱份数 */
const handleTopExpectedCountChange = (value: number | undefined) => {
  const normalized = normalizePositiveInteger(value)
  if (!normalized) return
  formData.value.expectedCount = normalized
  formData.value.mealSchedules.forEach((schedule) => {
    schedule.expectedCount = normalized
    schedule.recipes.forEach((recipe) => {
      if (!isManualServing(schedule, recipe.recipeId)) {
        recipe.plannedServings = normalized
      }
    })
  })
}

const rules: FormRules = {
  planDate: [{ required: true, message: '请选择计划日期', trigger: 'change' }],
  orgId: [{ required: true, message: '请选择实施组织', trigger: 'change' }],
  adjustReason: [{
    validator: (_rule, _value, callback) => {
      if (isAdjustmentMode.value && !adjustReason.value.trim()) {
        callback(new Error('请输入调整原因'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }]
}

watch(visible, async (value) => {
  if (!value) return
  initializeForm()
  await ensureRecipeDetails(
    formData.value.mealSchedules.flatMap(schedule => schedule.recipes.map(item => item.recipeId))
  )
  nextTick(() => formRef.value?.clearValidate())
})

watch(
  () => formData.value.orgId,
  (orgId) => {
    if (!orgId) {
      formData.value.orgName = ''
    }
  }
)

watch(
  () => formData.value.mealSchedules.flatMap(schedule => schedule.recipes.map(item => item.recipeId)),
  (ids) => {
    ensureRecipeDetails(ids)
  }
)

watch(
  () => JSON.stringify({
    recipes: formData.value.mealSchedules.flatMap((schedule) =>
      schedule.recipes.map((item) => ({
        recipeId: item.recipeId,
        servings: Number(item.plannedServings || 0)
      }))
    ),
    servingCount: Number(formData.value.expectedCount || 0),
    targetGroup: formData.value.targetGroup || '',
    healthStatus: formData.value.healthStatus || []
  }),
  () => {
    if (isInitializing.value) return
    nutritionAnalysisResult.value = null
  }
)

const initializeForm = () => {
  isInitializing.value = true
  adjustReason.value = ''
  recipeMetaMap.value = {}
  manualServingKeys.value = []
  aiBudgetLimit.value = 0
  aiConsiderStock.value = true
  aiPrioritizeExpiring.value = false
  resetNutritionAnalysisState()

  const base = createEmptyForm()
  const data = props.editData
  const persistedNutritionAnalysis = normalizeAiNutritionAssessment(data?.aiNutritionAssessment)

  if (!data) {
    formData.value = base
    planDimension.value = 'day'
    weekAnchorDate.value = ''
    monthValue.value = ''
    originalSnapshot.value = ''
    collapsedRecipeKeys.value = []
    isInitializing.value = false
    return
  }

  const mealSchedules = 'mealSchedules' in data && Array.isArray(data.mealSchedules) && data.mealSchedules.length
    ? data.mealSchedules.map((schedule, scheduleIndex) => ({
      mealKey: schedule.mealKey || createMealKey(),
      mealType: schedule.mealType || '',
      mealName: schedule.mealName || '',
      expectedCount: Number(schedule.expectedCount || 1),
      sortOrder: schedule.sortOrder || scheduleIndex + 1,
      recipes: (schedule.recipes || []).map((recipe) => {
        mergeRecipeMeta({
          id: recipe.recipeId,
          menuName: recipe.recipeName,
          categoryName: recipe.categoryName,
          unitCost: recipe.unitCost,
          ingredients: recipe.ingredients?.map((ingredient) => ({
            id: undefined,
            menuId: recipe.recipeId,
            materialId: null,
            materialName: ingredient.materialName,
            materialSpec: '',
            quantity: ingredient.quantity,
            unit: ingredient.unit,
            isMain: ingredient.isMain,
            remark: '',
            sortOrder: undefined
          }))
        })
        return {
          recipeId: recipe.recipeId,
          plannedServings: Number(recipe.plannedServings || schedule.expectedCount || 1),
          remark: recipe.remark || ''
        }
      })
    }))
    : [{
      mealKey: createMealKey(),
      mealType: data.mealType || '',
      mealName: '',
      expectedCount: Number(data.expectedCount || 1),
      sortOrder: 1,
      recipes: ('recipes' in data && Array.isArray(data.recipes) ? data.recipes : []).map((recipe) => {
        mergeRecipeMeta({
          id: recipe.recipeId,
          menuName: recipe.recipeName,
          categoryName: recipe.categoryName,
          unitCost: recipe.unitCost,
          ingredients: recipe.ingredients?.map((ingredient) => ({
            id: undefined,
            menuId: recipe.recipeId,
            materialId: null,
            materialName: ingredient.materialName,
            materialSpec: '',
            quantity: ingredient.quantity,
            unit: ingredient.unit,
            isMain: ingredient.isMain,
            remark: '',
            sortOrder: undefined
          }))
        })
        return {
          recipeId: recipe.recipeId,
          plannedServings: Number(recipe.plannedServings || data.expectedCount || 1),
          remark: recipe.remark || ''
        }
      })
    }]

  formData.value = {
    planDate: props.formMode === 'copy' ? '' : (data.planDate || ''),
    startDate: props.formMode === 'copy' ? '' : (data.startDate || ''),
    endDate: props.formMode === 'copy' ? '' : (data.endDate || ''),
    orgId: 'orgId' in data ? data.orgId : undefined,
    orgName: 'orgName' in data ? data.orgName : '',
    mealType: mealSchedules[0]?.mealType || '',
    expectedCount: mealSchedules[0]?.expectedCount || 1,
    targetGroup: data.targetGroup || '',
    healthStatus: normalizeHealthStatus(('healthStatus' in data ? data.healthStatus : '') || ''),
    dietRestrictions: ('dietRestrictions' in data ? data.dietRestrictions : '') || '',
    aiNutritionAssessment: props.formMode === 'copy' ? '' : (data.aiNutritionAssessment || ''),
    remark: ('remark' in data ? data.remark : '') || '',
    recipes: mealSchedules[0]?.recipes || [],
    mealSchedules,
    flavorPreferences: normalizeMultiValue(('flavorPreferences' in data ? data.flavorPreferences : '') || '').join(','),
    spicyLevel: Number(('spicyLevel' in data ? data.spicyLevel : 0) || 0),
    avoidIngredientIds: ('avoidIngredientIds' in data ? data.avoidIngredientIds : '') || '',
    dietTags: normalizeMultiValue(('dietTags' in data ? data.dietTags : '') || '').join(',')
  }

  nutritionAnalysisResult.value = props.formMode === 'copy' ? null : persistedNutritionAnalysis

  manualServingKeys.value = mealSchedules.flatMap(schedule =>
    schedule.recipes
      .filter(recipe => Number(recipe.plannedServings || 0) !== Number(schedule.expectedCount || 0))
      .map(recipe => getManualServingKey(schedule, recipe.recipeId))
  )

  planDimension.value = props.formMode === 'copy'
    ? 'day'
    : inferPlanDimension(formData.value.startDate || '', formData.value.endDate || '', formData.value.planDate || '')
  syncDimensionAuxValues()

  if (props.rejectionMode) {
    originalSnapshot.value = JSON.stringify(buildBasePayload())
  } else {
    originalSnapshot.value = ''
  }

  isInitializing.value = false
}

const normalizeAiNutritionAssessment = (value?: string) => {
  if (!value) return null

  try {
    return JSON.parse(value) as AINutritionAssessment
  } catch {
    return null
  }
}

const serializeAiNutritionAssessment = () =>
  nutritionAnalysisResult.value ? JSON.stringify(nutritionAnalysisResult.value) : ''

const normalizeHealthStatus = (value: string | string[]) => {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (!value) return []
  return value.split(/[,，]/).map(item => item.trim()).filter(Boolean)
}

const normalizeMultiValue = (value: string | string[] | undefined) => {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (!value) return []
  return value.split(/[,，]/).map(item => item.trim()).filter(Boolean)
}

const normalizePositiveInteger = (value: unknown) => {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return undefined
  return Math.floor(num)
}

const todayString = () => formatDate(new Date())

const parseDate = (value: string) => new Date(`${value}T00:00:00`)

const formatDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const formatIngredientQuantity = (value: number) => {
  if (!Number.isFinite(value)) return '0'
  const rounded = Number(value.toFixed(2))
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(2)
}

const diffDays = (start: string, end: string) => {
  const startTime = parseDate(start).getTime()
  const endTime = parseDate(end).getTime()
  return Math.round((endTime - startTime) / 86400000)
}

const getWeekRange = (anchorDate: string): [string, string] => {
  const date = parseDate(anchorDate)
  const day = date.getDay() === 0 ? 7 : date.getDay()
  const monday = new Date(date)
  monday.setDate(date.getDate() - day + 1)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  return [formatDate(monday), formatDate(sunday)]
}

const getMonthRange = (value: string): [string, string] => {
  const [yearText, monthText] = value.split('-')
  const year = Number(yearText)
  const month = Number(monthText)
  const firstDay = new Date(year, month - 1, 1)
  const lastDay = new Date(year, month, 0)
  return [formatDate(firstDay), formatDate(lastDay)]
}

const isWholeMonthRange = (start: string, end: string) => {
  if (!start || !end) return false
  const startDate = parseDate(start)
  const endDate = parseDate(end)
  if (startDate.getFullYear() !== endDate.getFullYear() || startDate.getMonth() !== endDate.getMonth()) {
    return false
  }
  const [monthStart, monthEnd] = getMonthRange(start.slice(0, 7))
  return start === monthStart && end === monthEnd
}

const inferPlanDimension = (startDate: string, endDate: string, planDate: string): PlanDimension => {
  if (!startDate || !endDate) return 'day'
  if (startDate === endDate || (planDate === startDate && startDate === endDate)) return 'day'
  if (isWholeMonthRange(startDate, endDate)) return 'month'
  if (diffDays(startDate, endDate) === 6) return 'week'
  return 'day'
}

const syncDimensionAuxValues = () => {
  if (planDimension.value === 'week') {
    weekAnchorDate.value = formData.value.startDate || formData.value.planDate || todayString()
    monthValue.value = ''
    return
  }
  if (planDimension.value === 'month') {
    monthValue.value = (formData.value.startDate || formData.value.planDate || todayString()).slice(0, 7)
    weekAnchorDate.value = ''
    return
  }
  weekAnchorDate.value = ''
  monthValue.value = ''
}

const applyDayRange = (value: string) => {
  formData.value.planDate = value
  formData.value.startDate = value
  formData.value.endDate = value
}

const applyWeekRange = (anchorDate: string) => {
  const [startDate, endDate] = getWeekRange(anchorDate)
  weekAnchorDate.value = anchorDate
  formData.value.planDate = startDate
  formData.value.startDate = startDate
  formData.value.endDate = endDate
}

const applyMonthRange = (value: string) => {
  const [startDate, endDate] = getMonthRange(value)
  monthValue.value = value
  formData.value.planDate = startDate
  formData.value.startDate = startDate
  formData.value.endDate = endDate
}

const handlePlanDimensionChange = (value: string | number | boolean) => {
  const nextDimension = String(value || 'day') as PlanDimension
  planDimension.value = nextDimension

  if (nextDimension === 'week') {
    applyWeekRange(weekAnchorDate.value || formData.value.startDate || formData.value.planDate || todayString())
    return
  }
  if (nextDimension === 'month') {
    applyMonthRange(monthValue.value || (formData.value.startDate || formData.value.planDate || todayString()).slice(0, 7))
    return
  }
  applyDayRange(formData.value.planDate || formData.value.startDate || todayString())
}

const handlePlanDateChange = (value?: string) => {
  if (!value) return
  applyDayRange(value)
}

const handleWeekAnchorChange = (value?: string) => {
  if (!value) return
  applyWeekRange(value)
}

const handleMonthChange = (value?: string) => {
  if (!value) return
  applyMonthRange(value)
}

const mergeRecipeMeta = (meta: Partial<RecipeMeta> & { id: number }) => {
  const current = recipeMetaMap.value[meta.id]
  recipeMetaMap.value[meta.id] = {
    id: meta.id,
    menuName: meta.menuName || current?.menuName || `菜谱${meta.id}`,
    categoryName: meta.categoryName ?? current?.categoryName,
    unitCost: meta.unitCost ?? current?.unitCost,
    ingredients: meta.ingredients ?? current?.ingredients,
    cookingSteps: meta.cookingSteps ?? current?.cookingSteps,
    cookingTime: meta.cookingTime ?? current?.cookingTime,
    status: meta.status ?? current?.status,
    loading: meta.loading ?? current?.loading,
    loadFailed: meta.loadFailed ?? current?.loadFailed
  }
}

const ensureRecipeDetails = async (ids: number[]) => {
  await Promise.all(ids.map(id => loadRecipeDetail(id)))
}

const loadRecipeDetail = async (recipeId: number) => {
  const existing = recipeMetaMap.value[recipeId]
  const hasLoadedDetail =
    existing &&
    existing.ingredients !== undefined &&
    existing.cookingSteps !== undefined &&
    existing.nutritionInfo !== undefined

  if (existing?.loading || existing?.loadFailed || hasLoadedDetail) return

  mergeRecipeMeta({ id: recipeId, loading: true, loadFailed: false })

  try {
    const res = await getRecipeDetail(recipeId)
    if (res.code === 'SUCCESS' && res.data) {
      const recipe = res.data
      mergeRecipeMeta({
        id: recipe.id,
        menuName: recipe.menuName,
        categoryName: recipe.categoryName,
        unitCost: recipe.unitCost,
        nutritionInfo: recipe.nutritionInfo,
        ingredients: recipe.ingredients || [],
        cookingSteps: recipe.cookingSteps || '',
        cookingTime: recipe.cookingTime,
        status: recipe.status,
        loading: false,
        loadFailed: false
      })
      return
    }
    mergeRecipeMeta({ id: recipeId, loading: false, loadFailed: true })
  } catch (error) {
    console.error('加载菜谱详情失败:', error)
    mergeRecipeMeta({ id: recipeId, loading: false, loadFailed: true })
  }
}

const getManualServingKey = (schedule: RecipePlanMealFormItem, recipeId: number) =>
  `${schedule.mealKey || schedule.sortOrder || 0}-${recipeId}`

const toggleRecipeCollapsed = (schedule: RecipePlanMealFormItem, recipeId: number) => {
  const key = getManualServingKey(schedule, recipeId)
  if (collapsedRecipeKeys.value.includes(key)) {
    collapsedRecipeKeys.value = collapsedRecipeKeys.value.filter(item => item !== key)
    return
  }
  collapsedRecipeKeys.value = [...collapsedRecipeKeys.value, key]
}

const isRecipeCollapsed = (schedule: RecipePlanMealFormItem, recipeId: number) =>
  collapsedRecipeKeys.value.includes(getManualServingKey(schedule, recipeId))

const addManualServingKey = (schedule: RecipePlanMealFormItem, recipeId: number) => {
  const key = getManualServingKey(schedule, recipeId)
  if (!manualServingKeys.value.includes(key)) {
    manualServingKeys.value = [...manualServingKeys.value, key]
  }
}

const removeManualServingKey = (schedule: RecipePlanMealFormItem, recipeId: number) => {
  const key = getManualServingKey(schedule, recipeId)
  manualServingKeys.value = manualServingKeys.value.filter(item => item !== key)
}

const isManualServing = (schedule: RecipePlanMealFormItem, recipeId: number) =>
  manualServingKeys.value.includes(getManualServingKey(schedule, recipeId))

const addMealSchedule = () => {
  formData.value.mealSchedules.push(createEmptyMealSchedule(formData.value.mealSchedules.length + 1))
}

const ensureDefaultMealSchedule = () => {
  if (!formData.value.mealSchedules.length) {
    formData.value.mealSchedules = [createEmptyMealSchedule(1)]
  }
  return formData.value.mealSchedules[0]
}

const createRecipeRow = (schedule: RecipePlanMealFormItem, recipeId: number): RecipePlanFormItem => ({
  recipeId,
  plannedServings: Number(schedule.expectedCount || formData.value.expectedCount || 1),
  remark: ''
})

const handleAddRecipeRow = () => {
  const schedule = ensureDefaultMealSchedule()
  currentRecipeExcludeIds.value = schedule.recipes.map(item => item.recipeId)
  recipePickerVisible.value = true
}

const handleRecipePickerConfirm = async (recipes: Recipe[]) => {
  if (!recipes.length) {
    return
  }

  const schedule = ensureDefaultMealSchedule()
  const existingIds = new Set(schedule.recipes.map(item => item.recipeId))
  const nextRecipes = recipes.filter(recipe => !existingIds.has(recipe.id))

  if (!nextRecipes.length) {
    ElMessage.warning('所选菜谱已全部添加')
    return
  }

  nextRecipes.forEach((recipe) => {
    mergeRecipeMeta({
      id: recipe.id,
      menuName: recipe.menuName,
      categoryName: recipe.categoryName,
      unitCost: recipe.unitCost,
      nutritionInfo: recipe.nutritionInfo,
      status: recipe.status,
      loadFailed: false
    })
    schedule.recipes.push(createRecipeRow(schedule, recipe.id))
  })

  currentRecipeExcludeIds.value = schedule.recipes.map(item => item.recipeId)
  await ensureRecipeDetails(nextRecipes.map(recipe => recipe.id))
}

const handleOrgPickerConfirm = async ({ orgId, orgName }: { orgId: number; orgName: string }) => {
  syncSelectedOrg(orgId, orgName)
  await nextTick()
  await formRef.value?.validateField(['orgId'])
}

const removeMealSchedule = (index: number) => {
  const schedule = formData.value.mealSchedules[index]
  if (schedule) {
    const scheduleKeyPrefix = `${schedule.mealKey || schedule.sortOrder || 0}-`
    manualServingKeys.value = manualServingKeys.value.filter(key => !key.startsWith(scheduleKeyPrefix))
    collapsedRecipeKeys.value = collapsedRecipeKeys.value.filter(key => !key.startsWith(scheduleKeyPrefix))
  }
  formData.value.mealSchedules.splice(index, 1)
  formData.value.mealSchedules.forEach((item, idx) => {
    item.sortOrder = idx + 1
  })
  formData.value.mealType = formData.value.mealSchedules[0]?.mealType || ''
}

const handleMealTypeChange = (schedule: RecipePlanMealFormItem, value: string) => {
  schedule.mealType = value
  if (value !== 'custom') {
    schedule.mealName = ''
  }

  if (formData.value.mealSchedules[0] === schedule) {
    formData.value.mealType = value
  }
}

const handleScheduleExpectedCountChange = (schedule: RecipePlanMealFormItem) => {
  const normalized = normalizePositiveInteger(schedule.expectedCount)
  if (!normalized) return
  schedule.expectedCount = normalized
  schedule.recipes.forEach((recipe) => {
    if (!isManualServing(schedule, recipe.recipeId)) {
      recipe.plannedServings = normalized
    }
  })
}

const removeScheduleRecipe = (schedule: RecipePlanMealFormItem, index: number) => {
  const removed = schedule.recipes[index]
  schedule.recipes.splice(index, 1)
  if (removed) {
    removeManualServingKey(schedule, removed.recipeId)
  }
}

const getRecipeMeta = (recipeId: number) => recipeMetaMap.value[recipeId]

const getRecipeName = (recipeId: number) => getRecipeMeta(recipeId)?.menuName || `菜谱${recipeId}`

const getRecipeCategory = (recipeId: number) => getRecipeMeta(recipeId)?.categoryName || ''

const getRecipeUnitCost = (recipeId: number) => Number(getRecipeMeta(recipeId)?.unitCost || 0)

const getRecipeCookingSteps = (recipeId: number) => getRecipeMeta(recipeId)?.cookingSteps || ''

const getRecipeCookingTime = (recipeId: number) => {
  const cookingTime = getRecipeMeta(recipeId)?.cookingTime
  return cookingTime ? `${cookingTime} 分钟` : '-'
}

const isRecipeDetailLoading = (recipeId: number) => Boolean(getRecipeMeta(recipeId)?.loading)

const isRecipeDetailUnavailable = (recipeId: number) => Boolean(getRecipeMeta(recipeId)?.loadFailed)

const getRecipeIngredientRows = (recipeId: number, plannedServings: number): IngredientRow[] => {
  const recipeMeta = getRecipeMeta(recipeId)
  const servings = Number(plannedServings || 0)
  return (recipeMeta?.ingredients || []).map((ingredient) => {
    const perServingQuantity = Number(ingredient.quantity || 0)
    return {
      materialName: ingredient.materialName,
      unit: ingredient.unit,
      perServingQuantity,
      totalQuantity: perServingQuantity * servings,
      isMain: Boolean(ingredient.isMain)
    }
  })
}

const getRecipeIngredientSummary = (recipeId: number, plannedServings: number) => {
  if (isRecipeDetailLoading(recipeId)) return '食材信息加载中'
  if (isRecipeDetailUnavailable(recipeId)) return '食材详情获取失败'

  const rows = getRecipeIngredientRows(recipeId, plannedServings)
  if (!rows.length) return '暂无食材信息'

  return rows
    .map((item) => `${item.materialName}${formatIngredientQuantity(item.totalQuantity)}${item.unit || ''}`)
    .join('、')
}

const getRecipeIngredientTags = (recipeId: number, plannedServings: number): IngredientTagItem[] => {
  if (isRecipeDetailLoading(recipeId)) {
    return [{ key: `loading-${recipeId}`, text: '食材信息加载中' }]
  }
  if (isRecipeDetailUnavailable(recipeId)) {
    return [{ key: `error-${recipeId}`, text: '食材详情获取失败' }]
  }

  const rows = getRecipeIngredientRows(recipeId, plannedServings)
  if (!rows.length) {
    return [{ key: `empty-${recipeId}`, text: '暂无食材信息' }]
  }

  return rows.map((item, index) => ({
    key: `${recipeId}-${item.materialName}-${index}`,
    text: `${item.materialName}${formatIngredientQuantity(item.totalQuantity)}${item.unit || ''}`
  }))
}

const getScheduleTotalServings = (schedule: RecipePlanMealFormItem) =>
  schedule.recipes.reduce((sum, item) => sum + Number(item.plannedServings || 0), 0)

const getScheduleTotalEstimatedCost = (schedule: RecipePlanMealFormItem) =>
  schedule.recipes.reduce((sum, item) => sum + (getRecipeUnitCost(item.recipeId) || 0) * Number(item.plannedServings || 0), 0)

const formatMoney = (value: number) => Number(value || 0).toFixed(2)

const handleRecipeServingsChange = (schedule: RecipePlanMealFormItem, row: RecipePlanFormItem) => {
  const normalized = normalizePositiveInteger(row.plannedServings)
  row.plannedServings = normalized || 1
  if (row.plannedServings === Number(schedule.expectedCount || 0)) {
    removeManualServingKey(schedule, row.recipeId)
    return
  }
  addManualServingKey(schedule, row.recipeId)
}

const syncAllScheduleRecipeServings = (schedule: RecipePlanMealFormItem) => {
  const nextServings = normalizePositiveInteger(schedule.expectedCount)
  if (!nextServings) {
    ElMessage.warning('请先填写有效的就餐人数')
    return
  }
  schedule.recipes.forEach((recipe) => {
    removeManualServingKey(schedule, recipe.recipeId)
    recipe.plannedServings = nextServings
  })
}

const buildMealSchedulesPayload = () =>
  formData.value.mealSchedules.map((schedule, scheduleIndex) => ({
    mealKey: schedule.mealKey || createMealKey(),
    mealType: schedule.mealType,
    mealName: schedule.mealType === 'custom' ? (schedule.mealName?.trim() || '') : '',
    expectedCount: Number(schedule.expectedCount || 0),
    sortOrder: scheduleIndex + 1,
    recipes: schedule.recipes.map((item, itemIndex) => ({
      recipeId: item.recipeId,
      plannedServings: Number(item.plannedServings || 0),
      sortOrder: itemIndex + 1,
      remark: item.remark?.trim() || ''
    }))
  }))

const buildBasePayload = () => {
  const mealSchedules = buildMealSchedulesPayload()
  const firstSchedule = mealSchedules[0]
  return {
    planDate: formData.value.planDate,
    startDate: formData.value.startDate || '',
    endDate: formData.value.endDate || '',
    orgId: formData.value.orgId,
    mealType: firstSchedule?.mealType || '',
    expectedCount: firstSchedule?.expectedCount || 0,
    targetGroup: formData.value.targetGroup || '',
    healthStatus: normalizeHealthStatus(formData.value.healthStatus || []).join(','),
    dietRestrictions: formData.value.dietRestrictions?.trim() || '',
    aiNutritionAssessment: serializeAiNutritionAssessment(),
    remark: formData.value.remark?.trim() || '',
    recipes: firstSchedule?.recipes || [],
    mealSchedules,
    flavorPreferences: normalizeMultiValue(formData.value.flavorPreferences).join(','),
    spicyLevel: Number(formData.value.spicyLevel || 0),
    avoidIngredientIds: formData.value.avoidIngredientIds?.trim() || '',
    dietTags: normalizeMultiValue(formData.value.dietTags).join(',')
  }
}

const buildAdjustmentSnapshot = () => ({
  ...buildBasePayload(),
  mealSchedules: formData.value.mealSchedules.map((schedule, scheduleIndex) => ({
    mealKey: schedule.mealKey || createMealKey(),
    mealType: schedule.mealType,
    mealName: schedule.mealType === 'custom' ? (schedule.mealName?.trim() || '') : '',
    expectedCount: Number(schedule.expectedCount || 0),
    sortOrder: scheduleIndex + 1,
    recipes: schedule.recipes.map((item, itemIndex) => ({
      recipeId: item.recipeId,
      recipeName: getRecipeName(item.recipeId),
      categoryName: getRecipeCategory(item.recipeId),
      plannedServings: Number(item.plannedServings || 0),
      remark: item.remark?.trim() || '',
      unitCost: getRecipeUnitCost(item.recipeId),
      cookingSteps: getRecipeCookingSteps(item.recipeId),
      ingredients: getRecipeIngredientRows(item.recipeId, item.plannedServings),
      sortOrder: itemIndex + 1
    }))
  }))
})

const validateImplementationRange = () => {
  if (!formData.value.startDate || !formData.value.endDate) {
    ElMessage.warning('请先选择有效的计划时间')
    return false
  }
  if (formData.value.endDate < formData.value.startDate) {
    ElMessage.warning('实施结束日期不能早于开始日期')
    return false
  }
  return true
}

const validateDimensionSelection = () => {
  if (planDimension.value === 'week' && !weekAnchorDate.value) {
    ElMessage.warning('请选择计划周')
    return false
  }
  if (planDimension.value === 'month' && !monthValue.value) {
    ElMessage.warning('请选择计划月份')
    return false
  }
  return true
}

const validateMealSchedules = () => {
  if (!formData.value.mealSchedules.length) {
    ElMessage.warning('请至少配置一个餐次')
    return false
  }

  const builtinMealTypes = new Set<string>()
  const customMealNames = new Set<string>()

  for (const schedule of formData.value.mealSchedules) {
    if (!schedule.mealType) {
      ElMessage.warning('请选择餐次')
      return false
    }
    if (schedule.mealType === 'custom') {
      const mealName = schedule.mealName?.trim() || ''
      if (!mealName) {
        ElMessage.warning('请输入自定义餐次名称')
        return false
      }
      if (customMealNames.has(mealName)) {
        ElMessage.warning('自定义餐次名称不能重复')
        return false
      }
      customMealNames.add(mealName)
    } else if (builtinMealTypes.has(schedule.mealType)) {
      ElMessage.warning('同一计划内内置餐次不能重复')
      return false
    } else {
      builtinMealTypes.add(schedule.mealType)
    }

    if (!schedule.expectedCount || schedule.expectedCount < 1) {
      ElMessage.warning('请完善餐次的就餐人数')
      return false
    }

    if (!schedule.recipes.length) {
      ElMessage.warning('每个餐次至少需要选择一道菜谱')
      return false
    }

    if (schedule.recipes.some(item => !item.recipeId || !item.plannedServings || item.plannedServings < 1)) {
      ElMessage.warning('请完善餐次内菜谱的计划份数')
      return false
    }
  }

  return true
}

const validateOrgSelection = (required: boolean) => {
  if (!required && !formData.value.orgId) {
    return true
  }
  if (!formData.value.orgId) {
    ElMessage.warning('请选择实施组织')
    return false
  }
  return true
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  if (!validateOrgSelection(true) || !validateDimensionSelection() || !validateImplementationRange() || !validateMealSchedules()) return

  submitting.value = true
  const payload: RecipePlanFormSubmitPayload = buildBasePayload() as RecipePlanFormSubmitPayload

  if (isAdjustmentMode.value) {
    payload.adjustReason = adjustReason.value.trim()
    payload.adjustType = 'modify'
    payload.afterData = JSON.stringify(buildAdjustmentSnapshot())
  }

  emit('submit', payload, () => {
    submitting.value = false
  })
}

const handleSaveDraft = async () => {
  try {
    await formRef.value?.validateField(['planDate'])
  } catch {
    return
  }

  if (!validateOrgSelection(false) || !validateDimensionSelection() || !validateImplementationRange() || !validateMealSchedules()) return

  submitting.value = true
  const payload: RecipePlanFormSubmitPayload = buildBasePayload() as RecipePlanFormSubmitPayload
  emit('save-draft', payload, () => {
    submitting.value = false
  })
}

const handleResubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  if (!validateOrgSelection(true) || !validateDimensionSelection() || !validateImplementationRange() || !validateMealSchedules()) return

  const currentSnapshot = JSON.stringify(buildBasePayload())
  if (currentSnapshot === originalSnapshot.value) {
    ElMessage.warning('未修改任何内容，请修改后再重新提交')
    return
  }

  submitting.value = true
  const payload: RecipePlanFormSubmitPayload = buildBasePayload() as RecipePlanFormSubmitPayload
  emit('resubmit', payload, () => {
    submitting.value = false
  })
}

const handleClosed = () => {
  submitting.value = false
  resetNutritionAnalysisState()
  formRef.value?.clearValidate()
}
</script>

<!-- 全局样式：el-dialog teleport 到 body，scoped 无法穿透 -->
<style lang="scss">
.el-dialog.plan-form-dialog {
  border-radius: 12px;
  overflow: hidden;
  margin-top: 8vh;

  .el-dialog__header {
    padding: 0 0 16px;
    margin-right: 0;
    border-bottom: 1px solid #E1E2E9;
  }

  .el-dialog__body {
    padding: 8px;
    max-height: 68vh;
    overflow-y: auto;
  }

  .el-dialog__footer {
    padding: 0;
    border-top: 1px solid #E1E2E9;
  }
}

.el-dialog.recipe-picker-dialog {
  width: 758px;
  max-width: calc(100vw - 32px);
  min-height: 780px;
  background: #FFFFFF;
  border-radius: 12px;
  overflow: hidden;

  .el-dialog__header {
    padding: 24px 24px 16px;
    margin-right: 0;
    border-bottom: 1px solid #E1E2E9;
  }

  .el-dialog__body {
    padding: 16px 24px 24px;
    min-height: 648px;
  }

  .el-dialog__footer {
    padding: 12px 24px 16px;
    border-top: 1px solid #E1E2E9;
  }
}
</style>

<!-- 组件内 slot 内容样式 -->
<style scoped lang="scss">
/* ===== 标题栏 ===== */
.dialog-header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 12px 8px 0;
  gap: 13px;
}

.dialog-header__content {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  padding: 0;
  width: 100%;
  min-height: 32px;
}

.dialog-title {
  width: 120px;
  min-height: 30px;
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-size: 20px;
  font-weight: 500;
  line-height: 30px;
  text-align: center;
  color: #000000;
  flex: none;
}

.dialog-close {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  background: #FFF2E2;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex: none;
}

.dialog-close:hover {
  background: #FFE5C2;
}

.dialog-close__icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #1C1D22;
  font-size: 24px;
}

/* ===== 表单容器 ===== */
.plan-form {
  .mb-16 {
    margin-bottom: 16px;
  }

  .alert-title {
    font-weight: 600;
  }

  .adjustment-hint-text {
    margin-top: 4px;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .day-range-fields {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }

  .day-range-fields--range-look {
    box-sizing: border-box;
    display: flex;
    align-items: center;
    width: 220px;
    height: 32px;
    max-width: 220px;
    padding: 4px 8px 4px 4px;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    background: #FFFFFF;
    gap: 8px;
    transition: border-color 0.2s ease, box-shadow 0.2s ease;
  }

  .day-range-fields__content {
    display: flex;
    align-items: center;
    gap: 2px;
    flex: 1;
    min-width: 0;
    height: 24px;
  }

  .basic-item--range:focus-within .day-range-fields--range-look {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
  }

  .basic-item--range.is-error .day-range-fields--range-look {
    border-color: var(--el-color-danger);
    box-shadow: 0 0 0 1px rgba(245, 108, 108, 0.15);
  }

  .day-range-fields__picker {
    flex: 1;
    min-width: 0;
    height: 24px;
  }

  .day-range-fields__picker :deep(.el-date-editor) {
    width: 100%;
    max-width: none;
  }

  .day-range-fields__picker :deep(.el-input__wrapper) {
    min-height: 24px;
    height: 24px;
    padding: 1px 4px;
    border-radius: 3px;
    background: transparent;
    box-shadow: none;
  }

  .day-range-fields__picker :deep(.el-input__inner) {
    height: 22px;
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.88);
  }

  .day-range-fields__picker :deep(.el-input__inner::placeholder) {
    color: rgba(0, 0, 0, 0.4);
  }

  .day-range-fields__picker :deep(.el-input__prefix),
  .day-range-fields__picker :deep(.el-input__suffix) {
    display: none;
  }

  .day-range-fields__separator {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: none;
    width: 9px;
    height: 22px;
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.4);
  }

  .day-range-fields__calendar {
    flex: none;
    width: 16px;
    height: 16px;
    color: rgba(0, 0, 0, 0.4);
    font-size: 16px;
  }

  /* ===== 分区 ===== */
  .form-section {
    padding: 0;

    & + .form-section {
      margin-top: 24px;
      padding-top: 24px;
      border-top: 1px solid #E1E2E9;
    }
  }

  .form-section--no-divider {
    margin-top: 24px;
    padding-top: 0;
    border-top: none !important;
  }

  .form-section--recipe-detail {
    margin-top: 16px;
  }

  .section-header {
    display: flex;
    align-items: center;
    gap: 0;
    margin-bottom: 12px;
  }

  .section-header--with-hint {
    justify-content: flex-start;
  }

  .section-header--detail-actions {
    justify-content: space-between;
    gap: 12px;
  }

  .section-header__title-wrap {
    display: inline-flex;
    align-items: center;
    min-width: 0;
  }

  .field-hint--inline {
    display: inline-flex;
    align-items: center;
    margin-bottom: 0;
    margin-left: 10px;
    min-height: 16px;
    line-height: 16px;
    white-space: nowrap;
    position: relative;
    top: 7px;
  }

  .section-bar {
    width: 4px;
    height: 16px;
    background: #5570F1;
    border-radius: 2px;
    margin-right: 8px;
    flex-shrink: 0;
  }

  .section-title {
    font-family: 'PingFang SC', 'Inter', sans-serif;
    font-size: 16px;
    font-weight: 500;
    color: #000000;
    line-height: 16px;
  }

  /* ===== 字段提示 ===== */
  .field-hint {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    margin-bottom: 12px;
  }

  .field-tip {
    margin-top: -8px;
    font-size: 12px;
    color: #909399;
  }

  .section-body {
    padding-left: 12px;
  }

  .section-body--basic {
    max-width: none;
  }

  .section-body--profile {
    padding-left: 114px;
  }

  .section-body--preference {
    padding-left: 114px;
  }

  .section-body--remark {
    padding-left: 0;
  }

  .profile-grid {
    display: grid;
    grid-template-columns: 220px 220px;
    justify-content: space-between;
    column-gap: 0;
    row-gap: 0;
    align-items: center;
    margin-bottom: 8px;
  }

  .profile-item,
  .profile-textarea-item {
    margin-bottom: 0;
  }

  .profile-item :deep(.el-form-item) {
    align-items: center;
  }

  .profile-item :deep(.el-form-item__content),
  .profile-textarea-item :deep(.el-form-item__content) {
    min-width: 0;
  }

  .profile-item :deep(.el-form-item__content) {
    display: flex;
    align-items: center;
    min-height: 32px;
  }

  .profile-item--health :deep(.el-form-item__content) {
    justify-content: flex-end;
  }

  .profile-item--target,
  .profile-item--health {
    position: relative;
  }

  .profile-item--target :deep(.el-form-item__label) {
    width: 102px !important;
    min-width: 102px;
    margin-left: -102px;
    padding-right: 8px;
    white-space: nowrap;
    line-height: 22px;
    text-align: right;
    justify-content: flex-end;
  }

  .profile-item--health :deep(.el-form-item__label) {
    width: 102px !important;
    min-width: 102px;
    margin-left: -102px;
    padding-right: 8px;
    white-space: nowrap;
    line-height: 22px;
    text-align: right;
    justify-content: flex-end;
  }

  .profile-textarea-item :deep(.el-form-item__label) {
    width: 102px !important;
    min-width: 102px;
    margin-left: -102px;
    padding-right: 8px;
    white-space: nowrap;
    line-height: 22px;
    text-align: right;
    justify-content: flex-end;
  }

  .section-body--profile :deep(.el-select) {
    width: 220px;
    max-width: 220px;
  }

  .profile-textarea-item {
    margin-bottom: 16px;
  }

  .profile-textarea-item :deep(.el-textarea) {
    width: 100%;
  }

  .profile-textarea-item :deep(.el-textarea__inner) {
    min-height: 54px !important;
    padding: 5px 12px;
    border-radius: 2px;
    box-shadow: 0 0 0 1px #D9D9D9 inset;
    font-size: 14px;
    line-height: 22px;
  }

  .profile-textarea-item--remark {
    margin-bottom: 0;
  }

  .profile-textarea-item--remark :deep(.el-form-item) {
    margin-left: 0;
  }

  .profile-textarea-item--remark :deep(.el-form-item__content) {
    margin-left: 0 !important;
    width: 690px;
    max-width: 100%;
    min-width: 0;
    display: block;
  }

  .profile-textarea-item--remark :deep(.el-form-item__label) {
    display: none;
  }

  .profile-textarea-item--remark :deep(.el-textarea) {
    width: 100%;
    display: block;
  }

  .profile-textarea-item--remark :deep(.el-textarea__inner) {
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    width: 100%;
    min-height: 66px !important;
    padding: 5px 12px;
    background: #FFFFFF;
    border: 1px solid #D9D9D9;
    border-radius: 2px;
    box-shadow: none;
    font-family: 'Roboto', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.88);
    resize: none;
  }

  .profile-textarea-item--remark :deep(.el-textarea__inner:focus) {
    box-shadow: none;
  }

  .profile-textarea-item--remark :deep(.el-textarea__inner::placeholder) {
    font-family: 'Roboto', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.35);
  }

  .preference-grid {
    display: grid;
    grid-template-columns: 220px 220px;
    justify-content: space-between;
    column-gap: 0;
    row-gap: 16px;
    align-items: start;
    margin-bottom: 16px;
  }

  .preference-item {
    margin-bottom: 0;
    position: relative;
  }

  .preference-item :deep(.el-form-item__content) {
    min-width: 0;
    display: flex;
    align-items: center;
  }

  .preference-item--left :deep(.el-form-item__label) {
    width: 102px !important;
    min-width: 102px;
    margin-left: -102px;
    padding-right: 8px;
    white-space: nowrap;
    line-height: 22px;
    text-align: right;
    justify-content: flex-end;
  }

  .preference-item--right :deep(.el-form-item__label) {
    width: 102px !important;
    min-width: 102px;
    margin-left: -102px;
    padding-right: 8px;
    white-space: nowrap;
    line-height: 22px;
    text-align: right;
    justify-content: flex-end;
  }

  .section-body--preference :deep(.el-select) {
    width: 220px;
    max-width: 220px;
  }

  .section-body--preference :deep(.el-input) {
    width: 220px;
    max-width: 220px;
  }

  .section-body--preference :deep(.el-select__wrapper),
  .section-body--preference :deep(.el-input__wrapper) {
    min-height: 32px;
    height: 32px;
    padding: 4px 12px;
    box-shadow: 0 0 0 1px #D9D9D9 inset;
    border-radius: 4px;
  }

  .section-body--preference :deep(.el-select__placeholder),
  .section-body--preference :deep(.el-select__selected-item),
  .section-body--preference :deep(.el-input__inner) {
    height: 22px;
    line-height: 22px;
    font-size: 14px;
  }
  .basic-grid {
    display: grid;
    grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
    column-gap: 28px;
    row-gap: 8px;
    align-items: start;
  }

  .basic-item {
    margin-bottom: 0;
  }

  .basic-item--left :deep(.el-form-item__label) {
    width: 102px !important;
    min-width: 102px;
    padding-right: 8px;
    white-space: nowrap;
    line-height: 32px;
    text-align: right;
    justify-content: flex-end;
  }

  .basic-item--right :deep(.el-form-item__label) {
    width: 132px !important;
    min-width: 132px;
    padding-right: 8px;
    white-space: nowrap;
    line-height: 32px;
    text-align: right;
    justify-content: flex-end;
  }

  .basic-item :deep(.el-form-item__content) {
    min-width: 0;
  }

  .basic-item--right :deep(.el-form-item__content) {
    display: flex;
    justify-content: flex-end;
  }

  .basic-item--left :deep(.el-input),
  .basic-item--left :deep(.el-select),
  .basic-item--right :deep(.el-input-number),
  .basic-item--org .plan-org-trigger {
    width: 220px;
    max-width: 220px;
  }

  .basic-item--date :deep(.el-input),
  .basic-item--meal :deep(.el-select),
  .basic-item--count :deep(.el-input-number),
  .basic-item--date :deep(.el-input__wrapper),
  .basic-item--meal :deep(.el-select__wrapper) {
    width: 220px;
    max-width: 220px;
  }

  .basic-item--count :deep(.el-input-number) {
    width: 220px !important;
    max-width: 220px !important;
    min-width: 220px;
  }

  .basic-item--count :deep(.el-input-number .el-input),
  .basic-item--count :deep(.el-input-number .el-input__wrapper),
  .basic-item--count :deep(.el-input-number .el-input__inner) {
    width: 100%;
  }

  .basic-item--count :deep(.el-input-number__decrease),
  .basic-item--count :deep(.el-input-number__increase) {
    box-sizing: border-box;
  }

  .plan-org-trigger {
    display: inline-flex;
    align-items: center;
    width: 220px;
    min-height: 32px;
    padding: 5px 11px;
    border: 1px solid var(--el-border-color);
    border-radius: 4px;
    background: #fff;
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s;
  }

  .plan-org-trigger:hover {
    border-color: var(--el-color-primary-light-5);
  }

  .plan-org-trigger:focus-visible {
    outline: none;
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
  }

  .plan-org-trigger__text {
    width: 100%;
    overflow: hidden;
    text-align: left;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
    line-height: 22px;
    color: var(--el-text-color-primary);
  }

  .plan-org-trigger__text.is-placeholder {
    color: var(--el-text-color-placeholder);
  }


  .basic-item--range {
    min-width: 0;
  }

  .basic-item--range :deep(.el-form-item__content) {
    flex: 1;
  }

  .basic-item--range :deep(.el-date-editor.el-range-editor),
  .basic-item--range :deep(.el-date-editor.el-input) {
    width: 220px;
    max-width: 220px;
  }

  .day-range-fields__picker :deep(.el-date-editor.el-input) {
    width: 100%;
    max-width: none;
  }

  .basic-item--range :deep(.el-input__prefix),
  .basic-item--range :deep(.el-range__icon) {
    display: none;
  }

  .basic-item--range :deep(.el-input__suffix),
  .basic-item--range :deep(.el-range__close-icon) {
    display: inline-flex;
    align-items: center;
  }

  .basic-item--range :deep(.el-input__suffix-inner) {
    display: inline-flex;
    align-items: center;
  }

  .basic-item--range :deep(.el-input__suffix-inner > :first-child) {
    margin-left: 0;
  }

  .basic-item--range :deep(.el-date-editor .el-input__wrapper),
  .basic-item--range :deep(.el-range-editor.el-input__wrapper) {
    padding-right: 12px;
  }

  .basic-item--range :deep(.el-range-input) {
    min-width: 88px;
  }

  .section-body--basic :deep(.el-input),
  .section-body--basic :deep(.el-select),
  .section-body--basic :deep(.el-date-editor.el-input),
  .section-body--basic :deep(.el-input-number) {
    width: 220px;
    max-width: 220px;
  }

  .section-body--basic :deep(.el-input__wrapper),
  .section-body--basic :deep(.el-select__wrapper),
  .section-body--basic :deep(.el-range-editor.el-input__wrapper),
  .section-body--basic :deep(.el-input-number),
  .section-body--basic :deep(.el-input-number__decrease),
  .section-body--basic :deep(.el-input-number__increase),
  .section-body--profile :deep(.el-input__wrapper),
  .section-body--profile :deep(.el-select__wrapper) {
    min-height: 32px;
    height: 32px;
  }

  .section-body--basic :deep(.el-input__wrapper),
  .section-body--basic :deep(.el-select__wrapper),
  .section-body--profile :deep(.el-input__wrapper),
  .section-body--profile :deep(.el-select__wrapper) {
    padding: 4px 12px;
    box-shadow: 0 0 0 1px #D9D9D9 inset;
    border-radius: 4px;
  }

  .section-body--basic :deep(.el-input__inner),
  .section-body--basic :deep(.el-range-input),
  .section-body--profile :deep(.el-input__inner),
  .section-body--profile :deep(.el-select__placeholder),
  .section-body--profile :deep(.el-select__selected-item) {
    height: 22px;
    line-height: 22px;
    font-size: 14px;
  }

  .field-tip--basic {
    margin-top: 10px;
    padding-left: 100px;
  }

  .readonly-date-input :deep(.el-input__wrapper) {
    background: #F5F7FA;
    box-shadow: 0 0 0 1px #E4E7ED inset;
    cursor: default;
  }

  .readonly-date-input :deep(.el-input__inner) {
    color: #909399;
  }

  /* ===== 营养目标条 ===== */
  .nutrition-target-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 0;
    margin-left: -102px;
    padding: 0;
    min-height: 24px;
    background: transparent;
    border-radius: 0;
  }

  .nutrition-label {
    font-size: 14px;
    line-height: 20px;
    color: rgba(51, 51, 51, 0.5);
    white-space: nowrap;
  }

  .nutrition-tag {
    box-sizing: border-box;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 76px;
    height: 24px;
    padding: 0 10px;
    background: rgba(104, 194, 58, 0.1);
    border: 1px solid rgba(104, 194, 58, 0.3);
    border-radius: 5px;
    color: #68C23A;
    font-size: 14px;
    line-height: 20px;
    white-space: nowrap;
  }

  .nutrition-values {
    width: 443px;
    height: 17px;
    flex: none;
    order: 2;
    flex-grow: 0;
    color: #FF4D4F;
    font-size: 12px;
    line-height: 17px;
    white-space: nowrap;
  }

  .detail-header-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
  }

  .detail-action-btn {
    box-sizing: border-box;
    display: inline-flex;
    flex-direction: row;
    justify-content: center;
    align-items: center;
    gap: 8px;
    height: 32px;
    padding: 5px 16px;
    border-radius: 6px;
    font-family: 'Roboto', 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    box-shadow: none;
    margin: 0;
  }

  .detail-action-btn--add {
    width: 110px;
    border: 1px solid #5570F1;
    color: #7288FA;
    background: #FFFFFF;
  }

  .detail-action-btn--add:hover,
  .detail-action-btn--add:focus {
    border-color: #5570F1;
    color: #7288FA;
    background: #FFFFFF;
  }

  .detail-action-btn--ai {
    width: 73px;
    border: 1px solid #FDAD00;
    color: #FDAD00;
    background: #FFFFFF;
  }

  .detail-action-btn--ai:hover,
  .detail-action-btn--ai:focus {
    border-color: #FDAD00;
    color: #FDAD00;
    background: #FFFFFF;
  }

  .detail-action-btn__icon {
    width: 14px;
    height: 14px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #7288FA;
    font-size: 14px;
    line-height: 14px;
    font-weight: 400;
    flex: none;
  }

  /* ===== 计划维度条 ===== */
  .dimension-bar {
    position: relative;
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 11px 16px;
    min-height: 50px;
    background: #F7F9FC;
    border: 1px solid #EBEEF5;
    border-radius: 8px;
    margin-bottom: 16px;
    flex-wrap: nowrap;
  }

  .dimension-label {
    width: 60px;
    font-size: 12px;
    font-weight: 400;
    line-height: 17px;
    color: rgba(0, 0, 0, 0.5);
    white-space: nowrap;
    flex: none;
  }

  .dimension-segmented {
    display: inline-flex;
    align-items: center;
    height: 28px;
    flex: none;
  }

  .dimension-segmented__item {
    width: 50px;
    height: 28px;
    border: 1px solid #E1E2E9;
    background: #FFFFFF;
    color: rgba(0, 0, 0, 0.5);
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 17px;
    padding: 0;
    cursor: pointer;
  }

  .dimension-segmented__item:first-child {
    border-radius: 4px 0 0 4px;
  }

  .dimension-segmented__item + .dimension-segmented__item {
    margin-left: -1px;
  }

  .dimension-segmented__item:last-child {
    border-radius: 0 4px 4px 0;
  }

  .dimension-segmented__item.is-active {
    background: #7288FA;
    border-color: #7288FA;
    color: #FFFFFF;
    position: relative;
    z-index: 1;
  }

  .dimension-range-box {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
    flex: none;
  }

  .dimension-range-box__label,
  .dimension-range-box__unit {
    font-family: 'Roboto', 'PingFang SC', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.85);
    white-space: nowrap;
  }

  .dimension-range-stepper {
    display: inline-flex;
    align-items: center;
    width: 140px;
    height: 28px;
    flex: none;
  }

  .dimension-range-stepper__btn {
    width: 31px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 4px 10px;
    background: #F5F7FA;
    border: 1px solid #D9D9D9;
    color: rgba(124, 126, 129, 0.85);
    font-size: 14px;
    line-height: 14px;
    cursor: pointer;
  }

  .dimension-range-stepper__btn:first-child {
    border-radius: 4px 0 0 4px;
  }

  .dimension-range-stepper__btn:last-child {
    border-radius: 0 4px 4px 0;
  }

  .dimension-range-stepper__value {
    width: 78px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 4px 12px;
    background: #FFFFFF;
    border-top: 1px solid #D9D9D9;
    border-bottom: 1px solid #D9D9D9;
    font-family: 'Roboto', 'PingFang SC', sans-serif;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.35);
    box-sizing: border-box;
  }

  .dimension-check {
    display: inline-flex;
    align-items: flex-start;
    gap: 8px;
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);
    white-space: nowrap;
    flex: none;
  }

  .dimension-check input {
    width: 16px;
    height: 16px;
    margin: 3px 0 0;
    accent-color: #7288FA;
    flex: none;
  }

  .dimension-impl {
    font-size: 13px;
    color: #909399;
    flex: 1;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    min-width: 0;
    display: none;
  }

  .dimension-actions {
    display: flex;
    gap: 8px;
    margin-left: 0;
  }

  /* ===== 表单项 ===== */
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    font-family: 'Roboto', 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 400;
    color: rgba(0, 0, 0, 0.85);
  }

  :deep(.el-form-item.is-required .el-form-item__label::before) {
    color: #FF4D4F !important;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    height: 32px;
    border-radius: 4px;
    box-shadow: 0 0 0 1px #D9D9D9 inset !important;
    padding: 4px 12px;

    &:hover {
      box-shadow: 0 0 0 1px #7288FA inset !important;
    }

    &.is-focus {
      box-shadow: 0 0 0 1px #7288FA inset !important;
    }
  }

  :deep(.el-input__inner),
  :deep(.el-select__placeholder),
  :deep(.el-select__selected-item) {
    font-size: 14px;
    height: 24px;
    line-height: 24px;
  }

  :deep(.el-input__inner::placeholder),
  :deep(.el-textarea__inner::placeholder) {
    color: rgba(0, 0, 0, 0.35);
  }

  :deep(.el-textarea__inner) {
    border: 1px solid #D9D9D9;
    border-radius: 2px;
    font-size: 14px;
    padding: 5px 12px;

    &:hover {
      border-color: #7288FA;
    }

    &:focus {
      border-color: #7288FA;
      box-shadow: none;
    }
  }

  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner) {
    border-radius: 4px;
  }

  /* ===== 餐次卡片 ===== */
  .meal-schedule-card {
    padding: 0;
    background: transparent;
    border: none;
    border-radius: 0;
  }

  .meal-schedule-card + .meal-schedule-card {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #E9EDF5;
  }

  .meal-schedule-card__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .meal-schedule-card__title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .meal-tag {
    box-sizing: border-box;
    display: inline-flex;
    align-items: center;
    height: 24px;
    padding: 2px 8px;
    border: 1px solid #c0c5ca;
    border-radius: 3px;
    background: #efefef;
    color: #666;
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 20px;
    white-space: nowrap;

    &--breakfast {
      background: #e3f9e9;
      border: 1px solid #2ba471;
      color: #2ba471;
    }

    &--lunch {
      background: #f2f3ff;
      border: 1px solid #0052d9;
      color: #0052d9;
    }

    &--dinner {
      background: #fff1e9;
      border: 1px solid #e37318;
      color: #e37318;
    }

    &--supper {
      background: #efefef;
      border: 1px solid #c0c5ca;
      color: #666;
    }
  }

  .meal-schedule-fields {
    margin-bottom: 8px;
  }

  .meal-schedule-tools {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 12px;
  }

  /* ===== 菜谱选择 ===== */
  .recipe-option {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .recipe-option-meta {
    color: #909399;
    font-size: 12px;
  }

  .recipe-table {
    margin-top: 0;
  }

  .recipe-info-card {
    box-sizing: border-box;
    width: 100%;
    min-height: 130px;
    padding: 13px 16px 16px;
    background: #FFFFFF;
    border: 1px solid #EBEEF5;
    border-radius: 8px;
  }

  .recipe-info-card.is-collapsed {
    min-height: 50px;
    height: 50px;
    padding: 11px 16px;
    overflow: hidden;
  }

  .recipe-info-card + .recipe-info-card {
    margin-top: 12px;
  }

  .recipe-info-card__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .recipe-info-card__meta {
    min-width: 0;
    display: flex;
    align-items: flex-start;
    align-content: flex-start;
    flex-wrap: wrap;
    gap: 8px;
    flex: 1;
  }

  .recipe-info-card__index {
    width: 16px;
    height: 16px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: #7288FA;
    border-radius: 3px;
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 600;
    line-height: 20px;
    color: #FFFFFF;
    flex: none;
  }

  .recipe-info-card__name {
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 20px;
    color: #000000;
  }

  .recipe-info-card__tag {
    box-sizing: border-box;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 24px;
    padding: 2px 8px;
    background: #EFEFEF;
    border: 1px solid #C0C5CA;
    border-radius: 5px;
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 20px;
    color: #666666;
    flex: none;
  }

  .recipe-info-card__material-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    min-width: 0;
  }

  .recipe-info-card__material-list--expanded {
    padding: 0 0 2px;
  }

  .recipe-info-card__material {
    box-sizing: border-box;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 24px;
    max-width: 100%;
    padding: 2px 10px;
    background: rgba(104, 194, 58, 0.1);
    border: 1px solid rgba(104, 194, 58, 0.3);
    border-radius: 5px;
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 20px;
    color: #68C23A;
    flex: none;
    white-space: normal;
    word-break: break-all;
  }

  .recipe-info-card__controls {
    display: inline-flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    flex: none;
    flex-shrink: 0;
    min-width: 0;
    padding-right: 1px;
    margin-top: -6px;
  }

  .recipe-info-card__controls-main {
    display: inline-flex;
    align-items: center;
    gap: 0;
    min-width: 0;
  }

  .recipe-info-card__pricing-group {
    display: inline-flex;
    align-items: center;
    margin-left: 35px;
    flex: none;
  }

  .recipe-info-card__stepper-wrap {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    width: 156px;
    height: 28px;
    margin-left: 27px;
    flex: none;
  }

  .recipe-info-card__stepper {
    display: inline-flex;
    align-items: center;
    padding: 0;
    width: 140px;
    height: 28px;
    flex: none;
  }

  .recipe-info-card__stepper-btn {
    box-sizing: border-box;
    width: 31px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 4px 10px;
    border: 1px solid #D9D9D9;
    background: #F5F7FA;
    color: rgba(124, 126, 129, 0.85);
    font-size: 14px;
    line-height: 14px;
    cursor: pointer;
    flex: none;
  }

  .recipe-info-card__stepper-btn:first-child {
    border-radius: 4px 0 0 4px;
  }

  .recipe-info-card__stepper-btn:last-child {
    border-radius: 0 4px 4px 0;
  }

  .recipe-info-card__stepper-value {
    box-sizing: border-box;
    width: 78px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 4px 12px;
    border-top: 1px solid #D9D9D9;
    border-bottom: 1px solid #D9D9D9;
    background: #FFFFFF;
    font-family: 'Roboto', sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.35);
    flex: none;
  }

  .recipe-info-card__stepper-unit {
    width: 12px;
    height: 22px;
    display: inline-flex;
    align-items: center;
    justify-content: flex-end;
    font-family: 'Roboto', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 22px;
    text-align: right;
    color: rgba(0, 0, 0, 0.85);
    flex: none;
  }

  .recipe-info-card__price-block {
    display: inline-flex;
    flex-direction: column;
    align-items: flex-end;
    justify-content: center;
    gap: 0;
    width: 52px;
    flex: none;
    margin-top: -3px;
  }

  .recipe-info-card__price-unit {
    font-family: 'Roboto', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.35);
    white-space: nowrap;
  }

  .recipe-info-card__price-total {
    font-family: 'PingFang SC', sans-serif;
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
    color: #FF4D4F;
    white-space: nowrap;
  }

  .recipe-info-card__collapse,
  .recipe-info-card__delete {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    border: none;
    background: transparent;
    cursor: pointer;
  }

  .recipe-info-card__collapse {
    box-sizing: border-box;
    height: 28px;
    padding: 8px;
    gap: 6px;
    border: 1px solid #BEC0CA;
    border-radius: 4px;
    font-family: 'PingFang SC', sans-serif;
    font-size: 13px;
    line-height: 18px;
    color: #53545C;
    flex: none;
    white-space: nowrap;
    margin-top: -1px;
    margin-left: 64px;
  }

  .recipe-info-card__collapse span {
    display: inline-flex;
    align-items: center;
    line-height: 1;
  }

  .recipe-info-card__collapse-icon {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    flex: none;
    vertical-align: middle;
    color: #53545C;
  }

  .recipe-info-card__collapse-icon::before,
  .recipe-info-card__collapse-icon::after {
    content: '';
    position: absolute;
    width: 8px;
    height: 1.5px;
    background: currentColor;
    border-radius: 999px;
  }

  .recipe-info-card__collapse-icon.is-collapsed::before,
  .recipe-info-card__collapse-icon.is-collapsed::after {
    top: 5px;
  }

  .recipe-info-card__collapse-icon.is-collapsed::before {
    left: 2px;
    transform: rotate(45deg);
    transform-origin: left center;
  }

  .recipe-info-card__collapse-icon.is-collapsed::after {
    right: 2px;
    transform: rotate(-45deg);
    transform-origin: right center;
  }

  .recipe-info-card__collapse-icon.is-expanded::before,
  .recipe-info-card__collapse-icon.is-expanded::after {
    top: 9px;
  }

  .recipe-info-card__collapse-icon.is-expanded::before {
    left: 2px;
    transform: rotate(-45deg);
    transform-origin: left center;
  }

  .recipe-info-card__collapse-icon.is-expanded::after {
    right: 2px;
    transform: rotate(45deg);
    transform-origin: right center;
  }

  .recipe-info-card__delete {
    width: 28px;
    height: 28px;
    border-radius: 5px;
    background: rgba(255, 77, 79, 0.1);
    color: #FF4D4F;
    font-size: 18px;
    line-height: 18px;
    flex: none;
  }

  .recipe-info-card__divider {
    height: 0;
    margin: 13px 0 10px;
    border-top: 1px dashed #999999;
  }

  .recipe-info-card.is-collapsed .recipe-info-card__divider {
    display: none;
  }

  .recipe-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* ===== 菜谱详情面板 ===== */
  .recipe-detail-panel {
    padding: 8px;
    background: #f8fafc;
    border-radius: 6px;
  }

  .recipe-detail-panel__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 12px;
  }

  .recipe-detail-panel__title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .recipe-detail-panel__meta {
    display: flex;
    gap: 16px;
    margin-top: 6px;
    font-size: 12px;
    color: #909399;
  }

  .detail-block + .detail-block {
    margin-top: 12px;
  }

  .detail-block__title {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }

  .detail-block__content {
    color: #606266;
    line-height: 1.7;
  }

  .detail-block__steps {
    white-space: pre-wrap;
  }

  /* ===== 汇总条 ===== */
  .summary-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 20px;
    margin-top: 12px;
    padding: 12px 16px;
    background: #f5f7fa;
    border-radius: 6px;
    color: #606266;
  }

  .summary-bar--cost-total {
    box-sizing: border-box;
    width: 100%;
    min-height: 50px;
    margin-top: 16px;
    padding: 11px 16px;
    display: flex;
    align-items: center;
    gap: 3px;
    background: rgba(255, 241, 240, 0.5);
    border: 1px solid rgba(255, 120, 117, 0.5);
    border-radius: 8px;
    color: rgba(0, 0, 0, 0.5);
  }

  .summary-bar__label {
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 17px;
    color: rgba(0, 0, 0, 0.5);
  }

  .summary-bar__amount {
    font-family: 'PingFang SC', sans-serif;
    font-size: 18px;
    font-weight: 600;
    line-height: 25px;
    color: #FF4D4F;
    margin-left: 10px;
  }

  .summary-bar__meta {
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    font-weight: 400;
    line-height: 17px;
    color: rgba(0, 0, 0, 0.5);
    margin-left: 3px;
  }

  .nutrition-analysis-section {
    margin-top: 24px;
  }

  .nutrition-analysis-section__header {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    padding: 0;
    gap: 12px;
    width: 100%;
    min-height: 32px;
  }

  .nutrition-analysis-section__title-wrap {
    display: flex;
    flex-direction: row;
    align-items: center;
    padding: 0;
    gap: 4px;
    width: 88px;
    height: 16px;
    flex: none;
  }

  .nutrition-analysis-section__body {
    margin-top: 12px;
  }

  .nutrition-analysis-section__panel {
    box-sizing: border-box;
    width: 710px;
    min-height: 130px;
    padding: 10px 16px 20px;
    background: #F7F9FC;
    border: 1px solid #EBEEF5;
    border-radius: 8px;
  }

  .nutrition-analysis-panel__summary {
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    width: 100%;
    height: 22px;
  }

  .nutrition-analysis-panel__summary-title {
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 20px;
    color: #000000;
  }

  .nutrition-analysis-panel__summary-meta {
    font-family: 'Roboto', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 12px;
    line-height: 22px;
    color: #999999;
    text-align: right;
    white-space: nowrap;
  }

  .nutrition-analysis-panel__cards {
    display: grid;
    grid-template-columns: repeat(4, 158px);
    gap: 16px;
    margin-top: 18px;
  }

  .nutrition-analysis-panel__status,
  .nutrition-analysis-panel__assessment {
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    line-height: 18px;
  }

  .nutrition-analysis-panel__status {
    color: #5570F1;
  }

  .nutrition-analysis-panel__assessment {
    color: #606266;
  }

  .nutrition-balance-panel {
    box-sizing: border-box;
    width: 710px;
    min-height: 130px;
    margin-top: 12px;
    padding: 16px;
    background: #FFFFFF;
    border: 1px solid #EBEEF5;
    border-radius: 8px;
  }

  .nutrition-suggestion-panel {
    box-sizing: border-box;
    width: 710px;
    min-height: 100px;
    margin-top: 12px;
    padding: 12px 16px 20px 11px;
    border: 1px solid #EBEEF5;
    border-radius: 8px;
    background: #FFFFFF;
  }

  .nutrition-suggestion-panel__title {
    width: 84px;
    height: 20px;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 20px;
    color: #000000;
    white-space: nowrap;
  }

  .nutrition-suggestion-panel__list {
    margin: 4px 0 0;
    padding: 0;
    list-style: none;
  }

  .nutrition-suggestion-panel__item,
  .nutrition-suggestion-panel__empty {
    font-family: 'Roboto', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 12px;
    line-height: 22px;
    color: #999999;
  }

  .nutrition-suggestion-panel__item {
    position: relative;
    padding-left: 12px;
  }

  .nutrition-suggestion-panel__item::before {
    content: '•';
    position: absolute;
    left: 0;
    top: 0;
    color: #999999;
  }

  .nutrition-suggestion-panel__empty {
    margin-top: 4px;
  }

  .nutrition-balance-panel__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .nutrition-balance-panel__title {
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    font-weight: 500;
    line-height: 20px;
    color: #1D2129;
  }

  .nutrition-balance-panel__score-badge {
    display: inline-flex;
    flex-direction: row;
    justify-content: center;
    align-items: center;
    gap: 8px;
    width: 83px;
    height: 32px;
    padding: 5px 16px;
    border-radius: 26px;
    font-family: 'PingFang SC', sans-serif;
    box-sizing: border-box;
  }

  .nutrition-balance-panel__score-badge.is-excellent,
  .nutrition-balance-panel__score-badge.is-good,
  .nutrition-balance-panel__score-badge.is-normal,
  .nutrition-balance-panel__score-badge.is-warning {
    color: #D54941;
    background: #FFF1F0;
    border: none;
  }

  .nutrition-balance-panel__score-badge.is-pending {
    color: #86909C;
    background: #F2F3F5;
    border: none;
  }

  .nutrition-balance-panel__score {
    font-size: 18px;
    line-height: 22px;
    font-weight: 600;
    color: inherit;
  }

  .nutrition-balance-panel__grade {
    font-size: 18px;
    line-height: 22px;
    font-weight: 600;
    color: inherit;
    white-space: nowrap;
  }

  .nutrition-balance-panel__rows {
    display: flex;
    flex-direction: column;
    gap: 14px;
    margin-top: 16px;
  }

  .nutrition-balance-panel__row {
    display: grid;
    grid-template-columns: 56px 1fr 40px;
    align-items: center;
    gap: 12px;
  }

  .nutrition-balance-panel__label,
  .nutrition-balance-panel__value {
    font-family: 'PingFang SC', sans-serif;
    font-size: 12px;
    line-height: 18px;
  }

  .nutrition-balance-panel__label {
    color: #4E5969;
  }

  .nutrition-balance-panel__value {
    color: #1D2129;
    text-align: right;
    font-weight: 500;
  }

  .nutrition-balance-panel__track {
    position: relative;
    height: 10px;
    background: #F2F3F5;
    border-radius: 999px;
    overflow: hidden;
  }

  .nutrition-balance-panel__fill {
    height: 100%;
    border-radius: 999px;
    transition: width 0.2s ease;
  }

  .nutrition-balance-panel__fill.is-protein {
    background: linear-gradient(90deg, #6DD400 0%, #95DE64 100%);
  }

  .nutrition-balance-panel__fill.is-carbohydrate {
    background: linear-gradient(90deg, #FA8C16 0%, #FFC069 100%);
  }

  .nutrition-balance-panel__fill.is-fat {
    background: linear-gradient(90deg, #597EF7 0%, #85A5FF 100%);
  }

  .nutrition-analysis-card {
    box-sizing: border-box;
    width: 158px;
    height: 70px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 15px 10px;
    background: #FFFFFF;
    border: 1px solid #EBEEF5;
    border-radius: 8px;
  }

  .nutrition-analysis-card__icon {
    width: 40px;
    height: 40px;
    border-radius: 12px;
    display: block;
    flex: none;
    object-fit: cover;
  }

  .nutrition-analysis-card__content {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: flex-start;
    padding: 0;
    width: 88px;
    min-width: 0;
  }

  .nutrition-analysis-card__value {
    width: 100%;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 600;
    font-size: 18px;
    line-height: 25px;
    color: #333333;
  }

  .nutrition-analysis-card__label {
    width: 100%;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 12px;
    line-height: 17px;
    color: rgba(0, 0, 0, 0.5);
    white-space: nowrap;
  }

  .nutrition-analysis-card__per-capita {
    width: 100%;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 12px;
    line-height: 17px;
    color: #68C23A;
  }

  .nutrition-analysis-section__bar {
    width: 4px;
    height: 16px;
    flex: none;
    order: 0;
    flex-grow: 0;
    background: #5570F1;
    border-radius: 2px;
  }

  .nutrition-analysis-section__title {
    width: 80px;
    height: 16px;
    flex: none;
    order: 1;
    flex-grow: 0;
    font-family: 'Inter', sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 16px;
    line-height: 16px;
    color: #000000;
    white-space: nowrap;
  }

  .nutrition-analysis-section__action {
    flex: none;
    min-width: 88px;
    height: 32px;
    padding: 5px 16px;
    border: 1px solid #7288FA !important;
    border-radius: 6px !important;
    background: #FFFFFF !important;
    color: #7288FA !important;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
  }

  .nutrition-analysis-section__action:hover,
  .nutrition-analysis-section__action:focus {
    border-color: #7288FA !important;
    background: #F5F7FF !important;
    color: #7288FA !important;
  }

  .recipe-picker-dialog__header {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    align-items: flex-start;
    padding: 0;
    gap: 13px;
    width: 710px;
    max-width: 100%;
    height: 32px;
  }

  .recipe-picker-dialog__header-content {
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding: 0;
    width: 710px;
    max-width: 100%;
    height: 32px;
    margin: 0 auto;
  }

  .recipe-picker-dialog__title {
    width: 80px;
    height: 30px;
    margin: 0;
    font-family: 'Poppins', 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 20px;
    line-height: 30px;
    text-align: center;
    color: #000000;
    flex: none;
    flex-grow: 0;
  }

  .recipe-picker-dialog__close {
    position: relative;
    width: 32px;
    height: 32px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    border: none;
    background: #FFF2E2;
    border-radius: 8px;
    cursor: pointer;
    flex: none;
    flex-grow: 0;
    flex-shrink: 0;
  }

  .recipe-picker-dialog__close-icon {
    width: 24px;
    height: 24px;
    font-size: 24px;
    color: #1C1D22;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .recipe-picker {
    min-height: 100%;
  }

  .recipe-picker__footer {
    display: flex;
    justify-content: flex-end;
    gap: 9px;
  }

  .recipe-picker__footer :deep(.el-button) {
    min-width: 58px;
    height: 32px;
    padding: 5px 16px;
    border-radius: 6px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    line-height: 22px;
  }

  .recipe-picker__footer :deep(.el-button:not(.el-button--primary)) {
    color: #53545C;
    border: 1px solid #BEC0CA;
    background: #FFFFFF;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
  }

  .recipe-picker__footer :deep(.el-button--primary) {
    background: #7288FA;
    border-color: #7288FA;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
  }

  .recipe-picker__list {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
    max-height: 560px;
    overflow-y: auto;
    align-content: start;
  }

  .recipe-picker__item {
    position: relative;
    display: flex;
    align-items: center;
    gap: 12px;
    min-height: 54px;
    padding: 9px 12px;
    border: none;
    border-radius: 12px;
    background: #F8F7F7;
    box-shadow: 0px 12px 16px -4px rgba(220, 220, 220, 0.6);
    cursor: pointer;
  }

  .recipe-picker__content {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 6px;
    flex: 1;
    min-width: 0;
  }

  .recipe-picker__item.is-active {
    background: #F8F7F7;
    box-shadow: 0px 12px 16px -4px rgba(220, 220, 220, 0.6);
    outline: 1px solid rgba(114, 136, 250, 0.35);
  }

  .recipe-picker__item input {
    margin: 0;
    width: 16px;
    height: 16px;
    accent-color: #7288FA;
    flex-shrink: 0;
  }

  .recipe-picker__name {
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 13px;
    line-height: 13px;
    color: #667187;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .recipe-picker__meta {
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 13px;
    line-height: 13px;
    color: #E96466;
    background: transparent;
    padding: 0;
    border-radius: 0;
    white-space: nowrap;
  }

  .ml-8 {
    margin-left: 8px;
  }
}

/* ===== Footer 按钮 ===== */
.dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
  padding: 12px 24px 16px;
}

.btn-cancel {
  border: 1px solid #BEC0CA !important;
  border-radius: 6px !important;
  color: #53545C !important;
  background: #FFFFFF !important;
  box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
  padding: 5px 16px;

  &:hover,
  &:focus {
    border-color: #BEC0CA !important;
    color: #53545C !important;
    background: #F5F5F5 !important;
  }
}

.btn-confirm {
  background: #7288FA !important;
  border: none !important;
  border-radius: 6px !important;
  color: #FFFFFF !important;
  box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
  padding: 5px 16px;

  &:hover,
  &:focus {
    background: #5E75E8 !important;
    color: #FFFFFF !important;
  }
}
</style>
