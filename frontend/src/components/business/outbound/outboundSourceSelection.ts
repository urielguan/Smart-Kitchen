interface OutboundSourceOrderOption {
  id: number
  orderNo: string
  orgId?: number | null
  orgName?: string
}

interface PlanLike {
  id: number
  planCode?: string
  planDate?: string
  mealType?: string
}

interface PlanMaterialLike {
  materialId: number
  materialName: string
  spec?: string
  unit?: string
  totalQuantity?: number
  unitCost?: number | null
}

interface WarehouseLike {
  id: number
}

interface ExistingItemLike {
  [key: string]: unknown
}

interface ApplySourceOrderSelectionInput {
  mode?: 'select' | 'hydrate' | 'clear' | 'skip' | 'fallback'
  sourceOrders: OutboundSourceOrderOption[]
  selectedSourceOrderId: number | null
  approvedPlans: PlanLike[]
  planMaterials?: PlanMaterialLike[]
  existingItems?: ExistingItemLike[]
  warehouses: WarehouseLike[]
}

interface SourceSummary {
  orderNo: string
  orgName: string
  planCode: string
  planDate: string
  mealType: string
  planSummary: string
}

interface SourceSelectionResult {
  formPatch: {
    sourceOrderId: number | null
    sourceOrderNo: string
  }
  summary: SourceSummary
  items: ExistingItemLike[]
}

const EMPTY_SUMMARY: SourceSummary = {
  orderNo: '',
  orgName: '',
  planCode: '',
  planDate: '',
  mealType: '',
  planSummary: '',
}

const MEAL_TYPE_LABELS: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  midnight_snack: '夜宵',
}

const formatPlanSummary = (plan?: PlanLike | undefined) => {
  if (!plan) return ''
  const parts = [plan.planCode, plan.planDate, plan.mealType ? (MEAL_TYPE_LABELS[plan.mealType] || plan.mealType) : ''].filter(Boolean)
  return parts.join(' | ')
}

export function applySourceOrderSelection(input: ApplySourceOrderSelectionInput): SourceSelectionResult {
  const mode = input.mode ?? 'select'
  const sourceOrder = input.sourceOrders.find(item => item.id === input.selectedSourceOrderId)
  const plan = input.approvedPlans.find(item => item.id === input.selectedSourceOrderId)
  const defaultWarehouseId = input.warehouses.length === 1 ? input.warehouses[0]?.id ?? null : null
  const sourceOrderNo = sourceOrder?.orderNo || plan?.planCode || ''
  const planCode = plan?.planCode || ''
  const planDate = plan?.planDate || ''
  const mealType = plan?.mealType ? (MEAL_TYPE_LABELS[plan.mealType] || plan.mealType) : ''

  const summary: SourceSummary = {
    ...EMPTY_SUMMARY,
    orderNo: sourceOrderNo,
    orgName: sourceOrder?.orgName || '',
    planCode,
    planDate,
    mealType,
    planSummary: formatPlanSummary(plan),
  }

  if (mode === 'skip') {
    return {
      formPatch: {
        sourceOrderId: input.selectedSourceOrderId,
        sourceOrderNo: '',
      },
      summary: EMPTY_SUMMARY,
      items: input.existingItems || [],
    }
  }

  if (mode === 'hydrate' || mode === 'clear' || mode === 'fallback') {
    return {
      formPatch: {
        sourceOrderId: sourceOrder?.id ?? input.selectedSourceOrderId,
        sourceOrderNo,
      },
      summary: mode === 'clear' ? EMPTY_SUMMARY : summary,
      items: input.existingItems || [],
    }
  }

  const purpose = planCode ? `菜谱计划[${planCode}]领料` : ''
  const items = (input.planMaterials || []).map(material => ({
    materialId: material.materialId,
    materialName: material.materialName,
    spec: material.spec || '',
    unit: material.unit || '',
    warehouseId: defaultWarehouseId,
    locationId: null,
    batchNo: '',
    quantity: Math.ceil(material.totalQuantity || 0),
    unitCost: material.unitCost || null,
    purpose,
    remark: '',
    _keyword: material.materialName,
    _specs: [],
    _locations: [],
    _batchNos: [],
  }))

  return {
    formPatch: {
      sourceOrderId: sourceOrder?.id ?? input.selectedSourceOrderId,
      sourceOrderNo,
    },
    summary,
    items,
  }
}
