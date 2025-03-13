package com.example.mq.examples.producer;

import com.example.mq.core.annotation.Producer;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.message.Message;
import com.example.mq.core.message.MessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ExampleProducer {

    @Producer(cluster = "primary", topic = "ai.test", sequence = true)
    public MessageResult sendToKafka(String message) {
        Message msg = new Message();
        msg.setPayload(message);
        msg.setMessageId(UUID.randomUUID().toString());
        return MessageResult.of(msg); // 方法体会被代理实现
    }

    @Producer(type = MessageQueueType.ROCKETMQ,
            cluster = "primary",
            topic = "ai-test",
            delayTime = 5000,
            delayUnit = TimeUnit.MILLISECONDS)
    public MessageResult sendToRocketMQ(String message) {
        Message msg = new Message();
        msg.setPayload(message);
        msg.setMessageId(UUID.randomUUID().toString());
        System.out.println("produce time: " + System.currentTimeMillis());
        return MessageResult.of(msg); // 方法体会被代理实现
    }

//    @Producer(cluster = "rabbitmq", topic = "test")
//    public SendResult sendToRabbitMQ(String message, String routingKey) {
//        return null; // 方法体会被代理实现
//    }
//
//    @Producer(cluster = "pulsar", topic = "test")
//    public SendResult sendToPulsar(String message, long delay, TimeUnit unit) {
//        return null; // 方法体会被代理实现
//    }
} 