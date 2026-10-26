import type { UnifiedRow } from './model'

/** Protect spreadsheet applications from executing cells as formulas. */
export function csvCell(value: unknown): string {
  let text = String(value ?? '').replace(/\0/g, '')
  if (/^[\s\u0001-\u0020]*[=+\-@]/u.test(text) || /^[\t\r\n]/.test(text)) text = "'" + text
  return '"' + text.replace(/"/g, '""') + '"'
}
export function datasetCsv(rows: readonly UnifiedRow[]): string {
  const headings = ['资源 ID', '数据名称', '数据类型', '格式', '大小（字节）', '空间参考', '所属目录', '上传人', '上传时间', '处理状态', '发布状态']
  const records = rows.map(row => [row.id, row.name, row.typeLabel, row.format, Number.isFinite(row.sizeBytes) ? row.sizeBytes : '', row.spatialRef, row.category, row.uploader, row.time, row.statusLabel, row.published ? '已发布' : '未发布'])
  return '\uFEFF' + [headings, ...records].map(values => values.map(csvCell).join(',')).join('\r\n') + '\r\n'
}
export function datasetExportFileName(scope: 'page' | 'selected', date = new Date()): string {
  const stamp = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
  return `空间数据_${scope === 'selected' ? '已选' : '当前页'}_${stamp}.csv`
}
export function downloadDatasetCsv(rows: readonly UnifiedRow[], scope: 'page' | 'selected'): void {
  if (!rows.length) throw new Error('没有可导出的数据')
  const url = URL.createObjectURL(new Blob([datasetCsv(rows)], { type: 'text/csv;charset=utf-8' }))
  let link: HTMLAnchorElement | undefined
  try {
    link = document.createElement('a')
    link.href = url
    link.download = datasetExportFileName(scope)
    document.body.appendChild(link)
    link.click()
  } finally { link?.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
}
