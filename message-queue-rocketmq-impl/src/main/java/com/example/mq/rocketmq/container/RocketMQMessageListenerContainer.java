package com.example.mq.rocketmq.container;

import com.example.mq.core.consumer.MessageListener;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.rocketmq.consumer.RocketMQConsumer;
import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ消息监听容器
 */
@Slf4j
public class RocketMQMessageListenerContainer extends MessageListenerContainer {

    @Override
    public void setupMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    @Override
    protected void doStart() {
        if (!(consumer instanceof RocketMQConsumer)) {
            throw new IllegalStateException("Consumer must be RocketMQConsumer type");
        }

        RocketMQConsumer rocketMQConsumer = (RocketMQConsumer) consumer;

        // 注册消息监听器
        rocketMQConsumer.registerListener(getTopic(), getGroup(), messageListener);

        log.info("启动RocketMQ消息监听容器: topic={}, group={}, concurrency={}", getTopic(), getGroup(), getConcurrency());
    }

    @Override
    protected void doStopInternal() {
        log.info("停止RocketMQ消息监听容器: topic={}, group={}", getTopic(), getGroup());
    }
} 