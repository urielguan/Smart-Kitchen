import { get, post } from '@/api'
import type { ApiResponse, LoginPayload, LoginResponse, UserSession } from '@/types'

export const login = async (payload: LoginPayload): Promise<ApiResponse<LoginResponse>> => {
  return await post<LoginResponse>('/v1/auth/login', payload)
}

export const getCurrentUser = async (): Promise<ApiResponse<UserSession>> => {
  return await get<UserSession>('/v1/auth/me')
}

/**
 * 模拟登录 fallback（后端不可用时使用）
 */
export const mockLogin = (username: string): ApiResponse<LoginResponse> => ({
  code: 'SUCCESS',
  message: 'mock login success',
  data: {
    accessToken: 'mock-kitchen-terminal-token',
    userId: username,
    employeeId: username,
    username,
    displayName: username,
    roles: username.includes('manager') || username.includes('supervisor') ? ['supervisor'] : ['chef'],
    locale: 'zh-CN'
  }
})
