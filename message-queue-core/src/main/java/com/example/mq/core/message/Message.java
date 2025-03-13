package com.example.mq.core.message;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一消息对象
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID，默认生成UUID
     */
    private String messageId = UUID.randomUUID().toString().replace("-", "");

    /**
     * 主题
     */
    private String topic;

    /**
     * 标签/分区键
     */
    private String key;

    /**
     * 消息体
     */
    private Object payload;

    /**
     * 头信息
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * 延迟时间（毫秒）
     */
    private long delayTime;

    /**
     * 创建消息
     *
     * @param topic   主题
     * @param payload 消息体
     */
    public Message(String topic, Object payload) {
        this.topic = topic;
        this.payload = payload;
    }

    /**
     * 创建消息
     *
     * @param topic   主题
     * @param key     标签/分区键
     * @param payload 消息体
     */
    public Message(String topic, String key, Object payload) {
        this.topic = topic;
        this.key = key;
        this.payload = payload;
    }

    /**
     * 添加头信息
     *
     * @param key   键
     * @param value 值
     */
    public void addHeader(String key, String value) {
        if (key != null && value != null) {
            this.headers.put(key, value);
        }
    }

    /**
     * 添加多个头信息
     *
     * @param headers 头信息映射表
     */
    public void addHeaders(Map<String, String> headers) {
        if (headers != null) {
            this.headers.putAll(headers);
        }
    }

    /**
     * 克隆消息对象，生成一个全新的消息实例
     * 避免多次发送相同消息时前一次的消息配置影响后一次
     *
     * @return 新的消息实例
     */
    public Message copy() {
        Message copied = new Message();
        copied.setMessageId(this.messageId)
                .setTopic(this.topic)
                .setKey(this.key)
                .setPayload(this.payload)
                .setDelayTime(this.delayTime);

        // 深度复制 headers
        if (this.headers != null) {
            Map<String, String> headersCopy = new HashMap<>(this.headers);
            copied.setHeaders(headersCopy);
        }

        return copied;
    }
} 