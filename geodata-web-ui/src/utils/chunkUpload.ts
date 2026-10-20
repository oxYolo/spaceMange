import { model3dApi, uploadApi } from '@/api'
import { guid } from '@/utils/format'

/** 分片大小（5MiB） */
export const UPLOAD_CHUNK_SIZE = 5 * 1024 * 1024

export type UploadProgress = (percent: number, text?: string) => void

/** 分片上传（layerContent 通道，5MB 分片） */
export async function chunkedUpload(file: File, onProgress: UploadProgress): Promise<any> {
  const gid = guid()
  const CHUNK = UPLOAD_CHUNK_SIZE
  const totalParts = Math.max(1, Math.ceil(file.size / CHUNK))
  for (let i = 0; i < totalParts; i++) {
    const blob = file.slice(i * CHUNK, Math.min((i + 1) * CHUNK, file.size))
    await uploadApi.addChunk(blob, i + 1, gid)
    onProgress(Math.round(((i + 1) / totalParts) * 100), `正在上传分片 ${i + 1} / ${totalParts}`)
  }
  return uploadApi.fileMerge(file.name, gid)
}

/** 三维分片上传（model3d 协议：init → part → complete；分片 5MiB，非尾片 ≥5MiB） */
export async function m3dUpload(
  file: File,
  opts: { name: string; modelType: string; srcFormat: string; srs?: string; userCode?: string },
  onProgress: UploadProgress,
): Promise<string> {
  const CHUNK = UPLOAD_CHUNK_SIZE
  const partCount = Math.max(1, Math.ceil(file.size / CHUNK))
  onProgress(0, '正在初始化上传…')
  const id: any = await model3dApi.init({
    name: opts.name.trim(),
    modelType: opts.modelType,
    srcFormat: opts.srcFormat,
    fileName: file.name,
    totalSize: file.size,
    partCount,
    srs: opts.srs || undefined,
    // 无登录态时后端用它兜底 createUser（决定 SignalR 进度推送目标）
    userCode: opts.userCode,
  })
  const realId = String(id ?? '')
  if (!realId) throw new Error('初始化上传失败：未返回数据 id')
  for (let i = 0; i < partCount; i++) {
    const blob = file.slice(i * CHUNK, Math.min((i + 1) * CHUNK, file.size))
    await model3dApi.uploadPart(realId, i + 1, blob)
    onProgress(Math.round(((i + 1) / partCount) * 100), `正在上传分片 ${i + 1} / ${partCount}`)
  }
  onProgress(100, '正在完成上传…')
  await model3dApi.complete(realId)
  return realId
}

/** 三维断点续传（原三维数据页功能）：GET parts → 跳过已传 → 续传 → complete */
export async function m3dResume(taskId: string, file: File, onProgress: UploadProgress): Promise<void> {
  const tid = taskId.trim()
  if (!tid) throw new Error('请输入原上传任务 id（init 返回的数据 id）')
  if (!file) throw new Error('请选择与原任务相同的文件')
  onProgress(0, '正在查询已传分片…')
  const done: number[] = (await model3dApi.parts(tid)) as any
  const doneSet = new Set<number>((Array.isArray(done) ? done : []).map(Number))
  const CHUNK = UPLOAD_CHUNK_SIZE
  const partCount = Math.max(1, Math.ceil(file.size / CHUNK))
  let last = 0
  for (let n = 1; n <= partCount; n++) {
    if (doneSet.has(n)) {
      last = n
      continue
    }
    if (last && n > last + 1) throw new Error(`分片 ${last + 1} 缺失，请确认与原任务为同一文件后重试`)
    const blob = file.slice((n - 1) * CHUNK, Math.min(n * CHUNK, file.size))
    await model3dApi.uploadPart(tid, n, blob)
    last = n
    onProgress(Math.round((n / partCount) * 100), `正在续传分片 ${n} / ${partCount}（已跳过 ${doneSet.size} 片）`)
  }
  onProgress(100, '正在完成上传…')
  await model3dApi.complete(tid)
}
