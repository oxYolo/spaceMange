import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/modules/auth'
import { getToken, rememberAccount, removeToken, setToken } from '@/auth/session'

const STORAGE_KEY = 'GeoDataUser'

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
  const userName = ref<string>(readStored())
  const token = ref<string>(getToken())
  const profileLoaded = ref(false)

  function setUser(name: string) {
    const v = (name || '').trim() || DEFAULT_USER
    userName.value = v
    try {
      localStorage.setItem(STORAGE_KEY, v)
    } catch {
      /* localStorage 不可用时仅保留内存态 */
    }
  }

  async function loadProfile(): Promise<void> {
    const response = await authApi.getUserInfo()
    if (response.code !== 200 || !response.user?.userName) {
      throw new Error(response.msg || '无法获取用户信息')
    }
    setUser(response.user.userName)
    profileLoaded.value = true
  }

  async function login(username: string, password: string, rememberMe: boolean): Promise<void> {
    const response = await authApi.login(username, password, rememberMe)
    if (response.code !== 200 || !response.data?.access_token) {
      throw new Error(response.msg || '登录失败')
    }
    token.value = response.data.access_token
    setToken(token.value)
    try {
      await loadProfile()
      rememberAccount(username, rememberMe)
    } catch (error) {
      clearSession()
      throw error
    }
  }

  function clearSession(): void {
    token.value = ''
    profileLoaded.value = false
    removeToken()
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      clearSession()
    }
  }

  return { userName, token, profileLoaded, setUser, loadProfile, login, logout, clearSession }
})
