/** 有界短缓存；共享正在进行的读取，失效后旧请求不会写回缓存。 */
export function createReadCache<T>(ttl: number | ((value: T) => number), limit = 100, now = Date.now) {
  const values = new Map<string, { value: T; expires: number }>()
  const pending = new Map<string, Promise<T>>()

  function invalidate(prefix = '') {
    for (const key of values.keys()) if (key.startsWith(prefix)) values.delete(key)
    for (const key of pending.keys()) if (key.startsWith(prefix)) pending.delete(key)
  }

  function read(key: string, loader: () => Promise<T>, force = false): Promise<T> {
    if (force) values.delete(key)
    const cached = values.get(key)
    if (cached && cached.expires > now()) return Promise.resolve(cached.value)
    if (pending.has(key)) return pending.get(key)!
    const task = Promise.resolve().then(loader).then((value) => {
      if (pending.get(key) === task) {
        values.delete(key)
        values.set(key, { value, expires: now() + (typeof ttl === 'function' ? ttl(value) : ttl) })
        while (values.size > limit) values.delete(values.keys().next().value!)
      }
      return value
    }).finally(() => {
      if (pending.get(key) === task) pending.delete(key)
    })
    pending.set(key, task)
    return task
  }

  return { read, invalidate }
}
