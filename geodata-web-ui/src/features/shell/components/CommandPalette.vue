<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/common/Icon.vue'
import { showError } from '@/utils/feedback'
import { createShellCommands, moveCommandSelection, paletteKeyAction, searchShellCommands, type ShellCommand } from '../commands'

const route = useRoute()
const router = useRouter()
const commands = createShellCommands()
const visible = ref(false)
const query = ref('')
const activeIndex = ref(0)
const executing = ref(false)
const composing = ref(false)
const input = ref<HTMLInputElement>()
const results = computed(() => searchShellCommands(commands, query.value))
const activeId = computed(() => results.value[activeIndex.value] ? `command-${results.value[activeIndex.value].id.replace(':', '-')}` : undefined)
let previousFocus: HTMLElement | null = null
let focusWorkspace = false
let focusRevision = 0
let focusFrame: number | undefined

function open() {
  if (visible.value) return
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  focusWorkspace = false
  query.value = ''
  activeIndex.value = 0
  composing.value = false
  visible.value = true
}
defineExpose({ open })

watch(query, () => { activeIndex.value = results.value.length ? 0 : -1 })
watch(() => route.path, () => { if (visible.value) { focusWorkspace = true; visible.value = false } })
watch(visible, (opened) => {
  if (opened) focusInput()
  else cancelInputFocus()
}, { flush: 'post' })

function cancelInputFocus() {
  focusRevision++
  if (focusFrame !== undefined) cancelAnimationFrame(focusFrame)
  focusFrame = undefined
}
onBeforeUnmount(cancelInputFocus)

function focusInput() {
  cancelInputFocus()
  const revision = focusRevision
  const apply = () => {
    if (!visible.value || revision !== focusRevision) return
    const field = input.value
    const dialog = field?.closest('.el-dialog')
    // 保留用户在打开期间主动移到关闭按钮的焦点，只修复 trap 的容器回焦。
    if (field && dialog && (document.activeElement === dialog || !dialog.contains(document.activeElement))) {
      field.focus({ preventScroll: true })
    }
  }
  void nextTick(async () => {
    // Element Plus 在 open-auto-focus 之后另排一次 nextTick 聚焦容器。
    await nextTick()
    apply()
    if (visible.value && revision === focusRevision) focusFrame = requestAnimationFrame(apply)
  })
}
function restoreFocus() {
  void nextTick(() => {
    const fallback = document.querySelector<HTMLElement>('#workspace-main')
    const previousAvailable = previousFocus?.isConnected && previousFocus !== document.body && !previousFocus.closest('[inert]')
    ;(focusWorkspace || !previousAvailable ? fallback : previousFocus)?.focus({ preventScroll: true })
  })
}

async function execute(command?: ShellCommand) {
  if (!visible.value || !command || executing.value || composing.value) return
  executing.value = true
  focusWorkspace = true
  visible.value = false
  try { await router.push(command.to) }
  catch (error) { showError(error, '无法打开入口，请重试') }
  finally { executing.value = false }
}
function onKeydown(event: KeyboardEvent) {
  if (!visible.value || composing.value) return
  const action = paletteKeyAction(event)
  if (!action) return
  if (action !== 'close' && event.target !== input.value) return
  event.preventDefault()
  event.stopPropagation()
  if (action === 'close') visible.value = false
  else if (action === 'execute') void execute(results.value[activeIndex.value])
  else {
    activeIndex.value = moveCommandSelection(activeIndex.value, action === 'next' ? 1 : -1, results.value.length)
    void nextTick(() => activeId.value && document.getElementById(activeId.value)?.scrollIntoView({ block: 'nearest' }))
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="快捷导航" width="570px" top="14vh" class="command-palette" append-to-body destroy-on-close :show-close="false" :close-on-press-escape="false" @open-auto-focus="focusInput" @opened="focusInput" @close-auto-focus="restoreFocus" @keydown="onKeydown">
    <template #header="{ titleId }"><div class="palette-heading"><h2 :id="titleId">快捷导航</h2><button type="button" aria-label="关闭快捷导航" @click="visible = false"><Icon name="x" :size="17" /></button></div></template>
    <div class="palette-search"><Icon name="search" :size="19" aria-hidden="true" /><input ref="input" v-model="query" type="text" role="combobox" aria-label="搜索页面或上传入口" aria-autocomplete="list" aria-controls="command-results" :aria-expanded="true" :aria-activedescendant="activeId" placeholder="搜索页面或操作，例如：上传、任务…" autocomplete="off" @compositionstart="composing = true" @compositionend="composing = false" /></div>
    <div class="palette-section">{{ query.trim() ? `搜索结果 · ${results.length}` : '常用入口' }}</div>
    <div id="command-results" class="palette-results" role="listbox" aria-label="快捷入口">
      <button v-for="(command, index) in results" :id="`command-${command.id.replace(':', '-')}`" :key="command.id" class="palette-command" :class="{ active: activeIndex === index }" type="button" role="option" :aria-selected="activeIndex === index" tabindex="-1" @pointermove="activeIndex = index" @mousedown.prevent @click="execute(command)"><span class="command-icon"><Icon :name="command.icon" :size="18" aria-hidden="true" /></span><span class="command-copy"><strong>{{ command.label }}</strong><small>{{ command.description }}</small></span><span class="command-enter" aria-hidden="true">↵</span></button>
    </div>
    <div v-if="!results.length" class="palette-empty" role="status">没有找到入口，试试“上传”“任务”或页面名称。</div>
    <footer class="palette-footer"><span><kbd>↑</kbd><kbd>↓</kbd> 选择</span><span><kbd>↵</kbd> 打开</span><span><kbd>Esc</kbd> 关闭</span></footer>
  </el-dialog>
</template>

<style scoped>
.palette-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.palette-heading h2 { margin: 0; font-size: 14px; color: var(--ink); font-weight: 600; }
.palette-heading button { display: grid; place-items: center; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--ink-3); cursor: pointer; }
.palette-heading button:hover { color: var(--primary); background: var(--primary-050); }
.palette-search { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border: 1px solid var(--line-2); border-radius: 8px; background: #fff; color: var(--ink-3); transition: border-color var(--motion-fast), box-shadow var(--motion-fast); }
.palette-search:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgb(17 99 181 / 8%); }
.palette-search input { flex: 1; min-width: 0; border: 0; outline: none; font: inherit; font-size: 13px; color: var(--ink); background: transparent; }
.palette-search input::placeholder { color: var(--ink-3); }
.palette-section { margin: 16px 3px 8px; font-size: 11px; color: var(--ink-3); }
.palette-results { max-height: min(350px, 47dvh); overflow-y: auto; padding: 2px; }
.palette-command { display: flex; align-items: center; gap: 11px; width: 100%; min-height: 57px; padding: 8px 11px; border: 1px solid transparent; border-radius: 7px; background: transparent; text-align: left; cursor: pointer; transition: background var(--motion-fast), border-color var(--motion-fast); }
.palette-command.active { border-color: #dbe9f7; background: var(--primary-050); }
.command-icon { display: grid; place-items: center; flex: 0 0 32px; width: 32px; height: 32px; border: 1px solid var(--line); border-radius: 7px; color: var(--ink-2); background: #fff; }
.active .command-icon { color: var(--primary); border-color: #ccdef0; }
.command-copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 3px; }
.command-copy strong { color: var(--ink); font-size: 12.5px; font-weight: 500; }
.command-copy small { color: var(--ink-3); font-size: 10.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.command-enter { color: var(--primary); opacity: 0; font-size: 16px; }
.active .command-enter { opacity: 1; }
.palette-empty { padding: 28px 10px; text-align: center; font-size: 12px; color: var(--ink-3); }
.palette-footer { display: flex; gap: 17px; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--line); color: var(--ink-3); font-size: 10px; }
.palette-footer kbd { min-width: 16px; display: inline-block; margin-right: 3px; padding: 0 3px; border: 1px solid var(--line); border-radius: 3px; background: var(--panel-2); font: inherit; line-height: 16px; text-align: center; }
@media (max-width: 600px) { .palette-results { max-height: 45dvh; } .palette-search { padding: 10px; } .palette-search input { font-size: 12px; } }
@media (prefers-reduced-motion: reduce) { .palette-command, .palette-search { transition: none; } }
</style>
