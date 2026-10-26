export type TaskStatusKey = 'wait' | 'run' | 'done' | 'fail' | 'unknown'

export interface TaskRecord {
  id?: string | number
  taskId?: string | number
  taskName?: string
  layerName?: string
  name?: string
  origin?: string | number
  status?: unknown
  storage?: string | number | boolean
  progress?: unknown
  percent?: unknown
  createBy?: string
  user?: string
  username?: string
  createDT?: string
  createTime?: string
  createDate?: string
  message?: string
  errorMessage?: string
  [key: string]: unknown
}

const statuses: Record<TaskStatusKey, { key: TaskStatusKey; label: string; type: string }> = {
  wait: { key: 'wait', label: '等待中', type: 'orange' },
  run: { key: 'run', label: '处理中', type: 'blue' },
  done: { key: 'done', label: '已完成', type: 'green' },
  fail: { key: 'fail', label: '失败', type: 'red' },
  unknown: { key: 'unknown', label: '状态未知', type: 'gray' },
}

const aliases: Record<string, TaskStatusKey> = {
  '0': 'run', '1': 'done', '2': 'fail', '-1': 'fail',
  SUCCESS: 'done', FINISH: 'done', FINISHED: 'done', DONE: 'done', COMPLETE: 'done', COMPLETED: 'done',
  成功: 'done', 完成: 'done', 已完成: 'done',
  FAIL: 'fail', FAILED: 'fail', ERROR: 'fail', EXCEPTION: 'fail', 失败: 'fail', 异常: 'fail',
  RUNNING: 'run', PROCESSING: 'run', DOING: 'run', EXECUTING: 'run', 进行中: 'run', 处理中: 'run', 运行中: 'run',
  WAIT: 'wait', WAITING: 'wait', PENDING: 'wait', QUEUE: 'wait', QUEUED: 'wait',
  等待: 'wait', 排队: 'wait', 待处理: 'wait', 未开始: 'wait',
}

/** ToolHis uses 0=processing, 1=success and 2=failure; unknown values remain unknown. */
export function taskStatus(status: unknown) {
  return statuses[aliases[String(status ?? '').trim().toUpperCase()] ?? 'unknown']
}

export function taskId(task: TaskRecord | null | undefined): string {
  return String(task?.id ?? task?.taskId ?? '')
}

export function taskName(task: TaskRecord | null | undefined): string {
  return String(task?.taskName ?? task?.layerName ?? task?.name ?? '未命名任务')
}

/** Missing progress is different from zero; completion itself is a reliable 100% signal. */
export function taskProgress(task: TaskRecord): number | null {
  if (taskStatus(task.status).key === 'done') return 100
  const raw = task.progress ?? task.percent
  if (raw === null || raw === undefined || raw === '' || typeof raw === 'boolean') return null
  const value = Number(raw)
  return Number.isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : null
}

export function taskStored(task: TaskRecord): boolean {
  return task.storage === true || String(task.storage) === '1'
}

export function taskStorageHint(task: TaskRecord): string {
  if (!taskId(task)) return '缺少任务标识，请刷新后重试'
  if (taskStored(task)) return '成果已入库，可前往数据管理查看'
  if (taskStatus(task.status).key !== 'done') return '任务成功完成后才可入库'
  // Origin 10 records are reports / 3D conversions; their results use their own product entry points.
  if (String(task.origin) === '10') return '检查报告或三维成果请在对应功能中查看'
  return ''
}

export function canStoreTask(task: TaskRecord): boolean {
  return taskStorageHint(task) === ''
}

export function taskNextStep(task: TaskRecord): string {
  const state = taskStatus(task.status).key
  if (state === 'unknown') return '暂时无法确认任务状态，请刷新后再操作。'
  if (state === 'fail') return '任务未完成，请检查源数据与处理参数后重新创建任务。'
  if (state === 'wait' || state === 'run') return '任务正在处理，可开启自动刷新查看最新状态。'
  return taskStorageHint(task) || '处理成果已就绪，选择目标目录入库后可在数据管理中使用。'
}

export function taskDeletionMessage(task: TaskRecord): string {
  const state = taskStatus(task.status).key
  if (state === 'run' || state === 'wait') return '该任务仍在处理中。删除只移除任务记录，不会取消后台处理，之后无法在这里继续跟踪。确认删除记录？'
  if (state === 'unknown') return '任务状态尚未确认。删除只移除任务记录，不会取消后台处理或删除已入库数据。确认删除记录？'
  return '删除后将无法在任务中心查看这条记录，已入库的数据仍可在数据管理中使用。确认删除记录？'
}

export function lastTaskPage(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(Math.max(0, total) / Math.max(1, pageSize)))
}
