package com.example.mq.core.enums;

/**
 * 消息队列类型枚举
 */
public enum MessageQueueType {
    /**
     * Kafka消息队列
     */
    KAFKA,

    /**
     * RocketMQ消息队列
     */
    ROCKETMQ,

    /**
     * RabbitMQ消息队列
     */
    RABBITMQ,

    /**
     * Pulsar消息队列
     */
    PULSAR;
} 