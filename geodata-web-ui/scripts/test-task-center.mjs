import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const temporary = await mkdtemp(join(tmpdir(), 'geodata-task-center-'))
const tick = () => new Promise((resolve) => setTimeout(resolve, 0))
function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
const requests = []
const storage = []
const confirmations = []
globalThis.__taskHarness = {
  getList: (...args) => {
    const request = { args, ...deferred() }
    requests.push(request)
    return request.promise
  },
  storageLayer: (...args) => {
    const request = { args, ...deferred() }
    storage.push(request)
    return request.promise
  },
  confirm: (message) => { confirmations.push(message); return Promise.resolve() },
}
const previousDocument = globalThis.document
const document = new EventTarget()
document.visibilityState = 'visible'
document.createElement = () => ({})
globalThis.document = document

try {
  const entry = join(temporary, 'task-center.mjs')
  const mocks = {
    '@/api': `const harness = globalThis.__taskHarness; export const layerToolApi = {getList: (...args) => harness.getList(...args), storageLayer: (...args) => harness.storageLayer(...args), delete: async () => true}; export const dataTypeApi = {getAllList: async () => [{id:'target-a',typeName:'成果目录'}]};`,
    '@/utils/im': `export const imEnabled = () => false; export const onImMessage = () => () => {};`,
    '@/auth/store': `export const useUserStore = () => ({userName:'admin'});`,
    '@/utils/feedback': `export const showError = (error) => { throw error };`,
    'element-plus': `export const ElMessage = {warning: () => {}, success: () => {}}; export const ElMessageBox = {confirm: (message) => globalThis.__taskHarness.confirm(message)};`,
  }
  await build({
    stdin: {
      contents: `import {createRenderer, h} from 'vue'; import {useTaskCenter} from ${JSON.stringify(resolve('src/features/tasks/useTaskCenter.ts'))}; export function mountTaskCenter(){let state; const renderer=createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),parentNode:()=>null,nextSibling:()=>null,insert:()=>{},remove:()=>{},setElementText:()=>{},setText:()=>{},setComment:()=>{},patchProp:()=>{}}); const app=renderer.createApp({setup(){state=useTaskCenter();return ()=>null}}); app.mount({}); return {state,unmount:()=>app.unmount()};}`,
      resolveDir: process.cwd(),
    },
    plugins: [{
      name: 'task-center-test-adapters', setup(build) {
        build.onResolve({ filter: /^(@\/|element-plus$)/ }, ({ path }) => path in mocks ? { path, namespace: 'test-adapter' } : { path: resolve('src', path.slice(2)) + '.ts' })
        build.onLoad({ filter: /.*/, namespace: 'test-adapter' }, ({ path }) => ({ contents: mocks[path], loader: 'js' }))
      },
    }],
    outfile: entry, bundle: true, platform: 'node', format: 'esm', logLevel: 'silent',
  })
  const { mountTaskCenter } = await import(pathToFileURL(entry).href)
  const app = mountTaskCenter()
  const { state } = app
  await tick()
  assert.equal(requests.length, 1)
  state.pickOrigin('7')
  requests[0].resolve({ records: [{ id: 'stale', status: '1' }], total: 1 })
  await tick()
  assert.equal(requests.length, 2)
  assert.equal(requests[1].args[2], '7')
  assert.equal(state.list.value.length, 0, 'a previous filter response cannot flash stale rows')
  requests[1].resolve({ records: [{ id: 'fresh', status: '1', origin: '7', storage: '0' }], total: 1 })
  await tick()
  assert.equal(state.list.value[0].id, 'fresh')

  await state.openStorage(state.list.value[0])
  assert.equal(state.storageTargets.value[0].label, '成果目录')
  state.storageTargetId.value = 'target-a'
  const firstStorage = state.confirmStorage()
  await state.confirmStorage()
  assert.equal(storage.length, 1, 'double-clicking storage submits once')
  storage[0].resolve('存储成功')
  await tick()
  requests.at(-1).resolve({ records: [{ id: 'fresh', status: '1', origin: '7', storage: '0' }], total: 1 })
  await firstStorage
  assert.equal(state.list.value[0].storage, '1', 'an older list response cannot reopen completed storage')
  await state.openStorage(state.list.value[0])
  assert.equal(state.storageVisible.value, false, 'stored results cannot be submitted again')

  state.page.pageIndex = 2
  state.page.total = 11
  state.list.value = [{ id: 'last-item', status: '0' }]
  const deletion = state.removeRow(state.list.value[0])
  await tick()
  assert.match(confirmations.at(-1), /不会取消后台处理/)
  assert.equal(state.page.pageIndex, 1, 'deleting the final item returns to the preceding page')
  assert.equal(requests.at(-1).args[1], 1)
  requests.at(-1).resolve({ records: [{ id: 'remaining', status: '1' }], total: 10 })
  await deletion
  assert.equal(state.list.value[0].id, 'remaining')

  const currentRefresh = state.refresh()
  await tick()
  const unmountedRequest = requests.at(-1)
  app.unmount()
  assert.equal(unmountedRequest.args[3].signal.aborted, true)
  unmountedRequest.resolve({ records: [{ id: 'after-unmount', status: '1' }], total: 1 })
  await currentRefresh
  assert.equal(state.list.value[0].id, 'remaining', 'unmounted pages ignore late responses')
  console.log('Task center integration tests passed: filter race, storage deduplication, directory labels, deletion/page recovery, unmount cancellation.')
} finally {
  globalThis.document = previousDocument
  delete globalThis.__taskHarness
  await rm(temporary, { recursive: true, force: true })
}
