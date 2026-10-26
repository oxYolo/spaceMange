import { isMvtServiceUrl } from './service-url'

export interface IntegrationService { type: string; url: string }
export interface IntegrationExample {
  id: 'curl' | 'openlayers' | 'geojson' | 'cesium'
  label: string
  language: string
  code: string
  requirement: string
}
export interface ServiceIntegration {
  protocol: string
  sourceUrl: string
  requestUrl: string
  capabilitiesUrl: string
  layer: string
  featureLimit?: number
  note: string
  examples: IntegrationExample[]
}

/** POSIX shell 的单个参数，不让 URL 中的引号、换行、$() 或反引号变成命令。 */
export function shellQuote(value: string): string {
  return "'" + value.replace(/'/g, "'\\''") + "'"
}

/** JavaScript 字符串字面量；也可安全粘贴进 HTML 内联脚本。 */
export function jsString(value: string): string {
  return JSON.stringify(value).replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029')
}

function parameter(url: URL, ...names: string[]): string {
  const wanted = new Set(names.map((name) => name.toLowerCase()))
  return [...url.searchParams].find(([name]) => wanted.has(name.toLowerCase()))?.[1] || ''
}
function removeParameters(url: URL, names: string[]): void {
  const wanted = new Set(names.map((name) => name.toLowerCase()))
  for (const name of [...url.searchParams.keys()]) if (wanted.has(name.toLowerCase())) url.searchParams.delete(name)
}
function webUrl(value: string, baseUrl?: string): URL | null {
  if (!value) return null
  try {
    const url = baseUrl ? new URL(value, baseUrl) : new URL(value)
    return ['http:', 'https:'].includes(url.protocol) ? url : null
  } catch { return null }
}

function moduleCode(imports: string, signature: string, body: string): string {
  const indented = body.split('\n').map((line) => line ? '  ' + line : '').join('\n')
  return (imports ? imports + '\n\n' : '') + `export ${signature} {\n${indented}\n}`
}

const TILE_PARAMETERS = ['service', 'version', 'request', 'layer', 'layers', 'tilematrixset', 'tilematrix', 'tilecol', 'tilerow', 'format', 'style']
const WMS_PARAMETERS = ['service', 'request', 'version', 'layers', 'bbox', 'width', 'height', 'srs', 'crs', 'styles', 'format', 'transparent', 'bgcolor', 'exceptions', 'time', 'elevation', 'sld', 'sld_body']

function capabilitiesUrlOf(url: URL): string {
  const capabilities = new URL(url)
  removeParameters(capabilities, TILE_PARAMETERS)
  capabilities.hash = ''
  capabilities.searchParams.set('SERVICE', 'WMTS')
  capabilities.searchParams.set('VERSION', '1.0.0')
  capabilities.searchParams.set('REQUEST', 'GetCapabilities')
  return capabilities.href
}

function wmsExample(url: URL, layer: string): IntegrationExample {
  const endpoint = new URL(url)
  const parameters: Record<string, string> = { LAYERS: layer }
  for (const name of ['version', 'styles', 'format', 'transparent', 'bgcolor', 'exceptions', 'time', 'elevation', 'sld', 'sld_body']) {
    const existing = [...url.searchParams].find(([key]) => key.toLowerCase() === name)
    if (existing) parameters[name.toUpperCase()] = existing[1]
  }
  removeParameters(endpoint, WMS_PARAMETERS)
  endpoint.hash = ''
  const params = '{\n' + Object.entries(parameters).map(([key, value]) => `    ${key}: ${jsString(value)}`).join(',\n') + '\n  }'
  return {
    id: 'openlayers', label: 'OpenLayers', language: 'JavaScript',
    requirement: '需安装 ol；调用 addServiceLayer(map)，传入已有 OpenLayers Map 实例。地图范围和坐标系由该实例决定。',
    code: moduleCode("import TileLayer from 'ol/layer/Tile.js';\nimport TileWMS from 'ol/source/TileWMS.js';", 'function addServiceLayer(map)', `// BBOX、宽高和请求坐标系由 OpenLayers 根据地图视图生成。\nconst layer = new TileLayer({\n  source: new TileWMS({\n    url: ${jsString(endpoint.href)},\n    params: ${params},\n    crossOrigin: 'anonymous'\n  })\n});\nmap.addLayer(layer);\nreturn layer;`),
  }
}

function wmtsExample(url: URL, layer: string, capabilitiesUrl: string, vector: boolean): IntegrationExample {
  const endpoint = new URL(url)
  removeParameters(endpoint, TILE_PARAMETERS)
  endpoint.hash = ''
  const matrixSet = parameter(url, 'tilematrixset')
  const format = parameter(url, 'format')
  const selection = [`layer: layerName`, ...(matrixSet ? [`matrixSet: ${jsString(matrixSet)}`] : []), ...(format ? [`format: ${jsString(format)}`] : [])].join(', ')
  const imports = vector
    ? "import VectorTileLayer from 'ol/layer/VectorTile.js';\nimport VectorTileSource from 'ol/source/VectorTile.js';\nimport MVT from 'ol/format/MVT.js';"
    : "import TileLayer from 'ol/layer/Tile.js';"
  const verify = format ? `\nif (!published.Format?.includes(${jsString(format)})) {\n  throw new Error('能力文档未声明当前地址的瓦片格式');\n}` : ''
  const verifyMatrix = matrixSet ? `\nif (!published.TileMatrixSetLink?.some(link => link.TileMatrixSet === ${jsString(matrixSet)})) {\n  throw new Error('能力文档未声明当前地址的矩阵集');\n}` : ''
  const attach = vector
    ? `const layer = new VectorTileLayer({\n  source: new VectorTileSource({\n    format: new MVT(),\n    projection: options.projection,\n    tileGrid: options.tileGrid,\n    tileUrlFunction: wmtsSource.getTileUrlFunction(),\n    wrapX: options.wrapX\n  })\n});`
    : `const layer = new TileLayer({ source: wmtsSource });`
  return {
    id: 'openlayers', label: 'OpenLayers', language: 'JavaScript',
    requirement: '需安装 ol；调用 addServiceLayer(map)，传入已有地图。能力文档提供真实网格，跨域访问需服务允许 CORS。',
    code: moduleCode(`${imports}\nimport WMTS, { optionsFromCapabilities } from 'ol/source/WMTS.js';\nimport WMTSCapabilities from 'ol/format/WMTSCapabilities.js';`, 'async function addServiceLayer(map)', `const response = await fetch(${jsString(capabilitiesUrl)});\nif (!response.ok) throw new Error('能力文档读取失败：' + response.status);\nconst capabilities = new WMTSCapabilities().read(await response.text());\nconst layerName = ${jsString(layer)};\nconst published = capabilities.Contents?.Layer?.find(item => item.Identifier === layerName);\nif (!published) throw new Error('能力文档中未找到当前图层');${verify}${verifyMatrix}\nconst options = optionsFromCapabilities(capabilities, { ${selection} });\nif (!options) throw new Error('无法解析当前图层的网格');\n\n// 沿用已返回的 KVP 端点和附加参数；网格、样式等来自能力文档。\nconst wmtsSource = new WMTS({\n  ...options,\n  urls: [${jsString(endpoint.href)}],\n  requestEncoding: 'KVP',\n  crossOrigin: 'anonymous'\n});\n${attach}\nmap.addLayer(layer);\nreturn layer;`),
  }
}

function boundedFeatureRequest(url: URL, layer: string): { url: string; limit: number } | null {
  const version = parameter(url, 'version')
  // 只使用已经返回的 WFS/OWS 端点，不把 WMS 地址猜成另一个端点。
  if (!layer || !/^\d+\.\d+(?:\.\d+)?$/.test(version) || !(/\/(?:wfs|ows)\/?$/i.test(url.pathname) || parameter(url, 'service').toUpperCase() === 'WFS')) return null
  const result = new URL(url)
  const suppliedLimit = Number(parameter(url, 'maxfeatures', 'count'))
  const limit = Number.isFinite(suppliedLimit) && suppliedLimit > 0 ? Math.max(1, Math.min(1000, Math.floor(suppliedLimit))) : 1000
  const reference = parameter(url, 'srsname', 'srs', 'crs')
  removeParameters(result, ['service', 'version', 'request', 'typename', 'typenames', 'layer', 'layers', 'outputformat', 'maxfeatures', 'count', 'format', 'width', 'height', 'styles', 'transparent', 'srs', 'crs'])
  result.hash = ''
  result.searchParams.set('service', 'WFS')
  result.searchParams.set('version', version)
  result.searchParams.set('request', 'GetFeature')
  result.searchParams.set(version.startsWith('2.') ? 'typeNames' : 'typeName', layer)
  result.searchParams.set('outputFormat', 'application/json')
  result.searchParams.set(version.startsWith('2.') ? 'count' : 'maxFeatures', String(limit))
  if (reference) { removeParameters(result, ['srsname']); result.searchParams.set('srsName', reference) }
  return { url: result.href, limit }
}

/** 只生成文本；不会请求服务、执行代码或补造图层/端点/矩阵参数。 */
export function buildServiceIntegration(service: IntegrationService, baseUrl?: string): ServiceIntegration {
  const protocol = service.type.toUpperCase() === '3DTILES' ? '3DTiles' : service.type.toUpperCase()
  const result: ServiceIntegration = { protocol, sourceUrl: '', requestUrl: '', capabilitiesUrl: '', layer: '', note: '', examples: [] }
  const url = webUrl(service.url.trim(), baseUrl)
  if (!url) { result.note = '服务地址无效，仅支持 HTTP 或 HTTPS 地址。'; return result }
  result.sourceUrl = url.href
  result.layer = parameter(url, protocol === 'WFS' ? 'typenames' : 'layer', protocol === 'WFS' ? 'typename' : 'layers', ...(protocol === 'WFS' ? ['layers', 'layer'] : []))
  const request = new URL(url)
  request.hash = ''
  result.requestUrl = request.href
  if (protocol === 'WFS') {
    const feature = boundedFeatureRequest(url, result.layer)
    if (!feature) { result.note = '当前地址缺少可确认的 WFS 端点、版本或图层名，暂不能生成限定条数的要素请求。'; return result }
    result.requestUrl = feature.url
    result.featureLimit = feature.limit
  }
  const output = protocol === 'WFS' ? 'features.geojson' : protocol === '3DTiles' ? 'model-response.bin' : 'service-response.bin'
  result.examples.push({
    id: 'curl', label: 'cURL', language: 'Bash / zsh', requirement: '需安装 curl；复制到终端执行。请求结果保存为文件，避免二进制内容写入终端。',
    code: `curl --fail --show-error --location --globoff \\\n  --url ${shellQuote(result.requestUrl)} \\\n  --output ${shellQuote(output)}`,
  })
  if (url.username || url.password) { result.note = '当前地址含用户信息；请在客户端中单独配置认证后使用该服务。'; return result }
  if (protocol === 'WMS') {
    if (result.layer) result.examples.push(wmsExample(url, result.layer))
    else result.note = '当前地址未提供 layers 参数，无法确认真实图层名；可先复制原地址。'
  } else if (protocol === 'WMTS' || protocol === 'MVT') {
    result.capabilitiesUrl = capabilitiesUrlOf(url)
    if (parameter(url, 'version') && parameter(url, 'version') !== '1.0.0') result.note = '原始地址沿用服务返回参数；客户端示例通过标准 WMTS 1.0.0 能力文档读取实际配置。'
    if (!result.layer) result.note = '当前地址缺少 layer 参数，暂不能生成地图接入代码；请从能力文档确认图层标识。'
    else if (protocol === 'MVT' && !isMvtServiceUrl(url.href)) result.note = '当前地址未声明可确认的 MVT 格式，未生成矢量瓦片代码。'
    else result.examples.push(wmtsExample(url, result.layer, result.capabilitiesUrl, protocol === 'MVT'))
  } else if (protocol === 'WFS') {
    result.examples.push({ id: 'geojson', label: 'GeoJSON 获取', language: 'JavaScript', requirement: `调用 fetchServiceFeatures() 返回最多 ${result.featureLimit} 条要素，保留原有范围及过滤条件。跨域访问需服务允许 CORS。`, code: moduleCode('', 'async function fetchServiceFeatures()', `const response = await fetch(${jsString(result.requestUrl)});\nif (!response.ok) throw new Error('要素读取失败：' + response.status);\nconst geojson = await response.json();\nif (geojson.type !== 'FeatureCollection' || !Array.isArray(geojson.features)) {\n  throw new Error('服务未返回 GeoJSON FeatureCollection');\n}\nreturn geojson;`) })
  } else if (protocol === '3DTiles') {
    if (/\.(?:glb|gltf)$/i.test(url.pathname)) result.note = '当前成果为 glTF 单体，需要确认地理定位后再接入三维场景；可复制原地址或请求命令。'
    else result.examples.push({ id: 'cesium', label: 'Cesium', language: 'JavaScript', requirement: '需安装 cesium 并配置其静态资源目录；调用 createServiceViewer(container)，传入有明确宽高的容器，卸载时销毁返回的 viewer。', code: moduleCode("import { Viewer, Cesium3DTileset } from 'cesium';\nimport 'cesium/Build/Cesium/Widgets/widgets.css';", 'async function createServiceViewer(container)', `const viewer = new Viewer(container, {\n  baseLayer: false, imageryProvider: false,\n  geocoder: false, baseLayerPicker: false,\n  timeline: false, animation: false\n});\ntry {\n  const url = ${jsString(result.requestUrl)};\n  const tileset = typeof Cesium3DTileset.fromUrl === 'function'\n    ? await Cesium3DTileset.fromUrl(url)\n    : new Cesium3DTileset({ url });\n  viewer.scene.primitives.add(tileset);\n  if (tileset.readyPromise) await tileset.readyPromise;\n  await viewer.zoomTo(tileset);\n  return viewer;\n} catch (error) {\n  viewer.destroy();\n  throw error;\n}`) })
  }
  return result
}
