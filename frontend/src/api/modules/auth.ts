import { get, post, put } from '../index'
import type { ApiResponse } from '@/types'
import { isDevPreviewMockEnabled } from '@/dev-preview/env'
import { devPreviewAuthApi } from '@/dev-preview/auth'

/** 登录请求参数 */
export interface LoginRequest {
  username: string
  password: string
  loginType?: string
  deviceId?: string
  deviceType?: string
}

/** 登录响应 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  mustChangePassword?: boolean
}

/** Token刷新请求 */
export interface TokenRefreshRequest {
  refreshToken: string
}

/** Token响应 */
export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

/** 角色信息 */
export interface RoleInfo {
  roleId: number
  roleCode: string
  roleName: string
  roleDesc?: string
}

/** 权限信息 */
export interface PermissionInfo {
  permissionId: number
  permissionCode: string
  permissionName: string
  permissionType?: string
  resourcePath?: string
}

/** 用户信息响应 */
export interface UserInfoResponse {
  userId: number
  username: string
  realName: string
  email?: string
  phone?: string
  avatarUrl?: string
  gender?: number
  orgId: number
  orgName?: string
  tenantId?: number
  status: string
  lastLoginAt?: string
  roles: RoleInfo[]
  permissions: PermissionInfo[]
}

/** 修改密码请求 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 强制修改密码请求（首次登录） */
export interface ForceChangePasswordRequest {
  newPassword: string
}

/** 修改个人信息请求 */
export interface UpdateProfileRequest {
  email?: string
  phone?: string
  gender?: number
  avatarUrl?: string
}

/**
 * 用户登录
 */
export const login = (data: LoginRequest): Promise<ApiResponse<LoginResponse>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewAuthApi.login(data)
  }
  return post<LoginResponse>('/v1/auth/login', data)
}

/**
 * 刷新Token
 */
export const refreshToken = (data: TokenRefreshRequest): Promise<ApiResponse<TokenResponse>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewAuthApi.refreshToken(data)
  }
  return post<TokenResponse>('/v1/auth/token/refresh', data)
}

/**
 * 用户登出
 */
export const logout = (): Promise<ApiResponse<void>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewAuthApi.logout()
  }
  return post<void>('/v1/auth/logout')
}

/**
 * 获取当前用户信息
 */
export const getCurrentUser = (): Promise<ApiResponse<UserInfoResponse>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewAuthApi.getCurrentUser()
  }
  return get<UserInfoResponse>('/v1/auth/me')
}

/**
 * 修改密码
 */
export const changePassword = (data: ChangePasswordRequest): Promise<ApiResponse<void>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewAuthApi.changePassword(data)
  }
  return put<void>('/v1/auth/password', data)
}

/**
 * 首次登录强制修改密码
 */
export const forceChangePassword = (data: ForceChangePasswordRequest): Promise<ApiResponse<void>> => {
  return put<void>('/v1/auth/password/force', data)
}

/**
 * 修改个人信息
 */
export const updateProfile = (data: UpdateProfileRequest): Promise<ApiResponse<void>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewAuthApi.updateProfile(data)
  }
  return put<void>('/v1/auth/profile', data)
}

/**
 * 上传头像
 */
export const uploadAvatar = (file: File): Promise<ApiResponse<{ avatarUrl: string }>> => {
  if (isDevPreviewMockEnabled()) {
    return devPreviewAuthApi.uploadAvatar(file)
  }
  const formData = new FormData()
  formData.append('file', file)
  return post<{ avatarUrl: string }>('/v1/auth/upload-avatar', formData)
}
