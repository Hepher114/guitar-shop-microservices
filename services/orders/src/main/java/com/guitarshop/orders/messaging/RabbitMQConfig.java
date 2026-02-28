package com.guitarshop.orders.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${guitarshop.rabbitmq.queue:checkout.events}")
    private String queueName;

    @Bean
    public Queue checkoutEventsQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public TopicExchange guitarshopOrdersExchange() {
        return new TopicExchange("guitarshop.orders");
    }

    @Bean
    public Binding binding(Queue checkoutEventsQueue, TopicExchange guitarshopOrdersExchange) {
        return BindingBuilder.bind(checkoutEventsQueue)
                .to(guitarshopOrdersExchange)
                .with("order.created");
    }
}
