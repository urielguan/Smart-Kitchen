import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResponse } from '@/types'
import { isDevBypassLoginEnabled } from '@/utils/dev-auth'

export interface RequestConfig extends AxiosRequestConfig {
  silentError?: boolean
}

let lastErrorToastMessage = ''
let lastErrorToastAt = 0
const ERROR_TOAST_DEDUPE_MS = 400
const rawErrorMessage = ElMessage.error.bind(ElMessage)

// ==================== 强制下线统一处理 ====================
let forceLogoutShown = false
const handleForceLogout = (message: string) => {
  if (forceLogoutShown) return
  forceLogoutShown = true
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  ElMessageBox.alert(message, '提示', {
    confirmButtonText: '确定',
    type: 'warning',
    showClose: false,
    closeOnClickModal: false,
    closeOnPressEscape: false
  }).then(() => {
    forceLogoutShown = false
    window.location.href = '/login'
  })
}
export { handleForceLogout }

const showErrorMessage = (message: string) => {
  const now = Date.now()
  if (message === lastErrorToastMessage && now - lastErrorToastAt < ERROR_TOAST_DEDUPE_MS) {
    return
  }

  lastErrorToastMessage = message
  lastErrorToastAt = now
  rawErrorMessage(message)
}

ElMessage.error = showErrorMessage as typeof ElMessage.error

const resolveBackendErrorMessage = (responseData: any): string => {
  if (responseData?.code === 'VALIDATION_FAILED') {
    const fieldError = responseData.data?.fieldErrors?.find(
      (item: any) => typeof item?.message === 'string' && item.message.trim()
    )
    if (fieldError?.message?.trim()) {
      return fieldError.message.trim()
    }
  }

  if (responseData && typeof responseData.message === 'string' && responseData.message.trim()) {
    return responseData.message.trim()
  }
  return ''
}

const FRIENDLY_OPERATION_ERROR = '操作失败，请检查网络或稍后重试'
const FRIENDLY_TIMEOUT_ERROR = '服务器响应超时，请稍后重试'

/** 创建 Axios 实例 */
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 90000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// ==================== Token 刷新相关 ====================

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

/** 从JWT中解析过期时间（毫秒时间戳） */
const getTokenExpireAt = (token: string): number | null => {
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null

    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const payload = JSON.parse(atob(padded))

    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

/** 判断Token是否已过期（提前5秒进入刷新，避免临界并发） */
const isTokenExpired = (token: string): boolean => {
  const expireAt = getTokenExpireAt(token)
  if (!expireAt) return false
  return Date.now() >= expireAt - 5000
}

/** 处理 refreshToken 并重试挂起的请求 */
const handleTokenRefresh = async (): Promise<string> => {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) {
    return Promise.reject(new Error('no refresh token'))
  }

  // 用独立的 axios 实例发刷新请求，避免触发当前实例的拦截器
  const res = await axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 10000
  }).post<ApiResponse<{ accessToken: string; refreshToken: string; expiresIn: number }>>(
    '/v1/auth/token/refresh',
    { refreshToken }
  )

  const data = res.data
  if (data.code !== 'SUCCESS' || !data.data) {
    return Promise.reject(new Error(data.message || '刷新Token失败'))
  }

  const newToken = data.data.accessToken
  const newRefreshToken = data.data.refreshToken

  localStorage.setItem('token', newToken)
  localStorage.setItem('refreshToken', newRefreshToken)

  return newToken
}

/** 将挂起的请求加入队列，等刷新完成后统一重试 */
const addPendingRequest = (resolve: (token: string) => void) => {
  pendingRequests.push(resolve)
}

/** 刷新成功后，通知所有挂起的请求 */
const resolvePendingRequests = (token: string) => {
  pendingRequests.forEach(resolve => resolve(token))
  pendingRequests = []
}

/** 刷新失败后，拒绝所有挂起的请求 */
const rejectPendingRequests = () => {
  pendingRequests.forEach(resolve => resolve(''))  // 空 token 会让后续请求走登录
  pendingRequests = []
}

/** 清除认证状态并跳转登录页 */
const redirectToLogin = () => {
  if (isDevBypassLoginEnabled()) {
    return
  }
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  window.location.href = '/login'
}

// ==================== 请求拦截器 ====================

service.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const url = config.url || ''
    const shouldSkipPreRefresh = url.includes('/v1/auth/login') || url.includes('/v1/auth/token/refresh')

    // 从 localStorage 获取 token
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      if (!shouldSkipPreRefresh && isTokenExpired(token)) {
        if (isRefreshing) {
          const refreshedToken = await new Promise<string>((resolve) => {
            addPendingRequest(resolve)
          })
          if (!refreshedToken) {
            redirectToLogin()
            return Promise.reject(new Error('登录已过期，请重新登录'))
          }
          config.headers['Authorization'] = `Bearer ${refreshedToken}`
        } else {
          isRefreshing = true
          try {
            const newToken = await handleTokenRefresh()
            resolvePendingRequests(newToken)
            config.headers['Authorization'] = `Bearer ${newToken}`
          } catch {
            rejectPendingRequests()
            redirectToLogin()
            return Promise.reject(new Error('登录已过期，请重新登录'))
          } finally {
            isRefreshing = false
          }
        }
      } else {
        config.headers['Authorization'] = `Bearer ${token}`
      }
    }

    // 如果是 FormData，删除 Content-Type 让浏览器自动设置 multipart boundary
    if (config.data instanceof FormData && config.headers) {
      delete config.headers['Content-Type']
    }

    // 添加来源终端 header（审计日志使用）
    if (config.headers) {
      config.headers['X-Source-Terminal'] = 'Web'
    }

    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// ==================== 响应拦截器 ====================

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const requestConfig = response.config as RequestConfig
    // blob 响应（文件下载）直接放行，不走 code 检查
    if (response.config.responseType === 'blob') {
      return response
    }

    const res = response.data

    // code 不为 SUCCESS 则为错误
    if (res.code !== 'SUCCESS') {
      // Token 过期：尝试刷新
      if (res.code === 'TOKEN_EXPIRED') {
        const config = response.config

        if (isRefreshing) {
          // 已有刷新请求进行中，排队等待
          return new Promise<AxiosResponse<ApiResponse>>((resolve) => {
            addPendingRequest((token: string) => {
              if (token) {
                config.headers['Authorization'] = `Bearer ${token}`
              }
              resolve(service.request(config))
            })
          })
        }

        isRefreshing = true
        return new Promise<AxiosResponse<ApiResponse>>((resolve, reject) => {
          handleTokenRefresh()
            .then((newToken) => {
              // 刷新成功，重试原请求
              config.headers['Authorization'] = `Bearer ${newToken}`
              resolvePendingRequests(newToken)
              resolve(service.request(config))
            })
            .catch(() => {
              // 刷新失败，跳转登录
              rejectPendingRequests()
              redirectToLogin()
              reject(new Error('登录已过期，请重新登录'))
            })
            .finally(() => {
              isRefreshing = false
            })
        })
      }

      if (!requestConfig.silentError) {
        showErrorMessage(res.message || FRIENDLY_OPERATION_ERROR)
      }

      // 未授权/Token无效：直接跳转登录页
      if (res.code === 'UNAUTHORIZED' || res.code === 'TOKEN_INVALID') {
        redirectToLogin()
      }

      const businessError = new Error(resolveBackendErrorMessage(res) || res.message || '请求失败') as Error & { backendPayload?: unknown }
      businessError.backendPayload = res
      return Promise.reject(businessError)
    }

    return response
  },
  (error) => {
    let message = FRIENDLY_OPERATION_ERROR
    const requestConfig = (error.config || {}) as RequestConfig
    if (!requestConfig.silentError) {
      console.error('Response error:', error)
    }
    if (error.response) {
      const status = error.response.status
      const resData = error.response.data

      // 强制下线：不尝试刷新，直接弹窗提示并跳转登录
      if (status === 401 && resData?.code === 'FORCE_LOGOUT') {
        handleForceLogout(resData.message || '您已被强制下线')
        return Promise.reject(new Error(resData.message || '您已被强制下线'))
      }

      // Token 过期：尝试刷新（后端 TOKEN_EXPIRED 返回 HTTP 401，走 error 路径）
      if (status === 401 && resData?.code === 'TOKEN_EXPIRED') {
        const config = error.config

        if (isRefreshing) {
          return new Promise<AxiosResponse<ApiResponse>>((resolve) => {
            addPendingRequest((token: string) => {
              if (token) {
                config.headers['Authorization'] = `Bearer ${token}`
              }
              resolve(service.request(config))
            })
          })
        }

        isRefreshing = true
        return new Promise<AxiosResponse<ApiResponse>>((resolve, reject) => {
          handleTokenRefresh()
            .then((newToken) => {
              config.headers['Authorization'] = `Bearer ${newToken}`
              resolvePendingRequests(newToken)
              resolve(service.request(config))
            })
            .catch(() => {
              rejectPendingRequests()
              redirectToLogin()
              reject(new Error('登录已过期，请重新登录'))
            })
            .finally(() => {
              isRefreshing = false
            })
        })
      }

      switch (status) {
        case 400:
          message = resolveBackendErrorMessage(resData) || '请求参数错误'
          break
        case 401:
          message = resolveBackendErrorMessage(resData) || '未授权，请重新登录'
          redirectToLogin()
          break
        case 403:
          message = resolveBackendErrorMessage(resData) || '拒绝访问'
          break
        case 404:
          message = resolveBackendErrorMessage(resData) || '请求的资源不存在'
          break
        case 409:
          message = resolveBackendErrorMessage(resData) || '数据冲突，请刷新后重试'
          break
        case 422:
          message = resolveBackendErrorMessage(resData) || '数据校验失败'
          break
        case 500:
          message = resolveBackendErrorMessage(resData) || '服务器内部错误'
          break
        case 504:
          message = resolveBackendErrorMessage(resData) || FRIENDLY_TIMEOUT_ERROR
          break
        default:
          message = resolveBackendErrorMessage(resData) || FRIENDLY_OPERATION_ERROR
      }
    } else if (error.code === 'ECONNABORTED' || typeof error.message === 'string' && error.message.toLowerCase().includes('timeout')) {
      message = FRIENDLY_TIMEOUT_ERROR
    }

    if (!requestConfig.silentError) {
      showErrorMessage(message)
    }
    // 将错误消息传递给调用方，避免调用方再次显示通用错误
    const businessError = new Error(message) as Error & { backendPayload?: unknown }
    businessError.backendPayload = error.response?.data
    return Promise.reject(businessError)
  }
)

/** 通用请求方法 */
export const request = <T = any>(config: RequestConfig): Promise<ApiResponse<T>> => {
  return service.request<any, AxiosResponse<ApiResponse<T>>>(config).then(res => res.data)
}

/** GET 请求 */
export const get = <T = any>(url: string, params?: any, config?: RequestConfig): Promise<ApiResponse<T>> => {
  return request<T>({ method: 'GET', url, params, ...config })
}

/** POST 请求 */
export const post = <T = any>(url: string, data?: any, config?: RequestConfig): Promise<ApiResponse<T>> => {
  return request<T>({ method: 'POST', url, data, ...config })
}

/** PUT 请求 */
export const put = <T = any>(url: string, data?: any, config?: RequestConfig): Promise<ApiResponse<T>> => {
  return request<T>({ method: 'PUT', url, data, ...config })
}

/** DELETE 请求 */
export const del = <T = any>(url: string, params?: any, config?: RequestConfig): Promise<ApiResponse<T>> => {
  return request<T>({ method: 'DELETE', url, params, ...config })
}

export default service
