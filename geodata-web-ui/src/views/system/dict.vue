<script setup lang="ts">
/**
 * 字典管理（系统）
 * 后端：DicDataTypeController（5 端点）：GetAllList / Insert / Update / Delete
 *       DicDataCodeController（6 端点）：GetDetailByTypeID 按分类取字典项 / Insert / Update / Delete
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { dictApi } from '@/api'
import { pickList } from '@/utils/format'

const typeLoading = ref(false)
const codeLoading = ref(false)
const typeList = ref<any[]>([]) // 字典分类
const codeList = ref<any[]>([]) // 当前分类下的字典项
const selectedId = ref<string>('')
const codeKeyword = ref('') // 字典项搜索关键字（GetDetailByConn.keyWord）
const selectedType = computed(() => typeList.value.find((t) => String(t.id) === selectedId.value) || null)

/** 载入字典分类（dicDataType/GetAllList） */
async function loadTypes() {
  typeLoading.value = true
  try {
    const res = await dictApi.typeList()
    typeList.value = pickList(res)
    if (selectedId.value && !typeList.value.some((t) => String(t.id) === selectedId.value)) {
      selectedId.value = ''
      codeList.value = []
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    typeLoading.value = false
  }
}

/** 载入当前分类下的字典项（dicDataCode/GetDetailByTypeID） */
async function loadCodes() {
  if (!selectedId.value) {
    codeList.value = []
    return
  }
  codeLoading.value = true
  try {
    const res = await dictApi.codeByTypeID(selectedId.value)
    codeList.value = pickList(res)
    codeKeyword.value = ''
  } catch (e: any) {
    ElMessage.error(e?.message || '字典项加载失败')
  } finally {
    codeLoading.value = false
  }
}

/** 按关键字搜索字典项（dicDataCode/GetDetailByConn? typeID & keyWord） */
async function searchCodes() {
  if (!selectedId.value) {
    ElMessage.warning('请先选择字典分类')
    return
  }
  const kw = codeKeyword.value.trim()
  if (!kw) {
    await loadCodes()
    return
  }
  codeLoading.value = true
  try {
    const res = await dictApi.codeByConn(selectedId.value, kw)
    codeList.value = pickList(res)
  } catch (e: any) {
    ElMessage.error(e?.message || '搜索失败')
  } finally {
    codeLoading.value = false
  }
}

function selectType(t: any) {
  selectedId.value = String(t.id)
  loadCodes()
}

/* ---------------- 字典分类弹窗 ---------------- */
const typeDialog = reactive({
  visible: false,
  mode: 'add' as 'add' | 'edit',
  form: { id: '', typeName: '', typeDesc: '' } as Record<string, any>,
})

function openAddType() {
  typeDialog.mode = 'add'
  typeDialog.form = { id: '', typeName: '', typeDesc: '' }
  typeDialog.visible = true
}

function openEditType() {
  if (!selectedType.value) return ElMessage.warning('请先选择字典分类')
  typeDialog.mode = 'edit'
  typeDialog.form = {
    id: String(selectedType.value.id),
    typeName: selectedType.value.typeName ?? '',
    typeDesc: selectedType.value.typeDesc ?? '',
  }
  typeDialog.visible = true
}

/** 保存字典分类（typeInsert / typeUpdate） */
async function saveType() {
  if (!typeDialog.form.typeName) return ElMessage.warning('请填写字典分类名称')
  typeLoading.value = true
  try {
    const body = { typeName: typeDialog.form.typeName, typeDesc: typeDialog.form.typeDesc }
    if (typeDialog.mode === 'edit') {
      await dictApi.typeUpdate({ ID: typeDialog.form.id, ...body })
      ElMessage.success('修改成功')
    } else {
      await dictApi.typeInsert(body)
      ElMessage.success('新增成功')
    }
    typeDialog.visible = false
    await loadTypes()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    typeLoading.value = false
  }
}

/** 删除字典分类（typeDelete） */
async function removeType() {
  if (!selectedType.value) return ElMessage.warning('请先选择字典分类')
  try {
    await ElMessageBox.confirm(`确定删除字典分类「${selectedType.value.typeName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  typeLoading.value = true
  try {
    await dictApi.typeDelete(String(selectedType.value.id))
    ElMessage.success('删除成功')
    selectedId.value = ''
    codeList.value = []
    await loadTypes()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  } finally {
    typeLoading.value = false
  }
}

/* ---------------- 字典项弹窗 ---------------- */
const codeDialog = reactive({
  visible: false,
  mode: 'add' as 'add' | 'edit',
  form: { id: '', code: '', codeName: '', description: '', sort: 0 } as Record<string, any>,
})

function openAddCode() {
  if (!selectedId.value) return ElMessage.warning('请先选择左侧字典分类')
  codeDialog.mode = 'add'
  codeDialog.form = { id: '', code: '', codeName: '', description: '', sort: 0 }
  codeDialog.visible = true
}

function openEditCode(row: any) {
  codeDialog.mode = 'edit'
  codeDialog.form = {
    id: String(row.id),
    code: row.code ?? '',
    codeName: row.codeName ?? '',
    description: row.description ?? '',
    sort: Number(row.sort ?? 0),
  }
  codeDialog.visible = true
}

/** 保存字典项（codeInsert / codeUpdate，typeID 取当前分类） */
async function saveCode() {
  if (!codeDialog.form.code) return ElMessage.warning('请填写代码')
  if (!codeDialog.form.codeName) return ElMessage.warning('请填写名称')
  codeLoading.value = true
  try {
    const body: Record<string, any> = {
      code: codeDialog.form.code,
      codeName: codeDialog.form.codeName,
      description: codeDialog.form.description,
      sort: Number(codeDialog.form.sort) || 0,
      typeID: selectedId.value,
    }
    if (codeDialog.mode === 'edit') {
      await dictApi.codeUpdate({ ID: codeDialog.form.id, ...body })
      ElMessage.success('修改成功')
    } else {
      await dictApi.codeInsert(body)
      ElMessage.success('新增成功')
    }
    codeDialog.visible = false
    await loadCodes()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    codeLoading.value = false
  }
}

/** 删除字典项（codeDelete） */
async function removeCode(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除字典项「${row.codeName || row.code}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  codeLoading.value = true
  try {
    await dictApi.codeDelete(String(row.id))
    ElMessage.success('删除成功')
    await loadCodes()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  } finally {
    codeLoading.value = false
  }
}

onMounted(() => {
  loadTypes()
})
</script>

<template>
  <div>
    <PageHead title="字典管理" sub="DicDataTypeController 5 端点 + DicDataCodeController 6 端点">
      <template #tools>
        <button class="btn" @click="loadTypes"><Icon name="refresh" :size="13" />刷新分类</button>
      </template>
    </PageHead>

    <div class="row">
      <!-- 左栏：字典分类 -->
      <div class="card" style="width: 300px; flex: 0 0 300px">
        <div class="card-h">
          字典分类
          <span class="cnt">{{ typeList.length }}</span>
          <div class="tools">
            <button class="btn xs primary" @click="openAddType"><Icon name="plus" :size="11" />新增</button>
            <button class="btn xs" @click="openEditType"><Icon name="edit" :size="11" />编辑</button>
            <button class="btn xs danger" @click="removeType"><Icon name="trash" :size="11" />删除</button>
          </div>
        </div>
        <div class="card-b" v-loading="typeLoading" style="max-height: 68vh; overflow: auto; padding: 8px">
          <div v-if="typeList.length" class="col" style="gap: 6px">
            <div
              v-for="t in typeList"
              :key="t.id"
              class="chip"
              :class="{ on: String(t.id) === selectedId }"
              style="width: 100%; justify-content: flex-start; height: 32px"
              @click="selectType(t)"
            >
              <Icon name="list" :size="12" />
              <span>{{ t.typeName || t.id }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无字典分类" :image-size="90" />
        </div>
      </div>

      <!-- 右栏：字典项 -->
      <div class="card flex1">
        <div class="card-h">
          字典项
          <span v-if="selectedType" class="tag blue">{{ selectedType.typeName }}</span>
          <span class="cnt">{{ codeList.length }}</span>
          <div class="tools">
            <el-input
              v-model="codeKeyword"
              placeholder="按代码/名称搜索"
              size="small"
              clearable
              style="width: 170px"
              @keyup.enter="searchCodes"
              @clear="loadCodes"
            >
              <template #prefix><Icon name="search" :size="12" /></template>
            </el-input>
            <button class="btn xs" @click="searchCodes"><Icon name="search" :size="11" />搜索</button>
            <button class="btn xs primary" @click="openAddCode"><Icon name="plus" :size="11" />新增字典项</button>
            <button class="btn xs" @click="loadCodes"><Icon name="refresh" :size="11" />刷新</button>
          </div>
        </div>
        <div class="card-b" style="padding: 0">
          <template v-if="selectedType">
            <el-table :data="codeList" v-loading="codeLoading" stripe style="width: 100%">
              <el-table-column label="代码" min-width="140">
                <template #default="{ row }"><span class="mono">{{ row.code ?? '-' }}</span></template>
              </el-table-column>
              <el-table-column label="名称" min-width="180">
                <template #default="{ row }">{{ row.codeName ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="描述" min-width="180">
                <template #default="{ row }">{{ row.description || '-' }}</template>
              </el-table-column>
              <el-table-column label="排序" width="90">
                <template #default="{ row }">{{ row.sort ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <button class="btn xs" @click="openEditCode(row)"><Icon name="edit" :size="11" />编辑</button>
                  <button class="btn xs danger" style="margin-left: 6px" @click="removeCode(row)">
                    <Icon name="trash" :size="11" />删除
                  </button>
                </template>
              </el-table-column>
              <template #empty><el-empty description="该分类下暂无字典项" :image-size="80" /></template>
            </el-table>
          </template>
          <el-empty v-else description="请选择左侧字典分类" :image-size="100" style="padding: 30px 0" />
        </div>
      </div>
    </div>

    <!-- 字典分类弹窗 -->
    <el-dialog
      v-model="typeDialog.visible"
      :title="typeDialog.mode === 'edit' ? '编辑字典分类' : '新增字典分类'"
      width="460px"
      destroy-on-close
    >
      <el-form label-width="90px">
        <el-form-item label="分类名称" required>
          <el-input v-model="typeDialog.form.typeName" placeholder="请输入分类名称" clearable />
        </el-form-item>
        <el-form-item label="分类描述">
          <el-input v-model="typeDialog.form.typeDesc" type="textarea" :rows="2" placeholder="请输入分类描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn" @click="typeDialog.visible = false">取消</button>
        <button class="btn primary" style="margin-left: 8px" @click="saveType">确定</button>
      </template>
    </el-dialog>

    <!-- 字典项弹窗 -->
    <el-dialog
      v-model="codeDialog.visible"
      :title="codeDialog.mode === 'edit' ? '编辑字典项' : '新增字典项'"
      width="460px"
      destroy-on-close
    >
      <el-form label-width="90px">
        <el-form-item label="代码" required>
          <el-input v-model="codeDialog.form.code" placeholder="代码值 code" clearable />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="codeDialog.form.codeName" placeholder="名称 codeName" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="codeDialog.form.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="codeDialog.form.sort" :min="0" :controls="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn" @click="codeDialog.visible = false">取消</button>
        <button class="btn primary" style="margin-left: 8px" @click="saveCode">确定</button>
      </template>
    </el-dialog>
  </div>
</template>
