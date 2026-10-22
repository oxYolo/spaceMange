<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import ResourceDetail from '@/components/resource/ResourceDetail.vue'
import ServicePreview from '@/components/preview/ServicePreview.vue'
import { layerContentApi, serverInterfaceApi } from '@/api'
import { LAYER_GEOM_IDS, normalizeLayer, isRasterDataType, extractSvcUrl, type UnifiedRow } from '@/utils/datasets'
import { pickList, pickTotal } from '@/utils/format'
import { copyText } from '@/utils/clipboard'
import { useUserStore } from '@/stores/user'

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
const user = useUserStore()
const keyword = ref('')
const published = ref('')
const applied = reactive({ keyword: '', published: '' })
const pageIndex = ref(1)
const pageSize = ref(10)
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
let pollTimer: ReturnType<typeof setInterval> | undefined

const hasRunningTiles = computed(() => rows.value.some((row) => tileStates[row.id]?.status === 'RUNNING'))

async function loadRows() {
  loading.value = true
  try {
    const input: Record<string, string> = {
      createBy: user.userName,
      dataType: LAYER_GEOM_IDS.raster + ',',
    }
    if (applied.keyword) input.layerName = applied.keyword
    if (applied.published) input.publishStatus = applied.published
    const result = await layerContentApi.getPageListByName(input, pageSize.value, pageIndex.value)
    const pageRows = pickList<any>(result).filter((item) => isRasterDataType(item.dataType))
    // 分页接口只返回摘要字段；文件名、大小和栅格属性在详情接口中。
    const details = await Promise.allSettled(pageRows.map((item) => layerContentApi.getDetailById(item.id)))
    rows.value = pageRows.map((item, index) => normalizeLayer({
      ...item,
      ...(details[index].status === 'fulfilled' && details[index].value ? details[index].value : {}),
      layerTypeName: item.layerTypeName,
      layerRefenceName: item.layerRefenceName,
    }))
    total.value = pickTotal(result, rows.value.length)
    await Promise.allSettled(rows.value.map((row) => refreshTile(row)))
  } catch (error: any) {
    ElMessage.error(error?.message || '栅格数据加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  applied.keyword = keyword.value.trim()
  applied.published = published.value
  pageIndex.value = 1
  void loadRows()
}

function reset() {
  keyword.value = ''
  published.value = ''
  search()
}

async function refreshTile(row: UnifiedRow) {
  const [progressResult, wmsResult, wmtsResult] = await Promise.allSettled([
    serverInterfaceApi.tileProgress(row.id),
    row.published ? serverInterfaceApi.getServerInterface(row.id, 'WMS') : Promise.resolve(null),
    row.published ? serverInterfaceApi.getServerInterface(row.id, 'WMTS') : Promise.resolve(null),
  ])
  const progress: any = progressResult.status === 'fulfilled' ? progressResult.value : null
  const rawStatus = String(progress?.status ?? 'IDLE').toUpperCase()
  const status: TileStatus = ['RUNNING', 'DONE', 'FAILED'].includes(rawStatus) ? rawStatus as TileStatus : 'IDLE'
  tileStates[row.id] = {
    status,
    percent: Math.max(0, Math.min(100, Number(progress?.percent) || 0)),
    tilesProcessed: progress?.tilesProcessed,
    tilesTotal: progress?.tilesTotal,
    wms: wmsResult.status === 'fulfilled' ? extractSvcUrl(wmsResult.value, 'WMS') : '',
    wmts: wmtsResult.status === 'fulfilled' ? extractSvcUrl(wmtsResult.value, 'WMTS') : '',
    error: progressResult.status === 'rejected' ? '进度暂不可用' : '',
  }
}

async function startTiling(row: UnifiedRow) {
  if (row.status !== 'ok') return ElMessage.warning('请等待栅格数据入库完成')
  if (tileStates[row.id]?.status === 'RUNNING') return ElMessage.info('当前图层正在切片')
  try {
    await ElMessageBox.confirm(
      `对「${row.name}」提交 WMTS 切片任务？切片层级采用后端 GeoServer 配置。`,
      '栅格切片',
      { type: 'info', confirmButtonText: '开始切片', cancelButtonText: '取消' },
    )
  } catch { return }
  tileBusy[row.id] = true
  try {
    await serverInterfaceApi.publishService({ id: row.id, layerName: row.name, publishType: 'WMTS' })
    ElMessage.success('切片任务已提交，正在读取处理进度')
    await loadRows()
  } catch (error: any) {
    ElMessage.error(error?.message || '切片任务提交失败')
  } finally {
    tileBusy[row.id] = false
  }
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
  if (!edit.row) return
  if (!edit.name.trim()) return ElMessage.warning('请填写数据名称')
  edit.saving = true
  try {
    await layerContentApi.update({ ...edit.row.raw, id: edit.row.id, layerName: edit.name.trim(), layerDesc: edit.desc.trim() })
    ElMessage.success('栅格数据已更新')
    edit.visible = false
    await loadRows()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    edit.saving = false
  }
}

async function remove(row: UnifiedRow) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」及其发布信息？`, '删除栅格数据', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    const removed = await layerContentApi.removeLayer(row.id)
    if (removed === false) throw new Error('后端未删除该数据')
    ElMessage.success('已删除')
    if (rows.value.length === 1 && pageIndex.value > 1) pageIndex.value--
    await loadRows()
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败')
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
  if (state.status === 'RUNNING') return `切片中 ${state.percent}%`
  if (state.status === 'FAILED') return '切片失败'
  if (state.wmts) return '瓦片可用'
  if (state.status === 'DONE') return '任务已完成'
  return '未切片'
}

onMounted(() => {
  void loadRows()
  pollTimer = setInterval(() => {
    if (hasRunningTiles.value && !document.hidden) {
      void Promise.allSettled(rows.value.filter((row) => tileStates[row.id]?.status === 'RUNNING').map(refreshTile))
    }
  }, 5000)
})
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
  <div class="raster-page">
    <PageHead title="栅格数据管理" sub="管理 GeoTIFF 资源、查看影像详情并提交 WMTS 切片任务">
      <template #tools>
        <el-button @click="router.push('/manage')">返回数据管理</el-button>
        <el-button type="primary" @click="router.push('/manage?upload=raster')">上传栅格数据</el-button>
      </template>
    </PageHead>

    <div class="raster-card">
      <div class="raster-filters">
        <el-input v-model="keyword" placeholder="搜索栅格名称" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
        <el-select v-model="published" placeholder="全部发布状态" clearable style="width: 160px" @change="search">
          <el-option label="已发布" value="1" /><el-option label="未发布" value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button class="refresh" @click="loadRows">刷新状态</el-button>
      </div>
      <el-table v-loading="loading" class="desktop-table" :data="rows" size="small" height="100%" row-key="id">
        <el-table-column label="栅格名称" min-width="175" show-overflow-tooltip>
          <template #default="{ row }"><a class="row-link" @click="openDetail(row)">{{ row.name }}</a></template>
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
            <span :class="{ failed: tileStates[row.id]?.status === 'FAILED' }">{{ tileLabel(row) }}</span>
            <el-progress v-if="tileStates[row.id]?.status === 'RUNNING'" :percentage="tileStates[row.id].percent" :show-text="false" :stroke-width="4" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="292" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" :loading="tileBusy[row.id]" :disabled="row.status !== 'ok' || tileStates[row.id]?.status === 'RUNNING'" @click="startTiling(row)">{{ tileStates[row.id]?.wmts ? '重新切片' : '切片' }}</el-button>
            <el-button link type="primary" :disabled="!row.published" @click="preview(row)">预览</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无栅格数据，可上传 GeoTIFF 文件" :image-size="72" /></template>
      </el-table>
      <div v-loading="loading" class="mobile-list">
        <div v-for="row in rows" :key="row.id" class="mobile-row">
          <a class="row-link" @click="openDetail(row)">{{ row.name }}</a>
          <div class="mobile-meta">{{ row.raw?.uploadFileName?.split(/[\\/]/).pop() || '—' }} · {{ row.size }} · {{ row.spatialRef }}</div>
          <div class="mobile-status"><el-tag size="small" :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '未发布' }}</el-tag><span>{{ tileLabel(row) }}</span></div>
          <el-progress v-if="tileStates[row.id]?.status === 'RUNNING'" :percentage="tileStates[row.id].percent" :stroke-width="4" />
          <div class="mobile-actions">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" :loading="tileBusy[row.id]" :disabled="row.status !== 'ok' || tileStates[row.id]?.status === 'RUNNING'" @click="startTiling(row)">{{ tileStates[row.id]?.wmts ? '重新切片' : '切片' }}</el-button>
            <el-button link type="primary" :disabled="!row.published" @click="preview(row)">预览</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !rows.length" description="暂无栅格数据，可上传 GeoTIFF 文件" :image-size="60" />
      </div>
      <div class="raster-pager"><el-pagination v-model:current-page="pageIndex" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" small @current-change="loadRows" @size-change="pageIndex = 1; loadRows()" /></div>
    </div>

    <div v-if="rows.length" class="raster-card service-card">
      <strong>服务与切片</strong><span class="muted">切片层级由后端配置；状态来自 GeoServer GWC 任务。</span>
      <div v-for="row in rows.filter((item) => item.published)" :key="row.id" class="service-row">
        <span class="service-name">{{ row.name }}</span>
        <el-tag size="small" :type="tileStates[row.id]?.status === 'RUNNING' ? 'warning' : tileStates[row.id]?.wmts ? 'success' : 'info'">{{ tileLabel(row) }}</el-tag>
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
    <ResourceDetail v-model="detailVisible" :layer="detailLayer" initial-tab="overview" @changed="loadRows" />
    <ServicePreview v-model="previewVisible" :row="previewRow" />
  </div>
</template>

<style scoped>
.raster-page { height: 100%; min-height: 0; display: flex; flex-direction: column; gap: 12px; }
.raster-card { background: #fff; border: 1px solid var(--line); border-radius: 7px; padding: 14px; min-height: 0; display: flex; flex-direction: column; }
.raster-card:first-of-type { flex: 1; }
.raster-filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.refresh { margin-left: auto; }
.raster-pager { display: flex; justify-content: flex-end; padding-top: 12px; }
.row-link { color: #1c70b7; cursor: pointer; font-weight: 600; }
.row-link:hover { text-decoration: underline; }
.muted { color: #8b98a8; font-size: 12px; }
.failed { color: #c45656; }
.service-card { flex: none; max-height: 180px; overflow: auto; gap: 8px; }
.service-card strong { font-size: 13px; }
.service-row { display: flex; gap: 12px; align-items: center; border-top: 1px solid #edf0f4; padding-top: 8px; font-size: 12px; }
.service-name { min-width: 170px; color: #334155; }
.mobile-list { display: none; }
@media (max-width: 900px) {
  .desktop-table { display: none; }
  .mobile-list { display: block; overflow: auto; flex: 1; }
  .mobile-row { border: 1px solid #e6edf4; border-radius: 7px; padding: 12px; margin-bottom: 10px; }
  .mobile-meta { color: #7c8b9d; font-size: 12px; margin-top: 5px; }
  .mobile-status, .mobile-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 9px; }
  .mobile-status { font-size: 12px; }
  .mobile-actions :deep(.el-button) { margin-left: 0; }
  .service-row { flex-wrap: wrap; }
  .raster-card { overflow: auto; }
}
</style>
