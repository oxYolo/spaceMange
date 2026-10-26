/** 最新请求与组件生命周期共用的轻量代次控制，不让过期结果覆盖当前界面。 */
export function createRequestScope() {
  let epoch = 0
  let disposed = false
  const channels = new Map<string, number>()
  function begin(channel: string) {
    const generation = epoch
    const sequence = (channels.get(channel) || 0) + 1
    channels.set(channel, sequence)
    return { current: () => !disposed && generation === epoch && channels.get(channel) === sequence }
  }
  function invalidate() { epoch++; channels.clear() }
  return { begin, invalidate, dispose: () => { disposed = true; invalidate() } }
}
