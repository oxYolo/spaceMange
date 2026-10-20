<script setup lang="ts">
/**
 * 任务中心（tasks）
 * 接口依据：LayerToolController — GET /layerTool/getList?pageSize&pageIndex&origin
 *  - GET  /layerTool/getList?pageSize&pageIndex&origin   任务分页（origin 为空取全部）
 *  - GET  /layerTool/storageLayer?id&targetId            成果入库
 *  - GET  /layerTool/delete?id                           删除任务记录
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { layerToolApi, dataTypeApi } from '@/api'
import { fmtTime, pickList, pickTotal, ORIGIN_NAME } from '@/utils/format'
import { onImMessage, imEnabled, type ImMessage } from '@/utils/im'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const USER_NAME = userStore.userName || 'admin'

/* ------------------------------ 通用 ------------------------------ */
function rowId(r: any): string {
  return String(r?.id ?? r?.taskId ?? '')
}
function rowLayer(r: any): string {
  return String(r?.layerName ?? r?.taskName ?? r?.name ?? '-')
}
function rowOrigin(r: any): string {
  return ORIGIN_NAME[String(r?.origin)] || (r?.origin != null ? `origin=${r.origin}` : '-')
}
function rowCreator(r: any): string {
  return String(r?.createBy ?? r?.user ?? r?.username ?? '-')
}
function rowTime(r: any): string {
  return fmtTime(r?.createDT ?? r?.createTime ?? r?.createDate)
}

/** 任务状态归一：兼容中英文 / 数字 */
function statusInfo(s: any): { label: string; type: string; key: 'wait' | 'run' | 'done' | 'fail' } {
  const v = String(s ?? '').trim()
  const up = v.toUpperCase()
  const doneSet = ['1', 'SUCCESS', 'FINISH', 'FINISHED', 'DONE', 'COMPLETE', 'COMPLETED']
  const failSet = ['-1', 'FAIL', 'FAILED', 'ERROR', 'EXCEPTION']
  const runSet = ['RUNNING', 'PROCESSING', 'DOING', 'EXECUTING']
  const waitSet = ['WAIT', 'WAITING', 'PENDING', 'QUEUE', 'QUEUED']
  if (doneSet.includes(up) || v === '成功' || v === '完成' || v === '已完成') return { label: '成功', type: 'green', key: 'done' }
  if (failSet.includes(up) || v === '失败' || v === '异常') return { label: '失败', type: 'red', key: 'fail' }
  if (runSet.includes(up) || v === '进行中' || v === '处理中' || v === '运行中') return { label: '进行中', type: 'blue', key: 'run' }
  if (waitSet.includes(up) || v === '等待' || v === '排队' || v === '待处理' || v === '未开始')
    return { label: '等待', type: 'orange', key: 'wait' }
  return { label: v || '未知', type: 'gray', key: 'wait' }
}
function taskPercent(r: any): number {
  const st = statusInfo(r?.status)
  if (st.key === 'done') return 100
  const n = Number(r?.progress ?? r?.percent ?? 0)
  if (Number.isNaN(n)) return 0
  return Math.max(0, Math.min(100, Math.round(n)))
}

/* ------------------------------ 数据加载 ------------------------------ */
const list = ref<any[]>([])
const loading = ref(false)
const page = reactive({ pageSize: 10, pageIndex: 1, total: 0 })
const originFilter = ref<string>('') // '' = 全部

const originChips = computed(() =>
  Object.keys(ORIGIN_NAME).map((k) => ({ value: k, label: `${k} · ${ORIGIN_NAME[k]}` })),
)

async function loadTasks() {
  loading.value = true
  try {
    const res = await layerToolApi.getList(page.pageSize, page.pageIndex, originFilter.value || undefined)
    list.value = pickList<any>(res)
    page.total = pickTotal(res, list.value.length)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务列表失败')
  } finally {
    loading.value = false
  }
}
function pickOrigin(v: string) {
  originFilter.value = v
  page.pageIndex = 1
  loadTasks()
}
function onPageChange(i: number) {
  page.pageIndex = i
  loadTasks()
}

/* ------------------------------ 自动刷新 ------------------------------ */
const autoRefresh = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

function clearTimer() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}
function toggleAuto(v: boolean | string | number) {
  autoRefresh.value = Boolean(v)
  clearTimer()
  // IM 实时推送可用时自动刷新降频为 15s 兜底；不可用维持 5s
  if (v) refreshTimer = setInterval(loadTasks, imEnabled() ? 15000 : 5000)
}

/* ------------------------------ 成果入库 ------------------------------ */
const storageVisible = ref(false)
const storageRow = ref<any>(null)
const storageTargetId = ref('')
const storageTargets = ref<any[]>([])

function flattenTypes(nodes: any[], out: any[] = []): any[] {
  for (const n of nodes || []) {
    out.push(n)
    if (n.children?.length) flattenTypes(n.children, out)
  }
  return out
}
async function loadStorageTargets() {
  try {
    storageTargets.value = flattenTypes(pickList<any>(await dataTypeApi.getAllList()))
  } catch {
    storageTargets.value = []
  }
}
async function openStorage(row: any) {
  storageRow.value = row
  storageTargetId.value = ''
  storageVisible.value = true
  if (!storageTargets.value.length) await loadStorageTargets()
}
async function confirmStorage() {
  if (!storageTargetId.value) {
    ElMessage.warning('请选择目标目录')
    return
  }
  try {
    await layerToolApi.storageLayer(rowId(storageRow.value), storageTargetId.value)
    ElMessage.success('成果已入库')
    storageVisible.value = false
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '成果入库失败')
  }
}

/* ------------------------------ 查看 / 删除 ------------------------------ */
const viewVisible = ref(false)
const viewRow = ref<any>(null)
function openView(row: any) {
  viewRow.value = row
  viewVisible.value = true
}
async function removeRow(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除该任务记录？（ID: ${rowId(row)}）`, '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await layerToolApi.delete(rowId(row))
    ElMessage.success('已删除')
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

let imUnsub: (() => void) | null = null
let imRefreshTimer: ReturnType<typeof setTimeout> | null = null

onMounted(async () => {
  await Promise.all([loadTasks(), loadStorageTargets()])
  // IM 实时事件（三维转换/拓扑检查/二维入库）：节流静默刷新，与自动刷新互补
  imUnsub = onImMessage(USER_NAME, (msg: ImMessage) => {
    if (!['Model3dConvert', 'TopologyCheck', 'layerInsertLog'].includes(msg.moduleType)) return
    if (imRefreshTimer) return
    imRefreshTimer = setTimeout(() => {
      imRefreshTimer = null
      loadTasks()
    }, 1200)
  })
})

onBeforeUnmount(() => {
  clearTimer()
  if (imRefreshTimer) clearTimeout(imRefreshTimer)
  imUnsub?.()
})
</script>

<template>
  <div>
    <PageHead title="任务中心" sub="接口依据：LayerTool getList">
      <template #tools>
        <button class="btn sm" @click="loadTasks"><Icon name="refresh" :size="13" />刷新</button>
        <span class="muted" style="font-size: 12px">自动刷新(5s)</span>
        <el-switch v-model="autoRefresh" size="small" @change="toggleAuto" />
      </template>
    </PageHead>

    <!-- origin 过滤 -->
    <div class="filters">
      <div class="chips">
        <span class="chip" :class="{ on: originFilter === '' }" @click="pickOrigin('')">全部</span>
        <span
          v-for="c in originChips"
          :key="c.value"
          class="chip"
          :class="{ on: originFilter === c.value }"
          @click="pickOrigin(c.value)"
        >
          {{ c.label }}
        </span>
      </div>
    </div>

    <div class="card">
      <div class="card-h">
        <Icon name="list" :size="14" />任务列表
        <span class="cnt">{{ page.total }}</span>
      </div>
      <div class="card-b">
        <el-table :data="list" v-loading="loading" size="small" border>
          <el-table-column label="任务名 / 工具" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ rowLayer(row) }}</template>
          </el-table-column>
          <el-table-column label="所属图层" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ rowLayer(row) }}</template>
          </el-table-column>
          <el-table-column label="工具类型" width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="tag violet">{{ rowOrigin(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span class="tag" :class="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</span>
            </template>
          </el-table-column>
          <el-table-column label="进度" width="160">
            <template #default="{ row }">
              <el-progress :percentage="taskPercent(row)" :status="statusInfo(row.status).key === 'fail' ? 'exception' : undefined" />
            </template>
          </el-table-column>
          <el-table-column label="创建人" width="110">
            <template #default="{ row }">{{ rowCreator(row) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ rowTime(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <button class="btn xs" @click="openStorage(row)"><Icon name="db" :size="12" />成果入库</button>
              <button class="btn xs" @click="openView(row)"><Icon name="eye" :size="12" />查看</button>
              <button class="btn xs danger" @click="removeRow(row)"><Icon name="trash" :size="12" />删除</button>
            </template>
          </el-table-column>
          <template #empty>暂无任务</template>
        </el-table>

        <div class="row mt8" style="justify-content: flex-end">
          <el-pagination
            layout="total, prev, pager, next"
            :total="page.total"
            :page-size="page.pageSize"
            :current-page="page.pageIndex"
            @current-change="onPageChange"
          />
        </div>
      </div>
    </div>

    <!-- 成果入库 -->
    <el-dialog v-model="storageVisible" title="成果入库" width="460px">
      <el-form label-width="90px" size="small">
        <el-form-item label="任务">
          <span class="mono">{{ rowId(storageRow) }}　{{ storageRow ? rowLayer(storageRow) : '' }}</span>
        </el-form-item>
        <el-form-item label="目标目录">
          <el-select v-model="storageTargetId" placeholder="请选择目标目录" filterable style="width: 100%">
            <el-option v-for="t in storageTargets" :key="rowId(t)" :label="rowLayer(t)" :value="rowId(t)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn sm" @click="storageVisible = false">取消</button>
        <button class="btn sm primary" @click="confirmStorage">确认入库</button>
      </template>
    </el-dialog>

    <!-- 任务详情 -->
    <el-dialog v-model="viewVisible" title="任务详情" width="520px" @closed="viewRow = null">
      <div v-if="viewRow" class="kv">
        <span class="k">任务 ID</span><span class="v mono">{{ rowId(viewRow) }}</span>
        <span class="k">任务名</span><span class="v">{{ rowLayer(viewRow) }}</span>
        <span class="k">工具类型</span><span class="v">{{ rowOrigin(viewRow) }}</span>
        <span class="k">状态</span>
        <span class="v"><span class="tag" :class="statusInfo(viewRow.status).type">{{ statusInfo(viewRow.status).label }}</span></span>
        <span class="k">进度</span><span class="v">{{ taskPercent(viewRow) }}%</span>
        <span class="k">创建人</span><span class="v">{{ rowCreator(viewRow) }}</span>
        <span class="k">创建时间</span><span class="v">{{ rowTime(viewRow) }}</span>
      </div>
      <template #footer>
        <button class="btn sm" @click="viewVisible = false">关闭</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filters {
  padding-bottom: 10px;
}
</style>
