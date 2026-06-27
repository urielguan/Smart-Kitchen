/** 库存状态 */
export type StockStatus = 'normal' | 'low' | 'high' | 'expired'

/** 物料状态 */
export type MaterialStatus = 'active' | 'inactive'

/** 物料信息 */
export interface Material {
  id: number
  materialCode: string
  materialName: string
  materialSpec: string
  unit: string
  categoryName: string
  shelfLifeDays: number
  nearExpiryDays: number
  warningDays: number
  minStock: number
  maxStock: number
  currentStock: number
  stockStatus: StockStatus
  storageRequire: string
  imageUrl: string
  status: MaterialStatus
  remark: string
  foodItemId?: number | null
  foodCode?: string | null
  foodName?: string | null
  calories?: number | null
  protein?: number | null
  carbohydrate?: number | null
  fat?: number | null
  sodium?: number | null
  fiber?: number | null
  vitaminA?: number | null
  vitaminB1?: number | null
  vitaminB2?: number | null
  vitaminC?: number | null
  vitaminE?: number | null
  calcium?: number | null
  iron?: number | null
  zinc?: number | null
  nutritionSourceType?: string | null
  nutritionSyncedAt?: string | null
  sourceVersion?: string | null
  expiryDate?: string
  createdAt: string
  updatedAt: string
}

/** 物料表单 */
export interface MaterialForm {
  materialCode: string
  materialName: string
  materialSpec: string
  unit: string
  categoryName: string
  shelfLifeDays: number
  nearExpiryDays: number
  warningDays: number
  minStock: number
  maxStock: number
  storageRequire: string
  imageUrl: string
  remark: string
}

/** 物料查询参数 */
export interface MaterialQuery {
  pageNum: number
  pageSize: number
  materialName?: string
  materialCode?: string
  categoryName?: string
  stockStatus?: StockStatus
  status?: MaterialStatus
}

/** 物料统计数据 */
export interface MaterialStatistics {
  total: number
  activeCount: number
  inactiveCount: number
  incompleteCount: number
}

/** 物料导入结果 */
export interface MaterialImportResult {
  total: number
  successCount: number
  failCount: number
  hasErrors: boolean
  errorFileUrl: string | null
}

export interface FoodCategory {
  id: number
  parentId?: number | null
  categoryCode: string
  categoryName: string
  categoryLevel: number
  sortOrder: number
  sourceFile?: string
  status: string
}

export interface FoodItem {
  id: number
  foodCode: string
  foodName: string
  categoryLevel1Id?: number | null
  categoryLevel2Id?: number | null
  edibleRatio?: number | null
  energyKcal?: number | null
  protein?: number | null
  fat?: number | null
  carbohydrate?: number | null
  dietaryFiber?: number | null
  sodium?: number | null
  vitaminA?: number | null
  vitaminB1?: number | null
  vitaminB2?: number | null
  vitaminC?: number | null
  vitaminE?: number | null
  calcium?: number | null
  iron?: number | null
  zinc?: number | null
  sourceFile?: string
  sourceVersion?: string
  status: string
}

export interface FoodImportResult {
  fileCount: number
  categoryCount: number
  itemCount: number
  sourceVersion: string
}

export interface MaterialFoodMapping {
  id: number
  materialId: number
  materialCode?: string
  materialName?: string
  foodItemId: number
  foodCode?: string
  foodName?: string
  matchStatus: string
  remark?: string
  confirmedAt?: string
}

export interface MaterialFoodMappingQuery {
  pageNum: number
  pageSize: number
  materialId?: number
  materialName?: string
  matchStatus?: string
}

export interface MaterialFoodMappingForm {
  materialId: number
  foodItemId: number
  matchStatus?: string
  remark?: string
}

export interface NutritionSyncResult {
  materialId: number
  foodItemId: number
  materialName: string
  foodName: string
  nutritionSourceType: string
}
