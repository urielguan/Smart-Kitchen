export interface InboundImportResultError {
  rowNumber: number
  field: string
  reason: string
}

export interface InboundImportResult {
  successCount: number
  failureCount: number
  totalCount: number
  partialSuccess: boolean
  errors: InboundImportResultError[]
  errorFileName?: string | null
}

export interface InboundOrderValidationFieldError {
  lineKey?: string | null
  field?: string | null
  message: string
}

export interface InboundOrderValidationErrorData {
  errorType: string
  globalMessage: string | null
  latestVersion?: number | null
  fieldErrors: InboundOrderValidationFieldError[]
}

export interface InboundAreaValidationPreviewItem {
  lineKey?: string
  warehouseId?: number | null
  locationId?: number | null
  materialId?: number | null
  quantity?: number | null
}

export interface InboundAreaValidationPreviewRequest {
  warehouseId?: number | null
  items: InboundAreaValidationPreviewItem[]
}

export interface InboundAreaValidationItemResult {
  lineKey?: string
  warehouseId?: number | null
  locationId?: number | null
  areaCoefficient?: number | null
  expectedOccupiedArea?: number | null
  currentOccupiedArea?: number | null
  projectedOccupiedArea?: number | null
  locationCapacity?: number | null
  validationResult?: string | null
  message?: string | null
}

export interface InboundAreaValidationLocationSummary {
  warehouseId?: number | null
  locationId?: number | null
  locationName?: string | null
  currentOccupiedArea?: number | null
  expectedIncrementArea?: number | null
  projectedOccupiedArea?: number | null
  locationCapacity?: number | null
  validationResult?: string | null
  hasSkippedItems: boolean
  message?: string | null
}

export interface InboundAreaValidationPreviewResponse {
  itemResults: InboundAreaValidationItemResult[]
  locationSummaries: InboundAreaValidationLocationSummary[]
  hasExceeded: boolean
  hasSkipped: boolean
  globalMessage: string | null
}

export interface InboundOrder {
  id: number
  inboundNo: string
  sourceType: string
  sourceId?: number
  sourceOrderId?: number | null
  sourceOrderNo?: string
  supplierId?: number | null
  supplierName?: string
  orgId?: number | null
  receivingOrgId?: number | null
  receivingOrgName?: string
  warehouseId: number
  warehouseName: string
  warehouseNames?: string
  totalAmount: number
  remark?: string
  attachments?: string[]
  status: 'draft' | 'pending' | 'approved' | 'completed' | 'rejected' | 'cancelled'
  version?: number
  postStatus?: string
  postErrorMessage?: string
  itemCount: number
  submittedAt?: string
  approvedAt?: string
  approveRemark?: string
  createdAt: string
  updatedAt: string
  items?: InboundOrderItem[]
}

export interface InboundOrderItem {
  id: number
  inboundId: number
  materialId: number
  materialName: string
  spec?: string
  unit: string
  warehouseId?: number
  warehouseName?: string
  locationId?: number
  locationName?: string
  quantity: number
  unitCost?: number
  totalCost?: number
  batchNo?: string
  productionDate?: string
  expiryDate?: string
}

export interface InboundOrderItemForm {
  materialId: number | null
  materialName: string
  spec: string
  unit: string
  locationId: number | null
  quantity: number | null
  unitCost: number | null
  batchNo: string
  productionDate: string
  expiryDate: string
}

export interface InboundOrderForm {
  warehouseId: number | null
  sourceType: string
  sourceId?: number | null
  sourceOrderId?: number | null
  sourceOrderNo?: string
  supplierId?: number | null
  supplierName?: string
  orgId?: number | null
  receivingOrgId?: number | null
  remark: string
  attachments?: string[]
  items: InboundOrderItemForm[]
}

export interface InboundSourceOrderOption {
  id: number
  orderNo: string
  supplierId?: number | null
  supplierName?: string
  orgId?: number | null
  receivingOrgId?: number | null
  receivingOrgName?: string
  availableQuantity?: number
  linkedQuantity?: number
}

export interface InboundOrderQuery {
  pageNum?: number
  pageSize?: number
  inboundNo?: string
  warehouseId?: number
  sourceType?: string
  status?: string
  startDate?: string
  endDate?: string
}

export interface InboundOrderStatistics {
  thisMonthTotalCount: number
  thisMonthPendingCount: number
  thisMonthApprovedCount: number
  thisMonthInboundAmount: number
}
