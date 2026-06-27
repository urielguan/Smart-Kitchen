/** 出库单 */
export interface OutboundOrder {
  id: number
  outboundNo: string
  outboundType: string
  warehouseId: number
  warehouseName: string
  warehouseNames?: string  // 多个仓库名称（逗号分隔）
  requesterId?: number
  requesterName?: string
  targetOrgId?: number
  targetOrgName?: string
  purpose?: string
  sourceOrderId?: number | null  // 来源业务ID；当前后端合同下，领用出库使用菜谱计划ID
  sourceOrderNo?: string          // 来源业务编号；当前后端合同下，领用出库使用菜谱计划编号
  totalAmount: number
  remark?: string
  attachments?: string[]
  status: 'draft' | 'pending' | 'approved' | 'completed'
  submittedAt?: string
  submittedBy?: number
  approvedAt?: string
  approvedBy?: number
  approverName?: string    // 审核人姓名
  executedAt?: string
  executorName?: string
  approveRemark?: string
  submitterName?: string   // 提交人姓名
  orgId: number
  createdAt: string
  updatedAt: string
  itemCount?: number
  items?: OutboundOrderItem[]
}

/** 出库单明细 */
export interface OutboundOrderItem {
  id: number
  outboundId: number
  materialId: number
  materialName: string
  spec?: string
  unit: string
  locationId?: number
  locationName?: string
  quantity: number
  unitCost?: number
  totalCost?: number
  batchNo?: string
  expiryDate?: string
  purpose?: string
  remark?: string
  allocations?: OutboundOrderAllocation[]
}

export interface OutboundOrderAllocation {
  id?: number
  outboundId?: number
  outboundItemId?: number
  sourceStockDetailId: number
  warehouseId: number
  warehouseName?: string
  locationId?: number | null
  locationName?: string
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  quantity: number
  sourceType?: string
}

/** 出库单表单 */
export interface OutboundOrderForm {
  outboundType: string
  warehouseId: number | null
  requesterId?: number | null
  targetOrgId?: number | null
  purpose: string
  sourceOrderId?: number | null  // 来源业务ID；当前后端合同下，领用出库使用菜谱计划ID
  sourceOrderNo?: string          // 来源业务编号；当前后端合同下，领用出库使用菜谱计划编号
  remark?: string
  attachments?: string[]
  items: OutboundOrderItemForm[]
}

/** 出库单明细表单 */
export interface OutboundOrderItemForm {
  materialId: number | null
  materialName: string
  spec?: string
  unit: string
  locationId?: number | null
  batchNo?: string
  quantity: number | null
  unitCost?: number | null
  expiryDate?: string
  purpose?: string
  remark?: string
  allocations?: OutboundOrderAllocationForm[]
}

export interface OutboundOrderAllocationForm {
  sourceStockDetailId: number
  warehouseId: number
  locationId?: number | null
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  quantity: number
  sourceType?: string
}

export interface OutboundSourceOrderOption {
  id: number
  orderNo: string
  orgId?: number | null
  orgName?: string
}

export interface OutboundTypeDictionaryOption {
  typeId: number
  typeCode: string
  typeName: string
  status: string
  sortOrder: number
  typeSource: string
  sourceRequirementText: string
  requiresSourceBiz: boolean
  approvalMode: string
  supportsAiSuggestion: boolean
}

export interface OutboundTypeSelectableOption {
  value: string
  label: string
  status: string
  sortOrder: number
  typeSource: string
  sourceRequirementText: string
  requiresSourceBiz: boolean
  approvalMode: string
  supportsAiSuggestion: boolean
}

export interface OutboundImportResultError {
  rowNumber: number
  field: string
  reason: string
}

export interface OutboundImportTask {
  taskNo: string
  taskStatus: string
  totalCount: number
  successCount: number
  failureCount: number
  canResume?: boolean
  canTerminate?: boolean
  errorFileName?: string | null
  errors?: OutboundImportResultError[]
}

export interface OutboundImportResult {
  totalCount: number
  successCount: number
  failureCount: number
  partialSuccess: boolean
  errors: OutboundImportResultError[]
  errorFileName?: string | null
  taskNo?: string
  taskStatus?: string
  task?: OutboundImportTask | null
}

export interface OutboundSuggestionPreviewRequest {
  orderId?: number | null
  warehouseScopeType?: string
  details: OutboundSuggestionPreviewDetailRequest[]
}

export interface OutboundSuggestionPreviewDetailRequest {
  detailId?: number | null
  lineNo: number
  materialId: number
  materialName?: string
  specName?: string
  requestQty: number
  fixedWarehouseId?: number | null
  fixedLocationId?: number | null
}

export interface OutboundSuggestionPreviewResult {
  success: boolean
  generateTime: string
  ruleVersion: string
  summary: {
    detailCount: number
    fullMatchedCount: number
    partialMatchedCount: number
    failedCount: number
  }
  details: OutboundSuggestionPreviewDetail[]
  warnings: string[]
}

export interface OutboundSuggestionPreviewDetail {
  detailId?: number | null
  lineNo: number
  materialId: number
  materialName?: string
  specName?: string
  requestQty: number
  matchedQty: number
  unmatchedQty: number
  suggestStatus: string
  message: string
  suggestions: OutboundSuggestionAllocation[]
  warnings: string[]
}

export interface OutboundSuggestionAllocation {
  sourceStockDetailId: number
  warehouseId: number
  warehouseName?: string
  locationId?: number | null
  locationName?: string
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  remainingShelfLifeDays?: number | null
  availableQty: number
  suggestQty: number
  reason: string
}

export interface OutboundSuggestionRevalidateRequest {
  orderId?: number | null
  details: OutboundSuggestionRevalidateDetailRequest[]
}

export interface OutboundSuggestionRevalidateDetailRequest {
  detailId?: number | null
  lineNo?: number | null
  materialId?: number | null
  specName?: string
  requestQty: number
  allocations: OutboundSuggestionRevalidateAllocationRequest[]
}

export interface OutboundSuggestionRevalidateAllocationRequest {
  sourceStockDetailId: number
  warehouseId?: number | null
  locationId?: number | null
  batchNo?: string
  suggestQty: number
}

export interface OutboundSuggestionRevalidateResult {
  valid: boolean
  result: string
  details: Array<{
    detailId?: number | null
    lineNo?: number | null
    valid: boolean
    result: string
    message: string
  }>
  warnings: string[]
}

/** 查询参数 */
export interface OutboundOrderQuery {
  pageNum?: number
  pageSize?: number
  outboundNo?: string
  outboundType?: string
  status?: string
  warehouseId?: number
  startDate?: string
  endDate?: string
}

/** 统计数据 */
export interface OutboundOrderStatistics {
  totalCount: number
  draftCount: number
  pendingCount: number
  approvedCount: number
  completedCount: number
  thisMonthAmount: number
}
