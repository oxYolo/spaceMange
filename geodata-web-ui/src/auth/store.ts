import { acceptHMRUpdate, defineStore } from 'pinia'
import { onScopeDispose, ref } from 'vue'
import { authApi } from './api'
import { AppRequestError, getErrorMessage, isRequestCanceled, isUnauthorized } from '@/api/error'
import { getSessionVersion, getToken, invalidateSession, onSessionChange, rememberAccount, removeToken, setToken } from '@/auth/session'

const STORAGE_KEY = 'GeoDataUser'
const activeSessionSubscriptions = new Set<() => void>()

/**
 * 当前用户（操作人）
 * 后端多数写操作需要 user / createUser / createBy 字段。此前各页硬编码 'admin'，
 * 现统一由此 store 提供；预留登录后可 setUser 覆盖，未登录时回退默认值。
 */
const DEFAULT_USER = 'admin'

function readStored(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) || DEFAULT_USER
  } catch {
    return DEFAULT_USER
  }
}

export const useUserStore = defineStore('user', () => {
  /** 当前登录用户名（供 createUser / createBy / user 参数使用） */
  const userName = ref<string>(getToken() ? readStored() : DEFAULT_USER)
  const token = ref<string>(getToken())
  const profileLoaded = ref(false)
  const profileError = ref('')
  let profileVersion = -1
  let profileRequest: { version: number; promise: Promise<void> } | undefined
  let loginAttempt = 0

  function resetProfile(): void {
    profileLoaded.value = false
    profileError.value = ''
    profileVersion = -1
    userName.value = DEFAULT_USER
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // Storage 不可用时仍清除内存资料。
    }
  }

  const unsubscribe = onSessionChange((value) => {
    token.value = value
    resetProfile()
  })
  const disposeSubscription = () => {
    unsubscribe()
    activeSessionSubscriptions.delete(disposeSubscription)
  }
  activeSessionSubscriptions.add(disposeSubscription)
  onScopeDispose(disposeSubscription)

  function setUser(name: string) {
    const v = (name || '').trim() || DEFAULT_USER
    userName.value = v
    try {
      localStorage.setItem(STORAGE_KEY, v)
    } catch {
      /* localStorage 不可用时仅保留内存态 */
    }
  }

  function loadProfile(): Promise<void> {
    const currentToken = getToken()
    const currentVersion = getSessionVersion()
    if (!currentToken) return Promise.reject(new AppRequestError('请先登录', { kind: 'business', code: 401, silent: true }))
    if (profileLoaded.value && profileVersion === currentVersion) return Promise.resolve()
    if (profileRequest?.version === currentVersion) return profileRequest.promise

    const promise = (async () => {
      try {
        const response = await authApi.getUserInfo()
        if (getToken() !== currentToken || getSessionVersion() !== currentVersion) {
          throw new AppRequestError('会话已切换', { kind: 'cancel', silent: true })
        }
        if (Number(response.code) !== 200 || !response.user?.userName?.trim()) {
          throw new AppRequestError(response.msg || '无法获取用户信息', { kind: 'business', code: response.code })
        }
        setUser(response.user.userName)
        profileError.value = ''
        profileVersion = currentVersion
        profileLoaded.value = true
      } catch (error) {
        if (getToken() !== currentToken || getSessionVersion() !== currentVersion) {
          throw new AppRequestError('会话已切换', { kind: 'cancel', silent: true })
        }
        if (isUnauthorized(error)) invalidateSession(currentToken, currentVersion)
        else if (!isRequestCanceled(error)) profileError.value = getErrorMessage(error, '暂时无法连接身份验证服务')
        throw error
      }
    })().finally(() => {
      if (profileRequest?.promise === promise) profileRequest = undefined
    })
    profileRequest = { version: currentVersion, promise }
    return promise
  }

  async function login(username: string, password: string, rememberMe: boolean): Promise<void> {
    const attempt = ++loginAttempt
    const response = await authApi.login(username, password, rememberMe)
    if (attempt !== loginAttempt) throw new AppRequestError('登录已取消', { kind: 'cancel', silent: true })
    if (Number(response.code) !== 200 || !response.data?.access_token) {
      throw new AppRequestError(response.msg || '登录失败', { kind: 'business', code: response.code })
    }
    const nextToken = response.data.access_token
    // 同一个 token 重新登录时也重新校验资料。
    resetProfile()
    setToken(nextToken)
    const nextVersion = getSessionVersion()
    token.value = nextToken
    rememberAccount(username, rememberMe)
    try {
      await loadProfile()
      if (attempt !== loginAttempt || getToken() !== nextToken || getSessionVersion() !== nextVersion) {
        throw new AppRequestError('登录已取消', { kind: 'cancel', silent: true })
      }
    } catch (error) {
      if (attempt === loginAttempt && isUnauthorized(error)) clearSession(nextToken, nextVersion)
      throw error
    }
  }

  function clearSession(expectedToken?: string, expectedVersion?: number): void {
    if ((expectedToken !== undefined && getToken() !== expectedToken)
      || (expectedVersion !== undefined && getSessionVersion() !== expectedVersion)) return
    loginAttempt++
    token.value = ''
    resetProfile()
    removeToken()
  }

  async function logout(): Promise<void> {
    const currentToken = getToken()
    clearSession(currentToken)
    // 先退出本地再通知服务端，慢响应不会再触碰当前会话。
    if (currentToken) await authApi.logout(currentToken)
  }

  return { userName, token, profileLoaded, profileError, setUser, loadProfile, login, logout, clearSession }
})

if (import.meta.hot) {
  // Pinia replaces setup actions during HMR; remove callbacks bound to the old setup closure.
  import.meta.hot.dispose(() => { for (const dispose of [...activeSessionSubscriptions]) dispose() })
  import.meta.hot.accept(acceptHMRUpdate(useUserStore, import.meta.hot))
}
