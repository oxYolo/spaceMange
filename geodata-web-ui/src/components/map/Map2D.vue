<script setup lang="ts">
/**
 * 二维地图基座（OpenLayers）
 * - 天地图底图（矢量 / 影像 + 注记）
 * - WMS / WMTS 服务图层挂载（直接吃 serverInterface 返回的地址）
 * - GeoJSON 矢量预览
 * - 点选拾取 / 框选 / 圈选 / 多边选（供空间查询使用）
 */
import { onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import Icon from '@/components/common/Icon.vue'
import 'ol/ol.css'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import ImageLayer from 'ol/layer/Image'
import XYZ from 'ol/source/XYZ'
import TileWMS from 'ol/source/TileWMS'
import ImageWMS from 'ol/source/ImageWMS'
import WMTS, { optionsFromCapabilities } from 'ol/source/WMTS'
import WMTSCapabilities from 'ol/format/WMTSCapabilities'
import VectorSource from 'ol/source/Vector'
import VectorTileSource from 'ol/source/VectorTile'
import VectorTileLayer from 'ol/layer/VectorTile'
import GeoJSON from 'ol/format/GeoJSON'
import WKT from 'ol/format/WKT'
import MVT from 'ol/format/MVT'
import { createXYZ } from 'ol/tilegrid'
import Draw, { createBox } from 'ol/interaction/Draw'
import { fromLonLat, toLonLat, transformExtent } from 'ol/proj'
import { getDistance } from 'ol/sphere'
import { Style, Stroke, Fill, Circle as OLCircle, Text } from 'ol/style'
import { defaults as defaultControls } from 'ol/control'
import urlConfig from '@/config'
import { geometryJsonToWkt } from '@/utils/spatial'

const props = withDefaults(
  defineProps<{
    center?: [number, number]
    zoom?: number
    /** 是否显示底图（false 时仅作为容器/图层渲染） */
    basemap?: boolean
    /** 影像底图档位：vec 矢量 / img 影像 */
    baseType?: 'vec' | 'img'
  }>(),
  {
    center: () => urlConfig.mapCenter as [number, number],
    zoom: 11,
    basemap: true,
    baseType: 'vec',
  },
)

export type DrawKind = 'Box' | 'Circle' | 'Polygon' | 'Point'

const emit = defineEmits<{
  (e: 'ready', map: Map): void
  (e: 'pick', payload: { lon: number; lat: number }): void
  (e: 'drawend', payload: { kind: DrawKind; wkt?: string; lon?: number; lat?: number; radius?: number }): void
  (e: 'moveend', extent: number[]): void
  (e: 'service-error', payload: { type: string; message: string }): void
}>()

const wrapRef = ref<HTMLDivElement>()
const mapRef = shallowRef<Map | null>(null)
const drawSource = new VectorSource()
const previewSource = new VectorSource()
const highlightSource = new VectorSource()
let drawInteraction: Draw | null = null
let disposed = false
let serviceGeneration = 0
let suppressPickUntil = 0
const serviceRequests = new Set<AbortController>()

function reportServiceError(source: any, type: string) {
  let reported = false
  const generation = serviceGeneration
  const report = () => {
    if (disposed || reported || generation !== serviceGeneration) return
    reported = true
    emit('service-error', { type, message: `${type} 地图数据加载失败，请检查服务地址或网络后重试` })
  }
  source.on('tileloaderror', report)
  source.on('imageloaderror', report)
}

const drawLayer = new VectorLayer({
  source: drawSource,
  style: new Style({
    fill: new Fill({ color: 'rgba(17,99,181,0.12)' }),
    stroke: new Stroke({ color: '#1163b5', width: 2, lineDash: [6, 4] }),
    image: new OLCircle({ radius: 5, fill: new Fill({ color: '#1163b5' }), stroke: new Stroke({ color: '#fff', width: 1.5 }) }),
  }),
})
const previewLayer = new VectorLayer({
  source: previewSource,
  style: new Style({
    fill: new Fill({ color: 'rgba(26,156,120,0.14)' }),
    stroke: new Stroke({ color: '#1a9c78', width: 1.6 }),
    image: new OLCircle({ radius: 4.5, fill: new Fill({ color: '#1a9c78' }), stroke: new Stroke({ color: '#fff', width: 1.2 }) }),
    text: new Text({
      font: '11px "Microsoft YaHei"',
      fill: new Fill({ color: '#14775c' }),
      stroke: new Stroke({ color: '#fff', width: 3 }),
      overflow: true,
    }),
  }),
})
/** 点选高亮层（属性气泡联动） */
const highlightLayer = new VectorLayer({
  source: highlightSource,
  style: new Style({
    fill: new Fill({ color: 'rgba(238,68,51,0.18)' }),
    stroke: new Stroke({ color: '#ee4433', width: 2.4 }),
    image: new OLCircle({ radius: 7, fill: new Fill({ color: '#ee4433' }), stroke: new Stroke({ color: '#fff', width: 2 }) }),
  }),
})

/** 属性气泡状态 */
const popup = reactive({
  visible: false,
  left: 0,
  top: 0,
  title: '',
  rows: [] as [string, string][],
  lon: 0,
  lat: 0,
})

function tdtLayer(type: 'vec' | 'img', annotation = false): TileLayer<XYZ> {
  const token = urlConfig.getTdtToken()
  const t = annotation ? (type === 'img' ? 'cia_w' : 'cva_w') : type === 'img' ? 'img_w' : 'vec_w'
  return new TileLayer({
    source: new XYZ({
      url: `${urlConfig.tdtUrlHttps || urlConfig.tdtUrl}/DataServer?T=${t}&x={x}&y={y}&l={z}&tk=${token}`,
      crossOrigin: 'anonymous',
      maxZoom: 18,
    }),
    properties: { kind: 'basemap' },
  })
}

function buildBase(): TileLayer<XYZ>[] {
  if (!props.basemap || !urlConfig.getTdtToken()) return []
  return [tdtLayer(props.baseType, false), tdtLayer(props.baseType, true)]
}

function init() {
  const map = new Map({
    target: wrapRef.value,
    layers: [...buildBase(), previewLayer, highlightLayer, drawLayer],
    view: new View({
      center: fromLonLat(props.center),
      zoom: props.zoom,
      projection: 'EPSG:3857',
    }),
    controls: defaultControls({ attribution: false, rotate: false, zoom: true }),
  })

  map.on('singleclick', (evt) => {
    if (drawInteraction || Date.now() < suppressPickUntil) return
    const [lon, lat] = toLonLat(evt.coordinate)
    emit('pick', { lon, lat })
  })
  map.on('moveend', () => {
    const ext = map.getView().calculateExtent(map.getSize())
    emit('moveend', transformExtent(ext, 'EPSG:3857', 'EPSG:4326'))
    if (popup.visible) positionPopup()
  })

  mapRef.value = map
  emit('ready', map)
}

onMounted(init)

onBeforeUnmount(() => {
  disposed = true
  clearServiceLayers()
  stopDraw()
  mapRef.value?.setTarget(undefined)
  mapRef.value?.dispose()
  mapRef.value = null
})

watch(
  () => props.center,
  (c) => {
    mapRef.value?.getView().setCenter(fromLonLat(c))
  },
)
watch(
  () => props.zoom,
  (z) => {
    mapRef.value?.getView().setZoom(z)
  },
)
watch(
  () => [props.baseType, props.basemap],
  () => {
    const map = mapRef.value
    if (!map) return
    ;[...map.getLayers().getArray()].forEach((l) => {
      if ((l as any).get('kind') === 'basemap') map.removeLayer(l)
    })
    const bases = buildBase()
    bases.forEach((l, i) => map.getLayers().insertAt(i, l))
  },
)

/* ---------------- 对外方法 ---------------- */

/** 定位到经纬度范围 [minLon,minLat,maxLon,maxLat] */
function fitExtent(bbox: [number, number, number, number]) {
  const map = mapRef.value
  if (!map || !bbox.every(Number.isFinite) || bbox[0] >= bbox[2] || bbox[1] >= bbox[3]) return
  const ext = transformExtent(bbox, 'EPSG:4326', 'EPSG:3857')
  map.getView().fit(ext, { padding: [40, 40, 40, 40], maxZoom: 17, duration: 400 })
}

/** 挂载 WMS 图层（serverInterface 返回地址） */
function addWms(url: string, layers?: string, opts?: { transparent?: boolean; title?: string }) {
  const map = mapRef.value
  if (!map || !url) return null
  const layer = new TileLayer({
    source: new TileWMS({
      url,
      params: { LAYERS: layers || '', TILED: true, FORMAT: 'image/png', TRANSPARENT: opts?.transparent !== false },
      crossOrigin: 'anonymous',
    }),
    properties: { kind: 'service', title: opts?.title || layers || 'WMS' },
  })
  map.addLayer(layer)
  reportServiceError(layer.getSource(), 'WMS')
  return layer
}

/** 挂载单张 WMS 图片（不支持切片时） */
function addWmsImage(url: string, layers?: string) {
  const map = mapRef.value
  if (!map || !url) return null
  const layer = new ImageLayer({
    source: new ImageWMS({ url, params: { LAYERS: layers || '' }, crossOrigin: 'anonymous' }),
    properties: { kind: 'service' },
  })
  map.addLayer(layer)
  reportServiceError(layer.getSource(), 'WMS')
  return layer
}

/**
 * 挂载 WMTS 图层（GeoWebCache）。
 * serverPath 是单瓦片样例地址（含固定 tileMatrix/tileRow/tileCol），不可直接作模板，
 * 因此读取 GetCapabilities 能力文档后按图层构建；矩阵集优先取 URL 提示（EPSG:4326），
 * 失败时退回能力文档自动选择。异步：失败请由调用方提示（如跨域未开放）。
 */
async function addWmts(url: string, layerHint?: string): Promise<any> {
  const map = mapRef.value
  if (!map || !url) return null
  const generation = serviceGeneration
  const controller = new AbortController()
  serviceRequests.add(controller)
  const parsedUrl = new URL(url, window.location.href)
  const base = parsedUrl.origin + parsedUrl.pathname
  let layerName = layerHint || ''
  let matrixSetHint = ''
  try {
    const u = parsedUrl
    layerName = layerName || u.searchParams.get('layer') || u.searchParams.get('LAYER') || ''
    matrixSetHint = u.searchParams.get('tileMatrixSet') || u.searchParams.get('TILEMATRIXSET') || ''
  } catch {
    /* 保底使用 layerHint */
  }
  const capsUrl = `${base}?service=WMTS&request=GetCapabilities&version=1.0.0`
  try {
  const resp = await fetch(capsUrl, { signal: controller.signal })
  if (!resp.ok) throw new Error(`WMTS GetCapabilities 失败（HTTP ${resp.status}）`)
  const caps = new WMTSCapabilities().read(await resp.text())
  if (disposed || generation !== serviceGeneration || mapRef.value !== map) return null
  const tryOpts = (matrixSet?: string) =>
    optionsFromCapabilities(caps, {
      layer: layerName || undefined,
      ...(matrixSet ? { matrixSet } : {}),
    } as any)
  let options = tryOpts(matrixSetHint)
  if (!options && matrixSetHint) options = tryOpts()
  if (!options) throw new Error(`WMTS 能力文档中未找到图层 ${layerName || '(未知)'}`)
  const layer = new TileLayer({
    source: new WMTS({ ...(options as any), crossOrigin: 'anonymous' } as any),
    properties: { kind: 'service', title: layerName || 'WMTS' },
  })
  map.addLayer(layer)
  reportServiceError(layer.getSource(), 'WMTS')
  return layer
  } finally {
    serviceRequests.delete(controller)
  }
}

/** 清除所有服务图层（保留底图与绘制层） */
function clearServiceLayers() {
  serviceGeneration++
  serviceRequests.forEach((controller) => controller.abort())
  serviceRequests.clear()
  const map = mapRef.value
  if (!map) return
  const toRemove: any[] = []
  map.getLayers().forEach((l) => {
    if ((l as any).get('kind') === 'service') toRemove.push(l)
  })
  toRemove.forEach((l) => map.removeLayer(l))
}

/**
 * 挂载 MVT 矢量瓦片（GeoServer GWC WMTS 端点 + mapbox-vector-tile 格式）。
 * 入参为后端 getMVTInfo 返回的样例地址（含 SERVICE/VERSION/REQUEST/LAYER/TILEMATRIXSET 等参数），
 * 解析出基地址与 LAYER 后按 EPSG:4326 网格（origin 左上，TILEROW 自上而下）重组瓦片请求。
 */
function addMvt(sampleUrl: string): any {
  const map = mapRef.value
  if (!map || !sampleUrl) return null
  let base = sampleUrl.split('?')[0]
  let layer = ''
  try {
    const u = new URL(sampleUrl)
    layer = u.searchParams.get('LAYER') || u.searchParams.get('layer') || ''
  } catch {
    /* 保底：正则提取 */
    const m = /[?&]LAYER=([^&]+)/i.exec(sampleUrl)
    layer = m ? decodeURIComponent(m[1]) : ''
  }
  if (!layer) {
    throw new Error('MVT 样例地址中未解析到 LAYER 参数')
  }
  const tileUrl = (coord: number[]): string => {
    const z = coord[0]
    const x = coord[1]
    const y = coord[2]
    return (
      `${base}?SERVICE=WMTS&VERSION=1.1.0&REQUEST=GetTile` +
      `&LAYER=${encodeURIComponent(layer)}` +
      `&TILEMATRIXSET=EPSG:4326&TILEMATRIX=EPSG:4326:${z}` +
      `&TILECOL=${x}&TILEROW=${y}` +
      `&format=application/vnd.mapbox-vector-tile`
    )
  }
  const layer_ = new VectorTileLayer({
    source: new VectorTileSource({
      format: new MVT(),
      projection: 'EPSG:4326',
      tileGrid: createXYZ({ extent: [-180, -90, 180, 90], maxResolution: 180 / 256, maxZoom: 21 }),
      tileUrlFunction: tileUrl,
    }),
    properties: { kind: 'service', title: layer },
    style: (feature: any) => {
      const geomType = feature.getGeometry()?.getType()
      if (geomType === 'Polygon' || geomType === 'MultiPolygon') {
        return new Style({
          fill: new Fill({ color: 'rgba(51,136,255,0.18)' }),
          stroke: new Stroke({ color: '#3388ff', width: 1.2 }),
        })
      }
      if (geomType === 'LineString' || geomType === 'MultiLineString') {
        return new Style({ stroke: new Stroke({ color: '#e2721f', width: 2 }) })
      }
      return new Style({
        image: new OLCircle({ radius: 4, fill: new Fill({ color: '#e23b2e' }), stroke: new Stroke({ color: '#fff', width: 1 }) }),
      })
    },
    renderMode: 'hybrid',
  })
  map.addLayer(layer_)
  reportServiceError(layer_.getSource(), 'MVT')
  return layer_
}

/** 用 GeoJSON 预览矢量（支持 FeatureCollection / Feature / Geometry） */
function setGeojson(geojson: any) {
  previewSource.clear()
  if (!geojson) return
  try {
    const feats = new GeoJSON().readFeatures(geojson, {
      dataProjection: 'EPSG:4326',
      featureProjection: 'EPSG:3857',
    })
    feats.forEach((f) => {
      const name = f.get('name') || f.get('NAME') || f.get('layerName')
      if (name) f.setStyle((previewLayer.getStyle() as Style).clone())
      f.set('_label', name || '')
    })
    previewSource.addFeatures(feats)
    if (feats.length) {
      const ext = previewSource.getExtent() as any
      mapRef.value?.getView().fit(ext, { padding: [50, 50, 50, 50], maxZoom: 16, duration: 400 })
    }
  } catch (e) {
    console.warn('[Map2D] GeoJSON 解析失败', e)
  }
}

function clearPreview() {
  previewSource.clear()
}

/* ---------------- 点选高亮 + 属性气泡 ---------------- */

/** 高亮 WKT 几何（EPSG:4326），支持单条或数组 */
function highlightWkt(wkt: string | string[], fit = false) {
  const map = mapRef.value
  if (!map) return
  highlightSource.clear()
  const arr = Array.isArray(wkt) ? wkt : [wkt]
  const fmt = new WKT()
  let added = 0
  arr.forEach((w) => {
    const s = String(w || '').trim()
    if (!s) return
    try {
      const feat = fmt.readFeature(s, { dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3857' })
      if (feat) { highlightSource.addFeature(feat); added++ }
    } catch {
      /* 忽略无法解析的 WKT */
    }
  })
  if (added && fit) {
    const ext = highlightSource.getExtent()
    if (ext && ext.every((n) => Number.isFinite(n))) {
      map.getView().fit(ext, { padding: [50, 50, 50, 50], maxZoom: 16, duration: 400 })
    }
  }
}
function clearHighlight() {
  highlightSource.clear()
  hidePopup()
}
/** 气泡像素定位（含边界收拢） */
function positionPopup() {
  const map = mapRef.value
  if (!map || !popup.visible) return
  const px = map.getPixelFromCoordinate(fromLonLat([popup.lon, popup.lat]))
  if (!px) return
  const size = map.getSize()
  const maxL = Math.max(8, (size?.[0] ?? 800) - 320)
  const maxT = Math.max(8, (size?.[1] ?? 600) - 200)
  popup.left = Math.min(Math.max(8, px[0] + 12), maxL)
  popup.top = Math.min(Math.max(8, px[1] - 10), maxT)
}
/** 展示属性气泡：rows 为 [字段, 值] 列表 */
function showPopup(lon: number, lat: number, title: string, rows: [string, string][]) {
  popup.lon = lon
  popup.lat = lat
  popup.title = title || '属性'
  popup.rows = rows || []
  popup.visible = true
  positionPopup()
}
function hidePopup() {
  popup.visible = false
  popup.rows = []
}
/** 定位到 WKT 几何（高亮 + 视野适配） */
function locateWkt(wkt: string) {
  if (!wkt) return
  highlightWkt(wkt, true)
}

/** 当前视图中心点经纬度（缓冲查询用） */
function getCenter(): [number, number] | null {
  const map = mapRef.value
  if (!map) return null
  const c = map.getView().getCenter()
  if (!c) return null
  const [lon, lat] = toLonLat(c)
  return [lon, lat]
}

/** 开启绘制交互 */
function startDraw(kind: DrawKind) {
  const map = mapRef.value
  if (!map) return
  stopDraw()
  drawSource.clear()
  // OpenLayers 的 Draw 没有 'Box' 类型，用 Circle + createBox 几何函数表达框选
  drawInteraction = new Draw({
    source: drawSource,
    type: (kind === 'Box' ? 'Circle' : kind) as any,
    geometryFunction: kind === 'Box' ? createBox() : undefined,
  } as any)
  drawInteraction.on('drawend', (evt: any) => {
    suppressPickUntil = Date.now() + 500
    const geom = evt.feature.getGeometry()
    const fmt = new GeoJSON()
    let payload: any = { kind }
    if (kind === 'Point') {
      const c = geom.getCoordinates()
      const [lon, lat] = toLonLat(c)
      payload = { kind, lon, lat, wkt: `POINT(${lon} ${lat})` }
    } else if (kind === 'Circle') {
      const c = geom.getCenter()
      const [lon, lat] = toLonLat(c)
      const edge = toLonLat([c[0] + geom.getRadius(), c[1]])
      const radius = getDistance([lon, lat], edge)
      payload = { kind, lon, lat, radius, wkt: `POINT(${lon} ${lat})` }
    } else {
      const feat = fmt.writeFeatureObject(evt.feature, {
        dataProjection: 'EPSG:4326',
        featureProjection: 'EPSG:3857',
      })
      payload = { kind, wkt: geojsonToWkt(feat.geometry) }
    }
    emit('drawend', payload)
    stopDraw()
  })
  map.addInteraction(drawInteraction)
}

function stopDraw() {
  const map = mapRef.value
  if (map && drawInteraction) map.removeInteraction(drawInteraction)
  drawInteraction = null
}

/** 简化 GeoJSON Geometry → WKT（仅供查询参数使用） */
function geojsonToWkt(g: any): string {
  return geometryJsonToWkt(g)
}

defineExpose({
  getMap: () => mapRef.value,
  fitExtent,
  addWms,
  addWmsImage,
  addWmts,
  addMvt,
  clearServiceLayers,
  setGeojson,
  clearPreview,
  highlightWkt,
  clearHighlight,
  showPopup,
  hidePopup,
  locateWkt,
  getCenter,
  startDraw,
  stopDraw,
})
</script>

<template>
  <div class="ol-wrap">
    <div ref="wrapRef" class="ol-map"></div>
    <div v-if="basemap && !urlConfig.getTdtToken()" class="basemap-note">底图暂不可用，仍可查看已加载的空间数据</div>
    <div v-if="popup.visible" class="map-popup" :style="{ left: popup.left + 'px', top: popup.top + 'px' }">
      <div class="mp-head">
        <span class="ellip" :title="popup.title">{{ popup.title }}</span>
        <button class="mp-close" type="button" aria-label="关闭属性弹窗" title="关闭" @click="hidePopup"><Icon name="x" :size="14" /></button>
      </div>
      <div class="mp-body">
        <div v-for="([k, v], i) in popup.rows" :key="i" class="mp-row">
          <span class="k">{{ k }}</span>
          <span class="v" :title="v">{{ v }}</span>
        </div>
        <div v-if="!popup.rows.length" class="muted">无属性</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ol-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}
.ol-map {
  width: 100%;
  height: 100%;
  background: #eef2f7;
}
.basemap-note { position: absolute; bottom: 7px; left: 8px; max-width: calc(100% - 16px); padding: 4px 7px; background: #ffffffdc; border-radius: 4px; color: var(--ink-3); font-size: 11px; pointer-events: none; }
.ol-map :deep(.ol-control) {
  background: rgba(255, 255, 255, 0.85);
  border-radius: 5px;
}
.map-popup {
  position: absolute;
  z-index: 10;
  min-width: 170px;
  max-width: 300px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(15, 26, 38, 0.18);
  font-size: 12px;
  padding: 8px 10px;
}
.map-popup .mp-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  margin-bottom: 4px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--line);
}
.map-popup .mp-close {
  display: inline-grid;
  place-items: center;
  padding: 2px;
  border: 0;
  background: transparent;
  cursor: pointer;
  color: #98a4b3;
}
.map-popup .mp-close:hover {
  color: #ee4433;
}
.map-popup .mp-row {
  display: flex;
  gap: 8px;
  line-height: 1.7;
}
.map-popup .mp-row .k {
  color: #7c8794;
  flex: 0 0 auto;
}
.map-popup .mp-row .v {
  font-family: Consolas, Menlo, monospace;
  word-break: break-all;
}
.map-popup .ellip {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
