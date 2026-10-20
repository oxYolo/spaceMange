<script setup lang="ts">
/**
 * 三维地图基座（Cesium）
 * - Cesium 静态库按需注入（public/lib/Cesium），不影响其它页面首屏
 * - 天地图影像底图 + 注记
 * - 3D Tiles 加载（直接使用 /model3d/{id}/publish-url 返回的公开地址）
 */
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { loadCesium } from '@/utils/cesium'
import urlConfig from '@/config'

const props = withDefaults(
  defineProps<{
    /** 3D Tiles 地址（tileset.json） */
    url?: string
    center?: [number, number, number]
    /** 是否显示天地图影像底图 */
    imagery?: boolean
  }>(),
  {
    url: '',
    center: () => urlConfig.cesiumCenter as [number, number, number],
    imagery: true,
  },
)

const emit = defineEmits<{
  (e: 'ready', viewer: any): void
  (e: 'loaded', tileset: any): void
  (e: 'error', err: any): void
}>()

const wrapRef = ref<HTMLDivElement>()
const viewerRef = shallowRef<any>(null)
const loading = ref(false)
const errMsg = ref('')

function tdtProvider(Cesium: any, kind: 'img' | 'cia' | 'vec' | 'cva') {
  return new Cesium.WebMapTileServiceImageryProvider({
    url: `${urlConfig.tdtUrl}/DataServer?T=${kind}_w&tk=${urlConfig.getTdtToken()}&x={TileMatrix}&y={TileRow}&l={TileCol}`,
    layer: `${kind}_w`,
    style: 'default',
    format: 'tiles',
    tileMatrixSetID: 'c',
    subdomains: ['t0', 't1', 't2', 't3', 't4', 't5', 't6', 't7'],
    maximumLevel: 18,
  })
}

/** WebGL 可用性预检：无 GPU / 无头环境直接给出友好提示，避免 Cesium 抛出原生错误面板 */
function hasWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas')
    return !!(
      (window as any).WebGLRenderingContext &&
      (canvas.getContext('webgl') || canvas.getContext('experimental-webgl'))
    )
  } catch {
    return false
  }
}

async function init() {
  loading.value = true
  errMsg.value = ''
  if (!hasWebGL()) {
    loading.value = false
    errMsg.value = '当前浏览器环境不支持 WebGL，无法渲染三维场景。请在开启硬件加速的浏览器中打开本页面。'
    emit('error', new Error('WebGL unavailable'))
    return
  }
  try {
    const Cesium = await loadCesium()
    const options: any = {
      animation: false,
      timeline: false,
      baseLayerPicker: false,
      geocoder: false,
      homeButton: false,
      sceneModePicker: false,
      navigationHelpButton: false,
      fullscreenButton: false,
      infoBox: false,
      selectionIndicator: false,
      shouldAnimate: false,
    }
    if (props.imagery) {
      try {
        options.imageryProvider = tdtProvider(Cesium, 'img')
      } catch (e) {
        /* 底图不可用时降级为无底图 */
      }
    }

    let viewer: any
    try {
      viewer = new Cesium.Viewer(wrapRef.value, options)
    } catch (e) {
      // 兼容新版 Cesium：baseLayer 取代 imageryProvider
      delete options.imageryProvider
      viewer = new Cesium.Viewer(wrapRef.value, options)
      if (props.imagery) {
        try {
          viewer.imageryLayers.addImageryProvider(tdtProvider(Cesium, 'img'))
        } catch (e2) {
          /* ignore */
        }
      }
    }

    // 隐藏版权信息条（可保留，按需开启）
    const credit = viewer.cesiumWidget?.creditContainer
    if (credit) credit.style.display = 'none'

    // 接管 Cesium 原生错误面板，统一由组件内提示
    if (viewer.cesiumWidget && typeof viewer.cesiumWidget.showErrorPanel === 'function') {
      viewer.cesiumWidget.showErrorPanel = (title: string, message?: any) => {
        errMsg.value = `${title || '三维渲染异常'}${message ? '：' + String(message) : ''}`
      }
    }

    viewer.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(props.center[0], props.center[1], props.center[2]),
    })

    viewerRef.value = viewer
    emit('ready', viewer)

    if (props.url) await loadTileset(props.url)
  } catch (e: any) {
    errMsg.value = e?.message || '三维场景初始化失败'
    emit('error', e)
  } finally {
    loading.value = false
  }
}

/** .glb/.gltf 单体模型走 Model 加载；其余按 3D Tiles（tileset.json）处理 */
const isGltfUrl = (url: string) => /\.(glb|gltf)(\?|#|$)/i.test(url)

async function loadTileset(url: string) {
  const viewer = viewerRef.value
  const Cesium = (window as any).Cesium
  if (!viewer || !Cesium || !url) return
  loading.value = true
  try {
    if (isGltfUrl(url)) {
      // 单体模型成果（obj2gltf / FBX2glTF / IfcConvert）。
      // Cesium 1.94 无 Model.fromGltfAsync，fromGltf 同步返回、异步就绪；
      // boundingSphere 需等 readyPromise 后才可读（就绪前为 undefined，静默跳过会导致相机不定位）
      const model: any = Cesium.Model.fromGltfAsync
        ? await Cesium.Model.fromGltfAsync({ url })
        : Cesium.Model.fromGltf({ url })
      // glb 源数据无地理坐标（局部坐标系），默认放到地图中心点的东北天坐标系，否则落在 ECEF 原点不可见
      const center = Cesium.Cartesian3.fromDegrees(props.center[0], props.center[1], 80)
      model.modelMatrix = Cesium.Transforms.eastNorthUpToFixedFrame(center)
      viewer.scene.primitives.add(model)
      // boundingSphere 为模型空间坐标，需乘 modelMatrix 得到世界坐标再定位
      const flyToModel = () => {
        try {
          const bs = model.boundingSphere
          if (!bs) return
          const worldCenter = Cesium.Matrix4.multiplyByPoint(
            model.modelMatrix, bs.center, new Cesium.Cartesian3(),
          )
          const worldBs = new Cesium.BoundingSphere(worldCenter, bs.radius)
          viewer.camera.flyToBoundingSphere(worldBs, {
            duration: 1.2,
            offset: new Cesium.HeadingPitchRange(0, -0.5, Math.max(worldBs.radius * 3, 50)),
          })
        } catch (e) {
          console.warn('[Map3D] glb 相机定位失败', e)
        }
      }
      if (model.readyPromise) {
        model.readyPromise.then(flyToModel).catch(() => flyToModel())
      } else {
        flyToModel()
      }
      emit('loaded', model)
    } else {
      // 兼容定制版 Cesium：老版本无 Cesium3DTileset.fromUrl 静态方法，走构造函数 + readyPromise
      const TilesetCtor: any = Cesium.Cesium3DTileset
      let tileset: any
      if (typeof TilesetCtor.fromUrl === 'function') {
        tileset = await TilesetCtor.fromUrl(url)
        viewer.scene.primitives.add(tileset)
      } else {
        tileset = new TilesetCtor({ url })
        viewer.scene.primitives.add(tileset)
        if (tileset.readyPromise) {
          try {
            await tileset.readyPromise
          } catch {
            /* 瓦片部分失败不阻断整体展示 */
          }
        }
      }
      await viewer.zoomTo(tileset)
      emit('loaded', tileset)
    }
  } catch (e: any) {
    errMsg.value = '三维数据加载失败：' + (e?.message || '地址不可达或格式不支持')
    emit('error', e)
  } finally {
    loading.value = false
  }
}

/** 清空所有 3D Tiles */
function clearTiles() {
  const viewer = viewerRef.value
  if (!viewer) return
  viewer.scene.primitives.removeAll()
}

/** 飞行定位 */
function flyTo(lon: number, lat: number, height = 2000) {
  const viewer = viewerRef.value
  const Cesium = (window as any).Cesium
  if (!viewer || !Cesium) return
  viewer.camera.flyTo({ destination: Cesium.Cartesian3.fromDegrees(lon, lat, height), duration: 1.2 })
}

onMounted(init)

onBeforeUnmount(() => {
  try {
    viewerRef.value?.destroy()
  } catch (e) {
    /* ignore */
  }
  viewerRef.value = null
})

watch(
  () => props.url,
  (v, old) => {
    if (!viewerRef.value) return
    if (v && v !== old) {
      clearTiles()
      loadTileset(v)
    } else if (!v) {
      clearTiles()
    }
  },
)

defineExpose({ getViewer: () => viewerRef.value, loadTileset, clearTiles, flyTo })
</script>

<template>
  <div class="cesium-wrap">
    <div ref="wrapRef" class="cesium-map"></div>
    <div v-if="loading" class="cesium-mask">三维场景加载中…</div>
    <div v-if="errMsg" class="cesium-err">{{ errMsg }}</div>
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
