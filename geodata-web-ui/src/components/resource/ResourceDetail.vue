<script setup lang="ts">
/**
 * 资源详情抽屉（6 页签 = 资源生命周期）
 * 概览 → 数据与要素 → 样式 → 服务与发布 → 元数据 → 活动
 * 后端依据：LayerContent / LayerField / MetaData / DataStyle / MapRelation / ServerInterface / LayerLog
 */
import { computed, reactive, ref, watch } from 'vue'
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
import { extractSvcUrl, isRasterDataType, SERVICE_PROTOCOLS } from '@/utils/datasets'
import { copyText } from '@/utils/clipboard'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

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
watch(
  () => [props.modelValue, props.initialTab],
  () => {
    if (props.modelValue) {
      tab.value = props.initialTab || 'overview'
      resetPanels()
      refreshDetail()
    }
  },
)

/** 打开抽屉时拉取最新详情（列表行数据可能已过期） */
async function refreshDetail() {
  if (!props.layer?.id) return
  try {
    const d: any = await layerContentApi.getDetailById(props.layer.id)
    if (d && typeof d === 'object') detail.value = { ...props.layer, ...d }
  } catch {
    /* 静默：保留行数据兜底 */
  }
}

const isRaster = computed(() => isRasterDataType(props.layer?.dataType))
const overviewMapRef = ref()
const attrMapRef = ref()
const detail = ref<any>({})

const bbox = computed(() => parseBBox(props.layer?.layerBBox))

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
  if (!props.layer?.id) return
  try {
    // 裸返回 List<LayerFieldEntity>
    fields.value = pickList(await layerContentApi.getFieldByLayerId(props.layer.id))
  } catch (e: any) {
    fields.value = []
  }
}

async function loadAttrs() {
  if (!props.layer?.id) return
  panelsLoading.value = true
  // 普通分页 = 无筛选条件（同步清理导出携带的查询条件）
  lastAttrQuery.value = {}
  try {
    // 返回 Result<PagedResultOutput>；分页参数为 pageSize / pageIndex
    const res = await layerContentApi.getAttrtableByLayerId(props.layer.id, attrPage.size, attrPage.index)
    const rows = pickList<any>(res)
    attrs.value = rows
    attrTotal.value = pickTotal(res, rows.length)
    const first = rows[0]
    if (first && typeof first === 'object') attrColumns.value = Object.keys(first)
    else if (fields.value.length) attrColumns.value = fields.value.map((f) => f.attributeName ?? f.fieldName ?? f.name)
  } catch (e: any) {
    attrs.value = []
    attrTotal.value = 0
  } finally {
    panelsLoading.value = false
  }
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
  if (!props.layer?.id) return
  attrSchema.loading = true
  try {
    // 裸返回 String：JSON 串 {LayerField:"a,b,c", DataTableJson:"{\"data\":[...]}"}
    const raw = await layerContentApi.getLayerAttrTabledDetail(
      props.layer.id,
      attrSchema.page.size,
      attrSchema.page.index,
    )
    const text = String(raw ?? '')
    let obj: any = null
    try {
      obj = text ? JSON.parse(text) : null
    } catch {
      obj = null
    }
    if (!obj) {
      attrSchema.fields = []
      attrSchema.columns = []
      attrSchema.rows = []
      attrSchema.total = 0
      return
    }
    attrSchema.fields = String(obj.LayerField ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
    let dataArr: any[] = []
    try {
      const dt = typeof obj.DataTableJson === 'string' ? JSON.parse(obj.DataTableJson) : obj.DataTableJson
      dataArr = dt?.data ?? dt ?? []
    } catch {
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
    attrSchema.fields = []
    attrSchema.columns = []
    attrSchema.rows = []
    attrSchema.total = 0
  } finally {
    attrSchema.loading = false
  }
}

async function loadRasterInfo() {
  if (!props.layer?.id) return
  try {
    rasterInfo.value = await layerToolApi.getTifInfo(props.layer.id)
  } catch {
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
    if (Number.isNaN(lon) || Number.isNaN(lat)) {
      ElMessage.warning('经度/纬度须为数字')
      return
    }
    const v: any = await layerContentApi.getLayerValue(props.layer.id, lon, lat)
    pixelProbe.value = v ?? null
    if (v == null) ElMessage.info('该坐标无像元值（或非栅格图层）')
  } catch (e: any) {
    ElMessage.error(e?.message || '取值失败')
  } finally {
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
  if (!props.layer?.id) return
  rasterStats.loading = true
  try {
    const r: any = await dataStyleApi.getTifMaxMin(props.layer.id, rasterStats.bandIndex)
    rasterStats.min = r?.min ?? r?.MIN ?? '-'
    rasterStats.max = r?.max ?? r?.MAX ?? '-'
  } catch (e: any) {
    ElMessage.error(e?.message || '读取极值失败')
  } finally {
    rasterStats.loading = false
  }
}

/** 唯一值：GET /dataStyle/getTifValues（像元值 > 50 时后端返回失败） */
async function loadTifValues() {
  if (!props.layer?.id) return
  rasterStats.loading = true
  try {
    const r: any = await dataStyleApi.getTifValues(props.layer.id, rasterStats.bandIndex)
    rasterStats.values = pickList(r)
  } catch (e: any) {
    ElMessage.error(e?.message || '读取唯一值失败（像元值过大时后端不支持）')
  } finally {
    rasterStats.loading = false
  }
}

/** 下载栅格原始文件：GET /layerContent/downloadTif?layerId（注意小写 id） */
async function downloadTifFile() {
  if (!props.layer?.id) return
  try {
    const url = await layerContentApi.downloadTif(props.layer.id)
    if (url) window.open(String(url), '_blank')
    else ElMessage.info('后端未返回下载地址')
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败')
  }
}

function startDraw(kind: 'Box' | 'Circle' | 'Polygon' | 'Point') {
  spatialMode.value = kind
  attrMapRef.value?.startDraw(kind)
}

async function onDrawEnd(payload: any) {
  try {
    // 点选走独立端点：POST /layerContent/pageAttrInfoByPoint（distance 单位：米）
    if (payload.kind === 'Point') {
      const ptWkt = `POINT(${payload.lon} ${payload.lat})`
      const res = await layerContentApi.pageAttrInfoByPoint({
        layerId: props.layer.id,
        layerIds: [props.layer.id],
        lon: payload.lon ?? 0,
        lat: payload.lat ?? 0,
        distance: pickTolerance.value,
      })
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
      layerId: props.layer.id,
      where: [],
      wkt: payload.wkt || '',
      distance: 0,
      lon: payload.lon ?? 0,
      lat: payload.lat ?? 0,
      layerIds: [props.layer.id],
    }
    if (payload.kind === 'Circle' && payload.radius) {
      query.distance = Math.round(payload.radius)
      // 圈选 = 点 + 半径（米）缓冲查询：后端按 POINT WKT + distance 路由到 ST_DWithin(geography)
      query.wkt = `POINT(${query.lon} ${query.lat})`
    }
    const res = await layerContentApi.pageAttrInfoBySwitch(query, attrPage.size, attrPage.index)
    const rows = pickList<any>(res?.records ?? res)
    attrs.value = rows
    attrTotal.value = pickTotal(res, rows.length)
    if (rows[0]) attrColumns.value = attrColumnsOf(rows[0])
    lastAttrQuery.value = {
      wkt: query.wkt,
      distance: query.distance || undefined,
      lon: query.lon || undefined,
      lat: query.lat || undefined,
    }
    ElMessage.success(`空间查询命中 ${attrTotal.value} 条`)
  } catch (e: any) {
    ElMessage.error(e?.message || '空间查询失败')
  }
}

/** 缓冲查询：以当前视图中心为圆心、按输入半径（米）做圆形缓冲查询 */
async function bufferQuery() {
  const center = attrMapRef.value?.getCenter?.()
  if (!center) {
    ElMessage.warning('地图尚未就绪')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('输入缓冲半径（米）', '缓冲查询', { inputValue: '500' })
    const radius = Number(String(value || '').trim())
    if (!Number.isFinite(radius) || radius <= 0) {
      ElMessage.warning('半径须为正数')
      return
    }
    const res = await layerContentApi.pageAttrInfoBySwitch(
      {
        layerId: props.layer.id,
        layerIds: [props.layer.id],
        wkt: `POINT(${center[0]} ${center[1]})`,
        distance: radius,
        lon: center[0],
        lat: center[1],
      },
      attrPage.size,
      attrPage.index,
    )
    const rows = pickList<any>(res?.records ?? res)
    attrs.value = rows
    attrTotal.value = pickTotal(res, rows.length)
    if (rows[0]) attrColumns.value = attrColumnsOf(rows[0])
    lastAttrQuery.value = { wkt: `POINT(${center[0]} ${center[1]})`, distance: radius, lon: center[0], lat: center[1] }
    ElMessage.success(`缓冲查询（${radius} 米）命中 ${attrTotal.value} 条`)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '缓冲查询失败')
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
  if (!props.layer?.id) return
  if (isRaster.value) {
    try {
      const v: any = await layerContentApi.getLayerValue(props.layer.id, p.lon, p.lat)
      attrMapRef.value?.showPopup(p.lon, p.lat, props.layer?.layerName || '栅格取值', [
        ['经度', String(p.lon)],
        ['纬度', String(p.lat)],
        ['像元值', v == null ? '无值（非栅格或坐标越界）' : String(v)],
      ])
    } catch (e: any) {
      ElMessage.error(e?.message || '取值失败')
    }
    return
  }
  try {
    const res = await layerContentApi.pageAttrInfoByPoint({
      layerId: props.layer.id,
      layerIds: [props.layer.id],
      lon: p.lon,
      lat: p.lat,
      distance: pickTolerance.value,
    })
    const first = Array.isArray(res) ? res[0] : res
    const rows = pickList<any>(first?.records ?? first)
    if (!rows.length) {
      attrMapRef.value?.hidePopup()
      ElMessage.info('该位置未命中要素（可调大点选容差）')
      return
    }
    attrs.value = rows
    attrTotal.value = rows.length
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
    ElMessage.error(e?.message || '点选查询失败')
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
  try {
    // 携带当前生效的查询条件：导出的是筛选结果，而非全表
    const url = await layerContentApi.downloadAttrInfoBySwitch({
      layerId: props.layer.id,
      layerIds: [props.layer.id],
      ...lastAttrQuery.value,
    })
    if (url) window.open(String(url), '_blank')
    else ElMessage.info('后端未返回下载地址')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

async function exportShp() {
  try {
    const p = await layerContentApi.outputShpFileData(props.layer.id)
    ElMessage.success('导出成功：' + p)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

/** 导出 TIF：GET /layerContent/OutputTifFileData?layerID */
async function exportTif() {
  try {
    const p = await layerContentApi.outputTifFileData(props.layer.id)
    ElMessage.success('导出成功：' + p)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

/** 字段名列取值（后端 LayerFieldEntity.attributeName） */
function fieldNameOf(row: any): string {
  return String(row?.attributeName ?? row?.fieldName ?? row?.columnName ?? row?.name ?? '')
}

/** 新增字段（LayerField/Insert，Body: LayerFieldEntity{layerID, attributeName, attributeType}） */
async function addField() {
  try {
    const { value } = await ElMessageBox.prompt('输入新增字段名', '新增字段')
    if (!value) return
    await layerFieldApi.insert({
      layerID: props.layer.id,
      attributeName: value,
      attributeType: '1',
    })
    ElMessage.success('字段已新增')
    loadFields()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '新增字段失败')
  }
}

/** 新增字段并同步落库（LayerField/InsertSync，会在属性表执行 ALTER TABLE） */
async function addFieldSync() {
  try {
    const { value } = await ElMessageBox.prompt(
      '输入新增字段名（将同步修改属性表结构）',
      '新增字段并同步',
    )
    if (!value) return
    await layerFieldApi.insertSync({
      layerID: props.layer.id,
      attributeName: value,
      attributeType: '1',
    })
    ElMessage.success('字段已新增并同步至属性表')
    loadFields()
    loadAttrs()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '同步新增字段失败')
  }
}

/** 删除单个字段（LayerField/Delete? id） */
async function removeField(row: any) {
  const id = String(row?.id ?? '')
  if (!id) {
    ElMessage.warning('该字段缺少 id，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除字段「${fieldNameOf(row)}」？`, '删除字段', { type: 'warning' })
  } catch {
    return
  }
  try {
    await layerFieldApi.remove(id)
    ElMessage.success('字段已删除')
    loadFields()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除字段失败')
  }
}

/** 清空该图层全部字段定义（LayerField/DeleteByLayerID? layerID） */
async function removeAllFields() {
  try {
    await ElMessageBox.confirm('确认清空该图层的全部字段定义？', '清空字段', { type: 'warning' })
  } catch {
    return
  }
  try {
    await layerFieldApi.deleteByLayerID(props.layer.id)
    ElMessage.success('字段定义已清空')
    loadFields()
  } catch (e: any) {
    ElMessage.error(e?.message || '清空失败')
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
    const ok = await layerContentApi.validateCondition(props.layer.id, conds)
    if (ok === false) {
      ElMessage.error('查询条件未通过后端校验')
      return
    }
    // where 为 JSON 串：GET /layerContent/GetLayerAttrTableByWhere（裸返回分页）
    const res = await layerContentApi.getLayerAttrTableByWhere(
      props.layer.id,
      JSON.stringify(conds),
      attrPage.size,
      1,
    )
    const rows = pickList<any>(res)
    attrs.value = rows
    attrTotal.value = pickTotal(res, rows.length)
    if (rows[0]) attrColumns.value = Object.keys(rows[0])
    lastAttrQuery.value = { where: conds }
    whereVisible.value = false
    ElMessage.success(`条件筛选命中 ${attrTotal.value} 条`)
  } catch (e: any) {
    ElMessage.error(e?.message || '条件筛选失败')
  } finally {
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
  attrColumns.value.filter((c) => !/geom|the_geom|shape/i.test(c)),
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
  if (!rowGid(editForm.value)) {
    ElMessage.warning('该记录缺少 gid，无法更新')
    return
  }
  editSaving.value = true
  try {
    await layerContentApi.updateAttrtable({
      id: props.layer.id,
      attrtable: JSON.stringify([editForm.value]),
    })
    ElMessage.success('记录已保存')
    editVisible.value = false
    loadAttrs()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
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
    await layerContentApi.insertAttrtable({
      id: props.layer.id,
      attrtable: JSON.stringify([rowObj]),
    } as any)
    ElMessage.success('记录已新增')
    addVisible.value = false
    loadAttrs()
  } catch (e: any) {
    ElMessage.error(e?.message || '新增失败')
  } finally {
    addSaving.value = false
  }
}

/* ---------------- 删除记录（DeleteAttrtable? id + gid） ---------------- */
async function removeRecord() {
  if (!currentRow.value) {
    ElMessage.warning('请先在属性表中点选一行记录')
    return
  }
  const gid = currentRow.value.gid ?? currentRow.value.GID ?? currentRow.value.id ?? currentRow.value.ID
  if (!gid) {
    ElMessage.warning('该记录缺少 gid，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm('确认删除所选记录？该操作不可撤销。', '删除记录', { type: 'warning' })
  } catch {
    return
  }
  try {
    await layerContentApi.deleteAttrtable(props.layer.id, String(gid))
    ElMessage.success('记录已删除')
    currentRow.value = null
    loadAttrs()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
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
    await layerContentApi.updateAttrtableList(list)
    ElMessage.success(`已批量修改 ${list.length} 条记录`)
    multiVisible.value = false
    loadAttrs()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量修改失败')
  } finally {
    multiSaving.value = false
  }
}

/* ---------------- 编辑资源（LayerContent/Update，Body: LayerContentInputDto） ---------------- */
const layerEditVisible = ref(false)
const layerSaving = ref(false)
const layerForm = ref<Record<string, any>>({})

function openEditLayer() {
  const l = props.layer || {}
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
  const nm = String(layerForm.value.layerName || '').trim()
  if (!nm) {
    ElMessage.warning('请输入资源名称')
    return
  }
  layerSaving.value = true
  try {
    await layerContentApi.update({ ...layerForm.value, layerName: nm })
    ElMessage.success('资源已保存')
    layerEditVisible.value = false
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
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
  let parsed: any
  try {
    parsed = JSON.parse(featJson.value)
  } catch {
    ElMessage.error('GeoJSON 格式不合法（需为合法 JSON）')
    return
  }
  featSaving.value = true
  try {
    await layerContentApi.editVectorFeatures({
      layerId: props.layer.id,
      geoJsonCollection: parsed,
    })
    ElMessage.success('要素编辑已保存')
    featVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    featSaving.value = false
  }
}

/* ---------------- 样式 ---------------- */
const styleInfo = ref<any>(null)
async function loadStyle() {
  if (!props.layer?.id) return
  try {
    // 无绑定时后端返回 null（原实现返回空对象，导致「暂无样式」空态不展示）
    styleInfo.value = await dataStyleApi.getDetailByLayerID(props.layer.id)
  } catch {
    styleInfo.value = null
  }
}

/* 样式绑定 / 解绑：
 * 样式侧 styleDefaultLayer ← dataStyle/Update；图层侧 layerDefaultStyle ← layerContent/Update */
const styleOptions = ref<any[]>([])
const styleBindId = ref('')
const styleBinding = ref(false)
async function loadStyleOptions() {
  try {
    const res = await dataStyleApi.getAllListPage({ styleName: '' }, 50, 1)
    styleOptions.value = pickList<any>(res)
  } catch {
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
  if (!props.layer?.id || !styleBindId.value) {
    ElMessage.warning('请先选择样式')
    return
  }
  styleBinding.value = true
  try {
    const opt = styleOptions.value.find((s) => s.id === styleBindId.value)
    await dataStyleApi.update({
      id: styleBindId.value,
      styleConfigType: opt?.styleConfigType,
      styleDefaultLayer: props.layer.id,
    })
    // 解绑此前绑定到该图层的其它样式
    if (styleInfo.value?.id && styleInfo.value.id !== styleBindId.value) {
      await dataStyleApi.update({
        id: styleInfo.value.id,
        styleConfigType: styleInfo.value.styleConfigType,
        styleDefaultLayer: '',
      })
    }
    await layerContentApi.update({ id: props.layer.id, layerDefaultStyle: styleBindId.value })
    ElMessage.success('已绑定样式')
    await loadStyle()
  } catch (e: any) {
    ElMessage.error(e?.message || '绑定失败')
  } finally {
    styleBinding.value = false
  }
}
async function unbindStyle() {
  if (!props.layer?.id || !styleInfo.value?.id) return
  styleBinding.value = true
  try {
    await dataStyleApi.update({
      id: styleInfo.value.id,
      styleConfigType: styleInfo.value.styleConfigType,
      styleDefaultLayer: '',
    })
    await layerContentApi.update({ id: props.layer.id, layerDefaultStyle: '' })
    ElMessage.success('已解除绑定')
    await loadStyle()
  } catch (e: any) {
    ElMessage.error(e?.message || '解绑失败')
  } finally {
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
  try {
    const linear = pickList<any>(await dataStyleApi.getLinearColorRamps())
    let random: any[] = []
    try {
      random = pickList<any>(await dataStyleApi.getRandomColorRamps())
    } catch {
      random = []
    }
    ramps.value = [...linear, ...random].map((r: any) => ({
      name: r.colorName ?? r.name ?? '-',
      colors: r.colors ?? r.colorList ?? (typeof r.colorJson === 'string' ? JSON.parse(r.colorJson) : []) ?? [],
    })).filter((r) => r.colors.length)
  } catch {
    ramps.value = []
  }
}

/** 渲染分析统一入口：按矢量/栅格路由到对应端点 */
async function runRender(kind: 'minmax' | 'values' | 'breaks' | 'attrlist') {
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
        ? await dataStyleApi.getTifMaxMin(props.layer.id, renderState.bandIndex)
        : await dataStyleApi.getShpMaxMin(props.layer.id, renderState.attr)
      renderState.minmax = { min: r?.min ?? '-', max: r?.max ?? '-' }
    } else if (kind === 'values') {
      const r: any = await dataStyleApi.getTifValues(props.layer.id, renderState.bandIndex)
      renderState.values = pickList(r)
    } else if (kind === 'breaks') {
      const r: any = isRaster.value
        ? await dataStyleApi.getTifJenksBreaks(props.layer.id, renderState.lvNum, renderState.bandIndex)
        : await dataStyleApi.getShpJenksBreaks(props.layer.id, renderState.attr, renderState.lvNum)
      renderState.breaks = pickList(r)
    } else {
      // 矢量字段取值列表：GET /dataStyle/GetDataAttribute
      const r: any = await dataStyleApi.getDataAttribute(props.layer.id, renderState.attr)
      renderState.attrList = pickList(r)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '渲染分析失败')
  } finally {
    renderState.loading = false
  }
}

/** 另存为样式：POST /dataStyle/Insert（先 IsExists 重名校验） */
async function saveAsStyle() {
  try {
    const { value } = await ElMessageBox.prompt('输入样式名称', '另存为样式')
    const name = String(value || '').trim()
    if (!name) return
    const exists: any = await dataStyleApi.isExists(name)
    if (exists === true || exists === 'true') {
      ElMessage.error('样式名已存在')
      return
    }
    await dataStyleApi.insert({
      styleName: name,
      name,
      styleType: isRaster.value ? '2' : '1',
      renderType: '分级设色',
      layerId: props.layer.id,
      layerAttr: isRaster.value ? String(renderState.bandIndex ?? 1) : renderState.attr,
      colorRamp: ramps.value[0]?.name ?? '',
      createBy: userStore.userName,
    })
    ElMessage.success('已另存为样式')
    loadStyle()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '另存失败')
  }
}

/* ---------------- 服务与发布 ---------------- */
const serviceInfo = ref<any>(null)
const svcList = ref<{ type: string; url: string }[]>([])
const relatedMaps = ref<any[]>([])
const publishLoading = ref(false)

/** 探测全部可用服务（矢量：WMS/WFS/MVT；栅格：WMS/WMTS） */
async function loadService() {
  if (!props.layer?.id) return
  try {
    const types = isRaster.value ? ['WMS', 'WMTS'] : ['WMS', 'WFS', 'MVT']
    const results = await Promise.allSettled(types.map((t) => serverInterfaceApi.getServerInterface(props.layer.id, t)))
    const list: { type: string; url: string }[] = []
    results.forEach((r, i) => {
      if (r.status !== 'fulfilled') return
      const url = extractSvcUrl((r as PromiseFulfilledResult<any>).value, types[i])
      if (url) list.push({ type: types[i], url })
    })
    svcList.value = list
    // 兼容保留：首个服务的原始返回（发布类型等）
    serviceInfo.value = results[0].status === 'fulfilled' ? (results[0] as PromiseFulfilledResult<any>).value : null
  } catch {
    svcList.value = []
    serviceInfo.value = null
  }
}

async function doPublish() {
  publishLoading.value = true
  try {
    await serverInterfaceApi.publishService({
      id: props.layer.id,
      layerName: props.layer.layerName,
      publishType: 'WMS',
    })
    ElMessage.success('已发布为 OGC 服务')
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    publishLoading.value = false
  }
}

/** 复制服务地址 */
async function copySvcUrl(url: string) {
  if (!url) return
  ;(await copyText(url)) ? ElMessage.success('地址已复制') : ElMessage.error('复制失败')
}

async function loadRelatedMaps() {
  try {
    relatedMaps.value = pickList(await mapRelationApi.getAllListBylayerID(props.layer.id))
  } catch {
    relatedMaps.value = []
  }
}

function copy(t: string) {
  if (!t) return
  void copyText(t)
  ElMessage.success('已复制')
}

/* ---------------- 元数据 ---------------- */
const meta = ref<any>({})
async function loadMeta() {
  try {
    meta.value = (await metadataApi.getDetailByDataId(props.layer.id)) || {}
  } catch {
    meta.value = {}
  }
}
async function saveMeta() {
  try {
    const body = { ...meta.value, dataId: props.layer.id, id: meta.value.id }
    if (meta.value.id) await metadataApi.update(body)
    else await metadataApi.insert(body)
    ElMessage.success('元数据已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

/** 删除元数据：POST /metadata/Delete?id */
async function removeMeta() {
  if (!meta.value?.id) {
    ElMessage.info('该资源尚无元数据记录')
    return
  }
  try {
    await ElMessageBox.confirm('确定删除该资源的元数据？', '删除确认', { type: 'warning' })
    await metadataApi.remove(meta.value.id)
    ElMessage.success('元数据已删除')
    loadMeta()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

/** 按数据 ID 删除全部元数据：POST /metadata/DeleteByDataId?dataId */
async function removeMetaByDataId() {
  try {
    await ElMessageBox.confirm('按数据 ID 删除该资源的全部元数据记录？', '删除确认', { type: 'warning' })
    await metadataApi.deleteByDataId(props.layer.id)
    ElMessage.success('元数据已按数据 ID 删除')
    meta.value = {}
    loadMeta()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

/* ---------------- 活动 ---------------- */
const logs = ref<any[]>([])
async function loadLogs() {
  try {
    const res = await layerLogApi.getList(50, 1)
    const rows = pickList<any>(res)
    // 后端无按资源过滤参数，前端过滤 + 兜底展示最近日志
    const filtered = rows.filter(
      (r) => String(r.dataId ?? r.objectId ?? r.businessId ?? '') === String(props.layer?.id),
    )
    logs.value = filtered.length ? filtered : rows.slice(0, 20)
  } catch {
    logs.value = []
  }
}

/* ---------------- 页签切换加载 ---------------- */
watch(tab, (t) => {
  if (!props.layer) return
  if (t === 'overview') loadOverview()
  if (t === 'data') {
    if (isRaster.value) loadRasterInfo()
    else loadFields()
    loadAttrs()
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
})

function loadOverview() {
  const b = bbox.value
  if (b) setTimeout(() => overviewMapRef.value?.fitExtent(b), 300)
}

watch(
  () => props.layer,
  () => {
    if (props.layer) {
      resetPanels()
      if (tab.value === 'overview') loadOverview()
    }
  },
)
</script>

<template>
  <el-drawer v-model="visible" size="760px" :with-header="false" destroy-on-close>
    <div v-if="layer" class="detail">
      <div class="d-head">
        <div class="dh-main">
          <div class="dh-title">
            {{ layer.layerName }}
            <span class="tag" :class="isRaster ? 'orange' : 'blue'">{{ layerTypeName(layer.dataType) }}</span>
            <span class="tag" :class="String(layer.publishStatus) === '1' ? 'green' : 'gray'">
              {{ String(layer.publishStatus) === '1' ? '已发布' : '未发布' }}
            </span>
          </div>
          <div class="dh-sub mono">ID: {{ layer.id }}</div>
        </div>
        <div class="row" style="gap: 6px">
          <el-button class="btn sm" @click="openEditLayer"><Icon name="edit" :size="13" /> 编辑资源</el-button>
          <el-button class="btn sm" @click="visible = false"><Icon name="x" :size="13" /> 关闭</el-button>
        </div>
      </div>

      <el-tabs v-model="tab" class="d-tabs">
        <!-- 概览 -->
        <el-tab-pane label="概览" name="overview">
          <div class="kv mb8">
            <span class="k">资源名称</span><span class="v">{{ layer.layerName }}</span>
            <span class="k">类型</span><span class="v">{{ layerTypeName(layer.dataType) }}</span>
            <span class="k">所属分类</span><span class="v">{{ layer.layerTypeName || layer.layerType || '-' }}</span>
            <span class="k">空间参考</span><span class="v">{{ layer.layerRefenceName || layer.layerRefence || '-' }}</span>
            <span class="k">空间表</span><span class="v mono">{{ layer.layerSpatialTable || '-' }}</span>
            <span class="k">属性表</span><span class="v mono">{{ layer.layerAttrTable || '-' }}</span>
            <span class="k">四至</span><span class="v mono">{{ layer.layerBBox || '-' }}</span>
            <span class="k">文件大小</span><span class="v">{{ fmtSize(layer.fileSize) }}</span>
            <span class="k">上传状态</span><span class="v">{{ layer.uploadStatus === '1' || layer.uploadStatus === 1 ? '已入库' : '未入库' }}</span>
            <span class="k">创建人 / 时间</span><span class="v">{{ layer.createBy || '-' }} · {{ fmtTime(layer.createDT) }}</span>
            <span class="k">描述</span><span class="v">{{ layer.layerDesc || '-' }}</span>
          </div>
          <div class="map-box">
            <Map2D ref="overviewMapRef" :zoom="12" :center="bbox ? [(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2] : [113.9042, 29.7234]" />
          </div>
        </el-tab-pane>

        <!-- 数据与要素 -->
        <el-tab-pane label="数据与要素" name="data">
          <template v-if="isRaster">
            <div class="card">
              <div class="card-h">
                栅格结构<span class="cnt">/layerTool/getTifInfo</span>
                <div class="tools"><el-button class="btn xs" @click="downloadTifFile">下载 TIF</el-button></div>
              </div>
              <div class="card-b">
                <div v-if="rasterInfo" class="kv">
                  <span class="k">列数 xsize</span><span class="v">{{ rasterInfo.xsize ?? '-' }}</span>
                  <span class="k">行数 ysize</span><span class="v">{{ rasterInfo.ysize ?? '-' }}</span>
                  <span class="k">像元 xCell</span><span class="v">{{ rasterInfo.xCell ?? '-' }} 米</span>
                  <span class="k">像元 yCell</span><span class="v">{{ rasterInfo.yCell ?? '-' }} 米</span>
                </div>
                <el-empty v-else description="未获取到栅格元信息（GDAL 不可用或图层非栅格）" :image-size="60" />
              </div>
            </div>

            <!-- 像元统计：getTifMaxMin / getTifValues -->
            <div class="card">
              <div class="card-h">
                像元统计<span class="cnt">/dataStyle/getTifMaxMin · getTifValues</span>
                <div class="tools">
                  <el-input-number v-model="rasterStats.bandIndex" :min="1" size="small" placeholder="波段" style="width: 96px" />
                  <el-button class="btn xs" :loading="rasterStats.loading" @click="loadTifMaxMin">读取极值</el-button>
                  <el-button class="btn xs" :loading="rasterStats.loading" @click="loadTifValues">读取唯一值</el-button>
                </div>
              </div>
              <div class="card-b">
                <div class="kv">
                  <span class="k">极值</span>
                  <span class="v mono">min = {{ rasterStats.min || '-' }} ／ max = {{ rasterStats.max || '-' }}</span>
                  <span class="k">唯一值</span>
                  <span class="v mono">{{ rasterStats.values.length ? rasterStats.values.slice(0, 20).join(' , ') : '—' }}</span>
                </div>
                <div class="muted" style="font-size: 11.5px; margin-top: 6px">
                  注意：像元值 &gt; 50 时唯一值接口后端返回失败，需改用区间分类。
                </div>
              </div>
            </div>

            <!-- 坐标取值：getLayerValue -->
            <div class="card">
              <div class="card-h">
                坐标取值<span class="cnt">/layerContent/getLayerValue</span>
              </div>
              <div class="card-b">
                <div class="row wrap" style="gap: 8px; align-items: center">
                  <el-input v-model="pixelProbe.lon" size="small" placeholder="经度 longitude" style="width: 170px" />
                  <el-input v-model="pixelProbe.lat" size="small" placeholder="纬度 latitude" style="width: 170px" />
                  <el-button class="btn xs" :loading="pixelProbe.loading" @click="probeLayerValue">读取像元值</el-button>
                </div>
                <div class="kv mt8">
                  <span class="k">像元值</span>
                  <span class="v mono">{{ pixelProbe.value === '' ? '—' : pixelProbe.value === null ? 'null（无值/非栅格）' : pixelProbe.value }}</span>
                </div>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="card mb8">
              <div class="card-h">
                字段定义<span class="cnt">{{ fields.length }}</span>
                <div class="tools">
                  <el-button class="btn xs" @click="addField">新增字段</el-button>
                  <el-button class="btn xs" @click="addFieldSync">新增字段并同步</el-button>
                  <el-button class="btn xs danger" @click="removeAllFields">清空字段</el-button>
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
                    <el-button class="btn xs danger" @click="removeField(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty><span class="muted">暂无字段定义</span></template>
              </el-table>
            </div>
          </template>

          <div class="card">
            <div class="card-h">
              属性表<span class="cnt">{{ attrTotal }}</span>
              <div class="tools">
                <el-button class="btn xs" @click="startDraw('Box')">框选</el-button>
                <el-button class="btn xs" @click="startDraw('Circle')">圈选</el-button>
                <el-button class="btn xs" @click="startDraw('Polygon')">多边选</el-button>
                <el-button class="btn xs" @click="startDraw('Point')">点选</el-button>
                <el-button class="btn xs" @click="bufferQuery">缓冲查询</el-button>
                <el-button class="btn xs" @click="openWhere">条件筛选</el-button>
                <el-button class="btn xs primary" @click="openAddRecord">新增记录</el-button>
                <el-button class="btn xs" @click="openEditRecord">编辑记录</el-button>
                <el-button class="btn xs" @click="openMultiEdit">批量修改</el-button>
                <el-button class="btn xs danger" @click="removeRecord">删除记录</el-button>
                <el-button v-if="!isRaster" class="btn xs" @click="openFeatureEdit">要素编辑</el-button>
                <el-button class="btn xs" @click="loadAttrs">刷新</el-button>
                <el-button class="btn xs" @click="exportAttr">导出查询</el-button>
                <el-button v-if="!isRaster" class="btn xs" @click="exportShp">导出 SHP</el-button>
                <el-button v-if="isRaster" class="btn xs" @click="exportTif">导出 TIF</el-button>
              </div>
            </div>
            <div class="map-box sm mb8">
              <Map2D ref="attrMapRef" :zoom="12" :center="bbox ? [(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2] : [113.9042, 29.7234]" @drawend="onDrawEnd" @pick="onMapPick" />
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
              提示：单击地图可直接查询要素属性（气泡 + 高亮，容差可调）；「圈选」为圆形缓冲（半径按米）；「缓冲查询」以视图中心为圆心；「导出查询」导出当前筛选结果；勾选多行可「批量修改」；「新增记录」需填 geom(WKT)。
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
        <el-tab-pane label="表结构" name="schema">
          <div class="card">
            <div class="card-h">
              原始属性表<span class="cnt">/layerContent/GetLayerAttrTabledDetail</span>
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
        <el-tab-pane label="样式" name="style">
          <div class="card">
            <div class="card-h">
              当前样式<span class="cnt">/dataStyle/GetDetailByLayerID</span>
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
                <div class="muted" style="font-size: 11.5px; margin-bottom: 4px">栅格分级（tifStyles · {{ styleInfo.tifStyles.length }} 级）</div>
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
                  v-model="styleBindId"
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
              渲染分析<span class="cnt">{{ isRaster ? 'getTifMaxMin · getTifValues · getTifJenksBreaks' : 'getShpMaxMin · getShpJenksBreaks · GetDataAttribute' }}</span>
              <div class="tools">
                <el-button class="btn xs primary" @click="saveAsStyle">另存为样式</el-button>
              </div>
            </div>
            <div class="card-b">
              <div class="filters" style="margin-bottom: 8px">
                <template v-if="!isRaster">
                  <el-select v-model="renderState.attr" placeholder="渲染字段" filterable style="width: 160px">
                    <el-option
                      v-for="f in fields"
                      :key="f.id ?? f.attributeName"
                      :label="f.attributeName ?? f.fieldName ?? f.name"
                      :value="f.attributeName ?? f.fieldName ?? f.name"
                    />
                  </el-select>
                </template>
                <template v-else>
                  <el-input-number v-model="renderState.bandIndex" :min="1" size="small" placeholder="波段" style="width: 110px" />
                </template>
                <el-input-number v-model="renderState.lvNum" :min="2" :max="12" size="small" style="width: 110px" />
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
                <div class="muted" style="font-size: 11.5px; margin-bottom: 5px">色带库（{{ ramps.length }}）— GetLinearColorRamps / GetRandomColorRamps</div>
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
        <el-tab-pane label="服务与发布" name="service">
          <div class="card mb8">
            <div class="card-h">
              服务发布<span class="cnt">/serverInterface</span>
              <div class="tools">
                <el-button class="btn xs" @click="loadService">查询服务</el-button>
                <el-button class="btn xs primary" :loading="publishLoading" @click="doPublish">发布为服务</el-button>
              </div>
            </div>
            <div class="card-b">
              <div v-if="!svcList.length" class="muted">尚未发布或未查询到服务信息。发布底层走 GeoServer REST（工作区 sdms / 数据存储 sdms_pg / EPSG:4326）。</div>
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
        <el-tab-pane label="元数据" name="meta">
          <div class="card">
            <div class="card-h">
              元数据<span class="cnt">/metadata/GetDetailByDataId</span>
              <div class="tools">
                <el-button class="btn xs" @click="loadMeta">重新加载</el-button>
                <el-button class="btn xs danger" @click="removeMeta">删除</el-button>
                <el-button class="btn xs danger" @click="removeMetaByDataId">按数据ID删除</el-button>
                <el-button class="btn xs primary" @click="saveMeta">保存</el-button>
              </div>
            </div>
            <div class="card-b">
              <el-form label-width="120px" size="small">
                <el-form-item label="数据 ID">
                  <el-input :model-value="layer.id" disabled />
                </el-form-item>
                <el-form-item label="元数据名称">
                  <el-input v-model="meta.metaName" placeholder="metaName" />
                </el-form-item>
                <el-form-item label="摘要">
                  <el-input v-model="meta.metaAbstract" type="textarea" :rows="3" placeholder="metaAbstract" />
                </el-form-item>
                <el-form-item label="关键字">
                  <el-input v-model="meta.metaKeywords" placeholder="metaKeywords" />
                </el-form-item>
                <el-form-item label="生产单位">
                  <el-input v-model="meta.produceOrg" placeholder="produceOrg" />
                </el-form-item>
                <el-form-item label="坐标系">
                  <el-input v-model="meta.coordSystem" placeholder="coordSystem" />
                </el-form-item>
              </el-form>
            </div>
          </div>
        </el-tab-pane>

        <!-- 活动 -->
        <el-tab-pane label="活动" name="activity">
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
        <el-button class="btn xs" @click="addWhereRow">＋ 加一条</el-button>
        <div class="muted" style="font-size: 11.5px; margin-top: 8px">
          提交链路：POST /layerContent/ValidateCondition 校验 → GET /layerContent/GetLayerAttrTableByWhere 查询（where 为 JSON 串）。
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
          提交：POST /layerContent/InsertAttrtable，Body: {id, attrtable:'[{字段..., geom:"WKT"}]'}（geom 必填）。
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
          提交：POST /layerContent/editVectorFeatures，Body: {layerId, geoJsonCollection}。
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
