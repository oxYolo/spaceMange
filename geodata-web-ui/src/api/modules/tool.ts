import { http, type ReqOptions } from '@/api/request'

/**
 * 图层工具（LayerToolController，13 端点）
 * 统一异步模型：提交 → 返回 taskId → /layerTool/getList?origin=N 轮询 → storageLayer 成果入库。
 */
export const layerToolApi = {
  /** 异步任务历史 / 轮询状态（按 createDT 倒序） */
  getList: (pageSize = 10, pageIndex = 1, origin?: string | number, opts?: ReqOptions) =>
    http.get<any>('/layerTool/getList', { pageSize, pageIndex, origin }, opts),

  /** 成果入库到目标目录并上图 */
  storageLayer: (id: string, targetId: string) =>
    http.get<string>('/layerTool/storageLayer', { id, targetId }),

  /** 删除任务记录 */
  delete: (id: string) => http.get<boolean>('/layerTool/delete', { id }),

  /** 栅格元信息（xCell/yCell 已折算为米） */
  getTifInfo: (layerId: string) => http.get<any>('/layerTool/getTifInfo', { layerId }),

  /** origin=1 裁剪 */
  cutLayer: (body: Record<string, any>) => http.post<string>('/layerTool/cutLayer', body),
  /** origin=2 重采样 */
  resample: (body: Record<string, any>) => http.post<string>('/layerTool/resample', body),
  /** origin=3 栅格矢量化 */
  tifToShp: (body: Record<string, any>) => http.post<string>('/layerTool/tifToShp', body),
  /** origin=4 矢量栅格化 */
  shpToTif: (body: Record<string, any>) => http.post<string>('/layerTool/shpToTif', body),
  /** origin=5 栅格重分类（matrix 为 JSON 字符串） */
  reclassification: (body: Record<string, any>) =>
    http.post<string>('/layerTool/reclassification', body),
  /** origin=6 矢量擦除 */
  eraseVectorLayer: (body: Record<string, any>) =>
    http.post<string>('/layerTool/eraseVectorLayer', body),
  /** origin=7 缓冲区 */
  bufferZone: (body: Record<string, any>) => http.post<string>('/layerTool/bufferZone', body),
  /** origin=8 矢量交并补 */
  intersectionAndUnion: (body: Record<string, any>) =>
    http.post<string>('/layerTool/intersectionAndUnion', body),
  /** origin=9 栅格叠加计算 */
  overlayCalculation: (body: Record<string, any>) =>
    http.post<string>('/layerTool/overlayCalculation', body),
}

/** 拓扑检查（TopologyController，10 端点；origin=10） */
export const topologyApi = {
  /** 执行拓扑检查（异步，返回 taskId） */
  check: (body: Record<string, any>) => http.post<string>('/topology/check', body),

  /** 轮询拓扑检查报告：processing / failed / TopologyCheckReport */
  checkReport: (taskId: string) => http.get<any>(`/topology/check/${taskId}`),

  /** 可用拓扑规则（19 条） */
  rules: (geometryType?: string) => http.get<any[]>('/topology/rules', { geometryType }),

  /** 同步执行拓扑检查 */
  checkSync: (body: Record<string, any>, pageIndex?: number, pageSize?: number) =>
    http.post<any>('/topology/checkSync', body, { pageIndex, pageSize }),

  /** 拓扑状态 */
  status: (layerId: string) => http.get<any>(`/topology/status/${layerId}`),

  /** 创建拓扑 */
  create: (topologyName: string, srid = 4326, tolerance = 0.00001) =>
    http.post<string>('/topology/create', null, { topologyName, srid, tolerance }),

  /** 要素导入拓扑 */
  importFeatures: (topologyName: string, featureTable: string) =>
    http.post<string>('/topology/import', null, { topologyName, featureTable }),

  /** 为要素表添加拓扑几何列 */
  addTopoColumn: (params: {
    topologyName: string
    tableName: string
    schemaName?: string
    geometryColumn?: string
    columnName?: string
  }) => http.post<string>('/topology/add-topo-column', null, params),

  /** 填充拓扑几何数据 */
  populate: (params: {
    tableName: string
    topologyName: string
    geometryColumn?: string
    topoGeometryColumn?: string
    condition?: string
    tolerance?: number
  }) => http.post<string>('/topology/populate', null, params),

  /** 删除拓扑 */
  remove: (layerId: string) => http.del<string>(`/topology/${layerId}`),
}

export default layerToolApi
