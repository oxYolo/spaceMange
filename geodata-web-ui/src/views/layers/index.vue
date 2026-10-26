<script setup lang="ts">
import { showError } from '@/utils/feedback'
/**
 * 图层配置（#/layers）—— 对齐 docs/低空监管数据中台-数据管理原型.html
 *
 * 语义映射（与用户确认的收敛原则一致）：
 *  - 左侧目录树 = 后端数据分类（sdms_datatype，与「编目管理→数据分类」同源，支持层级）
 *  - 服务挂载 = 更新 layerContent.layerType 指向目标目录（POST /layerContent/updateList）
 *  - 三维数据（model3d）无 layerType 字段 → 挂载入口禁用并标注
 *  - 「默认打开」开关后端无字段 → 本地 localStorage 记录
 *
 * 后端依据：
 *  - GET  /dataType/GetAllList                     分类树
 *  - POST /dataType/Insert | /Update | /Delete     目录增删改
 *  - POST /dataType/updateList                     目录排序（交换 codeSort）
 *  - POST /layerContent/updateList                 服务挂载 / 移出（LayerUpdateDto[]）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { DATA_TYPES, loadUnifiedPage, type UnifiedRow } from '@/utils/datasets'
import { dataTypeApi, layerContentApi } from '@/api'
import ServicePreview from '@/components/preview/ServicePreview.vue'
import { useUserStore } from '@/auth/store'

/* ---------------- 左侧目录树 ---------------- */
interface TreeNode {
  id: string
  name: string
  parentId: string
  count?: number
  children: TreeNode[]
}
const USER = useUserStore().userName
const tree = ref<TreeNode[]>([])
const treeLoading = ref(false)
const selectedDir = ref<TreeNode | null>(null)
const collapsed = ref<Record<string, boolean>>({})

function buildTree(list: any[]): TreeNode[] {
  if (!list.length) return []
  if (list.some((n) => Array.isArray(n.children))) {
    return list.map((n) => ({ ...toNode(n), children: (n.children || []).map(toNode) }))
  }
  const map = new Map<string, TreeNode>()
  list.forEach((n) => map.set(String(n.id), toNode(n)))
  const roots: TreeNode[] = []
  map.forEach((node) => {
    const pid = node.parentId && map.has(node.parentId) ? node.parentId : ''
    if (pid) map.get(pid)!.children.push(node)
    else roots.push(node)
  })
  return roots
}
function toNode(n: any): TreeNode {
  return {
    id: String(n.id ?? ''),
    name: String(n.typeName ?? n.name ?? '未命名'),
    parentId: n.parentId != null ? String(n.parentId) : '',
    count: n.totalLayerCount ?? n.totalCount,
    children: Array.isArray(n.children) ? n.children.map(toNode) : [],
  }
}

async function loadTree() {
  treeLoading.value = true
  try {
    const list = (await dataTypeApi.getAllList()) as any
    tree.value = buildTree(Array.isArray(list) ? list : (list?.item ?? []))
    await applyCounts()
  } catch (e: any) {
    tree.value = []
    showError(e, '目录树加载失败')
  } finally {
    treeLoading.value = false
  }
}

/** 补充各目录资源数（GET /layerContent/GetAllCountByDataType） */
async function applyCounts() {
  try {
    const stats: any = await layerContentApi.getAllCountByDataType()
    const arr = Array.isArray(stats) ? stats : Array.isArray(stats?.result) ? stats.result : []
    if (!arr.length) return
    const byId = new Map<string, number>()
    arr.forEach((s: any) => {
      const k = String(s.dataType ?? s.dataTypeId ?? s.id ?? '')
      const c = Number(s.count ?? s.totalLayerCount ?? s.num ?? 0)
      if (k) byId.set(k, c)
    })
    const walk = (nodes: TreeNode[]) => {
      nodes.forEach((n) => {
        if (byId.has(n.id)) n.count = byId.get(n.id)
        walk(n.children)
      })
    }
    walk(tree.value)
  } catch {
    /* 统计不可用不阻断 */
  }
}

function selectDir(n: TreeNode) {
  selectedDir.value = n
  refreshTip()
}

/* 目录 CRUD */
const dirDlg = reactive({ visible: false, mode: 'add' as 'add' | 'rename', name: '', node: null as TreeNode | null })

function openAddDir() {
  dirDlg.mode = 'add'
  dirDlg.name = ''
  dirDlg.node = selectedDir.value
  dirDlg.visible = true
}
function openRenameDir(n: TreeNode) {
  dirDlg.mode = 'rename'
  dirDlg.name = n.name
  dirDlg.node = n
  dirDlg.visible = true
}

async function saveDir() {
  const name = dirDlg.name.trim()
  if (!name) return ElMessage.warning('请填写目录名称')
  try {
    if (dirDlg.mode === 'add') {
      await dataTypeApi.insert({
        TypeName: name,
        TypeDesc: '',
        DictCodeID: '',
        ParentID: dirDlg.node?.id || '',
        Type: '',
        CodeSort: 0,
      })
      ElMessage.success(`目录「${name}」已创建${dirDlg.node ? `（位于「${dirDlg.node.name}」下）` : ''}`)
    } else {
      await dataTypeApi.update({ ID: dirDlg.node?.id, TypeName: name })
      ElMessage.success('已重命名')
    }
    dirDlg.visible = false
    await loadTree()
  } catch (e: any) {
    showError(e, '保存失败')
  }
}

async function removeDir(n: TreeNode) {
  const cnt = countAll(n)
  try {
    await ElMessageBox.confirm(
      `确认删除目录「${n.name}」？${cnt ? `该目录及子目录下共 ${cnt} 个资源将变为未分类，服务本身不受影响。` : ''}`,
      '删除目录',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await dataTypeApi.remove(n.id)
    if (selectedDir.value?.id === n.id) selectedDir.value = null
    ElMessage.success('目录已删除')
    await Promise.all([loadTree(), loadServices()])
  } catch (e: any) {
    showError(e, '删除失败')
  }
}
function countAll(n: TreeNode): number {
  return (n.count || 0) + n.children.reduce((a, c) => a + countAll(c), 0)
}

/** 上移 / 下移（交换同级 codeSort，逻辑与编目管理一致） */
async function moveDir(n: TreeNode, dir: -1 | 1) {
  const siblings = findSiblings(tree.value, n.id)
  const i = siblings.findIndex((s) => s.id === n.id)
  const j = i + dir
  if (j < 0 || j >= siblings.length) return
  const a = n
  const b = siblings[j]
  const sa = Number(a.count ?? 0)
  const sb = Number(b.count ?? 0)
  try {
    await dataTypeApi.updateList([
      { id: a.id, parentId: a.parentId, type: sb, codeSort: sb },
      { id: b.id, parentId: b.parentId, type: sa, codeSort: sa },
    ])
    ElMessage.success('排序已更新')
    await loadTree()
  } catch (e: any) {
    showError(e, '排序失败')
  }
}
function findSiblings(nodes: TreeNode[], id: string): TreeNode[] {
  for (const n of nodes) {
    if (n.children.some((c) => c.id === id)) return n.children
    const hit = findSiblings(n.children, id)
    if (hit.length) return hit
  }
  return nodes
}

/* ---------------- 右侧已发布服务 ---------------- */
const filters = reactive({ kw: '', type: '' })
const rows = ref<UnifiedRow[]>([])
const total = ref(0)
const pageSize = ref(10)
const pageIndex = ref(1)
const loading = ref(false)
const layPicked = ref<Set<string>>(new Set())
/** 每行挂载的目录 id → 目录名 */
const dirNameOf = ref<Record<string, string>>({})
/** 「默认打开」开关（localStorage 持久化） */
const defaultOpen = reactive<Record<string, boolean>>(loadDefaultOpen())

function loadDefaultOpen(): Record<string, boolean> {
  try {
    return JSON.parse(localStorage.getItem('gd-layer-default-open') || '{}')
  } catch {
    return {}
  }
}
function toggleDefault(key: string) {
  defaultOpen[key] = !defaultOpen[key]
  localStorage.setItem('gd-layer-default-open', JSON.stringify(defaultOpen))
}

async function loadServices() {
  loading.value = true
  try {
    const res = await loadUnifiedPage(
      { typeKey: filters.type, publishedOnly: true, keyword: filters.kw.trim() },
      pageSize.value,
      pageIndex.value,
    )
    rows.value = res.rows
    total.value = res.total
    layPicked.value = new Set()
    await loadDirNames()
  } catch (e: any) {
    showError(e, '服务列表加载失败')
  } finally {
    loading.value = false
  }
}

/** 加载全部已发布二维图层，建 layerId → 目录名 映射 */
async function loadDirNames() {
  const dirMap = new Map<string, string>()
  const walkTree = (nodes: TreeNode[]) => nodes.forEach((n) => (dirMap.set(n.id, n.name), walkTree(n.children)))
  walkTree(tree.value)
  try {
    const res = await layerContentApi.getPageListByName({ publishStatus: '1', createBy: USER }, 500, 1)
    const list: any[] = Array.isArray(res?.items) ? res.items : Array.isArray(res) ? res : []
    const map: Record<string, string> = {}
    list.forEach((l: any) => {
      const lt = String(l.layerType ?? '')
      if (lt && dirMap.has(lt)) map[String(l.id)] = dirMap.get(lt)!
    })
    dirNameOf.value = map
  } catch {
    dirNameOf.value = {}
  }
}

function dirOf(row: UnifiedRow): string {
  if (row.src !== 'layer') return '—'
  return dirNameOf.value[row.id] || '未挂载'
}
function inAnyDir(row: UnifiedRow): boolean {
  return row.src === 'layer' && !!dirNameOf.value[row.id]
}

/* 勾选放入目录 */
const tip = ref('')
function refreshTip() {
  const sel = selectedDir.value
  tip.value = layPicked.value.size
    ? sel
      ? `将放入目录：${sel.name}`
      : '请先在左侧选中一个目录'
    : sel
      ? `当前目录：${sel.name}`
      : '请在左侧选中目录后勾选服务'
}

/** 可勾选行：未挂载且为二维 */
const pickableRows = computed(() => rows.value.filter((r) => !inAnyDir(r)))
const allPicked = computed(
  () => pickableRows.value.length > 0 && pickableRows.value.every((r) => layPicked.value.has(r.key)),
)
const partPicked = computed(() => pickableRows.value.some((r) => layPicked.value.has(r.key)))
function togglePick(row: UnifiedRow, checked: boolean) {
  const s = new Set(layPicked.value)
  if (checked) s.add(row.key)
  else s.delete(row.key)
  layPicked.value = s
}
function toggleAll(checked: boolean) {
  layPicked.value = new Set(checked ? pickableRows.value.map((r) => r.key) : [])
}

async function applyPick() {
  if (!layPicked.value.size) return
  const target = selectedDir.value
  if (!target) return ElMessage.warning('请先在左侧选中一个目录')
  const targets = rows.value.filter((r) => layPicked.value.has(r.key))
  const lcRows = targets.filter((r) => r.src === 'layer')
  const skipped3d = targets.length - lcRows.length
  if (!lcRows.length) {
    return ElMessage.warning('三维数据暂不支持挂载目录（后端无 layerType 字段）')
  }
  try {
    await layerContentApi.updateList(lcRows.map((r) => ({ id: r.id, parentId: target.id, type: 'layer', sort: 0 })))
    ElMessage.success(
      `已将 ${lcRows.length} 个服务放入目录「${target.name}」${skipped3d ? `；${skipped3d} 个三维服务已跳过` : ''}`,
    )
    await Promise.all([loadTree(), loadServices()])
  } catch (e: any) {
    showError(e, '挂载失败')
  }
}

async function unmount(row: UnifiedRow) {
  try {
    await layerContentApi.updateList([{ id: row.id, parentId: '', type: 'layer', sort: 0 }])
    ElMessage.success('已移出目录')
    await Promise.all([loadTree(), loadServices()])
  } catch (e: any) {
    showError(e, '移出失败')
  }
}

/* 保存配置（挂载即时生效，此处提示整体状态） */
const treeStats = computed(() => {
  const countDirs = (ns: TreeNode[]): number => ns.reduce((a, n) => a + 1 + countDirs(n.children), 0)
  return { dirs: countDirs(tree.value), mounted: Object.keys(dirNameOf.value).length }
})
async function saveConfig() {
  await ElMessageBox.alert(
    `目录配置为即时保存模式：目录增删改与挂载操作均已实时生效。\n当前共 ${treeStats.value.dirs} 个目录 / ${treeStats.value.mounted} 个服务已挂载。`,
    '保存配置',
    { confirmButtonText: '知道了' },
  )
}

/* 预览 */
const previewVisible = ref(false)
const previewRow = ref<UnifiedRow | null>(null)
function openPreview(row: UnifiedRow) {
  previewRow.value = row
  previewVisible.value = true
}

onMounted(async () => {
  await loadTree()
  await loadServices()
  refreshTip()
})
</script>

<template>
  <div class="lay-page">
    <PageHead title="图层配置" sub="先在左侧建目录，再把右侧已发布服务挂到目录下，供渲染引擎按图层加载">
      <template #tools>
        <el-button class="btn sm primary" @click="saveConfig"><Icon name="check" :size="13" /> 保存配置</el-button>
      </template>
    </PageHead>

    <div class="lay-body">
      <!-- 左：目录树 -->
      <div class="card tree-panel">
        <button class="add-dir" @click="openAddDir"><Icon name="plus" :size="13" /> 新增目录</button>
        <div class="tree-root">
          <Icon name="layers" :size="14" />
          <span>全部目录</span>
        </div>
        <div v-loading="treeLoading" class="tree">
          <template v-if="tree.length">
            <template v-for="n in tree" :key="n.id">
              <div class="tnode">
                <div class="trow" :class="{ sel: selectedDir?.id === n.id }" @click="selectDir(n)">
                  <button class="caret" :class="{ open: !collapsed[n.id] }" type="button" :aria-label="`${collapsed[n.id] ? '展开' : '收起'}目录 ${n.name}`" :aria-expanded="!collapsed[n.id]" @click.stop="collapsed[n.id] = !collapsed[n.id]"><Icon name="chevron-right" :size="13" /></button>
                  <Icon name="tag" :size="13" />
                  <span class="tname" :title="n.name">{{ n.name }}</span>
                  <span v-if="n.count" class="tcnt">{{ n.count }}</span>
                  <span class="tacts">
                    <button class="tact" title="上移" aria-label="上移目录" @click.stop="moveDir(n, -1)"><Icon name="arrow-up" :size="13" /></button>
                    <button class="tact" title="下移" aria-label="下移目录" @click.stop="moveDir(n, 1)"><Icon name="arrow-down" :size="13" /></button>
                    <button class="tact" title="重命名" aria-label="重命名目录" @click.stop="openRenameDir(n)"><Icon name="edit" :size="13" /></button>
                    <button class="tact del" title="删除目录" aria-label="删除目录" @click.stop="removeDir(n)"><Icon name="x" :size="13" /></button>
                  </span>
                </div>
                <div v-if="n.children.length && !collapsed[n.id]" class="tchildren">
                  <div
                    v-for="c in n.children"
                    :key="c.id"
                    class="trow sub"
                    :class="{ sel: selectedDir?.id === c.id }"
                    @click="selectDir(c)"
                  >
                    <Icon name="tag" :size="12" />
                    <span class="tname" :title="c.name">{{ c.name }}</span>
                    <span v-if="c.count" class="tcnt">{{ c.count }}</span>
                    <span class="tacts">
                      <button class="tact" title="重命名" aria-label="重命名目录" @click.stop="openRenameDir(c)"><Icon name="edit" :size="13" /></button>
                      <button class="tact del" title="删除目录" aria-label="删除目录" @click.stop="removeDir(c)"><Icon name="x" :size="13" /></button>
                    </span>
                  </div>
                </div>
              </div>
            </template>
          </template>
          <div v-else-if="!treeLoading" class="tree-empty">
            暂无目录<br />
            点击上方「＋ 新增目录」开始搭建目录结构<br />
            再到右侧勾选已发布服务放入目录
          </div>
        </div>
      </div>

      <!-- 右：已发布服务 -->
      <div class="card list-panel">
        <div class="filters">
          <el-input
            v-model="filters.kw"
            placeholder="搜索服务名称"
            clearable
            style="width: 200px"
            @keyup.enter="loadServices"
            @clear="loadServices"
          />
          <el-select v-model="filters.type" placeholder="请选择服务类型" clearable style="width: 170px" @change="loadServices">
            <el-option v-for="t in DATA_TYPES" :key="t.key" :label="t.label" :value="t.key" />
          </el-select>
          <el-button class="btn sm primary" @click="loadServices">搜索</el-button>
          <el-button
            class="btn sm"
            @click="
              filters.kw = '';
              filters.type = '';
              loadServices()
            "
          >
            重置
          </el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="rows"
          size="small"
          height="100%"
          :row-class-name="({ row }: any) => (inAnyDir(row) ? 'in-tree' : '')"
        >
          <el-table-column width="42">
            <template #header>
              <el-checkbox
                :model-value="allPicked"
                :indeterminate="partPicked && !allPicked"
                :disabled="!pickableRows.length"
                @change="(v: any) => toggleAll(!!v)"
              />
            </template>
            <template #default="{ row }: any">
              <el-checkbox
                :model-value="layPicked.has(row.key)"
                :disabled="inAnyDir(row)"
                :title="inAnyDir(row) ? '该服务已在目录树中' : ''"
                @change="(v: any) => togglePick(row, !!v)"
              />
            </template>
          </el-table-column>
          <el-table-column label="服务名称" min-width="170" show-overflow-tooltip>
            <template #default="{ row }: any">{{ row.name }}</template>
          </el-table-column>
          <el-table-column label="服务类型" width="140">
            <template #default="{ row }: any">{{ row.typeLabel }}</template>
          </el-table-column>
          <el-table-column label="服务格式" width="96">
            <template #default="{ row }: any"><span class="fmt">{{ row.src === 'm3d' ? '3DTiles' : row.format }}</span></template>
          </el-table-column>
          <el-table-column label="所属目录" width="130" show-overflow-tooltip>
            <template #default="{ row }: any">
              <span :class="{ muted: !inAnyDir(row) }">{{ dirOf(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="默认打开" width="92">
            <template #default="{ row }: any">
              <div class="sw-wrap" :class="{ on: defaultOpen[row.key] }" title="渲染引擎加载时默认打开该图层" @click="toggleDefault(row.key)">
                <span class="sw"></span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }: any">
              <el-button class="op" link type="primary" size="small" @click="openPreview(row)">预览</el-button>
              <span class="op-sep">|</span>
              <el-button
                class="op"
                link
                type="primary"
                size="small"
                :disabled="row.src !== 'layer' || !inAnyDir(row)"
                :title="row.src === 'm3d' ? '三维数据暂不支持挂载目录' : ''"
                @click="unmount(row)"
              >
                移出目录
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有匹配的已发布服务" :image-size="70" />
          </template>
        </el-table>

        <!-- 勾选批量条 -->
        <div class="batch-bar" :class="{ hidden: !layPicked.size }">
          <span>已选择 <b>{{ layPicked.size }}</b> 项</span>
          <span class="lay-tip" :class="{ warn: layPicked.size && !selectedDir }">{{ tip }}</span>
          <div style="flex: 1"></div>
          <el-button class="btn sm primary" @click="applyPick">勾选放入目录</el-button>
        </div>

        <div class="pager">
          <el-pagination
            v-model:current-page="pageIndex"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            small
            @current-change="loadServices"
            @size-change="pageIndex = 1; loadServices()"
          />
        </div>
      </div>
    </div>

    <!-- 新增 / 重命名目录 -->
    <el-dialog
      v-model="dirDlg.visible"
      :title="dirDlg.mode === 'add' ? '新增目录' : '重命名目录'"
      width="460px"
      :close-on-click-modal="false"
    >
      <div class="field">
        <label><i class="req">*</i>目录名称</label>
        <el-input v-model="dirDlg.name" placeholder="如：空间数据 / 三维模型 / 基础服务" @keyup.enter="saveDir" />
        <div class="hint" v-if="dirDlg.mode === 'add'">
          {{ dirDlg.node ? `将创建在「${dirDlg.node.name}」下` : '将创建在根目录下；如需建子目录，请先在左侧选中父目录' }}
        </div>
      </div>
      <template #footer>
        <el-button class="btn" @click="dirDlg.visible = false">取消</el-button>
        <el-button class="btn primary" @click="saveDir">{{ dirDlg.mode === 'add' ? '创建' : '保存' }}</el-button>
      </template>
    </el-dialog>

    <!-- 全屏预览 -->
    <ServicePreview v-model="previewVisible" :row="previewRow" />
  </div>
</template>

<style scoped>
.lay-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.lay-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
}
@media (max-width: 1280px) {
  .lay-body {
    grid-template-columns: 1fr;
  }
}

/* 左：目录树 */
.tree-panel {
  display: flex;
  flex-direction: column;
  padding: 12px 10px 8px;
  min-height: 0;
}
.add-dir {
  width: 100%;
  height: 34px;
  border: 1px solid #c9dcf8;
  border-radius: 7px;
  background: #f2f8ff;
  color: var(--primary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  transition: 0.15s;
}
.add-dir:hover {
  background: #e8f2ff;
  border-color: var(--primary);
}
.tree-root {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 6px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--ink-2);
  font-weight: 600;
}
.tree {
  flex: 1;
  overflow: auto;
  padding: 2px 0 8px;
  min-height: 0;
}
.tnode {
  user-select: none;
}
.trow {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 31px;
  padding: 0 5px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12.5px;
  color: var(--ink-2);
  transition: background 0.12s;
}
.trow:hover {
  background: #f5f8fd;
}
.trow.sel {
  background: var(--primary-050);
  color: var(--primary);
  font-weight: 600;
}
.trow.sub {
  margin-left: 18px;
}
.caret {
  width: 13px;
  flex: 0 0 13px;
  display: inline-grid;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #9aa5b5;
  cursor: pointer;
  transition: transform 0.15s;
}
.caret.open {
  transform: rotate(90deg);
}
.tname {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tcnt {
  font-size: 10.5px;
  color: var(--ink-3);
  background: var(--panel-2);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 0 5px;
  line-height: 15px;
}
.tacts {
  display: none;
  align-items: center;
  gap: 0;
  margin-left: 2px;
}
.trow:hover .tacts,
.trow.sel .tacts {
  display: inline-flex;
}
.tact {
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  border-radius: 4px;
  color: #93a0b2;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
}
.tact:hover {
  background: #e7edf7;
  color: var(--primary);
}
.tact.del:hover {
  background: #fdecec;
  color: var(--danger);
}
.tchildren {
  margin-left: 14px;
  border-left: 1px dashed #e5eaf2;
  padding-left: 4px;
}
.tree-empty {
  font-size: 12.5px;
  color: var(--ink-3);
  padding: 34px 14px;
  line-height: 1.9;
  text-align: center;
}

/* 右：服务列表 */
.list-panel {
  display: flex;
  flex-direction: column;
  padding: 12px 12px 0;
  min-height: 0;
}
.list-panel .filters {
  padding-bottom: 10px;
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
/* 默认打开开关 */
.sw-wrap {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
}
.sw {
  position: relative;
  width: 34px;
  height: 18px;
  flex: 0 0 34px;
  border-radius: 9px;
  background: #d3d9e3;
  transition: background 0.2s;
}
.sw::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(20, 35, 60, 0.25);
  transition: transform 0.2s;
}
.sw-wrap.on .sw {
  background: var(--ok);
}
.sw-wrap.on .sw::after {
  transform: translateX(16px);
}
.sw-wrap:hover .sw {
  box-shadow: 0 0 0 3px rgba(43, 124, 255, 0.1);
}
/* 勾选批量条 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  font-size: 12.5px;
  color: var(--ink-2);
  border-top: 1px solid var(--line);
}
.batch-bar.hidden {
  display: none;
}
.batch-bar b {
  color: var(--primary);
}
.lay-tip {
  color: var(--ink-3);
}
.lay-tip.warn {
  color: var(--warn);
}
.op {
  padding: 0 !important;
  height: auto !important;
}
.op-sep {
  color: var(--line-2);
  margin: 0 6px;
}
.pager {
  padding: 10px 4px;
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
}
:deep(.in-tree) {
  background: #f7fbff;
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
</style>
