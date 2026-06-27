export interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T
  traceId?: string
  timestamp?: string
}

export interface PageQuery {
  pageNum: number
  pageSize: number
}

export interface PageResponse<T = unknown> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface PaginationState {
  pageNum: number
  pageSize: number
  total: number
}
