import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  PurchaseOrderAttachment,
  PurchaseOrderAuditPayload,
  PurchaseOrderFormPayload,
  PurchaseOrderInspectionPayload,
  PurchaseOrderItem,
  PurchaseOrderLinkedInboundRecord,
  PurchaseOrderLogisticsPayload,
  PurchaseOrderMaterialOption,
  PurchaseOrderPlanItemOption,
  PurchaseOrderQuery,
  PurchaseOrderRecord,
  PurchaseOrderReverseAuditPayload,
  PurchaseOrderReverseAuditResult,
  PurchaseOrderSelectablePlan,
  PurchaseOrderSceneIntegrationLogs,
  PurchaseOrderSceneIntegrationMeta,
  PurchaseOrderSceneIntegrationSyncPayload,
  PurchaseOrderSceneIntegrationTriggerResult,
  PurchaseOrderStatistics,
  PurchaseOrderSupplierOption,
  PurchaseOrderTraceabilityPayload,
  PurchaseOrderVoidApplyPayload,
  PurchaseOrderVoidAuditPayload,
} from '@/types/purchase'

const STORAGE_KEY = 'dev-preview-purchase-orders'

const nowIso = () => new Date().toISOString()

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))

const hasText = (value: unknown): boolean => typeof value === 'string' && value.trim().length > 0

const hasAttachment = (attachments: PurchaseOrderAttachment[] | undefined, fallbackUrl?: string): boolean => {
  return Boolean(fallbackUrl) || Boolean(attachments?.some((attachment) => hasText(attachment.url)))
}

const success = <T>(data: T, message = 'SUCCESS'): ApiResponse<T> => ({
  code: 'SUCCESS',
  message,
  data,
  timestamp: nowIso(),
})

const suppliers: PurchaseOrderSupplierOption[] = [
  { id: 1, name: '绿源蔬菜配送', contactName: '张敏', contactPhone: '13911112222' },
  { id: 2, name: '鲜品冻货供应链', contactName: '李强', contactPhone: '13733334444' },
  { id: 3, name: '谷丰粮油商行', contactName: '王娟', contactPhone: '13655556666' },
]

const materials: PurchaseOrderMaterialOption[] = [
  { id: 101, name: '西红柿', unit: 'kg', spec: '新鲜/一级', referencePrice: 6.8 },
  { id: 102, name: '鸡胸肉', unit: 'kg', spec: '冷鲜/去骨', referencePrice: 18.5 },
  { id: 103, name: '大米', unit: '袋', spec: '25kg/袋', referencePrice: 128 },
  { id: 104, name: '食用油', unit: '桶', spec: '5L/桶', referencePrice: 72 },
]

const selectablePlans: PurchaseOrderSelectablePlan[] = [
  {
    id: 201,
    planNo: 'PP20260501001',
    planName: '6月第一周食材采购计划',
    orgId: 1,
    orgName: '示范校区中央厨房',
    remainingQuantity: 320,
    remainingAmount: 8620,
  },
  {
    id: 202,
    planNo: 'PP20260501002',
    planName: '6月第二周食材采购计划',
    orgId: 1,
    orgName: '示范校区中央厨房',
    remainingQuantity: 180,
    remainingAmount: 5160,
  },
]

const planItems: PurchaseOrderPlanItemOption[] = [
  {
    id: 3001,
    planId: 201,
    planNo: 'PP20260501001',
    planName: '6月第一周食材采购计划',
    planOrgName: '示范校区中央厨房',
    materialId: 101,
    materialName: '西红柿',
    spec: '新鲜/一级',
    unit: 'kg',
    planQuantity: 120,
    orderedQuantity: 40,
    remainingQuantity: 80,
    unitPrice: 6.8,
    remark: '用于番茄炒蛋',
  },
  {
    id: 3002,
    planId: 201,
    planNo: 'PP20260501001',
    planName: '6月第一周食材采购计划',
    planOrgName: '示范校区中央厨房',
    materialId: 102,
    materialName: '鸡胸肉',
    spec: '冷鲜/去骨',
    unit: 'kg',
    planQuantity: 90,
    orderedQuantity: 30,
    remainingQuantity: 60,
    unitPrice: 18.5,
    remark: '用于香煎鸡排',
  },
  {
    id: 3003,
    planId: 202,
    planNo: 'PP20260501002',
    planName: '6月第二周食材采购计划',
    planOrgName: '示范校区中央厨房',
    materialId: 103,
    materialName: '大米',
    spec: '25kg/袋',
    unit: '袋',
    planQuantity: 30,
    orderedQuantity: 10,
    remainingQuantity: 20,
    unitPrice: 128,
    remark: '主食储备',
  },
]

const buildItem = (item: Partial<PurchaseOrderItem> & {
  materialId: number
  materialName: string
  spec: string
  unit: string
  quantity: number
  unitPrice: number
}): PurchaseOrderItem => ({
  id: item.id,
  planItemId: item.planItemId ?? null,
  planId: item.planId ?? null,
  planNo: item.planNo || '',
  planName: item.planName || '',
  planOrgName: item.planOrgName || '',
  materialId: item.materialId,
  materialName: item.materialName,
  spec: item.spec,
  unit: item.unit,
  quantity: item.quantity,
  unitPrice: item.unitPrice,
  unitCost: item.unitPrice,
  subtotal: Number((item.quantity * item.unitPrice).toFixed(2)),
  receivedQty: item.receivedQty ?? 0,
  inboundQty: item.inboundQty ?? 0,
  remainingInboundQty: item.remainingInboundQty ?? item.quantity,
  remark: item.remark || '',
})

const buildOrder = (order: Partial<PurchaseOrderRecord> & {
  id: number
  orderNo: string
  supplierId: number
  orgId: number
  orderDate: string
  expectedArrival: string
  status: PurchaseOrderRecord['status']
  items: PurchaseOrderItem[]
}): PurchaseOrderRecord => {
  const supplier = suppliers.find(item => item.id === order.supplierId)
  const totalAmount = order.items.reduce((sum, item) => sum + Number(item.subtotal || 0), 0)

  return {
    id: order.id,
    orderNo: order.orderNo,
    supplierId: order.supplierId,
    supplierName: supplier?.name || order.supplierName || '',
    orgId: order.orgId,
    orgName: order.orgName || '示范校区中央厨房',
    buyerName: order.buyerName || '王采购',
    createdById: order.createdById ?? 1,
    createdBy: order.createdBy || '本地预览管理员',
    orderDate: order.orderDate,
    expectedArrival: order.expectedArrival,
    totalAmount: Number(totalAmount.toFixed(2)),
    logisticsCompany: order.logisticsCompany || '',
    logisticsTrackingNo: order.logisticsTrackingNo || '',
    logisticsStatus: order.logisticsStatus || '',
    logisticsRemark: order.logisticsRemark || '',
    logisticsSourceType: order.logisticsSourceType || 'manual',
    logisticsSyncPayload: order.logisticsSyncPayload || '',
    shippedAt: order.shippedAt || '',
    arrivedAt: order.arrivedAt || '',
    logisticsAttachmentName: order.logisticsAttachmentName || '',
    logisticsAttachmentUrl: order.logisticsAttachmentUrl || '',
    logisticsAttachments: clone(order.logisticsAttachments || []),
    inspectionReportNo: order.inspectionReportNo || '',
    inspectionResult: order.inspectionResult || '',
    inspectionAgency: order.inspectionAgency || '',
    inspectionAt: order.inspectionAt || '',
    inspectionRemark: order.inspectionRemark || '',
    inspectionSourceType: order.inspectionSourceType || 'manual',
    inspectionSyncPayload: order.inspectionSyncPayload || '',
    inspectionAttachmentName: order.inspectionAttachmentName || '',
    inspectionAttachmentUrl: order.inspectionAttachmentUrl || '',
    inspectionAttachments: clone(order.inspectionAttachments || []),
    inspectionFilled: order.inspectionFilled ?? (
      hasText(order.inspectionReportNo)
      || hasText(order.inspectionResult)
      || hasText(order.inspectionAgency)
      || hasText(order.inspectionAt)
      || hasText(order.inspectionRemark)
      || hasText(order.inspectionSyncPayload)
      || hasAttachment(order.inspectionAttachments, order.inspectionAttachmentUrl)
    ),
    attachmentName: order.attachmentName || '',
    attachmentUrl: order.attachmentUrl || '',
    status: order.status,
    deleted: order.deleted ?? false,
    remark: order.remark || '',
    createdAt: order.createdAt || nowIso(),
    updatedAt: order.updatedAt || nowIso(),
    auditBy: order.auditBy ?? null,
    auditAt: order.auditAt ?? null,
    auditRemark: order.auditRemark || '',
    voidReason: order.voidReason || '',
    voidRequestedBy: order.voidRequestedBy ?? null,
    voidRequestedAt: order.voidRequestedAt ?? null,
    voidAuditBy: order.voidAuditBy ?? null,
    voidAuditAt: order.voidAuditAt ?? null,
    voidAuditRemark: order.voidAuditRemark || '',
    traceBatchId: order.traceBatchId || '',
    traceOrigin: order.traceOrigin || '',
    traceRemark: order.traceRemark || '',
    traceSourceType: order.traceSourceType || 'manual',
    traceSyncPayload: order.traceSyncPayload || '',
    traceAttachmentName: order.traceAttachmentName || '',
    traceAttachmentUrl: order.traceAttachmentUrl || '',
    traceabilityAttachments: clone(order.traceabilityAttachments || []),
    traceabilityFilled: order.traceabilityFilled ?? (
      hasText(order.traceBatchId)
      || hasText(order.traceOrigin)
      || hasText(order.traceRemark)
      || hasText(order.traceSyncPayload)
      || hasAttachment(order.traceabilityAttachments, order.traceAttachmentUrl)
    ),
    relatedPlanIds: clone(order.relatedPlanIds || []),
    relatedPlans: clone(order.relatedPlans || []),
    items: clone(order.items),
  }
}

const defaultOrders = (): PurchaseOrderRecord[] => [
  buildOrder({
    id: 1,
    orderNo: 'PO20260601001',
    supplierId: 1,
    orgId: 1,
    orderDate: '2026-06-01',
    expectedArrival: '2026-06-02',
    status: 'pending_approve',
    remark: '优先早班配送',
    relatedPlanIds: [201],
    relatedPlans: [
      {
        id: 201,
        planNo: 'PP20260501001',
        planName: '6月第一周食材采购计划',
        orgName: '示范校区中央厨房',
      },
    ],
    items: [
      buildItem({
        id: 11,
        planItemId: 3001,
        planId: 201,
        planNo: 'PP20260501001',
        planName: '6月第一周食材采购计划',
        planOrgName: '示范校区中央厨房',
        materialId: 101,
        materialName: '西红柿',
        spec: '新鲜/一级',
        unit: 'kg',
        quantity: 80,
        unitPrice: 6.8,
        remark: 'A级品控',
      }),
      buildItem({
        id: 12,
        planItemId: 3002,
        planId: 201,
        planNo: 'PP20260501001',
        planName: '6月第一周食材采购计划',
        planOrgName: '示范校区中央厨房',
        materialId: 102,
        materialName: '鸡胸肉',
        spec: '冷鲜/去骨',
        unit: 'kg',
        quantity: 60,
        unitPrice: 18.5,
      }),
    ],
  }),
  buildOrder({
    id: 2,
    orderNo: 'PO20260529002',
    supplierId: 3,
    orgId: 1,
    orderDate: '2026-05-29',
    expectedArrival: '2026-05-30',
    status: 'approved',
    logisticsCompany: '顺鲜冷链',
    logisticsTrackingNo: 'SFCL20260529001',
    logisticsStatus: 'in_transit',
    auditBy: '审核员李娜',
    auditAt: '2026-05-29T09:30:00.000Z',
    auditRemark: '价格合理，允许下单',
    relatedPlanIds: [202],
    relatedPlans: [
      {
        id: 202,
        planNo: 'PP20260501002',
        planName: '6月第二周食材采购计划',
        orgName: '示范校区中央厨房',
      },
    ],
    items: [
      buildItem({
        id: 21,
        planItemId: 3003,
        planId: 202,
        planNo: 'PP20260501002',
        planName: '6月第二周食材采购计划',
        planOrgName: '示范校区中央厨房',
        materialId: 103,
        materialName: '大米',
        spec: '25kg/袋',
        unit: '袋',
        quantity: 20,
        unitPrice: 128,
      }),
      buildItem({
        id: 22,
        materialId: 104,
        materialName: '食用油',
        spec: '5L/桶',
        unit: '桶',
        quantity: 12,
        unitPrice: 72,
      }),
    ],
  }),
]

const emptySceneMeta = (scene: 'logistics' | 'inspection' | 'traceability', bizId: number): PurchaseOrderSceneIntegrationMeta => ({
  bizModule: 'purchase_order',
  bizScene: scene,
  bizId,
  orgId: 1,
  tenantId: 1,
  selectedConfigId: null,
  defaultMode: 'manual',
  allowDocumentSwitch: 1,
  forceThirdParty: 0,
  allowManualFallback: 1,
  autoCoverEnabled: 0,
  externalNoFieldRule: '',
  currentBinding: null,
  configOptions: [],
  recentSyncLogs: [],
  recentCallbackLogs: [],
})

const emptySceneLogs = (): PurchaseOrderSceneIntegrationLogs => ({
  binding: null,
  syncLogs: [],
  callbackLogs: [],
})

const readOrders = (): PurchaseOrderRecord[] => {
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    const initialValue = defaultOrders()
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(initialValue))
    return initialValue
  }

  try {
    return clone(JSON.parse(raw) as PurchaseOrderRecord[])
  } catch {
    const initialValue = defaultOrders()
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(initialValue))
    return initialValue
  }
}

const writeOrders = (orders: PurchaseOrderRecord[]) => {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(orders))
}

const nextOrderId = (orders: PurchaseOrderRecord[]) => {
  return orders.reduce((max, item) => Math.max(max, item.id), 0) + 1
}

const nextItemId = (orders: PurchaseOrderRecord[]) => {
  return orders.flatMap(item => item.items).reduce((max, item) => Math.max(max, item.id || 0), 0) + 1
}

const filterOrders = (orders: PurchaseOrderRecord[], params: PurchaseOrderQuery = {}) => {
  return orders
    .filter((order) => {
      if (!params.showDeleted && order.deleted) {
        return false
      }
      if (params.orgId && order.orgId !== params.orgId) {
        return false
      }
      if (params.status && order.status !== params.status) {
        return false
      }
      if (params.dateStart && order.orderDate < params.dateStart) {
        return false
      }
      if (params.dateEnd && order.orderDate > params.dateEnd) {
        return false
      }
      if (!params.keyword) {
        return true
      }
      const keyword = params.keyword.toLowerCase()
      return [
        order.orderNo,
        order.supplierName,
        order.orgName,
        order.buyerName,
        order.remark,
        ...order.items.map(item => item.materialName),
      ]
        .filter(Boolean)
        .some(value => value.toLowerCase().includes(keyword))
    })
    .sort((left, right) => right.id - left.id)
}

const statsFromOrders = (orders: PurchaseOrderRecord[]): PurchaseOrderStatistics => {
  const visibleOrders = orders.filter(item => !item.deleted)
  return {
    total: visibleOrders.length,
    pending: visibleOrders.filter(item => ['pending_submit', 'pending_approve'].includes(item.status)).length,
    approved: visibleOrders.filter(item => item.status === 'approved').length,
    totalAmount: Number(visibleOrders.reduce((sum, item) => sum + item.totalAmount, 0).toFixed(2)),
  }
}

const materialById = (materialId: number) => {
  return materials.find(item => item.id === materialId)
}

const planItemById = (planItemId?: number) => {
  if (!planItemId) {
    return undefined
  }
  return planItems.find(item => item.id === planItemId)
}

const attachmentFromFile = (file?: File | null): PurchaseOrderAttachment[] => {
  if (!file) {
    return []
  }
  return [
    {
      id: Date.now(),
      name: file.name,
      size: `${Math.max(1, Math.round(file.size / 1024))}KB`,
      url: URL.createObjectURL(file),
      sortOrder: 1,
    },
  ]
}

const normalizePayloadItems = (
  payload: PurchaseOrderFormPayload,
  itemIdSeed: number,
): PurchaseOrderItem[] => {
  return payload.items.map((item, index) => {
    const material = materialById(item.materialId)
    const planItem = planItemById(item.planItemId)
    const unitPrice = Number(item.unitPrice || material?.referencePrice || 0)
    const quantity = Number(item.quantity || 0)
    return buildItem({
      id: itemIdSeed + index,
      planItemId: item.planItemId ?? null,
      planId: planItem?.planId ?? null,
      planNo: planItem?.planNo || '',
      planName: planItem?.planName || '',
      planOrgName: planItem?.planOrgName || '',
      materialId: item.materialId,
      materialName: material?.name || '未知物料',
      spec: item.spec || material?.spec || '',
      unit: material?.unit || '个',
      quantity,
      unitPrice,
      remark: item.remark || '',
    })
  })
}

const triggerDownload = (content: BlobPart, fileName: string) => {
  const blobUrl = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

const downloadByUrl = (url: string, fileName: string) => {
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

export const devPreviewPurchaseApi = {
  async getList(params: PurchaseOrderQuery): Promise<ApiResponse<PageResponse<PurchaseOrderRecord>>> {
    const orders = filterOrders(readOrders(), params)
    const pageNum = params.pageNum || 1
    const pageSize = params.pageSize || 10
    const start = (pageNum - 1) * pageSize
    const list = orders.slice(start, start + pageSize)

    return success({
      list: clone(list),
      total: orders.length,
      pageNum,
      pageSize,
    })
  },

  async getStatistics(_orgId?: number): Promise<ApiResponse<PurchaseOrderStatistics>> {
    return success(statsFromOrders(readOrders()))
  },

  async getDetail(id: number): Promise<ApiResponse<PurchaseOrderRecord>> {
    const order = readOrders().find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    return success(clone(order))
  },

  async create(data: PurchaseOrderFormPayload, file?: File | null): Promise<ApiResponse<number>> {
    const orders = readOrders()
    const id = nextOrderId(orders)
    const itemIdSeed = nextItemId(orders)
    const supplier = suppliers.find(item => item.id === data.supplierId)
    const attachments = attachmentFromFile(file)
    const order = buildOrder({
      id,
      orderNo: data.orderNo || `PO${new Date().toISOString().slice(0, 10).replace(/-/g, '')}${String(id).padStart(3, '0')}`,
      supplierId: data.supplierId,
      supplierName: supplier?.name || '',
      orgId: data.orgId,
      orderDate: data.orderDate,
      expectedArrival: data.expectedArrival,
      status: data.status,
      remark: data.remark || '',
      attachmentName: attachments[0]?.name || '',
      attachmentUrl: attachments[0]?.url || '',
      relatedPlanIds: clone(data.relatedPlanIds || []),
      relatedPlans: selectablePlans
        .filter(item => (data.relatedPlanIds || []).includes(item.id))
        .map(item => ({
          id: item.id,
          planNo: item.planNo,
          planName: item.planName,
          orgName: item.orgName,
        })),
      items: normalizePayloadItems(data, itemIdSeed),
    })

    orders.unshift(order)
    writeOrders(orders)
    return success(id)
  },

  async update(id: number, data: PurchaseOrderFormPayload, file?: File | null): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const index = orders.findIndex(item => item.id === id)
    if (index < 0) {
      throw new Error('采购订单不存在')
    }
    const current = orders[index]
    const attachments = file ? attachmentFromFile(file) : current.attachmentUrl ? [{
      name: current.attachmentName,
      url: current.attachmentUrl,
    }] : []

    orders[index] = buildOrder({
      ...current,
      supplierId: data.supplierId,
      orgId: data.orgId,
      orderDate: data.orderDate,
      expectedArrival: data.expectedArrival,
      status: data.status,
      remark: data.remark || '',
      attachmentName: attachments[0]?.name || '',
      attachmentUrl: attachments[0]?.url || '',
      relatedPlanIds: clone(data.relatedPlanIds || []),
      relatedPlans: selectablePlans
        .filter(item => (data.relatedPlanIds || []).includes(item.id))
        .map(item => ({
          id: item.id,
          planNo: item.planNo,
          planName: item.planName,
          orgName: item.orgName,
        })),
      items: normalizePayloadItems(data, nextItemId(orders)),
      updatedAt: nowIso(),
    })

    writeOrders(orders)
    return success(undefined)
  },

  async audit(id: number, data: PurchaseOrderAuditPayload): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    order.status = data.status
    order.auditBy = '本地预览管理员'
    order.auditAt = nowIso()
    order.auditRemark = data.remark || ''
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success(undefined)
  },

  async reverseAudit(id: number, data: PurchaseOrderReverseAuditPayload): Promise<ApiResponse<PurchaseOrderReverseAuditResult>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    if (!data.reason.trim()) {
      throw new Error('请填写反审核原因')
    }
    order.status = 'pending_approve'
    order.auditBy = null
    order.auditAt = null
    order.auditRemark = ''
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success({
      affectedInboundCount: 0,
      affectedInboundNos: [],
    })
  },

  async applyVoid(id: number, data: PurchaseOrderVoidApplyPayload): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    order.status = 'pending_void_approve'
    order.voidReason = data.reason
    order.voidRequestedBy = '本地预览管理员'
    order.voidRequestedAt = nowIso()
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success(undefined)
  },

  async auditVoid(id: number, data: PurchaseOrderVoidAuditPayload): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    order.status = data.approved ? 'voided' : 'approved'
    order.voidAuditBy = '本地预览管理员'
    order.voidAuditAt = nowIso()
    order.voidAuditRemark = data.remark || ''
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success(undefined)
  },

  async updateLogistics(id: number, data: PurchaseOrderLogisticsPayload): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    order.logisticsCompany = data.company || ''
    order.logisticsTrackingNo = data.trackingNo || ''
    order.logisticsStatus = data.logisticsStatus
    order.shippedAt = data.shippedAt || ''
    order.arrivedAt = data.arrivedAt || ''
    order.logisticsRemark = data.remark || ''
    order.logisticsSourceType = data.sourceType || 'manual'
    order.logisticsSyncPayload = data.syncPayload || ''
    order.logisticsAttachments = clone(data.attachments || [])
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success(undefined)
  },

  async deleteLogisticsAttachment(id: number): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (order) {
      order.logisticsAttachments = []
      order.updatedAt = nowIso()
      writeOrders(orders)
    }
    return success(undefined)
  },

  async updateInspection(id: number, data: PurchaseOrderInspectionPayload): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    order.inspectionReportNo = data.reportNo || ''
    order.inspectionResult = data.result || ''
    order.inspectionAgency = data.agency || ''
    order.inspectionAt = data.inspectedAt || ''
    order.inspectionRemark = data.remark || ''
    order.inspectionSourceType = data.sourceType || 'manual'
    order.inspectionSyncPayload = data.syncPayload || ''
    order.inspectionAttachments = clone(data.attachments || [])
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success(undefined)
  },

  async deleteInspectionAttachment(id: number): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (order) {
      order.inspectionAttachments = []
      order.updatedAt = nowIso()
      writeOrders(orders)
    }
    return success(undefined)
  },

  async updateTraceability(id: number, data: PurchaseOrderTraceabilityPayload): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    order.traceBatchId = data.traceBatchId || ''
    order.traceOrigin = data.origin || ''
    order.traceRemark = data.remark || ''
    order.traceSourceType = data.sourceType || 'manual'
    order.traceSyncPayload = data.syncPayload || ''
    order.traceabilityAttachments = clone(data.attachments || [])
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success(undefined)
  },

  async getSceneIntegrationMeta(scene: 'logistics' | 'inspection' | 'traceability', id: number): Promise<ApiResponse<PurchaseOrderSceneIntegrationMeta>> {
    return success(emptySceneMeta(scene, id))
  },

  async triggerSceneIntegrationSync(
    scene: 'logistics' | 'inspection' | 'traceability',
    id: number,
    data: PurchaseOrderSceneIntegrationSyncPayload,
  ): Promise<ApiResponse<PurchaseOrderSceneIntegrationTriggerResult>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    if (scene === 'logistics') {
      order.logisticsSourceType = 'third_party'
      order.logisticsTrackingNo = data.externalNo
      order.logisticsSyncPayload = JSON.stringify({ externalNo: data.externalNo, source: 'dev-preview' }, null, 2)
    } else if (scene === 'inspection') {
      order.inspectionSourceType = 'third_party'
      order.inspectionReportNo = data.externalNo
      order.inspectionSyncPayload = JSON.stringify({ externalNo: data.externalNo, source: 'dev-preview' }, null, 2)
    } else {
      order.traceSourceType = 'third_party'
      order.traceBatchId = data.externalNo
      order.traceSyncPayload = JSON.stringify({ externalNo: data.externalNo, source: 'dev-preview' }, null, 2)
    }
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success({
      taskId: Date.now(),
      taskNo: `DEV-${Date.now()}`,
      bindingId: Date.now(),
      syncStatus: 'success',
      message: data.queryOnly === 1 ? '同步成功，当前模式仅记录结果不自动覆盖' : '同步成功并已回写业务数据',
      normalizedPayload: JSON.stringify({ externalNo: data.externalNo, scene }, null, 2),
      downloadedFileCount: 0,
      binding: {
        bindingId: Date.now(),
        configId: data.configId,
        externalNo: data.externalNo,
        maintenanceMode: 'third_party',
        syncStatus: 'success',
        lastSyncAt: nowIso(),
      },
    })
  },

  async getSceneIntegrationLogs(_scene: 'logistics' | 'inspection' | 'traceability', _id: number): Promise<ApiResponse<PurchaseOrderSceneIntegrationLogs>> {
    return success(emptySceneLogs())
  },

  async deleteTraceabilityAttachment(id: number): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (order) {
      order.traceabilityAttachments = []
      order.updatedAt = nowIso()
      writeOrders(orders)
    }
    return success(undefined)
  },

  async uploadAttachment(file: File): Promise<ApiResponse<PurchaseOrderAttachment>> {
    const attachment = attachmentFromFile(file)[0]
    return success(attachment)
  },

  async deleteAttachment(_fileUrl: string, _fileName?: string): Promise<ApiResponse<void>> {
    return success(undefined)
  },

  async delete(id: number): Promise<ApiResponse<void>> {
    const orders = readOrders()
    const order = orders.find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    order.deleted = true
    order.updatedAt = nowIso()
    writeOrders(orders)
    return success(undefined)
  },

  async getItems(id: number): Promise<ApiResponse<PurchaseOrderItem[]>> {
    const order = readOrders().find(item => item.id === id)
    if (!order) {
      throw new Error('采购订单不存在')
    }
    return success(clone(order.items))
  },

  async getLinkedInboundRecords(_id: number): Promise<ApiResponse<PurchaseOrderLinkedInboundRecord[]>> {
    return success([])
  },

  async getSupplierOptions(_orgId?: number): Promise<ApiResponse<PurchaseOrderSupplierOption[]>> {
    return success(clone(suppliers))
  },

  async getMaterialOptions(_orgId?: number): Promise<ApiResponse<PurchaseOrderMaterialOption[]>> {
    return success(clone(materials))
  },

  async getSelectablePlans(params?: {
    keyword?: string
    excludeOrderId?: number
  }): Promise<ApiResponse<PurchaseOrderSelectablePlan[]>> {
    const keyword = params?.keyword?.toLowerCase()
    const data = selectablePlans.filter((item) => {
      if (!keyword) {
        return true
      }
      return [item.planNo, item.planName, item.orgName].some(value => value.toLowerCase().includes(keyword))
    })
    return success(clone(data))
  },

  async getPlanItems(params: {
    planIds: string
    excludeOrderId?: number
  }): Promise<ApiResponse<PurchaseOrderPlanItemOption[]>> {
    const ids = params.planIds.split(',').map(item => Number(item)).filter(Boolean)
    return success(clone(planItems.filter(item => ids.includes(item.planId))))
  },

  async downloadAttachment(id: number): Promise<void> {
    const order = readOrders().find(item => item.id === id)
    if (order?.attachmentUrl) {
      downloadByUrl(order.attachmentUrl, order.attachmentName || `${order.orderNo}-附件`)
      return
    }
    triggerDownload(`采购订单 ${order?.orderNo || id} 附件占位内容`, `${order?.orderNo || id}-附件.txt`)
  },

  async downloadLogisticsAttachment(id: number): Promise<void> {
    const order = readOrders().find(item => item.id === id)
    const attachment = order?.logisticsAttachments?.[0]
    if (attachment?.url) {
      downloadByUrl(attachment.url, attachment.name || `${order?.orderNo}-物流附件`)
      return
    }
    triggerDownload(`采购订单 ${order?.orderNo || id} 物流附件占位内容`, `${order?.orderNo || id}-物流附件.txt`)
  },

  async downloadInspectionAttachment(id: number): Promise<void> {
    const order = readOrders().find(item => item.id === id)
    const attachment = order?.inspectionAttachments?.[0]
    if (attachment?.url) {
      downloadByUrl(attachment.url, attachment.name || `${order?.orderNo}-检测附件`)
      return
    }
    triggerDownload(`采购订单 ${order?.orderNo || id} 检测附件占位内容`, `${order?.orderNo || id}-检测附件.txt`)
  },

  async downloadTraceabilityAttachment(id: number): Promise<void> {
    const order = readOrders().find(item => item.id === id)
    const attachment = order?.traceabilityAttachments?.[0]
    if (attachment?.url) {
      downloadByUrl(attachment.url, attachment.name || `${order?.orderNo}-溯源附件`)
      return
    }
    triggerDownload(`采购订单 ${order?.orderNo || id} 溯源附件占位内容`, `${order?.orderNo || id}-溯源附件.txt`)
  },

  async downloadAttachmentByUrl(fileUrl: string, fileName?: string): Promise<void> {
    if (fileUrl) {
      downloadByUrl(fileUrl, fileName || '采购订单附件')
      return
    }
    triggerDownload('采购订单附件占位内容', `${fileName || '采购订单附件'}.txt`)
  },
}
