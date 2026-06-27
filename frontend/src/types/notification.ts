import type { Recipe } from '@/types/recipe'

/** 通知统计 */
export interface NotificationStats {
  total: number
  unread: number
  highPriority: number
  expiring: number
  expired: number
}

 lowStock: number
}

 /** 物料通知 */
export interface MaterialNotification {
  id: number
  notificationType: string
  notificationTypeName: string
  materialId: number
  materialName: string
  inventoryId: number
  batchNo: string
  quantity: number
  unit: string
  expiryDate: string
  daysRemaining: number
  recommendedRecipes: RecommendedRecipe[]
  title: string
  content: string
  priority: string
  priorityName: string
  status: string
  statusName: string
  handledBy: number
  handledByName: string
  handledAt: string
  handleRemark: string
  createdAt: string
}
 /** 推荐菜谱 */
export interface RecommendedRecipe {
  id: number
  recipeCode: string
  recipeName: string
  categoryName: string
  imageUrl: string
  estimatedCost: number
}
