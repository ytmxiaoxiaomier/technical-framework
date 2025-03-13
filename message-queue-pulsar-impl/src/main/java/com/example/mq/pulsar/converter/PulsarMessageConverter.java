package com.example.mq.pulsar.converter;

import com.example.mq.core.message.Message;
import com.example.mq.support.converter.MessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.apache.pulsar.client.impl.MessageImpl;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pulsar消息转换器
 * 用于将统一消息对象和Pulsar消息对象相互转换
 */
@Slf4j
public class PulsarMessageConverter implements MessageConverter<org.apache.pulsar.client.api.Message<byte[]>> {
    /**
     * 消息序列化器
     */
    private final MessageSerializer serializer;

    /**
     * Pulsar默认头部前缀
     */
    private static final String PULSAR_HEADER_PREFIX = "pulsar_";

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
    private static final String DELAY_TIME_KEY = "delayTime";

    /**
     * 标签的头部键
     */
    private static final String TAGS_KEY = "tags";

    /**
     * 消息键的头部键
     */
    private static final String MESSAGE_KEY_KEY = "key";

    /**
     * 主题的头部键
     */
    private static final String TOPIC_KEY = "topic";

    public PulsarMessageConverter(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    /**
     * 将消息对象序列化为字节数组
     */
    public byte[] serializeMessage(Message message) {
        // 序列化消息体
        return serializer.serialize(message.getPayload());
    }

    /**
     * 设置消息构建器属性
     * 由于接口限制，需要使用此方法代替 toMQMessage
     */
    public void setMessageBuilderProperties(TypedMessageBuilder<byte[]> builder, Message message) {
        // 设置消息键
        if (StringUtils.hasText(message.getKey())) {
            builder.key(message.getKey());
        }

        // 设置消息体
        builder.value(serializeMessage(message));

        // 设置消息ID
        if (StringUtils.hasText(message.getMessageId())) {
            builder.property(MESSAGE_ID_KEY, message.getMessageId());
        }

        // 设置延迟时间
        if (message.getDelayTime() > 0) {
            builder.deliverAfter(message.getDelayTime(), TimeUnit.MILLISECONDS);
            builder.property(DELAY_TIME_KEY, String.valueOf(message.getDelayTime()));
        }

        // 设置属性
        Map<String, String> properties = new HashMap<>();

        // 添加时间戳
        properties.put(TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));

        // 添加主题
        properties.put(TOPIC_KEY, message.getTopic());

        // 添加自定义头部
        Map<String, String> headers = message.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value != null && !isPulsarInternalProperty(key)) {
                    properties.put(key, value);
                }
            }
        }

        // 设置所有属性
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            builder.property(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public org.apache.pulsar.client.api.Message<byte[]> toMQMessage(Message message) {
        // 由于无法直接创建 Pulsar 消息，需要通过 MessageBuilder 构建
        // 实际实现中，应该在 PulsarProducer 中使用 setMessageBuilderProperties 方法
        // 这里返回 null 表示不支持直接创建
        log.warn("Pulsar 不支持直接创建消息对象，请使用 setMessageBuilderProperties 方法");
        return null;
    }

    @Override
    public Message fromMQMessage(org.apache.pulsar.client.api.Message<byte[]> pulsarMessage) {
        Message message = new Message();

        // 设置消息键
        if (StringUtils.hasText(pulsarMessage.getKey())) {
            message.setKey(pulsarMessage.getKey());
        }

        // 设置主题，从原始消息获取
        String topic = null;

        // 尝试从原始消息中获取主题
        if (pulsarMessage instanceof MessageImpl) {
            try {
                topic = ((MessageImpl<byte[]>) pulsarMessage).getTopicName();
            } catch (Exception e) {
                // 忽略异常，尝试从属性中获取
            }
        }

        // 如果无法从原始消息中获取主题，则从属性中获取
        if (topic == null) {
            topic = pulsarMessage.getProperty(TOPIC_KEY);
        }

        if (StringUtils.hasText(topic)) {
            message.setTopic(topic);
        }

        // 反序列化消息体
        try {
            Object payload = serializer.deserialize(pulsarMessage.getData(), String.class);
            message.setPayload(payload);
        } catch (Exception e) {
            log.error("反序列化Pulsar消息体失败", e);
            message.setPayload(pulsarMessage.getData());
        }

        // 设置消息ID
        String messageId = pulsarMessage.getProperty(MESSAGE_ID_KEY);
        if (StringUtils.hasText(messageId)) {
            message.setMessageId(messageId);
        } else {
            // 如果没有消息ID，则使用Pulsar的消息ID
            message.setMessageId(pulsarMessage.getMessageId().toString());
        }

        // 提取属性
        Map<String, String> headers = new HashMap<>();
        Map<String, String> properties = pulsarMessage.getProperties();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isPulsarInternalProperty(key)) {
                continue;
            }

            // 处理特殊属性
            switch (key) {
                case MESSAGE_ID_KEY:
                    // 已处理
                    break;
                case DELAY_TIME_KEY:
                    try {
                        message.setDelayTime(Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        log.warn("解析延迟时间失败: {}", value);
                    }
                    break;
                default:
                    headers.put(key, value);
                    break;
            }
        }

        // 设置头部
        if (!headers.isEmpty()) {
            message.setHeaders(headers);
        }

        return message;
    }

    /**
     * 判断是否为Pulsar内部属性
     */
    private boolean isPulsarInternalProperty(String key) {
        return key.startsWith(PULSAR_HEADER_PREFIX) || key.startsWith("__");
    }
} 