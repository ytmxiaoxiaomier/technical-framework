package com.example.mq.support.converter;

import com.example.mq.core.message.Message;

/**
 * 消息转换器接口
 * 用于将统一消息对象转换为特定MQ的消息对象，以及反向转换
 */
public interface MessageConverter<T> {
    /**
     * 将统一消息对象转换为特定MQ的消息对象
     *
     * @param message 统一消息对象
     * @return 特定MQ的消息对象
     */
    T toMQMessage(Message message);

    /**
     * 将特定MQ的消息对象转换为统一消息对象
     *
     * @param mqMessage 特定MQ的消息对象
     * @return 统一消息对象
     */
    Message fromMQMessage(T mqMessage);
} 