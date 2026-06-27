export type DictCategoryType =
  | 'recipe_category'
  | 'warehouse_type'
  | 'material_category'
  | 'supplier_type'
  | 'device_type'
  | 'org_type'
  | 'employee_position'

export type DictCategorySourceType = 'system' | 'custom'
export type DictCategoryAreaCoefficientSource = 'system' | 'manual' | 'ai'
export type DictCategoryAreaCoefficientEffectScope = 'subsequent_only' | 'manual_recalc'
export type DictCategoryAreaCoefficientRecalcStatus =
  | 'not_applicable'
  | 'pending'
  | 'running'
  | 'completed'
  | 'failed'

export interface DictCategoryMeta {
  categoryType: DictCategoryType
  categoryName: string
  itemCount: number
  systemCount: number
  customCount: number
}

export interface DictCategoryItem {
  id: number
  categoryType: DictCategoryType
  categoryName: string
  dictCode: string
  dictName: string
  dictValue: string
  sourceType: DictCategorySourceType
  sortOrder: number
  status: 'active' | 'inactive'
  areaCoefficient?: number | null
  areaCoefficientSource?: DictCategoryAreaCoefficientSource | null
  referenceCount: number
  system: boolean
  createdAt: string
  updatedAt: string
}

export interface DictCategoryDetail extends DictCategoryItem {
  orgId: number
  tenantId: number
  createdBy: number | null
  updatedBy: number | null
  remark: string | null
  aiSuggestedAreaCoefficient?: number | null
  aiSuggestionReason?: string | null
  aiSuggestionGeneratedAt?: string | null
}

export interface DictCategoryOption {
  id: number
  categoryType: DictCategoryType
  dictCode: string
  dictName: string
  value: string
  status: 'active' | 'inactive'
  system: boolean
  sortOrder: number
  areaCoefficient?: number | null
}

export interface DictCategoryQuery {
  pageNum: number
  pageSize: number
  categoryType: DictCategoryType
  keyword?: string
  sourceType?: DictCategorySourceType | ''
  status?: 'active' | 'inactive' | ''
}

export interface DictCategoryForm {
  dictCode: string
  dictName: string
  sortOrder: number
  areaCoefficient?: number | null
  areaCoefficientSource?: DictCategoryAreaCoefficientSource | null
  aiSuggestedAreaCoefficient?: number | null
  aiSuggestionReason?: string | null
  aiSuggestionGeneratedAt?: string | null
  areaCoefficientImpactConfirmed?: boolean
  areaCoefficientEffectScope?: DictCategoryAreaCoefficientEffectScope
  lastKnownUpdatedAt?: string | null
  remark?: string
}

export interface DictCategoryAreaSuggestion {
  areaCoefficient: number
  reason: string
  generatedAt: string
}

export interface DictCategoryAreaCoefficientHistory {
  id: number
  correctionVersion: number
  oldAreaCoefficient: number
  newAreaCoefficient: number
  oldAreaCoefficientSource?: DictCategoryAreaCoefficientSource | null
  newAreaCoefficientSource?: DictCategoryAreaCoefficientSource | null
  impactScope: DictCategoryAreaCoefficientEffectScope
  impactAcknowledged: boolean
  impactAcknowledgedAt?: string | null
  recalcStatus: DictCategoryAreaCoefficientRecalcStatus
  recalcTaskId?: number | null
  recalcTaskNo?: string | null
  recalcProgressPercent?: number | null
  recalcResultMessage?: string | null
  recalcCompletedAt?: string | null
  operatorId?: number | null
  operatorName?: string | null
  currentVersion: boolean
  recalcAvailable: boolean
  createdAt?: string | null
}

export interface DictCategoryAreaCoefficientRecalcDetail {
  id: number
  detailCode: string
  detailName: string
  detailType: string
  status: DictCategoryAreaCoefficientRecalcStatus
  affectedRecordCount: number
  quantityTotal: number
  oldAreaTotal: number
  newAreaTotal: number
  deltaAreaTotal: number
  detailMessage?: string | null
  snapshotPayload?: string | null
}

export interface DictCategoryAreaCoefficientRecalcTask {
  id: number
  taskNo: string
  correctionId: number
  correctionVersion: number
  oldAreaCoefficient: number
  newAreaCoefficient: number
  status: DictCategoryAreaCoefficientRecalcStatus
  progressPercent: number
  totalSteps: number
  completedSteps: number
  successCount: number
  failureCount: number
  resultMessage?: string | null
  startedBy?: number | null
  startedByName?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  details: DictCategoryAreaCoefficientRecalcDetail[]
}
