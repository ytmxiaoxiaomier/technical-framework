package com.example.mq.rocketmq.converter;

import com.example.mq.core.message.Message;
import com.example.mq.support.converter.MessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * RocketMQ消息转换器
 * 用于将统一消息对象转换为RocketMQ消息对象
 */
public class RocketMQMessageConverter implements MessageConverter<org.apache.rocketmq.common.message.Message> {
    private final MessageSerializer serializer;

    // 常量定义，替代 MessageConst 中的常量
    private static final String PROPERTY_MSG_ID = "MSGID";
    private static final String PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEY_IDX = "MESSAGE_UNIQ_KEY";
    private static final String PROPERTY_DELAY_TIME_LEVEL = "DELAY";

    public RocketMQMessageConverter(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public org.apache.rocketmq.common.message.Message toMQMessage(Message message) {
        // 序列化消息体
        byte[] payload = serializer.serialize(message.getPayload());

        // 创建RocketMQ消息
        org.apache.rocketmq.common.message.Message rocketMsg = new org.apache.rocketmq.common.message.Message();

        // 设置主题
        rocketMsg.setTopic(message.getTopic());

        // 设置消息体
        rocketMsg.setBody(payload);

        // 设置标签，用key作为标签
        if (StringUtils.hasText(message.getKey())) {
            rocketMsg.setTags(message.getKey());
        }

        // 设置消息ID
        if (StringUtils.hasText(message.getMessageId())) {
            rocketMsg.putUserProperty(PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEY_IDX, message.getMessageId());
        }

        // 设置延迟级别，RocketMQ的延迟级别从1开始
        if (message.getDelayTime() > 0)
            rocketMsg.setDelayTimeMs(message.getDelayTime());

        // 设置消息头
        if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
            for (Map.Entry<String, String> entry : message.getHeaders().entrySet()) {
                rocketMsg.putUserProperty(entry.getKey(), entry.getValue());
            }
        }

        return rocketMsg;
    }

    @Override
    public Message fromMQMessage(org.apache.rocketmq.common.message.Message mqMessage) {
        // 创建统一消息对象
        Message message = new Message();

        // 设置主题
        message.setTopic(mqMessage.getTopic());

        // 设置标签作为key
        if (StringUtils.hasText(mqMessage.getTags())) {
            message.setKey(mqMessage.getTags());
        }

        // 设置消息ID
        String messageId = mqMessage.getProperty(PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEY_IDX);
        if (StringUtils.hasText(messageId)) {
            message.setMessageId(messageId);
        } else if (mqMessage.getProperty(PROPERTY_MSG_ID) != null) {
            // 使用RocketMQ自己的消息ID
            message.setMessageId(mqMessage.getProperty(PROPERTY_MSG_ID));
        }

        // 转换消息头
        Map<String, String> properties = mqMessage.getProperties();
        if (properties != null && !properties.isEmpty()) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                // 排除RocketMQ的内部属性
                if (!isRocketMQInternalProperty(entry.getKey())) {
                    message.addHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        // 反序列化消息体，这里默认反序列化为String
        if (mqMessage.getBody() != null && mqMessage.getBody().length > 0) {
            String payload = serializer.deserialize(mqMessage.getBody(), String.class);
            message.setPayload(payload);
        }

        return message;
    }

    /**
     * 将延迟时间转换为RocketMQ的延迟级别
     * RocketMQ支持以下级别：1s, 5s, 10s, 30s, 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m, 1h, 2h
     *
     * @param delayTimeMs 延迟时间（毫秒）
     * @return 延迟级别，范围1-18，如果没有匹配的级别则返回0
     */
    private int convertToDelayLevel(long delayTimeMs) {
        long seconds = delayTimeMs / 1000;

        if (seconds <= 1) return 1;      // 1s
        if (seconds <= 5) return 2;      // 5s
        if (seconds <= 10) return 3;     // 10s
        if (seconds <= 30) return 4;     // 30s
        if (seconds <= 60) return 5;     // 1m
        if (seconds <= 120) return 6;    // 2m
        if (seconds <= 180) return 7;    // 3m
        if (seconds <= 240) return 8;    // 4m
        if (seconds <= 300) return 9;    // 5m
        if (seconds <= 360) return 10;   // 6m
        if (seconds <= 420) return 11;   // 7m
        if (seconds <= 480) return 12;   // 8m
        if (seconds <= 540) return 13;   // 9m
        if (seconds <= 600) return 14;   // 10m
        if (seconds <= 1200) return 15;  // 20m
        if (seconds <= 1800) return 16;  // 30m
        if (seconds <= 3600) return 17;  // 1h
        return 18;                       // 2h
    }

    /**
     * 判断是否为RocketMQ内部属性
     *
     * @param key 属性键
     * @return 是否为内部属性
     */
    private boolean isRocketMQInternalProperty(String key) {
        return key.startsWith("TRAN_") ||
                key.startsWith("MSG_") ||
                key.startsWith("TRACE_") ||
                key.equals(PROPERTY_MSG_ID) ||
                key.equals(PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEY_IDX) ||
                key.equals(PROPERTY_DELAY_TIME_LEVEL);
    }
} 