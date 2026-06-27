import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { SupportedLocale, UserSession } from '@/types'
import { login, getCurrentUser, mockLogin } from '@/api/modules/auth'
import { useCookStore } from './cook'
import { useOfflineStore } from './offline'
import { clearSessionScopedStorage, readStorage, storageKeys, writeStorage } from '@/utils/storage'

const defaultLocale: SupportedLocale = 'zh-CN'

export const useAppStore = defineStore('app', () => {
  const locale = ref<SupportedLocale>(readStorage<SupportedLocale>(storageKeys.locale, defaultLocale))
  const session = ref<UserSession | null>(readStorage<UserSession | null>(storageKeys.session, null))
  const online = ref(typeof navigator !== 'undefined' ? navigator.onLine : true)

  const isAuthenticated = computed(() => Boolean(session.value?.accessToken))
  const currentRole = computed<'supervisor' | 'chef'>(() => {
    const roles = session.value?.roles || []
    const supervisorCodes = ['supervisor', 'manager', 'admin', '后厨主管', 'SUPER_ADMIN', 'ADMIN']
    return roles.some((role) => {
      const code = typeof role === 'string' ? role : (role as any).roleCode || (role as any).code || ''
      return supervisorCodes.includes(code)
    }) ? 'supervisor' : 'chef'
  })

  const setLocale = (nextLocale: SupportedLocale) => {
    locale.value = nextLocale
    writeStorage(storageKeys.locale, nextLocale)
  }

  const setOnline = (value: boolean) => {
    online.value = value
  }

  const resetSessionState = () => {
    const offlineStore = useOfflineStore()
    const cookStore = useCookStore()

    session.value = null
    clearSessionScopedStorage()
    offlineStore.resetSessionState()
    cookStore.resetSessionState()
  }

  const loginWithPassword = async (username: string, password: string) => {
    resetSessionState()
    let nextSession: UserSession

    try {
      // 第一步：登录获取 token
      const loginRes = await login({ username, password, deviceType: 'kitchen-terminal' })
      const token = loginRes.data.accessToken

      // 先保存 token 到临时 session，确保后续请求携带 Authorization header
      const tempSession: UserSession = {
        accessToken: token,
        refreshToken: loginRes.data.refreshToken,
        username,
        displayName: username,
        roles: ['chef'],
        locale: locale.value
      }
      session.value = tempSession
      writeStorage(storageKeys.session, tempSession)

      // 第二步：获取用户信息
      try {
        const meRes = await getCurrentUser()
        const me = meRes.data
        nextSession = {
          accessToken: token,
          refreshToken: loginRes.data.refreshToken,
          userId: me.userId,
          employeeId: me.employeeId,
          username: me.username || username,
          displayName: me.displayName || me.username || username,
          roles: me.roles || ['chef'],
          locale: me.locale || locale.value
        }
      } catch {
        // getMe 失败时用 loginRes 中已有的信息
        nextSession = {
          accessToken: token,
          refreshToken: loginRes.data.refreshToken,
          userId: loginRes.data.userId,
          employeeId: loginRes.data.employeeId,
          username: loginRes.data.username || username,
          displayName: loginRes.data.displayName || loginRes.data.nickname || username,
          roles: loginRes.data.roles || ['chef'],
          locale: loginRes.data.locale || locale.value
        }
      }
    } catch {
      // 后端不可用，使用 mock 登录
      const mockRes = mockLogin(username)
      nextSession = {
        accessToken: mockRes.data.accessToken!,
        userId: mockRes.data.userId,
        employeeId: mockRes.data.employeeId,
        username: mockRes.data.username || username,
        displayName: mockRes.data.displayName || username,
        roles: mockRes.data.roles || ['chef'],
        locale: mockRes.data.locale || locale.value
      }
    }

    session.value = nextSession
    writeStorage(storageKeys.session, nextSession)
    setLocale(nextSession.locale)
  }

  const logout = () => {
    resetSessionState()
  }

  return {
    locale,
    session,
    online,
    isAuthenticated,
    currentRole,
    setLocale,
    setOnline,
    resetSessionState,
    loginWithPassword,
    logout
  }
})
