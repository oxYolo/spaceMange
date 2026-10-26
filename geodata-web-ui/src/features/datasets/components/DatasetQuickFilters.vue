<script setup lang="ts">
import { readDatasetListState, type DatasetListState } from '../listState'

const props = defineProps<{ state: DatasetListState; disabled?: boolean }>()
const emit = defineEmits<{ apply: [state: DatasetListState] }>()
const shortcuts: { key: string; name: string; query: Record<string, string> }[] = [
  { key: 'all', name: '全部', query: {} },
  { key: 'unpublished', name: '未发布', query: { pub: 'no' } },
  { key: 'converting', name: '转换中', query: { status: 'converting' } },
  { key: 'failed', name: '处理失败', query: { status: 'fail' } },
]

function shortcutActive(query: Record<string, string>) {
  const state = readDatasetListState(query)
  return !props.state.type && !props.state.kw && !props.state.catalog && props.state.status === state.status && props.state.pub === state.pub
}

function applyShortcut(query: Record<string, string>) {
  if (props.disabled) return
  emit('apply', { ...readDatasetListState(query), size: props.state.size, sort: props.state.sort, order: props.state.order })
}
</script>

<template>
  <div class="quick-views" role="group" aria-label="快捷筛选">
    <span class="quick-caption">快捷筛选</span>
    <button v-for="item in shortcuts" :key="item.key" type="button" :disabled="disabled" :class="{ active: shortcutActive(item.query) }" :aria-pressed="shortcutActive(item.query)" @click="applyShortcut(item.query)">{{ item.name }}</button>
  </div>
</template>

<style scoped>
.quick-views { display: flex; align-items: center; flex-wrap: wrap; gap: 5px; border-top: 1px solid var(--line); margin-top: 12px; padding-top: 10px; }
.quick-caption { color: var(--ink-3); font-size: 11px; margin-right: 3px; white-space: nowrap; }
.quick-views button { border: 0; border-radius: 4px; padding: 4px 8px; background: transparent; color: var(--ink-2); font: inherit; font-size: 11px; cursor: pointer; transition: background var(--motion-fast), color var(--motion-fast); }
.quick-views button:hover, .quick-views button.active { background: var(--primary-050); color: var(--primary); }
.quick-views button:disabled { opacity: .5; cursor: default; }
@media (max-width: 450px) { .quick-views { gap: 3px; }.quick-views button { padding: 4px 6px; } }
</style>
