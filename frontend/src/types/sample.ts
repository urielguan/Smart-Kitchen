/** 留样记录查询参数 */
export interface SampleRecordQuery extends Partial<PageQuery> {
  status?: string
  sampleDate?: string
  sampleDateEnd?: string
  mealType?: string
  menuName?: string
  orgId?: number
  showRollbackIsolated?: boolean
}

/** 留样操作类型 */
export type SampleOperationType =
  | 'register'
  | 'edit'
  | 'dispose'
  | 'manual_disposal_supplement'
  | 'void'
  | 'archive'
  | 'ai_evaluate'

/** 留样操作锁 */
export interface SampleOperationLock {
  locked: boolean
  lockToken?: string | null
  operationType?: SampleOperationType | null
  operationTypeLabel?: string | null
  operatorId?: number | null
  operatorName?: string | null
  ownedByCurrentUser?: boolean
  acquiredAt?: string | null
  expiresAt?: string | null
}

/** 留样看板数据 */
export interface SampleDashboard {
  totalSamples: number
  pendingDisposal: number
  disposed: number
  overdue: number
  todaySampled: number
}

/** 留样记录列表项 */
export interface SampleRecord {
  id: number
  sampleNo: string
  taskId?: number | null
  taskNo?: string | null
  sourceLabel?: string | null
  recordOriginType?: string | null
  disposalSourceType?: string | null
  disposalSourceLabel?: string | null
  taskStatus?: string | null
  menuName: string
  sampleDate: string
  mealType: string
  sampleWeight?: number | null
  aiQualityScore?: number | null
  storageLocation?: string | null
  status: string
  rollbackIsolated?: boolean
  operationLock?: SampleOperationLock | null
  sampledAt?: string | null
  disposalDueAt?: string | null
  createdAt?: string | null
}

/** 留样记录详情 */
export interface SampleRecordDetail extends SampleRecord {
  menuId?: number | null
  sampleImages?: string[] | null
  aiAnalysisResult?: AiEvaluateResult | null
  storageTemp?: number | null
  sampledBy?: number | null
  disposalBy?: number | null
  sampledByName?: string | null
  disposalByName?: string | null
  disposalAt?: string | null
  disposalImages?: string[] | null
  disposalRemark?: string | null
  orgId?: number | null
  tenantId?: number | null
  updatedAt?: string | null
  lockStatus?: 'none' | 'investigation' | 'accident' | null
  traceBatchId?: string | null
  foodSafetyLedgerNo?: string | null
  supplementReason?: string | null
  supplementRemark?: string | null
  disposalSupplementScene?: string | null
  disposalSupplementRemark?: string | null
  disposalSupplementedAt?: string | null
  disposalSupplementedBy?: number | null
  disposalSupplementedByName?: string | null
  evidenceChainId?: string | null
  voidReason?: string | null
  archivedAt?: string | null
  evaluatedAt?: string | null
  rollbackIsolatedAt?: string | null
  rollbackIsolationReason?: string | null
  traceChain?: TraceChain | null
}

/** 新增留样记录请求体 */
export interface SampleRecordCreatePayload {
  taskId?: number | null
  menuId?: number | null
  menuName?: string
  sampleDate?: string
  mealType?: string
  sampleWeight?: number | null
  sampleImages?: string[] | null
  storageLocation?: string | null
  storageTemp?: number | null
  sampledBy?: number | null
  orgId?: number | null
  tenantId?: number | null
}

/** 手工新增可选烹饪任务 */
export interface SampleAvailableCookTask {
  id: number
  taskNo: string
  menuId?: number | null
  menuName: string
  sampleDate: string
  mealType: string
  taskStatus: string
  completedAt?: string | null
}

/** 历史补录任务查询参数 */
export interface SampleHistoryTaskQuery {
  businessDate: string
  keyword?: string
}

/** 留样登记请求体 */
export interface SampleRecordRegisterPayload {
  sampleWeight?: number | null
  sampleImages?: string[] | null
  storageLocation?: string | null
  storageTemp?: number | null
  sampledBy?: number | null
}

/** 销样请求体 */
export interface DisposalPayload {
  disposalImages: string[]
  disposalRemark?: string
}

/** 销样手工补录请求体 */
export interface ManualDisposalSupplementPayload extends DisposalPayload {
  supplementScene: 'system_missing' | 'interface_sync_exception' | 'device_offline' | 'history_migration_fix' | 'ops_closure_repair'
  supplementRemark: string
}

/** 销样提醒记录 */
export interface DisposalReminderRecord {
  id: number
  sampleNo: string
  taskId?: number | null
  menuName: string
  sampleDate: string
  sampledAt?: string | null
  storageLocation?: string | null
  disposalDueAt?: string | null
  status: string
  isOverdue: boolean
  remainHours: number
  operationLock?: SampleOperationLock | null
}

/** AI评估结果 */
export interface AiEvaluateResult {
  id: number
  finalScore: number
  starLevel: number
  dimensionScores: {
    colorScore: number
    shapeScore: number
    donenessScore: number
  }
  dimensionAnalysis: {
    colorAnalysis: string
    shapeAnalysis: string
    donenessAnalysis: string
  }
  suggestions: string[]
  riskLevel: 'low' | 'medium' | 'high'
}

/** 记录编辑请求体 */
export interface SampleRecordUpdatePayload {
  sampleWeight?: number | null
  storageLocation?: string | null
  storageTemp?: number | null
  sampleImages?: string[] | null
}

/** 操作日志 */
export interface OperationLog {
  id: number
  recordId: number
  action: string
  actionName: string
  operatorId?: number
  operatorName?: string
  content?: string
  terminal?: string
  createdAt: string
}

/** 追溯链 */
export interface TraceChain {
  planCode?: string
  taskNo?: string
  chefName?: string
  sampleNo?: string
  sampledByName?: string
  disposalByName?: string
}

/** 历史留样补录请求体 */
export interface SampleHistoryRecordCreatePayload {
  taskId: number
  recordOriginType: 'manual_history' | 'offline_delayed'
  supplementReason: string
  supplementRemark: string
  sampleWeight: number
  sampleImages?: string[] | null
  storageLocation: string
  storageTemp?: number | null
  sampledBy: number
}
