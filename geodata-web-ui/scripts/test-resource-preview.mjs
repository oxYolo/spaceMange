import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { build } from 'esbuild'
import { parse, compileScript } from '@vue/compiler-sfc'

const root = fileURLToPath(new URL('..', import.meta.url))
let moduleId = 0
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done }); return { promise, resolve } }
function environment() {
  const mounted = [], unmounted = [], events = [], successes = [], errors = [], infos = []
  const ref = (value) => ({ value })
  return {
    mounted, unmounted, events, successes, errors, infos,
    vue: {
      ref, shallowRef: ref, reactive: (value) => value,
      computed: (source) => ({ get value() { return typeof source === 'function' ? source() : source.get() }, set value(value) { source.set?.(value) } }),
      watch: (source, callback, options) => { if (options?.immediate) callback(source()) },
      onMounted: (callback) => mounted.push(callback), onBeforeUnmount: (callback) => unmounted.push(callback),
      nextTick: (callback) => Promise.resolve().then(callback), defineComponent: (value) => value,
    },
    message: { success: (value) => successes.push(value), error: (value) => errors.push(value), info: (value) => infos.push(value), warning: (value) => infos.push(value) },
    api: {}, config: { cesiumCenter: [114, 30, 2000], getTdtToken: () => '' },
  }
}
async function component(path, env, props) {
  globalThis.__previewRegression = env
  const source = await readFile(resolve(root, path), 'utf8')
  const compiled = compileScript(parse(source).descriptor, { id: `regression-${moduleId}` }).content
  const virtual = {
    vue: `const api=globalThis.__previewRegression.vue; export const {ref,shallowRef,reactive,computed,watch,onMounted,onBeforeUnmount,nextTick,defineComponent}=api;`,
    'element-plus': `const env=globalThis.__previewRegression; export const ElMessage=env.message; export const ElMessageBox={};`,
    '@/utils/feedback': `export const showError=(error)=>globalThis.__previewRegression.errors.push(error);`,
    '@/utils/cesium': `export const loadCesium=()=>globalThis.__previewRegression.loadCesium();`,
    '@/config': `export default globalThis.__previewRegression.config;`,
    '@/api': `const api=globalThis.__previewRegression.api; export const {dataStyleApi,layerContentApi,layerFieldApi,layerLogApi,layerToolApi,mapRelationApi,metadataApi,serverInterfaceApi}=api;`,
    '@/auth/store': `export const useUserStore=()=>({userName:'admin'});`,
    '@/utils/clipboard': `export const copyText=async()=>true;`,
    '@/utils/datasets': `export {isRasterDataType,normalizeLayer,SERVICE_PROTOCOLS} from ${JSON.stringify(resolve(root, 'src/features/datasets/model.ts'))}; export {layerIngestionReady} from ${JSON.stringify(resolve(root, 'src/features/datasets/ingestion.ts'))}; export const getDatasetServiceState=async()=>({addresses:[],error:''}); export const invalidateDatasets=()=>{};`,
  }
  const result = await build({ stdin: { contents: compiled, loader: 'ts', resolveDir: root }, bundle: true, write: false, format: 'esm', platform: 'node', plugins: [{
    name: 'component-regression-environment', setup(build) {
      build.onResolve({ filter: /.*/ }, (args) => {
        if (virtual[args.path]) return { path: args.path, namespace: 'virtual' }
        if (args.path.endsWith('.vue')) return { path: args.path, namespace: 'empty-component' }
        if (args.path.startsWith('@/')) return { path: resolve(root, 'src', args.path.slice(2) + '.ts') }
      })
      build.onLoad({ filter: /.*/, namespace: 'virtual' }, (args) => ({ contents: virtual[args.path], loader: 'ts', resolveDir: root }))
      build.onLoad({ filter: /.*/, namespace: 'empty-component' }, () => ({ contents: 'export default {}' }))
    },
  }] })
  const imported = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text + `\n// module ${moduleId++}`).toString('base64')}`)
  return imported.default.setup(props, { expose() {}, emit: (...args) => env.events.push(args) })
}

globalThis.window = {}
globalThis.document = { createElement: () => ({ getContext: () => ({}) }) }
const library = deferred(), closed = environment()
let viewersCreated = 0
closed.loadCesium = () => library.promise
const closedMap = await component('src/components/map/Map3D.vue', closed, { url: '', center: [114, 30, 2000], imagery: false })
closedMap.wrapRef.value = {}
const initialization = closed.mounted[0]()
closed.unmounted[0]()
library.resolve({ Viewer: function () { viewersCreated++ } })
await initialization
assert.equal(viewersCreated, 0, 'closing during library loading must not create a detached viewer')

const tilesA = deferred(), switching = environment(), added = [], removed = []
const primitive = (id) => ({ id, destroyed: false, isDestroyed() { return this.destroyed }, destroy() { this.destroyed = true } })
const modelA = primitive('A'), modelB = primitive('B')
class Viewer {
  destroyed = false
  scene = { primitives: { add: (item) => { added.push(item.id); return item }, remove: (item) => { removed.push(item.id); item.destroy(); return true } } }
  camera = { setView() {}, flyTo() {} }
  isDestroyed() { return this.destroyed }
  destroy() { this.destroyed = true }
  async zoomTo() { return true }
}
const Cesium = { Viewer, Cartesian3: { fromDegrees: (...value) => value }, Cesium3DTileset: { fromUrl: (url) => url === 'A' ? tilesA.promise : Promise.resolve(modelB) } }
globalThis.window.Cesium = Cesium
switching.loadCesium = async () => Cesium
const map = await component('src/components/map/Map3D.vue', switching, { url: '', center: [114, 30, 2000], imagery: false })
map.wrapRef.value = {}
await switching.mounted[0]()
const loadingA = map.loadTileset('A')
await map.loadTileset('B')
tilesA.resolve(modelA)
await loadingA
assert.deepEqual(added, ['B'], 'an older URL must never be attached after the selected URL')
assert.equal(modelA.destroyed, true, 'an obsolete asynchronously created tileset must be released')
assert.deepEqual(switching.events.filter(([name]) => name === 'loaded').map(([, value]) => value.id), ['B'])
switching.unmounted[0]()
assert.deepEqual(removed, ['B'])

const detailEnv = environment(), fieldsA = deferred(), fieldsB = deferred(), saving = deferred()
let writes = 0, publishes = 0, queriedPage = 0
Object.assign(detailEnv.api, {
  layerContentApi: {
    getFieldByLayerId: (id) => id === 'A' ? fieldsA.promise : fieldsB.promise,
    updateAttrtable: () => { writes++; return saving.promise },
    getLayerAttrTableByWhere: async (_id, _where, _size, page) => { queriedPage = page; return { items: [{ gid: 2, name: 'filtered' }], totalCount: 12 } },
  },
  serverInterfaceApi: { publishService: async () => { publishes++; return true } },
})
const props = { modelValue: false, layer: { id: 'A', dataType: '1' }, initialTab: 'overview' }
const detail = await component('src/components/resource/ResourceDetail.vue', detailEnv, props)
props.modelValue = true
const firstFields = detail.loadFields()
props.layer = { id: 'B', dataType: '1' }
const secondFields = detail.loadFields()
fieldsB.resolve([{ attributeName: 'new-resource' }]); await secondFields
fieldsA.resolve([{ attributeName: 'old-resource' }]); await firstFields
assert.equal(detail.fields.value[0].attributeName, 'new-resource', 'a delayed detail tab must not overwrite the selected resource')
detail.detail.value = { id: 'B', uploadStatus: '1', isShow: '0' }
await detail.doPublish()
assert.equal(publishes, 0, 'an uploaded file must not authorize publishing before ingestion is confirmed')
assert.ok(detailEnv.infos.some((text) => text.includes('入库待确认')))
detail.detail.value.isShow = '1'
assert.equal(detail.isSystemField({ attributeName: 'GID' }), true)
assert.equal(detail.isSystemField({ attributeName: 'the_geom' }), true)
assert.equal(detail.isSystemField({ attributeName: 'id' }), false, 'ordinary business id columns must remain deletable')
assert.equal(detail.canDeleteField({ id: 'definition-id', attributeName: 'id' }), true)
assert.equal(detail.canDeleteField({ id: 'definition-gid', attributeName: 'gid' }), false)
assert.equal(detail.canDeleteField({ attributeName: 'name' }), false, 'missing definition ids must not offer a delete action')
detail.fields.value = [{ id: 'definition-gid', attributeName: 'gid' }]
assert.equal(detail.canClearFields(), false)
await detail.removeField(detail.fields.value[0])
await detail.removeAllFields()
assert.ok(detailEnv.infos.some((text) => text.includes('系统字段')))
detail.editForm.value = { gid: 1, name: 'updated' }
const firstSave = detail.saveEditRecord(), duplicateSave = detail.saveEditRecord()
assert.equal(writes, 1, 'a repeated click must not submit a second mutation')
saving.resolve(false)
await Promise.all([firstSave, duplicateSave])
assert.equal(detailEnv.successes.length, 0, 'a false backend result must not show success')
assert.equal(detail.editSaving.value, false)
detail.lastAttrQuery.value = { where: [{ field: 'name', operator: '=', value: 'filtered' }] }
detail.attrPage.index = 2
await detail.loadAttrs()
assert.equal(queriedPage, 2, 'filtered pagination must keep its predicate')
assert.equal(detail.attrs.value[0].name, 'filtered')
assert.ok(detail.lastAttrQuery.value.where)

const pure = await build({ stdin: { contents: `export * from './src/utils/spatial.ts'; export * from './src/utils/requestScope.ts'; export {default as WKT} from 'ol/format/WKT';`, resolveDir: root }, bundle: true, write: false, platform: 'node', format: 'esm' })
const helpers = await import(`data:text/javascript;base64,${Buffer.from(pure.outputFiles[0].text).toString('base64')}`)
const polygon = { type: 'Polygon', coordinates: [[[0,0],[2,0],[2,2],[0,0]]] }
const wkt = helpers.geometryJsonToWkt(polygon)
assert.match(wkt, /^POLYGON\(\(/)
assert.ok(!wkt.startsWith('POLYGON((('), 'drawn polygons must not gain an extra parenthesis')
assert.equal(new helpers.WKT().readGeometry(wkt).getType(), 'Polygon')
const scope = helpers.createRequestScope(), old = scope.begin('row'), latest = scope.begin('row')
assert.equal(old.current(), false)
assert.equal(latest.current(), true)
scope.dispose(); assert.equal(latest.current(), false)
console.log('Resource preview: close/switch races, mutation deduplication, ingestion guard, filtered pagination and polygon geometry passed.')
