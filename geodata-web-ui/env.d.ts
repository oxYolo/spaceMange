/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

/** Cesium 以全局脚本方式加载（public/lib/Cesium/Cesium.js） */
declare const Cesium: any

interface Window {
  getUrlConfig: (env: string) => any
  CESIUM_BASE_URL?: string
}
declare const __GEODATA_MOCK__: boolean
