package com.example.mq.pulsar.factory;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.core.producer.IProducer;
import com.example.mq.pulsar.producer.PulsarProducer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Pulsar生产者工厂
 */
@Component
public class PulsarProducerFactory implements ProducerFactory {
    private final MessageSerializer serializer;

    @Autowired
    public PulsarProducerFactory(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public boolean supports(MessageQueueType type) {
        return MessageQueueType.PULSAR.equals(type);
    }

    @Override
    public IProducer createProducer(MessageQueueProperties properties) {
        return new PulsarProducer(properties, serializer);
    }
} 