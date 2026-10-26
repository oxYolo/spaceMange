<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Icon from '@/components/common/Icon.vue'
import { NAV_ADVANCED, NAV_MAIN, NAV_ALL } from '@/router/nav'
import { useSearchStore } from '@/stores/search'
import urlConfig from '@/config'
import { layerContentApi, model3dApi } from '@/api'
import { copyText } from '@/utils/clipboard'
import { useUserStore } from '@/auth/store'
import { createResourceCounter, type ResourceCountState } from '@/features/shell/resourceCount'
import { isGlobalSearchShortcut } from '@/features/shell/keyboard'
import { commandShortcutLabel, isCommandShortcut } from '@/features/shell/commands'
import CommandPalette from '@/features/shell/components/CommandPalette.vue'

const route = useRoute()
const router = useRouter()
const search = useSearchStore()
const userStore = useUserStore()
const navOpen = ref(false)
const mobile = ref(false)
const navTrigger = ref<HTMLButtonElement>()
const navPanel = ref<HTMLElement>()
const workspace = ref<HTMLElement>()
const searchInput = ref<HTMLInputElement>()
const commandPalette = ref<InstanceType<typeof CommandPalette>>()
const commandHint = ref('Ctrl K')
function openCommands() {
  navOpen.value = false
  commandPalette.value?.open()
}
const drawerOpen = computed(() => mobile.value && navOpen.value)
let focusBeforeNav: HTMLElement | null = null
let mediaQuery: MediaQueryList | undefined
const focusable = 'a[href], button:not([disabled]), input:not([disabled]), [tabindex="0"]'
const closed = ref<Record<string, boolean>>({})
const toggle = (id: string) => (closed.value[id] = !closed.value[id])
const advOpen = ref(false)
const toggleAdv = () => (advOpen.value = !advOpen.value)
const activePath = computed(() => route.path)
const currentNav = computed(() => NAV_ALL.find((c) => c.route === route.path) || null)
const activeGroup = computed(() => NAV_ADVANCED.find((g) => g.children.some((c) => c.route === route.path)))
const pageTitle = computed(() => currentNav.value?.name || String(route.meta.title || ''))
const userInitial = computed(() => userStore.userName.slice(0, 1).toUpperCase())

watch(() => route.path, () => {
  if (activeGroup.value) {
    advOpen.value = true
    closed.value[activeGroup.value.id] = false
  }
  if (navOpen.value) closeNav(false)
}, { immediate: true })

async function openNav() {
  focusBeforeNav = document.activeElement instanceof HTMLElement ? document.activeElement : null
  if (activeGroup.value) {
    advOpen.value = true
    closed.value[activeGroup.value.id] = false
  }
  navOpen.value = true
  await nextTick()
  const selected = navPanel.value?.querySelector<HTMLElement>('a[aria-current="page"]')
  ;(selected || navPanel.value?.querySelector<HTMLElement>(focusable))?.focus({ preventScroll: true })
}

function closeNav(restoreFocus = true) {
  navOpen.value = false
  void nextTick(() => {
    if (!mobile.value) return
    const target = restoreFocus ? focusBeforeNav || navTrigger.value : workspace.value
    target?.focus({ preventScroll: true })
  })
}

function focusWorkspace() { workspace.value?.focus({ preventScroll: true }) }

/** 离场只保留画面，旧页面和遮罩立即退出焦点与操作范围。 */
function deactivateLeavingView(element: Element) {
  if (element instanceof HTMLElement) element.inert = true
  element.setAttribute('aria-hidden', 'true')
}
function activateEnteringView(element: Element) {
  if (element instanceof HTMLElement) element.inert = false
  element.removeAttribute('aria-hidden')
}

function onMediaChange() {
  mobile.value = mediaQuery?.matches ?? false
  if (!mobile.value) navOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  const target = event.target instanceof Element ? event.target : null
  const inDialog = Boolean(target?.closest('[role="dialog"], [aria-modal="true"]'))
  if (isCommandShortcut(event, inDialog && !drawerOpen.value)) {
    event.preventDefault()
    openCommands()
    return
  }
  if (drawerOpen.value) {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeNav()
      return
    }
    if (event.key === 'Tab') {
      const items = [...(navPanel.value?.querySelectorAll<HTMLElement>(focusable) || [])]
        .filter((item) => !item.closest('[inert]') && item.getClientRects().length > 0)
      const first = items[0]
      const last = items[items.length - 1]
      if (first && event.shiftKey && (document.activeElement === first || !navPanel.value?.contains(document.activeElement))) {
        event.preventDefault()
        last?.focus()
      } else if (last && !event.shiftKey && (document.activeElement === last || !navPanel.value?.contains(document.activeElement))) {
        event.preventDefault()
        first?.focus()
      }
    }
    return
  }
  const editing = Boolean(target?.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"]), [role="textbox"]'))
  if (isGlobalSearchShortcut(event, editing, inDialog)) {
    event.preventDefault()
    searchInput.value?.focus()
    searchInput.value?.select()
  }
}

const resourceCount = ref<ResourceCountState>({ value: null, loading: false, unavailable: false })
const counter = createResourceCounter({
  countLayers: (user) => layerContentApi.getAllCount(user),
  countModels: () => model3dApi.page({ pageNum: 1, pageSize: 1 }),
}, (state) => { resourceCount.value = state })
const countLabel = computed(() => resourceCount.value.value === null ? (resourceCount.value.loading ? '…' : '—') : String(resourceCount.value.value))
const countTitle = computed(() => resourceCount.value.unavailable
  ? resourceCount.value.value === null ? '资源总数暂不可用' : '统计暂不可用，显示上次完整结果'
  : resourceCount.value.loading ? '正在更新资源总数' : '二维与三维资源总数')
function refreshManageCount() { void counter.refresh(userStore.userName) }
watch(() => userStore.userName, refreshManageCount, { immediate: true })

onMounted(() => {
  commandHint.value = commandShortcutLabel(navigator.platform)
  mediaQuery = window.matchMedia('(max-width: 900px)')
  onMediaChange()
  mediaQuery.addEventListener('change', onMediaChange)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('geodata:resources-changed', refreshManageCount)
})
onBeforeUnmount(() => {
  counter.dispose()
  mediaQuery?.removeEventListener('change', onMediaChange)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('geodata:resources-changed', refreshManageCount)
})

function onSearch() {
  search.submit()
  if (route.path !== '/manage') {
    const keyword = search.keyword.trim()
    void router.push({ path: '/manage', query: keyword ? { q: keyword } : {} })
  }
}

const envLabel = computed(() => urlConfig.envName)
function copyBase() {
  const text = new URL(urlConfig.baseUrl || '/', window.location.origin).href
  void copyText(text).then(ok => ok ? ElMessage.success('接口地址已复制') : ElMessage.error('复制失败，请重试'))
}

function openLoginPage() { void router.push('/login') }
</script>

<template>
  <div class="layout">
    <!-- 顶栏 -->
    <a class="skip-link" href="#workspace-main" @click.prevent="focusWorkspace">跳到工作区</a>
    <header class="topbar" :inert="drawerOpen">
      <button ref="navTrigger" class="nav-trigger" type="button" aria-label="打开导航" :aria-expanded="navOpen" aria-controls="console-navigation" @click="navOpen ? closeNav() : openNav()">
        <Icon name="menu" :size="18" />
      </button>
      <div class="brand">
        <span class="logo" aria-hidden="true">
          <Icon name="layers" :size="18" :stroke="1.8" />
        </span>
        <span class="brand-name">{{ urlConfig.systemName }}<small>空间数据工作台</small></span>
        <span class="env">{{ envLabel }}</span>
      </div>

      <form class="gsearch" role="search" @submit.prevent="onSearch">
        <Icon name="search" :size="16" aria-hidden="true" />
        <input
          ref="searchInput"
          v-model="search.keyword"
          type="search"
          aria-label="搜索数据名称"
          aria-keyshortcuts="/"
          placeholder="搜索数据名称…"
        />
        <kbd title="按 / 聚焦搜索" aria-hidden="true">/</kbd>
        <button class="command-launcher" type="button" aria-label="打开快捷导航" aria-keyshortcuts="Control+K Meta+K" :title="`快捷导航（${commandHint}）`" @click="openCommands"><Icon name="grid" :size="14" aria-hidden="true" /><span>快捷</span><kbd aria-hidden="true">{{ commandHint }}</kbd></button>
      </form>

      <div class="tb-sp"></div>

      <button class="tb-btn" type="button" title="复制当前接口地址" @click="copyBase">
        <Icon name="db" :size="14" aria-hidden="true" /> 接口地址
      </button>

      <div class="tb-user">
        <span class="avatar" aria-hidden="true">{{ userInitial }}</span>
        <span class="user-name">{{ userStore.userName }}</span>
        <button class="logout-button" type="button" @click="openLoginPage">登录页</button>
      </div>
    </header>

    <div class="shell">
      <Transition name="nav-overlay" @before-enter="activateEnteringView" @before-leave="deactivateLeavingView" @leave-cancelled="activateEnteringView">
        <button v-if="drawerOpen" class="nav-backdrop" type="button" tabindex="-1" aria-label="关闭导航" @click="closeNav()"></button>
      </Transition>
      <aside id="console-navigation" ref="navPanel" class="sidenav" :class="{ 'nav-open': navOpen }" :inert="mobile && !navOpen" :role="drawerOpen ? 'dialog' : undefined" :aria-modal="drawerOpen ? true : undefined" aria-label="工作台导航" tabindex="-1">
        <div class="drawer-heading"><strong>工作台导航</strong><button class="drawer-close" type="button" aria-label="关闭导航" @click="closeNav()"><Icon name="x" :size="18" /></button></div>
        <nav aria-label="主导航">
        <div class="nav-cap">数据工作台 <span>WORKSPACE</span></div>
        <RouterLink
          v-for="m in NAV_MAIN"
          :key="m.id"
          :to="m.route"
          class="nav-main"
          :class="{ active: activePath === m.route }"
          :aria-current="activePath === m.route ? 'page' : undefined"
          @click="activePath === m.route && drawerOpen ? closeNav(false) : undefined"
        >
          <Icon :name="m.icon" :size="17" aria-hidden="true" />
          {{ m.name }}
          <span v-if="m.route === '/manage'" class="nav-badge" :class="{ stale: resourceCount.unavailable }" :title="countTitle" :aria-label="`${countTitle}：${countLabel}`">{{ countLabel }}</span>
        </RouterLink>

        <button class="nav-cap adv-cap" type="button" :aria-expanded="advOpen" aria-controls="advanced-navigation" @click="toggleAdv">
          <span class="adv-label"><Icon name="grid" :size="16" aria-hidden="true" />高级功能</span>
          <span class="cv" :class="{ open: advOpen }" aria-hidden="true"><Icon name="chevron-down" :size="14" /></span>
        </button>
        <div id="advanced-navigation" class="nav-collapse" :class="{ expanded: advOpen }" :inert="!advOpen" :aria-hidden="!advOpen">
          <div class="nav-collapse-content">
          <template v-for="g in NAV_ADVANCED" :key="g.id">
            <button class="nav-grp" :class="{ closed: closed[g.id] }" type="button" :aria-expanded="!closed[g.id]" :aria-controls="`navigation-${g.id}`" @click="toggle(g.id)">
              <span class="ico" aria-hidden="true"><Icon :name="g.icon" :size="15" /></span>
              {{ g.title }}
              <span class="cv" aria-hidden="true"><Icon name="chevron-down" :size="14" /></span>
            </button>
            <div :id="`navigation-${g.id}`" class="nav-kids nav-collapse" :class="{ expanded: !closed[g.id] }" :inert="!!closed[g.id]" :aria-hidden="!!closed[g.id]">
              <div class="nav-collapse-content">
              <RouterLink
                v-for="c in g.children"
                :key="c.id"
                :to="c.route"
                class="nav-item"
                :class="{ active: activePath === c.route }"
                :aria-current="activePath === c.route ? 'page' : undefined"
                @click="activePath === c.route && drawerOpen ? closeNav(false) : undefined"
              >
                {{ c.name }}
              </RouterLink>
              </div>
            </div>
          </template>
          </div>
        </div>
        </nav>
        <div class="nav-footer"><span class="dot b" aria-hidden="true"></span>空间数据 · 统一管理</div>
      </aside>

      <main id="workspace-main" ref="workspace" class="workspace" :inert="drawerOpen" :aria-label="pageTitle" tabindex="-1">
        <nav class="crumb" aria-label="面包屑">
          <RouterLink to="/manage">工作台</RouterLink>
          <span class="sep" aria-hidden="true">/</span>
          <template v-if="activeGroup"><span>{{ activeGroup.title }}</span><span class="sep" aria-hidden="true">/</span></template>
          <strong aria-current="page">{{ pageTitle }}</strong>
        </nav>
        <div class="view">
          <div class="route-stack">
            <router-view v-slot="{ Component }">
              <Transition name="console-page" @before-enter="activateEnteringView" @before-leave="deactivateLeavingView" @leave-cancelled="activateEnteringView">
                <div :key="route.path" class="route-page">
                  <component :is="Component" />
                </div>
              </Transition>
            </router-view>
          </div>
        </div>
      </main>
    </div>
    <CommandPalette ref="commandPalette" />
  </div>
</template>

<style scoped>
.layout { height: 100%; }
.topbar { height: var(--top-h); display: flex; align-items: center; gap: 20px; padding: 0 20px; background: #fff; border-bottom: 1px solid var(--line); position: relative; z-index: 40; }
.brand { display: flex; align-items: center; gap: 10px; min-width: 0; color: var(--ink); }
.brand .logo { width: 34px; height: 34px; flex: 0 0 34px; border-radius: 9px; background: #1163b5; display: grid; place-items: center; color: #fff; box-shadow: 0 3px 8px rgba(17, 99, 181, .13); }
.brand-name { min-width: 0; font-size: 14px; line-height: 18px; font-weight: 700; white-space: nowrap; }
.brand-name small { display: block; margin-top: 2px; color: var(--ink-3); font-size: 10px; line-height: 13px; font-weight: 400; letter-spacing: .6px; }
.env { color: #4f6a86; background: #f1f5fa; border: 1px solid var(--line); border-radius: 5px; padding: 2px 7px; font-size: 10px; white-space: nowrap; }
.gsearch { flex: 0 1 350px; display: flex; align-items: center; gap: 9px; min-width: 120px; height: 34px; margin: 0; border: 1px solid var(--line); border-radius: 7px; padding: 0 10px; background: var(--panel-2); color: var(--ink-3); transition: border-color var(--motion-fast, 160ms) var(--motion-ease, ease), box-shadow var(--motion-fast, 160ms) var(--motion-ease, ease); }
.gsearch:focus-within { border-color: var(--primary); background: #fff; box-shadow: 0 0 0 3px rgba(17, 99, 181, .09); }
.gsearch input { flex: 1; width: 100%; min-width: 0; border: 0; outline: none; background: transparent; font: inherit; font-size: 12px; color: var(--ink); }
.gsearch input::placeholder { color: var(--ink-3); }
.gsearch kbd { min-width: 19px; text-align: center; font: inherit; font-size: 11px; line-height: 19px; color: var(--ink-3); border: 1px solid var(--line-2); border-radius: 4px; background: #fff; }
.command-launcher { display: inline-flex; align-items: center; flex-shrink: 0; gap: 5px; margin-left: 3px; padding: 0 0 0 9px; border: 0; border-left: 1px solid var(--line); color: var(--ink-2); background: transparent; font: inherit; font-size: 11px; cursor: pointer; }
.command-launcher:hover { color: var(--primary); }
.command-launcher kbd { padding: 0 4px; font-size: 9px; white-space: nowrap; }
.tb-sp { flex: 1; }
.tb-btn { display: inline-flex; align-items: center; gap: 6px; height: 30px; padding: 0 9px; border: 0; border-radius: var(--r-s); background: transparent; color: var(--ink-2); cursor: pointer; font-size: 12px; white-space: nowrap; }
.tb-btn:hover { color: var(--primary); background: var(--primary-050); }
.tb-user { display: flex; align-items: center; gap: 8px; min-width: 0; padding-left: 16px; border-left: 1px solid var(--line); font-size: 12px; color: var(--ink-2); white-space: nowrap; }
.avatar { width: 30px; height: 30px; flex: 0 0 30px; border: 1px solid #dae7f5; border-radius: 50%; background: #edf4fc; color: var(--primary); display: grid; place-items: center; font-size: 11px; font-weight: 600; }
.user-name { max-width: 90px; overflow: hidden; text-overflow: ellipsis; }
.logout-button { padding: 4px 6px; border: 0; border-radius: 4px; background: transparent; color: var(--ink-3); font-size: 12px; cursor: pointer; }
.logout-button:hover { color: var(--primary); background: var(--primary-050); }
.logout-button:disabled { opacity: .5; cursor: progress; }
.nav-cap { display: flex; align-items: center; justify-content: space-between; padding: 12px 18px 10px; color: var(--ink-3); font-size: 11px; font-weight: 500; }
.nav-cap > span { font-size: 8px; letter-spacing: .9px; color: #9aabba; }
.nav-main { position: relative; display: flex; align-items: center; gap: 11px; min-height: 40px; margin: 3px 10px; padding: 0 13px; border-radius: 7px; color: var(--ink-2); font-size: 13px; transition: background var(--motion-fast, 160ms) var(--motion-ease, ease), color var(--motion-fast, 160ms) var(--motion-ease, ease), transform var(--motion-base, 240ms) var(--motion-ease, ease); }
.nav-main:hover { background: var(--panel-2); color: var(--ink); }
.nav-main.active { background: var(--primary-050); color: var(--primary); font-weight: 600; }
.nav-main::before { content: ''; position: absolute; width: 3px; height: 16px; left: 0; border-radius: 2px; background: var(--primary); opacity: 0; transform: scaleY(.35); transition: opacity var(--motion-fast, 160ms) var(--motion-ease, ease), transform var(--motion-base, 240ms) var(--motion-ease, ease); }
.nav-main.active::before { opacity: 1; transform: scaleY(1); }
.nav-badge { margin-left: auto; padding: 0 6px; min-width: 22px; border-radius: 5px; background: #fff; border: 1px solid var(--line); color: var(--ink-3); font-size: 10px; line-height: 17px; text-align: center; font-variant-numeric: tabular-nums; font-weight: 400; }
.active .nav-badge { border-color: #d6e4f5; color: var(--primary); }
.nav-badge.stale { color: var(--ink-3); border-style: dashed; }
.adv-cap { width: calc(100% - 28px); margin: 20px 14px 3px; padding: 16px 4px 8px; border: 0; border-top: 1px solid var(--line); background: transparent; cursor: pointer; text-align: left; }
.adv-cap > span:first-child { color: inherit; font-size: 11px; letter-spacing: 0; }
.adv-label { display: inline-flex; align-items: center; gap: 9px; }
.adv-label svg { flex: 0 0 auto; }
.adv-cap:hover { color: var(--primary); }
.cv { display: inline-flex; align-items: center; justify-content: center; margin-left: auto; transition: transform var(--motion-base, 240ms) var(--motion-ease, ease); }
.adv-cap .cv { transform: rotate(-90deg); }
.adv-cap .cv.open { transform: rotate(0); }
.nav-grp { width: 100%; display: flex; align-items: center; gap: 9px; padding: 11px 20px 7px; border: 0; background: transparent; color: var(--ink-2); font-size: 11px; font-weight: 600; text-align: left; cursor: pointer; }
.nav-grp .ico { display: inline-flex; color: #91a1b2; }
.nav-grp .cv { color: var(--ink-3); }
.nav-grp.closed .cv { transform: rotate(-90deg); }
.nav-item { position: relative; display: flex; align-items: center; min-height: 34px; margin: 2px 10px; padding: 5px 10px 5px 37px; border-radius: 6px; color: var(--ink-2); font-size: 12px; transition: background var(--motion-fast, 160ms) var(--motion-ease, ease), color var(--motion-fast, 160ms) var(--motion-ease, ease), transform var(--motion-base, 240ms) var(--motion-ease, ease); }
.nav-item::before { content: ''; position: absolute; left: 22px; width: 4px; height: 4px; border-radius: 50%; background: #c0ccd8; }
.nav-item:hover { color: var(--primary); background: var(--panel-2); }
.nav-item.active { color: var(--primary); background: var(--primary-050); font-weight: 600; }
.nav-item.active::before { background: var(--primary); }
.nav-footer { display: flex; align-items: center; gap: 7px; margin: 26px 18px 0; padding-top: 16px; border-top: 1px solid var(--line); font-size: 10px; color: var(--ink-3); }
.nav-trigger, .nav-backdrop, .drawer-heading { display: none; }
.skip-link { position: fixed; top: 8px; left: 12px; z-index: 100; padding: 7px 12px; border: 1px solid var(--primary); border-radius: 5px; background: #fff; transform: translateY(-200%); }
.skip-link:focus { transform: translateY(0); }
@media (max-width: 1200px) {
  .topbar { gap: 14px; padding: 0 16px; }
  .env { display: none; }
  .gsearch { flex-basis: 300px; }
  .tb-btn { display: none; }
}
@media (max-width: 900px) {
  .nav-trigger, .drawer-close { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; flex: 0 0 32px; border: 1px solid var(--line); border-radius: 6px; background: #fff; color: var(--ink-2); cursor: pointer; }
  .drawer-heading { display: flex; align-items: center; justify-content: space-between; padding: 2px 14px 10px; border-bottom: 1px solid var(--line); margin-bottom: 6px; font-size: 13px; }
  .drawer-close { border: 0; }
  .sidenav { position: fixed; z-index: 52; top: var(--top-h); bottom: 0; left: 0; width: min(280px, calc(100vw - 56px)); transform: translateX(-105%); transition: transform var(--motion-base, 240ms) var(--motion-ease, ease), opacity var(--motion-base, 240ms) var(--motion-ease, ease); opacity: 0; box-shadow: var(--sh-2); }
  .sidenav.nav-open { transform: translateX(0); opacity: 1; }
  .nav-backdrop { display: block; position: fixed; z-index: 51; inset: var(--top-h) 0 0; border: 0; background: rgba(17, 31, 48, .26); }
  .tb-user { padding-left: 0; border-left: 0; }
  .brand-name small { display: none; }
  .brand .logo { width: 30px; height: 30px; flex-basis: 30px; border-radius: 7px; }
}
@media (max-width: 680px) {
  .topbar { display: grid; grid-template-columns: 32px minmax(0, 1fr) auto; grid-template-rows: 32px 32px; align-content: center; column-gap: 9px; row-gap: 8px; padding: 10px 12px; }
  .brand { gap: 7px; }
  .brand .logo { display: none; }
  .brand-name { overflow: hidden; text-overflow: ellipsis; font-size: 13px; }
  .gsearch { grid-column: 1 / -1; grid-row: 2; width: 100%; height: 32px; }
  .gsearch > kbd, .command-launcher kbd { display: none; }
  .tb-sp, .user-name, .avatar { display: none; }
  .tb-user { grid-column: 3; grid-row: 1; }
  .logout-button { padding: 5px 2px; }
}
.nav-collapse { display: grid; grid-template-rows: 0fr; opacity: 0; transition: grid-template-rows var(--motion-base, 240ms) var(--motion-ease, ease), opacity var(--motion-fast, 160ms) var(--motion-ease, ease); }
.nav-collapse.expanded { grid-template-rows: 1fr; opacity: 1; }
.nav-collapse-content { min-height: 0; overflow: hidden; }
.nav-grp { transition: color var(--motion-fast, 160ms) var(--motion-ease, ease), background var(--motion-fast, 160ms) var(--motion-ease, ease); }
.nav-grp:hover { color: var(--primary); background: var(--panel-2); }
.nav-main:hover, .nav-item:hover { transform: translateX(2px); }
.nav-main.active:hover, .nav-item.active:hover { transform: translateX(0); }
.nav-main:active, .nav-item:active { transform: scale(.985); }
.nav-main:focus-visible, .nav-item:focus-visible { background: var(--primary-050); }
.nav-badge { transition: color var(--motion-fast, 160ms), border-color var(--motion-fast, 160ms), background var(--motion-fast, 160ms); }
.route-stack { position: relative; isolation: isolate; height: 100%; min-height: 100%; }
.route-page { height: 100%; min-width: 0; min-height: 0; transform-origin: 50% 0; }
.console-page-enter-active { position: relative; z-index: 1; transition: opacity var(--motion-base, 240ms) var(--motion-ease, ease), transform var(--motion-base, 240ms) var(--motion-ease, ease); }
.console-page-leave-active { position: absolute; inset: 0; z-index: 0; pointer-events: none; user-select: none; transition: opacity var(--motion-fast, 160ms) var(--motion-ease, ease), transform var(--motion-fast, 160ms) var(--motion-ease, ease); }
.console-page-enter-from { opacity: 0; transform: translate3d(0, 12px, 0) scale(.995); }
.console-page-leave-to { opacity: 0; transform: translate3d(0, -6px, 0); }
.nav-overlay-enter-active, .nav-overlay-leave-active { transition: opacity var(--motion-base, 240ms) var(--motion-ease, ease); }
.nav-overlay-leave-active { pointer-events: none; }
.nav-overlay-enter-from, .nav-overlay-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .sidenav, .nav-main, .nav-main::before, .nav-item, .nav-grp, .nav-badge, .nav-collapse, .gsearch, .cv,
  .console-page-enter-active, .console-page-leave-active, .nav-overlay-enter-active, .nav-overlay-leave-active { transition: none !important; }
  .console-page-enter-from, .console-page-leave-to, .nav-overlay-enter-from, .nav-overlay-leave-to { opacity: 1; transform: none; }
  .nav-main:hover, .nav-item:hover, .nav-main:active, .nav-item:active { transform: none; }
}
</style>
