<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHead from '@/components/common/PageHead.vue'
import Icon from '@/components/common/Icon.vue'
import { fmtTime, ORIGIN_NAME } from '@/utils/format'
import { imEnabled } from '@/utils/im'
import { useTaskCenter } from '@/features/tasks/useTaskCenter'
import {
  canStoreTask, taskId, taskName, taskNextStep, taskProgress,
  taskStatus, taskStorageHint, taskStored, type TaskRecord,
} from '@/features/tasks/model'

const router = useRouter()
const {
  list, loading, page, originFilter, autoRefresh, listError, updatedAt, deletingIds,
  refresh, pickOrigin, onPageChange, removeRow, pageSummary,
  storageVisible, storageRow, storageTargetId, storageTargets, storageTargetsLoading, storageTargetsError,
  storageSubmitting, loadStorageTargets, openStorage, confirmStorage,
} = useTaskCenter()
const originChips = Object.entries(ORIGIN_NAME).map(([value, label]) => ({ value, label }))
const refreshLabel = computed(() => `自动刷新 · ${imEnabled() ? '15' : '5'} 秒`)
const viewVisible = ref(false)
const viewRow = ref<TaskRecord | null>(null)
// Keep the open detail panel in sync with refreshed rows.
const detailTask = computed(() => list.value.find((row) => taskId(row) === taskId(viewRow.value)) ?? viewRow.value)

function rowOrigin(row: TaskRecord) {
  return ORIGIN_NAME[String(row.origin)] || '其他处理任务'
}
function rowCreator(row: TaskRecord) {
  return row.createBy ?? row.user ?? row.username ?? '—'
}
function rowTime(row: TaskRecord) {
  return fmtTime(row.createDT ?? row.createTime ?? row.createDate)
}
function openView(row: TaskRecord) {
  viewRow.value = row
  viewVisible.value = true
}
</script>

<template>
  <div>
    <PageHead title="任务中心" sub="跟踪数据处理进度，将已完成的成果入库并继续使用。">
      <template #tools>
        <button class="btn sm" :disabled="loading" @click="refresh"><Icon name="refresh" :size="13" />刷新</button>
        <span class="muted refresh-label">{{ refreshLabel }}</span>
        <el-switch v-model="autoRefresh" size="small" aria-label="自动刷新任务状态" />
      </template>
    </PageHead>

    <div class="filters">
      <div class="chips" aria-label="按处理类型筛选">
        <button class="chip" :class="{ on: originFilter === '' }" :aria-pressed="originFilter === ''" @click="pickOrigin('')">全部</button>
        <button v-for="chip in originChips" :key="chip.value" class="chip" :class="{ on: originFilter === chip.value }"
          :aria-pressed="originFilter === chip.value" @click="pickOrigin(chip.value)">{{ chip.label }}</button>
      </div>
    </div>

    <el-alert v-if="listError" type="error" :closable="false" show-icon class="task-alert">
      <template #title>任务列表加载失败</template>
      <p>{{ listError }}<button class="btn xs" :disabled="loading" @click="refresh">重新加载</button></p>
    </el-alert>

    <div class="card">
      <div class="card-h task-card-head">
        <span class="row"><Icon name="list" :size="14" />任务列表<span class="cnt">{{ page.total }}</span></span>
        <span class="muted task-list-meta">
          <span v-if="pageSummary.done">当前页 {{ pageSummary.done }} 项成果待入库 · </span>
          {{ updatedAt ? `更新于 ${fmtTime(updatedAt, 'HH:mm:ss')}` : '等待加载' }}
        </span>
      </div>
      <div class="card-b">
        <el-table :data="list" v-loading="loading" size="small" border :row-key="taskId">
          <el-table-column label="任务名称" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ taskName(row) }}</template>
          </el-table-column>
          <el-table-column label="处理类型" width="160" show-overflow-tooltip>
            <template #default="{ row }"><span class="tag violet">{{ rowOrigin(row) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><span class="tag" :class="taskStatus(row.status).type">{{ taskStatus(row.status).label }}</span></template>
          </el-table-column>
          <el-table-column label="进度" width="160">
            <template #default="{ row }">
              <el-progress v-if="taskProgress(row) !== null" :percentage="taskProgress(row) ?? 0"
                :status="taskStatus(row.status).key === 'fail' ? 'exception' : undefined" />
              <span v-else class="muted">暂未提供进度</span>
            </template>
          </el-table-column>
          <el-table-column label="成果" width="100">
            <template #default="{ row }">
              <span v-if="taskStored(row)" class="tag green">已入库</span>
              <span v-else-if="canStoreTask(row)" class="tag orange">待入库</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="创建人" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ rowCreator(row) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ rowTime(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-tooltip :content="taskStorageHint(row)" :disabled="canStoreTask(row)" placement="top">
                <span><button class="btn xs" :disabled="!canStoreTask(row) || storageSubmitting" @click="openStorage(row)"><Icon name="db" :size="12" />成果入库</button></span>
              </el-tooltip>
              <button class="btn xs" @click="openView(row)"><Icon name="eye" :size="12" />查看</button>
              <button class="btn xs danger" :disabled="deletingIds.has(taskId(row)) || !taskId(row)" @click="removeRow(row)"><Icon name="trash" :size="12" />删除</button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="task-empty">
              <strong>{{ listError ? '暂时无法加载任务' : originFilter ? '该类型暂无任务' : '还没有处理任务' }}</strong>
              <p>{{ listError ? '请重新加载任务列表，稍后再试。' : originFilter ? '可以切换其他处理类型，或前往工具箱创建任务。' : '从工具箱发起裁剪、缓冲区等数据处理，完成后在这里将成果入库。' }}</p>
              <div class="row task-empty-actions">
                <button v-if="originFilter" class="btn sm" @click="pickOrigin('')">查看全部任务</button>
                <button v-if="!listError" class="btn sm primary" @click="router.push('/tools')">前往工具箱</button>
              </div>
            </div>
          </template>
        </el-table>
        <div v-if="page.total" class="row mt8" style="justify-content: flex-end">
          <el-pagination layout="total, prev, pager, next" :total="page.total" :page-size="page.pageSize"
            :current-page="page.pageIndex" @current-change="onPageChange" />
        </div>
      </div>
    </div>

    <el-dialog v-model="storageVisible" title="成果入库" width="min(460px, calc(100vw - 32px))"
      :close-on-click-modal="!storageSubmitting" :close-on-press-escape="!storageSubmitting" :show-close="!storageSubmitting">
      <p class="dialog-hint">选择成果所在目录，入库后可在数据管理中查看和发布。</p>
      <el-form label-width="80px" size="small">
        <el-form-item label="处理成果"><span>{{ taskName(storageRow) }}</span></el-form-item>
        <el-form-item label="目标目录">
          <el-select v-model="storageTargetId" :loading="storageTargetsLoading" :disabled="storageSubmitting"
            placeholder="请选择目标目录" filterable style="width: 100%">
            <el-option v-for="target in storageTargets" :key="target.id" :label="target.label" :value="target.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="storageTargetsError" class="target-error" role="alert">
        <span>{{ storageTargetsError }}</span><button class="btn xs" :disabled="storageTargetsLoading" @click="loadStorageTargets">重试</button>
      </div>
      <p v-else-if="!storageTargetsLoading && !storageTargets.length" class="dialog-hint">暂无可用目录，请先在图层配置中创建目录。</p>
      <template #footer>
        <button class="btn sm" :disabled="storageSubmitting" @click="storageVisible = false">取消</button>
        <button class="btn sm primary" :disabled="storageSubmitting || storageTargetsLoading || !storageTargetId" @click="confirmStorage">
          {{ storageSubmitting ? '正在入库…' : '确认入库' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewVisible" title="任务详情" width="min(540px, calc(100vw - 32px))" @closed="viewRow = null">
      <template v-if="detailTask">
        <div class="kv">
          <span class="k">任务 ID</span><span class="v mono">{{ taskId(detailTask) }}</span>
          <span class="k">任务名称</span><span class="v">{{ taskName(detailTask) }}</span>
          <span class="k">处理类型</span><span class="v">{{ rowOrigin(detailTask) }}</span>
          <span class="k">状态</span><span class="v"><span class="tag" :class="taskStatus(detailTask.status).type">{{ taskStatus(detailTask.status).label }}</span></span>
          <template v-if="taskStatus(detailTask.status).key === 'unknown'">
            <span class="k">原始状态</span><span class="v">{{ detailTask.status || '暂未返回' }}</span>
          </template>
          <span class="k">进度</span><span class="v">{{ taskProgress(detailTask) === null ? '暂未提供进度' : `${taskProgress(detailTask)}%` }}</span>
          <span class="k">创建人</span><span class="v">{{ rowCreator(detailTask) }}</span>
          <span class="k">创建时间</span><span class="v">{{ rowTime(detailTask) }}</span>
          <template v-if="detailTask.errorMessage || detailTask.message">
            <span class="k">处理信息</span><span class="v">{{ detailTask.errorMessage || detailTask.message }}</span>
          </template>
        </div>
        <p class="task-next-step">{{ taskNextStep(detailTask) }}</p>
      </template>
      <template #footer>
        <button class="btn sm" @click="viewVisible = false">关闭</button>
        <button v-if="detailTask && taskStored(detailTask)" class="btn sm primary" @click="router.push('/manage')">前往数据管理</button>
        <button v-else-if="detailTask && canStoreTask(detailTask)" class="btn sm primary" @click="viewVisible = false; openStorage(detailTask)">成果入库</button>
        <button v-else-if="detailTask && taskStatus(detailTask.status).key === 'fail'" class="btn sm primary" @click="router.push('/tools')">重新创建任务</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filters { padding-bottom: 10px; }
.filters .chip { font: inherit; }
.task-alert { margin-bottom: 12px; }
.task-alert p { margin: 4px 0 0; }
.task-alert .btn { margin-left: 12px; }
.task-card-head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.task-list-meta, .refresh-label { font-size: 12px; font-weight: 400; }
.task-empty { padding: 30px 16px; line-height: 1.7; }
.task-empty strong { color: var(--ink); font-size: 14px; }
.task-empty p { margin: 6px 0 14px; }
.task-empty-actions { justify-content: center; }
.dialog-hint { margin: 0 0 16px; color: var(--ink-3); font-size: 12px; line-height: 1.7; }
.target-error { display: flex; align-items: center; gap: 8px; color: var(--el-color-danger); font-size: 12px; }
.task-next-step { margin: 20px 0 0; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; line-height: 1.7; }
@media (max-width: 640px) { .task-list-meta { width: 100%; } }
</style>
