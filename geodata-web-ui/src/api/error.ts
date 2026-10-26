export type RequestErrorKind = 'http' | 'business' | 'network' | 'cancel'

/** 保留错误类别和状态，调用方无需再解析 Axios 的内部结构。 */
export class AppRequestError extends Error {
  readonly kind: RequestErrorKind
  readonly code?: number | string
  readonly status?: number
  readonly silent: boolean
  readonly originalError?: unknown

  constructor(message: string, options: {
    kind: RequestErrorKind
    code?: number | string
    status?: number
    silent?: boolean
    originalError?: unknown
  }) {
    super(message)
    this.name = 'AppRequestError'
    this.kind = options.kind
    this.code = options.code
    this.status = options.status
    this.silent = options.silent ?? false
    this.originalError = options.originalError
  }
}

export function isRequestCanceled(error: unknown): boolean {
  if (error instanceof AppRequestError) return error.kind === 'cancel'
  if (!error || typeof error !== 'object') return error === 'cancel' || error === 'close'
  const candidate = error as { code?: string; name?: string; __CANCEL__?: boolean }
  return candidate.code === 'ERR_CANCELED' || candidate.name === 'AbortError' || candidate.__CANCEL__ === true
}

export function getErrorMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  if (isRequestCanceled(error)) return ''
  if (error instanceof Error && error.message) return error.message
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message || fallback
  }
  return fallback
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof AppRequestError && (error.status === 401 || Number(error.code) === 401)
}
