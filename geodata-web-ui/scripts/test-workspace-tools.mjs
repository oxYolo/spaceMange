import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { fileURLToPath } from 'node:url'
const bundled = await build({ stdin: { contents: `export * from './src/features/datasets/listState.ts';export * from './src/features/datasets/export.ts';export * from './src/features/datasets/model.ts';export * from './src/utils/clipboard.ts'`, resolveDir: fileURLToPath(new URL('..', import.meta.url)) }, bundle: true, write: false, format: 'esm', platform: 'node' })
const tools = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`)
const filters = tools.readDatasetListState({ type: 'vector', pub: 'no', q: ' 武汉 ', sort: 'name', order: 'asc', page: '9', size: '20' })
assert.equal(filters.kw, '武汉')
assert.equal(filters.page, 9)
assert.equal(filters.size, 20)
const row = tools.normalizeLayer({id:'id1',layerName:'=HYPERLINK("x")',dataType:'1',uploadStatus:'1',fileSize:2,createBy:'测试',layerDesc:'unused'})
const csv = tools.datasetCsv([row])
assert.ok(csv.startsWith('\uFEFF'))
assert.ok(csv.includes('"\'=HYPERLINK(""x"")"'), 'spreadsheet formula cells are escaped')
assert.equal(tools.csvCell('a,"b"\nc'), '"a,""b""\nc"')
assert.equal(tools.csvCell('  @formula'), '"\'  @formula"')
assert.equal(tools.csvCell('\uFEFF=SUM(1)'), '"\'\uFEFF=SUM(1)"', 'unicode whitespace must not bypass formula escaping')
assert.equal(tools.csvCell('中文'), '"中文"')
assert.equal(tools.datasetExportFileName('page', new Date(2026, 9, 22, 0, 15)), '空间数据_当前页_2026-10-22.csv', 'filename uses the local date even before 08:00')
assert.equal(tools.datasetExportFileName('selected', new Date(2026, 0, 2)), '空间数据_已选_2026-01-02.csv')

let removed = 0
Object.defineProperty(globalThis, 'navigator', {configurable:true,value:{}})
globalThis.window = {isSecureContext:false}
globalThis.HTMLElement = class {}
globalThis.document = {createElement:()=>({style:{},focus(){},select(){},setSelectionRange(){},remove(){removed++}}),body:{appendChild(){}},execCommand:()=>false}
assert.equal(await tools.copyText('test'), false, 'clipboard fallback false must remain failure')
assert.equal(removed, 1)
globalThis.document.execCommand = () => {throw Error('denied')}
assert.equal(await tools.copyText('test'), false)
assert.equal(removed, 2, 'clipboard temporary DOM is cleaned on exception')
console.log('Workspace tools: list query parsing, CSV escaping and clipboard failure reporting passed.')
