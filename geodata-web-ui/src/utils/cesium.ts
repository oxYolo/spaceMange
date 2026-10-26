/**
 * Cesium 按需加载器
 * 该 Cesium 为定制版（含 GGS 与扩展包），必须按顺序加载：
 *   Cesium.js → Cesium_GGS_min.js → Cesium_ext_min.js
 * 否则 new Cesium.Viewer() 会因缺少扩展类（如 Cesium.GeoBloom）而失败。
 * 仅在三维页面首次使用时注入，避免 4.4MB+ 脚本拖慢控制台其它页面。
 */
let loading: Promise<any> | null = null
let managed = false
const loadedScripts = new Set<string>()

const CSS_FILES = ['/lib/Cesium/Widgets/widgets.css', '/lib/Cesium/Widgets/widgets_ext_min.css']
const JS_FILES = [
  '/lib/Cesium/Cesium.js',
  '/lib/Cesium/Cesium_GGS_min.js',
  '/lib/Cesium/Cesium_ext_min.js',
]

function injectCss(href: string, id: string) {
  if (document.getElementById(id)) return
  const link = document.createElement('link')
  link.id = id
  link.rel = 'stylesheet'
  link.href = href
  document.head.appendChild(link)
}

function injectScript(src: string): Promise<void> {
  if (loadedScripts.has(src)) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = src
    s.async = false
    s.onload = () => { loadedScripts.add(src); resolve() }
    s.onerror = () => { s.remove(); reject(new Error('三维组件加载失败，请检查网络后重试')) }
    document.head.appendChild(s)
  })
}

export function loadCesium(): Promise<any> {
  if (loading) return loading
  if ((window as any).Cesium && !managed) return Promise.resolve((window as any).Cesium)
  managed = true

  loading = (async () => {
    ;(window as any).CESIUM_BASE_URL = '/lib/Cesium/'
    CSS_FILES.forEach((f, i) => injectCss(f, `cesium-css-${i}`))
    // 顺序加载，保证扩展包在 Cesium.js 之后注入
    for (const f of JS_FILES) {
      await injectScript(f)
    }
    if (!(window as any).Cesium) throw new Error('Cesium 加载完成但未挂载到 window.Cesium')
    return (window as any).Cesium
  })().catch((e) => {
    loading = null
    throw e
  })

  return loading
}
