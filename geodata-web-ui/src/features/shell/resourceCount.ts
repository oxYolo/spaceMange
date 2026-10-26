export interface ResourceCountState {
  value: number | null
  loading: boolean
  unavailable: boolean
}

export function readResourceCount(value: unknown): number {
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '') {
    throw new Error('资源统计结果缺失')
  }
  const count = Number(value)
  if (!Number.isSafeInteger(count) || count < 0) throw new Error('资源统计结果无效')
  return count
}

export function readPageCount(value: unknown): number {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('分页统计结果缺失')
  const page = value as Record<string, unknown>
  return readResourceCount(page.totalCount ?? page.total ?? page.count ?? page.totalNum)
}

/** 两类资源并行统计，仅最新请求可以提交，部分失败时保留上次完整统计。 */
export function createResourceCounter(
  loaders: { countLayers: (user: string) => Promise<unknown>; countModels: () => Promise<unknown> },
  onChange: (state: ResourceCountState) => void,
) {
  let state: ResourceCountState = { value: null, loading: false, unavailable: false }
  let revision = 0
  let disposed = false
  let owner: string | undefined

  async function refresh(user: string): Promise<void> {
    if (disposed) return
    const requestRevision = ++revision
    state = user === owner ? { ...state, loading: true } : { value: null, loading: true, unavailable: false }
    owner = user
    onChange(state)
    const results = await Promise.allSettled([
      Promise.resolve().then(() => loaders.countLayers(user)).then(readResourceCount),
      Promise.resolve().then(() => loaders.countModels()).then(readPageCount),
    ])
    if (disposed || requestRevision !== revision) return
    const [layers, models] = results
    state = layers.status === 'fulfilled' && models.status === 'fulfilled' && Number.isSafeInteger(layers.value + models.value)
      ? { value: layers.value + models.value, loading: false, unavailable: false }
      : { ...state, loading: false, unavailable: true }
    onChange(state)
  }

  return { refresh, dispose() { disposed = true; revision++ } }
}
