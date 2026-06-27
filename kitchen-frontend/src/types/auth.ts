export type SupportedLocale = 'zh-CN' | 'en-US'

export interface UserSession {
  accessToken: string
  refreshToken?: string
  userId?: number | string
  employeeId?: number | string
  username: string
  displayName: string
  roles: string[]
  locale: SupportedLocale
}

export interface LoginPayload {
  username: string
  password: string
  deviceType?: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken?: string
  userId?: number | string
  employeeId?: number | string
  username?: string
  nickname?: string
  displayName?: string
  roles?: string[]
  locale?: SupportedLocale
}
