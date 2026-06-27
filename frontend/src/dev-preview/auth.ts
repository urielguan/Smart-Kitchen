import type { ApiResponse } from '@/types'
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  TokenRefreshRequest,
  TokenResponse,
  UpdateProfileRequest,
  UserInfoResponse,
} from '@/api/modules/auth'
import {
  getDevPreviewRefreshToken,
  getDevPreviewToken,
} from './env'

const now = () => new Date().toISOString()

const buildUser = (): UserInfoResponse => ({
  userId: 1,
  username: 'admin',
  realName: '本地预览管理员',
  email: 'preview@example.com',
  phone: '13800000000',
  avatarUrl: '',
  gender: 1,
  orgId: 1,
  orgName: '示范校区中央厨房',
  tenantId: 1,
  status: 'ENABLED',
  lastLoginAt: now(),
  roles: [
    {
      roleId: 1,
      roleCode: 'admin',
      roleName: 'admin',
      roleDesc: '本地预览管理员',
    },
  ],
  permissions: [
    {
      permissionId: 1,
      permissionCode: '*',
      permissionName: '全部权限',
      permissionType: 'MENU',
      resourcePath: '*',
    },
  ],
})

const success = <T>(data: T, message = 'SUCCESS'): ApiResponse<T> => ({
  code: 'SUCCESS',
  message,
  data,
  timestamp: now(),
})

export const devPreviewAuthApi = {
  async login(_data: LoginRequest): Promise<ApiResponse<LoginResponse>> {
    return success({
      accessToken: getDevPreviewToken(),
      refreshToken: getDevPreviewRefreshToken(),
      expiresIn: 24 * 60 * 60,
    })
  },

  async refreshToken(_data: TokenRefreshRequest): Promise<ApiResponse<TokenResponse>> {
    return success({
      accessToken: getDevPreviewToken(),
      refreshToken: getDevPreviewRefreshToken(),
      expiresIn: 24 * 60 * 60,
    })
  },

  async logout(): Promise<ApiResponse<void>> {
    return success(undefined)
  },

  async getCurrentUser(): Promise<ApiResponse<UserInfoResponse>> {
    return success(buildUser())
  },

  async changePassword(_data: ChangePasswordRequest): Promise<ApiResponse<void>> {
    return success(undefined)
  },

  async updateProfile(_data: UpdateProfileRequest): Promise<ApiResponse<void>> {
    return success(undefined)
  },

  async uploadAvatar(file: File): Promise<ApiResponse<{ avatarUrl: string }>> {
    return success({
      avatarUrl: URL.createObjectURL(file),
    })
  },
}
