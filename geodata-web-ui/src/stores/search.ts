import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 顶栏全局检索关键词：由布局写入，资源总览等页面消费 */
export const useSearchStore = defineStore('gsearch', () => {
  const keyword = ref('')
  const setKeyword = (v: string) => (keyword.value = v)
  return { keyword, setKeyword }
})
