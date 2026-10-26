import { DATASET_FILTER_TYPES } from './model'
import type { DatasetSort, SortOrder } from './query'

export interface DatasetListState {
  type: string
  status: string
  pub: string
  kw: string
  catalog: string
  sort: DatasetSort
  order: SortOrder
  page: number
  size: number
}
export const DATASET_LIST_QUERY_KEYS = ['type', 'status', 'pub', 'q', 'catalog', 'sort', 'order', 'page', 'size'] as const
const single = (value: unknown) => typeof value === 'string' ? value : ''
const oneOf = (value: string, values: readonly string[], fallback = '') => values.includes(value) ? value : fallback
export function readDatasetListState(query: Record<string, unknown>): DatasetListState {
  const type = single(query.type)
  const page = Number(single(query.page))
  return {
    type: oneOf(type === 'white' ? 'vector' : type === 'terrain' ? 'raster' : type, DATASET_FILTER_TYPES.map(item => item.key)),
    status: oneOf(single(query.status), ['uploading', 'converting', 'ok', 'fail']),
    pub: oneOf(single(query.pub), ['yes', 'no']),
    kw: single(query.q).trim().slice(0, 200),
    catalog: single(query.catalog).slice(0, 100),
    sort: oneOf(single(query.sort), ['time', 'name', 'size'], 'time') as DatasetSort,
    order: oneOf(single(query.order), ['asc', 'desc'], 'desc') as SortOrder,
    page: Number.isSafeInteger(page) && page > 0 && page <= 100000 ? page : 1,
    size: [10, 20, 50].includes(Number(single(query.size))) ? Number(query.size) : 10,
  }
}
export function datasetListQuery(state: DatasetListState): Record<string, string> {
  const query: Record<string, string> = {}
  if (state.type) query.type = state.type
  if (state.status) query.status = state.status
  if (state.pub) query.pub = state.pub
  if (state.kw.trim()) query.q = state.kw.trim()
  if (state.catalog) query.catalog = state.catalog
  if (state.sort !== 'time') query.sort = state.sort
  if (state.order !== 'desc') query.order = state.order
  if (state.page > 1) query.page = String(state.page)
  if (state.size !== 10) query.size = String(state.size)
  return query
}
export const datasetListSignature = (state: DatasetListState) => JSON.stringify(datasetListQuery(state))
