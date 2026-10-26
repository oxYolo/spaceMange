<script setup lang="ts">
type StyleFileEntry = { name: string; path: string }
import { showError } from '@/utils/feedback'
/**
 * 样式库 —— DataStyleController（35 端点，全量接通）
 * 关联端点依据：
 *  - GET  /dataStyle/GetAllList / GetAllListPage     列表 / 分页（name 可传空串）
 *  - POST /dataStyle/IsExists                        样式名重复校验
 *  - GET  /dataStyle/GetDetailById                   样式详情（styleInfo / ruleDatas / tifStyles）
 *  - GET  /dataStyle/GetDetailByLayerID              按图层取当前样式
 *  - POST /dataStyle/Insert / Update                 新建 / 保存（按 styleConfigType 分流）
 *  - POST /dataStyle/Delete / DeleteById             删除 / 批量删除
 *  - GET  /dataStyle/GetXmlContent                   读取 SLD 原文（GET + Body）
 *  - POST /dataStyle/InsertGeoServerStyle            推送 GeoServer
 *  - POST /dataStyle/ParsingSld / Parsethefile       SLD 反向解析 / 按文件路径解析
 *  - GET  /dataStyle/GetLinearColorRamps[ByName]     色带库 / 按名取 N 色
 *  - GET  /dataStyle/GetRandomColorRamps[ByName]     随机色带库 / 按名取 N 色
 *  - GET  /dataStyle/GetAllFolders / GetAllFiles     样式文件夹 / 文件
 *  - POST /dataStyle/AddFolder / UpdateFolder / DeleteFolder / UpdateFile / DeleteFile
 *  - GET  /dataStyle/getTifMaxMin / getTifJenksBreaks / getTifValues     栅格统计
 *  - GET  /dataStyle/getShpMaxMin / getShpJenksBreaks / GetDataAttribute 矢量统计
 *  - GET  /dataStyle/GetDataAttributesPage / Sort / GetDataAttributesById 属性分级预览
 *  - GET  /mapreleation/GetMapReleationByStyle       删除前引用校验（MapRelationController）
 *  - POST /layerContent/Update                        图层绑定样式（layerDefaultStyle）
 *
 * styleConfigType（后端约定）：
 *  1 单一符号（styleInfo） | 2 分级设色/唯一值（ruleDatas+symbol） | 3 灰度拉伸（SLD 文本）
 *  4 单波段（tifStyles+layerId） | 5 三波段（tifThreeColor+layerId） | 6 多波段（tifStyles+layerId+bandIndex）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { dataStyleApi, layerContentApi, layerFieldApi, mapRelationApi } from '@/api'
import { fmtTime, pickList, pickTotal } from '@/utils/format'
import { isRasterDataType, layerDataTypeName, LAYER_GEOM_IDS } from '@/utils/datasets'
import { useUserStore } from '@/auth/store'

const userStore = useUserStore()

interface StyleRow {
  id: string
  styleName?: string
  name?: string
  styleType?: string | number
  styleConfigType?: string
  renderType?: string
  renderStyle?: string
  styleDataType?: string
  styledatatype?: string
  styleRenderField?: string
  styleRenderFieldName?: string
  styleRenderColorBand?: string
  styleDefaultLayer?: string
  layerAttr?: string
  createBy?: string
  createUser?: string
  createDT?: string
  createTime?: string
  colorRamp?: any
  colorRamps?: any
  colorRampName?: string
  styleContent?: string
  [k: string]: any
}

/* ------------------------- 常量 ------------------------- */
/** 适用类型（styleDataType = 几何/数据类型字典 id，与图层 datatype 同组） */
const GEOM_OPTIONS = [
  { value: LAYER_GEOM_IDS.point, label: '点' },
  { value: LAYER_GEOM_IDS.line, label: '线' },
  { value: LAYER_GEOM_IDS.polygon, label: '面' },
  { value: LAYER_GEOM_IDS.raster, label: '影像' },
]
/** 渲染方式定义：key 前端使用，configType 透传后端 */
const RENDER_DEFS: { key: string; label: string; configType: string; raster: boolean; hint: string }[] = [
  { key: 'single', label: '单一符号', configType: '1', raster: false, hint: '按点/线/面配置一套符号参数' },
  { key: 'graduated', label: '分级设色', configType: '2', raster: false, hint: '按数值字段自然断点分级，逐级配色' },
  { key: 'unique', label: '唯一值', configType: '2', raster: false, hint: '按字段取值逐个配色' },
  { key: 'rasterRamp', label: '灰度拉伸', configType: '3', raster: true, hint: '直接编辑栅格 SLD 文本' },
  { key: 'rasterSingle', label: '单波段拉伸', configType: '4', raster: true, hint: '按断点生成 ColorMap，直接绑定图层并推送 GeoServer' },
  { key: 'rasterThree', label: '三波段', configType: '5', raster: true, hint: '设置透明色，直接绑定图层并推送 GeoServer' },
  { key: 'rasterMulti', label: '多波段', configType: '6', raster: true, hint: '按波段与断点生成 ColorMap，直接绑定图层' },
]
const CONFIG_TYPE_LABEL: Record<string, string> = {
  '1': '单一符号',
  '2': '分级/唯一值',
  '3': '灰度拉伸',
  '4': '单波段',
  '5': '三波段',
  '6': '多波段',
}
const LINE_TYPES = [
  { label: '实线', value: 1 },
  { label: '虚线', value: 2 },
  { label: '点线', value: 3 },
  { label: '虚点线', value: 4 },
]
const POINT_TYPES = [
  { label: '圆形', value: 'circle' },
  { label: '方形', value: 'square' },
  { label: '三角形', value: 'triangle' },
  { label: '星形', value: 'star' },
]
const defOf = (key: string) => RENDER_DEFS.find((d) => d.key === key) || RENDER_DEFS[0]
const geomNum = (id: string) => (id === LAYER_GEOM_IDS.point ? '1' : id === LAYER_GEOM_IDS.line ? '2' : '3')

/* ------------------------- 列表 ------------------------- */
const loading = ref(false)
const list = ref<StyleRow[]>([])
const keyword = ref('')
const current = ref<StyleRow | null>(null)
const total = ref(0)
const page = reactive({ index: 1, size: 10 })
const selection = ref<StyleRow[]>([])

function sName(row: any): string {
  return row?.styleName ?? row?.name ?? '-'
}
function typeLabel(row: any): string {
  const v = String(row?.styleDataType ?? row?.styledatatype ?? '')
  if (!v) return '—'
  const n = layerDataTypeName(v)
  return n === '-' ? '—' : n
}
function renderLabel(row: any): string {
  return row?.renderType || row?.renderStyle || CONFIG_TYPE_LABEL[String(row?.styleConfigType ?? '')] || '-'
}

async function load() {
  loading.value = true
  try {
    const res = await dataStyleApi.getAllListPage(
      { styleName: keyword.value.trim() || '' },
      page.size,
      page.index,
    )
    list.value = pickList<StyleRow>(res)
    total.value = pickTotal(res, list.value.length)
    if (current.value) {
      current.value = list.value.find((r) => r.id === current.value?.id) || null
    }
  } catch (e: any) {
    showError(e, '样式列表加载失败')
  } finally {
    loading.value = false
  }
}
function search() {
  page.index = 1
  load()
}
function onCurrentChange(row: StyleRow | null) {
  current.value = row
}
function onSelectionChange(rows: StyleRow[]) {
  selection.value = rows
}

/* ------------------------- 色带 ------------------------- */
const colorRamps = ref<any[]>([])

async function loadRamps() {
  try {
    colorRamps.value = pickList(await dataStyleApi.getLinearColorRamps())
  } catch {
    /* 色带库不可用时不阻断主流程 */
  }
}
function rampName(r: any): string {
  if (r == null) return ''
  if (typeof r === 'string') return r
  return r.name ?? r.colorName ?? r.colorRampName ?? r.rampName ?? ''
}
const rampOptions = computed(() =>
  colorRamps.value
    .map((r) => ({ label: rampName(r), value: rampName(r) }))
    .filter((o) => !!o.value),
)
function findRamp(name: string): any {
  return colorRamps.value.find((r) => rampName(r) === name)
}
function splitColors(v: any): string[] {
  if (v == null) return []
  if (Array.isArray(v)) return v.map((c) => String(c)).filter(Boolean)
  const s = String(v).trim()
  if (!s) return []
  const parts = s.split(/[,;|\s]+/).map((t) => t.trim()).filter(Boolean)
  return parts.length ? parts : [s]
}
const COLOR_RE = /^(#[0-9a-f]{3,8}|rgba?\(|hsla?\()/i
function validColors(cs: string[]): string[] {
  return cs.filter((c) => COLOR_RE.test(c))
}
/** java.awt.Color 的序列化兼容：{red,green,blue} / {value} / [r,g,b] / '#hex' */
function rgbHex(c: any): string {
  if (c == null) return ''
  if (typeof c === 'string') return COLOR_RE.test(c) ? c : ''
  if (Array.isArray(c)) {
    const [r, g, b] = c.map((n) => Number(n) || 0)
    return '#' + [r, g, b].map((n) => n.toString(16).padStart(2, '0')).join('')
  }
  if (c.red != null || c.green != null || c.blue != null) {
    const [r, g, b] = [Number(c.red) || 0, Number(c.green) || 0, Number(c.blue) || 0]
    return '#' + [r, g, b].map((n) => n.toString(16).padStart(2, '0')).join('')
  }
  if (c.value != null) {
    const v = Number(c.value) >>> 0
    return '#' + ((v >> 16) & 0xff).toString(16).padStart(2, '0') + ((v >> 8) & 0xff).toString(16).padStart(2, '0') + (v & 0xff).toString(16).padStart(2, '0')
  }
  return ''
}
/** 按色带名取 N 个色值（linear=GetLinearColorRampsByName / random=GetRandomColorRampsByName） */
async function rampColorHex(name: string, count: number, random = false): Promise<string[]> {
  if (!name || count <= 0) return []
  try {
    const r: any = random
      ? await dataStyleApi.getRandomColorRampsByName(name, count)
      : await dataStyleApi.getLinearColorRampsByName(name, count)
    const raw = r?.colorList ?? r?.colors ?? []
    const arr = Array.isArray(raw) ? raw : []
    return arr.map((c: any) => rgbHex(c)).filter(Boolean)
  } catch {
    return []
  }
}
function rampColors(val: any): string[] {
  const obj = typeof val === 'string' ? findRamp(val) : val
  if (obj && typeof obj === 'object') {
    const cand = obj.colors ?? obj.colorList ?? obj.colorValue ?? obj.value ?? obj.rgb ?? obj.colorRamps
    const cs = validColors(splitColors(cand))
    if (cs.length) return cs
    const n = rampName(obj)
    if (n && n !== val) return validColors(splitColors(n))
    return []
  }
  return validColors(splitColors(val))
}
function gradientOf(colors: string[]): string {
  return colors.length >= 2 ? `linear-gradient(90deg, ${colors.join(', ')})` : ''
}
const currentColors = computed(() => (current.value ? rampColors(rowRamp(current.value)) : []))
const currentGradient = computed(() => gradientOf(currentColors.value))
function rampGradientOf(name: string): string {
  return gradientOf(rampColors(name))
}
function rowRamp(row: any): any {
  return row?.styleRenderColorBand ?? row?.colorRamp ?? row?.colorRamps ?? row?.colorRampName
}

/* ------------------------- 图层选择器（绑定 / 字段来源） ------------------------- */
const layerOptions = ref<any[]>([])
const layerKeyword = ref('')
const layersLoading = ref(false)

async function loadLayers() {
  layersLoading.value = true
  try {
    const res = await layerContentApi.getPageListByName(
      { createBy: userStore.userName, layerName: layerKeyword.value.trim() || undefined },
      50,
      1,
    )
    layerOptions.value = pickList<any>(res)
  } catch {
    layerOptions.value = []
  } finally {
    layersLoading.value = false
  }
}
function layerName(id: string): string {
  const l = layerOptions.value.find((x) => x.id === id)
  return l ? l.layerName ?? l.name ?? id : id || '-'
}

/* ------------------------- 新建 / 编辑弹窗 ------------------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const saving = ref(false)
const originName = ref('')
const originLayerId = ref('') // 编辑前的绑定图层（换绑/解绑时同步清理图层侧）
const form = reactive({
  id: '',
  name: '',
  styleType: '1', // 1 矢量 / 2 栅格（展示用）
  styleDataType: '',
  renderKey: 'single',
  colorRamp: '',
  layerId: '', // styleDefaultLayer：样式绑定的默认图层
  fieldId: '', // styleRenderField：LayerField id
  fieldName: '', // styleRenderFieldName：字段名（SLD 过滤属性）
  lvNum: 5,
  bandIndex: 1,
  threeColor: '#FFFFFF', // 三波段透明色
  sldXml: '', // 灰度拉伸 SLD 原文
  randomRamp: false, // 规则取色用随机色带（GetRandomColorRampsByName）
})
/** 单一符号参数（styleInfo，camelCase 与后端 PolygonStyle 字段对应） */
const sym = reactive<Record<string, any>>({})
/** 分级 / 唯一值规则（ruleDatas 前端形态） */
interface RuleRow {
  valueOne: string
  value: string
  operator: string
  title: string
  color: string
}
const rules = ref<RuleRow[]>([])
/** 栅格断点规则（tifStyles） */
interface TifRuleRow {
  fillWay: string
  color: string
  quantityOne: string
  quantity: string
  label: string
  opacity: string
}
const tifRules = ref<TifRuleRow[]>([])

const currentDef = computed(() => defOf(form.renderKey))
const isRasterRender = computed(() => currentDef.value.raster)
const layerFields = ref<any[]>([])
const fieldsLoading = ref(false)

function resetSym(dt?: string) {
  const col = form.colorRamp ? (rampColors(form.colorRamp)[0] ?? '') : ''
  Object.keys(sym).forEach((k) => delete sym[k])
  if (dt === LAYER_GEOM_IDS.line) {
    Object.assign(sym, { type: '2', lineWidth: 1, lineType: 1, outlineColor: col || '#2B8FD8', lineTransparency: 1 })
  } else if (dt === LAYER_GEOM_IDS.point) {
    Object.assign(sym, {
      type: '1',
      pointType: 'circle',
      iconSize: '10',
      polygonColor: col || '#E23B2E',
      fillTransparency: 1,
    })
  } else {
    Object.assign(sym, {
      type: '3',
      isFill: true,
      isOutline: true,
      polygonColor: col || '#AAAAAA',
      outlineColor: '#000000',
      lineWidth: 1,
      lineType: 1,
      fillTransparency: 1,
      lineTransparency: 1,
    })
  }
}
function resetForm() {
  form.id = ''
  form.name = ''
  form.styleType = '1'
  form.styleDataType = ''
  form.renderKey = 'single'
  form.colorRamp = ''
  form.layerId = ''
  form.fieldId = ''
  form.fieldName = ''
  form.lvNum = 5
  form.bandIndex = 1
  form.threeColor = '#FFFFFF'
  form.sldXml = ''
  form.randomRamp = false
  rules.value = []
  tifRules.value = []
  resetSym('')
}
function openAdd() {
  dialogMode.value = 'add'
  originName.value = ''
  resetForm()
  dialogVisible.value = true
}

/** 符号参数 JSON（styleInfo）——按几何类型裁剪无关字段 */
function buildStyleInfo(): Record<string, any> {
  const dt = form.styleDataType
  const out: Record<string, any> = { type: geomNum(dt) }
  if (dt === LAYER_GEOM_IDS.point) {
    out.pointType = sym.pointType
    out.iconSize = sym.iconSize
    out.polygonColor = sym.polygonColor
    out.fillTransparency = sym.fillTransparency
    if (sym.isIcon) {
      out.isIcon = true
      out.iconPath = sym.iconPath
    }
    if (sym.iconRotation) out.iconRotation = sym.iconRotation
  } else if (dt === LAYER_GEOM_IDS.line) {
    out.lineWidth = sym.lineWidth
    out.lineType = sym.lineType
    out.outlineColor = sym.outlineColor
    out.lineTransparency = sym.lineTransparency
  } else {
    out.isFill = !!sym.isFill
    out.isOutline = !!sym.isOutline
    out.polygonColor = sym.polygonColor
    out.fillTransparency = sym.fillTransparency
    out.outlineColor = sym.outlineColor
    out.lineWidth = sym.lineWidth
    out.lineType = sym.lineType
    out.lineTransparency = sym.lineTransparency
  }
  if (sym.minScaleDenominator) out.minScaleDenominator = sym.minScaleDenominator
  if (sym.maxScaleDenominator) out.maxScaleDenominator = sym.maxScaleDenominator
  return out
}
/** 单条规则的符号 JSON（RuleData.polygonStyle，camelCase） */
function ruleSymbolJson(color: string): string {
  const dt = form.styleDataType
  if (dt === LAYER_GEOM_IDS.line) {
    return JSON.stringify({ type: '2', lineWidth: 1, lineType: 1, outlineColor: color, lineTransparency: 1 })
  }
  if (dt === LAYER_GEOM_IDS.point) {
    return JSON.stringify({ type: '1', pointType: 'circle', iconSize: '10', polygonColor: color, fillTransparency: 1 })
  }
  return JSON.stringify({
    type: '3',
    isFill: true,
    isOutline: true,
    polygonColor: color,
    outlineColor: color,
    lineWidth: 1,
    lineType: 1,
    fillTransparency: 1,
    lineTransparency: 1,
  })
}
/** ruleDatas 提交体 */
function buildRuleDatas(): Record<string, any>[] {
  return rules.value.map((r) => ({
    polygonStyle: ruleSymbolJson(r.color),
    valueOne: r.valueOne || undefined,
    value: r.value,
    operator: r.operator,
    title: r.title || form.fieldName || '',
  }))
}

async function loadFieldsForLayer(layerId: string) {
  layerFields.value = []
  if (!layerId) return
  fieldsLoading.value = true
  try {
    layerFields.value = pickList(await layerFieldApi.getDetailByLayerID(layerId))
  } catch {
    layerFields.value = []
  } finally {
    fieldsLoading.value = false
  }
}

/** 编辑回填：GetDetailById 全量（styleInfo / ruleDatas / tifStyles / 绑定关系） */
async function openEdit(row: any) {
  dialogMode.value = 'edit'
  originName.value = sName(row)
  originLayerId.value = String(row.styleDefaultLayer ?? '')
  resetForm()
  form.id = row.id
  form.name = sName(row)
  form.styleType = String(row.styleType ?? '1')
  form.styleDataType = String(row.styleDataType ?? row.styledatatype ?? '')
  form.colorRamp = String(row.styleRenderColorBand ?? '')
  form.layerId = String(row.styleDefaultLayer ?? '')
  form.fieldId = String(row.styleRenderField ?? '')
  form.fieldName = String(row.styleRenderFieldName ?? '')
  const cfg = String(row.styleConfigType ?? '1')
  form.renderKey = cfg === '2' ? 'graduated' : cfg === '3' ? 'rasterRamp' : cfg === '4' ? 'rasterSingle' : cfg === '5' ? 'rasterThree' : cfg === '6' ? 'rasterMulti' : 'single'
  if (form.styleType === '2' || isRasterDataType(form.styleDataType)) form.styleType = '2'
  dialogVisible.value = true
  try {
    const d: any = await dataStyleApi.getDetailById(row.id)
    if (d && typeof d === 'object') {
      form.name = sName(d) || form.name
      form.colorRamp = String(d.styleRenderColorBand ?? form.colorRamp)
      form.layerId = String(d.styleDefaultLayer ?? form.layerId)
      form.fieldId = String(d.styleRenderField ?? form.fieldId)
      form.fieldName = String(d.styleRenderFieldName ?? form.fieldName)
      if (form.styleDataType) resetSym(form.styleDataType)
      if (d.styleInfo && typeof d.styleInfo === 'object') {
        Object.keys(sym).forEach((k) => delete sym[k])
        Object.assign(sym, d.styleInfo)
      }
      if (Array.isArray(d.ruleDatas) && d.ruleDatas.length) {
        form.renderKey = d.ruleDatas.some((r: any) => r?.operator === '范围值') ? 'graduated' : 'unique'
        rules.value = d.ruleDatas.map((r: any) => {
          const ps = parsePolygonStyle(r.polygonStyle)
          return {
            valueOne: String(r.valueOne ?? ''),
            value: String(r.value ?? ''),
            operator: String(r.operator ?? 'EQ'),
            title: String(r.title ?? ''),
            color: ps?.polygonColor || ps?.outlineColor || '#3388ff',
          }
        })
      }
      if (Array.isArray(d.tifStyles) && d.tifStyles.length) {
        tifRules.value = d.tifStyles.map((t: any) => ({
          fillWay: String(t.fillWay ?? ''),
          color: t.color || '#3388ff',
          quantityOne: String(t.quantityOne ?? ''),
          quantity: String(t.quantity ?? ''),
          label: String(t.label ?? ''),
          opacity: String(t.opacity ?? '1'),
        }))
      }
      // 三波段透明色回填（后端存 RRGGBB，转 #RRGGBB 供取色器）
      if (d.tifThreeColor) {
        const c = String(d.tifThreeColor).replace('#', '').trim()
        if (/^[0-9a-f]{6}$/i.test(c)) form.threeColor = '#' + c
      }
      if (form.renderKey === 'rasterRamp' && d.styleContent) form.sldXml = String(d.styleContent)
    }
  } catch {
    /* 详情失败时保留行数据兜底 */
  }
  if (form.layerId) loadFieldsForLayer(form.layerId)
}
/** polygonStyle 可能是 JSON 串或对象 */
function parsePolygonStyle(v: any): any {
  if (!v) return null
  if (typeof v === 'object') return v
  try {
    return JSON.parse(v)
  } catch {
    return null
  }
}

/** 重名校验：POST /dataStyle/IsExists（返回 true 表示已存在） */
async function checkName(showTip = false): Promise<boolean> {
  const nm = String(form.name || '').trim()
  if (!nm) {
    if (showTip) ElMessage.warning('请输入样式名称')
    return false
  }
  try {
    const exists = await dataStyleApi.isExists(nm)
    const isSelf = dialogMode.value === 'edit' && nm === originName.value
    if (String(exists) === 'true' && !isSelf) {
      ElMessage.warning('样式名已存在')
      return false
    }
    return true
  } catch (e: any) {
    showError(e, '名称校验失败')
    return false
  }
}

async function save() {
  const nm = String(form.name || '').trim()
  if (!nm) {
    ElMessage.warning('请输入样式名称')
    return
  }
  if (!(await checkName())) return
  const def = currentDef.value
  if (!form.styleDataType) {
    ElMessage.warning('请选择适用类型（点/线/面/影像）')
    return
  }
  if ((def.configType === '2' || def.raster) && !form.layerId) {
    ElMessage.warning('请选择绑定图层（字段统计与 GeoServer 推送都依赖图层）')
    return
  }
  if (def.configType === '2' && !form.fieldName) {
    ElMessage.warning('请选择渲染字段')
    return
  }
  if (def.configType === '2' && !rules.value.length) {
    ElMessage.warning('请先生成或填写分级/唯一值规则')
    return
  }
  if ((def.configType === '4' || def.configType === '6') && !tifRules.value.length) {
    ElMessage.warning('请先读取断点生成栅格分级')
    return
  }
  saving.value = true
  try {
    const body: Record<string, any> = {
      styleName: nm,
      styleType: isRasterDataType(form.styleDataType) || def.raster ? '2' : '1',
      styleDataType: form.styleDataType,
      styleConfigType: def.configType,
      styleRenderColorBand: form.colorRamp,
      // 始终传串：空串 = 显式解绑（后端据此清空 styleDefaultLayer）
      styleDefaultLayer: form.layerId || '',
      createBy: userStore.userName,
    }
    if (def.configType === '1') {
      body.styleInfo = buildStyleInfo()
    } else if (def.configType === '2') {
      body.styleRenderField = form.fieldId || undefined
      body.styleRenderFieldName = form.fieldName
      body.ruleDatas = buildRuleDatas()
    } else if (def.configType === '3') {
      body.styleContent = form.sldXml
    } else if (def.configType === '4' || def.configType === '6') {
      // 单/多波段：走后端 Update 的 tif 分支（按图层 upsert 并绑定 GeoServer）
      body.layerId = form.layerId
      body.type = '1'
      body.bandIndex = form.bandIndex
      body.tifStyles = tifRules.value.map((r) => ({ ...r }))
    } else if (def.configType === '5') {
      body.layerId = form.layerId
      body.type = '1'
      body.tifThreeColor = String(form.threeColor).replace('#', '').toUpperCase()
    }
    if (dialogMode.value === 'edit' && def.configType !== '4' && def.configType !== '5' && def.configType !== '6') {
      body.id = form.id
    }
    let savedId = form.id
    if (def.configType === '4' || def.configType === '5' || def.configType === '6') {
      // Update 的 tif 分支内部按 styleDefaultLayer upsert，无需 id
      await dataStyleApi.update(body)
      const bound: any = await dataStyleApi.getDetailByLayerID(form.layerId)
      savedId = bound?.id ?? ''
    } else if (dialogMode.value === 'edit') {
      await dataStyleApi.update(body)
    } else {
      const created: any = await dataStyleApi.insert(body)
      savedId = created?.id ?? created?.result?.id ?? ''
    }
    // 绑定图层 → 图层侧 layerDefaultStyle（供地图渲染取样式）
    if (form.layerId && savedId) {
      try {
        await layerContentApi.update({ id: form.layerId, layerDefaultStyle: savedId })
      } catch {
        /* 图层绑定失败不阻断样式保存 */
      }
    }
    // 换绑 / 解绑时清理原图层的默认样式引用
    if (originLayerId.value && originLayerId.value !== form.layerId) {
      try {
        await layerContentApi.update({ id: originLayerId.value, layerDefaultStyle: '' })
      } catch {
        /* 清理失败不阻断保存 */
      }
    }
    ElMessage.success(dialogMode.value === 'edit' ? '样式已保存' : '样式已新建')
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    showError(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ------------------------- 规则生成 ------------------------- */
const genLoading = ref(false)

/** 分级设色：自然断点（getShpJenksBreaks）+ 色带取色 */
async function genGraduated() {
  if (!form.layerId || !form.fieldName) {
    ElMessage.warning('请先选择绑定图层与渲染字段')
    return
  }
  genLoading.value = true
  try {
    const res: any = await dataStyleApi.getShpJenksBreaks(form.layerId, form.fieldName, form.lvNum)
    const breaks = pickList<any>(res)
    if (!breaks.length) {
      ElMessage.warning('未取到断点（字段需为数值型）')
      return
    }
    const colors = (await rampColorHex(form.colorRamp, breaks.length, form.randomRamp)) || defaultColors(breaks.length)
    rules.value = breaks.map((b: any, i: number) => ({
      valueOne: fmtNum(b?.min ?? b?.['min']),
      value: fmtNum(b?.max ?? b?.['max']),
      operator: '范围值',
      title: form.fieldName,
      color: colors[i] || colors[colors.length - 1] || '#3388ff',
    }))
    ElMessage.success(`已生成 ${rules.value.length} 级规则`)
  } catch (e: any) {
    showError(e, '断点读取失败')
  } finally {
    genLoading.value = false
  }
}

/** 唯一值：字段取值列表（GetDataAttribute）+ 色带取色 */
async function genUnique() {
  if (!form.layerId || !form.fieldName) {
    ElMessage.warning('请先选择绑定图层与渲染字段')
    return
  }
  genLoading.value = true
  try {
    const res: any = await dataStyleApi.getDataAttribute(form.layerId, form.fieldName)
    const rows = pickList<any>(res)
    const values = rows.map((r: any) => String(r?.value ?? r?.['值'] ?? '')).filter((v) => v !== '')
    if (!values.length) {
      ElMessage.warning('未取到字段取值')
      return
    }
    const colors = (await rampColorHex(form.colorRamp, values.length, form.randomRamp)) || defaultColors(values.length)
    rules.value = values.map((v: string, i: number) => ({
      valueOne: '',
      value: v,
      operator: 'EQ',
      title: form.fieldName,
      color: colors[i] || colors[colors.length - 1] || '#3388ff',
    }))
    ElMessage.success(`已生成 ${rules.value.length} 条唯一值规则`)
  } catch (e: any) {
    showError(e, '字段取值读取失败')
  } finally {
    genLoading.value = false
  }
}

/** 栅格分级：自然断点（getTifJenksBreaks）→ tifStyles */
async function genTifRules() {
  if (!form.layerId) {
    ElMessage.warning('请先选择绑定图层')
    return
  }
  genLoading.value = true
  try {
    const res: any = await dataStyleApi.getTifJenksBreaks(form.layerId, form.lvNum, form.bandIndex)
    const breaks = pickList<any>(res)
    if (!breaks.length) {
      ElMessage.warning('未取到断点')
      return
    }
    const colors = (await rampColorHex(form.colorRamp, breaks.length, form.randomRamp)) || defaultColors(breaks.length)
    tifRules.value = breaks.map((b: any, i: number) => ({
      fillWay: i === 0 ? 'intervals' : 'intervals',
      color: colors[i] || colors[colors.length - 1] || '#3388ff',
      quantityOne: fmtNum(b?.min ?? b?.['min']),
      quantity: fmtNum(b?.max ?? b?.['max']),
      label: `${fmtNum(b?.min ?? b?.['min'])} ~ ${fmtNum(b?.max ?? b?.['max'])}`,
      opacity: '1',
    }))
    ElMessage.success(`已生成 ${tifRules.value.length} 级栅格规则`)
  } catch (e: any) {
    showError(e, '栅格断点读取失败')
  } finally {
    genLoading.value = false
  }
}

function fmtNum(v: any): string {
  const n = Number(v)
  if (v == null || v === '' || Number.isNaN(n)) return ''
  return String(Math.round(n * 1000) / 1000)
}
function defaultColors(n: number): string[] {
  const base = ['#3388ff', '#33cc66', '#ffcc33', '#ff7733', '#ee4433', '#aa55cc', '#00bbcc', '#88aa44']
  return Array.from({ length: n }, (_, i) => base[i % base.length])
}

/* ------------------------- 预览 ------------------------- */
const previewColors = computed(() => rules.value.map((r) => r.color))
const tifPreviewColors = computed(() => tifRules.value.map((r) => r.color))

/* ------------------------- 查看 SLD ------------------------- */
const sldVisible = ref(false)
const sldLoading = ref(false)
const sldText = ref('')
const sldTitle = ref('')

async function viewSld(row: any) {
  sldVisible.value = true
  sldLoading.value = true
  sldText.value = ''
  sldTitle.value = sName(row)
  try {
    const xml = await dataStyleApi.getXmlContent({ id: row.id })
    sldText.value = typeof xml === 'string' ? xml : JSON.stringify(xml, null, 2)
  } catch (e: any) {
    sldText.value = '读取失败：' + (e?.message || '未知错误')
  } finally {
    sldLoading.value = false
  }
}

/* ------------------------- 推送 GeoServer ------------------------- */
const pushingId = ref('')
async function pushGeo(row: any) {
  pushingId.value = row.id
  try {
    await dataStyleApi.insertGeoServerStyle({ id: row.id, name: sName(row) })
    ElMessage.success('已推送（接口无返回体）')
  } catch (e: any) {
    showError(e, '推送失败')
  } finally {
    pushingId.value = ''
  }
}

/* ------------------------- 应用样式到图层 ------------------------- */
const applyVisible = ref(false)
const applyRow = ref<StyleRow | null>(null)
const applyLayerId = ref('')
const applying = ref(false)

function openApply(row: any) {
  applyRow.value = row
  applyLayerId.value = String(row.styleDefaultLayer ?? '')
  applyVisible.value = true
  if (!layerOptions.value.length) loadLayers()
}
async function doApply() {
  if (!applyRow.value) return
  const prevLayer = String(applyRow.value.styleDefaultLayer ?? '')
  applying.value = true
  try {
    // 样式侧绑定（空串 = 解绑）
    await dataStyleApi.update({
      id: applyRow.value.id,
      styleConfigType: applyRow.value.styleConfigType,
      styleDefaultLayer: applyLayerId.value || '',
    })
    if (applyLayerId.value) {
      await layerContentApi.update({ id: applyLayerId.value, layerDefaultStyle: applyRow.value.id })
    }
    // 清理原图层的默认样式引用
    if (prevLayer && prevLayer !== applyLayerId.value) {
      try {
        await layerContentApi.update({ id: prevLayer, layerDefaultStyle: '' })
      } catch {
        /* 清理失败不阻断 */
      }
    }
    ElMessage.success(applyLayerId.value ? '已应用到图层' : '已解除绑定')
    applyVisible.value = false
    await load()
  } catch (e: any) {
    showError(e, '应用失败')
  } finally {
    applying.value = false
  }
}

/* ------------------------- 详情（GetDetailById + 解析 + 属性分级预览） ------------------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>(null)
const parseLoading = ref(false)
const attrPreviewLoading = ref(false)
const attrPreviewSort = ref(false)
const attrPreviewAll = ref(false)
const attrPreview = ref<{ count: number; columns: string[]; rows: any[][] }>({ count: 0, columns: [], rows: [] })
const attrPreviewPage = reactive({ index: 1, size: 10 })

async function openDetail(row: any) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  attrPreview.value = { count: 0, columns: [], rows: [] }
  try {
    detail.value = await dataStyleApi.getDetailById(row.id)
  } catch (e: any) {
    showError(e, '详情读取失败')
  } finally {
    detailLoading.value = false
  }
}
/** SLD 反向解析：ParsingSld → ruleDatas / symbolRulesdata */
async function parseSld() {
  if (!detail.value?.id) return
  parseLoading.value = true
  try {
    const r: any = await dataStyleApi.parsingSld(detail.value.id)
    detail.value = { ...(detail.value || {}), ...r }
    ElMessage.success('已解析 SLD')
  } catch (e: any) {
    showError(e, '解析失败')
  } finally {
    parseLoading.value = false
  }
}
/** 属性分级预览：GetDataAttributesPage / Sort / GetDataAttributes（全量） */
async function loadAttrPreview() {
  const d = detail.value
  if (!d?.styleDefaultLayer || !d?.styleRenderFieldName) {
    ElMessage.warning('该样式未绑定图层或未设置渲染字段，无法预览')
    return
  }
  attrPreviewLoading.value = true
  try {
    const styleJson = JSON.stringify({
      Type: geomNum(String(d.styleDataType ?? '')),
      PolygonColor: '#3388ff',
      IsFill: true,
      IsOutline: true,
      OutlineColor: '#3388ff',
      LineWidth: 1,
      LineTransparency: 1,
      FillTransparency: 1,
    })
    const layerId = d.styleDefaultLayer
    const field = d.styleRenderFieldName
    const band = d.styleRenderColorBand || ''
    let raw: any
    if (attrPreviewAll.value) {
      raw = await dataStyleApi.getDataAttributes(layerId, field, band, styleJson)
    } else if (attrPreviewSort.value) {
      raw = await dataStyleApi.getDataAttributesSort(layerId, field, band, styleJson, attrPreviewPage.size, attrPreviewPage.index)
    } else {
      raw = await dataStyleApi.getDataAttributesPage(layerId, field, band, styleJson, attrPreviewPage.size, attrPreviewPage.index)
    }
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    const dt = obj?.DataTableJson ?? obj?.dataTableJson ?? obj?.table ?? {}
    const cols: string[] = Array.isArray(dt.columnNames) ? dt.columnNames : []
    const rowsRaw: any[] = Array.isArray(dt.rows) ? dt.rows : Array.isArray(dt.dataRows) ? dt.dataRows : []
    attrPreview.value = {
      count: Number(obj?.Count ?? obj?.count ?? rowsRaw.length),
      columns: cols.length ? cols : ['符号', '值', '标记', '个数'],
      rows: rowsRaw.map((r: any) => (Array.isArray(r) ? r : cols.map((c) => r?.[c]))),
    }
  } catch (e: any) {
    showError(e, '属性分级预览失败')
  } finally {
    attrPreviewLoading.value = false
  }
}
/** 按样式 ID 预览：GetDataAttributesById（自带图层/字段/色带，无需额外参数） */
async function loadAttrPreviewById() {
  const d = detail.value
  if (!d?.id) return
  attrPreviewLoading.value = true
  try {
    const raw: any = await dataStyleApi.getDataAttributesById(d.id)
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    const dt = obj?.table ?? obj?.Table ?? {}
    const cols: string[] = Array.isArray(dt.columnNames) ? dt.columnNames : []
    const rowsRaw: any[] = Array.isArray(dt.rows) ? dt.rows : Array.isArray(dt.dataRows) ? dt.dataRows : []
    attrPreview.value = {
      count: rowsRaw.length,
      columns: cols.length ? cols : ['符号', '值', '标记', '个数'],
      rows: rowsRaw.map((r: any) => (Array.isArray(r) ? r : cols.map((c) => r?.[c]))),
    }
    ElMessage.success(`按样式预览：${rowsRaw.length} 组`)
  } catch (e: any) {
    showError(e, '按样式预览失败')
  } finally {
    attrPreviewLoading.value = false
  }
}
/** 预览行取色：第一列为 PascalCase PolygonStyle JSON */
function previewRowColor(row: any[]): string {
  try {
    const ps = typeof row?.[0] === 'string' ? JSON.parse(row[0]) : row?.[0]
    const c = ps?.PolygonColor ?? ps?.polygonColor ?? ''
    return /^#?[0-9a-f]{6}$/i.test(c) ? (c.startsWith('#') ? c : '#' + c) : ''
  } catch {
    return ''
  }
}

/* ------------------------- 删除 ------------------------- */
/** 归一化引用校验返回值：兼容 boolean / 列表 / 对象 */
function refIsTrue(v: any): boolean {
  if (v === true || v === 'true') return true
  if (Array.isArray(v)) return v.length > 0
  if (v && typeof v === 'object') {
    if (pickList(v).length > 0) return true
    return !!v.id
  }
  return false
}
async function onDelete(row: any) {
  let referenced = false
  try {
    referenced = refIsTrue(await mapRelationApi.getMapReleationByStyle(row.id))
  } catch {
    /* 校验接口失败时按未引用处理，不阻断删除 */
  }
  const tip = referenced
    ? `样式「${sName(row)}」已被地图引用，删除后关联地图将失去该样式。确认删除？`
    : `确认删除样式「${sName(row)}」？`
  try {
    await ElMessageBox.confirm(tip, referenced ? '引用校验警告' : '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  try {
    await dataStyleApi.remove(row.id, userStore.userName)
    ElMessage.success('已删除')
    if (current.value?.id === row.id) current.value = null
    await load()
  } catch (e: any) {
    showError(e, '删除失败')
  }
}
/** 批量删除：DeleteById 单条调用，逐条执行 */
async function onBatchDelete() {
  const rows = selection.value
  if (!rows.length) {
    ElMessage.warning('请先勾选要删除的样式')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量删除 ${rows.length} 个样式？被地图引用的样式将失去关联。`, '批量删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  let ok = 0
  const failed: string[] = []
  for (const r of rows) {
    try {
      await dataStyleApi.removeById({ styleId: r.id, userAccount: userStore.userName })
      ok++
    } catch {
      failed.push(sName(r))
    }
  }
  if (ok) ElMessage.success(`已删除 ${ok} 个样式`)
  if (failed.length) ElMessage.error(`删除失败：${failed.join('、')}`)
  await load()
}

/* ------------------------- 样式文件夹管理 ------------------------- */
const folderVisible = ref(false)
const folders = ref<string[]>([])
const activeFolder = ref('')
const folderFiles = ref<{ name: string; path: string }[]>([])
const foldersLoading = ref(false)
const filesLoading = ref(false)
const fileParseVisible = ref(false)
const fileParseResult = ref<any[]>([])
const fileParseTitle = ref('')

async function openFolders() {
  folderVisible.value = true
  await loadFolders()
}
async function loadFolders() {
  foldersLoading.value = true
  try {
    folders.value = pickList<string>(await dataStyleApi.getAllFolders())
    if (!activeFolder.value && folders.value.length) {
      activeFolder.value = folders.value[0]
      await loadFiles()
    }
  } catch (e: any) {
    showError(e, '文件夹列表读取失败')
  } finally {
    foldersLoading.value = false
  }
}
async function loadFiles() {
  if (!activeFolder.value) {
    folderFiles.value = []
    return
  }
  filesLoading.value = true
  try {
    folderFiles.value = pickList<any>(await dataStyleApi.getAllFiles(activeFolder.value))
  } catch {
    folderFiles.value = []
  } finally {
    filesLoading.value = false
  }
}
async function addFolder() {
  try {
    const { value } = await ElMessageBox.prompt('输入新文件夹名称', '新增文件夹')
    const nm = String(value || '').trim()
    if (!nm) return
    const ok = await dataStyleApi.addFolder(nm)
    if (ok === false) {
      ElMessage.error('新增失败（可能已存在）')
      return
    }
    ElMessage.success('已新增')
    activeFolder.value = nm
    await loadFolders()
    await loadFiles()
  } catch (e: any) {
    if (e !== 'cancel') showError(e, '新增失败')
  }
}
async function renameFolder(old: string) {
  try {
    const { value } = await ElMessageBox.prompt('输入新名称', `重命名「${old}」`, { inputValue: old })
    const nm = String(value || '').trim()
    if (!nm || nm === old) return
    const ok = await dataStyleApi.updateFolder(old, nm)
    if (ok === false) {
      ElMessage.error('重命名失败')
      return
    }
    ElMessage.success('已重命名')
    activeFolder.value = nm
    await loadFolders()
    await loadFiles()
  } catch (e: any) {
    if (e !== 'cancel') showError(e, '重命名失败')
  }
}
async function deleteFolder(name: string) {
  try {
    await ElMessageBox.confirm(`确认删除文件夹「${name}」及其全部文件？`, '删除文件夹', { type: 'warning' })
  } catch {
    return
  }
  try {
    const ok = await dataStyleApi.deleteFolder(name)
    if (ok === false) {
      ElMessage.error('删除失败')
      return
    }
    ElMessage.success('已删除')
    if (activeFolder.value === name) {
      activeFolder.value = ''
      folderFiles.value = []
    }
    await loadFolders()
  } catch (e: any) {
    showError(e, '删除失败')
  }
}
async function renameFile(f: { name: string; path: string }) {
  try {
    const { value } = await ElMessageBox.prompt('输入新文件名', `重命名「${f.name}」`, { inputValue: f.name })
    const nm = String(value || '').trim()
    if (!nm || nm === f.name) return
    const ok = await dataStyleApi.updateFile(activeFolder.value, f.name, nm)
    if (ok === false) {
      ElMessage.error('重命名失败')
      return
    }
    ElMessage.success('已重命名')
    await loadFiles()
  } catch (e: any) {
    if (e !== 'cancel') showError(e, '重命名失败')
  }
}
async function deleteFile(f: { name: string; path: string }) {
  try {
    await ElMessageBox.confirm(`确认删除文件「${f.name}」？`, '删除文件', { type: 'warning' })
  } catch {
    return
  }
  try {
    const ok = await dataStyleApi.deleteFile(activeFolder.value, f.name)
    if (ok === false) {
      ElMessage.error('删除失败')
      return
    }
    ElMessage.success('已删除')
    await loadFiles()
  } catch (e: any) {
    showError(e, '删除失败')
  }
}
/** 按文件路径解析 SLD：Parsethefile */
async function parseFile(f: { name: string; path: string }) {
  fileParseTitle.value = f.name
  fileParseResult.value = []
  fileParseVisible.value = true
  try {
    fileParseResult.value = pickList<any>(await dataStyleApi.parsethefile(f.path))
    if (!fileParseResult.value.length) ElMessage.info('未解析到规则（文件不存在或非 SLD）')
  } catch (e: any) {
    showError(e, '解析失败')
  }
}

/* ------------------------- 色带预览弹窗 ------------------------- */
const rampVisible = ref(false)
const rampRow = ref<StyleRow | null>(null)
function openRamp(row: any) {
  rampRow.value = row
  current.value = row
  rampVisible.value = true
}
const rampRowColors = computed(() => (rampRow.value ? rampColors(rowRamp(rampRow.value)) : []))
const rampRowGradient = computed(() => gradientOf(rampRowColors.value))

/** 预览用几何点计算（三角形 / 星形） */
function triPoints(r: number): string {
  const cx = 60, cy = 40
  const p1 = `${cx},${cy - r}`
  const p2 = `${cx - r * 0.866},${cy + r * 0.5}`
  const p3 = `${cx + r * 0.866},${cy + r * 0.5}`
  return `${p1} ${p2} ${p3}`
}
function starPoints(r: number): string {
  const cx = 60, cy = 40
  const pts: string[] = []
  for (let i = 0; i < 10; i++) {
    const rad = (Math.PI / 5) * i - Math.PI / 2
    const rr = i % 2 === 0 ? r : r * 0.45
    pts.push(`${cx + rr * Math.cos(rad)},${cy + rr * Math.sin(rad)}`)
  }
  return pts.join(' ')
}

onMounted(() => {
  load()
  loadRamps()
})
</script>

<template>
  <div>
    <PageHead title="样式库" sub="为矢量和栅格配置地图样式，管理分级规则、色带与样式文件">
      <template #tools>
        <button class="btn sm" @click="openFolders"><Icon name="tree" :size="13" />文件夹管理</button>
        <button class="btn sm" @click="load"><Icon name="refresh" :size="13" />刷新</button>
      </template>
    </PageHead>

    <div class="row">
      <!-- 左：筛选 + 列表 -->
      <div class="col flex1">
        <div class="filters">
          <el-input
            v-model="keyword"
            placeholder="按样式名称检索（可留空）"
            clearable
            style="width: 220px"
            @keyup.enter="search"
            @clear="search"
          >
            <template #prefix><Icon name="search" :size="14" /></template>
          </el-input>
          <button class="btn sm" @click="search"><Icon name="search" :size="13" />查询</button>
          <button class="btn sm primary" @click="openAdd"><Icon name="plus" :size="13" />新建样式</button>
          <button class="btn sm danger" :disabled="!selection.length" @click="onBatchDelete">
            <Icon name="trash" :size="13" />批量删除<span v-if="selection.length">（{{ selection.length }}）</span>
          </button>
        </div>

        <div class="card">
          <div class="card-h">
            样式清单
            <span class="cnt">{{ total }}</span>
            <div class="tools"><span class="muted">点击行可在右侧预览色带</span></div>
          </div>
          <div class="card-b" style="padding: 0">
            <el-table
              v-loading="loading"
              :data="list"
              size="small"
              highlight-current-row
              row-key="id"
              style="width: 100%"
              @current-change="onCurrentChange"
              @selection-change="onSelectionChange"
            >
              <el-table-column type="selection" width="38" reserve-selection />
              <el-table-column label="样式名" min-width="140">
                <template #default="{ row }">
                  <span class="mono">{{ sName(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="适用类型" width="86">
                <template #default="{ row }">
                  <span class="tag" :class="isRasterDataType(row.styleDataType) ? 'violet' : 'blue'">{{ typeLabel(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="渲染方式" width="104">
                <template #default="{ row }">
                  <span class="tag gray">{{ renderLabel(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="绑定图层" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="mono">{{ row.styleDefaultLayer ? layerName(row.styleDefaultLayer) : '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="创建人" width="96">
                <template #default="{ row }">{{ row.createBy || row.createUser || '-' }}</template>
              </el-table-column>
              <el-table-column label="创建时间" width="150">
                <template #default="{ row }">{{ fmtTime(row.createDT || row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" min-width="340" fixed="right">
                <template #default="{ row }">
                  <div class="row" style="gap: 5px; flex-wrap: wrap">
                    <button class="btn xs" @click.stop="openEdit(row)"><Icon name="edit" :size="12" />编辑</button>
                    <button class="btn xs" @click.stop="openDetail(row)"><Icon name="eye" :size="12" />详情</button>
                    <button class="btn xs" @click.stop="openApply(row)"><Icon name="layers" :size="12" />应用</button>
                    <button class="btn xs" @click.stop="viewSld(row)"><Icon name="file" :size="12" />SLD</button>
                    <button class="btn xs" :disabled="pushingId === row.id" @click.stop="pushGeo(row)">
                      <Icon name="up" :size="12" />推送
                    </button>
                    <button class="btn xs" @click.stop="openRamp(row)"><Icon name="palette" :size="12" />色带</button>
                    <button class="btn xs danger" @click.stop="onDelete(row)"><Icon name="trash" :size="12" />删除</button>
                  </div>
                </template>
              </el-table-column>
              <template #empty>
                <div class="muted" style="padding: 18px">暂无样式数据</div>
              </template>
            </el-table>
          </div>
          <div class="pager" style="padding: 8px 10px">
            <el-pagination
              v-model:current-page="page.index"
              v-model:page-size="page.size"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              small
              @current-change="load"
              @size-change="search"
            />
          </div>
        </div>
      </div>

      <!-- 右：色带预览 -->
      <div class="col" style="width: 288px; flex: 0 0 288px">
        <div class="card">
          <div class="card-h"><Icon name="palette" :size="14" />色带预览</div>
          <div class="card-b">
            <template v-if="current">
              <div class="mono mb8">{{ sName(current) }}</div>
              <div class="kv mb8">
                <span class="k">类型</span><span class="v">{{ typeLabel(current) }}</span>
                <span class="k">渲染方式</span><span class="v">{{ renderLabel(current) }}</span>
                <span class="k">色带</span><span class="v">{{ rampName(rowRamp(current)) || '未设置' }}</span>
              </div>
              <div v-if="currentGradient" class="ramp-bar" :style="{ background: currentGradient }" />
              <div v-else-if="currentColors.length === 1" class="ramp-bar" :style="{ background: currentColors[0] }" />
              <div v-else class="muted" style="font-size: 12px">无可预览色带（未匹配到色带库或色值）</div>
              <div v-if="currentColors.length >= 2" class="ramp-swatches mt8">
                <span v-for="(c, i) in currentColors" :key="i" class="sw" :style="{ background: c }" />
              </div>
            </template>
            <div v-else class="muted" style="font-size: 12px">在左侧选择一条样式以预览其色带。</div>
          </div>
        </div>

        <div class="card">
          <div class="card-h"><Icon name="list" :size="14" />渐变色带库<span class="cnt">{{ colorRamps.length }}</span></div>
          <div class="card-b">
            <div v-if="!rampOptions.length" class="muted" style="font-size: 12px">色带库为空或接口不可用。</div>
            <div v-else class="col" style="gap: 7px">
              <div v-for="o in rampOptions" :key="o.value" class="ramp-line">
                <span class="ramp-chip" :style="{ background: rampGradientOf(o.value) || '#eef2f7' }" />
                <span class="mono ellip" :title="o.label">{{ o.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="(dialogMode === 'edit' ? '编辑样式' : '新建样式') + ' · ' + currentDef.label"
      width="760px"
      append-to-body
    >
      <el-form :model="form" label-width="96px" label-position="right">
        <div class="row" style="gap: 12px">
          <el-form-item label="样式名称" required class="flex1">
            <el-input v-model="form.name" placeholder="失焦自动重名校验" @blur="checkName(true)" />
          </el-form-item>
          <el-form-item label="适用类型" required style="width: 200px">
            <el-select v-model="form.styleDataType" clearable placeholder="点/线/面/影像" style="width: 100%" @change="resetSym(form.styleDataType)">
              <el-option v-for="g in GEOM_OPTIONS" :key="g.value" :label="g.label" :value="g.value" />
            </el-select>
          </el-form-item>
        </div>
        <div class="row" style="gap: 12px">
          <el-form-item label="渲染方式" class="flex1">
            <el-select v-model="form.renderKey" style="width: 100%">
              <el-option v-for="d in RENDER_DEFS" :key="d.key" :label="d.label" :value="d.key" />
            </el-select>
          </el-form-item>
          <el-form-item label="色带" class="flex1">
            <el-select v-model="form.colorRamp" clearable filterable placeholder="选择渐变色带" style="width: 100%">
              <el-option v-for="o in rampOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="绑定图层">
          <el-select
            v-model="form.layerId"
            filterable
            remote
            clearable
            :remote-method="(q: string) => { layerKeyword = q; loadLayers() }"
            :loading="layersLoading"
            placeholder="选择图层（字段统计 / GeoServer 绑定依赖它）"
            style="width: 100%"
            @focus="loadLayers()"
            @change="loadFieldsForLayer(form.layerId)"
          >
            <el-option v-for="l in layerOptions" :key="l.id" :label="l.layerName || l.name" :value="l.id" />
          </el-select>
        </el-form-item>

        <!-- 单一符号 -->
        <template v-if="currentDef.configType === '1'">
          <div class="card mb8">
            <div class="card-h"><Icon name="gear" :size="13" />符号参数<span class="cnt">{{ form.styleDataType === LAYER_GEOM_IDS.point ? '点' : form.styleDataType === LAYER_GEOM_IDS.line ? '线' : '面' }}</span></div>
            <div class="card-b">
              <div class="row" style="gap: 12px; align-items: flex-start">
                <div class="flex1">
                  <template v-if="form.styleDataType === LAYER_GEOM_IDS.point">
                    <div class="row" style="gap: 10px; margin-bottom: 8px">
                      <span class="k" style="width: 64px">点形状</span>
                      <el-select v-model="sym.pointType" size="small" style="width: 130px">
                        <el-option v-for="p in POINT_TYPES" :key="p.value" :label="p.label" :value="p.value" />
                      </el-select>
                      <span class="k" style="width: 64px">大小</span>
                      <el-input-number v-model="sym.iconSize" size="small" :min="1" :max="64" style="width: 100px" />
                    </div>
                    <div class="row" style="gap: 10px; margin-bottom: 8px">
                      <span class="k" style="width: 64px">填充色</span>
                      <el-input v-model="sym.polygonColor" size="small" style="width: 110px" />
                      <input type="color" :value="sym.polygonColor || '#000000'" @input="sym.polygonColor = ($event.target as HTMLInputElement).value" />
                      <span class="k" style="width: 64px">透明度</span>
                      <el-input-number v-model="sym.fillTransparency" size="small" :min="0" :max="1" :step="0.1" style="width: 100px" />
                    </div>
                    <div class="row" style="gap: 10px">
                      <el-checkbox v-model="sym.isIcon">使用图标</el-checkbox>
                      <el-input v-if="sym.isIcon" v-model="sym.iconPath" size="small" placeholder="图标 URL（http/https）" style="width: 260px" />
                    </div>
                  </template>
                  <template v-else-if="form.styleDataType === LAYER_GEOM_IDS.line">
                    <div class="row" style="gap: 10px; margin-bottom: 8px">
                      <span class="k" style="width: 64px">线宽</span>
                      <el-input-number v-model="sym.lineWidth" size="small" :min="0.1" :step="0.5" style="width: 100px" />
                      <span class="k" style="width: 64px">线型</span>
                      <el-select v-model="sym.lineType" size="small" style="width: 120px">
                        <el-option v-for="l in LINE_TYPES" :key="l.value" :label="l.label" :value="l.value" />
                      </el-select>
                    </div>
                    <div class="row" style="gap: 10px">
                      <span class="k" style="width: 64px">颜色</span>
                      <el-input v-model="sym.outlineColor" size="small" style="width: 110px" />
                      <input type="color" :value="sym.outlineColor || '#000000'" @input="sym.outlineColor = ($event.target as HTMLInputElement).value" />
                      <span class="k" style="width: 64px">透明度</span>
                      <el-input-number v-model="sym.lineTransparency" size="small" :min="0" :max="1" :step="0.1" style="width: 100px" />
                    </div>
                  </template>
                  <template v-else>
                    <div class="row" style="gap: 10px; margin-bottom: 8px">
                      <el-checkbox v-model="sym.isFill">填充</el-checkbox>
                      <span class="k">填充色</span>
                      <el-input v-model="sym.polygonColor" size="small" style="width: 100px" :disabled="!sym.isFill" />
                      <input type="color" :value="sym.polygonColor || '#000000'" @input="sym.polygonColor = ($event.target as HTMLInputElement).value" />
                      <span class="k">填充透明度</span>
                      <el-input-number v-model="sym.fillTransparency" size="small" :min="0" :max="1" :step="0.1" style="width: 96px" :disabled="!sym.isFill" />
                    </div>
                    <div class="row" style="gap: 10px">
                      <el-checkbox v-model="sym.isOutline">描边</el-checkbox>
                      <span class="k">边色</span>
                      <el-input v-model="sym.outlineColor" size="small" style="width: 100px" :disabled="!sym.isOutline" />
                      <input type="color" :value="sym.outlineColor || '#000000'" @input="sym.outlineColor = ($event.target as HTMLInputElement).value" />
                      <span class="k">边宽</span>
                      <el-input-number v-model="sym.lineWidth" size="small" :min="0.1" :step="0.5" style="width: 88px" :disabled="!sym.isOutline" />
                      <span class="k">边型</span>
                      <el-select v-model="sym.lineType" size="small" style="width: 104px" :disabled="!sym.isOutline">
                        <el-option v-for="l in LINE_TYPES" :key="l.value" :label="l.label" :value="l.value" />
                      </el-select>
                    </div>
                  </template>
                </div>
                <!-- 符号预览 -->
                <div style="width: 180px; flex: 0 0 180px; text-align: center">
                  <svg viewBox="0 0 120 80" width="160" height="107" style="background: #f4f7fb; border: 1px solid var(--line); border-radius: 6px">
                    <template v-if="form.styleDataType === LAYER_GEOM_IDS.point">
                      <circle v-if="sym.pointType === 'circle'" cx="60" cy="40" :r="Math.max(3, Math.min(26, Number(sym.iconSize) || 8))" :fill="sym.polygonColor || '#333'" :fill-opacity="sym.fillTransparency ?? 1" />
                      <rect v-else-if="sym.pointType === 'square'" x="60 - (Number(sym.iconSize) || 8)" y="40 - (Number(sym.iconSize) || 8)" :width="(Number(sym.iconSize) || 8) * 2" :height="(Number(sym.iconSize) || 8) * 2" :fill="sym.polygonColor || '#333'" :fill-opacity="sym.fillTransparency ?? 1" />
                      <polygon v-else-if="sym.pointType === 'triangle'" :points="triPoints(Number(sym.iconSize) || 8)" :fill="sym.polygonColor || '#333'" :fill-opacity="sym.fillTransparency ?? 1" />
                      <polygon v-else :points="starPoints(Number(sym.iconSize) || 8)" :fill="sym.polygonColor || '#333'" :fill-opacity="sym.fillTransparency ?? 1" />
                    </template>
                    <template v-else-if="form.styleDataType === LAYER_GEOM_IDS.line">
                      <line x1="15" y1="60" x2="105" y2="20" :stroke="sym.outlineColor || '#333'" :stroke-width="sym.lineWidth || 1" :stroke-opacity="sym.lineTransparency ?? 1" :stroke-dasharray="sym.lineType === 2 ? '8 4' : sym.lineType === 3 ? '2 3' : sym.lineType === 4 ? '10 4 2 4' : ''" />
                    </template>
                    <template v-else>
                      <rect x="25" y="20" width="70" height="40" :fill="sym.isFill ? (sym.polygonColor || '#333') : 'none'" :fill-opacity="sym.isFill ? (sym.fillTransparency ?? 1) : 0" :stroke="sym.isOutline ? (sym.outlineColor || '#333') : 'none'" :stroke-width="sym.isOutline ? (sym.lineWidth || 1) : 0" :stroke-dasharray="sym.lineType === 2 ? '8 4' : sym.lineType === 3 ? '2 3' : ''" />
                    </template>
                  </svg>
                  <div class="muted" style="font-size: 11px; margin-top: 4px">符号预览</div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 分级设色 / 唯一值 -->
        <template v-if="currentDef.configType === '2'">
          <div class="card mb8">
            <div class="card-h">
              渲染规则<span class="cnt">{{ currentDef.label }} · {{ rules.length }} 条</span>
              <div class="tools">
                <span class="muted" style="margin-right: 8px">{{ currentDef.hint }}</span>
              </div>
            </div>
            <div class="card-b">
              <div class="row wrap mb8" style="gap: 8px; align-items: center">
                <span class="k">渲染字段</span>
                <el-select v-model="form.fieldName" size="small" filterable clearable placeholder="选择字段" style="width: 160px" :loading="fieldsLoading">
                  <el-option v-for="f in layerFields" :key="f.id || f.attributeName" :label="f.attributeName || f.fieldName" :value="f.attributeName || f.fieldName" />
                </el-select>
                <el-input v-model="form.fieldName" v-if="!layerFields.length" size="small" placeholder="字段名" style="width: 140px" />
                <template v-if="form.renderKey === 'graduated'">
                  <span class="k">分级数</span>
                  <el-input-number v-model="form.lvNum" size="small" :min="2" :max="12" style="width: 96px" />
                  <el-checkbox v-model="form.randomRamp">随机色带</el-checkbox>
                  <button class="btn xs primary" :loading="genLoading" @click="genGraduated"><Icon name="sort" :size="12" />读取断点并生成</button>
                </template>
                <template v-else>
                  <el-checkbox v-model="form.randomRamp">随机色带</el-checkbox>
                  <button class="btn xs primary" :loading="genLoading" @click="genUnique"><Icon name="list" :size="12" />读取取值并生成</button>
                </template>
              </div>
              <el-table :data="rules" size="small" max-height="240" empty-text="请先读取断点/取值生成规则，或直接编辑">
                <el-table-column label="#" width="44" type="index" />
                <el-table-column v-if="form.renderKey === 'graduated'" label="起始值" width="120">
                  <template #default="{ row }"><el-input v-model="row.valueOne" size="small" /></template>
                </el-table-column>
                <el-table-column label="值 / 终值" width="130">
                  <template #default="{ row }"><el-input v-model="row.value" size="small" /></template>
                </el-table-column>
                <el-table-column label="颜色" width="96">
                  <template #default="{ row }">
                    <div class="row" style="gap: 4px; align-items: center">
                      <span class="sw" :style="{ background: row.color }" />
                      <input type="color" :value="row.color || '#000000'" @input="row.color = ($event.target as HTMLInputElement).value" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作符" width="104">
                  <template #default="{ row }">
                    <el-select v-model="row.operator" size="small">
                      <el-option label="范围 (a,b]" value="范围值" />
                      <el-option label="等于" value="EQ" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="标题" min-width="120">
                  <template #default="{ row }"><el-input v-model="row.title" size="small" placeholder="字段名" /></template>
                </el-table-column>
                <el-table-column label="" width="52">
                  <template #default="{ $index }">
                    <button class="btn xs danger" @click="rules.splice($index, 1)">删</button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="previewColors.length" class="ramp-bar mt8" :style="{ background: gradientOf(previewColors) }" />
            </div>
          </div>
        </template>

        <!-- 灰度拉伸（SLD 文本） -->
        <template v-if="currentDef.configType === '3'">
          <div class="card mb8">
            <div class="card-h">栅格 SLD<span class="cnt">styleConfigType = 3</span></div>
            <div class="card-b">
              <el-input v-model="form.sldXml" type="textarea" :rows="10" placeholder="粘贴栅格 SLD 原文（含 RasterSymbolizer / ColorMap）" />
              <div class="muted" style="font-size: 11.5px; margin-top: 6px">保存时后端按 URL 解码后入库；可先用「详情 / SLD」查看既有样式原文。</div>
            </div>
          </div>
        </template>

        <!-- 单波段 / 多波段 -->
        <template v-if="currentDef.configType === '4' || currentDef.configType === '6'">
          <div class="card mb8">
            <div class="card-h">
              栅格分级<span class="cnt">{{ tifRules.length }} 级</span>
            </div>
            <div class="card-b">
              <div class="row wrap mb8" style="gap: 8px; align-items: center">
                <span class="k">波段</span>
                <el-input-number v-model="form.bandIndex" size="small" :min="1" style="width: 88px" />
                <span class="k">分级数</span>
                <el-input-number v-model="form.lvNum" size="small" :min="2" :max="12" style="width: 96px" />
                <el-checkbox v-model="form.randomRamp">随机色带</el-checkbox>
                <button class="btn xs primary" :loading="genLoading" @click="genTifRules"><Icon name="sort" :size="12" />读取断点并生成</button>
              </div>
              <el-table :data="tifRules" size="small" max-height="240" empty-text="请先读取断点生成栅格分级">
                <el-table-column label="#" width="44" type="index" />
                <el-table-column label="起始值" width="110">
                  <template #default="{ row }"><el-input v-model="row.quantityOne" size="small" /></template>
                </el-table-column>
                <el-table-column label="终值" width="110">
                  <template #default="{ row }"><el-input v-model="row.quantity" size="small" /></template>
                </el-table-column>
                <el-table-column label="颜色" width="96">
                  <template #default="{ row }">
                    <div class="row" style="gap: 4px; align-items: center">
                      <span class="sw" :style="{ background: row.color }" />
                      <input type="color" :value="row.color || '#000000'" @input="row.color = ($event.target as HTMLInputElement).value" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="透明度" width="96">
                  <template #default="{ row }"><el-input v-model="row.opacity" size="small" /></template>
                </el-table-column>
                <el-table-column label="标注" min-width="130">
                  <template #default="{ row }"><el-input v-model="row.label" size="small" /></template>
                </el-table-column>
                <el-table-column label="" width="52">
                  <template #default="{ $index }">
                    <button class="btn xs danger" @click="tifRules.splice($index, 1)">删</button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="tifPreviewColors.length" class="ramp-bar mt8" :style="{ background: gradientOf(tifPreviewColors) }" />
              <div class="muted" style="font-size: 11.5px; margin-top: 6px">保存后按所选图层 upsert 样式并推送 GeoServer（需该图层已有元数据极值记录）。</div>
            </div>
          </div>
        </template>

        <!-- 三波段 -->
        <template v-if="currentDef.configType === '5'">
          <div class="card mb8">
            <div class="card-h">三波段透明色<span class="cnt">tifThreeColor</span></div>
            <div class="card-b">
              <div class="row" style="gap: 10px; align-items: center">
                <el-color-picker v-model="form.threeColor" />
                <span class="mono">{{ form.threeColor }}</span>
                <span class="muted" style="font-size: 11.5px">保存后设置为该图层的输入/输出透明色（GeoServer ImageMosaic）。</span>
              </div>
            </div>
          </div>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 应用样式到图层 -->
    <el-dialog v-model="applyVisible" title="应用样式到图层" width="460px" append-to-body>
      <div class="mb8">样式：<b class="mono">{{ applyRow ? sName(applyRow) : '' }}</b></div>
      <el-select
        v-model="applyLayerId"
        filterable
        remote
        clearable
        :remote-method="(q: string) => { layerKeyword = q; loadLayers() }"
        :loading="layersLoading"
        placeholder="选择目标图层"
        style="width: 100%"
      >
        <el-option v-for="l in layerOptions" :key="l.id" :label="l.layerName || l.name" :value="l.id" />
      </el-select>
      <div class="muted" style="font-size: 11.5px; margin-top: 8px">
        将写入样式侧 styleDefaultLayer 与图层侧 layerDefaultStyle；地图渲染按图层默认样式取 SLD。
      </div>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="doApply">应用</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="`样式详情 · ${detail ? sName(detail) : ''}`" width="760px" append-to-body>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="kv mb8">
            <span class="k">样式 ID</span><span class="v mono">{{ detail.id ?? '-' }}</span>
            <span class="k">配置类型</span><span class="v">{{ CONFIG_TYPE_LABEL[String(detail.styleConfigType ?? '')] || detail.styleConfigType || '-' }}</span>
            <span class="k">适用类型</span><span class="v">{{ layerDataTypeName(detail.styleDataType) }}</span>
            <span class="k">绑定图层</span><span class="v mono">{{ detail.styleDefaultLayer || '-' }}</span>
            <span class="k">渲染字段</span><span class="v mono">{{ detail.styleRenderFieldName || detail.styleRenderField || '-' }}</span>
            <span class="k">色带</span><span class="v">{{ detail.styleRenderColorBand || '-' }}</span>
            <span class="k">三波段透明色</span><span class="v mono">{{ detail.tifThreeColor || '-' }}</span>
          </div>
          <div class="row" style="gap: 6px; margin-bottom: 8px">
            <button class="btn xs" @click="parseSld" :disabled="parseLoading"><Icon name="gear" :size="12" />解析 SLD</button>
            <button class="btn xs" @click="loadAttrPreview" :loading="attrPreviewLoading"><Icon name="db" :size="12" />属性分级预览</button>
            <button class="btn xs" @click="loadAttrPreviewById" :loading="attrPreviewLoading"><Icon name="eye" :size="12" />按样式预览</button>
            <el-checkbox v-model="attrPreviewSort" @change="loadAttrPreview">按值排序</el-checkbox>
            <el-checkbox v-model="attrPreviewAll" @change="loadAttrPreview">全量（不分页）</el-checkbox>
          </div>
          <div v-if="Array.isArray(detail.ruleDatas) && detail.ruleDatas.length" class="card mb8">
            <div class="card-h">规则明细（ruleDatas）<span class="cnt">{{ detail.ruleDatas.length }}</span></div>
            <div class="card-b" style="padding: 0">
              <el-table :data="detail.ruleDatas" size="small" max-height="200">
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
                <el-table-column label="标题" min-width="100">
                  <template #default="{ row }"><span class="mono">{{ row.title || '-' }}</span></template>
                </el-table-column>
              </el-table>
            </div>
          </div>
          <div v-if="detail.styleInfo && typeof detail.styleInfo === 'object'" class="card mb8">
            <div class="card-h">符号参数（styleInfo）</div>
            <div class="card-b">
              <div class="row wrap" style="gap: 6px">
                <span v-for="[k, v] in Object.entries(detail.styleInfo).filter(([, v]) => v !== null && v !== '' && v !== false)" :key="k" class="tag gray mono">{{ k }}: {{ v }}</span>
              </div>
            </div>
          </div>
          <div v-if="Array.isArray(detail.tifStyles) && detail.tifStyles.length" class="card mb8">
            <div class="card-h">栅格分级（tifStyles）<span class="cnt">{{ detail.tifStyles.length }}</span></div>
            <div class="card-b" style="padding: 0">
              <el-table :data="detail.tifStyles" size="small" max-height="200">
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
          </div>
          <div v-if="attrPreview.rows.length" class="card mb8">
            <div class="card-h">
              属性分级预览<span class="cnt">GetDataAttributesPage · 共 {{ attrPreview.count }} 组</span>
            </div>
            <div class="card-b" style="padding: 0">
              <el-table :data="attrPreview.rows" size="small" max-height="220">
                <el-table-column label="色" width="56">
                  <template #default="{ row }"><span class="sw" :style="{ background: previewRowColor(row as any[]) }" /></template>
                </el-table-column>
                <el-table-column v-for="(c, ci) in attrPreview.columns" :key="c" :label="c" min-width="90">
                  <template #default="{ row }">
                    <span class="mono" v-if="ci === 0">{{ '' }}</span>
                    <span v-else class="mono">{{ row[ci] }}</span>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pager" style="padding: 6px 10px">
                <el-pagination
                  v-model:current-page="attrPreviewPage.index"
                  v-model:page-size="attrPreviewPage.size"
                  :total="attrPreview.count"
                  layout="total, prev, pager, next"
                  small
                  @current-change="loadAttrPreview"
                />
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else-if="!detailLoading" description="该样式暂无明细数据（历史数据或明细为空）" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- SLD 原文弹窗 -->
    <el-dialog v-model="sldVisible" :title="`SLD 原文 · ${sldTitle}`" width="720px" append-to-body>
      <div v-loading="sldLoading">
        <pre class="sld-pre">{{ sldText }}</pre>
      </div>
    </el-dialog>

    <!-- 色带预览弹窗 -->
    <el-dialog v-model="rampVisible" :title="`色带预览 · ${rampRow ? sName(rampRow) : ''}`" width="460px" append-to-body>
      <div v-if="rampRowGradient" class="ramp-bar lg" :style="{ background: rampRowGradient }" />
      <div v-else-if="rampRowColors.length === 1" class="ramp-bar lg" :style="{ background: rampRowColors[0] }" />
      <div v-else class="muted">无可预览色带</div>
      <div v-if="rampRowColors.length >= 2" class="ramp-swatches mt8">
        <span v-for="(c, i) in rampRowColors" :key="i" class="sw lg" :style="{ background: c }" :title="c" />
      </div>
    </el-dialog>

    <!-- 样式文件夹管理 -->
    <el-dialog v-model="folderVisible" title="样式文件夹管理" width="640px" append-to-body>
      <div class="row" style="gap: 12px; align-items: flex-start">
        <div style="width: 220px; flex: 0 0 220px">
          <div class="row" style="justify-content: space-between; margin-bottom: 6px">
            <span class="k">文件夹</span>
            <button class="btn xs primary" @click="addFolder"><Icon name="plus" :size="12" />新增</button>
          </div>
          <div v-loading="foldersLoading" class="col" style="gap: 4px; max-height: 320px; overflow: auto">
            <div
              v-for="f in folders"
              :key="f"
              class="folder-item"
              :class="{ active: activeFolder === f }"
              @click="activeFolder = f; loadFiles()"
            >
              <Icon name="tree" :size="13" />
              <span class="ellip flex1" :title="f">{{ f }}</span>
              <button class="btn xs" @click.stop="renameFolder(f)">重命名</button>
              <button class="btn xs danger" @click.stop="deleteFolder(f)">删</button>
            </div>
            <div v-if="!folders.length" class="muted" style="font-size: 12px">暂无文件夹</div>
          </div>
        </div>
        <div class="flex1">
          <div class="row" style="justify-content: space-between; margin-bottom: 6px">
            <span class="k">文件（{{ activeFolder || '未选择' }}）</span>
            <button class="btn xs" @click="loadFiles"><Icon name="refresh" :size="12" />刷新</button>
          </div>
          <el-table v-loading="filesLoading" :data="folderFiles" size="small" max-height="300" empty-text="暂无文件">
            <el-table-column label="文件名" min-width="160">
              <template #default="{ row }"><span class="mono">{{ row.name }}</span></template>
            </el-table-column>
            <el-table-column label="路径" min-width="180" show-overflow-tooltip>
              <template #default="{ row }"><span class="mono" style="font-size: 11px">{{ row.path }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <div class="row" style="gap: 4px">
                  <button class="btn xs" @click="parseFile(row as StyleFileEntry)">解析</button>
                  <button class="btn xs" @click="renameFile(row as StyleFileEntry)">重命名</button>
                  <button class="btn xs danger" @click="deleteFile(row as StyleFileEntry)">删</button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="folderVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文件解析结果 -->
    <el-dialog v-model="fileParseVisible" :title="`SLD 解析 · ${fileParseTitle}`" width="560px" append-to-body>
      <el-table :data="fileParseResult" size="small" max-height="360" empty-text="未解析到规则">
        <el-table-column label="#" width="44" type="index" />
        <el-table-column label="范围/值" min-width="130">
          <template #default="{ row }">
            <span class="mono">{{ row.valueOne ? `${row.valueOne} ~ ${row.value}` : row.value }}</span>
          </template>
        </el-table-column>
        <el-table-column label="符号色" width="80">
          <template #default="{ row }">
            <span class="sw" :style="{ background: parsePolygonStyle(row.polygonStyle)?.polygonColor || '#ccc' }" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="fileParseVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ramp-bar {
  height: 22px;
  border-radius: 5px;
  border: 1px solid var(--line);
}
.ramp-bar.lg {
  height: 34px;
}
.ramp-swatches {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.sw {
  width: 22px;
  height: 18px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  display: inline-block;
}
.sw.lg {
  width: 34px;
  height: 26px;
}
.ramp-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ramp-chip {
  width: 84px;
  height: 16px;
  border-radius: 4px;
  border: 1px solid var(--line);
  flex: 0 0 84px;
}
.ellip {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.sld-pre {
  margin: 0;
  max-height: 460px;
  overflow: auto;
  background: #0f1a26;
  color: #cfe3f7;
  padding: 12px;
  border-radius: 6px;
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.folder-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 5px;
  cursor: pointer;
  border: 1px solid transparent;
  font-size: 12px;
}
.folder-item:hover {
  background: #f2f6fb;
}
.folder-item.active {
  background: #e8f1fb;
  border-color: #b9d4ef;
}
input[type='color'] {
  width: 28px;
  height: 24px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: none;
  cursor: pointer;
}
</style>
