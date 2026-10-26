<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { buildServiceIntegration, type IntegrationService } from '../serviceIntegration'
import { copyText } from '@/utils/clipboard'

const props = defineProps<{ service: IntegrationService }>()
const id = useId()
const integration = computed(() => buildServiceIntegration(props.service, window.location.href))
const selectedId = ref('curl')
const example = computed(() => integration.value.examples.find((item) => item.id === selectedId.value) || integration.value.examples[0])
watch(integration, (value) => {
  if (!value.examples.some((item) => item.id === selectedId.value)) selectedId.value = value.examples[0]?.id || ''
})

async function copy(value: string, label: string) {
  if (!value) return
  if (await copyText(value)) ElMessage.success(`${label}已复制`)
  else ElMessage.error('复制失败，请手动选择内容')
}
function changeExample(event: KeyboardEvent, index: number) {
  const count = integration.value.examples.length
  let next = index
  if (event.key === 'ArrowRight') next = (index + 1) % count
  else if (event.key === 'ArrowLeft') next = (index - 1 + count) % count
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = count - 1
  else return
  event.preventDefault()
  selectedId.value = integration.value.examples[next].id
  ;(event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
}
</script>

<template>
  <section class="integration" :aria-labelledby="`${id}-title`">
    <header class="integration-head">
      <h4 :id="`${id}-title`">服务接入</h4>
      <span>{{ integration.protocol }}</span>
    </header>
    <p v-if="integration.note" class="integration-note" role="note">{{ integration.note }}</p>
    <template v-if="integration.sourceUrl">
      <div class="integration-address-head">
        <span>当前服务地址</span>
        <button type="button" @click="copy(integration.sourceUrl, '服务地址')">复制地址</button>
      </div>
      <code class="integration-address">{{ integration.sourceUrl }}</code>
      <div class="integration-links">
        <button v-if="integration.capabilitiesUrl" type="button" @click="copy(integration.capabilitiesUrl, '能力文档地址')">复制能力文档地址</button>
        <a v-if="integration.protocol !== 'WFS' || integration.featureLimit" :href="integration.featureLimit ? integration.requestUrl : integration.sourceUrl" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer">{{ integration.featureLimit ? '打开限额请求 ↗' : '打开原始地址 ↗' }}</a>
      </div>
    </template>
    <template v-if="example">
      <div class="integration-tabs" role="tablist" aria-label="接入示例">
        <button
          v-for="(item, index) in integration.examples"
          :id="`${id}-${item.id}`"
          :key="item.id"
          type="button"
          role="tab"
          :aria-selected="item.id === example.id"
          :aria-controls="`${id}-code`"
          :tabindex="item.id === example.id ? 0 : -1"
          @click="selectedId = item.id"
          @keydown="changeExample($event, index)"
        >{{ item.label }}</button>
      </div>
      <section :id="`${id}-code`" role="tabpanel" :aria-labelledby="`${id}-${example.id}`" class="integration-example">
        <p class="integration-requirement">{{ example.requirement }}</p>
        <p v-if="integration.featureLimit" class="feature-limit">要素请求上限 {{ integration.featureLimit }} 条</p>
        <div class="code-head"><span>{{ example.language }}</span><button type="button" @click="copy(example.code, '接入示例')">复制代码</button></div>
        <pre tabindex="0" aria-label="可复制的接入示例"><code>{{ example.code }}</code></pre>
      </section>
      <p class="integration-footer">示例仅供复制，请在客户端项目中运行。</p>
    </template>
  </section>
</template>

<style scoped>
.integration { margin-top: 12px; padding: 11px; border: 1px solid #cfdfef; border-radius: 7px; background: #fff; cursor: default; }
.integration-head, .integration-address-head, .code-head, .integration-links { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.integration-head { margin-bottom: 9px; }
.integration-head h4 { margin: 0; color: var(--ink); font-size: 12.5px; }
.integration-head > span { color: var(--ink-3); font-size: 10px; }
.integration-note { margin: 0 0 10px; padding: 7px 8px; border-radius: 5px; background: #fff8e8; color: #8d641d; font-size: 11px; line-height: 1.7; }
.integration-address-head { margin-bottom: 5px; font-size: 11px; color: var(--ink-3); }
button, a { color: var(--primary); font-size: 11px; }
button { border: 0; padding: 3px 0; background: none; cursor: pointer; }
button:hover, a:hover { text-decoration: underline; }
button:focus-visible, a:focus-visible, pre:focus-visible { outline: 2px solid #79afe5; outline-offset: 3px; }
.integration-address { display: block; padding: 7px 8px; max-height: 96px; overflow: auto; border-radius: 5px; background: #f4f7fb; color: #3b4b60; font: 10.5px/1.7 Consolas, Menlo, monospace; overflow-wrap: anywhere; user-select: text; }
.integration-links { justify-content: flex-start; flex-wrap: wrap; gap: 7px 12px; margin: 5px 0 10px; }
.integration-links a { text-decoration: none; }
.integration-tabs { display: flex; gap: 5px; padding-bottom: 7px; border-bottom: 1px solid var(--line); }
.integration-tabs button { padding: 5px 9px; border-radius: 5px; color: var(--ink-2); transition: color var(--motion-fast, 160ms), background var(--motion-fast, 160ms); }
.integration-tabs button[aria-selected="true"] { background: #eaf3fe; color: var(--primary); font-weight: 600; }
.integration-tabs button:hover { background: #f3f7fc; text-decoration: none; }
.integration-requirement, .integration-footer { margin: 8px 0; color: var(--ink-3); font-size: 10.5px; line-height: 1.7; }
.feature-limit { margin: 5px 0 8px; color: #397d62; font-size: 10.5px; }
.code-head { padding: 5px 8px; border-radius: 5px 5px 0 0; background: #e9eff7; color: #526477; font-size: 10px; }
pre { margin: 0; padding: 9px; max-height: 300px; overflow: auto; border-radius: 0 0 5px 5px; background: #132034; color: #deebfa; white-space: pre; font: 10px/1.7 Consolas, Menlo, monospace; tab-size: 2; }
pre code { font: inherit; padding: 0; background: none; color: inherit; border-radius: 0; }
.integration-footer { margin-bottom: 0; }
@media (max-width: 520px) { .integration { padding: 9px; } .integration-head, .integration-address-head { flex-wrap: wrap; } pre { max-height: 260px; } }
@media (prefers-reduced-motion: reduce) { .integration-tabs button { transition: none; } }
</style>
