/**
 * 运行时环境配置读取（来自 public/config/urlConfig.js，部署时可直接修改）
 */
export interface UrlConfig {
  systemName: string
  envName: string
  baseUrl: string
  /** whulri 基座服务（whulri-system，行政区划 /region 等）地址前缀 */
  baseApiUrl: string
  /** .NET IM 服务（SignalR /MessageHub）地址，如 http://127.0.0.1:5000；留空禁用实时进度推送 */
  imUrl: string
  uploadChunkSize: number
  tdtUrl: string
  tdtUrlHttps: string
  mapCenter: [number, number]
  mapZoom: number
  cesiumCenter: [number, number, number]
  getTdtToken: () => string
}

const urlConfig: UrlConfig = (window as any).getUrlConfig
  ? (window as any).getUrlConfig(import.meta.env.MODE)
  : {
      systemName: '空间数据管理控制台',
      envName: '开发',
      baseUrl: '/api',
      baseApiUrl: '/base-api',
      imUrl: '',
      uploadChunkSize: 5 * 1024 * 1024,
      tdtUrl: 'http://t0.tianditu.com',
      tdtUrlHttps: 'https://t0.tianditu.com',
      mapCenter: [113.9042, 29.7234] as [number, number],
      mapZoom: 11,
      cesiumCenter: [113.9042, 29.7234, 40000] as [number, number, number],
      getTdtToken: () => '',
    }

export default urlConfig
