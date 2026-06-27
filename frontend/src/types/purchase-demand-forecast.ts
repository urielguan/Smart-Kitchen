export type PurchaseDemandForecastDimension = 'daily' | 'weekly'

export interface PurchaseDemandForecastExplanationFactor {
  label: string
  value: string
  description: string
}

export interface PurchaseDemandForecastItemRecord {
  id: number
  materialId: number
  materialName: string
  materialSpec: string
  unit: string
  currentInventoryQty: number
  historicalPlanAvgQty: number
  historicalOrderAvgQty: number
  recipeDemandQty: number
  forecastDemandQty?: number
  safetyStockQty?: number
  avgDailyDemandQty?: number
  reviewPeriodDays?: number
  reorderPointQty?: number
  targetStockQty?: number
  inventoryPositionQty?: number
  theoreticalSuggestedQty?: number
  suggestedQty: number
  confidenceLowerQty: number
  confidenceUpperQty: number
  confidenceRate: number
  estimatedUnitPrice: number
  estimatedAmount: number
  priority: string
  modelType?: string
  materialSegment?: string
  forecastBasis: string
  explanationSummary?: string
  explanationDetail?: string
  explanationTemplateCode?: string
  explanationTitle?: string
  warningLevel?: string
  warningMessage?: string
  approvalNote?: string
  manualReviewRequired?: boolean
  anomalyCodes?: string
  explanationSortScore?: number
  explanationFactors?: PurchaseDemandForecastExplanationFactor[]
  anomalyFlags?: string
  actualConsumptionQty?: number
  absError?: number
  ape?: number
  biasQty?: number
  recipeDriveRatio?: number
  demandActiveRatio?: number
  demandCv?: number
  activitySensitivity?: number
  serviceLevel?: number
  leadTimeDays?: number
  effectiveLeadTimeDays?: number
  minOrderQty?: number
  packSize?: number
  maxAllowedStockQty?: number
  maxCoverageDays?: number
  recommendedSupplierId?: number | null
  recommendedSupplierName?: string
  supplierScore?: number
  supplierFillRate?: number
  supplierOnTimeRate?: number
  orderNow?: boolean
  orderAction?: string
  shortageCost?: number
  holdingCost?: number
  expiryRiskCost?: number
  orderProcessingCost?: number
  purchasePriceCost?: number
  totalCost?: number
  phaseThreeRiskFlags?: string
  evaluationStatus?: string
  occupiedLinkQty?: number
  availableLinkQty?: number
  materialPlanStatus?: string
}

export interface PurchaseDemandForecastRecord {
  id: number
  forecastNo: string
  forecastName: string
  orgId: number | null
  orgName: string
  dimension: PurchaseDemandForecastDimension
  forecastDays: number
  basisDate: string
  horizonStartDate: string
  horizonEndDate: string
  materialCount: number
  totalSuggestedAmount: number
  calendarFactor: number
  holidayFactor: number
  activityFactor: number
  summaryBasis: string
  generatedBy: string
  generatedAt: string
  evaluationStatus?: string
  evaluatedAt?: string
  wape?: number
  mape?: number
  biasRate?: number
  stockoutRate?: number
  oversupplyRate?: number
  optimizationVersion?: number
  optimizationScore?: number
  explanationSummary?: string
  approvalSummary?: string
  reorderTriggeredCount?: number
  riskItemCount?: number
  supplierRecommendedCount?: number
  manualReviewCount?: number
  warningItemCount?: number
  totalOptimizationCost?: number
  materialPlanStatus?: string
  items: PurchaseDemandForecastItemRecord[]
}

export interface PurchaseDemandForecastDashboardSegmentSummary {
  materialSegment: string
  modelType: string
  materialCount: number
  totalSuggestedQty: number
  totalActualQty: number
  totalOptimizationCost?: number
  wape: number
  biasRate: number
}

export interface PurchaseDemandForecastDashboardOptimizationSummary {
  materialSegment: string
  modelType: string
  versionNo: number
  score: number
  wape: number
  stockoutRate: number
  oversupplyRate: number
  optimizedAt: string
  rollbackApplied?: boolean
  rollbackReason?: string
}

export interface PurchaseDemandForecastDashboardSupplierSummary {
  supplierId: number | null
  supplierName: string
  recommendCount: number
  avgSupplierScore: number
  avgEffectiveLeadTimeDays: number
}

export interface PurchaseDemandForecastDashboard {
  orgId: number | null
  orgName: string
  pendingEvaluationCount: number
  evaluatedForecastCount: number
  wape: number
  mape: number
  biasRate: number
  stockoutRate: number
  oversupplyRate: number
  optimizedConfigCount: number
  lastOptimizationAt: string
  lastEvaluationAt: string
  reorderTriggeredCount?: number
  riskItemCount?: number
  supplierRecommendedCount?: number
  avgOptimizationCost?: number
  rollbackCount?: number
  lastRollbackAt?: string
  manualReviewCount?: number
  warningItemCount?: number
  segmentSummaries: PurchaseDemandForecastDashboardSegmentSummary[]
  optimizationSummaries: PurchaseDemandForecastDashboardOptimizationSummary[]
  supplierSummaries: PurchaseDemandForecastDashboardSupplierSummary[]
}

export interface PurchaseDemandForecastMaterialLinkageItem {
  forecastDetailId: number
  materialId: number
  materialName: string
  materialSpec: string
  unit: string
  originalQty: number
  occupiedQty: number
  availableQty: number
  materialPlanStatus: string
}

export interface PurchaseDemandForecastMaterialLinkage {
  forecastId: number
  forecastNo: string
  orgId: number
  orgName: string
  materialPlanStatus: string
  items: PurchaseDemandForecastMaterialLinkageItem[]
}

export interface PurchaseDemandForecastLinkedPlanItemRecord {
  id: number
  materialId: number
  materialName: string
  materialSpec: string
  unit: string
  quantity: number
  estimatedUnitPrice: number
  estimatedAmount: number
  remark: string
}

export interface PurchaseDemandForecastLinkedPlanRecord {
  id: number
  planNo: string
  planName: string
  status: string
  planDate: string
  totalAmount: number
  createdBy: string
  createdAt: string
  items: PurchaseDemandForecastLinkedPlanItemRecord[]
}

export interface PurchaseDemandForecastQuery {
  pageNum?: number
  pageSize?: number
  orgId?: number
  keyword?: string
  dimension?: PurchaseDemandForecastDimension | ''
}

export interface PurchaseDemandForecastGeneratePayload {
  orgId?: number
  dimension: PurchaseDemandForecastDimension
}

export interface PurchaseDemandForecastPlanPrefillItem {
  forecastDetailId: number
  materialId: number
  materialName: string
  materialSpec: string
  unit: string
  quantity: number
  estimatedUnitPrice: number
  estimatedAmount: number
}

export interface PurchaseDemandForecastPlanPrefill {
  forecastId: number
  forecastNo: string
  planName: string
  orgId: number
  orgName: string
  planDate: string
  budgetAmount: number
  createdBy: string
  relatedDocument: string
  items: PurchaseDemandForecastPlanPrefillItem[]
}
