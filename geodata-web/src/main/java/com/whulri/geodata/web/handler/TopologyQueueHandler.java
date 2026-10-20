package com.whulri.geodata.web.handler;

import com.alibaba.fastjson.JSON;
import com.whulri.geodata.datamanage.dto.TopologyCheckDTO;
import com.whulri.geodata.datamanage.model.TopologyCheckReport;

import com.whulri.geodata.datamanage.service.ITopologyService;
import com.whulri.geodata.datamanage.service.IToolHisService;
import com.whulri.geodata.datamanage.socket.SignalrClient;
import com.whulri.geodata.datamanage.util.WebSocketUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.amqp.core.Message;
import com.rabbitmq.client.Channel;

import java.util.HashMap;
import java.util.Map;

/**
 * 拓扑检查队列处理器
 */
@Slf4j
@Component
public class TopologyQueueHandler {

    @Autowired
    private ITopologyService topologyService;

    @Autowired
    private IToolHisService toolHisService;

    private static final String MSG_TYPE = "TopologyCheck";

    @Autowired
    private SignalrClient signalrClient;

    @Value("${rabbitmq.topology.exchange:TopologyExchange}")
    private String topologyExchange;

    @Value("${rabbitmq.topology.routing:TopologyRouting}")
    private String topologyRoutingKey;

    /**
     * 处理拓扑检查消息
     */
    @RabbitListener(queues = "${rabbitmq.topology.queue:TopologyQueue}")
    public void handleTopologyTask(String msg, Message message, Channel channel) {
        final long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("收到拓扑检查任务消息: {}", msg);

        try {
            TopologyCheckDTO dto = JSON.parseObject(msg, TopologyCheckDTO.class);
            if (dto == null || StringUtils.isBlank(dto.getTaskId())) {
                log.error("消息格式错误，缺少必要参数");
                channel.basicAck(deliveryTag, false);
                return;
            }

            String userId = dto.getCreateBy();
            String guid = dto.getTaskId();

            // 创建 SignalR 连接
            SignalrClient connection = signalrClient;
            connection.start();

            // 更新状态为进行中
            updateTaskStatus(dto.getTaskId(), "0");

            // 发送进度通知
            WebSocketUtil.sendMsg(connection, userId, MSG_TYPE, 1, 10, "拓扑检查开始...", false, false, guid);

            // 先删除旧拓扑（确保数据是最新的，避免矢量编辑后拓扑表过期）
            try {
                topologyService.dropTopology(dto.getTopologyName());
            } catch (Exception e) {
                // 旧拓扑不存在时删除失败属正常（随后 ensureTopologyCreated 会重建），但需留痕可排查
                log.warn("删除旧拓扑失败（将继续重建）: topologyName={}, err={}", dto.getTopologyName(), e.getMessage());
            }

            // 重新创建拓扑
            int srid = dto.getSrid() != null ? dto.getSrid() : 4326;
            double tolerance = dto.getTolerance() != null ? dto.getTolerance() : 0.00001;
            topologyService.ensureTopologyCreated(
                dto.getLayerId(),
                dto.getFeatureTable(),
                dto.getTopologyName(),
                srid,
                tolerance
            );

            WebSocketUtil.sendMsg(connection, userId, MSG_TYPE, 2, 50, "拓扑初始化完成，开始检查...", false, false, guid);

            // 执行拓扑检查
            TopologyCheckReport report = topologyService.checkTopologySync(dto);

            // 更新状态为完成
            String status = report.isPassed() ? "1" : "2";
            updateTaskStatus(dto.getTaskId(), status);

            // 发送完成通知 - 将完整检查结果通过 SignalR 直接返回给前端
            String reportJson = JSON.toJSONString(report);
            WebSocketUtil.sendMsg(connection, userId, MSG_TYPE, 3, 100, reportJson, false, true, guid);

            // 确认消息
            channel.basicAck(deliveryTag, false);
            log.info("拓扑检查任务完成，taskId: {}, status: {}", dto.getTaskId(), status);

        } catch (Exception e) {
            log.error("拓扑检查任务执行失败: {}", e.getMessage(), e);

            // 更新任务状态为失败
            try {
                TopologyCheckDTO dto = JSON.parseObject(msg, TopologyCheckDTO.class);
                if (dto != null && StringUtils.isNotBlank(dto.getTaskId())) {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("error", e.getMessage());
                    updateTaskStatus(dto.getTaskId(), "2");

                    // 发送失败通知
                    SignalrClient connection = signalrClient;
                    connection.start();
                    WebSocketUtil.sendMsg(connection, dto.getCreateBy(), MSG_TYPE, 0, 100, "拓扑检查失败: " + e.getMessage(), true, true, dto.getTaskId());
                }
            } catch (Exception ex) {
                log.error("更新任务状态失败: {}", ex.getMessage(), ex);
            }

            // 拒绝消息
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ex) {
                log.error("basicNack处理失败: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(String taskId, String status) {
        try {
            var toolHis = toolHisService.getById(taskId);
            if (toolHis != null) {
                toolHis.setStatus(status);
                toolHisService.updateById(toolHis);
            }
        } catch (Exception e) {
            log.error("更新任务状态失败: {}", e.getMessage(), e);
        }
    }

}
