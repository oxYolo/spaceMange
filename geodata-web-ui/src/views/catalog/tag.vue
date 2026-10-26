<script setup lang="ts">
import { showError } from '@/utils/feedback'
/**
 * 数据标签（编目管理）
 * 后端：DataTagController（6 端点）：GetAllList / GetAllListByDataType / Insert / Update / Delete
 *       TagRelationController：MultiInsert 批量打标
 *       LayerContentController：GetPageListByName 资源下拉
 *       DicDataCodeController：GetAllList 数据类型下拉
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { dataTagApi, tagRelationApi, dictApi, layerContentApi } from '@/api'
import { pickList, fmtTime } from '@/utils/format'
import { useUserStore } from '@/auth/store'

const userStore = useUserStore()

const loading = ref(false)
const tags = ref<any[]>([]) // 全部标签
const dictOptions = ref<any[]>([]) // 数据类型字典
const kw = ref('')
const dataType = ref('')
const activeTag = ref('') // 当前选中的标签云 chip

/** 数据类型下拉选项（label 用 codeName/code，value 用 code） */
const typeOptions = computed(() =>
  dictOptions.value.map((d) => ({ value: d.code, label: d.codeName || d.code })),
)

function tagName(r: any) {
  return r?.tagName ?? r?.name ?? ''
}
function tagType(r: any) {
  return r?.dataType ?? r?.type ?? ''
}
function tagUser(r: any) {
  return r?.createUser ?? r?.creator ?? r?.user ?? ''
}
function tagTime(r: any) {
  return r?.createDT ?? r?.createTime ?? r?.createDate ?? ''
}

/** 顶部筛选后的标签集（供标签云渲染） */
const cloudTags = computed(() =>
  tags.value.filter((t) => {
    const name = String(tagName(t)).toLowerCase()
    if (kw.value && !name.includes(kw.value.toLowerCase())) return false
    if (dataType.value && String(tagType(t)) !== String(dataType.value)) return false
    return true
  }),
)

/** 明细表数据：在筛选基础上再按选中的 chip 过滤 */
const tableRows = computed(() => {
  if (!activeTag.value) return cloudTags.value
  return cloudTags.value.filter((t) => tagName(t) === activeTag.value)
})

/** 数据类型 code → 名称 */
function typeLabel(code: any): string {
  const hit = typeOptions.value.find((o) => String(o.value) === String(code))
  return hit ? hit.label : String(code || '-')
}

/** 载入标签列表（GetAllList / GetAllListByDataType） */
async function loadTags() {
  loading.value = true
  try {
    const res = dataType.value
      ? await dataTagApi.getAllListByDataType(dataType.value)
      : await dataTagApi.getAllList()
    tags.value = pickList(res)
  } catch (e: any) {
    showError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 载入数据类型字典 */
async function loadDict() {
  try {
    const res = await dictApi.codeAll()
    dictOptions.value = pickList(res)
  } catch (e: any) {
    showError(e, '字典加载失败')
  }
}

function toggleChip(name: string) {
  activeTag.value = activeTag.value === name ? '' : name
}

/* ---------------- 新增 / 编辑弹窗 ---------------- */
const dialog = reactive({
  visible: false,
  mode: 'add' as 'add' | 'edit',
  form: { id: '', tagName: '', dataType: '', tagDesc: '' } as Record<string, any>,
})

function openAdd() {
  dialog.mode = 'add'
  dialog.form = { id: '', tagName: '', dataType: '', tagDesc: '' }
  dialog.visible = true
}

function openEdit(row: any) {
  dialog.mode = 'edit'
  dialog.form = {
    id: String(row.id),
    tagName: tagName(row),
    dataType: tagType(row),
    tagDesc: row.tagDesc ?? row.description ?? '',
  }
  dialog.visible = true
}

/** 保存标签（Insert / Update） */
async function save() {
  if (!dialog.form.tagName) return ElMessage.warning('请填写标签名称')
  loading.value = true
  try {
    const body: Record<string, any> = {
      tagName: dialog.form.tagName,
      dataType: dialog.form.dataType,
      tagDesc: dialog.form.tagDesc,
    }
    if (dialog.mode === 'edit') {
      await dataTagApi.update({ ID: dialog.form.id, ...body })
      ElMessage.success('修改成功')
    } else {
      await dataTagApi.insert(body)
      ElMessage.success('新增成功')
    }
    dialog.visible = false
    await loadTags()
  } catch (e: any) {
    showError(e, '保存失败')
  } finally {
    loading.value = false
  }
}

/** 删除标签（Delete） */
async function removeTag(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除标签「${tagName(row)}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await dataTagApi.remove(String(row.id))
    ElMessage.success('删除成功')
    await loadTags()
  } catch (e: any) {
    showError(e, '删除失败')
  } finally {
    loading.value = false
  }
}

/* ---------------- 批量打标弹窗 ---------------- */
const batch = reactive({
  visible: false,
  resourceId: '',
  tagNames: [] as string[],
})
const resourceOptions = ref<any[]>([])

/** 打开批量打标弹窗并载入资源下拉（layerContentApi.getPageListByName） */
async function openBatch() {
  batch.resourceId = ''
  batch.tagNames = []
  batch.visible = true
  try {
    const res = await layerContentApi.getPageListByName({ createBy: userStore.userName }, 50, 1)
    resourceOptions.value = pickList(res)
  } catch (e: any) {
    showError(e, '资源列表加载失败')
  }
}

/** 批量打标（tagRelationApi.multiInsert） */
async function saveBatch() {
  if (!batch.resourceId) return ElMessage.warning('请选择资源')
  if (!batch.tagNames.length) return ElMessage.warning('请选择至少一个标签')
  loading.value = true
  try {
    await tagRelationApi.multiInsert({
      tagName: batch.tagNames.join(','),
      dataID: batch.resourceId,
      mapLayerID: '',
      createUser: userStore.userName,
    })
    ElMessage.success('批量打标成功')
    batch.visible = false
  } catch (e: any) {
    showError(e, '批量打标失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTags()
  loadDict()
})
</script>

<template>
  <div>
    <PageHead title="数据标签" sub="维护业务标签，为数据资源批量分类和标记">
      <template #tools>
        <button class="btn primary" @click="openAdd"><Icon name="plus" :size="13" />新增标签</button>
        <button class="btn" @click="openBatch"><Icon name="tag" :size="13" />批量打标</button>
        <button class="btn" @click="loadTags"><Icon name="refresh" :size="13" />刷新</button>
      </template>
    </PageHead>

    <!-- 顶部筛选 -->
    <div class="card mb8">
      <div class="card-b">
        <div class="filters" style="padding: 0">
          <el-input
            v-model="kw"
            placeholder="按标签名称检索"
            clearable
            style="width: 240px"
            @keyup.enter="loadTags"
          >
            <template #prefix><Icon name="search" :size="13" /></template>
          </el-input>
          <el-select v-model="dataType" placeholder="全部数据类型" clearable style="width: 200px" @change="loadTags">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <span class="muted" style="font-size: 12px">共 {{ tags.length }} 个标签</span>
        </div>

        <!-- 标签云 chips -->
        <div class="chips mt8">
          <span
            class="chip"
            :class="{ on: activeTag === '' }"
            @click="activeTag = ''"
          >全部</span>
          <span
            v-for="t in cloudTags"
            :key="t.id ?? tagName(t)"
            class="chip"
            :class="{ on: activeTag === tagName(t) }"
            @click="toggleChip(tagName(t))"
          >
            <Icon name="tag" :size="11" />{{ tagName(t) }}
          </span>
          <span v-if="!cloudTags.length" class="muted" style="font-size: 12px">无匹配标签</span>
        </div>
      </div>
    </div>

    <!-- 明细表 -->
    <div class="card">
      <div class="card-h">标签明细 <span class="cnt">{{ tableRows.length }}</span></div>
      <el-table :data="tableRows" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <span class="tag blue"><Icon name="tag" :size="11" />{{ tagName(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据类型" min-width="150">
          <template #default="{ row }">
            <span>{{ typeLabel(tagType(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" min-width="120">
          <template #default="{ row }">{{ tagUser(row) || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ fmtTime(tagTime(row)) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <button class="btn xs" @click="openEdit(row)"><Icon name="edit" :size="11" />编辑</button>
            <button class="btn xs danger" style="margin-left: 6px" @click="removeTag(row)">
              <Icon name="trash" :size="11" />删除
            </button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无标签数据" :image-size="90" /></template>
      </el-table>
    </div>

    <!-- 新增 / 编辑标签 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'edit' ? '编辑标签' : '新增标签'"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="标签名称" required>
          <el-input v-model="dialog.form.tagName" placeholder="请输入标签名称" clearable />
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="dialog.form.dataType" placeholder="请选择数据类型" clearable style="width: 100%">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.tagDesc" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn" @click="dialog.visible = false">取消</button>
        <button class="btn primary" style="margin-left: 8px" @click="save">确定</button>
      </template>
    </el-dialog>

    <!-- 批量打标 -->
    <el-dialog v-model="batch.visible" title="批量打标" width="520px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="选择资源" required>
          <el-select
            v-model="batch.resourceId"
            placeholder="请选择资源（前 50 条）"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="r in resourceOptions"
              :key="r.id"
              :label="r.layerName || r.name || String(r.id)"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" required>
          <el-select v-model="batch.tagNames" multiple placeholder="请选择标签（可多选）" style="width: 100%">
            <el-option v-for="t in tags" :key="t.id ?? tagName(t)" :label="tagName(t)" :value="tagName(t)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn" @click="batch.visible = false">取消</button>
        <button class="btn primary" style="margin-left: 8px" @click="saveBatch">确定打标</button>
      </template>
    </el-dialog>
  </div>
</template>
