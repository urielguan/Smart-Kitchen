import { ref, watch, type ComputedRef } from 'vue'
import { ElNotification } from 'element-plus'
import type { CookTask } from '@/types'

/**
 * 厨房终端异常告警通知 composable
 * 监听 alerts 列表变化，新增告警时播放音效 + 浏览器通知 + 应用内弹窗
 */
export function useAlertNotification(alerts: ComputedRef<readonly CookTask[]>) {
  const enabled = ref(true)
  const prevAlertIds = new Set<number>()

  const requestPermission = async () => {
    if ('Notification' in window && Notification.permission === 'default') {
      await Notification.requestPermission()
    }
  }

  watch(
    alerts,
    (newAlerts) => {
      if (!enabled.value) return

      const newIds: number[] = []
      const newAlertTasks: CookTask[] = []
      for (const task of newAlerts) {
        if (task.id != null && !prevAlertIds.has(task.id)) {
          newIds.push(task.id)
          newAlertTasks.push(task)
        }
      }

      // Update seen set with all current alert IDs
      prevAlertIds.clear()
      for (const task of newAlerts) {
        if (task.id != null) prevAlertIds.add(task.id)
      }

      if (newIds.length === 0) return

      // Play alert sound
      try {
        const audio = new Audio('/alert.mp3')
        audio.volume = 0.6
        audio.play().catch(() => {
          // Autoplay blocked — ignore
        })
      } catch {
        // Audio not available
      }

      // Browser notification
      if ('Notification' in window && Notification.permission === 'granted') {
        try {
          new Notification('后厨告警', {
            body: `${newIds.length} 个任务出现异常，请及时处理`,
            icon: '/favicon.ico',
            tag: 'kitchen-alert'
          })
        } catch {
          // Notification API not available
        }
      }

      // In-app popup notification (ElNotification)
      const taskNames = newAlertTasks
        .slice(0, 3)
        .map((t) => t.menuName)
        .join('、')
      const suffix = newIds.length > 3 ? ` 等 ${newIds.length} 个任务` : ''
      ElNotification({
        title: '后厨异常告警',
        message: `${taskNames}${suffix} 出现异常，请及时处理`,
        type: 'warning',
        duration: 8000,
        position: 'top-right'
      })
    },
    { deep: true }
  )

  return { requestPermission, enabled }
}
