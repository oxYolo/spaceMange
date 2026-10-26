<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Hide, Lock, User, View } from '@element-plus/icons-vue'
import { safeLoginRedirect } from '@/auth/redirect'
import { showError } from '@/utils/feedback'
import { getRememberedAccount } from '@/auth/session'
import { useUserStore } from './store'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const rememberedAccount = getRememberedAccount()
const username = ref(rememberedAccount)
const password = ref('')
const rememberMe = ref(Boolean(rememberedAccount))
const passwordVisible = ref(false)
const loading = ref(false)

async function submit() {
  if (loading.value) return
  if (!username.value.trim()) return ElMessage.warning('请输入账号')
  if (!password.value) return ElMessage.warning('请输入密码')

  loading.value = true
  try {
    await user.login(username.value, password.value, rememberMe.value)
    password.value = ''
    await router.replace(safeLoginRedirect(route.query.redirect))
  } catch (error) {
    showError(error, '登录失败')
  } finally {
    loading.value = false
  }
}

function forgotPassword() {
  ElMessage.info('请联系系统管理员重置密码')
}
</script>

<template>
  <main class="login-page">
    <div class="login-grid" aria-hidden="true"></div>
    <div class="orbit orbit-left" aria-hidden="true"></div>
    <div class="orbit orbit-left-inner" aria-hidden="true"></div>
    <div class="orbit orbit-right" aria-hidden="true"></div>
    <div class="light-trace light-trace-one" aria-hidden="true"></div>
    <div class="light-trace light-trace-two" aria-hidden="true"></div>

    <div class="login-content">
      <section class="intro" aria-label="平台介绍">
        <div class="identity">
          <div class="identity-mark">低</div>
          <div>
            <div class="identity-name">低空监管数据中台</div>
            <div class="identity-en">LOW-ALTITUDE REGULATORY DATA PLATFORM</div>
          </div>
        </div>

        <h1>低空空域 · 数据中枢</h1>
        <p class="intro-copy">
          汇聚矢量、栅格、地形、倾斜模型、白模与点云六类空间数据，提供数据<br class="desktop-break" />
          接入、服务发布与图层配置的一体化管控能力，为低空飞行监管、空域规<br class="desktop-break" />
          划与态势研判提供统一的数据底座。
        </p>

        <div class="intro-stats">
          <div><strong>6</strong><span>空间数据类型</span></div>
          <div><strong>7×24</strong><span>服务在线</span></div>
          <div><strong>秒级</strong><span>响应体验</span></div>
        </div>
      </section>

      <section class="login-card" aria-label="账户登录">
        <h2>账户登录</h2>
        <p class="card-subtitle">请输入账号和密码进入数据管理平台</p>

        <form @submit.prevent="submit" novalidate>
          <label for="login-username">账号</label>
          <div class="input-wrap">
            <User class="input-icon" aria-hidden="true" />
            <input
              id="login-username"
              v-model="username"
              type="text"
              name="username"
              placeholder="请输入账号"
              autocomplete="username"
              autofocus
            />
          </div>

          <label for="login-password">密码</label>
          <div class="input-wrap">
            <Lock class="input-icon" aria-hidden="true" />
            <input
              id="login-password"
              v-model="password"
              :type="passwordVisible ? 'text' : 'password'"
              name="password"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
            <button
              class="visibility-button"
              type="button"
              :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
              @click="passwordVisible = !passwordVisible"
            >
              <Hide v-if="passwordVisible" />
              <View v-else />
            </button>
          </div>

          <div class="form-options">
            <label class="remember-option">
              <input v-model="rememberMe" type="checkbox" />
              <span>记住账号</span>
            </label>
            <button type="button" class="forgot-link" @click="forgotPassword">忘记密码?</button>
          </div>

          <button class="submit-button" type="submit" :disabled="loading">
            {{ loading ? '登 录 中...' : '登 录' }}
          </button>
        </form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
  isolation: isolate;
  width: 100%;
  height: 100dvh;
  min-height: 550px;
  overflow-x: hidden;
  overflow-y: auto;
  color: #fff;
  background:
    radial-gradient(ellipse 475px 375px at 9% 12%, rgba(13, 88, 130, .33), transparent 76%),
    radial-gradient(ellipse 570px 480px at 93% 78%, rgba(26, 85, 178, .55), transparent 78%),
    linear-gradient(111deg, #06254a 0%, #052149 46%, #06204b 100%);
}
.login-grid {
  position: absolute;
  inset: 0;
  z-index: -1;
  opacity: .54;
  background-image:
    repeating-linear-gradient(90deg, transparent 0 80px, rgba(121, 180, 226, .067) 81px, transparent 82px),
    repeating-linear-gradient(0deg, transparent 0 80px, rgba(121, 180, 226, .057) 81px, transparent 82px);
  mask-image: linear-gradient(90deg, transparent 0, #000 27%, #000 76%, transparent 100%);
  animation: login-background-in calc(var(--motion-slow, 360ms) * 2) ease-out both;
}
.orbit {
  position: absolute;
  z-index: -1;
  border: 1px solid rgba(149, 193, 230, .09);
  border-radius: 50%;
  pointer-events: none;
  animation: login-background-in calc(var(--motion-slow, 360ms) * 2) ease-out 80ms both;
}
.orbit-left { width: 590px; height: 590px; left: -209px; top: -229px; }
.orbit-left-inner { width: 370px; height: 370px; left: -84px; top: -103px; }
.orbit-right { width: 385px; height: 385px; right: -162px; bottom: -183px; }
.light-trace {
  position: absolute;
  z-index: -1;
  width: 112%;
  height: 1px;
  left: -6%;
  background: linear-gradient(90deg, transparent, rgba(151, 200, 238, .15), transparent);
  transform-origin: center;
  pointer-events: none;
  animation: login-background-in calc(var(--motion-slow, 360ms) * 2) ease-out 160ms both;
}
.light-trace-one { top: 58%; transform: rotate(-13deg); }
.light-trace-two { top: 56%; transform: rotate(11deg); opacity: .53; }
.login-content {
  width: min(810px, calc(100% - 48px));
  min-height: 100%;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 474px) 294px;
  align-items: center;
  gap: 43px;
}
.intro { width: 100%; transform: translateY(1px); }
.identity { display: flex; align-items: center; gap: 9px; }
.identity, .intro h1, .intro-copy, .intro-stats {
  animation: login-content-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) both;
}
.intro h1 { animation-delay: 60ms; }
.intro-copy { animation-delay: 110ms; }
.intro-stats { animation-delay: 160ms; }
.identity-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #6dd6fd, #287de8);
  box-shadow: 0 0 18px rgba(48, 171, 255, .46);
  font-size: 16px;
  font-weight: 800;
  transition: transform var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)), box-shadow var(--motion-base, 240ms);
}
.identity:hover .identity-mark { transform: translateY(-1px); box-shadow: 0 0 24px rgba(48, 171, 255, .58); }
.identity-name { font-size: 14px; line-height: 17px; font-weight: 700; letter-spacing: .2px; }
.identity-en { margin-top: 2px; color: rgba(210, 230, 251, .54); font-size: 8px; letter-spacing: 1.6px; line-height: 11px; }
.intro h1 { margin: 28px 0 12px; font-size: 25px; line-height: 1.2; letter-spacing: .5px; font-weight: 760; }
.intro-copy { margin: 0; color: rgba(218, 234, 251, .62); font-size: 10px; line-height: 20px; letter-spacing: .05px; }
.intro-stats {
  display: flex;
  gap: 28px;
  width: 474px;
  max-width: 100%;
  margin-top: 25px;
  padding-top: 18px;
  border-top: 1px solid rgba(152, 192, 225, .13);
}
.intro-stats div { display: flex; flex-direction: column; min-width: 50px; }
.intro-stats strong { color: #fff; font-size: 20px; line-height: 25px; font-weight: 760; }
.intro-stats span { color: rgba(207, 229, 250, .46); font-size: 9px; line-height: 14px; }
.login-card {
  width: 294px;
  min-height: 290px;
  padding: 26px 25px 24px;
  border-radius: 9px;
  color: #1c2c41;
  background: #fff;
  box-shadow: 0 19px 46px rgba(0, 13, 46, .28), 0 2px 8px rgba(0, 15, 48, .08);
  animation: login-card-in var(--motion-slow, 360ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)) 180ms both;
}
.login-card h2 { margin: 0 0 3px; font-size: 15px; line-height: 21px; font-weight: 700; }
.card-subtitle { margin: 0 0 11px; color: #9ca7b4; font-size: 10px; line-height: 15px; }
.login-card form { display: flex; flex-direction: column; }
.login-card form > label { margin-bottom: 4px; color: #6e7a88; font-size: 10px; line-height: 14px; }
.input-wrap {
  display: flex;
  align-items: center;
  height: 33px;
  margin-bottom: 10px;
  border: 1px solid #e6ebf1;
  border-radius: 5px;
  background: #fff;
  transition: border-color var(--motion-fast, 160ms), box-shadow var(--motion-base, 240ms), background var(--motion-fast, 160ms);
}
.input-wrap:hover { border-color: #cfdaea; }
.input-wrap:focus-within { border-color: #abc4e5; box-shadow: 0 0 0 2px rgba(47, 118, 215, .09); }
.input-wrap:focus-within .input-icon { color: #4b82c8; }
.input-icon { flex: 0 0 auto; width: 11px; height: 11px; margin-left: 11px; color: #a7b2bf; }
.input-icon { transition: color var(--motion-fast, 160ms); }
.input-wrap input {
  flex: 1;
  width: 0;
  height: 100%;
  padding: 0 9px;
  border: 0;
  outline: 0;
  color: #25364d;
  background: transparent;
  font: inherit;
  font-size: 11px;
}
.input-wrap input::placeholder { color: #b3bdc8; }
.visibility-button {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  color: #a9b3bf;
  background: none;
  cursor: pointer;
  transition: color var(--motion-fast, 160ms), background var(--motion-fast, 160ms);
}
.visibility-button:hover { color: #4b82c8; background: #f1f6fc; }
.visibility-button:focus-visible,.forgot-link:focus-visible { outline: 2px solid #abc4e5; outline-offset: 3px; border-radius: 3px; }
.visibility-button svg { width: 12px; height: 12px; }
.form-options { display: flex; align-items: center; justify-content: space-between; margin: 1px 0 14px; }
.remember-option { display: inline-flex; align-items: center; gap: 5px; color: #8190a1; font-size: 10px; cursor: pointer; }
.remember-option input { width: 11px; height: 11px; margin: 0; accent-color: #2b70dd; }
.forgot-link { padding: 0; border: 0; background: none; color: #5b86bc; font-size: 10px; cursor: pointer; }
.forgot-link:hover { text-decoration: underline; }
.submit-button {
  height: 32px;
  border: 0;
  border-radius: 5px;
  color: #fff;
  background: linear-gradient(97deg, #3288f4, #2164d8);
  box-shadow: 0 2px 5px rgba(34, 99, 210, .16);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: filter var(--motion-fast, 160ms), transform var(--motion-base, 240ms) var(--motion-ease, cubic-bezier(.22, 1, .36, 1)), box-shadow var(--motion-base, 240ms);
}
.submit-button:hover:not(:disabled) { filter: brightness(1.06); transform: translateY(-1px); box-shadow: 0 5px 13px rgba(34, 99, 210, .28); }
.submit-button:active:not(:disabled) { transform: translateY(0) scale(.99); box-shadow: 0 1px 4px rgba(34, 99, 210, .2); }
.submit-button:focus-visible { outline: 3px solid rgba(47, 118, 215, .28); outline-offset: 3px; }
.submit-button:disabled { opacity: .7; cursor: wait; }
@keyframes login-background-in { from { opacity: 0; } }
@keyframes login-content-in { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes login-card-in { from { opacity: 0; transform: translateY(16px) scale(.985); } to { opacity: 1; transform: translateY(0) scale(1); } }
@media (max-width: 800px) {
  .login-content { grid-template-columns: minmax(0, 1fr); max-width: 474px; gap: 28px; padding: 44px 0; }
  .login-card { width: min(100%, 360px); justify-self: center; }
  .intro { transform: none; }
  .desktop-break { display: none; }
}
@media (max-width: 480px) {
  .login-content { width: calc(100% - 40px); gap: 24px; }
  .intro h1 { font-size: 23px; }
  .intro-stats { gap: 20px; }
  .login-card { padding: 25px; }
}
@media (max-width: 600px) {
  .login-page { min-height: 100dvh; }
  .login-content {
    width: calc(100% - 32px);
    max-width: 360px;
    min-width: 0;
    gap: 24px;
    padding: 32px 0;
    align-content: center;
  }
  .intro, .login-card, .login-card form, .input-wrap { min-width: 0; }
  .identity-mark { flex: 0 0 32px; }
  .identity > div:last-child { min-width: 0; }
  .identity-en { font-size: 7px; letter-spacing: 1px; overflow-wrap: anywhere; }
  .intro h1 { margin: 18px 0 0; font-size: 22px; }
  .intro-copy, .intro-stats { display: none; }
  .login-card { box-sizing: border-box; width: 100%; max-width: 100%; padding: 25px 24px; }
  .login-card h2 { font-size: 18px; line-height: 25px; }
  .card-subtitle { margin-bottom: 18px; font-size: 11px; line-height: 17px; }
  .login-card form > label { font-size: 12px; line-height: 18px; }
  .input-wrap { height: 42px; margin-bottom: 14px; }
  .input-wrap input { min-width: 0; font-size: 16px; }
  .visibility-button { width: 36px; height: 36px; }
  .remember-option, .forgot-link { font-size: 12px; }
  .form-options { margin-bottom: 20px; gap: 12px; }
  .submit-button { width: 100%; height: 42px; font-size: 14px; }
}
@media (prefers-reduced-motion: reduce) {
  .login-grid,.orbit,.light-trace,.identity,.intro h1,.intro-copy,.intro-stats,.login-card { animation: none; }
  .identity-mark,.input-wrap,.input-icon,.visibility-button,.submit-button { transition: none; }
  .identity:hover .identity-mark,.submit-button:hover:not(:disabled),.submit-button:active:not(:disabled) { transform: none; }
}
</style>
