import type { Recipe } from '@/types/recipe'

/** 菜谱计划查询参数 */
export interface RecipePlanQuery {
  pageNum: number
  pageSize: number
  planDateStart?: string
  planDateEnd?: string
  planDate?: string
  planCode?: string
  orgName?: string
  startDateStart?: string
  startDateEnd?: string
  mealType?: string
  status?: string
  orgId?: number
  planId?: number
  targetGroup?: string
  healthStatus?: string
  expectedCount?: number
  // ============= 用餐偏好相关字段 =============
  /** 口味偏好（逗号分隔） */
  flavorPreferences?: string
  /** 辣度级别（0-5）：0=不辣 */
  spicyLevel?: number
  /** 禁忌食材ID列表（逗号分隔） */
  avoidIngredientIds?: string
  /** 饮食偏好标签（逗号分隔） */
  dietTags?: string
}

/** 菜谱计划列表项 */
export type RecipePlanAdjustmentMode = 'future_only' | 'history_mixed'

export interface RecipePlan {
  id: number
  planCode: string
  planDate: string
  startDate?: string
  endDate?: string
  orgId?: number
  orgName?: string
  mealType: string
  mealTypeName: string
  expectedCount?: number
  mealDisplayName?: string
  expectedCountDisplay?: string
  mealScheduleCount?: number
  targetGroup?: string
  healthStatus?: string
  dietRestrictions?: string
  totalServings: number
  estimatedCost: number
  nutritionPassRate: number
  status: string
  statusName: string
  adjustmentStatus?: string
  adjustmentStatusName?: string
  hasPendingAdjustment?: boolean
  adjustmentMode?: RecipePlanAdjustmentMode
  adjustmentModeName?: string
  adjustmentHint?: string
  canAdjust?: boolean
  stockRiskLabel?: string
  auditStatusLabel?: string
  rejectionReason?: string
  stockRiskStatus?: StockRiskStatus
  stockRiskStatusName?: string
  stockRiskMessage?: string
  stockValidatedAt?: string
  stockNextRecheckAt?: string
  submittedByName?: string
  submittedAt?: string
  auditedByName?: string
  auditedAt?: string
  createdByName?: string
  createdAt: string
  recipeCount?: number
}

/** 食材简要信息 */
export interface IngredientBrief {
  materialName: string
  quantity: number
  unit: string
  isMain: boolean
}

/** 菜谱计划明细项 */
export interface RecipePlanItem {
  id: number
  recipeId: number
  recipeCode: string
  recipeName: string
  categoryName: string
  imageUrl?: string
  mealKey?: string
  mealType?: string
  mealName?: string
  mealExpectedCount?: number
  mealSortOrder?: number
  plannedServings: number
  cookedServings: number
  unitCost: number
  totalCost: number
  sortOrder: number
  remark?: string
  status: string
  ingredients?: IngredientBrief[]
}

export interface RecipePlanMealSchedule {
  mealKey?: string
  mealType: string
  mealTypeName?: string
  mealName?: string
  expectedCount: number
  sortOrder?: number
  recipes: RecipePlanItem[]
}

/** 菜谱计划详情 */
export interface RecipePlanDetail extends RecipePlan {
  targetGroup?: string
  targetGroupName?: string
  healthStatus?: string
  dietRestrictions?: string
  aiNutritionAssessment?: string
  useAiRecommend?: boolean
  auditRemark?: string
  remark?: string
  updatedAt?: string
  hasPendingAdjustment?: boolean
  auditLogs?: RecipePlanAuditLogItem[]
  recipes: RecipePlanItem[]
  mealSchedules?: RecipePlanMealSchedule[]
}

/** 审批日志条目 */
export interface RecipePlanAuditLogItem {
  id: number
  planId: number
  round: number
  action: string
  actionName: string
  operatorName: string
  remark: string
  createdAt: string
}

/** 菜谱计划表单 */
export interface RecipePlanForm {
  planDate: string
  startDate?: string
  endDate?: string
  orgId?: number
  orgName?: string
  mealType?: string
  expectedCount?: number
  targetGroup?: string
  healthStatus?: string[]
  dietRestrictions?: string
  aiNutritionAssessment?: string
  remark?: string
  recipes?: RecipePlanFormItem[]
  mealSchedules: RecipePlanMealFormItem[]
  // ============= 用餐偏好相关字段 =============
  /** 口味偏好（逗号分隔） */
  flavorPreferences?: string
  /** 辣度级别（1-5） */
  spicyLevel?: number
  /** 禁忌食材ID列表（逗号分隔） */
  avoidIngredientIds?: string
  /** 饮食偏好标签（逗号分隔） */
  dietTags?: string
}

/** 菜谱计划表单明细项 */
export interface RecipePlanFormItem {
  recipeId: number
  plannedServings: number
  remark?: string
}

export interface RecipePlanMealFormItem {
  mealKey?: string
  mealType: string
  mealName?: string
  expectedCount: number
  sortOrder?: number
  recipes: RecipePlanFormItem[]
}

export interface RecipePlanFormSubmitPayload extends RecipePlanForm {
  adjustReason?: string
  adjustType?: string
  afterData?: string
  adjustItems?: AdjustItem[]
}

/** 库存校验结果 */
export interface StockValidation {
  passed: boolean
  message: string
  riskStatus?: StockRiskStatus
  riskStatusName?: string
  shortageItems?: ShortageItem[]
  materialStockStatuses?: MaterialStockStatus[]
}

export type StockRiskStatus = 'normal' | 'warning' | 'expired' | 'shortage' | 'unknown'

/** 缺货项 */
export interface ShortageItem {
  materialId: number
  materialName: string
  requiredQuantity: number
  availableStock: number
  shortageQuantity: number
  unit: string
  restockSuggestion?: string
}

/** 物料库存状态 */
export interface MaterialStockStatus {
  materialId: number
  materialName: string
  requiredQuantity: number
  availableStock: number
  shortageQuantity?: number
  unit: string
  stockStatus: 'sufficient' | 'shortage'
  expiryStatus: 'normal' | 'warning' | 'expired'
  nearestExpiryDate?: string
  daysToExpiry?: number
  restockSuggestion?: string
}

/** AI营养评估结果 */
export interface AINutritionAssessment {
  overallScore: number
  grade: string
  assessment: string
  aiOptimizationSuggestions?: string
  aiStatus?: 'success' | 'not_configured' | 'failed'
  aiStatusMessage?: string
  totalCalories: number
  totalProtein: number
  totalCarbohydrate: number
  totalFat: number
  totalSodium: number
  totalFiber: number
  avgCalories: number
  avgProtein: number
  avgCarbohydrate: number
  avgFat: number
  passRate: number
  servingCount: number
  dietStructure: {
    proteinRatio: number
    carbohydrateRatio: number
    fatRatio: number
    evaluation: string
  }
  nutritionComparisons: NutritionComparison[]
  suggestions: string[]
}

/** 营养目标对比 */
export interface NutritionComparison {
  nutrientName: string
  actualValue?: number
  targetValue?: number
  status?: string
  percentage?: number
  comparisonStatus?: string
  perCapitaAmount?: number
  targetAmount?: number
  achievementRate?: number
  progressPercent?: number
}

/** 餐次选项 */
export const MEAL_TYPE_OPTIONS = [
  { value: 'breakfast', label: '早餐' },
  { value: 'lunch', label: '午餐' },
  { value: 'dinner', label: '晚餐' },
  { value: 'supper', label: '宵夜' },
  { value: 'custom', label: '自定义餐次' }
]

/** 计划状态选项 */
export const PLAN_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'pending', label: '待审核' },
  { value: 'approved', label: '已审核' },
  { value: 'rejected', label: '已拒绝' },
  { value: 'cooking', label: '烹饪中' },
  { value: 'completed', label: '已完成' }
]

/** 计划状态映射 */
export const PLAN_STATUS_MAP: Record<string, string> = {
  draft: '草稿',
  pending: '待审核',
  approved: '已审核',
  rejected: '已拒绝',
  cooking: '烹饪中',
  completed: '已完成'
}

/** 库存风险状态映射 */
export const STOCK_RISK_STATUS_MAP: Record<StockRiskStatus, string> = {
  normal: '正常',
  warning: '临期预警',
  expired: '已过期',
  shortage: '库存不足',
  unknown: '待人工确认'
}

/** 餐次映射 */
export const MEAL_TYPE_MAP: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  supper: '宵夜',
  custom: '自定义餐次',
  multi: '多餐次'
}

/** 菜谱计划调整申请 */
export interface RecipePlanAdjustment {
  id: number
  adjustCode?: string
  planId: number
  planCode: string
  planDate: string
  adjustType: string
  adjustTypeName: string
  adjustReason: string
  status: string
  statusName: string
  appliedByName: string
  appliedAt: string
  auditedByName?: string
  auditedAt?: string
  createdAt: string
}

/** 菜谱计划调整申请详情 */
export interface RecipePlanAdjustmentDetail extends RecipePlanAdjustment {
  beforeData: string
  afterData: string
  auditRemark?: string
  adjustItems: AdjustItem[]
}

/** 调整项 */
export interface AdjustItem {
  fieldName: string
  fieldLabel: string
  beforeValue: string
  afterValue: string
}

/** 调整申请状态选项 */
export const ADJUSTMENT_STATUS_OPTIONS = [
  { value: 'pending', label: '待调整审核' },
  { value: 'approved', label: '调整已审核' },
  { value: 'rejected', label: '调整已驳回' }
]

/** 调整申请状态映射 */
export const ADJUSTMENT_STATUS_MAP: Record<string, string> = {
  pending: '待调整审核',
  approved: '调整已审核',
  rejected: '调整已驳回'
}

/** 调整类型映射 */
export const ADJUST_TYPE_MAP: Record<string, string> = {
  add: '新增菜谱',
  remove: '移除菜谱',
  modify: '调整计划'
}

/** 目标人群选项 */
export const TARGET_GROUP_OPTIONS = [
  { value: 'adult', label: '普通成人', description: '健康成年人' },
  { value: 'elderly', label: '老年人', description: '60岁以上老人，需易消化、低盐低脂' },
  { value: 'child', label: '儿童', description: '3-12岁儿童，需高蛋白、高钙' },
  { value: 'teenager', label: '青少年', description: '13-18岁青少年，需高能量、高蛋白' },
  { value: 'patient', label: '病人', description: '需特殊饮食照护' },
  { value: 'worker', label: '体力劳动者', description: '需高能量、高蛋白' }
]

/** 目标人群映射 */
export const TARGET_GROUP_MAP: Record<string, string> = {
  adult: '普通成人',
  elderly: '老年人',
  child: '儿童',
  teenager: '青少年',
  patient: '病人',
  worker: '体力劳动者'
}

/** 健康状况选项 */
export const HEALTH_STATUS_OPTIONS = [
  { value: 'diabetes', label: '糖尿病', description: '需低糖、低GI饮食' },
  { value: 'hypertension', label: '高血压', description: '需低盐、低脂饮食' },
  { value: 'hyperlipidemia', label: '高血脂', description: '需低脂、低胆固醇饮食' },
  { value: 'obesity', label: '肥胖', description: '需低热量、高纤维饮食' },
  { value: 'gout', label: '痛风', description: '需低嘌呤饮食' },
  { value: 'kidney_disease', label: '肾病', description: '需低蛋白、低盐饮食' },
  { value: 'stomach_disease', label: '胃病', description: '需易消化、温和饮食' },
  { value: 'anemia', label: '贫血', description: '需高铁、高蛋白饮食' }
]

/** 健康状况映射 */
export const HEALTH_STATUS_MAP: Record<string, string> = {
  diabetes: '糖尿病',
  hypertension: '高血压',
  hyperlipidemia: '高血脂',
  obesity: '肥胖',
  gout: '痛风',
  kidney_disease: '肾病',
  stomach_disease: '胃病',
  anemia: '贫血'
}

/** 口味偏好选项 */
export const FLAVOR_PREFERENCE_OPTIONS = [
  { value: 'spicy', label: '喜辣', description: '喜欢辛辣口味' },
  { value: 'sweet', label: '喜甜', description: '喜欢甜味' },
  { value: 'sour', label: '喜酸', description: '喜欢酸味' },
  { value: 'salty', label: '喜咸', description: '喜欢咸味' },
  { value: 'light', label: '清淡', description: '喜欢清淡口味' },
  { value: 'numb', label: '麻辣', description: '喜欢麻辣口味' }
]

/** 辣度级别选项 */
export const SPICY_LEVEL_OPTIONS = [
  { value: 0, label: '不辣' },
  { value: 2, label: '微辣' },
  { value: 3, label: '小辣' },
  { value: 4, label: '中辣' },
  { value: 5, label: '特辣' }
]

/** 饮食偏好标签选项 */
export const DIET_TAG_OPTIONS = [
  { value: 'vegetarian', label: '素食', description: '不吃肉类' },
  { value: 'lowfat', label: '低脂', description: '低脂肪饮食' },
  { value: 'lowsugar', label: '低糖', description: '低糖饮食（适合糖尿病）' },
  { value: 'highprotein', label: '高蛋白', description: '高蛋白饮食' },
  { value: 'highfiber', label: '高纤维', description: '高纤维饮食' }
]

/** AI推荐结果 */
export interface AIRecommendResult {
  recipes: Recipe[]
  totalEstimatedCost: number
  budgetStatus: 'within' | 'near' | 'exceeded'
  budgetWarning?: string
  weeklyPlan?: DailyPlan[]
  recommendReason?: string
}

/** 周计划日菜单谱 */
export interface DailyPlan {
  date: string
  dayOfWeek: string
  mealType: string
  mealTypeName: string
  recipes: Recipe[]
  dailyCost: number
  dailyNutritionScore: number
  recommendation?: string
}

/** 人群营养目标参考值（每人每日） */
export const NUTRITION_TARGETS_BY_GROUP: Record<string, {
  calories: number
  protein: number
  carbohydrate: number
  fat: number
  sodium: number
  fiber: number
}> = {
  adult: { calories: 2000, protein: 65, carbohydrate: 300, fat: 60, sodium: 2000, fiber: 25 },
  elderly: { calories: 1800, protein: 60, carbohydrate: 260, fat: 50, sodium: 1500, fiber: 25 },
  child: { calories: 1600, protein: 50, carbohydrate: 220, fat: 45, sodium: 1500, fiber: 20 },
  teenager: { calories: 2400, protein: 75, carbohydrate: 350, fat: 70, sodium: 2000, fiber: 30 },
  patient: { calories: 1800, protein: 60, carbohydrate: 260, fat: 50, sodium: 1500, fiber: 25 },
  worker: { calories: 2800, protein: 85, carbohydrate: 400, fat: 80, sodium: 2500, fiber: 30 }
}

/** 营养雷达图数据 */
export interface NutritionChartData {
  radarData: { name: string; value: number; target: number; unit: string }[]
  barData: { nutrient: string; actual: number; target: number; status: string }[]
}

/** 菜谱扩展信息（包含AI推荐字段） */
export interface RecipeWithRecommend extends Recipe {
  recommendReason?: string
  recommendPriority?: number
  estimatedCost?: number
}

/** 菜谱计划导入结果 */
export interface PlanImportRecordResult {
  seqNo?: number
  planCode?: string
  planDate?: string
  action: 'created' | 'updated' | 'adjustment_created' | 'skipped_no_change' | 'failed'
  actionName: string
  message: string
}

export interface PlanImportResult {
  total: number
  successCount: number
  createdCount: number
  updatedCount: number
  adjustmentCreatedCount: number
  skippedCount: number
  failCount: number
  hasErrors: boolean
  errorFileUrl: string | null
  records: PlanImportRecordResult[]
}

export const RECIPE_PLAN_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'pending', label: '待审核' },
  { value: 'approved', label: '已审核' },
  { value: 'rejected', label: '已拒绝' },
  { value: 'completed', label: '已完成' }
]

export const RECIPE_PLAN_STATUS_MAP: Record<string, string> = {
  draft: '草稿',
  pending: '待审核',
  approved: '已审核',
  rejected: '已拒绝',
  completed: '已完成'
}

/** 复制计划结果 */
export interface CopyPlanResult {
  newPlanId: number
  newPlanCode: string
  hasAnomalies: boolean
  invalidRecipeCount: number
  anomalyItems: CopyPlanAnomalyItem[]
}

/** 复制计划异常项 */
export interface CopyPlanAnomalyItem {
  recipeId: number
  recipeName: string
  anomalyType: 'deleted' | 'disabled'
  anomalyMessage: string
}

/** 批量操作结果 */
export interface BatchOperationResult {
  batchId: string
  totalCount: number
  successCount: number
  failCount: number
  results: BatchItemResult[]
}

/** 批量操作单项结果 */
export interface BatchItemResult {
  planId: number
  planCode: string
  success: boolean
  failCategory?: 'permission' | 'business_rule' | 'system_error'
  failReason?: string
}
