<script setup lang="ts">
import { showError } from '@/utils/feedback'
/**
 * 标签关系（编目管理）
 * 后端：TagRelationController（8 端点）
 *   GetAllList / MultiInsert 批量建立关联 / Delete 解除关系
 *   GetMultiTagNameByMapID 按地图反查标签
 *   资源下拉：LayerContentController.GetPageListByName
 *   标签下拉：DataTagController.GetAllList
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { tagRelationApi, dataTagApi, layerContentApi } from '@/api'
import { pickList, fmtTime } from '@/utils/format'
import { useUserStore } from '@/auth/store'

const userStore = useUserStore()

const loading = ref(false)
const rows = ref<any[]>([]) // 关系表
const tags = ref<any[]>([]) // 标签下拉
const resourceOptions = ref<any[]>([]) // 资源下拉
const dataIdFilter = ref('') // 按资源查（前端过滤）

function relTagName(r: any) {
  return r?.tagName ?? r?.name ?? ''
}
function relTagType(r: any) {
  return r?.dataType ?? r?.type ?? ''
}
function relUser(r: any) {
  return r?.createUser ?? r?.creator ?? ''
}
function relTime(r: any) {
  return r?.createDT ?? r?.createTime ?? ''
}

/** 前端按资源过滤 */
const tableRows = computed(() => {
  if (!dataIdFilter.value) return rows.value
  const k = dataIdFilter.value.toLowerCase()
  return rows.value.filter(
    (r) =>
      String(r.dataID ?? '').toLowerCase().includes(k) ||
      String(relTagName(r)).toLowerCase().includes(k),
  )
})

/** 载入关系表（GetAllList） */
async function loadRows() {
  loading.value = true
  try {
    const res = await tagRelationApi.getAllList()
    rows.value = pickList(res)
  } catch (e: any) {
    showError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 载入标签 + 资源下拉 */
async function loadOptions() {
  try {
    const [tagRes, resRes] = await Promise.all([
      dataTagApi.getAllList(),
      layerContentApi.getPageListByName({ createBy: userStore.userName }, 50, 1),
    ])
    tags.value = pickList(tagRes)
    resourceOptions.value = pickList(resRes)
  } catch (e: any) {
    showError(e, '下拉数据加载失败')
  }
}

/* ---------------- 批量建立关联 ---------------- */
const batch = reactive({ visible: false, resourceId: '', tagNames: [] as string[] })

function openBatch() {
  batch.resourceId = ''
  batch.tagNames = []
  batch.visible = true
}

/** MultiInsert 批量建立关联 */
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
    ElMessage.success('关联建立成功')
    batch.visible = false
    await loadRows()
  } catch (e: any) {
    showError(e, '关联建立失败')
  } finally {
    loading.value = false
  }
}

/* ---------------- 按地图反查 ---------------- */
/** GetMultiTagNameByMapID：输入 mapLayerID，返回该地图的全部标签名 */
async function queryByMap() {
  try {
    const { value } = await ElMessageBox.prompt('请输入地图图层 ID（mapLayerID）', '按地图反查标签', {
      confirmButtonText: '查询',
      cancelButtonText: '取消',
      inputPlaceholder: 'mapLayerID',
    })
    if (!value) return
    loading.value = true
    try {
      const res = await tagRelationApi.getMultiTagNameByMapID(String(value).trim())
      const text = Array.isArray(res) ? res.join('、') : String(res ?? '（无结果）')
      await ElMessageBox.alert(text || '（无结果）', '查询结果', { confirmButtonText: '知道了' })
    } catch (e: any) {
      showError(e, '查询失败')
    } finally {
      loading.value = false
    }
  } catch {
    /* 取消输入 */
  }
}

/** 解除关系（Delete） */
async function removeRel(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定解除资源「${row.dataID}」与标签「${relTagName(row)}」的关系吗？`,
      '解除确认',
      { type: 'warning', confirmButtonText: '解除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await tagRelationApi.remove(String(row.id))
    ElMessage.success('已解除关系')
    await loadRows()
  } catch (e: any) {
    showError(e, '解除失败')
  } finally {
    loading.value = false
  }
}

/* ---------------- 编辑关系（Update） ---------------- */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ id: '', tagName: '', dataID: '', mapLayerID: '', createUser: userStore.userName })

function openEdit(row: any) {
  editForm.id = String(row.id ?? '')
  editForm.tagName = relTagName(row)
  editForm.dataID = String(row.dataID ?? '')
  editForm.mapLayerID = String(row.mapLayerID ?? '')
  editForm.createUser = String(row.createUser ?? relUser(row) ?? userStore.userName)
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.id) return
  if (!editForm.tagName.trim()) return ElMessage.warning('标签名不能为空')
  editSaving.value = true
  try {
    // POST /tagReleation/Update：Body: TagRelationDto
    await tagRelationApi.update({
      id: editForm.id,
      tagName: editForm.tagName.trim(),
      dataID: editForm.dataID,
      mapLayerID: editForm.mapLayerID,
      createUser: editForm.createUser,
    })
    ElMessage.success('关系已更新')
    editVisible.value = false
    await loadRows()
  } catch (e: any) {
    showError(e, '更新失败')
  } finally {
    editSaving.value = false
  }
}

onMounted(() => {
  loadRows()
  loadOptions()
})
</script>

<template>
  <div>
    <PageHead title="标签关系" sub="建立标签与资源的关联，按业务主题组织和检索数据">
      <template #tools>
        <button class="btn primary" @click="openBatch"><Icon name="plus" :size="13" />批量建立关联</button>
        <button class="btn" @click="queryByMap"><Icon name="search" :size="13" />按地图反查</button>
        <button class="btn" @click="loadRows"><Icon name="refresh" :size="13" />刷新</button>
      </template>
    </PageHead>

    <!-- 三个实体语义说明 -->
    <div class="row wrap mb8">
      <div class="card flex1">
        <div class="card-b">
          <div class="card-h" style="border: 0; padding: 0"><Icon name="list" :size="14" />分类是树</div>
          <div class="muted mt8" style="font-size: 12px">
            数据分类(DataType) 通过 parentId 自关联形成层级树，用于资源的归口管理。
          </div>
        </div>
      </div>
      <div class="card flex1">
        <div class="card-b">
          <div class="card-h" style="border: 0; padding: 0"><Icon name="tag" :size="14" />标签是扁平表</div>
          <div class="muted mt8" style="font-size: 12px">
            数据标签(DataTag) 是扁平列表，一个资源可打多个标签，用于多维度描述。
          </div>
        </div>
      </div>
      <div class="card flex1">
        <div class="card-b">
          <div class="card-h" style="border: 0; padding: 0"><Icon name="grid" :size="14" />标签关系是多对多</div>
          <div class="muted mt8" style="font-size: 12px">
            标签关系(TagRelation) 是资源与标签的多对多中间表，记录 dataID 与 tagName 的绑定。
          </div>
        </div>
      </div>
    </div>

    <!-- 关系表 -->
    <div class="card">
      <div class="card-h">
        标签关系明细 <span class="cnt">{{ tableRows.length }}</span>
        <div class="tools">
          <el-input
            v-model="dataIdFilter"
            placeholder="按资源/标签过滤"
            clearable
            size="small"
            style="width: 220px"
          >
            <template #prefix><Icon name="search" :size="12" /></template>
          </el-input>
        </div>
      </div>
      <el-table :data="tableRows" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="标签名称" min-width="160">
          <template #default="{ row }">
            <span class="tag violet"><Icon name="tag" :size="11" />{{ relTagName(row) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据类型" min-width="120">
          <template #default="{ row }">{{ relTagType(row) || '-' }}</template>
        </el-table-column>
        <el-table-column label="资源 ID (dataID)" min-width="180">
          <template #default="{ row }"><span class="mono">{{ row.dataID ?? '-' }}</span></template>
        </el-table-column>
        <el-table-column label="地图 ID (mapLayerID)" min-width="180">
          <template #default="{ row }"><span class="mono">{{ row.mapLayerID || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="创建人" min-width="110">
          <template #default="{ row }">{{ relUser(row) || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ fmtTime(relTime(row)) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <button class="btn xs" @click="openEdit(row)"><Icon name="edit" :size="11" />编辑</button>
            <button class="btn xs danger" @click="removeRel(row)"><Icon name="trash" :size="11" />解除</button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无标签关系" :image-size="90" /></template>
      </el-table>
    </div>

    <!-- 编辑关系 -->
    <el-dialog v-model="editVisible" title="编辑标签关系" width="480px" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item label="标签名" required>
          <el-input v-model="editForm.tagName" placeholder="tagName" />
        </el-form-item>
        <el-form-item label="资源 ID">
          <el-input v-model="editForm.dataID" disabled />
        </el-form-item>
        <el-form-item label="地图图层 ID">
          <el-input v-model="editForm.mapLayerID" placeholder="mapLayerID" />
        </el-form-item>
        <el-form-item label="创建人">
          <el-input v-model="editForm.createUser" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn sm" @click="editVisible = false">取消</button>
        <button class="btn sm primary" :disabled="editSaving" @click="saveEdit">保存</button>
      </template>
    </el-dialog>

    <!-- 批量建立关联 -->
    <el-dialog v-model="batch.visible" title="批量建立关联" width="520px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="选择资源" required>
          <el-select v-model="batch.resourceId" placeholder="请选择资源（前 50 条）" filterable style="width: 100%">
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
            <el-option
              v-for="t in tags"
              :key="t.id ?? relTagName(t)"
              :label="relTagName(t)"
              :value="relTagName(t)"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn" @click="batch.visible = false">取消</button>
        <button class="btn primary" style="margin-left: 8px" @click="saveBatch">确定建立</button>
      </template>
    </el-dialog>
  </div>
</template>
