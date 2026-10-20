<script setup lang="ts">
/**
 * 服务预览（全屏区域弹层，对齐原型 overlay.area）
 *  - 按数据类型并行探测全部可用服务：矢量/白模 → WMS / WFS / MVT；栅格 → WMS / WMTS；三维 → 3DTiles
 *  - 侧栏列出所有服务：协议说明 + 地址 + 复制 + 「加载到地图」
 *  - 点击服务即切换挂载：WMS 直接渲染；WMTS 读 GetCapabilities 挂载；MVT 矢量瓦片；WFS 仅提供地址
 *  - 三维：Map3D 加载 3D Tiles（publish-url）
 */
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import Icon from '@/components/common/Icon.vue'
import Map2D from '@/components/map/Map2D.vue'
import Map3D from '@/components/map/Map3D.vue'
import { fetchAllSvcAddresses, SERVICE_PROTOCOLS, type UnifiedRow } from '@/utils/datasets'
import { copyText } from '@/utils/clipboard'

const props = defineProps<{ modelValue: boolean; row: UnifiedRow | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const close = () => emit('update:modelValue', false)

interface SvcItem {
  type: string
  url: string
}
const services = ref<SvcItem[]>([])
const activeType = ref('')
const addrLoading = ref(false)
const mountHint = ref('')
const map2dRef = ref<any>(null)
const is3d = computed(() => props.row?.src === 'm3d')
/** 图层名兜底（服务地址中缺少 layers 参数时使用） */
const wmsLayers = computed(() => props.row?.name || '')

/** 默认优先渲染的类型：WMS → WMTS → MVT → WFS（WFS 仅地址，不做默认） */
const DEFAULT_ORDER = ['WMS', 'WMTS', 'MVT', 'WFS', '3DTiles']
const activeSvc = computed(() => services.value.find((s) => s.type === activeType.value) || null)
const activeProtocol = computed(() => SERVICE_PROTOCOLS[activeType.value])

let mountToken = 0

async function load() {
  services.value = []
  activeType.value = ''
  mountHint.value = ''
  if (!props.row || !props.modelValue) return
  addrLoading.value = true
  try {
    services.value = await fetchAllSvcAddresses(props.row)
    const first = DEFAULT_ORDER.map((t) => services.value.find((s) => s.type === t)).find(Boolean)
    activeType.value = first?.type ?? services.value[0]?.type ?? ''
  } finally {
    addrLoading.value = false
  }
  // 地图在弹层渲染完成后挂载
  await nextTick()
  if (!is3d.value && activeType.value) mountActive()
}

/** 按当前选中的服务类型挂载 */
async function mountActive() {
  const map = map2dRef.value
  const svc = activeSvc.value
  if (!map || !svc || is3d.value) return
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
      mountHint.value = 'MVT 矢量瓦片已挂载（几何与属性保留在瓦片内，可动态换肤）；若空白请确认 GeoServer 已启用矢量瓦片格式。'
    } else if (svc.type === 'WFS') {
      mountHint.value = 'WFS 为要素服务，不做底图渲染；地址可用于要素查询与下载。'
    } else {
      // WMS（默认）：从完整请求地址解析基地址与 layers，避免重复参数（后端地址自带 bbox/width 等）
      const [base, layers] = splitWmsUrlSafe(svc.url)
      map.addWms(base, layers || wmsLayers.value, { title: wmsLayers.value })
      // 视图适配图层范围：从服务地址解析 bbox（后端已按图层自身范围生成），避免叠加层落在视口之外
      const bboxMatch = /[?&]bbox=([^&]+)/i.exec(svc.url)
      if (bboxMatch) {
        const nums = bboxMatch[1].split(',').map(Number)
        if (nums.length === 4 && nums.every((n) => Number.isFinite(n))) {
          map.fitExtent(nums as [number, number, number, number])
        }
      }
    }
  } catch (e: any) {
    if (token === mountToken) {
      mountHint.value = `${svc.type} 图层挂载失败：${e?.message || e}。可复制地址到支持该协议的客户端加载。`
    }
  }
}

/** 本地拆解 WMS 地址（避免 preview 组件反向依赖 splitWmsUrl 的默认导出路径） */
function splitWmsUrlSafe(url: string): [string, string] {
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
    return [base || url, m ? decodeURIComponent(m[1]) : '']
  }
}

/** 切换服务 */
async function switchService(type: string) {
  if (type === activeType.value) return
  activeType.value = type
  await nextTick()
  if (!is3d.value) mountActive()
}

watch(
  () => [props.modelValue, props.row?.key],
  () => load(),
  { immediate: true },
)

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
  return SERVICE_PROTOCOLS[type]?.params || []
}
function exampleOf(type: string): string {
  return SERVICE_PROTOCOLS[type]?.example || ''
}
/** 展开「请求参数与示例」的服务类型（同时只展开一个） */
const expandedType = ref('')
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
    <Transition name="pv-fade">
      <div v-if="modelValue" class="pv-overlay">
        <div class="pv-dialog">
          <div class="pv-head">
            <h3>服务预览 · {{ row?.name || '' }}</h3>
            <span v-if="services.length" class="pv-svc-count">共 {{ services.length }} 个可用服务</span>
            <button class="pv-close" @click="close"><Icon name="x" :size="16" /></button>
          </div>
          <div class="pv-body">
            <div class="pv-stage">
              <!-- 三维：Cesium 3D Tiles -->
              <Map3D v-if="row && is3d && activeSvc" :url="activeSvc.url" class="pv-map" />
              <!-- 二维：OpenLayers WMS / WMTS / MVT -->
              <Map2D v-else-if="row && !is3d && services.length" ref="map2dRef" class="pv-map" @ready="mountActive" />
              <!-- 无地址 / 加载中 -->
              <div v-else class="pv-empty">
                <template v-if="addrLoading">正在获取服务地址…</template>
                <template v-else>
                  <Icon name="warn" :size="30" />
                  <p>暂未获取到可加载的服务地址</p>
                  <p class="muted">
                    {{ row?.src === 'm3d' ? '三维数据需转换发布完成（状态「已发布」）后才有加载地址' : '请确认服务已通过 GeoServer 发布成功' }}
                  </p>
                </template>
              </div>
              <div v-if="mountHint" class="pv-hint">{{ mountHint }}</div>
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
              <div
                v-for="s in services"
                :key="s.type"
                class="svc-item"
                :class="{ active: s.type === activeType }"
                @click="switchService(s.type)"
              >
                <div class="svc-item-h">
                  <span class="svc-badge" :class="badgeClass(s.type)">{{ s.type }}</span>
                  <span class="svc-label">{{ labelOf(s.type) }}</span>
                  <span v-if="s.type === activeType" class="svc-on">加载中</span>
                </div>
                <div class="svc-desc">{{ descOf(s.type) }}</div>
                <div class="url-box" @click.stop>
                  <span :title="s.url">{{ s.url }}</span>
                  <b class="copy" @click.stop="copyAddr(s.url)">复制</b>
                </div>
                <div class="svc-detail">{{ detailOf(s.type) }}</div>
                <button class="svc-more" @click.stop="toggleExpand(s.type)">
                  {{ expandedType === s.type ? '收起参数说明' : '请求参数与示例 ▾' }}
                </button>
                <div v-if="expandedType === s.type" class="svc-params">
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
                  <div v-if="exampleOf(s.type)" class="svc-example">
                    <div class="svc-example-t">请求示例（相对 GeoServer 根地址）：</div>
                    <pre>{{ exampleOf(s.type) }}</pre>
                  </div>
                </div>
              </div>
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
  z-index: 300;
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
}
.pv-svc-count {
  font-size: 11.5px;
  color: var(--ink-3);
  background: #f2f6fb;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 2px 9px;
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
}
.pv-close:hover {
  color: var(--ink);
  background: var(--panel-2);
}
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
  pointer-events: none;
}
.pv-side {
  width: 380px;
  flex: 0 0 380px;
  border-left: 1px solid var(--line);
  background: #fff;
  padding: 16px 18px 22px;
  overflow: auto;
}
.panel-title {
  font-size: 12.5px;
  color: var(--ink-3);
  margin: 2px 0 8px;
  font-weight: 600;
}
.info-row {
  display: flex;
  font-size: 12.5px;
  padding: 7px 0;
  border-bottom: 1px dashed #eef1f6;
}
.info-row .k {
  color: var(--ink-3);
  flex: 0 0 76px;
}
.info-row .v {
  color: var(--ink-2);
  word-break: break-all;
}
.svc-item {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 9px 10px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.svc-item:hover {
  border-color: #b9d4ef;
  background: #f7fafe;
}
.svc-item.active {
  border-color: var(--primary, #1163b5);
  background: #eef5fd;
}
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
}
.svc-desc {
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
  background: none;
  border: none;
  padding: 4px 0 0;
  font-size: 11.5px;
  color: var(--primary, #1163b5);
  cursor: pointer;
}
.svc-more:hover {
  text-decoration: underline;
}
.svc-params {
  margin-top: 6px;
  border-top: 1px dashed var(--line);
  padding-top: 6px;
}
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
  transition: opacity 0.18s;
}
.pv-fade-enter-from,
.pv-fade-leave-to {
  opacity: 0;
}
</style>
