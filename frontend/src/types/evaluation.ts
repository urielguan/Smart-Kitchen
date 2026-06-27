/** 评价来源 */
export type ReviewSource = 'meal' | 'supervision' | 'manual'

/** 投诉类型 */
export type ComplaintType = 'food' | 'service' | 'hygiene' | 'other'

/** 投诉来源 */
export type ComplaintSource = 'meal' | 'supervision' | 'manual'

/** 投诉状态 */
export type ComplaintStatus = 'pending' | 'dispatched' | 'processing' | 'closed'

/** 优先级 */
export type Priority = 'high' | 'medium' | 'low'

/** 满意度 */
export type Satisfaction = 'satisfied' | 'neutral' | 'dissatisfied'

/** 派单方式 */
export type DispatchType = 'auto' | 'manual'

/** 派单状态 */
export type DispatchStatus = 'pending' | 'processing' | 'completed' | 'cancelled'

/** 工单操作类型 */
export type WorkOrderAction = 'dispatch' | 'reassign' | 'process' | 'complete' | 'cancel'

// ==================== 评价相关 ====================

/** 用餐评价信息 */
export interface MealReview {
  id: number
  reviewNo: string                    // 评价编号
  source: ReviewSource                // 来源
  employeeId: number                  // 评价人ID
  employeeName: string                // 评价人姓名
  menuId?: number                     // 菜品ID
  menuName?: string                   // 菜品名称
  reviewDate: string                  // 评价日期
  mealType?: string                   // 餐次
  overallScore: number                // 综合评分（1-5）
  tasteScore?: number                 // 口味评分
  nutritionScore?: number             // 营养评分
  portionScore?: number               // 份量评分
  content?: string                    // 评价内容
  images?: string[]                   // 评价图片
  tags?: string[]                     // 评价标签
  points: number                      // 获得积分
  orgId: number                       // 组织ID
  orgName: string                     // 组织名称
  replyContent?: string               // 回复内容
  replyByName?: string                // 回复人姓名
  replyAt?: string                    // 回复时间
  createdAt: string                   // 创建时间
}

/** 评价查询参数 */
export interface ReviewQuery {
  pageNum: number
  pageSize: number
  source?: ReviewSource
  orgId?: number
  keyword?: string                    // 关键词（评价人/菜品名称模糊）
  overallScore?: number               // 评分（1-5）
  startTime?: string
  endTime?: string
}

/** 评价统计数据 */
export interface ReviewStatistics {
  totalReviews: number                // 总评价数
  avgScore: number                    // 平均评分
  fiveStarCount: number               // 五星好评数
  fourStarCount: number               // 四星好评数
  threeStarCount: number              // 三星评价数
  twoStarCount: number                // 二星评价数
  oneStarCount: number                // 一星评价数
  satisfactionRate: number            // 满意度（4星及以上占比）
}

/** 新增评价表单 */
export interface CreateReviewForm {
  source: ReviewSource
  employeeId: number
  menuId?: number
  menuName?: string
  reviewDate: string
  mealType?: string
  overallScore: number
  tasteScore?: number
  nutritionScore?: number
  portionScore?: number
  content?: string
  images?: string[]
  tags?: string[]
  orgId: number
}

/** 回复评价表单 */
export interface ReviewReplyForm {
  replyContent: string
}

// ==================== 投诉相关 ====================

/** 投诉信息 */
export interface Complaint {
  id: number
  complaintNo: string                 // 投诉编号
  complaintType?: ComplaintType       // 投诉类型
  source: ComplaintSource             // 来源
  title: string                       // 投诉标题
  description?: string                // 投诉描述
  submitterId: number                 // 投诉人ID
  submitterName: string               // 投诉人姓名
  submitterPhone?: string             // 投诉人电话
  relatedMenuId?: number              // 关联菜品ID
  relatedMenuName?: string            // 关联菜品名称
  images?: string[]                   // 投诉图片
  status: ComplaintStatus             // 状态
  priority: Priority                  // 优先级
  satisfaction?: Satisfaction         // 满意度
  satisfactionRemark?: string         // 满意度备注
  // 派单信息（冗余，用于列表展示）
  dispatchType?: DispatchType
  dispatchId?: number
  handlerId?: number
  handlerName?: string
  deadline?: string
  // 组织信息
  orgId: number
  orgName: string
  createdAt: string
  updatedAt: string
}

/** 投诉查询参数 */
export interface ComplaintQuery {
  pageNum: number
  pageSize: number
  complaintType?: ComplaintType
  source?: ComplaintSource
  status?: ComplaintStatus
  priority?: Priority
  orgId?: number
  submitterName?: string              // 投诉人姓名（模糊）
  startTime?: string
  endTime?: string
}

/** 投诉统计数据 */
export interface ComplaintStatistics {
  totalComplaints: number             // 总投诉数
  pendingCount: number                // 待处理数
  dispatchedCount: number             // 已派单数
  processingCount: number             // 处理中数
  closedCount: number                 // 已闭环数
  satisfactionRate: number            // 满意度（%）
}

/** 新增投诉表单 */
export interface CreateComplaintForm {
  complaintType: ComplaintType
  source: ComplaintSource
  title: string
  description?: string
  submitterId: number
  submitterName: string
  submitterPhone?: string
  relatedMenuId?: number
  relatedMenuName?: string
  images?: string[]
  priority?: Priority
  orgId: number
}

// ==================== 派单相关 ====================

/** 派单记录 */
export interface DispatchRecord {
  id: number
  dispatchNo: string                  // 派单编号
  complaintId: number                 // 关联投诉ID
  complaintNo: string                 // 投诉编号（冗余）
  complaintTitle?: string             // 投诉标题（冗余）
  dispatchType: DispatchType          // 派单方式
  assignerId?: number                 // 派单人ID
  assignerName?: string               // 派单人姓名
  handlerId: number                   // 处理人ID
  handlerName: string                 // 处理人姓名
  deadline?: string                   // 截止时间
  remark?: string                     // 派单备注
  status: DispatchStatus              // 状态
  handleResult?: string               // 处理结果
  handleImages?: string[]             // 处理图片
  completedAt?: string                // 完成时间
  // 优先级
  priority?: Priority
  orgId: number
  orgName: string
  createdAt: string
}

/** 派单查询参数 */
export interface DispatchQuery {
  pageNum: number
  pageSize: number
  dispatchType?: DispatchType
  status?: DispatchStatus
  orgId?: number
  handlerName?: string                // 处理人姓名（模糊）
  startTime?: string
  endTime?: string
}

/** 派单表单 */
export interface DispatchForm {
  dispatchType: DispatchType          // auto/manual
  handlerId?: number                  // 人工派单时必填
  priority?: Priority                 // 优先级
  deadline?: string                   // 截止时间
  remark?: string                     // 备注
}

/** 处理工单表单 */
export interface ProcessWorkOrderForm {
  action: 'process' | 'complete' | 'cancel'  // process=标记处理中，complete=完成，cancel=取消
  content: string                     // 处理内容
  images?: string[]                   // 处理图片
}

// ==================== 工单处理记录相关 ====================

/** 工单处理记录 */
export interface WorkOrderRecord {
  id: number
  dispatchId: number                  // 关联派单ID
  complaintId: number                 // 关联投诉ID
  action: WorkOrderAction             // 操作类型
  actionName: string                  // 操作名称
  operatorId: number                  // 操作人ID
  operatorName: string                // 操作人姓名
  content?: string                    // 操作内容
  images?: string[]                   // 处理图片
  createdAt: string                   // 操作时间
}

// ==================== 通用相关 ====================

/** 处理人选项 */
export interface HandlerOption {
  id: number
  name: string
  orgId?: number
  orgName?: string
  deptId?: number
  deptName?: string
  position?: string
}

/** 评分分布 */
export interface ScoreDistribution {
  scoreLevel: number
  scoreName: string
  count: number
  percentage: number
}

/** 热门标签 */
export interface HotTag {
  tagName: string
  count: number
  percentage: number
}

/** 积分排行 */
export interface PointsRanking {
  rankNo: number
  employeeId: number
  employeeName: string
  avatarUrl?: string
  orgName: string
  position?: string
  totalPoints: number
}
