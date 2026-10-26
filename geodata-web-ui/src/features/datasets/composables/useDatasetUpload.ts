import { showError } from '@/utils/feedback'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { dataTypeApi, dictApi, layerContentApi, model3dApi, regionApi } from '@/api'
import { DATA_TYPES, isRasterTypeKey } from '@/utils/datasets'
import { guid, pickList } from '@/utils/format'
import { chunkedUpload, m3dUpload, m3dResume } from '@/utils/chunkUpload'
import { acceptedDatasetFiles, datasetFormatHint, inspectShapeFiles, isShapeDataset, validateDatasetFiles } from '../uploadValidation'

interface UploadOptions {
  getUserName: () => string
  onUploaded: () => void | Promise<void>
}
const M3D_TYPES: Record<string, string> = { oblique: 'OSGB', pointcloud: 'POINTCLOUD', tileset: 'TILESET', single: 'SINGLE', bim: 'BIM' }
function m3dSrcFormat(typeKey: string, fileName: string): string {
  const ext = fileName.split('.').pop()!.toLowerCase()
  if (typeKey === 'tileset') return 'tileset'
  if (ext === 'zip') return { oblique: 'osgb', pointcloud: 'las', single: 'obj', bim: 'ifc' }[typeKey] || ''
  return ext
}

/** Upload orchestration validates the full selection before creating any server state. */
export function useDatasetUpload(options: UploadOptions) {
  const upload = reactive({
    visible: false, submitting: false, progress: 0, progressText: '',
    phase: 'idle' as 'idle' | 'checking' | 'uploading' | 'processing',
    name: '', typeKey: '', catalogId: '', layerRefence: '', srs: '', area: '', confidential: '内部',
    keywords: '', layerDesc: '', autoMap: false, srcMode: 'local' as 'local' | 'resume', resumeId: '',
    file: null as File | null, familyFiles: [] as File[],
  })
  const uploadInputRef = ref<HTMLInputElement>()
  const dragOver = ref(false)
  const formErrors = reactive({ name: '', typeKey: '', files: '', resumeId: '' })
  const uploadError = ref('')
  const attempted = ref(false)
  const catalogOptions = ref<{ id: string; label: string }[]>([])
  const srsOptions = ref<any[]>([])
  const areaOptions = ref<any[]>([])
  let disposed = false

  const curType = computed(() => DATA_TYPES.find(type => type.key === upload.typeKey))
  const isLayerType = computed(() => curType.value?.src === 'layer')
  const isM3dType = computed(() => curType.value?.src === 'm3d')
  const isRasterType = computed(() => isRasterTypeKey(upload.typeKey))
  const canMultiPick = computed(() => isShapeDataset(upload.typeKey))
  const uploadAccept = computed(() => acceptedDatasetFiles(upload.typeKey))
  const selectedFiles = computed(() => [upload.file, ...upload.familyFiles].filter((file): file is File => Boolean(file)))
  const totalFileBytes = computed(() => selectedFiles.value.reduce((sum, file) => sum + file.size, 0))
  const shapeFiles = computed(() => inspectShapeFiles(selectedFiles.value))
  const fileValidation = computed(() => validateDatasetFiles(upload.typeKey, selectedFiles.value, upload.srcMode === 'resume'))
  const formatHint = computed(() => datasetFormatHint(upload.typeKey))
  const completionHint = computed(() => {
    if (!curType.value) return '选择数据类型后查看上传要求。'
    if (upload.typeKey === 'tileset') return '完整 3DTiles 成果上传后直接发布，无需再发起转换。'
    if (isM3dType.value) return '上传完成后为「待转换」，请在数据列表中发起转换。'
    if (canMultiPick.value) return '文件上传后进入异步入库，请在数据列表中跟踪处理结果。'
    return upload.autoMap ? '栅格入库完成后同时创建地图。' : '栅格上传后完成入库，可在数据列表中发布服务。'
  })
  const submitLabel = computed(() => {
    if (upload.submitting) {
      if (upload.phase === 'checking') return '正在核对原任务…'
      if (upload.phase === 'uploading') return `上传文件 ${upload.progress}%`
      if (isM3dType.value) return upload.typeKey === 'tileset' ? '正在发布成果…' : '正在完成上传…'
      return canMultiPick.value ? '正在提交入库…' : '正在入库…'
    }
    if (upload.srcMode === 'resume') return '开始续传'
    if (isM3dType.value) return upload.typeKey === 'tileset' ? '上传并发布' : '上传文件'
    return '上传并入库'
  })

  function flattenCatalog(nodes: any[]): { id: string; label: string }[] {
    return nodes.flatMap(node => [{ id: String(node.id), label: String(node.typeName ?? node.name ?? node.id) }, ...flattenCatalog(node.children || [])])
  }
  async function loadFormOptions() {
    const results = await Promise.allSettled([dataTypeApi.getAllList(), dictApi.codeAll(), regionApi.getAllList()])
    if (disposed) return
    if (results[0].status === 'fulfilled') catalogOptions.value = flattenCatalog(pickList(results[0].value))
    if (results[1].status === 'fulfilled') srsOptions.value = pickList(results[1].value)
    if (results[2].status === 'fulfilled') areaOptions.value = results[2].value
  }
  function clearErrors() {
    Object.assign(formErrors, { name: '', typeKey: '', files: '', resumeId: '' })
    uploadError.value = ''
  }
  function validateForm(): boolean {
    formErrors.name = upload.srcMode !== 'resume' && !upload.name.trim() ? '请填写数据名称' : ''
    formErrors.typeKey = !curType.value ? '请选择数据类型' : ''
    formErrors.files = upload.typeKey ? fileValidation.value : ''
    formErrors.resumeId = upload.srcMode === 'resume' && !upload.resumeId.trim() ? '请输入原上传任务 ID' : ''
    return !Object.values(formErrors).some(Boolean)
  }
  function openUpload(typeKey = '', catalogId = '') {
    if (upload.submitting) return
    attempted.value = false
    clearErrors()
    Object.assign(upload, {
      visible: true, name: '', typeKey, catalogId, layerRefence: '', srs: '', area: '', confidential: '内部',
      keywords: '', layerDesc: '', autoMap: false, srcMode: 'local', resumeId: '', file: null, familyFiles: [],
      progress: 0, progressText: '', phase: 'idle',
    })
    if (!catalogOptions.value.length || !srsOptions.value.length || !areaOptions.value.length) void loadFormOptions()
  }
  function setFiles(files: File[]) {
    const primary = canMultiPick.value ? files.find(file => /\.shp$/i.test(file.name)) ?? files[0] : files[0]
    upload.file = primary ?? null
    upload.familyFiles = files.filter(file => file !== primary)
  }
  function applyPickedFiles(files: File[]) {
    if (!files.length || upload.submitting) return
    if (!upload.typeKey) {
      attempted.value = true
      formErrors.typeKey = '请先选择数据类型，再添加文件'
      return
    }
    // Keep every selected file. A dropped multi-file selection must never silently discard data.
    setFiles(files)
    uploadError.value = ''
    if (!upload.name.trim()) upload.name = upload.file!.name.replace(/\.[^.]+$/, '')
    if (fileValidation.value) { attempted.value = true; validateForm() }
  }
  function onPickFile(event: Event) {
    const input = event.target as HTMLInputElement
    applyPickedFiles(Array.from(input.files || []))
    input.value = ''
  }
  function onDropFile(event: DragEvent) {
    dragOver.value = false
    applyPickedFiles(Array.from(event.dataTransfer?.files || []))
  }
  function removeFile(file?: File) {
    if (upload.submitting) return
    setFiles(file ? selectedFiles.value.filter(item => item !== file) : [])
    uploadError.value = ''
    if (uploadInputRef.value) uploadInputRef.value.value = ''
  }
  function chooseFiles() { if (!upload.submitting) uploadInputRef.value?.click() }
  watch(() => upload.typeKey, (typeKey, previous) => {
    upload.autoMap = false
    if (previous && typeKey !== previous) {
      setFiles([])
      upload.srcMode = 'local'
      upload.resumeId = ''
      upload.progress = 0
    }
  }, { flush: 'sync' })
  watch(() => [upload.name, upload.typeKey, upload.srcMode, upload.resumeId, ...selectedFiles.value], () => {
    uploadError.value = ''
    if (attempted.value) validateForm()
  })
  onBeforeUnmount(() => { disposed = true })

  function progress(percent: number, text?: string) {
    if (disposed) return
    upload.progress = percent
    upload.phase = percent >= 100 ? 'processing' : 'uploading'
    upload.progressText = text || (percent >= 100 ? submitLabel.value : '正在上传文件…')
  }
  async function submitUpload() {
    if (upload.submitting) return
    attempted.value = true
    uploadError.value = ''
    if (!validateForm()) {
      ElMessage.warning(Object.values(formErrors).find(Boolean)!)
      return
    }
    const type = curType.value!
    const file = upload.file!
    upload.submitting = true
    upload.progress = 0
    upload.phase = upload.srcMode === 'resume' ? 'checking' : 'uploading'
    try {
      if (upload.srcMode === 'resume') {
        const original = await model3dApi.get(upload.resumeId.trim())
        if (!original || String(original.status) !== '0') throw new Error('原任务不处于上传中状态，请在数据列表中确认任务 ID')
        if (String(original.modelType).toUpperCase() !== M3D_TYPES[upload.typeKey]) throw new Error('所选数据类型与原任务不一致，请重新选择')
        if (original.fileName !== file.name || (Number(original.totalSize) > 0 && Number(original.totalSize) !== file.size)) throw new Error('所选文件与原任务的文件名或大小不一致，请选择原始文件')
        upload.phase = 'uploading'
        await m3dResume(upload.resumeId.trim(), file, progress)
        if (!disposed) ElMessage.success(String(original.srcFormat).toLowerCase() === 'tileset' ? '续传完成，3DTiles 成果已发布' : '续传完成，状态为「待转换」，请在数据列表中发起转换')
      } else if (type.src === 'layer') {
        const allFiles = [...selectedFiles.value]
        const totalBytes = totalFileBytes.value
        let uploadedBytes = 0
        let merged: any
        for (let index = 0; index < allFiles.length; index++) {
          const item = allFiles[index]
          const result = await chunkedUpload(item, (percent, text) => {
            const weighted = Math.round(((uploadedBytes + item.size * percent / 100) / totalBytes) * 100)
            const complete = index === allFiles.length - 1 && percent >= 100
            progress(complete ? 100 : Math.min(99, weighted), text ? `${item.name}：${text}` : undefined)
          })
          uploadedBytes += item.size
          if (index === 0) merged = result
        }
        upload.phase = 'processing'
        upload.progressText = canMultiPick.value ? '文件已上传，正在提交入库…' : '文件已上传，正在入库…'
        const basename = String(merged?.logicName || '').trim() || String(merged?.physicalName || '').replace(/\\/g, '/').split('/').pop() || file.name
        const common = {
          // LayerLog is created before backend ID generation; sending the ID keeps its layerId usable.
          id: guid(), layerName: upload.name.trim(), dataType: '', layerType: upload.catalogId, layerRefence: upload.layerRefence,
          area: upload.area, dataConfidential: upload.confidential, keywords: upload.keywords, layerDesc: upload.layerDesc,
          uploadFileType: isRasterTypeKey(type.key) ? '2' : '1',
          uploadFileName: String(merged?.physicalName || '') || basename,
          fileSize: Math.max(1, Math.ceil(totalBytes / 1024)), createBy: options.getUserName(),
        }
        if (upload.autoMap && isRasterType.value) {
          const map = await layerContentApi.insertImageLayer({ ...common, createByName: options.getUserName() })
          if (!map?.id) throw new Error('未能创建地图，请检查栅格文件后重试')
          if (!disposed) ElMessage.success(`「${upload.name.trim()}」已入库并创建地图`)
        } else {
          const created = await layerContentApi.insert({ ...common, uploadStatus: '1' }, { filePathName: basename, user: options.getUserName(), isImport: '1' })
          if (!created?.id) throw new Error('未能创建数据记录，请检查文件完整性后重试')
          await layerContentApi.updateStatus(created.id)
          // SHP ingestion completes asynchronously in ExcuteShpJob; UpdateStatus only marks the upload.
          if (!disposed) ElMessage.success(canMultiPick.value ? '文件已上传，正在入库，请在数据列表中跟踪处理结果' : `「${upload.name.trim()}」入库成功`)
        }
      } else {
        await m3dUpload(file, { name: upload.name.trim(), modelType: M3D_TYPES[upload.typeKey], srcFormat: m3dSrcFormat(upload.typeKey, file.name), srs: upload.srs, userCode: options.getUserName() }, progress)
        if (!disposed) ElMessage.success(upload.typeKey === 'tileset' ? '3DTiles 成果上传完成并已发布' : '上传完成，状态为「待转换」，请在数据列表中发起转换')
      }
      if (disposed) return
      upload.visible = false
      await options.onUploaded()
    } catch (error) {
      if (!disposed) {
        uploadError.value = error instanceof Error ? error.message : '上传未完成，请检查文件后重试'
        showError(error, '上传失败，已保留填写内容，可修正后重试')
      }
    } finally {
      upload.submitting = false
      upload.phase = 'idle'
      upload.progressText = ''
    }
  }
  return {
    upload, uploadInputRef, dragOver, catalogOptions, srsOptions, areaOptions,
    curType, isLayerType, isM3dType, isRasterType, uploadAccept, canMultiPick, selectedFiles, totalFileBytes,
    shapeFiles, fileValidation, formErrors, uploadError, formatHint, completionHint, submitLabel,
    openUpload, chooseFiles, onPickFile, onDropFile, removeFile, submitUpload,
  }
}
