package com.whulri.geodata.model3d.handler;

import com.alibaba.fastjson.JSON;
import com.whulri.geodata.convert.core.ConvertTask;
import com.whulri.geodata.model3d.service.IModel3dService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 三维转换队列处理器（薄壳：解析消息 → 服务层执行 → 手动 ack）。
 * 转换全流程（下载/解压/命令/进度/发布/状态回写）在 Model3dServiceImpl.executeConvert 中，
 * 本类只负责消息生命周期，不掺业务细节。
 *
 * <p>失败处理：转投死信队列（保留消息与失败原因，便于排查/人工重驱动），成功转投后再 ack；
 * 转投失败则 nack(requeue=false)，由主队列 DLX 兜底进入死信队列，均不引入无限重投。</p>
 */
@Slf4j
@Component
public class Model3dConvertQueueHandler {

    @Resource
    private IModel3dService model3dService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${geodata.convert.mq.dlx:Model3dConvertDlxExchange}")
    private String dlx;

    @Value("${geodata.convert.mq.dlq-routing:model3d.convert.dead}")
    private String dlqRouting;

    @RabbitListener(queues = "${geodata.convert.mq.queue:Model3dConvertQueue}")
    public void handleConvertTask(String msg, Message message, Channel channel) {
        final long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("收到三维转换任务消息: {}", msg);
        try {
            ConvertTask task = JSON.parseObject(msg, ConvertTask.class);
            if (task == null || StringUtils.isBlank(task.getModel3dId())) {
                log.error("三维转换消息格式错误，缺少 model3dId，转投死信队列");
                deadLetter(msg, "消息缺少 model3dId", deliveryTag, channel);
                return;
            }
            model3dService.executeConvert(task);
            channel.basicAck(deliveryTag, false);
            log.info("三维转换任务处理完成: {}", task.getModel3dId());
        } catch (Exception e) {
            log.error("三维转换任务执行失败: {}", e.getMessage(), e);
            deadLetter(msg, e.getMessage(), deliveryTag, channel);
        }
    }

    /**
     * 转投死信队列：成功投递后 ack 原消息（避免丢失且不无限重投）；
     * 若转投本身失败，退回 nack(requeue=false)，由主队列 DLX 兜底进入死信队列。
     */
    private void deadLetter(String msg, String reason, long deliveryTag, Channel channel) {
        boolean delivered = false;
        try {
            MessagePostProcessor header = m -> {
                m.getMessageProperties().setHeader("x-original-queue", "Model3dConvertQueue");
                m.getMessageProperties().setHeader("x-failure-reason",
                        reason == null ? "" : (reason.length() > 500 ? reason.substring(0, 500) : reason));
                m.getMessageProperties().setHeader("x-failed-at", String.valueOf(System.currentTimeMillis()));
                return m;
            };
            rabbitTemplate.convertAndSend(dlx, dlqRouting, msg, header);
            delivered = true;
        } catch (Exception ex) {
            log.error("转投死信队列失败，将按原语义丢弃消息: {}", ex.getMessage(), ex);
        }
        try {
            if (delivered) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception ex) {
            log.error("消息确认处理失败: {}", ex.getMessage(), ex);
        }
    }
}
