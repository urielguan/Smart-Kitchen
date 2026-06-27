<template>
  <el-dialog
    v-model="visible"
    width="758px"
    top="40px"
    destroy-on-close
    :show-close="false"
    class="plan-detail-dialog"
    @close="handleClose"
  >
    <!-- Custom Header -->
    <template #header>
      <div class="dialog-header">
        <div class="dialog-header__content">
          <div class="dialog-header__title">菜谱详情</div>
          <button class="dialog-close" type="button" aria-label="关闭" @click="handleClose">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 6L18 18" />
              <path d="M18 6L6 18" />
            </svg>
          </button>
        </div>
      </div>
    </template>

    <div v-if="plan" class="plan-detail">
      <section class="info-card">
        <div class="info-card__main">
          <div class="plan-title-row">
            <h2 class="plan-code">{{ plan.planCode }}</h2>
            <div class="status-badge" :class="plan.status">
              <span class="status-badge__dot"></span>
              {{ RECIPE_PLAN_STATUS_MAP[plan.status] || plan.status }}
            </div>
          </div>

          <div class="plan-meta">
            <span class="meta-item meta-item--date">
              <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <circle cx="7" cy="7" r="5.5" stroke="#F4B740" />
                <path d="M7 3.8V7L9.2 8.3" stroke="#F4B740" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <span>{{ plan.planDate }}</span>
            </span>
            <span class="meta-item meta-item--date" v-if="plan.startDate && plan.endDate">
              <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <circle cx="7" cy="7" r="5.5" stroke="#F4B740" />
                <path d="M7 3.8V7L9.2 8.3" stroke="#F4B740" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <span>实施: {{ plan.startDate }} 至 {{ plan.endDate }}</span>
            </span>
            <span class="meta-item meta-item--date">
              <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <path d="M2.2 10.8H11.8L7 4.2L2.2 10.8Z" stroke="#F4B740" stroke-linejoin="round" />
                <path d="M5.6 8.6H8.4" stroke="#F4B740" stroke-linecap="round" />
              </svg>
              <span>{{ mealDisplayText }}</span>
            </span>
            <span class="meta-item" v-if="plan.orgName">{{ plan.orgName }}</span>
          </div>
        </div>

        <div class="info-stats">
          <div class="stat-item">
            <span class="stat-value">{{ plan.recipeCount || 0 }}</span>
            <span class="stat-label">菜品数</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ plan.totalServings || 0 }}</span>
            <span class="stat-label">总份数</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ expectedCountText }}</span>
            <span class="stat-label">就餐人数</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value stat-value--primary">¥{{ (plan.estimatedCost || 0).toFixed(2) }}</span>
            <span class="stat-label">预估成本(元)</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value" :class="getNutritionClass(summaryNutritionPassRate)">
              {{ summaryNutritionPassRate.toFixed(1) }}%
            </span>
            <span class="stat-label">营养达标率</span>
          </div>
        </div>
      </section>

      <section class="detail-section detail-section--recipe" v-if="plan.recipes?.length">
        <div class="detail-section__heading">
          <span class="detail-section__accent"></span>
          <span class="detail-section__title">菜谱明细</span>
        </div>

        <div class="recipe-list">
          <div
            v-for="(recipe, index) in plan.recipes"
            :key="recipe.id"
            class="recipe-item"
            :class="{ 'recipe-item--expanded': expandedRecipes.has(recipe.id) }"
          >
            <div class="recipe-main-row">
              <div class="recipe-summary">
                <span class="recipe-index">{{ index + 1 }}</span>
                <span class="recipe-name">{{ recipe.recipeName }}</span>
                <span class="recipe-tag">{{ recipe.categoryName }}</span>
              </div>

              <div class="recipe-servings-control">
                <button class="stepper-btn" type="button" disabled>-</button>
                <span class="stepper-value">{{ recipe.plannedServings }}</span>
                <button class="stepper-btn" type="button" disabled>+</button>
                <span class="stepper-unit">份</span>
              </div>

              <div class="recipe-price-block">
                <div class="unit-cost">¥{{ (recipe.unitCost || 0).toFixed(2) }}/份</div>
                <div class="total-cost">¥{{ (recipe.totalCost || 0).toFixed(2) }}</div>
              </div>

              <button
                v-if="recipe.ingredients?.length"
                type="button"
                class="ingredients-toggle"
                @click="toggleIngredients(recipe.id)"
              >
                <span>{{ expandedRecipes.has(recipe.id) ? '收起' : '展开' }}</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedRecipes.has(recipe.id) }">
                  <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
              </button>
            </div>

            <div v-if="recipe.ingredients?.length && expandedRecipes.has(recipe.id)" class="ingredients-panel">
              <span
                v-for="(ing, i) in recipe.ingredients"
                :key="i"
                class="ingredient-tag"
                :class="{ main: ing.isMain }"
              >{{ ing.materialName }} {{ ing.quantity }}{{ ing.unit }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="detail-section detail-section--nutrition" v-if="plan?.id && plan.recipes?.length">
        <div class="detail-section__heading">
          <span class="detail-section__accent"></span>
          <span class="detail-section__title">AI营养评估</span>
          <button
            v-if="plan?.id && plan.recipes?.length"
            type="button"
            class="ai-generate-btn"
            :disabled="nutritionLoading"
            @click="handleAiAnalysis"
          >
            {{ nutritionLoading ? '分析中...' : 'AI分析' }}
          </button>
        </div>

        <div v-if="nutritionLoading && !nutritionData" class="nutrition-loading-state">
          <span>营养分析加载中...</span>
        </div>

        <div v-else-if="nutritionData" class="nutrition-panel">
          <div class="nutrition-overview-card">
            <div class="nutrition-ring" :class="getScoreClass(nutritionData.overallScore)">
              <svg viewBox="0 0 192 192" class="nutrition-ring__svg" aria-hidden="true">
                <defs>
                  <linearGradient id="nutritionOverviewScoreGradient" x1="96" y1="23" x2="96" y2="169" gradientUnits="userSpaceOnUse">
                    <stop offset="0%" stop-color="#6e84ff" />
                    <stop offset="100%" stop-color="#637aff" />
                  </linearGradient>
                </defs>
                <path
                  class="nutrition-ring__track"
                  :d="nutritionOverviewTrackPath"
                  :stroke-width="nutritionOverviewStroke"
                  stroke="url(#nutritionOverviewScoreGradient)"
                />
                <path
                  class="nutrition-ring__progress"
                  :d="nutritionOverviewScorePath"
                  :stroke-width="nutritionOverviewStroke"
                  stroke="url(#nutritionOverviewScoreGradient)"
                />
                <path
                  class="nutrition-ring__inner-dash"
                  :d="nutritionOverviewArcPath"
                  :stroke-width="nutritionOverviewInnerStroke"
                  :stroke-dasharray="nutritionOverviewInnerDasharray"
                />
              </svg>
              <div class="nutrition-ring__content">
                <span class="nutrition-ring__value">{{ nutritionData.overallScore || 0 }}</span>
                <span class="nutrition-ring__label">综合评分</span>
              </div>
            </div>

            <div class="nutrition-ring nutrition-ring--pass-rate">
              <svg viewBox="0 0 192 192" class="nutrition-ring__svg" aria-hidden="true">
                <defs>
                  <linearGradient id="nutritionOverviewPassGradient" x1="96" y1="23" x2="96" y2="169" gradientUnits="userSpaceOnUse">
                    <stop offset="0%" stop-color="#68c23a" />
                    <stop offset="100%" stop-color="#3cc7ec" />
                  </linearGradient>
                </defs>
                <path
                  class="nutrition-ring__track"
                  :d="nutritionOverviewTrackPath"
                  :stroke-width="nutritionOverviewStroke"
                  stroke="url(#nutritionOverviewPassGradient)"
                />
                <path
                  class="nutrition-ring__progress"
                  :d="nutritionOverviewPassPath"
                  :stroke-width="nutritionOverviewStroke"
                  stroke="url(#nutritionOverviewPassGradient)"
                />
                <path
                  class="nutrition-ring__inner-dash"
                  :d="nutritionOverviewArcPath"
                  :stroke-width="nutritionOverviewInnerStroke"
                  :stroke-dasharray="nutritionOverviewInnerDasharray"
                />
              </svg>
              <div class="nutrition-ring__content">
                <span class="nutrition-ring__value">{{ (nutritionData.passRate || 0).toFixed(1) }}%</span>
                <span class="nutrition-ring__label">营养达标率</span>
              </div>
            </div>

            <div class="nutrition-overview-stats">
              <div class="nutrition-overview-stats__servings">
                <span class="nutrition-overview-stats__servings-label">就餐人数</span>
                <span class="nutrition-overview-stats__servings-value">{{ nutritionServingDisplay }}个</span>
              </div>
              <div class="nutrition-overview-stats__grid">
                <div class="nutrition-overview-metric nutrition-overview-metric--left">
                  <span class="nutrition-overview-metric__label">总热量：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--dark">{{ (nutritionData.totalCalories || 0).toFixed(0) }} kcal</span>
                </div>
                <div class="nutrition-overview-metric nutrition-overview-metric--right">
                  <span class="nutrition-overview-metric__label">人均热量：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--green">{{ (nutritionData.avgCalories || 0).toFixed(1) }} kcal</span>
                </div>
                <div class="nutrition-overview-metric nutrition-overview-metric--left">
                  <span class="nutrition-overview-metric__label">蛋白质：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--dark">{{ (nutritionData.totalProtein || 0).toFixed(1) }}g</span>
                </div>
                <div class="nutrition-overview-metric nutrition-overview-metric--right">
                  <span class="nutrition-overview-metric__label">人均：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--green">{{ (nutritionData.avgProtein || 0).toFixed(1) }}g</span>
                </div>
                <div class="nutrition-overview-metric nutrition-overview-metric--left">
                  <span class="nutrition-overview-metric__label">碳水：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--dark">{{ (nutritionData.totalCarbohydrate || 0).toFixed(1) }}g</span>
                </div>
                <div class="nutrition-overview-metric nutrition-overview-metric--right">
                  <span class="nutrition-overview-metric__label">人均：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--green">{{ (nutritionData.avgCarbohydrate || 0).toFixed(1) }}g</span>
                </div>
                <div class="nutrition-overview-metric nutrition-overview-metric--left">
                  <span class="nutrition-overview-metric__label">脂肪：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--dark">{{ (nutritionData.totalFat || 0).toFixed(1) }}g</span>
                </div>
                <div class="nutrition-overview-metric nutrition-overview-metric--right">
                  <span class="nutrition-overview-metric__label">人均：</span>
                  <span class="nutrition-overview-metric__value nutrition-overview-metric__value--green">{{ (nutritionData.avgFat || 0).toFixed(1) }}g</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="nutritionSummaryRows.length" class="nutrition-structure-card">
            <div class="nutrition-structure-card__header">
              <span class="nutrition-structure-card__title">饮食结构分析</span>
              <span class="nutrition-structure-card__badge">
                <span class="nutrition-structure-card__badge-number">{{ nutritionStructureScore }}</span>
                <span class="nutrition-structure-card__badge-text">需调整</span>
              </span>
            </div>
            <div class="nutrition-structure-card__rows">
              <div v-for="item in nutritionSummaryRows.slice(0, 3)" :key="item.name" class="nutrition-structure-row">
                <span class="nutrition-structure-row__name">{{ item.name }}</span>
                <div class="nutrition-structure-row__bar">
                  <span class="nutrition-structure-row__fill" :class="getStructureBarClass(item.name)" :style="{ width: `${Math.min(item.percentage, 100)}%` }"></span>
                </div>
                <span class="nutrition-structure-row__percent">{{ item.percentage }}%</span>
              </div>
            </div>
            <div class="nutrition-structure-card__summary">{{ nutritionStructureSummary }}</div>
          </div>

          <div v-if="nutritionTargetRows.length" class="nutrition-target-card">
            <div class="nutrition-target-card__title">营养目标对比</div>
            <div class="nutrition-target-card__table-header">
              <span>营养素</span>
              <span>实际人均</span>
              <span>目标值</span>
              <span>状态</span>
              <span class="nutrition-target-card__table-header-rate">达成率</span>
            </div>
            <div class="nutrition-target-card__rows">
              <div v-for="row in nutritionTargetRows" :key="row.name" class="nutrition-target-row">
                <span class="nutrition-target-row__name">{{ row.name }}</span>
                <span class="nutrition-target-row__actual">{{ row.actual }}</span>
                <span class="nutrition-target-row__target">{{ row.target }}</span>
                <span class="nutrition-target-row__status" :class="getTargetStatusClass(row.status)">{{ row.statusLabel }}</span>
                <div class="nutrition-target-row__progress">
                  <span class="nutrition-target-row__progress-fill" :class="getStructureBarClass(row.name)" :style="{ width: `${row.progressPercent}%` }"></span>
                </div>
                <span class="nutrition-target-row__percentage">{{ row.percentage }}%</span>
              </div>
            </div>
          </div>

          <div class="ai-narrative-card">
            <div class="ai-narrative-card__header">
              <span class="ai-narrative-card__title">AI优化建议</span>
            </div>
            <div v-if="hasAiNarrativeContent" class="ai-narrative-card__content">
              <p class="ai-narrative-text">{{ aiOptimizationText }}</p>
            </div>
            <p v-else class="ai-narrative-card__empty">{{ aiEmptyDesc }}</p>
          </div>

          <div v-if="hasDetailedReportContent" class="ai-report-card" :class="{ 'ai-report-card--collapsed': !showFullAssessment }">
            <div class="ai-report-card__header">
              <span class="ai-report-card__title">详细报告</span>
              <button class="ai-assessment-toggle ai-report-card__toggle" type="button" @click="showFullAssessment = !showFullAssessment">
                <span>{{ showFullAssessment ? '收起' : '展开' }}</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: showFullAssessment }">
                  <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
              </button>
            </div>
            <div class="ai-report-card__content">
              <p class="ai-assessment-content">{{ showFullAssessment ? aiDetailedReportText : aiDetailedReportPreviewText }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="detail-section detail-section--basic-info">
        <div class="detail-section__heading">
          <span class="detail-section__accent"></span>
          <span class="detail-section__title">基础信息</span>
        </div>

        <div class="basic-info-card basic-info-card--audit-row">
          <div class="basic-info-audit-row">
            <div class="basic-info-audit-cell basic-info-audit-cell--label">审核人</div>
            <div class="basic-info-audit-cell basic-info-audit-cell--value">{{ auditPersonDisplayText }}</div>
            <div class="basic-info-audit-cell basic-info-audit-cell--label">审核时间</div>
            <div class="basic-info-audit-cell basic-info-audit-cell--value">{{ auditTimeDisplayText }}</div>
          </div>
        </div>
      </section>

      <section v-if="plan.stockRiskMessage || plan.stockRiskStatus || plan.remark || plan.auditLogs?.length || plan.rejectionReason" class="detail-section detail-section--secondary">
        <div v-if="plan.stockRiskMessage || plan.stockRiskStatus" class="secondary-card">
          <div class="secondary-card__header">
            <span class="secondary-card__title">库存风险</span>
            <span class="secondary-card__tag" :class="getStockRiskClass(plan.stockRiskStatus)">{{ getStockRiskLabel(plan.stockRiskStatus, plan.stockRiskLabel) }}</span>
          </div>
          <p class="secondary-card__text">{{ plan.stockRiskMessage || '暂无库存风险说明' }}</p>
        </div>

        <div v-if="plan.rejectionReason || plan.auditStatusLabel || plan.auditedByName || plan.auditedAt" class="secondary-card">
          <div class="secondary-card__header">
            <span class="secondary-card__title">审核信息</span>
          </div>
          <div class="secondary-card__meta">
            <span v-if="plan.auditStatusLabel">审核状态：{{ plan.auditStatusLabel }}</span>
            <span v-if="plan.auditedByName">审核人：{{ plan.auditedByName }}</span>
            <span v-if="plan.auditedAt">审核时间：{{ formatDateTime(plan.auditedAt) }}</span>
          </div>
          <p v-if="plan.rejectionReason" class="secondary-card__text">驳回原因：{{ plan.rejectionReason }}</p>
        </div>

        <div v-if="plan.remark" class="secondary-card">
          <div class="secondary-card__header">
            <span class="secondary-card__title">备注信息</span>
          </div>
          <p class="secondary-card__text">{{ plan.remark }}</p>
        </div>

        <div v-if="plan.auditLogs?.length" class="secondary-card">
          <div class="secondary-card__header">
            <span class="secondary-card__title">审核记录</span>
          </div>
          <div class="timeline-list">
            <div v-for="log in plan.auditLogs" :key="log.id" class="timeline-item">
              <div class="timeline-item__row">
                <span class="timeline-item__action">{{ log.actionName || log.action }}</span>
                <span v-if="log.operatorName" class="timeline-item__operator">{{ log.operatorName }}</span>
                <span v-if="log.createdAt" class="timeline-item__time">{{ formatDateTime(log.createdAt) }}</span>
              </div>
              <p v-if="log.remark" class="timeline-item__remark">{{ log.remark }}</p>
            </div>
          </div>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <button class="dialog-footer__cancel" @click="handleClose">取消</button>
        <button
          v-if="plan?.status === 'approved'"
          class="dialog-footer__cancel dialog-footer__cancel--warning"
          v-permission="PLAN_PERMISSIONS.WITHDRAW"
          @click="handleWithdraw"
        >
          撤回计划
        </button>
        <button
          v-if="plan?.status === 'rejected'"
          class="dialog-footer__primary dialog-footer__primary--wide"
          v-permission="PLAN_PERMISSIONS.EDIT"
          @click="handleModifyRejected"
        >
          按驳回意见修改
        </button>
        <button
          v-else-if="plan?.status === 'approved' && plan?.canAdjust !== false"
          class="dialog-footer__primary"
          v-permission="PLAN_PERMISSIONS.ADJUST"
          @click="handleGoToAdjustment"
        >
          {{ plan?.hasPendingAdjustment ? '查看调整进度' : '调整计划' }}
        </button>
        <button
          v-else
          class="dialog-footer__primary"
          v-permission="PLAN_PERMISSIONS.CREATE"
          @click="handleCopy"
        >
          复制计划
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import type { RecipePlanDetail, AINutritionAssessment, StockRiskStatus } from '@/types/plan'
import { RECIPE_PLAN_STATUS_MAP, STOCK_RISK_STATUS_MAP, NUTRITION_TARGETS_BY_GROUP, TARGET_GROUP_MAP, HEALTH_STATUS_MAP } from '@/types/plan'
import { PLAN_PERMISSIONS } from '@/constants/permission'
import { analyzePlanNutrition, getAiNutritionAssessment } from '@/api/modules/plan'
import { formatDateTime } from '@/utils'

const polarToCartesian = (cx: number, cy: number, radius: number, angleInDegrees: number) => {
  const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180
  return {
    x: cx + radius * Math.cos(angleInRadians),
    y: cy + radius * Math.sin(angleInRadians)
  }
}

const describeArc = (cx: number, cy: number, radius: number, startAngle: number, endAngle: number) => {
  const start = polarToCartesian(cx, cy, radius, endAngle)
  const end = polarToCartesian(cx, cy, radius, startAngle)
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1'

  return [`M ${start.x} ${start.y}`, `A ${radius} ${radius} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`].join(' ')
}

const props = defineProps<{
  modelValue: boolean
  plan: RecipePlanDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'refresh': []
  'request-adjustment': [plan: RecipePlanDetail]
  'request-withdraw': [plan: RecipePlanDetail]
  'modify-rejected': [plan: RecipePlanDetail]
  'copy': [plan: RecipePlanDetail]
}>()

const router = useRouter()
const nutritionLoading = ref(false)
const nutritionData = ref<AINutritionAssessment | null>(null)
const liveAiNutritionData = ref<{ planId: number; data: AINutritionAssessment } | null>(null)
const aiNarrativeData = ref<Partial<AINutritionAssessment> | null>(null)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const showFullAssessment = ref(false)
const expandedRecipes = ref(new Set<number>())
const nutritionRequestToken = ref(0)
const aiAnalysisInFlight = ref(false)
const pendingAiNarrativeSync = ref<{ planId: number; data: Partial<AINutritionAssessment> } | null>(null)
type RawNutritionAssessment = Partial<AINutritionAssessment> & {
  nutritionComparison?: AINutritionAssessment['nutritionComparisons']
}

type NutritionComparisonDisplayRow = {
  name: string
  actual: string
  target: string
  status: string
  statusLabel: string
  percentage: number
  progressPercent: number
}

function normalizeNutritionComparisons(data?: RawNutritionAssessment | null): AINutritionAssessment['nutritionComparisons'] {
  if (!data) return []
  if (data.nutritionComparisons?.length) return data.nutritionComparisons
  return data.nutritionComparison || []
}

function buildDisplayNutritionData(
  data: RawNutritionAssessment,
  fallback?: AINutritionAssessment | null
): AINutritionAssessment {
  return {
    overallScore: data.overallScore ?? fallback?.overallScore ?? 0,
    grade: data.grade ?? fallback?.grade ?? '',
    assessment: data.assessment ?? fallback?.assessment ?? '',
    aiOptimizationSuggestions: data.aiOptimizationSuggestions ?? fallback?.aiOptimizationSuggestions,
    aiStatus: data.aiStatus ?? fallback?.aiStatus,
    aiStatusMessage: data.aiStatusMessage ?? fallback?.aiStatusMessage,
    totalCalories: data.totalCalories ?? fallback?.totalCalories ?? 0,
    totalProtein: data.totalProtein ?? fallback?.totalProtein ?? 0,
    totalCarbohydrate: data.totalCarbohydrate ?? fallback?.totalCarbohydrate ?? 0,
    totalFat: data.totalFat ?? fallback?.totalFat ?? 0,
    totalSodium: data.totalSodium ?? fallback?.totalSodium ?? 0,
    totalFiber: data.totalFiber ?? fallback?.totalFiber ?? 0,
    avgCalories: data.avgCalories ?? fallback?.avgCalories ?? 0,
    avgProtein: data.avgProtein ?? fallback?.avgProtein ?? 0,
    avgCarbohydrate: data.avgCarbohydrate ?? fallback?.avgCarbohydrate ?? 0,
    avgFat: data.avgFat ?? fallback?.avgFat ?? 0,
    passRate: data.passRate ?? fallback?.passRate ?? 0,
    servingCount: data.servingCount ?? fallback?.servingCount ?? 0,
    dietStructure: data.dietStructure ?? fallback?.dietStructure ?? {
      proteinRatio: 0,
      carbohydrateRatio: 0,
      fatRatio: 0,
      evaluation: ''
    },
    nutritionComparisons: normalizeNutritionComparisons(data).length
      ? normalizeNutritionComparisons(data)
      : (fallback?.nutritionComparisons || []),
    suggestions: data.suggestions ?? fallback?.suggestions ?? []
  }
}

function buildRuleNutritionData(data: AINutritionAssessment): AINutritionAssessment {
  return buildDisplayNutritionData(
    {
      ...data,
      suggestions: [],
      aiOptimizationSuggestions: undefined,
      assessment: '',
      aiStatus: undefined,
      aiStatusMessage: undefined
    },
    null
  )
}

function normalizeAiNarrativeStatus(data: Partial<AINutritionAssessment>) {
  if (data.aiStatus) return data.aiStatus
  if (data.suggestions?.length || data.aiOptimizationSuggestions || data.assessment) {
    return 'success'
  }
  return undefined
}

function buildAiNarrativeData(data: Partial<AINutritionAssessment>): Partial<AINutritionAssessment> {
  const aiStatus = normalizeAiNarrativeStatus(data)
  const isSuccess = aiStatus === 'success'
  return {
    suggestions: isSuccess ? (data.suggestions || []) : [],
    aiOptimizationSuggestions: isSuccess ? data.aiOptimizationSuggestions : undefined,
    assessment: isSuccess ? data.assessment : '',
    aiStatus,
    aiStatusMessage: data.aiStatusMessage
  }
}

function extractAiNarrativeFromPlan(plan?: RecipePlanDetail | null) {
  if (!plan?.aiNutritionAssessment) return null
  try {
    const parsed = JSON.parse(plan.aiNutritionAssessment) as RawNutritionAssessment
    return buildAiNarrativeData(parsed)
  } catch {
    return null
  }
}

function extractNutritionDataFromPlan(plan?: RecipePlanDetail | null) {
  if (!plan?.aiNutritionAssessment) return null
  try {
    const parsed = JSON.parse(plan.aiNutritionAssessment) as RawNutritionAssessment
    return buildDisplayNutritionData(parsed)
  } catch {
    return null
  }
}

function normalizeTargetGroup(targetGroup?: string) {
  if (!targetGroup) return 'adult'
  if (targetGroup === 'general') return 'adult'
  return targetGroup
}

const buildNutritionAnalysisRecipes = (plan?: RecipePlanDetail | null) => {
  return (plan?.recipes || [])
    .map(item => ({
      recipeId: Number(item.recipeId || item.id || 0),
      servings: Number(item.plannedServings || 0)
    }))
    .filter(item => item.recipeId > 0 && item.servings > 0)
}

const loadRuleNutritionData = async (plan?: RecipePlanDetail | null) => {
  const requestToken = ++nutritionRequestToken.value
  const planId = plan?.id
  const mergeBaseline = nutritionData.value
  const analysisRecipes = buildNutritionAnalysisRecipes(plan)

  if (!planId || !analysisRecipes.length) {
    if (requestToken === nutritionRequestToken.value) {
      nutritionData.value = null
      nutritionLoading.value = false
    }
    return
  }

  nutritionLoading.value = true
  try {
    const res = await analyzePlanNutrition({
      recipes: analysisRecipes,
      servingCount: Number(plan.expectedCount || 1),
      targetGroup: normalizeTargetGroup(plan.targetGroup),
      healthStatus: plan.healthStatus
    })

    const isStale = requestToken !== nutritionRequestToken.value || !visible.value || props.plan?.id !== planId
    if (isStale) return

    const hasManualAiOverride = liveAiNutritionData.value?.planId === planId
    if (hasManualAiOverride) {
      nutritionData.value = liveAiNutritionData.value?.data || nutritionData.value
      return
    }

    if (res.code === 'SUCCESS' && res.data) {
      nutritionData.value = buildDisplayNutritionData(res.data, mergeBaseline)
      return
    }
    if (!mergeBaseline) {
      nutritionData.value = null
    }
  } catch {
    if (requestToken !== nutritionRequestToken.value || !visible.value || props.plan?.id !== planId) return
    if (!mergeBaseline) {
      nutritionData.value = null
    }
  } finally {
    if (requestToken === nutritionRequestToken.value && visible.value && props.plan?.id === planId) {
      nutritionLoading.value = false
    }
  }
}

const handleAiAnalysis = async () => {
  const planId = props.plan?.id
  if (nutritionLoading.value || !planId || !props.plan?.recipes?.length) return

  nutritionRequestToken.value += 1
  aiAnalysisInFlight.value = true
  nutritionLoading.value = true
  try {
    const res = await getAiNutritionAssessment(planId)
    if (!visible.value || props.plan?.id !== planId) return

    if (res.code === 'SUCCESS' && res.data) {
      const aiResult = buildDisplayNutritionData(res.data as RawNutritionAssessment)
      nutritionData.value = aiResult
      liveAiNutritionData.value = { planId, data: aiResult }
      const nextNarrative = buildAiNarrativeData(aiResult)
      aiNarrativeData.value = nextNarrative
      pendingAiNarrativeSync.value = { planId, data: nextNarrative }
      emit('refresh')
      ElMessage.success('AI营养分析已更新')
      return
    }
    ElMessage.error(res.message || 'AI营养分析生成失败')
  } catch {
    if (!visible.value || props.plan?.id !== planId) return
    ElMessage.error('AI营养分析生成失败')
  } finally {
    aiAnalysisInFlight.value = false
    if (visible.value && props.plan?.id === planId) {
      nutritionLoading.value = false
    }
  }
}

watch(
  () => props.plan,
  (plan, previousPlan) => {
    const planId = plan?.id
    const persistedNutrition = extractNutritionDataFromPlan(plan)
    const persistedNarrative = extractAiNarrativeFromPlan(plan)
    const pendingNarrativeState = pendingAiNarrativeSync.value
    const pendingNarrative = pendingNarrativeState && pendingNarrativeState.planId === planId
      ? pendingNarrativeState.data
      : null
    const pendingLiveAiNutrition = liveAiNutritionData.value && liveAiNutritionData.value.planId === planId
      ? liveAiNutritionData.value.data
      : null
    const hasPlanChanged = plan?.id !== previousPlan?.id
    expandedRecipes.value = new Set<number>()

    if (pendingAiNarrativeSync.value && pendingAiNarrativeSync.value.planId !== planId) {
      pendingAiNarrativeSync.value = null
    }
    if (liveAiNutritionData.value && liveAiNutritionData.value.planId !== planId) {
      liveAiNutritionData.value = null
    }

    if (visible.value) {
      nutritionData.value = pendingLiveAiNutrition || persistedNutrition
      if (!aiAnalysisInFlight.value && !pendingLiveAiNutrition) {
        void loadRuleNutritionData(plan)
      }
    } else {
      nutritionData.value = pendingLiveAiNutrition || persistedNutrition
      nutritionLoading.value = false
    }

    if (hasPlanChanged) {
      aiNarrativeData.value = pendingNarrative || persistedNarrative
      showFullAssessment.value = true
      return
    }

    if (pendingNarrative) {
      aiNarrativeData.value = pendingNarrative
    } else if (persistedNarrative) {
      aiNarrativeData.value = persistedNarrative
    }
  },
  { immediate: true }
)

watch(
  () => [visible.value, props.plan?.id] as const,
  ([isVisible, planId], previousValue) => {
    const previousPlanId = previousValue?.[1]

    if (!isVisible) {
      nutritionRequestToken.value += 1
      aiAnalysisInFlight.value = false
      pendingAiNarrativeSync.value = null
      liveAiNutritionData.value = null
      nutritionData.value = null
      nutritionLoading.value = false
      expandedRecipes.value = new Set<number>()
      showFullAssessment.value = true
      return
    }

    if (planId && planId !== previousPlanId) {
      pendingAiNarrativeSync.value = null
      liveAiNutritionData.value = null
      expandedRecipes.value = new Set<number>()
      showFullAssessment.value = true
    }

    if (isVisible && planId && !aiAnalysisInFlight.value && liveAiNutritionData.value?.planId !== planId) {
      void loadRuleNutritionData(props.plan)
    }
  },
  { immediate: true }
)

const toggleIngredients = (recipeId: number) => {
  const s = new Set(expandedRecipes.value)
  if (s.has(recipeId)) {
    s.delete(recipeId)
  } else {
    s.add(recipeId)
  }
  expandedRecipes.value = s
}

const handleClose = () => {
  visible.value = false
}

/** 发起调整或查看进度 */
const handleGoToAdjustment = async () => {
  if (!props.plan) return
  if (!props.plan.hasPendingAdjustment) {
    emit('request-adjustment', props.plan)
    handleClose()
    return
  }
  handleClose()
  await nextTick()
  router.push({
    path: '/plan-adjustment',
    query: {
      planId: String(props.plan.id),
      action: 'view'
    }
  })
}

/** 发起撤回 */
const handleWithdraw = () => {
  if (!props.plan) return
  emit('request-withdraw', props.plan)
  handleClose()
}

/** 按驳回意见修改 */
const handleModifyRejected = () => {
  if (!props.plan) return
  emit('modify-rejected', props.plan)
  handleClose()
}

/** 复制计划 */
const handleCopy = () => {
  if (!props.plan) return
  emit('copy', props.plan)
  handleClose()
}

const aiEmptyDesc = computed(() => {
  const status = aiNarrativeData.value?.aiStatus
  if (status === 'not_configured') {
    return aiNarrativeData.value?.aiStatusMessage || '当前未接入AI接口，无法生成优化建议、AI营养建议和AI营养评估报告。'
  }
  if (status === 'failed') {
    return aiNarrativeData.value?.aiStatusMessage || '当前暂无建议内容生成，请检查AI接口配置后重试。'
  }
  return '点击下方按钮后，将基于当前计划的营养计算结果生成优化建议、AI营养建议和AI营养评估报告。'
})

const hasAiNarrativeContent = computed(() => {
  const data = aiNarrativeData.value
  return Boolean(
    data?.suggestions?.length ||
    data?.aiOptimizationSuggestions ||
    data?.assessment
  )
})

const aiOptimizationText = computed(() => {
  if (aiNarrativeData.value?.aiOptimizationSuggestions) {
    return aiNarrativeData.value.aiOptimizationSuggestions
  }

  if (aiNarrativeData.value?.suggestions?.length) {
    return aiNarrativeData.value.suggestions.join(' ')
  }

  return aiEmptyDesc.value
})

const hasDetailedReportContent = computed(() => {
  return Boolean(aiNarrativeData.value?.assessment)
})

const aiDetailedReportText = computed(() => {
  if (aiNarrativeData.value?.assessment) {
    return aiNarrativeData.value.assessment
  }

  return ''
})

const aiDetailedReportPreviewText = computed(() => {
  const report = aiDetailedReportText.value
  if (!report) return ''

  const firstLines = report.split('\n').slice(0, 2).join('\n')
  return firstLines
})

const aiNarrativeTitle = computed(() => {
  if (aiNarrativeData.value?.aiStatus === 'success') {
    return '已生成AI营养建议'
  }
  return '尚未生成AI营养建议'
})

const mealDisplayText = computed(() => props.plan?.mealDisplayName || props.plan?.mealTypeName || '-')

const auditPersonDisplayText = computed(() => {
  return props.plan?.auditedByName || '-'
})

const auditTimeDisplayText = computed(() => {
  return props.plan?.auditedAt ? formatDateTime(props.plan.auditedAt) : '-'
})

const targetGroupDisplayText = computed(() => {
  const targetGroup = props.plan?.targetGroup
  if (!targetGroup) return '-'
  return TARGET_GROUP_MAP[targetGroup] || targetGroup
})

const healthStatusDisplayText = computed(() => {
  const healthStatus = props.plan?.healthStatus
  if (!healthStatus) return '-'

  const normalized = Array.isArray(healthStatus)
    ? healthStatus
    : String(healthStatus)
        .split(',')
        .map(item => item.trim())
        .filter(Boolean)

  if (!normalized.length) return '-'

  return normalized.map(item => HEALTH_STATUS_MAP[item] || item).join('、')
})

const dietRestrictionsDisplayText = computed(() => {
  return props.plan?.dietRestrictions || '-'
})

const expectedCountText = computed(() => {
  if (props.plan?.expectedCountDisplay) return props.plan.expectedCountDisplay.replace(/人$/, '')
  if (props.plan?.expectedCount) return String(props.plan.expectedCount)
  return '-'
})

const nutritionServingDisplay = computed(() => {
  const servingCount = nutritionData.value?.servingCount
  if (servingCount) return String(servingCount)
  return expectedCountText.value
})

const persistedNutritionData = computed(() => extractNutritionDataFromPlan(props.plan))

const normalizePassRate = (value: unknown) => {
  if (value === null || value === undefined || value === '') return null
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return null
  return Math.min(Math.max(numericValue, 0), 100)
}

const summaryNutritionPassRate = computed(() => {
  const liveAiPassRate = liveAiNutritionData.value?.planId === props.plan?.id
    ? normalizePassRate(liveAiNutritionData.value.data.passRate)
    : null
  if (liveAiPassRate !== null) {
    return liveAiPassRate
  }

  const persistedPassRate = normalizePassRate(persistedNutritionData.value?.passRate)
  if (persistedPassRate !== null) {
    return persistedPassRate
  }

  const livePassRate = normalizePassRate(nutritionData.value?.passRate)
  if (livePassRate !== null) {
    return livePassRate
  }

  const detailPassRate = normalizePassRate(props.plan?.nutritionPassRate)
  if (detailPassRate !== null) {
    return detailPassRate
  }

  return 0
})

const nutritionTargetGroupKey = computed(() => normalizeTargetGroup(props.plan?.targetGroup))

const nutritionTargetReference = computed(() => {
  return NUTRITION_TARGETS_BY_GROUP[nutritionTargetGroupKey.value] || NUTRITION_TARGETS_BY_GROUP.adult
})

const nutritionSummaryRows = computed(() => {
  if (!nutritionData.value) return []

  const protein = Number(nutritionData.value.totalProtein || 0)
  const carbohydrate = Number(nutritionData.value.totalCarbohydrate || 0)
  const fat = Number(nutritionData.value.totalFat || 0)
  const total = protein + carbohydrate + fat

  if (!total) return []

  return [
    { name: '蛋白质', actual: protein.toFixed(1), target: '0.0', status: '', percentage: Math.round((protein / total) * 100) },
    { name: '碳水', actual: carbohydrate.toFixed(1), target: '0.0', status: '', percentage: Math.round((carbohydrate / total) * 100) },
    { name: '脂肪', actual: fat.toFixed(1), target: '0.0', status: '', percentage: Math.round((fat / total) * 100) }
  ]
})


const nutritionOverviewPercent = computed(() => {
  const value = Number(nutritionData.value?.overallScore || 0)
  return Math.min(Math.max(value, 0), 100)
})

const nutritionOverviewPassPercent = computed(() => {
  const value = Number(nutritionData.value?.passRate || 0)
  return Math.min(Math.max(value, 0), 100)
})

const nutritionOverviewArcLength = 300
const nutritionOverviewStartAngle = 210
const nutritionOverviewStroke = 20
const nutritionOverviewRadius = 75
const nutritionOverviewInnerRadius = 61
const nutritionOverviewInnerStroke = 1
const nutritionOverviewCircumference = 2 * Math.PI * nutritionOverviewRadius
const nutritionOverviewVisibleArc = nutritionOverviewCircumference * (nutritionOverviewArcLength / 360)
const nutritionOverviewGapArc = nutritionOverviewCircumference - nutritionOverviewVisibleArc
const nutritionOverviewDashoffset = `${nutritionOverviewCircumference * (1 - nutritionOverviewStartAngle / 360)}`
const nutritionOverviewTrackPath = describeArc(96, 96, nutritionOverviewRadius, nutritionOverviewStartAngle, nutritionOverviewStartAngle + nutritionOverviewArcLength)
const nutritionOverviewArcPath = describeArc(96, 96, nutritionOverviewInnerRadius, nutritionOverviewStartAngle, nutritionOverviewStartAngle + nutritionOverviewArcLength)
const nutritionOverviewInnerDasharray = '0.8 6.2'

const buildNutritionOverviewProgressPath = (percent: number) => {
  const clampedPercent = Math.min(Math.max(percent, 0), 100)
  const progressEndAngle = nutritionOverviewStartAngle + nutritionOverviewArcLength * (clampedPercent / 100)
  return describeArc(96, 96, nutritionOverviewRadius, nutritionOverviewStartAngle, progressEndAngle)
}

const nutritionOverviewScorePath = computed(() => buildNutritionOverviewProgressPath(nutritionOverviewPercent.value))
const nutritionOverviewPassPath = computed(() => buildNutritionOverviewProgressPath(nutritionOverviewPassPercent.value))

const nutritionStructureScore = computed(() => {
  if (!nutritionSummaryRows.value.length) return '0'
  const total = nutritionSummaryRows.value.reduce((sum, item) => sum + item.percentage, 0)
  return String(Math.round(total / nutritionSummaryRows.value.length))
})

const nutritionStructureSummary = computed(() => {
  if (!nutritionSummaryRows.value.length) return '暂无饮食结构分析结果。'

  const lowItems = nutritionSummaryRows.value.filter(item => item.percentage < 90).map(item => item.name)
  const highItems = nutritionSummaryRows.value.filter(item => item.percentage > 110).map(item => item.name)

  if (!lowItems.length && !highItems.length) {
    return '饮食结构较均衡，请继续保持当前菜谱搭配。'
  }

  const segments: string[] = []
  if (lowItems.length) {
    segments.push(`${lowItems.join('、')}偏低`)
  }
  if (highItems.length) {
    segments.push(`${highItems.join('、')}偏高`)
  }

  return `饮食结构需要调整。${segments.join('，')}，建议参考AI推荐优化菜谱搭配。`
})

const getStructureBarClass = (name: string) => {
  if (name.includes('蛋白')) return 'is-protein'
  if (name.includes('碳水')) return 'is-carbohydrate'
  if (name.includes('脂肪')) return 'is-fat'
  return 'is-default'
}

const buildNutritionTargetRow = (
  name: string,
  actual: number,
  target: number,
  unit: string,
  status?: string
): NutritionComparisonDisplayRow => {
  const percentage = target > 0 ? Math.round((actual / target) * 100) : 0
  const normalizedStatus = status || getNutritionTargetStatusByPercent(percentage)
  return {
    name,
    actual: `${actual.toFixed(1)} ${unit}`,
    target: `${target.toFixed(1)} ${unit}`,
    status: normalizedStatus,
    statusLabel: getNutritionTargetStatusLabel(normalizedStatus),
    percentage,
    progressPercent: Math.min(Math.max(percentage, 0), 100)
  }
}

const getNutritionComparisonUnit = (name: string) => {
  return name.includes('热量') ? 'kcal' : 'g'
}

const getNutritionComparisonNumber = (...values: Array<number | string | null | undefined>) => {
  for (const value of values) {
    if (value === null || value === undefined || value === '') {
      continue
    }

    const parsed = Number(value)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }
  return 0
}

const normalizeNutritionTargetStatus = (status?: string) => {
  if (!status) return ''

  const normalized = status.trim().toLowerCase()
  if (['low', 'below', 'insufficient', '不足', '偏低'].includes(normalized)) return 'low'
  if (['high', 'above', 'excess', '过量', '偏高'].includes(normalized)) return 'high'
  if (['normal', '正常', '达标'].includes(normalized)) return 'normal'
  return status
}

const getNutritionComparisonStatus = (comparison: AINutritionAssessment['nutritionComparisons'][number]) => {
  return normalizeNutritionTargetStatus(comparison.status || comparison.comparisonStatus || '')
}

const getNutritionComparisonPercentage = (comparison: AINutritionAssessment['nutritionComparisons'][number]) => {
  return Math.round(getNutritionComparisonNumber(
    comparison.achievementRate,
    comparison.progressPercent,
    comparison.percentage
  ))
}

const getNutritionComparisonProgressPercent = (
  comparison: AINutritionAssessment['nutritionComparisons'][number],
  fallbackPercentage: number
) => {
  const progress = getNutritionComparisonNumber(
    comparison.progressPercent,
    comparison.achievementRate,
    comparison.percentage,
    fallbackPercentage
  )
  return Math.min(Math.max(progress, 0), 100)
}

const getNutritionTargetStatusByPercent = (percentage: number) => {
  if (percentage < 90) return 'low'
  if (percentage > 100) return 'high'
  return 'normal'
}

const nutritionTargetRows = computed<NutritionComparisonDisplayRow[]>(() => {
  const comparisonRows = nutritionData.value?.nutritionComparisons || []

  if (comparisonRows.length) {
    return comparisonRows.map(item => {
      const percentage = getNutritionComparisonPercentage(item)
      const unit = getNutritionComparisonUnit(item.nutrientName)
      const actualValue = getNutritionComparisonNumber(item.perCapitaAmount, item.actualValue)
      const targetValue = getNutritionComparisonNumber(item.targetAmount, item.targetValue)
      const status = getNutritionComparisonStatus(item) || getNutritionTargetStatusByPercent(percentage)

      return {
        name: item.nutrientName,
        actual: `${actualValue.toFixed(1)} ${unit}`,
        target: `${targetValue.toFixed(1)} ${unit}`,
        status,
        statusLabel: getNutritionTargetStatusLabel(status),
        percentage,
        progressPercent: getNutritionComparisonProgressPercent(item, percentage)
      }
    })
  }

  if (!nutritionData.value) return []

  const target = nutritionTargetReference.value

  return [
    buildNutritionTargetRow('热量', Number(nutritionData.value.avgCalories || 0), target.calories / 3, 'kcal'),
    buildNutritionTargetRow('蛋白质', Number(nutritionData.value.avgProtein || 0), target.protein / 3, 'g'),
    buildNutritionTargetRow('碳水化合物', Number(nutritionData.value.avgCarbohydrate || 0), target.carbohydrate / 3, 'g'),
    buildNutritionTargetRow('脂肪', Number(nutritionData.value.avgFat || 0), target.fat / 3, 'g')
  ]
})




const getNutritionTargetStatusLabel = (status?: string) => {
  const normalizedStatus = normalizeNutritionTargetStatus(status)
  if (normalizedStatus === 'low') return '偏低'
  if (normalizedStatus === 'high') return '过量'
  return '正常'
}

const getTargetStatusClass = (status?: string) => {
  const normalizedStatus = normalizeNutritionTargetStatus(status)
  if (normalizedStatus === 'low') return 'is-low'
  if (normalizedStatus === 'high') return 'is-high'
  return 'is-normal'
}

/** 获取营养达标率样式类 */
const getNutritionClass = (rate: number) => {
  if (rate >= 80) return 'excellent'
  if (rate >= 60) return 'good'
  return 'needs-improvement'
}

/** 获取评分样式类 */
const getScoreClass = (score: number) => {
  if (score >= 85) return 'score-excellent'
  if (score >= 70) return 'score-good'
  if (score >= 60) return 'score-pass'
  return 'score-improve'
}

const getStockRiskClass = (status?: string) => {
  const map: Record<string, string> = {
    normal: 'is-normal',
    warning: 'is-warning',
    expired: 'is-expired',
    shortage: 'is-shortage',
    unknown: 'is-unknown'
  }
  return status ? (map[status] || 'is-unknown') : 'is-unknown'
}

const getStockRiskLabel = (status?: string, fallback?: string) => {
  if (!status) {
    return fallback || '待人工确认'
  }
  return STOCK_RISK_STATUS_MAP[(status as StockRiskStatus)] || fallback || '待人工确认'
}

onBeforeRouteLeave(() => {
  if (visible.value) {
    visible.value = false
  }
})

onBeforeUnmount(() => {
  if (visible.value) {
    visible.value = false
  }
})
</script>

<style lang="scss" scoped>
:deep(.plan-detail-dialog.el-dialog) {
  display: flex !important;
  flex-direction: column;
  width: 758px !important;
  max-width: calc(100vw - 32px);
  height: 815px !important;
  max-height: 815px !important;
  margin: 0 auto;
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
}

@media (max-height: 854px) {
  :deep(.plan-detail-dialog.el-dialog) {
    height: calc(100vh - 40px) !important;
    max-height: calc(100vh - 40px) !important;
  }
}

:deep(.plan-detail-dialog .el-dialog__header) {
  position: relative;
  flex: 0 0 auto;
  min-height: 72px;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:deep(.plan-detail-dialog .el-dialog__header::after) {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  border-top: 1px solid #e1e2e9;
  pointer-events: none;
}

:deep(.plan-detail-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0;
  overflow-y: auto;
  background: #ffffff;
}

:deep(.plan-detail-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  margin: 0;
  padding: 0;
  background: #ffffff;
  border-top: 1px solid #e1e2e9;
}

.dialog-header {
  width: calc(100% - 48px);
  margin: 24px 24px 0;
}

.dialog-header__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
}

.dialog-header__title {
  margin: 0;
  font-family: 'Poppins', sans-serif;
  font-size: 20px;
  font-weight: 500;
  line-height: 30px;
  color: #000000;
}

.dialog-close {
  position: relative;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: #fff2e2;
  cursor: pointer;
}

.dialog-close svg {
  position: absolute;
  top: 4px;
  left: 4px;
  width: 24px;
  height: 24px;
}

.dialog-close path {
  fill: none;
  stroke: #1c1d22;
  stroke-width: 2;
  stroke-linecap: round;
}

.plan-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px 24px 24px;
  background: #ffffff;
}

.info-card,
.secondary-card,
.nutrition-summary-card,
.nutrition-metric-card,
.nutrition-comparison-list,
.ai-narrative-card {
  border: 1px solid #eceef3;
  border-radius: 12px;
  background: #ffffff;
}

.info-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  min-height: 160px;
  padding: 16px 20px;
  background: #ffffff;
  border: 1px solid #e1e2e9;
  border-radius: 10px;
}

.info-card__main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.plan-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.plan-code {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 28px;
  color: #1c1d22;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  color: #667085;
  background: #f2f4f7;
}

.status-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-badge.pending {
  color: #d97706;
  background: #fff7ed;
}

.status-badge.approved {
  color: #16a34a;
  background: #f0fdf4;
}

.status-badge.rejected {
  color: #dc2626;
  background: #fef2f2;
}

.status-badge.draft {
  color: #667085;
  background: #f2f4f7;
}

.plan-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.meta-item {
  font-size: 13px;
  line-height: 20px;
  color: #667187;
}

.meta-item--date {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.meta-item--date svg {
  width: 14px;
  height: 14px;
  flex: none;
}

.info-stats {
  display: grid;
  grid-template-columns: 75px 36px 75px 36px 75px 36px 80px 36px 75px;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed #e4d6c8;
  padding-top: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  text-align: center;
}

.stat-divider {
  width: 36px;
  height: 0;
  margin: 0;
  justify-self: center;
  align-self: center;
  border-top: 1px solid #e1e2e9;
  background: transparent;
  transform: rotate(90deg);
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  line-height: 24px;
  color: #1c1d22;
  word-break: break-word;
}

.stat-value--primary,
.stat-value.excellent {
  color: #e96466;
}

.stat-value.good {
  color: #f59e0b;
}

.stat-value.needs-improvement {
  color: #dc2626;
}

.stat-label {
  font-size: 12px;
  line-height: 18px;
  color: #98a2b3;
}

.info-stats .stat-divider:first-of-type {
  width: 36px;
  height: 0;
  margin: 0;
  justify-self: center;
  align-self: center;
  border-top: 1px solid #e1e2e9;
  background: transparent;
  transform: rotate(90deg);
}

.info-stats .stat-item:nth-child(9) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0;
  width: 75px;
  height: 54px;
}

.info-stats .stat-item:nth-child(9) .stat-value,
.info-stats .stat-item:nth-child(9) .stat-value.excellent,
.info-stats .stat-item:nth-child(9) .stat-value.good,
.info-stats .stat-item:nth-child(9) .stat-value.needs-improvement {
  width: 75px;
  height: 34px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  text-align: center;
  color: #ff4d4f;
}

.info-stats .stat-item:nth-child(9) .stat-label {
  width: 75px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  text-align: center;
  color: #333333;
}

.info-stats .stat-item:nth-child(7) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0;
  width: 75px;
  height: 54px;
}

.info-stats .stat-item:nth-child(7) .stat-value {
  width: 79px;
  height: 34px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  text-align: center;
  color: #7288fa;
  white-space: nowrap;
}

.info-stats .stat-item:nth-child(7) .stat-label {
  width: 80px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  text-align: center;
  color: #333333;
}

.info-stats .stat-item:nth-child(3) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0;
  width: 75px;
  height: 54px;
}

.info-stats .stat-item:nth-child(3) .stat-value {
  width: 75px;
  height: 34px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  text-align: center;
  color: #ff4d4f;
}

.info-stats .stat-item:nth-child(3) .stat-label {
  width: 75px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  text-align: center;
  color: #333333;
}

.info-stats .stat-item:nth-child(5) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0;
  width: 75px;
  height: 54px;
}

.info-stats .stat-item:nth-child(5) .stat-value {
  width: 75px;
  height: 34px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  text-align: center;
  color: #ff4d4f;
}

.info-stats .stat-item:nth-child(5) .stat-label {
  width: 75px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  text-align: center;
  color: #333333;
}

.info-stats .stat-item:first-child {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0;
  width: 75px;
  height: 54px;
}

.info-stats .stat-item:first-child .stat-value {
  width: 75px;
  height: 34px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  text-align: center;
  color: #ff4d4f;
}

.info-stats .stat-item:first-child .stat-label {
  width: 75px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  text-align: center;
  color: #333333;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-section__heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-section__accent {
  width: 4px;
  height: 16px;
  border-radius: 999px;
  background: #7288fa;
}

.detail-section__title {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  color: #1c1d22;
}

.ai-generate-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #cdd6ff;
  border-radius: 6px;
  background: #eef2ff;
  color: #7288fa;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}

.ai-generate-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.basic-info-card {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  padding: 0;
  border: 1px solid #e1e2e9;
  border-radius: 0;
  background: #ffffff;
}

.basic-info-card--audit-row {
  width: 710px;
  height: 40px;
}

.basic-info-audit-row {
  display: grid;
  grid-template-columns: 126px 229px 126px 229px;
  width: 100%;
  height: 40px;
}

.basic-info-audit-cell {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  height: 40px;
  padding: 0 15px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
  border-right: 1px solid #e1e2e9;
}

.basic-info-audit-cell:last-child {
  border-right: none;
}

.basic-info-audit-cell--label {
  background: #f5f7fa;
  border-color: #eceef5;
}

.recipe-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recipe-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 16px;
  min-height: 50px;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.recipe-item--expanded {
  min-height: 130px;
}

.recipe-main-row {
  display: grid;
  grid-template-columns: 218px 156px 84px 66px;
  align-items: center;
  min-height: 48px;
  column-gap: 27px;
}

.recipe-summary {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  width: 218px;
  height: 24px;
}

.recipe-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex: none;
  border-radius: 3px;
  background: #7288fa;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  color: #ffffff;
}

.recipe-name {
  max-width: 56px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;
  color: #000000;
}

.recipe-tag,
.ingredient-chip,
.ingredient-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border-radius: 5px;
}

.recipe-tag {
  height: 24px;
  padding: 2px 8px;
  border: 1px solid #c0c5ca;
  background: #efefef;
  font-family: 'PingFang SC', sans-serif;
  font-size: 12px;
  font-weight: 400;
  line-height: 20px;
  color: #666666;
}

.ingredient-chip {
  min-width: 82px;
  height: 24px;
  padding: 0 10px;
  border: 1px solid rgba(104, 194, 58, 0.3);
  background: rgba(104, 194, 58, 0.1);
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;
  color: #68c23a;
}

.ingredient-tag {
  padding: 2px 10px;
  font-size: 12px;
  line-height: 18px;
  color: #667187;
  background: #f2f4f8;
}

.ingredient-tag.main {
  color: #68c23a;
  background: rgba(104, 194, 58, 0.1);
  border: 1px solid rgba(104, 194, 58, 0.3);
}

.recipe-servings-control {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: 156px;
  height: 28px;
  padding: 0;
  white-space: nowrap;
}

.stepper-btn {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 31px;
  height: 28px;
  padding: 4px 12px;
  border: 1px solid #d9d9d9;
  background: #f5f7fa;
  color: #7c7e81;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  font-weight: 400;
  line-height: 22px;
}

.recipe-servings-control .stepper-btn:first-child {
  border-radius: 4px 0 0 4px;
}

.recipe-servings-control .stepper-btn:nth-of-type(2) {
  border-radius: 0 4px 4px 0;
}

.stepper-value {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 78px;
  height: 28px;
  padding: 4px 12px;
  border-top: 1px solid #d9d9d9;
  border-bottom: 1px solid #d9d9d9;
  background: #ffffff;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  font-weight: 400;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.35);
}

.stepper-unit {
  width: 12px;
  height: 22px;
  font-family: 'Roboto', sans-serif;
  font-size: 12px;
  font-weight: 400;
  line-height: 22px;
  text-align: right;
  color: rgba(0, 0, 0, 0.85);
}

.recipe-price-block {
  width: 45px;
  text-align: right;
}

.unit-cost {
  width: 45px;
  font-family: 'Roboto', sans-serif;
  font-size: 12px;
  font-weight: 400;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.35);
  white-space: nowrap;
}

.total-cost {
  width: 45px;
  font-family: 'PingFang SC', sans-serif;
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
  color: #ff4d4f;
  white-space: nowrap;
}

.ingredients-toggle,
.ai-assessment-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 66px;
  height: 28px;
  padding: 8px;
  box-sizing: border-box;
  border: 1px solid #bec0ca;
  border-radius: 4px;
  background: #ffffff;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 18px;
  color: #53545c;
  white-space: nowrap;
  cursor: pointer;
}

.ingredients-toggle svg,
.ai-assessment-toggle svg {
  width: 16px;
  height: 16px;
  transition: transform 0.2s ease;
}

.ingredients-toggle svg.rotated,
.ai-assessment-toggle svg.rotated {
  transform: rotate(180deg);
}

.ingredients-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  align-content: flex-start;
  gap: 8px;
  min-height: 58px;
  padding: 10px 0 12px;
  border-top: 1px dashed #eceef3;
}

.nutrition-loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  border: 1px solid #eceef3;
  border-radius: 12px;
  background: #fafbff;
  color: #667187;
  font-size: 14px;
}

.nutrition-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.nutrition-overview-card {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 172px 172px minmax(0, 1fr);
  align-items: start;
  width: 100%;
  min-height: 172px;
  padding: 0 16px 0 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.nutrition-ring {
  position: relative;
  width: 172px;
  height: 172px;
}

.nutrition-ring__svg {
  width: 172px;
  height: 172px;
  overflow: visible;
}

.nutrition-ring__track,
.nutrition-ring__progress {
  fill: none;
  stroke-linecap: round;
}

.nutrition-ring__track {
  stroke: url(#nutritionOverviewScoreGradient);
  opacity: 0.2;
}

.nutrition-ring--pass-rate .nutrition-ring__track {
  stroke: url(#nutritionOverviewPassGradient);
  opacity: 0.2;
}

.nutrition-ring__progress {
  fill: none;
}

.nutrition-ring__inner-dash {
  fill: none;
  stroke: #999999;
  stroke-linecap: round;
}

.nutrition-ring__content {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.nutrition-ring__value {
  font-family: 'Poppins', sans-serif;
  font-style: normal;
  font-weight: 700;
  font-size: 28.6667px;
  line-height: 30px;
  text-align: center;
  color: #666666;
}

.nutrition-ring__label {
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 13.4902px;
  line-height: 19px;
  color: #666666;
}

.nutrition-overview-stats {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 15px 0 0 30px;
}

.nutrition-overview-stats__servings {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  min-height: 34px;
}

.nutrition-overview-stats__servings-label {
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  text-align: center;
  color: #333333;
}

.nutrition-overview-stats__servings-value {
  width: 39px;
  height: 34px;
  flex: none;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  text-align: center;
  color: #ff4d4f;
}

.nutrition-overview-stats__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
  row-gap: 4px;
}

.nutrition-overview-metric {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  white-space: nowrap;
}

.nutrition-overview-metric__label {
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  color: #999999;
}

.nutrition-overview-metric__value {
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
}

.nutrition-overview-metric__value--dark {
  color: #333333;
}

.nutrition-overview-metric__value--green {
  color: #68c23a;
}

.nutrition-structure-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 172px;
  padding: 10px 15px 13px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.nutrition-structure-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 4px;
}

.nutrition-structure-card__title {
  width: 84px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  color: #000000;
}

.nutrition-structure-card__badge {
  box-sizing: border-box;
  display: inline-flex;
  flex-direction: row;
  justify-content: center;
  align-items: center;
  min-width: 83px;
  width: fit-content;
  height: 32px;
  flex: none;
  padding: 5px 16px;
  gap: 4px;
  border-radius: 26px;
  background: #fff1f0;
  color: #d54941;
  white-space: nowrap;
}

.nutrition-structure-card__badge-number {
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 600;
  font-size: 22px;
  line-height: 22px;
  text-align: center;
  color: #d54941;
  flex: none;
}

.nutrition-structure-card__badge-text {
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 22px;
  text-align: center;
  color: #d54941;
  flex: none;
  white-space: nowrap;
}

.nutrition-structure-card__rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 0 15px 2px;
  border-bottom: 1px dashed #e1e2e9;
}

.nutrition-structure-row {
  display: grid;
  grid-template-columns: 40px 548px 52px;
  align-items: center;
  column-gap: 4px;
  min-height: 22px;
}

.nutrition-structure-row__name,
.nutrition-structure-row__percent {
  font-family: 'Roboto', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 22px;
  color: #999999;
}

.nutrition-structure-row__percent {
  width: 52px;
  text-align: right;
  white-space: nowrap;
}

.nutrition-structure-row__bar {
  position: relative;
  width: 548px;
  height: 10px;
  overflow: hidden;
  border-radius: 5px;
  background: #f2f4f8;
}

.nutrition-structure-row__fill {
  display: block;
  height: 100%;
  border-radius: 5px;
}

.nutrition-structure-row__fill.is-protein {
  background: #ff7474;
}

.nutrition-structure-row__fill.is-carbohydrate {
  background: #fdad00;
}

.nutrition-structure-row__fill.is-fat {
  background: #38cb89;
}

.nutrition-structure-row__fill.is-default {
  background: #7288fa;
}

.nutrition-structure-card__summary {
  width: 431px;
  margin-top: 11px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 17px;
  color: #999999;
}

.nutrition-target-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 172px;
  padding: 10px 15px 13px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.nutrition-target-card__title {
  width: 84px;
  height: 20px;
  margin-bottom: 4px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  color: #000000;
}

.nutrition-target-card__table-header,
.nutrition-target-row {
  display: grid;
  grid-template-columns: 72px 78px 64px 56px 319px 56px;
  align-items: center;
  column-gap: 2px;
}

.nutrition-target-card__table-header {
  min-height: 22px;
  margin-left: 2px;
  font-family: 'Roboto', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 22px;
  color: #999999;
}

.nutrition-target-card__table-header-rate {
  grid-column: 5 / span 2;
}

.nutrition-target-card__rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nutrition-target-row {
  min-height: 22px;
}

.nutrition-target-row__name,
.nutrition-target-row__actual,
.nutrition-target-row__target {
  font-family: 'Roboto', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 22px;
  color: #333333;
  white-space: nowrap;
}

.nutrition-target-row__status {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  width: fit-content;
  height: 20px;
  padding: 0 8px;
  border-radius: 5px;
  border: 1px solid #c0c5ca;
  background: #efefef;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 20px;
  color: #666666;
  white-space: nowrap;
}

.nutrition-target-row__status.is-low {
  background: #fffbe6;
  border-color: #faad14;
  color: #d48806;
}

.nutrition-target-row__status.is-high {
  background: #fff1f0;
  border-color: #f5222d;
  color: #f5222d;
}

.nutrition-target-row__status.is-normal {
  background: #f6ffed;
  border-color: #52c41a;
  color: #389e0d;
}

.nutrition-target-row__progress {
  position: relative;
  width: 319px;
  height: 10px;
  overflow: hidden;
  border-radius: 5px;
  background: #f2f4f8;
}

.nutrition-target-row__progress-fill {
  display: block;
  height: 100%;
  border-radius: 5px;
}

.nutrition-target-row__progress-fill.is-protein {
  background: #ff7474;
}

.nutrition-target-row__progress-fill.is-carbohydrate {
  background: #fdad00;
}

.nutrition-target-row__progress-fill.is-fat {
  background: #38cb89;
}

.nutrition-target-row__progress-fill.is-default {
  background: #7288fa;
}

.nutrition-target-row__percentage {
  width: 56px;
  font-family: 'Roboto', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 22px;
  text-align: right;
  color: #999999;
  white-space: nowrap;
}

.ai-narrative-card,
.nutrition-comparison-list,
.secondary-card {
  padding: 16px;
}

.nutrition-metric-card__subvalue,
.secondary-card__meta,
.timeline-item__time,
.timeline-item__operator,
.nutrition-comparison-item__values,
.nutrition-comparison-item__percentage {
  font-size: 12px;
  line-height: 18px;
  color: #667187;
}

.nutrition-comparison-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nutrition-comparison-item {
  display: grid;
  grid-template-columns: minmax(0, 132px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.nutrition-comparison-item__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nutrition-comparison-item__name,
.timeline-item__action {
  font-size: 13px;
  line-height: 20px;
  color: #1c1d22;
}

.nutrition-comparison-item__bar {
  position: relative;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef2ff;
}

.nutrition-comparison-item__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #7288fa 0%, #9eb0ff 100%);
}

.ai-narrative-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100px;
  padding: 12px 16px 12px 11px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.ai-narrative-card__header {
  display: flex;
  align-items: center;
  min-height: 20px;
  margin-bottom: 4px;
}

.ai-narrative-card__title {
  display: inline-block;
  width: auto;
  min-width: 69px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  color: #000000;
  white-space: nowrap;
}

.ai-narrative-card__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-narrative-card__empty,
.ai-narrative-text,
.ai-assessment-content,
.secondary-card__text,
.timeline-item__remark {
  margin: 0;
  font-size: 12px;
  line-height: 22px;
  color: #999999;
  white-space: pre-wrap;
}

.ai-narrative-text {
  width: 100%;
  min-height: 44px;
  font-family: 'Roboto', sans-serif;
  font-style: normal;
  font-weight: 400;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.ai-assessment-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-report-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 202px;
  padding: 12px 16px 12px 11px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.ai-report-card--collapsed {
  min-height: 100px;
}

.ai-report-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 28px;
  margin-bottom: 8px;
}

.ai-report-card__title {
  width: 56px;
  height: 20px;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 20px;
  color: #000000;
  white-space: nowrap;
}

.ai-report-card__toggle {
  width: 66px;
  height: 28px;
  padding: 8px;
}

.ai-report-card__content {
  display: flex;
  flex: 1;
}

.ai-report-card .ai-assessment-content {
  width: 100%;
  min-height: 154px;
  font-family: 'Roboto', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  line-height: 22px;
  color: #999999;
}

.ai-report-card--collapsed .ai-assessment-content {
  min-height: 44px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.detail-section--secondary {
  gap: 10px;
}

.secondary-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.secondary-card__tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
}

.secondary-card__tag.is-normal {
  color: #16a34a;
  background: #f0fdf4;
}

.secondary-card__tag.is-warning {
  color: #d97706;
  background: #fff7ed;
}

.secondary-card__tag.is-expired,
.secondary-card__tag.is-shortage {
  color: #dc2626;
  background: #fef2f2;
}

.secondary-card__tag.is-unknown {
  color: #667187;
  background: #f2f4f8;
}

.secondary-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.timeline-item {
  padding-top: 10px;
  border-top: 1px dashed #eceef3;
}

.timeline-item:first-child {
  padding-top: 0;
  border-top: none;
}

.timeline-item__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 24px 16px;
  background: #ffffff;
}

.dialog-footer__cancel,
.dialog-footer__primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 5px 16px;
  border-radius: 6px;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;
}

.dialog-footer__cancel {
  border: 1px solid #bec0ca;
  background: #ffffff;
  color: #53545c;
}

.dialog-footer__cancel--warning {
  border-color: rgba(230, 162, 60, 0.45);
  color: var(--el-color-warning);
}

.dialog-footer__primary {
  border: none;
  background: #7288fa;
  color: #ffffff;
}

.dialog-footer__primary--wide {
  min-width: 116px;
}

@media (max-width: 860px) {
  .recipe-main-row,
  .nutrition-panel__overview,
  .nutrition-summary-card__stats,
  .nutrition-metrics-grid,
  .nutrition-comparison-item,
  .info-stats {
    grid-template-columns: 1fr;
  }

  .stat-divider {
    display: none;
  }

  .info-stats {
    justify-content: stretch;
  }

  .recipe-price-block {
    text-align: left;
  }
}
</style>
