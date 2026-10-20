package com.whulri.geodata.web.config;

import com.whulri.geodata.datamanage.socket.SignalrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * SignalR（.NET IM 服务 /MessageHub）实时推送通道健康指示器。
 *
 * <p>设计约束（重要）：IM 是「尽力而为」的实时信号通道，{@code sdms_model3d} 等 DB 状态才是事实源，
 * IM 断开时前端自动退化为轮询兜底、业务不受影响。因此本组件**即使连接断开也返回 UP**——
 * 避免拖垮 {@code /actuator/health} 聚合状态与容器 HEALTHCHECK（IM 单点故障不应重启本服务），
 * 真实降级通过 health details 暴露 + {@code SignalrClient} 周期 WARN 日志告警。</p>
 *
 * <p>读取方式：{@code GET /actuator/health} → {@code components.signalr}（需 management.endpoint.health.show-details=always）。</p>
 */
@Component
public class SignalrHealthIndicator implements HealthIndicator {

    @Autowired
    private SignalrClient signalrClient;

    @Override
    public Health health() {
        boolean connected = signalrClient.isConnected();
        Health.Builder builder = Health.up()
                .withDetail("connected", connected)
                .withDetail("stats", signalrClient.getStats())
                .withDetail("note", connected
                        ? "IM 实时推送通道正常"
                        : "IM 连接不可用，已降级为前端轮询兜底（业务不受影响，请检查 IM 服务 Lri.IMCore:8082）");
        return builder.build();
    }
}
