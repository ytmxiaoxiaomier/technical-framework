package com.example.mq.pulsar.config;

import com.example.mq.core.factory.ConsumerFactory;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.pulsar.factory.PulsarConsumerFactory;
import com.example.mq.pulsar.factory.PulsarProducerFactory;
import com.example.mq.support.serializer.JsonMessageSerializer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pulsar自动配置类
 */
@Configuration
public class PulsarAutoConfiguration {

    /**
     * 注册消息序列化器
     */
    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer messageSerializer() {
        return new JsonMessageSerializer();
    }

    /**
     * 注册Pulsar生产者工厂
     */
    @Bean
    @ConditionalOnMissingBean(name = "pulsarProducerFactory")
    public ProducerFactory pulsarProducerFactory(MessageSerializer serializer) {
        return new PulsarProducerFactory(serializer);
    }

    /**
     * 注册Pulsar消费者工厂
     */
    @Bean
    @ConditionalOnMissingBean(name = "pulsarConsumerFactory")
    public ConsumerFactory pulsarConsumerFactory(MessageSerializer serializer) {
        return new PulsarConsumerFactory(serializer);
    }
} 