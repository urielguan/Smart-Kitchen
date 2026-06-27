const SESSION_KEY = 'kitchen-terminal:session'
const LOCALE_KEY = 'kitchen-terminal:locale'
const OFFLINE_QUEUE_KEY = 'kitchen-terminal:offline-queue'

export const storageKeys = {
  session: SESSION_KEY,
  locale: LOCALE_KEY,
  offlineQueue: OFFLINE_QUEUE_KEY
}

export const readStorage = <T>(key: string, fallback: T): T => {
  if (typeof window === 'undefined') {
    return fallback
  }

  const raw = window.localStorage.getItem(key)
  if (!raw) {
    return fallback
  }

  try {
    return JSON.parse(raw) as T
  } catch (error) {
    console.warn(`Failed to parse local storage key: ${key}`, error)
    return fallback
  }
}

export const writeStorage = (key: string, value: unknown) => {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem(key, JSON.stringify(value))
}

export const removeStorage = (key: string) => {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(key)
}

export const clearSessionScopedStorage = () => {
  removeStorage(storageKeys.session)
  removeStorage(storageKeys.offlineQueue)
}
