import { http } from '@/api/request'

/** 图层字段（LayerFieldController，9 端点，全部裸返回或简单返回） */
export const layerFieldApi = {
  /** 全量字段（裸返回 ListResultOutput） */
  getAllList: () => http.get<any>('/layerField/GetAllList'),

  /** 字段详情 */
  getDetailById: (id: string) => http.get<any>('/layerField/GetDetailById', { id }),

  /** 按图层取字段列表（裸返回） */
  getDetailByLayerID: (layerID: string) => http.get<any>('/layerField/GetDetailByLayerID', { layerID }),

  insert: (body: Record<string, any>) => http.post<any>('/layerField/Insert', body),

  /** 新增字段并同步落库 */
  insertSync: (body: Record<string, any>) => http.post<any>('/layerField/InsertSync', body),

  multiInsert: (body: Record<string, any>[]) => http.post<any>('/layerField/MultiInsert', body),

  update: (body: Record<string, any>) => http.post<any>('/layerField/Update', body),

  remove: (id: string) => http.post<any>('/layerField/Delete', null, { id }),

  /** 按图层批量删除字段 */
  deleteByLayerID: (layerID: string) => http.post<any>('/layerField/DeleteByLayerID', null, { layerID }),
}

/** 元数据（MetaDataController，7 端点；注意 Insert/Update/Delete 路径无前导斜杠） */
export const metadataApi = {
  getAllList: () => http.get<any>('/metadata/GetAllList'),

  getDetailById: (id: string) => http.get<any>('/metadata/GetDetailById', { id }),

  /** 按数据 ID 取元数据 */
  getDetailByDataId: (dataId: string) => http.get<any>('/metadata/GetDetailByDataId', { dataId }),

  insert: (body: Record<string, any>) => http.post<any>('/metadata/Insert', body),

  update: (body: Record<string, any>) => http.post<any>('/metadata/Update', body),

  remove: (id: string) => http.post<any>('/metadata/Delete', null, { id }),

  deleteByDataId: (dataId: string) => http.post<any>('/metadata/DeleteByDataId', null, { dataId }),
}

export default layerFieldApi
