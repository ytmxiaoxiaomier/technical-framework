package com.example.mq.kafka.converter;

import com.example.mq.core.message.Message;
import com.example.mq.support.converter.MessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka消息转换器
 * 用于将统一消息对象和Kafka消息对象相互转换
 */
@Slf4j
public class KafkaMessageConverter implements MessageConverter<ProducerRecord<String, byte[]>> {
    /**
     * 消息序列化器
     */
    private final MessageSerializer serializer;

    /**
     * Kafka默认头部前缀
     */
    private static final String KAFKA_HEADER_PREFIX = "kafka_";

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

    public KafkaMessageConverter(MessageSerializer serializer) {
        this.serializer = serializer;
    }

    /**
     * 将统一消息对象转换为Kafka生产者消息
     */
    @Override
    public ProducerRecord<String, byte[]> toMQMessage(Message message) {
        String topic = message.getTopic();
        String key = message.getKey();

        // 序列化消息体
        byte[] payload = serializer.serialize(message.getPayload());

        // 创建Kafka消息记录
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);

        // 添加消息头部
        addHeaders(record, message);

        return record;
    }

    /**
     * 将Kafka消费者消息转换为统一消息对象
     */
    public Message fromMQMessage(ConsumerRecord<String, byte[]> mqMessage) {
        Message message = new Message();

        // 提取主题
        message.setTopic(mqMessage.topic());

        // 提取消息键
        message.setKey(mqMessage.key());

        // 反序列化消息体
        try {
            // 尝试反序列化为字符串，如果失败保留原始字节数组
            Object payload = serializer.deserialize(mqMessage.value(), String.class);
            message.setPayload(payload);
        } catch (Exception e) {
            log.error("反序列化Kafka消息体失败", e);
            // 如果反序列化失败，则保留原始字节数组
            message.setPayload(mqMessage.value());
        }

        // 提取头部信息
        extractHeaders(mqMessage, message);

        return message;
    }

    /**
     * 添加消息头部
     */
    private void addHeaders(ProducerRecord<String, byte[]> record, Message message) {
        List<RecordHeader> headers = new ArrayList<>();

        // 添加消息ID
        if (message.getMessageId() != null) {
            headers.add(new RecordHeader(MESSAGE_ID_KEY, message.getMessageId().getBytes()));
        }

        // 添加延迟时间
        if (message.getDelayTime() > 0) {
            headers.add(new RecordHeader(DELAY_TIME_KEY, String.valueOf(message.getDelayTime()).getBytes()));
        }

        // 添加用户自定义头部
        Map<String, String> messageHeaders = message.getHeaders();
        if (messageHeaders != null && !messageHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : messageHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    headers.add(new RecordHeader(entry.getKey(), entry.getValue().getBytes()));
                }
            }
        }

        // 将头部添加到记录
        for (RecordHeader header : headers) {
            record.headers().add(header);
        }
    }

    /**
     * 提取头部信息
     */
    private void extractHeaders(ConsumerRecord<String, byte[]> record, Message message) {
        Map<String, String> headers = new HashMap<>();

        for (Header header : record.headers()) {
            String key = header.key();
            String value = new String(header.value());

            if (isKafkaInternalHeader(key)) {
                continue;
            }

            if (MESSAGE_ID_KEY.equals(key)) {
                message.setMessageId(value);
            } else if (DELAY_TIME_KEY.equals(key)) {
                try {
                    message.setDelayTime(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    log.warn("解析延迟时间失败", e);
                }
            } else {
                headers.put(key, value);
            }
        }

        if (!headers.isEmpty()) {
            message.setHeaders(headers);
        }
    }

    /**
     * 判断是否为Kafka内部头部
     */
    private boolean isKafkaInternalHeader(String key) {
        return key.startsWith(KAFKA_HEADER_PREFIX);
    }

    @Override
    public Message fromMQMessage(ProducerRecord<String, byte[]> mqMessage) {
        Message message = new Message();
        message.setTopic(mqMessage.topic());
        message.setKey(mqMessage.key());

        try {
            Object payload = serializer.deserialize(mqMessage.value(), String.class);
            message.setPayload(payload);
        } catch (Exception e) {
            log.error("反序列化Kafka消息体失败", e);
            message.setPayload(mqMessage.value());
        }

        // 提取头部信息
        Map<String, String> headers = new HashMap<>();
        mqMessage.headers().forEach(header -> {
            String key = header.key();
            String value = new String(header.value());

            if (isKafkaInternalHeader(key)) {
                return;
            }

            if (MESSAGE_ID_KEY.equals(key)) {
                message.setMessageId(value);
            } else if (DELAY_TIME_KEY.equals(key)) {
                try {
                    message.setDelayTime(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    log.warn("解析延迟时间失败", e);
                }
            } else {
                headers.put(key, value);
            }
        });

        if (!headers.isEmpty()) {
            message.setHeaders(headers);
        }

        return message;
    }

    /**
     * 将Kafka消息转换为统一消息格式
     */
    public Message toMessage(ConsumerRecord<String, byte[]> record) {
        // 创建消息对象
        Message message = new Message();
        message.setTopic(record.topic());
        message.setKey(record.key());

        // 反序列化消息体
        Object payload = serializer.deserialize(record.value());
        message.setPayload(payload);

        // 设置消息头
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            String value = new String(header.value());
            headers.put(header.key(), value);
        }
        message.addHeaders(headers);

        return message;
    }

    /**
     * 将统一消息格式转换为Kafka消息
     */
    public ProducerRecord<String, byte[]> toKafkaMessage(Message message) {
        // 序列化消息体
        byte[] value = serializer.serialize(message.getPayload());

        // 创建生产者记录
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                message.getTopic(),
                message.getKey(),
                value
        );

        // 设置消息头
        if (message.getHeaders() != null) {
            message.getHeaders().forEach((key, val) ->
                    record.headers().add(key, val.getBytes())
            );
        }

        return record;
    }

    /**
     * 将统一消息对象转换为Kafka消息
     */
    public ProducerRecord<String, byte[]> toProducerRecord(Message message) {
        // 序列化消息体
        byte[] value = serializer.serialize(message.getPayload());

        // 创建Kafka消息
        return new ProducerRecord<>(
                message.getTopic(),
                message.getKey(),
                value
        );
    }
} 