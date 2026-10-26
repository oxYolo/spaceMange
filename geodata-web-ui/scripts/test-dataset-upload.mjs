import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const temporary = await mkdtemp(join(tmpdir(), 'geodata-dataset-upload-'))
function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
const harness = {
  chunks: [], inserts: [], statusUpdates: [], warnings: [], successes: [], errors: [], uploaded: 0, threeDimensionalCalls: 0,
  gate: null, insertGate: null, modelGate: null, failNextChunk: false, original: null,
  async chunkedUpload(file, onProgress) {
    this.chunks.push(file)
    if (this.failNextChunk) { this.failNextChunk = false; throw new Error('上传连接中断') }
    if (this.gate) {
      const pending = this.gate
      this.gate = null
      await pending.promise
    }
    onProgress(100, '文件已上传')
    return { logicName: file.name, physicalName: `/srv/upload/${file.name}` }
  },
  async insert(body, query) {
    this.inserts.push({ body, query })
    if (this.insertGate) { const pending = this.insertGate; this.insertGate = null; await pending.promise }
    return { id: `created-${this.inserts.length}` }
  },
  async modelUpload(...args) {
    this.threeDimensionalCalls++
    args.at(-1)(100, '正在完成上传…')
    if (this.modelGate) { const pending = this.modelGate; this.modelGate = null; await pending.promise }
    return 'model-a'
  },
}
globalThis.__datasetUploadHarness = harness

try {
  const entry = join(temporary, 'dataset-upload.mjs')
  const mocks = {
    '@/api': `const harness=globalThis.__datasetUploadHarness; export const dataTypeApi={getAllList:async()=>[{id:'catalog-a',typeName:'基础目录'}]}; export const dictApi={codeAll:async()=>[]}; export const regionApi={getAllList:async()=>[]}; export const model3dApi={get:async()=>harness.original}; export const layerContentApi={insert:(...args)=>harness.insert(...args),updateStatus:async(id)=>{harness.statusUpdates.push(id)},insertImageLayer:async()=>{throw new Error('Unexpected map creation')}};`,
    '@/utils/datasets': `export {DATA_TYPES,extFormat,isRasterTypeKey} from ${JSON.stringify(resolve('src/features/datasets/model.ts'))};`,
    '@/utils/chunkUpload': `const harness=globalThis.__datasetUploadHarness; export const chunkedUpload=(...args)=>harness.chunkedUpload(...args); export const m3dUpload=(...args)=>harness.modelUpload(...args); export const m3dResume=(...args)=>harness.modelUpload(...args);`,
    '@/utils/feedback': `export const showError=(error)=>{globalThis.__datasetUploadHarness.errors.push(error)};`,
    'element-plus': `const harness=globalThis.__datasetUploadHarness; export const ElMessage={warning:(message)=>{harness.warnings.push(message)},success:(message)=>{harness.successes.push(message)}};`,
  }
  await build({
    stdin: {
      contents: `import {createRenderer,nextTick} from 'vue'; import {useDatasetUpload} from ${JSON.stringify(resolve('src/features/datasets/composables/useDatasetUpload.ts'))}; export {nextTick}; export function mountDatasetUpload(){let state; const renderer=createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),parentNode:()=>null,nextSibling:()=>null,insert:()=>{},remove:()=>{},setElementText:()=>{},setText:()=>{},setComment:()=>{},patchProp:()=>{}}); const app=renderer.createApp({setup(){state=useDatasetUpload({getUserName:()=> 'admin',onUploaded:()=>{globalThis.__datasetUploadHarness.uploaded++}});return ()=>null}}); app.mount({});return {state,unmount:()=>app.unmount()};}`,
      resolveDir: process.cwd(),
    },
    plugins: [{
      name: 'dataset-upload-test-adapters', setup(build) {
        build.onResolve({ filter: /^\//, namespace: 'test-adapter' }, ({ path }) => ({ path, namespace: 'file' }))
        build.onResolve({ filter: /^(@\/|element-plus$)/ }, ({ path }) => path in mocks ? { path, namespace: 'test-adapter' } : { path: resolve('src', path.slice(2)) + '.ts' })
        build.onLoad({ filter: /.*/, namespace: 'test-adapter' }, ({ path }) => ({ contents: mocks[path], loader: 'js' }))
      },
    }],
    outfile: entry, bundle: true, platform: 'node', format: 'esm', logLevel: 'silent',
  })
  const { mountDatasetUpload, nextTick } = await import(pathToFileURL(entry).href)
  const app = mountDatasetUpload()
  const { state } = app
  const file = (name, size) => new File([new Uint8Array(size)], name)
  function pick(files) {
    const input = { files, value: 'selected-file' }
    state.onPickFile({ target: input })
    assert.equal(input.value, '', 'the same file can be selected again after a validation failure')
  }

  state.openUpload('oblique')
  await nextTick()
  state.upload.srcMode = 'resume'
  state.upload.resumeId = 'existing-3d-task'
  pick([file('previous.zip', 1024)])
  state.upload.typeKey = 'raster'
  await nextTick()
  assert.equal(state.upload.srcMode, 'local', 'switching from a 3D resume to raster resets the upload protocol')
  assert.equal(state.upload.resumeId, '', 'the previous 3D task cannot receive a raster upload')
  assert.equal(state.upload.file, null, 'switching types removes the incompatible old file')
  assert.deepEqual(state.upload.familyFiles, [])
  assert.equal(harness.threeDimensionalCalls, 0)

  const multipleBefore = harness.chunks.length
  pick([file('first.tif', 1024), file('second.tif', 2048)])
  assert.equal(state.selectedFiles.value.length, 2, 'a dropped raster group keeps every file visible')
  await state.submitUpload()
  assert.equal(harness.chunks.length, multipleBefore, 'multi-file raster selections cannot upload only the first item')
  assert.match(state.formErrors.files, /每次只支持上传一个文件/)
  state.removeFile(state.selectedFiles.value[1])
  await nextTick()
  assert.equal(state.formErrors.files, '', 'correcting an invalid selection clears its inline error')

  pick([file('image.tif', 2048)])
  const gate = deferred()
  harness.gate = gate
  const uploadOnce = state.submitUpload()
  await state.submitUpload()
  assert.equal(harness.chunks.length, 1, 'double-clicking submit starts one file upload')
  assert.equal(state.upload.submitting, true)
  gate.resolve()
  await uploadOnce
  assert.equal(harness.inserts.length, 1, 'double-clicking submit creates one dataset')
  assert.equal(harness.uploaded, 1, 'one successful upload emits one completion callback')
  assert.equal(state.upload.submitting, false)

  state.openUpload('vector')
  await nextTick()
  pick([file('incomplete.shp', 1600)])
  const chunksBeforeInvalid = harness.chunks.length
  const insertsBeforeInvalid = harness.inserts.length
  const uploadedBeforeInvalid = harness.uploaded
  await state.submitUpload()
  assert.match(harness.warnings.at(-1), /SHP 配套文件不完整/)
  assert.deepEqual(state.shapeFiles.value.missing, ['.shx', '.dbf'])
  assert.match(state.formErrors.files, /缺少 .shx、.dbf/)
  assert.equal(harness.chunks.length, chunksBeforeInvalid, 'an invalid SHP group creates no uploaded files')
  assert.equal(harness.inserts.length, insertsBeforeInvalid, 'an invalid SHP group creates no dataset record')
  assert.equal(harness.uploaded, uploadedBeforeInvalid)

  state.openUpload('vector', 'catalog-a')
  await nextTick()
  const shapeFiles = [file('roads.dbf', 2200), file('roads.prj', 350), file('roads.shp', 1500), file('roads.shx', 800)]
  pick(shapeFiles)
  assert.equal(state.upload.file.name, 'roads.shp', 'the SHP main file is selected independent of chooser order')
  const firstShapeChunk = harness.chunks.length
  const callbacksBeforeShape = harness.uploaded
  await state.submitUpload()
  const shapeChunks = harness.chunks.slice(firstShapeChunk)
  assert.equal(shapeChunks.length, shapeFiles.length)
  assert.deepEqual(shapeChunks.map((item) => item.name).sort(), shapeFiles.map((item) => item.name).sort(), 'each selected companion file is uploaded exactly once')
  const totalBytes = shapeFiles.reduce((sum, item) => sum + item.size, 0)
  const inserted = harness.inserts.at(-1)
  assert.match(inserted.body.id, /^[0-9a-f-]{36}$/, 'preallocated IDs let backend ingestion logs refer to the created layer')
  assert.equal(inserted.body.fileSize, Math.ceil(totalBytes / 1024), 'stored size includes the complete SHP family in KiB')
  assert.notEqual(inserted.body.fileSize, Math.ceil(state.upload.file.size / 1024), 'the size must not describe only the main SHP file')
  assert.equal(inserted.body.layerType, 'catalog-a')
  assert.equal(inserted.body.uploadFileType, '1')
  assert.equal(inserted.query.filePathName, 'roads.shp', 'ingestion references the main SHP basename')
  assert.equal(harness.uploaded - callbacksBeforeShape, 1, 'a successful SHP group emits one completion callback')
  assert.equal(harness.statusUpdates.length, harness.inserts.length)
  assert.equal(state.upload.visible, false)
  assert.equal(state.upload.progress, 100)
  assert.deepEqual(harness.errors, [])
  assert.match(harness.successes.at(-1), /正在入库/)
  assert.doesNotMatch(harness.successes.at(-1), /入库成功/, 'an accepted asynchronous vector ingestion must not claim completion')

  state.openUpload('raster')
  await nextTick()
  pick([file('retry.tif', 2048)])
  harness.failNextChunk = true
  await state.submitUpload()
  assert.equal(state.upload.file.name, 'retry.tif')
  assert.equal(state.upload.visible, true)
  assert.equal(state.upload.name, 'retry')
  assert.match(state.uploadError.value, /上传连接中断/)
  state.upload.name = '修正后的栅格'
  await nextTick()
  assert.equal(state.uploadError.value, '', 'editing an input clears a previous server failure')
  const processingGate = deferred()
  harness.insertGate = processingGate
  const retry = state.submitUpload()
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(state.upload.phase, 'processing')
  assert.equal(state.submitLabel.value, '正在入库…')
  processingGate.resolve()
  await retry
  assert.match(harness.successes.at(-1), /入库成功/)
  assert.equal(state.uploadError.value, '')

  state.openUpload('oblique')
  await nextTick()
  pick([file('model.osgb', 2048)])
  assert.equal(state.submitLabel.value, '上传文件')
  assert.match(state.completionHint.value, /待转换/)
  const modelGate = deferred()
  harness.modelGate = modelGate
  const model = state.submitUpload()
  assert.equal(state.submitLabel.value, '正在完成上传…', 'three-dimensional completion must not say ingestion is running')
  modelGate.resolve()
  await model
  assert.match(harness.successes.at(-1), /待转换/)

  state.openUpload('tileset')
  await nextTick()
  state.upload.srcMode = 'resume'
  state.upload.resumeId = 'original-tiles'
  harness.original = { status: '0', modelType: 'TILESET', fileName: 'tiles.zip', totalSize: 1024, srcFormat: 'tileset' }
  pick([file('different.zip', 1024)])
  const callsBeforeResume = harness.threeDimensionalCalls
  await state.submitUpload()
  assert.equal(harness.threeDimensionalCalls, callsBeforeResume, 'resume verifies the original file before uploading any parts')
  assert.match(state.uploadError.value, /文件名或大小不一致/)
  pick([file('tiles.zip', 1024)])
  await nextTick()
  assert.equal(state.uploadError.value, '')
  await state.submitUpload()
  assert.equal(harness.threeDimensionalCalls, callsBeforeResume + 1)
  assert.match(harness.successes.at(-1), /已发布/)
  assert.doesNotMatch(harness.successes.at(-1), /待转换/, 'resumed TILESET results are published by completeUpload')
  app.unmount()
  console.log('Dataset upload integration tests passed: type/resume reset, multi-file rejection, inline validation/recovery, complete SHP group/size/ID, retry, accurate raster/3D phases, verified resume.')
} finally {
  delete globalThis.__datasetUploadHarness
  await rm(temporary, { recursive: true, force: true })
}
