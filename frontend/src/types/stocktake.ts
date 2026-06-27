export type StocktakeStatus = 'draft' | 'pending' | 'rejected' | 'completed' | 'voided'

export interface StocktakeOrderListItem {
  id: number
  stocktakeNo: string
  warehouseId: number
  warehouseName?: string
  locationId?: number | null
  locationName?: string
  stocktakeType?: string
  stocktakeDate: string
  startAt?: string
  endAt?: string
  checkerId?: number | null
  checkerName?: string
  itemCount?: number
  diffQtyTotal?: number
  profitAmountTotal?: number
  lossAmountTotal?: number
  diffRate?: number
  status: StocktakeStatus
  versionNo?: number
  approvedAt?: string
  createdAt: string
  updatedAt: string
}

export interface StocktakeOrderItem {
  id: number
  stocktakeId: number
  materialId: number
  materialName?: string
  spec?: string
  unit?: string
  warehouseId?: number | null
  warehouseName?: string
  locationId?: number | null
  locationName?: string
  batchNo?: string
  inventoryId?: number | null
  expiryDate?: string
  systemQty?: number
  actualQty?: number | null
  diffQty?: number
  unitCost?: number
  diffAmount?: number
  diffType?: string
  diffDirection?: string
  diffReason?: string
  recognitionSource?: string
  aiConfidence?: number | null
  remark?: string
  lineRemark?: string
  createdAt?: string
  updatedAt?: string
}

export interface StocktakeVersionSummary {
  id: number
  stocktakeId: number
  versionNo: number
  status: string
  itemCount?: number
  diffQtyTotal?: number
  profitAmountTotal?: number
  lossAmountTotal?: number
  submittedBy?: number | null
  submitterName?: string
  submittedAt?: string
  createdAt?: string
}

export interface StocktakeVersionDetail {
  id: number
  stocktakeId: number
  versionNo: number
  stocktakeNo: string
  warehouseId: number
  warehouseName?: string
  locationId?: number | null
  locationName?: string
  stocktakeType?: string
  stocktakeDate: string
  startAt?: string
  endAt?: string
  checkerId?: number | null
  checkerName?: string
  itemCount?: number
  diffQtyTotal?: number
  profitAmountTotal?: number
  lossAmountTotal?: number
  diffRate?: number
  surplusQty?: number
  deficitQty?: number
  surplusAmount?: number
  deficitAmount?: number
  remark?: string
  attachments?: string[]
  status: string
  approvedBy?: number | null
  approverName?: string
  approvedAt?: string
  approveRemark?: string
  rejectRemark?: string
  voidReason?: string
  submittedBy?: number | null
  submitterName?: string
  submittedAt?: string
  createdBy?: number | null
  creatorName?: string
  createdAt?: string
  updatedAt?: string
  items?: StocktakeOrderItem[]
}

export interface StocktakeOperationLog {
  id: number
  stocktakeId: number
  action: string
  actionName: string
  operatorId?: number | null
  operatorName?: string
  content?: string
  createdAt: string
}

export interface StocktakeOrderDetail {
  id: number
  stocktakeNo: string
  warehouseId: number
  warehouseName?: string
  locationId?: number | null
  locationName?: string
  stocktakeType?: string
  stocktakeDate: string
  startAt?: string
  endAt?: string
  checkerId?: number | null
  checkerName?: string
  itemCount?: number
  diffQtyTotal?: number
  profitAmountTotal?: number
  lossAmountTotal?: number
  diffRate?: number
  surplusQty?: number
  deficitQty?: number
  surplusAmount?: number
  deficitAmount?: number
  remark?: string
  attachments?: string[]
  status: StocktakeStatus
  approvedBy?: number | null
  approverName?: string
  approvedAt?: string
  approveRemark?: string
  rejectRemark?: string
  voidReason?: string
  versionNo?: number
  createdBy?: number | null
  creatorName?: string
  updatedBy?: number | null
  updaterName?: string
  createdAt: string
  updatedAt: string
  items?: StocktakeOrderItem[]
  versions?: StocktakeVersionSummary[]
  operationLogs?: StocktakeOperationLog[]
}

export interface StocktakeSnapshotPreviewItem {
  inventoryId: number
  warehouseId: number
  warehouseName?: string
  locationId?: number | null
  locationName?: string
  materialId: number
  materialName?: string
  spec?: string
  unit?: string
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  quantity?: number
  unitCost?: number
  totalCost?: number
  inventoryStatus?: string
  updatedAt?: string
}

export interface StocktakeStatistics {
  thisMonthCount: number
  pendingCount: number
  profitAmountTotal: number
  lossAmountTotal: number
}

export interface StocktakeOrderItemForm {
  id?: number
  inventoryId?: number | null
  materialId: number
  materialName?: string
  spec?: string
  unit?: string
  warehouseId?: number | null
  locationId?: number | null
  batchNo?: string
  expiryDate?: string
  systemQty?: number
  actualQty?: number | null
  unitCost?: number
  diffReason?: string
  recognitionSource?: string
  aiConfidence?: number | null
  remark?: string
  lineRemark?: string
}

export interface StocktakeOrderForm {
  warehouseId: number | null
  locationId?: number | null
  warehouseIds?: number[]
  locationIds?: number[]
  stocktakeType?: string
  stocktakeDate: string
  startAt?: string
  endAt?: string
  checkerId?: number | null
  checkerName?: string
  remark?: string
  attachments?: string[]
  items?: StocktakeOrderItemForm[]
}

export interface StocktakeOrderQuery {
  pageNum?: number
  pageSize?: number
  stocktakeNo?: string
  startDate?: string
  endDate?: string
  warehouseId?: number
  locationId?: number
  status?: StocktakeStatus | ''
  checkerId?: number
  checkerName?: string
}

export interface StocktakeApprovePayload {
  approveRemark?: string
}

export interface StocktakeRejectPayload {
  rejectRemark: string
}

export interface StocktakeVoidPayload {
  voidReason: string
}

export interface StocktakeSnapshotPreviewQuery {
  warehouseId?: number
  locationId?: number | null
  warehouseIds?: number[]
  locationIds?: number[]
}
