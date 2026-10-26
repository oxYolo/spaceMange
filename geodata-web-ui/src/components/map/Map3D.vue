<script setup lang="ts">
/** Cesium 地图：场景和数据各自拥有加载代次，关闭或切换后释放过期成果。 */
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { loadCesium } from '@/utils/cesium'
import urlConfig from '@/config'

const props = withDefaults(defineProps<{
  url?: string
  center?: [number, number, number]
  imagery?: boolean
}>(), {
  url: '', center: () => urlConfig.cesiumCenter as [number, number, number], imagery: true,
})
const emit = defineEmits<{
  (e: 'ready', viewer: any): void
  (e: 'loaded', tileset: any): void
  (e: 'error', err: any): void
}>()
const wrapRef = ref<HTMLDivElement>()
const viewerRef = shallowRef<any>(null)
const loading = ref(false)
const errMsg = ref('')
let disposed = false
let initGeneration = 0
let loadGeneration = 0
let activePrimitive: any = null

function alive(viewer: any): boolean {
  return !disposed && viewer === viewerRef.value && !viewer?.isDestroyed?.()
}
function destroyPrimitive(primitive: any, viewer?: any) {
  if (!primitive) return
  try {
    const removed = viewer && !viewer.isDestroyed?.() && viewer.scene.primitives.remove(primitive)
    if (!removed && !primitive.isDestroyed?.()) primitive.destroy?.()
  } catch { /* 已销毁的场景无需再次释放 */ }
}
function clearTiles() {
  loadGeneration++
  destroyPrimitive(activePrimitive, viewerRef.value)
  activePrimitive = null
  if (!disposed) loading.value = false
}
function destroyViewer() {
  clearTiles()
  try { if (viewerRef.value && !viewerRef.value.isDestroyed?.()) viewerRef.value.destroy() } catch { /* 已释放 */ }
  viewerRef.value = null
}
function tdtProvider(Cesium: any) {
  return new Cesium.WebMapTileServiceImageryProvider({
    url: `${urlConfig.tdtUrlHttps || urlConfig.tdtUrl}/DataServer?T=img_w&tk=${urlConfig.getTdtToken()}&x={TileCol}&y={TileRow}&l={TileMatrix}`,
    layer: 'img_w', style: 'default', format: 'tiles', tileMatrixSetID: 'w', maximumLevel: 18,
  })
}
function hasWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas')
    const context: any = canvas.getContext('webgl2') || canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
    context?.getExtension?.('WEBGL_lose_context')?.loseContext()
    return Boolean(context)
  } catch { return false }
}

async function init() {
  if (disposed) return
  const generation = ++initGeneration
  destroyViewer()
  loading.value = true
  errMsg.value = ''
  if (!hasWebGL()) {
    loading.value = false
    errMsg.value = '当前浏览器无法显示三维场景，请开启硬件加速后重试。'
    emit('error', new Error('WebGL unavailable'))
    return
  }
  try {
    const Cesium = await loadCesium()
    if (disposed || generation !== initGeneration || !wrapRef.value) return
    const viewer = new Cesium.Viewer(wrapRef.value, {
      animation: false, timeline: false, baseLayerPicker: false, geocoder: false,
      homeButton: false, sceneModePicker: false, navigationHelpButton: false,
      fullscreenButton: false, infoBox: false, selectionIndicator: false,
      shouldAnimate: false, imageryProvider: false, baseLayer: false,
    })
    viewerRef.value = viewer
    if (props.imagery && urlConfig.getTdtToken()) {
      try { viewer.imageryLayers.addImageryProvider(tdtProvider(Cesium)) } catch { /* 数据仍可在无底图场景显示 */ }
    }
    if (viewer.cesiumWidget && typeof viewer.cesiumWidget.showErrorPanel === 'function') {
      viewer.cesiumWidget.showErrorPanel = () => {
        if (!alive(viewer)) return
        errMsg.value = '三维场景显示异常，请重试或更换浏览器。'
      }
    }
    viewer.camera.setView({ destination: Cesium.Cartesian3.fromDegrees(...props.center) })
    emit('ready', viewer)
    loading.value = false
    if (props.url) await loadTileset(props.url)
  } catch (error: any) {
    if (disposed || generation !== initGeneration) return
    destroyViewer()
    errMsg.value = error?.message || '三维场景初始化失败'
    emit('error', error)
  }
}

const isGltfUrl = (url: string) => /\.(glb|gltf)(\?|#|$)/i.test(url)
async function loadTileset(url: string) {
  const viewer = viewerRef.value
  const Cesium = (window as any).Cesium
  if (!viewer || !Cesium || !alive(viewer)) return
  clearTiles()
  if (!url) return
  const generation = loadGeneration
  const current = () => alive(viewer) && generation === loadGeneration
  let candidate: any = null
  loading.value = true
  errMsg.value = ''
  try {
    if (isGltfUrl(url)) {
      candidate = Cesium.Model.fromGltfAsync
        ? await Cesium.Model.fromGltfAsync({ url }) : Cesium.Model.fromGltf({ url })
      if (!current()) { destroyPrimitive(candidate); return }
      const center = Cesium.Cartesian3.fromDegrees(props.center[0], props.center[1], 80)
      candidate.modelMatrix = Cesium.Transforms.eastNorthUpToFixedFrame(center)
      viewer.scene.primitives.add(candidate)
      activePrimitive = candidate
      if (candidate.readyPromise) await candidate.readyPromise
      if (!current()) return
      const sphere = candidate.boundingSphere
      if (sphere) {
        const worldCenter = Cesium.Matrix4.multiplyByPoint(candidate.modelMatrix, sphere.center, new Cesium.Cartesian3())
        const bounds = new Cesium.BoundingSphere(worldCenter, sphere.radius)
        viewer.camera.flyToBoundingSphere(bounds, {
          duration: 1.2, offset: new Cesium.HeadingPitchRange(0, -0.5, Math.max(bounds.radius * 3, 50)),
        })
      }
    } else {
      const Constructor = Cesium.Cesium3DTileset
      candidate = typeof Constructor.fromUrl === 'function' ? await Constructor.fromUrl(url) : new Constructor({ url })
      if (!current()) { destroyPrimitive(candidate); return }
      viewer.scene.primitives.add(candidate)
      activePrimitive = candidate
      if (candidate.readyPromise) await candidate.readyPromise
      if (!current()) return
      await viewer.zoomTo(candidate)
      if (!current()) return
    }
    emit('loaded', candidate)
  } catch (error: any) {
    if (!current()) return
    destroyPrimitive(candidate, viewer)
    activePrimitive = null
    errMsg.value = '三维数据暂时无法加载，请确认服务可访问后重试。'
    emit('error', error)
  } finally {
    if (current()) loading.value = false
  }
}
function retry() {
  if (loading.value) return
  if (viewerRef.value) void loadTileset(props.url)
  else void init()
}
function flyTo(lon: number, lat: number, height = 2000) {
  const viewer = viewerRef.value
  const Cesium = (window as any).Cesium
  if (!viewer || !Cesium || !alive(viewer)) return
  viewer.camera.flyTo({ destination: Cesium.Cartesian3.fromDegrees(lon, lat, height), duration: 1.2 })
}
onMounted(init)
onBeforeUnmount(() => { disposed = true; initGeneration++; destroyViewer() })
watch(() => props.url, (url, old) => {
  if (url !== old && viewerRef.value) void loadTileset(url)
})
defineExpose({ getViewer: () => viewerRef.value, loadTileset, clearTiles, flyTo, retry })
</script>

<template>
  <div class="cesium-wrap">
    <div ref="wrapRef" class="cesium-map"></div>
    <div v-if="loading" class="cesium-mask">三维场景加载中…</div>
    <div v-if="errMsg" class="cesium-err" role="alert">{{ errMsg }}<button type="button" :disabled="loading" @click="retry">重试</button></div>
  </div>
</template>

<style scoped>
.cesium-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  background: #0b1220;
}
.cesium-map {
  width: 100%;
  height: 100%;
}
.cesium-mask,
.cesium-err {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 12.5px;
}
.cesium-err button { display: block; margin: 9px auto 0; padding: 5px 14px; border: 1px solid #fff9; border-radius: 5px; background: transparent; color: #fff; cursor: pointer; }
.cesium-mask {
  background: rgba(16, 32, 52, 0.78);
  color: #dce8f5;
}
.cesium-err {
  background: rgba(207, 68, 56, 0.92);
  color: #fff;
  max-width: 78%;
  text-align: center;
}
.cesium-wrap :deep(.cesium-viewer-bottom) {
  display: none;
}
</style>
