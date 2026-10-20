/**
 * .NET IM 服务（SignalR /MessageHub）接收端 —— 契约已对照服务端源码校准：
 * `D:\JavaProject\消息通知Core\Lri.IMCore`（Hubs/MessageHub.cs + Program.cs + appsettings.json）。
 *
 * 已核实的服务端事实：
 *  - 路由 `/MessageHub`（Program.cs MapHub），HTTP 端口 **8082**（LriHost.Endpoints.Http）；HTTPS 默认关闭；
 *  - 连接身份取 query **`name`**（MessageHub.GetUserInfo；缺失时记为 BK-<guid>），
 *    与 `SendPrivate(receiverUser, …)` 的接收人按 **Ordinal 精确匹配（区分大小写）**（ClientList.FindByValues）；
 *  - 推送客户端方法名固定为 **`receiveMessage`**（`Clients.*.SendAsync("receiveMessage", message)`）；
 *  - 消息体：Java 端 SignalrClient.sendPrivate(userId, msg) 发送 ProcessVO 的 JSON 串
 *    `{ moduleType, content: { currentStep, currentProgress, currentWork, currentUserId,
 *    isError, isFinish, result } }`（见 WebSocketUtil / ProcessVO）；
 *  - CORS：服务端 appsettings `CorsDomain`（WithOrigins + AllowCredentials），控制台来源必须列入
 *    （本仓库已加入 9200 开发来源；生产走 nginx 同源反代或补配来源）。
 *
 * 本模块行为：
 *  - Hub 基地址取运行时配置 `urlConfig.imUrl`（见 public/config/urlConfig.js），留空则禁用；
 *  - 以 `{imUrl}/MessageHub?name=<用户>` 连接，<用户> 必须与 Java 推送目标一致；
 *  - 任意连接/接收失败均静默降级（页面 5s 轮询兜底；IM 在线时页面轮询降频为 15s），不影响业务功能。
 *  - 行级局部更新：消息带 bizId（业务主键）/status 时，页面按行 patch 进度，不触发整列表刷新；
 *    需要只监听某条业务时可用的定向订阅见 onImBizMessage。
 */
import { HubConnection, HubConnectionBuilder, LogLevel } from '@microsoft/signalr'
import urlConfig from '@/config'

export interface ImMessage {
  /** 业务模块类型：Model3dConvert / layerInsertLog / topologyCheck 等 */
  moduleType: string
  step?: number
  progress?: number
  work?: string
  userId?: string
  isError?: boolean
  isFinish?: boolean
  result?: string | null
  /** 业务主键（三维 = model3dId），有则可做行级局部更新 */
  bizId?: string | null
  /** 业务状态机码（三维：2=QUEUED 3=CONVERTING 4=PUBLISHED 5=FAILED） */
  status?: number | null
  /** 失败原因（status=5） */
  failReason?: string | null
  /** 发布对象键（status=4） */
  publishKey?: string | null
  raw?: any
}

type ImHandler = (msg: ImMessage) => void

/** 服务端推送使用的客户端方法名（MessageHub 三处推送统一使用 `receiveMessage`，见服务端源码） */
const CLIENT_RECEIVE_METHOD = 'receiveMessage'

function normalize(payload: any): ImMessage | null {
  if (payload == null) return null
  let obj: any = payload
  if (typeof payload === 'string') {
    try {
      obj = JSON.parse(payload)
    } catch {
      return { moduleType: 'unknown', work: payload }
    }
  }
  const content = obj?.content ?? obj?.progressInfo ?? obj
  const moduleType = String(obj?.moduleType ?? obj?.type ?? '')
  if (!moduleType && !content) return null
  return {
    moduleType,
    step: content?.currentStep,
    progress: content?.currentProgress,
    work: content?.currentWork,
    userId: content?.currentUserId,
    isError: content?.isError,
    isFinish: content?.isFinish,
    result: content?.result ?? null,
    bizId: content?.bizId ?? obj?.bizId ?? null,
    status: content?.status ?? obj?.status ?? null,
    failReason: content?.failReason ?? obj?.failReason ?? null,
    publishKey: content?.publishKey ?? obj?.publishKey ?? null,
    raw: obj,
  }
}

export function imEnabled(): boolean {
  return !!String(urlConfig.imUrl || '').trim()
}

let connection: HubConnection | null = null
let starting = false
let currentUser = ''
const handlers = new Set<ImHandler>()

/**
 * 订阅 IM 进度消息；返回取消订阅函数。
 * @param userId 与后端推送目标一致的用户名（AuthUtil.getNickName）
 */
export function onImMessage(userId: string, handler: ImHandler): () => void {
  currentUser = userId || currentUser
  handlers.add(handler)
  void ensureConnected()
  return () => {
    handlers.delete(handler)
  }
}

/**
 * 定向订阅：仅当 msg.bizId 与给定业务主键一致时触发（行级局部更新用，bizId 为空则全量放行）。
 */
export function onImBizMessage(userId: string, bizId: string, handler: ImHandler): () => void {
  return onImMessage(userId, (msg) => {
    if (!bizId || String(msg.bizId) === String(bizId)) handler(msg)
  })
}

async function ensureConnected() {
  if (!imEnabled() || connection || starting) return
  starting = true
  try {
    const base = String(urlConfig.imUrl).replace(/\/+$/, '')
    // 服务端身份 = query name（Ordinal 精确匹配、区分大小写），必须与 Java 推送目标一致
    const url = `${base}/MessageHub?name=${encodeURIComponent(currentUser || 'admin')}`
    connection = new HubConnectionBuilder()
      .withUrl(url)
      .withAutomaticReconnect([0, 2000, 5000, 10000, 30000])
      .configureLogging(LogLevel.None)
      .build()
    const conn = connection
    conn.onclose((err) => {
      // withAutomaticReconnect 的重连序列耗尽后连接不会自行恢复：置空引用，
      // 下次订阅（onImMessage）或轮询 tick（ensureImAlive）时重建
      console.warn('[im] 连接关闭，将在下次重试时重建:', err?.message || '')
      if (connection === conn) connection = null
    })
    conn.on(CLIENT_RECEIVE_METHOD, (payload: any) => {
      const msg = normalize(payload)
      if (!msg) return
      handlers.forEach((h) => {
        try {
          h(msg)
        } catch {
          /* 单个订阅者异常不影响其它订阅 */
        }
      })
    })
    await conn.start()
  } catch (e) {
    // 服务未部署 / 跨域未放开：降级轮询兜底；留一条 warn 便于排查「为什么没走实时通道」
    console.warn('[im] SignalR 连接失败，已降级为轮询兜底:', (e as Error)?.message || e)
    try {
      connection?.stop()
    } catch {
      /* ignore */
    }
    connection = null
  } finally {
    starting = false
  }
}

/** 幂等保活：连接断开（引用已置空）时尝试重建；连接正常时为零成本空操作，供轮询 tick 调用 */
export function ensureImAlive(): void {
  void ensureConnected()
}
