package com.example.mq.rocketmq.factory;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.factory.ConsumerFactory;
import com.example.mq.rocketmq.consumer.RocketMQConsumer;
import com.example.mq.rocketmq.container.RocketMQMessageListenerContainer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RocketMQ消费者工厂
 */
@Component
public class RocketMQConsumerFactory implements ConsumerFactory {
    private final MessageSerializer serializer;

    @Autowired
    public RocketMQConsumerFactory(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public boolean supports(MessageQueueType type) {
        return MessageQueueType.ROCKETMQ.equals(type);
    }

    @Override
    public IConsumer createConsumer(MessageQueueProperties properties) {
        return new RocketMQConsumer(properties, serializer);
    }

    @Override
    public MessageListenerContainer createContainer(MessageQueueProperties properties) {
        return new RocketMQMessageListenerContainer();
    }
} 