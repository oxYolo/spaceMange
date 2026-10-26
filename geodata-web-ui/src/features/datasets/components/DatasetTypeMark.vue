<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@/components/common/Icon.vue'
const props = defineProps<{ type: string; size?: 'small' | 'normal' }>()
const category = computed(() => ['raster', 'terrain'].includes(props.type) ? 'raster' : ['vector', 'white'].includes(props.type) ? 'vector' : 'model')
</script>
<template>
  <span class="dataset-mark" :class="[category, size]" aria-hidden="true">
    <Icon :name="category === 'raster' ? 'grid' : category === 'vector' ? 'layers' : 'cube'" :size="size === 'small' ? 15 : 20" />
  </span>
</template>
<style scoped>
.dataset-mark { transition: transform var(--motion-base) var(--motion-ease), box-shadow var(--motion-base); width: 38px; height: 40px; border: 1px solid; border-radius: 9px; display: inline-flex; align-items: center; justify-content: center; flex: none; }
.dataset-mark.vector { color: #2c70be; background: #edf5ff; border-color: #d9e9fa; }
.dataset-mark.raster { color: #158b87; background: #ecf8f5; border-color: #d3eee5; }
.dataset-mark.model { color: #7461b9; background: #f4f0ff; border-color: #e4ddf7; }
.dataset-mark.small { width: 31px; height: 33px; border-radius: 7px; }
</style>
