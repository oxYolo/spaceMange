import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { parse, compileScript } from '@vue/compiler-sfc'
import { build } from 'esbuild'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const row = { id: 'raster-a', name: '底图', published: false, raw: {} }
const harness = { calls: [], errors: [], route: null, load: async () => ({ rows: [row], total: 25 }) }
globalThis.__rasterPageTest = harness
const source = await readFile(resolve(root, 'src/views/raster/index.vue'), 'utf8')
const script = compileScript(parse(source).descriptor, { id: 'raster-integration' }).content
const mocks = {
  'vue-router': `const h=globalThis.__rasterPageTest;export const useRoute=()=>h.route;export const useRouter=()=>({replace:async({query})=>{h.route.query=query},push:async()=>{}})`,
  'element-plus': `export const ElMessage={warning(){},success(){},error(){}}; export const ElMessageBox={confirm:async()=>{}}`,
  '@/api': `export const layerContentApi={getDetailById:async()=>null};export const serverInterfaceApi={}`,
  '@/utils/datasets': `const h=globalThis.__rasterPageTest;export const normalizeLayer=x=>x;export const loadUnifiedPage=(...args)=>{h.calls.push(args);return h.load(...args)};export const getDatasetServiceState=async()=>({progress:{status:'IDLE',percent:0},wms:'',wmts:''});export const getDatasetCapabilities=()=>({canDelete:true,canSlice:true});export const invalidateDatasets=()=>{}`,
  '@/composables/usePolling': `export const usePolling=()=>{}`,
  '@/utils/async': `export const mapSettledWithConcurrency=(rows,n,fn)=>Promise.allSettled(rows.map(fn))`,
  '@/utils/feedback': `export const showError=error=>globalThis.__rasterPageTest.errors.push(error)`,
  '@/utils/clipboard': `export const copyText=async()=>true`,
}
const result = await build({
  stdin: {
    contents: `import {createRenderer,reactive,nextTick} from 'vue';import Raster from 'test:raster';export {nextTick};export function mount(query){globalThis.__rasterPageTest.route=reactive({query});const renderer=createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),parentNode:()=>null,nextSibling:()=>null,insert(){},remove(){},setElementText(){},setText(){},setComment(){},patchProp(){}});Raster.render=()=>null;const app=renderer.createApp(Raster);app.mount({});return {state:app._instance.setupState,unmount:()=>app.unmount()}}`,
    resolveDir: root,
  },
  bundle: true, write: false, platform: 'node', format: 'cjs', logLevel: 'silent',
  plugins: [{ name: 'raster-test-adapters', setup(builder) {
    builder.onResolve({ filter: /^test:raster$/ }, () => ({ path: 'raster', namespace: 'raster' }))
    builder.onLoad({ filter: /.*/, namespace: 'raster' }, () => ({ contents: script, loader: 'ts', resolveDir: root }))
    builder.onResolve({ filter: /^(vue-router|element-plus|@\/)/ }, ({ path }) => path in mocks
      ? { path, namespace: 'mock' }
      : path.endsWith('.vue') ? { path, namespace: 'component' } : { path: resolve(root, 'src', path.slice(2)) + '.ts' })
    builder.onLoad({ filter: /.*/, namespace: 'mock' }, ({ path }) => ({ contents: mocks[path], loader: 'js' }))
    builder.onLoad({ filter: /.*/, namespace: 'component' }, () => ({ contents: 'export default {}', loader: 'js' }))
  } }],
})
const compiled = { exports: {} }
new Function('require', 'module', 'exports', result.outputFiles[0].text)(createRequire(import.meta.url), compiled, compiled.exports)
const app = compiled.exports.mount({ q: '底图', pub: 'no', page: '2', size: '10' })
const tick = async () => { await compiled.exports.nextTick(); await new Promise(setImmediate); await compiled.exports.nextTick() }
try {
  await tick()
  assert.equal(app.state.keyword, '底图')
  assert.equal(app.state.published, '0')
  assert.equal(app.state.pageIndex, 2)
  assert.equal(harness.calls.length, 1, 'URL 恢复只加载一次')
  let before = harness.calls.length
  app.state.pageSize = 20
  app.state.pageIndex = 1
  await tick()
  assert.equal(harness.calls.length, before + 1, '页大小/页码同批变更只加载一次')
  assert.equal(harness.route.query.size, '20')
  app.state.keyword = '新影像'
  app.state.published = '1'
  app.state.search()
  await tick()
  assert.equal(harness.route.query.q, '新影像')
  assert.equal(harness.route.query.pub, 'yes')
  assert.equal(harness.route.query.page, undefined)
  harness.route.query = { q: '历史查询', pub: 'no', page: '2', size: '10' }
  await tick()
  assert.equal(app.state.keyword, '历史查询')
  assert.equal(app.state.pageIndex, 2)
  assert.equal(app.state.applied.published, '0')
  harness.load = async () => ({ rows: [], total: 4 })
  before = harness.calls.length
  app.state.pageIndex = 5
  await tick()
  await tick()
  assert.equal(app.state.pageIndex, 1, '越界页码自动回退')
  assert.equal(harness.calls.length, before + 2, '越界仅追加一次正确页加载')
  harness.load = async () => { throw new Error('服务中断') }
  await app.state.refreshRows()
  assert.equal(app.state.emptyTitle, '栅格数据加载失败', '故障不伪装为空数据')
  harness.load = async () => ({ rows: [], total: 0 })
  await app.state.refreshRows()
  assert.equal(app.state.emptyTitle, '没有符合条件的栅格数据')
  app.state.reset()
  await tick()
  assert.equal(app.state.emptyTitle, '暂无栅格数据')
  assert.deepEqual(harness.route.query, {}, '清除后不残留筛选或分页参数')
  console.log('栅格页面测试通过：URL 恢复/后退、分页单次请求/越界回退、错误与筛选空态、清除状态')
} finally {
  app.unmount()
  delete globalThis.__rasterPageTest
}
