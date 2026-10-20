import { http } from '@/api/request'

/**
 * 图层内容 / 资源（LayerContentController，36 端点）
 * 注意：端点均已统一返回 Result 外壳；查询参数名存在 layerID / layerId 大小写差异，已按源码逐端点书写。
 */
export const layerContentApi = {
  /** 全量图层 */
  getAllList: () => http.get<any[]>('/layerContent/GetAllList'),

  /** 资源总数（裸返回 Integer） */
  getAllCount: (userCode?: string) => http.get<number>('/layerContent/GetAllCount', { userCode }),

  /** 按分类统计资源数 */
  getAllCountByDataType: (userCode?: string) =>
    http.get<any>('/layerContent/GetAllCountByDataType', { userCode }),

  /** 按状态分页查询（form/query 绑定） */
  getAllListStatus: (input: Record<string, any>, pageSize = 10, pageIndex = 1) =>
    http.get<any>('/layerContent/GetAllListStatus', { ...input, pageSize, pageIndex }),

  /** 条件查询（不分页，裸返回 ListResultOutput，字段名是单数 item） */
  getAllListByName: (input: Record<string, any>) =>
    http.post<any>('/layerContent/GetAllListByName', input),

  /** 分页查询（裸返回 PagedResultOutput{totalCount,items}） */
  getPageListByName: (input: Record<string, any>, pageSize = 10, pageIndex = 1) =>
    http.post<any>('/layerContent/GetPageListByName', input, { pageSize, pageIndex }),

  /** 按地图取图层清单（裸返回） */
  getAllListByMapID: (mapID: string) =>
    http.get<any>('/layerContent/GetAllListByMapID', { mapID }),

  /** 详情 */
  getDetailById: (id: string) => http.get<any>('/layerContent/GetDetailById', { id }),

  /** 属性表结构明细（裸返回 String） */
  getLayerAttrTabledDetail: (layerID: string, pageSize = 10, pageIndex = 1) =>
    http.get<string>('/layerContent/GetLayerAttrTabledDetail', { layerID, pageSize, pageIndex }),

  /** 新建资源：Query(filePathName,user,isImport) + Body（入库含导入/GDAL/GeoServer 同步链路，不设超时） */
  insert: (input: Record<string, any>, query: { filePathName: string; user: string; isImport: string }) =>
    http.post<any>('/layerContent/Insert', input, query, { timeout: 0 }),

  /** 新建影像图层（form 表单绑定，裸返回 MapEntity；含栅格入库同步链路，不设超时） */
  insertImageLayer: (input: Record<string, any>) =>
    http.postForm<any>('/layerContent/InsertImageLayer', input, undefined, { timeout: 0 }),

  update: (input: Record<string, any>) => http.post<any>('/layerContent/Update', input),

  /** 上传完成后置 uploadStatus=1 */
  updateStatus: (layerID: string) => http.post<any>('/layerContent/UpdateStatus', null, { layerID }),

  /** 删除资源（裸返回 boolean） */
  remove: (id: string, user: string) => http.post<boolean>('/layerContent/Delete', null, { id, user }),

  /** 删除资源并连带清理发布信息（裸返回 boolean） */
  removeLayer: (id: string) => http.post<boolean>('/layerContent/DeleteLayer', null, { id }),

  /** 目录树（返回 Gzip+Base64 压缩串，须先解压） */
  getLayerTree: (params: {
    dictType: string
    dataType?: string
    publishStatus?: string
    layerName?: string
    uploadFileType?: string
    maxRaster?: number
    isclassify?: boolean
  }) => http.get<string>('/layerContent/getLayerTree', params),

  /** 按分类节点加载子树（压缩串） */
  getLayerTreeByType: (dataTypeId: string, publishStatus = '', layerName = '') =>
    http.get<string>('/layerContent/getLayerTreeByType', { dataTypeId, publishStatus, layerName }),

  /** 按 where 读属性表（裸返回） */
  getLayerAttrTableByWhere: (layerId: string, where: string, pageSize = 10, pageIndex = 1) =>
    http.get<any>('/layerContent/GetLayerAttrTableByWhere', { layerId, where, pageSize, pageIndex }),

  /** 属性表分页读取 */
  getAttrtableByLayerId: (layerId: string, pageSize = 10, pageIndex = 1) =>
    http.get<any>('/layerContent/GetAttrtableByLayerId', { layerId, pageSize, pageIndex }),

  /** 空间查询（圆选/多边选/框选/缓冲区/SQL） */
  pageAttrInfoBySwitch: (query: Record<string, any>, pageSize = 10, pageIndex = 1) =>
    http.post<any>('/layerContent/pageAttrInfoBySwitch', query, { pageSize, pageIndex }),

  /** 点选查询 */
  pageAttrInfoByPoint: (query: Record<string, any>) =>
    http.post<any>('/layerContent/pageAttrInfoByPoint', query),

  /** 导出查询结果 → MinIO URL */
  downloadAttrInfoBySwitch: (query: Record<string, any>) =>
    http.post<string>('/layerContent/downloadAttrInfoBySwitch', query),

  /** 下载栅格原始文件（小写 layerId） */
  downloadTif: (layerId: string) => http.get<string>('/layerContent/downloadTif', { layerId }),

  /** 新增要素记录（裸返回 boolean） */
  insertAttrtable: (body: Record<string, any>) =>
    http.post<boolean>('/layerContent/InsertAttrtable', body),

  /** 删除要素记录（裸返回 boolean） */
  deleteAttrtable: (id: string, gid: string) =>
    http.post<boolean>('/layerContent/DeleteAttrtable', null, { id, gid }),

  /** 修改要素记录 */
  updateAttrtable: (body: Record<string, any>) =>
    http.post<boolean>('/layerContent/UpdateAttrtable', body),

  /** 批量修改要素记录 */
  updateAttrtableList: (body: Record<string, any>[]) =>
    http.post<boolean>('/layerContent/UpdateAttrtableList', body),

  /** 字段定义（裸返回 List） */
  getFieldByLayerId: (layerId: string, attributeType?: string) =>
    http.get<any[]>('/layerContent/GetFieldByLayerId', { layerId, attributeType }),

  /** 校验查询条件（裸返回 boolean） */
  validateCondition: (layerId: string, where: Record<string, any>[]) =>
    http.post<boolean>('/layerContent/ValidateCondition', where, { layerId }),

  /** 导出 SHP（大写 layerID） */
  outputShpFileData: (layerID: string) => http.get<string>('/layerContent/OutputShpFileData', { layerID }),

  /** 导出 TIF（大写 layerID） */
  outputTifFileData: (layerID: string) => http.get<string>('/layerContent/OutputTifFileData', { layerID }),

  /** 目录拖拽排序 / 批量调整目录 */
  updateList: (body: Record<string, any>[]) => http.post<boolean>('/layerContent/updateList', body),

  /** 批量更新（含目录迁移） */
  updateBothList: (body: Record<string, any>[]) => http.post<boolean>('/layerContent/updateBothList', body),

  /** 地图点选取值 */
  getLayerValue: (layerId: string, longitude: number, latitude: number) =>
    http.get<any>('/layerContent/getLayerValue', { layerId, longitude, latitude }),

  /** 矢量要素编辑保存（GeoJSON） */
  editVectorFeatures: (dto: { layerId: string; geoJsonCollection: any }) =>
    http.post<boolean>('/layerContent/editVectorFeatures', dto),
}

export default layerContentApi
