/**
 * 从 getServerInterface 响应提取地址。
 *
 * 后端实际返回 `{ publishType, list: [...] }`（ServerInterfaceServiceImpl#getServerInterface），
 * list 项由 ServerInterfaceOutputDto 序列化，字段带 @JsonProperty 中文键：
 * `服务路径`（地址）、`关键字`（WMS/WMTS/WFS 等）；英文键 serverPath/serverKeyWord 一并兼容，
 * 裸串与通用字段形态亦保留。WMS 地址为完整 GetMap URL，WMTS 为单瓦片样例地址。
 */
/** 后端部分 MVT 响应仍标记为 WMTS，只有实际请求格式才能证明是矢量瓦片。 */
export function isMvtServiceUrl(value: string): boolean {
  try {
    const url = new URL(value, 'https://parse.invalid')
    const format = [...url.searchParams].find(([key]) => key.toLowerCase() === 'format')?.[1].toLowerCase().replace(/\s+/g, '') || ''
    return format.split(';')[0] === 'application/vnd.mapbox-vector-tile' || format === 'application/x-protobuf;type=mapbox-vector'
  } catch { return false }
}

export function extractSvcUrl(res: any, serverType?: string): string {
  if (!res) return ''
  if (typeof res === 'string') return res
  if (typeof res === 'object') {
    const inner = res.result ?? res
    if (typeof inner === 'string') return inner
    // { publishType, list: [{ 关键字|serverKeyWord, 服务路径|serverPath }] }
    if (Array.isArray(inner?.list) && inner.list.length) {
      const kw = String(serverType || inner.publishType || '').toUpperCase()
      const kwOf = (x: any) => String(x?.serverKeyWord ?? x?.['关键字'] ?? '').toUpperCase()
      const pathOf = (x: any) => (typeof x?.serverPath === 'string' ? x.serverPath : x?.['服务路径'])
      const hit =
        inner.list.find((x: any) => kwOf(x) === kw) ||
        // getMVTInfo 的历史实现将关键字写成 WMTS；普通图片 WMTS 不可当成 MVT。
        (kw === 'MVT' ? inner.list.find((x: any) => kwOf(x) === 'WMTS' && isMvtServiceUrl(String(pathOf(x) || ''))) : undefined) ||
        // 请求 WFS 时不可把返回列表中现有的 WMS 地址误判为 WFS。
        inner.list.find((x: any) => !kwOf(x) && typeof pathOf(x) === 'string' && pathOf(x))
      const path = String(pathOf(hit) ?? '').trim()
      if (path) return path
      return ''
    }
    return String(
      inner?.url ?? inner?.serviceUrl ?? inner?.path ?? inner?.address ?? inner?.value ?? inner?.serverUrl ?? '',
    )
  }
  return ''
}

/** 拆分 WMS 请求地址 → [服务基地址, layers 参数值]（OpenLayers TileWMS 需要基地址 + layers 分开传） */
export function splitWmsUrl(url: string): [string, string] {
  if (!url) return ['', '']
  try {
    const u = new URL(url)
    const layers = u.searchParams.get('layers') || u.searchParams.get('LAYERS') || ''
    u.search = ''
    u.hash = ''
    return [u.toString(), layers]
  } catch {
    const [base, qs] = url.split('?')
    const m = /(?:^|&)layers=([^&]+)/i.exec(qs || '')
    let layer = m?.[1] || ''
    try { layer = decodeURIComponent(layer) } catch { /* 保留原始参数 */ }
    return [base || url, layer]
  }
}
