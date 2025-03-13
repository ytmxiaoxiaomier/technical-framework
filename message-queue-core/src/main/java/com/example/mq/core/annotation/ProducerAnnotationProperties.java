package com.example.mq.core.annotation;

import com.example.mq.core.enums.MessageQueueType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @see Producer
 */
@Data
@Builder
public class ProducerAnnotationProperties {

    /**
     * 消息队列类型
     */
    private MessageQueueType type;
    /**
     * 集群名称
     */
    private String cluster;
    /**
     * 主题/队列
     */
    private String topic;
    /**
     * 标签/分区键
     */
    private String key;
    /**
     * 是否异步发送
     */
    private boolean async;
    /**
     * 是否顺序发送
     */
    private boolean sequence;
    /**
     * 事务id
     */
    private String transactionId;
    /**
     * 延迟时间
     */
    private long delayTime;
    /**
     * 延迟时间单位
     */
    private TimeUnit delayUnit;
    /**
     * 超时时间（毫秒）
     */
    private long timeout;
    /**
     * 额外属性
     */
    private Map<String, String> properties;

}
