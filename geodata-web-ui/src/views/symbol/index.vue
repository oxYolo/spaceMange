<script setup lang="ts">
/**
 * 符号库 —— SymbolController（6 端点）
 * 关联端点依据：
 *  - GET  /symbol/GetAllList     全量符号
 *  - POST /symbol/insert         新增（路径全小写）
 *  - POST /symbol/Update         保存
 *  - POST /symbol/Delete         删除（id）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { symbolApi } from '@/api'
import { pickList, pickTotal } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 后端 SymbolEntity / SymbolDto 字段（sdms_symbol 表） */
interface SymbolRow {
  id: string
  symbolName?: string // 符号名称
  symbolType?: string // 点 / 线 / 面
  symbolClassify?: string // 符号分类
  symbolDesc?: string // 符号描述
  imageorFontType?: string // 图片 / 字体
  path?: string // 图标路径（图片或字体）
  fontFamily?: string // 字体名
  charUnicode?: string // 字体符号（unicode）
  fontColor?: string // 字体颜色
  fontSize?: string // 字体大小
  fontAngle?: string // 字体角度
  imagelong?: string
  imageWide?: string
  imageAngle?: string
  createBy?: string
  createDT?: string
  [k: string]: any
}

const loading = ref(false)
const list = ref<SymbolRow[]>([])
const keyword = ref('')
const total = ref(0)
const page = reactive({ index: 1, size: 12 })

/** 符号类型（点/线/面）与线型（实线/虚线/点线）枚举 */
const SYMBOL_TYPES = ['点', '线', '面']
const LINE_TYPES = ['实线', '虚线', '点线']

/** 前端关键字过滤（名称 / 类型 / 字体名） */
/**
 * 列表已在服务端按 symbolName 过滤并分页，此处直接返回当前页数据；
 * 保留 computed 以便后续做页内二次过滤。
 */
const filtered = computed(() => list.value)

/** SVG 虚线样式映射 */
function dashOf(lineType?: string): string {
  if (lineType === '虚线') return '8 5'
  if (lineType === '点线') return '2 4'
  return '0'
}
/** 点符号半径（依据 size 做合理收敛） */
function pointR(size?: number | string): number {
  const n = Number(size) || 10
  return Math.min(15, Math.max(6, n / 1.4))
}

async function load() {
  loading.value = true
  try {
    // 服务端分页：POST /symbol/GetAllListPage（pageIndex / pageSize 为必填 @RequestParam）
    // 关键字映射到 SymbolInputDto.symbolName（后端按名称过滤）
    const res = await symbolApi.getAllListPage(
      { symbolName: keyword.value.trim() || '' },
      page.index,
      page.size,
    )
    list.value = pickList<SymbolRow>(res)
    total.value = pickTotal(res, list.value.length)
  } catch (e: any) {
    ElMessage.error(e?.message || '符号列表加载失败')
  } finally {
    loading.value = false
  }
}
/** 检索条件变化时回到第 1 页再查 */
function search() {
  page.index = 1
  load()
}

/* ------------------------- 新增 / 编辑弹窗 ------------------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const saving = ref(false)
const form = reactive<Record<string, any>>({
  id: '',
  symbolName: '',
  symbolType: '点',
  symbolClassify: '',
  symbolDesc: '',
  imageorFontType: '字体',
  path: '',
  fontFamily: '',
  charUnicode: '',
  fontColor: '#1163b5',
  fontSize: '12',
  fontAngle: '0',
})

function resetForm() {
  form.id = ''
  form.symbolName = ''
  form.symbolType = '点'
  form.symbolClassify = ''
  form.symbolDesc = ''
  form.imageorFontType = '字体'
  form.path = ''
  form.fontFamily = ''
  form.charUnicode = ''
  form.fontColor = '#1163b5'
  form.fontSize = '12'
  form.fontAngle = '0'
}
function openAdd() {
  dialogMode.value = 'add'
  resetForm()
  dialogVisible.value = true
}
function openEdit(row: SymbolRow) {
  dialogMode.value = 'edit'
  form.id = row.id
  form.symbolName = row.symbolName ?? ''
  form.symbolType = row.symbolType || '点'
  form.symbolClassify = row.symbolClassify ?? ''
  form.symbolDesc = row.symbolDesc ?? ''
  form.imageorFontType = row.imageorFontType || '字体'
  form.path = row.path ?? ''
  form.fontFamily = row.fontFamily ?? ''
  form.charUnicode = row.charUnicode ?? ''
  form.fontColor = row.fontColor || '#1163b5'
  form.fontSize = String(row.fontSize ?? '12')
  form.fontAngle = String(row.fontAngle ?? '0')
  dialogVisible.value = true
}

async function save() {
  const nm = String(form.symbolName || '').trim()
  if (!nm) {
    ElMessage.warning('请输入符号名称')
    return
  }
  saving.value = true
  try {
    const body: Record<string, any> = {
      symbolName: nm,
      symbolType: form.symbolType,
      symbolClassify: form.symbolClassify,
      symbolDesc: form.symbolDesc,
      imageorFontType: form.imageorFontType,
      path: form.path,
      fontFamily: form.fontFamily,
      charUnicode: form.charUnicode,
      fontColor: form.fontColor,
      fontSize: String(form.fontSize ?? ''),
      fontAngle: String(form.fontAngle ?? ''),
      createBy: userStore.userName,
    }
    if (dialogMode.value === 'edit') {
      body.id = form.id
      await symbolApi.update(body)
      ElMessage.success('符号已保存')
    } else {
      await symbolApi.insert(body)
      ElMessage.success('符号已新增')
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row: SymbolRow) {
  try {
    await ElMessageBox.confirm(`确认删除符号「${row.symbolName || row.id}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await symbolApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHead title="符号库" sub="SymbolController · 6 端点（列表 / 增删改 / 详情）">
      <template #tools>
        <el-input
          v-model="keyword"
          placeholder="按符号名称检索（服务端）"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><Icon name="search" :size="14" /></template>
        </el-input>
        <button class="btn sm" @click="search"><Icon name="search" :size="13" />查询</button>
        <button class="btn sm primary" @click="openAdd"><Icon name="plus" :size="13" />新增符号</button>
        <button class="btn sm" @click="load"><Icon name="refresh" :size="13" />刷新</button>
      </template>
    </PageHead>

    <div v-loading="loading">
      <div v-if="!filtered.length" class="card">
        <div class="card-b muted" style="text-align: center; padding: 34px">
          {{ list.length ? '无匹配的符号' : '暂无符号数据' }}
        </div>
      </div>

      <div v-else class="sym-grid">
        <div v-for="s in filtered" :key="s.id" class="card sym-card">
          <div class="sym-canvas">
            <svg viewBox="0 0 64 48" width="100%" height="100%">
              <!-- 点 -->
              <template v-if="(s.symbolType || '点') === '点'">
                <circle
                  cx="32"
                  cy="24"
                  :r="pointR(s.fontSize)"
                  :fill="s.fontColor || '#1163b5'"
                  :stroke="s.fontColor || '#0d5099'"
                  stroke-width="2"
                />
              </template>
              <!-- 线 -->
              <template v-else-if="s.symbolType === '线'">
                <path
                  d="M9 37 L24 12 L40 34 L55 10"
                  fill="none"
                  :stroke="s.fontColor || '#1163b5'"
                  stroke-width="3"
                  :stroke-dasharray="dashOf(s.symbolClassify)"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </template>
              <!-- 面 -->
              <template v-else>
                <polygon
                  points="32,7 55,19 55,35 32,45 9,35 9,19"
                  :fill="s.fontColor ? s.fontColor + '33' : '#d5e7f8'"
                  fill-opacity="0.7"
                  :stroke="s.fontColor || '#1163b5'"
                  stroke-width="2"
                  :stroke-dasharray="dashOf(s.symbolClassify)"
                  stroke-linejoin="round"
                />
              </template>
            </svg>
          </div>
          <div class="sym-meta">
            <div class="sym-name mono" :title="s.symbolName">{{ s.symbolName || '-' }}</div>
            <div class="row" style="gap: 5px; flex-wrap: wrap">
              <span class="tag blue">{{ s.symbolType || '点' }}</span>
              <span v-if="s.symbolClassify" class="tag gray">{{ s.symbolClassify }}</span>
              <span class="tag gray">字号 {{ s.fontSize ?? '-' }}</span>
            </div>
            <div class="muted sym-sub mono" :title="s.fontFamily">
              {{ s.fontFamily || s.charUnicode || '—' }}
            </div>
          </div>
          <div class="sym-ops">
            <button class="btn xs" @click="openEdit(s)"><Icon name="edit" :size="12" />编辑</button>
            <button class="btn xs danger" @click="onDelete(s)"><Icon name="trash" :size="12" />删除</button>
          </div>
        </div>
      </div>

      <div class="pager" style="padding-top: 10px">
        <el-pagination
          v-model:current-page="page.index"
          v-model:page-size="page.size"
          :total="total"
          :page-sizes="[12, 24, 48]"
          layout="total, sizes, prev, pager, next"
          small
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'edit' ? '编辑符号' : '新增符号'"
      width="520px"
      append-to-body
    >
      <el-form :model="form" label-width="88px" label-position="right">
        <el-form-item label="名称" required>
          <el-input v-model="form.symbolName" placeholder="请输入符号名称" />
        </el-form-item>
        <el-form-item label="符号类型">
          <el-select v-model="form.symbolType" style="width: 100%">
            <el-option v-for="t in SYMBOL_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="符号分类">
          <el-select v-model="form.symbolClassify" style="width: 100%">
            <el-option v-for="l in LINE_TYPES" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="图片/字体">
          <el-select v-model="form.imageorFontType" style="width: 100%">
            <el-option label="字体" value="字体" />
            <el-option label="图片" value="图片" />
          </el-select>
        </el-form-item>
        <el-form-item label="字体名">
          <el-input v-model="form.fontFamily" placeholder="如 esriDefault / 宋体" />
        </el-form-item>
        <el-form-item label="图标路径">
          <el-input v-model="form.path" placeholder="字体图标编码或图片路径" />
        </el-form-item>
        <el-form-item label="字体符号">
          <el-input v-model="form.charUnicode" placeholder="unicode 码点，如 &#xE600;" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.symbolDesc" placeholder="符号用途说明" />
        </el-form-item>
        <el-form-item label="填充色">
          <el-color-picker v-model="form.fontColor" />
          <span class="mono muted" style="margin-left: 8px">{{ form.fontColor }}</span>
        </el-form-item>
        <el-form-item label="线色">
          <el-input v-model="form.fontAngle" placeholder="字体角度，如 0" />
        </el-form-item>
        <el-form-item label="大小">
          <el-input v-model="form.fontSize" placeholder="字体大小，如 12" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sym-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}
.sym-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sym-canvas {
  height: 96px;
  background: repeating-conic-gradient(#f5f8fb 0% 25%, #ffffff 0% 50%) 50% / 14px 14px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 0;
}
.sym-canvas svg {
  max-height: 84px;
}
.sym-meta {
  padding: 9px 11px 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.sym-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sym-sub {
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sym-ops {
  margin-top: auto;
  padding: 8px 11px 11px;
  display: flex;
  gap: 6px;
}
</style>
