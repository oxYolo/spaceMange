/**
 * 数据中台统一数据集工具（数据管理 / 服务管理 / 图层配置 三页共用）
 *
 * 后端依据：
 *  - layerContent（矢量/栅格）：dataType 1 矢量 / 2 栅格；publishStatus 1 已发布
 *  - model3d（三维）：modelType OSGB/POINTCLOUD/TILESET/SINGLE/BIM；状态机 0 上传中
 *    1 已上传(待转换) 2 已排队 3 转换中 4 已发布 5 失败
 *  - serverInterface：二维发布 publishService / 查地址 getServerInterface(dataId, serverType)
 *
 * 能力收敛（与用户确认）：
 *  - 白模数据：后端无独立支撑，归入矢量（SHP）仅上传
 *  - 服务格式：矢量→WMS/WFS/MVT，栅格→WMS/WMTS，三维→3DTiles
 *    （MVT 走后端 getMVTInfo：GeoServer GWC WMTS 端点 + mapbox-vector-tile 格式，需 GS 启用矢量瓦片；
 *      Terrain 后端无端点）
 */

/* ---------------- sdms_layer.datatype 取值（几何类型字典 id，1cfe51dd 组） ---------------- */
/**
 * 与后端硬编码一致的字典 id（点/线/面/影像）。
 * 后端矢量入库时按 SHP 实际几何类型回填 datatype；栅格恒为影像。
 *
 * 唯一真源在后端：geodata-common/src/main/java/com/whulri/geodata/common/constant/DictionaryIds.java
 * 修改字典 GUID 时必须两处同步（前端无法直接引用 Java 常量）。
 */
export const LAYER_GEOM_IDS = {
  point: '6b6941f1-67a3-11e7-8eb2-005056bb1c7e',
  line: '7776934c-67a3-11e7-8eb2-005056bb1c7e',
  polygon: 'a2758dc0-67a3-11e7-8eb2-005056bb1c7e',
  raster: 'acf11b57-0626-4e49-b385-2e9a4195221c',
} as const

/** 图层查询参数：矢量 = 点/线/面多值串（后端 contains 匹配），栅格 = 影像 */
const VECTOR_DT_QUERY = LAYER_GEOM_IDS.point + ',' + LAYER_GEOM_IDS.line + ',' + LAYER_GEOM_IDS.polygon + ','
const RASTER_DT_QUERY = LAYER_GEOM_IDS.raster + ','

/** 类型键是否为栅格（raster / terrain） */
export function isRasterTypeKey(key?: string): boolean {
  return key === 'raster' || key === 'terrain'
}

/** datatype 值是否为栅格（字典 UUID；兼容历史 '2'） */
export function isRasterDataType(v: any): boolean {
  const s = String(v ?? '')
  return s === '2' || s === LAYER_GEOM_IDS.raster
}

/** datatype 值 → 显示名（点/线/面/影像；兼容历史 '1'/'2'） */
export function layerDataTypeName(v: any): string {
  const s = String(v ?? '')
  if (s === '1') return '矢量'
  if (s === '2') return '栅格'
  if (s === LAYER_GEOM_IDS.point) return '点'
  if (s === LAYER_GEOM_IDS.line) return '线'
  if (s === LAYER_GEOM_IDS.polygon) return '面'
  if (s === LAYER_GEOM_IDS.raster) return '影像'
  return s || '-'
}

/* ---------------- 数据类型定义 ---------------- */

export type TypeKey =
  | 'vector'
  | 'raster'
  | 'terrain'
  | 'white'
  | 'oblique'
  | 'pointcloud'
  | 'tileset'
  | 'single'
  | 'bim'
export type RowSrc = 'layer' | 'm3d'

export interface TypeDef {
  key: TypeKey
  label: string
  formats: string[]
  src: RowSrc
  /** 查询参数：layerContent → dataType；model3d → modelType */
  query: Record<string, string>
}

export const DATA_TYPES: TypeDef[] = [
  { key: 'vector', label: '矢量数据', formats: ['SHP', 'GeoJSON', 'DWG', 'KML'], src: 'layer', query: { dataType: VECTOR_DT_QUERY } },
  { key: 'raster', label: '栅格数据（DOM）', formats: ['GeoTIFF'], src: 'layer', query: { dataType: RASTER_DT_QUERY } },
  { key: 'terrain', label: '地形数据（DEM）', formats: ['GeoTIFF'], src: 'layer', query: { dataType: RASTER_DT_QUERY } },
  { key: 'white', label: '白模数据', formats: ['SHP'], src: 'layer', query: { dataType: VECTOR_DT_QUERY } },
  { key: 'oblique', label: '倾斜模型数据', formats: ['OSGB'], src: 'm3d', query: { modelType: 'OSGB' } },
  { key: 'pointcloud', label: '点云数据', formats: ['LAS', 'LAZ'], src: 'm3d', query: { modelType: 'POINTCLOUD' } },
  { key: 'tileset', label: '三维成果（3DTiles）', formats: ['3DTiles'], src: 'm3d', query: { modelType: 'TILESET' } },
  { key: 'single', label: '单体模型', formats: ['OBJ', 'FBX'], src: 'm3d', query: { modelType: 'SINGLE' } },
  { key: 'bim', label: 'BIM 模型', formats: ['IFC'], src: 'm3d', query: { modelType: 'BIM' } },
]

/** 列表按后端能实际区分的类型筛选；白模/地形仍可作为上传入口。 */
export const DATASET_FILTER_TYPES: TypeDef[] = DATA_TYPES
  .filter((type) => type.key !== 'white' && type.key !== 'terrain')
  .map((type) => ({
    ...type,
    label: type.key === 'vector' ? '矢量 / 白模' : type.key === 'raster' ? '栅格 / 地形' : type.label,
  }))

export const typeDef = (key: string): TypeDef | undefined => DATA_TYPES.find((t) => t.key === key)
export const typeLabel = (key: string): string => typeDef(key)?.label || key

/** 扩展名 → 数据格式 */
const EXT_FORMAT: Record<string, string> = {
  shp: 'SHP', shx: 'SHP', dbf: 'SHP', prj: 'SHP', cpg: 'SHP',
  geojson: 'GeoJSON', json: 'GeoJSON',
  dwg: 'DWG', dxf: 'DWG',
  kml: 'KML', kmz: 'KML',
  tif: 'GeoTIFF', tiff: 'GeoTIFF', img: 'GeoTIFF',
  osgb: 'OSGB',
  las: 'LAS', laz: 'LAZ',
  tileset: '3DTiles',
  obj: 'OBJ', fbx: 'FBX', ifc: 'IFC',
}

export function extFormat(fileName?: string): string {
  const n = String(fileName || '').trim()
  const ext = n.includes('.') ? n.split('.').pop()!.toLowerCase() : ''
  return EXT_FORMAT[ext] || ''
}

/** 上传弹窗 accept 属性（本地上传支持的扩展名 + 压缩包） */
export const UPLOAD_ACCEPT =
  '.shp,.shx,.dbf,.prj,.cpg,.geojson,.json,.dwg,.dxf,.kml,.kmz,.tif,.tiff,.img,.osgb,.las,.laz,.zip,.tar,.gz,.7z'

/* ---------------- 服务格式（按后端能力收敛） ---------------- */

/** 各数据类型可选服务格式（与后端 ServerInterfaceServiceImpl.getListByType 对齐：WMS/WMTS/WFS/MVT） */
export const SERVICE_FORMATS: Record<string, string[]> = {
  vector: ['WMS', 'WFS', 'MVT'],
  white: ['WMS', 'WFS', 'MVT'],
  raster: ['WMS', 'WMTS'],
  terrain: ['WMS', 'WMTS'],
  oblique: ['3DTiles'],
  pointcloud: ['3DTiles'],
  tileset: ['3DTiles'],
  single: ['3DTiles'],
  bim: ['3DTiles'],
}

/** 各数据类型默认勾选的服务格式 */
export const SERVICE_DEFAULTS: Record<string, string[]> = {
  vector: ['WMS'],
  white: ['WMS'],
  raster: ['WMS'],
  terrain: ['WMS'],
  oblique: ['3DTiles'],
  pointcloud: ['3DTiles'],
  tileset: ['3DTiles'],
  single: ['3DTiles'],
  bim: ['3DTiles'],
}

export interface ServiceProtocol {
  /** 协议标识（与后端 serverType 一致） */
  type: string
  label: string
  /** 一句话说明 */
  desc: string
  /** 详细说明（用途 / 适用场景 / 客户端） */
  detail: string
  /** 是否可在地图预览中直接渲染 */
  renderable: boolean
  /** 关键请求参数（用于界面内「请求参数」速查） */
  params?: { name: string; must: string; desc: string }[]
  /** 请求示例（相对 GeoServer 根地址） */
  example?: string
}

/** OGC 服务协议说明（预览侧栏 / 详情页签共用；参数与示例同 ServerInterfaceServiceImpl 实际生成逻辑一致） */
export const SERVICE_PROTOCOLS: Record<string, ServiceProtocol> = {
  WMS: {
    type: 'WMS',
    label: 'WMS 地图服务',
    desc: 'OGC 动态渲染地图图片',
    detail:
      'Web Map Service：服务端按请求参数实时渲染并返回整幅地图图片（PNG/JPEG）。适合矢量/栅格的动态浏览，可叠加 SLD 样式、按 BBOX 出图；数据实时性最好，但每次请求都要渲染。适用客户端：QGIS、ArcGIS、OpenLayers 等所有 OGC 客户端。',
    renderable: true,
    params: [
      { name: 'service', must: '是', desc: '固定 WMS' },
      { name: 'version', must: '是', desc: '1.0.0~2.0.0，默认 1.1.0' },
      { name: 'request', must: '是', desc: 'GetMap / GetCapabilities / GetFeatureInfo' },
      { name: 'layers', must: '是', desc: '工作区:要素类型，如 sdms:cb_road' },
      { name: 'bbox', must: '是', desc: 'minX,minY,maxX,maxY（用后端返回的图层四至）' },
      { name: 'srs', must: '是', desc: '默认 EPSG:4326' },
      { name: 'styles', must: '是', desc: '空 = 默认样式；可传已推送的 SLD 名' },
      { name: 'width / height', must: '是', desc: '图片尺寸（px），示例 768×670' },
      { name: 'format', must: '是', desc: 'image/png（另支持 jpeg/tiff/svg/pdf/kml）' },
      { name: 'transparent', must: '否', desc: '叠加底图时设 true' },
      { name: 'sld / sld_body', must: '否', desc: '远程样式 URL / 内联 SLD，临时覆盖样式' },
    ],
    example:
      '/geoserver/sdms/wms?service=WMS&version=1.1.0&request=GetMap&layers=sdms:cb_road&bbox=113.8,29.5,114.1,29.9&srs=EPSG:4326&styles=&width=768&height=670&format=image/png',
  },
  WMTS: {
    type: 'WMTS',
    label: 'WMTS 瓦片服务',
    desc: '预切片地图瓦片',
    detail:
      'Web Map Tile Service：GeoServer GWC 预切片（或按需切片）后以瓦片方式分发，前端按层级行列号直接取瓦片，速度快、可缓存、负载低。适合栅格（DOM）与大范围浏览；切片未完成时预览会提示。适用客户端：QGIS、ArcGIS、各类 Web 地图。',
    renderable: true,
    params: [
      { name: 'SERVICE / VERSION', must: '是', desc: 'WMTS / 1.0.0（标准版本；原始地址沿用服务返回参数）' },
      { name: 'REQUEST', must: '是', desc: 'GetTile / GetCapabilities' },
      { name: 'LAYER', must: '是', desc: '工作区:要素类型' },
      { name: 'TILEMATRIXSET', must: '是', desc: 'EPSG:4326（另有 EPSG:900913）' },
      { name: 'TILEMATRIX', must: '是', desc: 'EPSG:4326:{z}' },
      { name: 'TILECOL / TILEROW', must: '是', desc: '瓦片行列；4326 网格左上原点，行自上而下' },
      { name: 'format', must: '是', desc: 'image/png、image/jpeg' },
      { name: 'style', must: '否', desc: '样式名' },
    ],
    example:
      '/geoserver/gwc/service/wmts?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&LAYER=sdms:dom_2024&TILEMATRIXSET=EPSG:4326&TILEMATRIX=EPSG:4326:10&TILECOL=843&TILEROW=392&format=image/png',
  },
  WFS: {
    type: 'WFS',
    label: 'WFS 要素服务',
    desc: '矢量要素查询与下载',
    detail:
      'Web Feature Service：直接返回矢量要素的几何与属性（GML/GeoJSON），支持按条件过滤（CQL/FEATUREID），可用于要素编辑、属性核查与数据下载，不适合直接做底图渲染。适用客户端：QGIS、ArcGIS、GDAL/ogr2ogr。',
    renderable: false,
    params: [
      { name: 'service / version', must: '是', desc: 'WFS / 1.1.0' },
      { name: 'request', must: '是', desc: 'GetFeature / DescribeFeatureType' },
      { name: 'typeName', must: '是', desc: '工作区:要素类型' },
      { name: 'outputFormat', must: '否', desc: 'application/json（GeoJSON）；默认 GML' },
      { name: 'CQL_FILTER', must: '否', desc: '属性条件，如 length>500' },
      { name: 'featureID', must: '否', desc: '表名.gid 取单要素' },
      { name: 'maxFeatures', must: '否', desc: '返回上限，建议显式设置' },
    ],
    example:
      '/geoserver/sdms/wfs?service=WFS&version=1.1.0&request=GetFeature&typeName=sdms:cb_road&outputFormat=application/json&maxFeatures=1000',
  },
  MVT: {
    type: 'MVT',
    label: 'MVT 矢量瓦片',
    desc: 'Mapbox 矢量瓦片',
    detail:
      'Mapbox Vector Tile（application/vnd.mapbox-vector-tile，经 GeoServer GWC WMTS 端点分发）：瓦片内保留几何与属性，客户端可动态换肤、无级缩放不失真、体积小。适合大数据量矢量图层的高性能 Web 展示；需要 GeoServer 已启用矢量瓦片格式。',
    renderable: true,
    params: [
      { name: 'SERVICE / VERSION', must: '是', desc: 'WMTS / 1.0.0（标准版本；原始地址沿用服务返回参数）' },
      { name: 'REQUEST', must: '是', desc: 'GetTile' },
      { name: 'LAYER', must: '是', desc: '工作区:要素类型' },
      { name: 'TILEMATRIXSET', must: '是', desc: 'EPSG:4326' },
      { name: 'TILEMATRIX / TILECOL / TILEROW', must: '是', desc: '按 {z}/{x}/{y} 重组' },
      { name: 'format', must: '是', desc: 'application/vnd.mapbox-vector-tile' },
    ],
    example:
      '/geoserver/gwc/service/wmts?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&LAYER=sdms:cb_road&TILEMATRIXSET=EPSG:4326&TILEMATRIX=EPSG:4326:{z}&TILECOL={x}&TILEROW={y}&format=application/vnd.mapbox-vector-tile',
  },
  '3DTiles': {
    type: '3DTiles',
    label: '3D Tiles 三维服务',
    desc: '三维模型/点云流式加载',
    detail:
      '3D Tiles 1.0：Cesium 生态的三维瓦片流式加载规范，用于 OSGB 倾斜摄影、LAS/LAZ 点云、OBJ/FBX/IFC 单体与 BIM 模型的 Web 端发布与浏览，按 LOD 流式调度。需转换发布完成（状态「已发布」）后才有加载地址。',
    renderable: true,
    params: [{ name: 'url', must: '是', desc: 'tileset.json 地址（GET /model3d/{id}/publish-url）' }],
    example: 'GET /model3d/{id}/publish-url → { url: "…/tileset.json" }，Cesium 直接 new Cesium3DTileset({ url })',
  },
}

/** 数据类型适用的协议列表（用于探测与展示） */
export function protocolsFor(typeKey: string): string[] {
  return [...(SERVICE_FORMATS[typeKey] || [])]
}

export const SERVICE_PROTOCOL: Record<string, string> = {
  WMS: 'OGC WMS 地图服务',
  WFS: 'OGC WFS 要素服务',
  WMTS: 'OGC WMTS 瓦片地图服务',
  '3DTiles': '3D Tiles 1.0 三维服务',
}

/* ---------------- 统一行模型 ---------------- */

export type RowStatus = 'uploading' | 'ok' | 'fail' | 'converting'

export interface UnifiedRow {
  key: string
  id: string
  src: RowSrc
  name: string
  typeKey: TypeKey
  typeLabel: string
  format: string
  /** 空间参考（layerRefenceName / model3d.srs） */
  spatialRef: string
  /** 所属分类目录名（layerTypeName，仅二维） */
  category: string
  size: string
  sizeBytes: number
  uploader: string
  time: string
  status: RowStatus
  statusLabel: string
  progress: number
  /** 是否已发布为服务 */
  published: boolean
  /** Authoritative completion check for asynchronous vector ingestion. */
  ingestReady?: boolean
  ingestPending?: boolean
  /** 分片数（仅三维） */
  partCount?: number
  /** 原始记录（layerContentDto / Model3dEntity） */
  raw: any
}

function humanSize(bytes?: number | string): string {
  const n = Number(bytes)
  if (!n || Number.isNaN(n)) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** layerContent 记录 → 统一行（白模归入矢量展示） */
export function normalizeLayer(r: any): UnifiedRow {
  const dt = String(r?.dataType ?? '')
  const typeKey: TypeKey = isRasterDataType(dt) ? 'raster' : 'vector'
  const status = String(r?.uploadStatus ?? '') === '1' ? 'ok' : 'uploading'
  const ingestReady = String(r?.publishStatus ?? '') === '1' || String(r?.isShow ?? r?.IsShow ?? '') === '1'
  const ingestPending = !ingestReady && r?.ingestPending === true
  return {
    key: 'layer:' + String(r?.id ?? ''),
    id: String(r?.id ?? ''),
    src: 'layer',
    name: String(r?.layerName ?? r?.name ?? '-'),
    typeKey,
    typeLabel: isRasterDataType(dt) ? '栅格数据' : '矢量数据',
    format: extFormat(r?.uploadFileName) || (isRasterDataType(dt) ? 'GeoTIFF' : 'SHP'),
    spatialRef: String(r?.layerRefenceName ?? r?.layerRefence ?? ''),
    category: String(r?.layerTypeName ?? r?.dataTypes ?? ''),
    size: humanSize(Number(r?.fileSize) ? Number(r.fileSize) * 1024 : r?.fileSize),
    // layerContent.fileSize 的单位是 KB；统一模型与 model3d 一律使用字节。
    sizeBytes: Math.max(0, Number(r?.fileSize) || 0) * 1024,
    uploader: String(r?.createBy ?? '-'),
    time: String(r?.createDT ?? r?.createTime ?? ''),
    status: ingestPending ? 'uploading' : ingestReady ? 'ok' : status,
    statusLabel: ingestReady ? '已入库' : ingestPending ? '入库待确认' : status === 'ok' ? '已上传' : '上传处理中',
    progress: ingestPending ? 0 : ingestReady || status === 'ok' ? 100 : 0,
    published: String(r?.publishStatus ?? '') === '1',
    ingestReady,
    ingestPending,
    raw: r,
  }
}

const M3D_LABEL: Record<string, string> = {
  TILESET: '三维模型（3DTiles）',
  OSGB: '倾斜模型数据',
  POINTCLOUD: '点云数据',
  SINGLE: '单体模型',
  BIM: 'BIM 模型',
}

/** modelType → 前端类型键（列表归一 / 筛选共用） */
const M3D_TYPE_KEY: Record<string, TypeKey> = {
  OSGB: 'oblique',
  POINTCLOUD: 'pointcloud',
  TILESET: 'tileset',
  SINGLE: 'single',
  BIM: 'bim',
}

/** model3d 状态 → 统一状态（原型「上传状态」列承载三维全生命周期） */
export function m3dStatus(status: string, progress: number): { status: RowStatus; label: string; progress: number } {
  switch (String(status)) {
    case '0':
      return { status: 'uploading', label: '数据上传中', progress: Math.max(0, Math.min(100, Number(progress) || 0)) }
    case '1':
      return { status: 'ok', label: '待转换', progress: 100 }
    case '2':
      return { status: 'converting', label: '排队待转换', progress: 0 }
    case '3':
      return { status: 'converting', label: '转换中', progress: Math.max(0, Math.min(100, Number(progress) || 0)) }
    case '4':
      return { status: 'ok', label: '已发布（3DTiles）', progress: 100 }
    case '5':
      return { status: 'fail', label: '处理失败', progress: 0 }
    default:
      return { status: 'fail', label: '状态未知', progress: 0 }
  }
}

/** model3d 记录 → 统一行 */
export function normalizeM3d(r: any): UnifiedRow {
  const mt = String(r?.modelType ?? '')
  const st = m3dStatus(r?.status, r?.progress)
  const typeKey: TypeKey = M3D_TYPE_KEY[mt] || 'oblique'
  return {
    key: 'm3d:' + String(r?.id ?? ''),
    id: String(r?.id ?? ''),
    src: 'm3d',
    name: String(r?.name ?? '-'),
    typeKey,
    typeLabel: M3D_LABEL[mt] || '三维模型',
    format: String(r?.srcFormat ?? '').toUpperCase() || extFormat(r?.fileName) || '-',
    spatialRef: String(r?.srs ?? ''),
    category: '',
    size: humanSize(r?.totalSize),
    sizeBytes: Number(r?.totalSize) || 0,
    uploader: String(r?.createUser ?? '-'),
    time: String(r?.createTime ?? r?.updateTime ?? ''),
    status: st.status,
    statusLabel: st.label,
    progress: st.progress,
    published: String(r?.status ?? '') === '4',
    partCount: Number(r?.partCount) || undefined,
    raw: r,
  }
}

export interface DatasetCapabilities {
  canPublish: boolean
  canConvert: boolean
  canSlice: boolean
  canViewAttributes: boolean
  canPreview: boolean
  canDelete: boolean
}

/** 将状态机约束集中到领域层，页面和批量操作使用相同规则。 */
export function getDatasetCapabilities(row: UnifiedRow): DatasetCapabilities {
  const ingestPending = row.src === 'layer' && (row.ingestPending === true || row.raw?.ingestPending === true) && !row.ingestReady && !row.published
  const busy = row.status === 'uploading' || row.status === 'converting' || ingestPending
  const ready = row.status === 'ok' && !ingestPending
  const convertReady = row.src === 'm3d' && ['1', '5'].includes(String(row.raw?.status ?? ''))
  return {
    canPublish: row.src === 'layer' && ready,
    canConvert: convertReady,
    canSlice: row.src === 'layer' && isRasterTypeKey(row.typeKey) && ready,
    canViewAttributes: row.src === 'layer' && !isRasterTypeKey(row.typeKey) && ready,
    canPreview: row.published && !busy,
    canDelete: !busy,
  }
}
