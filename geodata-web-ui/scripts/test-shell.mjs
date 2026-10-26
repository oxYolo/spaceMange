import assert from 'node:assert/strict'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { build } from 'esbuild'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const bundle = await build({
  stdin: {
    contents: `export * from './src/features/shell/resourceCount.ts'; export * from './src/features/shell/keyboard.ts'; export * from './src/features/shell/commands.ts'; export {NAV_MAIN,NAV_ADVANCED} from './src/router/nav.ts'`,
    resolveDir: root,
  },
  bundle: true, platform: 'node', format: 'cjs', write: false,
  alias: { '@': join(root, 'src') },
})
const compiledModule = { exports: {} }
new Function('module', 'exports', bundle.outputFiles[0].text)(compiledModule, compiledModule.exports)
const { createResourceCounter, readResourceCount, readPageCount, isGlobalSearchShortcut } = compiledModule.exports
const { createShellCommands, searchShellCommands, isCommandShortcut, paletteKeyAction, moveCommandSelection, commandShortcutLabel, NAV_MAIN, NAV_ADVANCED } = compiledModule.exports

assert.equal(readResourceCount(0), 0)
assert.equal(readResourceCount('12'), 12)
assert.equal(readPageCount({ totalCount: '7' }), 7)
for (const invalid of [undefined, null, '', false, -1, 1.5, Infinity, 'unknown', Number.MAX_SAFE_INTEGER + 1]) {
  assert.throws(() => readResourceCount(invalid), `不得把 ${String(invalid)} 伪装为0`)
}
assert.throws(() => readPageCount({ items: [] }), '没有 total 的响应不能认定0')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const flush = () => new Promise((resolve) => setImmediate(resolve))
const states = []
let layers = deferred()
let models = deferred()
let layerCalls = 0
let modelCalls = 0
const counter = createResourceCounter({
  countLayers: () => { layerCalls++; return layers.promise },
  countModels: () => { modelCalls++; return models.promise },
}, (state) => states.push(state))
const initial = counter.refresh('admin')
await flush()
assert.equal(layerCalls, 1)
assert.equal(modelCalls, 1, '二维未返回前已发起三维统计，两请求并行')
layers.resolve(8)
models.resolve({ total: 2 })
await initial
assert.deepEqual(states.at(-1), { value: 10, loading: false, unavailable: false })

layers = deferred()
models = deferred()
const partial = counter.refresh('admin')
layers.resolve(7)
models.reject(new Error('三维服务不可用'))
await partial
assert.deepEqual(states.at(-1), { value: 10, loading: false, unavailable: true }, '部分失败保留上次完整统计，不显示7或0')

layers = deferred()
models = deferred()
const oldLayers = layers
const oldModels = models
const older = counter.refresh('admin')
await flush()
layers = deferred()
models = deferred()
const newer = counter.refresh('admin')
await flush()
layers.resolve(20)
models.resolve({ totalCount: 4 })
await newer
oldLayers.resolve(1)
oldModels.resolve({ total: 1 })
await older
assert.equal(states.at(-1).value, 24, '旧统计不能覆盖新结果')

layers = deferred()
models = deferred()
const otherUser = counter.refresh('operator')
assert.equal(states.at(-1).value, null, '用户切换不能沿用上一用户的统计')
layers.reject(new Error('服务不可用'))
models.resolve({ total: 0 })
await otherUser
assert.deepEqual(states.at(-1), { value: null, loading: false, unavailable: true })

layers = deferred()
models = deferred()
const beforeDispose = counter.refresh('operator')
await flush()
counter.dispose()
const eventsBeforeDispose = states.length
layers.resolve(1)
models.resolve({ total: 2 })
await beforeDispose
await counter.refresh('operator')
assert.equal(states.length, eventsBeforeDispose, '卸载后不提交响应或发起新统计')

const slash = { key: '/', altKey: false, ctrlKey: false, metaKey: false, shiftKey: false, isComposing: false, defaultPrevented: false }
assert.equal(isGlobalSearchShortcut(slash, false, false), true)
for (const key of ['altKey', 'ctrlKey', 'metaKey', 'shiftKey', 'isComposing', 'defaultPrevented']) {
  assert.equal(isGlobalSearchShortcut({ ...slash, [key]: true }, false, false), false)
}
assert.equal(isGlobalSearchShortcut(slash, true, false), false, '编辑文本时可输入斜杠')
assert.equal(isGlobalSearchShortcut(slash, false, true), false, '弹层中不抢焦点')
assert.equal(isGlobalSearchShortcut({ ...slash, key: '?' }, false, false), false)

const commands = createShellCommands()
assert.equal(new Set(commands.map(command => command.id)).size, commands.length, '命令 ID 唯一')
for (const navigation of [...NAV_MAIN, ...NAV_ADVANCED.flatMap(group => group.children)]) {
  assert.equal(commands.find(command => command.id === `page:${navigation.id}`)?.to.path, navigation.route, '命令复用现有导航配置')
}
assert.equal(commands.length, NAV_MAIN.length + NAV_ADVANCED.flatMap(group => group.children).length + 2)
assert.deepEqual(searchShellCommands(commands, '').map(command => command.id), ['page:manage', 'upload:vector', 'upload:raster', 'page:tasks', 'page:maps', 'page:layers'])
assert.equal(searchShellCommands(commands, '任务中心')[0].id, 'page:tasks')
assert.equal(searchShellCommands(commands, 'ＳＨＰ')[0].id, 'upload:vector', '英文别名兼容大小写与全角输入')
assert.equal(searchShellCommands(commands, 'raster 上传')[0].id, 'upload:raster', '多词搜索同时命中')
assert.deepEqual(searchShellCommands(commands, '样式与符号').map(command => command.id), ['page:style', 'page:symbol'])
assert.equal(searchShellCommands(commands, '/system/log')[0].id, 'page:log')
assert.deepEqual(searchShellCommands(commands, '不存在的入口'), [])
assert.ok(commands.every(command => command.to.path.startsWith('/') && !command.id.includes('remove') && !command.id.includes('logout')), '命令仅包含页面和上传入口')
assert.deepEqual(commands.find(command => command.id === 'upload:raster').to, { path: '/manage', query: { upload: 'raster' } })

const ctrlK = { key: 'k', ctrlKey: true }
assert.equal(isCommandShortcut(ctrlK), true)
assert.equal(isCommandShortcut({ key: 'K', metaKey: true }), true)
for (const field of ['altKey', 'shiftKey', 'isComposing', 'repeat', 'defaultPrevented']) assert.equal(isCommandShortcut({ ...ctrlK, [field]: true }), false)
assert.equal(isCommandShortcut({ ...ctrlK, keyCode: 229 }), false)
assert.equal(isCommandShortcut(ctrlK, true), false, '已有弹层时不抢快捷键')
assert.equal(isCommandShortcut({ key: 'k' }), false)
assert.equal(paletteKeyAction({ key: 'ArrowDown' }), 'next')
assert.equal(paletteKeyAction({ key: 'ArrowUp' }), 'previous')
assert.equal(paletteKeyAction({ key: 'Enter' }), 'execute')
assert.equal(paletteKeyAction({ key: 'Escape' }), 'close')
assert.equal(paletteKeyAction(ctrlK), 'close')
assert.equal(paletteKeyAction({ key: 'Enter', isComposing: true }), null, '输入法确认不能执行命令')
assert.equal(paletteKeyAction({ key: 'Escape', keyCode: 229 }), null)
assert.equal(paletteKeyAction({ key: 'ArrowDown', ctrlKey: true }), null)
assert.equal(moveCommandSelection(5, 1, 6), 0)
assert.equal(moveCommandSelection(0, -1, 6), 5)
assert.equal(moveCommandSelection(-1, 1, 6), 0)
assert.equal(moveCommandSelection(3, -1, 0), -1, '空结果可安全使用方向键')
assert.equal(commandShortcutLabel('MacIntel'), '⌘ K')
assert.equal(commandShortcutLabel('Win32'), 'Ctrl K')
console.log('壳层测试通过：资源统计与竞态隔离、命令导航/搜索/快捷键/输入法保护、键盘选项循环')
