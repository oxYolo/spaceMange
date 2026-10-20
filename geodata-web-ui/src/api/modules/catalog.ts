import { http } from '@/api/request'

/** 数据分类（DataTypeController，8 端点） */
export const dataTypeApi = {
  /** 分类树 */
  getAllList: () => http.get<any>('/dataType/GetAllList'),

  /** 按类型取分类（裸返回 ListResultOutput） */
  getAllListByDataType: (dataType: string) => http.get<any>('/dataType/GetAllListByDataType', { dataType }),

  getDetailById: (id: string) => http.get<any>('/dataType/GetDetailById', { id }),

  /** 按名称取分类（裸返回） */
  getDetailByName: (name: string, dataType: string) =>
    http.get<any>('/dataType/GetDetailByName', { name, dataType }),

  insert: (body: Record<string, any>) => http.post<any>('/dataType/Insert', body),

  update: (body: Record<string, any>) => http.post<any>('/dataType/Update', body),

  remove: (id: string) => http.post<any>('/dataType/Delete', null, { id }),

  /** 分类排序 / 移动 */
  updateList: (body: Record<string, any>[]) => http.post<any>('/dataType/updateList', body),
}

/** 数据标签（DataTagController，6 端点） */
export const dataTagApi = {
  getAllList: () => http.get<any>('/dataTag/GetAllList'),

  /** 按类型取标签（裸返回） */
  getAllListByDataType: (dataType: string) => http.get<any>('/dataTag/GetAllListByDataType', { dataType }),

  getDetailById: (id: string) => http.get<any>('/dataTag/GetDetailById', { id }),

  insert: (body: Record<string, any>) => http.post<any>('/dataTag/Insert', body),

  update: (body: Record<string, any>) => http.post<any>('/dataTag/Update', body),

  remove: (id: string) => http.post<any>('/dataTag/Delete', null, { id }),
}

/** 标签关系（TagRelationController，8 端点） */
export const tagRelationApi = {
  getAllList: () => http.get<any>('/tagReleation/GetAllList'),

  getDetailById: (id: string) => http.get<any>('/tagReleation/GetDetailById', { id }),

  /** 批量打标 */
  multiInsert: (body: Record<string, any>) => http.post<any>('/tagReleation/MultiInsert', body),

  insert: (body: Record<string, any>) => http.post<any>('/tagReleation/Insert', body),

  update: (body: Record<string, any>) => http.post<any>('/tagReleation/Update', body),

  remove: (id: string) => http.post<any>('/tagReleation/Delete', null, { id }),

  /** 取地图的多标签名 */
  getMultiTagNameByMapID: (mapLayerID: string) =>
    http.get<any>('/tagReleation/GetMultiTagNameByMapID', { mapLayerID }),

  /** 删除地图标签关系 */
  deleteByMapID: (mapID: string) => http.post<any>('/tagReleation/DeleteByMapID', null, { mapID }),
}

/** 字典（DicDataTypeController 5 + DicDataCodeController 6） */
export const dictApi = {
  typeList: () => http.get<any>('/dicDataType/GetAllList'),
  typeDetail: (id: string) => http.get<any>('/dicDataType/GetDetailById', { id }),
  typeInsert: (body: Record<string, any>) => http.post<any>('/dicDataType/Insert', body),
  typeUpdate: (body: Record<string, any>) => http.post<any>('/dicDataType/Update', body),
  typeDelete: (id: string) => http.post<any>('/dicDataType/Delete', null, { id }),

  /** 字典代码项（下拉） */
  codeByTypeID: (typeID: string) => http.get<any>('/dicDataCode/GetDetailByTypeID', { typeID }),
  codeAll: () => http.get<any>('/dicDataCode/GetAllList'),
  codeByConn: (typeID: string, keyWord: string) =>
    http.get<any>('/dicDataCode/GetDetailByConn', { typeID, keyWord }),
  codeInsert: (body: Record<string, any>) => http.post<any>('/dicDataCode/Insert', body),
  codeUpdate: (body: Record<string, any>) => http.post<any>('/dicDataCode/Update', body),
  codeDelete: (id: string) => http.post<any>('/dicDataCode/Delete', null, { id }),
}

export default dataTypeApi
