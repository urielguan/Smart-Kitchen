/** 通用响应结构 */
export interface ApiResponse<T = any> {
  code: string
  message: string
  data: T
  traceId?: string
  timestamp?: string
}

/** 分页请求参数 */
export interface PageQuery {
  pageNum: number
  pageSize: number
}

/** 分页响应结构 */
export interface PageResponse<T = any> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/** 通用 ID 参数 */
export interface IdParam {
  id: number
}