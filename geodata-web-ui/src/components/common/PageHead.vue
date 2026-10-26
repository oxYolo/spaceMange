<script setup lang="ts">
defineProps<{ title: string; sub?: string }>()
</script>

<template>
  <header class="page-head">
    <div class="page-head-copy">
      <h1>{{ title }}</h1>
      <p v-if="sub" class="sub">{{ sub }}</p>
    </div>
    <div v-if="$slots.tools" class="tools">
      <slot name="tools" />
    </div>
  </header>
</template>
