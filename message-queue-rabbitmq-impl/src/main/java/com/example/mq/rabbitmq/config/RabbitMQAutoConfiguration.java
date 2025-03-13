package com.example.mq.rabbitmq.config;

import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.rabbitmq.factory.RabbitMQProducerFactory;
import com.example.mq.support.serializer.JsonMessageSerializer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ自动配置类
 */
@Configuration
public class RabbitMQAutoConfiguration {

    /**
     * 注册消息序列化器
     */
    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer messageSerializer() {
        return new JsonMessageSerializer();
    }

    /**
     * 注册RabbitMQ生产者工厂
     */
    @Bean
    @ConditionalOnMissingBean(name = "rabbitMQProducerFactory")
    public ProducerFactory rabbitMQProducerFactory(MessageSerializer serializer) {
        return new RabbitMQProducerFactory(serializer);
    }
} 