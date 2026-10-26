<script setup lang="ts">
/**
 * 服务预览（全屏区域弹层，对齐原型 overlay.area）
 *  - 按数据类型并行探测全部可用服务：矢量/白模 → WMS / WFS / MVT；栅格 → WMS / WMTS；三维 → 3DTiles
 *  - 侧栏列出所有服务：协议说明 + 地址 + 复制 + 「加载到地图」
 *  - 点击服务即切换挂载：WMS 直接渲染；WMTS 读 GetCapabilities 挂载；MVT 矢量瓦片；WFS 仅提供地址
 *  - 三维：Map3D 加载 3D Tiles（publish-url）
 */
import { computed, defineAsyncComponent, h, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { showError } from '@/utils/feedback'
import { ElMessage } from 'element-plus'
import Icon from '@/components/common/Icon.vue'
import ServiceIntegrationPanel from '@/features/datasets/components/ServiceIntegrationPanel.vue'
import { fetchAllSvcAddresses, SERVICE_PROTOCOLS, splitWmsUrl, type UnifiedRow } from '@/utils/datasets'
import { copyText } from '@/utils/clipboard'

const props = defineProps<{ modelValue: boolean; row: UnifiedRow | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
const MapUnavailable = { render: () => h('div', { class: 'pv-empty', role: 'alert' }, [h('p', '地图组件暂不可用，请检查网络后重试'), h('button', { class: 'retry-btn', onClick: () => load(true) }, '重新加载')]) }
const MapLoading = { render: () => h('div', { class: 'pv-empty', role: 'status' }, '地图组件加载中…') }
const Map2D = defineAsyncComponent({ loader: () => import('@/components/map/Map2D.vue'), loadingComponent: MapLoading, errorComponent: MapUnavailable, timeout: 20000 })
const Map3D = defineAsyncComponent({ loader: () => import('@/components/map/Map3D.vue'), loadingComponent: MapLoading, errorComponent: MapUnavailable, timeout: 20000 })

const close = () => emit('update:modelValue', false)

interface SvcItem {
  type: string
  url: string
}
const services = ref<SvcItem[]>([])
const activeType = ref('')
const addrLoading = ref(false)
const mountHint = ref('')
const expandedType = ref('')
const map2dRef = ref<any>(null)
const dialogRef = ref<HTMLElement>()
const is3d = computed(() => props.row?.src === 'm3d')
/** 图层名兜底（服务地址中缺少 layers 参数时使用） */
const wmsLayers = computed(() => props.row?.name || '')

/** 默认优先渲染的类型：WMS → WMTS → MVT → WFS（WFS 仅地址，不做默认） */
const DEFAULT_ORDER = ['WMS', 'WMTS', 'MVT', 'WFS', '3DTiles']
const activeSvc = computed(() => services.value.find((s) => s.type === activeType.value) || null)
const activeProtocol = computed(() => SERVICE_PROTOCOLS[activeType.value])
const addressOnly = computed(() => activeType.value === 'WFS')

let mountToken = 0
let loadToken = 0
let disposed = false
let mountedKey = ''
let mountedMap: any = null
let previousFocus: HTMLElement | null = null
let previousOverflow: string | null = null

async function load(force = false) {
  const token = ++loadToken
  mountToken++
  mountedKey = ''
  services.value = []
  activeType.value = ''
  expandedType.value = ''
  mountHint.value = ''
  addrLoading.value = false
  const row = props.row
  if (!row || !props.modelValue || disposed) return
  const current = () => !disposed && token === loadToken && props.modelValue && props.row?.key === row.key
  addrLoading.value = true
  try {
    const addresses = await fetchAllSvcAddresses(row, { force })
    if (!current()) return
    services.value = addresses
    const first = DEFAULT_ORDER.map((t) => services.value.find((s) => s.type === t)).find(Boolean)
    activeType.value = first?.type ?? services.value[0]?.type ?? ''
  } catch (error) {
    if (!current()) return
    mountHint.value = '服务地址暂不可用，请稍后重试'
    showError(error, '服务地址读取失败')
  } finally {
    if (current()) addrLoading.value = false
  }
}

/** 按当前选中的服务类型挂载 */
async function mountActive() {
  const map = map2dRef.value
  const svc = activeSvc.value
  if (!map || !svc || is3d.value || !props.modelValue || addressOnly.value) return
  const key = `${props.row?.key}|${svc.type}|${svc.url}`
  if (key === mountedKey && map === mountedMap) return
  mountedKey = key
  mountedMap = map
  const token = ++mountToken
  map.clearServiceLayers()
  map.clearHighlight()
  mountHint.value = ''
  try {
    if (svc.type === 'WMTS') {
      // 图层名由地址中的 layer 参数解析，勿传数据名称（非 GeoServer 图层标识）
      await map.addWmts(svc.url)
    } else if (svc.type === 'MVT') {
      map.addMvt(svc.url)
      mountHint.value = '可缩放地图查看矢量细节。'
    } else if (svc.type === 'WFS') {
      mountHint.value = 'WFS 为要素服务，不做底图渲染；地址可用于要素查询与下载。'
    } else {
      // WMS（默认）：从完整请求地址解析基地址与 layers，避免重复参数（后端地址自带 bbox/width 等）
      const [base, layers] = splitWmsUrl(svc.url)
      map.addWms(base, layers || wmsLayers.value, { title: wmsLayers.value })
      // 视图适配图层范围：从服务地址解析 bbox（后端已按图层自身范围生成），避免叠加层落在视口之外
      const bboxMatch = /[?&]bbox=([^&]+)/i.exec(svc.url)
      if (bboxMatch) {
        const nums = decodeURIComponent(bboxMatch[1]).split(',').map(Number)
        if (nums.length === 4 && nums.every((n) => Number.isFinite(n))) {
          map.fitExtent(nums as [number, number, number, number])
        }
      }
    }
  } catch (e: any) {
    if (token === mountToken) {
      mountedKey = ''
      mountHint.value = `${svc.type} 图层挂载失败：${e?.message || e}。可复制地址到支持该协议的客户端加载。`
    }
  }
}

/** 切换服务 */
async function switchService(type: string) {
  if (type === activeType.value) return
  mountToken++
  mountedKey = ''
  map2dRef.value?.clearServiceLayers()
  activeType.value = type
  mountHint.value = ''
  await nextTick()
  if (!is3d.value) mountActive()
}

watch(
  () => [props.modelValue, props.row?.key],
  () => { void load() },
  { immediate: true, flush: 'post' },
)

function restorePage() {
  if (previousOverflow !== null) document.body.style.overflow = previousOverflow
  previousOverflow = null
  if (previousFocus?.isConnected) previousFocus.focus()
  previousFocus = null
}
watch(() => props.modelValue, async (open) => {
  if (open) {
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    await nextTick()
    if (props.modelValue && !disposed) dialogRef.value?.focus()
  } else restorePage()
}, { immediate: true })
onBeforeUnmount(() => { disposed = true; loadToken++; mountToken++; restorePage() })

function onServiceError(payload: { type: string; message: string }) {
  if (payload.type !== activeType.value) return
  mountHint.value = payload.message
  mountedKey = ''
}
function retryMap() { mountedKey = ''; void mountActive() }
function trapFocus(event: KeyboardEvent) {
  const controls = dialogRef.value?.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex="0"]')
  if (!controls?.length) return
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.value)) {
    event.preventDefault(); last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault(); first.focus()
  }
}

async function copyAddr(url: string) {
  if (!url) return
  ;(await copyText(url)) ? ElMessage.success('服务地址已复制') : ElMessage.error('复制失败，请手动选择地址')
}

/** 协议中文说明（侧栏展开用） */
function detailOf(type: string): string {
  return SERVICE_PROTOCOLS[type]?.detail || ''
}
function labelOf(type: string): string {
  return SERVICE_PROTOCOLS[type]?.label || type
}
function descOf(type: string): string {
  return SERVICE_PROTOCOLS[type]?.desc || ''
}
function paramsOf(type: string): { name: string; must: string; desc: string }[] {
  if (type === '3DTiles') return [{ name: 'url', must: '是', desc: '已发布三维成果的访问地址' }]
  return SERVICE_PROTOCOLS[type]?.params || []
}
/** 展开「请求参数与示例」的服务类型（同时只展开一个） */
function toggleExpand(type: string) {
  expandedType.value = expandedType.value === type ? '' : type
}
/** 协议徽标样式类（3DTiles 以 dtiles 承载，避开 CSS 类名不能以数字开头） */
function badgeClass(type: string): string {
  return ({ wms: 'wms', wmts: 'wmts', wfs: 'wfs', mvt: 'mvt', '3dtiles': 'dtiles' } as Record<string, string>)[
    String(type).toLowerCase()
  ] || 'wms'
}
</script>

<template>
  <Teleport to="body">
    <Transition name="pv-fade" appear>
      <div v-if="modelValue" class="pv-overlay" @click.self="close">
        <div ref="dialogRef" class="pv-dialog" role="dialog" aria-modal="true" aria-labelledby="service-preview-title" tabindex="-1" @keydown.esc.stop="close" @keydown.tab="trapFocus">
          <div class="pv-head">
            <h3 id="service-preview-title">服务预览 · {{ row?.name || '' }}</h3>
            <span v-if="services.length" class="pv-svc-count">共 {{ services.length }} 个可用服务</span>
            <button class="pv-close" type="button" aria-label="关闭服务预览" @click="close"><Icon name="x" :size="16" /></button>
          </div>
          <div class="pv-body">
            <div class="pv-stage">
              <!-- 三维：Cesium 3D Tiles -->
              <Map3D v-if="row && is3d && activeSvc" :key="row.key" :url="activeSvc.url" class="pv-map" />
              <!-- 二维：OpenLayers WMS / WMTS / MVT -->
              <Map2D v-else-if="row && !is3d && services.length && !addressOnly" ref="map2dRef" class="pv-map" @ready="mountActive" @service-error="onServiceError" />
              <!-- 无地址 / 加载中 -->
              <div v-else class="pv-empty">
                <template v-if="addrLoading">正在获取服务地址…</template>
                <template v-else-if="addressOnly">
                  <Icon name="file" :size="32" />
                  <p>WFS 要素查询服务</p>
                  <p class="muted">该协议用于查询与下载要素。可复制地址到 GIS 客户端，或切换地图协议预览。</p>
                </template>
                <template v-else>
                  <Icon name="warn" :size="30" />
                  <p>暂未获取到可加载的服务地址</p>
                  <p class="muted">
                    {{ row?.src === 'm3d' ? '请等待三维数据转换发布完成后重试' : '请确认数据已发布为服务后重试' }}
                  </p>
                  <button type="button" class="retry-btn" @click="load(true)">重新获取服务</button>
                </template>
              </div>
              <Transition name="pv-feedback" mode="out-in" appear>
                <div v-if="mountHint" :key="mountHint" class="pv-hint" role="status">{{ mountHint }}<button v-if="activeSvc" type="button" @click="retryMap">重新加载</button></div>
              </Transition>
            </div>

            <aside class="pv-side">
              <div class="panel-title">资源信息</div>
              <div class="info-row"><span class="k">名称</span><span class="v">{{ row?.name || '-' }}</span></div>
              <div class="info-row"><span class="k">数据类型</span><span class="v">{{ row?.typeLabel || '-' }}</span></div>
              <div class="info-row"><span class="k">数据格式</span><span class="v">{{ row?.format || '-' }}</span></div>
              <div class="info-row" style="border-bottom: none">
                <span class="k">当前状态</span><span class="v">{{ row?.statusLabel || '-' }}</span>
              </div>

              <div class="panel-title" style="margin-top: 14px">
                服务列表<span class="muted" style="font-weight: 400">（点击切换地图加载）</span>
              </div>
              <div v-if="!services.length && !addrLoading" class="hint">暂无可用服务</div>
              <section
                v-for="(s, index) in services"
                :key="s.type"
                class="svc-item"
                :class="{ active: s.type === activeType }"
                :style="{ '--svc-index': index }"
              >
                <button class="svc-select" type="button" :aria-pressed="s.type === activeType" :aria-label="`选择 ${s.type} 服务`" @click="switchService(s.type)">
                  <span class="svc-item-h">
                    <span class="svc-badge" :class="badgeClass(s.type)">{{ s.type }}</span>
                    <span class="svc-label">{{ labelOf(s.type) }}</span>
                    <span v-if="s.type === activeType" class="svc-on">当前选择</span>
                  </span>
                  <span class="svc-desc">{{ descOf(s.type) }}</span>
                </button>
                <div v-if="s.type !== activeType" class="url-box">
                  <span :title="s.url">{{ s.url }}</span>
                  <button type="button" class="copy" @click.stop="copyAddr(s.url)">复制</button>
                </div>
                <div class="svc-detail">{{ detailOf(s.type) }}</div>
                <ServiceIntegrationPanel v-if="s.type === activeType" :service="s" />
                <button class="svc-more" type="button" :aria-expanded="expandedType === s.type" @click.stop="toggleExpand(s.type)">
                  {{ expandedType === s.type ? '收起参数说明' : '请求参数说明' }}<Icon name="chevron-down" :size="13" :class="{ expanded: expandedType === s.type }" aria-hidden="true" />
                </button>
                <Transition name="pv-expand">
                  <div v-if="expandedType === s.type" class="svc-params">
                    <div class="svc-params-inner">
                      <table>
                        <thead>
                          <tr><th>参数</th><th>必填</th><th>说明</th></tr>
                        </thead>
                        <tbody>
                          <tr v-for="p in paramsOf(s.type)" :key="p.name">
                            <td class="mono">{{ p.name }}</td>
                            <td>{{ p.must }}</td>
                            <td>{{ p.desc }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                </Transition>
              </section>
            </aside>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.pv-overlay {
  position: fixed;
  inset: 0;
  z-index: 2200;
  background: rgba(20, 32, 52, 0.35);
  display: flex;
  align-items: stretch;
  justify-content: center;
  padding: 22px;
}
.pv-dialog {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 16px 48px rgba(15, 30, 60, 0.25);
  width: 100%;
  max-width: 1280px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  outline: none;
}
.pv-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
}
.pv-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  animation: pv-content-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) 40ms backwards;
}
.pv-svc-count {
  font-size: 11.5px;
  color: var(--ink-3);
  background: #f2f6fb;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 2px 9px;
  animation: pv-content-in var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) backwards;
}
.pv-close {
  margin-left: auto;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--ink-3);
  display: inline-flex;
  padding: 4px;
  border-radius: 5px;
  transition: color var(--motion-fast, 160ms), background var(--motion-fast, 160ms), transform var(--motion-fast, 160ms);
}
.pv-close:hover {
  color: var(--ink);
  background: var(--panel-2);
  transform: translateY(-1px);
}
.pv-close:active { transform: translateY(0); }
.pv-body {
  flex: 1;
  display: flex;
  min-height: 0;
}
.pv-stage {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef3f9;
}
.pv-map {
  width: 100%;
  height: 100%;
}
.pv-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--ink-2);
  font-size: 13px;
  text-align: center;
  padding: 30px;
  animation: pv-content-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) 80ms backwards;
}
.pv-hint {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  padding: 7px 10px;
  border-radius: 6px;
  background: rgba(255, 248, 234, 0.96);
  border: 1px solid #f3e0b0;
  color: #a9710d;
  font-size: 12px;
  line-height: 1.6;
  pointer-events: auto;
}
.retry-btn, .pv-hint button { border: 1px solid var(--line); border-radius: 5px; padding: 5px 10px; background: #fff; color: var(--primary); cursor: pointer; transition: background var(--motion-fast, 160ms), border-color var(--motion-fast, 160ms), box-shadow var(--motion-fast, 160ms), transform var(--motion-fast, 160ms); }
.retry-btn:hover, .pv-hint button:hover { background: #f3f8ff; border-color: #b8cfea; box-shadow: 0 3px 9px #17345c0c; transform: translateY(-1px); }
.retry-btn:active, .pv-hint button:active { transform: translateY(0); box-shadow: none; }
.retry-btn:disabled, .pv-hint button:disabled { cursor: wait; opacity: .6; transform: none; }
.pv-hint button { margin-left: 8px; font-size: 11px; }
.pv-empty p { margin: 3px 0; line-height: 1.7; max-width: 400px; }
.pv-side {
  width: 380px;
  flex: 0 0 380px;
  border-left: 1px solid var(--line);
  background: #fff;
  padding: 16px 18px 22px;
  overflow: auto;
  animation: pv-side-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) 50ms backwards;
}
.panel-title {
  font-size: 12.5px;
  color: var(--ink-3);
  margin: 2px 0 8px;
  font-weight: 600;
  animation: pv-content-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) 90ms backwards;
}
.info-row {
  display: flex;
  font-size: 12.5px;
  padding: 7px 0;
  border-bottom: 1px dashed #eef1f6;
  animation: pv-content-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) backwards;
}
.info-row:nth-child(2) { animation-delay: 100ms; }
.info-row:nth-child(3) { animation-delay: 125ms; }
.info-row:nth-child(4) { animation-delay: 150ms; }
.info-row:nth-child(5) { animation-delay: 175ms; }
.info-row .k {
  color: var(--ink-3);
  flex: 0 0 76px;
}
.info-row .v {
  color: var(--ink-2);
  word-break: break-all;
}
.svc-item {
  position: relative;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 9px 10px;
  margin-bottom: 8px;
  cursor: pointer;
  animation: pv-content-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) calc(120ms + var(--svc-index, 0) * 45ms) backwards;
  transition: border-color var(--motion-fast, 160ms), background var(--motion-base, 240ms), box-shadow var(--motion-base, 240ms), transform var(--motion-fast, 160ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1));
}
.svc-item::before { content: ''; position: absolute; left: -1px; top: 12px; bottom: 12px; width: 3px; border-radius: 0 3px 3px 0; background: var(--primary, #1163b5); opacity: 0; transform: scaleY(.6); transition: opacity var(--motion-base, 240ms), transform var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)); }
.svc-item:hover {
  border-color: #b9d4ef;
  background: #f7fafe;
  box-shadow: 0 4px 12px #17345c08;
  transform: translateY(-1px);
}
.svc-item.active {
  border-color: var(--primary, #1163b5);
  background: #eef5fd;
  box-shadow: 0 3px 12px #1163b50d;
}
.svc-item.active::before { opacity: 1; transform: scaleY(1); }
.svc-item:active { transform: translateY(0); }
.svc-select { display: block; width: 100%; padding: 0; border: 0; border-radius: 4px; background: none; text-align: left; cursor: pointer; }
.svc-select:focus-visible, .pv-close:focus-visible, .retry-btn:focus-visible, .pv-hint button:focus-visible, .svc-more:focus-visible, .copy:focus-visible { outline: 2px solid #79afe5; outline-offset: 3px; }
.svc-item-h {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 3px;
}
.svc-badge {
  font-size: 10.5px;
  font-weight: 700;
  color: #fff;
  background: #1163b5;
  border-radius: 4px;
  padding: 1px 6px;
  letter-spacing: 0.4px;
}
.svc-badge.wmts {
  background: #1a9c78;
}
.svc-badge.wfs {
  background: #8a6d3b;
}
.svc-badge.mvt {
  background: #7a4fc9;
}
.svc-badge.dtiles {
  background: #d06a1f;
}
.svc-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-1, #22314a);
}
.svc-on {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--primary, #1163b5);
  animation: pv-selection-in var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1));
}
.svc-desc {
  display: block;
  font-size: 11.5px;
  color: var(--ink-3);
  margin-bottom: 5px;
}
.svc-detail {
  font-size: 11px;
  color: #7c8794;
  line-height: 1.65;
  margin-top: 6px;
}
.svc-more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: none;
  border: none;
  padding: 4px 0 0;
  font-size: 11.5px;
  color: var(--primary, #1163b5);
  cursor: pointer;
}
.svc-more svg { transition: transform var(--motion-base, 240ms) var(--motion-ease, ease); }
.svc-more svg.expanded { transform: rotate(180deg); }
.svc-more:hover {
  text-decoration: underline;
}
.svc-params {
  display: grid;
  grid-template-rows: 1fr;
  margin-top: 6px;
  border-top: 1px dashed var(--line);
  padding-top: 6px;
}
.svc-params-inner { min-height: 0; overflow: hidden; }
.svc-params table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}
.svc-params th,
.svc-params td {
  border: 1px solid #eef1f6;
  padding: 3px 6px;
  text-align: left;
  vertical-align: top;
}
.svc-params th {
  background: #f6f8fc;
  color: var(--ink-3);
  font-weight: 600;
}
.svc-params td.mono {
  font-family: Consolas, Menlo, monospace;
  white-space: nowrap;
}
.svc-example {
  margin-top: 6px;
}
.svc-example-t {
  font-size: 11px;
  color: var(--ink-3);
  margin-bottom: 3px;
}
.svc-example pre {
  margin: 0;
  background: #0f1a26;
  color: #cfe3f7;
  border-radius: 5px;
  padding: 7px 9px;
  font-size: 10.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, Menlo, monospace;
}
.url-box {
  display: flex;
  gap: 6px;
  align-items: center;
  background: #f6f8fc;
  border: 1px solid var(--line);
  border-radius: 5px;
  padding: 5px 8px;
  font-size: 11.5px;
  color: #3c4b60;
  font-family: Consolas, Menlo, monospace;
}
.url-box span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.url-box .copy {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--primary, #1163b5);
  cursor: pointer;
  font-family: inherit;
  flex: 0 0 auto;
  font-weight: 600;
}
.url-box .copy:hover {
  text-decoration: underline;
}
.hint {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 6px;
  line-height: 1.6;
}
.pv-fade-enter-active,
.pv-fade-leave-active {
  transition: opacity var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1));
}
.pv-fade-enter-from,
.pv-fade-leave-to {
  opacity: 0;
}
.pv-feedback-enter-active, .pv-feedback-leave-active { transition: opacity var(--motion-fast, 160ms), transform var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)); }
.pv-feedback-enter-from, .pv-feedback-leave-to { opacity: 0; transform: translateY(6px); }
.pv-expand-enter-active, .pv-expand-leave-active { transition: grid-template-rows var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)), opacity var(--motion-fast, 160ms), margin var(--motion-base, 240ms), padding var(--motion-base, 240ms); }
.pv-expand-enter-from, .pv-expand-leave-to { grid-template-rows: 0fr; opacity: 0; margin-top: 0; padding-top: 0; }
@keyframes pv-content-in { from { opacity: 0; transform: translateY(7px); } to { opacity: 1; transform: translateY(0); } }
@keyframes pv-side-in { from { opacity: .35; } to { opacity: 1; } }
@keyframes pv-selection-in { from { opacity: 0; transform: translateX(4px); } to { opacity: 1; transform: translateX(0); } }
@media (max-width: 900px) {
  .pv-overlay { padding: 10px; }
  .pv-body { flex-direction: column; overflow: auto; }
  .pv-stage { flex: 1 0 42vh; min-height: 260px; }
  .pv-side { width: auto; flex: 0 0 auto; border-left: 0; border-top: 1px solid var(--line); }
  .pv-head { padding: 12px; }
  .pv-head h3 { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .pv-svc-count { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .pv-head h3, .pv-svc-count, .pv-side, .panel-title, .info-row, .svc-item, .svc-on, .pv-empty { animation: none; }
  .svc-item, .svc-item::before, .pv-close, .retry-btn, .pv-hint button, .pv-fade-enter-active, .pv-fade-leave-active, .pv-feedback-enter-active, .pv-feedback-leave-active, .pv-expand-enter-active, .pv-expand-leave-active { transition: none; }
  .svc-item:hover, .svc-item:active, .pv-close:hover, .pv-close:active, .retry-btn:hover, .retry-btn:active, .pv-hint button:hover, .pv-hint button:active, .pv-feedback-enter-from, .pv-feedback-leave-to { transform: none; }
  .pv-fade-enter-from, .pv-fade-leave-to, .pv-feedback-enter-from, .pv-feedback-leave-to { opacity: 1; }
}
</style>
