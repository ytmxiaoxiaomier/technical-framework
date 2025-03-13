package com.example.mq.kafka.factory;

import com.example.mq.core.annotation.ProducerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.core.producer.IProducer;
import com.example.mq.kafka.producer.KafkaProducer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Kafka生产者工厂
 */
@Component
public class KafkaProducerFactory implements ProducerFactory {
    private final MessageSerializer serializer;

    @Autowired
    public KafkaProducerFactory(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public boolean supports(MessageQueueType type) {
        return MessageQueueType.KAFKA.equals(type);
    }

    @Override
    public IProducer createProducer(MessageQueueProperties properties) {
        return new KafkaProducer(properties, serializer);
    }

}