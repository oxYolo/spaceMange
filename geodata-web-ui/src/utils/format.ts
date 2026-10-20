import dayjs from 'dayjs'

/** 时间格式化 */
export function fmtTime(v?: string | number | Date, pattern = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!v) return '-'
  const d = dayjs(v)
  return d.isValid() ? d.format(pattern) : String(v)
}

/** 文件大小格式化 */
export function fmtSize(bytes?: number | string): string {
  const n = Number(bytes)
  if (!n || Number.isNaN(n)) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 图层数据类型显示名（兼容历史 '1'/'2' 与几何类型字典 UUID：点/线/面/影像） */
export function layerTypeName(t?: string | number): string {
  const v = String(t ?? '')
  if (v === '1') return '矢量数据'
  if (v === '2') return '栅格数据'
  if (v === '6b6941f1-67a3-11e7-8eb2-005056bb1c7e') return '点数据'
  if (v === '7776934c-67a3-11e7-8eb2-005056bb1c7e') return '线数据'
  if (v === 'a2758dc0-67a3-11e7-8eb2-005056bb1c7e') return '面数据'
  if (v === 'acf11b57-0626-4e49-b385-2e9a4195221c') return '影像数据'
  return v || '-'
}

/** 发布状态 */
export function publishStatusName(v?: string | number): string {
  const s = String(v ?? '')
  if (s === '1') return '已发布'
  if (s === '0' || s === '') return '未发布'
  return s
}

/** 三维状态机：0~5 */
export const M3D_STATUS: Record<string, { label: string; type: string }> = {
  '0': { label: '上传中', type: 'blue' },
  '1': { label: '待转换', type: 'orange' },
  '2': { label: '已入队', type: 'violet' },
  '3': { label: '转换中', type: 'blue' },
  '4': { label: '已发布', type: 'green' },
  '5': { label: '失败', type: 'red' },
}

/** 工具 origin → 名称（1~10） */
export const ORIGIN_NAME: Record<string, string> = {
  '1': '图层裁剪',
  '2': '重采样',
  '3': '栅格矢量化',
  '4': '矢量栅格化',
  '5': '重分类',
  '6': '擦除',
  '7': '缓冲区',
  '8': '矢量交并补',
  '9': '栅格叠加计算',
  '10': '拓扑检查 / 三维转换',
}

/** 从各种响应结构中安全取列表 */
export function pickList<T = any>(res: any): T[] {
  if (!res) return []
  if (Array.isArray(res)) return res
  if (Array.isArray(res.item)) return res.item // ListResultOutput 字段是单数 item
  if (Array.isArray(res.items)) return res.items // PagedResultOutput
  if (Array.isArray(res.records)) return res.records
  if (Array.isArray(res.list)) return res.list
  if (Array.isArray(res.rows)) return res.rows
  if (Array.isArray(res.data)) return res.data
  return []
}

/** 从各种响应结构中安全取总数 */
export function pickTotal(res: any, fallback = 0): number {
  if (!res) return fallback
  const n = res.totalCount ?? res.total ?? res.count ?? res.totalNum
  return typeof n === 'number' ? n : fallback
}

/** 生成 GUID（分片上传会话标识） */
export function guid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/** WKT 转 Extent（仅支持 POLYGON / MULTIPOLYGON 的粗解析） */
export function wktToExtent(wkt?: string): [number, number, number, number] | null {
  if (!wkt) return null
  const nums = wkt.match(/-?\d+(\.\d+)?/g)
  if (!nums || nums.length < 8) return null
  const coords: number[][] = []
  for (let i = 0; i + 1 < nums.length; i += 2) {
    coords.push([parseFloat(nums[i]), parseFloat(nums[i + 1])])
  }
  const xs = coords.map((c) => c[0])
  const ys = coords.map((c) => c[1])
  return [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)]
}

/** 解析 bbox 字符串 "minx,miny,maxx,maxy" */
export function parseBBox(bbox?: string): [number, number, number, number] | null {
  if (!bbox) return null
  const p = bbox.split(',').map((s) => parseFloat(s.trim()))
  if (p.length !== 4 || p.some((n) => Number.isNaN(n))) return null
  return [p[0], p[1], p[2], p[3]]
}
