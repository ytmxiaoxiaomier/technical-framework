package com.example.mq.core.consumer;

import com.example.mq.core.message.Message;

/**
 * 消息监听器接口
 */
public interface MessageListener {
    /**
     * 消息处理方法
     *
     * @param message 消息
     * @param context 消费上下文
     * @return 消费结果
     */
    ConsumeResult onMessage(Message message, ConsumeContext context);

    /**
     * 消费结果枚举
     */
    enum ConsumeResult {
        /**
         * 消费成功
         */
        SUCCESS,

        /**
         * 消费失败，重试
         */
        RETRY,

        /**
         * 消费失败，不重试
         */
        FAILURE
    }

    /**
     * 消费上下文
     */
    interface ConsumeContext {
        /**
         * 获取消费者组
         *
         * @return 消费者组
         */
        String consumerGroup();

        /**
         * 获取主题
         *
         * @return 主题
         */
        String topic();

        /**
         * 获取原始消息
         *
         * @return 各MQ实现的原始消息对象
         */
        Object getRawMessage();

        /**
         * 手动确认消息
         */
        void acknowledge();

        /**
         * 拒绝消息
         *
         * @param requeue 是否重新入队
         */
        void reject(boolean requeue);
    }
} 