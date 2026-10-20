<script setup lang="ts">
defineProps<{ title: string; sub?: string }>()
</script>

<template>
  <div class="page-head">
    <h2>{{ title }}</h2>
    <span v-if="sub" class="sub">{{ sub }}</span>
    <div class="tools">
      <slot name="tools" />
    </div>
  </div>
</template>
