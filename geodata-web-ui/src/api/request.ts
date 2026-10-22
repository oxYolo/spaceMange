import axios, { type AxiosRequestConfig, type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import urlConfig from '@/config'
import { getToken, removeToken } from '@/auth/session'

/** 统一返回体外壳：{ code, success, result, msg } */
export interface ResultEnvelope<T = any> {
  code: number
  success?: boolean
  result?: T
  msg?: string
}

/** 裸返回判定：后端有大量端点没有 Result 外壳，直接返回数据体 */
export function isEnvelope(v: any): v is ResultEnvelope {
  return (
    !!v &&
    typeof v === 'object' &&
    !Array.isArray(v) &&
    ('code' in v || 'success' in v) &&
    'result' in v
  )
}

const service: AxiosInstance = axios.create({
  baseURL: urlConfig.baseUrl,
  timeout: 120000,
})

service.interceptors.request.use(
  (config) => {
    const skipToken = config.headers?.isToken === false
    if (config.headers) delete config.headers.isToken
    const token = getToken()
    if (!skipToken && token && config.headers) {
      config.headers.Authorization = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error),
)

service.interceptors.response.use(
  (res) => {
    // 二进制直接返回
    if (res.request?.responseType === 'blob' || res.request?.responseType === 'arraybuffer') {
      return res.data
    }
    return res.data
  },
  (error) => {
    const status = error?.response?.status
    const body = error?.response?.data
    const msg =
      body?.msg ||
      body?.message ||
      (status ? `请求失败（HTTP ${status}）` : error?.message || '网络异常，请检查后端服务是否已启动')
    if (!error?.config?.silent) {
      ElMessage.error(msg)
    }
    if (status === 401 && !String(error?.config?.url || '').startsWith('/auth/')) {
      removeToken()
      if (!window.location.hash.startsWith('#/login')) {
        const redirect = window.location.hash.slice(1) || '/manage'
        window.location.hash = '#/login?redirect=' + encodeURIComponent(redirect)
      }
    }
    return Promise.reject(new Error(msg))
  },
)

/** 解包：有外壳取 result，裸返回原样返回 */
export async function unwrap<T = any>(p: Promise<any>): Promise<T> {
  const r = await p
  if (isEnvelope(r)) {
    if (r.code !== 200) {
      const err = new Error(r.msg || '操作失败') as Error & { code?: number }
      err.code = r.code
      throw err
    }
    return (r.result ?? null) as T
  }
  return r as T
}

/** 对象转 x-www-form-urlencoded */
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
  /** 静默：不弹出错误提示 */
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
