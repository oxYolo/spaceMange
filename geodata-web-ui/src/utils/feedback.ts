import { ElMessage } from 'element-plus'
import { AppRequestError, getErrorMessage } from '@/api/error'

const recentMessages = new Map<string, number>()

/** 页面统一提示入口：取消静默，同一故障在并发请求中只提示一次。 */
export function showError(error: unknown, fallback?: string): void {
  if (error instanceof AppRequestError && error.silent) return
  const message = getErrorMessage(error, fallback)
  if (!message) return
  const now = Date.now()
  for (const [text, timestamp] of recentMessages) {
    if (now - timestamp >= 1500) recentMessages.delete(text)
  }
  if (recentMessages.has(message)) return
  recentMessages.set(message, now)
  ElMessage.error(message)
}
