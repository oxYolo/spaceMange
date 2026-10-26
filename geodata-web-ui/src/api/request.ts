import axios, { type AxiosRequestConfig, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import urlConfig from '@/config'
import { getSessionVersion, getToken, invalidateSession } from '@/auth/session'
import { AppRequestError, isRequestCanceled } from './error'

export { AppRequestError, getErrorMessage, isRequestCanceled, isUnauthorized } from './error'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 兼容旧调用；请求层不弹提示，提示由页面统一处理。 */
    silent?: boolean
    /** 请求发起时的会话版本，由拦截器设置。 */
    sessionVersion?: number
  }
}

/** 统一返回体外壳：{ code, success, result, msg } */
export interface ResultEnvelope<T = any> {
  code?: number | string
  success?: boolean
  result?: T
  msg?: string
}

/** 裸返回判定：后端有大量端点没有 Result 外壳，直接返回数据体 */
export function isEnvelope(v: unknown): v is ResultEnvelope {
  return (
    !!v &&
    typeof v === 'object' &&
    !Array.isArray(v) &&
    ('code' in v || 'success' in v) &&
    'result' in v
  )
}

function bodyMessage(body: unknown): string {
  if (!body || typeof body !== 'object') return ''
  const record = body as Record<string, unknown>
  return typeof record.msg === 'string' && record.msg.trim() ? record.msg
    : typeof record.message === 'string' ? record.message : ''
}

/** 认证接口使用 {code, data/user}，业务接口使用 Result；两种错误都需识别。 */
function businessError(body: unknown): AppRequestError | undefined {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return
  const record = body as Record<string, unknown>
  const hasStatus = isEnvelope(body) || typeof record.code === 'number'
    || (typeof record.code === 'string' && /^\d+$/.test(record.code)
      && ('msg' in record || 'message' in record || 'success' in record))
  if (!hasStatus) return
  if (record.success !== false && (record.code === undefined || Number(record.code) === 200)) return
  return new AppRequestError(bodyMessage(body) || '操作失败', {
    kind: 'business',
    code: typeof record.code === 'number' || typeof record.code === 'string' ? record.code : undefined,
  })
}

function sentToken(config?: InternalAxiosRequestConfig): string {
  const authorization = config?.headers.get('Authorization')
  return typeof authorization === 'string' && authorization.startsWith('Bearer ') ? authorization.slice(7) : ''
}

function expireIfUnauthorized(error: AppRequestError, config?: InternalAxiosRequestConfig): AppRequestError {
  if (error.status !== 401 && Number(error.code) !== 401) return error
  const token = sentToken(config)
  if (!token) return error // 登录失败属于表单错误，不触发会话失效。
  invalidateSession(token, config?.sessionVersion)
  return new AppRequestError(error.message, {
    kind: error.kind, code: error.code, status: error.status, originalError: error.originalError,
    silent: true, // 由单次会话失效通知统一提示，旧会话请求同样静默。
  })
}

const service: AxiosInstance = axios.create({
  baseURL: urlConfig.baseUrl,
  timeout: 120000,
})

service.interceptors.request.use(
  (config) => {
    const skipToken = config.headers.get('isToken') === false
    config.headers.delete('isToken')
    const token = getToken()
    config.sessionVersion = getSessionVersion()
    if (!skipToken && token) config.headers.set('Authorization', 'Bearer ' + token)
    return config
  },
  (error) => Promise.reject(error),
)

service.interceptors.response.use(
  (res) => {
    // 二进制直接返回
    if (res.config.responseType === 'blob' || res.config.responseType === 'arraybuffer') return res.data
    const error = businessError(res.data)
    if (error) throw expireIfUnauthorized(error, res.config)
    return res.data
  },
  (error) => {
    if (error instanceof AppRequestError) return Promise.reject(error)
    if (isRequestCanceled(error)) {
      return Promise.reject(new AppRequestError('请求已取消', { kind: 'cancel', silent: true, originalError: error }))
    }
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    const body = axios.isAxiosError(error) ? error.response?.data : undefined
    const config = axios.isAxiosError(error) ? error.config : undefined
    const timeout = axios.isAxiosError(error) && (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT')
    const message = bodyMessage(body) || (status ? `请求失败（HTTP ${status}）`
      : timeout ? '请求超时，请稍后重试' : '网络异常，请检查网络和后端服务')
    return Promise.reject(expireIfUnauthorized(new AppRequestError(message, {
      kind: status ? 'http' : 'network', status,
      code: axios.isAxiosError(error) ? error.code : undefined,
      originalError: error,
    }), config))
  },
)

/** 解包：有外壳取 result，裸返回原样返回 */
export async function unwrap<T = any>(p: Promise<any>): Promise<T> {
  const r = await p
  const error = businessError(r)
  if (error) throw error
  if (isEnvelope(r)) {
    return (r.result ?? null) as T
  }
  return r as T
}

/** 对象转 multipart/form-data */
function toFormData(data: Record<string, any>): FormData {
  const fd = new FormData()
  Object.entries(data || {}).forEach(([k, v]) => {
    if (v === undefined || v === null) return
    if (v instanceof Blob || typeof v === 'string') {
      fd.append(k, v as any)
    } else if (typeof v === 'object') {
      fd.append(k, JSON.stringify(v))
    } else {
      fd.append(k, String(v))
    }
  })
  return fd
}

export interface ReqOptions extends AxiosRequestConfig {
  /** 兼容旧调用；页面决定是否提示，请求层不会重复弹窗。 */
  silent?: boolean
}

export const http = {
  /** GET 查询参数 */
  get<T = any>(url: string, params?: Record<string, any>, opts?: ReqOptions): Promise<T> {
    return unwrap<T>(service.get(url, { params, ...opts }))
  },
  /** GET + 请求体（少数端点如 dataStyle/GetXmlContent 为 GET + @RequestBody） */
  getBody<T = any>(url: string, data?: any, opts?: ReqOptions): Promise<T> {
    return unwrap<T>(service.request({ url, method: 'get', data, ...opts }))
  },
  /** POST JSON：可同时携带 query 参数 */
  post<T = any>(
    url: string,
    data?: any,
    params?: Record<string, any>,
    opts?: ReqOptions,
  ): Promise<T> {
    return unwrap<T>(service.post(url, data, { params, ...opts }))
  },
  /** POST 表单（无 @RequestBody 的端点：map/Insert、layerContent/InsertImageLayer 等） */
  postForm<T = any>(
    url: string,
    data: Record<string, any>,
    params?: Record<string, any>,
    opts?: ReqOptions,
  ): Promise<T> {
    return unwrap<T>(
      service.post(url, toFormData(data), {
        params,
        headers: { 'Content-Type': 'multipart/form-data' },
        ...opts,
      }),
    )
  },
  /** POST multipart 文件上传 */
  postFile<T = any>(
    url: string,
    form: FormData,
    opts?: ReqOptions & { onProgress?: (percent: number) => void },
  ): Promise<T> {
    const { onProgress, ...rest } = opts || {}
    return unwrap<T>(
      service.post(url, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 0,
        ...rest,
        onUploadProgress: onProgress
          ? (e) => {
              if (e.total) onProgress(Math.round((e.loaded / e.total) * 100))
            }
          : undefined,
      }),
    )
  },
  /** DELETE */
  del<T = any>(url: string, params?: Record<string, any>, opts?: ReqOptions): Promise<T> {
    return unwrap<T>(service.delete(url, { params, ...opts }))
  },
  /** 原始实例（下载等场景） */
  raw: service,
}

export default service
