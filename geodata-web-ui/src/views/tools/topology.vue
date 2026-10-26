<script setup lang="ts">
import { showError } from '@/utils/feedback'
/**
 * 拓扑检查（tools/topology）
 * 接口依据：TopologyController(10)
 *  - GET  /topology/rules?geometryType=               可用规则（19 条）
 *  - POST /topology/check                             异步检查 → taskId
 *  - GET  /topology/check/{taskId}                    轮询报告（processing / failed / 报告）
 *  - POST /topology/checkSync?pageIndex&pageSize       同步检查（默认容差 0.001）
 *  - GET  /topology/status/{layerId}                  拓扑状态
 *  - POST /topology/create?topologyName&srid&tolerance 创建拓扑
 *  - POST /topology/import?topologyName&featureTable   导入要素
 *  - POST /topology/add-topo-column                    添加拓扑列
 *  - POST /topology/populate                           填充拓扑数据
 *  - DELETE /topology/{layerId}                        删除拓扑
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { layerContentApi, topologyApi } from '@/api'
import { pickList } from '@/utils/format'
import { copyText as clipboardCopy } from '@/utils/clipboard'
import { useUserStore } from '@/auth/store'

/* ------------------------------ 通用 ------------------------------ */
const USER = useUserStore().userName
function rowId(r: any): string {
  return String(r?.id ?? r?.layerId ?? r?.layerID ?? '')
}
function rowName(r: any): string {
  return String(r?.layerName ?? r?.name ?? r?.title ?? '-')
}
async function copyText(text?: string) {
  const t = (text ?? '').trim()
  if (!t) return
  const ok = await clipboardCopy(t)
  if (ok) ElMessage.success('已复制')
  else ElMessage.error('复制失败')
}

/* ------------------------------ 图层 ------------------------------ */
const layerOptions = ref<any[]>([])
async function loadLayers() {
  try {
    layerOptions.value = pickList<any>(await layerContentApi.getPageListByName({ createBy: USER }, 50, 1))
  } catch (e: any) {
    showError(e, '加载图层下拉失败')
  }
}

/* ------------------------------ 规则 ------------------------------ */
interface Rule {
  code: string
  name: string
  geometryType?: string
  ruleGroup?: string
}

/** 兜底规则（接口失败时使用，19 条中文规则名） */
const FALLBACK_RULES: Rule[] = [
  { code: 'R01', name: '不能有重叠', geometryType: 'Polygon' },
  { code: 'R02', name: '不能有缝隙', geometryType: 'Polygon' },
  { code: 'R03', name: '不能有多余点', geometryType: 'Polygon' },
  { code: 'R04', name: '不能自相交', geometryType: 'Polygon' },
  { code: 'R05', name: '不能自重叠', geometryType: 'Polygon' },
  { code: 'R06', name: '必须被其他要素覆盖', geometryType: 'Polygon' },
  { code: 'R07', name: '必须被其他要素包含', geometryType: 'Polygon' },
  { code: 'R08', name: '不能与其他要素重叠', geometryType: 'Polygon' },
  { code: 'R09', name: '必须完全覆盖其他要素', geometryType: 'Polygon' },
  { code: 'R10', name: '不能有悬挂点', geometryType: 'LineString' },
  { code: 'R11', name: '不能有伪节点', geometryType: 'LineString' },
  { code: 'R12', name: '不能自重叠（线）', geometryType: 'LineString' },
  { code: 'R13', name: '不能自相交（线）', geometryType: 'LineString' },
  { code: 'R14', name: '不能与其他要素重叠（线）', geometryType: 'LineString' },
  { code: 'R15', name: '必须与其他要素相接', geometryType: 'LineString' },
  { code: 'R16', name: '必须被其他要素覆盖（线）', geometryType: 'LineString' },
  { code: 'R17', name: '点必须位于线内', geometryType: 'Point' },
  { code: 'R18', name: '点必须位于面内', geometryType: 'Point' },
  { code: 'R19', name: '必须与节点重合', geometryType: 'Point' },
]

const rules = ref<Rule[]>([])
const rulesLoading = ref(false)
const selectedRules = ref<string[]>([])

const GEO_LABEL: Record<string, string> = {
  Point: '点',
  LineString: '线',
  Polygon: '面',
  MultiPoint: '点',
  MultiLineString: '线',
  MultiPolygon: '面',
}

/** 按 geometryType / ruleGroup 分组 */
const groupedRules = computed(() => {
  const map = new Map<string, Rule[]>()
  for (const r of rules.value) {
    const k = GEO_LABEL[String(r.geometryType)] || r.ruleGroup || '通用'
    if (!map.has(k)) map.set(k, [])
    map.get(k)!.push(r)
  }
  return Array.from(map.entries()).map(([name, items]) => ({ name, items }))
})

async function loadRules() {
  rulesLoading.value = true
  try {
    const res = await topologyApi.rules()
    const list = pickList<any>(res)
    rules.value = (list.length ? list : FALLBACK_RULES).map((r: any) => ({
      code: String(r.code ?? r.ruleCode ?? r.id ?? ''),
      name: String(r.name ?? r.ruleName ?? r.label ?? r.code ?? ''),
      geometryType: r.geometryType ?? r.geometry ?? undefined,
      ruleGroup: r.ruleGroup ?? r.group ?? undefined,
    }))
  } catch {
    rules.value = FALLBACK_RULES
  } finally {
    rulesLoading.value = false
  }
}
function selectAllRules() {
  selectedRules.value = rules.value.map((r) => r.code)
}
function clearRules() {
  selectedRules.value = []
}

/* ------------------------------ 参数表单 ------------------------------ */
const form = reactive<{ layerId: string; srid: number; tolerance: number }>({
  layerId: '',
  srid: 4326,
  tolerance: 0.00001,
})
const SYNC_DEFAULT_TOLERANCE = 0.001

function buildBody() {
  return {
    layerId: form.layerId,
    rules: selectedRules.value.slice(),
    srid: Number(form.srid) || 4326,
    tolerance: Number(form.tolerance),
  }
}

/* ------------------------------ 拓扑状态 ------------------------------ */
const statusLoading = ref(false)
const topoStatus = ref<any>(null)
async function loadStatus() {
  if (!form.layerId) {
    topoStatus.value = null
    return
  }
  statusLoading.value = true
  try {
    topoStatus.value = await topologyApi.status(form.layerId)
  } catch {
    topoStatus.value = null
  } finally {
    statusLoading.value = false
  }
}
function onLayerChange() {
  loadStatus()
}
function statusTagType(s: any): string {
  const v = String(s ?? '').toUpperCase()
  if (['1', 'SUCCESS', 'OK', 'VALID', '正常', '有效'].includes(v)) return 'green'
  if (['0', 'NONE', '未创建', '无'].includes(v) || !v) return 'gray'
  if (['FAILED', 'INVALID', 'ERROR', '异常', '无效'].includes(v)) return 'red'
  return 'blue'
}
function statusText(): string {
  if (topoStatus.value == null) return '未知'
  if (typeof topoStatus.value === 'string') return topoStatus.value
  return String(topoStatus.value.status ?? topoStatus.value.state ?? topoStatus.value.topologyName ?? '已返回')
}
const statusDetail = computed<string>(() => {
  const s = topoStatus.value
  if (!s || typeof s === 'string') return ''
  const parts: string[] = []
  if (s.topologyName) parts.push(`拓扑：${s.topologyName}`)
  if (s.srid) parts.push(`SRID：${s.srid}`)
  if (s.tolerance != null) parts.push(`容差：${s.tolerance}`)
  if (s.layerCount != null) parts.push(`要素图层：${s.layerCount}`)
  return parts.join('　')
})

/* ------------------------------ 报告渲染 ------------------------------ */
const reportRows = ref<any[]>([])
const reportRaw = ref<any>(null)
function renderReport(res: any) {
  reportRaw.value = res
  let list: any[] = []
  if (Array.isArray(res)) list = res
  else if (res && typeof res === 'object') {
    list = pickList<any>(res)
    if (!list.length) list = res.violations ?? res.items ?? res.errors ?? res.records ?? []
  }
  reportRows.value = Array.isArray(list) ? list : []
}
function reportRowId(r: any): string {
  return String(r?.id ?? r?.gid ?? r?.featureId ?? r?.fid ?? '-')
}
function reportRule(r: any): string {
  return String(r?.ruleName ?? r?.rule ?? r?.ruleCode ?? r?.code ?? '-')
}
function reportMsg(r: any): string {
  return String(r?.message ?? r?.description ?? r?.msg ?? r?.error ?? '-')
}
function reportGeom(r: any): string {
  return String(r?.wkt ?? r?.geometry ?? r?.geom ?? '-')
}

/* ------------------------------ 异步检查 ------------------------------ */
const reportVisible = ref(false)
const reportStatus = ref<'processing' | 'failed' | 'done'>('processing')
const checkTaskId = ref('')
let reportTimer: ReturnType<typeof setInterval> | null = null

function clearReportTimer() {
  if (reportTimer) {
    clearInterval(reportTimer)
    reportTimer = null
  }
}
async function runAsync() {
  if (!form.layerId) {
    ElMessage.warning('请选择目标图层')
    return
  }
  if (!selectedRules.value.length) {
    ElMessage.warning('请至少勾选一条拓扑规则')
    return
  }
  try {
    const taskId = await topologyApi.check(buildBody())
    checkTaskId.value = String(taskId ?? '')
    reportStatus.value = 'processing'
    reportRows.value = []
    reportRaw.value = null
    reportVisible.value = true
    clearReportTimer()
    reportTimer = setInterval(pollReport, 2000)
    pollReport()
  } catch (e: any) {
    showError(e, '异步检查提交失败')
  }
}
async function pollReport() {
  if (!checkTaskId.value) return
  try {
    const res = await topologyApi.checkReport(checkTaskId.value)
    // 后端返回 JSON 字符串：processing / failed / 报告对象
    if (typeof res === 'string') {
      const v = res.trim()
      if (v === 'processing' || v.includes('"processing"')) return
      if (v === 'failed' || v.includes('"failed"')) {
        reportStatus.value = 'failed'
        clearReportTimer()
        ElMessage.error('拓扑检查失败')
        return
      }
      try {
        renderReport(JSON.parse(v))
      } catch {
        renderReport(res)
      }
      reportStatus.value = 'done'
      clearReportTimer()
      return
    }
    renderReport(res)
    reportStatus.value = 'done'
    clearReportTimer()
  } catch {
    /* 轮询失败静默，等待下一次 */
  }
}
function closeReport() {
  clearReportTimer()
  reportVisible.value = false
}

/* ------------------------------ 同步检查 ------------------------------ */
const syncLoading = ref(false)
const syncRows = ref<any[]>([])
const syncRaw = ref<any>(null)
const syncPage = reactive({ pageIndex: 1, pageSize: 10, total: 0 })
const syncDone = ref(false)
async function runSync() {
  if (!form.layerId) {
    ElMessage.warning('请选择目标图层')
    return
  }
  syncLoading.value = true
  try {
    const res = await topologyApi.checkSync(buildBody(), syncPage.pageIndex, syncPage.pageSize)
    renderReport(res)
    syncRows.value = reportRows.value
    syncRaw.value = res
    syncPage.total = syncRows.value.length || Number(res?.totalCount ?? 0)
    syncDone.value = true
  } catch (e: any) {
    showError(e, '同步检查失败')
  } finally {
    syncLoading.value = false
  }
}
function syncPageChange(i: number) {
  syncPage.pageIndex = i
  runSync()
}

/* ------------------------------ 拓扑管理动作 ------------------------------ */
interface ActionField {
  key: string
  label: string
  def: any
}
interface Action {
  title: string
  tip: string
  fields: ActionField[]
  submit: (f: Record<string, any>) => Promise<any>
}
const ACTIONS: Record<string, Action> = {
  create: {
    title: '创建拓扑',
    tip: 'POST /topology/create',
    fields: [
      { key: 'topologyName', label: '拓扑名称', def: '' },
      { key: 'srid', label: 'SRID', def: 4326 },
      { key: 'tolerance', label: '容差', def: 0.00001 },
    ],
    submit: (f) => topologyApi.create(String(f.topologyName), Number(f.srid), Number(f.tolerance)),
  },
  import: {
    title: '导入要素',
    tip: 'POST /topology/import',
    fields: [
      { key: 'topologyName', label: '拓扑名称', def: '' },
      { key: 'featureTable', label: '要素表名', def: '' },
    ],
    submit: (f) => topologyApi.importFeatures(String(f.topologyName), String(f.featureTable)),
  },
  addColumn: {
    title: '添加拓扑列',
    tip: 'POST /topology/add-topo-column',
    fields: [
      { key: 'topologyName', label: '拓扑名称', def: '' },
      { key: 'tableName', label: '表名', def: '' },
      { key: 'schemaName', label: '模式 schema', def: 'public' },
      { key: 'geometryColumn', label: '几何列', def: 'geom' },
      { key: 'columnName', label: '拓扑列名', def: 'topo_geom' },
    ],
    submit: (f) => topologyApi.addTopoColumn(f as any),
  },
  populate: {
    title: '填充拓扑数据',
    tip: 'POST /topology/populate',
    fields: [
      { key: 'topologyName', label: '拓扑名称', def: '' },
      { key: 'tableName', label: '表名', def: '' },
      { key: 'geometryColumn', label: '几何列', def: 'geom' },
      { key: 'topoGeometryColumn', label: '拓扑几何列', def: 'topo_geom' },
      { key: 'condition', label: '条件 where', def: '' },
      { key: 'tolerance', label: '容差', def: 0.00001 },
    ],
    submit: (f) => topologyApi.populate(f as any),
  },
}

const actVisible = ref(false)
const actBusy = ref(false)
const actKey = ref<string>('create')
const actForm = reactive<Record<string, any>>({})
const actConf = computed<Action>(() => ACTIONS[actKey.value])

function openAction(key: string) {
  actKey.value = key
  const conf = ACTIONS[key]
  Object.keys(actForm).forEach((k) => delete actForm[k])
  conf.fields.forEach((f) => (actForm[f.key] = f.def))
  actVisible.value = true
}
async function submitAction() {
  actBusy.value = true
  try {
    const res = await actConf.value.submit({ ...actForm })
    ElMessage.success(typeof res === 'string' && res ? res : '操作成功')
    actVisible.value = false
    await loadStatus()
  } catch (e: any) {
    showError(e, '操作失败')
  } finally {
    actBusy.value = false
  }
}

/** 删除拓扑（二次确认） */
async function removeTopology() {
  if (!form.layerId) {
    ElMessage.warning('请先选择目标图层')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除该图层的拓扑？（LayerId: ${form.layerId}）`, '删除拓扑', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await topologyApi.remove(form.layerId)
    ElMessage.success('拓扑已删除')
    await loadStatus()
  } catch (e: any) {
    showError(e, '删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadLayers(), loadRules()])
})

onBeforeUnmount(clearReportTimer)
</script>

<template>
  <div>
    <PageHead title="拓扑检查" sub="检查空间数据的拓扑关系，定位重叠、缝隙和几何错误">
      <template #tools>
        <button class="btn sm" @click="loadRules"><Icon name="refresh" :size="13" />刷新规则</button>
        <button class="btn sm" @click="loadStatus"><Icon name="eye" :size="13" />刷新状态</button>
      </template>
    </PageHead>

    <!-- 拓扑状态条 -->
    <div class="card mb8">
      <div class="card-b row" style="align-items: center; gap: 10px">
        <Icon name="warn" :size="15" />
        <span class="muted" style="font-size: 12px">当前图层拓扑状态</span>
        <span class="tag" :class="statusTagType(topoStatus)">{{ statusText() }}</span>
        <span class="muted" style="font-size: 11.5px">{{ statusDetail }}</span>
        <span v-if="statusLoading" class="muted" style="font-size: 11.5px; margin-left: auto">查询中…</span>
        <span v-else class="muted mono" style="font-size: 11px; margin-left: auto">/topology/status/{layerId}</span>
      </div>
    </div>

    <div class="row topo-wrap">
      <!-- 左：规则分组勾选 -->
      <div class="card col-rules">
        <div class="card-h">
          <Icon name="check" :size="14" />拓扑规则
          <span class="cnt">{{ selectedRules.length }}/{{ rules.length }}</span>
          <div class="tools">
            <button class="btn xs" @click="selectAllRules">全选</button>
            <button class="btn xs" @click="clearRules">清空</button>
          </div>
        </div>
        <div class="card-b rules-body" v-loading="rulesLoading">
          <div v-for="g in groupedRules" :key="g.name" class="rule-grp">
            <div class="rule-grp-h">{{ g.name }}（{{ g.items.length }}）</div>
            <el-checkbox-group v-model="selectedRules">
              <div v-for="r in g.items" :key="r.code" class="rule-item">
                <el-checkbox :value="r.code">
                  <span class="mono rule-code">{{ r.code }}</span>
                  <span class="rule-name">{{ r.name }}</span>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>
          <el-empty v-if="!rules.length" description="暂无规则" :image-size="60" />
        </div>
      </div>

      <!-- 右：参数表单与操作 -->
      <div class="col flex1">
        <div class="card">
          <div class="card-h"><Icon name="edit" :size="14" />检查参数</div>
          <div class="card-b">
            <el-form label-width="90px" size="small">
              <el-form-item label="目标图层">
                <el-select v-model="form.layerId" placeholder="请选择图层" filterable style="width: 100%" @change="onLayerChange">
                  <el-option v-for="l in layerOptions" :key="rowId(l)" :label="rowName(l)" :value="rowId(l)" />
                </el-select>
              </el-form-item>
              <el-form-item label="SRID">
                <el-input-number v-model="form.srid" :controls="false" style="width: 200px" />
                <span class="muted" style="margin-left: 8px; font-size: 11.5px">默认 4326</span>
              </el-form-item>
              <el-form-item label="容差 tolerance">
                <el-input-number v-model="form.tolerance" :controls="false" :precision="6" style="width: 200px" />
                <span class="muted" style="margin-left: 8px; font-size: 11.5px">异步默认 0.00001</span>
              </el-form-item>
            </el-form>

            <el-alert
              type="warning"
              :closable="false"
              show-icon
              title="注意：同步检查默认容差为 0.001，与异步的 0.00001 不同；同步调用时请按需调整上方容差。"
            />

            <div class="row mt8" style="gap: 8px">
              <button class="btn primary" @click="runAsync"><Icon name="play" :size="13" />执行检查（异步）</button>
              <button class="btn" :disabled="syncLoading" @click="runSync">
                <Icon name="play" :size="13" />{{ syncLoading ? '执行中…' : '执行检查（同步）' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 拓扑管理 -->
        <div class="card">
          <div class="card-h"><Icon name="gear" :size="14" />拓扑管理</div>
          <div class="card-b row wrap" style="gap: 8px">
            <button class="btn sm" @click="openAction('create')"><Icon name="plus" :size="13" />创建拓扑</button>
            <button class="btn sm" @click="openAction('import')"><Icon name="upload" :size="13" />导入要素</button>
            <button class="btn sm" @click="openAction('addColumn')"><Icon name="grid" :size="13" />添加拓扑列</button>
            <button class="btn sm" @click="openAction('populate')"><Icon name="db" :size="13" />填充拓扑数据</button>
            <button class="btn sm danger" @click="removeTopology"><Icon name="trash" :size="13" />删除拓扑</button>
          </div>
        </div>

        <!-- 同步结果 -->
        <div v-if="syncDone" class="card">
          <div class="card-h">
            <Icon name="list" :size="14" />同步检查结果
            <span class="cnt">{{ syncPage.total }}</span>
            <div class="tools">
              <button class="btn xs" @click="copyText(JSON.stringify(syncRaw))"><Icon name="copy" :size="12" />复制原始</button>
            </div>
          </div>
          <div class="card-b">
            <el-table :data="syncRows" size="small" border>
              <el-table-column label="要素 ID" width="120"><template #default="{ row }">{{ reportRowId(row) }}</template></el-table-column>
              <el-table-column label="规则" width="150"><template #default="{ row }">{{ reportRule(row) }}</template></el-table-column>
              <el-table-column label="描述" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ reportMsg(row) }}</template></el-table-column>
              <el-table-column label="几何" min-width="200" show-overflow-tooltip><template #default="{ row }"><span class="mono">{{ reportGeom(row) }}</span></template></el-table-column>
              <template #empty>检查通过，未发现违规要素</template>
            </el-table>
            <div class="row mt8" style="justify-content: flex-end">
              <el-pagination
                layout="total, prev, pager, next"
                :total="syncPage.total"
                :page-size="syncPage.pageSize"
                :current-page="syncPage.pageIndex"
                @current-change="syncPageChange"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 异步报告弹窗 -->
    <el-dialog v-model="reportVisible" title="拓扑检查报告" width="720px" :close-on-click-modal="false" @close="closeReport">
      <div class="row mb8" style="align-items: center; gap: 10px">
        <span class="muted" style="font-size: 12px">任务 ID</span>
        <span class="mono">{{ checkTaskId || '-' }}</span>
        <span class="tag" :class="reportStatus === 'done' ? 'green' : reportStatus === 'failed' ? 'red' : 'blue'">
          {{ reportStatus === 'done' ? '检查完成' : reportStatus === 'failed' ? '检查失败' : '检查中…' }}
        </span>
      </div>
      <el-progress v-if="reportStatus === 'processing'" :percentage="100" :indeterminate="true" :duration="3" :show-text="false" />

      <el-table v-if="reportStatus === 'done'" :data="reportRows" size="small" border>
        <el-table-column label="要素 ID" width="120"><template #default="{ row }">{{ reportRowId(row) }}</template></el-table-column>
        <el-table-column label="规则" width="150"><template #default="{ row }">{{ reportRule(row) }}</template></el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ reportMsg(row) }}</template></el-table-column>
        <el-table-column label="几何" min-width="200" show-overflow-tooltip><template #default="{ row }"><span class="mono">{{ reportGeom(row) }}</span></template></el-table-column>
        <template #empty>检查通过，未发现违规要素</template>
      </el-table>

      <template #footer>
        <button class="btn sm" @click="copyText(JSON.stringify(reportRaw))"><Icon name="copy" :size="13" />复制原始报告</button>
        <button class="btn sm primary" @click="closeReport">关闭</button>
      </template>
    </el-dialog>

    <!-- 拓扑管理动作弹窗 -->
    <el-dialog v-model="actVisible" :title="actConf.title" width="480px">
      <el-form label-width="110px" size="small">
        <el-form-item v-for="f in actConf.fields" :key="f.key" :label="f.label">
          <el-input v-if="typeof f.def === 'string'" v-model="actForm[f.key]" />
          <el-input-number v-else v-model="actForm[f.key]" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div class="muted" style="font-size: 11.5px">{{ actConf.tip }}</div>
      <template #footer>
        <button class="btn sm" @click="actVisible = false">取消</button>
        <button class="btn sm primary" :disabled="actBusy" @click="submitAction">{{ actBusy ? '提交中…' : '提交' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.topo-wrap {
  align-items: flex-start;
}
.col-rules {
  flex: 0 0 300px;
}
.rules-body {
  max-height: 620px;
  overflow: auto;
}
.rule-grp + .rule-grp {
  margin-top: 10px;
}
.rule-grp-h {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-3);
  letter-spacing: 1px;
  padding: 4px 2px;
}
.rule-item {
  display: block;
  margin: 1px 0;
}
.rule-item .rule-code {
  display: inline-block;
  min-width: 34px;
  color: var(--ink-3);
  font-size: 11px;
}
.rule-name {
  font-size: 12.5px;
}
</style>
