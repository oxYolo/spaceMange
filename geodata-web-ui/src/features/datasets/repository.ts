import { layerContentApi, model3dApi, serverInterfaceApi } from '@/api'
import { useUserStore } from '@/auth/store'
import { getToken, onSessionChange } from '@/auth/session'
import { normalizeLayer, normalizeM3d, typeDef } from './model'
import { createDatasetQueryService, readDatasetPage } from './query'
import { createDatasetServiceReader } from './services'

/** 缓存始终绑定当前用户和登录会话。 */
function scope(): string {
  const user = useUserStore()
  return JSON.stringify([user.userName, getToken()])
}

const queries = createDatasetQueryService({
  scope,
  async layer(filter, size, index) {
    const input: Record<string, unknown> = { createBy: useUserStore().userName }
    if (filter.keyword) input.layerName = filter.keyword
    if (filter.typeKey) Object.assign(input, typeDef(filter.typeKey)?.query)
    if (filter.layerType) input.layerType = filter.layerType
    if (filter.publishedOnly === true) input.publishStatus = '1'
    return readDatasetPage(await layerContentApi.getPageListByName(input, size, index), normalizeLayer)
  },
  async model(filter, size, index) {
    const query: Record<string, any> = { pageNum: index, pageSize: size }
    if (filter.keyword) query.keyword = filter.keyword
    if (filter.typeKey) Object.assign(query, typeDef(filter.typeKey)?.query)
    if (filter.publishedOnly === true) query.status = '4'
    return readDatasetPage(await model3dApi.page(query), normalizeM3d)
  },
})

const services = createDatasetServiceReader({
  scope,
  address: (id, type) => serverInterfaceApi.getServerInterface(id, type),
  publishUrl: (id) => model3dApi.publishUrl(id),
  tileProgress: (id) => serverInterfaceApi.tileProgress(id),
})

export const { loadUnifiedPage, invalidateDatasetQueries } = queries
export const { fetchAllSvcAddresses, fetchSvcAddressDetail, fetchSvcAddress, getDatasetServiceState, invalidateDatasetServices } = services

/** 上传、发布、删除等写操作成功后调用，确保下一次读取反映最新数据。 */
export function invalidateDatasets(): void {
  invalidateDatasetQueries()
  invalidateDatasetServices()
}

// 登出/换号时丢弃缓存和进行中的缓存任务，旧会话的慢响应不会再写回。
const unsubscribeSession = onSessionChange(invalidateDatasets)
if (import.meta.hot) import.meta.hot.dispose(unsubscribeSession)
