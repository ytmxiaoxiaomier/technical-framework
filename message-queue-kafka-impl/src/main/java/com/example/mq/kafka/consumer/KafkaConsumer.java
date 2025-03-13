package com.example.mq.kafka.consumer;

import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.consumer.MessageListener;

/**
 * Kafka消费者接口
 */
public interface KafkaConsumer extends IConsumer {

    /**
     * 订阅主题
     *
     * @param topic 主题
     * @param group 消费组
     */
    void subscribe(String topic, String group);

    /**
     * 轮询消息
     *
     * @param listener 消息监听器
     */
    void poll(MessageListener listener);

    /**
     * 提交偏移量
     *
     * @param topic     主题
     * @param partition 分区
     * @param offset    偏移量
     */
    void commitOffset(String topic, int partition, long offset);

    /**
     * 暂停消费
     *
     * @param topic 主题
     */
    void pause(String topic);

    /**
     * 恢复消费
     *
     * @param topic 主题
     */
    void resume(String topic);
} 