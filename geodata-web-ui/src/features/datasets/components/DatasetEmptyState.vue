<script setup lang="ts">
import Icon from '@/components/common/Icon.vue'
withDefaults(defineProps<{ error?: string; filtered?: boolean; loading?: boolean }>(), { error: '', filtered: false, loading: false })
defineEmits<{ retry: []; reset: []; upload: [] }>()
</script>
<template>
  <div v-if="!loading" class="dataset-empty" role="status">
    <div class="empty-symbol" :class="{ error: !!error }"><Icon :name="error ? 'warn' : filtered ? 'search' : 'layers'" :size="28" /></div>
    <strong>{{ error ? '数据暂时无法加载' : filtered ? '没有找到匹配的数据' : '开始管理空间数据' }}</strong>
    <p>{{ error || (filtered ? '试试其他名称，或清除当前筛选条件。' : '上传矢量、栅格或三维文件，再发布为可用的数据服务。') }}</p>
    <el-button v-if="error" type="primary" plain @click="$emit('retry')">重新加载</el-button>
    <el-button v-else-if="filtered" @click="$emit('reset')">清除筛选</el-button>
    <el-button v-else type="primary" @click="$emit('upload')">上传数据</el-button>
  </div>
</template>
<style scoped>
.dataset-empty { padding: 48px 20px; text-align: center; display: flex; align-items: center; flex-direction: column; gap: 8px; }
.empty-symbol { width: 64px; height: 64px; display: grid; place-items: center; color: var(--primary); background: var(--primary-050); border-radius: 20px; margin-bottom: 7px; }
.empty-symbol.error { color: var(--warn); background: #fff5e5; }
strong { font-size: 15px; color: var(--ink); font-weight: 600; }
p { max-width: 380px; margin: 0 0 8px; font-size: 12px; color: var(--ink-3); line-height: 1.8; overflow-wrap: anywhere; }
</style>
