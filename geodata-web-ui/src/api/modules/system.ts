import { http } from '@/api/request'
import urlConfig from '@/config'

/** 操作日志（LayerLogController，2 端点） */
export const layerLogApi = {
  getList: (pageSize = 10, pageIndex = 1) => http.get<any>('/layerLog/getList', { pageSize, pageIndex }),
  remove: (id: string) => http.get<boolean>('/layerLog/delete', { id }),
}

/**
 * 行政区划（whulri 基座 whulri-system · SysRegionController）
 * GET {baseApiUrl}/region/list → AjaxResult{code, msg, data:[SysRegion]}
 * SysRegion 关键字段：code（区划编号）/ name（名称）/ regionLevel / parentCode
 * 需基座权限 system:region:list；基座 Token 由 request.ts 统一注入 Authorization。
 * baseApiUrl 默认 /base-api（开发走 Vite 代理，生产由 nginx 反代到 whulri-system:9201）。
 */
export const regionApi = {
  /** 行政区划平铺列表（按当前账号数据权限过滤） */
  getAllList: async (): Promise<any[]> => {
    // 基座返回 AjaxResult{code,msg,data}，与本项目 Result{result} 外壳不同，单独解包
    const res: any = await http.get<any>('/region/list', undefined, {
      baseURL: urlConfig.baseApiUrl,
    })
    if (res && typeof res === 'object' && !Array.isArray(res) && 'code' in res) {
      if (res.code !== 200) throw new Error(res.msg || '行政区划查询失败')
      return Array.isArray(res.data) ? res.data : []
    }
    return Array.isArray(res) ? res : []
  },
}

export default layerLogApi
