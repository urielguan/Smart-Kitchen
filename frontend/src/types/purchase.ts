export type PurchaseOrderStatus =
  | 'pending_submit'
  | 'pending_approve'
  | 'approved'
  | 'rejected'
  | 'pending_void_approve'
  | 'voided'
  | 'delivering'
  | 'pending_receipt'
  | 'received'
  | 'inspected'
  | 'completed'
  | 'closed'
  | 'cancelled'

export type PurchaseOrderMaintenanceSourceType = 'manual' | 'third_party'

export type PurchaseOrderLogisticsStatus = 'pending' | 'shipped' | 'in_transit' | 'arrived'

export type PurchaseOrderSceneIntegrationLogType = 'sync' | 'callback'

export type PurchaseOrderInboundStatus = 'draft' | 'pending' | 'approved' | 'completed' | 'rejected' | 'cancelled'

export interface PurchaseOrderAttachment {
  id?: number
  name: string
  size?: string
  url: string
  sortOrder?: number
}

export interface PurchaseOrderRelatedPlan {
  id: number
  planNo: string
  planName: string
  orgName?: string
}

export interface PurchaseOrderItem {
  id?: number
  planItemId?: number | null
  planId?: number | null
  planNo?: string
  planName?: string
  planOrgName?: string
  materialId: number | null
  materialName: string
  spec: string
  unit: string
  quantity: number | null
  unitPrice?: number | null
  unitCost?: number | null
  subtotal: number
  receivedQty: number
  inboundQty?: number
  remainingInboundQty?: number
  remark: string
}

export interface PurchaseOrderLinkedInboundRecord {
  inboundOrderId: number
  inboundNo: string
  inboundDate: string
  status: PurchaseOrderInboundStatus | string
  postStatus?: string
  materialName: string
  spec: string
  unit: string
  inboundQuantity: number
  operatorName: string
  createdAt: string
}

export interface PurchaseOrderRecord {
  id: number
  orderNo: string
  supplierId: number | null
  supplierName: string
  orgId: number | null
  orgName: string
  buyerName: string
  createdById: number | null
  createdBy: string
  orderDate: string
  expectedArrival: string
  totalAmount: number
  logisticsCompany: string
  logisticsTrackingNo: string
  logisticsStatus: PurchaseOrderLogisticsStatus | ''
  logisticsRemark: string
  logisticsSourceType: PurchaseOrderMaintenanceSourceType
  logisticsSyncPayload: string
  shippedAt: string
  arrivedAt: string
  logisticsAttachmentName: string
  logisticsAttachmentUrl: string
  logisticsAttachments: PurchaseOrderAttachment[]
  inspectionReportNo: string
  inspectionResult: string
  inspectionAgency: string
  inspectionAt: string
  inspectionRemark: string
  inspectionSourceType: PurchaseOrderMaintenanceSourceType
  inspectionSyncPayload: string
  inspectionAttachmentName: string
  inspectionAttachmentUrl: string
  inspectionAttachments: PurchaseOrderAttachment[]
  inspectionFilled: boolean
  attachmentName: string
  attachmentUrl: string
  status: PurchaseOrderStatus
  deleted: boolean
  remark: string
  createdAt: string
  updatedAt: string
  auditBy: string | null
  auditAt: string | null
  auditRemark: string
  voidReason: string
  voidRequestedBy: string | null
  voidRequestedAt: string | null
  voidAuditBy: string | null
  voidAuditAt: string | null
  voidAuditRemark: string
  traceBatchId: string
  traceOrigin: string
  traceRemark: string
  traceSourceType: PurchaseOrderMaintenanceSourceType
  traceSyncPayload: string
  traceAttachmentName: string
  traceAttachmentUrl: string
  traceabilityAttachments: PurchaseOrderAttachment[]
  traceabilityFilled: boolean
  relatedPlanIds: number[]
  relatedPlans: PurchaseOrderRelatedPlan[]
  items: PurchaseOrderItem[]
}

export interface PurchaseOrderStatistics {
  total: number
  pending: number
  approved: number
  totalAmount: number
}

export interface PurchaseOrderSupplierOption {
  id: number
  name: string
  contactName: string
  contactPhone: string
  disabled?: boolean
  unavailableReason?: string
}

export interface PurchaseOrderMaterialOption {
  id: number
  name: string
  unit: string
  spec: string
  referencePrice: number
}

export interface PurchaseOrderSelectablePlan {
  id: number
  planNo: string
  planName: string
  orgId: number
  orgName: string
  remainingQuantity: number
  remainingAmount: number
}

export interface PurchaseOrderPlanItemOption {
  id: number
  planId: number
  planNo: string
  planName: string
  planOrgName?: string
  materialId: number
  materialName: string
  spec: string
  unit: string
  planQuantity: number
  orderedQuantity: number
  remainingQuantity: number
  unitPrice: number
  remark: string
}

export interface PurchaseOrderQuery {
  pageNum?: number
  pageSize?: number
  orgId?: number
  keyword?: string
  orderNo?: string
  supplierName?: string
  status?: PurchaseOrderStatus | ''
  dateStart?: string
  dateEnd?: string
}

export interface PurchaseOrderFormPayload {
  orderNo?: string
  orgId: number
  supplierId: number
  orderDate: string
  expectedArrival: string
  attachmentName?: string
  attachmentUrl?: string
  remark?: string
  clearAttachment?: boolean
  status: Extract<PurchaseOrderStatus, 'pending_submit' | 'pending_approve'>
  relatedPlanIds?: number[]
  items: Array<{
    planItemId?: number
    materialId: number
    spec?: string
    quantity: number
    unitPrice: number
    remark?: string
  }>
}

export interface PurchaseOrderAuditPayload {
  status: Extract<PurchaseOrderStatus, 'approved' | 'rejected'>
  remark?: string
}

export interface PurchaseOrderReverseAuditPayload {
  reason: string
}

export interface PurchaseOrderReverseAuditResult {
  affectedInboundCount?: number
  affectedInboundNos?: string[]
}

export interface PurchaseOrderVoidApplyPayload {
  reason: string
}

export interface PurchaseOrderVoidAuditPayload {
  approved: boolean
  remark?: string
}

export interface PurchaseOrderLogisticsPayload {
  company?: string
  trackingNo?: string
  logisticsStatus: PurchaseOrderLogisticsStatus
  shippedAt?: string
  arrivedAt?: string
  remark?: string
  sourceType?: PurchaseOrderMaintenanceSourceType
  syncPayload?: string
  integrationConfigId?: number
  integrationExternalNo?: string
  attachments?: PurchaseOrderAttachment[]
}

export interface PurchaseOrderInspectionPayload {
  reportNo?: string
  result?: string
  agency?: string
  inspectedAt?: string
  remark?: string
  sourceType?: PurchaseOrderMaintenanceSourceType
  syncPayload?: string
  integrationConfigId?: number
  integrationExternalNo?: string
  attachments?: PurchaseOrderAttachment[]
}

export interface PurchaseOrderTraceabilityPayload {
  traceBatchId?: string
  origin?: string
  remark?: string
  sourceType?: PurchaseOrderMaintenanceSourceType
  syncPayload?: string
  integrationConfigId?: number
  integrationExternalNo?: string
  attachments?: PurchaseOrderAttachment[]
}

export interface PurchaseOrderSceneIntegrationConfigOption {
  id: number
  configName: string
  providerCode: string
  providerName: string
  defaultMode?: PurchaseOrderMaintenanceSourceType | string
  allowDocumentSwitch?: number
  forceThirdParty?: number
  allowManualFallback?: number
  autoCoverEnabled?: number
  callbackEnabled?: number
  syncFrequencyMinutes?: number | null
  externalNoFieldRule?: string
  lastSyncStatus?: string
  lastErrorMessage?: string
  lastSyncAt?: string
}

export interface PurchaseOrderSceneIntegrationBinding {
  bindingId?: number | null
  configId?: number | null
  configName?: string
  providerCode?: string
  providerName?: string
  externalNo?: string
  maintenanceMode?: PurchaseOrderMaintenanceSourceType | string
  modeSource?: string
  modeLocked?: number
  syncStatus?: string
  lastErrorMessage?: string
  firstBindAt?: string
  lastSyncAt?: string
  nextSyncAt?: string
  updatedAt?: string
}

export interface PurchaseOrderSceneIntegrationLog {
  id: number
  logType: PurchaseOrderSceneIntegrationLogType | string
  bindingId?: number | null
  configId?: number | null
  providerCode?: string
  providerName?: string
  externalNo?: string
  status?: string
  triggerType?: string
  taskNo?: string
  message?: string
  errorMessage?: string
  createdAt?: string
}

export interface PurchaseOrderSceneIntegrationLogs {
  binding?: PurchaseOrderSceneIntegrationBinding | null
  syncLogs: PurchaseOrderSceneIntegrationLog[]
  callbackLogs: PurchaseOrderSceneIntegrationLog[]
}

export interface PurchaseOrderSceneIntegrationMeta {
  bizModule: string
  bizScene: string
  bizId: number
  orgId: number
  tenantId: number
  selectedConfigId?: number | null
  defaultMode?: PurchaseOrderMaintenanceSourceType | string
  allowDocumentSwitch?: number
  forceThirdParty?: number
  allowManualFallback?: number
  autoCoverEnabled?: number
  externalNoFieldRule?: string
  currentBinding?: PurchaseOrderSceneIntegrationBinding | null
  configOptions: PurchaseOrderSceneIntegrationConfigOption[]
  recentSyncLogs: PurchaseOrderSceneIntegrationLog[]
  recentCallbackLogs: PurchaseOrderSceneIntegrationLog[]
}

export interface PurchaseOrderSceneIntegrationSyncPayload {
  configId: number
  externalNo: string
  queryOnly?: number
}

export interface PurchaseOrderSceneIntegrationTriggerResult {
  taskId?: number | null
  taskNo?: string
  bindingId?: number | null
  syncStatus?: string
  message?: string
  normalizedPayload?: string
  downloadedFileCount?: number
  binding?: PurchaseOrderSceneIntegrationBinding | null
}
