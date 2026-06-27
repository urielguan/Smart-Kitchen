export type PurchasePlanStatus = 'draft' | 'pending' | 'approved' | 'rejected' | 'pending_void_approve' | 'voided'

export interface PurchasePlanItem {
  id: number
  materialId: number | null
  materialName: string
  materialSpec: string
  unit: string
  quantity: number | null
  unitPrice: number | null
  subtotal: number
  orderedQuantity: number
  remark: string
  sourceForecastDetailId?: number | null
  lockMaterialSpec?: boolean
  lockUnitPrice?: boolean
}

export interface PurchaseOrderLinkItem {
  id?: number
  planItemId: number
  materialName: string
  materialSpec: string
  unit: string
  quantity: number
  unitPrice: number
  subtotal: number
  remark?: string
}

export interface PurchaseOrderLinkRecord {
  id: number
  orderNo: string
  createdAt: string
  createdBy: string
  items: PurchaseOrderLinkItem[]
}

export interface PurchasePlanLinkedOrderRecord {
  orderId: number
  orderNo: string
  status: string
  materialName: string
  materialSpec: string
  unit: string
  quantity: number
  operatorName: string
  createdAt: string
}

export interface PurchasePlanAttachment {
  id: number
  name: string
  size: string
  url: string
  sortOrder?: number
}

export interface PurchasePlanRecord {
  id: number
  planNo: string
  planName: string
  orgId: number | null
  orgName: string
  planDate: string
  createdAt: string
  createdBy: string
  budgetAmount: number
  totalAmount: number
  relatedDocument: string
  attachmentName: string
  attachmentUrl: string
  remark: string
  status: PurchasePlanStatus
  deleted: boolean
  auditRemark: string
  auditBy: string | null
  auditAt: string | null
  voidOriginalStatus: Extract<PurchasePlanStatus, 'approved' | 'rejected'> | null
  voidReason: string
  voidRequestedBy: string | null
  voidRequestedAt: string | null
  voidAuditBy: string | null
  voidAuditAt: string | null
  voidAuditRemark: string
  mergeLocked: boolean
  mergeOrderId: number | null
  generatedOrderCount: number
  allItemsGenerated: boolean
  attachments: PurchasePlanAttachment[]
  items: PurchasePlanItem[]
  orderLinks: PurchaseOrderLinkRecord[]
}

export interface PurchasePlanStatistics {
  total: number
  pending: number
  approved: number
  totalBudget: number
}

export interface PurchasePlanQuery {
  pageNum?: number
  pageSize?: number
  orgId?: number
  keyword?: string
  planName?: string
  planNo?: string
  status?: PurchasePlanStatus | ''
}

export interface PurchasePlanFormItemPayload {
  materialId: number
  materialSpec?: string
  quantity: number
  unitPrice: number
  remark?: string
}

export interface PurchasePlanFormPayload {
  planNo?: string
  planName: string
  orgId: number
  planDate: string
  budgetAmount: number
  relatedDocument?: string
  remark?: string
  status: Extract<PurchasePlanStatus, 'draft' | 'pending'>
  attachments?: PurchasePlanAttachment[]
  items: PurchasePlanFormItemPayload[]
}

export interface PurchasePlanAuditPayload {
  status: Extract<PurchasePlanStatus, 'approved' | 'rejected'>
  remark?: string
}

export interface PurchasePlanReverseAuditPayload {
  reason: string
}

export interface PurchasePlanReverseAuditResult {
  affectedOrderCount: number
  affectedOrderNos: string[]
}

export interface PurchasePlanVoidApplyPayload {
  reason: string
}

export interface PurchasePlanVoidAuditPayload {
  approved: boolean
  remark?: string
}

export interface PurchasePlanGenerateOrderPayload {
  supplierId: number
  items: Array<{
    planItemId: number
    quantity: number
    unitPrice: number
    subtotal: number
  }>
}

export interface PurchasePlanMergeGenerateOrderPayload {
  planIds: number[]
}

export interface PurchasePlanMaterialOption {
  id: number
  name: string
  unit: string
  spec: string
  referencePrice: number
}

export type PurchasePlanRelatedDocumentType = 'recipePlan' | 'purchaseDemandForecast'

export interface PurchasePlanRelatedDocumentOption {
  id: number
  documentType: PurchasePlanRelatedDocumentType
  documentTypeLabel: string
  documentNo: string
  title: string
  optionLabel: string
  orgId: number | null
  orgName: string
}

export interface PurchasePlanRelatedDocumentItemPrefill {
  sourceForecastDetailId?: number | null
  materialId: number | null
  materialName: string
  materialSpec: string
  unit: string
  quantity: number
  unitPrice: number
}

export interface PurchasePlanRecipeMaterialLinkageItem {
  materialId: number
  materialName: string
  materialSpec: string
  unit: string
  originalQty: number
  occupiedQty: number
  availableQty: number
  materialPlanStatus: string
}

export interface PurchasePlanRecipeMaterialLinkage {
  recipePlanId: number
  planCode: string
  orgId: number
  orgName: string
  materialPlanStatus: string
  items: PurchasePlanRecipeMaterialLinkageItem[]
}

export interface PurchaseOrderGenerateResult {
  id: number
  orderNo: string
  totalAmount: number
}

export interface SelectablePurchasePlan {
  id: number
  planNo: string
  planName: string
  orgId: number
  orgName: string
  remainingQuantity: number
  remainingAmount: number
}
