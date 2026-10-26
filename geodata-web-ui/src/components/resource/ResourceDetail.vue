<script setup lang="ts">
import { showError } from '@/utils/feedback'
/**
 * 资源详情抽屉（6 页签 = 资源生命周期）
 * 概览 → 数据与要素 → 样式 → 服务与发布 → 元数据 → 活动
 * 后端依据：LayerContent / LayerField / MetaData / DataStyle / MapRelation / ServerInterface / LayerLog
 */
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/common/Icon.vue'
import Map2D from '@/components/map/Map2D.vue'
import {
  dataStyleApi,
  layerContentApi,
  layerFieldApi,
  layerLogApi,
  layerToolApi,
  mapRelationApi,
  metadataApi,
  serverInterfaceApi,
} from '@/api'
import { fmtSize, fmtTime, layerTypeName, pickList, pickTotal, parseBBox } from '@/utils/format'
import { getDatasetServiceState, invalidateDatasets, isRasterDataType, layerIngestionReady, normalizeLayer, SERVICE_PROTOCOLS } from '@/utils/datasets'
import { copyText } from '@/utils/clipboard'
import { useUserStore } from '@/auth/store'
import { createRequestScope } from '@/utils/requestScope'

const userStore = useUserStore()
function requireWriteSuccess<T>(result: T): T {
  if (result === false) throw new Error('操作未完成，请刷新后重试')
  return result
}

const props = defineProps<{
  modelValue: boolean
  layer: any
  initialTab?: string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'changed'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const tab = ref(props.initialTab || 'overview')
const requests = createRequestScope()
const panelErrors = reactive<Record<string, string>>({})
const fieldSaving = ref(false)
const attrDeleting = ref(false)
const styleSaving = ref(false)
const downloadBusy = ref(false)
const DATA_WRITE_REQUESTS = new Set(['addField', 'addFieldSync', 'removeField', 'removeAllFields', 'saveEditRecord', 'saveAddRecord', 'removeRecord', 'saveMultiEdit', 'saveFeatures', 'doPublish', 'bindStyleToLayer', 'unbindStyle'])
function beginResourceRequest(channel: string) {
  const id = String(props.layer?.id || '')
  if (!props.modelValue || !id) return null
  if (DATA_WRITE_REQUESTS.has(channel) && !layerIngestionReady(detail.value)) {
    ElMessage.info('入库待确认，请刷新状态或查看活动后再操作')
    return null
  }
  const request = requests.begin(channel)
  if (channel === 'attrs') panelsLoading.value = false
  delete panelErrors[channel]
  return { id, current: () => props.modelValue && String(props.layer?.id || '') === id && request.current() }
}
const TAB_REQUESTS: Record<string, string[]> = {
  overview: ['detail'], data: ['fields', 'attrs', 'raster'], schema: ['schema'],
  style: ['style', 'styleOptions', 'ramps'], service: ['services', 'relatedMaps'], meta: ['meta'], activity: ['logs'],
}
const activePanelError = computed(() => (TAB_REQUESTS[tab.value] || []).map((key) => panelErrors[key]).filter(Boolean).join('；'))
function readError(channel: string, error: unknown) {
  panelErrors[channel] = error instanceof Error ? error.message : '读取失败，请重试'
}
function retryActiveTab() { if (tab.value === 'overview') void refreshDetail(); loadActiveTab(tab.value) }
onBeforeUnmount(() => requests.dispose())

/** 打开抽屉时拉取最新详情（列表行数据可能已过期） */
async function refreshDetail() {
  const request = beginResourceRequest('detail')
  if (!request) return
  if (!props.layer?.id) return
  try {
    const id = request.id
    const d: any = await layerContentApi.getDetailById(id)
    if (!request.current()) return
    if (d && typeof d === 'object') detail.value = { ...props.layer, ...d }
    loadOverview()
  } catch {
    if (!request.current()) return
    readError("detail", new Error('读取失败，请重试'))
    /* 静默：保留行数据兜底 */
  }
}

const isRaster = computed(() => isRasterDataType(props.layer?.dataType))
const overviewMapRef = ref()
const attrMapRef = ref()
const detail = ref<any>({})
const dataReady = computed(() => layerIngestionReady(detail.value))

const bbox = computed(() => parseBBox(detail.value?.layerBBox || props.layer?.layerBBox))

function resetPanels() {
  detail.value = { ...(props.layer || {}) }
  fields.value = []
  attrs.value = []
  attrTotal.value = 0
  styleInfo.value = null
  styleBindId.value = ''
  meta.value = {}
  logs.value = []
  serviceInfo.value = null
  rasterInfo.value = null
  relatedMaps.value = []
  currentRow.value = null
  selectionRows.value = []
  attrColumns.value = []
  attrPage.index = attrSchema.page.index = 1
  attrSchema.fields = []; attrSchema.columns = []; attrSchema.rows = []; attrSchema.total = 0
  attrSchema.loading = panelsLoading.value = false
  svcList.value = []
  lastAttrQuery.value = {}
  whereRows.value = []
  whereVisible.value = editVisible.value = addVisible.value = multiVisible.value = layerEditVisible.value = featVisible.value = false
  whereLoading.value = editSaving.value = addSaving.value = multiSaving.value = layerSaving.value = featSaving.value = false
  publishLoading.value = styleBinding.value = metaSaving.value = false
  fieldSaving.value = attrDeleting.value = styleSaving.value = downloadBusy.value = false
  pixelProbe.lon = pixelProbe.lat = ''; pixelProbe.value = ''; pixelProbe.loading = false
  rasterStats.min = rasterStats.max = ''; rasterStats.values = []; rasterStats.loading = false; rasterStats.bandIndex = undefined
  renderState.attr = ''; renderState.resultKind = ''; renderState.values = []; renderState.breaks = []; renderState.attrList = []
  renderState.minmax = { min: '', max: '' }; renderState.loading = false; renderState.bandIndex = undefined
  for (const key of Object.keys(panelErrors)) delete panelErrors[key]
  attrMapRef.value?.clearHighlight()
}

/* ---------------- 数据与要素 ---------------- */
const fields = ref<any[]>([])
const attrs = ref<any[]>([])
const attrTotal = ref(0)
const attrPage = reactive({ size: 10, index: 1 })
const attrColumns = ref<string[]>([])
const panelsLoading = ref(false)
const spatialMode = ref<'Box' | 'Circle' | 'Polygon' | 'Point'>('Box')
const rasterInfo = ref<any>(null)

async function loadFields() {
  const request = beginResourceRequest('fields')
  if (!request) return
  if (!props.layer?.id) return
  try {
    // 裸返回 List<LayerFieldEntity>
    const readResult = pickList(await layerContentApi.getFieldByLayerId(request.id))
    if (!request.current()) return
    fields.value = readResult
  } catch (e: any) {
    if (!request.current()) return
    readError("fields", e)
    fields.value = []
  }
}

async function loadAttrs() {
  const request = beginResourceRequest('attrs')
  if (!request) return
  if (!props.layer?.id) return
  panelsLoading.value = true
  try {
    // 翻页保留当前条件，导出和页码始终对应同一组查询结果。
    if (Object.keys(lastAttrQuery.value).length) {
      const query = { ...lastAttrQuery.value }
      const res = query.where
        ? await layerContentApi.getLayerAttrTableByWhere(request.id, JSON.stringify(query.where), attrPage.size, attrPage.index)
        : await layerContentApi.pageAttrInfoBySwitch({ layerId: request.id, layerIds: [request.id], ...query }, attrPage.size, attrPage.index)
      if (!request.current()) return
      const rows = pickList<any>(res?.records ?? res)
      attrs.value = rows
      attrTotal.value = pickTotal(res, rows.length)
      if (rows[0]) attrColumns.value = attrColumnsOf(rows[0])
      currentRow.value = null
      selectionRows.value = []
      return
    }
    // 该端点的 items 实际为 JSON 字符串（每项内是记录数组），兼容对象数组返回。
    let res: any = null
    try {
      const readResult = await layerContentApi.getAttrtableByLayerId(request.id, attrPage.size, attrPage.index)
      if (!request.current()) return
      res = readResult
    } catch {
      if (!request.current()) return
      // 旧接口按固定 "Name" 字段去重，部分 SHP 没有该字段时改读通用属性表接口。
    }
    let rows = pickList<any>(res).flatMap((item) => {
      if (typeof item !== 'string') return item && typeof item === 'object' ? [item] : []
      try {
        const parsed = JSON.parse(item)
        return Array.isArray(parsed) ? parsed : Array.isArray(parsed?.data) ? parsed.data : []
      } catch { return [] }
    })
    let fallbackTotal = 0
    if (!rows.length) {
      const raw = await layerContentApi.getLayerAttrTabledDetail(request.id, attrPage.size, attrPage.index)
      if (!request.current()) return
      const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
      const data = typeof obj?.DataTableJson === 'string' ? JSON.parse(obj.DataTableJson) : obj?.DataTableJson
      rows = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : []
      fallbackTotal = Number(obj?.TotalCount ?? obj?.totalCount ?? 0)
    }
    attrs.value = rows
    attrTotal.value = fallbackTotal || pickTotal(res, rows.length)
    const first = rows[0]
    if (first && typeof first === 'object') attrColumns.value = Object.keys(first).filter((key) => !/^geom(new)?$/i.test(key))
    else if (fields.value.length) attrColumns.value = fields.value.map((f) => f.attributeName ?? f.fieldName ?? f.name)
  } catch (e: any) {
    if (!request.current()) return
    readError("attrs", e)
    attrs.value = []
    attrTotal.value = 0
  } finally {
    if (!request.current()) return
    panelsLoading.value = false
  }
}
function resetAttrQuery() {
  lastAttrQuery.value = {}
  attrPage.index = 1
  currentRow.value = null
  selectionRows.value = []
  attrMapRef.value?.clearHighlight()
  void loadAttrs()
}

/* ---------------- 原始属性表结构（GetLayerAttrTabledDetail） ---------------- */
const attrSchema = reactive({
  loading: false,
  fields: [] as string[],
  columns: [] as string[],
  rows: [] as any[],
  total: 0,
  page: { index: 1, size: 20 },
})
async function loadAttrTableDetail() {
  const request = beginResourceRequest('schema')
  if (!request) return
  if (!props.layer?.id) return
  attrSchema.loading = true
  try {
    // 裸返回 String：JSON 串 {LayerField:"a,b,c", DataTableJson:"{\"data\":[...]}"}
    const raw = await layerContentApi.getLayerAttrTabledDetail(
      request.id,
      attrSchema.page.size,
      attrSchema.page.index,
    )
    if (!request.current()) return
    const text = typeof raw === 'string' ? raw : ''
    let obj: any = null
    try {
      obj = text ? JSON.parse(text) : raw
    } catch {
      if (!request.current()) return
      obj = null
    }
    if (!obj || typeof obj !== 'object') throw new Error('属性表结构暂不可用，请重试')
    attrSchema.fields = String(obj.LayerField ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
    let dataArr: any[] = []
    try {
      const dt = typeof obj.DataTableJson === 'string' ? JSON.parse(obj.DataTableJson) : obj.DataTableJson
      dataArr = dt?.data ?? dt ?? []
    } catch {
      if (!request.current()) return
      dataArr = []
    }
    attrSchema.rows = Array.isArray(dataArr) ? dataArr : []
    attrSchema.columns = attrSchema.rows[0] ? Object.keys(attrSchema.rows[0]) : attrSchema.fields
    // 后端已回传 TotalCount（LayerContentServiceImpl getLayerAttrTabledDetail）；缺失时按当前页近似
    const totalCount = Number((obj as any).TotalCount ?? (obj as any).totalCount ?? 0)
    attrSchema.total = totalCount > 0
      ? totalCount
      : attrSchema.rows.length < attrSchema.page.size
        ? (attrSchema.page.index - 1) * attrSchema.page.size + attrSchema.rows.length
        : attrSchema.page.index * attrSchema.page.size + 1
  } catch {
    if (!request.current()) return
    readError("schema", new Error('读取失败，请重试'))
    attrSchema.fields = []
    attrSchema.columns = []
    attrSchema.rows = []
    attrSchema.total = 0
  } finally {
    if (!request.current()) return
    attrSchema.loading = false
  }
}

async function loadRasterInfo() {
  const request = beginResourceRequest('raster')
  if (!request) return
  if (!props.layer?.id) return
  try {
    const readResult = await layerToolApi.getTifInfo(request.id)
    if (!request.current()) return
    rasterInfo.value = readResult
  } catch {
    if (!request.current()) return
    readError("raster", new Error('读取失败，请重试'))
    rasterInfo.value = null
  }
}

/* ---------------- 栅格像元取值（getLayerValue） ---------------- */
const pixelProbe = reactive({
  loading: false,
  lon: '',
  lat: '',
  value: '' as any,
})
/**
 * 按经纬度读取栅格像元值：GET /layerContent/getLayerValue?layerId&longitude&latitude
 * 仅对 uploadFileType='2'（栅格）有效，其余返回 null。
 */
async function probeLayerValue() {
  if (pixelProbe.loading) return
  const request = beginResourceRequest('probeLayerValue')
  if (!request) return
  if (!props.layer?.id) return
  if (pixelProbe.lon === '' || pixelProbe.lat === '') {
    ElMessage.warning('请输入经度与纬度')
    return
  }
  pixelProbe.loading = true
  pixelProbe.value = ''
  try {
    const lon = Number(pixelProbe.lon)
    const lat = Number(pixelProbe.lat)
    if (!Number.isFinite(lon) || !Number.isFinite(lat) || lon < -180 || lon > 180 || lat < -90 || lat > 90) {
      ElMessage.warning('请输入有效经纬度：经度 -180～180，纬度 -90～90')
      return
    }
    const v: any = await layerContentApi.getLayerValue(request.id, lon, lat)
    if (!request.current()) return
    pixelProbe.value = v ?? null
    if (v == null) ElMessage.info('该坐标无像元值（或非栅格图层）')
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '取值失败')
  } finally {
    if (!request.current()) return
    pixelProbe.loading = false
  }
}

/* ---------------- 栅格像元统计 + 下载 TIF（原型：tifMaxMin / tifValues / downloadTif） ---------------- */
const rasterStats = reactive({
  loading: false,
  bandIndex: undefined as number | undefined,
  min: '' as any,
  max: '' as any,
  values: [] as any[],
})

/** 极值：GET /dataStyle/getTifMaxMin?id&bandIndex */
async function loadTifMaxMin() {
  if (rasterStats.loading) return
  const request = beginResourceRequest('loadTifMaxMin')
  if (!request) return
  if (!props.layer?.id) return
  rasterStats.loading = true
  try {
    const r: any = await dataStyleApi.getTifMaxMin(request.id, rasterStats.bandIndex)
    if (!request.current()) return
    rasterStats.min = r?.min ?? r?.MIN ?? '-'
    rasterStats.max = r?.max ?? r?.MAX ?? '-'
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '读取极值失败')
  } finally {
    if (!request.current()) return
    rasterStats.loading = false
  }
}

/** 唯一值：GET /dataStyle/getTifValues（像元值 > 50 时后端返回失败） */
async function loadTifValues() {
  if (rasterStats.loading) return
  const request = beginResourceRequest('loadTifValues')
  if (!request) return
  if (!props.layer?.id) return
  rasterStats.loading = true
  try {
    const r: any = await dataStyleApi.getTifValues(request.id, rasterStats.bandIndex)
    if (!request.current()) return
    rasterStats.values = pickList(r)
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '读取唯一值失败，请重试')
  } finally {
    if (!request.current()) return
    rasterStats.loading = false
  }
}

/** 下载栅格原始文件：GET /layerContent/downloadTif?layerId（注意小写 id） */
async function downloadTifFile() {
  if (downloadBusy.value) return
  const request = beginResourceRequest('downloadTifFile')
  if (!request) return
  downloadBusy.value = true
  try {
    if (!props.layer?.id) return
    try {
      const url = await layerContentApi.downloadTif(request.id)
      if (!request.current()) return
      if (url) window.open(String(url), '_blank')
      else ElMessage.info('暂未生成下载地址，请稍后重试')
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '下载失败')
    }

  } finally {
    if (request.current()) downloadBusy.value = false
  }
}

function startDraw(kind: 'Box' | 'Circle' | 'Polygon' | 'Point') {
  spatialMode.value = kind
  attrMapRef.value?.startDraw(kind)
}

async function onDrawEnd(payload: any) {
  const request = beginResourceRequest('attrs')
  if (!request) return
  attrPage.index = 1
  try {
    // 点选走独立端点：POST /layerContent/pageAttrInfoByPoint（distance 单位：米）
    if (payload.kind === 'Point') {
      const ptWkt = `POINT(${payload.lon} ${payload.lat})`
      const res = await layerContentApi.pageAttrInfoByPoint({
        layerId: request.id,
        layerIds: [request.id],
        lon: payload.lon ?? 0,
        lat: payload.lat ?? 0,
        distance: pickTolerance.value,
      })
      if (!request.current()) return
      const first = Array.isArray(res) ? res[0] : res
      const rows = pickList<any>(first?.records ?? first)
      attrs.value = rows
      attrTotal.value = pickTotal(first, rows.length)
      if (rows[0]) attrColumns.value = attrColumnsOf(rows[0])
      if (rows[0]) currentRow.value = rows[0]
      const wkt = geomWktOf(rows[0])
      if (wkt) attrMapRef.value?.highlightWkt(wkt)
      lastAttrQuery.value = { wkt: ptWkt, distance: pickTolerance.value, lon: payload.lon, lat: payload.lat }
      ElMessage.success(`点选命中 ${attrTotal.value} 条`)
      return
    }
    const query: Record<string, any> = {
      layerId: request.id,
      where: [],
      wkt: payload.wkt || '',
      distance: 0,
      lon: payload.lon ?? 0,
      lat: payload.lat ?? 0,
      layerIds: [request.id],
    }
    if (payload.kind === 'Circle' && payload.radius) {
      query.distance = Math.round(payload.radius)
      // 圈选 = 点 + 半径（米）缓冲查询：后端按 POINT WKT + distance 路由到 ST_DWithin(geography)
      query.wkt = `POINT(${query.lon} ${query.lat})`
    }
    const res = await layerContentApi.pageAttrInfoBySwitch(query, attrPage.size, attrPage.index)
    if (!request.current()) return
    const rows = pickList<any>(res?.records ?? res)
    attrs.value = rows
    attrTotal.value = pickTotal(res, rows.length)
    if (rows[0]) attrColumns.value = attrColumnsOf(rows[0])
    lastAttrQuery.value = {
      wkt: query.wkt,
      distance: query.distance || undefined,
      lon: query.lon ?? undefined,
      lat: query.lat ?? undefined,
    }
    ElMessage.success(`空间查询命中 ${attrTotal.value} 条`)
  } catch (e: any) {
    if (!request.current()) return
    readError("attrs", e)
    showError(e, '空间查询失败')
  }
}

/** 缓冲查询：以当前视图中心为圆心、按输入半径（米）做圆形缓冲查询 */
async function bufferQuery() {
  const request = beginResourceRequest('attrs')
  if (!request) return
  attrPage.index = 1
  const center = attrMapRef.value?.getCenter?.()
  if (!center) {
    ElMessage.warning('地图尚未就绪')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('输入缓冲半径（米）', '缓冲查询', { inputValue: '500' })
    if (!request.current()) return
    const radius = Number(String(value || '').trim())
    if (!Number.isFinite(radius) || radius <= 0) {
      ElMessage.warning('半径须为正数')
      return
    }
    const res = await layerContentApi.pageAttrInfoBySwitch(
      {
        layerId: request.id,
        layerIds: [request.id],
        wkt: `POINT(${center[0]} ${center[1]})`,
        distance: radius,
        lon: center[0],
        lat: center[1],
      },
      attrPage.size,
      attrPage.index,
    )
    if (!request.current()) return
    const rows = pickList<any>(res?.records ?? res)
    attrs.value = rows
    attrTotal.value = pickTotal(res, rows.length)
    if (rows[0]) attrColumns.value = attrColumnsOf(rows[0])
    lastAttrQuery.value = { wkt: `POINT(${center[0]} ${center[1]})`, distance: radius, lon: center[0], lat: center[1] }
    ElMessage.success(`缓冲查询（${radius} 米）命中 ${attrTotal.value} 条`)
  } catch (e: any) {
    if (!request.current()) return
    readError("attrs", e)
    if (e !== 'cancel' && e !== 'close') showError(e, '缓冲查询失败')
  }
}

/* ---------------- 地图点击查属性（气泡 + 高亮） ---------------- */
/** 点选容差（米）：pageAttrInfoByPoint 的 distance 按米计算 */
const pickTolerance = ref(10)
/** 当前生效的查询条件（导出时携带，保证「导出查询」导的是筛选结果而非全表） */
const lastAttrQuery = ref<Record<string, any>>({})

/** 取几何 WKT（点选查询返回 geomnew；普通查询可能带 geom） */
function geomWktOf(row: any): string {
  if (!row) return ''
  return String(row.geomnew ?? row.geom ?? row.the_geom ?? row.shape ?? '').trim()
}
/** 属性列：剔除几何列，避免表格刷屏 */
function attrColumnsOf(row: any): string[] {
  return Object.keys(row).filter((c) => !/^(geom|the_geom|shape|geomnew)$/i.test(c))
}

/** 地图单击：矢量走点选查询出属性气泡；栅格读像元值 */
async function onMapPick(p: { lon: number; lat: number }) {
  const request = beginResourceRequest('attrs')
  if (!request) return
  attrPage.index = 1
  if (!props.layer?.id) return
  if (isRaster.value) {
    try {
      const v: any = await layerContentApi.getLayerValue(request.id, p.lon, p.lat)
      if (!request.current()) return
      attrMapRef.value?.showPopup(p.lon, p.lat, props.layer?.layerName || '栅格取值', [
        ['经度', String(p.lon)],
        ['纬度', String(p.lat)],
        ['像元值', v == null ? '无值（非栅格或坐标越界）' : String(v)],
      ])
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '取值失败')
    }
    return
  }
  try {
    const res = await layerContentApi.pageAttrInfoByPoint({
      layerId: request.id,
      layerIds: [request.id],
      lon: p.lon,
      lat: p.lat,
      distance: pickTolerance.value,
    })
    if (!request.current()) return
    const first = Array.isArray(res) ? res[0] : res
    const rows = pickList<any>(first?.records ?? first)
    if (!rows.length) {
      attrMapRef.value?.hidePopup()
      ElMessage.info('该位置未命中要素（可调大点选容差）')
      return
    }
    attrs.value = rows
    attrTotal.value = pickTotal(first, rows.length)
    attrColumns.value = attrColumnsOf(rows[0])
    currentRow.value = rows[0]
    lastAttrQuery.value = { wkt: `POINT(${p.lon} ${p.lat})`, distance: pickTolerance.value, lon: p.lon, lat: p.lat }
    const wkt = geomWktOf(rows[0])
    if (wkt) attrMapRef.value?.highlightWkt(wkt)
    const kv: [string, string][] = Object.entries(rows[0])
      .filter(([k]) => !/^(geom|the_geom|shape|geomnew)$/i.test(k))
      .slice(0, 12)
      .map(([k, v]) => [k, v == null ? '' : String(v)])
    attrMapRef.value?.showPopup(p.lon, p.lat, props.layer?.layerName || '要素属性', kv)
  } catch (e: any) {
    if (!request.current()) return
    readError("attrs", e)
    showError(e, '点选查询失败')
  }
}

/** 表格行定位：高亮并视野适配（依赖行内几何 WKT） */
function locateCurrentRow() {
  const wkt = geomWktOf(currentRow.value)
  if (!wkt) {
    ElMessage.warning('所选行不含几何信息（分页列表不带几何，请用点选/空间查询结果定位）')
    return
  }
  attrMapRef.value?.locateWkt(wkt)
}

async function exportAttr() {
  if (downloadBusy.value) return
  const request = beginResourceRequest('exportAttr')
  if (!request) return
  downloadBusy.value = true
  try {
    try {
      // 携带当前生效的查询条件：导出的是筛选结果，而非全表
      const url = await layerContentApi.downloadAttrInfoBySwitch({
        layerId: request.id,
        layerIds: [request.id],
        ...lastAttrQuery.value,
      })
      if (!request.current()) return
      if (url) window.open(String(url), '_blank')
      else ElMessage.info('暂未生成下载地址，请稍后重试')
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '导出失败')
    }

  } finally {
    if (request.current()) downloadBusy.value = false
  }
}

async function exportShp() {
  if (downloadBusy.value) return
  const request = beginResourceRequest('exportShp')
  if (!request) return
  downloadBusy.value = true
  try {
    try {
      const p = await layerContentApi.outputShpFileData(request.id)
      if (!request.current()) return
      ElMessage.success('导出成功：' + p)
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '导出失败')
    }

  } finally {
    if (request.current()) downloadBusy.value = false
  }
}

/** 导出 TIF：GET /layerContent/OutputTifFileData?layerID */
async function exportTif() {
  if (downloadBusy.value) return
  const request = beginResourceRequest('exportTif')
  if (!request) return
  downloadBusy.value = true
  try {
    try {
      const p = await layerContentApi.outputTifFileData(request.id)
      if (!request.current()) return
      ElMessage.success('导出成功：' + p)
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '导出失败')
    }

  } finally {
    if (request.current()) downloadBusy.value = false
  }
}

/** 字段名列取值（后端 LayerFieldEntity.attributeName） */
function fieldNameOf(row: any): string {
  return String(row?.attributeName ?? row?.fieldName ?? row?.columnName ?? row?.name ?? '')
}

/** 空间查询/记录更新依赖 gid 与几何列；普通业务字段 id 不在保留名单内。 */
function isSystemField(row: any): boolean {
  return /^(gid|geom|geomnew|the_geom)$/i.test(fieldNameOf(row).trim())
}
function canDeleteField(row: any): boolean {
  return dataReady.value && !fieldSaving.value && Boolean(row?.id) && !isSystemField(row)
}
function canClearFields(): boolean {
  return dataReady.value && !fieldSaving.value && fields.value.length > 0 && !fields.value.some(isSystemField)
}
function fieldDeleteHint(row: any): string {
  if (isSystemField(row)) return '系统字段用于要素定位或空间查询，无法删除'
  if (!row?.id) return '该字段缺少定义标识，暂不可删除'
  return dataReady.value ? '删除业务字段' : '入库待确认'
}

/** 新增字段（LayerField/Insert，Body: LayerFieldEntity{layerID, attributeName, attributeType}） */
async function addField() {
  if (fieldSaving.value) return
  const request = beginResourceRequest('addField')
  if (!request) return
  fieldSaving.value = true
  try {
    try {
      const { value } = await ElMessageBox.prompt('输入新增字段名', '新增字段')
      if (!request.current()) return
      if (!value) return
      requireWriteSuccess(await layerFieldApi.insert({
        layerID: request.id,
        attributeName: value,
        attributeType: '1',
      }))
      if (!request.current()) return
      ElMessage.success('字段已新增')
      loadFields()
    } catch (e: any) {
      if (!request.current()) return
      if (e !== 'cancel' && e !== 'close') showError(e, '新增字段失败')
    }

  } finally {
    if (request.current()) fieldSaving.value = false
  }
}

/** 新增字段并同步落库（LayerField/InsertSync，会在属性表执行 ALTER TABLE） */
async function addFieldSync() {
  if (fieldSaving.value) return
  const request = beginResourceRequest('addFieldSync')
  if (!request) return
  fieldSaving.value = true
  try {
    try {
      const { value } = await ElMessageBox.prompt(
        '输入新增字段名（将同步修改属性表结构）',
        '新增字段并同步',
      )
      if (!request.current()) return
      if (!value) return
      requireWriteSuccess(await layerFieldApi.insertSync({
        layerID: request.id,
        attributeName: value,
        attributeType: '1',
      }))
      if (!request.current()) return
      ElMessage.success('字段已新增并同步至属性表')
      loadFields()
      loadAttrs()
    } catch (e: any) {
      if (!request.current()) return
      if (e !== 'cancel' && e !== 'close') showError(e, '同步新增字段失败')
    }

  } finally {
    if (request.current()) fieldSaving.value = false
  }
}

/** 删除单个字段（LayerField/Delete? id） */
async function removeField(row: any) {
  if (fieldSaving.value) return
  if (isSystemField(row)) return ElMessage.info('系统字段用于要素定位或空间查询，无法删除')
  const request = beginResourceRequest('removeField')
  if (!request) return
  fieldSaving.value = true
  try {
    const id = String(row?.id ?? '')
    if (!id) {
      ElMessage.warning('该字段缺少 id，无法删除')
      return
    }
    try {
      await ElMessageBox.confirm(`确认删除字段「${fieldNameOf(row)}」？`, '删除字段', { type: 'warning' })
      if (!request.current()) return
    } catch {
      if (!request.current()) return
      return
    }
    try {
      requireWriteSuccess(await layerFieldApi.remove(id))
      if (!request.current()) return
      ElMessage.success('字段已删除')
      loadFields()
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '删除字段失败')
    }

  } finally {
    if (request.current()) fieldSaving.value = false
  }
}

/** 清空该图层全部字段定义（LayerField/DeleteByLayerID? layerID） */
async function removeAllFields() {
  if (!canClearFields()) {
    if (fields.value.some(isSystemField)) ElMessage.info('当前包含系统字段，请逐项删除业务字段')
    return
  }
  if (fieldSaving.value) return
  const request = beginResourceRequest('removeAllFields')
  if (!request) return
  fieldSaving.value = true
  try {
    try {
      await ElMessageBox.confirm('确认清空该图层的全部字段定义？', '清空字段', { type: 'warning' })
      if (!request.current()) return
    } catch {
      if (!request.current()) return
      return
    }
    try {
      requireWriteSuccess(await layerFieldApi.deleteByLayerID(request.id))
      if (!request.current()) return
      ElMessage.success('字段定义已清空')
      loadFields()
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '清空失败')
    }

  } finally {
    if (request.current()) fieldSaving.value = false
  }
}

/* ---------------- 条件筛选（原型 attrWhere → GetLayerAttrTableByWhere，先 ValidateCondition） ---------------- */
const WHERE_OPS = ['=', '<>', '>', '<', '>=', '<=', 'like', 'in']
const whereVisible = ref(false)
const whereLoading = ref(false)
const whereRows = ref<{ field: string; op: string; value: string }[]>([])

function firstFieldName(): string {
  return String(fields.value[0]?.attributeName ?? fields.value[0]?.fieldName ?? attrColumns.value[0] ?? 'gid')
}
function openWhere() {
  if (!whereRows.value.length) whereRows.value.push({ field: firstFieldName(), op: '=', value: '' })
  whereVisible.value = true
}
function addWhereRow() {
  whereRows.value.push({ field: firstFieldName(), op: '=', value: '' })
}
function removeWhereRow(i: number) {
  whereRows.value.splice(i, 1)
}

async function runWhereQuery() {
  if (whereLoading.value) return
  const request = beginResourceRequest('attrs')
  if (!request) return
  const conds = whereRows.value
    .filter((r) => r.field && r.value !== '')
    .map((r) => ({ field: r.field, operator: r.op, value: r.value }))
  if (!conds.length) {
    ElMessage.warning('请至少填写一条完整条件')
    return
  }
  whereLoading.value = true
  try {
    // 先校验条件合法性：POST /layerContent/ValidateCondition（裸返回 boolean）
    const ok = await layerContentApi.validateCondition(request.id, conds)
    if (!request.current()) return
    if (ok === false) {
      ElMessage.error('查询条件无效，请检查字段、运算符和取值')
      return
    }
    // where 为 JSON 串：GET /layerContent/GetLayerAttrTableByWhere（裸返回分页）
    const res = await layerContentApi.getLayerAttrTableByWhere(
      request.id,
      JSON.stringify(conds),
      attrPage.size,
      1,
    )
    if (!request.current()) return
    const rows = pickList<any>(res)
    attrs.value = rows
    attrTotal.value = pickTotal(res, rows.length)
    attrPage.index = 1
    if (rows[0]) attrColumns.value = Object.keys(rows[0])
    lastAttrQuery.value = { where: conds }
    whereVisible.value = false
    ElMessage.success(`条件筛选命中 ${attrTotal.value} 条`)
  } catch (e: any) {
    if (!request.current()) return
    readError("attrs", e)
    showError(e, '条件筛选失败')
  } finally {
    if (!request.current()) return
    whereLoading.value = false
  }
}

/* ---------------- 编辑记录（原型 attrUpdate → UpdateAttrtable，Body: LayerParameter{id, attrtable:JSON串}） ---------------- */
const currentRow = ref<any>(null)
/** 表格多选行（供批量修改用） */
const selectionRows = ref<any[]>([])
function onAttrSelectionChange(rows: any[]) {
  selectionRows.value = rows
}
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = ref<Record<string, any>>({})
const editableColumns = computed(() =>
  attrColumns.value.filter((c) => !/^(gid|id|geom|geomnew|the_geom|shape)$/i.test(c)),
)

function openEditRecord() {
  if (!currentRow.value) {
    ElMessage.warning('请先在属性表中点选一行记录')
    return
  }
  editForm.value = { ...currentRow.value }
  editVisible.value = true
}

async function saveEditRecord() {
  if (editSaving.value) return
  const request = beginResourceRequest('saveEditRecord')
  if (!request) return
  if (!rowGid(editForm.value)) {
    ElMessage.warning('该记录缺少 gid，无法更新')
    return
  }
  editSaving.value = true
  try {
    requireWriteSuccess(await layerContentApi.updateAttrtable({
      id: request.id,
      attrtable: JSON.stringify([editForm.value]),
    }))
    if (!request.current()) return
    ElMessage.success('记录已保存')
    editVisible.value = false
    loadAttrs()
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '保存失败')
  } finally {
    if (!request.current()) return
    editSaving.value = false
  }
}

/* ---------------- 新增记录（InsertAttrtable，Body: LayerParameter{id, attrtable}）
 * 后端实现要点：attrtable 是「JSON 行字符串数组」的序列化文本，每行是 {字段名:值, geom:'WKT'}，
 * row.get("geom") 必取（缺失会抛），后续按图层字段定义取交集生成 INSERT。故 geom 为必填。
 * ---------------- */
const addVisible = ref(false)
const addSaving = ref(false)
const addForm = ref<Record<string, any>>({})

function openAddRecord() {
  const blank: Record<string, any> = {}
  editableColumns.value.forEach((c) => (blank[c] = ''))
  addForm.value = blank
  addVisible.value = true
}

async function saveAddRecord() {
  if (addSaving.value) return
  const request = beginResourceRequest('saveAddRecord')
  if (!request) return
  const geom = String(addForm.value.geom ?? '').trim()
  if (!geom) {
    ElMessage.warning('几何字段 geom 必填（WKT，如 POINT(114.1 29.7)）')
    return
  }
  const rowObj: Record<string, any> = { geom }
  editableColumns.value.forEach((c) => {
    if (addForm.value[c] !== '' && addForm.value[c] != null) rowObj[c] = addForm.value[c]
  })
  addSaving.value = true
  try {
    requireWriteSuccess(await layerContentApi.insertAttrtable({
      id: request.id,
      attrtable: JSON.stringify([rowObj]),
    } as any))
    if (!request.current()) return
    ElMessage.success('记录已新增')
    addVisible.value = false
    loadAttrs()
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '新增失败')
  } finally {
    if (!request.current()) return
    addSaving.value = false
  }
}

/* ---------------- 删除记录（DeleteAttrtable? id + gid） ---------------- */
async function removeRecord() {
  if (attrDeleting.value) return
  const request = beginResourceRequest('removeRecord')
  if (!request) return
  attrDeleting.value = true
  try {
    if (!currentRow.value) {
      ElMessage.warning('请先在属性表中点选一行记录')
      return
    }
    const gid = rowGid(currentRow.value)
    if (gid == null || gid === '') {
      ElMessage.warning('该记录缺少 gid，无法删除')
      return
    }
    try {
      await ElMessageBox.confirm('确认删除所选记录？该操作不可撤销。', '删除记录', { type: 'warning' })
      if (!request.current()) return
    } catch {
      if (!request.current()) return
      return
    }
    try {
      requireWriteSuccess(await layerContentApi.deleteAttrtable(request.id, String(gid)))
      if (!request.current()) return
      ElMessage.success('记录已删除')
      currentRow.value = null
      loadAttrs()
    } catch (e: any) {
      if (!request.current()) return
      showError(e, '删除失败')
    }

  } finally {
    if (request.current()) attrDeleting.value = false
  }
}

/* ---------------- 批量修改（UpdateAttrtableList，Body: LayerParameter[]）
 * 后端逐条复用 updateAttrtable：attrtable 为「Map 数组的 JSON」且每行必须含 gid，否则静默返回 false。
 * ---------------- */
const multiVisible = ref(false)
const multiSaving = ref(false)
const multiForm = ref<Record<string, any>>({})
const multiRows = computed(() => (selectionRows.value.length ? selectionRows.value : currentRow.value ? [currentRow.value] : []))

function rowGid(row: any): string | undefined {
  const g = row?.gid ?? row?.GID ?? row?.Gid
  return g == null ? undefined : String(g)
}

function openMultiEdit() {
  if (!multiRows.value.length) {
    ElMessage.warning('请先勾选（或点选）至少一行记录')
    return
  }
  if (multiRows.value.some((r) => !rowGid(r))) {
    ElMessage.warning('所选记录存在缺失 gid 的行，无法批量修改')
    return
  }
  multiForm.value = {}
  multiVisible.value = true
}

async function saveMultiEdit() {
  if (multiSaving.value) return
  const request = beginResourceRequest('saveMultiEdit')
  if (!request) return
  const patch = Object.fromEntries(
    Object.entries(multiForm.value).filter(([, v]) => v !== '' && v != null),
  )
  if (!Object.keys(patch).length) {
    ElMessage.warning('请至少填写一个要统一修改的字段')
    return
  }
  multiSaving.value = true
  try {
    const list = multiRows.value.map((row) => ({
      id: props.layer.id,
      attrtable: JSON.stringify([{ ...row, ...patch }]),
    }))
    requireWriteSuccess(await layerContentApi.updateAttrtableList(list))
    if (!request.current()) return
    ElMessage.success(`已批量修改 ${list.length} 条记录`)
    multiVisible.value = false
    loadAttrs()
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '批量修改失败')
  } finally {
    if (!request.current()) return
    multiSaving.value = false
  }
}

/* ---------------- 编辑资源（LayerContent/Update，Body: LayerContentInputDto） ---------------- */
const layerEditVisible = ref(false)
const layerSaving = ref(false)
const layerForm = ref<Record<string, any>>({})

function openEditLayer() {
  const l = detail.value || props.layer || {}
  layerForm.value = {
    id: l.id,
    layerName: l.layerName ?? '',
    dataType: l.dataType ?? '',
    layerType: l.layerType ?? '',
    layerDesc: l.layerDesc ?? '',
    layerRefence: l.layerRefence ?? '',
    layerTag: l.layerTag ?? '',
    dataConfidential: l.dataConfidential ?? '',
    keywords: l.keywords ?? '',
    dataDesc: l.dataDesc ?? '',
    dataAccuracy: l.dataAccuracy ?? '',
  }
  layerEditVisible.value = true
}

async function saveLayer() {
  if (layerSaving.value) return
  const request = beginResourceRequest('saveLayer')
  if (!request) return
  const nm = String(layerForm.value.layerName || '').trim()
  if (!nm) {
    ElMessage.warning('请输入资源名称')
    return
  }
  layerSaving.value = true
  try {
    requireWriteSuccess(await layerContentApi.update({ ...layerForm.value, layerName: nm }))
    if (!request.current()) return
    ElMessage.success('资源已保存')
    detail.value = { ...detail.value, ...layerForm.value, layerName: nm }
    invalidateDatasets()
    layerEditVisible.value = false
    emit('changed')
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '保存失败')
  } finally {
    if (!request.current()) return
    layerSaving.value = false
  }
}

/* ---------------- 要素编辑（原型 editFeatures → editVectorFeatures，Body: VectorEditDTO） ---------------- */
const featVisible = ref(false)
const featSaving = ref(false)
const featJson = ref('')

function openFeatureEdit() {
  featJson.value = ''
  featVisible.value = true
}

async function saveFeatures() {
  if (featSaving.value) return
  const request = beginResourceRequest('saveFeatures')
  if (!request) return
  let parsed: any
  try {
    parsed = JSON.parse(featJson.value)
    if (parsed?.type !== 'FeatureCollection' || !Array.isArray(parsed.features) || parsed.features.some((feature: any) => feature?.type !== 'Feature')) {
      throw new Error('invalid GeoJSON')
    }
  } catch {
    if (!request.current()) return
    ElMessage.error('请提供包含 features 数组的 GeoJSON FeatureCollection')
    return
  }
  featSaving.value = true
  try {
    requireWriteSuccess(await layerContentApi.editVectorFeatures({
      layerId: request.id,
      geoJsonCollection: parsed,
    }))
    if (!request.current()) return
    ElMessage.success('要素编辑已保存')
    featVisible.value = false
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '保存失败')
  } finally {
    if (!request.current()) return
    featSaving.value = false
  }
}

/* ---------------- 样式 ---------------- */
const styleInfo = ref<any>(null)
async function loadStyle() {
  const request = beginResourceRequest('style')
  if (!request) return
  if (!props.layer?.id) return
  try {
    // 无绑定时后端返回 null（原实现返回空对象，导致「暂无样式」空态不展示）
    const readResult = await dataStyleApi.getDetailByLayerID(request.id)
    if (!request.current()) return
    styleInfo.value = readResult
  } catch {
    if (!request.current()) return
    readError("style", new Error('读取失败，请重试'))
    styleInfo.value = null
  }
}

/* 样式绑定 / 解绑：
 * 样式侧 styleDefaultLayer ← dataStyle/Update；图层侧 layerDefaultStyle ← layerContent/Update */
const styleOptions = ref<any[]>([])
const styleBindId = ref('')
const styleBinding = ref(false)
async function loadStyleOptions() {
  const request = beginResourceRequest('styleOptions')
  if (!request) return
  try {
    const res = await dataStyleApi.getAllListPage({ styleName: '' }, 50, 1)
    if (!request.current()) return
    styleOptions.value = pickList<any>(res)
  } catch {
    if (!request.current()) return
    readError("styleOptions", new Error('读取失败，请重试'))
    styleOptions.value = []
  }
}
function styleOptionLabel(s: any): string {
  return `${s.styleName ?? s.name ?? '-'}（${CONFIG_TYPE_LABEL[String(s.styleConfigType ?? '')] || '未分类'}）`
}
const CONFIG_TYPE_LABEL: Record<string, string> = {
  '1': '单一符号',
  '2': '分级/唯一值',
  '3': '灰度拉伸',
  '4': '单波段',
  '5': '三波段',
  '6': '多波段',
}
async function bindStyleToLayer() {
  if (styleBinding.value) return
  const request = beginResourceRequest('bindStyleToLayer')
  if (!request) return
  if (!props.layer?.id || !styleBindId.value) {
    ElMessage.warning('请先选择样式')
    return
  }
  styleBinding.value = true
  try {
    const opt = styleOptions.value.find((s) => s.id === styleBindId.value)
    requireWriteSuccess(await dataStyleApi.update({
      id: styleBindId.value,
      styleConfigType: opt?.styleConfigType,
      styleDefaultLayer: request.id,
    }))
    if (!request.current()) return
    // 解绑此前绑定到该图层的其它样式
    if (styleInfo.value?.id && styleInfo.value.id !== styleBindId.value) {
      requireWriteSuccess(await dataStyleApi.update({
        id: styleInfo.value.id,
        styleConfigType: styleInfo.value.styleConfigType,
        styleDefaultLayer: '',
      }))
      if (!request.current()) return
    }
    requireWriteSuccess(await layerContentApi.update({ id: request.id, layerDefaultStyle: styleBindId.value }))
    if (!request.current()) return
    ElMessage.success('已绑定样式')
    await loadStyle()
    if (!request.current()) return
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '绑定失败')
  } finally {
    if (!request.current()) return
    styleBinding.value = false
  }
}
async function unbindStyle() {
  if (styleBinding.value) return
  const request = beginResourceRequest('unbindStyle')
  if (!request) return
  if (!props.layer?.id || !styleInfo.value?.id) return
  styleBinding.value = true
  try {
    requireWriteSuccess(await dataStyleApi.update({
      id: styleInfo.value.id,
      styleConfigType: styleInfo.value.styleConfigType,
      styleDefaultLayer: '',
    }))
    if (!request.current()) return
    requireWriteSuccess(await layerContentApi.update({ id: request.id, layerDefaultStyle: '' }))
    if (!request.current()) return
    ElMessage.success('已解除绑定')
    await loadStyle()
    if (!request.current()) return
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '解绑失败')
  } finally {
    if (!request.current()) return
    styleBinding.value = false
  }
}
function parsePolygonStyle(v: any): any {
  if (!v) return null
  if (typeof v === 'object') return v
  try {
    return JSON.parse(v)
  } catch {
    return null
  }
}

/* ---------------- 渲染分析（原型样式页签：断点/极值/唯一值/字段取值/色带库/另存为样式） ---------------- */
const renderState = reactive({
  attr: '', // 矢量：渲染字段
  bandIndex: undefined as number | undefined, // 栅格：波段号
  lvNum: 5, // 分级数
  loading: false,
  resultKind: '' as '' | 'minmax' | 'values' | 'breaks' | 'attrlist',
  minmax: { min: '' as any, max: '' as any },
  values: [] as any[],
  breaks: [] as any[],
  attrList: [] as any[],
})
const ramps = ref<{ name: string; colors: string[] }[]>([])

/** 色带库：线性色带 + 随机色带合并展示 */
async function loadRamps() {
  const request = beginResourceRequest('ramps')
  if (!request) return
  try {
    const linear = pickList<any>(await dataStyleApi.getLinearColorRamps())
    if (!request.current()) return
    let random: any[] = []
    try {
      const readResult = pickList<any>(await dataStyleApi.getRandomColorRamps())
      if (!request.current()) return
      random = readResult
    } catch {
      if (!request.current()) return
      random = []
    }
    ramps.value = [...linear, ...random].map((r: any) => ({
      name: r.colorName ?? r.name ?? '-',
      colors: r.colors ?? r.colorList ?? (typeof r.colorJson === 'string' ? JSON.parse(r.colorJson) : []) ?? [],
    })).filter((r) => r.colors.length)
  } catch {
    if (!request.current()) return
    readError("ramps", new Error('读取失败，请重试'))
    ramps.value = []
  }
}

/** 渲染分析统一入口：按矢量/栅格路由到对应端点 */
async function runRender(kind: 'minmax' | 'values' | 'breaks' | 'attrlist') {
  if (renderState.loading) return
  const request = beginResourceRequest('runRender')
  if (!request) return
  if (!props.layer?.id) return
  if (!isRaster.value && kind !== 'attrlist' && !renderState.attr) {
    ElMessage.warning('请先选择渲染字段')
    return
  }
  renderState.loading = true
  renderState.resultKind = kind
  try {
    if (kind === 'minmax') {
      const r: any = isRaster.value
        ? await dataStyleApi.getTifMaxMin(request.id, renderState.bandIndex)
        : await dataStyleApi.getShpMaxMin(request.id, renderState.attr)
      if (!request.current()) return
      renderState.minmax = { min: r?.min ?? '-', max: r?.max ?? '-' }
    } else if (kind === 'values') {
      const r: any = await dataStyleApi.getTifValues(request.id, renderState.bandIndex)
      if (!request.current()) return
      renderState.values = pickList(r)
    } else if (kind === 'breaks') {
      const r: any = isRaster.value
        ? await dataStyleApi.getTifJenksBreaks(request.id, renderState.lvNum, renderState.bandIndex)
        : await dataStyleApi.getShpJenksBreaks(request.id, renderState.attr, renderState.lvNum)
      if (!request.current()) return
      renderState.breaks = pickList(r)
    } else {
      // 矢量字段取值列表：GET /dataStyle/GetDataAttribute
      const r: any = await dataStyleApi.getDataAttribute(request.id, renderState.attr)
      if (!request.current()) return
      renderState.attrList = pickList(r)
    }
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '渲染分析失败')
  } finally {
    if (!request.current()) return
    renderState.loading = false
  }
}

/** 另存为样式：POST /dataStyle/Insert（先 IsExists 重名校验） */
async function saveAsStyle() {
  if (styleSaving.value) return
  const request = beginResourceRequest('saveAsStyle')
  if (!request) return
  styleSaving.value = true
  try {
    try {
      const { value } = await ElMessageBox.prompt('输入样式名称', '另存为样式')
      if (!request.current()) return
      const name = String(value || '').trim()
      if (!name) return
      const exists: any = await dataStyleApi.isExists(name)
      if (!request.current()) return
      if (exists === true || exists === 'true') {
        ElMessage.error('样式名已存在')
        return
      }
      requireWriteSuccess(await dataStyleApi.insert({
        styleName: name,
        name,
        styleType: isRaster.value ? '2' : '1',
        renderType: '分级设色',
        layerId: request.id,
        layerAttr: isRaster.value ? String(renderState.bandIndex ?? 1) : renderState.attr,
        colorRamp: ramps.value[0]?.name ?? '',
        createBy: userStore.userName,
      }))
      if (!request.current()) return
      ElMessage.success('已另存为样式')
      loadStyle()
    } catch (e: any) {
      if (!request.current()) return
      if (e !== 'cancel' && e !== 'close') showError(e, '另存失败')
    }

  } finally {
    if (request.current()) styleSaving.value = false
  }
}

/* ---------------- 服务与发布 ---------------- */
const serviceInfo = ref<any>(null)
const svcList = ref<{ type: string; url: string }[]>([])
const relatedMaps = ref<any[]>([])
const publishLoading = ref(false)

/** 探测全部可用服务（矢量：WMS/WFS/MVT；栅格：WMS/WMTS） */
async function loadService() {
  const request = beginResourceRequest('services')
  if (!request) return
  if (!props.layer?.id) return
  try {
    const state = await getDatasetServiceState(normalizeLayer(detail.value))
    if (!request.current()) return
    svcList.value = state.addresses
    serviceInfo.value = state
    if (state.error) panelErrors.services = state.error
  } catch {
    if (!request.current()) return
    readError("services", new Error('读取失败，请重试'))
    svcList.value = []
    serviceInfo.value = null
  }
}

async function doPublish() {
  if (publishLoading.value) return
  const request = beginResourceRequest('doPublish')
  if (!request) return
  publishLoading.value = true
  try {
    requireWriteSuccess(await serverInterfaceApi.publishService({
      id: request.id,
      layerName: props.layer.layerName,
      publishType: 'WMS',
    }))
    if (!request.current()) return
    ElMessage.success('已发布为 OGC 服务')
    invalidateDatasets()
    await refreshDetail()
    if (!request.current()) return
    await loadService()
    if (!request.current()) return
    emit('changed')
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '发布失败')
  } finally {
    if (!request.current()) return
    publishLoading.value = false
  }
}

/** 复制服务地址 */
async function copySvcUrl(url: string) {
  if (!url) return
    ; (await copyText(url)) ? ElMessage.success('地址已复制') : ElMessage.error('复制失败')
}

async function loadRelatedMaps() {
  const request = beginResourceRequest('relatedMaps')
  if (!request) return
  try {
    const readResult = pickList(await mapRelationApi.getAllListBylayerID(request.id))
    if (!request.current()) return
    relatedMaps.value = readResult
  } catch {
    if (!request.current()) return
    readError("relatedMaps", new Error('读取失败，请重试'))
    relatedMaps.value = []
  }
}

async function copy(t: string) {
  if (!t) return
    ; (await copyText(t)) ? ElMessage.success('已复制') : ElMessage.error('复制失败，请手动选择内容')
}

/* ---------------- 元数据 ---------------- */
const meta = ref<any>({})
const metaSaving = ref(false)
async function loadMeta() {
  const request = beginResourceRequest('meta')
  if (!request) return
  try {
    const readResult = (await metadataApi.getDetailByDataId(request.id)) || {}
    if (!request.current()) return
    meta.value = readResult
  } catch {
    if (!request.current()) return
    readError("meta", new Error('读取失败，请重试'))
    meta.value = {}
  }
}
async function saveMeta() {
  if (metaSaving.value) return
  const request = beginResourceRequest('saveMeta')
  if (!request) return
  metaSaving.value = true
  try {
    const body = { ...meta.value, dataId: request.id, id: meta.value.id }
    if (meta.value.id) requireWriteSuccess(await metadataApi.update(body))
    else requireWriteSuccess(await metadataApi.insert(body))
    if (!request.current()) return
    ElMessage.success('元数据已保存')
    await loadMeta()
    if (!request.current()) return
  } catch (e: any) {
    if (!request.current()) return
    showError(e, '保存失败')
  } finally {
    if (!request.current()) return
    metaSaving.value = false
  }
}

/** 删除元数据：POST /metadata/Delete?id */
async function removeMeta() {
  if (metaSaving.value) return
  const request = beginResourceRequest('removeMeta')
  if (!request) return
  metaSaving.value = true
  try {
    if (!meta.value?.id) {
      ElMessage.info('该资源尚无元数据记录')
      return
    }
    try {
      await ElMessageBox.confirm('确定删除该资源的元数据？', '删除确认', { type: 'warning' })
      if (!request.current()) return
      requireWriteSuccess(await metadataApi.remove(meta.value.id))
      if (!request.current()) return
      ElMessage.success('元数据已删除')
      loadMeta()
    } catch (e: any) {
      if (!request.current()) return
      if (e !== 'cancel' && e !== 'close') showError(e, '删除失败')
    }

  } finally {
    if (request.current()) metaSaving.value = false
  }
}

/** 按数据 ID 删除全部元数据：POST /metadata/DeleteByDataId?dataId */
async function removeMetaByDataId() {
  if (metaSaving.value) return
  const request = beginResourceRequest('removeMetaByDataId')
  if (!request) return
  metaSaving.value = true
  try {
    try {
      await ElMessageBox.confirm('按数据 ID 删除该资源的全部元数据记录？', '删除确认', { type: 'warning' })
      if (!request.current()) return
      requireWriteSuccess(await metadataApi.deleteByDataId(request.id))
      if (!request.current()) return
      ElMessage.success('元数据已按数据 ID 删除')
      meta.value = {}
      loadMeta()
    } catch (e: any) {
      if (!request.current()) return
      if (e !== 'cancel' && e !== 'close') showError(e, '删除失败')
    }

  } finally {
    if (request.current()) metaSaving.value = false
  }
}

/* ---------------- 活动 ---------------- */
const logs = ref<any[]>([])
async function loadLogs() {
  const request = beginResourceRequest('logs')
  if (!request) return
  try {
    const res = await layerLogApi.getList(50, 1)
    if (!request.current()) return
    const rows = pickList<any>(res)
    // 只展示可确认属于当前资源的日志，避免将其他资源活动显示到这里。
    const filtered = rows.filter(
      (r) => String(r.dataId ?? r.objectId ?? r.businessId ?? '') === String(props.layer?.id),
    )
    logs.value = filtered
  } catch {
    if (!request.current()) return
    readError("logs", new Error('读取失败，请重试'))
    logs.value = []
  }
}

/* ---------------- 页签切换加载 ---------------- */
function loadActiveTab(t: string) {
  if (!props.layer || !props.modelValue) return
  if (t === 'overview') loadOverview()
  if (t === 'data') {
    if (isRaster.value) loadRasterInfo()
    else {
      loadFields()
      loadAttrs()
    }
  }
  if (t === 'schema') loadAttrTableDetail()
  if (t === 'style') {
    loadStyle()
    loadRamps()
    loadStyleOptions()
    if (!isRaster.value && !fields.value.length) loadFields()
  }
  if (t === 'service') {
    loadService()
    loadRelatedMaps()
  }
  if (t === 'meta') loadMeta()
  if (t === 'activity') loadLogs()
}

watch(tab, loadActiveTab)
watch(
  () => [props.modelValue, props.initialTab, props.layer?.id],
  () => {
    requests.invalidate()
    if (props.modelValue) {
      const nextTab = props.initialTab || 'overview'
      const tabChanged = tab.value !== nextTab
      tab.value = nextTab
      resetPanels()
      refreshDetail()
      if (!tabChanged) loadActiveTab(nextTab)
    } else {
      whereVisible.value = editVisible.value = addVisible.value = multiVisible.value = layerEditVisible.value = featVisible.value = false
    }
  },
  { immediate: true, flush: 'sync' },
)

function loadOverview() {
  void nextTick(() => {
    if (props.modelValue && tab.value === 'overview' && bbox.value) overviewMapRef.value?.fitExtent(bbox.value)
  })
}

</script>

<template>
  <el-drawer v-model="visible" size="min(760px, 100vw)" :with-header="false" destroy-on-close>
    <div v-if="layer" class="detail">
      <div class="d-head">
        <div class="dh-main">
          <div class="dh-title">
            {{ detail.layerName || layer.layerName }}
            <span class="tag" :class="isRaster ? 'orange' : 'blue'">{{ layerTypeName(layer.dataType) }}</span>
            <span class="tag" :class="String(detail.publishStatus) === '1' ? 'green' : 'gray'">
              {{ String(detail.publishStatus) === '1' ? '已发布' : '未发布' }}
            </span>
          </div>
          <div class="dh-sub mono">ID: {{ layer.id }}</div>
        </div>
        <div class="row" style="gap: 6px">
          <el-button class="btn sm" @click="openEditLayer"><Icon name="edit" :size="13" /> 编辑资源</el-button>
          <el-button class="btn sm" @click="visible = false"><Icon name="x" :size="13" /> 关闭</el-button>
        </div>
      </div>

      <div v-if="activePanelError" class="panel-error" role="alert"><span>{{ activePanelError }}</span><el-button link type="primary" @click="retryActiveTab">重试</el-button></div>
<div v-if="!dataReady" class="panel-error" role="status"><span>入库待确认。可刷新状态或查看活动记录，确认完成后再发布或编辑要素。</span><el-button link type="primary" @click="refreshDetail">刷新状态</el-button><el-button link type="primary" @click="tab = 'activity'">查看活动</el-button></div>
      <el-tabs v-model="tab" class="d-tabs">
        <!-- 概览 -->
        <el-tab-pane lazy label="概览" name="overview">
          <div class="kv mb8">
            <span class="k">资源名称</span><span class="v">{{ detail.layerName || layer.layerName }}</span>
            <span class="k">类型</span><span class="v">{{ layerTypeName(layer.dataType) }}</span>
            <span class="k">所属分类</span><span class="v">{{ detail.layerTypeName || detail.layerType || '-' }}</span>
            <span class="k">空间参考</span><span class="v">{{ detail.layerRefenceName || detail.layerRefence || '-' }}</span>
            <span class="k">空间表</span><span class="v mono">{{ detail.layerSpatialTable || detail.layerspatialtable || '-' }}</span>
            <span class="k">属性表</span><span class="v mono">{{ detail.layerAttrTable || '-' }}</span>
            <span class="k">四至</span><span class="v mono">{{ detail.layerBBox || '-' }}</span>
            <span class="k">文件大小</span><span class="v">{{ fmtSize(Number(detail.fileSize || 0) * 1024) }}</span>
            <template v-if="isRaster">
              <span class="k">源文件</span><span class="v mono">{{ detail.uploadFileName?.split(/[\\/]/).pop() || '-' }}</span>
              <span class="k">波段数</span><span class="v">{{ detail.rasterCount ?? '-' }}</span>
              <span class="k">像元类型</span><span class="v">{{ detail.rasterDatatype || '-' }}</span>
            </template>
            <span class="k">上传状态</span><span class="v">{{ dataReady ? '已入库' : '入库待确认' }}</span>
            <span class="k">创建人 / 时间</span><span class="v">{{ layer.createBy || '-' }} · {{ fmtTime(layer.createDT) }}</span>
            <span class="k">描述</span><span class="v">{{ detail.layerDesc || '-' }}</span>
          </div>
          <div class="map-box">
            <Map2D v-if="modelValue && tab === 'overview'" ref="overviewMapRef" @ready="loadOverview" :zoom="12" :center="bbox ? [(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2] : [113.9042, 29.7234]" />
          </div>
        </el-tab-pane>

        <!-- 数据与要素 -->
        <el-tab-pane lazy :label="isRaster ? '栅格详情' : '数据与要素'" name="data">
          <template v-if="isRaster">
            <div class="card">
              <div class="card-h">
                栅格结构
                <div class="tools"><el-button class="btn xs" :loading="downloadBusy" @click="downloadTifFile">下载 TIF</el-button></div>
              </div>
              <div class="card-b">
                <div v-if="rasterInfo" class="kv">
                  <span class="k">影像列数</span><span class="v">{{ rasterInfo.xsize ?? rasterInfo.width ?? '-' }}</span>
                  <span class="k">影像行数</span><span class="v">{{ rasterInfo.ysize ?? rasterInfo.height ?? '-' }}</span>
                  <span class="k">像元宽度</span><span class="v">{{ rasterInfo.xCell ?? '-' }}</span>
                  <span class="k">像元高度</span><span class="v">{{ rasterInfo.yCell ?? '-' }}</span>
                </div>
                <el-empty v-else description="暂无栅格结构信息，请刷新重试" :image-size="60" />
              </div>
            </div>

            <!-- 像元统计：getTifMaxMin / getTifValues -->
            <div class="card">
              <div class="card-h">
                像元统计
                <div class="tools">
                  <el-input-number v-model="rasterStats.bandIndex" :disabled="rasterStats.loading" :min="1" size="small" placeholder="波段" style="width: 96px" />
                  <el-button class="btn xs" :loading="rasterStats.loading" @click="loadTifMaxMin">读取极值</el-button>
                  <el-button class="btn xs" :loading="rasterStats.loading" @click="loadTifValues">读取唯一值</el-button>
                </div>
              </div>
              <div class="card-b">
                <div class="kv">
                  <span class="k">极值</span>
                  <span class="v mono">min = {{ rasterStats.min ?? '-' }} ／ max = {{ rasterStats.max ?? '-' }}</span>
                  <span class="k">唯一值</span>
                  <span class="v mono">{{ rasterStats.values.length ? rasterStats.values.slice(0, 20).join(' , ') : '—' }}</span>
                </div>
                <div class="muted" style="font-size: 11.5px; margin-top: 6px">
                  连续型影像建议使用区间分类；唯一值较多时仅展示前 20 项。
                </div>
              </div>
            </div>

            <!-- 坐标取值：getLayerValue -->
            <div class="card">
              <div class="card-h">
                坐标取值
              </div>
              <div class="card-b">
                <div class="row wrap" style="gap: 8px; align-items: center">
                  <el-input v-model="pixelProbe.lon" size="small" placeholder="经度（-180～180）" style="width: 170px" />
                  <el-input v-model="pixelProbe.lat" size="small" placeholder="纬度（-90～90）" style="width: 170px" />
                  <el-button class="btn xs" :loading="pixelProbe.loading" @click="probeLayerValue">读取像元值</el-button>
                </div>
                <div class="kv mt8">
                  <span class="k">像元值</span>
                  <span class="v mono">{{ pixelProbe.value === '' ? '—' : pixelProbe.value === null ? '该坐标无有效像元值' : pixelProbe.value }}</span>
                </div>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="card mb8">
              <div class="card-h">
                字段定义<span class="cnt">{{ fields.length }}</span>
                <div class="tools">
                  <el-button class="btn xs" :disabled="fieldSaving || !dataReady" @click="addField">新增字段</el-button>
                  <el-button class="btn xs" :disabled="fieldSaving || !dataReady" @click="addFieldSync">新增字段并同步</el-button>
                  <el-button class="btn xs danger" :disabled="!canClearFields()" :title="fields.some(isSystemField) ? '包含系统字段，请逐项删除业务字段' : '清空字段定义'" @click="removeAllFields">清空字段</el-button>
                </div>
              </div>
              <el-table :data="fields" size="small" max-height="220">
                <el-table-column label="字段名" min-width="120">
                  <template #default="{ row }">{{ fieldNameOf(row) }}</template>
                </el-table-column>
                <el-table-column label="类型" width="120">
                  <template #default="{ row }">{{ row.attributeType ?? row.fieldType ?? row.dataType ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="别名" min-width="120">
                  <template #default="{ row }">{{ row.attributeDesc ?? row.fieldAlias ?? row.alias ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-button class="btn xs danger" :disabled="!canDeleteField(row)" :title="fieldDeleteHint(row)" @click="removeField(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty><span class="muted">暂无字段定义</span></template>
              </el-table>
            </div>
          </template>

          <div v-if="!isRaster" class="card">
            <div class="card-h attr-card-head">
              <span class="attr-card-title">属性表 <span class="cnt">{{ attrTotal }}</span></span>
              <div class="tools">
                <el-button class="btn xs" @click="startDraw('Box')">框选</el-button>
                <el-button class="btn xs" @click="startDraw('Circle')">圈选</el-button>
                <el-button class="btn xs" @click="startDraw('Polygon')">多边选</el-button>
                <el-button class="btn xs" @click="startDraw('Point')">点选</el-button>
                <el-button class="btn xs" @click="bufferQuery">缓冲查询</el-button>
                <el-button class="btn xs" @click="openWhere">条件筛选</el-button>
                <el-button class="btn xs primary" :disabled="!dataReady" @click="openAddRecord">新增记录</el-button>
                <el-button class="btn xs" :disabled="!dataReady" @click="openEditRecord">编辑记录</el-button>
                <el-button class="btn xs" :disabled="!dataReady" @click="openMultiEdit">批量修改</el-button>
                <el-button class="btn xs danger" :loading="attrDeleting" :disabled="!dataReady" @click="removeRecord">删除记录</el-button>
                <el-button v-if="!isRaster" class="btn xs" :disabled="!dataReady" @click="openFeatureEdit">要素编辑</el-button>
                <el-button class="btn xs" @click="loadAttrs">刷新</el-button>
                <el-button v-if="Object.keys(lastAttrQuery).length" class="btn xs" @click="resetAttrQuery">清除筛选</el-button>
                <el-button class="btn xs" :loading="downloadBusy" @click="exportAttr">导出查询</el-button>
                <el-button v-if="!isRaster" class="btn xs" :loading="downloadBusy" @click="exportShp">导出 SHP</el-button>
                <el-button v-if="isRaster" class="btn xs" :loading="downloadBusy" @click="exportTif">导出 TIF</el-button>
              </div>
            </div>
            <div class="map-box sm mb8">
              <Map2D v-if="modelValue && tab === 'data'" ref="attrMapRef" @ready="() => bbox && attrMapRef?.fitExtent(bbox)" :zoom="12" :center="bbox ? [(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2] : [113.9042, 29.7234]" @drawend="onDrawEnd" @pick="onMapPick" />
            </div>
            <div class="row wrap mb8" style="gap: 8px; align-items: center">
              <span class="muted" style="font-size: 11.5px">直接单击地图查询属性（气泡展示并高亮）：</span>
              <span class="k">点选容差(米)</span>
              <el-input-number v-model="pickTolerance" size="small" :min="1" :max="1000" style="width: 96px" />
              <el-button class="btn xs" @click="locateCurrentRow"><Icon name="globe2" :size="12" />定位所选行</el-button>
              <el-button class="btn xs" @click="attrMapRef?.clearHighlight()">清除高亮</el-button>
            </div>
            <el-table
              v-loading="panelsLoading"
              :data="attrs"
              size="small"
              height="240"
              highlight-current-row
              @current-change="(row: any) => (currentRow = row)"
              @selection-change="onAttrSelectionChange"
            >
              <el-table-column type="selection" width="38" />
              <el-table-column
                v-for="c in attrColumns"
                :key="c"
                :prop="c"
                :label="c"
                min-width="110"
                show-overflow-tooltip
              />
              <template #empty><span class="muted">暂无属性数据（或用地图框选/圈选/条件筛选查询）</span></template>
            </el-table>
            <div class="muted" style="font-size: 11.5px; padding-top: 4px">
              提示：单击地图可直接查询要素属性（气泡 + 高亮，容差可调）；「圈选」为圆形缓冲（半径按米）；「缓冲查询」以视图中心为圆心；「导出查询」导出当前筛选结果；勾选多行可「批量修改」；新增记录时需填写 WKT 几何。
            </div>
            <div class="pager">
              <el-pagination
                v-model:current-page="attrPage.index"
                v-model:page-size="attrPage.size"
                :total="attrTotal"
                layout="total, prev, pager, next"
                small
                @current-change="loadAttrs"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 表结构（原始属性表） -->
        <el-tab-pane lazy v-if="!isRaster" label="表结构" name="schema">
          <div class="card">
            <div class="card-h">
              原始属性表
              <div class="tools">
                <button class="btn sm" @click="loadAttrTableDetail">
                  <Icon name="refresh" :size="13" /> 刷新
                </button>
              </div>
            </div>
            <div class="card-b">
              <div v-if="attrSchema.fields.length" class="row wrap mb8" style="gap: 6px">
                <span v-for="f in attrSchema.fields" :key="f" class="tag gray mono">{{ f }}</span>
              </div>
              <el-table
                v-loading="attrSchema.loading"
                :data="attrSchema.rows"
                size="small"
                border
                max-height="420"
                empty-text="无表结构数据"
              >
                <el-table-column
                  v-for="c in attrSchema.columns"
                  :key="c"
                  :prop="c"
                  :label="c"
                  min-width="140"
                  show-overflow-tooltip
                />
              </el-table>
              <div class="row mt8" style="justify-content: flex-end; align-items: center; gap: 8px">
                <el-pagination
                  v-model:current-page="attrSchema.page.index"
                  v-model:page-size="attrSchema.page.size"
                  :total="attrSchema.total"
                  layout="total, prev, pager, next"
                  small
                  @current-change="loadAttrTableDetail"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 样式 -->
        <el-tab-pane lazy label="样式" name="style">
          <div class="card">
            <div class="card-h">
              当前样式
              <div class="tools">
                <el-button class="btn xs" @click="loadStyle">刷新</el-button>
                <el-button v-if="styleInfo" class="btn xs danger" :loading="styleBinding" @click="unbindStyle">解除绑定</el-button>
                <el-button class="btn xs primary" @click="$router.push('/style')">打开样式库</el-button>
              </div>
            </div>
            <div class="card-b">
              <div v-if="styleInfo && Object.keys(styleInfo).length" class="kv mb8">
                <span class="k">样式名</span><span class="v">{{ styleInfo.styleName ?? styleInfo.name ?? '-' }}</span>
                <span class="k">样式 ID</span><span class="v mono">{{ styleInfo.id ?? '-' }}</span>
                <span class="k">配置类型</span><span class="v">{{ (CONFIG_TYPE_LABEL[String(styleInfo.styleConfigType ?? '')] || styleInfo.styleConfigType) ?? '-' }}</span>
                <span class="k">渲染方式</span><span class="v">{{ styleInfo.renderType ?? styleInfo.styleType ?? '-' }}</span>
                <span class="k">绑定字段</span><span class="v">{{ styleInfo.styleRenderFieldName ?? styleInfo.styleRenderField ?? styleInfo.layerAttr ?? '-' }}</span>
                <span class="k">色带</span><span class="v">{{ styleInfo.colorRamp ?? styleInfo.styleRenderColorBand ?? styleInfo.colorName ?? '-' }}</span>
              </div>
              <el-empty v-else description="该图层暂无样式" :image-size="60" />

              <!-- 规则明细（分级 / 唯一值） -->
              <div v-if="Array.isArray(styleInfo?.ruleDatas) && styleInfo.ruleDatas.length" class="mt8">
                <div class="muted" style="font-size: 11.5px; margin-bottom: 4px">规则明细（ruleDatas · {{ styleInfo.ruleDatas.length }} 条）</div>
                <el-table :data="styleInfo.ruleDatas" size="small" max-height="200">
                  <el-table-column label="#" width="44" type="index" />
                  <el-table-column label="范围/值" min-width="120">
                    <template #default="{ row }">
                      <span class="mono">{{ row.valueOne ? `${row.valueOne} ~ ${row.value}` : row.value }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作符" width="90">
                    <template #default="{ row }"><span class="tag gray">{{ row.operator || 'EQ' }}</span></template>
                  </el-table-column>
                  <el-table-column label="符号色" width="80">
                    <template #default="{ row }">
                      <span class="sw" :style="{ background: parsePolygonStyle(row.polygonStyle)?.polygonColor || parsePolygonStyle(row.polygonStyle)?.PolygonColor || '#ccc' }" />
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <!-- 栅格分级（tifStyles） -->
              <div v-if="Array.isArray(styleInfo?.tifStyles) && styleInfo.tifStyles.length" class="mt8">
                <div class="muted" style="font-size: 11.5px; margin-bottom: 4px">栅格分级（{{ styleInfo.tifStyles.length }} 级）</div>
                <el-table :data="styleInfo.tifStyles" size="small" max-height="200">
                  <el-table-column label="#" width="44" type="index" />
                  <el-table-column label="范围" min-width="140">
                    <template #default="{ row }"><span class="mono">{{ row.quantityOne }} ~ {{ row.quantity }}</span></template>
                  </el-table-column>
                  <el-table-column label="颜色" width="80">
                    <template #default="{ row }"><span class="sw" :style="{ background: row.color }" /></template>
                  </el-table-column>
                  <el-table-column label="标注" min-width="100">
                    <template #default="{ row }">{{ row.label || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>

              <!-- 绑定 / 更换样式 -->
              <div class="row mt8" style="gap: 8px; align-items: center">
                <el-select
                  v-model="styleBindId" :disabled="styleBinding"
                  filterable
                  clearable
                  placeholder="从样式库选择并绑定到本图层"
                  style="width: 300px"
                  @focus="loadStyleOptions"
                >
                  <el-option v-for="s in styleOptions" :key="s.id" :label="styleOptionLabel(s)" :value="s.id" />
                </el-select>
                <el-button class="btn xs primary" :loading="styleBinding" @click="bindStyleToLayer">绑定 / 更换</el-button>
              </div>
            </div>
          </div>

          <!-- 渲染分析：断点 / 极值 / 唯一值 / 字段取值 / 色带库 / 另存为样式 -->
          <div class="card">
            <div class="card-h">
              渲染分析<span class="cnt">极值、分类与取值</span>
              <div class="tools">
                <el-button class="btn xs primary" :loading="styleSaving" @click="saveAsStyle">另存为样式</el-button>
              </div>
            </div>
            <div class="card-b">
              <div class="filters" style="margin-bottom: 8px">
                <template v-if="!isRaster">
                  <el-select v-model="renderState.attr" :disabled="renderState.loading" placeholder="渲染字段" filterable style="width: 160px">
                    <el-option
                      v-for="f in fields"
                      :key="f.id ?? f.attributeName"
                      :label="f.attributeName ?? f.fieldName ?? f.name"
                      :value="f.attributeName ?? f.fieldName ?? f.name"
                    />
                  </el-select>
                </template>
                <template v-else>
                  <el-input-number v-model="renderState.bandIndex" :disabled="renderState.loading" :min="1" size="small" placeholder="波段" style="width: 110px" />
                </template>
                <el-input-number v-model="renderState.lvNum" :disabled="renderState.loading" :min="2" :max="12" size="small" style="width: 110px" />
                <el-button class="btn xs" :loading="renderState.loading" @click="runRender('minmax')">极值</el-button>
                <el-button v-if="isRaster" class="btn xs" :loading="renderState.loading" @click="runRender('values')">唯一值</el-button>
                <el-button class="btn xs" :loading="renderState.loading" @click="runRender('breaks')">自然断点</el-button>
                <el-button v-if="!isRaster" class="btn xs" :loading="renderState.loading" @click="runRender('attrlist')">字段取值</el-button>
              </div>

              <div v-if="renderState.resultKind === 'minmax'" class="kv">
                <span class="k">极值</span>
                <span class="v mono">min = {{ renderState.minmax.min }} ／ max = {{ renderState.minmax.max }}</span>
              </div>
              <div v-else-if="renderState.resultKind === 'values'">
                <div class="mono" style="font-size: 12px; word-break: break-all">
                  {{ renderState.values.slice(0, 50).join(' , ') || '—' }}
                </div>
              </div>
              <div v-else-if="renderState.resultKind === 'breaks'">
                <el-table :data="renderState.breaks" size="small" max-height="180">
                  <el-table-column v-for="k in Object.keys(renderState.breaks[0] ?? {})" :key="k" :prop="k" :label="k" min-width="90" />
                  <template #empty><span class="muted">无断点数据</span></template>
                </el-table>
              </div>
              <div v-else-if="renderState.resultKind === 'attrlist'">
                <div class="mono" style="font-size: 12px; word-break: break-all; max-height: 140px; overflow: auto">
                  {{ renderState.attrList.slice(0, 100).join(' , ') || '—' }}
                </div>
              </div>

              <div v-if="ramps.length" style="margin-top: 10px">
                <div class="muted" style="font-size: 11.5px; margin-bottom: 5px">色带库（{{ ramps.length }}）</div>
                <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 8px">
                  <div v-for="r in ramps.slice(0, 12)" :key="r.name">
                    <div class="muted" style="font-size: 10.5px; margin-bottom: 2px">{{ r.name }}</div>
                    <div style="display: flex; height: 12px; border-radius: 3px; overflow: hidden">
                      <i
                        v-for="(c, i) in r.colors.slice(0, 10)"
                        :key="i"
                        :style="{ background: c, flex: 1 }"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 服务与发布 -->
        <el-tab-pane lazy label="服务与发布" name="service">
          <div class="card mb8">
            <div class="card-h">
              服务发布
              <div class="tools">
                <el-button class="btn xs" @click="loadService">查询服务</el-button>
                <el-button class="btn xs primary" :loading="publishLoading" :disabled="!dataReady" @click="doPublish">发布为服务</el-button>
              </div>
            </div>
            <div class="card-b">
              <div v-if="!svcList.length" class="muted">暂无可用服务。完成入库并发布后，可查看各协议的访问地址。</div>
              <div v-else class="col" style="gap: 8px">
                <div v-for="s in svcList" :key="s.type" class="svc-row">
                  <div class="row" style="gap: 7px; align-items: center; flex-wrap: wrap">
                    <span class="svc-badge" :class="s.type.toLowerCase()">{{ s.type }}</span>
                    <b style="font-size: 12.5px">{{ SERVICE_PROTOCOLS[s.type]?.label || s.type }}</b>
                    <span class="muted" style="font-size: 11.5px">{{ SERVICE_PROTOCOLS[s.type]?.desc || '' }}</span>
                    <button class="btn xs" style="margin-left: auto" @click="copySvcUrl(s.url)">复制地址</button>
                  </div>
                  <div class="mono" style="font-size: 11px; color: #3c4b60; word-break: break-all; margin: 4px 0">{{ s.url }}</div>
                  <div class="muted" style="font-size: 11px; line-height: 1.65">{{ SERVICE_PROTOCOLS[s.type]?.detail || '' }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-h">
              所属地图<span class="cnt">{{ relatedMaps.length }}</span>
              <div class="tools"><el-button class="btn xs" @click="loadRelatedMaps">刷新</el-button></div>
            </div>
            <el-table :data="relatedMaps" size="small" max-height="200">
              <el-table-column label="地图 ID" prop="mapId" min-width="140" show-overflow-tooltip />
              <el-table-column label="样式 ID" prop="dataStyleID" min-width="120" show-overflow-tooltip />
              <el-table-column label="比例尺区间" width="150">
                <template #default="{ row }">
                  {{ row.minScaleDenominator ?? '-' }} ~ {{ row.maxScaleDenominator ?? '-' }}
                </template>
              </el-table-column>
              <template #empty><span class="muted">该图层未被任何地图引用</span></template>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 元数据 -->
        <el-tab-pane lazy label="元数据" name="meta">
          <div class="card">
            <div class="card-h">
              元数据
              <div class="tools">
                <el-button class="btn xs" @click="loadMeta">重新加载</el-button>
                <el-button class="btn xs danger" :disabled="metaSaving" @click="removeMeta">删除</el-button>
                <el-button class="btn xs danger" :disabled="metaSaving" @click="removeMetaByDataId">按数据ID删除</el-button>
                <el-button class="btn xs primary" :loading="metaSaving" @click="saveMeta">保存</el-button>
              </div>
            </div>
            <div class="card-b">
              <el-form label-width="120px" size="small">
                <el-form-item label="数据 ID">
                  <el-input :model-value="layer.id" disabled />
                </el-form-item>
                <el-form-item label="元数据名称">
                  <el-input v-model="meta.metaName" placeholder="请输入元数据名称" />
                </el-form-item>
                <el-form-item label="摘要">
                  <el-input v-model="meta.metaAbstract" type="textarea" :rows="3" placeholder="简要说明数据内容与用途" />
                </el-form-item>
                <el-form-item label="关键字">
                  <el-input v-model="meta.metaKeywords" placeholder="多个关键词可用逗号分隔" />
                </el-form-item>
                <el-form-item label="生产单位">
                  <el-input v-model="meta.produceOrg" placeholder="请输入生产单位" />
                </el-form-item>
                <el-form-item label="坐标系">
                  <el-input v-model="meta.coordSystem" placeholder="例如 EPSG:4326" />
                </el-form-item>
              </el-form>
            </div>
          </div>
        </el-tab-pane>

        <!-- 活动 -->
        <el-tab-pane lazy label="活动" name="activity">
          <div class="card">
            <div class="card-h">
              操作日志<span class="cnt">{{ logs.length }}</span>
              <div class="tools"><el-button class="btn xs" @click="loadLogs">刷新</el-button></div>
            </div>
            <el-table :data="logs" size="small" max-height="420">
              <el-table-column label="操作" prop="operationType" width="110">
                <template #default="{ row }">{{ row.operationType ?? row.operType ?? row.type ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="操作人" width="100">
                <template #default="{ row }">{{ row.createBy ?? row.operateBy ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="时间" width="150">
                <template #default="{ row }">{{ fmtTime(row.createDT ?? row.operateTime) }}</template>
              </el-table-column>
              <el-table-column label="描述" min-width="180">
                <template #default="{ row }">{{ row.description ?? row.remark ?? '-' }}</template>
              </el-table-column>
              <template #empty><span class="muted">暂无日志记录</span></template>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 条件筛选弹窗：ValidateCondition → GetLayerAttrTableByWhere -->
      <el-dialog v-model="whereVisible" title="条件筛选" width="640px" append-to-body :close-on-click-modal="false">
        <div v-for="(r, i) in whereRows" :key="i" style="display: flex; gap: 8px; margin-bottom: 8px">
          <el-select v-model="r.field" filterable style="width: 180px" placeholder="字段">
            <el-option
              v-for="f in (fields.length ? fields : attrColumns.map((c) => ({ attributeName: c })))"
              :key="f.attributeName ?? f"
              :label="f.attributeName ?? f"
              :value="f.attributeName ?? f"
            />
          </el-select>
          <el-select v-model="r.op" style="width: 100px">
            <el-option v-for="op in WHERE_OPS" :key="op" :label="op" :value="op" />
          </el-select>
          <el-input v-model="r.value" placeholder="值" style="flex: 1" />
          <el-button class="btn xs" @click="removeWhereRow(i)">删除</el-button>
        </div>
        <el-button class="btn xs" @click="addWhereRow"><Icon name="plus" :size="13" />加一条</el-button>
        <div class="muted" style="font-size: 11.5px; margin-top: 8px">
          同时满足所有已填写条件的记录会显示在属性表中；可使用“清除筛选”恢复全表。
        </div>
        <template #footer>
          <el-button class="btn sm" @click="whereVisible = false">取消</el-button>
          <el-button class="btn sm primary" :loading="whereLoading" @click="runWhereQuery">查询</el-button>
        </template>
      </el-dialog>

      <!-- 编辑记录弹窗：UpdateAttrtable（LayerParameter{id, attrtable:JSON串}） -->
      <el-dialog v-model="editVisible" title="编辑属性记录" width="560px" append-to-body :close-on-click-modal="false">
        <el-form label-width="140px" size="small">
          <el-form-item v-for="c in editableColumns" :key="c" :label="c">
            <el-input v-model="editForm[c]" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button class="btn sm" @click="editVisible = false">取消</el-button>
          <el-button class="btn sm primary" :loading="editSaving" @click="saveEditRecord">保存</el-button>
        </template>
      </el-dialog>

      <!-- 编辑资源弹窗：LayerContent/Update（Body: LayerContentInputDto） -->
      <el-dialog v-model="layerEditVisible" title="编辑资源" width="600px" append-to-body :close-on-click-modal="false">
        <el-form label-width="120px" size="small">
          <el-form-item label="资源名称" required>
            <el-input v-model="layerForm.layerName" />
          </el-form-item>
          <el-form-item label="数据类型">
            <el-select v-model="layerForm.dataType" style="width: 100%">
              <el-option label="矢量" value="1" />
              <el-option label="栅格" value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属分类 ID">
            <el-input v-model="layerForm.layerType" placeholder="目录(DataType) 的 id" />
          </el-form-item>
          <el-form-item label="空间参考">
            <el-input v-model="layerForm.layerRefence" placeholder="如 EPSG:4326" />
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="layerForm.layerTag" placeholder="多个用英文逗号分隔" />
          </el-form-item>
          <el-form-item label="涉密等级">
            <el-input v-model="layerForm.dataConfidential" />
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="layerForm.keywords" />
          </el-form-item>
          <el-form-item label="数据精度">
            <el-input v-model="layerForm.dataAccuracy" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="layerForm.layerDesc" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button class="btn sm" @click="layerEditVisible = false">取消</el-button>
          <el-button class="btn sm primary" :loading="layerSaving" @click="saveLayer">保存</el-button>
        </template>
      </el-dialog>

      <!-- 新增记录弹窗：InsertAttrtable（LayerParameter{id, attrtable:JSON行数组}，每行需含 geom WKT） -->
      <el-dialog v-model="addVisible" title="新增属性记录" width="560px" append-to-body :close-on-click-modal="false">
        <el-form label-width="140px" size="small">
          <el-form-item label="geom(WKT)" required>
            <el-input v-model="addForm.geom" placeholder="如 POINT(114.1 29.7)" class="mono" />
          </el-form-item>
          <el-form-item v-for="c in editableColumns" :key="c" :label="c">
            <el-input v-model="addForm[c]" />
          </el-form-item>
          <template v-if="!editableColumns.length">
            <div class="muted" style="font-size: 12px">当前图层无可用字段定义，请先在「字段定义」中新增字段。</div>
          </template>
        </el-form>
        <div class="muted" style="font-size: 11.5px">
          请填写属性和 WKT 几何；要素坐标应与当前图层的空间参考一致。
        </div>
        <template #footer>
          <el-button class="btn sm" @click="addVisible = false">取消</el-button>
          <el-button class="btn sm primary" :loading="addSaving" @click="saveAddRecord">保存</el-button>
        </template>
      </el-dialog>

      <!-- 批量修改弹窗：UpdateAttrtableList（LayerParameter[]） -->
      <el-dialog v-model="multiVisible" title="批量修改属性记录" width="560px" append-to-body :close-on-click-modal="false">
        <div class="muted" style="font-size: 12px; margin-bottom: 8px">
          将统一修改已选 <b>{{ multiRows.length }}</b> 条记录；只填写需要覆盖的字段（留空不改）。
        </div>
        <el-form label-width="140px" size="small">
          <el-form-item v-for="c in editableColumns" :key="c" :label="c">
            <el-input v-model="multiForm[c]" placeholder="留空表示不修改" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button class="btn sm" @click="multiVisible = false">取消</el-button>
          <el-button class="btn sm primary" :loading="multiSaving" @click="saveMultiEdit">保存</el-button>
        </template>
      </el-dialog>

      <!-- 要素编辑弹窗：editVectorFeatures（VectorEditDTO{layerId, geoJsonCollection}） -->
      <el-dialog v-model="featVisible" title="矢量要素编辑（GeoJSON）" width="680px" append-to-body :close-on-click-modal="false">
        <el-input
          v-model="featJson"
          type="textarea"
          :rows="12"
          placeholder='粘贴 FeatureCollection，例如：{"type":"FeatureCollection","features":[{"type":"Feature","properties":{...},"geometry":{...}}]}'
          class="mono"
        />
        <div class="muted" style="font-size: 11.5px; margin-top: 6px">
          请粘贴 GeoJSON FeatureCollection；保存前请确认要素标识和几何坐标正确。
        </div>
        <template #footer>
          <el-button class="btn sm" @click="featVisible = false">取消</el-button>
          <el-button class="btn sm primary" :loading="featSaving" @click="saveFeatures">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </el-drawer>
</template>

<style scoped>
.detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.d-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
}
.panel-error { display: flex; align-items: center; gap: 12px; margin: 10px 0; padding: 10px 12px; border: 1px solid #f4d5ba; border-radius: 7px; background: #fff8ef; color: #94571c; font-size: 12px; line-height: 1.6; }
.panel-error span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.dh-title {
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dh-sub {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 3px;
}
.d-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.d-tabs :deep(.el-tabs__content) {
  height: calc(100% - 40px);
  overflow: auto;
}
.map-box {
  height: 280px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  overflow: hidden;
}
.map-box.sm {
  height: 220px;
}
.attr-card-head { flex-wrap: wrap; }
.attr-card-title { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; }
.attr-card-head .tools { flex: 1 1 100%; margin-left: 0; flex-wrap: wrap; }
.attr-card-head .tools :deep(.el-button) { margin: 0; }
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}
.sw {
  width: 22px;
  height: 18px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  display: inline-block;
}
.svc-row {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 9px 10px;
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
.svc-badge.\33 dtiles {
  background: #d06a1f;
}
/* 地图点选气泡 */
.attr-popup {
  position: absolute;
  z-index: 10;
  min-width: 180px;
  max-width: 300px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(15, 26, 38, 0.16);
  font-size: 12px;
  padding: 8px 10px;
}
.attr-popup .ap-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  margin-bottom: 4px;
}
.attr-popup .ap-row {
  display: flex;
  gap: 8px;
  line-height: 1.6;
}
.attr-popup .ap-row .k {
  color: var(--ink-3);
  flex: 0 0 auto;
}
.attr-popup .ap-row .v {
  font-family: Consolas, Menlo, monospace;
  word-break: break-all;
}
.attr-popup .ap-close {
  cursor: pointer;
  color: var(--ink-3);
}
</style>
