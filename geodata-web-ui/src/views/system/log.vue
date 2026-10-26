<script setup lang="ts">
import { showError } from '@/utils/feedback'
/**
 * 操作日志（系统）
 * 后端：LayerLogController（2 端点）
 *   getList(pageSize,pageIndex) 分页查询 / delete 删除
 * 注：后端无筛选参数，操作人 / 操作对象为前端本地过滤。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { layerLogApi } from '@/api'
import { pickList, pickTotal, fmtTime } from '@/utils/format'

const loading = ref(false)
const rows = ref<any[]>([]) // 当前页日志
const total = ref(0)
const pageSize = ref(20)
const pageIndex = ref(1)

const filterUser = ref('') // 操作人（本地过滤）
const filterObject = ref('') // 操作对象（本地过滤）

/* 字段兼容取值（后端字段命名不统一） */
const fObject = (r: any) => r.objectName ?? r.object ?? '-'
const fType = (r: any) => r.operType ?? r.operationType ?? '-'
const fUser = (r: any) => r.createBy ?? r.operateBy ?? '-'
const fTime = (r: any) => r.createDT ?? r.operateTime ?? ''
const fDesc = (r: any) => r.description ?? r.remark ?? '-'

/** 载入日志分页（getList） */
async function loadLogs() {
  loading.value = true
  try {
    const res = await layerLogApi.getList(pageSize.value, pageIndex.value)
    rows.value = pickList(res)
    total.value = pickTotal(res, rows.value.length)
  } catch (e: any) {
    showError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

/** 本地过滤后的表格数据 */
const tableRows = computed(() =>
  rows.value.filter((r) => {
    if (filterUser.value && !String(fUser(r)).toLowerCase().includes(filterUser.value.toLowerCase())) return false
    if (filterObject.value && !String(fObject(r)).toLowerCase().includes(filterObject.value.toLowerCase())) return false
    return true
  }),
)

function onPageChange(p: number) {
  pageIndex.value = p
  loadLogs()
}
function onSizeChange(s: number) {
  pageSize.value = s
  pageIndex.value = 1
  loadLogs()
}

/** 刷新 */
function refresh() {
  pageIndex.value = 1
  loadLogs()
}

/** 导出当前筛选结果为 CSV（加 BOM 防 Excel 中文乱码） */
function exportCsv() {
  const header = ['序号', '操作对象', '操作类型', '操作人', '操作时间', '操作描述']
  const lines = tableRows.value.map((r, i) => [
    i + 1,
    fObject(r),
    fType(r),
    fUser(r),
    fmtTime(fTime(r)),
    fDesc(r),
  ])
  const escape = (v: any) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const csv = [header, ...lines].map((arr) => arr.map(escape).join(',')).join('\r\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'operation-log.csv'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success('已导出 operation-log.csv')
}

/** 删除日志（delete） */
async function removeLog(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该条操作日志吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await layerLogApi.remove(String(row.id))
    ElMessage.success('删除成功')
    await loadLogs()
  } catch (e: any) {
    showError(e, '删除失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadLogs()
})
</script>

<template>
  <div>
    <PageHead title="操作日志" sub="查询数据操作记录，追踪处理过程与异常原因">
      <template #tools>
        <button class="btn" @click="refresh"><Icon name="refresh" :size="13" />刷新</button>
        <button class="btn" @click="exportCsv"><Icon name="download" :size="13" />导出 CSV</button>
      </template>
    </PageHead>

    <!-- 顶部筛选（本地过滤） -->
    <div class="card mb8">
      <div class="card-b">
        <div class="filters" style="padding: 0">
          <el-input v-model="filterUser" placeholder="按操作人过滤" clearable style="width: 200px">
            <template #prefix><Icon name="search" :size="13" /></template>
          </el-input>
          <el-input v-model="filterObject" placeholder="按操作对象过滤" clearable style="width: 220px">
            <template #prefix><Icon name="search" :size="13" /></template>
          </el-input>
          <span class="muted" style="font-size: 12px">共 {{ total }} 条，当前显示 {{ tableRows.length }} 条</span>
        </div>
      </div>
    </div>

    <!-- 日志表格 -->
    <div class="card">
      <el-table :data="tableRows" v-loading="loading" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="64" />
        <el-table-column label="操作对象" min-width="160">
          <template #default="{ row }">{{ fObject(row) }}</template>
        </el-table-column>
        <el-table-column label="操作类型" min-width="120">
          <template #default="{ row }">
            <span class="tag gray">{{ fType(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" min-width="120">
          <template #default="{ row }">{{ fUser(row) }}</template>
        </el-table-column>
        <el-table-column label="操作时间" min-width="180">
          <template #default="{ row }">{{ fmtTime(fTime(row)) }}</template>
        </el-table-column>
        <el-table-column label="操作描述" min-width="240">
          <template #default="{ row }">{{ fDesc(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <button class="btn xs danger" @click="removeLog(row)"><Icon name="trash" :size="11" />删除</button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无操作日志" :image-size="90" /></template>
      </el-table>

      <div class="card-b" style="display: flex; justify-content: flex-end">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="pageIndex"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>
  </div>
</template>
