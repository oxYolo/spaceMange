<script setup lang="ts">
import { showError } from '@/utils/feedback'
/**
 * 工具箱（tools）
 * 接口依据：LayerToolController(13)
 *  - GET  /layerTool/getList?pageSize&pageIndex&origin   任务列表 / 轮询
 *  - GET  /layerTool/storageLayer?id&targetId            成果入库
 *  - GET  /layerTool/getTifInfo?layerId                  栅格元信息（读极值）
 *  - POST /layerTool/{cutLayer|resample|tifToShp|shpToTif|reclassification|
 *                     eraseVectorLayer|bufferZone|intersectionAndUnion|overlayCalculation}
 *  统一请求体 LayerToolDto：{ layerId, layerName, detailJson, createBy, 各工具专属dto }
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { layerContentApi, layerToolApi, dataTypeApi, dataStyleApi } from '@/api'
import { fmtTime, pickList, ORIGIN_NAME } from '@/utils/format'
import { copyText as clipboardCopy } from '@/utils/clipboard'
import { useUserStore } from '@/auth/store'

/* ------------------------------ 通用 ------------------------------ */
const userStore = useUserStore()
const USER = userStore.userName

function rowId(r: any): string {
  return String(r?.id ?? r?.taskId ?? r?.layerId ?? r?.layerID ?? '')
}
function rowName(r: any): string {
  return String(r?.layerName ?? r?.name ?? r?.taskName ?? '-')
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

async function copyText(text?: string) {
  const t = (text ?? '').trim()
  if (!t) return
  const ok = await clipboardCopy(t)
  if (ok) ElMessage.success('已复制')
  else ElMessage.error('复制失败')
}

/* ------------------------------ 图层与目录 ------------------------------ */
const layerOptions = ref<any[]>([])
const storageTargets = ref<any[]>([])

async function loadLayers() {
  try {
    const res = await layerContentApi.getPageListByName({ createBy: USER }, 50, 1)
    layerOptions.value = pickList<any>(res)
  } catch (e: any) {
    showError(e, '加载图层下拉失败')
  }
}
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

/* ------------------------------ 算法定义 ------------------------------ */
interface AlgoField {
  key: string
  label: string
  type: 'number' | 'text' | 'select'
  def?: any
  options?: { label: string; value: any }[]
  placeholder?: string
}
interface Algo {
  key: string
  name: string
  origin: number
  kind: 'simple' | 'inter' | 'overlay' | 'reclass'
  desc: string
  fields: AlgoField[]
}

const RESAMPLE_METHODS = ['near', 'bilinear', 'cubic', 'cubicspline', 'lanczos', 'average', 'mode', 'max', 'min', 'med'].map(
  (v) => ({ label: v, value: v }),
)

const GROUPS: { name: string; items: Algo[] }[] = [
  {
    name: '矢量分析',
    items: [
      {
        key: 'cutLayer',
        name: '图层裁剪',
        origin: 1,
        kind: 'simple',
        desc: '用裁剪范围图层/自定义 WKT 裁剪目标图层',
        fields: [
          { key: 'clipLayerId', label: '裁剪范围图层', type: 'select' },
          { key: 'wkt', label: '自定义 WKT', type: 'text', placeholder: '可选，如 POLYGON((…))' },
        ],
      },
      {
        key: 'eraseVectorLayer',
        name: '矢量擦除',
        origin: 6,
        kind: 'simple',
        desc: '用擦除图层从目标图层中扣除重叠部分',
        fields: [{ key: 'eraseLayerId', label: '擦除图层', type: 'select' }],
      },
      {
        key: 'bufferZone',
        name: '缓冲区',
        origin: 7,
        kind: 'simple',
        desc: '按距离生成缓冲面',
        fields: [
          { key: 'distance', label: '缓冲距离(m)', type: 'number', def: 100 },
          { key: 'dissolve', label: '融合(dissolve)', type: 'select', def: false, options: [{ label: '否', value: false }, { label: '是', value: true }] },
        ],
      },
      {
        key: 'intersectionAndUnion',
        name: '矢量交并补',
        origin: 8,
        kind: 'inter',
        desc: '两图层求交 / 并 / 对称差',
        fields: [],
      },
    ],
  },
  {
    name: '栅格分析',
    items: [
      {
        key: 'resample',
        name: '重采样',
        origin: 2,
        kind: 'simple',
        desc: '调整栅格行列数与像元大小（可读取极值自动填充）',
        fields: [
          { key: 'xsize', label: '列数 xsize', type: 'number', def: 0 },
          { key: 'ysize', label: '行数 ysize', type: 'number', def: 0 },
          { key: 'xCell', label: '像元 xCell(m)', type: 'number', def: 0 },
          { key: 'yCell', label: '像元 yCell(m)', type: 'number', def: 0 },
          { key: 'resamplingMethod', label: '重采样方法', type: 'select', def: 'near', options: RESAMPLE_METHODS },
        ],
      },
      {
        key: 'shpToTif',
        name: '矢量栅格化',
        origin: 4,
        kind: 'simple',
        desc: '矢量按属性字段转换为栅格',
        fields: [
          { key: 'attributeName', label: '属性字段', type: 'text' },
          { key: 'x', label: '起点 X', type: 'number', def: 0 },
          { key: 'y', label: '起点 Y', type: 'number', def: 0 },
          { key: 'height', label: '高度 height', type: 'number', def: 0 },
          { key: 'width', label: '宽度 width', type: 'number', def: 0 },
          {
            key: 'type',
            label: '尺寸类型',
            type: 'select',
            def: '1',
            options: [
              { label: '1 · 空间分辨率', value: '1' },
              { label: '2 · 网格大小', value: '2' },
            ],
          },
        ],
      },
      {
        key: 'tifToShp',
        name: '栅格矢量化',
        origin: 3,
        kind: 'simple',
        desc: '栅格按属性字段转换为矢量',
        fields: [{ key: 'attributeName', label: '属性字段', type: 'text' }],
      },
      {
        key: 'overlayCalculation',
        name: '栅格叠加计算',
        origin: 9,
        kind: 'overlay',
        desc: '按公式对多图层/数值做叠加运算',
        fields: [],
      },
    ],
  },
  {
    name: '拓扑与校验',
    items: [
      {
        key: 'reclassification',
        name: '重分类',
        origin: 5,
        kind: 'reclass',
        desc: '按分类区间或唯一值重映射栅格值（matrix 为 JSON 字符串）',
        fields: [
          {
            key: 'reclassType',
            label: '重分类方式',
            type: 'select',
            def: '1',
            options: [
              { label: '1 · 分类区间', value: '1' },
              { label: '2 · 唯一值', value: '2' },
            ],
          },
        ],
      },
    ],
  },
]

const ALL_ALGOS: Algo[] = GROUPS.flatMap((g) => g.items)
const algoMap = (k: string) => ALL_ALGOS.find((a) => a.key === k)!

/* ------------------------------ 当前算法与表单 ------------------------------ */
const currentKey = ref<string>('cutLayer')
const algo = computed<Algo>(() => algoMap(currentKey.value))
const form = reactive<Record<string, any>>({})
const interOp = ref<'intersection' | 'union' | 'symDifference'>('intersection')
const overlayFormulas = ref<{ dataType: 'layer' | 'number'; value: any }[]>([{ dataType: 'layer', value: '' }])
const matrixRows = ref<any[]>([])
const matrixVisible = ref(false)

function resetForm() {
  const a = algo.value
  Object.keys(form).forEach((k) => delete form[k])
  form.layerId = ''
  a.fields.forEach((f) => {
    form[f.key] = f.def ?? (f.type === 'number' ? 0 : '')
  })
  interOp.value = 'intersection'
  overlayFormulas.value = [{ dataType: 'layer', value: '' }]
  matrixRows.value = []
}

function selectAlgo(k: string) {
  currentKey.value = k
  resetForm()
}

/* ------------------------------ 读取极值（栅格） ------------------------------ */
const readingTif = ref(false)
async function readTifInfo() {
  if (!form.layerId) {
    ElMessage.warning('请先选择目标图层')
    return
  }
  readingTif.value = true
  try {
    const info = await layerToolApi.getTifInfo(form.layerId)
    if (!info) {
      ElMessage.warning('未读取到栅格信息')
      return
    }
    const cols = Number(info.xsize ?? info.width ?? info.cols ?? 0)
    const rows = Number(info.ysize ?? info.height ?? info.rows ?? 0)
    const xCell = Number(info.xCell ?? info.cellSizeX ?? 0)
    const yCell = Number(info.yCell ?? info.cellSizeY ?? 0)
    if (cols) form.xsize = cols
    if (rows) form.ysize = rows
    if (xCell) form.xCell = xCell
    if (yCell) form.yCell = yCell
    ElMessage.success('已读取栅格极值并填充')
  } catch (e: any) {
    showError(e, '读取栅格信息失败')
  } finally {
    readingTif.value = false
  }
}

/* ------------------------------ 重分类矩阵 ------------------------------ */
function openMatrix() {
  const t = String(form.reclassType ?? '1')
  if (!matrixRows.value.length) {
    matrixRows.value = t === '1' ? [{ min: '', max: '', value: '' }] : [{ old: '', value: '' }]
  }
  matrixVisible.value = true
}
function addMatrixRow() {
  matrixRows.value.push(String(form.reclassType) === '2' ? { old: '', value: '' } : { min: '', max: '', value: '' })
}
function confirmMatrix() {
  const t = String(form.reclassType)
  const data = matrixRows.value
    .filter((r) => t === '2' ? r.old !== '' : r.min !== '' || r.max !== '')
    .map((r) =>
      t === '2'
        ? { old: Number(r.old), value: Number(r.value) }
        : { min: Number(r.min), max: Number(r.max), value: Number(r.value) },
    )
  if (!data.length) {
    ElMessage.warning('请至少录入一行有效规则')
    return
  }
  form.matrix = JSON.stringify(data)
  matrixVisible.value = false
  ElMessage.success('矩阵已写入')
}

/* --------- 矩阵辅助：读取极值 / 自然断点 / 唯一值 / 选色带（原型重分类弹窗同款） --------- */
const matrixBusy = ref(false)
const rampOptions = ref<{ name: string; colors: string[] }[]>([])
const rampSel = ref('')

/** 读取极值：GET /dataStyle/getTifMaxMin → 按现有行数均分区间 */
async function matrixFromMaxMin() {
  if (!form.layerId) return ElMessage.warning('请先选择目标图层')
  matrixBusy.value = true
  try {
    const r: any = await dataStyleApi.getTifMaxMin(form.layerId)
    const min = Number(r?.min ?? 0)
    const max = Number(r?.max ?? 0)
    if (Number.isNaN(min) || Number.isNaN(max)) {
      ElMessage.error('后端未返回有效极值')
      return
    }
    const n = Math.max(2, matrixRows.value.length || 4)
    const step = (max - min) / n
    matrixRows.value = Array.from({ length: n }, (_, i) => ({
      min: (min + step * i).toFixed(2),
      max: (min + step * (i + 1)).toFixed(2),
      value: String(i + 1),
    }))
    ElMessage.success(`极值 [${min}, ${max}] 已均分为 ${n} 段`)
  } catch (e: any) {
    showError(e, '读取极值失败')
  } finally {
    matrixBusy.value = false
  }
}

/** 自然断点：GET /dataStyle/getTifJenksBreaks → 直接生成区间行 */
async function matrixFromJenks() {
  if (!form.layerId) return ElMessage.warning('请先选择目标图层')
  matrixBusy.value = true
  try {
    const n = Math.max(2, matrixRows.value.length || 4)
    const res: any = await dataStyleApi.getTifJenksBreaks(form.layerId, n)
    const list = pickList<any>(res)
    // 兼容 {min,max,value} 结构与断点值列表结构
    if (list.length && list[0]?.min !== undefined) {
      matrixRows.value = list.map((x: any, i: number) => ({
        min: x.min, max: x.max, value: String(x.value ?? i + 1),
      }))
    } else {
      const pts = list.map((x: any) => Number(x.value ?? x)).filter((v: number) => !Number.isNaN(v)).sort((a: number, b: number) => a - b)
      if (pts.length < 2) {
        ElMessage.error('断点数据不足')
        return
      }
      matrixRows.value = pts.slice(0, -1).map((p: number, i: number) => ({
        min: p, max: pts[i + 1], value: String(i + 1),
      }))
    }
    ElMessage.success('自然断点已生成')
  } catch (e: any) {
    showError(e, '读取自然断点失败')
  } finally {
    matrixBusy.value = false
  }
}

/** 唯一值：GET /dataStyle/getTifValues → 切换为 type=2 行 */
async function matrixFromValues() {
  if (!form.layerId) return ElMessage.warning('请先选择目标图层')
  matrixBusy.value = true
  try {
    const res: any = await dataStyleApi.getTifValues(form.layerId)
    const list = pickList<any>(res).map((x: any) => Number(x.value ?? x)).filter((v: number) => !Number.isNaN(v))
    if (!list.length) {
      ElMessage.error('未取到唯一值（像元值过大时后端不支持）')
      return
    }
    form.reclassType = '2'
    matrixRows.value = list.slice(0, 30).map((v: number, i: number) => ({ old: String(v), value: String(i + 1) }))
    ElMessage.success(`唯一值已载入 ${matrixRows.value.length} 行`)
  } catch (e: any) {
    showError(e, '读取唯一值失败')
  } finally {
    matrixBusy.value = false
  }
}

/** 选色带：GET /dataStyle/GetLinearColorRamps → 应用到矩阵行预览色 */
async function loadRampOptions() {
  if (rampOptions.value.length) return
  try {
    rampOptions.value = pickList<any>(await dataStyleApi.getLinearColorRamps()).map((r: any) => ({
      name: r.colorName ?? r.name ?? '-',
      colors: r.colors ?? r.colorList ?? [],
    })).filter((r: any) => r.colors.length)
  } catch {
    rampOptions.value = []
  }
}
function applyRamp() {
  const r = rampOptions.value.find((x) => x.name === rampSel.value)
  if (!r || !r.colors.length) {
    ElMessage.warning('请先选择色带')
    return
  }
  const n = matrixRows.value.length
  matrixRows.value.forEach((row, i) => {
    ;(row as any)._color = r.colors[Math.min(r.colors.length - 1, Math.round((i / Math.max(1, n - 1)) * (r.colors.length - 1)))]
  })
  ElMessage.success(`色带「${r.name}」已应用到 ${n} 行预览`)
}

/* ------------------------------ 运行任务 ------------------------------ */
function buildDto(): Record<string, any> {
  const a = algo.value
  switch (a.key) {
    case 'intersectionAndUnion':
      return { layerIds: [form.layerId, form.layerIdB], operationType: interOp.value }
    case 'overlayCalculation':
      return { formulaList: overlayFormulas.value.filter((f) => f.value !== '' && f.value !== null) }
    case 'reclassification':
      return { type: form.reclassType, matrix: form.matrix ?? '[]' }
    default: {
      const dto: Record<string, any> = {}
      a.fields.forEach((f) => (dto[f.key] = form[f.key]))
      return dto
    }
  }
}

const running = ref(false)
async function runTask() {
  const a = algo.value
  if (!form.layerId) {
    ElMessage.warning('请选择目标图层')
    return
  }
  if (a.key === 'intersectionAndUnion' && !form.layerIdB) {
    ElMessage.warning('请选择第二个图层')
    return
  }
  if (a.key === 'intersectionAndUnion' && form.layerId === form.layerIdB) {
    ElMessage.warning('两个图层不能相同')
    return
  }
  const dto = buildDto()
  const row = layerOptions.value.find((r) => rowId(r) === form.layerId) || {}
  const body = {
    layerId: form.layerId,
    layerName: rowName(row),
    detailJson: JSON.stringify(dto),
    createBy: USER,
    ...dto,
  }
  running.value = true
  try {
    const apiFn: Record<string, (b: any) => Promise<any>> = {
      cutLayer: layerToolApi.cutLayer,
      resample: layerToolApi.resample,
      tifToShp: layerToolApi.tifToShp,
      shpToTif: layerToolApi.shpToTif,
      reclassification: layerToolApi.reclassification,
      eraseVectorLayer: layerToolApi.eraseVectorLayer,
      bufferZone: layerToolApi.bufferZone,
      intersectionAndUnion: layerToolApi.intersectionAndUnion,
      overlayCalculation: layerToolApi.overlayCalculation,
    }
    const taskId = await apiFn[a.key](body)
    ElMessage.success('任务已提交')
    openProgress(String(taskId ?? ''), a.origin)
    loadTasks()
  } catch (e: any) {
    showError(e, '任务提交失败')
  } finally {
    running.value = false
  }
}

/* ------------------------------ 任务进度弹窗 ------------------------------ */
const progressVisible = ref(false)
const progress = reactive<{ taskId: string; origin: number; percent: number; status: string; taskRowId: string }>({
  taskId: '',
  origin: 1,
  percent: 0,
  status: '',
  taskRowId: '',
})
let pollTimer: ReturnType<typeof setInterval> | null = null
const storageTargetId = ref('')
const completed = computed(() => statusInfo(progress.status).key === 'done')

function clearTimer() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}
function openProgress(taskId: string, origin: number) {
  progress.taskId = taskId
  progress.origin = origin
  progress.percent = 0
  progress.status = 'waiting'
  progress.taskRowId = taskId
  storageTargetId.value = ''
  progressVisible.value = true
  clearTimer()
  // 每 2 秒轮询任务列表，定位 taskId 对应任务
  pollTimer = setInterval(pollProgress, 2000)
  pollProgress()
}
async function pollProgress() {
  try {
    const res = await layerToolApi.getList(1, 1, String(progress.origin))
    const list = pickList<any>(res)
    const row = list.find((r) => rowId(r) === progress.taskId) || list[0]
    if (!row) return
    progress.taskRowId = rowId(row)
    progress.percent = taskPercent(row)
    progress.status = String(row.status ?? progress.status)
    if (statusInfo(row.status).key === 'done' || statusInfo(row.status).key === 'fail') {
      clearTimer()
      loadTasks()
      if (statusInfo(row.status).key === 'fail') ElMessage.error('任务执行失败')
    }
  } catch {
    /* 轮询失败静默，等待下一次 */
  }
}
function closeProgress() {
  clearTimer()
  progressVisible.value = false
}

/* ------------------------------ 成果入库 ------------------------------ */
async function doStorage() {
  if (!storageTargetId.value) {
    ElMessage.warning('请选择目标目录')
    return
  }
  try {
    const res = await layerToolApi.storageLayer(progress.taskRowId, storageTargetId.value)
    ElMessage.success(res ? '入库成功' : '入库完成')
    progressVisible.value = false
    clearTimer()
    await loadTasks()
  } catch (e: any) {
    showError(e, '成果入库失败')
  }
}

/* ------------------------------ 任务队列（右栏） ------------------------------ */
const tasks = ref<any[]>([])
const tasksLoading = ref(false)
const viewRow = ref<any>(null)
const viewVisible = ref(false)

async function loadTasks() {
  tasksLoading.value = true
  try {
    tasks.value = pickList<any>(await layerToolApi.getList(10, 1, ''))
  } catch (e: any) {
    showError(e, '加载任务队列失败')
  } finally {
    tasksLoading.value = false
  }
}
function viewTask(row: any) {
  viewRow.value = row
  viewVisible.value = true
}

onMounted(async () => {
  resetForm()
  await Promise.all([loadLayers(), loadStorageTargets(), loadTasks()])
})

onBeforeUnmount(clearTimer)
</script>

<template>
  <div>
    <PageHead title="工具箱" sub="选择数据并提交空间处理任务，在任务中心跟踪进度与入库成果">
      <template #tools>
        <button class="btn sm" @click="loadLayers"><Icon name="refresh" :size="13" />刷新图层</button>
        <button class="btn sm" @click="loadTasks"><Icon name="list" :size="13" />刷新任务</button>
      </template>
    </PageHead>

    <div class="tools-3col">
      <!-- 左：算法分组 -->
      <div class="card col-left">
        <div class="card-h"><Icon name="tool" :size="14" />算法</div>
        <div class="card-b algo-tree">
          <div v-for="g in GROUPS" :key="g.name" class="algo-grp">
            <div class="algo-grp-h">{{ g.name }}</div>
            <div
              v-for="it in g.items"
              :key="it.key"
              class="algo-item"
              :class="{ on: currentKey === it.key }"
              @click="selectAlgo(it.key)"
            >
              <Icon name="play" :size="12" />
              <span class="nm">{{ it.name }}</span>
              <span class="origin mono">origin={{ it.origin }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 中：参数表单 -->
      <div class="card col-mid">
        <div class="card-h">
          <Icon name="edit" :size="14" />{{ algo.name }}
          <span class="tag violet">origin={{ algo.origin }}</span>
          <div class="tools">
            <button v-if="algo.key === 'resample'" class="btn sm" :disabled="readingTif" @click="readTifInfo">
              <Icon name="download" :size="13" />{{ readingTif ? '读取中…' : '读取极值' }}
            </button>
            <button class="btn sm primary" :disabled="running" @click="runTask">
              <Icon name="play" :size="13" />{{ running ? '提交中…' : '运行' }}
            </button>
          </div>
        </div>
        <div class="card-b">
          <div class="muted mb8" style="font-size: 11.5px">{{ algo.desc }}</div>

          <el-form label-width="110px" size="small">
            <el-form-item label="目标图层">
              <el-select v-model="form.layerId" placeholder="请选择图层" filterable style="width: 100%">
                <el-option v-for="l in layerOptions" :key="rowId(l)" :label="rowName(l)" :value="rowId(l)" />
              </el-select>
            </el-form-item>

            <!-- 矢量交并补 -->
            <template v-if="algo.kind === 'inter'">
              <el-form-item label="第二个图层">
                <el-select v-model="form.layerIdB" placeholder="请选择第二个图层" filterable style="width: 100%">
                  <el-option v-for="l in layerOptions" :key="rowId(l)" :label="rowName(l)" :value="rowId(l)" />
                </el-select>
              </el-form-item>
              <el-form-item label="运算类型">
                <el-radio-group v-model="interOp">
                  <el-radio value="intersection">交 intersection</el-radio>
                  <el-radio value="union">并 union</el-radio>
                  <el-radio value="symDifference">对称差 symDifference</el-radio>
                </el-radio-group>
              </el-form-item>
            </template>

            <!-- 栅格叠加计算 -->
            <template v-else-if="algo.kind === 'overlay'">
              <el-form-item label="公式项">
                <div style="width: 100%">
                  <div v-for="(f, i) in overlayFormulas" :key="i" class="row mb8" style="gap: 6px; align-items: center">
                    <el-select v-model="f.dataType" size="small" style="width: 96px">
                      <el-option label="图层" value="layer" />
                      <el-option label="数值" value="number" />
                    </el-select>
                    <el-select
                      v-if="f.dataType === 'layer'"
                      v-model="f.value"
                      size="small"
                      filterable
                      placeholder="选择图层"
                      style="flex: 1"
                    >
                      <el-option v-for="l in layerOptions" :key="rowId(l)" :label="rowName(l)" :value="rowId(l)" />
                    </el-select>
                    <el-input v-else v-model="f.value" size="small" placeholder="数值，如 1.5" style="flex: 1" />
                    <button class="btn xs danger" @click="overlayFormulas.splice(i, 1)">删除</button>
                  </div>
                  <button class="btn xs" @click="overlayFormulas.push({ dataType: 'layer', value: '' })">
                    <Icon name="plus" :size="12" />添加项
                  </button>
                </div>
              </el-form-item>
            </template>

            <!-- 重分类 -->
            <template v-else-if="algo.kind === 'reclass'">
              <el-form-item label="重分类方式">
                <el-select v-model="form.reclassType" style="width: 100%">
                  <el-option label="1 · 分类区间 {min,max,value}" value="1" />
                  <el-option label="2 · 唯一值 {old,value}" value="2" />
                </el-select>
              </el-form-item>
              <el-form-item label="重分类矩阵">
                <div style="width: 100%" class="row" >
                  <el-input
                    v-model="form.matrix"
                    type="textarea"
                    :rows="2"
                    readonly
                    placeholder="点击右侧按钮录入…"
                    style="flex: 1"
                  />
                  <button class="btn sm" style="height: auto" @click="openMatrix"><Icon name="grid" :size="13" />录入矩阵</button>
                </div>
              </el-form-item>
            </template>

            <!-- 普通字段 -->
            <template v-else>
              <el-form-item v-for="f in algo.fields" :key="f.key" :label="f.label">
                <el-select
                  v-if="f.type === 'select' && f.key.endsWith('LayerId')"
                  v-model="form[f.key]"
                  placeholder="请选择图层"
                  filterable
                  style="width: 100%"
                >
                  <el-option v-for="l in layerOptions" :key="rowId(l)" :label="rowName(l)" :value="rowId(l)" />
                </el-select>
                <el-select v-else-if="f.type === 'select'" v-model="form[f.key]" style="width: 100%">
                  <el-option v-for="o in f.options" :key="String(o.value)" :label="o.label" :value="o.value" />
                </el-select>
                <el-input-number
                  v-else-if="f.type === 'number'"
                  v-model="form[f.key]"
                  :controls="false"
                  style="width: 100%"
                />
                <el-input v-else v-model="form[f.key]" :placeholder="f.placeholder" />
              </el-form-item>
            </template>
          </el-form>

          <div class="muted mt8" style="font-size: 11px">
            请求体：<code>LayerToolDto = { layerId, layerName, detailJson, createBy:当前用户, …专属dto }</code>
          </div>
        </div>
      </div>

      <!-- 右：任务队列 -->
      <div class="card col-right">
        <div class="card-h">
          <Icon name="list" :size="14" />任务队列
          <div class="tools">
            <button class="btn xs" @click="loadTasks"><Icon name="refresh" :size="12" />刷新</button>
          </div>
        </div>
        <div class="card-b">
          <el-table :data="tasks" v-loading="tasksLoading" size="small">
            <el-table-column label="工具" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ ORIGIN_NAME[String(row.origin)] || row.origin || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="82">
              <template #default="{ row }">
                <span class="tag" :class="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.createDT ?? row.createTime ?? row.createDate) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ row }">
                <button class="btn xs" @click="viewTask(row)">查看</button>
              </template>
            </el-table-column>
            <template #empty>暂无任务</template>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 重分类矩阵录入 -->
    <el-dialog v-model="matrixVisible" title="重分类矩阵录入" width="560px">
      <el-table :data="matrixRows" size="small" border>
        <el-table-column v-if="String(form.reclassType) === '2'" label="原值 old">
          <template #default="{ row }"><el-input v-model="row.old" size="small" /></template>
        </el-table-column>
        <template v-else>
          <el-table-column label="最小值 min">
            <template #default="{ row }"><el-input v-model="row.min" size="small" /></template>
          </el-table-column>
          <el-table-column label="最大值 max">
            <template #default="{ row }"><el-input v-model="row.max" size="small" /></template>
          </el-table-column>
        </template>
        <el-table-column label="新值 value">
          <template #default="{ row }"><el-input v-model="row.value" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <button class="btn xs danger" @click="matrixRows.splice($index, 1)">删除</button>
          </template>
        </el-table-column>
      </el-table>
      <button class="btn xs mt8" @click="addMatrixRow"><Icon name="plus" :size="12" />添加行</button>

      <!-- 辅助填充：读取极值 / 自然断点 / 唯一值 / 选色带 -->
      <div style="display: flex; gap: 8px; align-items: center; margin-top: 10px; flex-wrap: wrap">
        <button class="btn xs" :disabled="matrixBusy" @click="matrixFromMaxMin">读取极值</button>
        <button class="btn xs" :disabled="matrixBusy" @click="matrixFromJenks">自然断点</button>
        <button class="btn xs" :disabled="matrixBusy" @click="matrixFromValues">唯一值</button>
        <el-select
          v-model="rampSel"
          size="small"
          placeholder="选色带"
          clearable
          filterable
          style="width: 140px"
          @focus="loadRampOptions"
        >
          <el-option v-for="r in rampOptions" :key="r.name" :label="r.name" :value="r.name" />
        </el-select>
        <button class="btn xs" @click="applyRamp">应用色带</button>
      </div>
      <div class="muted" style="font-size: 11.5px; margin-top: 6px">
        提交时 <code>matrix</code> 必须序列化为<b>字符串</b>，不是数组。
      </div>

      <template #footer>
        <button class="btn sm" @click="matrixVisible = false">取消</button>
        <button class="btn sm primary" @click="confirmMatrix">生成 matrix(JSON)</button>
      </template>
    </el-dialog>

    <!-- 任务进度 -->
    <el-dialog v-model="progressVisible" title="任务进度" width="520px" :close-on-click-modal="false" @close="closeProgress">
      <div class="kv">
        <span class="k">任务 ID</span><span class="v mono">{{ progress.taskId || '-' }}</span>
        <span class="k">工具</span><span class="v">{{ ORIGIN_NAME[String(progress.origin)] || progress.origin }}</span>
        <span class="k">状态</span>
        <span class="v"><span class="tag" :class="statusInfo(progress.status).type">{{ statusInfo(progress.status).label }}</span></span>
      </div>
      <el-progress class="mt8" :percentage="progress.percent" :status="completed ? 'success' : undefined" />

      <div v-if="completed" class="mt8">
        <div class="muted" style="font-size: 11.5px">成果入库目标目录</div>
        <div class="row mt8" style="gap: 6px">
          <el-select v-model="storageTargetId" placeholder="选择目标目录" filterable size="small" style="flex: 1">
            <el-option v-for="t in storageTargets" :key="rowId(t)" :label="rowName(t)" :value="rowId(t)" />
          </el-select>
          <button class="btn sm primary" @click="doStorage"><Icon name="db" :size="13" />成果入库</button>
        </div>
      </div>

      <template #footer>
        <button class="btn sm" @click="closeProgress">关闭</button>
      </template>
    </el-dialog>

    <!-- 任务详情（右栏查看） -->
    <el-dialog v-model="viewVisible" title="任务详情" width="520px" @closed="viewRow = null">
      <div v-if="viewRow" class="kv">
        <span class="k">任务 ID</span><span class="v mono">{{ rowId(viewRow) }}</span>
        <span class="k">图层</span><span class="v">{{ rowName(viewRow) }}</span>
        <span class="k">工具</span><span class="v">{{ ORIGIN_NAME[String(viewRow.origin)] || viewRow.origin || '-' }}</span>
        <span class="k">状态</span>
        <span class="v"><span class="tag" :class="statusInfo(viewRow.status).type">{{ statusInfo(viewRow.status).label }}</span></span>
        <span class="k">进度</span><span class="v">{{ taskPercent(viewRow) }}%</span>
        <span class="k">创建时间</span><span class="v">{{ fmtTime(viewRow.createDT ?? viewRow.createTime) }}</span>
      </div>
      <template #footer>
        <button class="btn sm" @click="copyText(JSON.stringify(viewRow))"><Icon name="copy" :size="13" />复制原始数据</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tools-3col {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.col-left {
  flex: 0 0 224px;
}
.col-mid {
  flex: 1;
  min-width: 0;
}
.col-right {
  flex: 0 0 380px;
}
.algo-tree {
  max-height: 640px;
  overflow: auto;
}
.algo-grp + .algo-grp {
  margin-top: 10px;
}
.algo-grp-h {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-3);
  letter-spacing: 1px;
  padding: 4px 2px;
}
.algo-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 9px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--ink-2);
  font-size: 12.5px;
  transition: 0.13s;
}
.algo-item:hover {
  background: var(--panel-2);
  color: var(--primary);
}
.algo-item.on {
  background: var(--primary-050);
  color: var(--primary);
  font-weight: 600;
}
.algo-item .nm {
  flex: 1;
}
.algo-item .origin {
  font-size: 10.5px;
  color: var(--ink-3);
}
</style>
