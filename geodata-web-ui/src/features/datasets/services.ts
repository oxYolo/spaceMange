import { createReadCache } from './cache'
import { isRasterTypeKey, protocolsFor, type UnifiedRow } from './model'
import { extractSvcUrl } from './service-url'

export interface SvcAddressDetail { url: string; type: string }
export interface ServiceReadOptions { force?: boolean }
export type DatasetTileStatus = 'IDLE' | 'RUNNING' | 'DONE' | 'FAILED'

export interface DatasetTileProgress {
  status: DatasetTileStatus
  percent: number
  tilesProcessed?: number
  tilesTotal?: number
  message: string
  error: string
}

export interface DatasetServiceState {
  addresses: SvcAddressDetail[]
  formats: string[]
  url: string
  wms: string
  wmts: string
  tile: string
  progress: DatasetTileProgress
  /** 地址或切片读取失败时保留明确提示，避免伪装为“没有服务”。 */
  error: string
}

interface AddressRead { items: SvcAddressDetail[]; errors: string[] }

export interface DatasetServiceSource {
  address(dataId: string, protocol: string): Promise<unknown>
  publishUrl(dataId: string): Promise<unknown>
  tileProgress(dataId: string): Promise<unknown>
  scope(): string
}

function idleProgress(): DatasetTileProgress {
  return { status: 'IDLE', percent: 0, message: '', error: '' }
}

function unwrap(value: any): any { return value?.result ?? value }

function preferredAddress(items: SvcAddressDetail[]): SvcAddressDetail | undefined {
  // 预览优先选择可渲染协议；WFS 作为仅提供要素查询的最后兜底。
  return ['WMS', 'WMTS', 'MVT', '3DTiles', 'WFS'].map((type) => items.find((item) => item.type === type)).find(Boolean)
}

/** 缓存地址和切片进度分别设置时效，轮询时不重复获取稳定服务地址。 */
export function createDatasetServiceReader(source: DatasetServiceSource) {
  const addresses = createReadCache<AddressRead>((value) => value.items.length && !value.errors.length ? 30_000 : 3_000)
  const progress = createReadCache<DatasetTileProgress>(2_000)
  // 在每次读取时还带上发布状态，发布后会自然获得新的缓存键。
  const keyFor = (row: UnifiedRow, scope: string) => `${row.src}:${row.id}|${scope}|${row.typeKey}|${row.published}|`

  function assertSession(scope: string) {
    if (source.scope() !== scope) throw new Error('登录状态已变化，请重新加载服务')
  }

  async function readAddresses(row: UnifiedRow, options: ServiceReadOptions): Promise<AddressRead> {
    if (!row.published) return { items: [], errors: [] }
    const scope = source.scope()
    const value = await addresses.read(keyFor(row, scope), async () => {
      if (row.src === 'm3d') {
        try {
          const url = extractSvcUrl(await source.publishUrl(row.id))
          return { items: url ? [{ url, type: '3DTiles' }] : [], errors: [] }
        } catch { return { items: [], errors: ['三维服务地址暂不可用'] } }
      }
      const protocols = protocolsFor(row.typeKey)
      const results = await Promise.allSettled(protocols.map((type) => source.address(row.id, type)))
      const errors: string[] = []
      const items = results.flatMap((result, index) => {
        const type = protocols[index]
        if (result.status === 'rejected') errors.push(`${type} 服务地址暂不可用`)
        const url = result.status === 'fulfilled' ? extractSvcUrl(result.value, type) : ''
        return url ? [{ type, url }] : []
      })
      return { items, errors }
    }, options.force)
    assertSession(scope)
    return { items: value.items.map((address) => ({ ...address })), errors: [...value.errors] }
  }

  async function fetchAllSvcAddresses(row: UnifiedRow, options: ServiceReadOptions = {}): Promise<SvcAddressDetail[]> {
    const value = await readAddresses(row, options)
    if (value.errors.length && !value.items.length) throw new Error(value.errors.join('；'))
    return value.items
  }

  async function fetchSvcAddressDetail(row: UnifiedRow, options: ServiceReadOptions = {}): Promise<SvcAddressDetail> {
    const items = await fetchAllSvcAddresses(row, options)
    return preferredAddress(items) || { url: '', type: row.src === 'm3d' ? '3DTiles' : '' }
  }

  async function readProgress(row: UnifiedRow, options: ServiceReadOptions): Promise<DatasetTileProgress> {
    if (row.src !== 'layer' || !isRasterTypeKey(row.typeKey)) return idleProgress()
    const scope = source.scope()
    const result = await progress.read(keyFor(row, scope), async () => {
      try {
        const value = unwrap(await source.tileProgress(row.id))
        const rawStatus = String(value?.status || '').toUpperCase()
        if (!['IDLE', 'RUNNING', 'DONE', 'FAILED'].includes(rawStatus)) throw new Error('切片进度响应异常')
        const status: DatasetTileStatus = ['RUNNING', 'DONE', 'FAILED'].includes(rawStatus) ? rawStatus as DatasetTileStatus : 'IDLE'
        return {
          status,
          percent: Math.max(0, Math.min(100, Number(value?.percent) || 0)),
          tilesProcessed: value?.tilesProcessed,
          tilesTotal: value?.tilesTotal,
          message: String(value?.message ?? value?.msg ?? ''),
          error: status === 'FAILED' ? String(value?.error ?? value?.message ?? '切片失败') : '',
        }
      } catch {
        return { ...idleProgress(), error: '进度暂不可用' }
      }
    }, options.force)
    assertSession(scope)
    return result
  }

  async function getDatasetServiceState(row: UnifiedRow, options: ServiceReadOptions = {}): Promise<DatasetServiceState> {
    const scope = source.scope()
    const [value, tileProgress] = await Promise.all([readAddresses(row, options), readProgress(row, options)])
    assertSession(scope)
    const items = value.items
    const wms = items.find((item) => item.type === 'WMS')?.url || ''
    const wmts = items.find((item) => item.type === 'WMTS')?.url || ''
    return {
      addresses: items,
      formats: items.map((item) => item.type),
      url: preferredAddress(items)?.url || '',
      wms,
      wmts,
      tile: tileProgress.status === 'RUNNING' ? '切片中' : tileProgress.status === 'FAILED' ? '切片失败' : '',
      progress: { ...tileProgress },
      error: [...value.errors, tileProgress.error].filter(Boolean).join('；'),
    }
  }

  function invalidateDatasetServices(row?: Pick<UnifiedRow, 'src' | 'id'>) {
    const prefix = row ? `${row.src}:${row.id}|` : ''
    addresses.invalidate(prefix)
    progress.invalidate(prefix)
  }

  return {
    fetchAllSvcAddresses,
    fetchSvcAddressDetail,
    fetchSvcAddress: async (row: UnifiedRow, options: ServiceReadOptions = {}) => (await fetchSvcAddressDetail(row, options)).url,
    getDatasetServiceState,
    invalidateDatasetServices,
  }
}
