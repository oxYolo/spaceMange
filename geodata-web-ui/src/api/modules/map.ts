import { http } from '@/api/request'

/**
 * 地图（MapController，15 端点）
 * 注意：Insert / Update / GetAllListByName 无 @RequestBody，走 form 表单。
 */
export const mapApi = {
  getAllList: () => http.get<any>('/map/GetAllList'),

  /** 条件查询（form 表单） */
  getAllListByName: (input: Record<string, any>) => http.postForm<any>('/map/GetAllListByName', input),

  /** 分页查询（Body + query） */
  getPageListByName: (input: Record<string, any>, pageSize = 10, pageIndex = 1) =>
    http.post<any>('/map/GetPageListByName', input, { pageSize, pageIndex }),

  getDetailById: (id: string) => http.get<any>('/map/GetDetailById', { id }),

  /** 新建地图（form 表单） */
  insert: (input: Record<string, any>) => http.postForm<any>('/map/Insert', input),

  /** 保存地图属性（form 表单） */
  update: (input: Record<string, any>) => http.postForm<any>('/map/Update', input),

  updateMapLegend: (mapID: string, legendUrlPath: string) =>
    http.post<boolean>('/map/UpdateMapLegend', null, { mapID, legendUrlPath }),

  updateDataType: (mapID: string, dataTypeID: string) =>
    http.post<any>('/map/UpdateDataType', null, { mapID, dataTypeID }),

  updateMapBBox: (mapID: string) => http.post<boolean>('/map/UpdateMapBBox', null, { mapID }),

  remove: (id: string, user: string) => http.post<boolean>('/map/Delete', null, { id, user }),

  /** 经纬度点位查询图层属性 */
  getLayerAttrByLayerPt: (layerID: string, lon: number, lat: number, tolerance?: number) =>
    http.get<any>('/map/GetLayerAttrByLayerPt', { layerID, lon, lat, tolerance }),

  getTypeInstance: (tagid: string) => http.get<any>('/map/GetTypeInstance', { tagid }),

  getMaps: (tagid: string, typeid: string) => http.get<any>('/map/GetMaps', { tagid, typeid }),

  /** 取地图样式信息（参数名 MapId 大写开头） */
  getDataStyle: (MapId: string) => http.get<any>('/map/GetDataStyle', { MapId }),

  getAllDataToTable: (username: string) => http.get<any>('/map/GetAllDataToTable', { username }),
}

/** 地图图层关联 + 发布（MapRelationController，19 端点） */
export const mapRelationApi = {
  getAllList: () => http.get<any>('/mapreleation/GetAllList'),

  /** 图层所属地图 */
  getAllListBylayerID: (layerId: string) => http.get<any>('/mapreleation/GetAllListBylayerID', { layerId }),

  /** 地图下已关联图层（TOC） */
  getAllListByMapId: (mapId: string) => http.get<any>('/mapreleation/GetAllListByMapId', { mapId }),

  getDetailById: (id: string) => http.get<any>('/mapreleation/GetDetailById', { id }),

  /** 样式是否被引用（删除前校验） */
  getMapReleationByStyle: (styleID: string) =>
    http.get<any>('/mapreleation/GetMapReleationByStyle', { styleID }),

  /** 图层勾选态 */
  getLayersList: (layerId: string, mapId: string) =>
    http.get<any>('/mapreleation/GetLayersList', { layerId, mapId }),

  insert: (body: Record<string, any>) => http.post<any>('/mapreleation/Insert', body),

  /** 批量关联图层到地图 */
  multiInsert: (body: Record<string, any>[]) => http.post<boolean>('/mapreleation/MultiInsert', body),

  update: (body: Record<string, any>, user: string) =>
    http.post<any>('/mapreleation/Update', body, { user }),

  remove: (id: string, user: string) => http.post<boolean>('/mapreleation/Delete', null, { id, user }),

  multiDelete: (body: Record<string, any>[]) => http.post<boolean>('/mapreleation/MultiDelete', body),

  deleteByMapID: (mapID: string) => http.post<boolean>('/mapreleation/DeleteByMapID', null, { mapID }),

  /** 发布地图 */
  publicMap: (mapId: string) => http.post<boolean>('/mapreleation/PublicMap', null, { mapId }),

  /** 切换样式（参数名 mapid 全小写） */
  changeStyle: (mapid: string, layerStr: string, styleStr: string) =>
    http.post<boolean>('/mapreleation/ChangeStyle', null, { mapid, layerStr, styleStr }),

  changeStyleObject: (body: Record<string, any>) =>
    http.post<boolean>('/mapreleation/ChangeStyleObject', body),

  /** 批量更新关联（排序 / 样式） */
  multiUpdate: (mapId: string, body: Record<string, any>[], user: string) =>
    http.post<any>('/mapreleation/MultiUpdate', body, { mapId, user }),

  /** 刷新地图缓存 */
  refreshMap: (mapId: string, user: string) =>
    http.post<boolean>('/mapreleation/RefreshMap', null, { mapId, user }),

  /** 是否存在切片任务 */
  isExistTilesTask: (mapId: string) => http.get<boolean>('/mapreleation/IsExistTilesTask', { mapId }),

  /** 查询地图组切片进度（GWC seed 任务；status: IDLE/RUNNING/DONE/FAILED） */
  tileProgress: (mapId: string) => http.get<any>('/mapreleation/TileProgress', { mapId }),

  /** 批量更新比例尺区间并上传 GeoServer */
  multiUpdateScale: (body: Record<string, any>[], user: string) =>
    http.post<any>('/mapreleation/MultiUpdateScale', body, { user }),
}

/** 服务发布（ServerInterfaceController，3 端点） */
export const serverInterfaceApi = {
  /** 图层发布为 OGC 服务 */
  publishService: (body: Record<string, any>) => http.post<boolean>('/serverInterface/publishService', body),

  /** 查询服务信息（WMS/WFS/WMTS 地址） */
  getServerInterface: (dataId: string, serverType: string) =>
    http.get<any>('/serverInterface/getServerInterface', { dataId, serverType }),

  /** 查询图层切片进度（GWC seed 任务；status: IDLE/RUNNING/DONE/FAILED） */
  tileProgress: (dataId: string) => http.get<any>('/serverInterface/tileProgress', { dataId }),
}

export default mapApi
