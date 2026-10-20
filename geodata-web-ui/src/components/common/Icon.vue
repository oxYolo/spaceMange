<script setup lang="ts">
/** 线性图标（24×24 描边），取自原型 ICON 字典 */
const ICONS: Record<string, string> = {
  layers: '<path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 13l9 5 9-5"/>',
  globe:
    '<circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3c3 3.5 3 14 0 18M12 3c-3 3.5-3 14 0 18"/>',
  palette:
    '<path d="M12 3a9 9 0 100 18h1.5a2 2 0 001.6-3.2 2 2 0 011.6-3.2H18a3 3 0 003-3A9 9 0 0012 3z"/><circle cx="7.5" cy="11" r="1"/><circle cx="10" cy="7.5" r="1"/><circle cx="14.5" cy="7.5" r="1"/>',
  tool: '<path d="M14.7 6.3a4 4 0 105.3 5.3L21 21H3l6.3-6.3"/><path d="M14.7 6.3l3 3"/>',
  tag: '<path d="M3 12l9-9h7a2 2 0 012 2v7l-9 9-9-9z"/><circle cx="16" cy="8" r="1.4"/>',
  gear:
    '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M19.1 4.9L17 7M7 17l-2.1 2.1"/>',
  cube: '<path d="M12 2l9 5v10l-9 5-9-5V7z"/><path d="M3 7l9 5 9-5M12 12v10"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
  refresh: '<path d="M20 11a8 8 0 10-2 5.3"/><path d="M20 5v6h-6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  edit: '<path d="M4 20h4l10-10-4-4L4 16z"/><path d="M14 6l4 4"/>',
  trash: '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/>',
  up: '<path d="M12 16V4m0 0L7 9m5-5l5 5"/><path d="M4 17v2a1 1 0 001 1h14a1 1 0 001-1v-2"/>',
  down: '<path d="M12 4v12m0 0l5-5m-5 5l-5-5"/><path d="M4 19h16"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>',
  grid: '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>',
  warn: '<path d="M12 3l9 16H3z"/><path d="M12 9v5M12 17h.01"/>',
  db: '<ellipse cx="12" cy="6" rx="8" ry="3"/><path d="M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
  play: '<path d="M6 4l14 8-14 8V4z"/>',
  eye: '<path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6-10-6-10-6z"/><circle cx="12" cy="12" r="2.6"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 012-2h8"/>',
  check: '<path d="M4 12.5l5 5L20 6.5"/>',
  x: '<path d="M6 6l12 12M18 6L6 18"/>',
  filter: '<path d="M3 5h18l-7 8v6l-4 2v-8z"/>',
  sort: '<path d="M7 4v16m0 0l-3-3m3 3l3-3"/><path d="M17 20V4m0 0l-3 3m3-3l3 3"/>',
  file: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>',
  download: '<path d="M12 3v12m0 0l4-4m-4 4l-4-4"/><path d="M4 21h16"/>',
  upload: '<path d="M12 21V9m0 0l-4 4m4-4l4 4"/><path d="M4 3h16"/>',
  globe2: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18"/>',
  tree: '<path d="M12 3v5M12 8H6v4M12 8h6v4M6 12v3M18 12v3M12 8v3"/><rect x="9" y="18" width="6" height="3" rx="0.5"/>',
}

withDefaults(defineProps<{ name: string; size?: number | string; stroke?: number | string }>(), {
  size: 15,
  stroke: 1.7,
})

const dict = ICONS
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
    v-html="dict[name] || dict.layers"
  />
</template>
