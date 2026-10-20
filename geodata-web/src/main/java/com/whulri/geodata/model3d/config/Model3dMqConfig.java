package com.whulri.geodata.model3d.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 三维转换队列声明（RabbitAdmin 建连后自动声明，避免依赖手工建队列）。
 * 既有 Topology/LayerTool 队列不受影响。
 *
 * <p>死信处理：主队列直接声明 {@code x-dead-letter-exchange} + routing-key 作兜底，
 * 任何 nack(requeue=false) / 过期消息自动进入 DLQ；正常失败路径由处理器手动转投，
 * 以便在消息头携带业务失败原因（{@code x-failure-reason}）。</p>
 */
@Configuration
public class Model3dMqConfig {

    @Value("${geodata.convert.mq.exchange:Model3dConvertExchange}")
    private String exchange;

    @Value("${geodata.convert.mq.queue:Model3dConvertQueue}")
    private String queue;

    @Value("${geodata.convert.mq.routing:model3d.convert}")
    private String routing;

    @Value("${geodata.convert.mq.dlx:Model3dConvertDlxExchange}")
    private String dlx;

    @Value("${geodata.convert.mq.dlq:Model3dConvertDeadQueue}")
    private String dlq;

    @Value("${geodata.convert.mq.dlq-routing:model3d.convert.dead}")
    private String dlqRouting;

    @Bean
    public TopicExchange model3dConvertExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue model3dConvertQueue() {
        // 主队列直接声明死信路由（前期无存量队列，无需为升级兼容回避该参数）：
        // 任何 nack(requeue=false) / 过期消息自动进入 DLQ 作兜底；
        // 正常失败路径仍由处理器手动转投，以携带 x-failure-reason。
        return QueueBuilder.durable(queue)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(dlqRouting)
                .build();
    }

    @Bean
    public Binding model3dConvertBinding() {
        return BindingBuilder.bind(model3dConvertQueue())
                .to(model3dConvertExchange())
                .with(routing);
    }

    // ==================== 死信（转换失败消息留存，便于排查与人工重驱动） ====================

    @Bean
    public DirectExchange model3dConvertDlxExchange() {
        return new DirectExchange(dlx, true, false);
    }

    @Bean
    public Queue model3dConvertDeadQueue() {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding model3dConvertDeadBinding() {
        return BindingBuilder.bind(model3dConvertDeadQueue())
                .to(model3dConvertDlxExchange())
                .with(dlqRouting);
    }
}
