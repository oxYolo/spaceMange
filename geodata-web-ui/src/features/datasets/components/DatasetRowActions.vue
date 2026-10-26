<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@/components/common/Icon.vue'
import { getDatasetCapabilities, type UnifiedRow } from '../model'
const props = defineProps<{ row: UnifiedRow; busy?: boolean; deletingDisabled?: boolean; publishLabel: string }>()
const emit = defineEmits<{ detail: [tab?: string]; publish: []; preview: []; copy: []; edit: []; remove: [] }>()
const caps = computed(() => getDatasetCapabilities(props.row))
function command(value: string) {
  if (value === 'detail') emit('detail')
  else if (value === 'copy') emit('copy')
  else if (value === 'edit') emit('edit')
  else if (value === 'publish') emit('publish')
  else if (value === 'remove') emit('remove')
}
</script>
<template>
  <div class="row-actions">
    <el-button link type="primary" size="small" @click="emit('detail', caps.canViewAttributes ? 'data' : 'overview')">{{ caps.canViewAttributes ? '属性' : '详情' }}</el-button>
    <el-button v-if="row.published" link type="primary" size="small" :disabled="!caps.canPreview || busy" @click="emit('preview')">预览</el-button>
    <el-button v-else link type="primary" size="small" :disabled="!(caps.canPublish || caps.canConvert) || busy" :loading="busy" @click="emit('publish')">{{ publishLabel }}</el-button>
    <el-dropdown trigger="click" @command="command">
      <el-button link size="small" :aria-label="row.name + '的更多操作'">更多<Icon name="chevron-down" :size="13" aria-hidden="true" /></el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="detail">数据详情</el-dropdown-item>
          <el-dropdown-item v-if="row.published" command="copy">复制服务地址</el-dropdown-item>
          <el-dropdown-item v-if="row.published && row.src === 'layer'" command="publish" :disabled="busy || !caps.canPublish">重新发布</el-dropdown-item>
          <el-dropdown-item v-if="row.src === 'layer'" command="edit" :disabled="busy">编辑信息</el-dropdown-item>
          <el-dropdown-item command="remove" divided :disabled="busy || deletingDisabled || !caps.canDelete"><span class="delete-label">删除数据</span></el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>
<style scoped>
.row-actions { display: flex; align-items: center; gap: 14px; white-space: nowrap; }
.row-actions :deep(.el-button) { margin: 0; padding: 4px 0; font-size: 12px; }
.row-actions :deep(.el-button span) { gap: 3px; }
.delete-label { color: var(--danger); }
</style>
