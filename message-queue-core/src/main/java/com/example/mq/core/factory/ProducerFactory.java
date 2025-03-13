package com.example.mq.core.factory;

import com.example.mq.core.annotation.ProducerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.producer.IProducer;

import java.util.Map;

/**
 * 生产者工厂接口
 */
public interface ProducerFactory {
    /**
     * 判断是否支持指定类型的消息队列
     *
     * @param type 消息队列类型
     * @return 是否支持
     */
    boolean supports(MessageQueueType type);

    /**
     * 创建生产者
     *
     * @param properties 消息队列配置
     * @return 生产者实例
     */
    IProducer createProducer(MessageQueueProperties properties);

    /**
     * 创建生产者
     *
     * @param properties           消息队列配置
     * @param annotationProperties 注解配置
     * @return
     */
    default IProducer createProducer(MessageQueueProperties properties, ProducerAnnotationProperties annotationProperties) {
        Map<String, Object> extProperties = properties.getProperties();
        extProperties.put(IProducer.class.getName(), annotationProperties);
        return createProducer(properties);
    }
}