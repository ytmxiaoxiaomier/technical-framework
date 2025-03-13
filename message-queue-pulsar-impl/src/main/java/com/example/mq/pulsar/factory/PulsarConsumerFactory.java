package com.example.mq.pulsar.factory;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.factory.ConsumerFactory;
import com.example.mq.pulsar.consumer.PulsarConsumer;
import com.example.mq.pulsar.container.PulsarMessageListenerContainer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Pulsar消费者工厂
 */
@Component
public class PulsarConsumerFactory implements ConsumerFactory {
    private final MessageSerializer serializer;

    @Autowired
    public PulsarConsumerFactory(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public boolean supports(MessageQueueType type) {
        return MessageQueueType.PULSAR.equals(type);
    }

    @Override
    public IConsumer createConsumer(MessageQueueProperties properties) {
        return new PulsarConsumer(properties, serializer);
    }

    @Override
    public MessageListenerContainer createContainer(MessageQueueProperties properties) {
        return new PulsarMessageListenerContainer();
    }
} 