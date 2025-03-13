package com.example.mq.core.factory;

import com.example.mq.core.annotation.ConsumerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.core.enums.MessageQueueType;

import java.util.Map;

/**
 * 消费者工厂接口
 */
public interface ConsumerFactory {
    /**
     * 判断是否支持指定类型的消息队列
     *
     * @param type 消息队列类型
     * @return 是否支持
     */
    boolean supports(MessageQueueType type);

    /**
     * 创建消费者
     *
     * @param properties 消息队列配置
     * @return 消费者实例
     */
    IConsumer createConsumer(MessageQueueProperties properties);

    /**
     * 创建消费者
     *
     * @param properties     消息队列配置
     * @param annoProperties 注解配置
     * @return
     */
    default IConsumer createConsumer(MessageQueueProperties properties, ConsumerAnnotationProperties annoProperties) {
        Map<String, Object> extProperties = properties.getProperties();
        extProperties.put(IConsumer.class.getName(), annoProperties);
        return createConsumer(properties);
    }

    /**
     * 创建监听容器
     *
     * @param properties 消息队列配置
     * @return 监听容器实例
     */
    MessageListenerContainer createContainer(MessageQueueProperties properties);
} 