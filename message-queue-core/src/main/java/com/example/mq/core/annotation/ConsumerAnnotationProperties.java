package com.example.mq.core.annotation;

import com.example.mq.core.enums.MessageQueueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 消费者注解属性类
 * 用于存储MessageQueueListener注解的配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerAnnotationProperties {
    /**
     * 消息队列类型
     */
    private MessageQueueType type = MessageQueueType.KAFKA;

    /**
     * 集群名称，不指定则使用默认集群
     */
    private String cluster = "";

    /**
     * 主题
     */
    private String topic;

    /**
     * 消费者组
     */
    private String group;

    /**
     * 并发消费者数
     */
    private int concurrency = 1;

    /**
     * 是否顺序消费
     */
    private boolean sequence = false;

    /**
     * 是否开启事务
     */
    private boolean transaction = false;

    /**
     * 延迟消费
     */
    private boolean delayQueue = false;

    /**
     * 优先级（用于多监听器时的排序）
     */
    private int order = 0;

    /**
     * 额外属性
     */
    private Map<String, String> properties = new HashMap<>();

}
