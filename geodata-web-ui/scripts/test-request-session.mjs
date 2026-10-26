import assert from 'node:assert/strict'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { build } from 'esbuild'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const inaccessibleStorage = {
  getItem() { throw new Error('Storage disabled') },
  setItem() { throw new Error('Storage disabled') },
  removeItem() { throw new Error('Storage disabled') },
}
globalThis.window = {}
globalThis.__GEODATA_TEST_MODE__ = 'development'
globalThis.sessionStorage = inaccessibleStorage
globalThis.localStorage = inaccessibleStorage

// 使用生产模块和 Axios 适配器验证行为，不依赖真实后端或新增测试依赖。
const bundle = await build({
  stdin: {
    resolveDir: root,
    contents: `
      export * from './src/auth/session.ts'
      export * from './src/auth/redirect.ts'
      export * from './src/api/request.ts'
      export * from './src/auth/store.ts'
      export * from './src/utils/feedback.ts'
      export { authApi } from './src/auth/api.ts'
      export { default as urlConfig } from './src/config.ts'
      export { sm2 } from 'sm-crypto'
      export { createPinia, setActivePinia } from 'pinia'
      export { AxiosError, CanceledError } from 'axios'
      export { messages } from 'element-plus'
    `,
  },
  absWorkingDir: root,
  bundle: true,
  format: 'cjs',
  platform: 'node',
  write: false,
  alias: { '@': join(root, 'src') },
  define: { 'import.meta.env.MODE': 'globalThis.__GEODATA_TEST_MODE__', 'import.meta.hot': 'undefined', 'process.env.NODE_ENV': '"test"' },
  plugins: [{
    name: 'notification-spy',
    setup(builder) {
      builder.onResolve({ filter: /^element-plus$/ }, () => ({ path: 'element-plus', namespace: 'test' }))
      builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents: `
        export const messages = []
        export const ElMessage = { error(message) { messages.push(message) } }
      ` }))
    },
  }],
})
const compiledModule = { exports: {} }
new Function('require', 'module', 'exports', bundle.outputFiles[0].text)(
  createRequire(import.meta.url), compiledModule, compiledModule.exports,
)
const app = compiledModule.exports
const response = (config, data) => ({ config, data, status: 200, statusText: 'OK', headers: {} })
const flushRequests = () => new Promise((resolve) => setImmediate(resolve))
function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => { resolve = resolvePromise; reject = rejectPromise })
  return { promise, resolve, reject }
}

app.setToken(' token-a ')
assert.equal(app.getToken(), 'token-a', 'Storage 不可用时仍能登录')
app.rememberAccount(' operator ', true)
assert.equal(app.getRememberedAccount(), 'operator')
app.rememberAccount('operator', false)
assert.equal(app.getRememberedAccount(), '')
let expiryCount = 0
const unsubscribeExpiry = app.onSessionExpired(() => expiryCount++)
assert.equal(app.invalidateSession('old-token'), false)
assert.equal(app.getToken(), 'token-a')
assert.equal(app.invalidateSession('token-a'), true)
assert.equal(app.invalidateSession('token-a'), false)
assert.equal(expiryCount, 1, '并发失效只广播一次')
assert.equal(app.getToken(), '')

assert.equal(await app.unwrap(Promise.resolve({ code: 200, success: true, result: false })), false)
assert.equal(await app.unwrap(Promise.resolve({ code: 200, result: 0 })), 0)
assert.deepEqual(await app.unwrap(Promise.resolve([1, 2])), [1, 2])
assert.deepEqual(await app.unwrap(Promise.resolve({ code: 200, data: { access_token: 'x' } })), { code: 200, data: { access_token: 'x' } })
await assert.rejects(app.unwrap(Promise.resolve({ code: 500, msg: '业务失败' })), (error) => error.kind === 'business' && error.code === 500)
await assert.rejects(app.unwrap(Promise.resolve({ code: '401', msg: '认证失败' })), (error) => error.kind === 'business' && error.code === '401')
assert.deepEqual(await app.unwrap(Promise.resolve({ code: '4326', codeName: 'WGS84' })), { code: '4326', codeName: 'WGS84' }, '普通字典不误识别成错误')
await assert.rejects(app.unwrap(Promise.resolve({ code: 200, success: false, result: null })), (error) => error.kind === 'business')

app.http.raw.defaults.adapter = async (config) => {
  throw new app.AxiosError('Network Error', 'ERR_BAD_RESPONSE', config, undefined, { ...response(config, { msg: '服务不可用' }), status: 503 })
}
await assert.rejects(app.http.get('/failure'), (error) => error.kind === 'http' && error.status === 503 && error.message === '服务不可用')
assert.equal(app.messages.length, 0, '请求层不重复弹错')
app.http.raw.defaults.adapter = async (config) => { throw new app.CanceledError('cancel', config) }
let cancellation
try { await app.http.get('/canceled') } catch (error) { cancellation = error }
assert.equal(app.isRequestCanceled(cancellation), true)
app.showError(cancellation, '不应出现')
assert.equal(app.messages.length, 0)
app.showError(new Error('页面失败'))
app.showError(new Error('页面失败'))
assert.deepEqual(app.messages, ['页面失败'], '并发页面报错按内容去重')
app.showError(new Error('另一故障'))
app.showError(new Error('页面失败'))
assert.deepEqual(app.messages, ['页面失败', '另一故障'], '交错故障不会重复提示同一内容')

app.setToken('http-token')
let capturedOldConfig
const oldReply = deferred()
app.http.raw.defaults.adapter = (config) => { capturedOldConfig = config; return oldReply.promise }
const oldRequest = app.http.get('/old-session')
await flushRequests()
app.setToken('new-token')
oldReply.resolve(response(capturedOldConfig, { code: 401, msg: '旧 token 失效' }))
await assert.rejects(oldRequest, (error) => error.code === 401 && error.silent)
assert.equal(app.getToken(), 'new-token', '旧请求不能清除新登录')
app.http.raw.defaults.adapter = async (config) => response(config, { code: 401, msg: '失效' })
await Promise.allSettled([app.http.get('/expired-a'), app.http.get('/expired-b')])
assert.equal(app.getToken(), '')
assert.equal(expiryCount, 2, 'HTTP 200 业务 401 同样只失效一次')
await assert.rejects(app.authApi.login('wrong', 'wrong', false), (error) => error.code === 401 && !error.silent)

// 同值 token 重新登录时，旧会话的响应仍须隔离。
app.setToken('reused-token')
let sameTokenConfig
const sameTokenReply = deferred()
app.http.raw.defaults.adapter = (config) => { sameTokenConfig = config; return sameTokenReply.promise }
const sameTokenRequest = app.http.get('/old-same-token')
await flushRequests()
app.setToken('reused-token')
sameTokenReply.resolve(response(sameTokenConfig, { code: '401', msg: '旧会话失效' }))
await assert.rejects(sameTokenRequest, (error) => error.code === '401' && error.silent)
assert.equal(app.getToken(), 'reused-token')
assert.equal(expiryCount, 2)
app.http.raw.defaults.adapter = async (config) => {
  throw new app.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { ...response(config, { msg: '请重新登录' }), status: 401 })
}
await Promise.allSettled([app.http.get('/http-401-a'), app.http.get('/http-401-b')])
assert.equal(app.getToken(), '')
assert.equal(expiryCount, 3, '并发 HTTP 401 只产生一次会话过期提示')

for (const invalid of [undefined, ['x'], '//outside.test', '/\\outside.test', '/login', '/login?redirect=/manage', '/%6cogin', '/maps/../login', '/%2foutside.test', '/maps\n']) {
  assert.equal(app.safeLoginRedirect(invalid), '/manage', `拒绝无效回跳 ${JSON.stringify(invalid)}`)
}
assert.equal(app.safeLoginRedirect('/maps?name=%E5%9C%B0%E5%9B%BE'), '/maps?name=%E5%9C%B0%E5%9B%BE')

app.setActivePinia(app.createPinia())
const user = app.useUserStore()
app.setToken('profile-token')
const profileReply = deferred()
let profileCalls = 0
app.http.raw.defaults.adapter = async (config) => {
  profileCalls++
  await profileReply.promise
  return response(config, { code: 200, user: { userName: 'operator' } })
}
const profiles = [user.loadProfile(), user.loadProfile(), user.loadProfile()]
await flushRequests()
assert.equal(profileCalls, 1, '并发路由只请求一次用户资料')
profileReply.resolve()
await Promise.all(profiles)
assert.equal(user.userName, 'operator')
assert.equal(user.profileLoaded, true)
await user.loadProfile()
assert.equal(profileCalls, 1, '同会话已加载资料直接复用')
app.invalidateSession('profile-token')
assert.equal(user.token, '')
assert.equal(user.userName, 'admin')
assert.equal(user.profileLoaded, false)

app.setToken('stale-profile')
const staleReply = deferred()
app.http.raw.defaults.adapter = async (config) => {
  await staleReply.promise
  return response(config, { code: 200, user: { userName: 'old-user' } })
}
const staleProfile = user.loadProfile()
await flushRequests()
app.setToken('stale-profile')
staleReply.resolve()
await assert.rejects(staleProfile, (error) => app.isRequestCanceled(error))
assert.equal(user.profileLoaded, false)
assert.equal(user.userName, 'admin', '旧资料不能覆盖当前会话')
app.setToken('latest-profile')

const logoutReply = deferred()
app.http.raw.defaults.adapter = async (config) => {
  assert.equal(config.headers.get('Authorization'), 'Bearer latest-profile')
  await logoutReply.promise
  return response(config, { code: 200 })
}
const loggingOut = user.logout()
assert.equal(app.getToken(), '', '退出立刻清除本地身份')
await flushRequests()
app.setToken('latest-profile')
logoutReply.resolve()
await loggingOut
assert.equal(app.getToken(), 'latest-profile', '慢退出响应不能清除同值 token 的新登录')

const loginReply = deferred()
app.http.raw.defaults.adapter = async (config) => {
  await loginReply.promise
  return response(config, { code: 200, data: { access_token: 'should-not-sign-in' } })
}
const signingIn = user.login('operator', 'password', false)
user.clearSession()
loginReply.resolve()
await assert.rejects(signingIn, (error) => app.isRequestCanceled(error))
assert.equal(app.getToken(), '', '取消登录后不能被慢响应再次登录')

app.setToken('valid-profile-network')
let transientProfileCalls = 0
app.http.raw.defaults.adapter = async (config) => {
  transientProfileCalls++
  throw new app.AxiosError('Network Error', 'ERR_NETWORK', config)
}
await assert.rejects(user.loadProfile(), (error) => error.kind === 'network')
assert.equal(app.getToken(), 'valid-profile-network', 'temporary profile connectivity must not clear the valid token')
assert.equal(user.profileLoaded, false)
assert.match(user.profileError, /网络异常/)
app.http.raw.defaults.adapter = async (config) => response(config, { code: 200, user: { userName: 'verified-again' } })
await user.loadProfile()
assert.equal(user.profileError, '')
assert.equal(user.userName, 'verified-again')

app.setToken('profile-503')
app.http.raw.defaults.adapter = async (config) => {
  throw new app.AxiosError('Unavailable', 'ERR_BAD_RESPONSE', config, undefined, { ...response(config, { msg: '验证服务正在恢复' }), status: 503 })
}
assert.equal(app.getToken(), 'profile-503', 'HTTP 503 is connectivity failure, not session expiry')
app.http.raw.defaults.adapter = async (config) => response(config, { code: 401, msg: '登录已过期' })
await assert.rejects(user.loadProfile())
assert.equal(app.getToken(), '', 'a genuine profile 401 still expires the session')
assert.equal(user.profileError, '')

app.http.raw.defaults.adapter = async (config) => {
  if (config.url === '/auth/dronelogin') return response(config, { code: 200, data: { access_token: 'signed-but-profile-offline' } })
  throw new app.AxiosError('Network Error', 'ERR_NETWORK', config)
}
await assert.rejects(user.login('operator', 'password', true), (error) => error.kind === 'network')
assert.equal(app.getToken(), 'signed-but-profile-offline', 'an issued login token survives temporary profile failure')
assert.equal(user.profileLoaded, false)
assert.ok(user.profileError)
app.setToken('old-failed-profile')
const oldFailedProfileReply = deferred()
let oldFailedConfig
app.http.raw.defaults.adapter = (config) => { oldFailedConfig = config; return oldFailedProfileReply.promise }
const oldFailedProfile = user.loadProfile()
await flushRequests()
app.setToken('new-profile-after-failure')
oldFailedProfileReply.reject(new app.AxiosError('Network Error', 'ERR_NETWORK', oldFailedConfig))
await assert.rejects(oldFailedProfile, (error) => app.isRequestCanceled(error))
assert.equal(user.profileError, '', 'old profile failures cannot put the new session into a retry state')
assert.equal(app.getToken(), 'new-profile-after-failure')
unsubscribeExpiry()
user.$dispose()

// 使用临时密钥验证生产登录加密，包含公钥换行和空格的真实配置场景。
globalThis.__GEODATA_TEST_MODE__ = 'production'
const keyPair = app.sm2.generateKeyPairHex()
app.urlConfig.authSm2PublicKey = keyPair.publicKey.match(/.{1,16}/g).join(' \n')
app.http.raw.defaults.adapter = async (config) => {
  const body = JSON.parse(config.data)
  assert.notEqual(body.password, 'example-password')
  assert.equal(app.sm2.doDecrypt(body.password, keyPair.privateKey, 1), 'example-password')
  return response(config, { code: 200, data: { access_token: 'encrypted-login' } })
}
await app.authApi.login('operator', 'example-password', false)
app.urlConfig.authSm2PublicKey = 'invalid'
assert.throws(() => app.authApi.login('operator', 'example-password', false), /SM2/)

function evaluateCommonJs(source) {
  const module = { exports: {} }
  new Function('require', 'module', 'exports', source)(createRequire(import.meta.url), module, module.exports)
  return module.exports
}
const persisted = new Map()
globalThis.sessionStorage = { getItem: (key) => persisted.get(key) ?? null, setItem: (key, value) => persisted.set(key, value), removeItem: (key) => persisted.delete(key) }
const sessionBuild = await build({ entryPoints: [join(root, 'src/auth/session.ts')], bundle: true, write: false, format: 'cjs', platform: 'node', define: { 'import.meta.hot': 'globalThis.__sessionHot' } })
const hotData = {}
let disposeSession
globalThis.__sessionHot = { data: hotData, dispose: (callback) => { disposeSession = callback } }
const beforeHmr = evaluateCommonJs(sessionBuild.outputFiles[0].text)
beforeHmr.setToken('persisted-session')
const previousVersion = beforeHmr.getSessionVersion()
let hmrNotices = 0
const stopHmrNotice = beforeHmr.onSessionChange(() => hmrNotices++)
disposeSession(hotData)
const afterHmr = evaluateCommonJs(sessionBuild.outputFiles[0].text)
assert.equal(afterHmr.getToken(), 'persisted-session')
assert.equal(afterHmr.getSessionVersion(), previousVersion, 'HMR keeps the version used by existing requests')
afterHmr.setToken('same-tab-session')
assert.equal(hmrNotices, 1, 'existing store subscriptions receive changes from the replacement module')
stopHmrNotice()
afterHmr.setToken('same-tab-session')
assert.equal(hmrNotices, 1, 'an old subscription can still be disposed after HMR')
globalThis.__sessionHot = undefined
const afterRefresh = evaluateCommonJs(sessionBuild.outputFiles[0].text)
assert.equal(afterRefresh.getToken(), 'same-tab-session', 'same-tab refresh restores sessionStorage')

delete globalThis.__sessionHot
console.log('请求与会话测试通过：错误归一化、真实401失效、临时profile故障重试、旧会话竞态、同tab刷新/HMR')
