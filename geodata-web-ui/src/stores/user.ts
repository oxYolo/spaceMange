import { defineStore } from 'pinia'
import { ref } from 'vue'

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

  function setUser(name: string) {
    const v = (name || '').trim() || DEFAULT_USER
    userName.value = v
    try {
      localStorage.setItem(STORAGE_KEY, v)
    } catch {
      /* localStorage 不可用时仅保留内存态 */
    }
  }

  return { userName, setUser }
})
