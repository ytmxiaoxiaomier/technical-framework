package com.example.mq.core.consumer;

/**
 * 消息消费者接口
 */
public interface IConsumer {
    /**
     * 订阅主题
     *
     * @param topic         主题
     * @param consumerGroup 消费者组
     */
    void subscribe(String topic, String consumerGroup);

    /**
     * 取消订阅主题
     *
     * @param topic 主题
     */
    void unsubscribe(String topic);

    /**
     * 暂停消费
     */
    void pause();

    /**
     * 恢复消费
     */
    void resume();

    /**
     * 关闭消费者
     */
    void close();
} 