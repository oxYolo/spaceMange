import { http } from '@/api/request'

/**
 * 三维数据管理（Model3dController，9 端点）
 * 状态机：0 上传中 / 1 待转换 / 2 已入队 / 3 转换中 / 4 已发布 / 5 失败
 */
export const model3dApi = {
  /** 初始化上传 → 返回数据 id（userCode：无登录态时兜底操作人 / SignalR 推送目标） */
  init: (body: {
    name: string
    directoryId?: string
    modelType: string
    srcFormat: string
    fileName: string
    totalSize: number
    partCount: number
    srs?: string
    userCode?: string
  }) => http.post<string>('/model3d/init', body),

  /** 上传分片（partNo 从 1 开始，非尾片 ≥ 5MiB） */
  uploadPart: (id: string, partNo: number, file: Blob, onProgress?: (p: number) => void) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.postFile<void>(`/model3d/${id}/part?partNo=${partNo}`, fd, { onProgress })
  },

  /** 完成上传（TILESET 直传会在 complete 内同步解压+发布，大包耗时较长，不设超时） */
  complete: (id: string) => http.post<any>(`/model3d/${id}/complete`, undefined, undefined, { timeout: 0 }),

  /** 已上传分片序号（断点续传） */
  parts: (id: string) => http.get<number[]>(`/model3d/${id}/parts`),

  /** 下发转换任务 */
  convert: (id: string) => http.post<void>(`/model3d/${id}/convert`),

  /** 详情 */
  get: (id: string) => http.get<any>(`/model3d/${id}`),

  /** 分页查询 */
  page: (query: {
    keyword?: string
    directoryId?: string
    modelType?: string
    status?: string
    pageNum?: number
    pageSize?: number
  }) => http.get<any>('/model3d/page', query),

  /** 签发加载地址（预签名） */
  publishUrl: (id: string) => http.get<string>(`/model3d/${id}/publish-url`),

  /** 删除 */
  remove: (id: string) => http.del<void>(`/model3d/${id}`),
}

export default model3dApi
