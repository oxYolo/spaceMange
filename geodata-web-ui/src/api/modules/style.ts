import { http } from '@/api/request'

/**
 * 样式（DataStyleController，35 端点）
 * 注意：GetAllList 的 name 为必填（可传空串）。
 */
export const dataStyleApi = {
  /** 样式库列表 */
  getAllList: (name = '') => http.get<any>('/dataStyle/GetAllList', { name }),

  /** 样式分页查询 */
  getAllListPage: (body: Record<string, any>, pageSize = 10, pageIndex = 1) =>
    http.post<any>('/dataStyle/GetAllListPage', body, { pageSize, pageIndex }),

  /** 样式名重复校验 */
  isExists: (name: string) => http.post<boolean>('/dataStyle/IsExists', null, { name }),

  getDetailById: (id: string) => http.get<any>('/dataStyle/GetDetailById', { id }),

  /** 取图层当前样式 */
  getDetailByLayerID: (id: string) => http.get<any>('/dataStyle/GetDetailByLayerID', { id }),

  insert: (body: Record<string, any>) => http.post<any>('/dataStyle/Insert', body),

  update: (body: Record<string, any>) => http.post<any>('/dataStyle/Update', body),

  remove: (id: string, user: string) => http.post<any>('/dataStyle/Delete', null, { id, user }),

  /** 批量删除 */
  removeById: (body: Record<string, any>) => http.post<any>('/dataStyle/DeleteById', body),

  /** 栅格极值 */
  getTifMaxMin: (id: string, bandIndex?: number) =>
    http.get<any>('/dataStyle/getTifMaxMin', { id, bandIndex }),

  /** 自然断点分级 */
  getTifJenksBreaks: (id: string, lvNum: number, bandIndex?: number) =>
    http.get<any>('/dataStyle/getTifJenksBreaks', { id, lvNum, bandIndex }),

  /** 栅格唯一值（像元值 > 50 时后端报 400） */
  getTifValues: (id: string, bandIndex?: number) => http.get<any>('/dataStyle/getTifValues', { id, bandIndex }),

  /** 矢量字段取值列表 */
  getDataAttribute: (layerId: string, layerAttr: string) =>
    http.get<any>('/dataStyle/GetDataAttribute', { layerId, layerAttr }),

  /** 矢量字段极值 */
  getShpMaxMin: (layerId: string, layerAttr: string) =>
    http.get<any>('/dataStyle/getShpMaxMin', { layerId, layerAttr }),

  /** 矢量字段自然断点 */
  getShpJenksBreaks: (layerId: string, layerAttr: string, lvNum: number) =>
    http.get<any>('/dataStyle/getShpJenksBreaks', { layerId, layerAttr, lvNum }),

  /** 属性分级预览（分页） */
  getDataAttributesPage: (
    layerId: string,
    layerAttr: string,
    colorName: string,
    style: string,
    pageSize = 10,
    pageIndex = 1,
  ) => http.get<any>('/dataStyle/GetDataAttributesPage', { layerId, layerAttr, colorName, style, pageSize, pageIndex }),

  getDataAttributesSort: (
    layerId: string,
    layerAttr: string,
    colorName: string,
    style: string,
    pageSize = 10,
    pageIndex = 1,
  ) => http.get<any>('/dataStyle/GetDataAttributesSort', { layerId, layerAttr, colorName, style, pageSize, pageIndex }),

  getDataAttributes: (layerId: string, layerAttr: string, colorName: string, style: string) =>
    http.get<any>('/dataStyle/GetDataAttributes', { layerId, layerAttr, colorName, style }),

  getDataAttributesById: (styleId: string) => http.get<any>('/dataStyle/GetDataAttributesById', { styleId }),

  /** 随机 / 渐变色带库 */
  getRandomColorRamps: () => http.get<any>('/dataStyle/GetRandomColorRamps'),
  getRandomColorRampsByName: (colorName: string, count: number) =>
    http.get<any>('/dataStyle/GetRandomColorRampsByName', { colorName, count }),
  getLinearColorRamps: () => http.get<any>('/dataStyle/GetLinearColorRamps'),
  getLinearColorRampsByName: (colorName: string, count: number) =>
    http.get<any>('/dataStyle/GetLinearColorRampsByName', { colorName, count }),

  /** 样式文件夹 */
  getAllFolders: () => http.get<any>('/dataStyle/GetAllFolders'),
  getAllFiles: (folderName: string) => http.get<any>('/dataStyle/GetAllFiles', { folderName }),
  addFolder: (folderName: string) => http.post<boolean>('/dataStyle/AddFolder', null, { folderName }),
  updateFolder: (oldName: string, newName: string) =>
    http.post<boolean>('/dataStyle/UpdateFolder', null, { oldName, newName }),
  deleteFolder: (folderName: string) => http.post<boolean>('/dataStyle/DeleteFolder', null, { folderName }),
  updateFile: (folderName: string, oldName: string, newName: string) =>
    http.post<boolean>('/dataStyle/UpdateFile', null, { folderName, oldName, newName }),
  deleteFile: (folderName: string, fileName: string) =>
    http.post<boolean>('/dataStyle/DeleteFile', null, { folderName, fileName }),

  /** SLD 原文（GET + Body） */
  getXmlContent: (body: Record<string, any>) => http.getBody<string>('/dataStyle/GetXmlContent', body),

  /** 推送样式到 GeoServer（返回 Result&lt;Void&gt;） */
  insertGeoServerStyle: (body: Record<string, any>) =>
    http.post<void>('/dataStyle/InsertGeoServerStyle', body),

  /** SLD 反向解析 */
  parsingSld: (id: string) => http.post<any>('/dataStyle/ParsingSld', null, { id }),

  /** SLD 文件解析 */
  parsethefile: (id: string) => http.post<any>('/dataStyle/Parsethefile', null, { id }),
}

/** 符号（SymbolController，6 端点；insert 路径全小写） */
export const symbolApi = {
  getAllList: () => http.get<any>('/symbol/GetAllList'),
  /** 分页（后端签名 pageIndex 在前、pageSize 在后，均为必填 @RequestParam） */
  getAllListPage: (body: Record<string, any>, pageIndex = 1, pageSize = 10) =>
    http.post<any>('/symbol/GetAllListPage', body, { pageIndex, pageSize }),
  getDetailById: (id: string) => http.get<any>('/symbol/GetDetailById', { id }),
  insert: (body: Record<string, any>) => http.post<any>('/symbol/insert', body),
  update: (body: Record<string, any>) => http.post<any>('/symbol/Update', body),
  remove: (id: string) => http.post<any>('/symbol/Delete', null, { id }),
}

export default dataStyleApi
