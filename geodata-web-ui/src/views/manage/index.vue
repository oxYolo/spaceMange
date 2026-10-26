<script setup lang="ts">
/** Dataset workspace: filtering and row actions; upload/query/state rules live in features/datasets. */
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
const ResourceDetail = defineAsyncComponent(() => import('@/components/resource/ResourceDetail.vue'))
import {
  DATA_TYPES,
  DATASET_FILTER_TYPES,
  SERVICE_DEFAULTS,
  SERVICE_FORMATS,
  loadUnifiedPage,
  m3dStatus,
  applyIngestionDetail,
  needsIngestionVerification,
  fetchSvcAddress,
  fetchAllSvcAddresses,
  getDatasetServiceState,
  getDatasetCapabilities,
  invalidateDatasets,
  invalidateDatasetQueries,
  type UnifiedRow,
} from '@/utils/datasets'
import {
  dataTypeApi,
  dictApi,
  layerContentApi,
  model3dApi,
  serverInterfaceApi,
} from '@/api'
import { fmtTime, pickList } from '@/utils/format'
import { decompressJson } from '@/utils/gzip'
import { onImMessage, onImBizMessage, imEnabled, ensureImAlive, type ImMessage } from '@/utils/im'
import { copyText } from '@/utils/clipboard'
import { useSearchStore } from '@/stores/search'
import { mapSettledWithConcurrency } from '@/utils/async'
import { showError } from '@/utils/feedback'
import DatasetQuickFilters from '@/features/datasets/components/DatasetQuickFilters.vue'
import { downloadDatasetCsv } from '@/features/datasets/export'
import { getSessionVersion } from '@/auth/session'
import DatasetRowActions from '@/features/datasets/components/DatasetRowActions.vue'
import DatasetTypeMark from '@/features/datasets/components/DatasetTypeMark.vue'
import DatasetEmptyState from '@/features/datasets/components/DatasetEmptyState.vue'
import { DATASET_LIST_QUERY_KEYS, datasetListQuery, datasetListSignature, readDatasetListState, type DatasetListState } from '@/features/datasets/listState'
import type { DatasetSort } from '@/features/datasets/query'
import DatasetUploadDrawer from '@/features/datasets/components/DatasetUploadDrawer.vue'
import { useUserStore } from '@/auth/store'
const ServicePreview = defineAsyncComponent(() => import('@/components/preview/ServicePreview.vue'))

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const globalSearch = useSearchStore()
const listError = ref('')
const batchDeleting = ref(false)
const rowBusy = reactive<Record<string, boolean>>({})
let disposed = false
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
  treeChildrenCache.clear()
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
    if (!e?.silent) showError(e, '目录树加载失败')
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
  if (node?.layerContent) {
    const id = String(node.layerContent.id ?? node.id ?? '')
    if (id) { drawerLayer.value = { ...node.layerContent, id }; drawerVisible.value = true }
    return
  }
  selectedCatalog.value = String(node?.id ?? '')
  applied.catalog = selectedCatalog.value
  mobileTreeOpen.value = false
  pageIndex.value = 1
  loadList()
}
function clearTreeFilter() {
  selectedCatalog.value = ''
  applied.catalog = ''
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
const initialListState = readDatasetListState(route.query)
const filters = reactive({ type: initialListState.type, status: initialListState.status, pub: initialListState.pub, kw: initialListState.kw })
const applied = reactive({ ...filters, catalog: initialListState.catalog })
selectedCatalog.value = initialListState.catalog
const sort = ref<DatasetSort>(initialListState.sort)
const order = ref<'desc' | 'asc'>(initialListState.order)
const rows = ref<UnifiedRow[]>([])
const total = ref(0)
const pageSize = ref(initialListState.size)
const pageIndex = ref(initialListState.page)
const viewMode = ref<'table' | 'cards'>('table')
try { if (localStorage.getItem('GeoDataListView') === 'cards') viewMode.value = 'cards' } catch { /* Optional preference. */ }
function setViewMode(value: 'table' | 'cards') {
  viewMode.value = value
  try { localStorage.setItem('GeoDataListView', value) } catch { /* Keep current preference in memory. */ }
}
const lastUpdated = ref('')
function currentListState(): DatasetListState {
  return { ...applied, sort: sort.value, order: order.value, page: pageIndex.value, size: pageSize.value }
}
function applyListView(state: DatasetListState) {
  if (loading.value || batchDeleting.value || disposed) return
  Object.assign(filters, { type: state.type, status: state.status, pub: state.pub, kw: state.kw })
  Object.assign(applied, filters, { catalog: state.catalog })
  selectedCatalog.value = state.catalog
  treeRef.value?.setCurrentKey?.(state.catalog || null)
  sort.value = state.sort; order.value = state.order; pageSize.value = state.size; pageIndex.value = 1
  void loadList()
}
function persistListQuery() {
  const current = currentListState()
  if (datasetListSignature(readDatasetListState(route.query)) === datasetListSignature(current)) return
  const query = { ...route.query }
  DATASET_LIST_QUERY_KEYS.forEach(key => { delete query[key] })
  void router.replace({ query: { ...query, ...datasetListQuery(current) } })
}
const hasFilters = computed(() => Boolean(applied.type || applied.status || applied.pub || applied.kw || applied.catalog))
const activeFilterTags = computed(() => [
  applied.type && { key: 'type', label: DATASET_FILTER_TYPES.find(type => type.key === applied.type)?.label || applied.type },
  applied.status && { key: 'status', label: STATUS_OPTS.find(status => status.value === applied.status)?.label || applied.status },
  applied.pub && { key: 'pub', label: applied.pub === 'yes' ? '已发布' : '未发布' },
  applied.kw && { key: 'kw', label: `名称：${applied.kw}` },
  applied.catalog && { key: 'catalog', label: '已选目录' },
].filter((item): item is { key: string; label: string } => Boolean(item)))
function removeFilter(key: string) {
  if (key === 'catalog') {
    selectedCatalog.value = applied.catalog = ''
    treeRef.value?.setCurrentKey?.(null)
  } else if (key in filters) {
    filters[key as keyof typeof filters] = ''
    applied[key as keyof typeof filters] = ''
  } else return
  pageIndex.value = 1
  void loadList()
}
function refreshList() { invalidateDatasetQueries(); return loadList() }
function changeSort(value: string) {
  const [field, direction] = value.split(':')
  sort.value = field as DatasetSort
  order.value = direction as 'asc' | 'desc'
  pageIndex.value = 1
  void loadList()
}
const loading = ref(false)
let listRequestId = 0
let lastRequestedSignature = ''
function onPaginationChange() {
  if (datasetListSignature(currentListState()) !== lastRequestedSignature) void loadList()
}

const STATUS_OPTS = [
  { value: 'uploading', label: '上传 / 入库中' },
  { value: 'converting', label: '转换中' },
  { value: 'ok', label: '上传完成' },
  { value: 'fail', label: '处理失败' },
]

/** 发布状态筛选（原服务管理页并入；「未发布」为前端过滤） */
const PUB_OPTS = [
  { value: 'yes', label: '已发布' },
  { value: 'no', label: '未发布' },
]

async function loadList(silent = false) {
  if (silent && (batchDeleting.value || Object.values(rowBusy).some(Boolean))) { schedulePoll(); return }
  const requestId = ++listRequestId
  lastRequestedSignature = datasetListSignature(currentListState())
  if (!silent) persistListQuery()
  if (!silent) { loading.value = true; listError.value = '' }
  try {
    const res = await loadUnifiedPage(
      {
        typeKey: applied.type,
        status: applied.status,
        keyword: applied.kw,
        layerType: applied.catalog,
        publishedOnly: applied.pub === 'yes' ? true : applied.pub === 'no' ? false : undefined,
        order: order.value,
        sort: sort.value,
      },
      pageSize.value,
      pageIndex.value,
    )
    if (disposed || requestId !== listRequestId) return
    if (silent && (batchDeleting.value || Object.values(rowBusy).some(Boolean))) return
    if (!res.rows.length && pageIndex.value > Math.max(1, Math.ceil(res.total / pageSize.value))) {
      pageIndex.value = Math.max(1, Math.ceil(res.total / pageSize.value))
      await loadList(silent)
      return
    }
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    const selectedKeys = silent ? new Set(picked.value) : new Set<string>()
    const previousRows = new Map(rows.value.map(row => [row.key, row]))
    clearSelection()
    rows.value = res.rows.map(row => {
      if (!needsIngestionVerification(row)) return row
      // 列表 DTO 缺少 isShow，已被详情确认的同一资源不可在每次轮询时重新降为待确认。
      const previouslyReady = silent && row.status === 'ok' && previousRows.get(row.key)?.ingestReady === true
      return applyIngestionDetail(row, previouslyReady ? { id: row.id, isShow: 1 } : {})
    })
    total.value = res.total
    if (selectedKeys.size) {
      await nextTick()
      if (disposed || requestId !== listRequestId) return
      for (const row of rows.value) if (selectedKeys.has(row.key) && isRemovableRow(row)) togglePicked(row, true)
    }
    void probeRows(requestId)
  } catch (e: any) {
    if (requestId === listRequestId && !silent) {
      listError.value = e?.message || '列表加载失败'
      showError(e, '列表加载失败')
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
  sort.value = 'time'
  order.value = order.value === 'desc' ? 'asc' : 'desc'
  pageIndex.value = 1
  void loadList()
}

/** 三维转换/上传进行中（或二维上传中）时 5s 静默轮询刷新进度 */
let pollTimer: number | null = null
/* ================= 服务探测（原服务管理页并入） ================= */
/** 每行服务探测结果：key → { formats, url, tile } */
const svcInfo = reactive<Record<string, { formats: string[]; url: string; tile: string; error?: string }>>({})

/** 逐行探测已发布服务的格式与切片状态（仅当前页，并行；未发布行跳过） */
async function probeRows(requestId = listRequestId) {
  const currentRows = [...rows.value]
  await mapSettledWithConcurrency(currentRows, 4, async row => {
    if (disposed || requestId !== listRequestId) return
    if (needsIngestionVerification(row)) {
      try {
        const verified = await verifyIngestion(row)
        if (disposed || requestId !== listRequestId) return
        Object.assign(row, verified)
      } catch { /* Keep pending, retry on the next poll. Never assume import succeeded. */ }
    }
    const info = await getDatasetServiceState(row)
    if (disposed || requestId !== listRequestId) return
    svcInfo[row.key] = info
  })
  const activeKeys = new Set(currentRows.map(row => row.key))
  if (requestId === listRequestId) Object.keys(svcInfo).forEach(key => { if (!activeKeys.has(key)) delete svcInfo[key] })
}

const hasRunning = computed(() =>
  rows.value.some(
    (r) =>
      r.ingestPending || r.status === 'uploading' ||
      r.status === 'converting' ||
      (r.src === 'm3d' && ['0', '2', '3'].includes(String(r.raw?.status ?? ''))) ||
      svcInfo[r.key]?.tile === '切片中',
  ),
)
const anyDialogOpen = computed(
  () => uploadDrawer.value?.visible || edit.visible || detail.visible || publish.visible || previewVisible.value || drawerVisible.value,
)
function schedulePoll() {
  if (disposed) return
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(
    () => {
      // IM 断线自愈：automaticReconnect 序列耗尽后不会自动恢复，轮询 tick 里幂等重连（连接正常时零成本）
      ensureImAlive()
      if (!disposed && !document.hidden && hasRunning.value && !anyDialogOpen.value) { invalidateDatasetQueries(); void loadList(true) }
      else schedulePoll()
    },
    imEnabled() ? 15000 : 5000,
  )
}

const uploadDrawer = ref<InstanceType<typeof DatasetUploadDrawer>>()
function openUpload(typeKey?: unknown) {
  uploadDrawer.value?.open(typeof typeKey === 'string' ? typeKey : '', selectedCatalog.value)
}
async function onUploaded() {
  invalidateDatasets()
  pageIndex.value = 1
  await Promise.all([loadList(), loadTree()])
  notifyResourceCountChanged()
}

/* ================= 编辑（仅名称与摘要，仅二维） ================= */
const edit = reactive({ visible: false, saving: false, id: '', name: '', desc: '' })

function openEdit(row: UnifiedRow) {
  if (row.src === 'm3d' || rowBusy[row.key] || batchDeleting.value) return
  edit.visible = true
  edit.id = row.id
  edit.name = row.name
  edit.desc = String(row.raw?.layerDesc ?? '')
}
async function saveEdit() {
  if (edit.saving) return
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
    invalidateDatasets()
    await loadList()
  } catch (e: any) {
    showError(e, '保存失败')
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

const ingestionReads = new Map<string, Promise<UnifiedRow>>()
function verifyIngestion(row: UnifiedRow): Promise<UnifiedRow> {
  if (!needsIngestionVerification(row)) return Promise.resolve(row)
  const pending = ingestionReads.get(row.key)
  if (pending) return pending
  const read = layerContentApi.getDetailById(row.id).then(detail => applyIngestionDetail(row, detail))
  ingestionReads.set(row.key, read)
  void read.finally(() => { if (ingestionReads.get(row.key) === read) ingestionReads.delete(row.key) }).catch(() => {})
  return read
}
async function confirmIngestion(row: UnifiedRow): Promise<boolean> {
  try {
    const verified = await verifyIngestion(row)
    if (disposed) return false
    Object.assign(row, verified)
    if (row.ingestPending) {
      ElMessage.warning('尚未确认矢量入库完成，请在详情的活动日志查看结果，稍后刷新')
      return false
    }
    return true
  } catch (error) { showError(error, '无法确认入库结果，请刷新后重试'); return false }
}

/* ================= 发布（二维 OGC / 三维下发转换） ================= */
const publish = reactive({
  visible: false,
  submitting: false,
  row: null as UnifiedRow | null,
  name: '',
  formats: [] as string[],
})

const publishAvail = computed(() => (publish.row ? SERVICE_FORMATS[publish.row.typeKey] || [] : []))

async function openPublish(row: UnifiedRow) {
  if (rowBusy[row.key] || batchDeleting.value) return
  if (needsIngestionVerification(row)) {
    rowBusy[row.key] = true
    try { if (!await confirmIngestion(row)) return } finally { rowBusy[row.key] = false }
  }
  const capability = getDatasetCapabilities(row)
  if (!capability.canPublish && !capability.canConvert) return ElMessage.warning('请等待数据处理完成后再发布')
  if (row.src === 'm3d') {
    if (rowBusy[row.key]) return
    rowBusy[row.key] = true
    try {
      await ElMessageBox.confirm(`转换「${row.name}」并发布为 3DTiles 服务？`, '转换并发布', {
        type: 'info', confirmButtonText: '开始转换', cancelButtonText: '取消',
      })
      if (disposed) return
      await model3dApi.convert(row.id)
      ElMessage.success('已提交转换任务，可在列表跟踪进度')
      invalidateDatasets()
      await loadList()
    } catch (error) { showError(error, '转换任务提交失败') }
    finally { rowBusy[row.key] = false }
    return
  }
  publish.row = row
  publish.name = row.name
  publish.formats = [...(SERVICE_DEFAULTS[row.typeKey] || [])]
  publish.visible = true
}

async function submitPublish() {
  if (publish.submitting) return
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
    invalidateDatasets()
    await loadList()
  } catch (e: any) {
    showError(e, '发布失败（请确认 GeoServer 服务可用）')
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
  if (rowBusy[row.key] || batchDeleting.value || listError.value) return
  rowBusy[row.key] = true
  try {
    if (needsIngestionVerification(row) && !await confirmIngestion(row)) return
    if (!getDatasetCapabilities(row).canDelete || svcInfo[row.key]?.tile === '切片中') return ElMessage.warning('数据正在处理，请处理结束后再删除')
    await ElMessageBox.confirm(`确认删除「${row.name}」？数据及其发布信息将被清理。`, '删除数据', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
    if (disposed) return
    const removed = row.src === 'layer' ? await layerContentApi.removeLayer(row.id) : await model3dApi.remove(row.id)
    if (removed === false) throw new Error('该数据未被删除，请刷新后重试')
    invalidateDatasets()
    if (rows.value.length === 1 && pageIndex.value > 1) pageIndex.value--
    ElMessage.success('已删除')
    await Promise.all([loadList(), loadTree()])
    notifyResourceCountChanged()
  } catch (error) { showError(error, '删除失败') }
  finally { rowBusy[row.key] = false }
}

/* ================= 批量删除（原服务管理页并入） ================= */
const dataTable = ref<{ clearSelection: () => void; toggleRowSelection: (row: UnifiedRow, selected: boolean) => void }>()
function clearSelection() { dataTable.value?.clearSelection(); picked.value = new Set() }
const picked = ref<Set<string>>(new Set())
const batchVisible = computed(() => picked.value.size > 0)
const batchCount = computed(() => picked.value.size)
function isRemovableRow(row: UnifiedRow) {
  return !rowBusy[row.key] && getDatasetCapabilities(row).canDelete && svcInfo[row.key]?.tile !== '切片中'
}
function togglePicked(row: UnifiedRow, checked: boolean) {
  if (checked && (batchDeleting.value || listError.value || !isRemovableRow(row))) return
  const next = new Set(picked.value)
  if (checked) next.add(row.key)
  else next.delete(row.key)
  picked.value = next
  dataTable.value?.toggleRowSelection(row, checked)
}
function onSelectionChange(selection: UnifiedRow[]) {
  picked.value = new Set(selection.filter(isRemovableRow).map((r) => r.key))
}
async function batchDel() {
  if (batchDeleting.value || listError.value) return
  const removable = (row: UnifiedRow) => picked.value.has(row.key) && isRemovableRow(row)
  let targets = rows.value.filter(removable)
  if (!targets.length) return
  batchDeleting.value = true
  try {
    await ElMessageBox.confirm(`确认删除已选中的 ${targets.length} 条数据？数据及其发布信息将被清理。`, '批量删除', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
    if (disposed) return
    targets = targets.filter(removable)
    if (!targets.length) return
    let ok = 0
    for (const row of targets) {
      if (disposed) return
      rowBusy[row.key] = true
      try {
        const removed = row.src === 'layer' ? await layerContentApi.removeLayer(row.id) : await model3dApi.remove(row.id)
        if (removed !== false) ok++
      } catch { /* Report a partial result after completing the selection. */ }
      finally { rowBusy[row.key] = false }
    }
    if (ok === targets.length) ElMessage.success(`已删除 ${ok} 条数据`)
    else ElMessage.warning(`已删除 ${ok} 条，${targets.length - ok} 条失败，请刷新后重试`)
    invalidateDatasets()
    picked.value = new Set()
    pageIndex.value = Math.min(pageIndex.value, Math.max(1, Math.ceil((total.value - ok) / pageSize.value)))
    await Promise.all([loadList(), loadTree()])
    notifyResourceCountChanged()
  } catch (error) { showError(error, '批量删除失败') }
  finally { batchDeleting.value = false }
}

const copyingServices = ref(false)
function exportResources(scope: 'page' | 'selected') {
  if (disposed || loading.value || listError.value || batchDeleting.value) return ElMessage.warning('请等待列表加载成功后再导出')
  if (scope !== 'page' && scope !== 'selected') return
  const snapshot = rows.value.filter(row => scope === 'page' || picked.value.has(row.key))
  try { downloadDatasetCsv(snapshot, scope); ElMessage.success(`已导出${scope === 'page' ? '当前页' : '已选'} ${snapshot.length} 条资源清单`) }
  catch (error) { showError(error, '导出失败') }
}
async function copyPublishedServices() {
  if (copyingServices.value || disposed) return
  if (loading.value || listError.value || batchDeleting.value) return ElMessage.warning('请等待列表加载成功后再复制')
  const selected = picked.value.size > 0
  const targets = rows.value.filter(row => row.published && (!selected || picked.value.has(row.key)))
    .map(row => ({ ...row, raw: { ...row.raw } }))
  if (!targets.length) return ElMessage.warning('当前范围没有已发布资源')
  copyingServices.value = true
  const session = getSessionVersion()
  try {
    const result = await mapSettledWithConcurrency(targets, 4, async row => {
      const addresses = await fetchAllSvcAddresses(row, { force: true })
      return { row, addresses }
    })
    if (disposed || session !== getSessionVersion()) return
    const lines: string[] = []
    let failed = 0
    for (const item of result) {
      if (item.status !== 'fulfilled' || !item.value.addresses.length) { failed++; continue }
      const name = item.value.row.name.replace(/[\t\r\n]+/g, ' ')
      for (const address of item.value.addresses) lines.push(`${name}\t${address.type}\t${address.url}`)
    }
    if (!lines.length) return ElMessage.warning('暂未读取到服务地址，请稍后重试')
    if (!await copyText(lines.join('\n'))) throw new Error('复制失败，请在服务预览中手动复制')
    if (disposed || session !== getSessionVersion()) return
    const message = `已复制${selected ? '已选范围' : '当前页'} ${lines.length} 个可用服务地址${failed ? `，${failed} 条资源未读取到地址` : ''}`
    failed ? ElMessage.warning(message) : ElMessage.success(message)
  } catch (error) { if (!disposed && session === getSessionVersion()) showError(error, '服务地址读取失败') }
  finally { copyingServices.value = false }
}

/* ================= 预览（三维预览 / 二维已发布预览） ================= */
const previewVisible = ref(false)
const previewRow = ref<UnifiedRow | null>(null)
function openPreview(row: UnifiedRow) {
  if (!row.published && row.src === 'layer') return ElMessage.warning('该数据尚未发布为服务，请先发布')
  if (row.src === 'm3d' && String(row.raw?.status ?? '') !== '4')
    return ElMessage.warning('三维数据转换发布完成后才可预览')
  previewRow.value = row
  previewVisible.value = true
}

/* ================= 行操作辅助 ================= */
function canPublish(row: UnifiedRow): boolean {
  const capability = getDatasetCapabilities(row)
  return capability.canPublish || capability.canConvert
}
function publishLabel(row: UnifiedRow): string {
  if (row.src === 'layer') return row.published ? '重新发布' : '发布'
  const st = String(row.raw?.status ?? '')
  if (st === '1') return '转换并发布'
  if (st === '5') return '重试转换'
  if (st === '4') return '复制地址'
  return row.statusLabel
}
function statusClass(r: UnifiedRow): string {
  if (r.ingestPending) return 's-run'
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
    if (!anyDialogOpen.value) { invalidateDatasetQueries(); void loadList(true) }
    if (msg.isFinish) loadTree()
  }, 1200)
}

function consumeUploadQuery() {
  if (typeof route.query.upload !== 'string') return
  const type = DATA_TYPES.find(item => item.key === route.query.upload)?.key
  if (!type) return
  openUpload(type)
  const { upload: _upload, ...query } = route.query
  void router.replace({ query })
}
watch(() => route.query.upload, consumeUploadQuery)
watch(() => globalSearch.sequence, () => {
  filters.type = filters.status = filters.pub = ''
  selectedCatalog.value = ''
  treeRef.value?.setCurrentKey?.(null)
  filters.kw = globalSearch.submittedKeyword
  doSearch()
})
watch(() => datasetListSignature(readDatasetListState(route.query)), () => {
  const state = readDatasetListState(route.query)
  if (datasetListSignature(state) === datasetListSignature(currentListState())) return
  Object.assign(filters, { type: state.type, status: state.status, pub: state.pub, kw: state.kw })
  Object.assign(applied, filters, { catalog: state.catalog })
  selectedCatalog.value = state.catalog
  sort.value = state.sort; order.value = state.order; pageIndex.value = state.page; pageSize.value = state.size
  void loadList()
})
onMounted(async () => {
  await Promise.all([loadDictOptions().then(loadTree), loadList()])
  if (disposed) return
  imUnsub = onImMessage(USER, onImMsg)
  consumeUploadQuery()
})
onBeforeUnmount(() => {
  disposed = true
  listRequestId++
  if (pollTimer) window.clearTimeout(pollTimer)
  if (imRefreshTimer) window.clearTimeout(imRefreshTimer)
  imUnsub?.()
})
</script>

<template>
  <div class="manage-page" :class="{ 'cards-view': viewMode === 'cards' }">
    <PageHead title="数据管理" sub="上传数据并完成入库，再发布为地图服务；在列表中跟踪处理进度">
      <template #tools>
        <el-button class="btn sm catalog-trigger" @click="mobileTreeOpen = !mobileTreeOpen"><Icon name="tree" :size="13" /> {{ mobileTreeOpen ? '收起目录' : '目录筛选' }}</el-button>
        <el-button class="btn sm primary" @click="openUpload"><Icon name="plus" :size="13" /> 上传数据</el-button>
      </template>
    </PageHead>

    <div class="manage-body">
      <!-- 左：资源目录树（原资源总览） -->
      <div class="card tree-panel" :class="{ 'mobile-open': mobileTreeOpen }">
        <div class="tree-caption"><Icon name="tree" :size="15" /><strong>资源目录</strong><el-button link size="small" @click="router.push('/layers')">管理目录</el-button></div>
        <div class="tree-tools">
          <el-select
            v-model="dictType"
            size="small"
            placeholder="目录分类"
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
        <button type="button" class="tree-all" :class="{ on: !selectedCatalog }" @click="clearTreeFilter">
          <Icon name="layers" :size="13" />
          全部数据
        </button>
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
            <el-select v-model="filters.type" placeholder="全部数据类型" aria-label="数据类型" clearable style="width: 170px" @change="doSearch">
              <el-option v-for="t in DATASET_FILTER_TYPES" :key="t.key" :label="t.label" :value="t.key" />
            </el-select>
            <el-select v-model="filters.status" placeholder="全部处理状态" aria-label="处理状态" clearable style="width: 150px" @change="doSearch">
              <el-option v-for="s in STATUS_OPTS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-select v-model="filters.pub" placeholder="全部发布状态" aria-label="发布状态" clearable style="width: 150px" @change="doSearch">
              <el-option v-for="p in PUB_OPTS" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
            <el-input
              v-model="filters.kw"
              placeholder="搜索数据名称" aria-label="数据名称"
              clearable
              style="width: 200px"
              @keyup.enter="doSearch"
              @clear="doSearch"
            />
            <el-button class="btn sm primary" @click="doSearch"><Icon name="search" :size="13" />查询</el-button>
            <el-button class="btn sm" @click="doReset">重置</el-button>
          </div>
          <DatasetQuickFilters :state="currentListState()" :disabled="loading || batchDeleting" @apply="applyListView" />
        </div>

        <Transition name="sdms-reveal">
        <div class="active-filters" v-if="activeFilterTags.length">
          <span>当前筛选</span><TransitionGroup name="sdms-chip"><el-tag v-for="tag in activeFilterTags" :key="tag.key" closable size="small" @close="removeFilter(tag.key)">{{ tag.label }}</el-tag></TransitionGroup>
          <el-button link size="small" @click="doReset">清除全部</el-button>
        </div>
        </Transition>
        <div class="card table-card">
          <div class="list-toolbar">
            <div class="list-result"><strong>数据资源</strong><span class="result-count">{{ total }}</span><span v-if="lastUpdated" class="updated-label">{{ lastUpdated }} 更新</span></div>
            <div class="list-tools">
              <el-dropdown trigger="click" @command="command => command === 'copy' ? copyPublishedServices() : exportResources(command)">
                <el-button size="small" :disabled="loading || !rows.length || !!listError" :loading="copyingServices"><Icon name="download" :size="13" />清单与地址<Icon name="chevron-down" :size="14" class="dropdown-chevron" aria-hidden="true" /></el-button>
                <template #dropdown><el-dropdown-menu>
                  <el-dropdown-item command="page">导出当前页清单（{{ rows.length }} 条）</el-dropdown-item>
                  <el-dropdown-item command="selected" :disabled="!picked.size">导出已选清单（{{ picked.size }} 条）</el-dropdown-item>
                  <el-dropdown-item command="copy" divided>{{ picked.size ? '复制已选资源服务地址' : '复制当前页服务地址' }}</el-dropdown-item>
                </el-dropdown-menu></template>
              </el-dropdown>
              <el-select :model-value="sort + ':' + order" aria-label="数据排序" size="small" class="sort-select" @change="changeSort">
                <el-option label="最新上传" value="time:desc" /><el-option label="最早上传" value="time:asc" /><el-option label="名称 A → Z" value="name:asc" /><el-option label="文件由大到小" value="size:desc" />
              </el-select>
              <el-button size="small" aria-label="刷新数据列表" :loading="loading" @click="refreshList"><Icon name="refresh" :size="13" /><span class="refresh-label">刷新</span></el-button>
              <div class="view-toggle" role="group" aria-label="列表显示方式">
                <button type="button" :class="{ active: viewMode === 'table' }" :aria-pressed="viewMode === 'table'" aria-label="表格视图" @click="setViewMode('table')"><Icon name="list" :size="16" /></button>
                <button type="button" :class="{ active: viewMode === 'cards' }" :aria-pressed="viewMode === 'cards'" aria-label="卡片视图" @click="setViewMode('cards')"><Icon name="grid" :size="15" /></button>
              </div>
            </div>
          </div>
          <el-alert v-if="listError" :title="listError + (rows.length ? '；以下保留上次加载结果' : '')" type="error" show-icon :closable="false"><el-button link @click="loadList()">重新加载</el-button></el-alert>
          <el-table ref="dataTable" class="dataset-table" row-key="key" v-loading="loading" :data="rows" size="small" height="100%" @selection-change="onSelectionChange">
            <el-table-column type="selection" width="42" :selectable="(row: UnifiedRow) => !listError && !batchDeleting && isRemovableRow(row)" />
            <el-table-column label="数据名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }: any">
                <div class="dataset-name-cell"><DatasetTypeMark :type="row.typeKey" :title="row.typeLabel" size="small" /><div><button class="row-link" type="button" @click="openDetail(row)">{{ row.name }}</button><span class="row-secondary">{{ row.format }}<span v-if="row.spatialRef"> · {{ row.spatialRef }}</span></span></div></div>
              </template>
            </el-table-column>
            <el-table-column label="分类" width="90" show-overflow-tooltip>
              <template #default="{ row }: any">{{ row.category || '—' }}</template>
            </el-table-column>
            <el-table-column label="数据大小" width="82">
              <template #default="{ row }: any">{{ row.size }}</template>
            </el-table-column>
            <el-table-column width="144">
              <template #header>
                <button type="button" class="th-sort" aria-label="切换上传时间排序" @click="toggleOrder">
                  上传时间
                  <span class="arrows" :class="order"><i></i><i></i></span>
                </button>
              </template>
              <template #default="{ row }: any"><span class="muted">{{ fmtTimeCol(row.time) }}</span></template>
            </el-table-column>
            <el-table-column label="处理 / 服务状态" width="180">
              <template #default="{ row }: any">
                <div class="status" :class="statusClass(row)">
                  <span class="d"></span>{{ row.statusLabel }}
                  <template v-if="!row.ingestPending && (row.status === 'uploading' || row.status === 'converting')"> {{ row.progress }}%</template>
                </div>
                <div v-if="!row.ingestPending && (row.status === 'uploading' || row.status === 'converting')" class="pbar">
                  <i :style="{ width: Math.max(4, row.progress) + '%' }"></i>
                </div>
                <div class="row-secondary service-summary" :title="svcInfo[row.key]?.error">
                  <span :class="row.published ? 'published-label' : ''">{{ row.published ? '已发布' : '未发布' }}</span>
                  <span v-if="svcInfo[row.key]?.formats?.length"> · {{ svcInfo[row.key].formats.join(' / ') }}</span>
                  <span v-else-if="svcInfo[row.key]?.error"> · 服务暂不可用</span>
                  <span v-if="svcInfo[row.key]?.tile === '切片中'"> · 切片中</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="174" fixed="right">
              <template #default="{ row }: any">
                <DatasetRowActions :row="row" :busy="rowBusy[row.key] || batchDeleting" :deleting-disabled="!!listError || svcInfo[row.key]?.tile === '切片中'" :publish-label="publishLabel(row)" @detail="tab => openDetail(row, tab)" @publish="openPublish(row)" @preview="openPreview(row)" @copy="copySvcAddr(row)" @edit="openEdit(row)" @remove="removeRow(row)" />
              </template>
            </el-table-column>
            <template #empty>
              <DatasetEmptyState :error="listError" :filtered="hasFilters" :loading="loading" @retry="refreshList" @reset="doReset" @upload="openUpload" />
            </template>
          </el-table>
          <div v-loading="loading" class="mobile-list">
            <div v-for="(row, index) in rows" :key="row.key" class="mobile-record" :class="{ selected: picked.has(row.key) }" :style="{ '--motion-index': Math.min(index, 7) }">
              <div class="mobile-record-head">
                <DatasetTypeMark :type="row.typeKey" /><button class="mobile-name" type="button" @click="openDetail(row)">{{ row.name }}</button>
                <span class="tag" :class="row.published ? 'green' : 'gray'">{{ row.published ? '已发布' : '未发布' }}</span>
              </div>
              <div class="mobile-record-meta">
                <span>{{ row.typeLabel }} · {{ row.format }}</span><span>{{ row.spatialRef || '未登记空间参考' }}</span>
                <span>{{ row.size }}</span>
                <span>{{ fmtTimeCol(row.time) }}</span>
                <span class="status" :class="statusClass(row)"><i class="d"></i>{{ row.statusLabel }}</span>
              </div>
              <div v-if="svcInfo[row.key]?.formats?.length" class="mobile-service">服务：{{ svcInfo[row.key].formats.join(' / ') }}</div>
              <div class="mobile-actions">
                <el-checkbox :model-value="picked.has(row.key)" :disabled="!!listError || batchDeleting || !isRemovableRow(row)" :aria-label="'选择' + row.name" @change="value => togglePicked(row, Boolean(value))" />
                <DatasetRowActions :row="row" :busy="rowBusy[row.key] || batchDeleting" :deleting-disabled="!!listError || svcInfo[row.key]?.tile === '切片中'" :publish-label="publishLabel(row)" @detail="tab => openDetail(row, tab)" @publish="openPublish(row)" @preview="openPreview(row)" @copy="copySvcAddr(row)" @edit="openEdit(row)" @remove="removeRow(row)" />
              </div>
            </div>
            <DatasetEmptyState v-if="!rows.length" :error="listError" :filtered="hasFilters" :loading="loading" @retry="refreshList" @reset="doReset" @upload="openUpload" />
          </div>
          <Transition name="sdms-reveal"><div v-if="batchVisible" class="batch-bar">
            <span>已选择 <b>{{ batchCount }}</b> 项</span><el-button link @click="clearSelection">取消选择</el-button>
            <el-button link type="primary" @click="exportResources('selected')">导出清单</el-button>
            <el-button class="btn sm danger" :loading="batchDeleting" @click="batchDel">批量删除</el-button>
          </div></Transition>
          <div class="pager">
            <span class="mobile-total">共 {{ total }} 条</span>
            <el-pagination
              v-model:current-page="pageIndex"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              small
              @change="onPaginationChange"
              @size-change="pageIndex = 1"
            />
          </div>
        </div>
      </div>
    </div>

    <DatasetUploadDrawer ref="uploadDrawer" @uploaded="onUploaded" />

    <!-- 编辑数据 -->
    <el-dialog v-model="edit.visible" title="编辑数据" width="560px" :close-on-click-modal="false">
      <div class="field">
        <label><i class="req">*</i>数据名称</label>
        <el-input v-model="edit.name" :disabled="edit.saving" maxlength="100" />
      </div>
      <div class="grid2">
        <div class="field"><label>数据类型</label><el-input disabled model-value="系统记录，不可修改" /></div>
        <div class="field"><label>数据格式</label><el-input disabled model-value="系统记录，不可修改" /></div>
      </div>
      <div class="field" style="margin-bottom: 0">
        <label>摘要 / 备注</label>
        <el-input v-model="edit.desc" :disabled="edit.saving" type="textarea" maxlength="500" :rows="3" placeholder="选填，如数据范围、坐标系、密级等说明" />
        <div class="hint">仅支持修改数据名称与摘要；其余属性由系统记录，不可修改。</div>
      </div>
      <template #footer>
        <el-button class="btn" :disabled="edit.saving" @click="edit.visible = false">取消</el-button>
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
          <el-input v-model="publish.name" :disabled="publish.submitting" placeholder="如：DOM 正射影像服务" />
        </div>
        <div class="field">
          <label>数据类型</label>
          <el-input :model-value="publish.row?.typeLabel" disabled />
        </div>
      </div>
      <div class="field">
        <label><i class="req">*</i>服务格式（可多选）</label>
        <el-checkbox-group v-model="publish.formats" :disabled="publish.submitting">
          <el-checkbox v-for="f in publishAvail" :key="f" :value="f">{{ f }}</el-checkbox>
        </el-checkbox-group>
        <div class="hint">矢量可选 WMS / WFS / MVT，栅格可选 WMS / WMTS；发布由 GeoServer 完成，勾选 WMTS 会触发切片任务。</div>
      </div>
      <template #footer>
        <el-button class="btn" :disabled="publish.submitting" @click="publish.visible = false">取消</el-button>
        <el-button class="btn primary" :loading="publish.submitting" @click="submitPublish">确定</el-button>
      </template>
    </el-dialog>

    <!-- 数据详情（二维六页签抽屉，复用原资源总览组件） -->
    <ResourceDetail v-if="drawerVisible" v-model="drawerVisible" :layer="drawerLayer" :initial-tab="drawerTab" @changed="onUploaded" />

    <!-- 全屏预览 -->
    <ServicePreview v-if="previewVisible" v-model="previewVisible" :row="previewRow" />
  </div>
</template>

<style scoped>
.manage-page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
.manage-body { flex: 1; display: flex; gap: 16px; min-height: 0; }
.catalog-trigger, .mobile-list, .mobile-total { display: none; }
.tree-panel { width: 216px; flex: 0 0 216px; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.tree-caption { display: flex; align-items: center; justify-content: space-between; padding: 16px 14px 12px; font-size: 13px; font-weight: 600; }
.tree-caption a { font-size: 11px; font-weight: 400; }
.tree-tools { padding: 0 12px 12px; border-bottom: 1px solid var(--line); }
.tree-all { display: flex; align-items: center; gap: 8px; min-height: 34px; margin: 10px 8px 4px; padding: 0 10px; border: 0; border-radius: 5px; background: transparent; font: inherit; color: var(--ink-2); cursor: pointer; text-align: left; }
.tree-all:hover { background: var(--panel-2); }
.tree-all.on { background: var(--primary-050); color: var(--primary); font-weight: 600; }
.tree-wrap { flex: 1; overflow: auto; padding: 0 6px 12px; min-height: 0; }
.tree-wrap :deep(.el-tree-node__content) { height: 34px; border-radius: 4px; }
.tree-node { display: flex; align-items: center; gap: 6px; font-size: 12px; width: 100%; min-width: 0; }
.tree-node.islayer .tn-name { color: var(--ink-2); }
.tn-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tn-cnt { margin-left: auto; font-size: 10px; color: var(--ink-3); padding: 0 6px; background: var(--panel-2); border-radius: 8px; }
.list-col { flex: 1; min-width: 0; display: flex; flex-direction: column; min-height: 0; gap: 12px; }
.filter-card { padding: 14px; animation: sdms-fade-in var(--motion-slow) ease-out 40ms backwards; }
.filters { display: flex; flex-wrap: wrap; gap: 10px; margin: 0; align-items: center; }
.filters :deep(.el-select) { flex: 1 1 130px; min-width: 120px; }
.filters :deep(.el-input) { flex: 1.3 1 170px; }
.filters :deep(.el-button + .el-button) { margin-left: 0; }
.active-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; font-size: 11px; color: var(--ink-3); padding: 0 2px; }
.active-filters :deep(.el-tag) { max-width: 250px; }
.table-card { animation: sdms-fade-in var(--motion-slow) ease-out 90ms backwards; flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.list-toolbar { min-height: 58px; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--line); }
.list-result { display: flex; align-items: center; gap: 9px; white-space: nowrap; }
.list-result strong { font-size: 13px; font-weight: 600; }
.result-count { min-width: 23px; text-align: center; background: var(--primary-050); color: var(--primary); font-size: 11px; padding: 0 6px; border-radius: 4px; }
.updated-label { font-size: 10px; color: var(--ink-3); }
.list-tools { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; }
.dropdown-chevron { flex: 0 0 auto; margin-left: 2px; }
.list-toolbar { flex-wrap: wrap; }
.batch-bar { flex-wrap: wrap; }
.sort-select { width: 130px; }
.view-toggle { display: flex; border: 1px solid var(--line); border-radius: 5px; padding: 2px; }
.view-toggle button { transition: background-color var(--motion-base), color var(--motion-base), transform var(--motion-base); display: grid; place-items: center; padding: 3px 7px; border: 0; background: transparent; color: var(--ink-3); border-radius: 3px; cursor: pointer; }
.view-toggle button.active { background: var(--primary-050); color: var(--primary); }
.dataset-table { flex: 1; min-height: 0; --el-table-row-hover-bg-color: #f7faff; }
.dataset-table :deep(.el-table__cell) { padding: 12px 0; }
.dataset-table :deep(th.el-table__cell) { font-weight: 500; font-size: 11.5px; padding: 10px 0; }
.dataset-name-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.dataset-name-cell > div { min-width: 0; }
.row-link { display: block; max-width: 100%; padding: 0; border: 0; background: transparent; font: inherit; color: var(--ink); font-weight: 500; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: left; }
.row-link:hover, .mobile-name:hover { color: var(--primary); }
.row-secondary { display: block; color: var(--ink-3); font-size: 10.5px; line-height: 1.6; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.th-sort { cursor: pointer; border: 0; padding: 0; background: transparent; color: inherit; font: inherit; display: inline-flex; align-items: center; }
.arrows { display: inline-flex; flex-direction: column; margin-left: 5px; gap: 2px; }
.arrows i { width: 0; height: 0; border-left: 3px solid transparent; border-right: 3px solid transparent; }
.arrows i:first-child { border-bottom: 4px solid #c3cad6; }
.arrows i:last-child { border-top: 4px solid #c3cad6; }
.arrows.asc i:first-child { border-bottom-color: var(--primary); }
.arrows.desc i:last-child { border-top-color: var(--primary); }
.status { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; font-size: 12px; }
.status .d { width: 6px; height: 6px; border-radius: 50%; flex: 0 0 6px; background: currentColor; }
.s-ok { color: var(--ok); }.s-err { color: var(--danger); }.s-run { color: var(--warn); }
.s-run .d { animation: sdms-status-pulse 2.2s ease-in-out infinite; }
.pbar { margin-top: 5px; height: 3px; border-radius: 2px; background: #eef1f6; overflow: hidden; width: 110px; }
.pbar i { display: block; height: 100%; position: relative; overflow: hidden; background: #dc9b32; transition: width var(--motion-slow) var(--motion-ease); }
.pbar i::after { content: ''; position: absolute; inset: 0; background: linear-gradient(90deg, transparent, rgb(255 255 255 / 55%), transparent); animation: sdms-progress-sheen 1.5s ease-in-out infinite; }
.published-label { color: var(--ok); }
.svc-fmts { font-size: 11px; color: var(--ink-2); }
.batch-bar { display: flex; align-items: center; gap: 12px; padding: 9px 16px; font-size: 12px; color: var(--ink-2); border-top: 1px solid var(--line); background: var(--primary-050); }
.batch-bar b { color: var(--primary); }
.table-card .pager { padding: 12px 16px; display: flex; justify-content: flex-end; border-top: 1px solid var(--line); }
.cards-view .dataset-table { display: none; }
.cards-view .mobile-list { display: grid; }
.mobile-list { grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 14px; padding: 16px; overflow: auto; flex: 1; align-content: start; }
.mobile-record { min-width: 0; padding: 16px; border: 1px solid var(--line); border-radius: 7px; background: #fff; animation: sdms-rise-in 420ms var(--motion-ease) calc(var(--motion-index, 0) * 40ms) backwards; transition: border-color var(--motion-base), background-color var(--motion-base), box-shadow var(--motion-base), transform var(--motion-base) var(--motion-ease); }
.mobile-record.selected { border-color: #a6c9ed; background: #f8fbff; box-shadow: 0 0 0 1px #d5e7f8; }
.mobile-record:focus-within { border-color: #9ebfe0; }
@media (hover: hover) and (pointer: fine) {
  .mobile-record:hover { border-color: #bdd1e6; transform: translateY(-3px); box-shadow: 0 7px 20px rgb(24 70 119 / 8%); }
  .mobile-record:hover :deep(.dataset-mark) { transform: translateY(-1px) rotate(-4deg); }
}
.mobile-record-head { display: flex; align-items: center; gap: 10px; }
.mobile-record-head .tag { margin-left: auto; flex-shrink: 0; font-size: 10px; }
.mobile-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 0; border: 0; background: none; color: var(--ink); font: inherit; font-size: 13px; font-weight: 600; text-align: left; cursor: pointer; }
.mobile-record-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 7px 10px; margin-top: 15px; color: var(--ink-3); font-size: 11px; }
.mobile-record-meta > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-record-meta > span:first-child { color: var(--ink-2); }
.mobile-service { margin-top: 10px; color: var(--ink-2); font-size: 11px; }
.mobile-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; padding-top: 9px; border-top: 1px solid var(--line); }
.mobile-actions :deep(.el-checkbox) { height: 24px; margin-right: 0; }
.mobile-list > :deep(.dataset-empty) { grid-column: 1 / -1; }
@media (max-width: 1280px) {
  .catalog-trigger { display: inline-flex; }
  .manage-body { position: relative; }
  .tree-panel { display: none; position: absolute; top: 0; left: 0; bottom: 0; z-index: 4; width: 240px; box-shadow: var(--sh-2); }
  .tree-panel.mobile-open { display: flex; animation: sdms-rise-in var(--motion-base) var(--motion-ease); }
}
@media (max-width: 900px) {
  .manage-page { height: auto; min-height: 100%; }
  .manage-body { flex: none; overflow: visible; }
  .tree-panel { bottom: auto; max-height: 440px; min-height: 260px; }
  .list-col { flex: 1; }
  .filter-card { padding: 12px; }
  .filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
  .filters :deep(.el-select), .filters :deep(.el-input) { width: 100% !important; min-width: 0; }
  .table-card { flex: none; }
  .list-toolbar { flex-wrap: wrap; padding: 12px; gap: 10px; }
  .updated-label { display: none; }
  .list-tools { margin-left: auto; }
  .view-toggle { display: none; }
  .table-card .dataset-table { display: none; }
  .mobile-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); padding: 12px; gap: 10px; }
  .mobile-record { padding: 13px; }
  .table-card .pager { justify-content: space-between; align-items: center; padding: 10px 12px; }
  .mobile-total { display: inline; color: var(--ink-3); font-size: 11px; white-space: nowrap; }
  .table-card .pager :deep(.el-pagination__sizes), .table-card .pager :deep(.el-pagination__total) { display: none; }
  .grid2 { grid-template-columns: 1fr; }
}
@media (max-width: 450px) {
  .mobile-list { grid-template-columns: minmax(0, 1fr); }
  .list-tools { gap: 5px; }.sort-select { width: 118px; }
  .refresh-label { display: none; }
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
