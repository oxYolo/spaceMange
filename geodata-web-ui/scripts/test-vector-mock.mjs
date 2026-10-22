import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'

const base = process.env.GEODATA_TEST_URL || 'http://127.0.0.1:9200'
const stem = `vector_test_${Date.now()}`
const points = [[113.90, 29.72], [113.91, 29.73]]

function header(lengthWords) {
  const bytes = Buffer.alloc(100)
  bytes.writeInt32BE(9994, 0)
  bytes.writeInt32BE(lengthWords, 24)
  bytes.writeInt32LE(1000, 28)
  bytes.writeInt32LE(1, 32)
  bytes.writeDoubleLE(113.90, 36)
  bytes.writeDoubleLE(29.72, 44)
  bytes.writeDoubleLE(113.91, 52)
  bytes.writeDoubleLE(29.73, 60)
  return bytes
}
function shapeFiles() {
  const shp = Buffer.alloc(100 + points.length * 28)
  header(shp.length / 2).copy(shp)
  const shx = Buffer.alloc(100 + points.length * 8)
  header(shx.length / 2).copy(shx)
  points.forEach(([x, y], index) => {
    const offset = 100 + index * 28
    shp.writeInt32BE(index + 1, offset)
    shp.writeInt32BE(10, offset + 4)
    shp.writeInt32LE(1, offset + 8)
    shp.writeDoubleLE(x, offset + 12)
    shp.writeDoubleLE(y, offset + 20)
    shx.writeInt32BE(offset / 2, 100 + index * 8)
    shx.writeInt32BE(10, 104 + index * 8)
  })
  return { shp, shx }
}
function dbfFile() {
  const fields = [{ name: 'Label', type: 'C', length: 12 }, { name: 'Height', type: 'N', length: 8 }]
  const headerLength = 32 + fields.length * 32 + 1
  const recordLength = 1 + fields.reduce((sum, field) => sum + field.length, 0)
  const dbf = Buffer.alloc(headerLength + points.length * recordLength + 1, 0x20)
  dbf[0] = 3
  dbf.writeUInt32LE(points.length, 4)
  dbf.writeUInt16LE(headerLength, 8)
  dbf.writeUInt16LE(recordLength, 10)
  fields.forEach((field, index) => {
    const offset = 32 + index * 32
    dbf.write(field.name, offset, 'ascii')
    dbf.write(field.type, offset + 11, 'ascii')
    dbf[offset + 16] = field.length
  })
  dbf[headerLength - 1] = 0x0d
  points.forEach((_, index) => {
    const offset = headerLength + index * recordLength
    dbf[offset] = 0x20
    dbf.write(`P${index + 1}`.padEnd(12), offset + 1, 'ascii')
    dbf.write(String((index + 1) * 10).padStart(8), offset + 13, 'ascii')
  })
  dbf[dbf.length - 1] = 0x1a
  return dbf
}

const fixtureDir = process.argv.find((arg) => arg.startsWith('--fixture-dir='))?.slice('--fixture-dir='.length)
if (fixtureDir) {
  await mkdir(fixtureDir, { recursive: true })
  for (const [extension, bytes] of Object.entries({ ...shapeFiles(), dbf: dbfFile() })) {
    await writeFile(join(fixtureDir, `sample_points.${extension}`), bytes)
  }
  await writeFile(join(fixtureDir, 'sample_points.prj'), 'GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]')
  await writeFile(join(fixtureDir, 'sample_points.cpg'), 'UTF-8')
  console.log(`SHP 测试文件已生成：${fixtureDir}`)
  process.exit(0)
}

async function request(path, init) {
  const response = await fetch(base + path, init)
  const data = await response.json()
  assert.equal(response.status, 200, JSON.stringify(data))
  if (data?.code === undefined) return data
  assert.equal(data.code, 200, JSON.stringify(data))
  return data.result
}

let createdId = ''
try {
  const files = { ...shapeFiles(), dbf: dbfFile() }
  for (const [extension, bytes] of Object.entries(files)) {
    const fileName = `${stem}.${extension}`
    const guid = `${stem}-${extension}`
    const upload = new FormData()
    upload.append('file', new Blob([bytes]), fileName)
    upload.append('chunk', '1')
    upload.append('guid', guid)
    await request('/api/upload/UploadForBreakpoint/AddFiles', { method: 'POST', body: upload })
    const merge = new FormData()
    merge.append('fileName', fileName)
    merge.append('guid', guid)
    const merged = await request('/api/upload/UploadForBreakpoint/FileMerge', { method: 'POST', body: merge })
    assert.equal(merged.logicName, fileName)
  }

  const inserted = await request(`/api/layerContent/Insert?filePathName=${stem}.shp&user=admin&isImport=1`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ layerName: '矢量联调样本', uploadFileType: '1', uploadFileName: `/mock-upload/files/${stem}.shp`, createBy: 'admin', layerType: 'cat-base' }),
  })
  createdId = inserted.id
  assert.ok(createdId)
  await request(`/api/layerContent/UpdateStatus?layerID=${createdId}`, { method: 'POST' })
  const listed = await request('/api/layerContent/GetPageListByName?pageSize=20&pageIndex=1', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ createBy: 'admin', layerName: '矢量联调样本' }),
  })
  assert.equal(listed.items[0].uploadStatus, '1')
  assert.equal(listed.items[0].dataType, '6b6941f1-67a3-11e7-8eb2-005056bb1c7e')

  const nativeAttrs = await request(`/api/layerContent/GetAttrtableByLayerId?layerId=${createdId}&pageSize=10&pageIndex=1`)
  assert.equal(nativeAttrs, null) // 无 Name 字段时复现后端旧查询的限制，前端须走通用属性表回退。
  const rawDetail = await request(`/api/layerContent/GetLayerAttrTabledDetail?layerID=${createdId}&pageSize=10&pageIndex=1`)
  const attributes = JSON.parse(JSON.parse(rawDetail).DataTableJson).data
  assert.deepEqual(attributes.map((row) => row.Label), ['P1', 'P2'])

  await request('/api/serverInterface/publishService', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ id: createdId, layerName: '矢量联调样本', publishType: 'WMS' }),
  })
  const wms = await request(`/api/serverInterface/getServerInterface?dataId=${createdId}&serverType=WMS`)
  const wfs = await request(`/api/serverInterface/getServerInterface?dataId=${createdId}&serverType=WFS`)
  assert.equal(wms.list.length, 1)
  assert.equal(wfs.list.length, 0)
  console.log('矢量 Mock 联调通过：分片上传 → 合并 → SHP/DBF 入库 → 属性读取 → WMS 发布')
} finally {
  if (createdId) await request(`/api/layerContent/DeleteLayer?id=${createdId}`, { method: 'POST' })
}
