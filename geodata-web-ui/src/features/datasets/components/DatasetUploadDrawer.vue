<script setup lang="ts">
import { computed, useId } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage } from 'element-plus'
import Icon from '@/components/common/Icon.vue'
import { DATA_TYPES } from '@/utils/datasets'
import { fmtSize } from '@/utils/format'
import { useUserStore } from '@/auth/store'
import { useDatasetUpload } from '../composables/useDatasetUpload'

const emit = defineEmits<{ uploaded: [] }>()
const user = useUserStore()
const prefix = `dataset-upload-${useId()}`
const fileKeys = new WeakMap<File, number>()
let nextFileKey = 0
function fileKey(file: File) {
  if (!fileKeys.has(file)) fileKeys.set(file, ++nextFileKey)
  return fileKeys.get(file)!
}
const { upload, uploadInputRef, dragOver, catalogOptions, srsOptions, areaOptions,
  isLayerType, isM3dType, isRasterType, uploadAccept, canMultiPick, selectedFiles, totalFileBytes,
  shapeFiles, formErrors, uploadError, formatHint, completionHint, submitLabel,
  openUpload, chooseFiles, onPickFile, onDropFile, removeFile, submitUpload } = useDatasetUpload({
  getUserName: () => user.userName,
  onUploaded: () => emit('uploaded'),
})
function closeDrawer(done: () => void) { if (!upload.submitting) done() }
onBeforeRouteLeave(() => {
  if (!upload.submitting) return true
  ElMessage.warning('正在处理上传，请等待完成后再离开')
  return false
})
defineExpose({ open: openUpload, visible: computed(() => upload.visible), busy: computed(() => upload.submitting) })
</script>

<template>
  <el-drawer v-model="upload.visible" title="上传数据" size="min(560px, 100vw)" :close-on-click-modal="false"
    :close-on-press-escape="!upload.submitting" :show-close="!upload.submitting" :before-close="closeDrawer">
    <el-form class="upload-form" :class="{ 'is-open': upload.visible }" :disabled="upload.submitting" @submit.prevent="submitUpload">
      <section class="form-section" :aria-labelledby="`${prefix}-basics-title`">
        <div class="section-head"><h3 :id="`${prefix}-basics-title`">基本信息</h3><span class="required-note">必填</span></div>
        <div class="grid2">
          <div class="field">
            <label :for="`${prefix}-type`">数据类型<span class="req" aria-hidden="true">*</span></label>
            <el-select :id="`${prefix}-type`" v-model="upload.typeKey" placeholder="选择数据类型" style="width:100%"
              :aria-invalid="Boolean(formErrors.typeKey)" :aria-describedby="formErrors.typeKey ? `${prefix}-type-error` : undefined">
              <el-option v-for="type in DATA_TYPES" :key="type.key" :label="type.label" :value="type.key" />
            </el-select>
            <Transition name="upload-feedback"><p v-if="formErrors.typeKey" :id="`${prefix}-type-error`" class="field-error">{{ formErrors.typeKey }}</p></Transition>
          </div>
          <div v-if="upload.srcMode !== 'resume'" class="field">
            <label :for="`${prefix}-name`">数据名称<span class="req" aria-hidden="true">*</span></label>
            <el-input :id="`${prefix}-name`" v-model="upload.name" placeholder="选择文件后自动填写" maxlength="100"
              :aria-invalid="Boolean(formErrors.name)" :aria-describedby="formErrors.name ? `${prefix}-name-error` : undefined" />
            <Transition name="upload-feedback"><p v-if="formErrors.name" :id="`${prefix}-name-error`" class="field-error">{{ formErrors.name }}</p></Transition>
          </div>
        </div>
        <div v-if="isM3dType" class="field source-field">
          <span :id="`${prefix}-source-label`" class="field-label">上传方式</span>
          <el-radio-group v-model="upload.srcMode" :aria-labelledby="`${prefix}-source-label`">
            <el-radio value="local">新建上传</el-radio><el-radio value="resume">继续原任务</el-radio>
          </el-radio-group>
        </div>
        <div v-if="upload.srcMode === 'resume'" class="field">
          <label :for="`${prefix}-resume`">原任务 ID<span class="req" aria-hidden="true">*</span></label>
          <el-input :id="`${prefix}-resume`" v-model="upload.resumeId" placeholder="填写上传中任务的数据 ID"
            :aria-invalid="Boolean(formErrors.resumeId)" :aria-describedby="`${prefix}-resume-hint`" />
          <p :id="`${prefix}-resume-hint`" class="hint">选择原始文件，系统会核对文件名和大小，再跳过已上传的分片。</p>
          <Transition name="upload-feedback"><p v-if="formErrors.resumeId" class="field-error">{{ formErrors.resumeId }}</p></Transition>
        </div>
      </section>

      <section class="form-section" :aria-labelledby="`${prefix}-files-label`">
        <div class="section-head"><label :id="`${prefix}-files-label`" :for="`${prefix}-files`">数据文件<span class="req" aria-hidden="true">*</span></label><span class="required-note">必填</span></div>
        <p :id="`${prefix}-format-hint`" class="format-hint">{{ formatHint }}</p>
        <input :id="`${prefix}-files`" ref="uploadInputRef" type="file" class="visually-hidden" tabindex="-1"
          :accept="uploadAccept" :multiple="canMultiPick" :disabled="upload.submitting || !upload.typeKey" @change="onPickFile" />
        <button type="button" class="drop" :class="{ over: dragOver, invalid: Boolean(formErrors.files) }"
          :disabled="upload.submitting || !upload.typeKey" :aria-controls="`${prefix}-files`"
          :aria-describedby="`${prefix}-format-hint${formErrors.files ? ` ${prefix}-files-error` : ''}`"
          :aria-invalid="Boolean(formErrors.files)" @click="chooseFiles" @dragover.prevent="dragOver = !upload.submitting"
          @dragleave="dragOver = false" @drop.prevent="onDropFile">
          <Icon name="upload" :size="22" />
          <span class="drop-title">{{ selectedFiles.length ? '重新选择文件' : '点击选择文件，或拖拽到此处' }}</span>
          <span class="drop-hint">{{ !upload.typeKey ? '请先选择数据类型' : canMultiPick ? '一次选择完整的一组 SHP 文件' : '每次上传一个文件' }}</span>
        </button>
        <Transition name="upload-block"><div v-if="selectedFiles.length" class="selected-files">
          <div class="files-summary"><strong>已选 {{ selectedFiles.length }} 个文件</strong><span>共 {{ fmtSize(totalFileBytes) }}</span></div>
          <TransitionGroup appear name="upload-file" tag="ul" class="file-list" aria-label="已选择的文件">
            <li v-for="(file, index) in selectedFiles" :key="fileKey(file)" :style="{ '--file-order': Math.min(index, 5) }">
              <Icon name="file" :size="15" /><span class="file-name" :title="file.name">{{ file.name }}</span><span class="file-size">{{ fmtSize(file.size) }}</span>
              <button type="button" class="remove-file" :disabled="upload.submitting" :aria-label="`移除 ${file.name}`" @click="removeFile(file)"><Icon name="x" :size="13" /></button>
            </li>
          </TransitionGroup>
          <div v-if="canMultiPick" class="shape-check" :class="{ incomplete: shapeFiles.missing.length }" role="status">
            <span v-if="shapeFiles.missing.length">缺少必需文件：{{ shapeFiles.missing.join('、') }}</span>
            <span v-else>必需文件已齐备</span>
            <span v-if="shapeFiles.optionalMissing.length" class="optional-files">建议补充 {{ shapeFiles.optionalMissing.join('、') }}</span>
          </div>
        </div></Transition>
        <Transition name="upload-feedback"><p v-if="formErrors.files" :id="`${prefix}-files-error`" class="field-error" role="alert">{{ formErrors.files }}</p></Transition>
        <el-checkbox v-if="isRasterType && upload.srcMode === 'local'" v-model="upload.autoMap" class="auto-map">入库后同时创建地图</el-checkbox>
        <p class="completion-hint">{{ completionHint }}</p>
        <Transition name="upload-block"><div v-if="upload.submitting" class="upload-progress" role="status" aria-live="polite">
          <el-progress :percentage="upload.progress" :show-text="upload.phase === 'uploading'" :stroke-width="5" />
          <span>{{ upload.phase === 'uploading' ? upload.progressText || '正在上传文件…' : submitLabel }}</span>
        </div></Transition>
        <Transition name="upload-feedback"><div v-if="uploadError" class="upload-error" role="alert"><strong>本次上传未完成</strong><p>{{ uploadError }}</p><span>填写内容和文件已保留，可修正后重试。</span></div></Transition>
      </section>

      <details v-if="upload.srcMode !== 'resume' && upload.typeKey" class="optional-section">
        <summary>补充信息<span>选填</span></summary>
        <div class="optional-body">
          <div v-if="isLayerType" class="field">
            <label :for="`${prefix}-catalog`">所属目录</label>
            <el-select :id="`${prefix}-catalog`" v-model="upload.catalogId" placeholder="选择所属目录" clearable filterable style="width:100%"><el-option v-for="catalog in catalogOptions" :key="catalog.id" :label="catalog.label" :value="catalog.id" /></el-select>
          </div>
          <div class="field">
            <label :for="`${prefix}-srs`">空间参考系</label>
            <el-select v-if="isLayerType" :id="`${prefix}-srs`" v-model="upload.layerRefence" placeholder="选择坐标系" clearable filterable style="width:100%"><el-option v-for="item in srsOptions" :key="item.id || item.code" :label="item.codeName || item.code" :value="item.codeName || item.code" /></el-select>
            <el-input v-else :id="`${prefix}-srs`" v-model="upload.srs" placeholder="如 EPSG:4326" />
          </div>
          <div v-if="isLayerType" class="grid2">
            <div class="field"><label :for="`${prefix}-area`">行政区划</label><el-select :id="`${prefix}-area`" v-model="upload.area" placeholder="选择行政区划" clearable filterable style="width:100%"><el-option v-for="area in areaOptions" :key="area.id ?? area.code" :label="area.areaName ?? area.name ?? area.code" :value="area.areaName ?? area.name ?? area.code" /></el-select></div>
            <div class="field"><label :for="`${prefix}-confidential`">数据密级</label><el-select :id="`${prefix}-confidential`" v-model="upload.confidential" style="width:100%"><el-option label="内部" value="内部" /><el-option label="公开" value="公开" /><el-option label="涉密" value="涉密" /></el-select></div>
          </div>
          <div v-if="isLayerType" class="field"><label :for="`${prefix}-tags`">数据标签</label><el-input :id="`${prefix}-tags`" v-model="upload.keywords" placeholder="多个标签以逗号分隔" /></div>
          <div v-if="isLayerType" class="field last-field"><label :for="`${prefix}-desc`">摘要 / 备注</label><el-input :id="`${prefix}-desc`" v-model="upload.layerDesc" type="textarea" :rows="3" placeholder="如数据范围、来源与使用说明" /></div>
        </div>
      </details>
    </el-form>
    <template #footer><el-button :disabled="upload.submitting" @click="upload.visible = false">取消</el-button><el-button type="primary" :loading="upload.submitting" @click="submitUpload">{{ submitLabel }}</el-button></template>
  </el-drawer>
</template>

<style scoped>
.upload-form { padding: 0 2px; }
.form-section + .form-section { margin-top: 24px; }
.section-head { display:flex; align-items:center; gap:8px; margin-bottom:14px; }
.section-head h3,.section-head label { margin:0; font-size:14px; font-weight:600; color:var(--ink); }
.required-note { font-size:11px; color:var(--ink-3); padding:1px 5px; border:1px solid var(--line); border-radius:4px; }
.grid2 { display:grid; grid-template-columns:1fr 1fr; gap:0 14px; }
.field { margin-bottom:15px; }
.field label,.field-label { display:block; margin-bottom:6px; font-size:13px; color:var(--ink-2); }
.req { color:var(--danger); margin-left:4px; }
.field-error { margin:7px 0 0; color:var(--danger); font-size:12px; line-height:1.6; }
.hint,.format-hint,.completion-hint { font-size:12px; line-height:1.7; color:var(--ink-3); margin:6px 0 0; }
.format-hint { margin:-4px 0 12px; color:var(--ink-2); }
.completion-hint { margin-top:12px; }
.source-field { margin-bottom:0; }
.drop { width:100%; display:flex; flex-direction:column; align-items:center; gap:7px; padding:20px 14px; border:1px dashed var(--line-2); background:var(--panel-2); border-radius:8px; color:var(--primary); font:inherit; cursor:pointer; transition:background var(--motion-fast,160ms),border-color var(--motion-fast,160ms),transform var(--motion-base,240ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)),box-shadow var(--motion-base,240ms); }
.drop:hover,.drop.over { background:var(--primary-050); border-color:var(--primary); }
.drop.over { transform:translateY(-2px) scale(1.008); box-shadow:0 0 0 4px var(--primary-050),0 7px 18px rgba(17,99,181,.08); }
.drop :deep(svg) { transition:transform var(--motion-base,240ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)); }
.drop.over :deep(svg) { transform:translateY(-3px); }
.drop:active:not(:disabled) { transform:scale(.99); }
.drop:focus-visible { outline:3px solid var(--primary-100); outline-offset:3px; border-color:var(--primary); }
.drop:disabled { cursor:not-allowed; opacity:.65; }
.drop.invalid { border-color:var(--danger); }
.drop-title { font-size:13px; font-weight:500; }
.drop-hint { font-size:12px; color:var(--ink-3); }
.selected-files { margin-top:14px; border:1px solid var(--line); border-radius:7px; overflow:hidden; }
.files-summary { display:flex; align-items:center; justify-content:space-between; padding:9px 11px; font-size:12px; color:var(--ink-3); background:var(--panel-2); }
.files-summary strong { color:var(--ink-2); font-weight:500; }
.file-list { position:relative; list-style:none; padding:0; margin:0; }
.file-list li { display:flex; align-items:center; gap:8px; padding:9px 11px; border-top:1px solid var(--line); font-size:12px; }
.file-name { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.file-size { flex:none; color:var(--ink-3); }
.remove-file { display:flex; padding:4px; border:0; border-radius:4px; background:transparent; color:var(--ink-3); cursor:pointer; transition:background var(--motion-fast,160ms),color var(--motion-fast,160ms),transform var(--motion-fast,160ms); }
.remove-file:hover { background:#fff0ed; color:var(--danger); }
.remove-file:active:not(:disabled) { transform:scale(.9); }
.remove-file:disabled { cursor:not-allowed; opacity:.5; }
.remove-file:focus-visible { outline:2px solid var(--primary); }
.shape-check { display:flex; gap:4px 12px; flex-wrap:wrap; padding:9px 11px; border-top:1px solid var(--line); font-size:12px; color:var(--ok); background:#f5fbf8; transition:background var(--motion-base,240ms),color var(--motion-base,240ms); }
.shape-check.incomplete { color:var(--warn); background:#fffaf1; }
.optional-files { color:var(--ink-3); }
.auto-map { margin-top:10px; }
.upload-progress { display:grid; gap:7px; margin-top:15px; padding:12px; background:var(--primary-050); border-radius:6px; color:var(--primary); font-size:12px; }
.upload-error { margin-top:14px; padding:12px; border:1px solid #f1d5d1; border-radius:6px; background:#fff7f5; color:var(--danger); font-size:12px; line-height:1.7; }
.upload-error strong { font-weight:600; }
.upload-error p { margin:4px 0; overflow-wrap:anywhere; }
.optional-section { margin-top:24px; border-top:1px solid var(--line); padding-top:15px; }
.optional-section summary { color:var(--ink-2); font-size:13px; font-weight:500; cursor:pointer; }
.optional-section summary span { margin-left:8px; font-size:11px; color:var(--ink-3); font-weight:400; }
.optional-body { padding-top:16px; }
.last-field { margin-bottom:0; }
.visually-hidden { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
.upload-form.is-open .form-section { animation:upload-section-in var(--motion-slow,360ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)) both; }
.upload-form.is-open .form-section:nth-child(2) { animation-delay:60ms; }
.upload-form.is-open .optional-section { animation:upload-section-in var(--motion-slow,360ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)) 100ms both; }
.optional-section[open] .optional-body { animation:upload-section-in var(--motion-base,240ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)) both; }
.upload-progress :deep(.el-progress-bar__inner) { transition:width var(--motion-base,240ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)); }
.upload-block-enter-active,.upload-block-leave-active { transition:opacity var(--motion-base,240ms),transform var(--motion-base,240ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)); }
.upload-block-enter-from,.upload-block-leave-to { opacity:0; transform:translateY(8px); }
.upload-feedback-enter-active,.upload-feedback-leave-active { transition:opacity var(--motion-fast,160ms),transform var(--motion-fast,160ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)); }
.upload-feedback-enter-from,.upload-feedback-leave-to { opacity:0; transform:translateY(-4px); }
.upload-file-move,.upload-file-enter-active,.upload-file-leave-active { transition:opacity var(--motion-base,240ms),transform var(--motion-base,240ms) var(--motion-ease,cubic-bezier(.22,1,.36,1)); }
.upload-file-enter-active { transition-delay:calc(var(--file-order,0) * 25ms); }
.upload-file-enter-from { opacity:0; transform:translateX(10px); }
.upload-file-leave-to { opacity:0; transform:translateX(-8px); }
.upload-file-leave-active { position:absolute; width:100%; }
@keyframes upload-section-in { from { opacity:0; transform:translateY(10px); } to { opacity:1; transform:translateY(0); } }
@media(max-width:520px) { .grid2 { grid-template-columns:1fr; } .file-list li { gap:6px; } }
@media(prefers-reduced-motion:reduce) {
  .upload-form.is-open .form-section,.upload-form.is-open .optional-section,.optional-section[open] .optional-body { animation:none; }
  .drop,.drop :deep(svg),.remove-file,.shape-check,.upload-progress :deep(.el-progress-bar__inner),
  .upload-block-enter-active,.upload-block-leave-active,.upload-feedback-enter-active,.upload-feedback-leave-active,
  .upload-file-move,.upload-file-enter-active,.upload-file-leave-active { transition:none; }
  .drop.over,.drop.over :deep(svg),.drop:active:not(:disabled),.remove-file:active:not(:disabled) { transform:none; }
}
</style>
