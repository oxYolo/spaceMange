<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
const ConsoleLayout = defineAsyncComponent(() => import('@/layouts/ConsoleLayout.vue'))
const route = useRoute()
</script>

<template>
  <router-view v-if="route.path === '/login'" />
  <ConsoleLayout v-else />
</template>
