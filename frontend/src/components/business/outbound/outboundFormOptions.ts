interface ApiSuccess<T> {
  code: string
  data?: T
}

interface PagedList<T> {
  list?: T[]
}

interface LoadOutboundFormOptionsDeps<Warehouse = unknown, Material = unknown, Plan = unknown> {
  loadOutboundTypeOptions: () => Promise<unknown>
  getWarehouses: () => Promise<ApiSuccess<PagedList<Warehouse>>>
  getMaterials: () => Promise<ApiSuccess<PagedList<Material>>>
  ensureOrgTreeLoaded: () => Promise<unknown>
  getPlanList: () => Promise<ApiSuccess<PagedList<Plan>>>
  onOutboundTypeError?: (message: string, error: unknown) => void
  onOrgTreeError?: (message: string, error: unknown) => void
  onPlanListError?: (message: string, error: unknown) => void
}

interface LoadOutboundFormOptionsResult<Warehouse = unknown, Material = unknown, Plan = unknown> {
  warehouses: Warehouse[]
  materials: Material[]
  approvedPlans: Plan[]
}

export async function loadOutboundFormOptions<Warehouse = unknown, Material = unknown, Plan = unknown>(
  deps: LoadOutboundFormOptionsDeps<Warehouse, Material, Plan>
): Promise<LoadOutboundFormOptionsResult<Warehouse, Material, Plan>> {
  try {
    await deps.loadOutboundTypeOptions()
  } catch (error) {
    deps.onOutboundTypeError?.('加载出库类型失败', error)
  }

  const [warehouseRes, materialRes] = await Promise.all([
    deps.getWarehouses(),
    deps.getMaterials(),
  ])

  try {
    await deps.ensureOrgTreeLoaded()
  } catch (error) {
    deps.onOrgTreeError?.('加载组织树失败', error)
  }

  let approvedPlans: Plan[] = []
  try {
    const planRes = await deps.getPlanList()
    if (planRes.code === 'SUCCESS') {
      approvedPlans = planRes.data?.list || []
    }
  } catch (error) {
    deps.onPlanListError?.('加载菜谱计划失败', error)
  }

  return {
    warehouses: warehouseRes.code === 'SUCCESS' ? warehouseRes.data?.list || [] : [],
    materials: materialRes.code === 'SUCCESS' ? materialRes.data?.list || [] : [],
    approvedPlans,
  }
}
