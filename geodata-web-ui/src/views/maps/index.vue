<script setup lang="ts">
/**
 * 地图管理（#/maps）—— 覆盖 MapController + MapRelationController 的地图编排链路
 *
 * 已接入：
 *  - 列表/详情/新建/编辑/删除：GET map/GetAllList（分类已替换为名称）、map/GetDetailById（mapTypeCode
 *    回填表单）、map/Insert | Update | Delete（form 绑定）
 *  - 发布与缓存：mapreleation/PublicMap（GeoServer 图层组 + 缩略图）、RefreshMap（重建图层组并重新切片）、
 *    TileProgress（GWC 任务进度，RUNNING 时自动轮询）、map/UpdateMapBBox（按关联图层重算范围）
 *  - 图层编排（抽屉）：GetAllListByMapId 清单、MultiInsert 关联、MultiUpdate 整表覆盖保存（排序/比例尺/移除）
 *  - mapEnName（英文标识）：后端 Insert 不落库该字段（发布与缩略图依赖），由前端随表单携带并做 ASCII 兜底
 * 未接入（旧浏览/点查类接口，按需再扩）：GetAllListByName/GetPageListByName、ChangeStyle(Object)、
 *  MultiUpdateScale、IsExistTilesTask（切片任务判定，TileProgress 已覆盖展示）、
 *  GetLayerAttrByLayerPt、GetTypeInstance、GetMaps、GetDataStyle、GetAllDataToTable
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { dataTypeApi, layerContentApi, mapApi, mapRelationApi } from '@/api'
import { fmtTime, pickList } from '@/utils/format'
import { isRasterDataType, layerDataTypeName } from '@/utils/datasets'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const USER = userStore.userName || 'admin'

/* ---------------- 列表（GetAllList 无分页参数：前端过滤 + 本地分页） ---------------- */
const rows = ref<any[]>([])
const loading = ref(false)
const kw = ref('')
const page = reactive({ size: 10, index: 1 })

const filtered = computed(() => {
  const k = kw.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter(
    (r) =>
      String(r.mapName || '').toLowerCase().includes(k) ||
      String(r.mapType || '').toLowerCase().includes(k) ||
      String(r.createUserName || '').toLowerCase().includes(k),
  )
})
const paged = computed(() =>
  filtered.value.slice((page.index - 1) * page.size, page.index * page.size),
)

async function loadList(silent = false) {
  if (!silent) loading.value = true
  try {
    rows.value = pickList<any>(await mapApi.getAllList())
  } catch (e: any) {
    if (!silent) ElMessage.error(e?.message || '地图列表加载失败')
  } finally {
    if (!silent) loading.value = false
    scheduleTilePoll()
  }
}

/* ---------------- 切片进度（GWC 任务） ---------------- */
const tileInfo = reactive<Record<string, any>>({})
async function queryTile(row: any, silent = false) {
  try {
    tileInfo[row.id] = await mapRelationApi.tileProgress(row.id)
  } catch {
    if (!silent) ElMessage.warning('切片进度查询失败（请确认 GeoServer 可用）')
  }
}
const hasTiling = computed(() =>
  Object.values(tileInfo).some((t: any) => String(t?.status || '').toUpperCase() === 'RUNNING'),
)
function tileText(row: any): string {
  const t = tileInfo[row.id]
  if (!t) return '—'
  const st = String(t.status || '').toUpperCase()
  if (st === 'RUNNING') {
    const p = t.percent != null ? `${t.percent}%` : ''
    const cnt = t.tilesTotal ? ` ${t.tilesProcessed ?? 0}/${t.tilesTotal}` : ''
    const remain = t.remainSeconds ? ` 剩${Math.max(1, Math.round(t.remainSeconds / 60))}分` : ''
    return `切片中 ${p}${cnt}${remain}`.trim()
  }
  if (st === 'DONE') return '已完成'
  if (st === 'IDLE') return '无切片任务'
  if (st === 'FAILED') return '查询失败'
  return st || '—'
}
let tileTimer: number | null = null
function scheduleTilePoll() {
  if (tileTimer) window.clearTimeout(tileTimer)
  tileTimer = window.setTimeout(async () => {
    if (hasTiling.value) {
      for (const r of rows.value) {
        if (String(tileInfo[r.id]?.status || '').toUpperCase() === 'RUNNING') await queryTile(r, true)
      }
    }
    scheduleTilePoll()
  }, 10000)
}

/* ---------------- 新建 / 编辑 ---------------- */
const CONFIDENTIAL_OPTS = ['内部', '公开', '涉密']
const edit = reactive({
  visible: false,
  saving: false,
  isNew: true,
  id: '',
  raw: null as any,
  form: {
    mapName: '',
    mapEnName: '',
    mapType: '',
    spatialRefence: 'EPSG:4326',
    dataSource: '',
    dataConfidential: '内部',
    keywords: '',
    serviceObject: '',
    dataDesc: '',
    dataAccuracy: '',
    mapLegend: '',
  },
})
function resetForm() {
  edit.form = {
    mapName: '',
    mapEnName: '',
    mapType: '',
    spatialRefence: 'EPSG:4326',
    dataSource: '',
    dataConfidential: '内部',
    keywords: '',
    serviceObject: '',
    dataDesc: '',
    dataAccuracy: '',
    mapLegend: '',
  }
}

const catalogOptions = ref<{ id: string; label: string }[]>([])
async function loadCatalogs() {
  try {
    const list: any = await dataTypeApi.getAllList()
    const flat: { id: string; label: string }[] = []
    const walk = (arr: any[], depth = 0) =>
      arr.forEach((n: any) => {
        flat.push({ id: String(n.id), label: `${'　'.repeat(depth)}${n.typeName ?? n.name ?? n.id}` })
        if (Array.isArray(n.children)) walk(n.children, depth + 1)
      })
    walk(pickList<any>(list))
    catalogOptions.value = flat
  } catch {
    catalogOptions.value = []
  }
}

/** mapEnName 兜底：保留 ASCII 字符；中文名取不到时用时间戳标识（发布/缩略图依赖该字段非空） */
function autoEnName(name: string): string {
  const ascii = name.replace(/[^a-zA-Z0-9]/g, '').toLowerCase()
  return ascii || 'map' + Date.now().toString(36)
}

function openCreate() {
  edit.isNew = true
  edit.id = ''
  edit.raw = null
  resetForm()
  edit.visible = true
  if (!catalogOptions.value.length) loadCatalogs()
}

async function openEdit(row: any) {
  edit.isNew = false
  edit.id = row.id
  edit.raw = null
  resetForm()
  edit.visible = true
  edit.saving = true
  try {
    const d: any = await mapApi.getDetailById(row.id)
    edit.raw = d || {}
    edit.form = {
      mapName: String(d?.mapName ?? row.mapName ?? ''),
      mapEnName: String(d?.mapEnName ?? ''),
      mapType: String(d?.mapTypeCode ?? ''),
      spatialRefence: String(d?.spatialRefence ?? ''),
      dataSource: String(d?.dataSource ?? ''),
      dataConfidential: String(d?.dataConfidential || '内部'),
      keywords: String(d?.keywords ?? ''),
      serviceObject: String(d?.serviceObject ?? ''),
      dataDesc: String(d?.dataDesc ?? ''),
      dataAccuracy: String(d?.dataAccuracy ?? ''),
      mapLegend: String(d?.mapLegend ?? ''),
    }
    if (!catalogOptions.value.length) loadCatalogs()
  } catch (e: any) {
    ElMessage.error(e?.message || '地图详情加载失败')
  } finally {
    edit.saving = false
  }
}

async function saveEdit() {
  if (!edit.form.mapName.trim()) return ElMessage.warning('请填写地图名称')
  edit.saving = true
  try {
    if (edit.isNew) {
      const dto: any = await mapApi.insert({
        mapName: edit.form.mapName.trim(),
        mapEnName: edit.form.mapEnName.trim() || autoEnName(edit.form.mapName),
        mapType: edit.form.mapType,
        spatialRefence: edit.form.spatialRefence,
        dataSource: edit.form.dataSource,
        dataConfidential: edit.form.dataConfidential,
        keywords: edit.form.keywords,
        serviceObject: edit.form.serviceObject,
        dataDesc: edit.form.dataDesc,
        dataAccuracy: edit.form.dataAccuracy,
        mapLegend: edit.form.mapLegend,
        createUserId: USER,
        createUserName: USER,
      })
      if (!dto?.id) throw new Error('新建地图失败：后端未返回记录')
      ElMessage.success('地图已创建，请在「图层」中关联图层后发布')
    } else {
      // Update 会整体覆盖以下字段（含 mapStatus / mapPublishAddress / publishDT），
      // 必须回带详情原始值；采用白名单显式回带，避免时间字段等无关项的绑定风险
      const raw = edit.raw || {}
      await mapApi.update({
        id: edit.id,
        mapPublishAddress: raw.mapPublishAddress ?? '',
        mapStatus: raw.mapStatus ?? '',
        mapTag: raw.mapTag ?? '',
        publishDT: raw.publishDT ?? '',
        sortCode: raw.sortCode ?? '',
        enabledMark: raw.enabledMark ?? '',
        deleteMark: raw.deleteMark ?? '',
        createUserId: raw.createUserId ?? USER,
        createUserName: raw.createUserName ?? USER,
        modifyUserId: raw.modifyUserId ?? '',
        modifyUserName: raw.modifyUserName ?? '',
        mapScale: raw.mapScale ?? '',
        // 表单可编辑字段
        mapType: edit.form.mapType,
        spatialRefence: edit.form.spatialRefence,
        dataSource: edit.form.dataSource,
        dataConfidential: edit.form.dataConfidential,
        keywords: edit.form.keywords,
        serviceObject: edit.form.serviceObject,
        dataDesc: edit.form.dataDesc,
        dataAccuracy: edit.form.dataAccuracy,
        mapLegend: edit.form.mapLegend,
        mapName: edit.form.mapName, // 后端不更新该字段，仅用于日志文案
      })
      ElMessage.success('已保存（地图名称由系统记录，不可修改）')
    }
    edit.visible = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    edit.saving = false
  }
}

/* ---------------- 行操作：发布 / 刷新缓存 / 删除 ---------------- */
async function publishMap(row: any) {
  try {
    await ElMessageBox.confirm(
      `将按已关联图层重建 GeoServer 图层组并生成缩略图。图层有变动时请先「重算范围」。确认发布「${row.mapName}」？`,
      '发布地图',
      { type: 'info', confirmButtonText: '发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await mapRelationApi.publicMap(row.id)
    ElMessage.success('发布成功（GeoServer 图层组已更新）')
    await loadList(true)
    await queryTile(row)
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败（请确认已关联图层且 GeoServer 可用）')
  }
}

async function refreshCache(row: any) {
  try {
    await ElMessageBox.confirm(
      `将重建图层组并重新发起切片，过程可能较久。确认刷新「${row.mapName}」的缓存？`,
      '刷新缓存',
      { type: 'warning', confirmButtonText: '刷新', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await mapRelationApi.refreshMap(row.id, USER)
    ElMessage.success('缓存刷新任务已下发')
    await queryTile(row)
  } catch (e: any) {
    ElMessage.error(e?.message || '刷新失败')
  }
}

async function removeMap(row: any) {
  try {
    await ElMessageBox.confirm(
      `删除「${row.mapName}」将同时清理图层关联与 GeoServer 图层组，确认删除？`,
      '删除地图',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await mapApi.remove(row.id, USER)
    ElMessage.success('已删除')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

/* ---------------- 图层编排抽屉 ---------------- */
const drawer = reactive({
  visible: false,
  map: null as any,
  loading: false,
  saving: false,
  dirty: false,
  list: [] as any[],
})

async function openLayers(row: any) {
  drawer.map = row
  drawer.visible = true
  drawer.dirty = false
  await loadRelations()
  queryTile(row)
}
async function loadRelations() {
  if (!drawer.map) return
  drawer.loading = true
  try {
    const list = pickList<any>(await mapRelationApi.getAllListByMapId(drawer.map.id))
    list.sort((a, b) => Number(a.dataSort ?? 9999) - Number(b.dataSort ?? 9999))
    drawer.list = list
    drawer.dirty = false
  } catch (e: any) {
    drawer.list = []
    ElMessage.error(e?.message || '图层关联加载失败')
  } finally {
    drawer.loading = false
  }
}
function relName(r: any): string {
  return String(r?.layers?.layerName ?? r?.dataConfigName ?? r?.dataConfigID ?? '-')
}
function relTypeTag(r: any): string {
  const t = String(r?.layers?.uploadFileType ?? '')
  return t === '2' ? '栅格' : t === '1' ? '矢量' : ''
}
function moveRel(i: number, dir: -1 | 1) {
  const j = i + dir
  if (j < 0 || j >= drawer.list.length) return
  const arr = drawer.list
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
  drawer.dirty = true
}
function removeRel(i: number) {
  drawer.list.splice(i, 1)
  drawer.dirty = true
}
async function saveRelations() {
  if (!drawer.map) return
  drawer.saving = true
  try {
    // MultiUpdate 为「整表覆盖」：删旧关联后按提交顺序重建，故全量回带并重排 dataSort
    const payload = drawer.list.map((r, i) => ({
      mapId: drawer.map.id,
      dataConfigID: r.dataConfigID,
      dataStyleID: r.dataStyleID ?? '',
      dataSort: i + 1,
      minScaleDenominator: r.minScaleDenominator ?? '',
      maxScaleDenominator: r.maxScaleDenominator ?? '',
    }))
    await mapRelationApi.multiUpdate(drawer.map.id, payload, USER)
    ElMessage.success('已保存排序 / 比例尺')
    await loadRelations()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    drawer.saving = false
  }
}
async function recalcBBox() {
  if (!drawer.map) return
  try {
    await mapApi.updateMapBBox(drawer.map.id)
    ElMessage.success('已按关联图层重算地图范围')
    await loadList(true)
  } catch (e: any) {
    ElMessage.error(e?.message || '重算范围失败')
  }
}

/* ---------------- 关联图层弹窗 ---------------- */
const picker = reactive({
  visible: false,
  loading: false,
  keyword: '',
  list: [] as any[],
  selected: [] as string[],
})
const pickerFiltered = computed(() => {
  const k = picker.keyword.trim().toLowerCase()
  if (!k) return picker.list
  return picker.list.filter((l) => String(l.layerName ?? '').toLowerCase().includes(k))
})
async function openPicker() {
  picker.visible = true
  picker.keyword = ''
  picker.selected = []
  picker.loading = true
  try {
    picker.list = pickList<any>(await layerContentApi.getPageListByName({ createBy: USER }, 50, 1))
  } catch (e: any) {
    picker.list = []
    ElMessage.error(e?.message || '图层列表加载失败')
  } finally {
    picker.loading = false
  }
}
async function confirmPicker() {
  if (!drawer.map) return
  if (!picker.selected.length) return ElMessage.warning('请选择要关联的图层')
  drawer.saving = true
  try {
    const payload = picker.selected.map((id, i) => ({
      mapId: drawer.map.id,
      dataConfigID: id,
      dataSort: drawer.list.length + i + 1,
    }))
    await mapRelationApi.multiInsert(payload)
    ElMessage.success(`已关联 ${picker.selected.length} 个图层`)
    picker.visible = false
    await loadRelations()
  } catch (e: any) {
    ElMessage.error(e?.message || '关联失败')
  } finally {
    drawer.saving = false
  }
}
function onPickerChange(sel: any[]) {
  picker.selected = sel.map((s) => String(s.id))
}

/* ---------------- 生命周期 ---------------- */
onMounted(async () => {
  await loadList()
  loadCatalogs()
})
onBeforeUnmount(() => {
  if (tileTimer) window.clearTimeout(tileTimer)
})
</script>

<template>
  <div class="maps-page">
    <PageHead title="地图管理" sub="地图编排与发布：图层关联、切片缓存、GeoServer 图层组发布">
      <template #tools>
        <el-button class="btn sm primary" @click="openCreate"><Icon name="plus" :size="13" /> 新建地图</el-button>
        <el-button class="btn sm" @click="loadList()"><Icon name="refresh" :size="13" /> 刷新</el-button>
      </template>
    </PageHead>

    <div class="card filter-card">
      <div class="filters">
        <el-input
          v-model="kw"
          placeholder="请输入地图名称 / 分类 / 创建人"
          clearable
          style="width: 240px"
          @keyup.enter="page.index = 1"
          @clear="page.index = 1"
        />
        <span class="muted total-hint">共 {{ filtered.length }} 张地图</span>
      </div>
    </div>

    <div class="card table-card">
      <el-table v-loading="loading" :data="paged" size="small" height="100%">
        <el-table-column label="地图名称" min-width="170" show-overflow-tooltip>
          <template #default="{ row }: any">
            <a class="row-link" @click="openLayers(row)">{{ row.mapName || '-' }}</a>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="150" show-overflow-tooltip>
          <template #default="{ row }: any">{{ row.mapType || '—' }}</template>
        </el-table-column>
        <el-table-column label="空间参考" width="140" show-overflow-tooltip>
          <template #default="{ row }: any">{{ row.spatialRefenceName || row.spatialRefence || '—' }}</template>
        </el-table-column>
        <el-table-column label="切片状态" width="180">
          <template #default="{ row }: any">
            <span class="tag" :class="String(tileInfo[row.id]?.status || '').toUpperCase() === 'RUNNING' ? 'orange' : 'gray'">
              {{ tileText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="100" show-overflow-tooltip>
          <template #default="{ row }: any">{{ row.createUserName || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }: any"><span class="muted">{{ fmtTime(row.createDT) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="252" fixed="right">
          <template #default="{ row }: any">
            <el-button class="op" link type="primary" size="small" @click="openLayers(row)">图层</el-button>
            <span class="op-sep">|</span>
            <el-button class="op" link type="primary" size="small" @click="publishMap(row)">发布</el-button>
            <span class="op-sep">|</span>
            <el-button class="op" link type="primary" size="small" @click="queryTile(row)">切片</el-button>
            <span class="op-sep">|</span>
            <el-button class="op" link type="primary" size="small" @click="refreshCache(row)">刷新</el-button>
            <span class="op-sep">|</span>
            <el-button class="op" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <span class="op-sep">|</span>
            <el-button class="op del" link type="danger" size="small" @click="removeMap(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无地图（可新建后关联图层并发布）" :image-size="80" />
        </template>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="page.index"
          v-model:page-size="page.size"
          :total="filtered.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          small
        />
      </div>
    </div>

    <!-- 新建 / 编辑地图 -->
    <el-dialog
      v-model="edit.visible"
      :title="edit.isNew ? '新建地图' : '编辑地图'"
      width="640px"
      :close-on-click-modal="false"
    >
      <div class="grid2">
        <div class="field">
          <label><i class="req">*</i>地图名称</label>
          <el-input v-model="edit.form.mapName" :disabled="!edit.isNew" placeholder="如：赤壁市基础地图" />
          <div v-if="!edit.isNew" class="hint">地图名称由系统记录，不可修改</div>
        </div>
        <div class="field">
          <label>英文标识（mapEnName）</label>
          <el-input v-model="edit.form.mapEnName" :disabled="!edit.isNew" placeholder="发布图层组 / 缩略图使用，留空自动生成" />
        </div>
      </div>
      <div class="grid2">
        <div class="field">
          <label>所属分类</label>
          <el-select v-model="edit.form.mapType" placeholder="选择分类（sdms_datatype）" clearable filterable style="width: 100%">
            <el-option v-for="c in catalogOptions" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </div>
        <div class="field">
          <label>空间参考</label>
          <el-input v-model="edit.form.spatialRefence" placeholder="如 EPSG:4326" />
        </div>
      </div>
      <div class="grid2">
        <div class="field">
          <label>数据来源</label>
          <el-input v-model="edit.form.dataSource" placeholder="数据来源说明" />
        </div>
        <div class="field">
          <label>数据密级</label>
          <el-select v-model="edit.form.dataConfidential" style="width: 100%">
            <el-option v-for="c in CONFIDENTIAL_OPTS" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
      </div>
      <div class="grid2">
        <div class="field">
          <label>关键字</label>
          <el-input v-model="edit.form.keywords" placeholder="多个关键字以逗号分隔" />
        </div>
        <div class="field">
          <label>服务对象</label>
          <el-input v-model="edit.form.serviceObject" placeholder="如 内部业务系统" />
        </div>
      </div>
      <div class="grid2">
        <div class="field">
          <label>图例地址</label>
          <el-input v-model="edit.form.mapLegend" placeholder="图例图片 URL（选填）" />
        </div>
        <div class="field">
          <label>数据精度</label>
          <el-input v-model="edit.form.dataAccuracy" placeholder="如 1:2000（选填）" />
        </div>
      </div>
      <div class="field" style="margin-bottom: 0">
        <label>数据说明</label>
        <el-input v-model="edit.form.dataDesc" type="textarea" :rows="2" placeholder="选填" />
      </div>
      <template #footer>
        <el-button class="btn" @click="edit.visible = false">取消</el-button>
        <el-button class="btn primary" :loading="edit.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 图层编排抽屉 -->
    <el-drawer v-model="drawer.visible" :title="'图层编排 · ' + (drawer.map?.mapName || '')" size="760px">
      <div class="dlg-body">
        <div class="drawer-tools">
          <el-button class="btn sm primary" @click="openPicker"><Icon name="plus" :size="12" /> 关联图层</el-button>
          <el-button class="btn sm" :disabled="!drawer.dirty" :loading="drawer.saving" @click="saveRelations">保存排序 / 比例尺</el-button>
          <el-button class="btn sm" @click="recalcBBox">重算范围</el-button>
          <el-button class="btn sm" @click="loadRelations">重新加载</el-button>
          <span v-if="drawer.dirty" class="dirty-hint">有未保存的调整</span>
        </div>
        <el-table v-loading="drawer.loading" :data="drawer.list" size="small" max-height="62vh">
          <el-table-column type="index" width="44" label="#" />
          <el-table-column label="图层" min-width="170" show-overflow-tooltip>
            <template #default="{ row }: any">
              <span>{{ relName(row) }}</span>
              <span v-if="relTypeTag(row)" class="tag type-tag" :class="relTypeTag(row) === '栅格' ? 'orange' : 'blue'">
                {{ relTypeTag(row) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="样式" width="140" show-overflow-tooltip>
            <template #default="{ row }: any">{{ row.dataStyleName || row.styleName || '默认' }}</template>
          </el-table-column>
          <el-table-column label="最小比例尺" width="118">
            <template #default="{ row }: any">
              <el-input v-model="row.minScaleDenominator" size="small" placeholder="选填" @change="drawer.dirty = true" />
            </template>
          </el-table-column>
          <el-table-column label="最大比例尺" width="118">
            <template #default="{ row }: any">
              <el-input v-model="row.maxScaleDenominator" size="small" placeholder="选填" @change="drawer.dirty = true" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="156" fixed="right">
            <template #default="{ $index }: any">
              <el-button class="op" link type="primary" size="small" :disabled="$index === 0" @click="moveRel($index, -1)">上移</el-button>
              <span class="op-sep">|</span>
              <el-button class="op" link type="primary" size="small" :disabled="$index === drawer.list.length - 1" @click="moveRel($index, 1)">下移</el-button>
              <span class="op-sep">|</span>
              <el-button class="op del" link type="danger" size="small" @click="removeRel($index)">移除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="尚未关联图层（点击「关联图层」添加）" :image-size="70" />
          </template>
        </el-table>
        <div class="hint">
          提示：调整顺序 / 比例尺后点击「保存排序 / 比例尺」整表保存（后端为整表覆盖写入）；移除同样在保存后生效。
          发布或刷新缓存前建议先「重算范围」。
        </div>
      </div>
    </el-drawer>

    <!-- 关联图层弹窗 -->
    <el-dialog v-model="picker.visible" title="关联图层" width="680px" :close-on-click-modal="false">
      <div class="filters">
        <el-input v-model="picker.keyword" placeholder="按图层名称过滤" clearable style="width: 220px" />
        <span class="muted">已选 {{ picker.selected.length }} 项（列表展示近 50 条图层）</span>
      </div>
      <el-table
        v-loading="picker.loading"
        :data="pickerFiltered"
        size="small"
        max-height="380"
        @selection-change="onPickerChange"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column label="图层名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: any">{{ row.layerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }: any">
            <span class="tag" :class="isRasterDataType(row.dataType) ? 'orange' : 'blue'">
              {{ layerDataTypeName(row.dataType) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="上传状态" width="110">
          <template #default="{ row }: any">
            {{ String(row.uploadStatus) === '1' ? '已上传' : '上传中' }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button class="btn" @click="picker.visible = false">取消</el-button>
        <el-button class="btn primary" :loading="drawer.saving" @click="confirmPicker">关联</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.maps-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.filter-card {
  padding: 12px 14px;
}
.total-hint {
  margin-left: auto;
  font-size: 12px;
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
.row-link {
  color: var(--ink);
  font-weight: 500;
  cursor: pointer;
}
.row-link:hover {
  color: var(--primary);
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
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
.dlg-body {
  padding: 2px 4px;
}
.drawer-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.dirty-hint {
  font-size: 12px;
  color: var(--warn, #d99a2b);
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}
.field {
  margin-bottom: 14px;
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
.type-tag {
  margin-left: 6px;
}
.muted {
  color: var(--ink-3);
}
</style>
