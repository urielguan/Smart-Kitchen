import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { OfflineActionRecord } from '@/types'
import { storageKeys } from '@/utils/storage'
import { encrypt, decrypt } from '@/utils/crypto'
import { createOfflineActionId } from '@/utils/format'
import type { ConflictInfo } from '@/utils/conflict'

/** 从 localStorage 读取并解密队列 */
const readEncryptedQueue = (): OfflineActionRecord[] => {
  if (typeof window === 'undefined') return []
  const raw = window.localStorage.getItem(storageKeys.offlineQueue)
  if (!raw) return []
  try {
    const decrypted = decrypt(raw)
    return JSON.parse(decrypted) as OfflineActionRecord[]
  } catch {
    // 兼容旧的明文数据：尝试直接 JSON.parse
    try {
      return JSON.parse(raw) as OfflineActionRecord[]
    } catch {
      return []
    }
  }
}

export const useOfflineStore = defineStore('offline', () => {
  const queue = ref<OfflineActionRecord[]>(readEncryptedQueue())
  const syncing = ref(false)

  // 冲突状态（非持久化，页面刷新后重置）
  const activeConflict = ref<ConflictInfo | null>(null)
  const conflictRecordId = ref<string | null>(null)

  const pendingItems = computed(() => queue.value.filter((item) => item.syncStatus === 'pending'))
  const failedItems = computed(() => queue.value.filter((item) => item.syncStatus === 'failed' && !item.conflictPending))
  const conflictItems = computed(() => queue.value.filter((item) => item.conflictPending))
  const syncingItems = computed(() => queue.value.filter((item) => item.syncStatus === 'syncing'))
  const pendingCount = computed(() => pendingItems.value.length)
  const failedCount = computed(() => failedItems.value.length)
  const syncExceptionCount = computed(() => failedItems.value.length + conflictItems.value.length)
  const hasConflict = computed(() => activeConflict.value !== null)
  const hasSyncExceptions = computed(() => syncExceptionCount.value > 0)

  const persist = () => {
    const json = JSON.stringify(queue.value)
    window.localStorage.setItem(storageKeys.offlineQueue, encrypt(json))
  }

  const enqueue = (record: Omit<OfflineActionRecord, 'id' | 'createdAt' | 'syncStatus'>) => {
    queue.value.unshift({
      ...record,
      id: createOfflineActionId(),
      createdAt: new Date().toISOString(),
      syncStatus: 'pending',
      retryCount: record.retryCount ?? 0,
      nextRetryAt: record.nextRetryAt ?? null,
      conflictPending: record.conflictPending ?? false
    })
    persist()
  }

  const markSyncing = (id: string) => {
    const target = queue.value.find((item) => item.id === id)
    if (!target) {
      return
    }

    target.syncStatus = 'syncing'
    target.errorMessage = undefined
    target.nextRetryAt = null
    target.conflictPending = false
    persist()
  }

  const markFailed = (id: string, errorMessage: string, nextRetryAt?: string | null) => {
    const target = queue.value.find((item) => item.id === id)
    if (!target) {
      return
    }

    target.syncStatus = 'failed'
    target.errorMessage = errorMessage
    target.retryCount = (target.retryCount || 0) + 1
    target.nextRetryAt = nextRetryAt ?? null
    persist()
  }

  const markDone = (id: string) => {
    queue.value = queue.value.filter((item) => item.id !== id)
    persist()
  }

  const retryFailed = (id: string) => {
    const target = queue.value.find((item) => item.id === id)
    if (!target || target.syncStatus !== 'failed') return
    target.syncStatus = 'pending'
    target.errorMessage = undefined
    target.nextRetryAt = null
    target.conflictPending = false
    persist()
  }

  const removeFailed = (id: string) => {
    queue.value = queue.value.filter((item) => item.id !== id)
    persist()
  }

  const clearAllFailed = () => {
    queue.value = queue.value.filter((item) => item.syncStatus !== 'failed')
    persist()
  }

  // --- 冲突管理 ---

  const setConflict = (recordId: string, conflictInfo: ConflictInfo) => {
    conflictRecordId.value = recordId
    activeConflict.value = conflictInfo
    const target = queue.value.find((item) => item.id === recordId)
    if (target) {
      target.syncStatus = 'failed'
      target.conflictPending = true
      target.errorMessage = conflictInfo.serverMessage
      persist()
    }
  }

  const clearConflict = () => {
    conflictRecordId.value = null
    activeConflict.value = null
  }

  const resetSessionState = () => {
    queue.value = []
    syncing.value = false
    clearConflict()
    window.localStorage.removeItem(storageKeys.offlineQueue)
  }

  const resolveConflictDiscard = () => {
    if (conflictRecordId.value) {
      queue.value = queue.value.filter((item) => item.id !== conflictRecordId.value)
      persist()
    }
    clearConflict()
  }

  const resolveConflictForceRetry = () => {
    if (conflictRecordId.value) {
      const target = queue.value.find((item) => item.id === conflictRecordId.value)
      if (target) {
        target.syncStatus = 'pending'
        target.errorMessage = undefined
        persist()
      }
    }
    clearConflict()
  }

  const getReadyForRetry = () => {
    const now = Date.now()
    return queue.value.filter((item) => {
      if (item.syncStatus !== 'pending' && item.syncStatus !== 'failed') {
        return false
      }
      if (!item.nextRetryAt) {
        return true
      }
      return new Date(item.nextRetryAt).getTime() <= now
    })
  }

  return {
    queue,
    syncing,
    pendingItems,
    failedItems,
    conflictItems,
    syncingItems,
    pendingCount,
    failedCount,
    hasConflict,
    hasSyncExceptions,
    syncExceptionCount,
    activeConflict,
    conflictRecordId,
    enqueue,
    markSyncing,
    markFailed,
    markDone,
    retryFailed,
    removeFailed,
    clearAllFailed,
    setConflict,
    clearConflict,
    resetSessionState,
    resolveConflictDiscard,
    resolveConflictForceRetry,
    getReadyForRetry
  }
})
