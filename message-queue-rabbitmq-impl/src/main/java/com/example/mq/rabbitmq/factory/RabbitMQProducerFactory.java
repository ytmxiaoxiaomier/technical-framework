package com.example.mq.rabbitmq.factory;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.core.producer.IProducer;
import com.example.mq.rabbitmq.producer.RabbitMQProducer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ生产者工厂
 */
@Component
public class RabbitMQProducerFactory implements ProducerFactory {
    private final MessageSerializer serializer;

    @Autowired
    public RabbitMQProducerFactory(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public boolean supports(MessageQueueType type) {
        return MessageQueueType.RABBITMQ.equals(type);
    }

    @Override
    public IProducer createProducer(MessageQueueProperties properties) {
        return new RabbitMQProducer(properties, serializer);
    }
} 