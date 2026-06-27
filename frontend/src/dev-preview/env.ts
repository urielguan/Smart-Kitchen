const MOCK_TOKEN = 'dev-mock-token'
const MOCK_REFRESH_TOKEN = 'dev-mock-refresh-token'

export const isDevPreviewMockEnabled = () => {
  return import.meta.env.DEV && import.meta.env.VITE_ENABLE_LOCAL_MOCK === 'true'
}

export const seedDevPreviewAuth = () => {
  if (typeof window === 'undefined' || !isDevPreviewMockEnabled()) {
    return
  }

  if (!window.localStorage.getItem('token')) {
    window.localStorage.setItem('token', MOCK_TOKEN)
  }

  if (!window.localStorage.getItem('refreshToken')) {
    window.localStorage.setItem('refreshToken', MOCK_REFRESH_TOKEN)
  }
}

export const getDevPreviewToken = () => MOCK_TOKEN
export const getDevPreviewRefreshToken = () => MOCK_REFRESH_TOKEN
