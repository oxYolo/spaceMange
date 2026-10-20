import pako from 'pako'

/**
 * 解压后端返回的压缩串。
 * 对应后端 com.whulri.dmspdatamanagementcore.util.CompressionUtils#compressString：
 *   字符串 → UTF-8 → Gzip → Base64
 * 用于 /layerContent/getLayerTree、/getLayerTreeByType。
 */
export function decompressString(compressed: string): string {
  if (!compressed) return ''
  try {
    const bin = atob(compressed)
    const bytes = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
    const out = pako.ungzip(bytes)
    return new TextDecoder('utf-8').decode(out)
  } catch (e) {
    // 兼容：后端未压缩时原样返回
    return compressed
  }
}

/** 解压并 JSON.parse */
export function decompressJson<T = any>(compressed: string): T | null {
  const text = decompressString(compressed)
  if (!text) return null
  try {
    return JSON.parse(text) as T
  } catch {
    return null
  }
}
