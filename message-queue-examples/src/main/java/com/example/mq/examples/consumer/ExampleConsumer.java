package com.example.mq.examples.consumer;

import com.example.mq.core.annotation.MessageQueueListener;
import com.example.mq.core.annotation.MessageQueueListeners;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExampleConsumer {

    @MessageQueueListeners(value = {
            @MessageQueueListener(type = MessageQueueType.ROCKETMQ, cluster = "primary",
                    topic = "ai-test", group = "test-group"),
            @MessageQueueListener(cluster = "primary", topic = "ai.test", group = "test")})
    public void onKafkaMessage(Message message) {
        log.info("id={}, body={}", message.getMessageId(), message.getPayload());
        log.info("consume time: {}", System.currentTimeMillis());
    }

//    @MessageQueueListener(cluster = "primary", topic = "ai.test", group = "test-group")
//    public void onRocketMQMessage(Message message) {
//        log.info("id={}, body={}", message.getMessageId(), message.getPayload());
//    }

//    @MessageQueueListener(
//            topic = "test-topic-rabbitmq",
//            cluster = "rabbitmq",
//            group = "test-group"
//    )
//    public void onRabbitMQMessage(Message message) {
//        log.info("收到RabbitMQ消息: {}", message);
//    }
//
//    @MessageQueueListener(
//            topic = "test-topic-pulsar",
//            cluster = "pulsar",
//            group = "test-group"
//    )
//    public void onPulsarMessage(Message message) {
//        try {
//            log.info("收到Pulsar消息: {}", message);
//        } catch (Exception e) {
//            log.error("处理Pulsar消息失败", e);
//        }
//    }
} 