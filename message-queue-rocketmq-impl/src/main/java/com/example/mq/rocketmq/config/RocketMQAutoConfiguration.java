package com.example.mq.rocketmq.config;

import com.example.mq.core.factory.ConsumerFactory;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.rocketmq.factory.RocketMQConsumerFactory;
import com.example.mq.rocketmq.factory.RocketMQProducerFactory;
import com.example.mq.support.serializer.JsonMessageSerializer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ自动配置类
 */
@Configuration
public class RocketMQAutoConfiguration {

    /**
     * 注册消息序列化器
     */
    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer messageSerializer() {
        return new JsonMessageSerializer();
    }

    /**
     * 注册RocketMQ生产者工厂
     */
    @Bean
    @ConditionalOnMissingBean(name = "rocketMQProducerFactory")
    public ProducerFactory rocketMQProducerFactory(MessageSerializer serializer) {
        return new RocketMQProducerFactory(serializer);
    }

    /**
     * 注册RocketMQ消费者工厂
     */
    @Bean
    @ConditionalOnMissingBean(name = "rocketMQConsumerFactory")
    public ConsumerFactory rocketMQConsumerFactory(MessageSerializer serializer) {
        return new RocketMQConsumerFactory(serializer);
    }
} 