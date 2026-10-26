<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
const ResourceDetail = defineAsyncComponent(() => import('@/components/resource/ResourceDetail.vue'))
const ServicePreview = defineAsyncComponent(() => import('@/components/preview/ServicePreview.vue'))
import { layerContentApi, serverInterfaceApi } from '@/api'
import { normalizeLayer, loadUnifiedPage, getDatasetServiceState, getDatasetCapabilities, invalidateDatasets, type UnifiedRow } from '@/utils/datasets'
import { usePolling } from '@/composables/usePolling'
import { mapSettledWithConcurrency } from '@/utils/async'
import { showError } from '@/utils/feedback'
import { copyText } from '@/utils/clipboard'
import { readDatasetListState } from '@/features/datasets/listState'

type TileStatus = 'IDLE' | 'RUNNING' | 'DONE' | 'FAILED'
interface TileState {
  status: TileStatus
  percent: number
  tilesProcessed?: number
  tilesTotal?: number
  wms: string
  wmts: string
  error?: string
}

const router = useRouter()
const route = useRoute()
function readRouteState() {
  const state = readDatasetListState(route.query)
  return { keyword: state.kw, published: state.pub === 'yes' ? '1' : state.pub === 'no' ? '0' : '', page: state.page, size: state.size }
}
const initialState = readRouteState()
const keyword = ref(initialState.keyword)
const published = ref(initialState.published)
const applied = reactive({ keyword: initialState.keyword, published: initialState.published })
const pageIndex = ref(initialState.page)
const pageSize = ref(initialState.size)
const rows = ref<UnifiedRow[]>([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detailLayer = ref<any>(null)
const previewVisible = ref(false)
const previewRow = ref<UnifiedRow | null>(null)
const tileStates = reactive<Record<string, TileState>>({})
const tileBusy = reactive<Record<string, boolean>>({})
const edit = reactive({ visible: false, saving: false, row: null as UnifiedRow | null, name: '', desc: '' })
let requestId = 0
let disposed = false
const loadError = ref('')

const hasRunningTiles = computed(() => rows.value.some((row) => tileStates[row.id]?.status === 'RUNNING'))
const hasFilters = computed(() => Boolean(applied.keyword || applied.published))
const publishedRows = computed(() => rows.value.filter((row) => row.published))
const emptyTitle = computed(() => loadError.value ? '栅格数据加载失败' : hasFilters.value ? '没有符合条件的栅格数据' : '暂无栅格数据')
const emptyDescription = computed(() => loadError.value ? '请重试加载，或检查服务连接。' : hasFilters.value ? '试试调整数据名称或发布状态。' : '上传 GeoTIFF 文件后，可查看影像、发布服务与切片。')

function currentState() { return { keyword: applied.keyword, published: applied.published, page: pageIndex.value, size: pageSize.value } }
function persistQuery() {
  if (JSON.stringify(readRouteState()) === JSON.stringify(currentState())) return
  const query = { ...route.query }
  for (const key of ['q', 'pub', 'page', 'size']) delete query[key]
  if (applied.keyword) query.q = applied.keyword
  if (applied.published) query.pub = applied.published === '1' ? 'yes' : 'no'
  if (pageIndex.value > 1) query.page = String(pageIndex.value)
  if (pageSize.value !== 10) query.size = String(pageSize.value)
  void router.replace({ query })
}

async function loadRows() {
  const current = ++requestId
  persistQuery()
  loading.value = true
  loadError.value = ''
  try {
    const result = await loadUnifiedPage({
      typeKey: 'raster', keyword: applied.keyword,
      publishedOnly: applied.published === '1' ? true : applied.published === '0' ? false : undefined,
    }, pageSize.value, pageIndex.value)
    if (disposed || current !== requestId) return
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
    if (pageIndex.value > lastPage) {
      pageIndex.value = lastPage
      return // 统一查询 watcher 将修正后的页码加载一次。
    }
    rows.value = result.rows
    total.value = result.total
    // Render the summary first. Optional metadata enriches only its current page.
    void mapSettledWithConcurrency(result.rows, 4, async row => {
      if (disposed || current !== requestId) return
      try {
        const detail = await layerContentApi.getDetailById(row.id)
        if (disposed || current !== requestId || !detail) return
        const index = rows.value.findIndex(item => item.id === row.id)
        if (index >= 0) rows.value[index] = normalizeLayer({ ...row.raw, ...detail,
          layerTypeName: row.raw.layerTypeName, layerRefenceName: row.raw.layerRefenceName })
      } catch { /* Summary remains usable when optional detail fails. */ }
    })
    void mapSettledWithConcurrency(result.rows, 4, row => refreshTile(row))
    const keys = new Set(result.rows.map(row => row.id))
    Object.keys(tileStates).forEach(id => { if (!keys.has(id)) delete tileStates[id] })
  } catch (error) {
    if (current !== requestId || disposed) return
    loadError.value = error instanceof Error ? error.message : '栅格数据加载失败'
    showError(error, '栅格数据加载失败')
  } finally {
    if (current === requestId) loading.value = false
  }
}
function refreshRows() { invalidateDatasets(); return loadRows() }

watch(() => JSON.stringify(readRouteState()), () => {
  const state = readRouteState()
  if (JSON.stringify(state) === JSON.stringify(currentState())) return
  keyword.value = applied.keyword = state.keyword
  published.value = applied.published = state.published
  pageIndex.value = state.page
  pageSize.value = state.size
})
watch([() => applied.keyword, () => applied.published, pageIndex, pageSize], () => { void loadRows() }, { immediate: true })

function search() {
  const unchanged = applied.keyword === keyword.value.trim() && applied.published === published.value && pageIndex.value === 1
  applied.keyword = keyword.value.trim()
  applied.published = published.value
  pageIndex.value = 1
  if (unchanged) void loadRows()
}

function reset() {
  keyword.value = ''
  published.value = ''
  search()
}

async function refreshTile(row: UnifiedRow, signal?: AbortSignal) {
  const current = requestId
  if (signal?.aborted || disposed) return
  const state = await getDatasetServiceState(row)
  if (signal?.aborted || disposed || current !== requestId || !rows.value.some(item => item.id === row.id)) return
  const previous = tileStates[row.id]
  // A transient progress failure must not stop an active job's polling.
  const progress = state.progress.error && previous?.status === 'RUNNING' ? { ...previous, error: state.progress.error } : state.progress
  tileStates[row.id] = { ...progress, wms: state.wms || previous?.wms || '', wmts: state.wmts || previous?.wmts || '' }
}

async function startTiling(row: UnifiedRow) {
  if (tileBusy[row.id]) return
  if (!getDatasetCapabilities(row).canSlice) return ElMessage.warning('请等待栅格数据入库完成')
  if (tileStates[row.id]?.status === 'RUNNING') return ElMessage.info('当前图层正在切片')
  tileBusy[row.id] = true
  try {
    await ElMessageBox.confirm(`为「${row.name}」创建 WMTS 瓦片？可在处理期间继续使用已发布的 WMS 服务。`, '栅格切片', {
      type: 'info', confirmButtonText: '开始切片', cancelButtonText: '取消',
    })
    if (disposed) return
    await serverInterfaceApi.publishService({ id: row.id, layerName: row.name, publishType: 'WMTS' })
    ElMessage.success('切片任务已提交')
    await refreshRows()
  } catch (error) { showError(error, '切片任务提交失败') }
  finally { tileBusy[row.id] = false }
}

function openDetail(row: UnifiedRow) {
  detailLayer.value = { ...row.raw, id: row.id }
  detailVisible.value = true
}

function openEdit(row: UnifiedRow) {
  edit.row = row
  edit.name = row.name
  edit.desc = String(row.raw?.layerDesc || '')
  edit.visible = true
}

async function saveEdit() {
  if (edit.saving) return
  if (!edit.row) return
  if (!edit.name.trim()) return ElMessage.warning('请填写数据名称')
  edit.saving = true
  try {
    await layerContentApi.update({ ...edit.row.raw, id: edit.row.id, layerName: edit.name.trim(), layerDesc: edit.desc.trim() })
    ElMessage.success('栅格数据已更新')
    edit.visible = false
    await refreshRows()
  } catch (error: any) {
    showError(error, '保存失败')
  } finally {
    edit.saving = false
  }
}

async function remove(row: UnifiedRow) {
  if (!getDatasetCapabilities(row as UnifiedRow).canDelete || tileStates[row.id]?.status === 'RUNNING') return ElMessage.warning('数据正在处理，请处理结束后再删除')
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」及其发布信息？`, '删除栅格数据', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    const removed = await layerContentApi.removeLayer(row.id)
    if (removed === false) throw new Error('后端未删除该数据')
    ElMessage.success('已删除')
    await refreshRows()
  } catch (error: any) {
    showError(error, '删除失败')
  }
}

async function copyAddress(url: string) {
  if (!url) return ElMessage.warning('尚未获取到服务地址')
  ;(await copyText(url)) ? ElMessage.success('服务地址已复制') : ElMessage.error('复制失败')
}

function preview(row: UnifiedRow) {
  if (!row.published) return ElMessage.warning('请先发布数据服务')
  previewRow.value = row
  previewVisible.value = true
}

function tileLabel(row: UnifiedRow) {
  const state = tileStates[row.id]
  if (!state) return '查询中'
  if (state.error) return state.error
  if (state.status === 'RUNNING') return `切片中 ${state.percent}%`
  if (state.status === 'FAILED') return '切片失败'
  if (state.wmts) return '瓦片可用'
  if (state.status === 'DONE') return '任务已完成'
  return '未切片'
}

usePolling(async signal => {
  const activeRows = rows.value.filter(row => tileStates[row.id]?.status === 'RUNNING')
  await mapSettledWithConcurrency(activeRows, 4, row => refreshTile(row, signal))
}, { enabled: hasRunningTiles, interval: 5000, immediate: false })
onBeforeUnmount(() => { disposed = true; requestId++ })
</script>

<template>
  <div class="raster-page">
    <PageHead title="栅格数据管理" sub="管理 GeoTIFF 资源、查看影像详情并提交 WMTS 切片任务">
      <template #tools>
        <el-button @click="router.push('/manage')">全部数据</el-button>
        <el-button type="primary" @click="router.push('/manage?upload=raster')">上传栅格数据</el-button>
      </template>
    </PageHead>

    <div class="raster-card list-card">
      <el-alert v-if="loadError && rows.length" class="load-alert" title="最新数据加载失败，当前保留上次结果" :description="loadError" type="error" show-icon :closable="false" />
      <div class="raster-filters">
        <el-input v-model="keyword" class="keyword-filter" aria-label="搜索栅格名称" placeholder="搜索栅格名称" clearable @keyup.enter="search" @clear="search" />
        <el-select v-model="published" class="status-filter" aria-label="发布状态" placeholder="全部发布状态" clearable @change="search">
          <el-option label="已发布" value="1" /><el-option label="未发布" value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">清除</el-button>
      </div>
      <div class="raster-results"><span><strong>栅格资源</strong><template v-if="!loadError"> · {{ total }} 项</template><span v-if="hasFilters" class="result-filtered">已筛选</span></span><el-button text size="small" :loading="loading" @click="refreshRows">刷新状态</el-button></div>
      <el-table v-loading="loading" class="desktop-table" :data="rows" size="small" height="100%" row-key="id">
        <el-table-column label="栅格名称" min-width="175" show-overflow-tooltip>
          <template #default="{ row }"><button class="row-link" type="button" @click="openDetail(row as UnifiedRow)">{{ row.name }}</button></template>
        </el-table-column>
        <el-table-column label="文件 / 格式" min-width="155" show-overflow-tooltip>
          <template #default="{ row }"><div>{{ row.raw?.uploadFileName?.split(/[\\/]/).pop() || '—' }}</div><span class="muted">{{ row.format }}</span></template>
        </el-table-column>
        <el-table-column label="空间参考" width="112" prop="spatialRef" />
        <el-table-column label="大小" width="95" prop="size" />
        <el-table-column label="入库" width="100">
          <template #default="{ row }"><el-tag size="small" :type="row.status === 'ok' ? 'success' : 'warning'">{{ row.status === 'ok' ? '已入库' : row.statusLabel }}</el-tag></template>
        </el-table-column>
        <el-table-column label="发布" width="98">
          <template #default="{ row }"><el-tag size="small" :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '未发布' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="切片状态" width="170">
          <template #default="{ row }">
            <span :class="{ failed: tileStates[row.id]?.status === 'FAILED' }">{{ tileLabel(row as UnifiedRow) }}</span>
            <el-progress v-if="tileStates[row.id]?.status === 'RUNNING'" :percentage="tileStates[row.id].percent" :show-text="false" :stroke-width="4" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="292" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as UnifiedRow)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row as UnifiedRow)">编辑</el-button>
            <el-button link type="primary" :loading="tileBusy[row.id]" :disabled="row.status !== 'ok' || tileStates[row.id]?.status === 'RUNNING'" @click="startTiling(row as UnifiedRow)">{{ tileStates[row.id]?.wmts ? '重新切片' : '切片' }}</el-button>
            <el-button link type="primary" :disabled="!row.published" @click="preview(row as UnifiedRow)">预览</el-button>
            <el-button link type="danger" :disabled="!getDatasetCapabilities(row as UnifiedRow).canDelete || tileStates[row.id]?.status === 'RUNNING'" @click="remove(row as UnifiedRow)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="emptyTitle" :image-size="64"><p class="empty-description">{{ emptyDescription }}</p><el-button v-if="loadError" size="small" @click="refreshRows">重新加载</el-button><el-button v-else-if="hasFilters" size="small" @click="reset">清除筛选</el-button><el-button v-else type="primary" size="small" @click="router.push('/manage?upload=raster')">上传栅格数据</el-button></el-empty></template>
      </el-table>
      <div v-loading="loading" class="mobile-list">
        <div v-for="(row, index) in rows" :key="row.id" class="mobile-row" :style="{ '--motion-index': Math.min(index, 7) }">
          <button class="row-link" type="button" @click="openDetail(row as UnifiedRow)">{{ row.name }}</button>
          <div class="mobile-meta">{{ row.raw?.uploadFileName?.split(/[\\/]/).pop() || '—' }} · {{ row.size }} · {{ row.spatialRef }}</div>
          <div class="mobile-status"><el-tag size="small" :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '未发布' }}</el-tag><span>{{ tileLabel(row as UnifiedRow) }}</span></div>
          <el-progress v-if="tileStates[row.id]?.status === 'RUNNING'" :percentage="tileStates[row.id].percent" :stroke-width="4" />
          <div class="mobile-actions">
            <el-button link type="primary" @click="openDetail(row as UnifiedRow)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row as UnifiedRow)">编辑</el-button>
            <el-button link type="primary" :loading="tileBusy[row.id]" :disabled="row.status !== 'ok' || tileStates[row.id]?.status === 'RUNNING'" @click="startTiling(row as UnifiedRow)">{{ tileStates[row.id]?.wmts ? '重新切片' : '切片' }}</el-button>
            <el-button link type="primary" :disabled="!row.published" @click="preview(row as UnifiedRow)">预览</el-button>
            <el-button link type="danger" :disabled="!getDatasetCapabilities(row as UnifiedRow).canDelete || tileStates[row.id]?.status === 'RUNNING'" @click="remove(row as UnifiedRow)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !rows.length" :description="emptyTitle" :image-size="60"><p class="empty-description">{{ emptyDescription }}</p><el-button v-if="loadError" size="small" @click="refreshRows">重新加载</el-button><el-button v-else-if="hasFilters" size="small" @click="reset">清除筛选</el-button><el-button v-else type="primary" size="small" @click="router.push('/manage?upload=raster')">上传栅格数据</el-button></el-empty>
      </div>
      <div v-if="total > 0" class="raster-pager"><el-pagination v-model:current-page="pageIndex" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" :pager-count="5" layout="total, sizes, prev, pager, next" small @size-change="pageIndex = 1" /></div>
    </div>

    <div v-if="publishedRows.length" class="raster-card service-card">
      <strong>服务与切片</strong><span class="muted">栅格发布后可预览地图服务；切片完成后可使用 WMTS 地址。</span>
      <div v-for="(row, index) in publishedRows" :key="row.id" class="service-row" :style="{ '--motion-index': Math.min(index, 7) }">
        <span class="service-name">{{ row.name }}</span>
        <el-tag size="small" :type="tileStates[row.id]?.status === 'RUNNING' ? 'warning' : tileStates[row.id]?.wmts ? 'success' : 'info'">{{ tileLabel(row as UnifiedRow) }}</el-tag>
        <el-button link type="primary" :disabled="!tileStates[row.id]?.wms" @click="copyAddress(tileStates[row.id]?.wms)">复制 WMS</el-button>
        <el-button link type="primary" :disabled="!tileStates[row.id]?.wmts" @click="copyAddress(tileStates[row.id]?.wmts)">复制 WMTS</el-button>
        <span v-if="tileStates[row.id]?.tilesTotal" class="muted">{{ tileStates[row.id]?.tilesProcessed || 0 }} / {{ tileStates[row.id]?.tilesTotal }} 瓦片</span>
      </div>
    </div>

    <el-dialog v-model="edit.visible" title="编辑栅格数据" width="500px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="数据名称" required><el-input v-model="edit.name" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="数据摘要"><el-input v-model="edit.desc" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="edit.visible = false">取消</el-button><el-button type="primary" :loading="edit.saving" @click="saveEdit">保存</el-button></template>
    </el-dialog>
    <ResourceDetail v-if="detailVisible" v-model="detailVisible" :layer="detailLayer" initial-tab="overview" @changed="refreshRows" />
    <ServicePreview v-if="previewVisible" v-model="previewVisible" :row="previewRow" />
  </div>
</template>

<style scoped>
.raster-page { height: 100%; min-height: 0; display: flex; flex-direction: column; gap: 16px; }
.raster-page :deep(.page-head) { margin-bottom: 0; }
.raster-card { animation: sdms-fade-in var(--motion-slow) ease-out 60ms backwards; background: #fff; border: 1px solid var(--line); border-radius: var(--r); padding: 16px; min-height: 0; display: flex; flex-direction: column; box-shadow: var(--sh); }
.list-card { flex: 1; min-height: 300px; }
.load-alert { flex: 0 0 auto; margin-bottom: 12px; }
.raster-filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; padding-bottom: 14px; }
.keyword-filter { width: 230px; }
.status-filter { width: 155px; }
.raster-filters :deep(.el-button + .el-button) { margin-left: 0; }
.raster-results { flex: 0 0 auto; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; border-top: 1px solid var(--line); color: var(--ink-3); font-size: 12px; }
.raster-results strong { color: var(--ink-2); font-size: 12px; font-weight: 600; }
.result-filtered { margin-left: 10px; padding: 2px 6px; background: var(--primary-050); color: var(--primary); border-radius: 4px; font-size: 10px; }
.raster-pager { display: flex; flex: 0 0 auto; justify-content: flex-end; padding-top: 14px; overflow-x: auto; }
.row-link { display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 0; border: 0; background: transparent; color: var(--primary); cursor: pointer; font: inherit; font-weight: 600; text-align: left; }
.row-link:hover { text-decoration: underline; }
.muted { color: #8b98a8; font-size: 12px; }
.failed { color: #c45656; }
.service-card { flex: none; max-height: 170px; overflow: auto; gap: 8px; }
.service-card strong { font-size: 13px; }
.service-row { animation: sdms-fade-in var(--motion-slow) ease-out calc(var(--motion-index, 0) * 35ms) backwards; display: flex; gap: 12px; align-items: center; border-top: 1px solid #edf0f4; padding-top: 8px; font-size: 12px; }
.service-name { flex: 1 1 170px; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-2); }
.empty-description { margin: 0 0 12px; max-width: 300px; color: var(--ink-3); font-size: 12px; line-height: 1.6; }
.mobile-list { display: none; }
@media (max-width: 900px) {
  .desktop-table { display: none; }
  .mobile-list { display: block; overflow: auto; flex: 1; }
  .mobile-row { animation: sdms-rise-in 420ms var(--motion-ease) calc(var(--motion-index, 0) * 45ms) backwards; transition: border-color var(--motion-base), box-shadow var(--motion-base), transform var(--motion-base) var(--motion-ease); border: 1px solid #e6edf4; border-radius: 7px; padding: 12px; margin-bottom: 10px; }
  .mobile-meta { color: #7c8b9d; font-size: 12px; margin-top: 5px; }
  .mobile-status, .mobile-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 9px; }
  .mobile-status { font-size: 12px; }
  .mobile-actions :deep(.el-button) { margin-left: 0; }
  .service-row { flex-wrap: wrap; }
  .raster-card { overflow: auto; }
}
@media (hover: hover) and (pointer: fine) {
  .mobile-row:hover { transform: translateY(-2px); border-color: #bdd1e6; box-shadow: 0 5px 16px rgb(24 70 119 / 7%); }
}
@media (max-width: 600px) {
  .raster-page { height: auto; min-height: 100%; }
  .raster-card { padding: 12px; }
  .list-card { min-height: 380px; }
  .keyword-filter { width: 100%; }
  .status-filter { flex: 1; width: auto; min-width: 140px; }
  .mobile-list { overflow: visible; flex: none; }
  .mobile-meta { overflow-wrap: anywhere; }
  .mobile-actions { gap: 14px; }
  .service-card { max-height: none; }
  .service-name { flex-basis: 100%; }
  .raster-pager { justify-content: flex-start; }
  .raster-pager :deep(.el-pagination__sizes) { display: none; }
}
</style>
