import axios, { AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/modules/app'
import type { ApiResponse } from '@/types'
import { storageKeys, readStorage, writeStorage } from '@/utils/storage'
import type { UserSession } from '@/types'

/** 同步期间抑制重复错误提示 */
let suppressErrorMessages = false

export const setSuppressErrors = (value: boolean) => {
  suppressErrorMessages = value
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// ====== Token 自动续期 ======
let refreshPromise: Promise<void> | null = null

function decodeTokenExp(token: string): number | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    // JWT 使用 base64url 编码，需要替换字符后再解码
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    while (base64.length % 4 !== 0) base64 += '='
    const payload = JSON.parse(atob(base64))
    return typeof payload.exp === 'number' ? payload.exp : null
  } catch {
    return null
  }
}

function isTokenExpiringSoon(token: string, thresholdSeconds = 300): boolean {
  const exp = decodeTokenExp(token)
  if (!exp) return true // 无法解析视为即将过期
  return Date.now() / 1000 > exp - thresholdSeconds
}

const redirectToLogin = () => {
  const appStore = useAppStore()
  appStore.resetSessionState()
  window.location.href = '/login'
}

async function tryRefreshToken(): Promise<void> {
  const session = readStorage<UserSession | null>(storageKeys.session, null)
  if (!session?.refreshToken) {
    // 无 refreshToken，跳转登录
    redirectToLogin()
    return
  }

  try {
    const res = await axios.post<ApiResponse<{ accessToken: string; refreshToken?: string }>>(
      (import.meta.env.VITE_API_BASE_URL || '/api') + '/v1/auth/token/refresh',
      { refreshToken: session.refreshToken }
    )
    if (res.data?.code === 'SUCCESS' && res.data.data?.accessToken) {
      session.accessToken = res.data.data.accessToken
      if (res.data.data.refreshToken) {
        session.refreshToken = res.data.data.refreshToken
      }
      writeStorage(storageKeys.session, session)
    } else {
      throw new Error('refresh failed')
    }
  } catch {
    redirectToLogin()
  }
}

/** 生成 Trace ID，兼容非安全上下文（HTTP + 非 localhost） */
const generateTraceId = (): string =>
  typeof crypto?.randomUUID === 'function'
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => (Math.random() * 16 | 0).toString(16))

service.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // 跳过认证相关请求的 token 续期检查，避免旧 token 刷新失败阻塞登录
    if (config.url?.includes('/token/refresh') || config.url?.includes('/auth/login')) {
      if (config.headers) {
        config.headers['X-Trace-Id'] = generateTraceId()
      }
      return config
    }

    const session = readStorage<UserSession | null>(storageKeys.session, null)
    if (session?.accessToken) {
      if (isTokenExpiringSoon(session.accessToken)) {
        if (!refreshPromise) {
          refreshPromise = tryRefreshToken().finally(() => { refreshPromise = null })
        }
        await refreshPromise
        // 重新读取更新后的 session
        const updated = readStorage<UserSession | null>(storageKeys.session, null)
        if (updated?.accessToken && config.headers) {
          config.headers.Authorization = `Bearer ${updated.accessToken}`
        }
      } else if (config.headers) {
        config.headers.Authorization = `Bearer ${session.accessToken}`
      }
    }

    if (config.headers) {
      config.headers['X-Trace-Id'] = generateTraceId()
    }

    if (config.data instanceof FormData && config.headers) {
      delete config.headers['Content-Type']
    }

    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    if (res.code !== 'SUCCESS' && res.code !== '200') {
      if (res.code === 'UNAUTHORIZED' || res.code === 'TOKEN_EXPIRED' || res.code === '401') {
        redirectToLogin()
      }

      if (!suppressErrorMessages) {
        ElMessage.error(res.message || '请求失败')
      }
      const err = new Error(res.message || '请求失败')
      ;(err as any).response = response
      return Promise.reject(err)
    }

    return response
  },
  (error) => {
    let message = '网络错误，请稍后重试'

    if (error.response) {
      switch (error.response.status) {
        case 401:
          message = '未授权，请重新登录'
          redirectToLogin()
          break
        case 403:
          message = '无权限访问'
          break
        case 404:
          message = '请求资源不存在'
          break
        case 500:
          message = '服务器错误'
          break
        default:
          message = error.response.data?.message || '请求失败'
      }
    }

    if (!suppressErrorMessages) {
      ElMessage.error(message)
    }
    const err = new Error(message)
    ;(err as any).response = error.response
    return Promise.reject(err)
  }
)

export const request = <T = unknown>(config: AxiosRequestConfig): Promise<ApiResponse<T>> => {
  return service.request<unknown, AxiosResponse<ApiResponse<T>>>(config).then((res) => res.data)
}

export const get = <T = unknown>(url: string, params?: unknown): Promise<ApiResponse<T>> => {
  return request<T>({ method: 'GET', url, params })
}

export const post = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
  return request<T>({ method: 'POST', url, data, ...config })
}

export default service
