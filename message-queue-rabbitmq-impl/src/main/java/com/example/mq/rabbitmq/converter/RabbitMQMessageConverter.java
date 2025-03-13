package com.example.mq.rabbitmq.converter;

import com.example.mq.core.message.Message;
import com.example.mq.support.converter.MessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ消息转换器
 * 用于将统一消息对象和RabbitMQ消息对象相互转换
 */
@Slf4j
public class RabbitMQMessageConverter implements MessageConverter<Delivery> {
    /**
     * 消息序列化器
     */
    private final MessageSerializer serializer;

    /**
     * RabbitMQ默认头部前缀
     */
    private static final String RABBITMQ_HEADER_PREFIX = "x-";

    /**
     * 消息ID的头部键
     */
    private static final String MESSAGE_ID_KEY = "messageId";

    /**
     * 时间戳的头部键
     */
    private static final String TIMESTAMP_KEY = "timestamp";

    /**
     * 延迟时间的头部键
     */
    private static final String DELAY_TIME_KEY = "x-delay";

    /**
     * 标签的头部键
     */
    private static final String TAGS_KEY = "tags";

    /**
     * 消息键的头部键
     */
    private static final String MESSAGE_KEY_KEY = "messageKey";

    /**
     * 主题的头部键
     */
    private static final String TOPIC_KEY = "topic";

    public RabbitMQMessageConverter(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    /**
     * 获取消息体的字节数组
     */
    public byte[] getMessageBody(Message message) {
        return serializer.serialize(message.getPayload());
    }

    @Override
    public Delivery toMQMessage(Message message) {
        // 由于不能直接创建 Delivery 对象，我们只能在这里返回 null
        // 实际发送消息时，应当使用 createMessageProperties 和 getMessageBody 方法
        log.warn("RabbitMQ 不支持直接创建 Delivery 对象，请使用 createMessageProperties 和 getMessageBody 方法");
        return null;
    }

    /**
     * 创建消息属性
     */
    public AMQP.BasicProperties createMessageProperties(Message message) {
        AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();

        // 设置持久化
        builder.deliveryMode(2); // 2 表示持久化

        // 设置消息ID
        if (StringUtils.hasText(message.getMessageId())) {
            builder.messageId(message.getMessageId());
        }

        // 设置内容类型
        builder.contentType("application/json");

        // 设置消息头部
        Map<String, Object> headers = new HashMap<>();

        // 添加延迟时间
        if (message.getDelayTime() > 0) {
            headers.put(DELAY_TIME_KEY, message.getDelayTime());
        }

        // 添加时间戳
        headers.put(TIMESTAMP_KEY, System.currentTimeMillis());

        // 添加主题
        headers.put(TOPIC_KEY, message.getTopic());

        // 添加消息键
        if (StringUtils.hasText(message.getKey())) {
            headers.put(MESSAGE_KEY_KEY, message.getKey());
        }

        // 添加自定义头部
        Map<String, String> messageHeaders = message.getHeaders();
        if (messageHeaders != null && !messageHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : messageHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    headers.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // 设置头部
        if (!headers.isEmpty()) {
            builder.headers(headers);
        }

        return builder.build();
    }

    @Override
    public Message fromMQMessage(Delivery delivery) {
        Message message = new Message();

        // 获取属性
        AMQP.BasicProperties properties = delivery.getProperties();

        // 设置消息ID
        if (StringUtils.hasText(properties.getMessageId())) {
            message.setMessageId(properties.getMessageId());
        }

        // 反序列化消息体
        try {
            byte[] body = delivery.getBody();
            Object payload = serializer.deserialize(body, String.class);
            message.setPayload(payload);
        } catch (Exception e) {
            log.error("反序列化RabbitMQ消息体失败", e);
            message.setPayload(delivery.getBody());
        }

        // 处理头部信息
        Map<String, Object> headers = properties.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            Map<String, String> customHeaders = new HashMap<>();

            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value == null) {
                    continue;
                }

                String strValue = value.toString();

                if (DELAY_TIME_KEY.equals(key)) {
                    try {
                        if (value instanceof Number) {
                            message.setDelayTime(((Number) value).longValue());
                        } else {
                            message.setDelayTime(Long.parseLong(strValue));
                        }
                    } catch (NumberFormatException e) {
                        log.warn("解析延迟时间失败: {}", strValue);
                    }
                } else if (TOPIC_KEY.equals(key)) {
                    message.setTopic(strValue);
                } else if (MESSAGE_KEY_KEY.equals(key)) {
                    message.setKey(strValue);
                } else if (!isRabbitMQInternalHeader(key)) {
                    customHeaders.put(key, strValue);
                }
            }

            // 设置自定义头部
            if (!customHeaders.isEmpty()) {
                message.setHeaders(customHeaders);
            }
        }

        return message;
    }

    /**
     * 判断是否为RabbitMQ内部头部
     */
    private boolean isRabbitMQInternalHeader(String key) {
        return key.startsWith(RABBITMQ_HEADER_PREFIX) ||
                key.equals(MESSAGE_ID_KEY) ||
                key.equals(TIMESTAMP_KEY) ||
                key.equals(DELAY_TIME_KEY) ||
                key.equals(TAGS_KEY) ||
                key.equals(MESSAGE_KEY_KEY) ||
                key.equals(TOPIC_KEY);
    }
} 