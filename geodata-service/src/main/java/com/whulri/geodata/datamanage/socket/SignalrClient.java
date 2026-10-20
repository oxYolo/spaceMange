package com.whulri.geodata.datamanage.socket;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;
import com.whulri.geodata.datamanage.config.IMConfig;
import com.whulri.geodata.common.bridge.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SignalR（IM 服务 /MessageHub）客户端。
 *
 * <p>单例长连接语义：本 Bean 全局只维护一条 HubConnection，start() 幂等；
 * 连接断开自动指数退避重连（1s/2s/5s/10s/30s，上限 30s）。
 * <b>禁止调用方每次发送前新建连接</b>——旧实现每次 start() 都重建连接且不关闭旧连接，
 * 高频进度推送（三维转换每 ≥3% 一次）会在 IM 服务侧累积大量泄漏连接。</p>
 *
 * <p>发送失败（IM 未部署/断连/超时）不抛出、不阻塞业务：消息进入有界重试队列
 * （容量 64，满则丢弃最旧并 WARN），后台线程每 2s 尝试重发。
 * DB 状态机始终是事实源，消息仅作「信号」，重试耗尽/丢弃的最坏后果是前端兜底轮询延迟。</p>
 *
 * <p>观测：sendSuccess/sendFail/dropped/reconnects 计数，getStats() 供运维与指标采集。</p>
 */
@Slf4j
@Component
public class SignalrClient {

    /** 重试队列容量（进度消息高频，超出按最旧丢弃，保证内存有界） */
    private static final int RETRY_QUEUE_CAPACITY = 64;

    private volatile HubConnection connection;

    /** 建连单飞标记（多线程并发确保只建一条） */
    private final AtomicBoolean starting = new AtomicBoolean(false);

    /** 重连单飞标记 */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    /** 已探测到 IM 配置不可用（未部署 imIP 未配），抑制重连风暴，待下次 start() 显式重置 */
    private volatile boolean unavailable;

    private final BlockingQueue<Object[]> retryQueue =
            new ArrayBlockingQueue<>(RETRY_QUEUE_CAPACITY);

    private final AtomicBoolean retryThreadStarted = new AtomicBoolean(false);

    private final AtomicLong sendSuccess = new AtomicLong();
    private final AtomicLong sendFail = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();

    /** 周期统计任务：每 60s 评估一次通道健康，降级时 WARN（可告警），健康时每 5 分钟一行 INFO 降噪 */
    private final ScheduledExecutorService statsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "signalr-stats");
        t.setDaemon(true);
        return t;
    });

    /** 上次健康 INFO 时间 / 上次观测到的 dropped 值（降噪与增量判断用） */
    private volatile long lastHealthyLogAt = 0L;
    private volatile long lastDroppedSeen = 0L;

    /** 退避序列（秒），重连间隔，上限 30s */
    private static final int[] BACKOFF_SECONDS = {1, 2, 5, 10, 30};
    private final AtomicInteger backoffIndex = new AtomicInteger(0);

    public SignalrClient() {
    }

    @PostConstruct
    public void init() {
        // 仅预热重试调度线程（不建连）：IM 服务未部署时启动建连只会刷错误日志，
        // 首条消息发送时才惰性建连更合理
        ensureRetryThread();
        // 周期输出通道统计：断连/积压/丢弃 -> WARN（日志告警源）；健康 -> 每 5 分钟一行 INFO
        statsScheduler.scheduleAtFixedRate(this::logStats, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 确保连接可用（幂等）：已连接直接返回；无连接则建连；被标记不可用时重置后重试。
     * 兼容旧调用方「发送前先 start()」的用法——现在 start() 无论如何调用都不会重复建连。
     */
    public synchronized void start() {
        unavailable = false;
        ensureConnected();
    }

    private synchronized void ensureConnected() {
        HubConnection current = connection;
        if (current != null && current.getConnectionState() == HubConnectionState.CONNECTED) {
            return;
        }
        if (starting.get()) {
            return;
        }
        String url = resolveHubUrl();
        if (url == null) {
            // IM 配置缺失/服务未部署：不抛异常，降级走重试队列与前端轮询兜底
            return;
        }
        starting.set(true);
        try {
            HubConnection conn = HubConnectionBuilder.create(url).build();
            conn.setKeepAliveInterval(TimeUnit.MINUTES.toMillis(10));
            conn.onClosed(error -> {
                log.warn("SignalR 连接已断开（5s 后自动重连）: {}",
                        error == null ? "unknown" : error.getMessage());
                connection = null;
                scheduleReconnect();
            });
            conn.start().blockingAwait(5, TimeUnit.SECONDS);
            connection = conn;
            backoffIndex.set(0);
            log.info("SignalR 连接已建立: {}", url);
            // 连上后立即冲服重试队列中积压的消息
            flushRetryQueue();
        } catch (Exception e) {
            connection = null;
            sendFail.incrementAndGet();
            log.warn("SignalR 连接失败（降级为重试队列 + 前端轮询兜底）: {}", e.getMessage());
            scheduleReconnect();
        } finally {
            starting.set(false);
        }
    }

    /** 单飞重连：上一次重连未结束则不并发触发；退避序列递增 */
    private void scheduleReconnect() {
        if (unavailable) {
            return;
        }
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    int idx = Math.min(backoffIndex.get(), BACKOFF_SECONDS.length - 1);
                    long waitSec = BACKOFF_SECONDS[idx];
                    if (idx < BACKOFF_SECONDS.length - 1) {
                        backoffIndex.incrementAndGet();
                    }
                    Thread.sleep(waitSec * 1000L);
                    if (unavailable) {
                        return;
                    }
                    HubConnection c = connection;
                    if (c != null && c.getConnectionState() == HubConnectionState.CONNECTED) {
                        return;
                    }
                    reconnects.incrementAndGet();
                    ensureConnected();
                    HubConnection after = connection;
                    if (after != null && after.getConnectionState() == HubConnectionState.CONNECTED) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                reconnecting.set(false);
            }
        }, "signalr-reconnect");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 私聊发送。未连接时先尝试惰性建连；仍不可用则入重试队列。
     * 同步发送（阻塞等服务端 ack，最坏 5s）——进度消息频率低（节流 ≥3%），可接受；
     * 失败绝不在业务线程抛出。
     */
    public void sendPrivate(String receiveUser, Object msg) {
        if (receiveUser == null || receiveUser.isBlank() || msg == null) {
            return;
        }
        if (!doSend(receiveUser, msg)) {
            enqueue(receiveUser, msg);
        }
    }

    public boolean sendPrivates(List<String> receiveUsers, Object msg) {
        if (receiveUsers == null || receiveUsers.isEmpty()) {
            return true;
        }
        for (String user : receiveUsers) {
            sendPrivate(user, msg);
        }
        return true;
    }

    /** 实际发送：连接可用则同步发送并统计；任何失败返回 false 由调用方入队 */
    private boolean doSend(String receiveUser, Object msg) {
        HubConnection conn = connection;
        if (conn == null || conn.getConnectionState() != HubConnectionState.CONNECTED) {
            ensureConnected();
            conn = connection;
        }
        if (conn == null || conn.getConnectionState() != HubConnectionState.CONNECTED) {
            sendFail.incrementAndGet();
            return false;
        }
        try {
            synchronized (this) {
                conn.send("SendPrivate", receiveUser, msg);
            }
            sendSuccess.incrementAndGet();
            return true;
        } catch (Exception e) {
            sendFail.incrementAndGet();
            log.warn("SignalR 发送失败，转入重试队列: user={}, err={}", receiveUser, e.getMessage());
            return false;
        }
    }

    private void enqueue(String receiveUser, Object msg) {
        Object[] item = {receiveUser, msg};
        // 满则丢弃最旧，保证有界
        while (!retryQueue.offer(item)) {
            Object[] stale = retryQueue.poll();
            if (stale == null) {
                break;
            }
            dropped.incrementAndGet();
            log.warn("SignalR 重试队列已满，丢弃最旧消息: user={}", stale[0]);
        }
        ensureRetryThread();
    }

    /** 重试调度：每 2s 冲服一次队列；连接恢复即发，未恢复继续等 */
    private void ensureRetryThread() {
        if (retryThreadStarted.compareAndSet(false, true)) {
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        flushRetryQueue();
                    } catch (Exception e) {
                        log.warn("SignalR 重试冲服异常（pending={}）: {}", retryQueue.size(), e.getMessage());
                    }
                }
            }, "signalr-retry-dispatcher");
            t.setDaemon(true);
            t.start();
        }
    }

    private void flushRetryQueue() {
        HubConnection conn = connection;
        if (conn == null || conn.getConnectionState() != HubConnectionState.CONNECTED) {
            return;
        }
        List<Object[]> pending = new java.util.ArrayList<>();
        retryQueue.drainTo(pending);
        for (Object[] item : pending) {
            String user = (String) item[0];
            Object msg = item[1];
            if (!doSend(user, msg)) {
                // 仍失败：放回队列头部（保持顺序）
                retryQueue.offer(item);
            }
        }
    }

    /**
     * 解析 Hub 地址；IM 未配置时返回 null 并抑制后续重连（避免刷日志），
     * 下次显式 start() 或配置修正后重置。运行期允许修正配置（读取 IMConfig Bean 的当前值）。
     */
    private String resolveHubUrl() {
        try {
            IMConfig imconfig = SpringUtil.getBean(IMConfig.class);
            String scheme = imconfig.getIsEnableSSL() ? "https" : "http";
            String url = scheme + "://" + imconfig.getImIP() + ":" + imconfig.getImPort()
                    + "/MessageHub?name=datamanage";
            unavailable = false;
            return url;
        } catch (Exception e) {
            if (!unavailable) {
                log.warn("IM 配置不可用（SignalR 实时推送降级为轮询兜底）: {}", e.getMessage());
            }
            unavailable = true;
            return null;
        }
    }

    /** 幂等关闭：清空引用，等待下次发送惰性重建。多调用方共享同一 Bean，close 不再终结单例生命周期 */
    public synchronized void close() {
        HubConnection conn = connection;
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                log.debug("关闭 SignalR 连接异常（忽略）: {}", e.getMessage());
            }
            connection = null;
        }
        retryQueue.clear();
    }

    /** 周期健康评估：断连/积压/丢弃立即 WARN，健康状态每 5 分钟一行 INFO 降噪 */
    private void logStats() {
        try {
            boolean connected = isConnected();
            long droppedDelta = dropped.get() - lastDroppedSeen;
            lastDroppedSeen = dropped.get();
            int pending = retryQueue.size();
            if (!connected) {
                lastHealthyLogAt = 0L;
                log.warn("SignalR 实时推送降级中（IM 连接不可用，前端走轮询兜底，业务不受影响）: {}", getStats());
            } else if (pending > 0 || droppedDelta > 0) {
                log.warn("SignalR 推送存在积压/丢弃: {}", getStats());
            } else {
                long now = System.currentTimeMillis();
                if (now - lastHealthyLogAt > 5 * 60 * 1000L) {
                    lastHealthyLogAt = now;
                    log.info("SignalR 实时推送正常: {}", getStats());
                }
            }
        } catch (Exception e) {
            log.debug("SignalR 周期统计异常（忽略）: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        statsScheduler.shutdownNow();
    }

    /**
     * 观测快照：发送成功/失败/丢弃/重连次数、当前连接状态、积压消息数。
     * 供日志巡检与后续 actuator 指标使用（P2）。
     */
    public String getStats() {
        HubConnection conn = connection;
        String state = conn == null ? "NULL" : conn.getConnectionState().name();
        return String.format(
                "SignalR stats @%s: state=%s, sent=%d, failed=%d, dropped=%d, reconnects=%d, pending=%d",
                LocalDateTime.now(), state,
                sendSuccess.get(), sendFail.get(), dropped.get(), reconnects.get(),
                retryQueue.size());
    }

    /** 是否已连接（供调用方判断实时通道健康度） */
    public boolean isConnected() {
        HubConnection conn = connection;
        return conn != null && conn.getConnectionState() == HubConnectionState.CONNECTED;
    }

    /** 兼容旧 @Data 生成的读访问（无外部 setter 依赖，仅保留读） */
    public HubConnection getConnection() {
        return connection;
    }
}
