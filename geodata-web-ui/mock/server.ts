import { gzipSync } from 'node:zlib'
import { randomUUID } from 'node:crypto'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Plugin } from 'vite'
import { inspectShapefile } from './shapefile'

// 仅在 Vite 开发服务器中使用。所有数据均为虚构示例，重启开发服务器后恢复初始值。
type Row = Record<string, any>
const GEOM = {
  point: '6b6941f1-67a3-11e7-8eb2-005056bb1c7e',
  line: '7776934c-67a3-11e7-8eb2-005056bb1c7e',
  polygon: 'a2758dc0-67a3-11e7-8eb2-005056bb1c7e',
  raster: 'acf11b57-0626-4e49-b385-2e9a4195221c',
}
const stamp = '2026-10-20 09:30:00'
const seed = () => ({
  types: [
    { id: 'cat-base', typeName: '基础地理', parentId: '', codeSort: 1, dictCodeID: 'ba5ab799-6acd-11e7-87f3-005056bb1c7e' },
    { id: 'cat-traffic', typeName: '交通设施', parentId: 'cat-base', codeSort: 1 },
    { id: 'cat-building', typeName: '建筑与用地', parentId: 'cat-base', codeSort: 2 },
    { id: 'cat-image', typeName: '遥感影像', parentId: '', codeSort: 2 },
    { id: 'cat-model', typeName: '三维模型', parentId: '', codeSort: 3 },
  ],
  layers: [
    { id: 'layer-road', layerName: '赤壁市主干道路', dataType: GEOM.line, uploadFileType: '1', uploadFileName: 'chibi_roads.shp', layerType: 'cat-traffic', layerRefenceName: 'EPSG:4326', layerBBox: '113.72,29.57,114.12,29.89', fileSize: 18420, uploadStatus: '1', publishStatus: '1', createBy: 'admin', createDT: '2026-10-20 09:20:00', layerDesc: '道路中心线示例数据', layerspatialtable: 'mock_roads' },
    { id: 'layer-building', layerName: '中心城区建筑轮廓', dataType: GEOM.polygon, uploadFileType: '1', uploadFileName: 'buildings.geojson', layerType: 'cat-building', layerRefenceName: 'EPSG:4326', layerBBox: '113.83,29.68,113.99,29.78', fileSize: 32600, uploadStatus: '1', publishStatus: '1', createBy: 'admin', createDT: '2026-10-19 16:42:00', layerDesc: '建筑面示例数据', layerspatialtable: 'mock_buildings' },
    { id: 'layer-poi', layerName: '应急设施点位', dataType: GEOM.point, uploadFileType: '1', uploadFileName: 'emergency_poi.shp', layerType: 'cat-base', layerRefenceName: 'EPSG:4326', layerBBox: '113.75,29.58,114.08,29.86', fileSize: 2800, uploadStatus: '1', publishStatus: '0', createBy: 'admin', createDT: '2026-10-18 11:08:00', layerDesc: '应急设施点位示例数据', layerspatialtable: 'mock_poi' },
    { id: 'layer-dom', layerName: '城区 DOM 正射影像', dataType: GEOM.raster, uploadFileType: '2', uploadFileName: 'city_dom.tif', layerType: 'cat-image', layerRefenceName: 'EPSG:4326', layerBBox: '113.80,29.62,114.05,29.83', fileSize: 495000, rasterCount: 3, rasterDatatype: 'Byte', uploadStatus: '1', publishStatus: '1', createBy: 'admin', createDT: '2026-10-17 14:36:00', layerDesc: '影像示例数据', layerspatialtable: 'mock_dom' },
    { id: 'layer-dem', layerName: '赤壁地形 DEM', dataType: GEOM.raster, uploadFileType: '2', uploadFileName: 'terrain_dem.tif', layerType: 'cat-image', layerRefenceName: 'EPSG:4326', layerBBox: '113.65,29.45,114.25,30.02', fileSize: 128000, rasterCount: 1, rasterDatatype: 'Float32', uploadStatus: '1', publishStatus: '0', createBy: 'admin', createDT: '2026-10-15 10:15:00', layerDesc: '地形示例数据', layerspatialtable: 'mock_dem' },
  ],
  models: [
    { id: 'model-osgb', name: '中心城区倾斜摄影', modelType: 'OSGB', srcFormat: 'OSGB', fileName: 'city_osgb.zip', totalSize: 1430000000, partCount: 274, status: '4', progress: 100, srs: 'EPSG:4547', createUser: 'admin', createTime: '2026-10-16 15:18:00' },
    { id: 'model-point', name: '滨江点云样本', modelType: 'POINTCLOUD', srcFormat: 'LAS', fileName: 'riverside.las', totalSize: 245000000, partCount: 47, status: '1', progress: 100, srs: 'EPSG:4547', createUser: 'admin', createTime: '2026-10-14 09:44:00' },
  ],
  maps: [
    { id: 'map-city', mapName: '赤壁城区综合地图', mapEnName: 'chibi_city', mapType: '基础地理', mapTypeCode: 'cat-base', spatialRefence: 'EPSG:4326', mapStatus: '1', mapPublishAddress: '/mock-service/wms?layers=mock:chibi_city', createUserName: 'admin', createUserId: 'admin', createDT: '2026-10-18 15:00:00', dataConfidential: '内部', dataDesc: '道路、建筑与影像综合示例' },
    { id: 'map-traffic', mapName: '交通设施专题图', mapEnName: 'traffic', mapType: '交通设施', mapTypeCode: 'cat-traffic', spatialRefence: 'EPSG:4326', mapStatus: '0', mapPublishAddress: '', createUserName: 'admin', createUserId: 'admin', createDT: '2026-10-17 10:00:00', dataConfidential: '内部', dataDesc: '交通数据示例' },
  ],
  relations: [
    { id: 'rel-road', mapId: 'map-city', dataConfigID: 'layer-road', dataSort: 1, dataStyleID: 'style-road' },
    { id: 'rel-building', mapId: 'map-city', dataConfigID: 'layer-building', dataSort: 2, dataStyleID: 'style-building' },
    { id: 'rel-dom', mapId: 'map-city', dataConfigID: 'layer-dom', dataSort: 3, dataStyleID: '' },
    { id: 'rel-traffic', mapId: 'map-traffic', dataConfigID: 'layer-road', dataSort: 1, dataStyleID: 'style-road' },
  ],
  tags: [
    { id: 'tag-base', tagName: '基础底图', dataType: 'vector', tagDesc: '基础地理底图', createUser: 'admin', createDT: stamp },
    { id: 'tag-traffic', tagName: '交通', dataType: 'vector', tagDesc: '交通专题', createUser: 'admin', createDT: stamp },
    { id: 'tag-imagery', tagName: '影像', dataType: 'raster', tagDesc: '遥感影像', createUser: 'admin', createDT: stamp },
  ],
  tagRelations: [
    { id: 'tr-1', tagName: '交通', dataType: 'vector', dataID: 'layer-road', createUser: 'admin', createDT: stamp },
    { id: 'tr-2', tagName: '影像', dataType: 'raster', dataID: 'layer-dom', createUser: 'admin', createDT: stamp },
  ],
  dictTypes: [
    { id: 'ba5ab799-6acd-11e7-87f3-005056bb1c7e', typeName: '数据分类', typeCode: 'DATA_CATEGORY', typeDesc: '示例目录分类' },
    { id: 'dict-srs', typeName: '空间参考系', typeCode: 'SRS', typeDesc: '常用坐标系' },
  ],
  dictCodes: [
    { id: 'code-vector', typeID: 'ba5ab799-6acd-11e7-87f3-005056bb1c7e', code: 'vector', codeName: '矢量数据', codeSort: 1 },
    { id: 'code-raster', typeID: 'ba5ab799-6acd-11e7-87f3-005056bb1c7e', code: 'raster', codeName: '栅格数据', codeSort: 2 },
    { id: 'code-4326', typeID: 'dict-srs', code: 'EPSG:4326', codeName: 'WGS 84 (4326)', codeSort: 1 },
    { id: 'code-4547', typeID: 'dict-srs', code: 'EPSG:4547', codeName: 'CGCS2000 (4547)', codeSort: 2 },
  ],
  styles: [
    { id: 'style-road', styleName: '道路蓝线', styleDataType: GEOM.line, styleConfigType: '1', renderType: '单一符号', styleDefaultLayer: 'layer-road', createBy: 'admin', createDT: stamp, styleInfo: { lineColor: '#2f78c8', lineWidth: 2 } },
    { id: 'style-building', styleName: '建筑暖灰面', styleDataType: GEOM.polygon, styleConfigType: '1', renderType: '单一符号', styleDefaultLayer: 'layer-building', createBy: 'admin', createDT: stamp, styleInfo: { fillColor: '#d5a989' } },
    { id: 'style-dom', styleName: '影像自然色', styleDataType: GEOM.raster, styleConfigType: '4', renderType: '单波段', styleDefaultLayer: 'layer-dom', createBy: 'admin', createDT: stamp },
  ],
  symbols: [
    { id: 'symbol-poi', symbolName: '应急点位', symbolType: '点', symbolClassify: '公共设施', imageorFontType: '字体', charUnicode: '●', fontColor: '#e85f41', fontSize: '14', createBy: 'admin', createDT: stamp },
    { id: 'symbol-road', symbolName: '交通路线', symbolType: '线', symbolClassify: '交通', imageorFontType: '字体', fontColor: '#2f78c8', fontSize: '12', createBy: 'admin', createDT: stamp },
  ],
  tasks: [
    { id: 'task-1', taskId: 'task-1', layerName: '城区 DOM 裁剪', origin: '1', status: 'SUCCESS', progress: 100, createBy: 'admin', createDT: '2026-10-19 18:25:00' },
    { id: 'task-2', taskId: 'task-2', layerName: '道路缓冲区分析', origin: '7', status: 'SUCCESS', progress: 100, createBy: 'admin', createDT: '2026-10-18 11:30:00' },
  ],
  logs: [
    { id: 'log-1', objectName: '赤壁市主干道路', operType: '发布', createBy: 'admin', createDT: '2026-10-20 09:25:00', description: '示例操作记录' },
    { id: 'log-2', objectName: '赤壁城区综合地图', operType: '更新', createBy: 'admin', createDT: '2026-10-19 14:00:00', description: '示例操作记录' },
  ],
  folders: ['默认样式', '专题地图'],
})

const db = seed()
const tileJobs = new Map<string, number>([['layer-dom', 0]])
const uploadedChunks = new Map<string, Map<number, Buffer>>()
const uploadedNames = new Map<string, string>()
const uploadedFiles = new Map<string, Buffer>()
const uploadedAttrs = new Map<string, Row[]>()
const serviceFormats = new Map<string, Set<string>>([
  ['layer-road', new Set(['WMS', 'WFS', 'MVT'])],
  ['layer-building', new Set(['WMS', 'WFS', 'MVT'])],
  ['layer-dom', new Set(['WMS', 'WMTS'])],
])
let mockToken = ''
let sequence = 100
const id = (prefix: string) => `${prefix}-mock-${++sequence}`
const success = (result: unknown) => ({ code: 200, success: true, result, msg: '成功' })
const page = (rows: Row[], q: URLSearchParams) => {
  const size = Math.max(1, Number(q.get('pageSize') || 10))
  const index = Math.max(1, Number(q.get('pageIndex') || q.get('pageNum') || 1))
  return { items: rows.slice((index - 1) * size, index * size), totalCount: rows.length }
}
const byId = (rows: Row[], value: string | null) => rows.find((r) => String(r.id) === String(value || '')) || null
const listBy = (rows: Row[], key: string, value: string | null) => value ? rows.filter((r) => String(r[key]) === value) : rows
const formValue = (v: unknown) => String(v ?? '')
function normalized(body: Row): Row {
  const out: Row = {}
  for (const [key, value] of Object.entries(body)) {
    const normalizedKey = key === 'ID' ? 'id' : key === 'ParentID' ? 'parentId' : key === 'TypeID' ? 'typeID' : key[0].toLowerCase() + key.slice(1)
    out[normalizedKey] = value
  }
  return out
}
function insert(rows: Row[], body: Row, prefix: string): Row {
  const row = { ...normalized(body), id: id(prefix), createDT: stamp }
  rows.unshift(row)
  return row
}
function update(rows: Row[], body: Row): Row | null {
  const value = normalized(body)
  const row = byId(rows, formValue(value.id))
  if (row) Object.assign(row, value)
  return row
}
function remove(rows: Row[], value: string | null): boolean {
  const index = rows.findIndex((r) => String(r.id) === String(value))
  if (index < 0) return false
  rows.splice(index, 1)
  return true
}
function categoryCounts(): Row[] {
  return db.types.map((t) => ({ dataType: t.id, count: db.layers.filter((l) => l.layerType === t.id).length }))
}
function categoryTree(parentId = ''): Row[] {
  return db.types.filter((t) => String(t.parentId || '') === parentId).map((t) => ({
    id: t.id, name: t.typeName, totalLayerCount: db.layers.filter((l) => l.layerType === t.id).length,
    children: categoryTree(t.id),
  }))
}
function layerList(body: Row, q: URLSearchParams): Row[] {
  return db.layers.filter((l) => {
    if (body.layerName && !String(l.layerName).toLowerCase().includes(String(body.layerName).toLowerCase())) return false
    if (body.dataType && !String(body.dataType).split(',').includes(String(l.dataType))) return false
    if (body.layerType && l.layerType !== body.layerType) return false
    if (body.publishStatus && l.publishStatus !== body.publishStatus) return false
    if (q.get('publishStatus') && l.publishStatus !== q.get('publishStatus')) return false
    return true
  }).map((l) => ({ ...l, layerTypeName: byId(db.types, l.layerType)?.typeName || '' }))
}
function mapRelations(mapId: string | null): Row[] {
  return listBy(db.relations, 'mapId', mapId).map((r) => ({
    ...r, layers: byId(db.layers, r.dataConfigID), dataConfigName: byId(db.layers, r.dataConfigID)?.layerName || '',
  }))
}
function attrs(layerId: string | null): Row[] {
  if (layerId && uploadedAttrs.has(layerId)) return uploadedAttrs.get(layerId) || []
  const layer = byId(db.layers, layerId)
  if (!layer || layer.dataType === GEOM.raster) return []
  return [
    { gid: 1, name: layer.layerName + ' A', category: '示例', value: 126.5 },
    { gid: 2, name: layer.layerName + ' B', category: '示例', value: 88.2 },
    { gid: 3, name: layer.layerName + ' C', category: '示例', value: 73.8 },
  ]
}
const ramps = [
  { colorName: '海洋蓝', colors: ['#d9eff7', '#75bfd5', '#286b9c', '#123c62'] },
  { colorName: '地形暖色', colors: ['#f7e4ba', '#dfad73', '#a96c43', '#70472f'] },
]

function route(method: string, path: string, q: URLSearchParams, body: Row, authorization = ''): unknown {
  const p = path.toLowerCase()
  const get = (key: string) => q.get(key) || q.get(key[0].toUpperCase() + key.slice(1))
  if (p === '/auth/dronelogin' && method === 'POST') {
    if (body.username !== 'admin' || body.password !== 'admin123') {
      return { __mockStatus: 401, code: 401, msg: '账号或密码错误' }
    }
    mockToken = 'mock-' + randomUUID()
    return { code: 200, msg: '成功', data: { access_token: mockToken, expires_in: 3600 } }
  }
  if (p === '/system/user/getinfo') {
    if (!mockToken || authorization !== `Bearer ${mockToken}`) {
      return { __mockStatus: 401, code: 401, msg: '登录状态已失效' }
    }
    return { code: 200, msg: '成功', user: { userName: 'admin', nickName: '管理员' }, roles: ['admin'], permissions: ['*:*:*'] }
  }
  if (p === '/auth/logout') {
    mockToken = ''
    return { code: 200, msg: '成功' }
  }
  if (p === '/region/list') return { code: 200, msg: '成功', data: [
    { code: '421281', name: '赤壁市', regionLevel: 3, parentCode: '421200' },
    { code: '421281001', name: '蒲圻街道', regionLevel: 4, parentCode: '421281' },
  ] }

  if (p === '/datatype/getalllist') return db.types
  if (p === '/datatype/getalllistbydatatype') return listBy(db.types, 'type', get('dataType'))
  if (p === '/datatype/getdetailbyid') return success(byId(db.types, get('id')))
  if (p === '/datatype/getdetailbyname') return success(db.types.find((r) => r.typeName === get('name')) || null)
  if (p === '/datatype/insert') return success(insert(db.types, body, 'cat'))
  if (p === '/datatype/update') return success(update(db.types, body))
  if (p === '/datatype/delete') { const ok = remove(db.types, get('id')); if (ok) db.layers.forEach((r) => { if (r.layerType === get('id')) r.layerType = '' }); return success(ok) }
  if (p === '/datatype/updatelist') { for (const row of Array.isArray(body) ? body : []) update(db.types, row); return success(true) }

  if (p === '/layercontent/getalllist') return success(layerList({}, q))
  if (p === '/layercontent/getallcount') return db.layers.length
  if (p === '/layercontent/getallcountbydatatype') return success(categoryCounts())
  if (p === '/layercontent/getpagelistbyname') return page(layerList(body, q), q)
  if (p === '/layercontent/getalllistbyname') return { item: layerList(body, q) }
  if (p === '/layercontent/getallliststatus') return page(layerList(Object.fromEntries(q), q), q)
  if (p === '/layercontent/getalllistbymapid') return { item: mapRelations(get('mapID')).map((r) => r.layers).filter(Boolean) }
  if (p === '/layercontent/getdetailbyid') return success(byId(layerList({}, q), get('id')))
  if (p === '/upload/uploadforbreakpoint/filemerge') {
    const guid = formValue(body.guid)
    const fileName = formValue(body.fileName)
    const parts = uploadedChunks.get(guid)
    if (!parts || uploadedNames.get(guid) !== fileName || !parts.size) throw new Error('找不到该文件的分片，请重新上传')
    const numbers = [...parts.keys()].sort((a, b) => a - b)
    if (numbers.some((value, index) => value !== index + 1)) throw new Error('分片不完整')
    const file = Buffer.concat(numbers.map((number) => parts.get(number)!))
    uploadedFiles.set(fileName, file)
    uploadedChunks.delete(guid)
    uploadedNames.delete(guid)
    return success({ logicName: fileName, physicalName: `/mock-upload/files/${fileName}`, fileSize: String(file.length) })
  }
  if (p === '/upload/uploadforbreakpoint/parts') {
    const guid = formValue(get('guid'))
    return success([...uploadedChunks.get(guid)?.keys() || []].sort((a, b) => a - b))
  }
  if (p === '/layercontent/insert') {
    if (body.uploadFileType !== '1') return { __mockError: '本地 Mock 仅模拟 SHP 矢量入库；栅格入库需要真实后端。' }
    const fileName = formValue(get('filePathName'))
    const stem = fileName.replace(/\.shp$/i, '')
    if (!stem || !/\.shp$/i.test(fileName)) throw new Error('请先上传 .shp 主文件')
    const files = new Map<string, Buffer>()
    for (const ext of ['shp', 'shx', 'dbf', 'prj', 'cpg']) {
      const file = uploadedFiles.get(`${stem}.${ext}`)
      if (file) files.set(ext, file)
    }
    const parsed = inspectShapefile(files)
    const row = insert(db.layers, {
      ...body,
      dataType: GEOM[parsed.geometry as keyof typeof GEOM],
      layerBBox: parsed.bbox,
      layerAttrTable: `mock_${stem.replace(/[^a-z\d_]/gi, '_')}`,
      layerSpatialTable: `mock_${stem.replace(/[^a-z\d_]/gi, '_')}`,
      fileSize: parsed.fileSize,
      uploadStatus: '0',
      publishStatus: '0',
      createBy: body.createBy || get('user') || 'admin',
    }, 'layer')
    uploadedAttrs.set(row.id, parsed.attributes)
    db.logs.unshift({ id: id('log'), objectName: row.layerName, operType: '入库', createBy: row.createBy, createDT: stamp, description: '本地 Mock SHP 入库' })
    return success(row)
  }
  if (p === '/layercontent/getlayertree') return success(gzipSync(JSON.stringify(categoryTree())).toString('base64'))
  if (p === '/layercontent/getlayertreebytype') return success(gzipSync(JSON.stringify(categoryTree().filter((r) => r.id === get('dataTypeId')))).toString('base64'))
  if (p === '/layercontent/getattrtablebylayerid') {
    const rows = attrs(get('layerId'))
    if (uploadedAttrs.has(formValue(get('layerId'))) && !rows.some((row) => 'Name' in row)) return success(null)
    const result = page(rows, q)
    return success({ items: [JSON.stringify(result.items)], totalCount: result.totalCount })
  }
  if (p === '/layercontent/getlayerattrtablebywhere') return success(page(attrs(get('layerId')), q))
  if (p === '/layercontent/getlayerattrtableddetail') {
    const rows = attrs(get('layerID'))
    const result = page(rows, q)
    return JSON.stringify({ LayerField: Object.keys(rows[0] || {}).join(','), DataTableJson: JSON.stringify({ data: result.items }), TotalCount: result.totalCount })
  }
  if (p === '/layercontent/getfieldbylayerid') return Object.keys(attrs(get('layerId'))[0] || {}).map((name) => ({ attributeName: name, fieldName: name, dataType: name === 'gid' ? 'number' : 'string' }))
  if (p === '/layercontent/update') return success(update(db.layers, body))
  if (p === '/layercontent/updatestatus') { const row = byId(db.layers, get('layerID')); if (row) row.uploadStatus = '1'; return success(Boolean(row)) }
  if (p === '/layercontent/deletelayer' || p === '/layercontent/delete') {
    const layerId = formValue(get('id'))
    uploadedAttrs.delete(layerId)
    serviceFormats.delete(layerId)
    return remove(db.layers, layerId)
  }
  if (p === '/layercontent/updatelist' || p === '/layercontent/updatebothlist') { for (const row of Array.isArray(body) ? body : []) { const l = byId(db.layers, row.id); if (l) l.layerType = row.parentId || '' } return true }
  if (p === '/layercontent/validatecondition') return true
  if (p === '/layercontent/getlayervalue') return success({ value: 126.5 })

  if (p === '/model3d/page') {
    const keyword = get('keyword') || ''
    const modelType = get('modelType') || ''
    const status = get('status') || ''
    const rows = db.models.filter((r) => (!keyword || r.name.includes(keyword)) && (!modelType || r.modelType === modelType) && (!status || r.status === status))
    return success({ records: page(rows, q).items, total: rows.length })
  }
  if (/^\/model3d\/[^/]+$/.test(p)) { const modelId = path.split('/')[2]; return method === 'DELETE' ? success(remove(db.models, modelId)) : success(byId(db.models, modelId)) }
  if (/^\/model3d\/[^/]+\/publish-url$/.test(p)) return success(`/mock-service/tileset/${path.split('/')[2]}/tileset.json`)

  if (p === '/map/getalllist') return { item: db.maps }
  if (p === '/map/getdetailbyid') return success(byId(db.maps, get('id')))
  if (p === '/map/insert') return success(insert(db.maps, { ...body, mapStatus: '0', createUserName: body.createUserName || 'admin' }, 'map'))
  if (p === '/map/update') return success(update(db.maps, body))
  if (p === '/map/delete') { db.relations = db.relations.filter((r) => r.mapId !== get('id')); return remove(db.maps, get('id')) }
  if (p === '/map/updatemapbbox') return true
  if (p === '/mapreleation/getalllist') return { item: mapRelations(null) }
  if (p === '/mapreleation/getalllistbymapid') return { item: mapRelations(get('mapId')) }
  if (p === '/mapreleation/getalllistbylayerid') return { item: db.relations.filter((r) => r.dataConfigID === get('layerId')) }
  if (p === '/mapreleation/getmapreleationbystyle') return db.relations.filter((r) => r.dataStyleID === get('styleID'))
  if (p === '/mapreleation/multiinsert') { for (const row of Array.isArray(body) ? body : []) insert(db.relations, row, 'rel'); return true }
  if (p === '/mapreleation/multiupdate') { const mapId = get('mapId') || get('mapID'); db.relations = db.relations.filter((r) => r.mapId !== mapId); for (const row of Array.isArray(body) ? body : []) insert(db.relations, { ...row, mapId }, 'rel'); return true }
  if (p === '/mapreleation/publicmap') { const row = byId(db.maps, get('mapId')); if (row) row.mapStatus = '1'; return Boolean(row) }
  if (p === '/mapreleation/refreshmap') return true
  if (p === '/mapreleation/tileprogress') return success({ status: 'DONE', percent: 100, tilesProcessed: 128, tilesTotal: 128 })
  if (p === '/serverinterface/tileprogress') {
    const started = tileJobs.get(formValue(get('dataId')))
    if (started === undefined) return success({ status: 'IDLE', percent: 0, tilesProcessed: 0, tilesTotal: 0 })
    const percent = started === 0 ? 100 : Math.min(100, Math.floor((Date.now() - started) / 60))
    return success({ status: percent >= 100 ? 'DONE' : 'RUNNING', percent, tilesProcessed: Math.floor(percent * 1.28), tilesTotal: 128 })
  }
  if (p === '/serverinterface/getserverinterface') {
    const layer = byId(db.layers, get('dataId'))
    const kind = get('serverType') || ''
    const allowed = layer && layer.publishStatus === '1' && serviceFormats.get(layer.id)?.has(kind)
      && (kind !== 'WMTS' || !layer || layer.dataType !== GEOM.raster || (tileJobs.has(layer.id) && (tileJobs.get(layer.id) === 0 || Date.now() - tileJobs.get(layer.id)! >= 6000)))
    return success({ publishType: kind, list: allowed ? [{ '关键字': kind, '服务路径': `/mock-service/${kind.toLowerCase()}?layers=mock:${layer.id}` }] : [] })
  }
  if (p === '/serverinterface/publishservice') {
    const layer = byId(db.layers, formValue(body.id))
    if (layer) {
      layer.publishStatus = '1'
      const formats = serviceFormats.get(layer.id) || new Set<string>()
      for (const format of String(body.publishType || 'WMS').toUpperCase().split(',')) formats.add(format.trim())
      serviceFormats.set(layer.id, formats)
      if (layer.dataType === GEOM.raster && String(body.publishType || '').toUpperCase().split(',').includes('WMTS')) tileJobs.set(layer.id, Date.now())
    }
    return success(Boolean(layer))
  }

  if (p === '/datatag/getalllist') return success(db.tags)
  if (p === '/datatag/getalllistbydatatype') return success(listBy(db.tags, 'dataType', get('dataType')))
  if (p === '/datatag/getdetailbyid') return success(byId(db.tags, get('id')))
  if (p === '/datatag/insert') return success(insert(db.tags, body, 'tag'))
  if (p === '/datatag/update') return success(update(db.tags, body))
  if (p === '/datatag/delete') return success(remove(db.tags, get('id')))
  if (p === '/tagreleation/getalllist') return success(db.tagRelations)
  if (p === '/tagreleation/getmultitagnamebymapid') return success(db.tagRelations.filter((r) => r.dataID === get('mapLayerID')).map((r) => r.tagName).join(','))
  if (p === '/tagreleation/multiinsert') { for (const tagName of String(body.tagName || '').split(',').filter(Boolean)) insert(db.tagRelations, { ...body, tagName }, 'tr'); return success(true) }
  if (p === '/tagreleation/update') return success(update(db.tagRelations, body))
  if (p === '/tagreleation/delete') return success(remove(db.tagRelations, get('id')))

  if (p === '/dicdatatype/getalllist') return success(db.dictTypes)
  if (p === '/dicdatatype/getdetailbyid') return success(byId(db.dictTypes, get('id')))
  if (p === '/dicdatatype/insert') return success(insert(db.dictTypes, body, 'dict'))
  if (p === '/dicdatatype/update') return success(update(db.dictTypes, body))
  if (p === '/dicdatatype/delete') return success(remove(db.dictTypes, get('id')))
  if (p === '/dicdatacode/getalllist') return success(db.dictCodes)
  if (p === '/dicdatacode/getdetailbytypeid') return success(listBy(db.dictCodes, 'typeID', get('typeID')))
  if (p === '/dicdatacode/getdetailbyconn') return success(listBy(db.dictCodes, 'typeID', get('typeID')).filter((r) => !get('keyWord') || r.codeName.includes(get('keyWord'))))
  if (p === '/dicdatacode/insert') return success(insert(db.dictCodes, body, 'code'))
  if (p === '/dicdatacode/update') return success(update(db.dictCodes, body))
  if (p === '/dicdatacode/delete') return success(remove(db.dictCodes, get('id')))

  if (p === '/datastyle/getalllist') return success(db.styles)
  if (p === '/datastyle/getalllistpage') return page(db.styles.filter((r) => !body.styleName || r.styleName.includes(body.styleName)), q)
  if (p === '/datastyle/getdetailbyid') return success(byId(db.styles, get('id')))
  if (p === '/datastyle/getdetailbylayerid') return success(db.styles.find((r) => r.styleDefaultLayer === get('id')) || null)
  if (p === '/datastyle/isexists') return success(db.styles.some((r) => r.styleName === get('name')))
  if (p === '/datastyle/insert') return success(insert(db.styles, body, 'style'))
  if (p === '/datastyle/update') return success(update(db.styles, body))
  if (p === '/datastyle/delete') return success(remove(db.styles, get('id')))
  if (p === '/datastyle/deletebyid') return success(remove(db.styles, formValue(body.styleId)))
  if (p === '/datastyle/getlinearcolorramps' || p === '/datastyle/getrandomcolorramps') return success(ramps)
  if (p === '/datastyle/getlinearcolorrampsbyname' || p === '/datastyle/getrandomcolorrampsbyname') return success({ colorList: (ramps.find((r) => r.colorName === get('colorName')) || ramps[0]).colors.slice(0, Number(get('count') || 4)) })
  if (p === '/datastyle/getallfolders') return success(db.folders)
  if (p === '/datastyle/getallfiles') return success([])
  if (p === '/datastyle/addfolder') { db.folders.push(formValue(get('folderName'))); return success(true) }
  if (p === '/datastyle/updatefolder') { const i = db.folders.indexOf(formValue(get('oldName'))); if (i >= 0) db.folders[i] = formValue(get('newName')); return success(i >= 0) }
  if (p === '/datastyle/deletefolder') { const i = db.folders.indexOf(formValue(get('folderName'))); if (i >= 0) db.folders.splice(i, 1); return success(i >= 0) }
  if (p === '/datastyle/getxmlcontent') return success('<StyledLayerDescriptor><!-- 本地 Mock 样式 --></StyledLayerDescriptor>')
  if (p === '/datastyle/gettifmaxmin' || p === '/datastyle/getshpmaxmin') return success({ min: 0, max: 255, minValue: 0, maxValue: 255 })
  if (p === '/datastyle/gettifvalues' || p === '/datastyle/getdataattribute') return success([10, 30, 60, 90])
  if (p === '/datastyle/gettifjenksbreaks' || p === '/datastyle/getshpjenksbreaks') return success([0, 64, 128, 192, 255])

  if (p === '/symbol/getalllist') return success(db.symbols)
  if (p === '/symbol/getalllistpage') return page(db.symbols.filter((r) => !body.symbolName || r.symbolName.includes(body.symbolName)), q)
  if (p === '/symbol/getdetailbyid') return success(byId(db.symbols, get('id')))
  if (p === '/symbol/insert') return success(insert(db.symbols, body, 'symbol'))
  if (p === '/symbol/update') return success(update(db.symbols, body))
  if (p === '/symbol/delete') return success(remove(db.symbols, get('id')))

  if (p === '/layertool/getlist') return success(page(listBy(db.tasks, 'origin', get('origin')), q))
  if (p === '/layertool/delete') return success(remove(db.tasks, get('id')))
  if (p === '/layertool/gettifinfo') return success({ width: 4096, height: 4096, xCell: 2, yCell: 2, bandCount: 3 })
  if (p === '/topology/rules') return success([{ code: 'MustNotOverlap', name: '面不能重叠', geometryType: 'polygon' }, { code: 'MustNotHaveGaps', name: '面不能有缝隙', geometryType: 'polygon' }])
  if (/^\/topology\/status\//.test(p)) return success({ hasTopology: false, status: '未创建' })
  if (p === '/layerlog/getlist') return success(page(db.logs, q))
  if (p === '/layerlog/delete') return success(remove(db.logs, get('id')))
  if (p === '/layerfield/getdetailbylayerid' || p === '/layerfield/getalllist') return success(attrs(get('layerID')).length ? ['gid', 'name', 'category', 'value'].map((name) => ({ attributeName: name, fieldName: name })) : [])
  if (p === '/metadata/getdetailbydataid') return success({ id: 'meta-1', dataId: get('dataId'), abstract: '本地 Mock 元数据', keywords: '示例' })
  if (p === '/metadata/getalllist') return success([])

  return { __mockError: `本地 Mock 未实现 ${method} ${path}；此操作需要真实后端。` }
}

function send(res: ServerResponse, data: unknown, status = 200) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.setHeader('Cache-Control', 'no-store')
  res.end(JSON.stringify(data))
}
async function parseBody(req: IncomingMessage): Promise<Row> {
  if (!['POST', 'PUT', 'PATCH'].includes(req.method || '')) return {}
  const chunks: Buffer[] = []
  let bytes = 0
  for await (const chunk of req) {
    bytes += chunk.length
    if (bytes > 1024 * 1024) throw new Error('Mock 请求体上限为 1 MiB')
    chunks.push(chunk)
  }
  const raw = Buffer.concat(chunks).toString('utf8')
  if (!raw) return {}
  const contentType = String(req.headers['content-type'] || '')
  if (contentType.includes('application/json')) return JSON.parse(raw)
  if (contentType.includes('application/x-www-form-urlencoded')) return Object.fromEntries(new URLSearchParams(raw))
  if (contentType.includes('multipart/form-data')) {
    const boundary = /boundary=([^;]+)/.exec(contentType)?.[1]
    if (!boundary) return {}
    const fields: Row = {}
    for (const part of raw.split('--' + boundary)) {
      const name = /Content-Disposition: form-data; name="([^"]+)"/i.exec(part)?.[1]
      if (name) fields[name] = part.split('\r\n\r\n')[1]?.replace(/\r\n$/, '') || ''
    }
    return fields
  }
  return {}
}

async function receiveMockChunk(req: IncomingMessage): Promise<unknown> {
  const contentType = String(req.headers['content-type'] || '')
  if (!contentType.includes('multipart/form-data')) throw new Error('需要 multipart/form-data')
  const chunks: Buffer[] = []
  let bytes = 0
  for await (const part of req) {
    bytes += part.length
    if (bytes > 6 * 1024 * 1024) throw new Error('单个分片不能超过 5 MiB')
    chunks.push(part)
  }
  const request = new Request('http://localhost/mock-upload', {
    method: 'POST', headers: { 'content-type': contentType }, body: new Uint8Array(Buffer.concat(chunks)),
  })
  const form = await request.formData()
  const file = form.get('file')
  const guid = String(form.get('guid') || '')
  const chunk = Number(form.get('chunk'))
  if (!(file instanceof File) || !file.size || !guid || !Number.isInteger(chunk) || chunk < 1) throw new Error('分片参数不完整')
  if (!file.name || file.name === 'blob' || /[\\/]/.test(file.name)) throw new Error('请保留原始文件名上传')
  const existingName = uploadedNames.get(guid)
  if (existingName && existingName !== file.name) throw new Error('同一上传任务的文件名不一致')
  const parts = uploadedChunks.get(guid) || new Map<number, Buffer>()
  parts.set(chunk, Buffer.from(await file.arrayBuffer()))
  uploadedChunks.set(guid, parts)
  uploadedNames.set(guid, file.name)
  return success('分片上传成功')
}

export function localMockPlugin(): Plugin {
  return {
    name: 'geodata-local-mock',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = new URL(req.url || '/', 'http://localhost')
        if (url.pathname.startsWith('/mock-service/')) {
          if (url.pathname.endsWith('/tileset.json')) return send(res, { asset: { version: '1.0' }, geometricError: 0, root: { boundingVolume: { region: [1.98, 0.51, 1.99, 0.52, 0, 10] }, geometricError: 0 } })
          res.setHeader('Content-Type', 'image/svg+xml')
          res.end('<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256"><rect width="100%" height="100%" fill="#e6eef5" fill-opacity="0.65"/><text x="128" y="128" text-anchor="middle" fill="#6285a3" font-size="20">MOCK</text></svg>')
          return
        }
        if (!url.pathname.startsWith('/api/') && !url.pathname.startsWith('/base-api/')) return next()
        const path = url.pathname.replace(/^\/(api|base-api)/, '')
        if (path.toLowerCase() === '/upload/uploadforbreakpoint/addfiles') {
          try { send(res, await receiveMockChunk(req)) }
          catch (error) { send(res, { code: 400, success: false, result: null, msg: error instanceof Error ? error.message : '上传失败' }, 400) }
          return
        }
        if (/^\/Kneecoordinates\//i.test(path) || /^\/model3d\/[^/]+\/part$/i.test(path)) {
          send(res, { code: 501, success: false, result: null, msg: '本地 Mock 不处理真实文件上传。' }, 501)
          return
        }
        try {
          const body = await parseBody(req)
          const result = route(req.method || 'GET', path, url.searchParams, body, String(req.headers.authorization || ''))
          if (result && typeof result === 'object' && '__mockStatus' in result) {
            const { __mockStatus, ...payload } = result as Row
            send(res, payload, Number(__mockStatus))
            return
          }
          if (result && typeof result === 'object' && '__mockError' in result) {
            send(res, { code: 501, success: false, result: null, msg: (result as Row).__mockError }, 501)
            return
          }
          send(res, result)
        } catch (error) {
          send(res, { code: 400, success: false, result: null, msg: error instanceof Error ? error.message : 'Mock 请求解析失败' }, 400)
        }
      })
    },
  }
}
