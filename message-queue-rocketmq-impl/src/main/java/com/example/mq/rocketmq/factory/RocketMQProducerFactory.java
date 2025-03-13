package com.example.mq.rocketmq.factory;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.core.producer.IProducer;
import com.example.mq.rocketmq.producer.RocketMQProducer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RocketMQ生产者工厂
 */
@Component
public class RocketMQProducerFactory implements ProducerFactory {
    private final MessageSerializer serializer;

    @Autowired
    public RocketMQProducerFactory(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public boolean supports(MessageQueueType type) {
        return MessageQueueType.ROCKETMQ.equals(type);
    }

    @Override
    public IProducer createProducer(MessageQueueProperties properties) {
        return new RocketMQProducer(properties, serializer);
    }
} 