<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Icon from '@/components/common/Icon.vue'
import { NAV_ADVANCED, NAV_MAIN, NAV_ALL } from '@/router/nav'
import { useSearchStore } from '@/stores/search'
import urlConfig from '@/config'
import { layerContentApi, model3dApi } from '@/api'
import { pickTotal } from '@/utils/format'
import { copyText } from '@/utils/clipboard'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const search = useSearchStore()
const userStore = useUserStore()
const USER = userStore.userName
const navOpen = ref(false)
watch(() => route.path, () => { navOpen.value = false })

const closed = ref<Record<string, boolean>>({})
const toggle = (id: string) => (closed.value[id] = !closed.value[id])
/** 「高级功能」整组折叠开关（默认收起） */
const advOpen = ref(false)
const toggleAdv = () => (advOpen.value = !advOpen.value)

const activePath = computed(() => route.path)

/* ---------------- 导航角标实时计数（数据管理 = 二维资源数 + 三维资源数） ---------------- */
const liveCounts = ref<Record<string, number>>({})
async function refreshManageCount() {
  let total = 0
  // 二维资源总数：GET /layerContent/GetAllCount（裸返回 Integer）
  try {
    const n = Number(await layerContentApi.getAllCount(USER))
    if (!Number.isNaN(n)) total += n
  } catch {
    /* 后端未启动时静默 */
  }
  // 三维资源总数：GET /model3d/page?pageSize=1 取 total
  try {
    const res: any = await model3dApi.page({ pageNum: 1, pageSize: 1 })
    total += Number(pickTotal(res, 0)) || 0
  } catch {
    /* 静默 */
  }
  liveCounts.value['/manage'] = total
}
onMounted(() => {
  void refreshManageCount()
  window.addEventListener('geodata:resources-changed', refreshManageCount)
})
onBeforeUnmount(() => window.removeEventListener('geodata:resources-changed', refreshManageCount))

/** 角标：有实时计数用计数，否则回退到端点数标注 */
function badgeOf(routePath: string, fallback = ''): string {
  const n = liveCounts.value[routePath]
  return n !== undefined ? String(n) : fallback
}

/** 当前页所属导航项（用于面包屑） */
const currentNav = computed(() => NAV_ALL.find((c) => c.route === route.path) || null)
/** 是否处于高级功能区 */
const inAdvanced = computed(() => NAV_ADVANCED.some((g) => g.children.some((c) => c.route === route.path)))

const pageTitle = computed(() => currentNav.value?.name || String(route.meta.title || ''))

function onSearch() {
  if (!search.keyword.trim()) return
  if (route.path !== '/manage') router.push('/manage')
}

const envLabel = computed(() => `${urlConfig.envName} · ${urlConfig.baseUrl || '同源'}`)
const mockMode = __GEODATA_MOCK__

function copyBase() {
  const text = mockMode ? `${window.location.origin}/api` : urlConfig.baseUrl || window.location.origin
  void copyText(text)
  ElMessage.success('已复制接口地址：' + text)
}

async function onLogout() {
  try {
    await userStore.logout()
  } catch {
    // 服务不可达时仍允许退出当前浏览器会话。
  }
  await router.replace('/login')
}
</script>

<template>
  <div class="layout">
    <!-- 顶栏 -->
    <div class="topbar">
      <button class="nav-trigger" type="button" :aria-label="navOpen ? '关闭导航' : '打开导航'" @click="navOpen = !navOpen">
        <Icon name="menu" :size="18" />
      </button>
      <div class="brand">
        <span class="logo">
          <Icon name="layers" :size="16" :stroke="1.8" />
        </span>
        {{ urlConfig.systemName }}
        <span class="env" @click="copyBase" title="点击复制接口地址">{{ envLabel }}</span>
      </div>

      <div class="gsearch">
        <Icon name="search" :size="14" />
        <input
          v-model="search.keyword"
          placeholder="检索数据集 / 图层 / 地图 / 样式…"
          @keyup.enter="onSearch"
        />
        <span class="kbd">/</span>
      </div>

      <div class="tb-sp"></div>

      <button class="tb-btn" @click="copyBase">
        <Icon name="db" :size="13" /> {{ mockMode ? '本地 Mock API' : 'geodata-service :8982' }}
      </button>

      <div class="tb-user">
        <span class="avatar">管</span>
        <span>管理员 · {{ userStore.userName }}</span>
        <button class="logout-button" type="button" @click="onLogout">退出</button>
      </div>
    </div>

    <div class="shell">
      <button v-if="navOpen" class="nav-backdrop" type="button" aria-label="关闭导航" @click="navOpen = false"></button>
      <!-- 侧栏：主导航（原型三页）+ 高级功能折叠组 -->
      <aside class="sidenav" :class="{ 'nav-open': navOpen }">
        <div class="nav-cap">数据中台</div>
        <div
          v-for="m in NAV_MAIN"
          :key="m.id"
          class="nav-main"
          :class="{ active: activePath === m.route }"
          @click="router.push(m.route)"
        >
          <span class="dot" :class="activePath === m.route ? 'b' : 'n'"></span>
          <Icon :name="m.icon" :size="14" />
          {{ m.name }}
          <span v-if="badgeOf(m.route)" class="nav-badge">{{ badgeOf(m.route) }}</span>
        </div>

        <div class="nav-cap adv-cap" @click="toggleAdv">
          高级功能
          <span class="cv" :class="{ open: advOpen }">▼</span>
        </div>
        <template v-if="advOpen">
          <template v-for="g in NAV_ADVANCED" :key="g.id">
            <div class="nav-grp" :class="{ closed: closed[g.id] }" @click="toggle(g.id)">
              <span class="ico"><Icon :name="g.icon" :size="15" /></span>
              {{ g.title }}
              <span class="cv">▼</span>
            </div>
            <div class="nav-kids" :class="{ closed: closed[g.id] }">
              <div
                v-for="c in g.children"
                :key="c.id"
                class="nav-item"
                :class="{ active: activePath === c.route }"
                @click="router.push(c.route)"
              >
                {{ c.name }}
                <span class="nb">{{ badgeOf(c.route, c.api) }}</span>
              </div>
            </div>
          </template>
        </template>
      </aside>

      <!-- 工作区 -->
      <main class="workspace">
        <div class="crumb">
          <span>{{ inAdvanced ? '高级功能' : '数据中台' }}</span>
          <span class="sep">/</span>
          <b>{{ pageTitle }}</b>
          <span class="hint"></span>
        </div>
        <div v-if="mockMode" class="mock-notice">当前展示本地示例数据；操作只影响本次开发服务进程，重启后恢复。</div>
        <div class="view">
          <router-view v-slot="{ Component }">
            <keep-alive :max="8">
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.nav-trigger,
.nav-backdrop { display: none; }
.layout {
  height: 100%;
}
.mock-notice {
  margin: 8px 14px 0;
  padding: 7px 10px;
  border: 1px solid #f1d9a2;
  border-radius: 5px;
  background: #fff9e9;
  color: #825d1e;
  font-size: 12px;
}
.topbar {
  height: var(--top-h);
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 14px;
  background: #fff;
  border-bottom: 1px solid var(--line);
  position: relative;
  z-index: 40;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--ink);
  white-space: nowrap;
}
.brand .logo {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: linear-gradient(135deg, #1163b5, #2b8fd8);
  display: grid;
  place-items: center;
  color: #fff;
  box-shadow: 0 1px 4px rgba(17, 99, 181, 0.35);
}
.brand .env {
  font-size: 10.5px;
  font-weight: 600;
  color: #1a7a5e;
  background: #eafaf4;
  border: 1px solid #bfe6d8;
  border-radius: 10px;
  padding: 0 7px;
  line-height: 16px;
  cursor: pointer;
}
.gsearch {
  flex: 0 1 430px;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  border: 1px solid var(--line-2);
  border-radius: 16px;
  padding: 0 12px;
  background: var(--panel-2);
  color: var(--ink-3);
  transition: 0.15s;
}
.gsearch:focus-within {
  border-color: var(--primary);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(17, 99, 181, 0.1);
}
.gsearch input {
  flex: 1;
  border: 0;
  outline: none;
  background: transparent;
  font-size: 12.5px;
  color: var(--ink);
}
.gsearch .kbd {
  font-size: 10.5px;
  color: var(--ink-3);
  border: 1px solid var(--line-2);
  border-radius: 4px;
  padding: 0 4px;
  background: #fff;
}
.tb-sp {
  flex: 1;
}
.tb-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 11px;
  border: 1px solid var(--line-2);
  border-radius: var(--r-s);
  background: #fff;
  color: var(--ink-2);
  cursor: pointer;
  font-size: 12.5px;
  white-space: nowrap;
}
.tb-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-050);
}
.tb-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 12px;
  border-left: 1px solid var(--line);
  font-size: 12.5px;
  color: var(--ink-2);
  white-space: nowrap;
}
.tb-user .avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2b8fd8, #1a9c78);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 11.5px;
  font-weight: 600;
}
.logout-button {
  margin-left: 4px;
  padding: 3px 5px;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  font-size: 12px;
  cursor: pointer;
}
.logout-button:hover { color: var(--primary); }

.nav-cap {
  padding: 12px 16px 6px;
  font-size: 10.5px;
  font-weight: 700;
  color: var(--ink-3);
  letter-spacing: 1.4px;
}
/* 主导航项（原型样式：圆点 + 图标 + 文字） */
.nav-main {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 36px;
  margin: 2px 10px;
  padding: 0 11px;
  border-radius: 6px;
  color: var(--ink-2);
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  transition: 0.14s;
}
.nav-main:hover {
  background: var(--panel-2);
  color: var(--ink);
}
.nav-main.active {
  background: var(--primary-050);
  color: var(--primary);
  font-weight: 600;
}
.nav-badge {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--ink-3);
  background: var(--panel-2);
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 0 6px;
  line-height: 15px;
  font-weight: 400;
}
.nav-main.active .nav-badge {
  background: #fff;
  border-color: #bcd9f2;
  color: var(--primary);
}
/* 高级功能折叠标题 */
.adv-cap {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.adv-cap:hover {
  color: var(--primary);
}
.adv-cap .cv {
  margin-left: auto;
  font-size: 9px;
  transition: 0.18s;
}
.adv-cap .cv.open {
  transform: rotate(180deg);
}
.nav-grp {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px 6px;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 700;
  user-select: none;
  cursor: pointer;
}
.nav-grp .ico {
  width: 15px;
  height: 15px;
  color: var(--ink-3);
  display: inline-flex;
}
.nav-grp .cv {
  margin-left: auto;
  font-size: 9px;
  color: var(--ink-3);
  transition: 0.18s;
}
.nav-grp.closed .cv {
  transform: rotate(-90deg);
}
.nav-kids.closed {
  display: none;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px 7px 36px;
  cursor: pointer;
  color: var(--ink-2);
  font-size: 12.5px;
  border-left: 2.5px solid transparent;
  transition: 0.13s;
  user-select: none;
  white-space: nowrap;
}
.nav-item .nb {
  flex: 0 0 auto;
}
.nav-item:hover {
  background: var(--panel-2);
  color: var(--primary);
}
.nav-item.active {
  background: var(--primary-050);
  color: var(--primary);
  border-left-color: var(--primary);
  font-weight: 600;
}
.nav-item .nb {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--ink-3);
  background: var(--panel-2);
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 0 6px;
  line-height: 15px;
}
.nav-item.active .nb {
  background: #fff;
  border-color: #bcd9f2;
  color: var(--primary);
}
@media (max-width: 900px) {
  .nav-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    flex: 0 0 32px;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: #fff;
    color: var(--ink-2);
    cursor: pointer;
  }
  .sidenav {
    position: fixed;
    z-index: 52;
    top: var(--top-h);
    bottom: 0;
    left: 0;
    width: var(--nav-w);
    transform: translateX(-100%);
    transition: transform .18s ease;
    box-shadow: var(--sh-2);
  }
  .sidenav.nav-open { transform: translateX(0); }
  .nav-backdrop {
    display: block;
    position: fixed;
    z-index: 51;
    inset: var(--top-h) 0 0;
    border: 0;
    background: rgba(17, 31, 48, .28);
  }
  .topbar { gap: 9px; }
  .gsearch { min-width: 0; }
  .brand { flex: 0 0 auto; }
  .tb-btn { display: none; }
  .tb-user { padding-left: 0; border-left: 0; }
}
@media (max-width: 680px) {
  .gsearch, .tb-user > span:not(.avatar) { display: none; }
  .topbar { padding: 0 10px; }
  .brand { font-size: 13px; }
  .brand .env { display: none; }
  .logout-button { margin-left: 0; }
}
</style>
