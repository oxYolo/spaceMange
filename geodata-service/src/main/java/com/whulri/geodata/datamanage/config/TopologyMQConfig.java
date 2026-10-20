package com.whulri.geodata.datamanage.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 - 拓扑检查队列
 */
@Configuration
public class TopologyMQConfig {

    @Value("${rabbitmq.topology.queue:TopologyQueue}")
    private String topologyQueue;

    @Value("${rabbitmq.topology.exchange:TopologyExchange}")
    private String topologyExchange;

    @Value("${rabbitmq.topology.routing:TopologyRouting}")
    private String topologyRoutingKey;

    @Bean
    public Queue topologyQueue() {
        return QueueBuilder.durable(topologyQueue).build();
    }

    @Bean
    public DirectExchange topologyExchange() {
        return new DirectExchange(topologyExchange);
    }

    @Bean
    public Binding topologyBinding(Queue topologyQueue, DirectExchange topologyExchange) {
        return BindingBuilder.bind(topologyQueue).to(topologyExchange).with(topologyRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate topologyRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
