import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { parse, compileScript } from '@vue/compiler-sfc'
import { build } from 'esbuild'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const row = id => ({ id, key: `layer:${id}`, src: 'layer', typeKey: 'raster', name: `影像${id}`, status: 'ok', statusLabel: '已入库', published: false, raw: { id, publishStatus: '0' }, time: '2026-10-22', progress: 100 })
const harness = { rows: [row('a'), row('b')], total: 2, calls: 0, confirmCalls: 0, removeCalls: 0, route: null, search: null, confirmGate: null,
  exports: [], copied: [], addressCalls: [], addressGate: null, failedAddress: '', sessionVersion: 0 }
harness.rows[0].typeKey = 'vector'
globalThis.__managePageTest = harness
globalThis.window = { setTimeout: () => 1, clearTimeout() {}, dispatchEvent() {} }
globalThis.localStorage = { getItem: () => null, setItem() {} }
const script = compileScript(parse(await readFile(resolve(root, 'src/views/manage/index.vue'), 'utf8')).descriptor, { id: 'manage-review' }).content
const mocks = {
  'vue-router': `const h=globalThis.__managePageTest;export const useRoute=()=>h.route;export const useRouter=()=>({replace:async({query})=>{h.route.query=query},push:async()=>{}})`,
  'element-plus': `const h=globalThis.__managePageTest;export const ElMessage={success(){},warning(){},error(){}};export const ElMessageBox={confirm:async()=>{h.confirmCalls++;if(h.confirmGate)await h.confirmGate.promise}}`,
  '@/api': `const h=globalThis.__managePageTest;const remove=async id=>{h.removeCalls++;h.rows=h.rows.filter(r=>r.id!==id);h.total=h.rows.length;return true};export const layerContentApi={getDetailById:async id=>({id,isShow:1}),removeLayer:remove,getAllCountByDataType:async()=>[]};export const dataTypeApi={getAllList:async()=>[]};export const dictApi={typeList:async()=>[]};export const model3dApi={remove};export const serverInterfaceApi={}`,
  '@/utils/datasets': `export * from ${JSON.stringify(resolve(root, 'src/features/datasets/model.ts'))};export * from ${JSON.stringify(resolve(root, 'src/features/datasets/ingestion.ts'))};const h=globalThis.__managePageTest;export const loadUnifiedPage=async()=>{h.calls++;return{rows:h.rows.map(r=>({...r})),total:h.total}};export const fetchSvcAddress=async()=>'';export const fetchAllSvcAddresses=async row=>{h.addressCalls.push(row.id);if(h.addressGate)await h.addressGate.promise;if(row.id===h.failedAddress)throw Error('unavailable');return[{type:'WMS',url:'https://example.test/'+row.id}]};export const getDatasetServiceState=async()=>({formats:[],url:'',tile:''});export const invalidateDatasets=()=>{};export const invalidateDatasetQueries=()=>{}`,
  '@/utils/gzip': `export const decompressJson=()=>[]`,
  '@/utils/im': `export const onImMessage=()=>()=>{};export const onImBizMessage=()=>()=>{};export const imEnabled=()=>false;export const ensureImAlive=()=>{}`,
  '@/utils/clipboard': `export const copyText=async text=>{globalThis.__managePageTest.copied.push(text);return true}`,
  '@/features/datasets/export': `export const downloadDatasetCsv=(rows,scope)=>{if(!rows.length)throw Error('empty');globalThis.__managePageTest.exports.push({rows:rows.map(row=>({...row})),scope})}`,
  '@/auth/session': `export const getSessionVersion=()=>globalThis.__managePageTest.sessionVersion`,
  '@/utils/feedback': `export const showError=()=>{}`,
  '@/stores/search': `export const useSearchStore=()=>globalThis.__managePageTest.search`,
  '@/auth/store': `export const useUserStore=()=>({userName:'admin'})`,
}
const result = await build({
  stdin: { contents: `import{createRenderer,reactive,nextTick}from'vue';import View from'test:manage';export{nextTick};export function mount(){const h=globalThis.__managePageTest;h.route=reactive({query:{q:'底图',pub:'no'}});h.search=reactive({sequence:0,submittedKeyword:''});const renderer=createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),parentNode:()=>null,nextSibling:()=>null,insert(){},remove(){},setElementText(){},setText(){},setComment(){},patchProp(){}});View.render=()=>null;const app=renderer.createApp(View);app.mount({});return{state:app._instance.setupState,unmount:()=>app.unmount()}}`, resolveDir: root },
  bundle: true, write: false, platform: 'node', format: 'cjs', logLevel: 'silent',
  plugins: [{ name: 'manage-test-adapters', setup(builder) {
    builder.onResolve({ filter: /^\//, namespace: 'mock' }, ({ path }) => ({ path, namespace: 'file' }))
    builder.onResolve({ filter: /^test:manage$/ }, () => ({ path: 'manage', namespace: 'view' }))
    builder.onLoad({ filter: /.*/, namespace: 'view' }, () => ({ contents: script, loader: 'ts', resolveDir: root }))
    builder.onResolve({ filter: /^(vue-router|element-plus|@\/)/ }, ({ path }) => path in mocks ? { path, namespace: 'mock' } : path.endsWith('.vue') ? { path, namespace: 'component' } : { path: resolve(root, 'src', path.slice(2)) + '.ts' })
    builder.onLoad({ filter: /.*/, namespace: 'mock' }, ({ path }) => ({ contents: mocks[path], loader: 'js' }))
    builder.onLoad({ filter: /.*/, namespace: 'component' }, () => ({ contents: 'export default {}', loader: 'js' }))
  } }],
})
const compiled = { exports: {} }
new Function('require', 'module', 'exports', result.outputFiles[0].text)(createRequire(import.meta.url), compiled, compiled.exports)
globalThis.document = { hidden: false }
const app = compiled.exports.mount()
const state = app.state
const tick = async () => { await compiled.exports.nextTick(); await new Promise(setImmediate); await compiled.exports.nextTick() }
function deferred() { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
try {
  await tick()
  assert.equal(state.rows[0].ingestReady, true, '详情已权威确认矢量入库')
  const selected = new Map()
  state.dataTable = {
    clearSelection() { selected.clear(); state.onSelectionChange([]) },
    toggleRowSelection(row, checked) {
      if (checked) selected.set(row.key, row)
      else selected.delete(row.key)
      state.onSelectionChange([...selected.values()])
    },
  }
  state.togglePicked(state.rows[0], true)
  assert.equal(selected.size, 1, '卡片选择同步表格')
  await state.loadList(true)
  assert.equal(state.picked.size, 1, '后台刷新保留选中项')
  assert.equal(state.rows[0].ingestReady, true, '列表 DTO 不得让已经核验的入库状态循环回退')
  assert.equal(selected.size, 1)
  state.filters.kw = '未提交草稿'
  state.filters.status = 'fail'
  state.removeFilter('pub')
  await tick()
  assert.equal(state.applied.kw, '底图', '移除标签不提交其他草稿')
  assert.equal(state.applied.status, '')
  assert.equal(harness.route.query.pub, undefined)
  harness.total = 100
  let before = harness.calls
  state.pageSize = 20
  state.pageIndex = 1
  state.onPaginationChange()
  state.onPaginationChange()
  await tick()
  assert.equal(harness.calls, before + 1, '分页同一状态只请求一次')
  state.clearSelection()
  state.exportResources('page')
  assert.equal(harness.exports.at(-1).rows.length, 2, '当前页导出覆盖当前可见列表')
  state.togglePicked(state.rows[0], true)
  state.exportResources('selected')
  assert.equal(harness.exports.at(-1).scope, 'selected')
  assert.deepEqual(harness.exports.at(-1).rows.map(row => row.id), ['a'], '已选导出不混入未选择资源')
  before = harness.exports.length
  state.loading = true
  state.exportResources('page')
  state.loading = false
  state.listError = '上次结果已过期'
  state.exportResources('selected')
  await state.copyPublishedServices()
  assert.equal(harness.addressCalls.length, 0, '错误页不能使用后台残留结果复制')
  state.listError = ''
  state.batchDeleting = true
  state.exportResources('selected')
  state.batchDeleting = false
  assert.equal(harness.exports.length, before, '加载/错误/批删期间不导出旧页')
  state.rows.forEach(row => { row.published = true })
  harness.addressGate = deferred()
  const copying = state.copyPublishedServices()
  await state.copyPublishedServices()
  assert.deepEqual(harness.addressCalls, ['a'], '有选择时只读取已选范围且重复点击不重复请求')
  state.rows[0].name = '读取中被更改的名称'
  state.clearSelection()
  harness.addressGate.resolve()
  await copying
  assert.equal(harness.copied.length, 1)
  assert.ok(harness.copied[0].startsWith('影像a\t'), '异步服务复制保留点击时的资源快照')
  assert.ok(!harness.copied[0].includes('/b'), '读取期间取消选择不能扩大复制范围')
  harness.addressGate = null
  harness.failedAddress = 'b'
  await state.copyPublishedServices()
  assert.ok(harness.copied.at(-1).includes('/a') && !harness.copied.at(-1).includes('/b'), '部分失败仍能复制已读到的地址')
  harness.failedAddress = ''
  harness.addressGate = deferred()
  before = harness.copied.length
  const previousSessionCopy = state.copyPublishedServices()
  harness.sessionVersion++
  harness.addressGate.resolve()
  await previousSessionCopy
  assert.equal(harness.copied.length, before, '会话变化后不把旧账号结果写入剪贴板')
  harness.addressGate = null
  harness.confirmGate = deferred()
  const target = state.rows[0]
  const deleting = state.removeRow(target)
  await state.removeRow(target)
  assert.equal(harness.confirmCalls, 1, '单行双击只确认一次')
  assert.equal(state.rowBusy[target.key], true)
  harness.confirmGate.resolve()
  await deleting
  await tick()
  assert.equal(harness.removeCalls, 1)
  assert.equal(state.rowBusy[target.key], false)
  state.togglePicked(state.rows[0], true)
  harness.confirmGate = deferred()
  const batching = state.batchDel()
  before = harness.calls
  await state.loadList(true)
  assert.equal(harness.calls, before, '批量确认期间后台刷新不清目标')
  await state.removeRow(state.rows[0])
  assert.equal(harness.confirmCalls, 2, '批删锁阻止单行重复确认')
  harness.confirmGate.resolve()
  await batching
  assert.equal(harness.removeCalls, 2)
  assert.equal(state.batchDeleting, false)
  console.log('管理页测试通过：跨视图选择同步、轮询保留选择、筛选草稿隔离、分页去重、单行/批删互斥与锁释放')
} finally {
  app.unmount()
  delete globalThis.__managePageTest
}
