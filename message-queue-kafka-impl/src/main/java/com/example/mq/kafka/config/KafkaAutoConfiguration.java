package com.example.mq.kafka.config;

import com.example.mq.core.factory.ConsumerFactory;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.kafka.factory.KafkaConsumerFactory;
import com.example.mq.kafka.factory.KafkaProducerFactory;
import com.example.mq.support.serializer.JsonMessageSerializer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka自动配置类
 */
@Configuration
public class KafkaAutoConfiguration {

    /**
     * 注册消息序列化器
     */
    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer messageSerializer() {
        return new JsonMessageSerializer();
    }

    /**
     * 注册Kafka生产者工厂
     */
    @Bean
    @ConditionalOnMissingBean(name = "kafkaProducerFactory")
    public ProducerFactory kafkaProducerFactory(MessageSerializer serializer) {
        return new KafkaProducerFactory(serializer);
    }

    /**
     * 注册Kafka消费者工厂
     */
    @Bean
    @ConditionalOnMissingBean(name = "kafkaConsumerFactory")
    public ConsumerFactory kafkaConsumerFactory(MessageSerializer serializer) {
        return new KafkaConsumerFactory(serializer);
    }
} 