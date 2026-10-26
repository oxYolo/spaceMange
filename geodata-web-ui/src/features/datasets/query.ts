import { createReadCache } from './cache'
import { typeDef, type UnifiedRow } from './model'

export type DatasetSort = 'time' | 'name' | 'size'
export type SortOrder = 'asc' | 'desc'

export interface ListFilter {
  typeKey?: string
  status?: string
  /** 与后端一致，搜索数据名称。 */
  keyword?: string
  layerType?: string
  publishedOnly?: boolean
  sort?: DatasetSort
  order?: SortOrder
}

export interface PageResult {
  rows: UnifiedRow[]
  total: number
}

export interface DatasetQueryOptions { force?: boolean }

/** 接口适配器统一提供按上传时间倒序的记录，每次最多读取 200 条。 */
export interface DatasetQuerySource {
  layer(filter: ListFilter, size: number, index: number): Promise<PageResult>
  model(filter: ListFilter, size: number, index: number): Promise<PageResult>
  /** 用户与登录会话，避免缓存跨账户复用。 */
  scope(): string
}

const MAX_PAGE_SIZE = 200
const nameCollator = new Intl.Collator('zh-CN', { numeric: true })

function integer(value: number, fallback: number, max = Number.MAX_SAFE_INTEGER): number {
  return Number.isFinite(value) ? Math.min(max, Math.max(1, Math.trunc(value))) : fallback
}

function timestamp(value: string): number {
  // Java 常返回无时区的本地时间；Safari 不接受日期与时间之间的空格。
  const parsed = Date.parse(value.trim().replace(/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})/, '$1T$2'))
  return Number.isFinite(parsed) ? parsed : 0
}

/** 空列表必须来自明确的数组结构，网络/协议异常不可悄悄显示为“暂无数据”。 */
export function readDatasetPage(payload: any, normalize: (record: any) => UnifiedRow): PageResult {
  const value = payload?.result ?? payload
  const list: unknown[] | undefined = Array.isArray(value)
    ? value
    : [value?.item, value?.items, value?.records, value?.list, value?.rows, value?.data].find(Array.isArray)
  if (!list) throw new Error('数据列表响应格式异常，请重试')
  const rawTotal = value?.totalCount ?? value?.total ?? value?.count ?? value?.totalNum ?? list.length
  const total = Number(rawTotal)
  if (!Number.isFinite(total) || total < 0) throw new Error('数据总数响应格式异常，请重试')
  return { rows: list.map(normalize), total: Math.trunc(total) }
}

/** 全局排序必须发生在分页前，ID 作为并列值的稳定排序依据。 */
export function sortDatasetRows(rows: UnifiedRow[], sort: DatasetSort = 'time', order: SortOrder = 'desc'): UnifiedRow[] {
  const direction = order === 'asc' ? 1 : -1
  // 每条记录仅解析一次时间，名称比较复用 Collator，避免全量筛选时重复构造。
  return rows.map((row) => ({ row, value: sort === 'time' ? timestamp(row.time) : row.sizeBytes }))
    .sort((a, b) => {
      const value = sort === 'name' ? nameCollator.compare(a.row.name, b.row.name) : a.value - b.value
      return value * direction || a.row.key.localeCompare(b.row.key)
    })
    .map(({ row }) => row)
}

/** 两个后端列表的查询编排。默认倒序只读各源覆盖当前页所需的前缀。 */
export function createDatasetQueryService(source: DatasetQuerySource) {
  const cache = createReadCache<PageResult>(5_000)

  async function loadUnifiedPage(filter: ListFilter, pageSize: number, pageIndex: number, options: DatasetQueryOptions = {}): Promise<PageResult> {
    const size = integer(pageSize, 10, MAX_PAGE_SIZE)
    const index = integer(pageIndex, 1)
    const normalized: ListFilter = { ...filter, keyword: filter.keyword?.trim() || undefined }
    const definition = filter.typeKey ? typeDef(filter.typeKey) : undefined
    // 未识别的数据类型不能静默退回全部数据。
    if (filter.typeKey && !definition) return { rows: [], total: 0 }
    const sort = normalized.sort || 'time'
    const order = normalized.order || 'desc'
    const needsAll = Boolean(normalized.status) || normalized.publishedOnly === false || sort !== 'time' || order !== 'desc'
    const needed = Math.min(Number.MAX_SAFE_INTEGER, size * index)
    const sourceFilter = { ...normalized, status: undefined, sort: undefined, order: undefined }
    const scope = source.scope()
    const kinds: ('layer' | 'model')[] = []
    if (!definition || definition.src === 'layer') kinds.push('layer')
    if ((!definition || definition.src === 'm3d') && !normalized.layerType) kinds.push('model')

    function readPage(kind: 'layer' | 'model', fetchSize: number, number: number) {
      const key = JSON.stringify([scope, kind, sourceFilter, fetchSize, number])
      return cache.read(key, () => source[kind](sourceFilter, fetchSize, number), options.force).then((result) => {
        if (source.scope() !== scope) throw new Error('登录状态已变化，请重新加载数据')
        return result
      })
    }

    // 单源后端已按时间倒序，直接取目标页，翻至第 N 页无须重读前 N-1 页。
    if (kinds.length === 1 && !needsAll) {
      const result = await readPage(kinds[0], size, index)
      return { total: result.total, rows: result.rows.map((row) => ({ ...row, raw: { ...row.raw } })) }
    }

    async function collect(kind: 'layer' | 'model'): Promise<PageResult> {
      const fetchSize = needsAll ? MAX_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, needed)
      const fetchPage = (number: number) => readPage(kind, fetchSize, number)
      const first = await fetchPage(1)
      const count = needsAll ? first.total : Math.min(first.total, needed)
      const pages = Math.ceil(count / fetchSize)
      const rows = [...first.rows]
      // 每源最多 4 个并发，防止大目录的筛选产生请求洪峰。
      for (let start = 2; start <= pages; start += 4) {
        const parts = await Promise.all(Array.from({ length: Math.min(4, pages - start + 1) }, (_, offset) => fetchPage(start + offset)))
        rows.push(...parts.flatMap((part) => part.rows))
      }
      return { rows, total: first.total }
    }

    const parts = await Promise.all(kinds.map(collect))
    let rows = parts.flatMap((part) => part.rows)
    if (normalized.status) rows = rows.filter((row) => row.status === normalized.status)
    if (normalized.publishedOnly !== undefined) rows = rows.filter((row) => row.published === normalized.publishedOnly)
    const total = needsAll ? rows.length : parts.reduce((sum, part) => sum + part.total, 0)
    rows = sortDatasetRows(rows, sort, order)
    return {
      // 页面可更新推送状态，但不能污染其他页面读取的缓存对象。
      rows: rows.slice((index - 1) * size, index * size).map((row) => ({ ...row, raw: { ...row.raw } })),
      total,
    }
  }

  return { loadUnifiedPage, invalidateDatasetQueries: () => cache.invalidate() }
}
