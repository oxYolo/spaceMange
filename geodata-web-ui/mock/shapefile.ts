/** 本地 Mock 专用：读取 SHP/DBF 的基础结构和属性，不写文件，也不代替 GDAL 入库。 */
export function inspectShapefile(files: Map<string, Buffer>) {
  const shp = files.get('shp')
  const shx = files.get('shx')
  const dbf = files.get('dbf')
  if (!shp || !shx || !dbf) throw new Error('SHP 文件组缺少 .shp、.shx 或 .dbf')
  if (shp.length < 100 || shx.length < 100 || shp.readInt32BE(0) !== 9994 || shx.readInt32BE(0) !== 9994) {
    throw new Error('SHP/SHX 文件头无效')
  }
  const shapeType = shp.readInt32LE(32)
  if (shx.readInt32LE(32) !== shapeType) throw new Error('SHP 与 SHX 几何类型不一致')
  const geometry = ({ 1: 'point', 3: 'line', 5: 'polygon', 8: 'point', 11: 'point', 13: 'line', 15: 'polygon' } as Record<number, string>)[shapeType]
  if (!geometry) throw new Error(`本地 Mock 暂不支持 SHP 几何类型 ${shapeType}`)
  const bbox = [36, 44, 52, 60].map((offset) => shp.readDoubleLE(offset))
  if (bbox.some((value) => !Number.isFinite(value))) throw new Error('SHP 范围信息无效')
  return { geometry, bbox: bbox.join(','), attributes: readDbf(dbf), fileSize: Math.max(1, Math.ceil(shp.length / 1024)) }
}

function readDbf(dbf: Buffer): Record<string, unknown>[] {
  if (dbf.length < 33) throw new Error('DBF 文件头无效')
  const recordCount = dbf.readUInt32LE(4)
  const headerLength = dbf.readUInt16LE(8)
  const recordLength = dbf.readUInt16LE(10)
  if (recordCount > 5000 || headerLength < 33 || recordLength < 1 || headerLength + recordCount * recordLength > dbf.length) {
    throw new Error('DBF 记录长度无效或超出本地 Mock 限制')
  }
  const fields: { name: string; type: string; length: number }[] = []
  for (let offset = 32; offset + 32 <= headerLength && dbf[offset] !== 0x0d; offset += 32) {
    const name = dbf.subarray(offset, offset + 11).toString('utf8').replace(/\0.*$/, '').trim()
    if (!name) continue
    fields.push({ name, type: String.fromCharCode(dbf[offset + 11]), length: dbf[offset + 16] })
  }
  const rows: Record<string, unknown>[] = []
  for (let index = 0; index < recordCount; index++) {
    const start = headerLength + index * recordLength
    if (dbf[start] === 0x2a) continue
    const row: Record<string, unknown> = { gid: index + 1 }
    let cursor = start + 1
    for (const field of fields) {
      const value = dbf.subarray(cursor, cursor + field.length).toString('utf8').trim()
      row[field.name] = ['N', 'F'].includes(field.type) ? (value === '' ? null : Number(value)) : value
      cursor += field.length
    }
    rows.push(row)
  }
  return rows
}
