import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { build } from 'esbuild'

// 仅打包领域纯逻辑，测试不需要启动后端、浏览器或 Pinia。
const bundled = await build({
  stdin: {
    contents: [
      "export * from './src/features/datasets/model.ts'",
      "export * from './src/features/datasets/listState.ts'",
      "export * from './src/features/datasets/ingestion.ts'",
      "export * from './src/features/datasets/query.ts'",
      "export * from './src/features/datasets/services.ts'",
      "export * from './src/features/datasets/service-url.ts'",
      "export * from './src/features/datasets/cache.ts'",
      "export * from './src/utils/async.ts'",
    ].join('\n'),
    resolveDir: fileURLToPath(new URL('..', import.meta.url)),
  },
  bundle: true, write: false, format: 'esm', platform: 'node',
})
const domain = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`)
const { normalizeLayer, normalizeM3d, createDatasetQueryService, createDatasetServiceReader, createReadCache } = domain

function layer(id, time, extra = {}) {
  return normalizeLayer({ id: String(id), layerName: `资源 ${id}`, createDT: time, dataType: '1', uploadStatus: '1', fileSize: id, ...extra })
}
function model(id, time, extra = {}) {
  return normalizeM3d({ id: String(id), name: `模型 ${id}`, createTime: time, modelType: 'OSGB', status: '1', totalSize: id, ...extra })
}
const time = (day) => new Date(Date.UTC(2025, 0, day)).toISOString()
const layers = Array.from({ length: 430 }, (_, index) => layer(index + 1, time(500 - index), { publishStatus: index % 3 === 0 ? '1' : '0' }))
const models = Array.from({ length: 430 }, (_, index) => model(index + 1, time(499.5 - index), { status: index % 4 === 0 ? '3' : '1' }))
const calls = []
const source = (kind, records) => async (_filter, size, index) => {
  assert.ok(size <= 200, 'respect backend page-size limit')
  calls.push({ kind, size, index })
  return { rows: records.slice((index - 1) * size, index * size), total: records.length }
}
const queries = createDatasetQueryService({ layer: source('layer', layers), model: source('model', models), scope: () => 'admin' })
const expected = domain.sortDatasetRows([...layers, ...models])
const deepPage = await queries.loadUnifiedPage({}, 50, 9)
assert.equal(deepPage.total, 860)
assert.deepEqual(deepPage.rows.map((row) => row.key), expected.slice(400, 450).map((row) => row.key), 'deep merged page must include model rows beyond the 200 cap')
assert.ok(calls.some((call) => call.kind === 'model' && call.index === 3))

const asc = await queries.loadUnifiedPage({ order: 'asc' }, 10, 1)
assert.deepEqual(asc.rows.map((row) => row.key), domain.sortDatasetRows([...layers, ...models], 'time', 'asc').slice(0, 10).map((row) => row.key), 'ascending order must apply before pagination')
const filtered = await queries.loadUnifiedPage({ status: 'converting', order: 'asc' }, 20, 2)
const expectedFiltered = domain.sortDatasetRows(models.filter((row) => row.status === 'converting'), 'time', 'asc')
assert.equal(filtered.total, expectedFiltered.length)
assert.deepEqual(filtered.rows.map((row) => row.key), expectedFiltered.slice(20, 40).map((row) => row.key))
const before = calls.length
await queries.loadUnifiedPage({ typeKey: 'vector' }, 10, 30)
assert.deepEqual(calls.slice(before), [{ kind: 'layer', size: 10, index: 30 }], 'single-source pagination should fetch only the requested page')
assert.deepEqual(await queries.loadUnifiedPage({ typeKey: 'invalid' }, 10, 1), { rows: [], total: 0 })
assert.equal(normalizeLayer({ fileSize: 2 }).sizeBytes, 2048, '2D KB and 3D byte sizes must be comparable')
assert.equal(domain.m3dStatus('1', 100).label, '待转换')
assert.equal(domain.getDatasetCapabilities(models.find((row) => row.status === 'converting')).canDelete, false)
assert.equal(domain.getDatasetCapabilities(layer('r', '', { dataType: '2' })).canSlice, true)
assert.equal(domain.DATASET_FILTER_TYPES.find((type) => type.key === 'vector').label, '矢量 / 白模')
assert.equal(domain.DATASET_FILTER_TYPES.find((type) => type.key === 'raster').label, '栅格 / 地形')
assert.ok(!domain.DATASET_FILTER_TYPES.some((type) => ['white', 'terrain'].includes(type.key)))
assert.ok(domain.DATA_TYPES.some((type) => type.key === 'white'), 'upload choices must retain white-model entry')

const uploadedVector = layer('pending-vector', '', { layerSpatialTable: 'preallocated_table' })
assert.equal(uploadedVector.statusLabel, '已上传', 'uploadStatus only confirms the file upload')
assert.equal(domain.needsIngestionVerification(uploadedVector), true)
const pendingVector = domain.applyIngestionDetail(uploadedVector, { id: uploadedVector.id, isShow: 0, layerTypeName: '详情中的分类' })
assert.equal(pendingVector.ingestPending, true)
assert.equal(pendingVector.statusLabel, '入库待确认')
assert.notEqual(pendingVector.status, 'fail', 'hidden tool results are not evidence of ingestion failure')
assert.equal(pendingVector.category, uploadedVector.category, 'detail DTO differences cannot overwrite list presentation fields')
assert.equal(domain.needsIngestionVerification(pendingVector), true, 'pending imports must be rechecked')
for (const capability of ['canPublish', 'canViewAttributes', 'canDelete']) assert.equal(domain.getDatasetCapabilities(pendingVector)[capability], false)
assert.equal(domain.applyIngestionDetail(uploadedVector, null).ingestPending, true, 'missing details must not authorize actions')
assert.equal(domain.applyIngestionDetail(uploadedVector, { id: 'another-layer', isShow: 1 }).ingestPending, true, 'a different layer response cannot authorize actions')
const completeVector = domain.applyIngestionDetail(pendingVector, { id: uploadedVector.id, isShow: 1 })
assert.equal(completeVector.ingestReady, true)
assert.equal(completeVector.ingestPending, false)
assert.equal(completeVector.statusLabel, '已入库')
assert.equal(domain.needsIngestionVerification(completeVector), false)
for (const capability of ['canPublish', 'canViewAttributes', 'canDelete']) assert.equal(domain.getDatasetCapabilities(completeVector)[capability], true)
assert.equal(layer('visible', '', { isShow: '1' }).statusLabel, '已入库')
const publishedVector = layer('published', '', { publishStatus: '1' })
assert.equal(publishedVector.ingestReady, true)
assert.equal(domain.needsIngestionVerification(publishedVector), false)
const rasterWithoutDetail = layer('raster-only', '', { dataType: '2' })
assert.equal(domain.needsIngestionVerification(rasterWithoutDetail), false)
assert.equal(domain.applyIngestionDetail(rasterWithoutDetail, null), rasterWithoutDetail)
assert.equal(domain.needsIngestionVerification(models[0]), false)

const parseDate = Date.parse
try {
  // 模拟 Safari 只接受 ISO 日期时间分隔符，验证后端空格时间仍能参与排序。
  Date.parse = (value) => value.includes(' ') ? NaN : parseDate(value)
  assert.deepEqual(domain.sortDatasetRows([
    layer('older', '2026-10-20 23:59:59'),
    layer('newer', '2026-10-22 00:00:01'),
    layer('middle', '2026-10-21T12:00:00'),
  ]).map((row) => row.id), ['newer', 'middle', 'older'])
} finally { Date.parse = parseDate }
assert.throws(() => domain.readDatasetPage({ error: 'offline' }, normalizeLayer), /响应格式异常/)
assert.throws(() => domain.readDatasetPage(null, normalizeLayer), /响应格式异常/)
assert.equal(domain.readDatasetPage({ items: [], totalCount: '0' }, normalizeLayer).total, 0)
const failedQueries = createDatasetQueryService({
  scope: () => 'admin',
  layer: async () => { throw new Error('layer offline') },
  model: async () => ({ rows: models.slice(0, 10), total: models.length }),
})
await assert.rejects(failedQueries.loadUnifiedPage({}, 10, 1), /layer offline/, 'failed source must not masquerade as an empty or partial result')
let actor = 'first-session'
let releaseQuery
const changingSession = createDatasetQueryService({
  scope: () => actor,
  layer: () => new Promise((resolve) => { releaseQuery = resolve }),
  model: async () => ({ rows: [], total: 0 }),
})
const oldQuery = changingSession.loadUnifiedPage({ typeKey: 'vector' }, 10, 1)
await Promise.resolve()
actor = 'second-session'
changingSession.invalidateDatasetQueries()
releaseQuery({ rows: layers.slice(0, 10), total: layers.length })
await assert.rejects(oldQuery, /登录状态已变化/, 'a response from a previous session must not reach the current page')

assert.equal(domain.extractSvcUrl({ list: [{ '关键字': 'WMS', '服务路径': '/wms' }] }, 'WFS'), '', 'a WMS-only response must not be counted as WFS')
assert.deepEqual(domain.protocolsFor('single'), ['3DTiles'])
const serviceCalls = []
const serviceReader = createDatasetServiceReader({
  scope: () => 'admin',
  address: async (id, protocol) => { serviceCalls.push(protocol); return { list: [{ serverKeyWord: protocol, serverPath: `/${id}/${protocol}` }] } },
  publishUrl: async (id) => `/${id}/tileset.json`,
  tileProgress: async () => { serviceCalls.push('progress'); return { result: { status: 'RUNNING', percent: 42 } } },
})
const raster = layer('raster', '', { dataType: '2', publishStatus: '1' })
const states = await Promise.all([serviceReader.getDatasetServiceState(raster), serviceReader.getDatasetServiceState(raster)])
assert.deepEqual(serviceCalls, ['WMS', 'WMTS', 'progress'], 'concurrent consumers should share the same protocol and progress reads')
assert.deepEqual(states[0].formats, ['WMS', 'WMTS'])
assert.equal(states[0].progress.percent, 42)
assert.equal(states[0].tile, '切片中')
await serviceReader.getDatasetServiceState(raster)
assert.equal(serviceCalls.length, 3, 'stable service reads should use the short cache')
await serviceReader.getDatasetServiceState(raster, { force: true })
assert.equal(serviceCalls.length, 6, 'force should bypass both caches')
serviceReader.invalidateDatasetServices(raster)
await serviceReader.getDatasetServiceState(raster)
assert.equal(serviceCalls.length, 9, 'mutation invalidation must force a fresh read')
serviceCalls.length = 0
await serviceReader.getDatasetServiceState(layer('vector', '', { publishStatus: '1' }))
assert.deepEqual(serviceCalls, ['WMS', 'WFS', 'MVT'], 'vector rows must not request raster protocols or tile progress')
const unavailableServices = createDatasetServiceReader({
  scope: () => 'admin',
  address: async () => { throw new Error('offline') },
  publishUrl: async () => { throw new Error('offline') },
  tileProgress: async () => { throw new Error('offline') },
})
const unavailable = await unavailableServices.getDatasetServiceState(raster)
assert.equal(unavailable.formats.length, 0)
assert.match(unavailable.error, /WMS 服务地址暂不可用/)
assert.equal(unavailable.progress.error, '进度暂不可用')
await assert.rejects(unavailableServices.fetchAllSvcAddresses(raster), /服务地址暂不可用/, 'network failure must remain distinct from an unpublished service')

const cache = createReadCache(30_000)
let finish
const staleRead = cache.read('row', () => new Promise((resolve) => { finish = resolve }))
await Promise.resolve()
cache.invalidate('row')
assert.equal(await cache.read('row', async () => 'new'), 'new')
finish('old')
await staleRead
assert.equal(await cache.read('row', async () => 'unexpected'), 'new', 'an invalidated request cannot overwrite a fresh value')
console.log('Dataset domain: pagination, sorting, state rules, service caching and invalidation passed.')

let active = 0
let maximum = 0
const settled = await domain.mapSettledWithConcurrency(Array.from({ length: 13 }, (_, index) => index), 3, async index => {
  active++; maximum = Math.max(maximum, active)
  await new Promise(resolve => setTimeout(resolve, 1))
  active--
  if (index === 5) throw new Error('one item failed')
  return index
})
assert.equal(maximum, 3)
assert.equal(settled[5].status, 'rejected')
assert.equal(settled[12].value, 12, 'one failed row must not prevent remaining rows loading')

const defaults = domain.readDatasetListState({})
assert.deepEqual(domain.datasetListQuery(defaults), {})
const linkState = domain.readDatasetListState({ type: 'white', status: 'ok', pub: 'no', q: ' 武汉 ', catalog: 'roads', sort: 'size', order: 'asc', page: '3', size: '20' })
assert.equal(linkState.type, 'vector')
assert.equal(linkState.kw, '武汉')
assert.deepEqual(domain.readDatasetListState(domain.datasetListQuery(linkState)), linkState, 'reload and back navigation retain filters, ordering and pagination')
assert.equal(domain.readDatasetListState({page: 'Infinity', size: '999', sort: 'wrong', q: ['x'], type: 'unknown'}).page, 1)
assert.deepEqual(domain.datasetListQuery(domain.readDatasetListState({page: '-1', size: '999', sort: 'wrong', q: ['x'], type: 'unknown'})), {}, 'invalid URL values cannot create invalid queries')
