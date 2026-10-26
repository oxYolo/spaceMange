export interface DatasetFile { name: string; size: number }
const shapeExtensions = ['shp', 'shx', 'dbf', 'prj', 'cpg']
const requiredShapeExtensions = ['shp', 'shx', 'dbf']
const allowed: Record<string, string[]> = {
  vector: shapeExtensions, white: shapeExtensions, raster: ['tif', 'tiff', 'img'], terrain: ['tif', 'tiff', 'img'],
  oblique: ['osgb', 'zip'], pointcloud: ['las', 'laz', 'zip'], tileset: ['zip', 'json'], single: ['obj', 'fbx', 'zip'], bim: ['ifc', 'zip'],
}
export function isShapeDataset(typeKey: string): boolean { return typeKey === 'vector' || typeKey === 'white' }
export function acceptedDatasetFiles(typeKey: string): string {
  return (allowed[typeKey] || []).map(ext => `.${ext}`).join(',')
}
export function inspectShapeFiles(files: DatasetFile[]) {
  const extensions = new Set(files.map(file => file.name.split('.').pop()?.toLowerCase() || ''))
  return {
    missing: requiredShapeExtensions.filter(ext => !extensions.has(ext)).map(ext => `.${ext}`),
    optionalMissing: ['prj', 'cpg'].filter(ext => !extensions.has(ext)).map(ext => `.${ext}`),
  }
}
export function datasetFormatHint(typeKey: string): string {
  if (!typeKey) return '先选择数据类型，再添加文件。'
  if (isShapeDataset(typeKey)) return '选择同名的 .shp、.shx、.dbf 文件；建议同时添加 .prj（坐标系）和 .cpg（编码）。'
  if (typeKey === 'raster' || typeKey === 'terrain') return '每次上传一个栅格文件：.tif、.tiff 或 .img。'
  if (typeKey === 'tileset') return '上传完整 3DTiles 成果 ZIP，保留目录结构；也可选择 tileset.json。完成后直接发布。'
  const formats = (allowed[typeKey] || []).filter(ext => ext !== 'zip').map(ext => `.${ext}`).join('、')
  return `每次上传一个 ${formats} 文件或 ZIP 包，上传后在数据列表中发起转换。`
}
/** Validate the entire file group before any upload creates server state. */
export function validateDatasetFiles(typeKey: string, files: DatasetFile[], resume = false): string {
  if (!typeKey) return '请选择数据类型'
  if (resume && (isShapeDataset(typeKey) || typeKey === 'raster' || typeKey === 'terrain')) return '仅三维数据支持断点续传'
  if (!files.length) return '请选择本地文件'
  if (files.some(file => file.size <= 0)) return '所选数据包含空文件，请检查后重新选择'
  if (resume && files.length !== 1) return '续传时请只选择原任务对应的一个文件'
  const extensions = files.map(file => file.name.split('.').pop()?.toLowerCase() || '')
  const supported = allowed[typeKey]
  if (!supported) return '该数据类型暂不支持上传'
  const invalid = extensions.find(ext => !supported.includes(ext))
  if (invalid !== undefined) return `当前类型不支持 .${invalid || '?'} 文件，支持：${supported.map(ext => '.' + ext).join(' / ')}`
  if (isShapeDataset(typeKey)) {
    const { missing } = inspectShapeFiles(files)
    if (missing.length) return `SHP 配套文件不完整：缺少 ${missing.join('、')}，请添加同名文件`
    if (new Set(extensions).size !== extensions.length) return '请只选择一组 SHP 数据，同一扩展名不能重复'
    if (new Set(files.map(file => file.name.replace(/\.[^.]+$/, '').toLowerCase())).size !== 1) return 'SHP 配套文件必须具有相同的主文件名'
  } else if (files.length !== 1) return '该数据类型每次只支持上传一个文件，请移除多余文件后重试'
  return ''
}
