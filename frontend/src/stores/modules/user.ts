import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/modules/auth'
import { handleForceLogout } from '@/api/index'
import { getDevBypassToken, getDevBypassUser, isDevBypassLoginEnabled } from '@/utils/dev-auth'

const MENU_PERMISSION_MAP: Record<string, string> = {
  '/dashboard': 'dashboard',
  '/supplier': 'supplier',
  '/purchase-plan': 'purchasePlan',
  '/purchase-demand-forecast': 'purchasePlan',
  '/purchase-plan/purchase-demand-forecast': 'purchasePlan',
  '/purchase': 'purchase',
  '/warehouse': 'warehouse',
  '/material': 'material',
  '/inventory': 'inventory',
  '/inbound': 'inbound',
  '/outbound': 'outbound',
  '/stocktake': 'stocktake',
  '/recipe': 'recipe',
  '/plan': 'plan',
  '/plan-adjustment': 'plan-adjustment',
  '/cook/nutrition': 'cook/nutrition',
  '/cook/dashboard': 'cook/dashboard',
  '/cook': 'cook',
  '/sample': 'sample',
  '/morning-check': 'morning-check',
  '/video-monitor': 'video-monitor',
  '/video-playback': 'video-playback',
  '/violation': 'violation',
  '/behavior-analysis': 'behavior-analysis',
  '/device': 'device',
  '/alert': 'alert',
  '/org': 'org',
  '/employee': 'employee',
  '/dict-category': 'dict-category',
  '/role': 'role',
  '/evaluation': 'evaluation',
  '/notification': 'notification',
  '/ai-config': 'ai-config',
  '/integration-management': 'integration-management'
}

const MENU_ROUTE_ORDER = Object.keys(MENU_PERMISSION_MAP)

const HIDDEN_ROUTE_PERMISSION_RULES: Array<{
  test: (path: string) => boolean
  permission: string
}> = [
  { test: (path) => path === '/stocktake/create', permission: 'stocktake:create' },
  { test: (path) => /^\/stocktake\/\d+\/edit$/.test(path), permission: 'stocktake:edit' }
]

/** 用户信息 */
export interface UserInfo {
  id: number
  userName: string
  realName: string
  avatar?: string
  email?: string
  phone?: string
  gender?: number
  status?: string
  orgId: number | null
  orgName: string
  tenantId?: number
  lastLoginAt?: string
  roles: string[]
  permissions: string[]
}

export const useUserStore = defineStore('user', () => {
  /** 用户信息 */
  const userInfo = ref<UserInfo | null>(null)

  /** Token */
  const token = ref<string>(localStorage.getItem('token') || '')

  /** RefreshToken */
  const refreshTokenValue = ref<string>(localStorage.getItem('refreshToken') || '')

  /** 是否已登录 */
  const isLoggedIn = ref<boolean>(!!token.value)

  /** 是否需要强制修改密码 */
  const mustChangePassword = ref<boolean>(localStorage.getItem('mustChangePassword') === 'true')

  if (isDevBypassLoginEnabled()) {
    token.value = getDevBypassToken()
    isLoggedIn.value = true
    localStorage.setItem('token', token.value)
  }

  /** 设置 Token */
  const setToken = (newToken: string, newRefreshToken?: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
    isLoggedIn.value = true
    if (newRefreshToken) {
      refreshTokenValue.value = newRefreshToken
      localStorage.setItem('refreshToken', newRefreshToken)
    }
  }

  /** 设置用户信息 */
  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
  }

  /** 用户登录 */
  const login = async (username: string, password: string): Promise<void> => {
    const res = await authApi.login({ username, password })
    if (res.code === 'SUCCESS' && res.data) {
      // 只存储 token
      setToken(res.data.accessToken, res.data.refreshToken)
      // 判断是否需要强制修改密码
      mustChangePassword.value = !!res.data.mustChangePassword
      if (mustChangePassword.value) {
        localStorage.setItem('mustChangePassword', 'true')
      } else {
        // 立即获取完整用户信息
        await fetchUserInfo()
        startHeartbeat()
      }
    } else {
      throw new Error(res.message || '登录失败')
    }
  }

  /** 获取用户信息 */
  const fetchUserInfo = async (): Promise<void> => {
    if (isDevBypassLoginEnabled()) {
      setUserInfo(getDevBypassUser())
      return
    }

    const res = await authApi.getCurrentUser()
    if (res.code === 'SUCCESS' && res.data) {
      const roleNames = res.data.roles?.map(r => r.roleName) || []
      const permissionCodes = res.data.permissions?.map(p => p.permissionCode) || []
      setUserInfo({
        id: res.data.userId,
        userName: res.data.username,
        realName: res.data.realName,
        avatar: res.data.avatarUrl,
        email: res.data.email,
        phone: res.data.phone,
        gender: res.data.gender,
        status: res.data.status,
        orgId: res.data.orgId,
        orgName: res.data.orgName || '',
        tenantId: res.data.tenantId,
        lastLoginAt: res.data.lastLoginAt,
        roles: roleNames,
        permissions: permissionCodes
      })
    } else {
      throw new Error(res.message || '获取用户信息失败')
    }
  }

  /** 用户登出 */
  const logout = async (): Promise<void> => {
    if (isDevBypassLoginEnabled()) {
      setUserInfo(getDevBypassUser())
      setToken(getDevBypassToken())
      localStorage.removeItem('refreshToken')
      return
    }

    try {
      await authApi.logout()
    } catch (e) {
      // 忽略登出接口错误，仍然清除本地状态
      console.warn('登出接口调用失败', e)
    } finally {
      stopHeartbeat()
      // 清除本地状态
      token.value = ''
      refreshTokenValue.value = ''
      userInfo.value = null
      isLoggedIn.value = false
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('mustChangePassword')
    }
  }

  /** 清除登录状态（不调用后端） */
  const clearAuth = () => {
    if (isDevBypassLoginEnabled()) {
      setUserInfo(getDevBypassUser())
      setToken(getDevBypassToken())
      localStorage.removeItem('refreshToken')
      return
    }
    stopHeartbeat()
    token.value = ''
    refreshTokenValue.value = ''
    userInfo.value = null
    isLoggedIn.value = false
    mustChangePassword.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('mustChangePassword')
  }

  /** 获取用户名称 */
  const userName = computed(() => userInfo.value?.realName || userInfo.value?.userName || '用户')

  /** 是否管理员 */
  const isAdmin = (): boolean => {
    if (!userInfo.value) return false
    return userInfo.value.userName?.toLowerCase() === 'admin' || userInfo.value.roles.includes('admin')
  }

  /** 检查当前用户是否可访问指定页面路由 */
  const canAccessRoute = (path: string): boolean => {
    if (!userInfo.value) return false
    if (isAdmin()) return true
    if (path === '/dashboard') return true
    // 个人中心不做权限控制，登录用户可访问
    if (path === '/profile') return true

    const hiddenRule = HIDDEN_ROUTE_PERMISSION_RULES.find((rule) => rule.test(path))
    if (hiddenRule) {
      return userInfo.value.permissions.includes(hiddenRule.permission)
    }

    if (path === '/plan-adjustment') {
      return ['plan-adjustment', 'plan:adjust', 'plan-adjustment:approve']
        .some(permission => userInfo.value!.permissions.includes(permission))
    }

    if (path === '/integration-management') {
      return ['integration-management', 'integration-management:view-log', 'integration-management:view-callback', 'ai-config']
        .some(permission => userInfo.value!.permissions.includes(permission))
    }

    const permissionCode = MENU_PERMISSION_MAP[path]
    if (!permissionCode) return false
    return userInfo.value.permissions.includes(permissionCode)
  }

  /** 获取当前用户有权限的首个页面路由 */
  const getFirstAccessibleRoute = (): string | null => {
    if (!userInfo.value) return null
    if (isAdmin()) return MENU_ROUTE_ORDER[0] || null
    if (canAccessRoute('/dashboard')) return '/dashboard'
    const target = MENU_ROUTE_ORDER.find(path => canAccessRoute(path))
    return target || null
  }

  /** 检查用户是否拥有指定权限 */
  const hasPermission = (permission: string): boolean => {
    if (!userInfo.value) return true
    if (isAdmin()) return true
    const perms = userInfo.value.permissions
    if (perms.includes('*')) return true
    return perms.includes(permission)
  }

  /** 跨标签页同步：监听 localStorage 变化 */
  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (e: StorageEvent) => {
      if (e.key === 'token') {
        const newToken = e.newValue || ''
        if (newToken !== token.value) {
          // token 变化（登录其他账号或退出登录），刷新页面以同步状态
          window.location.reload()
        }
      }
    })
  }

  // ==================== 会话心跳检测（强制下线感知） ====================
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null

  /** 启动心跳：定期请求 auth-service 检测会话是否仍然有效 */
  const startHeartbeat = () => {
    stopHeartbeat()
    heartbeatTimer = setInterval(async () => {
      if (!token.value || document.hidden) return
      try {
        const res = await authApi.getCurrentUser()
        if (res.code === 'FORCE_LOGOUT') {
          handleForceLogout(res.message || '您已被强制下线')
        }
      } catch (error: any) {
        const payload = error?.backendPayload as any
        if (payload?.code === 'FORCE_LOGOUT') {
          handleForceLogout(payload.message || '您已被强制下线')
        }
      }
    }, 30_000)
  }

  /** 停止心跳 */
  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  /** 页面从后台切回前台时立即检测一次 */
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && token.value && heartbeatTimer) {
        authApi.getCurrentUser().then(res => {
          if (res.code === 'FORCE_LOGOUT') {
            handleForceLogout(res.message || '您已被强制下线')
          }
        }).catch((error: any) => {
          const payload = error?.backendPayload as any
          if (payload?.code === 'FORCE_LOGOUT') {
            handleForceLogout(payload.message || '您已被强制下线')
          }
        })
      }
    })
  }

  return {
    userInfo,
    token,
    refreshTokenValue,
    isLoggedIn,
    mustChangePassword,
    userName,
    isAdmin,
    canAccessRoute,
    getFirstAccessibleRoute,
    hasPermission,
    setToken,
    setUserInfo,
    login,
    fetchUserInfo,
    logout,
    clearAuth
  }
})
