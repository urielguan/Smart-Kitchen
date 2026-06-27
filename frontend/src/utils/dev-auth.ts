const DEV_BYPASS_LOGIN =
  import.meta.env.DEV && import.meta.env.VITE_BYPASS_LOGIN === 'true'

const DEFAULT_DEV_TOKEN =
  'eyJhbGciOiJIUzM4NCJ9.eyJyZWFsTmFtZSI6Iuezu-e7n-euoeeQhuWRmCIsInJvbGVzIjoiYWRtaW4iLCJ0ZW5hbnRJZCI6MSwidHlwZSI6ImFjY2VzcyIsInVzZXJJZCI6OSwib3JnSWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3ODAzOTA1OTUsImV4cCI6NDEwMjQxNTk5OX0.FZ3x5kmxh3O9RGS2J4X8hQe7XSfS7ey_SSkln-ubkxv-xrj5_17iRlBj3rWCGncA'

const DEV_TOKEN = import.meta.env.VITE_DEV_BYPASS_TOKEN || DEFAULT_DEV_TOKEN

const DEV_USER = {
  id: 9,
  userName: 'admin',
  realName: '系统管理员',
  orgId: 1,
  orgName: '智慧食安集团总部',
  roles: ['admin'],
  permissions: ['*']
}

export const isDevBypassLoginEnabled = (): boolean => DEV_BYPASS_LOGIN

export const getDevBypassToken = (): string => DEV_TOKEN

export const getDevBypassUser = () => DEV_USER

export const ensureDevBypassAuth = (): void => {
  if (!DEV_BYPASS_LOGIN || typeof window === 'undefined') return
  localStorage.setItem('token', DEV_TOKEN)
  localStorage.removeItem('refreshToken')
  localStorage.setItem('devBypassAuthApplied', '1')
}

export const clearDevBypassAuth = (): void => {
  if (typeof window === 'undefined') return

  const currentToken = localStorage.getItem('token')
  const bypassApplied = localStorage.getItem('devBypassAuthApplied') === '1'
  if (bypassApplied || currentToken === DEV_TOKEN) {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
  }
  localStorage.removeItem('devBypassAuthApplied')
}
