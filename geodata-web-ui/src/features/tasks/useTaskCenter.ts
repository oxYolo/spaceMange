import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dataTypeApi, layerToolApi } from '@/api'
import { usePolling } from '@/composables/usePolling'
import { showError } from '@/utils/feedback'
import { pickList, pickTotal } from '@/utils/format'
import { imEnabled, onImMessage } from '@/utils/im'
import { useUserStore } from '@/auth/store'
import { canStoreTask, lastTaskPage, taskDeletionMessage, taskId, taskStorageHint, type TaskRecord } from './model'

interface StorageTarget {
  id: string
  label: string
}

function flattenTargets(nodes: Record<string, any>[], depth = 0): StorageTarget[] {
  return nodes.flatMap((node) => {
    const id = taskId(node)
    const label = String(node.typeName ?? node.layerName ?? node.name ?? '未命名目录')
    const current = id ? [{ id, label: `${'　'.repeat(depth)}${label}` }] : []
    return [...current, ...flattenTargets(node.children || [], depth + 1)]
  })
}

/** Task list, refresh scheduling and result actions share one lifecycle. */
export function useTaskCenter() {
  const user = useUserStore()
  const list = ref<TaskRecord[]>([])
  const page = reactive({ pageSize: 10, pageIndex: 1, total: 0 })
  const originFilter = ref('')
  const autoRefresh = ref(true)
  const listError = ref('')
  const updatedAt = ref<Date | null>(null)
  const deletingIds = ref(new Set<string>())
  const storedIds = new Set<string>()
  let revision = 0
  let disposed = false
  let imUnsub: (() => void) | undefined
  let imRefreshTimer: ReturnType<typeof setTimeout> | undefined

  const polling = usePolling(async (signal) => {
    const currentRevision = revision
    const currentPage = page.pageIndex
    try {
      const result = await layerToolApi.getList(page.pageSize, currentPage, originFilter.value || undefined, { signal, silent: true })
      if (signal.aborted || disposed || currentRevision !== revision) return
      const rows = pickList<TaskRecord>(result)
      const total = pickTotal(result, rows.length)
      const lastPage = lastTaskPage(total, page.pageSize)
      if (currentPage > lastPage) {
        page.pageIndex = lastPage
        revision += 1
        void polling.trigger()
        return
      }
      list.value = rows.map((row) => storedIds.has(taskId(row)) ? { ...row, storage: '1' } : row)
      page.total = total
      listError.value = ''
      updatedAt.value = new Date()
      const active = list.value.find((row) => taskId(row) === taskId(storageRow.value))
      if (active) storageRow.value = active
    } catch (error) {
      if (signal.aborted || disposed || currentRevision !== revision) return
      listError.value = error instanceof Error ? error.message : '任务列表加载失败，请稍后重试'
    }
  }, { enabled: autoRefresh, interval: () => imEnabled() ? 15000 : 5000 })

  function pickOrigin(origin: string) {
    if (originFilter.value === origin) return
    originFilter.value = origin
    page.pageIndex = 1
    revision += 1
    void polling.trigger()
  }

  function onPageChange(index: number) {
    if (index === page.pageIndex) return
    page.pageIndex = index
    revision += 1
    void polling.trigger()
  }

  const storageVisible = ref(false)
  const storageRow = ref<TaskRecord | null>(null)
  const storageTargetId = ref('')
  const storageTargets = ref<StorageTarget[]>([])
  const storageTargetsLoading = ref(false)
  const storageTargetsError = ref('')
  const storageSubmitting = ref(false)
  let targetRequest: Promise<void> | undefined

  function loadStorageTargets(): Promise<void> {
    if (targetRequest) return targetRequest
    storageTargetsLoading.value = true
    targetRequest = (async () => {
      try {
        const result = await dataTypeApi.getAllList()
        if (disposed) return
        storageTargets.value = flattenTargets(pickList(result))
        storageTargetsError.value = ''
      } catch (error) {
        if (disposed) return
        storageTargetsError.value = error instanceof Error ? error.message : '目标目录加载失败'
      } finally {
        storageTargetsLoading.value = false
        targetRequest = undefined
      }
    })()
    return targetRequest
  }

  async function openStorage(row: TaskRecord) {
    if (!canStoreTask(row)) {
      ElMessage.warning(taskStorageHint(row))
      return
    }
    storageRow.value = row
    storageTargetId.value = ''
    storageVisible.value = true
    if (!storageTargets.value.length) await loadStorageTargets()
  }

  async function confirmStorage() {
    const row = storageRow.value
    if (storageSubmitting.value || !row) return
    if (!canStoreTask(row)) {
      ElMessage.warning(taskStorageHint(row))
      return
    }
    if (!storageTargets.value.some((target) => target.id === storageTargetId.value)) {
      ElMessage.warning('请选择有效的目标目录')
      return
    }
    storageSubmitting.value = true
    try {
      const result = await layerToolApi.storageLayer(taskId(row), storageTargetId.value)
      if (!result || /失败|不存在|错误/.test(String(result))) throw new Error(String(result || '成果入库失败'))
      if (disposed) return
      storedIds.add(taskId(row))
      row.storage = '1'
      ElMessage.success('成果已入库，可前往数据管理查看')
      storageVisible.value = false
      await polling.trigger()
    } catch (error) {
      if (!disposed) showError(error, '成果入库失败')
    } finally {
      storageSubmitting.value = false
    }
  }

  async function removeRow(row: TaskRecord) {
    const id = taskId(row)
    if (!id || deletingIds.value.has(id)) return
    deletingIds.value.add(id)
    try {
      try {
        await ElMessageBox.confirm(taskDeletionMessage(row), '删除任务记录', {
          type: 'warning', confirmButtonText: '删除记录', cancelButtonText: '取消',
        })
      } catch {
        return
      }
      if (disposed) return
      const result = await layerToolApi.delete(id)
      if (result === false) throw new Error('任务记录未删除，请刷新后重试')
      if (disposed) return
      ElMessage.success('任务记录已删除')
      // Invalidate an in-flight list request so it cannot restore the deleted record.
      revision += 1
      list.value = list.value.filter((item) => taskId(item) !== id)
      page.total = Math.max(0, page.total - 1)
      page.pageIndex = Math.min(page.pageIndex, lastTaskPage(page.total, page.pageSize))
      await polling.trigger()
    } catch (error) {
      if (!disposed) showError(error, '删除任务记录失败')
    } finally {
      deletingIds.value.delete(id)
    }
  }

  const pageSummary = computed(() => {
    const summary = { done: 0 }
    for (const task of list.value) {
      if (canStoreTask(task)) summary.done += 1
    }
    return summary
  })

  onMounted(() => {
    imUnsub = onImMessage(user.userName || 'admin', (message) => {
      if (!['Model3dConvert', 'TopologyCheck', 'layerInsertLog'].includes(message.moduleType) || imRefreshTimer) return
      imRefreshTimer = setTimeout(() => {
        imRefreshTimer = undefined
        void polling.trigger()
      }, 1200)
    })
  })

  onBeforeUnmount(() => {
    disposed = true
    revision += 1
    if (imRefreshTimer) clearTimeout(imRefreshTimer)
    imUnsub?.()
  })

  return {
    list, loading: polling.running, page, originFilter, autoRefresh, listError, updatedAt, deletingIds,
    refresh: polling.trigger, pickOrigin, onPageChange, removeRow, pageSummary,
    storageVisible, storageRow, storageTargetId, storageTargets, storageTargetsLoading, storageTargetsError,
    storageSubmitting, loadStorageTargets, openStorage, confirmStorage,
  }
}
