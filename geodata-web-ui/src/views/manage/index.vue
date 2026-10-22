<script setup lang="ts">
/**
 * 数据管理（#/manage）—— 对齐 docs/低空监管数据中台-数据管理原型.html
 * 真融合：新原型样式与结构为骨架，原「资源总览 /data」「数据接入 /ingest」「三维数据 /model3d」
 * 三页的功能与字段全部并入（旧页已删除）：
 *  - 左侧资源目录树：dictType 字典 + getLayerTree(Gzip 解压) + 分类计数 + 点击过滤（原资源总览）
 *  - 表格列保留原属性：分类 / 空间参考 / 发布状态 / 数据格式 等
 *  - 上传抽屉保留原表单字段：所属目录 / 空间参考系(字典) / 行政区划(region) / 数据密级 / 数据标签 / 摘要
 *  - 二维链路：分片上传 AddFiles→FileMerge→Insert(或 InsertImageLayer 同步建图)→UpdateStatus（原数据接入）
 *  - 三维链路（原三维数据页全量）：init(srs)→分片(5MiB)→complete→下发转换/重试；状态机进度轮询；
 *    断点续传（GET parts→跳过已传→续传→complete）；已发布可复制地址 / 三维预览
 * 能力收敛（已确认）：服务器目录上传入口禁用；SHP 坐标解析与裸分片通道移除；
 * 原型 6 类型不含 TILESET/SINGLE/BIM 直传类型（可经断点续传恢复任务）。
 * 服务管理页已并入（主导航 4→3）：发布状态筛选、服务格式探测列（WMS/WFS/WMTS + 切片中）、
 * 已发布数据「复制地址」、批量删除；原页「使用状态(启停)」为无端点装饰、发布向导由行内发布覆盖、
 * 「更新状态(已添加到地图)」归地图管理域，均收敛移除。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import ResourceDetail from '@/components/resource/ResourceDetail.vue'
import {
  DATA_TYPES,
  SERVICE_DEFAULTS,
  SERVICE_FORMATS,
  UPLOAD_ACCEPT,
  extFormat,
  isRasterTypeKey,
  loadUnifiedPage,
  m3dStatus,
  fetchSvcAddress,
  extractSvcUrl,
  type UnifiedRow,
} from '@/utils/datasets'
import {
  dataTypeApi,
  dictApi,
  layerContentApi,
  model3dApi,
  regionApi,
  serverInterfaceApi,
} from '@/api'
import { fmtTime, pickList, pickTotal } from '@/utils/format'
import { decompressJson } from '@/utils/gzip'
import { onImMessage, onImBizMessage, imEnabled, ensureImAlive, type ImMessage } from '@/utils/im'
import { copyText } from '@/utils/clipboard'
import { chunkedUpload, m3dUpload, m3dResume } from '@/utils/chunkUpload'
import { useUserStore } from '@/stores/user'
import ServicePreview from '@/components/preview/ServicePreview.vue'

const userStore = useUserStore()
const route = useRoute()
const USER = userStore.userName || 'admin'

/* ================= 左侧资源目录树（原资源总览） ================= */
const treeRef = ref()
const treeLoading = ref(false)
const treeData = ref<any[]>([])
const dictType = ref('')
const dictOptions = ref<any[]>([])
const treeMode = ref<'layer' | 'category'>('layer')
const selectedCatalog = ref('')
const mobileTreeOpen = ref(false)
function notifyResourceCountChanged() {
  window.dispatchEvent(new Event('geodata:resources-changed'))
}

async function loadDictOptions() {
  try {
    // 目录树分类维度 = sdms_datatype.dictCodeID（字典软绑定字符串），取值来自字典分类的 typeCode
    const res = await dictApi.typeList()
    dictOptions.value = pickList(res)
    const first = dictOptions.value[0]
    // dictType = 目录绑定字典分类的 GUID（sdms_datatype.dictCodeID）；优先「数据分类」(ba5ab799，代码硬编码值)
    const DATA_CATEGORY_ID = 'ba5ab799-6acd-11e7-87f3-005056bb1c7e'
    dictType.value = dictOptions.value.find((d: any) => d.id === DATA_CATEGORY_ID)?.id ?? first?.id ?? ''
  } catch {
    dictOptions.value = []
  }
}

/** 主通道：getLayerTree（Gzip+Base64 压缩串，先解压） */
async function loadTree() {
  treeLoading.value = true
  try {
    if (dictType.value) {
      const raw = await layerContentApi.getLayerTree({
        dictType: dictType.value,
        publishStatus: '',
        layerName: '',
        uploadFileType: '',
      })
      const arr = decompressJson<any[]>(String(raw || ''))
      if (Array.isArray(arr) && arr.length) {
        treeData.value = arr
        treeMode.value = 'layer'
        return
      }
    }
    // 兜底：直接用分类表构建目录树（无图层节点）
    const res = await dataTypeApi.getAllList()
    treeData.value = buildCategoryTree(pickList(res))
    treeMode.value = 'category'
    await applyCategoryCounts()
  } catch (e: any) {
    treeData.value = []
    if (!e?.silent) ElMessage.error(e?.message || '目录树加载失败')
  } finally {
    treeLoading.value = false
  }
}

function buildCategoryTree(list: any[]): any[] {
  if (!list.length) return []
  if (list.some((n) => Array.isArray(n.children))) return list
  const map = new Map<string, any>()
  list.forEach((n) => map.set(String(n.id), { ...n, name: n.typeName ?? n.name, children: [] }))
  const roots: any[] = []
  map.forEach((node) => {
    const pid = node.parentId ? String(node.parentId) : ''
    if (pid && map.has(pid)) map.get(pid).children.push(node)
    else roots.push(node)
  })
  return roots
}

/** 兜底模式补充各分类资源数（GET /layerContent/GetAllCountByDataType） */
async function applyCategoryCounts() {
  try {
    const res = await layerContentApi.getAllCountByDataType()
    const stats = pickList<any>(res)
    if (!stats?.length) return
    const byId = new Map<string, number>()
    stats.forEach((s) => {
      const k = String(s.dataType ?? s.dataTypeId ?? s.id ?? '')
      const c = Number(s.count ?? s.totalLayerCount ?? s.num ?? 0)
      if (k) byId.set(k, c)
    })
    const walk = (nodes: any[]) => {
      nodes.forEach((n) => {
        const c = byId.get(String(n.id))
        if (c != null) n.totalLayerCount = c
        if (Array.isArray(n.children)) walk(n.children)
      })
    }
    walk(treeData.value)
  } catch {
    /* 统计不可用不阻断 */
  }
}

function onTreeClick(node: any) {
  selectedCatalog.value = String(node?.id ?? '')
  mobileTreeOpen.value = false
  pageIndex.value = 1
  loadList()
}
function clearTreeFilter() {
  selectedCatalog.value = ''
  mobileTreeOpen.value = false
  treeRef.value?.setCurrentKey?.(null)
  pageIndex.value = 1
  loadList()
}

/** category 兜底模式懒加载子树（getLayerTreeByType，压缩串） */
const treeChildrenCache = new Map<string, any[]>()
async function loadTreeNode(node: any, resolve: (data: any[]) => void) {
  // el-tree 懒加载回调的 node 为内部节点对象，业务 id 在 node.data.id
  const bizId = String(node?.data?.id ?? '')
  if (treeMode.value !== 'category' || !bizId) {
    resolve([])
    return
  }
  if (treeChildrenCache.has(bizId)) {
    resolve(treeChildrenCache.get(bizId)!)
    return
  }
  try {
    const raw = await layerContentApi.getLayerTreeByType(bizId)
    const arr = decompressJson<any[]>(String(raw || ''))
    let kids: any[] = []
    if (Array.isArray(arr) && arr.length) {
      const root = arr.length === 1 && Array.isArray(arr[0]?.children) ? arr[0] : null
      kids = (root ? root.children : arr).map(normalizeTreeNode)
    }
    treeChildrenCache.set(bizId, kids)
    resolve(kids)
  } catch {
    resolve([])
  }
}
function normalizeTreeNode(n: any): any {
  return {
    ...n,
    id: String(n.id ?? n.layerId ?? n.dataTypeId ?? ''),
    name: String(n.name ?? n.layerName ?? n.typeName ?? '未命名'),
    _leaf: !Array.isArray(n.children) || n.children.length === 0,
    children: undefined,
  }
}

/* ================= 筛选与列表 ================= */
const filters = reactive({ type: '', status: '', pub: '', kw: '' })
const applied = reactive({ type: '', status: '', pub: '', kw: '', catalog: '' })
const order = ref<'desc' | 'asc'>('desc')
const rows = ref<UnifiedRow[]>([])
const total = ref(0)
const pageSize = ref(10)
const pageIndex = ref(1)
const loading = ref(false)
let listRequestId = 0

const STATUS_OPTS = [
  { value: 'uploading', label: '数据上传中' },
  { value: 'ok', label: '数据上传成功' },
  { value: 'fail', label: '处理失败' },
]

/** 发布状态筛选（原服务管理页并入；「未发布」为前端过滤） */
const PUB_OPTS = [
  { value: 'yes', label: '已发布' },
  { value: 'no', label: '未发布' },
]

async function loadList(silent = false) {
  const requestId = ++listRequestId
  if (!silent) loading.value = true
  try {
    const res = await loadUnifiedPage(
      {
        typeKey: applied.type,
        status: applied.status,
        keyword: applied.kw,
        layerType: applied.catalog,
        publishedOnly: applied.pub === 'yes' ? true : applied.pub === 'no' ? false : undefined,
      },
      pageSize.value,
      pageIndex.value,
    )
    if (requestId !== listRequestId) return
    let list = res.rows
    if (order.value === 'asc') list = [...list].reverse()
    rows.value = list
    total.value = res.total
    probeRows()
  } catch (e: any) {
    if (requestId === listRequestId && !silent) {
      rows.value = []
      total.value = 0
      ElMessage.error(e?.message || '列表加载失败')
    }
  } finally {
    if (requestId === listRequestId) {
      loading.value = false
      schedulePoll()
    }
  }
}

function doSearch() {
  applied.type = filters.type
  applied.status = filters.status
  applied.pub = filters.pub
  applied.kw = filters.kw.trim()
  applied.catalog = selectedCatalog.value
  pageIndex.value = 1
  loadList()
}
function doReset() {
  filters.type = filters.status = filters.pub = filters.kw = ''
  selectedCatalog.value = ''
  treeRef.value?.setCurrentKey?.(null)
  doSearch()
}
function toggleOrder() {
  order.value = order.value === 'desc' ? 'asc' : 'desc'
  rows.value = [...rows.value].reverse()
}

/** 三维转换/上传进行中（或二维上传中）时 5s 静默轮询刷新进度 */
let pollTimer: number | null = null
/* ================= 服务探测（原服务管理页并入） ================= */
/** 每行服务探测结果：key → { formats, url, tile } */
const svcInfo = reactive<Record<string, { formats: string[]; url: string; tile: string }>>({})

/** 逐行探测已发布服务的格式与切片状态（仅当前页，并行；未发布行跳过） */
async function probeRows() {
  await Promise.all(
    rows.value.map(async (r) => {
      if (r.src === 'm3d') {
        const st = String(r.raw?.status ?? '')
        svcInfo[r.key] = { formats: st === '4' ? ['3DTiles'] : [], url: '', tile: '' }
        return
      }
      if (!r.published) {
        delete svcInfo[r.key]
        return
      }
      const info = { formats: [] as string[], url: '', tile: '' }
      const probes = await Promise.allSettled([
        serverInterfaceApi.getServerInterface(r.id, 'WMS'),
        serverInterfaceApi.getServerInterface(r.id, 'WFS'),
        serverInterfaceApi.getServerInterface(r.id, 'WMTS'),
        serverInterfaceApi.getServerInterface(r.id, 'MVT'),
        serverInterfaceApi.tileProgress(r.id),
      ])
      // getServerInterface 返回 {publishType, list:[{关键字, 服务路径}]}（DTO 中文键），按所查类型取 服务路径
      const pick = (res: any, type: string): string => extractSvcUrl(res, type)
      const wmsUrl = probes[0].status === 'fulfilled' ? pick((probes[0] as any).value, 'WMS') : ''
      if (wmsUrl) {
        info.formats.push('WMS')
        info.url = info.url || wmsUrl
      }
      if (probes[1].status === 'fulfilled' && pick((probes[1] as any).value, 'WFS')) info.formats.push('WFS')
      if (probes[2].status === 'fulfilled' && pick((probes[2] as any).value, 'WMTS')) info.formats.push('WMTS')
      if (probes[3].status === 'fulfilled' && pick((probes[3] as any).value, 'MVT')) info.formats.push('MVT')
      if (probes[4].status === 'fulfilled') {
        const tp: any = (probes[4] as any).value
        const st = String(tp?.result?.status ?? tp?.status ?? '').toUpperCase()
        if (st === 'RUNNING') info.tile = '切片中'
      }
      svcInfo[r.key] = info
    }),
  )
}

const hasRunning = computed(() =>
  rows.value.some(
    (r) =>
      r.status === 'uploading' ||
      r.status === 'converting' ||
      (r.src === 'm3d' && ['0', '2', '3'].includes(String(r.raw?.status ?? ''))) ||
      svcInfo[r.key]?.tile === '切片中',
  ),
)
const anyDialogOpen = computed(
  () => upload.visible || edit.visible || detail.visible || publish.visible || previewVisible.value || drawerVisible.value,
)
function schedulePoll() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(
    () => {
      // IM 断线自愈：automaticReconnect 序列耗尽后不会自动恢复，轮询 tick 里幂等重连（连接正常时零成本）
      ensureImAlive()
      if (hasRunning.value && !anyDialogOpen.value) loadList(true)
      else schedulePoll()
    },
    imEnabled() ? 15000 : 5000,
  )
}

/* ================= 上传抽屉（全量字段 = 原新建资源表单 + 原型结构） ================= */
const upload = reactive({
  visible: false,
  submitting: false,
  progress: 0,
  progressText: '',
  name: '',
  typeKey: '' as string,
  catalogId: '',
  layerRefence: '',
  srs: '',
  area: '',
  confidential: '内部',
  keywords: '',
  layerDesc: '',
  autoMap: false,
  srcMode: 'local' as 'local' | 'dir' | 'resume',
  dir: '',
  resumeId: '',
  file: null as File | null,
  /** SHP 家族配套文件（.shx/.dbf/.prj/.cpg），仅矢量多选时填充 */
  familyFiles: [] as File[],
})

const uploadInputRef = ref<HTMLInputElement>()
const dragOver = ref(false)
const fmtPopVisible = ref(false)

/* 所属目录（sdms_datatype，与图层配置同源） */
const catalogOptions = ref<{ id: string; label: string }[]>([])
function flattenCatalog(list: any[], out: { id: string; label: string }[] = []) {
  list.forEach((n: any) => {
    out.push({ id: String(n.id), label: String(n.typeName ?? n.name ?? n.id) })
    if (Array.isArray(n.children)) flattenCatalog(n.children, out)
  })
  return out
}
async function loadCatalogs() {
  try {
    catalogOptions.value = flattenCatalog(pickList(await dataTypeApi.getAllList()))
  } catch {
    catalogOptions.value = []
  }
}

/* 空间参考系（字典）/ 行政区划（whulri 基座 /region/list） */
const srsOptions = ref<any[]>([])
const areaOptions = ref<any[]>([])
async function loadFormOptions() {
  try {
    srsOptions.value = pickList(await dictApi.codeAll())
  } catch {
    srsOptions.value = []
  }
  try {
    areaOptions.value = await regionApi.getAllList()
  } catch {
    areaOptions.value = []
  }
}

const curType = computed(() => DATA_TYPES.find((t) => t.key === upload.typeKey))
const isLayerType = computed(() => curType.value?.src === 'layer')
const isM3dType = computed(() => curType.value?.src === 'm3d')
const isRasterType = computed(() => upload.typeKey === 'raster' || upload.typeKey === 'terrain')
const uploadAccept = computed(() =>
  isM3dType.value ? '.osgb,.las,.laz,.obj,.fbx,.ifc,.zip,.json' : UPLOAD_ACCEPT,
)

/** 格式自动识别 */
const detect = computed(() => {
  const f = upload.srcMode === 'local' ? upload.file?.name : upload.dir.trim()
  if (!f) return null
  const seg = f.replace(/[\\/]+$/, '').split(/[\\/]/).pop() || ''
  const ext = seg.includes('.') ? seg.split('.').pop()!.toLowerCase() : ''
  const fmt = extFormat(seg)
  const candidates = DATA_TYPES.filter((t) => t.formats.includes(fmt)).map((t) => t.key)
  return { ext, fmt, candidates, archive: ['zip', 'tar', 'gz', 'tgz', '7z', 'rar'].includes(ext) }
})
const detectClass = computed(() => {
  const d = detect.value
  if (!d) return ''
  if (d.archive || !d.fmt) return 'warn'
  return d.candidates.includes(upload.typeKey as any) ? 'ok' : 'warn'
})
const detectText = computed(() => {
  const d = detect.value
  if (!d) return '选择数据文件后，系统将按扩展名自动识别数据格式。'
  const def = curType.value?.formats[0] || '-'
  if (upload.srcMode === 'resume') return '续传模式：将按原任务分片协议跳过已传分片，格式沿用原任务登记。'
  if (d.archive)
    return `上传的是压缩包（.${d.ext}），无法直接读取内部格式，暂按数据类型「${curType.value?.label}」的默认格式 ${def} 登记。`
  if (!d.fmt) return `无法识别扩展名 .${d.ext}，暂按数据类型「${curType.value?.label}」的默认格式 ${def} 登记。`
  if (d.candidates.includes(upload.typeKey as any))
    return `已识别数据格式：${d.fmt}（按扩展名 .${d.ext}），与数据类型匹配。`
  const candLabel = d.candidates.map((k) => DATA_TYPES.find((t) => t.key === k)?.label).join(' / ')
  return `已识别数据格式：${d.fmt}（按扩展名 .${d.ext}），与当前选择的数据类型「${curType.value?.label}」不一致（该格式通常属「${candLabel || '未知类型'}」）。`
})

/** 三维类型/源格式映射（zip 由后端解压后按类型定位内容；tileset 直传 zip 或 tileset.json） */
const M3D_TYPE_BY_KEY: Record<string, string> = {
  oblique: 'OSGB',
  pointcloud: 'POINTCLOUD',
  tileset: 'TILESET',
  single: 'SINGLE',
  bim: 'BIM',
}
const M3D_FMT_BY_KEY: Record<string, string[]> = {
  oblique: ['osgb', 'zip'],
  pointcloud: ['las', 'laz', 'zip'],
  tileset: ['zip', 'json'],
  single: ['obj', 'fbx', 'zip'],
  bim: ['ifc', 'zip'],
}
/** 计算 srcFormat：zip 无法探测内部格式，按类型取默认源格式 */
function m3dSrcFormat(typeKey: string, fileName: string): string {
  const ext = (fileName.split('.').pop() || '').toLowerCase()
  if (typeKey === 'tileset') return ['zip', 'json'].includes(ext) ? 'tileset' : ''
  if (ext === 'zip') {
    const fallback: Record<string, string> = { oblique: 'osgb', pointcloud: 'las', single: 'obj', bim: 'ifc' }
    return fallback[typeKey] || ''
  }
  return ['osgb', 'las', 'laz', 'obj', 'fbx', 'ifc'].includes(ext) ? ext : ''
}

function openUpload() {
  upload.visible = true
  upload.name = ''
  upload.typeKey = ''
  upload.catalogId = ''
  upload.layerRefence = ''
  upload.srs = ''
  upload.area = ''
  upload.confidential = '内部'
  upload.keywords = ''
  upload.layerDesc = ''
  upload.autoMap = false
  upload.srcMode = 'local'
  upload.dir = ''
  upload.resumeId = ''
  upload.file = null
  upload.familyFiles = []
  upload.progress = 0
  upload.progressText = ''
  upload.submitting = false
  if (!catalogOptions.value.length) loadCatalogs()
  if (!srsOptions.value.length || !areaOptions.value.length) loadFormOptions()
}

/** 矢量数据支持 SHP 家族多选（.shp 主文件 + .shx/.dbf/.prj/.cpg） */
const canMultiPick = computed(
  () => curType.value?.src === 'layer' && !isRasterTypeKey(curType.value?.key),
)

/** 归一选择结果：矢量家族优先取 .shp 作主文件，其余作配套文件；非矢量仅取第一个 */
function applyPickedFiles(list: File[]) {
  if (!list.length) return
  if (!canMultiPick.value && list.length > 1) {
    ElMessage.warning('该数据类型仅支持单文件上传，已取第一个文件')
  }
  const files = canMultiPick.value ? [...list] : [list[0]]
  let primary = files[0]
  if (canMultiPick.value && files.length > 1) {
    const shp = files.find((f) => /\.shp$/i.test(f.name))
    if (shp) primary = shp
  }
  upload.file = primary
  upload.familyFiles = files.filter((f) => f !== primary)
  if (!upload.name.trim()) upload.name = primary.name.replace(/\.[^.]+$/, '')
}
function onPickFile(e: Event) {
  const t = e.target as HTMLInputElement
  applyPickedFiles(Array.from(t.files || []))
  t.value = ''
}
function onDropFile(e: DragEvent) {
  dragOver.value = false
  applyPickedFiles(Array.from(e.dataTransfer?.files || []))
}
function removeFile() {
  upload.file = null
  upload.familyFiles = []
  if (uploadInputRef.value) uploadInputRef.value.value = ''
}
/** 切换类型时清理不适用的字段 */
watch(
  () => upload.typeKey,
  () => {
    upload.autoMap = false
  },
)

/* ---------- 上传链路 ---------- */

async function submitUpload() {
  if (upload.srcMode === 'resume') {
    if (!upload.resumeId.trim()) return ElMessage.warning('请输入原上传任务 id')
    if (!upload.file) return ElMessage.warning('请选择与原任务相同的文件')
    upload.submitting = true
    upload.progress = 0
    try {
      await m3dResume(upload.resumeId, upload.file!, (p, t) => {
        upload.progress = p
        if (t) upload.progressText = t
      })
      ElMessage.success('续传完成，状态为「待转换」，可在列表中下发转换')
      upload.visible = false
      pageIndex.value = 1
      await loadList()
    } catch (e: any) {
      ElMessage.error(e?.message || '续传失败（已传分片会跳过，可再次尝试）')
    } finally {
      upload.submitting = false
    }
    return
  }

  if (!upload.name.trim()) return ElMessage.warning('请填写数据名称')
  if (!upload.typeKey) return ElMessage.warning('请选择数据类型')
  if (upload.srcMode === 'dir') return ElMessage.warning('服务器目录导入暂未开放，请使用本地上传')
  if (!upload.file) return ElMessage.warning('请选择本地文件')

  const t = curType.value!
  const file = upload.file
  upload.submitting = true
  upload.progress = 0
  try {
    if (t.src === 'layer') {
      // 矢量/栅格：分片上传 → 合并 → 入库（或同步建图 InsertImageLayer）
      // 矢量：后端导入仅支持 SHP（GDAL ShpReader + UploadFile 目录中家族文件齐备），
      // 需多选 .shp/.shx/.dbf（建议含 .prj/.cpg）一起上传；压缩包等格式无法入库
      if (canMultiPick.value) {
        const extent = (n: string) => n.toLowerCase().split('.').pop() || ''
        const exts = [file, ...(upload.familyFiles || [])].map((f) => extent(f.name))
        const allowed = ['shp', 'shx', 'dbf', 'prj', 'cpg']
        const bad = exts.find((e) => !allowed.includes(e))
        if (bad) {
          throw new Error(
            `矢量入库仅支持 SHP 系列文件（.shp/.shx/.dbf/.prj/.cpg），当前包含 .${bad}，请解压后重试`,
          )
        }
        if (!exts.includes('shp') || !exts.includes('shx') || !exts.includes('dbf')) {
          throw new Error('SHP 配套文件不完整：请一次多选 .shp + .shx + .dbf（建议含 .prj/.cpg）')
        }
        if (new Set(exts).size !== exts.length) throw new Error('同一 SHP 文件组中存在重复扩展名，请只选择一组数据')
        const stems = [file, ...(upload.familyFiles || [])].map((f) => f.name.replace(/\.[^.]+$/, '').toLowerCase())
        if (new Set(stems).size !== 1) throw new Error('SHP 配套文件必须具有相同的主文件名')
        if ([file, ...(upload.familyFiles || [])].some((f) => f.size === 0)) throw new Error('SHP 文件组包含空文件')
      }
      const allFiles = [file, ...(upload.familyFiles || [])]
      let merged: any = null
      for (let i = 0; i < allFiles.length; i++) {
        const f = allFiles[i]
        const m: any = await chunkedUpload(f, (p, txt) => {
          upload.progress = Math.round(((i + p / 100) / allFiles.length) * 100)
          if (txt) upload.progressText = allFiles.length > 1 ? `${f.name}：${txt}` : txt
        })
        if (i === 0) merged = m
      }
      // 入库契约（LayerContentServiceImpl）：
      //  - filePathName 传「文件名」：后端多处用 basename 与 UploadFile 目录拼接，
      //    传全路径会在 Paths.get 多段拼接时抛 InvalidPathException（Windows 实测）
      //  - uploadFileName 传服务端全路径（InsertImageLayer 无 filePathName query，靠它定位文件）
      const fileNameOnly =
        String(merged?.logicName || '').trim() ||
        String(merged?.physicalName || '').replace(/\\/g, '/').split('/').pop() ||
        file.name
      const fullPath = String(merged?.physicalName || '')
      const common = {
        layerName: upload.name.trim(),
        // sdms_layer.datatype 由后端在 SHP 解析完成后按实际几何类型回填（栅格由后端置为「影像」）
        dataType: '',
        layerType: upload.catalogId,
        layerRefence: upload.layerRefence,
        area: upload.area,
        dataConfidential: upload.confidential,
        keywords: upload.keywords,
        layerDesc: upload.layerDesc,
        uploadFileType: isRasterTypeKey(t.key) ? '2' : '1',
        uploadFileName: fullPath || fileNameOnly,
        fileSize: Math.round(file.size / 1024),
        createBy: USER,
      }
      if (upload.autoMap && isRasterType.value) {
        // 同步建图：InsertImageLayer（form 表单绑定，无 @RequestBody）入库并创建地图
        const mapEntity: any = await layerContentApi.insertImageLayer({
          ...common,
          createByName: USER,
        })
        if (!mapEntity?.id) throw new Error('入库失败：后端未创建地图记录（详见服务端日志）')
        ElMessage.success(`「${upload.name.trim()}」已入库并创建地图（id=${mapEntity.id}）`)
      } else {
        const created: any = await layerContentApi.insert(
          { ...common, uploadStatus: '1' },
          { filePathName: fileNameOnly, user: USER, isImport: '1' },
        )
        if (!created?.id) {
          throw new Error('入库失败：后端未返回图层记录（请检查文件完整性，详见服务端日志/图层日志）')
        }
        await layerContentApi.updateStatus(created.id)
        ElMessage.success(`「${upload.name.trim()}」上传入库成功`)
      }
    } else {
      // 三维：init(含 srs) → 分片 → complete。
      // TILESET 直传（zip / tileset.json）：complete 内同步解压+发布（接口已放宽超时）；
      // 其余格式（OSGB/LAS/LAZ/OBJ/FBX/IFC，支持 zip 包）complete 后为「待转换」，列表中下发转换
      const ext = (file.name.split('.').pop() || '').toLowerCase()
      if (!M3D_FMT_BY_KEY[upload.typeKey]?.includes(ext)) {
        return ElMessage.warning(
          `数据类型「${t.label}」不支持 .${ext || '?'} 文件（允许：${M3D_FMT_BY_KEY[upload.typeKey]?.join(' / ')}）`,
        )
      }
      const srcFormat = m3dSrcFormat(upload.typeKey, file.name)
      if (!srcFormat) {
        return ElMessage.warning(`无法识别文件「${file.name}」的三维源格式，请检查扩展名`)
      }
      await m3dUpload(
        file,
        { name: upload.name, modelType: M3D_TYPE_BY_KEY[upload.typeKey], srcFormat, srs: upload.srs, userCode: USER },
        (p, txt) => {
          upload.progress = p
          if (txt) upload.progressText = txt
        },
      )
      const isTilesetDirect = M3D_TYPE_BY_KEY[upload.typeKey] === 'TILESET'
      ElMessage.success(
        isTilesetDirect
          ? `「${upload.name.trim()}」上传完成，现成 3DTiles 已直接发布`
          : `「${upload.name.trim()}」上传完成，状态为「待转换」，请在列表中下发转换`,
      )
    }
    upload.visible = false
    pageIndex.value = 1
    await Promise.all([loadList(), loadTree()])
    notifyResourceCountChanged()
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败（上传中断可稍后重试）')
  } finally {
    upload.submitting = false
    upload.progressText = ''
  }
}

/* ================= 编辑（仅名称与摘要，仅二维） ================= */
const edit = reactive({ visible: false, saving: false, id: '', name: '', desc: '' })

function openEdit(row: UnifiedRow) {
  if (row.src === 'm3d') return
  edit.visible = true
  edit.id = row.id
  edit.name = row.name
  edit.desc = String(row.raw?.layerDesc ?? '')
}
async function saveEdit() {
  if (!edit.name.trim()) return ElMessage.warning('请填写数据名称')
  edit.saving = true
  try {
    const row = rows.value.find((r) => r.id === edit.id)
    await layerContentApi.update({
      ...(row?.raw || {}),
      id: edit.id,
      layerName: edit.name.trim(),
      layerDesc: edit.desc.trim(),
    })
    ElMessage.success('保存成功')
    edit.visible = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    edit.saving = false
  }
}

/* ================= 详情（二维 ResourceDetail 抽屉 / 三维增强弹窗） ================= */
const drawerVisible = ref(false)
const drawerLayer = ref<any>(null)
const drawerTab = ref('overview')
const detail = reactive({ visible: false, row: null as UnifiedRow | null })

function openDetail(row: UnifiedRow, initialTab = 'overview') {
  if (row.src === 'layer') {
    drawerLayer.value = { ...row.raw, id: row.id }
    drawerTab.value = initialTab
    drawerVisible.value = true
  } else {
    detail.row = row
    detail.visible = true
  }
}

const detailRows = computed<[string, string][]>(() => {
  const r = detail.row
  if (!r) return []
  const raw = r.raw || {}
  const base: [string, string][] = [
    ['数据名称', r.name],
    ['数据类型', r.typeLabel],
    ['源格式', r.format],
    ['数据大小', r.size],
    ['分片数', String(raw.partCount ?? '—')],
    ['空间参考', String(raw.srs ?? '—')],
    ['源文件', String(raw.fileName ?? '—')],
    ['上传人', r.uploader],
    ['创建时间', r.time],
    ['当前状态', r.statusLabel + (r.status === 'uploading' || r.status === 'converting' ? `（${r.progress}%）` : '')],
  ]
  if (raw.tileCount != null) base.push(['瓦片节点数', String(raw.tileCount)])
  if (raw.lodDepth != null) base.push(['LOD 深度', String(raw.lodDepth)])
  if (raw.bbox) base.push(['地理包围盒', String(raw.bbox)])
  if (raw.taskId) base.push(['转换任务 id', String(raw.taskId)])
  if (raw.failReason) base.push(['失败原因', String(raw.failReason)])
  return base
})

/* ================= 发布（二维 OGC / 三维下发转换） ================= */
const publish = reactive({
  visible: false,
  submitting: false,
  row: null as UnifiedRow | null,
  name: '',
  formats: [] as string[],
})

const publishAvail = computed(() => (publish.row ? SERVICE_FORMATS[publish.row.typeKey] || [] : []))

function openPublish(row: UnifiedRow) {
  if (row.src === 'm3d') {
    const st = String(row.raw?.status ?? '')
    if (st === '2' || st === '3' || st === '4') return
    ElMessageBox.confirm(
      `确认对「${row.name}」下发三维转换任务？转换完成后将自动发布为 3DTiles 服务。`,
      '下发转换',
      { type: 'info', confirmButtonText: '下发', cancelButtonText: '取消' },
    )
      .then(async () => {
        await model3dApi.convert(row.id)
        ElMessage.success('已下发转换任务，可在列表跟踪进度')
        await loadList()
      })
      .catch(() => {})
    return
  }
  publish.row = row
  publish.name = row.name
  publish.formats = [...(SERVICE_DEFAULTS[row.typeKey] || [])]
  publish.visible = true
}

async function submitPublish() {
  const row = publish.row
  if (!row) return
  if (!publish.name.trim()) return ElMessage.warning('请填写服务名称')
  if (!publish.formats.length) return ElMessage.warning('请至少选择一个服务格式')
  publish.submitting = true
  try {
    for (const t of publish.formats) {
      await serverInterfaceApi.publishService({
        id: row.id,
        layerName: publish.name.trim(),
        publishType: t,
      })
    }
    ElMessage.success(`服务「${publish.name.trim()}」发布请求已提交：${publish.formats.join(' / ')}`)
    publish.visible = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败（请确认 GeoServer 服务可用）')
  } finally {
    publish.submitting = false
  }
}

/** 复制服务地址（原服务管理页并入；三维=3DTiles 加载地址，二维=探测的 WMS/WMTS/WFS 地址） */
async function copySvcAddr(row: UnifiedRow) {
  const url = svcInfo[row.key]?.url || (await fetchSvcAddress(row).catch(() => ''))
  if (!url) return ElMessage.warning('未获取到服务地址（请确认服务已发布成功）')
  ;(await copyText(url))
    ? ElMessage.success('地址已复制：' + url)
    : ElMessage.error('复制失败，请手动选择复制')
}

/* ================= 删除 ================= */
async function removeRow(row: UnifiedRow) {
  const tip =
    row.src === 'layer'
      ? '删除后数据及其发布信息将被清理（DeleteLayer）。'
      : '删除后将清理三维数据对象与转换记录。'
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？${tip}`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    if (row.src === 'layer') await layerContentApi.removeLayer(row.id)
    else await model3dApi.remove(row.id)
    ElMessage.success('已删除')
    await Promise.all([loadList(), loadTree()])
    notifyResourceCountChanged()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

/* ================= 批量删除（原服务管理页并入） ================= */
const picked = ref<Set<string>>(new Set())
const batchVisible = computed(() => picked.value.size > 0)
const batchCount = computed(() => picked.value.size)
function onSelectionChange(selection: UnifiedRow[]) {
  picked.value = new Set(selection.map((r) => r.key))
}
async function batchDel() {
  const targets = rows.value.filter((r) => picked.value.has(r.key))
  if (!targets.length) return
  try {
    await ElMessageBox.confirm(
      '确认删除已选中的 ' + targets.length + ' 条数据？删除后数据及其发布信息将被清理。',
      '批量删除',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  let ok = 0
  for (const r of targets) {
    try {
      if (r.src === 'layer') await layerContentApi.removeLayer(r.id)
      else await model3dApi.remove(r.id)
      ok++
    } catch {
      /* 单个失败继续 */
    }
  }
  ElMessage.success('已删除 ' + ok + ' 条数据')
  picked.value = new Set()
  await Promise.all([loadList(), loadTree()])
  notifyResourceCountChanged()
}

/* ================= 预览（三维预览 / 二维已发布预览） ================= */
const previewVisible = ref(false)
const previewRow = ref<UnifiedRow | null>(null)
function openPreview(row: UnifiedRow) {
  if (!row.published && row.src === 'layer') return ElMessage.warning('该数据尚未发布为服务，请先发布')
  if (svcInfo[row.key]?.tile === '切片中') return ElMessage.warning('该服务切片尚未完成，暂不可预览')
  if (row.src === 'm3d' && String(row.raw?.status ?? '') !== '4')
    return ElMessage.warning('三维数据转换发布完成后才可预览')
  previewRow.value = row
  previewVisible.value = true
}

/* ================= 行操作辅助 ================= */
function canPublish(row: UnifiedRow): boolean {
  if (row.src === 'layer') return true
  const st = String(row.raw?.status ?? '')
  return st === '1' || st === '5'
}
function publishLabel(row: UnifiedRow): string {
  if (row.src === 'layer') return row.published ? '重新发布' : '发布'
  const st = String(row.raw?.status ?? '')
  if (st === '1') return '发布'
  if (st === '5') return '重试'
  if (st === '4') return '复制地址'
  return '转换中'
}
function statusClass(r: UnifiedRow): string {
  if (r.status === 'ok') return 's-ok'
  if (r.status === 'fail') return 's-err'
  return 's-run'
}
function fmtTimeCol(t: string): string {
  return fmtTime(t)
}

/* ================= 生命周期 ================= */
/** SignalR 实时进度（.NET IM 服务；未部署时静默降级，5s 轮询兜底） */
let imUnsub: (() => void) | null = null
let imRefreshTimer: number | null = null
/** IM 事件行级 patch：三维行按 bizId 局部更新（进度/状态/失败原因/发布键），跳过乱序回退 */
function patchM3dRowByMsg(msg: ImMessage) {
  if (msg.bizId == null || msg.status == null) return
  const id = String(msg.bizId)
  const patch = (r: UnifiedRow): boolean => {
    if (r.src !== 'm3d' || r.id !== id) return false
    const cur = Number(r.raw?.status)
    if (!Number.isNaN(cur) && msg.status! < cur) return false // 乱序旧消息不回退
    r.raw.status = String(msg.status)
    if (msg.progress != null) r.raw.progress = msg.progress
    if (msg.failReason != null) r.raw.failReason = msg.failReason
    if (msg.publishKey != null) r.raw.publishObjectKey = msg.publishKey
    const st = m3dStatus(r.raw.status, r.raw.progress)
    r.status = st.status
    r.statusLabel = st.label
    r.progress = st.progress
    r.published = String(r.raw.status) === '4'
    return true
  }
  rows.value.forEach(patch)
  // 详情弹窗打开时同步 patch（弹窗内进度/失败原因实时可见）
  if (detail.visible && detail.row) patch(detail.row)
}

function onImMsg(msg: ImMessage) {
  if (!['Model3dConvert', 'layerInsertLog'].includes(msg.moduleType)) return
  // 行级局部更新先行：不受弹窗/节流门控，进度条秒级推进（含详情弹窗内）
  patchM3dRowByMsg(msg)
  // 权威静默刷新仍节流；弹窗打开时不打断（anyDialogOpen 门控保留）
  if (imRefreshTimer) return
  imRefreshTimer = window.setTimeout(() => {
    imRefreshTimer = null
    if (!anyDialogOpen.value) loadList(true)
    if (msg.isFinish) loadTree()
  }, 1200)
}

onMounted(async () => {
  await loadDictOptions()
  await loadTree()
  await loadList()
  loadCatalogs()
  loadFormOptions()
  imUnsub = onImMessage(USER, onImMsg)
  if (route.query.upload === 'raster') {
    openUpload()
    upload.typeKey = 'raster'
  }
})
onBeforeUnmount(() => {
  if (pollTimer) window.clearTimeout(pollTimer)
  if (imRefreshTimer) window.clearTimeout(imRefreshTimer)
  imUnsub?.()
})
</script>

<template>
  <div class="manage-page">
    <PageHead title="数据管理" sub="矢量 / 栅格 / 三维数据统一接入：目录树检索、上传、发布与预览全链路">
      <template #tools>
        <el-button class="btn sm catalog-trigger" @click="mobileTreeOpen = !mobileTreeOpen"><Icon name="tree" :size="13" /> {{ mobileTreeOpen ? '收起目录' : '目录筛选' }}</el-button>
        <el-button class="btn sm primary" @click="openUpload"><Icon name="plus" :size="13" /> 上传数据</el-button>
      </template>
    </PageHead>

    <div class="manage-body">
      <!-- 左：资源目录树（原资源总览） -->
      <div class="card tree-panel" :class="{ 'mobile-open': mobileTreeOpen }">
        <div class="tree-tools">
          <el-select
            v-model="dictType"
            size="small"
            placeholder="字典类型(dictType)"
            clearable
            filterable
            style="width: 100%"
            @change="loadTree"
          >
            <el-option
              v-for="d in dictOptions"
              :key="d.id"
              :label="d.typeName || d.typeCode"
              :value="d.id"
            />
          </el-select>
        </div>
        <div class="tree-all" :class="{ on: !selectedCatalog }" @click="clearTreeFilter">
          <Icon name="layers" :size="13" />
          全部数据
        </div>
        <div v-loading="treeLoading" class="tree-wrap">
          <el-tree
            v-if="treeData.length"
            ref="treeRef"
            :data="treeData"
            node-key="id"
            :props="{ label: 'name', children: 'children', isLeaf: '_leaf' }"
            highlight-current
            :current-node-key="selectedCatalog"
            :lazy="treeMode === 'category'"
            :load="loadTreeNode"
            :default-expand-all="treeMode === 'layer'"
            @node-click="onTreeClick"
          >
            <template #default="{ data }">
              <span class="tree-node" :class="{ islayer: !!data.layerContent }">
                <span class="tn-name">{{ data.name }}</span>
                <span v-if="data.totalLayerCount != null" class="tn-cnt">{{ data.totalLayerCount }}</span>
              </span>
            </template>
          </el-tree>
          <el-empty v-else-if="!treeLoading" description="暂无目录数据（可在 图层配置 页建立）" :image-size="60" />
        </div>
      </div>

      <!-- 右：筛选 + 列表（新原型样式） -->
      <div class="list-col">
        <div class="card filter-card">
          <div class="filters">
            <el-select v-model="filters.type" placeholder="请选择数据类型" clearable style="width: 170px" @change="doSearch">
              <el-option v-for="t in DATA_TYPES" :key="t.key" :label="t.label" :value="t.key" />
            </el-select>
            <el-select v-model="filters.status" placeholder="请选择上传状态" clearable style="width: 150px" @change="doSearch">
              <el-option v-for="s in STATUS_OPTS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-select v-model="filters.pub" placeholder="请选择发布状态" clearable style="width: 150px" @change="doSearch">
              <el-option v-for="p in PUB_OPTS" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
            <el-input
              v-model="filters.kw"
              placeholder="请输入数据名称"
              clearable
              style="width: 200px"
              @keyup.enter="doSearch"
              @clear="doSearch"
            />
            <el-button class="btn sm primary" @click="doSearch">搜索</el-button>
            <el-button class="btn sm" @click="doReset">重置</el-button>
          </div>
        </div>

        <div class="card table-card">
          <el-table v-loading="loading" :data="rows" size="small" height="100%" @selection-change="onSelectionChange">
            <el-table-column type="selection" width="42" />
            <el-table-column label="数据名称" min-width="170" show-overflow-tooltip>
              <template #default="{ row }: any">
                <a class="row-link" @click="openDetail(row)">{{ row.name }}</a>
              </template>
            </el-table-column>
            <el-table-column label="数据类型" width="126">
              <template #default="{ row }: any">
                <span class="tag" :class="row.src === 'm3d' ? 'violet' : row.typeKey === 'raster' ? 'orange' : 'blue'">
                  {{ row.typeLabel }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="数据格式" width="84">
              <template #default="{ row }: any"><span class="fmt">{{ row.format }}</span></template>
            </el-table-column>
            <el-table-column label="分类" width="104" show-overflow-tooltip>
              <template #default="{ row }: any">{{ row.category || '—' }}</template>
            </el-table-column>
            <el-table-column label="空间参考" width="128" show-overflow-tooltip>
              <template #default="{ row }: any">{{ row.spatialRef || '—' }}</template>
            </el-table-column>
            <el-table-column label="数据大小" width="88">
              <template #default="{ row }: any">{{ row.size }}</template>
            </el-table-column>
            <el-table-column label="上传人" width="92" show-overflow-tooltip>
              <template #default="{ row }: any">{{ row.uploader }}</template>
            </el-table-column>
            <el-table-column width="152">
              <template #header>
                <span class="th-sort" @click="toggleOrder">
                  上传时间
                  <span class="arrows" :class="order"><i></i><i></i></span>
                </span>
              </template>
              <template #default="{ row }: any"><span class="muted">{{ fmtTimeCol(row.time) }}</span></template>
            </el-table-column>
            <el-table-column label="上传状态" width="164">
              <template #default="{ row }: any">
                <div class="status" :class="statusClass(row)">
                  <span class="d"></span>{{ row.statusLabel }}
                  <template v-if="row.status === 'uploading' || row.status === 'converting'"> {{ row.progress }}%</template>
                </div>
                <div v-if="row.status === 'uploading' || row.status === 'converting'" class="pbar">
                  <i :style="{ width: Math.max(4, row.progress) + '%' }"></i>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="发布" width="72">
              <template #default="{ row }: any">
                <span class="tag" :class="row.published ? 'green' : 'gray'">
                  {{ row.published ? '已发布' : '未发布' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="服务格式" width="118">
              <template #default="{ row }: any">
                <template v-if="svcInfo[row.key]?.formats?.length">
                  <span class="svc-fmts">{{ svcInfo[row.key].formats.join(' / ') }}</span>
                  <span v-if="svcInfo[row.key]?.tile === '切片中'" class="tag orange" style="margin-left: 4px">切片中</span>
                </template>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="318" fixed="right">
              <template #default="{ row }: any">
                <!-- 二维 -->
                <template v-if="row.src === 'layer'">
                  <el-button
                    v-if="row.published"
                    class="op"
                    link
                    type="primary"
                    size="small"
                    @click="copySvcAddr(row)"
                  >复制地址</el-button>
                  <span v-if="row.published" class="op-sep">|</span>
                  <el-button
                    class="op"
                    link
                    type="primary"
                    size="small"
                    @click="openEdit(row)"
                  >编辑</el-button>
                  <span class="op-sep">|</span>
                  <el-button class="op" link type="primary" size="small" @click="openDetail(row)">详情</el-button>
                  <span class="op-sep">|</span>
                  <el-button v-if="row.typeKey === 'vector'" class="op" link type="primary" size="small" @click="openDetail(row, 'data')">属性</el-button>
                  <span v-if="row.typeKey === 'vector'" class="op-sep">|</span>
                  <el-button class="op" link type="primary" size="small" @click="openPublish(row)">
                    {{ publishLabel(row) }}
                  </el-button>
                  <span class="op-sep">|</span>
                  <el-button class="op del" link type="danger" size="small" @click="removeRow(row)">删除</el-button>
                </template>
                <!-- 三维：下发转换 / 重试 / 复制地址 / 三维预览 -->
                <template v-else>
                  <el-button
                    v-if="canPublish(row)"
                    class="op"
                    link
                    type="primary"
                    size="small"
                    @click="openPublish(row)"
                  >{{ publishLabel(row) }}</el-button>
                  <el-button
                    v-else-if="String(row.raw?.status ?? '') === '4'"
                    class="op"
                    link
                    type="primary"
                    size="small"
                    @click="copySvcAddr(row)"
                  >复制地址</el-button>
                  <span v-else class="op-dis">{{ publishLabel(row) }}</span>
                  <span class="op-sep">|</span>
                  <el-button
                    class="op"
                    link
                    type="primary"
                    size="small"
                    :disabled="String(row.raw?.status ?? '') !== '4'"
                    :title="String(row.raw?.status ?? '') === '4' ? '' : '转换发布完成后可预览'"
                    @click="openPreview(row)"
                  >预览</el-button>
                  <span class="op-sep">|</span>
                  <el-button class="op" link type="primary" size="small" @click="openDetail(row)">详情</el-button>
                  <span class="op-sep">|</span>
                  <el-button class="op del" link type="danger" size="small" @click="removeRow(row)">删除</el-button>
                </template>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无数据（请确认后端 geodata-service 已启动并已建库）" :image-size="80" />
            </template>
          </el-table>
          <div v-loading="loading" class="mobile-list">
            <div v-for="row in rows" :key="row.key" class="mobile-record">
              <div class="mobile-record-head">
                <button class="mobile-name" type="button" @click="openDetail(row)">{{ row.name }}</button>
                <span class="tag" :class="row.published ? 'green' : 'gray'">{{ row.published ? '已发布' : '未发布' }}</span>
              </div>
              <div class="mobile-record-meta">
                <span>{{ row.typeLabel }} · {{ row.format }}</span>
                <span>{{ row.size }}</span>
                <span>{{ fmtTimeCol(row.time) }}</span>
                <span class="status" :class="statusClass(row)"><i class="d"></i>{{ row.statusLabel }}</span>
              </div>
              <div v-if="svcInfo[row.key]?.formats?.length" class="mobile-service">服务：{{ svcInfo[row.key].formats.join(' / ') }}</div>
              <div class="mobile-actions">
                <el-button v-if="row.src === 'layer' && row.typeKey === 'vector'" link type="primary" @click="openDetail(row, 'data')">属性</el-button>
                <el-button v-if="row.src === 'layer'" link type="primary" @click="openPublish(row)">{{ publishLabel(row) }}</el-button>
                <el-button v-else-if="canPublish(row)" link type="primary" @click="openPublish(row)">{{ publishLabel(row) }}</el-button>
                <el-button v-if="row.published" link type="primary" @click="copySvcAddr(row)">复制地址</el-button>
                <el-button v-if="row.src === 'm3d' && row.published" link type="primary" @click="openPreview(row)">预览</el-button>
                <el-button v-if="row.src === 'layer'" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeRow(row)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="!loading && !rows.length" description="暂无数据" :image-size="64" />
          </div>
          <div v-if="batchVisible" class="batch-bar">
            <span>已选择 <b>{{ batchCount }}</b> 项</span>
            <el-button class="btn sm danger" @click="batchDel">批量删除</el-button>
          </div>
          <div class="pager">
            <span class="mobile-total">共 {{ total }} 条</span>
            <el-pagination
              v-model:current-page="pageIndex"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              small
              @current-change="() => loadList()"
              @size-change="pageIndex = 1; loadList()"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 上传数据（右侧抽屉，全量字段） -->
    <el-drawer v-model="upload.visible" title="上传数据" size="min(560px, 100vw)" :close-on-click-modal="false">
      <div class="dlg-body">
        <div class="grid2">
          <div class="field">
            <label><i class="req">*</i>数据名称</label>
            <el-input v-model="upload.name" placeholder="如：20230131test1111" />
          </div>
          <div class="field">
            <label><i class="req">*</i>数据类型</label>
            <el-select v-model="upload.typeKey" placeholder="请选择数据类型" style="width: 100%">
              <el-option v-for="t in DATA_TYPES" :key="t.key" :label="t.label" :value="t.key" />
            </el-select>
          </div>
        </div>

        <!-- 所属目录（原新建资源表单字段） -->
        <div v-if="isLayerType" class="field">
          <label>所属目录</label>
          <el-select v-model="upload.catalogId" placeholder="选择目录（layerType，选填）" clearable filterable style="width: 100%">
            <el-option v-for="c in catalogOptions" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </div>

        <!-- 空间参考：二维走字典下拉（layerRefence），三维为文本（srs） -->
        <div class="field">
          <label>空间参考系</label>
          <el-select
            v-if="isLayerType"
            v-model="upload.layerRefence"
            placeholder="坐标系（如 GCS_WGS_1984 (4326)）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in srsOptions"
              :key="d.id || d.code"
              :label="d.codeName || d.code"
              :value="d.codeName || d.code"
            />
          </el-select>
          <el-input v-else-if="isM3dType" v-model="upload.srs" placeholder="如 EPSG:4326 / GCS_WGS_1984" />
        </div>

        <div v-if="isLayerType" class="grid2">
          <div class="field">
            <label>行政区划</label>
            <el-select v-model="upload.area" placeholder="行政区划" clearable filterable style="width: 100%">
              <el-option
                v-for="a in areaOptions"
                :key="a.id ?? a.code"
                :label="a.areaName ?? a.name ?? a.code"
                :value="a.areaName ?? a.name ?? a.code"
              />
            </el-select>
          </div>
          <div class="field">
            <label>数据密级</label>
            <el-select v-model="upload.confidential" style="width: 100%">
              <el-option label="内部" value="内部" />
              <el-option label="公开" value="公开" />
              <el-option label="涉密" value="涉密" />
            </el-select>
          </div>
        </div>

        <div v-if="isLayerType" class="field">
          <label>数据标签</label>
          <el-input v-model="upload.keywords" placeholder="多个标签以逗号分隔" />
        </div>

        <div class="field">
          <label>
            <i class="req">*</i>数据来源
            <span v-if="upload.srcMode !== 'resume'" class="fmt-trigger" title="查看支持的数据格式" @click.stop="fmtPopVisible = !fmtPopVisible">!</span>
          </label>
          <div v-if="fmtPopVisible && upload.srcMode !== 'resume'" class="fmt-pop" @click.stop>
            <div class="fmt-pop-head">支持的数据格式</div>
            <div class="fmt-box">
              <div v-for="t in DATA_TYPES" :key="t.key" class="fmt-row">
                <span class="t">{{ t.label }}</span>
                <span class="f"><b v-for="f in t.formats" :key="f">{{ f }}</b></span>
              </div>
            </div>
            <div class="fmt-note">上传时平台按文件扩展名自动读取数据格式（如 .shp → SHP、.tif → GeoTIFF），并回填到列表。</div>
          </div>
          <div class="src-row">
            <el-radio-group v-model="upload.srcMode">
              <el-radio value="local">本地上传文件</el-radio>
              <el-radio value="dir" disabled title="后端暂未提供服务器目录导入端点">服务器目录上传</el-radio>
              <el-radio v-if="isM3dType" value="resume">三维断点续传</el-radio>
            </el-radio-group>
          </div>

          <!-- 三维断点续传（原三维数据页） -->
          <template v-if="upload.srcMode === 'resume'">
            <div class="field">
              <label>任务 id</label>
              <el-input v-model="upload.resumeId" placeholder="原上传 init 返回的数据 id" />
              <div class="hint">流程：GET /model3d/{id}/parts 查已传分片 → 跳过已有分片续传 → complete 完成。须与原任务为同一文件。</div>
            </div>
            <button class="btn sm" type="button" @click="uploadInputRef?.click()">
              <Icon name="file" :size="13" /> 选择文件（与原任务相同）
            </button>
            <div v-if="upload.file" class="file-chip">
              <Icon name="file" :size="14" />
              <span class="nm">{{ upload.file.name }}</span>
              <span>{{ (upload.file.size / 1024 / 1024).toFixed(1) }}MB</span>
              <span class="x" @click.stop="removeFile">移除</span>
            </div>
          </template>

          <!-- 本地上传 -->
          <template v-else>
            <div
              class="drop"
              :class="{ over: dragOver }"
              @click="uploadInputRef?.click()"
              @dragover.prevent="dragOver = true"
              @dragleave="dragOver = false"
              @drop.prevent="onDropFile"
            >
              <div class="big"><b>点击选择</b> 或 将本地文件拖拽到此处</div>
              <div class="small">
                矢量：SHP 系列（可一次多选 .shp/.shx/.dbf/.prj/.cpg）；栅格：GeoTIFF；三维：OSGB / LAS / LAZ / OBJ / FBX / IFC / 3DTiles（支持 zip 包）；单文件建议不超过 50GB
              </div>
            </div>
            <input
              ref="uploadInputRef"
              type="file"
              class="hidden"
              :accept="uploadAccept"
              :multiple="canMultiPick"
              @change="onPickFile"
            />

            <div v-if="upload.file" class="file-chip">
              <Icon name="file" :size="14" />
              <span class="nm">{{ upload.file.name }}</span>
              <span>{{ (upload.file.size / 1024 / 1024).toFixed(1) }}MB</span>
              <span v-if="upload.familyFiles.length">+{{ upload.familyFiles.length }} 个配套文件</span>
              <span v-if="isM3dType">计划 {{ Math.max(1, Math.ceil(upload.file.size / (5 * 1024 * 1024))) }} 片</span>
              <span class="x" @click.stop="removeFile">移除</span>
            </div>
          </template>

          <div v-if="upload.submitting || upload.progress > 0" class="bar"><i :style="{ width: upload.progress + '%' }"></i></div>
          <div v-if="upload.submitting && upload.progressText" class="prog-txt">
            <span>{{ upload.progressText }}</span><span>{{ upload.progress }}%</span>
          </div>

          <!-- 同步建图（原数据接入，栅格专属） -->
          <el-checkbox v-if="isRasterType && upload.srcMode === 'local'" v-model="upload.autoMap" class="automap">
            入库同时创建地图（InsertImageLayer）
          </el-checkbox>

          <div v-if="upload.srcMode !== 'resume'" class="detect" :class="detectClass">{{ detectText }}</div>
          <div v-else class="detect">续传按 5MiB 分片进行，已传分片自动跳过；完成后状态为「待转换」。</div>
        </div>

        <div class="field" style="margin-bottom: 0">
          <label>{{ isLayerType ? '摘要 / 备注' : '备注' }}</label>
          <el-input
            v-model="upload.layerDesc"
            type="textarea"
            :rows="3"
            placeholder="选填，如数据范围、坐标系、密级等说明"
          />
        </div>
      </div>
      <template #footer>
        <el-button class="btn" @click="upload.visible = false">取消</el-button>
        <el-button class="btn primary" :loading="upload.submitting" @click="submitUpload">
          {{ upload.submitting ? `上传中 ${upload.progress}%` : upload.srcMode === 'resume' ? '开始续传' : '上传' }}
        </el-button>
      </template>
    </el-drawer>

    <!-- 编辑数据 -->
    <el-dialog v-model="edit.visible" title="编辑数据" width="560px" :close-on-click-modal="false">
      <div class="field">
        <label><i class="req">*</i>数据名称</label>
        <el-input v-model="edit.name" />
      </div>
      <div class="grid2">
        <div class="field"><label>数据类型</label><el-input disabled model-value="系统记录，不可修改" /></div>
        <div class="field"><label>数据格式</label><el-input disabled model-value="系统记录，不可修改" /></div>
      </div>
      <div class="field" style="margin-bottom: 0">
        <label>摘要 / 备注</label>
        <el-input v-model="edit.desc" type="textarea" :rows="3" placeholder="选填，如数据范围、坐标系、密级等说明" />
        <div class="hint">仅支持修改数据名称与摘要；其余属性由系统记录，不可修改。</div>
      </div>
      <template #footer>
        <el-button class="btn" @click="edit.visible = false">取消</el-button>
        <el-button class="btn primary" :loading="edit.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 三维数据详情（状态机全量字段） -->
    <el-dialog v-model="detail.visible" :title="'三维数据详情 · ' + (detail.row?.name || '')" width="620px">
      <div class="info-rows">
        <div v-for="[k, v] in detailRows" :key="k" class="info-row">
          <span class="k">{{ k }}</span><span class="v">{{ v || '—' }}</span>
        </div>
      </div>
      <template #footer>
        <el-button class="btn" @click="detail.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发布数据服务（二维） -->
    <el-dialog
      v-model="publish.visible"
      :title="publish.row?.published ? '重新发布数据服务' : '发布数据服务'"
      width="560px"
      :close-on-click-modal="false"
    >
      <div class="grid2">
        <div class="field">
          <label><i class="req">*</i>服务名称</label>
          <el-input v-model="publish.name" placeholder="如：DOM 正射影像服务" />
        </div>
        <div class="field">
          <label>数据类型</label>
          <el-input :model-value="publish.row?.typeLabel" disabled />
        </div>
      </div>
      <div class="field">
        <label><i class="req">*</i>服务格式（可多选）</label>
        <el-checkbox-group v-model="publish.formats">
          <el-checkbox v-for="f in publishAvail" :key="f" :value="f">{{ f }}</el-checkbox>
        </el-checkbox-group>
        <div class="hint">矢量可选 WMS / WFS，栅格可选 WMS / WMTS；发布由 GeoServer 完成，勾选 WMTS 会触发切片任务。</div>
      </div>
      <template #footer>
        <el-button class="btn" @click="publish.visible = false">取消</el-button>
        <el-button class="btn primary" :loading="publish.submitting" @click="submitPublish">确定</el-button>
      </template>
    </el-dialog>

    <!-- 数据详情（二维六页签抽屉，复用原资源总览组件） -->
    <ResourceDetail v-model="drawerVisible" :layer="drawerLayer" :initial-tab="drawerTab" @changed="loadList" />

    <!-- 全屏预览 -->
    <ServicePreview v-model="previewVisible" :row="previewRow" />
  </div>
</template>

<style scoped>
.manage-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.manage-body {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}
.catalog-trigger,
.mobile-list,
.mobile-total { display: none; }

/* ---------- 左：目录树 ---------- */
.tree-panel {
  width: 252px;
  flex: 0 0 252px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.tree-tools {
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
}
.tree-all {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  margin: 8px 8px 0;
  padding: 0 9px;
  border-radius: 6px;
  font-size: 12.5px;
  color: var(--ink-2);
  cursor: pointer;
  user-select: none;
}
.tree-all:hover {
  background: var(--panel-2);
}
.tree-all.on {
  background: var(--primary-050);
  color: var(--primary);
  font-weight: 600;
}
.tree-wrap {
  flex: 1;
  overflow: auto;
  padding: 4px 6px 12px;
  min-height: 0;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  width: 100%;
  min-width: 0;
}
.tree-node.islayer .tn-name {
  color: var(--ink-2);
}
.tn-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tn-cnt {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--ink-3);
  background: var(--panel-2);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 0 5px;
}

/* ---------- 右：筛选 + 表格 ---------- */
.list-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.filter-card {
  padding: 12px 14px 2px;
}
.table-card {
  margin-top: 12px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 4px 6px 0;
}
.table-card .pager {
  padding: 10px 8px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 900px) {
  .catalog-trigger { display: inline-flex; }
  .manage-body {
    overflow-y: auto;
    flex-direction: column;
  }
  .tree-panel {
    display: none;
    width: 100%;
    flex: none;
    max-height: 260px;
  }
  .tree-panel.mobile-open { display: flex; }
  .list-col { flex: none; }
  .table-card { min-height: 420px; }
}
@media (max-width: 700px) {
  .manage-page { height: auto; min-height: 100%; }
  .manage-body { overflow: visible; }
  .filter-card { padding: 12px 12px 2px; }
  .filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filters :deep(.el-select),
  .filters :deep(.el-input) { width: 100% !important; min-width: 0; }
  .table-card { min-height: 0; padding: 0; overflow: hidden; }
  .table-card > :deep(.el-table) { display: none; }
  .mobile-list { display: block; padding: 9px; min-height: 180px; }
  .mobile-record {
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 7px;
    background: #fff;
  }
  .mobile-record + .mobile-record { margin-top: 9px; }
  .mobile-record-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
  .mobile-name { padding: 0; border: 0; background: none; color: var(--ink); font: inherit; font-size: 13px; font-weight: 650; text-align: left; cursor: pointer; }
  .mobile-name:hover { color: var(--primary); }
  .mobile-record-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 5px 12px; margin-top: 8px; color: var(--ink-3); font-size: 11.5px; }
  .mobile-service { margin-top: 7px; color: var(--ink-2); font-size: 11.5px; }
  .mobile-actions { display: flex; flex-wrap: wrap; gap: 3px 12px; margin-top: 9px; padding-top: 8px; border-top: 1px solid var(--line); }
  .mobile-actions :deep(.el-button) { margin: 0; padding: 0; font-size: 12px; }
  .table-card .pager { justify-content: space-between; align-items: center; padding: 6px 12px 10px; border-top: 1px solid var(--line); }
  .mobile-total { display: inline; color: var(--ink-3); font-size: 12px; white-space: nowrap; }
  .table-card .pager :deep(.el-pagination__sizes),
  .table-card .pager :deep(.el-pagination__total) { display: none; }
  .batch-bar { display: none; }
  .grid2 { grid-template-columns: 1fr; }
}
.row-link {
  color: var(--ink);
  font-weight: 500;
  cursor: pointer;
}
.row-link:hover {
  color: var(--primary);
}
.fmt {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  color: var(--primary);
  background: var(--primary-050);
  border: 1px solid var(--primary-100);
  border-radius: 4px;
  padding: 0 6px;
  line-height: 18px;
  display: inline-block;
}
.th-sort {
  cursor: pointer;
  user-select: none;
  display: inline-flex;
  align-items: center;
}
.arrows {
  display: inline-flex;
  flex-direction: column;
  margin-left: 5px;
  line-height: 0;
}
.arrows i {
  display: block;
  width: 0;
  height: 0;
  border-left: 3.5px solid transparent;
  border-right: 3.5px solid transparent;
}
.arrows i:first-child {
  border-bottom: 4px solid #c3cad6;
  margin-bottom: 2px;
}
.arrows i:last-child {
  border-top: 4px solid #c3cad6;
}
.arrows.asc i:first-child {
  border-bottom-color: var(--primary);
}
.arrows.desc i:last-child {
  border-top-color: var(--primary);
}
/* 状态（呼吸灯 + 进度条） */
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  font-size: 12.5px;
}
.status .d {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: 0 0 7px;
}
.status.s-ok {
  color: var(--ok);
}
.status.s-ok .d {
  background: var(--ok);
}
.status.s-err {
  color: var(--danger);
}
.status.s-err .d {
  background: var(--danger);
}
.status.s-run {
  color: var(--warn);
}
.status.s-run .d {
  background: var(--warn);
  animation: blink 1s infinite;
}
@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}
.pbar {
  margin-top: 5px;
  height: 5px;
  border-radius: 3px;
  background: #eef1f6;
  overflow: hidden;
  width: 118px;
}
.pbar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #e08b1b, #f5b544);
  transition: width 0.3s;
}
.op {
  padding: 0 !important;
  height: auto !important;
}
.op-sep {
  color: var(--line-2);
  margin: 0 5px;
}
.op.del {
  color: var(--danger);
}
.op-dis {
  color: #bcc3ce;
  font-size: 12px;
  cursor: not-allowed;
}

.svc-fmts {
  font-size: 12px;
  color: var(--ink-2);
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  font-size: 12.5px;
  color: var(--ink-2);
  border-top: 1px solid var(--line);
}
.batch-bar b {
  color: var(--primary);
}

/* ---------- 抽屉表单 ---------- */
.dlg-body {
  padding: 4px 2px;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}
.field {
  margin-bottom: 15px;
  position: relative;
}
.field label {
  display: block;
  font-size: 13px;
  color: var(--ink-2);
  margin-bottom: 6px;
}
.field .req {
  color: var(--danger);
  font-style: normal;
  margin-right: 3px;
}
.hint {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 6px;
  line-height: 1.6;
}
.src-row {
  margin-bottom: 12px;
}
.drop {
  border: 1.5px dashed var(--line-2);
  border-radius: 8px;
  background: #fbfcfe;
  padding: 22px 16px;
  text-align: center;
  cursor: pointer;
  transition: 0.15s;
}
.drop:hover,
.drop.over {
  border-color: var(--primary);
  background: #f5f9ff;
}
.drop .big {
  font-size: 13.5px;
  color: var(--ink-2);
}
.drop .big b {
  color: var(--primary);
  font-weight: 500;
}
.drop .small {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 5px;
}
.file-chip {
  display: flex;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--line);
  background: #f8fafd;
  border-radius: 6px;
  padding: 9px 11px;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--ink-2);
}
.file-chip .nm {
  color: var(--ink);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-chip .x {
  color: var(--ink-3);
  cursor: pointer;
}
.file-chip .x:hover {
  color: var(--danger);
}
.bar {
  height: 6px;
  border-radius: 4px;
  background: #eaeef5;
  overflow: hidden;
  margin-top: 9px;
}
.bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2b7cff, #57b8ff);
  transition: width 0.3s;
}
.prog-txt {
  display: flex;
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 6px;
}
.prog-txt span:last-child {
  margin-left: auto;
}
.automap {
  margin-top: 10px;
}
.detect {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  background: #f6f7fa;
  color: var(--ink-2);
  border: 1px dashed var(--line-2);
}
.detect.ok {
  background: #eefaf3;
  border-color: #c2e9d5;
  color: #1d8a55;
}
.detect.warn {
  background: #fff8ea;
  border-color: #f3e0b0;
  color: #a9710d;
}
.fmt-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  border: 1px solid var(--line-2);
  background: #fff;
  color: var(--ink-3);
  font-size: 10px;
  font-weight: 700;
  margin-left: 6px;
  cursor: pointer;
  user-select: none;
  vertical-align: 1px;
}
.fmt-trigger:hover {
  color: var(--primary);
  border-color: var(--primary);
}
.fmt-pop {
  position: absolute;
  top: 26px;
  left: 0;
  z-index: 40;
  width: 430px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 11px 12px 12px;
  box-shadow: var(--sh-2);
}
.fmt-pop-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 8px;
}
.fmt-pop-head::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--primary);
}
.fmt-box {
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fbfcfe;
  overflow: hidden;
}
.fmt-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 11px;
  font-size: 12.5px;
  border-bottom: 1px solid var(--line);
}
.fmt-row:last-child {
  border-bottom: none;
}
.fmt-row .t {
  width: 128px;
  flex: none;
  color: var(--ink-2);
}
.fmt-row .f {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.fmt-row .f b {
  font-weight: 500;
  font-size: 11.5px;
  line-height: 17px;
  padding: 0 7px;
  border-radius: 4px;
  color: var(--primary);
  background: var(--primary-050);
  border: 1px solid var(--primary-100);
}
.fmt-note {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 6px;
  line-height: 1.6;
}
.hidden {
  display: none !important;
}
/* 详情信息行 */
.info-rows .info-row {
  display: flex;
  font-size: 12.5px;
  padding: 7px 0;
  border-bottom: 1px dashed #eef1f6;
}
.info-rows .info-row .k {
  color: var(--ink-3);
  flex: 0 0 92px;
}
.info-rows .info-row .v {
  color: var(--ink-2);
  word-break: break-all;
}
</style>
