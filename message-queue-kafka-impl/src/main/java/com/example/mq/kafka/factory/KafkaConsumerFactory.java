package com.example.mq.kafka.factory;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.factory.ConsumerFactory;
import com.example.mq.kafka.consumer.KafkaConsumerImpl;
import com.example.mq.kafka.container.KafkaMessageListenerContainer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Kafka消费者工厂实现
 */
@Component
public class KafkaConsumerFactory implements ConsumerFactory {

    private final MessageSerializer serializer;

    @Autowired
    public KafkaConsumerFactory(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public boolean supports(MessageQueueType type) {
        return type == MessageQueueType.KAFKA;
    }

    @Override
    public IConsumer createConsumer(MessageQueueProperties properties) {
        return new KafkaConsumerImpl(properties, serializer);
    }

    @Override
    public MessageListenerContainer createContainer(MessageQueueProperties properties) {
        return new KafkaMessageListenerContainer();
    }
} 