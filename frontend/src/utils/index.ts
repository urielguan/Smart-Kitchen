/**
 * 工具函数
 */

/** API 基础路径 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * 获取完整图片 URL
 * @param path 图片相对路径（如 /upload/materials/wms-service/2026/03/23/xxx.jpg）
 * @returns 完整可访问的 URL
 */
export function getImageUrl(path?: string): string {
  if (!path) return ''
  // 如果已经是完整 URL，直接返回
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }
  // 拼接 API 基础路径
  return API_BASE_URL + path
}

/**
 * 生成物料编码
 * @returns 物料编码
 */
export function generateMaterialCode(): string {
  return 'MAT-' + String(Date.now()).slice(-6)
}

/**
 * 判断是否临期
 * @param expiryDate 到期日期
 * @param warningDays 预警天数
 * @returns 是否临期
 */
export function isNearExpiry(expiryDate?: string, warningDays?: number): boolean {
  if (!expiryDate) return false
  const diff = (new Date(expiryDate).getTime() - Date.now()) / 86400000
  return diff >= 0 && diff <= (warningDays || 7)
}

/**
 * 格式化日期
 * @param date 日期
 * @param format 格式
 * @returns 格式化后的日期字符串
 */
export function formatDate(date: string | Date | null | undefined, format = 'YYYY-MM-DD'): string {
  if (!date) return ''
  const d = new Date(date)
  if (isNaN(d.getTime())) return ''
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 格式化日期时间
 * @param date 日期
 * @returns 格式化后的日期时间字符串
 */
export function formatDateTime(date: string | Date | null | undefined): string {
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

/**
 * 计算剩余天数
 * @param expiryDate 到期日期
 * @returns 剩余天数
 */
export function calcRemainingDays(expiryDate?: string): number {
  if (!expiryDate) return 0
  return Math.ceil((new Date(expiryDate).getTime() - Date.now()) / 86400000)
}

/**
 * 深拷贝
 * @param obj 对象
 * @returns 拷贝后的对象
 */
export function deepClone<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj))
}

/**
 * 防抖函数
 * @param fn 函数
 * @param delay 延迟时间
 * @returns 防抖后的函数
 */
export function debounce<T extends (...args: any[]) => any>(
  fn: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  return function (this: any, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/**
 * 节流函数
 * @param fn 函数
 * @param delay 延迟时间
 * @returns 节流后的函数
 */
export function throttle<T extends (...args: any[]) => any>(
  fn: T,
  delay: number
): (...args: Parameters<T>) => void {
  let lastTime = 0
  return function (this: any, ...args: Parameters<T>) {
    const now = Date.now()
    if (now - lastTime >= delay) {
      lastTime = now
      fn.apply(this, args)
    }
  }
}